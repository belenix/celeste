/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */

package sunlabs.celeste.client.filesystem;

import java.io.IOException;
import java.io.Serializable;

import java.net.InetSocketAddress;
import java.net.URL;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;

import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.TimeProfiler;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.InternetMediaType;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.ExponentialBackoff;
import sunlabs.beehive.util.ExtentBuffer;
import sunlabs.beehive.util.OrderedProperties;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.simple.DirectoryImpl;
import sunlabs.celeste.client.filesystem.simple.FileException;
import sunlabs.celeste.client.filesystem.simple.FileImpl;
import sunlabs.celeste.client.filesystem.simple.FileSystem;
import sunlabs.celeste.client.filesystem.simple.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.simple.DirectoryImpl.Dirent;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.ProfileCache;
import sunlabs.celeste.util.CelesteEncoderDecoder;

import static sunlabs.celeste.client.filesystem.FileAttributes.Names.*;
import static sunlabs.celeste.client.filesystem.FileSystemAttributes.Names.MAINTAIN_SERIAL_NUMBERS_NAME;

/**
 * The {@code CelesteFileSystem} class organizes Celeste files and directories
 * into a coherent whole that provides much (but not all) of the behavior
 * expected of a POSIX-conformant file system.
 */
public class CelesteFileSystem implements HierarchicalFileSystem {
    //
    // TimeProfiler configuration information
    //
    // Should contain a colon-separated list of <class>.<method> strings,
    // where class omits the package prefix and method omits all signature
    // information but the method name itself.  Instrumented methods check
    // this string to see whether they're mentioned in it.
    //
    // XXX: This ought to be moved into TimeProfiler, so that the checks for
    //      a given enabling can be encapsulated there.
    //
    private static final String profileConfig = System.getProperty(
        "sunlabs.celeste.client.filesystem.ProfilerConfiguration", "");
    private static final String profileOutputFileName = System.getProperty(
        "sunlabs.celeste.client.filesystem.ProfilerOutputFile");

    //
    // The address of the Celeste node that this file system handle will use
    // to handle Celeste requests.
    //
    private final InetSocketAddress celesteAddress;

    //
    // The address of the Celeste confederation to which this file system
    // instance belongs.
    //
    private final BeehiveObjectId networkId;

    //
    // An unlocked profile used to authenticate all operations that make their
    // way to Celeste.
    //
    // XXX: To be phased out and replaced by a pair of (Profile_ password)
    //      arguments to be passed to all operations requiring authentication.
    //
    //      Alternatively, might be retained as something that can be
    //      optionally set.
    //
    private final Profile_ invokerProfile;
    private final String invokerPassword;

    //
    // XXX: We're redesigning the relationship between profiles and file
    //      systems.  We've agreed that profiles should be immutable.  We've
    //      also agreed that profiles represent principals in the system.  We
    //      want some way of allowing a principal, via its profile, to
    //      designate a set of file systems that the principal "owns" or
    //      controls in some way.  (We would also like to enable such
    //      associations for other Celeste objects and would like the solution
    //      for file systems to be extensible to other object types.)
    //
    //      The old way of handling this issue was to set the ROOT_DIR
    //      property in a given profile to name the object id of the uniqueId
    //      part of a FileIdentifier naming the root directory of the file
    //      system associated with the profile.  This approach has the
    //      drawback of allowing only a single file system per profile, which
    //      forces an unfortunate conflation between profile and file system.
    //
    //      The new design addresses these problems in two separate steps,
    //      only one of which is implemented as yet.
    //
    //      First, we make profiles immutable by no longer using the ROOT_DIR
    //      property.  Instead we apply a well-known and deterministic
    //      transformation of the profile name to obtain an object-id that
    //      names user-settable information.  (This object id currently refers
    //      to the superblock object for the sole file system associated with
    //      the profile.  Its meaning will change in step two.)
    //
    //      Second, the object id for user settable information will refer to
    //      a directory of file systems (and possibly other object types)
    //      associated with the user's profile.
    //
    //      Note, though, that there's a problem with the approach outlined in
    //      step one.  If we rely on a deterministic and well-known
    //      transformation of the profile's object id to obtain the metadata
    //      object id, then we're open to an attack that pre-emptively stores
    //      something with that object id.  (For example, the attacker could
    //      store a file there and then delete the file, leaving that object
    //      id unusable for anything.)  What to do?  The current plan is
    //      simply to live with the problem.
    //

    //
    // Each file system instance has an associated profile whose name is used
    // to identify it.  This profile is also used to authorize access to the
    // file system's root directory.  (And since all lookups start from the
    // root directory, the profile implicitly is required to authorize access
    // to all files in the file system.)
    //
    // Note that, as things stand, the name space profile is used here only as
    // a way to obtain the object id of the corresponding root directory (or
    // rather, superblock), (to the point where there's no need to store the
    // profile's identity once the root directory id's been computed from it).
    // In particular, there's no need for that profile to be unlocked, since
    // it's not used to sign (or even verify) anything.
    //
    private final Profile_ nameSpaceProfile;

    private final Superblock superblock;

    /**
     * <p>
     *
     * Create a {@code CelesteFileSystem} object representing a file system
     * identified by {@code nameSpace} in the Celeste confederation that the
     * Celeste node at {@code celesteAddress} belongs to.  Access to the file
     * system is via proxies obtained from {@code proxyCache}, using the
     * credential identified by {@code invokerCredentialName} and {@code
     * invokerPassword}.
     *
     * </p><p>
     *
     * Both the name space denoted by {@code nameSpace} and the credential
     * denoted by {@code invokerCredentialName} must already exist in Celeste.
     *
     * </p><p>
     *
     * {@code proxyCache} can be {@code null}, in which case this {@code
     * CelesteFileSystem} instance will use a cache constructed with default
     * capacity and timeout parameters.
     *
     * </p>
     *
     * @param celesteAddress        the address of the Celeste node that will
     *                              handle requests made on behalf of this
     *                              file system
     * @param proxyCache            a {@code CelesteProxy.Cache} providing
     *                              access to the Celeste node at {@code
     *                              celesteAddress}
     * @param nameSpace             the name space identifying this file
     *                              system
     * @param invokerCredentialName the name of the credential of the entity
     *                              to whom operations issued against this
     *                              file system will be ascribed
     * @param invokerPassword       the password for {@code
     *                              invokerCredentialName}
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CredentialProblem
     *         if either the name space denoted by {@code nameSpace} or the
     *         credential denoted by {@code invokerCredentialName} is not
     *         present in Celeste, or if either is defective in some other way
     * @throws FileException.Deleted
     * @throws FileException.DirectoryCorrupted
     *         if the contents of the file system's internal superblock
     *         directory have been corrupted
     * @throws FileException.PermissionDenied
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public CelesteFileSystem(InetSocketAddress celesteAddress,
            CelesteProxy.Cache proxyCache, String nameSpace,
            String invokerCredentialName, String invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.IOException,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // If the caller doesn't supply a proxy cache, set one up here with
        // default parameters that might or might not match actual
        // requirements.
        //
        if (proxyCache == null) {
            proxyCache = new CelesteProxy.Cache(4,
                Time.secondsInMilliseconds(0));
        }

        CelesteAPI proxy = null;
        try {
            proxy = proxyCache.getAndRemove(celesteAddress);
            this.networkId = proxy.getNetworkObjectId();
        } catch (Exception e) {
            throw new FileException.CelesteInaccessible(e);
        } finally {
            proxyCache.addAndEvictOld(celesteAddress, proxy);
        }

        this.celesteAddress = celesteAddress;

        //
        // We only need profileCache for as long as it takes to obtain the
        // nameSpaceProfile and invokerProfile credentials, so create it with
        // a short timeout for its entries.
        //
        final ProfileCache profileCache =
            new ProfileCache(celesteAddress, proxyCache, 10);

        this.fileImplCache =
            new FileImpl.Cache(10, celesteAddress, proxyCache);

        try {
            this.nameSpaceProfile = profileCache.get(nameSpace);
        } catch (Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        if (this.nameSpaceProfile == null) {
            Throwable nested = new CelesteException.NotFoundException(
                String.format("%s: missing name space: \"%s\"",
                    celesteAddress, nameSpace));
            throw new FileException.CredentialProblem(nested);
        }

        boolean profile_exists =
            profileCache.profileExists(invokerCredentialName);
        if (!profile_exists) {
            Throwable nested = new CelesteException.NotFoundException(
                String.format("%s: missing credential: \"%s\"",
                    celesteAddress, invokerCredentialName));
            throw new FileException.CredentialProblem(nested);
        }

        //
        // This needs clear ways to provide error messages.  null is only
        // returned if the underlying Celeste.readProfile fails without an
        // exception...
        //
        try {
            this.invokerProfile = profileCache.get(invokerCredentialName);
        } catch (Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        this.invokerPassword = invokerPassword; // Be very careful about doing this.
        if (this.invokerProfile == null) {
            Throwable nested = new CelesteException.NotFoundException(
                String.format("%s: non-loadable profile: %s",
                    celesteAddress, invokerCredentialName));
            throw new FileException.CredentialProblem(nested);
        }

        this.superblock = new Superblock(this);
    }

    /**
     * Create a new file system and its root directory, using the invoker
     * credential associated with this {@code CelesteFileSystem} object.
     */
    public void newFileSystem()
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.newFileSystem(null);
    }

    /**
     * Create a new file system and its root directory, using the invoker
     * credential associated with this {@code CelesteFileSystem} object, and
     * associating the given {@code clientMetadata} with the new file system's
     * root directory.
     *
     * @param clientProperties  properties to be associated with the new file
     *                          system's root directory
     */
    public void newFileSystem(OrderedProperties clientProperties)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.newFileSystem(clientProperties, new OrderedProperties());
    }

    /**
     * <p>
     *
     * Create a new file system and its root directory, using the invoker
     * credential associated with this {@code CelesteFileSystem} object,
     * associating the given {@code clientProperties} with the new file system's
     * root directory, and applying the file system-wide attributes given in
     * {@code fileSystemAttributes}.
     *
     * </p><p>
     *
     * This method recognizes one file system-wide attribute:
     * <q>MaintainSerialNumbers</q>.  If {@code true}, the file system will
     * associate a unique <em>serial number</em> (analogous to an inode number
     * in other file systems) for each file.  If {@code false} (the default),
     * files will all have an identical, default serial number.
     *
     * </p>
     *
     * @param clientProperties      properties to be associated with the new file
     *                              system's root directory
     * @param fileSystemAttributes  attributes governing the behavior of the
     *                              file system as a whole
     */
    //
    // XXX: Move the description of MaintainSerialNumbers to
    //      FileSystemAttributes when that class is created.
    //
    public void newFileSystem(OrderedProperties clientProperties,
            OrderedProperties fileSystemAttributes)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.newFileSystem(clientProperties, null, fileSystemAttributes);
    }

   /**
     * <p>
     *
     * Create a new file system and its root directory, using the invoker
     * credential associated with this {@code CelesteFileSystem} object,
     * associating the given {@code clientMetadata} with the new file system's
     * root directory, and applying the file system-wide attributes given in
     * {@code fileSystemAttributes}.  The {@code defaultCreationAttributes}
     * parameter should contain attributes drawn from {@link
     * FileAttributes#creationSet} that are to be used as defaults when calls
     * to the {@link #createFile(PathName) createFile()} and {@link
     * #createDirectory(PathName) createDirectory()} methods and their
     * overloadings don't specify those attributes directly.
     *
     * </p><p>
     *
     * This method recognizes one file system-wide attribute:
     * <q>MaintainSerialNumbers</q>.  If {@code true}, the file system will
     * associate a unique <em>serial number</em> (analogous to an inode number
     * in other file systems) for each file.  If {@code false} (the default),
     * files will all have an identical, default serial number.
     *
     * </p>
     *
     * @param clientProperties          properties to be associated with the
     *                                  new file system's root directory
     * @param defaultCreationAttributes attributes that are to be used as
     *                                  defaults when creating files or
     *                                  directories in this file system
     * @param fileSystemAttributes      attributes governing the behavior of
     *                                  the file system as a whole
     *
     * @throws FileException.BadVersion
     * @throws FileException.CapacityExceeded
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     *         if the credentials supplied when instantiating this {@code
     *         CelesteFileSsytem} object can't successfully be used to create
     *         the file system
     * @throws FileException.Deleted
     * @throws FileException.Exists
     *         if the file system already exists
     * @throws FileException.InvalidName
     * @throws FileException.Locked
     * @throws FileException.NotFound
     * @throws FileException.PermissionDenied
     * @throws FileException.RetriesExceeded
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    //
    // XXX: Move the description of MaintainSerialNumbers to
    //      FileSystemAttributes when that class is created.
    //
    public void newFileSystem(OrderedProperties clientProperties,
            OrderedProperties defaultCreationAttributes,
            OrderedProperties fileSystemAttributes)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.IOException,
            FileException.Locked,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        try {
            this.superblock.create(clientProperties, defaultCreationAttributes,
                fileSystemAttributes);
        } catch (FileException.FileSystemNotFound e) {
            //
            // The create call is supposed to cure the conditions that would
            // cause this exception to be thrown.  That is, it should not
            // occur, and if it does, it most likely indicates a bug.
            //
            System.err.printf("Unexpected exception; probable bug:%n");
            e.printStackTrace(System.err);
            throw new FileException.Runtime(e);
        }
    }

    /**
     * Prepare this {@code CelesteFileSystem} instance for reclamation by
     * discarding cached resources it holds.
     */
    public void dispose() {
        //
        // Flush out the FileImpl cache.  Assume that there's little to be
        // gained by discarding the cache itself (which would require making
        // the field referencing it non-final and complicate logic elsewhere
        // in this class).
        //
        this.fileImplCache.setCapacity(0);
    }

    /**
     * Returns {@code true} if the file system associated with this {@code
     * CelesteFileSystem} handle exists.
     *
     * @return  {@code true} if there is a file system associated with this
     *          handle
     */
    public boolean exists()  throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.Runtime,
            FileException.ValidationFailed {
        synchronized(this) {
            //
            // If the superblock claims it exists, that's good enough for us.
            //
            if (this.superblock == null)
                return false;
            if (!this.superblock.exists())
                return false;
            return true;
        }
    }

    /**
     * Returns {@code true} if the file named by {@code path} exists and
     * {@code false} otherwise.
     *
     * @return  {@code true} if the file named by {@code path} exists
     *
     * @throws FileException.BadVersion
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.DirectoryCorrupted
     *         if the contents of any of the directories leading to the file
     *         named by {@code path} have been corrupted
     * @throws FileException.FileSystemNotFound
     * @throws FileException.IOException
     * @throws FileException.NotDirectory
     * @throws FileException.PermissionDenied
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public boolean fileExists(PathName path) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.NotDirectory,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        synchronized(this) {
            FileImpl fileImpl = null;
            try {
                final CelesteFileSystem.File file = this.getFile(path);
                fileImpl = CelesteFileSystem.this.getAndRemove(file.fid);
                return fileImpl.fileExists();
            } catch (FileException.Deleted e) {
                return false;
            } catch (FileException.NotFound e) {
                return false;
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }
    }

    /**
     * Returns the address of the Celeste node with which this {@code
     * CelesteFileSystem} instance communicates.
     *
     * @return  the address of the Celeste node that this file system handle
     *          uses to manage its files
     */
    public InetSocketAddress getCelesteAddress() {
        return this.celesteAddress;
    }

    /**
     * Returns the name of this file system's associated name space, which,
     * in turn, acts as the name of this file system itself.
     *
     * @return this file system's name
     */
    public String getName() {
        return this.nameSpaceProfile.getName();
    }

    /**
     * Returns the name space associated with this file system.
     *
     * @return  this file system's name space
     */
    //
    // Does the return type need to be as specific as Profile_, or would
    // Credential do?
    //
    public Profile_ getNameSpace() {
        return this.nameSpaceProfile;
    }

    /**
     * Returns the {@code BeehiveObjectId} of the name space associated with
     * this file system.
     *
     * @return this file system's name space's object id
     */
    public BeehiveObjectId getNameSpaceId() {
        return this.nameSpaceProfile.getObjectId();
    }

    /**
     * Returns the credential supplied when this {@code CelesteFileSystem}
     * access handle was created.  This credential is used to authenticate all
     * requests made to the underlying Celeste file store through this handle.
     *
     * @return  the credential supplied when this {@code CelesteFileSystem}
     *          instance was created
     */
    public Credential getInvokerCredential() {
        return this.invokerProfile;
    }

    //
    // Convenience method for accessing the invoker password.
    //
    private char[] getInvokerPassword() {
        return this.invokerPassword.toCharArray();
    }

//    /**
//     * Provided that this file system has been created, returns a map
//     * containing the default attributes to be applied to newly created files
//     * and directories; if not, returns {@code null}.
//     *
//     * @return the default creation attributes for this file system
//     */
//    //
//    // XXX: Needs work.  If default creation attributes haven't been set
//    //      explicitly, this method will return an empty map.  But there are
//    //      defaults nonetheless -- the ones that FileImpl applies.  Need to
//    //      get those and merge them in with anything that's been explicitly
//    //      supplied as a default.
//    //
//    public OrderedProperties getDefaultCreationAttributes() throws
//            FileException.BadVersion,
//            FileException.CelesteFailed,
//            FileException.CelesteInaccessible,
//            FileException.CredentialProblem,
//            FileException.Deleted,
//            FileException.DirectoryCorrupted,
//            FileException.IOException,
//            FileException.NotFound,
//            FileException.PermissionDenied,
//            FileException.Runtime,
//            FileException.ValidationFailed {
//        return this.superblock.getDefaultCreationAttributes();
//    }

    /**
     * {@code CelesteInputStream} presents an {@link java.io.InputStream input
     * stream} view of a file.
     */
    public class CelesteInputStream extends java.io.InputStream {
        private final CelesteFileSystem.File file;
        private boolean isClosed = false;

        public CelesteInputStream(CelesteFileSystem.File file) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            super();

            this.file = file;

            if (!this.file.exists())
                throw new FileException.NotFound();
        }

        @Override
        public void close() {
            this.isClosed = true;
        }

        public void seek(long offset) {
            synchronized(this.file) {
                this.file.position(offset);
            }
        }

        @Override
        public int read() throws IOException {
            byte buf[] = new byte[1];
            int len = this.read(buf, 0, buf.length);
            if (len < 1) {
                throw new IOException("nothing to read");
            }
            return buf[0];
        }

        @Override
        public int read(byte[] buf) throws IOException {
            return this.read(buf, 0, buf.length);
        }

        @Override
        public int read(byte[] buf, int offset, int maxlen) throws IOException {
            if (this.isClosed)
                throw new IOException("file is closed");
            //
            // N.B.  The call to this.file.read() updates the underlying seek
            // pointer, so there's no need to do so here.
            //
            return this.file.read(ByteBuffer.wrap(buf, offset, maxlen));
        }

        @Override
        public long skip(long n) {
            synchronized(this.file) {
                this.file.position(this.file.position() + n);
                return n;
            }
        }
    }

    /**
     * {@code CelesteOutputStream} presents an {@link java.io.OutputStream
     * output stream} view of a file.
     */
    public class CelesteOutputStream extends java.io.OutputStream {
        private final CelesteFileSystem.File file;
        private boolean isClosed = false;
        private int bufferSize = 0;

        /**
         * Wrap an output stream around {@code file}, truncating the file to
         * zero length and setting its position to {@code 0L} if {@code
         * truncate} is {@code true}.  Writes to the output stream will be to
         * {@code file}'s current position, which will then be updated to
         * match the number of bytes written.
         *
         * @param file      the file that's to be accessed through an output
         *                  stream
         * @param truncate  if {@code true} truncate the file to zero length
         *                  before wrapping it with the output stream
         *
         * @throws FileException.NotFound
         *      if {@code file} does not exist
         * @throws FileException.IsDirectory
         *      if {@code file} is actually a directory
         * @throws IOException
         *      if truncating {@code file} fails
         */
        public CelesteOutputStream(CelesteFileSystem.File file,
                boolean truncate)
            throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.NotFound,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.Runtime,
                FileException.ValidationFailed,
                IOException {
            super();
            this.file = file;
            synchronized (this.file) {
                if (!this.file.exists()) {
                    throw new FileException.NotFound();
                } else if (truncate) {
                    //
                    // Could check if the file is already empty.
                    //
                    this.file.truncate(0);
                }
            }
        }

        /**
         * Truncate {@code file} to zero length and wrap an output stream
         * around it.
         *
         * @param file  the file that's to be accessed through an output
         *              stream
         *
         * @throws FileException.NotFound
         *      if {@code file} does not exist
         * @throws FileException.IsDirectory
         *      if {@code file} is actually a directory
         * @throws IOException
         *      if truncating {@code file} fails
         *
         * @see #CelesteOutputStream(CelesteFileSystem.File, boolean)
         */
        //
        // XXX: javadoc refuses to accept the @see tag above, no matter what
        //      variations I try in qualifying the classes it mentions.
        //
        public CelesteOutputStream(CelesteFileSystem.File file) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed,
                IOException {
            this(file, true);
        }

        @Override
        public void write(int b) throws IOException {
            byte[] buffer = new byte[1];
            buffer[0] = (byte) b;
            this.write(buffer);
        }

        @Override
        public void write(byte[] b) throws IOException {
            this.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
            if (this.isClosed)
                throw new IllegalStateException("file is closed");

            //
            // XXX: Profiling/Debugging support.
            //
            boolean debug = CelesteFileSystem.profileConfig.contains(
                "CelesteFileSystem.CelesteOutputStream.write");
            TimeProfiler timer = new TimeProfiler(
                "CelesteFileSystem.COS.write");

            //
            // Impedance matching:  Turn the byte array from which we'll write
            // into an ExtentBuffer, which is what the FileChannel.write()
            // variant (from CelesteFileSystem.File) we'll use expects.
            //
            ExtentBuffer src = new ExtentBuffer(this.file.position(),
                ByteBuffer.wrap(b, offset, length).slice());

            try {
                synchronized(this.file) {
                    //
                    // Break the write up into buffer sized chunks.
                    //
                    // It would be best to buffer writes until the buffer size
                    // number of bytes is available to submit in one write.
                    //
                    // The breakup is expressed by adjusting src's position
                    // and limit to reflect the span each chunk covers.  After
                    // each partial write, src's position will have advanced
                    // to equal its limit, which is then increased by the
                    // length of the next partial write.
                    //
                    final int blockSize = this.file.getBlockSize();
                    assert blockSize > 0;
                    final int bufSize = this.bufferSize == 0 ?
                        blockSize : this.bufferSize;

                    //
                    // Write from the current offset up to the minimum of the
                    // next buffer size boundary at or beyond the current file
                    // offset and the requested ending point.
                    //
                    long fileOffset = this.file.position();
                    long bufferBoundary =
                        ((fileOffset + bufSize - 1) / bufSize) * bufSize;
                    int chunkLength = Math.min(
                        (int)(bufferBoundary - fileOffset), length);
                    if (chunkLength > 0) {
                        //System.err.printf(
                        //    "CFS.OS.write: leading %d fragment @ %d%n",
                        //    chunkLength, fileOffset);
                        final int startPosition = src.position();
                        src.limit(startPosition + chunkLength);
                        int bytesWritten = this.file.write(src);
                        this.file.position(fileOffset + bytesWritten);
                        assert src.position() == startPosition + chunkLength;
                        assert src.position() == src.limit();
                        timer.stamp("chunk_0");
                    }
                    //
                    // Write the rest of the file in bufSize chunks, up to
                    // the last one, which may be smaller.
                    //
                    // Note that, since the src ExtentBuffer was originally
                    // formed by calling slice(), its capacity is the original
                    // limit.  Thus, we progress by advancing limit and
                    // writing from position to limit, until limit would pass
                    // capacity.
                    //
                    final int capacity = src.capacity();
                    int chunk = 1;
                    while (src.position() < capacity) {
                        //
                        // The code above is intended to handle slop leading
                        // up to the next block boundary.  So if the position
                        // is not now at a block boundary, something's
                        // terribly wrong.
                        //
                        fileOffset = this.file.position();
                        assert (fileOffset / bufSize) * bufSize ==
                            fileOffset;
                        int newLimit = src.position() + bufSize;
                        if (newLimit > capacity)
                            newLimit = capacity;
                        src.limit(newLimit);
                        //System.err.printf("CFS.OS.write: chunk [%d # %d)%n",
                        //    this.file.position(), src.remaining());
                        //System.err.printf("  data: %s%n", src.asString());
                        int bytesWritten = this.file.write(src);
                        this.file.position(fileOffset + bytesWritten);
                        timer.stamp(String.format("chunk_%d", chunk));
                        chunk++;
                    }
                    if (debug) {
                        //
                        // 1.  overall time
                        // 2.  time to complete writing the first chunk
                        // 3*. times for each successive block size and final
                        //     chunk
                        //
                        timer.printCSV(CelesteFileSystem.profileOutputFileName);
                    }
                }
            } catch (FileException e) {
                String msg = String.format("write to output stream failed: %s",
                    e.getMessage());
                IOException ioe = new IOException(msg, e);
                throw ioe;
            }
        }

        @Override
        public void close() {
            this.isClosed = true;
        }

        /**
         * Get the size of the buffer used for {@link #write(byte[], int, int)
         * write()} calls.
         *
         * @return  the write buffer size
         */
        public int getBufferSize() {
            return this.bufferSize;
        }

        /**
         * <p>
         *
         * Set the size of the buffer used for {@link #write(byte[], int, int)
         * write()} calls.  A value of zero is legal and requests that the
         * file's block size be used for the buffer size.
         *
         * </p><p>
         *
         * For best performance, the buffer size should be a multiple of the
         * file's block size.  Larger multiples consume more temporary heap
         * space but also improve write performance.
         *
         * </p>
         *
         * @param bufferSize    the buffer size to use for writes made through
         *                      this {@code CelesteOutputStream}
         *
         * @throws  IllegalArgumentException
         *          if {@code bufferSize < 0}
         */
        public void setBufferSize(int bufferSize) {
            if (bufferSize < 0)
                throw new IllegalArgumentException(
                    "buffer size must be non-negative");
            this.bufferSize = bufferSize;
        }
    }

    //
    // XXX: Needs a javadoc comment.
    //
    // XXX: The association of name to file is immutable in this class.  So
    //      how are renames to be handled?
    //
    //      Similarly, the implementation of delete [tries to] obliterate the
    //      file.  So how are hard links to be handled?  (The expedient
    //      answer, and expediency seems to be winning, is to forbid hard
    //      links.)
    //
    //      This is all very messy.  A File object captures an underlying
    //      Celeste file (as denoted by a FileImpl object) along with a
    //      snapshot of its state as a citizen of a file system.  That is, the
    //      snapshot captures its name within the file system at the time the
    //      File object is created.  If the file is subsequently renamed (via
    //      CelesteFileSystem.renameFile()), the File object's name becomes
    //      obsolete.  This situation smells strongly of improper factoring.
    //
    //      (The only place where the pathName is used in a nontrivial way is
    //      in the getFile() method of the Directory subclass.  ... Well, no,
    //      it's not that simple.  The CelesteHTTPd client class uses the
    //      pathName value in its directory listing web page.  And now the
    //      WebDAV class does, too.)
    //
    /**
     * <p>
     *
     * The {@code File} class provides access to methods and behavior common
     * to files and directories in a Celeste file system.  The {@link
     * Directory} class specializes {@code File} for directory-specific
     * behavior.
     *
     * </p><p>
     *
     * Each {@code File} instance is roughly analogous to a Unix-style file
     * descriptor.  In particular, each instance maintains an offset into the
     * file it represents that determines the starting point for methods such
     * as {@link #read(ByteBuffer)} and {@link #write(ByteBuffer)}.  This
     * offset position is initialized to {@code 0L} whenever a fresh {@code
     * File} instance is created, such as by way of calling the {@link
     * CelesteFileSystem#getFile(PathName)} method.
     *
     * </p>
     */
    public class File extends FileSystem.File {
        //
        // Note that the path field is set once at creation time and isn't
        // updated thereafter.  Since files are subject to renames, the field
        // is best regarded as a hint.  (Improving this situation would be
        // very difficult, since the rename code would have to track down
        // affected File objects and has no way to do so without the
        // introduction of expensive additional machinery.)
        //
        // XXX: A possible way to fix the problem (includes machinery):
        //
        //      In the superblock directory, maintain a rename count that's
        //      bumped whenever a rename occurs.  Associate with every file
        //      the rename count value that was current the last time its path
        //      field was computed.  Whenever a path is fetched, compare the
        //      file's value with that in the superblock.  If they're not
        //      equal, recompute the path.  (For this technique to be
        //      completely reliable, we would have to prevent updates to the
        //      superblock count while inspecting, using, and updating its
        //      value.  That would force serialization of renames and path
        //      fetches.  So reliability would come at a substantial cost.)
        //
        //      Another possible solution:  Take advantage of not supporting
        //      hard links and store <unique file id of parent dir, leaf name>
        //      as an attribute of each file.  That would allow on-demand path
        //      name computation.  Renames would have to update this
        //      attribute, and the update would have to synchronize (somehow)
        //      with path name computation.
        //
        // XXX: Heads up for work in progress:
        //
        //      There's a desire to support WebDAV-style locks.  Doing so
        //      presents difficulties.  One problem is that a given lock can
        //      be given an "infinite" depth, meaning that it applies to the
        //      given file (actually directory) and all its descendants in the
        //      file system name space.  When acquiring such a lock, one must
        //      check to see whether any of the files in the covered subtree
        //      already has a conflicting lock; if so the acquisition must
        //      fail.  Thus, having a way to track locks by pathname is
        //      desirable.  (Then one can check for locks whose path starts
        //      with a prefix of the one being acquired and examine them for
        //      compatibility.)  But...  Supporting rename means that we must
        //      be able to update the lock tracking information in tandem with
        //      the renamed files themselves.  This approach also requires
        //      that the path name tracking problem described in the XXX above
        //      must be solved.
        //
        // XXX: Need to expose FileImpl.setACL() at this level.
        //
        private PathName path;
        private final FileIdentifier fid;
        private long offset = 0L;

        //
        // A CelesteFileSystem.File object merely acts as a handle for
        // referring to a file.  In particular, having access to a File object
        // does not guarantee that the corresponding file exists.  (In this
        // respect, it's like FileImpl at the next layer down.)
        //
        // The createFile() method produces a File object that _does_ (modulo
        // races) refer to an existent file.
        //
        // XXX; This constructor probably ought to have restricted visibility.
        //      It's not intended for CelesteFileSystem clients to use.
        //
        /**
         * Creates a new reference to {@code file}, recording {@code path} as
         * its name in this file system.  Initializes the file's offset to
         * {@code 0L}.
         *
         * @param path  the name to be recorded for this file
         * @param file  the object to be used to access the file itself
         */
        public File(PathName path, FileImpl file) {
            this.path = path;
            this.fid = file.getFileIdentifier();
        }

        public CelesteFileSystem getFileSystem() {
            return CelesteFileSystem.this;
        }

        public boolean exists()  throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.fileExists();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Tests if this file is a directory (see {@link
         * CelesteFileSystem.Directory}).
         *
         * @return {@code true} if this file is a directory.
         */
        public boolean isDirectory() {
            return (this instanceof CelesteFileSystem.Directory);
        }

        /**
         * Tests if this file is a file (see {@link CelesteFileSystem.File}).
         *
         * @return {@code true} if this file is a normal file.
         */
        public boolean isFile() {
            return (this instanceof CelesteFileSystem.File);
        }

        /**
         * Provided that this {@code File} instance is actually a directory
         * (that has been trapped in file clothing), return a {@code
         * Directory} handle for it.  If not, throw {@code
         * IllegalStateException}
         *
         * @return  a {@code Directory} accessor for this directory
         *
         * @throws IllegalStateException
         *      if this file is not a directory
         */
        public Directory asDirectory() {
            if (!this.isDirectory())
                throw new IllegalStateException("non-directory file");
            return (Directory)this;
        }

        public long length() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getFileLength();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Return the time that this file was created, or {@code 0} if this
         * file does not exist.  The time is reported with respect to the
         * local clock of the entity that created the file.
         *
         * @return  the file's creation time, expressed in milliseconds since
         *          midnight, January 1, 1970 UTC
         */
        public long getCreationTime() {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getCreationTime();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public long lastModified() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getModifiedTime();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Return the time that this file's metadata was last modified.  The
         * time is reported with respect to the local clock of the entity that
         * modified the file.
         *
         * @return  the file's metadata modification time, expressed in
         *          milliseconds since midnight, January 1, 1970 UTC
         */
        public long lastMetadataChanged() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getClientMetadataChangeTime();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        public long serialNumber() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getSerialNumber();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * <p>
         *
         * Returns the properties associated with this file, in the form of
         * an {@code OrderedProperties} map containing {@code String} names
         * and corresponding String values.
         *
         * </p><p>
         *
         * (A file property is a string-valued item associated with a given
         * file that the file system implementation stores with the file and
         * retrieves on request, but otherwise does not use or interpret.)
         *
         * </p>
         *
         * @return  this file's properties
         *
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.IOException
         * @throws FileException.NotFound
         * @throws FileException.Runtime
         */
        public OrderedProperties getClientProperties() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                return fileImpl.getClientProperties();
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        /**
         * <p>
         *
         * Replaces any existing properties associated with this file with the
         * ones contained in {@code clientProperties}, creating a new version
         * of the file that differs from the previous version only in the
         * properties and in the {@link
         * FileAttributes.Names#METADATA_CHANGED_TIME_NAME metadata changed
         * time} attribute.
         *
         * </p><p>
         *
         * (A file property is a string-valued item associated with a given
         * file that the file system implementation stores with the file and
         * retrieves on request, but otherwise does not use or interpret.)
         *
         * </p>
         *
         * @param clientProperties  the properties that are to replace the
         *                          file's existing properties
         *
         * @throws FileException.CredentialProblem
         *      if the credential supplied when this file's {@code
         *      CelesteFileSystem} handle was created couldn't be successfully
         *      used to authorize the file update
         */
        public void setClientProperties(OrderedProperties clientProperties)
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                fileImpl.setClientProperties(clientProperties,
                    fs.invokerProfile, fs.getInvokerPassword());
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public InternetMediaType getContentType() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            final FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                return InternetMediaType.getInstance(fileImpl.getContentType());
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public void setContentType(InternetMediaType type) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteInaccessible,
                FileException.CelesteFailed,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            final FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                fileImpl.setContentType(fs.invokerProfile,
                    fs.getInvokerPassword(), null, type.toString());
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * <p>
         *
         * Sets the value of the deletion time to live attribute for this file
         * to {@code duration} seconds.
         *
         * </p><p>
         *
         * Use the {@link #getAttributes(Set) getAttributes()} method to
         * obtain the value of the deletion time to live attribute.
         *
         * </p>
         *
         * @param duration  the time in seconds a record of this file's
         *                  deletion should persist in the file store after
         *                  the file has been deleted
         */
        public void setDeletionTimeToLive(long duration) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteInaccessible,
                FileException.CelesteFailed,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            final FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                fileImpl.setDeletionTimeToLive(fs.invokerProfile,
                    fs.getInvokerPassword(), null, duration);
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Provided that this {@code File} instance exists and represents a
         * file (as opposed to a directory}, return a {@code
         * CelesteInputStream} for accessing its contents.
         *
         * @return  a {@code CelesteInputStream} allowing access to this file's
         *          contents
         *
         * @throws FileException.NotFound
         *         if this file does not exist
         * @throws FileException.IsDirectory
         *         if this file is actually a directory
         */
        public CelesteInputStream getInputStream() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            return new CelesteFileSystem.CelesteInputStream(this);
        }

        /**
         * Provided that this {@code File} instance exists and represents a
         * file (as opposed to a directory}, return a {@code
         * CelesteOutputStream} that can be used to write to this file, from
         * its current position onward.
         *
         * @return  a {@code CelesteOutputStream} that can be used to write to
         *          this file
         *
         * @throws FileException.NotFound
         *         if this file does not exist
         * @throws FileException.IsDirectory
         *         if this file is actually a directory
         *
         * @see #getOutputStream(boolean)
         */
        public CelesteOutputStream getOutputStream() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed,
                IOException {
            return this.getOutputStream(false);
        }

        /**
         * Returns an {@code OutputStream} that can be used to write to this
         * file.  If {@code truncate} is {@code true}, the file is truncated
         * before being wrapped with the resultant output stream,
         *
         * @code truncate   {@code true} if the file is to be truncated before
         *                  becoming accessible through the returned output
         *                  stream, and {@code false} otherwise
         *
         * @return  a {@code CelesteOutputStream} that can be used to write to
         *          this file
         *
         * @throws FileException.NotFound
         *         if this file does not exist
         * @throws FileException.IsDirectory
         *         if this file is actually a directory
         */
        public CelesteOutputStream getOutputStream(boolean truncate) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed,
                IOException {
            return new CelesteFileSystem.CelesteOutputStream(this, truncate);
        }

        public int getBlockSize() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                final int blockSize = fileImpl.getPreferredBlockSize(false);
                return blockSize;
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Returns this file's access control list.
         *
         * @return  this file's access control list
         */
        public CelesteACL getACL() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getACL();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Replaces this file's access control list with {@code acl}, creating
         * a new version of the file that differs from the previous version
         * only in the access control list and in the {@link
         * FileAttributes.Names#METADATA_CHANGED_TIME_NAME metadata changed
         * time} attribute.
         *
         * @param acl   the new access control list for this file
         */
        public void setACL(CelesteACL acl)
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                fileImpl.setACL(fs.invokerProfile, fs.getInvokerPassword(),
                    null, acl);
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        /**
         * <p>
         *
         * Return the path name this {@code File} object's underlying file had
         * at the time this object was created.
         *
         * </p><p>
         *
         * Caution:  If the file is renamed, this method will still yield the
         * original name.
         *
         * </p>
         */
        public PathName getPathName() {
            return this.path;
        }

        /**
         * Returns a map containing values for each of the attributes
         * contained in {@code attributes} that appear in {@link
         * FileAttributes#attributeSet}.  If the {@code attributes} parameter
         * is {@code null}, the resulting map will contain entries for all
         * fetchable attributes named in {@link FileAttributes#attributeSet}.
         *
         * @param attributes    a set of names of attributes whose values are
         *                      to be fetched; if {@code null}, all attributes
         *                      are to be fetched
         *
         * @return  an {@code OrderedProperties} map containing entries for
         *          the desired attributes
         */
        public OrderedProperties getAttributes(Set<String> attributes) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            if (attributes == null)
                attributes = FileAttributes.attributeSet;
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                return fileImpl.getAttributes(attributes, false);
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public String toString() {
            return this.path.toString();
        }

        //
        // Methods for reporting various statistics.
        //
        // For now, statistics consist solely of those reported by FileImpl.
        // They could probably be augmented, at least at the aggregate level,
        // with information on numbers of files in play, and so on.
        //

        /**
         * Return an XHTML table containing performance statistics for
         * accesses to this file.
         *
         * @return  this file's performance statistics, as an XHTML table
         */
        public XHTML.Table getStatisticsAsXHTML() {
            FileImpl fileImpl = CelesteFileSystem.this.getAndRemove(this.fid);
            try {
                return fileImpl.getStatisticsAsXHTML();
            } finally {
                CelesteFileSystem.this.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Return an XHTML table containing performance statistics for
         * accesses to all files mediated by this instantiation of {@code
         * CelesteFileSystem.File}.
         *
         * @return  aggregate file access statistics, as an XHTML table
         */
        //
        // XXX: Logically a static method, but since it belongs to an inner
        //      class, it can't be.
        //
        public XHTML.Table getAggregateStatisticsAsXHTML() {
            return FileImpl.getAggregateStatisticsAsXHTML();
        }

        //
        // FileChannel methods
        //
        // XXX: Reconsider whether or not this class should extend
        //      FileChannel.
        //

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long size() throws IOException {
            //
            // Impedance matching:  The superclass method throws IOException,
            // so FileExceptions must be wrapped or unpacked as needed.
            //
            try {
                return this.length();
            } catch (FileException.BadVersion e) {
                throw new IOException(e);
            } catch (FileException.CelesteFailed e) {
                throw new IOException(e);
            } catch (FileException.CelesteInaccessible e) {
                throw new IOException(e);
            } catch (FileException.CredentialProblem e) {
                throw new IOException(e);
            } catch (FileException.Deleted e) {
                throw new IOException(e);
            } catch (FileException.DirectoryCorrupted e) {
                throw new IOException(e);
            } catch (FileException.IOException e) {
                throw (IOException)e.getCause();
            } catch (FileException.NotFound e) {
                throw new IOException(e);
            } catch (FileException.PermissionDenied e) {
                throw new IOException(e);
            } catch (FileException.Runtime e) {
                throw new IOException(e);
            } catch (FileException.ValidationFailed e) {
                throw new IOException(e);
            }
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            synchronized (this) {
                int bytesRead = this.read(dst, this.offset);
                this.offset += bytesRead;
                return bytesRead;
            }
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                synchronized (this) {
                    int totalBytesRead = 0;
                    int bytesRead = 0;
                    do {
                        //
                        // Accommodate short reads by pressing on until the
                        // buffer's filled (or there's no more to read).
                        //
                        bytesRead = fileImpl.read(fs.invokerProfile,
                            fs.getInvokerPassword(), dst, position);
                        totalBytesRead += bytesRead;
                        position += bytesRead;
                    } while (dst.remaining() > 0 && bytesRead > 0);
                    return totalBytesRead;
                }
            } catch (FileException e) {
                IOException ioe = new IOException("read failed", e);
                throw ioe;
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public FileChannel position(long newPosition) {
            synchronized (this) {
                this.offset = newPosition;
            }
            return this;
        }

        @Override
        public long position() {
            synchronized (this) {
                return this.offset;
            }
        }

        @Override
        public MappedByteBuffer map(FileChannel.MapMode mode, long position, long size) {
            return null;
        }

        @Override
        public void force(boolean metaData) {
            //
        }

        //
        // XXX: Ought to support this method...  To do...
        //
        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            synchronized (this) {
                //
                // Use the two argument write method to do the write.  Since
                // that method has pwrite-like behavior, update the offset
                // explicitly after the write succeeds.
                //
                int writeLen = this.write(src, this.offset);
                this.offset += writeLen;
                return writeLen;
            }
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            try {
                return this.write(new ExtentBuffer(position, src));
            } catch (FileException e) {
                String msg = String.format("write failed: %s", e.getMessage());
                IOException ioe = new IOException(msg, e);
                throw ioe;
            }
        }

        public int write(ExtentBuffer src) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.IsDirectory,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            //
            // XXX: Profiling/Debugging support.
            //
            boolean debug = CelesteFileSystem.profileConfig.contains(
                "CelesteFileSystem.File.write");
            TimeProfiler timer = new TimeProfiler(
                "CelesteFileSystem.File.write");

            final CelesteFileSystem fs = CelesteFileSystem.this;
            synchronized (this) {
                int writeLen = src.remaining();
                FileImpl fileImpl = fs.getAndRemove(this.fid);
                try {
                    timer.stamp("slop");
                    fileImpl.write(src, fs.invokerProfile,
                        fs.getInvokerPassword(), null);
                    //
                    // The write succeeded; therefore, the span of src between
                    // its position and limit has been consumed.  Update src
                    // accordingly.
                    //
                    src.position(src.limit());
                    //System.err.printf("File.write: contents now: %s%n",
                    //    FileImpl.dumpFile(fileImpl, fs.invokerProfile,
                    //        fs.getInvokerPassword()));
                    timer.stamp("write_seek");
                } finally {
                    fs.addAndEvictOld(fileImpl);
                }

                if (debug) {
                    //
                    // 1.  overall time
                    // 2.  slop to get to the write
                    // 3.  time to write and reposition the file
                    //
                    timer.printCSV(CelesteFileSystem.profileOutputFileName);
                }

                return writeLen;
            }
        }

        public Serializable runExtension(URL[] jarFileURLs, String[] args) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed,
                IOException {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                return fileImpl.runExtension(
                    fs.invokerProfile, fs.getInvokerPassword(), jarFileURLs, args);
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = fs.getAndRemove(this.fid);
            try {
                fileImpl.truncate(size, fs.invokerProfile, fs.getInvokerPassword());
            } catch (FileException e) {
                IOException ioe = new IOException("truncate failed", e);
                throw ioe;
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
            return this;
        }

        //
        // It's tempting to provide WebDAV-style locks by implementing the
        // following two methods.  But the specification of java.nio.FileLock
        // has requirements that don't mesh with those of WebDAV locking.
        //

        @Override
        public FileLock tryLock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException();
        }

        //
        // FileChannel inherits this method from AbstractInterruptibleChannel,
        // but leaves it abstract.  Since the FileImpl object that holds state
        // for this file's interaction with Celeste is obtained and released
        // whenever needed, there's nothing to do here.  (In an alternative
        // design implemented to latch onto the FileImpl for the lifetime of
        // this File object, one would release it here.)
        //
        @Override
        public void implCloseChannel() throws IOException {
            //
        }

        //
        // Other methods
        //

        /**
         * Return this file's {@code FileIdentifier}, its identifier within
         * the underlying Celeste file store.
         *
         * @return this file's file identifier
         */
        public FileIdentifier getFileIdentifier() {
            return this.fid;
        }

        /**
         * Acquire the modification lock on this file, thereby preventing all
         * invocations of operations that modify this file except ones issued
         * by the entity specified when constructing the {@code
         * CelesteFileSystem} instance associated with this file.
         */
        public void acquireModificationLock()
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteInaccessible,
                FileException.CelesteFailed,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = null;
            try {
                fileImpl = fs.getAndRemove(this.fid);
                fileImpl.acquireModificationLock(
                    fs.invokerProfile, fs.getInvokerPassword(), null, null);
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Relinquish the modification lock on this file, thereby allowing
         * unrestricted invocations of operations that modify this file.
         */
        public void releaseModificationLock()
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteInaccessible,
                FileException.CelesteFailed,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.Locked,
                FileException.NotFound,
                FileException.NotLocked,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            FileImpl fileImpl = null;
            try {
                fileImpl = fs.getAndRemove(this.fid);
                fileImpl.releaseModificationLock(
                    fs.invokerProfile, fs.getInvokerPassword(), null);
            } finally {
                fs.addAndEvictOld(fileImpl);
            }
        }

        //
        // Two File objects are equal if they refer to equal FileIds.
        //
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CelesteFileSystem.File))
                return false;
            CelesteFileSystem.File file = (CelesteFileSystem.File)other;
            return this.fid.equals(file.fid);
        }

        @Override
        public int hashCode() {
            return this.fid.hashCode();
        }
    }

    //
    // XXX: Should this class _really_ extend CelesteFileSystem.File?  A nasty
    //      ontological question...  (They both probably ought to extend
    //      something that provides their common behavior.)
    //
    public class Directory extends CelesteFileSystem.File
            implements HierarchicalFileSystem.Directory {

        private final DirectoryImpl directoryImpl;

        public Directory(PathName path, DirectoryImpl directory) {
            //
            // XXX: Find a way to avoid needing to dig out the DirectoryImpl's
            //      internal FileImpl; it's really none of our business here.
            //
            super(path, directory.getFileImpl());
            this.directoryImpl = directory;
        }

        @Override
        public long size() throws IOException {
            //
            // Impedance matching:  The superclass method throws IOException,
            // so FileExceptions must be wrapped or unpacked as needed.
            //
            try {
                return this.length();
            } catch (FileException.BadVersion e) {
                throw new IOException(e);
            } catch (FileException.CelesteFailed e) {
                throw new IOException(e);
            } catch (FileException.CelesteInaccessible e) {
                throw new IOException(e);
            } catch (FileException.CredentialProblem e) {
                throw new IOException(e);
            } catch (FileException.Deleted e) {
                throw new IOException(e);
            } catch (FileException.DirectoryCorrupted e) {
                throw new IOException(e);
            } catch (FileException.IOException e) {
                throw (IOException)e.getCause();
            } catch (FileException.NotFound e) {
                throw new IOException(e);
            } catch (FileException.PermissionDenied e) {
                throw new IOException(e);
            } catch (FileException.Runtime e) {
                throw new IOException(e);
            } catch (FileException.ValidationFailed e) {
                throw new IOException(e);
            }
        }

        @Override
        public long length() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed{
            return this.directoryImpl.getAllNames(
                CelesteFileSystem.this.invokerProfile, CelesteFileSystem.this.invokerPassword).size();
        }

        //
        // This *may* return purged files, or files that were marked as
        // deleted.  Instead of saying "may", let's enumerate the conditions
        // that it will return purged files or files that were marked as
        // deleted.  More information may provide inspiration which will be
        // able to solve these inconsistencies later.  It *will* return
        // invalid directory entries under the following conditions:
        //
        // - The file was linked to in another directory, and was purged or
        //   marked as deleted there
        // - The file itself was purged or marked as deleted, but
        //   - The user doing that change was not permitted to change this
        //     directory accordingly
        //   - The user doing that change failed to change the directory
        // - The file was lost in Celeste
        //   - due to the Anchor Object disappearing (file not found error)
        //   - due to the Version Object disappearing (meta-data not retrievable)
        // - This is a cached directory, and it was not made aware of the
        //   changes occurring since it was instantiated
        //
        public String[] list() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            SortedSet<String> names = this.directoryImpl.getAllNames(
                fs.invokerProfile, fs.invokerPassword);
            return names.toArray(new String[names.size()]);
        }

        /**
         * Return an array of directory entries, ordered by their leaf name
         * fields, representing every file in this directory, including {@code
         * "."} and {@code ".."}.
         *
         * @return a sorted array of directory entries for the files in this
         *          directory
         *
         * @throws FileException.BadVersion
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.CredentialProblem
         * @throws FileException.Deleted
         * @throws FileException.DirectoryCorrupted
         *         if this directory's contents have been corrupted
         * @throws FileException.IOException
         * @throws FileException.NotFound
         * @throws FileException.PermissionDenied
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         */
        //
        // XXX: Doesn't scale to large directories.
        //
        public Dirent[] getDirents() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            SortedSet<Dirent> dirents = this.directoryImpl.getDirents(
                fs.invokerProfile, fs.invokerPassword);
            return dirents.toArray(new Dirent[dirents.size()]);
        }

        /**
         * Look up and return the file or directory whose entry in this
         * directory has key {@code leafName}.
         *
         * @throws FileException.CelesteInaccessible
         * @throws FileException.CredentialProblem
         * @throws FileException.Deleted
         * @throws FileException.DirectoryCorrupted
         *         if this directoy's contents have been corrupted
         * @throws FileException.IOException
         *         if this directory could not be read or a file named within
         *         it could not be inspected
         * @throws FileException.InvalidName
         *         if {@code leafName} is syntactically invalid (contains a '/'
         *         character)
         * @throws FileException.NotFound
         *         if this directory contains no entry for {@code leafName}
         * @throws FileException.PermissionDenied
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         */
        //
        // XXX: This method should not have public visibility.  Exposing it
        //      significantly complicates keeping the File.path field accurate
        //      and precludes implementing CelesteFileSystem methods that
        //      use path names from using a non-DirectoryImpl-based naming
        //      scheme.
        //
        public CelesteFileSystem.File getFile(String leafName) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.InvalidName,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            final CelesteFileSystem fs = CelesteFileSystem.this;
            if (leafName.contains("/")) {
                throw new FileException.InvalidName(
                    String.format("Invalid '/' character in %s", leafName));
            }
            FileImpl f = null;
            try {
                FileIdentifier fid = this.directoryImpl.getFileIdentifier(
                    fs.invokerProfile, fs.invokerPassword, leafName);
                PathName path = new PathName(String.format("%s%s%s",
                    this.getPathName(), "/", leafName));
                f = fs.getAndRemove(fid);
                if (DirectoryImpl.isDirectory(f)) {
                    return new CelesteFileSystem.Directory(path,
                        new DirectoryImpl(f));
                }
                return new CelesteFileSystem.File(path, f);
            } finally {
                fs.addAndEvictOld(f);
            }
        }

        @Override
        public OrderedProperties getAttributes(Set<String> attributes) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
            if (attributes == null)
                attributes = FileAttributes.attributeSet;
            return this.directoryImpl.getAttributes(attributes, /*false*/true);
        }

        //
        // Overrides of CelesteFileSystem.File methods that are inappropriate
        // for directories.  They all throw FileException.IsDirectory, wrapped
        // with an IOException where interface conformance demands it.
        //

        @Override
        public CelesteInputStream getInputStream() throws
                FileException.IsDirectory {
            throw new FileException.IsDirectory();
        }

        @Override
        public CelesteOutputStream getOutputStream() throws FileException.IsDirectory {
            throw new FileException.IsDirectory();
        }

        @Override
        public CelesteOutputStream getOutputStream(boolean truncate) throws
                FileException.IsDirectory {
            throw new FileException.IsDirectory();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            throwWrappedIsDirectoryException();
            return -1;
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            throwWrappedIsDirectoryException();
            return -1;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throwWrappedIsDirectoryException();
            return -1;
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            throwWrappedIsDirectoryException();
            return -1;
        }

        @Override
        public int write(ExtentBuffer src) throws FileException.IsDirectory {
            throw new FileException.IsDirectory();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            throwWrappedIsDirectoryException();
            return null;
        }

        private void throwWrappedIsDirectoryException() throws IOException {
            Throwable inner = new FileException.IsDirectory();
            IOException ioe = new IOException(inner);
            throw ioe;
        }
    }

    /**
     * Given a file extension (without the extension separator), return the
     * best-guess content type.
     *
     * @param extension the file extension to use to determine content type
     *
     * @return  the file's content type
     */
    public static InternetMediaType commonFileExtension(String extension) {
        InternetMediaType type = CelesteFileSystem.mimeTypes.get(extension);
        return type == null ? InternetMediaType.Application.OctetStream : type;
    }

    //
    // XXX: Do we want to make this table extensible with new mime types?
    //
    private final static HashMap<String, InternetMediaType> mimeTypes =
        new HashMap<String, InternetMediaType>();
    static {
        CelesteFileSystem.mimeTypes.put("txt", InternetMediaType.Text.Plain);
        CelesteFileSystem.mimeTypes.put("text", InternetMediaType.Text.Plain);
        CelesteFileSystem.mimeTypes.put("htm", InternetMediaType.Text.HTML);
        CelesteFileSystem.mimeTypes.put("html", InternetMediaType.Text.HTML);
        CelesteFileSystem.mimeTypes.put("gif", InternetMediaType.Image.Gif);
        CelesteFileSystem.mimeTypes.put("jpg", InternetMediaType.Image.Jpeg);
        CelesteFileSystem.mimeTypes.put("jpeg", InternetMediaType.Image.Jpeg);
        CelesteFileSystem.mimeTypes.put("mp3", InternetMediaType.Audio.Mpeg);
        CelesteFileSystem.mimeTypes.put("mp4", InternetMediaType.Video.Mp4);
        CelesteFileSystem.mimeTypes.put("m3u", InternetMediaType.Audio.XMpegUrl);
        CelesteFileSystem.mimeTypes.put("wma", InternetMediaType.Audio.XMsWma);
        CelesteFileSystem.mimeTypes.put("wax", InternetMediaType.Audio.XMsWax);
        CelesteFileSystem.mimeTypes.put("asf", InternetMediaType.Video.XMsAsf);
        CelesteFileSystem.mimeTypes.put("asx", InternetMediaType.Video.XMsAsx);
        CelesteFileSystem.mimeTypes.put("wmv", InternetMediaType.Video.XMsWmv);
        CelesteFileSystem.mimeTypes.put("wvx", InternetMediaType.Video.XMsWvx);
        CelesteFileSystem.mimeTypes.put("wmx", InternetMediaType.Video.XMsWmx);
//        CelesteFileSystem.mimeTypes.put("ogm", ContentType.Application.Ogm);
        CelesteFileSystem.mimeTypes.put("ogg", InternetMediaType.Application.Ogg);

        CelesteFileSystem.mimeTypes.put("mpg", InternetMediaType.Video.Mpeg);
        CelesteFileSystem.mimeTypes.put("mpeg", InternetMediaType.Video.Mpeg);
        CelesteFileSystem.mimeTypes.put("vob", InternetMediaType.Video.Mpeg);
        CelesteFileSystem.mimeTypes.put("avi", InternetMediaType.Video.AVI);
    }

    /**
     * Get a fresh reference to an existing file in the file system.
     *
     * @param path  a path naming the file to be retrieved
     *
     * @return  a new {@code File} instance for accessing the file named by
     *          {@code path}
     *
     * @throws FileException.BadVersion
     *         if one or more components of {@code path} refers to a file or
     *         directory with metadata encoded in a format that the file
     *         system implementation does not understand
     * @throws FileException.Deleted
     *         if one or more components of {@code path} refers to a file or
     *         directory that has been deleted
     * @throws FileException.FileSystemNotFound
     *         if this file system cannot be found, for example, because it
     *         has not yet been created
     * @throws FileException.NotDirectory
     *         if one or more components of the basename portion of {@code path}
     *         is not a directory
     * @throws FileException.NotFound
     *         if one or more components of {@code path} do not exist
     * @throws FileException.PermissionDenied
     *         if the caller is denied read access to one or more components
     *         of {@code path}
     * @throws FileException.ValidationFailed
     */
    //
    // XXX: Modify to return null if path names a nonexistent file?
    //
    public CelesteFileSystem.File getFile(PathName path) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        FileImpl f = null;
        try {
            DirectoryImpl parent = traverseToParent(path);
            if (parent == null)
                throw new FileException.NotFound(path.trimLeafComponent());
            String leaf = path.getLeafComponent();
            if (leaf.equals("/"))
                return new CelesteFileSystem.Directory(path, parent);
            FileIdentifier fid = parent.getFileIdentifier(
                this.invokerProfile, this.invokerPassword, leaf);
            if (fid == null)
                throw new FileException.NotFound(path);
            f = this.getAndRemove(fid);
            if (DirectoryImpl.isDirectory(f)) {
                return new CelesteFileSystem.Directory(
                    path, new DirectoryImpl(f));
            }
            return new CelesteFileSystem.File(path, f);
        } finally {
            this.addAndEvictOld(f);
        }
    }

    //
    // XXX: Need variant of createFile() that takes a boolean "exclusive"
    //      argument that, when set, forces the create to fail when a file of
    //      the given name already exists, but, when unset, allows the
    //      creation (overwriting the existing entry).
    //
    // XXX: Now that we have a general version of createFile() that uses
    //      OrderedProperties to specify the initial file attributes, we
    //      should consider phasing the other variants out in its favor.
    //
    // XXX: To phase out variants that don't explicitly mention the contentType
    //      argument, we'll need to provide another way to say "make your best
    //      guess about content type".  A possible approach is to let FileImpl
    //      handle a completely omitted content type attribute (which would
    //      set it to "application/octet-stream"), and add "?" as a value for
    //      the attribute that we explicitly check for.  The new "?" value
    //      would trigger the commonFileExtension() behavior that the single
    //      argument variant of create() currently implements.  Or finally, we
    //      could interpret an empty or null string value for the attribute as
    //      denoting these semantics.
    //

    public CelesteFileSystem.File createFile(PathName path) throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        String extension = path.getExtension();
        String contentType =
            CelesteFileSystem.commonFileExtension(extension).toString();
        return this.createFile(path, contentType);
    }

    public CelesteFileSystem.File createFile(PathName path,
            OrderedProperties clientProps)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        String extension = path.getExtension();
        String contentType =
            CelesteFileSystem.commonFileExtension(extension).toString();
        return this.createFile(path, contentType, clientProps);
    }

    //
    // XXX: It would be good to have the contentType symmetrical with the
    //      value of getContentType().
    //
    //      It might be best to take an InternetMediaType here.
    //
    public CelesteFileSystem.File createFile(PathName path, String contentType)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.createFile(path, contentType, null);
    }

    public CelesteFileSystem.File createFile(PathName path, String contentType,
            OrderedProperties clientProps)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        OrderedProperties attrs = new OrderedProperties();
        attrs.setProperty(CONTENT_TYPE_NAME, contentType);
        return this.createFile(path, attrs, clientProps);
    }

    /**
     * Create the file designated by {@code path}, taking its initial
     * attributes from those supplied in {@code fileAttributes}, and
     * associating the client-supplied metadata from {@code clientProps} with
     * the file.
     *
     * @param path              a path name leading to the file
     * @param fileAttributes    initial attributes for the file, which will be
     *                          restricted to the set denoted by {@link
     *                          FileAttributes.WhenSettable#atCreation} and
     *                          augmented with the default attributes
     *                          specified when the file system was created
     * @param clientProps       client-supplied properties to be associated with
     *                          the file, or {@code null} if there are none
     */
    //
    // XXX: Try do eliminate FileException.Deleted from this method's
    //      signature.  There's a start at it below, but there's more work to
    //      do.  And getting rid of it from calls to traverseToParent() et al
    //      is likely to be difficult...
    //
    public CelesteFileSystem.File createFile(PathName path,
            OrderedProperties fileAttributes,
            OrderedProperties clientProps)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // 1) Check if the parent exists, and does not contain this leaf name.
        // 2) Make a random new file, and create it in Celeste.
        // 3) Add the corresponding directory entry.
        //
        // XXX: Race avoidance?  (The code below does nothing more than hope
        //      they don't occur.)
        //

        //
        // XXX: Profiling/Debugging support.
        //
        boolean debug = CelesteFileSystem.profileConfig.contains(
            "CelesteFileSystem.createFile");
        TimeProfiler timer = new TimeProfiler("CelesteFileSystem.createFile");

        FileImpl leafFile = null;
        try {
            DirectoryImpl parent = traverseToParent(path);
            String leaf = path.getLeafComponent();
            timer.stamp("lookup");
            try {
                FileIdentifier fid = parent.getFileIdentifier(
                    this.invokerProfile, this.invokerPassword, leaf);
                timer.stamp("getfid");
                if (fid != null) {
                    leafFile = this.getAndRemove(fid);
                    timer.stamp("get_file");
                }
            } catch (FileException.NotFound keepGoing) {
                //
                // This is the normal case; the file is not supposed to exist.
                //
                // XXX: This normal case is expensive, in the 310 - 410 msec
                //      range when run on ivrel as part of directoryTest.
                //      (Note that we're very likely seeing interpreted rather
                //      than compiled code in this setting, since the test
                //      fires up a fresh JVM for each file created.)
                //
                timer.stamp("get_file_ex");
            }
            if (leafFile != null) {
                timer.stamp("cfs_slop1");
                //
                // Maybe somebody deleted the file, but was unable to remove
                // the directory entry?
                //
                if (!leafFile.fileExists())
                    parent.unlink(leafFile, this.invokerProfile, this.getInvokerPassword());
                //
                // ...  or, even worse, somebody just marked the file as
                // deleted, and did not unlink it from here?
                //
                else if (leafFile.getIsDeleted())
                    parent.unlink(leafFile, this.invokerProfile, this.getInvokerPassword());
                else
                    throw new FileException.Exists();
                //
                // We're now done with (this version of) leafFile.  (The
                // finally clause below will clean things up if an exception
                // was thrown above.)
                //
                this.addAndEvictOld(leafFile);
                leafFile = null;
                timer.stamp("unlink_existing");
            }
            //
            // The directory slot that will contain this file is (now)
            // unoccupied, so it's safe to create the file.
            //
            // XXX: Race point:  Nothing prevents the slot from being
            //      (re-)occupied before the code below fills it in.  Losing
            //      the race is mostly indistinguishable from the slot being
            //      occupied in the first place, except that it results in an
            //      orphaned file (the one created below).
            //

            timer.stamp("cfs_slop2");

            // note that leafFile is re-created with a different object id
            leafFile = this.getFreshFileImpl();
            //
            // XXX: If the create fails, ought to recycle the serial #.
            //

            BeehiveObjectId thisGroupId = BeehiveObjectId.ZERO;

            final long newSerialNumber = this.getUnusedSerialNumber();

            //
            // Discard attributes that can't be set at creation time by client
            // code and add in defaults for ones that the client hasn't
            // specified.  Then augment them with ones that CelesteFileSystem
            // owns.
            //
            timer.stamp("serial#");
            OrderedProperties attrs =
                this.getCreationAttributes(fileAttributes);
            attrs.setProperty(FILE_SERIAL_NUMBER_NAME,
                Long.toString(newSerialNumber));

            //
            // Create the file and link it into place.
            //
            timer.stamp("props_setup");
            try {
                leafFile.create(attrs, clientProps,  this.invokerProfile,
                    this.getInvokerPassword(), thisGroupId);
            } catch (FileException.Deleted e) {
                //
                // Since we create a random fresh FileIdentifier for the file,
                // it's exceedingly unlikely we'll pick one that matches a
                // deleted file, and even more unlikely that it will happen
                // twice in a row.  So retry from the beginning.
                //
                return this.createFile(path, fileAttributes, clientProps);
            }
            timer.stamp("create");
            parent.link(leaf, leafFile, this.invokerProfile,
                this.getInvokerPassword());
            timer.stamp("link");
            if (debug) {
                //
                // 1.  overall
                // 2.  lookup
                // 3.  get_and_remove (leafFile)
                // 4.  slop
                // 5.  serial number
                // 6.  attribute setup, including filtering
                // 7.  create (call to FileImpl.create())
                // 8.  link
                //
                timer.printCSV(CelesteFileSystem.profileOutputFileName);
            }
            return new CelesteFileSystem.File(path, leafFile);
        } finally {
            this.addAndEvictOld(leafFile);
        }
    }

    /**
     * Remove the file named by {@code path} from the file system name space.
     * If this file system does not support links (as indicated by the value
     * of its {@code MaintainSerialNumbers} attribute), delete the file as
     * well.
     *
     * @param path  a path name denoting the file to be removed
     */
    //
    // XXX: Using the MaintainSerialNumbers attribute to determine whether or
    //      not the file should be deleted is a half measure.  There should be
    //      an explicit SupportHardLinks attribute and associated machinery
    //      for maintaining link counts.  Only when that attribute is not set
    //      or the link count drops to zero should the file be deleted.
    //
    //      But until that attribute and corresponding link support is added,
    //      this technique suffices.
    //
    public void deleteFile(PathName path) throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.IsDirectory,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.deleteFile(path, !this.isMaintainSerialNumbers());
    }

    /**
     * Remove the file named by {@code path} from the file system name space.
     * If {@code purge} is {@code true}, delete the file as well.
     *
     * @param path  a path name denoting the file to be removed
     *
     * @throws FileException.CapacityExceeded
     * @throws FileException.FileSystemNotFound
     * @throws FileException.IOException
     * @throws FileException.NotDirectory
     * @throws FileException.NotFound
     */
    //
    // XXX: This method should eventually become private.  The file system
    //      implementation should be responsible for determining whether or
    //      not to purge the file.  (See the XXX preceding the single argument
    //      version of this method.)
    //
    public void deleteFile(PathName path, boolean purge) throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.IsDirectory,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        FileImpl leafFile = null;
        try {
            DirectoryImpl parent = traverseToParent(path);
            String leaf = path.getLeafComponent();
            FileIdentifier fid = parent.getFileIdentifier(
                this.invokerProfile, this.invokerPassword, leaf);
            if (fid == null)
                throw new FileException.NotFound();
            leafFile = this.getAndRemove(fid);
            if (DirectoryImpl.isDirectory(leafFile))
                throw new FileException.IsDirectory();
            parent.unlink(leaf, this.invokerProfile, this.getInvokerPassword());
            if (purge) {
                if (leafFile != null) {
                    try {
                        //System.err.println("purging forever");
                        leafFile.purgeForever(this.invokerProfile, this.getInvokerPassword());
                    } catch (FileException e) {
                        //
                        // XXX: Does this buy anything worth having?  Marking a
                        //      file as deleted is a crock.  Either it should
                        //      exist or not; there should be no twilight zone
                        //      states.
                        //
                        leafFile.markDeleted(this.invokerProfile, this.getInvokerPassword());
                    }
                }
            }
        } finally {
            this.addAndEvictOld(leafFile);
        }
    }

    /**
     * Creates a new directory at the location named by {@code path}.
     *
     * @param path              the path name of the new directory
     *
     * @return  a newly created directory
     *
     * @throws FileException.CredentialProblem
     *      if some problem occurs in accessing or using the credential
     *      associated with the caller
     * @throws FileException.Exists
     *      if a file or directory already exists at {@code path}
     */
    public CelesteFileSystem.Directory createDirectory(PathName path)
        throws
            FileException.BadVersion,
            FileException.Exists,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.createDirectory(path, null);
    }

    /**
     * Creates a new directory at the location named by {@code path},
     * associating the properties contained in {@code clientProperties} with
     * the new directory.
     *
     * @param path              the path name of the new directory
     * @param clientProperties    properties to be associated with the new
     *                          directory
     *
     * @return  a newly created directory
     *
     * @throws FileException.CredentialProblem
     *      if some problem occurs in accessing or using the credential
     *      associated with the caller
     * @throws FileException.Exists
     *      if a file or directory already exists at {@code path}
     */
    public CelesteFileSystem.Directory createDirectory(PathName path,
            OrderedProperties clientProperties)
        throws
            FileException.BadVersion,
            FileException.Exists,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        OrderedProperties attrs = new OrderedProperties();
        attrs.setProperty(FILE_SERIAL_NUMBER_NAME, Long.toString(
            this.getUnusedSerialNumber()));
        return this.createDirectory(path, attrs, clientProperties);
    }

    /**
     * Creates a new directory at the location named by {@code path},
     * associating the properties contained in {@code clientProperties} with
     * the new directory and setting the attributes contained in {@code
     * directoryAttributes} on the directory.
     *
     * @param path                  the path name of the new directory
     * @param directoryAttributes   initial attributes for the directory,
     *                              which will be restricted to the set
     *                              denoted by {@link
     *                              FileAttributes.WhenSettable#atCreation}
     * @param clientProperties      properties to be associated with the new
     *                              directory
     *
     * @return  a newly created directory
     *
     * @throws FileException.CredentialProblem
     *      if some problem occurs in accessing or using the credential
     *      associated with the caller
     * @throws FileException.Exists
     *      if a file or directory already exists at {@code path}
     */
    public CelesteFileSystem.Directory createDirectory(PathName path,
            OrderedProperties directoryAttributes,
            OrderedProperties clientProperties)
        throws
            FileException.BadVersion,
            FileException.Exists,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        FileImpl leafFile = null;
        try {
            DirectoryImpl parent = traverseToParent(path);
            //
            // XXX: Debug.
            //
            //      It seems to be possible to exercise CelesteFileSystem in
            //      unintended ways that result in violations of class
            //      invariants.  Check for one of those here.
            //
            if (parent == null)
                throw new IllegalStateException(String.format(
                    "%s has no parent directory!", path));

            String leaf = path.getLeafComponent();
            try {
                FileIdentifier fid = parent.getFileIdentifier(
                    this.invokerProfile, this.invokerPassword, leaf);
                if (fid != null)
                    leafFile = this.getAndRemove(fid);
            } catch (FileException.NotFound keepGoing) {
                //
                // Ignore the exception; the file is not supposed to exist.
                //
            }
            if (leafFile != null) {
                //
                // Check for partial deletes (marking file as deleted, but
                // leaving directory entry. etc.) and compensate for them.
                //
                if (!leafFile.fileExists() || leafFile.getIsDeleted())
                    parent.unlink(leafFile, this.invokerProfile, this.getInvokerPassword());
                else
                    throw new FileException.Exists();
            }
            //
            // The location in the parent directory where the new directory will
            // be placed is now empty.
            //

            DirectoryImpl leafDir = makeDirectoryImpl(
                this.invokerProfile.getObjectId(), new BeehiveObjectId());

            //
            // Discard attributes that can't be set at creation time by client
            // code and add in defaults for ones that the client hasn't
            // specified.  Then augment them with ones that CelesteFileSystem
            // owns.
            //
            OrderedProperties attrs =
                this.getCreationAttributes(directoryAttributes);
            attrs.setProperty(FILE_SERIAL_NUMBER_NAME,
                Long.toString(this.getUnusedSerialNumber()));

            //
            // XXX: If the create fails, ought to recycle the serial #.
            //
            // XXX: Ought to find a way to do the link without requiring the
            //      new directory to hand over its internal FileImpl instance.
            //      (One possible way would be to overload the link method to
            //      add a variant whose second argument was a DirectoryImpl
            //      instance.)
            //
            leafDir.create(parent, attrs, clientProperties,
                this.invokerProfile, this.getInvokerPassword());
            parent.link(leaf, leafDir.getFileImpl(), this.invokerProfile, this.getInvokerPassword());
            return new CelesteFileSystem.Directory(path, leafDir);
        } finally {
            this.addAndEvictOld(leafFile);
        }
    }

    /**
     * Removes the directory named by {@code path}, which must be empty.
     *
     * @param path  the location of the directory to remove
     *
     * @throws FileException.CapacityExceeded
     *         if the updated parent directory of the directory being removed
     *         is too large to store
     * @throws FileException.CredentialProblem
     *         if there are credential problems in accessing the directory
     * @throws FileException.DirectoryCorrupted
     *         if the contents of any of the directories on {@code path}
     *         have been corrupted
     * @throws FileException.FileSystemNotFound
     *         if this file system cannot be found
     * @throws FileException.NotDirectory
     *         if {@code path} exists, but does not refer to a directory
     * @throws FileException.NotEmpty
     *         if the directory {@code path} names is non-empty
     * @throws FileException.NotFound
     *         if {@code path} does not name a file or directory
     */
    public void deleteDirectory(PathName path) throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.DTokenMismatch,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotEmpty,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // Synchronization:  Once we find the parent directory (the one
        // containing the one to be deleted), prevent it from changing.  Then
        // look up the victim directory and lock it, so that it doesn't change
        // from being empty to not after we commit to the removal.
        //
        // XXX: Is this ordering safe?  Unfortunately, not.  Suppose a
        //      concurrent rename operation attempts to move a file from the
        //      victim directory to its parent (or vice versa).  That
        //      operation will lock both directories and might choose the
        //      other order (based on their hash codes).
        //
        //      So there's a mess to fix...
        //
        FileImpl leafDirAsFile = null;
        try {
            DirectoryImpl parent = this.traverseToParent(path);
            String leaf = path.getLeafComponent();
            FileIdentifier leafDirFid =
                parent.getFileIdentifier(this.invokerProfile, this.invokerPassword, leaf);
            leafDirAsFile = this.getAndRemove(leafDirFid);
            if (!(DirectoryImpl.isDirectory(leafDirAsFile))) {
                throw new FileException.NotDirectory();
            }
            DirectoryImpl leafDir = new DirectoryImpl(leafDirAsFile);

            //
            // We require that the directory be empty (that is, have only "."
            // and ".." components).
            //
            if (leafDir.getAllNames(this.invokerProfile,
                    this.invokerPassword).size() > 2) {
                throw new FileException.NotEmpty();
            }

            //
            // Remove the entry for the directory from its parent directory
            // and then blow the directory away.  (The purgeForever() call is
            // appropriate even should hard links be implemented, since any
            // such implementation will forbid hard links to directories.)
            //
            parent.unlink(leaf, this.invokerProfile, this.getInvokerPassword());
            leafDirAsFile.purgeForever(this.invokerProfile, this.getInvokerPassword());
        } finally {
            this.addAndEvictOld(leafDirAsFile);
        }
    }

    //
    // XXX: Still needs attention to partial failures.  That is, suppose
    //      something goes wrong part way through.  Is the resulting state
    //      allowed?  If not, how can it be fixed?  (Postponing the unlink
    //      from the original location until the link in the new location's
    //      been established goes a long way toward satisfying the
    //      requirements.)
    //
    // XXX: Needs attention to delete vs. unlink issues.
    //
    public void renameFile(PathName srcPath, PathName dstPath) throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.Exists,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.IsDirectory,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotEmpty,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        FileImpl srcLeafFile = null;
        FileImpl dstLeafFile = null;
        try {
            //
            // Two directories will potentially be modified as part of this
            // operation:  the one containing an entry for the file to be
            // moved, and the one into which it is to be moved.  The source
            // directory needs to be locked because we can't expose a window
            // during which the file is present in neither directory.  The
            // destination directory needs to be locked here because the
            // directory-level operation to add the file as a new entry may
            // choose to do its internal synchronization by locking itself.
            // (Moreover, if the destination directory already has an entry
            // for the destination name, there can be no window exposed during
            // which an entry with that name doesn't exist.)  If it does,
            // there's risk of deadlock from rename operations proceeding in
            // opposing orders.  To avoid this risk, we lock both directories
            // up front, ordering the locking by their hash codes (and hoping
            // fervently that there's no hash code collision).
            //
            // Note that it's possible that the two directories are one and
            // the same.  Since the same thread can nest holding the same
            // lock, this is not a problem.
            //
            // XXX: This locking still isn't complete.  Some code paths
            //      require that the entity previously occupying the target
            //      slot in the destination directory (should it be existent
            //      and a directory) be checked for emptiness.  That directory
            //      needs to be locked, so that its status doesn't change
            //      between the check and action taken based on the check.
            //
            DirectoryImpl srcParent = traverseToParent(srcPath);
            DirectoryImpl dstParent = traverseToParent(dstPath);
            DirectoryImpl lock1st = null;
            DirectoryImpl lock2nd = null;
            if (srcParent.hashCode() < dstParent.hashCode()) {
                lock1st = srcParent;
                lock2nd = dstParent;
            } else {
                lock1st = dstParent;
                lock2nd = srcParent;
            }
            synchronized (lock1st) { synchronized (lock2nd) {
                String srcLeaf = srcPath.getLeafComponent();
                srcLeafFile = this.getAndRemove(srcParent.getFileIdentifier(this.invokerProfile, this.invokerPassword, srcLeaf));

                //
                // Check for a self-rename by seeing whether there's a file
                // already using the target name and, if so, verifying that
                // it's not the one being renamed.  (This check is probably
                // unneeded, since this file system doesn't support hard links
                // and paths are all canonicalized upon creation.  However, I
                // haven't convinced myself that there are no loopholes.)
                //
                String dstLeaf = dstPath.getLeafComponent();
                try {
                    dstLeafFile =
                        this.getAndRemove(dstParent.getFileIdentifier(this.invokerProfile, this.invokerPassword, dstLeaf));
                } catch (FileException.NotFound noSuchFile) {
                }
                if (srcLeafFile.equals(dstLeafFile)) {
                    //
                    // Nothing need be done to accomplish the rename; the two
                    // names denote the same file.
                    //
                    return;
                }

                //
                // XXX: Need checks against attempts to rename "."  or ".."
                //      entries.  Also need checks to ensure that the rename
                //      doesn't create a directory graph cycle.  (The latter
                //      check potentially could be made against srcPath and
                //      dstPath, without having to navigate through the
                //      directories themselves, although both paths would have
                //      to be absolute w.r.t. this file system's root.)
                //
                //      The "." and ".." checks ought to be straightforward,
                //      since paths are all in canonical form.  Resolving them
                //      into absolute form will eliminate those components
                //      altogether.
                //
                //      Hmmm...  Also need to ensure that the root isn't
                //      renamed.
                //

                //
                // If the destination location exists, it must be of the same
                // type (file or directory) as the thing being renamed.
                //
                boolean srcLeafIsDir = DirectoryImpl.isDirectory(srcLeafFile);
                boolean dstLeafIsDir = false;
                if (dstLeafFile != null) {
                    dstLeafIsDir = DirectoryImpl.isDirectory(dstLeafFile);
                    if (srcLeafIsDir != dstLeafIsDir)
                        throw new FileException.IsDirectory();
                    if (dstLeafIsDir) {
                        //
                        // XXX: Need properly synchronized check to verify
                        //      that dstLeafFile (which is a directory) is
                        //      empty.  For now, make the check without
                        //      synchronization.
                        //
                        DirectoryImpl dstLeafDir =
                            new DirectoryImpl(dstLeafFile);
                        if (dstLeafDir.getAllNames(this.invokerProfile, this.invokerPassword).size() > 2)
                            throw new FileException.NotEmpty();
                    }
                }

                //
                // At this point, we switch from validity checking to
                // switching links around.
                //

                //
                // If we're renaming a directory, update its ".." entry to
                // name its new home.
                //
                if (srcLeafIsDir) {
                    DirectoryImpl srcLeafDir = new DirectoryImpl(srcLeafFile);
                    srcLeafDir.setParent(dstParent, this.invokerProfile, this.getInvokerPassword());
                }

                //
                // Give the file its new name, replacing dstLeafFile (should
                // it exist) as holder of that name.
                //
                // (Note that, since CelesteFileSystem doesn't support hard
                // links, after this link() call, there will be no way to name
                // dstLeafFile at the CelesteFileSystem level.  However, it's
                // possible that some other thread retains a reference to that
                // file (e.g., through an open file reference in the Samba
                // server code), so it's inappropriate to mark it as deleted
                // or to expunge it by exposing its delete token.  (That is,
                // the file will just have to decay away through the normal
                // Beehive mechanisms.)
                //
                dstParent.link(dstLeaf, srcLeafFile, this.invokerProfile, this.getInvokerPassword());
                srcParent.unlink(srcLeaf, this.invokerProfile, this.getInvokerPassword());
            }}
        } finally {
            this.addAndEvictOld(srcLeafFile);
            this.addAndEvictOld(dstLeafFile);
        }
    }

    //
    // Return the directory containing the final component of path.
    //
    // The method tolerates concurrent modifications to directories positioned
    // between the root and the one being looked up.  As the traversal reaches
    // each intermediate directory, it holds a reference to that directory, so
    // that it's valid to call getFile() on that directory.  The directory is
    // responsible for its own internal locking to guarantee that its lookup
    // is consistent.  Thus, concurrent modifications can change the outcome
    // of the traversal, but cannot disrupt the traversal itself.
    //
    // XXX: But what happens if the directory the traversal has reached is
    //      deleted altogether?  (I don't think anything prevents this.)
    //
    // XXX: This method is takes way too long to execute.  Perhaps it needs a
    //      cache of <path, DirectoryImpl> pairs to help.  Or perhaps an even
    //      more radical reorganization is needed.
    //
    private DirectoryImpl traverseToParent(PathName path) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // Start from the root and traverse through the path's components.
        // Stop immediately before the final component, as we're interested in
        // the directory that that component resides in.
        //
        DirectoryImpl child = this.getRootDirectory();
        if (child == null)
            throw new FileException.FileSystemNotFound(
                this.nameSpaceProfile.getName());
        String[] components = path.getComponents();
        for (int i = 0; i < components.length - 1; i++) {
            String component = components[i];
            //
            // XXX: For the moment, treat all paths as being relative to the
            //      root directory (dropping a leading "/" component if
            //      necessary).  Eventually, it would be useful to allow a
            //      second argument that acts as a current working directory
            //      against which to resolve relative paths.
            //
            if (component.equals("/"))
                continue;
            DirectoryImpl parent = child;
            FileImpl f = null;
            try {
                FileIdentifier fid = parent.getFileIdentifier(
                    this.invokerProfile, this.invokerPassword, component);
                if (fid == null)
                    throw new FileException.NotFound(component);
                f = this.getAndRemove(fid);
                if (DirectoryImpl.isDirectory(f))
                    child = new DirectoryImpl(f);
                else
                    throw new FileException.NotDirectory();
            } finally {
                this.addAndEvictOld(f);
            }
        }
        return child;
    }

    //
    // Get the implementation object representing the root directory of the
    // file system associated with profile.
    //
    private DirectoryImpl getRootDirectory() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.superblock.getRootDirectory();
    }

    //
    // Convenience method that factors out parameters to the DirectoryImpl
    // constructor that are constant for this file system instantiation.
    //
    private DirectoryImpl makeDirectoryImpl(BeehiveObjectId nameSpaceId,
            BeehiveObjectId uniqueFileId) {
        //
        // Use the DirectoryImpl constructor that takes a FileImpl argument,
        // so that we can grab and cache that FileImpl.
        //
        FileIdentifier fid = new FileIdentifier(nameSpaceId, uniqueFileId);
        FileImpl f = null;
        try {
            f = this.getAndRemove(fid);
            return new DirectoryImpl(f);
        } finally {
            this.addAndEvictOld(f);
        }
    }

    //
    // Using the name space profile associated with this file system, obtain a
    // fresh FileImpl (one referring to a newly generated unique id) and insert
    // that FileImpl into the cache.  Callers must put the returned FileImpl
    // object back into the cache by invoking this.addEndEvictOld() when
    // they're done with it.
    //
    private FileImpl getFreshFileImpl() {
        FileIdentifier fid = new FileIdentifier(
            this.nameSpaceProfile.getObjectId(), new BeehiveObjectId());
        return this.getAndRemove(fid);
    }

    //
    // File system attribute support
    //

    /**
     * Returns {@code true} if this file system is configured to support unique
     * serial maintenance and {@code false} otherwise.
     *
     * @return  {@code true} is serial numbers are maintained and {@code
     *          false} otherwise
     *
     * @see FileSystemAttributes.Names#MAINTAIN_SERIAL_NUMBERS_NAME
     */
    public boolean isMaintainSerialNumbers() {
        return this.superblock.isMaintainSerialNumbers();
    }

    private boolean maintainSerialNumbersFromAttrs(OrderedProperties attrs) {
        if (attrs == null)
            return false;
        String rawMaintainSerialNumbers =
            attrs.getProperty(MAINTAIN_SERIAL_NUMBERS_NAME);
        if (rawMaintainSerialNumbers == null)
            return false;
        return Boolean.valueOf(rawMaintainSerialNumbers);
    }

    //
    // Each CelesteFileSystem instance has an associated Superblock instance.
    // The Superblock organizes the persistent data required to implement the
    // file system.  This data consists of the file system's root directory
    // and its serial number manager file.
    //
    // We avoid needing synchronization by having member variables and
    // superblock directory entries only transition away from null (or
    // missing) to a subsequently-constant value, never back to null or
    // missing.
    //
    // XXX: Since writing the above, some synchronization has crept in.
    //      Need to examine it to see whether it's really needed.
    //
    // There are several reasons why fields and entries may need to transition
    // away from null and empty.  Consider first the sequence:
    //      sb = new Superblock() ... sb.create()
    // The create call will bring superblock components into existence, and
    // fields and entries must be updated accordingly.  Second, consider:
    //      thread 1                    thread 2
    //      sb = new Superblock()
    //                                  sb2.create()
    //      sb.someMethod()
    //  In this situation, null fields in sb can't be taken as meaning
    //  "nonexistent", but must rather be interpreted as "not yet known to be
    //  existent".  That is, null fields need to be recalculated when
    //  accessed, until they transition away from null.  (Discovering that a
    //  field is still null after such a recalculation is grounds for having
    //  someMethod() throw FileException.FileSystemNotFound.)
    //
    private static class Superblock {
        //
        // Given a profile, the location of the superblock of the file system
        // associated with that profile is determined by hashing the name of
        // this class.  (Thus changing this class name would orphan all
        // existing Celeste file systems.)
        //
        // XXX: Ultimately, we want to break the 1-1 association between
        //      profiles (which are supposed to represent principals, not
        //      objects associated with principals) and file systems.  That
        //      will probably mean adding a level of indirection here.
        //
        // XXX: Note that a DOS attack is possible, since the name of the
        //      superblock is predictable given that of the profile.
        //
        private final static BeehiveObjectId superblockId = new BeehiveObjectId(
            Superblock.class.getName().getBytes());

        //
        // The file system whose superblock this instance is.
        //
        private final CelesteFileSystem fileSystem;

        //
        // Creation and management of the serial number file (see below) is
        // delegated to an instance of SerialNumberManager.  (The Superblock
        // class knows how to locate the file and the SerialNumberManager
        // knows how to manipulate it.  This division of responsibility leads
        // to package level access for some fields and methods that otherwise
        // would be private.)
        //
        private final SerialNumberManager serialNumberManager;

        //
        // The file system's persistent information is stored as files and
        // properties that are accessed through this directory.  (Note that
        // this directory is accessible only to the file system
        // implementation, so we can stash whatever information we want in it
        // without interfering with the name space that our clients see.)
        //
        // In this version of the implementation, there are two files of
        // interest (as named below):
        //  -   the root directory
        //  -   the serial number manager file
        //
        // In addition, the superblock directory has the following property
        // (as named below):
        //  -   the default attributes to apply when creating files in this
        //      file system (this may eventually be split in two:  one for
        //      files and the other for directories)
        //
        private final FileIdentifier superblockFileId;
        private DirectoryImpl superblock = null;
        final static String         ROOT    = "root";
        private final static String SERIAL  = "serial#s";
        private final static String DEFAULT_CREATION_ATTRIBUTES_NAME =
            "DefaultCreationAttributes";

        //
        // Cached value; starts out as null and is updated at first reference.
        // Once set, never subsequently changes.
        //
        private OrderedProperties   defaultCreationAttributes = null;

        //
        // Cached value; starts out as null and is updated at first reference.
        // Once set, never subsequently changes.
        //
        private DirectoryImpl   rootDir = null;

        public Superblock(CelesteFileSystem fileSystem) throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            this.fileSystem = fileSystem;

            //
            // The handle for the superblock directory itself.  N.B.  This is
            // a well-known value (no randomly generated component).
            //
            Profile_ nameSpaceProfile = this.fileSystem.nameSpaceProfile;
            this.superblockFileId = new FileIdentifier(
                nameSpaceProfile.getObjectId(), Superblock.superblockId);
            FileImpl superblockFile = null;
            try {
                superblockFile =
                    this.fileSystem.getAndRemove(this.superblockFileId);
                this.superblock = new DirectoryImpl(superblockFile);
            } finally {
                this.fileSystem.addAndEvictOld(superblockFile);
            }

            this.serialNumberManager =
                new SerialNumberManager(this.fileSystem, this);

            //
            // Attempt to obtain default creation attributes from the
            // corresponding property set on the superblock.  This will fail
            // if the superblock hasn't yet been created.
            //
            try {
                this.setDefaultCreationAttributes();
            } catch (FileException.NotFound e) {
                //
                // The superblock hasn't been created yet.  Nothing more to
                // do.
                //
                return;
            }

            //
            // Interrogate the superblock directory for its root and serial#
            // files (allowing for the possibility that the directory might
            // not exist yet).
            //
            try {
                this.setRootDirectory();
            } catch (FileException.NotFound e) {
                //
                // Since the superblock's subcomponents are created in order,
                // given that the root directory doesn't yet exist, the
                // subcomponents that follow it in the sequence don't exist
                // yet either.
                //
                return;
            }
            //
            // getSerialNumberFileHandle() uses this.fileSystem's FileImpl
            // cache, so its result must be properly inactivated; hence the
            // try/finally.
            //
            this.serialNumberManager.serialNumberFile = null;
            try {
                this.serialNumberManager.serialNumberFile =
                    this.getSerialNumberFileHandle();
            } catch (FileException.NotFound e) {
                //
                // No initialization left to do...
                //
                return;
            } finally {
                this.fileSystem.addAndEvictOld(
                    this.serialNumberManager.serialNumberFile);
            }
        }

        //
        // Provided that its identity hasn't already been discovered, fetch
        // and record a handle for the root directory.
        //
        private void setRootDirectory() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized (this) {
                if (this.rootDir == null) {
                    this.rootDir = this.getRootDirectoryHandle();
                }
            }
        }

        /**
         * Returns the root directory, fetching and recording it if not already
         * in place.
         */
        public DirectoryImpl getRootDirectory() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized (this) {
                this.setRootDirectory();
                return this.rootDir;
            }
        }

        //
        // If the defaultCreationAttributes field hasn't been set yet (possibly
        // because the superblock hadn't been created at the last attempt) try
        // to set it now by reading the DEFAULT_CREATION_ATTRIBUTES_NAME
        // property from the superblock and using it to set the field.
        //
        private void setDefaultCreationAttributes()
            throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized (this) {
                if (this.defaultCreationAttributes != null)
                    return;

                FileImpl superblockFile = null;
                try {
                    //
                    // Get the superblock's client properties, extract the
                    // encoded form of the default creation attributes, and
                    // decode that form into OrderedProperties.
                    //
                    // Note that if the superblock doesn't yet exist, the call
                    // to getClientProperties() will throw an exception, so
                    // that this.defaultCreationAttributes will remain null
                    // (in anticipation of a subsequent call that retries).
                    //
                    superblockFile =
                        this.fileSystem.getAndRemove(this.superblockFileId);
                    OrderedProperties clientProperties =
                        superblockFile.getClientProperties();
                    String rawDefaultCreationAttributes =
                        clientProperties.getProperty(
                            DEFAULT_CREATION_ATTRIBUTES_NAME);
                    this.defaultCreationAttributes = new OrderedProperties();
                    if (rawDefaultCreationAttributes != null) {
                        this.defaultCreationAttributes.load(ByteBuffer.wrap(
                            CelesteEncoderDecoder.fromHexString(
                                rawDefaultCreationAttributes)));
                    }
                } finally {
                    this.fileSystem.addAndEvictOld(superblockFile);
                }
            }
        }

        /**
         * Returns the default creation attributes for this file system,
         * provided that the file system has been created; if not, returns
         * {@code null}.
         *
         * @return  this file system's default creation attributes
         */
        public OrderedProperties getDefaultCreationAttributes()
            throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized (this) {
                if (this.defaultCreationAttributes == null) {
                    this.setDefaultCreationAttributes();
                }
                return this.defaultCreationAttributes;
            }
        }

        //
        // When a new file system is created, its superblock must be created.
        // This method does so.  It tolerates partially successful previous
        // attempts by building on what it finds.  (Note that this activity is
        // distinct from that of the constructor.  Here, we actually create
        // the superblock and its subcomponents, as opposed to initializing
        // fields containing handles for them.)
        //
        // XXX: Need to decide what to do when the file system attributes
        //      are inconsistent with a partially created file system.
        //
        //      Also need to create a persistent record of the file system
        //      attributes given here, so that they truly apply to the
        //      file system as opposed to accesses made to the file system
        //      through this CelesteFileSystem handle.
        //
        //      The MaintainSerialNumbers attribute's persistent record is
        //      whether or not the serial number file exists.
        //
        //      The default creation attributes are encoded into a property
        //      that's set on the superblock.
        //
        public void create(OrderedProperties clientProperties,
                OrderedProperties defaultCreationAttributes,
                OrderedProperties fileSystemAttributes) throws
                    FileException.BadVersion,
                    FileException.CapacityExceeded,
                    FileException.CelesteFailed,
                    FileException.CelesteInaccessible,
                    FileException.CredentialProblem,
                    FileException.Deleted,
                    FileException.DirectoryCorrupted,
                    FileException.Exists,
                    FileException.FileSystemNotFound,
                    FileException.IOException,
                    FileException.InvalidName,
                    FileException.Locked,
                    FileException.NotFound,
                    FileException.PermissionDenied,
                    FileException.RetriesExceeded,
                    FileException.Runtime,
                    FileException.ValidationFailed {
            //
            // Set up properties to be applied to the superblock directory.
            // There's currently only one:  an encoded version of the default
            // creation attributes to be used when creating files or
            // directories in this file system.  (By placing those attributes
            // there, other handles for this file system can grab them as part
            // of their initialization.)
            //
            if (defaultCreationAttributes == null)
                defaultCreationAttributes = new OrderedProperties();
            String encodedDefaultCreationAttributes =
                CelesteEncoderDecoder.toHexString(
                    defaultCreationAttributes.toByteArray());
            OrderedProperties superblockProperties = new OrderedProperties();
            superblockProperties.setProperty(DEFAULT_CREATION_ATTRIBUTES_NAME,
                encodedDefaultCreationAttributes);
            synchronized (this) {
                //
                // Drop the default creation attributes in place a bit early
                // so that they can be used to create the superblock itself.
                // (Strictly speaking, this shouldn't happen until after the
                // attributes have been installed as a property in the
                // superblock.  But the sequencing violation ought to be
                // harmless.  Note that this code bypasses the
                // setDefaultCreationAttributes() method, but respects its
                // synchronization.)
                //
                this.defaultCreationAttributes = defaultCreationAttributes;
            }
            this.createSuperblockDirectory(superblockProperties);

            //
            // Populate the superblock directory with whichever of the file
            // system root directory and serial number file it doesn't already
            // have.
            //
            if (this.rootDir == null)
                this.createRootDirectory(clientProperties);

            final CelesteFileSystem fs = this.fileSystem;
            boolean useSerialNumbers =
                fs.maintainSerialNumbersFromAttrs(fileSystemAttributes);
            if (useSerialNumbers &&
                    this.serialNumberManager.serialNumberFile == null)
                this.serialNumberManager.create();
        }

        /**
         * Returns {@code true} if the superblock directory associated with
         * this {@code Superblock} handle exists.
         *
         * @return  {@code true} if the superblock directory exists
         */
        public boolean exists()  throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized(this) {
                if (this.superblock == null)
                    return false;
                if (!this.superblock.directoryExists())
                    return false;
                return true;
            }
        }

        /**
         * Returns a copy of {@code explicitAttributes} that omits attributes
         * not present in {@link FileAttributes#creationSet} and that adds
         * attributes from the set of default creation attributes that aren't
         * mentioned in {@code explictAttributes}.
         *
         * @param explicitAttributes    explicitly supplied attributes to
         *                              apply when a file or directory is
         *                              created
         *
         * @return  {@code explicitAttributes} augmented with default
         *          creation attribute values
         *
         * @throws FileException.BadVersion
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.CredentialProblem
         * @throws FileException.Deleted
         * @throws FileException.DirectoryCorrupted
         * @throws FileException.FileSystemNotFound
         *         if this file system has not yet been created
         * @throws FileException.IOException
         * @throws FileException.NotFound
         * @throws FileException.PermissionDenied
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         */
        //
        // XXX: Perhaps this code should be redone to use the defaults
        //      facility that the Properties class provides.  (Would require
        //      revising OrderedProperties to give access to Properties
        //      defaults.)
        //
        // XXX: The semantics aren't quite right.  Although code above
        //      CelesteFileSystem shouldn't be allowed to name attributes
        //      outside of FileAttributes#creationSet, CelesteFileSystem
        //      itself must be able to for attributes that it manages itself
        //      (such as FILE_SERIAL_NUMBER_NAME).  So it seems necessary to
        //      avoid filtering explicitAttributes.
        //
        //      On the other hand, there's no problem if this method is called
        //      _before_ CelesteFileSystem adds its own attributes.  So note
        //      this requirement and leave the method alone, at least for now.
        //
        public OrderedProperties getCreationAttributes(
                OrderedProperties explicitAttributes)
            throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.FileSystemNotFound,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            OrderedProperties defaults = this.getDefaultCreationAttributes();
            if (defaults == null)
                throw new FileException.FileSystemNotFound(
                    this.fileSystem.nameSpaceProfile.getName());
            OrderedProperties result = new OrderedProperties();
            for (String attrName : FileAttributes.creationSet) {
                //
                // If the property's defined in explicitAttributes, pass it
                // through.  (This code counts on null-valued properties being
                // forbidden.)
                //
                String value = explicitAttributes.getProperty(attrName);
                if (value != null) {
                    result.setProperty(attrName, value);
                    continue;
                }
                //
                // If there's a default value, add it.
                //
                value = this.defaultCreationAttributes.getProperty(attrName);
                if (value != null)
                    result.setProperty(attrName, value);
            }
            return result;
        }

        /**
         * Returns {@code true} if this superblock is configured to support
         * unique serial maintenance and {@code false} otherwise.
         *
         * @return  {@code true} is serial numbers are maintained and {@code
         *          false} otherwise
         *
         * @see FileSystemAttributes.Names#MAINTAIN_SERIAL_NUMBERS_NAME
         */
        public boolean isMaintainSerialNumbers() {
            return this.serialNumberManager.isUniqueSerialNumbers();
        }

        private void createSuperblockDirectory(
            OrderedProperties properties) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.Exists,
                FileException.FileSystemNotFound,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            //
            // We have a handle (in the form of this.superblock) for the
            // directory holding the superblock information.  But the
            // directory itself need not exist yet.  Unconditionally attempt
            // to create it, catching and ignoring the exception resulting
            // when it already exists.
            //
            Profile_ invokerProfile = this.fileSystem.invokerProfile;
            try {
                //
                // Use the file system's default creation parameters to create
                // its superblock.
                //
                // Note that the superblock's ACL is important.  If it forbids
                // a given principal to read, then that principal will have to
                // no access to the file system at all; this is a consequence
                // of path name traversals starting by asking the superblock
                // for the root directory.
                //
                // XXX: It ought to be possible to use non-default attributes
                //      for the superblock.  Doing so would require adding an
                //      explicit creationAttributes parameter to this method.
                //
                OrderedProperties attrs = this.getCreationAttributes(
                    new OrderedProperties());
                attrs.setProperty(FILE_SERIAL_NUMBER_NAME,
                    SerialNumberManager.SERIAL_SUPER);
                this.superblock.create(this.superblock, attrs, properties,
                    invokerProfile, this.fileSystem.getInvokerPassword());
            } catch (FileException.Exists e) {
                //
                // Ignore.
                //
            }
        }

        private void createRootDirectory(OrderedProperties clientProperties)
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.FileSystemNotFound,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {

            assert this.rootDir == null;

            //
            // Obtain a new random file name to tentatively be used to name
            // the root directory.
            //
            FileImpl rootAsFile = this.fileSystem.getFreshFileImpl();
            DirectoryImpl root = new DirectoryImpl(rootAsFile);

            try {
                //
                // Create a tentative root directory to go along with its
                // tentative handle.  Note that the directory will use the
                // default creation attributes specified in the call to
                // newFileSystem() that's building this file system.
                //
                // XXX: It ought to be possible to use non-default attributes
                //      for the root directory.  Doing so would require adding
                //      an explicit creationAttributes parameter to this
                //      method.
                //
                OrderedProperties attrs = this.getCreationAttributes(
                    new OrderedProperties());
                attrs.setProperty(FILE_SERIAL_NUMBER_NAME,
                    SerialNumberManager.SERIAL_ROOT);
                Profile_ invokerProfile = this.fileSystem.invokerProfile;
                root.create(root, attrs, clientProperties,
                    invokerProfile, this.fileSystem.getInvokerPassword());

                //
                // Attempt to link an entry for the tentative root into the
                // superblock directory.  If there's already an entry there,
                // the link will fail, and the existing directory should be
                // used in place of the one created above.
                //
                this.superblock.link(Superblock.ROOT, rootAsFile,
                    this.fileSystem.invokerProfile, this.fileSystem.getInvokerPassword(), false);
                this.rootDir = root;
            } catch (FileException.Exists e) {
                //
                // Get the fruits of the racing thread's labors, allowing any
                // exception to propagate outward.  Let the tentative root
                // directory decay away (that is, don't bother deleting it).
                //
                // XXX: That's sloppy.  It really should be deleted.
                //
                this.rootDir = this.getRootDirectoryHandle();
            } finally {
                this.fileSystem.addAndEvictOld(rootAsFile);
            }
            assert this.rootDir != null;
        }

        //
        // Interrogate the superblock for an entry naming the root directory
        // and return that entry, throwing FileException.NotFound for
        // nonexistence and some other FileException for any other problem.
        // The superblock itself must already exist.
        //
        private DirectoryImpl getRootDirectoryHandle() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            assert this.superblock != null;
            FileIdentifier rootFid = this.superblock.getFileIdentifier(
                fileSystem.invokerProfile, fileSystem.invokerPassword, Superblock.ROOT);
            FileImpl rootAsFile = null;
            try {
                rootAsFile = this.fileSystem.getAndRemove(rootFid);
                return new DirectoryImpl(rootAsFile);
            } finally {
                this.fileSystem.addAndEvictOld(rootAsFile);
            }
        }

        //
        // Interrogate the superblock for an entry naming the serial number
        // file and return that entry, throwing FileException.NotFound for
        // nonexistence and some other FileException for any other problem.
        // The superblock itself must already exist.
        //
        // N.B.  Calls to this method should be enclosed in try/finally, with
        // the finally clause calling this.fileSystem.addAndEvictOld() on this
        // method's return value.
        //
        private FileImpl getSerialNumberFileHandle() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            assert this.superblock != null;
            FileIdentifier serialNumberFid =
                this.superblock.getFileIdentifier(fileSystem.invokerProfile,
                    fileSystem.invokerPassword, Superblock.SERIAL);
            return this.fileSystem.getAndRemove(serialNumberFid);
        }
    }

    //
    // Methods for managing file serial numbers
    //

    private long getUnusedSerialNumber() {
        return this.superblock.serialNumberManager.getUnusedSerialNumber();
    }

    //
    // XXX: Ought to be abstracted out into a separate Beehive application
    //      (using the not yet in place support for deterministic state
    //      machines).
    //
    private static class SerialNumberManager {
        //
        // The maximum number of decimal digits required to represent a serial
        // number.  (Since long is signed, this value might be a bit larger
        // than need be.  But no harm ensues.)
        //
        private final static int maxDigits =
            (int)Math.ceil(Math.log10(Math.pow(2.0, 64.0)));

        //
        // Well-known file serial numbers.
        //
        // File serial number trivia:  The root directory traditionally gets
        // inode # 2.  But why 2?  Because 1 was used for fs-internal
        // purposes.  So use it here for the superblock directory.
        //
        final static long           SERIAL_SUPER    = 1L;
        final static long           SERIAL_ROOT     = 2L;
        final static long           SERIAL_SERIAL   = 3L;
        //
        // SERIAL_FIRST is the first sequential serial number available for
        // general use.
        //
        private final static long   SERIAL_FIRST    = 4L;

        //
        // The contents of this file hold state describing the serial numbers
        // associated with the containing CelesteFileSystem instance.
        //
        // In the current implementation this state is simply a string
        // representation (in decimal) of the next unused serial number.  In a
        // more complete implementation, it would be a bit map describing
        // which serial numbers are known to be in use.
        //
        // Note that it's now possible to create a file system that doesn't
        // maintain serial numbers at all.  The implementation records this
        // setting by leaving this field null.
        //
        private FileImpl    serialNumberFile = null;

        private final CelesteFileSystem fileSystem;
        private final Superblock        superblock;

        public SerialNumberManager(CelesteFileSystem fileSystem,
                Superblock superblock) {
            this.fileSystem = fileSystem;
            this.superblock = superblock;
        }

        //
        // Create and initialize serialNumberFile.
        //
        // Needs to cope with the possibility that someone else is
        // concurrently doing so.
        //
        // XXX: What about destruction?
        //
        public void create() throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            assert this.serialNumberFile == null;

            //
            // Obtain a new random file name to tentatively be used to name
            // the serial number file.
            //
            FileImpl serialNumberFile = this.fileSystem.getFreshFileImpl();

            try {
                //
                // Create a tentative serial number file to go along with its
                // tentative handle.
                //
                Profile_ invokerProfile =
                    this.superblock.fileSystem.invokerProfile;
                BeehiveObjectId groupId = BeehiveObjectId.ZERO;
                // thisGroupId = this.superblock.fileSystem.groupProfile;
                OrderedProperties attrs = new OrderedProperties();
                attrs.setProperty(CONTENT_TYPE_NAME,
                    InternetMediaType.Text.Plain.toString());
                attrs.setProperty(FILE_SERIAL_NUMBER_NAME, Long.toString(
                    SerialNumberManager.SERIAL_SERIAL));
                serialNumberFile.create(attrs, null, invokerProfile,
                    this.superblock.fileSystem.getInvokerPassword(),
                    groupId);

                //
                // Attempt to link an entry for the tentative serial number
                // file into the superblock directory.  If there's already an
                // entry there, the link will fail, and the existing serial
                // number file should be used in place of the one created
                // above.
                //
                this.superblock.superblock.link(Superblock.SERIAL,
                    serialNumberFile, this.fileSystem.invokerProfile,
                    this.fileSystem.getInvokerPassword(), false);
                this.serialNumberFile = serialNumberFile;
            } catch (FileException.Exists e) {
                //
                // Get the fruits of the racing thread's labors, allowing any
                // exception to propagate outward.    Let the tentative serial
                // number file decay away (that is, don't bother deleting it).
                // But do return it to the cache.
                //
                this.fileSystem.addAndEvictOld(serialNumberFile);
                this.serialNumberFile =
                    this.superblock.getSerialNumberFileHandle();
            } finally {
                this.fileSystem.addAndEvictOld(serialNumberFile);
            }
            assert this.serialNumberFile != null;
        }

        /**
         * Return {@code true} if this serial number manager provides a unique
         * value for each call to {@link #getUnusedSerialNumber()} {@code
         * false} otherwise.
         *
         * @return  {@code true} if serial numbers are being maintained and
         *          {@code false} otherwise
         */
        public boolean isUniqueSerialNumbers() {
            return this.serialNumberFile != null;
        }

        /**
         * Obtain an unused serial number that is guaranteed free of conflict
         * with other serial numbers in use for the Celeste file system
         * associated with this serial number manager.  Return 0 upon failure
         * to obtain such a serial number or if this file system does not
         * support serial number maintenance.
         */
        public long getUnusedSerialNumber() {
            //
            // If we're not supporting serial number maintenance, bail out
            // quickly.
            //
            if (this.serialNumberFile == null)
                return 0;

            //
            // Synchronize on this to eliminate conflicts due to concurrent
            // requests made to this serial number manager.  However, doing so
            // is just an optimization.  There may be concurrent updates
            // originating from other instances of CelesteFileSystem, so
            // ultimately it's necessary to rely on the linearizer (and suffer
            // the possibility of livelock).
            //
            // XXX: Ought to bound the number of retries.
            //
            ExponentialBackoff delayer = new ExponentialBackoff(2, 100);
            synchronized (this) {
                try {
                    for (;;) {
                        BeehiveObjectId currentVersion =
                            this.serialNumberFile.getLatestVersionId(false);
                        long serialNumber = this.readUnusedSerialNumber();
                        try {
                            this.writeUnusedSerialNumber(serialNumber + 1,
                                currentVersion);
                        } catch (FileException.RetriesExceeded e) {
                            //
                            // Conflicting update; retry after backing off.
                            //
                            delayer.backOff();
                            continue;
                        }
                        return serialNumber;
                    }
                } catch (FileException e) {
                    return 0;
                }
            }
        }

        //
        // Obtain the stored value of the next unused serial number.
        //
        private long readUnusedSerialNumber() {
            assert this.serialNumberFile != null;
            try {
                ExtentBuffer buffer = this.serialNumberFile.read(
                    fileSystem.invokerProfile,
                    fileSystem.getInvokerPassword(),
                    SerialNumberManager.maxDigits, 0);
                //
                // Assume that the read returned the complete representation
                // and that the resulting buffer has a backing array with
                // offset 0 (exploits knowledge of FileImpl's implementation).
                //
                // However, the very first read against a newly created serial
                // number file will return with length 0.  In this case, the
                // first value in the sequence of non-dedicated serial numbers
                // should be returned.
                //
                if (buffer.remaining() == 0) {
                    return SerialNumberManager.SERIAL_FIRST;
                }
                byte[] digitBytes = new byte[buffer.remaining()];
                buffer.get(digitBytes);
                String digits = new String(digitBytes);
                return Long.valueOf(digits);
            } catch (FileException e) {
                //
                // Return the indeterminate/unknown value.
                //
                return 0;
            }
        }

        //
        // Store a new value for the next unused serial number, predicating
        // it on the given version.
        //
        // XXX: If we move away from having a file system store an invoker
        //      profile to be used for all modification operations (in favor
        //      of requiring this information to be supplied explicitly to all
        //      such ops), we'll face the problem of needing an invoker
        //      profile that we can use for file system-internal operations
        //      such as updating the serial number file.  Nasty, nasty,
        //      nasty...
        //
        private void writeUnusedSerialNumber(long value,
                BeehiveObjectId version)
            throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {

            assert this.serialNumberFile != null;
            String digits = String.format("%d", value);
            ExtentBuffer buffer = ExtentBuffer.wrap(0, digits.getBytes());
            this.serialNumberFile.write(buffer,
                this.superblock.fileSystem.invokerProfile,
                this.superblock.fileSystem.getInvokerPassword(),
                version);
        }
    }

    //
    // Convenience access to the Superblock method.
    //
    private OrderedProperties getCreationAttributes(
            OrderedProperties explicitAttributes)
    throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.superblock.getCreationAttributes(explicitAttributes);
    }

    // -- file cache stuff

    //
    // So what's this caching for, anyhow?  Here's the explanation.
    //
    // Over time, a FileImpl object representing a given file builds up
    // considerable state.  This state effectively caches information about
    // the file held in Celeste and, in particular, it can be thrown away with
    // no effects other than a potential performance hit in reconstructing it
    // as required.  However, the performance impact can be substantial due to
    // the cost of round trips to Celeste and the size of cached file
    // contents.
    //
    // Moreover, there can be multiple FileImpl objects representing a given
    // Celeste file.  But due to the considerations outlined above having
    // multiple objects or only having one and repeatedly throwing it away and
    // reconstructing its cached state is undesirable.  Hence the cache.
    //
    // A previous implementation of the cache used weak references, but it
    // proved to be too willing to discard its contents (even after trying
    // several approaches).  So the current implementation instead makes
    // minimal extensions to the LRUCache class, with the extensions crafted
    // to hide some of the base class's unneeded (for this use) generality.
    //
    // As an extension of LRUCache, FileImplCache should be used with the
    // try/finally idiom described in LRUCache's class-level javadoc comment.
    //
    // N.B.  This caching code only caches files and doesn't handle
    // directories (i.e., DirectoryImpl instances aren't cached).  However,
    // DirectoryImpl relies so heavily on FileImpl that there seems to be no
    // incremental benefit in additional caching.  That is, just keeping the
    // FileImpl instance that a DirectoryImpl instance using the corresponding
    // file as backing store refers to ought to suffice.
    //
    // This cache could potentially be shared among all CelesteFileSystem
    // instances mediated by this JVM instance.  Doing that would require
    // reconsidering dispose()'s strategy of flushing the cache to reclaim
    // resources specific to a single CelesteFileSystem instance, since those
    // (FileImpl) resources would no longer be distinguishable from ones used
    // by others.
    //
    private final FileImpl.Cache fileImplCache;

    //
    // A specialized version of the base class method that handles an
    // exception that FileImplFactory can't throw.  Pulled out from the
    // FileImplCache class to remove verbosity from its invocations.
    //
    private FileImpl getAndRemove(FileIdentifier fid) {
        FileImpl fileImpl = null;
        try {
            fileImpl = this.fileImplCache.getAndRemove(fid);
        } catch (Exception cantHappen) {
            //
        }
        return fileImpl;
    }

    //
    // A specialized version of the base class method that eliminates an
    // unneeded argument and handles null (so that the caller doesn't have
    // to).  Pulled out from the FileImplCache class to remove verbosity from
    // its invocations.
    //
    private void addAndEvictOld(FileImpl fileImpl) {
        if (fileImpl == null)
            return;
        this.fileImplCache.addAndEvictOld(
            fileImpl.getFileIdentifier(), fileImpl);
    }
}
