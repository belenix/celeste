/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.celeste.client.filesystem.tabula;

import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CONTENT_TYPE_NAME;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import sunlabs.asdf.util.Time;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.simple.FileImpl;
import sunlabs.celeste.client.filesystem.tabula.FileTreeMap.DirectoryInfo;
import sunlabs.celeste.client.filesystem.tabula.FileTreeMap.FileInfo;
import sunlabs.celeste.client.filesystem.tabula.FileTreeMap.OccupantInfo;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.NewNameSpaceOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.ProfileCache;
import sunlabs.celeste.util.ACL;
import sunlabs.celeste.util.ACL.ACLException;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.util.ExtentBuffer;
import sunlabs.titan.util.OrderedProperties;

/**
 * <p>
 *
 * A file system that makes use of the underlying Celeste file store to hold
 * its persistent state.
 *
 * </p><<p>
 *
 * This implementation uses a multi-level directory to store its entire name
 * space in a single file.
 *
 * </p>
 */
//
// XXX: How big can the name space of a single instance of this file system
//      grow before reading and writing the FileTreeMap holding its name space
//      becomes unacceptably slow or consume too much memory?
//
//      This question will become more acute if FileTreeMap changes to
//      accommodate more state, such as attributes and properties for files
//      and directories.  (Right now, OccupantInfo holds only the minimum
//      information about an occupant required to allow lookups to proceed
//      without interaction with Celeste once the FileTreeMap itself has been
//      read.  Adding attributes and properties would eliminate that
//      minimality property.)
//
public class TabulaFileSystem {
    /**
     * <p>
     *
     * A reference to a file that is managed by its containing {@code
     * TabulaFileSystem instance}.  Each reference to a given file maintains
     * its own offset (called a <em>position</em>) into the file's data.
     * Variants of this class's {@code read()} and {@code write()} methods
     * whose arguments don't specify a beginning position within the file use
     * the reference's position as their starting point.
     *
     * </p><p>
     *
     * This class is called {@code FileReference} rather than plain {@code
     * File} both to avoid confusion with {@code java.io.File} and {@code
     * CelesteFileSystem.File} and to to emphasize that {@code FileReference}
     * objects are are just handles that let one refer to files, not the files
     * themselves.
     *
     * </p>
     */
    public class FileReference extends Occupant {
        private static final long serialVersionUID = 0L;

        //
        // The offset in the file that's used for operations that need to
        // refer to a file offset, but don't take an explicit argument giving
        // one.  Such operations are expected to update this offset upon
        // successful completion.
        //
        private long position = 0;

        //
        // N.B.  Package access because FileReferences should be obtainable
        // only through TabulaFileSystem methods, not directly.
        //
        FileReference(FileIdentifier fid) {
            super(fid);
        }

        /**
         * Returns the file system that provides this file reference.
         *
         * @return the {@code FileReference}'s {@code TabulaFileSystem}
         */
        public TabulaFileSystem getFileSystem() {
            return TabulaFileSystem.this;
        }

        public void setFilePosition(long position) {
            if (position < 0)
                throw new IllegalArgumentException(
                    "position must be non-negative");
            synchronized(this) {
                this.position = position;
            }
        }

        public long getFilePosition() {
            synchronized(this) {
                return this.position;
            }
        }

        /**
         * Returns this file path name, or {@code null} if it has been
         * detached from the file system name space.
         *
         * @return this file's path name
         */
        public PathName getPath() {
            final TabulaFileSystem tfs = TabulaFileSystem.this;
            synchronized(tfs) {
                return tfs.fidToPath.get(this.getFileIdentifier());
            }
        }

        /**
         * Returns {@code true} if this file exists and {@code false}
         * otherwise
         */
        public boolean exists() throws
                FSException.CommunicationFailure,
                FSException.IO,
                FSException.InternalFailure {
            final TabulaFileSystem tfs = TabulaFileSystem.this;
            FileImpl fileImpl = null;
            try {
                fileImpl = tfs.getAndRemove(this.getFileIdentifier());
                return fileImpl.fileExists();
            } catch (FileException.BadVersion e) {
                //
                // XXX: Is this the right exception to throw?
                //
                //      We've fallen victim to version skew:  some other
                //      client using a more recent version of FileImpl has
                //      written to the fileTreeMap file and now we're
                //      confronted with an up-rev version we don't understand.
                //      Throwing an exception with implied action "update your
                //      software" might be helpful.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.CelesteFailed e) {
                throw new FSException.InternalFailure(null, e);
            }  catch (FileException.CelesteInaccessible e) {
                throw new FSException.CommunicationFailure(null,
                    tfs.celesteAddress);
            } catch (FileException.IOException e) {
                throw new FSException.IO(null, e);
            } catch (FileException.Runtime e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.ValidationFailed e) {
                throw new FSException.InternalFailure(null, e);
            } finally {
                tfs.addAndEvictOld(fileImpl);
            }
        }

        /**
         * Creates the file associated with the file reference, provided that
         * it does not already exist.
         *
         * @throws FSException.Exists
         *         if this {@code FileReference} already denotes an existent
         *         file
         */
        //
        // XXX: Ought to accept attributes and properties to apply to the new
        //      file.  (But I'm waiting for some progress on unifying them,
        //      first.)
        //
        // XXX: Exceptions need a beady eye.  E.g., it may be necessary to dig
        //      deeper in the cause chain for PermissionDenied to get to an
        //      ACLException.  The mapping for other exceptions is strained,
        //      too.
        //
        // XXX: Should groupId really be a distinct argument, or should we
        //      punt on it until better unified attribute/property support is
        //      in place?
        //
        public void create(TitanGuid groupId) throws
                FSException.CommunicationFailure,
                FSException.CredentialNotFound,
                FSException.Exists,
                FSException.IO,
                FSException.InsufficientSpace,
                FSException.InternalFailure,
                FSException.PermissionDenied {
            final TabulaFileSystem tfs = TabulaFileSystem.this;
            FileImpl fileImpl = null;
            try {
                fileImpl = tfs.getAndRemove(this.getFileIdentifier());
                fileImpl.create(null, null, tfs.invokerCredential,
                    tfs.invokerPassword.toCharArray(), groupId);
            } catch (FileException.BadVersion e) {
                //
                // XXX: Is this the right exception to throw?
                //
                //      We've fallen victim to version skew:  some other
                //      client using a more recent version of FileImpl has
                //      written to the fileTreeMap file and now we're
                //      confronted with an up-rev version we don't understand.
                //      Throwing an exception with implied action "update your
                //      software" might be helpful.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.CapacityExceeded e) {
                throw new FSException.InsufficientSpace(null, e);
            } catch (FileException.CelesteFailed e) {
                throw new FSException.InternalFailure(null, e);
            }  catch (FileException.CelesteInaccessible e) {
                throw new FSException.CommunicationFailure(null,
                    tfs.celesteAddress);
            } catch (FileException.CredentialProblem e) {
                //
                // XXX: Recheck this after CelesteException.CredentialFailure
                //      is re-whacked.  It might go away altogether or
                //      reflecting it as a different exception might be
                //      appropriate.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Deleted e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Exists e) {
                throw new FSException.Exists(this.getPath());
            } catch (FileException.NotFound e) {
                //
                // As best I can tell, this exception is a consequence of not
                // finding the invoker credential (which is indicative of
                // credential-related exceptions needing a rework...).
                //
                throw new FSException.CredentialNotFound(
                    tfs.invokerCredential.getName(), e);
            } catch (FileException.PermissionDenied e) {
                Throwable cause = e.getCause();
                if (cause instanceof ACLException) {
                    throw new FSException.PermissionDenied(this.getPath(),
                        (ACLException)cause);
                } else {
                    throw new FSException.PermissionDenied(this.getPath());
                }
            } catch (FileException.Runtime e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.ValidationFailed e) {
                throw new FSException.InternalFailure(null, e);
            } finally {
                tfs.addAndEvictOld(fileImpl);
            }
        }

        @Override
        public String toString() {
            return String.format("FileReference[fid=%s, position=%d]",
                this.getFileIdentifier(), this.getFilePosition());
        }
    }

    //
    // The file system's persistent state consists of its name space and
    // occupants (file, directories, etc.) that reside in that name space.
    //
    // A major differentiator between this file system and its predecessor
    // CelesteFileSystem is that the name space information (including
    // directory entries and information, such as directory ACLs, needed to
    // facilitate lookups) resides in a single FileTreeMap object.  This
    // object plays the role of the superblock in traditional file system
    // implementations.
    //
    // The implementation stores its FileTreeMap persistently as a file in the
    // underlying Celeste file store.  The basic pattern of name space-related
    // operations is to optimistically assume that the local copy is current,
    // perform the operation, and to store the updated FileTreeMap.  If the
    // store fails due to a predication failure, the implementation retries
    // the operation after fetching a fresh copy of the FileTreeMap.  (Local
    // synchronization ensures that local data structures remain mutually
    // consistent and optimizes access to Celeste by forcing contending local
    // threads to serialize themselves.)
    //
    // XXX: Consider recasting FileTreeMap as a MutableObject and using the
    //      Beehive object store directly, rather than going through the
    //      Celeste layer.  The only Celeste feature that's really needed here
    //      is support for predication.
    //
    // XXX: Many operations throw the checked exception
    //      FSException.InternalFailure.  By definition, this exception
    //      indicates that something went wrong that that caller can't be
    //      expected to resolve in any reasonable way.  Thus, forcing callers
    //      to handle it amounts to busy work.  It would be better to make the
    //      exception be unchecked.  Since that can't be done (the exception
    //      is a subclass of a checked exception), wrapping it in
    //      RuntimeException might be a good way out.  The TabulaFileSystem
    //      class documentation would have to explain this convention.
    //

    //
    // The (well-known) unique file id for the file holding the external
    // representation of the FileTreeMap for this file system.
    //
    private final static TitanGuid fileTreeMapUniqueId =
        new TitanGuidImpl("FileTreeMap".getBytes());
    //
    // The FileIdentifer of the above file.
    //
    private final FileIdentifier fileTreeMapFid;
    //
    // The most recent known version of the above file as stored in Celeste.
    //
    private TitanGuid fileTreeMapVersion = null;
    //
    // The content type ascribed to this file.
    //
    private final static String tabulaContentType = "X-Celeste/TabulaNameSpace";
    //
    // The name of the property that holds version infomation for this file.
    //
    private final static String versionName = "TabulaFileSystem.Version";

    //
    // The default directory ACL to apply to newly created directories.  It
    // grants permission for all operations to everybody.
    //
    private static DirectoryACL defaultDirectoryACL = new DirectoryACL(
        new DirectoryACL.DirectoryACE(new CelesteACL.AllMatcher(),
            EnumSet.allOf(DirectoryACL.DirectoryOps.class),
            ACL.Disposition.grant)
    );

    //
    // The file system's name space
    //
    private final Credential nameSpaceProfile;

    //
    // The address of the Celeste node that this file system handle will use
    // to handle Celeste requests.  Used to set up caches and is not
    // subsequently directly referenced.
    //
    private final InetSocketAddress celesteAddress;

    //
    // The credential, corresponding object id, and password to be used to
    // authenticate all interactions with Celeste made through this file
    // system handle.
    //
    private final Credential          invokerCredential;
    private final TitanGuid   invokerId;
    private final String            invokerPassword;

    //
    // FileImpl objects are all accessed by way of this cache.
    //
    private final FileImpl.Cache fileImplCache;

    //
    // This file system's name space.  Rather than accessing this field
    // directly, use the getFileTreeMap() method, which attempts to set the
    // field if it hasn't been initialized yet.
    //
    private FileTreeMap fileTreeMap;

    //
    // A map that's used to record what path name (or path names, should links
    // ever be supported) correspond to a given FileReference.  The map is
    // potentially more general, in that it uses FileIdentifiers as keys, but
    // the current implementation only adds entries for fids arising from
    // FileReferences.
    //
    // Note that a given FileReference's useful lifetime is no longer than
    // that of the TabulaFileSystem instance that issued it.  Thus, this map
    // does not need to be persistent.
    //
    // Note also that the rename() method handles updates to this map by
    // creating a copy, updating the copy, and then replacing the original
    // with the copy.
    //
    // Access to this map should be synchronized on this.
    //
    transient private Map<FileIdentifier, PathName> fidToPath =
        new HashMap<FileIdentifier, PathName>();

    /**
     * <p>
     *
     * Create a {@code TabulaFileSystem} object representing a file system
     * identified by {@code name} in the Celeste confederation that the
     * Celeste node at {@code celesteAddress} belongs to.  Access to the file
     * system is via proxies obtained from {@code proxyCache}, using the
     * credential identified by {@code invokerCredentialName} and {@code
     * invokerPassword}.
     *
     * </p><p>
     *
     * Both the name space denoted by {@code name} and the credential
     * denoted by {@code invokerCredentialName} must already exist in Celeste.
     *
     * </p><p>
     *
     * {@code proxyCache} can be {@code null}, in which case this {@code
     * TabulaFileSystem} instance will use a cache constructed with default
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
     * @param name                  the name of the name space identifying
     *                              this file system
     * @param invokerCredentialName the name of the credential of the entity
     *                              to whom operations issued against this
     *                              file system will be ascribed
     * @param invokerPassword       the password for {@code
     *                              invokerCredentialName}
     */
    //
    // XXX: It would be good to relax the restriction that the name space and
    //      invoker credential must already exist.
    //
    public TabulaFileSystem(InetSocketAddress celesteAddress,
            CelesteProxy.Cache proxyCache, String name,
            String invokerCredentialName, String invokerPassword)
        throws
            FSException.CommunicationFailure,
            FSException.CredentialNotFound,
            FSException.IO,
            FSException.InternalFailure {

        this.celesteAddress = celesteAddress;

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
        } catch (Exception e) {
            throw new FSException.CommunicationFailure(null, celesteAddress, e);
        } finally {
            proxyCache.addAndEvictOld(celesteAddress, proxy);
        }

        this.fileImplCache =
            new FileImpl.Cache(10, celesteAddress, proxyCache);

        //
        // We only need profileCache for as long as it takes to obtain the
        // nameSpaceProfile and invokerCredential credentials, so create it
        // with a short timeout for its entries.
        //
        final ProfileCache profileCache =
            new ProfileCache(celesteAddress, proxyCache, 10);
        try {
            this.nameSpaceProfile = profileCache.get(name);
        } catch (Exception e) {
            //
            // XXX: Perhaps we need to throw a different exception here.  The
            // problem isn't necessarily that the name space doesn't exist.
            //
            throw new FSException.CredentialNotFound(name);
        }
        if (this.nameSpaceProfile == null) {
            throw new FSException.CredentialNotFound(name);
        }
        try {
            this.invokerCredential = profileCache.get(invokerCredentialName);
        } catch (Exception e) {
            throw new FSException.CredentialNotFound(invokerCredentialName);
        }
        if (this.invokerCredential == null) {
            throw new FSException.CredentialNotFound(invokerCredentialName);
        }
        this.invokerId = this.invokerCredential.getObjectId();
        this.invokerPassword = invokerPassword;

        this.fileTreeMapFid = new FileIdentifier(
            this.nameSpaceProfile.getObjectId(),
            TabulaFileSystem.fileTreeMapUniqueId);

        //
        // All of the above was a lead in to getting hold of the FileTreeMap
        // that holds the file system's name space (and effectively acts as
        // its superblock).  But the file system might not have been created
        // yet...
        //
        this.fileTreeMap = this.getFileTreeMap();
    }

    /**
     * Returns the address of the Celeste node with which this {@code
     * TabulaFileSystem} instance communicates.
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
    public Credential getNameSpace() {
        return this.nameSpaceProfile;
    }

    /**
     * Returns the {@code TitanGuid} of the name space associated with
     * this file system.
     *
     * @return this file system's name space's object id
     */
    public TitanGuid getNameSpaceId() {
        return this.nameSpaceProfile.getObjectId();
    }

    /**
     * <p>
     *
     * Returns {@code true} if this file system exists and {@code false}
     * otherwise.
     *
     * </p<p>
     *
     * Unless some external means is used to prevent it, the file system could
     * be created between the time of inquiry and use of its results,
     * rendering this method's results obsolete.
     *
     * </p>
     */
    public boolean fileSystemExists() throws
            FSException.CommunicationFailure,
            FSException.IO,
            FSException.InternalFailure {
        synchronized(this) {
            return this.getFileTreeMap() != null;
        }
    }

    /**
     * Creates this file system and its root directory, using the invoker
     * credential associated with this {@code TabulaFileSystem} object.
     *
     * @throws FSException.CommunicationFailure
     *         if communication with the Celeste node designated to field
     *         requests from this {@code TabulaFileSystem} handle fails
     * @throws FSException.FileSystemExists
     *         if the file system already exists
     * @throws FSException.IO
     *         if any of the i/o operations required to store this file
     *         system's housekeeping information failed
     * @throws FSException.InternalFailure
     *         if unrecoverable errors occur within the implementation
     * @throws FSException.InsufficientSpace
     *         if the underlying Celeste file store does not have enough room
     *         to store this file system's housekeeping information
     */
    public void newFileSystem() throws
            FSException.CommunicationFailure,
            FSException.FileSystemExists,
            FSException.IO,
            FSException.InternalFailure,
            FSException.InsufficientSpace {
        //
        // This local synchronization protects the integrity of this
        // TabulaFileSystem object's state.  It also reduces, but doesn't
        // eliminate contention against state held in the Celeste file store.
        //
        synchronized(this) {
            //
            // Plunge ahead and attempt to create the file system's name
            // space.  If the file system already exists, the file holding the
            // serialized form of the name space will also already exist, and
            // the attempt to create the file below will throw an exception.
            //
            FileTreeMap fileTreeMap = new FileTreeMap();
            //
            // XXX: Need to add a root directory to the file system name
            //      space.  But that directory needs to have attributes such
            //      as an ACL, an owner, and a group.  (More besides; those
            //      are the ones that immediately come to mind.)  How to
            //      obtain them?
            //
            //      The owner comes from the invoker credential.
            //
            //      The acl could come from (the as yet unimplemented) default
            //      creation attributes (or from an overloading of this method
            //      that takes attribute arguments).
            //
            //      The group is problematic.
            //
            // XXX: The information called out explicitly above is all part
            //      of FileTreeMap.DirectoryInfo.  It would be reasonable to
            //      add a FileTreeMap constructor that takes a DirectoryInfo
            //      and uses it to create the root directory.
            //
            DirectoryInfo rootInfo = new DirectoryInfo(
                this.newFid(), null, this.invokerId, null,
                TabulaFileSystem.defaultDirectoryACL);
            fileTreeMap.put(new PathName("/"), rootInfo);

            this.createFileTreeMapFile();
            this.fileTreeMap = fileTreeMap;
            try {
                this.writeFileTreeMap();
            } catch (FSException.FileSystemNotFound e) {
                //
                // We just created the file whose non-existence triggers this
                // exception.  Something has gone horribly wrong...
                //
                throw new FSException.InternalFailure(null, e.getCause());
            } catch (IllegalStateException e) {
                //
                // Some other client snuck in between our create and our write
                // with an update of its own.  Nonetheless, we've achieved our
                // purpose of creating the file system.  Our caller should not
                // be able to distinguish this sequence from where the
                // intermediate update occurred immediately after our creation
                // write occurred.
                //
                // So nothing needs to be done in response to this exception.
                //
            }
        }
    }

    /**
     * Creates a new directory with the given {@code attributes} and {@code
     * properties} ar {@code path}.
     *
     * <p>
     *
     * N.B. The current implementation of this method ignores the {@code
     * attributes} and {@code properties} parameters.
     *
     * </p>
     *
     * @param path          a path leading to the new directory
     * @param attributes
     * @param properties
     *
     * @throws FSException.Exists
     *         if a file system object already exists at {@code path}
     * @throws FSException.Locked
     *         if the file system object named by some component of {@code
     *         path} exists, but is locked by an entity other than the caller;
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the first offending component
     * @throws FSException.NotDirectory
     *         if the file system object named by a non-leaf component of
     *         {@code path} exists and has no lock forbidding access, but is
     *         not a directory; calling {@link FSException#getPath()
     *         getPath()} on the exception gives the first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some non-leaf
     *         component of {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some non-leaf component of
     *         {@code path} denies lookup access to the principal associated
     *         with this {@code TabulaFileSystem} handle or the parent
     *         directory of {@code path} denies link access; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     */
    //
    // XXX: This method's signature is subject to change, especially as
    //      attributes and properties get become more unified.
    //
    // XXX: Ownership of the new directory derives from the invoker.  But what
    //      about group membership?  A simple answer is to inherit it from the
    //      parent directory.
    //
    // XXX: Consider a refactoring that makes mkdir/rmdir be symmetric with
    //      corresponding methods on plain files.  (Parsimony in method names
    //      is desirable.)
    //
    public void mkdir(PathName path, OrderedProperties attributes,
            OrderedProperties properties)
        throws
            FSException.CommunicationFailure,
            FSException.Exists,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Verify that the parent exists, is accessible, and that
                // there are no locks that would prevent creating the new
                // directory.
                //
                PathName parentPath = path.getDirName();
                Occupant parent = this.fileTreeMap.lookup(
                    parentPath, this.invokerCredential, true, null);
                if (!(parent instanceof Directory))
                    throw new FSException.NotDirectory(parentPath);
                //
                // Refuse to create on top of an existing occupant.
                //
                if (this.fileTreeMap.get(path) != null)
                    throw new FSException.Exists(path);

                //
                // Verify that we have permission to perform a link operation.
                //
                DirectoryInfo parentInfo =
                    (DirectoryInfo)this.fileTreeMap.get(parentPath);
                try {
                    parentInfo.getACL().check(DirectoryACL.DirectoryOps.link,
                        parentInfo.getFileAttributeAccessor(), this.invokerId);
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(parentPath, e);
                }

                //
                // Set up the DirectoryInfo for the new directory.  Inherit
                // the new directory's group and ACL from the parent.
                //
                FileIdentifier fid = this.newFid();
                DirectoryInfo info = new DirectoryInfo(fid, null,
                    this.invokerId, parentInfo.getGroup(),
                    parentInfo.getACL());

                //
                // Drop the new directory in place and attempt to commit it.
                //
                this.fileTreeMap.put(path, info);
                try {
                    this.writeFileTreeMap();
                    return;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     * Removes the directory named by {@code path}.  For removal to succeed,
     * the directory must contain no entries.
     *
     * @param path  a path name leading to the directory to be removed
     *
     * @throws FSException.Locked
     *         if the file system object named by some component of {@code
     *         path} exists, but is locked by an entity other than the caller;
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the first offending component
     * @throws FSException.NotDirectory
     *         if the file system object named by some component of {@code
     *         path} exists and has no lock forbidding access, but is not a
     *         directory; calling {@link FSException#getPath() getPath()} on
     *         the exception gives the first offending component
     * @throws FSException.NotEmpty
     *         if the file system object named by {@code path} exists, is
     *         accessible and a directory, has no locks preventing removal,
     *         but is non-empty
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies lookup access to the principal associated with this
     *         {@code TabulaFileSystem} handle or the parent directory of
     *         {@code path} denies unlink access; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     */
    public void rmdir(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotEmpty,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Verify that the file system object at path exists, is
                // accessible and is a directory, and that there are no locks
                // that would prevent removing it.
                //
                Occupant occupant = this.fileTreeMap.lookup(path,
                    this.invokerCredential, true, null);
                if (!(occupant instanceof Directory))
                    throw new FSException.NotDirectory(path);

                //
                // Verify that the directory is empty.
                //
                // XXX: This check is a candidate for using the as yet
                //      nonexistent PathName.descendantsIterator() method.
                //
                @SuppressWarnings("unchecked")
                SortedSet<PathName> paths =
                    (SortedSet)this.fileTreeMap.keySet();
                if (path.getDescendants(paths, true).size() != 0)
                    throw new FSException.NotEmpty(path);

                //
                // Verify that we have permission to perform an unlink
                // operation.
                //
                PathName parentPath = path.getDirName();
                DirectoryInfo parentInfo =
                    (DirectoryInfo)this.fileTreeMap.get(parentPath);
                try {
                    parentInfo.getACL().check(DirectoryACL.DirectoryOps.unlink,
                        parentInfo.getFileAttributeAccessor(), this.invokerId);
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(parentPath, e);
                }

                //
                // Zap the directory's entry and attempt to commit the
                // resulting modified map.
                //
                this.fileTreeMap.remove(path);
                try {
                    this.writeFileTreeMap();
                    return;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     * Renames {@code from} to {@code to}.
     *
     * @param from  the existing name of the file system object being renamed
     * @param to    the new name to give to the file system object being
     *              renamed
     *
     * @throws FSException.IsDirectory
     *         if {@code from} refers to a non-directory but {@code to} refers
     *         to a directory
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies lookup access to the principal associated with this
     *         {@code TabulaFileSystem} handle or if the parent directory of
     *         {@code from} or {@code to} denies unlink or link access
     *         (respectively); calling {@link FSException#getPath() getPath()}
     *         on the exception gives the offending component
     */
    //
    // XXX: The implementation provides POSIX semantics.  Might want to
    //      consider imposing additional restrictions, such as insisting that
    //      the target doesn't exist.  Doing so could potentially simplify
    //      adding a method variant that supports predication on the current
    //      version of the existing name.
    //
    //      (But it's not clear that name space operations should be
    //      predicated on the objects the names refer to as opposed to on the
    //      state of the name space itself.  In the current implementation,
    //      where the name space is captured as a single object, that's very
    //      attractive.  It would be less so if the implementation were to be
    //      extended to allow the name space to be split into multiple pieces,
    //      say by re-introducing single-level directories.)
    //
    public void rename(PathName from, PathName to) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.IsDirectory,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotEmpty,
            FSException.NotFound,
            FSException.PermissionDenied {
        if (from == null || to == null)
            throw new IllegalArgumentException(
                "both arguments must be non-null");
        //
        // Declare immediate victory on an attempt to rename something to
        // itself.
        //
        if (from.equals(to))
            return;

        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Verify that from exists, is accessible, and has no locks
                // that would prevent the rename.
                //
                Occupant fromOccupant = this.fileTreeMap.lookup(from,
                    this.invokerCredential, true, null);
                //
                // Verify that the parent of to exists, is accessible, and has
                // no locks that would prevent the rename.
                //
                PathName toParent = to.getDirName();
                this.fileTreeMap.lookup(toParent, this.invokerCredential,
                    true, null);
                //
                // With worries about the path leading up to to addressed,
                // gather information about to itself.
                //
                Occupant toOccupant = null;
                try {
                    toOccupant = this.fileTreeMap.lookup(to,
                        this.invokerCredential, true, null);
                } catch (FSException.NotFound e) {
                    //
                    // Leave toOccupant untouched.
                    //
                }
                boolean toIsDirectory = toOccupant instanceof Directory;

                //
                // The exact renaming operation to be performed and the
                // constraints on it depend on whether or not to and from are
                // directories.  Set toBase to the path of the directory into
                // which from will ultimately be moved and check the
                // constraints.
                //
                PathName toBase = to;
                boolean fromIsDirectory = fromOccupant instanceof Directory;
                if (!fromIsDirectory) {
                    //
                    // from is not a directory; therefore, to can't be a
                    // directory (although it can be non-existent).
                    //
                    // toBase becomes toParent in this case.
                    //
                    if (toIsDirectory)
                        throw new FSException.IsDirectory(to);
                    toBase = toParent;
                } else {
                    //
                    // from is a directory; therefore, to must not refer to a
                    // non-directory (but can be non-existent).
                    //
                    if (toOccupant != null && !toIsDirectory)
                        throw new FSException.NotDirectory(to);
                    //
                    // Moreover, if to exists, it must be an empty directory.
                    // In this case, it will be logically removed and replaced
                    // by from.  So toBase becomes toParent.
                    //
                    if (toOccupant != null) {
                        @SuppressWarnings("unchecked")
                        SortedSet<PathName> paths =
                            (SortedSet)this.fileTreeMap.keySet();
                        if (to.getDescendants(paths, true).size() != 0)
                            throw new FSException.NotEmpty(to);
                        toBase = toParent;
                    }
                }

                //
                // The directory containing from must allow unlink access and
                // the directory containing to must allow link access.
                //
                PathName fromParent = from.getDirName();
                DirectoryInfo fromInfo =
                    (DirectoryInfo)this.fileTreeMap.get(fromParent);
                try {
                    fromInfo.getACL().check(DirectoryACL.DirectoryOps.unlink,
                        fromInfo.getFileAttributeAccessor(), this.invokerId);
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(fromParent, e);
                }
                DirectoryInfo toInfo =
                    (DirectoryInfo)this.fileTreeMap.get(toParent);
                try {
                    toInfo.getACL().check(DirectoryACL.DirectoryOps.link,
                        toInfo.getFileAttributeAccessor(), this.invokerId);
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(toParent, e);
                }

                //
                // Check for conflicting locks.  This amounts to looking for
                // locks in the path leading from the root to to and verifying
                // that the subtree rooted at from has no conflicting locks.
                //
                // XXX: Quadratic algorithm.  We could perhaps do better by
                //      folding the check into the name space update that
                //      accomplishes the rename.
                //
                for (HierarchicalFileSystem.FileName p : to) {
                    OccupantInfo info = this.fileTreeMap.get(p);
                    if (info == null) {
                        //
                        // to is non-existent, so there can be no interior
                        // locks.
                        //
                        break;
                    }
                    Lock lock = info.getLock();
                    if (lock == null)
                        continue;
                    PathName conflictPath =
                        this.fileTreeMap.conflictingLock(from, lock);
                    if (conflictPath != null)
                        throw new FSException.Locked(conflictPath, lock);
                }

                //
                // Everything checks out.  Do the rename by uprooting old and
                // adding a path name for each of its descendants to new.
                //
                // For occupants that support path name tracking (currently
                // file references), update affected path names in the
                // fidToPath map.  Since we don't know at this point whether
                // the rename will successfully commit, do the fidToPath
                // update in a separate copy of the map, so that the updated
                // copy can be moved into place atomically once the
                // fileTreeMap commit succeeds.
                //
                Map<FileIdentifier, PathName> fidToPathWork =
                    new HashMap<FileIdentifier, PathName>(this.fidToPath);
                SortedSet<PathName> fromPaths = from.getDescendants(
                    (SortedSet<PathName>)this.fileTreeMap.keySet(), false);
                for (PathName fromPath : fromPaths) {
                    //System.err.printf(
                    //    "rename: from %s, fromPath %s, toBase %s%n",
                    //    from, fromPath, toBase);
                    OccupantInfo info = this.fileTreeMap.get(fromPath);
                    this.fileTreeMap.remove(fromPath);
                    PathName relativeFrom = from.relativize(fromPath);
                    PathName toPath = relativeFrom.resolve(toBase);
                    this.fileTreeMap.put(toPath, info);

                    //
                    // XXX: Probably ought to use a marker interface to test
                    //      for need to update fidToPath rather than wiring in
                    //      specific OccupantInfo subclasses.
                    //
                    if (info instanceof FileInfo) {
                        fidToPathWork.put(info.fid, toPath);
                    }
                }

                //
                // Attempt to commit the result of the rename.
                //
                try {
                    this.writeFileTreeMap();
                    this.fidToPath = fidToPathWork;
                    return;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     * Returns the contents of the directory named by {@code path}, in the
     * form of a set of paths leading to the directory's entries.
     *
     * @param path  the directory whose contents are to be listed
     *
     * @return a set of path names listing {@code path}'s contents
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any component of {@code
     *         path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies lookup access to the principal associated with this
     *         {@code TabulaFileSystem} handle or if the directory named by
     *         {@code path} denies enumerate permission; in either case
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the offending component
     */
    public SortedSet<PathName> list(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InternalFailure,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            prepareToList(path);
            return this.fileTreeMap.list(path, this.invokerCredential);
        }
    }

    /**
     * Returns the contents of the directory named by {@code path}, in the
     * form of a set of paths leading to the directory's entries.
     *
     * @param path  the directory whose contents are to be listed
     *
     * @return a set of path names listing {@code path}'s contents
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any component of {@code
     *         path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies lookup access to the principal associated with this
     *         {@code TabulaFileSystem} handle or if the directory named by
     *         {@code path} denies enumerate permission; in either case
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the offending component
     */
    public SortedSet<PathName> listDescendants(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InternalFailure,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            prepareToList(path);
            return this.fileTreeMap.listDescendants(path, this.invokerCredential);
        }
    }

    //
    // Worker method for list() and listDescendants().  Verifies access and
    // permissions for the operation.  Assumes that the caller has done
    // proper locking.
    //
    private void prepareToList(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InternalFailure,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        //
        // As a precondition, the file system must exist.  Provided it does,
        // get the current version of the name space.
        //
        if (this.fileTreeMap == null)
            throw new FSException.FileSystemNotFound(this.getName());
        this.readFileTreeMap();

        //
        // Validate access to the path to be listed, ensuring that it names a
        // directory.
        //
        Occupant occupant = null;
        try {
            occupant = this.fileTreeMap.lookup(path, this.invokerCredential,
                false, null);
        } catch (FSException.Locked cantHappen) {
            //
            // If this exception _does_ happen, we have a serious bug.
            //
            throw new FSException.InternalFailure(null, cantHappen);
        }
        if (!(occupant instanceof Directory))
            throw new FSException.NotDirectory(path);
        //
        // Check for enumerate permission.
        //
        DirectoryInfo info = (DirectoryInfo)this.fileTreeMap.get(path);
        try {
            info.getACL().check(DirectoryACL.DirectoryOps.enumerate,
                info.getFileAttributeAccessor(), this.invokerId);
        } catch (ACLException e) {
            throw new FSException.PermissionDenied(path, e);
        }
    }

    //
    // Lock manipulation operations
    //

    /**
     * Applies a WebDAV-style write lock with scope {@code depth} to the file
     * system object named by {@code path}.  Returns a string lock token that
     * uniquely identifies this lock and that is required to unlock it.
     *
     * @param path  the location of the file system object to be locked
     * @param depth whether the lock it to apply only to {@code path} itself
     *              or to everything beneath it as well
     *
     * @return  a string distinguishing this lock from all others within this
     *          file system
     *
     * @throws FSException.Locked
     *         if the file system object named by some component of {@code
     *         path} exists, but is locked by an entity other than the caller
     *         or {@code depth} is {@code INFINITY} and some descendant of
     *         {@code path} has a conflicting lock; in either case calling
     *         {@link FSException#getPath() getPath()} on the exception gives
     *         the name of the offending file system object and calling {@link
     *         FSException.Locked#getLock() getLock()} provides details about
     *         the lock
     * @throws FSException.NotDirectory
     *         if the file system object named by any non-leaf component of
     *         {@code path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies access to the principal associated with this {@code
     *         TabulaFileSystem} handle; calling {@link FSException#getPath()
     *         getPath()} on the exception gives the offending component
     */
    //
    // XXX: Consider refining to allow recursive locking.
    //
    // XXX: Do we need a variant that allows the caller to supply the lock
    //      token?  Almost certainly not, since the WebDAV spec says that the
    //      server is in charge of generating tokens.
    //
    public String lock(PathName path, Lock.Depth depth) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Set up the lock that will be applied should all checks
                // succeed.
                //
                Lock lock = new Lock(this.invokerId, depth, new TitanGuidImpl().toString());

                //
                // Verify that the file system object at path exists, is
                // accessible, and that there are no locks that would prevent
                // locking it.
                //
                this.fileTreeMap.lookup(path, this.invokerCredential, true, lock);
                OccupantInfo info = this.fileTreeMap.get(path);

                //
                // If the occupant is a directory and depth == INFINITY, check
                // for conflicting locks in its descendants.
                //
                // XXX: The code below does not detect the situation where the
                //      invoker has a lock on a descendant.  (But it looks
                //      like it should!  What's going on?)
                //
                boolean isDirectory = info instanceof DirectoryInfo;
                if (depth == Lock.Depth.INFINITY && isDirectory) {
                    //
                    // Iterate over descendants of path looking for locks.
                    // This could easily turn out to be a performance problem
                    // when the file system contains numerous objects.  If so,
                    // the problem could be addressed by augmenting
                    // this.fileTreeMap with a companion map that contains
                    // entries only for locked paths and iterating over that
                    // instead.
                    //
                    for (Map.Entry<PathName, OccupantInfo> entry :
                            this.fileTreeMap.entrySet()) {
                        PathName p = entry.getKey();
                        if (!path.isAncestor(p))
                            continue;

                        //
                        // Since we'll be generating a new token for the
                        // proposed lock, any existing lock must have a
                        // different token and therefore must be in conflict
                        // with the proposed lock.
                        //
                        OccupantInfo innerInfo = entry.getValue();
                        Lock innerLock = innerInfo.getLock();
                        if (innerLock != null)
                            throw new FSException.Locked(p, innerLock);
                    }
                }

                //
                // No information available locally forbids creating the lock.
                // Drop the lock in place and attempt to commit it.
                //
                info.setLock(lock);
                try {
                    this.writeFileTreeMap();
                    return lock.token;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     *
     * @throws FSException.Locked
     *         if the file system object named by some component of {@code
     *         path} exists, but is locked by an entity other than the caller
     *         or a lock exists on the file system object named by {@code
     *         path} with a matching lock owner, but with a different lock
     *         token
     * @throws FSException.NotLocked
     *         if the file system object named by {@code path} exists, but is
     *         not locked
     */
    public void unlock(PathName path, String token) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.NotLocked,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Verify that the file system object at path exists, is
                // accessible, and that there are no locks that would prevent
                // unlocking it.
                //
                this.fileTreeMap.lookup(path, this.invokerCredential, true, null);
                OccupantInfo info = this.fileTreeMap.get(path);

                Lock lock = info.getLock();
                if (lock == null)
                    throw new FSException.NotLocked(path);

                //
                // If the incoming token matches the lock's token, we're clear
                // to remove the lock.  Otherwise, the operation must fail.
                //
                // XXX: This will have to change when recursive lock support
                //      is added.
                //
                if (!lock.token.equals(token))
                    throw new FSException.Locked(path, lock);
                info.setLock(null);

                //
                // Attempt to commit the modified locking state.
                //
                try {
                    this.writeFileTreeMap();
                    return;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     * Returns a {@code Lock} object describing the lock on the file system
     * object named by {@code path} or {@code null} if there is no lock at
     * that location.
     *
     * @param path  the location of the file system object whole lock is to be
     *              inspected
     *
     * @return  information describing the lock or {@code code null} if there
     *          is no lock at {@code path}
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any non-leaf component of
     *         {@code path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies access to the principal associated with this {@code
     *         TabulaFileSystem} handle; calling {@link FSException#getPath()
     *         getPath()} on the exception gives the offending component
     */
    public Lock inspectLock(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InternalFailure,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            this.readFileTreeMap();
            //
            // Verify that the file system object at path exists, is
            // accessible, and that there are no locks that would prevent
            // inspecting it.
            //
            try {
                this.fileTreeMap.lookup(path, this.invokerCredential, false, null);
            } catch (FSException.Locked cantHappen) {
                //
                // If this exception _does_ happen, we have a serious bug.
                //
                throw new FSException.InternalFailure(null, cantHappen);
            }
            OccupantInfo info = this.fileTreeMap.get(path);
            return info.getLock();
        }
    }

    //
    // FileReference methods
    //
    // XXX: Should there be a method that, given a FileReference and a
    //      PathName, makes the path refer to the reference?  (Given such a
    //      method, we'd have a back-handed way of creating multiple links to
    //      a given file, so it shouldn't be added lightly.)
    //

    /**
     * Returns a fresh reference to a file named by {@code path}.  If that
     * location already denotes a file, the returned reference refers to that
     * file; if not the reference contains a fresh {@code FileIdentifier} for
     * a file that can subsequently be created with {@link
     * FileReference#create() FileReference.create()} and {@code path} names
     * that (as yet still non-existent) file.  In both cases, the reference's
     * file position value is 0.
     *
     * @throws FSException.NotFile
     *         if {@code path} denotes a file system object that is not a file
     */
    public FileReference getFile(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.IsDirectory,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFile,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            FileReference file = null;
            for (;;) {
                //
                // Verify that the parent directory is properly accessible.
                //
                PathName parentPath = path.getDirName();
                Occupant parent = this.fileTreeMap.lookup(
                    parentPath, this.invokerCredential, true, null);
                if (!(parent instanceof Directory))
                    throw new FSException.NotDirectory(parentPath);

                //
                // If there's something already present at path that's not a
                // file reference, fail.  Otherwise, we have or can create a
                // suitable reference.
                //
                OccupantInfo occupantInfo = this.fileTreeMap.get(path);
                if (occupantInfo != null && !(occupantInfo instanceof FileInfo))
                    throw new FSException.NotFile(path);

                FileInfo info = null;
                if (occupantInfo != null) {
                    info = (FileInfo)occupantInfo;
                } else {
                    //
                    // No previous reference exists.  Before creating a
                    // completely fresh one, verify that the parent directory
                    // allows links.
                    //
                    DirectoryInfo parentInfo =
                        (DirectoryInfo)this.fileTreeMap.get(parentPath);
                    try {
                        parentInfo.getACL().check(
                            DirectoryACL.DirectoryOps.link,
                            parentInfo.getFileAttributeAccessor(),
                            this.invokerId);
                    } catch (ACLException e) {
                        throw new FSException.PermissionDenied(parentPath, e);
                    }

                    info = new FileInfo(newFid(), null);
                }
                file = new FileReference(info.fid);

                //
                // Drop the new file reference in place and attempt to commit
                // it to stable storage.  If the commit succeeds, update the
                // reverse lookup map to match.
                //
                this.fileTreeMap.put(path, info);
                try {
                    this.writeFileTreeMap();
                    this.fidToPath.put(info.fid, path);
                    return file;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    /**
     * <p>
     *
     * Removes {@code path} as a way of referring to the file currently
     * referenced by that name.  If {@code path} does not name a {@code
     * FileReference}, the method will fail.
     *
     * </p><p>
     *
     * Note that this method merely removes a way of naming a file from the
     * file system name space; it does not affect whether or not the file
     * exists.  To delete a file, one must call {@link FileReference#delete()
     * FileReference.delete()}.  Stated differently, the existence of a file
     * and the availability of a path name that refers to that file are
     * independent of each other.
     *
     * </p>
     *
     * @throws FSException.NotFile
     *         if {@code path} denotes a file system object that is not a file
     */
    public void removeFile(PathName path) throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            FSException.IsDirectory,
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFile,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.FileSystemNotFound(this.getName());
            for (;;) {
                //
                // Verify that the parent directory is properly accessible.
                //
                PathName parentPath = path.getDirName();
                Occupant parent = this.fileTreeMap.lookup(
                    parentPath, this.invokerCredential, true, null);
                if (!(parent instanceof Directory))
                    throw new FSException.NotDirectory(parentPath);

                //
                // If there's something already present at path that's not a
                // file reference, fail.
                //
                OccupantInfo occupantInfo = this.fileTreeMap.get(path);
                if (occupantInfo == null)
                    throw new FSException.NotFound(path);
                else if (!(occupantInfo instanceof FileInfo))
                    throw new FSException.NotFile(path);
                FileInfo info = (FileInfo)occupantInfo;

                //
                // Make sure the parent directory allows unlinks.
                //
                DirectoryInfo parentInfo =
                    (DirectoryInfo)this.fileTreeMap.get(parentPath);
                try {
                    parentInfo.getACL().check(DirectoryACL.DirectoryOps.unlink,
                        parentInfo.getFileAttributeAccessor(), this.invokerId);
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(parentPath, e);
                }

                //
                // Perform the unlink and attempt to commit it to stable
                // storage.  If the commit succeeds, update the revese lookup
                // map to match.
                //
                this.fileTreeMap.remove(path);
                try {
                    this.writeFileTreeMap();
                    this.fidToPath.remove(info.fid);
                    return;
                } catch (IllegalStateException e) {
                    this.readFileTreeMap();
                }
            }
        }
    }

    //
    // Methods for storing and retrieving the file system's FileTreeMap in a
    // Celeste file.
    //

    //
    // Creates the backing store file, initializing it with fileTreeMap.
    //
    private void createFileTreeMapFile() throws
            FSException.FileSystemExists,
            FSException.InternalFailure {
        //
        // Set properties and attributes.
        //
        // The version property describes the format of the file's contents.
        // Since (at least for now) all it holds is the serialized FileTreeMap
        // holding this file system's name space, we use FileTreeMap.version
        // as its value.
        //
        // The attribute are ContentType, which is the value of
        // TabulaFileSystem.tabulaContentType, and the ACL.
        //
        // XXX: Since we must permit arbitrary callers to perform file system
        //      operations (since the ACLs in particular occupants may well
        //      permit them to do so), we need to allow complete access to the
        //      fileTreeMap file.  (Otherwise, some caller who would be
        //      permitted to perform the requested operation could be rejected
        //      because access for updating the fileTreeMap is denied.)  But
        //      this means that we can't used the ACL to protect the
        //      fileTreeMap file from access through paths that don't go
        //      through this implementation.
        //
        OrderedProperties props = new OrderedProperties();
        props.setProperty(TabulaFileSystem.versionName, FileTreeMap.version);
        OrderedProperties attrs = new OrderedProperties();
        attrs.setProperty(CONTENT_TYPE_NAME,
            TabulaFileSystem.tabulaContentType);
        //
        // XXX: Still need to define and set the ACL for this file.
        //

        FileImpl fileImpl = null;
        try {
            fileImpl = this.getAndRemove(this.fileTreeMapFid);
            //
            // XXX: Using null as the group value eventually leads to the
            //      TitanGuid() constructor being contronted with "null"
            //      as its argument.  Somewhere or another I suspect we have
            //      String.Format("%s", null) coming into play.  Track down
            //      and fix!
            //
            //      In the meantime, use an explicit value.
            //
            fileImpl.create(attrs, props,
                this.invokerCredential, this.getInvokerPassword(),
                TitanGuidImpl.ZERO);
            this.fileTreeMapVersion = fileImpl.getLatestVersionId(false);
        } catch (FileException.Exists e) {
            //
            // Already created.
            //
            throw new FSException.FileSystemExists(this.getName());
        } catch (FileException e) {
            throw new FSException.InternalFailure(null, e);
        } finally {
            this.addAndEvictOld(fileImpl);
        }
    }

    //
    // Store this.fileTreeMap (which presumably has been updated) in Celeste.
    // If the file has been updated since the last locally known version,
    // throw IllegalStateException, so that any operation that might be based
    // on that previous version can be retried.
    //
    private void writeFileTreeMap() throws
            FSException.CommunicationFailure,
            FSException.FileSystemNotFound,
            FSException.IO,
            FSException.InsufficientSpace,
            FSException.InternalFailure,
            IllegalStateException {
        //
        // Synchronize to ensure that what's written accurately reflects the
        // local state.
        //
        synchronized(this) {
            if (this.fileTreeMap == null)
                throw new FSException.InternalFailure(null,
                    new NullPointerException());
            byte[] serialization = this.fileTreeMap.serialize();
            ExtentBuffer eb = new ExtentBuffer(0L,
                ByteBuffer.wrap(serialization));

            FileImpl fileImpl = null;
            try {
                fileImpl = this.getAndRemove(this.fileTreeMapFid);
                fileImpl.write(eb, this.invokerCredential,
                    this.getInvokerPassword(), this.fileTreeMapVersion);
            } catch (FileException.BadVersion e) {
                //
                // XXX: Is this the right exception to throw?
                //
                //      Probably:  We're doing a write, so the exception can
                //      only arise from reading back metadata for the
                //      resulting version whose version doesn't match what we
                //      just wrote.  This is bad juju and deserves the
                //      InternalFailure exception.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.CapacityExceeded e) {
                throw new FSException.InsufficientSpace(null, e);
            } catch (FileException.CelesteFailed e) {
                throw new FSException.InternalFailure(null, e);
            }  catch (FileException.CelesteInaccessible e) {
                throw new FSException.CommunicationFailure(null,
                    this.celesteAddress);
            } catch (FileException.CredentialProblem e) {
                //
                // XXX: Recheck this after CelesteException.CredentialFailure
                //      is re-whacked.  It might go away altogether or
                //      reflecting it as a different exception might be
                //      appropriate.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Deleted e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.IOException e) {
                throw new FSException.IO(null, e);
            } catch (FileException.InvalidName e) {
                //
                // Arises from CelesteException.IllegalParameterException,
                // which indicates that the implementation messed up somehow.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Locked e) {
                //
                // The implementation does not (yet) use locking to restict
                // access to the file tree map, so if it _is_locked,
                // something's badly wrong.
                //
                // XXX: Could attempt to break or ignore the lock.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.NotFound e) {
                //
                // Most likely cause:  The file system hasn't yet been
                // created.  Report accordingly.
                //
                throw new FSException.FileSystemNotFound(this.getName());
            } catch (FileException.PermissionDenied e) {
                //
                // Either the implementation has a bug or somebody replaced
                // the ACL.
                //
                // XXX: There's no apparent way to prevent ACL replacement.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.RetriesExceeded e) {
                //
                // We lost a race with some other client of this file system,
                // and the on-Celeste version of this.fileTreeMap is more
                // recent than our local version.  Alert our caller by
                // throwing IllegalStateException after updating our record of
                // the current version.
                //
                // (We use IllegalStateException to avoid introducing a new
                // FSException subclass; such subclasses are visible to client
                // code, and this occurrence is a purely internal matter.)
                //
                // XXX: Need a variant of FileImpl.getLatestVersionId() that
                //      throws no (or at least minimal) exceptions.  Since
                //      FileImpl has just interacted with Celeste, all that's
                //      needed is to dig the version id out of the metadata
                //      coming back from the exception that indicated a
                //      predication failure.
                //
                try {
                    this.fileTreeMapVersion =
                        fileImpl.getLatestVersionId(false);
                } catch (FileException inner) {
                    throw new FSException.InternalFailure(null, inner);
                }
                throw new IllegalStateException();
            } catch (FileException.Runtime e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.ValidationFailed e) {
                throw new FSException.InternalFailure(null, e);
            } finally {
                this.addAndEvictOld(fileImpl);
            }
        }
    }

    //
    // XXX: Quick and dirty implementation that depends on knowledge of how
    //      FileImpl.write() and FileImpl.read() are implemented.  Needs to be
    //      bullet-proofed to avoid assumptions about a single read grabbing
    //      the entire file.
    //
    private void readFileTreeMap() throws
            FSException.CommunicationFailure,
            FSException.IO,
            FSException.InternalFailure,
            FSException.FileSystemNotFound {
        synchronized(this) {
            FileImpl fileImpl = null;
            byte[] serialization = null;
            try {
                //
                // Read the external (serialized) form of the file tree map
                // and use the resulting bytes to reconstitute a local copy of
                // the object.
                //
                fileImpl = this.getAndRemove(this.fileTreeMapFid);
                ExtentBuffer eb = fileImpl.read(this.invokerCredential,
                    this.getInvokerPassword(), Integer.MAX_VALUE, 0L);
                serialization = new byte[eb.remaining()];
                eb.get(serialization);
                this.fileTreeMap = FileTreeMap.deserialize(serialization);
            } catch (FileException.BadVersion e) {
                //
                // XXX: Is this the right exception to throw?
                //
                //      We've fallen victim to version skew:  some other
                //      client using a more recent version of FileImpl has
                //      written to the fileTreeMap file and now we're
                //      confronted with an up-rev version we don't understand.
                //      Throwing an exception with implied action "update your
                //      software" might be helpful.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.CelesteFailed e) {
                throw new FSException.InternalFailure(null, e);
            }  catch (FileException.CelesteInaccessible e) {
                throw new FSException.CommunicationFailure(null,
                    this.celesteAddress);
            } catch (FileException.CredentialProblem e) {
                //
                // XXX: Recheck this after CelesteException.CredentialFailure
                //      is re-whacked.  It might go away altogether or
                //      reflecting it as a different exception might be
                //      appropriate.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Deleted e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.IOException e) {
                throw new FSException.IO(null, e);
            } catch (FileException.NotFound e) {
                //
                // Most likely cause:  The file system hasn't yet been
                // created.  Report accordingly.
                //
                throw new FSException.FileSystemNotFound(this.getName());
            } catch (FileException.PermissionDenied e) {
                //
                // Either the implementation has a bug or somebody replaced
                // the ACL.
                //
                // XXX: There's no apparent way to prevent ACL replacement.
                //
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.Runtime e) {
                throw new FSException.InternalFailure(null, e);
            } catch (FileException.ValidationFailed e) {
                throw new FSException.InternalFailure(null, e);
            } finally {
                this.addAndEvictOld(fileImpl);
            }
        }
    }

    //
    // Convenience method for accessing the invoker password.
    //
    private char[] getInvokerPassword() {
        return this.invokerPassword.toCharArray();
    }

    //
    // Convenience method to generate an unused FileIdentifier.
    //
    private FileIdentifier newFid() {
        return new FileIdentifier(this.getNameSpaceId(), new TitanGuidImpl());
    }

    //
    // If this.fileTreeMap hasn't been set yet (perhaps because this file
    // system hadn't been created when this file system handle was created),
    // try to set it now.
    //
    private void setFileTreeMap() throws
            FSException.CommunicationFailure,
            FSException.IO,
            FSException.InternalFailure {
        synchronized(this) {
            try {
                this.readFileTreeMap();
            } catch (FSException.FileSystemNotFound e) {
                //
                // Leave this.fileTreeMap's null value unchanged.
                //
            }
        }
    }

    //
    // Returns the FileTreeMap for this file system, provided that this file
    // system has been created.  If not, returns null.
    //
    private FileTreeMap getFileTreeMap() throws
            FSException.CommunicationFailure,
            FSException.IO,
            FSException.InternalFailure {
        synchronized(this) {
            if (this.fileTreeMap != null)
                this.setFileTreeMap();
            return this.fileTreeMap;
        }
    }

    //
    // FileImpl caching methods
    //

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

    //
    // Exercise the basics of this file system.
    //
    public static void main(String[] args) throws Exception {
        //
        // Set up access to the Celeste file store and ensure that credentials
        // and names spaces exist.
        //
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 14000);
        CelesteProxy.Cache pcache = new CelesteProxy.Cache(4, 0L);
        CelesteAPI node = pcache.getAndRemove(addr);

        String fsName = "tabula-test";
        String fsPassword = "tabula-password";
        String credName = "my-credential";
        String credPassword = "my-password";
        String replicationParams = "Credential.Replication.Store=2";

        //
        // Create the name space...
        //
        try {
            Profile_ fileSystemCredential = new Profile_(fsName, fsPassword.toCharArray());
            NewNameSpaceOperation operation = new NewNameSpaceOperation(fileSystemCredential.getObjectId(), TitanGuidImpl.ZERO, replicationParams);
            Credential.Signature signature = fileSystemCredential.sign(fsPassword.toCharArray(), operation.getId());
            node.newNameSpace(operation, signature, fileSystemCredential);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw e;
        }
        //
        // ... and the invoker credential.
        //
        try {
            Profile_ invokerCredential = new Profile_(credName, credPassword.toCharArray());
            NewCredentialOperation operation = new NewCredentialOperation(invokerCredential.getObjectId(), TitanGuidImpl.ZERO, replicationParams);
            Credential.Signature signature = invokerCredential.sign(credPassword.toCharArray(), operation.getId());
            node.newCredential(operation, signature, invokerCredential);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw e;
        }

        //
        // Get a handle for the file system we're about to create.
        //
        TabulaFileSystem tfs = new TabulaFileSystem(addr, pcache, fsName,
            credName, credPassword);

        PathName root = new PathName("/");
        PathName a = new PathName("/a");
        PathName a_file_a = new PathName("/a/file_a");
        PathName ab = new PathName("/a/b");
        PathName abc = new PathName("/a/b/c");
        PathName b = new PathName("/b");
        PathName bc = new PathName("/b/c");
        PathName bcd = new PathName("/b/c/d");
        PathName bce = new PathName("/b/c/e");
        PathName bcef = new PathName("/b/c/e/f");
        PathName bceg = new PathName("/b/c/e/g");
        PathName bceg_file_b = new PathName("/b/c/e/g/file_b");
        PathName bceh = new PathName("/b/c/e/h");
        PathName i = new PathName("/i");
        PathName ieh = new PathName("/i/e/h");

        tfs.newFileSystem();
        tfs.mkdir(a, null, null);
        FileReference a_file_a_ref = tfs.getFile(a_file_a);
        tfs.mkdir(b, null, null);
        tfs.mkdir(bc, null, null);
        tfs.mkdir(bcd, null, null);
        tfs.mkdir(bce, null, null);
        tfs.mkdir(bcef, null, null);
        System.out.printf("%s contents: %s%n", bc, tfs.list(bc));
        String bceToken = tfs.lock(bce, Lock.Depth.ZERO);
        System.out.printf("%s lock: %s%n", bce, tfs.inspectLock(bce));
        try {
            tfs.lock(bc, Lock.Depth.INFINITY);
        } catch (FSException.Locked e) {
            System.out.printf(
                "lock of %s failed as expected; conflicting lock at %s%n",
                bc, e.getPath());
        }
        tfs.unlock(bce, bceToken);
        String bcToken = tfs.lock(bc, Lock.Depth.INFINITY);
        try {
            tfs.lock(bce, Lock.Depth.ZERO);
        } catch (FSException.Locked e) {
            System.out.printf(
                "lock of %s failed as expected; conflicting lock at %s%n",
                bce, e.getPath());
        } catch (Exception e) {
            System.out.printf(
                "lock of %s failed with unexpected exception %s%n", e);
        }
        tfs.unlock(bc, bcToken);
        try {
            tfs.rmdir(bce);
        } catch (FSException.NotEmpty e) {
            System.out.printf("rmdir of %s failed as expected with NotEmpty%n",
                e.getPath());
        }
        tfs.rmdir(bcef);
        //
        // Rename tests.  Try a simple case first.
        //
        tfs.mkdir(bceg, null, null);
        FileReference bceg_file_b_ref = tfs.getFile(bceg_file_b);
        System.out.printf("before rename:%n%s%n", tfs.listDescendants(root));
        tfs.rename(bceg, bceh);
        System.out.printf("after rename(%s, %s):%n%s%n",
            bceg, bceh, tfs.listDescendants(root));
        System.out.printf("new name from file ref: %s%n",
            bceg_file_b_ref.getPath());
        //
        // Now rename an entire hierarchy.
        //
        tfs.rename(b, ab);
        System.out.printf("after rename(%s, %s):%n%s%n",
            b, ab, tfs.listDescendants(root));
        //
        // Do a rename to an empty directory.
        //
        tfs.rename(abc, i);
        System.out.printf("after rename(%s, %s):%n%s%n",
            abc, i, tfs.listDescendants(root));
        //
        // Verify that a rename that would overwrite a non-empty directory
        // fails.
        //
        try {
            tfs.rename(ieh, a);
        } catch (FSException.NotEmpty e) {
            System.out.printf(
                "rename(%s, %s) failed with NotEmpty as expected; "+
                "offending path %s%n",
                ieh, a, e.getPath());
        }
        //
        // Verify that a rename that would create a cycle fails.
        //
        try {
            tfs.rename(ieh, i);
        } catch (FSException.NotEmpty e) {
            System.out.printf(
                "rename(%s, %s) failed with NotEmpty as expected; offending path %s%n",
                ieh, i, e.getPath());
        }
    }
}
