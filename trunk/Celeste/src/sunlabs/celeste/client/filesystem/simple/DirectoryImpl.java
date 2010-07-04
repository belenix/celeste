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

package sunlabs.celeste.client.filesystem.simple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.ExponentialBackoff;
import sunlabs.beehive.util.ExtentBuffer;
import sunlabs.beehive.util.OrderedProperties;

import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.filesystem.FileAttributes;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;

import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CONTENT_TYPE_NAME;

/**
 * DirectoryImpl represents directories as they are stored in Celeste files.
 */
//
// Implementation strategy:
//
//      The directory's contents are stored in a file, which is kept as the
//      "store" field.  The contents consist of entries in a OrderedProperties
//      object, which is converted to and from a byte array when it is stored
//      and fetched.  Each entry's key is a POSIX-style file leaf name (so '/'
//      is disallowed as a component character).  Its value is in one of two
//      possible formats:
//        - legacy:  a colon-separated pair of the string representations of
//          the Beehive object ids for the corresponding file's creator and
//          unique file id; or
//        - current:  a colon-separated triple with the first two components
//          as above and the third component holding a string representation
//          of the file's serial number.
//      (If the serial number component isn't present, it defaults to zero
//      when requested.)  Since the old implementation ignores fields beyond
//      the ones it understands, this directory representation doesn't require
//      changing the data encoding version number (see below).
//
//      However, the OrderedProperties object also contains entries whose keys
//      are not of the form described above -- specifically, they start with
//      "/".  These entries contain implementation-specific directory
//      metadata.  There currently is one such metadata item:  "/version",
//      whose value is the string form of the integer encoding the version of
//      the directory representation, as stored in the underlying file.
//
// XXX: How well can we expect the hash table that implements
//      OrderedProperties to scale in the face of high rates of insertion and
//      deletion traffic and of large numbers of entries?  It's likely that
//      the representation decision will have to be revisited for production
//      use.
//
// XXX: There's no provision here for access control (setting and checking
//      ACLs, setting owner and group, interrogating ACL, owner, and group),
//      but there should be.  There's also no provision for updating
//      client-supplied metadata after initial directory creation.
//
//      (Given that the only way to create a directory is to supply a FileImpl
//      handle for its backing store, one could circumvent these deficiencies
//      by accessing the backing store file directly, but that would be close
//      to criminal.)
//
// XXX: Now that there's support for client-supplied metadata in FileImpl,
//      the metadata directory entries described above could potentially be
//      captured as true file metadata rather than as additional entries in
//      the directory's primary data.  Consider doing so.
//
//      (Doing that for the /version item would have the advantage that it
//      wouldn't be necessary to understand the representation before
//      being able to interrogate its version...)
//
public class DirectoryImpl {
    /**
     * <p>
     *
     * Each {@code Dirent} instance represents an entry in a directory.
     * Entries contain a file name and a long-valued serial number (more
     * commonly known as an inode number).  The serial number may be missing.
     * If so, it is represented as {@code 0}.
     *
     * </p><p>
     *
     * {@code Dirent} instances are immutable; therefore, their fields are
     * directly accessible.
     *
     * </p><p>
     *
     * Instances of this class are ordered by their {@code leafName} fields.
     *
     * </p><p>
     *
     * Note:  This class has a natural ordering that is inconsistent with
     * equals.  However, such inconsistencies manifest themselves only for
     * instances with equal {@code leafName} fields, which will never occur
     * within a collection representing the contents of a directory.
     *
     * </p>
     */
    public static class Dirent implements Comparable<Dirent> {
        /**
         * The name of the file this directory entry represents.
         */
        public final String leafName;

        /**
         * The serial number of the file this directory entry represents with
         * respect to the file's containing file system, or {@code 0} if the
         * serial number is not defined or is missing.
         */
        public final long serialNumber;

        /**
         * Construct a new directory entry.
         *
         * @param leafName      the name of the file the entry represents
         * @param serialNumber  the serial number of the file this entry
         *                      represents or {@code 0} in the serial number
         *                      is undefined or unknown
         */
        public Dirent(String leafName, long serialNumber) {
            this.leafName = leafName;
            this.serialNumber = serialNumber;
        }

        /**
         * Construct a new directory entry without specifying the serial
         * number of the file it represents.
         *
         * @param leafName      the name of the file the entry represents
         */
        public Dirent(String leafName) {
            this(leafName, 0);
        }

        public int compareTo(Dirent other) {
            return this.leafName.compareTo(other.leafName);
        }
    }

    //
    // XXX: For performance debugging; names a property containing logging
    //      configuration information, where, for now, the only relevant
    //      configuration values are "on", "off", or null.
    //
    public final static String LOGGING_CONFIGURATION_NAME =
        "LoggingConfiguration";

    //
    // The version of the data encoding 'on disk'.  The current encoding is
    // upward compatible with the original encoding and thus remains at
    // version 1.
    //
    private static final int defaultDataEncodingVersion = 1;
    private int dataEncodingVersion;

    private final FileImpl store;

    //
    // Constructor(s)
    //
    // XXX: It seems rather peculiar to make client code be responsible for
    //      determining the identity of the file that DirectoryImpl will be
    //      using for its backing store, rather than assigning that
    //      responsibility to DirectoryImpl itself.  But the constructor
    //      insists on that role reversal.
    //
    //      Perhaps the primary reason for this arrangement is that
    //      directories (as implemented here) store a FileIdentifier (or
    //      equivalent) for each entry, regardless of that entry's type.  When
    //      the entry names a directory, what's actually recorded is the
    //      identity of its backing store file.
    //
    //      If we were to make directories be full-fledged Celeste objects, we
    //      wouldn't have this distortion.  (But then we would need some way
    //      to interrogate an object to determine what type takes
    //      responsibility for it.)
    //

    /**
     * Constructor to be used by the file system, to convert files of type
     * 'directory' into directories.
     *
     * @param file
     */
    public DirectoryImpl(FileImpl file) {
        //
        // This directory handle is not yet ready for use.  That is, it still
        // needs to be initialized either by creating its backing store file
        // or by reading that file from Celeste.
        //
        this.dataEncodingVersion = 0;
        this.store = file;
    }

    /**
     * <p>
     *
     * Creates a directory using the supplied {@code attributes}, which should be
     * populated using attributes noted as being settable at creation
     * time in {@link sunlabs.celeste.client.filesystem.FileAttributes.Names},
     * with attribute values given as strings.  If {@code clientMetadata} is
     * non-{@code null} any properties it contains are included among the new
     * directory's metadata.
     *
     * </p><p>
     *
     * This method can only be called on a <q>fresh</q> {@code DirectoryImpl},
     * before any directory access or modification operations have taken
     * place.
     *
     * </p>
     *
     * @param parent            the new directory's parent directory
     * @param attributes        attributes for the initial version of the
     *                          directory
     * @param clientProperties  client-supplied properties to be associated
     *                          with the directory's initial version; {@code
     *                          null} if there are none
     * @param invokerCredential the credential to use to do the create
     * @param invokerPassword   a password allowing {@code invokerCredential}
     *                          to produce signatures
     *
     * @throws FileException.BadVersion
     * @throws FileException.CapacityExceeded
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.Deleted
     * @throws FileException.InvalidName
     * @throws FileException.Locked
     * @throws FileException.NotFound
     * @throws FileException.PermissionDenied
     * @throws FileException.RetriesExceeded
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public void create(DirectoryImpl parent, OrderedProperties attributes,
            OrderedProperties clientProperties,
            Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.Exists,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // Ensure that this directory has the proper content type.
        //
        if (attributes == null)
            attributes = new OrderedProperties();
        attributes.setProperty(CONTENT_TYPE_NAME,
            FileProperties.CONTENT_TYPE_DIRECTORY);

        //
        // The sequence below is ordered to ensure that the FileImpl
        // representing this directory as a file is created before the
        // directory contents are created and written.  (Otherwise, the ".."
        // entry won't get the proper serial number.)
        //
        synchronized (this) {
            BeehiveObjectId thisGroupId = BeehiveObjectId.ZERO;

            try {
                this.store.create(attributes, clientProperties, invokerCredential,
                    invokerPassword, thisGroupId);

                OrderedProperties dir = new OrderedProperties();
                dir.setProperty("/version",
                    Integer.toString(DirectoryImpl.defaultDataEncodingVersion));
                String dirent = String.format("%s:%s:%d",
                    this.store.getNameSpaceId().toString(),
                    this.store.getUniqueFileId().toString(),
                    this.store.getSerialNumber());
                dir.setProperty(".", dirent);
                dirent = String.format("%s:%s:%d",
                    parent.store.getNameSpaceId().toString(),
                    parent.store.getUniqueFileId().toString(),
                    parent.store.getSerialNumber());
                dir.setProperty("..", dirent);

                ByteArrayOutputStream dir_out = new ByteArrayOutputStream();
                try {
                    dir.store(dir_out, String.format(
                        "Directory Contents v%d",
                        DirectoryImpl.defaultDataEncodingVersion));
                } catch (IOException e) {
                    throw new FileException.IOException(e);
                }
                byte[] dirData = dir_out.toByteArray();
                this.store.write(dirData, 0, dirData.length, 0L,
                    invokerCredential, invokerPassword);
            } catch (FileException.Exists e) {
                throw (FileException.Exists)e;
            }

            // this makes the local data structure legal
            this.dataEncodingVersion =
                DirectoryImpl.defaultDataEncodingVersion;
        }
    }

    //
    // A note on semantics:  As implemented here, if the directory already has
    // an entry for the name to be linked, whatever used to be there will lose
    // a reference.  If this entry was the only reference, the file it named
    // will become orphaned.  Callers should take this into account.
    //
    public void link(String name, FileImpl file, Credential accessorProfile, char[] password)
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
            FileException.Runtime,
            FileException.ValidationFailed {
        this.link(name, file, accessorProfile, password, true);
    }

    //
    // XXX: Add instrumentation to determine where the time's going.
    //
    //      Still have to figure out how to report this...
    //
    public void link(String name, FileImpl file, Credential accessorProfile,
            char[] requestorPassword, boolean overwriteAllowed)
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
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // XXX: Debugging is unconditionally enabled, because I haven't
        //      devised a way to gracefully turn it on or off here.  (It
        //      doesn't seem right to load down every method in the API with
        //      attributes when most methods don't otherwise need them.)
        //
        //      And now it's unconditionally disabled, since the performance
        //      statistics gathering it enables has served its purpose for the
        //      moment.
        //
        long startTime = System.currentTimeMillis();
        boolean debug = false;

        checkValidFilename(name);

        //
        // If overwrites are allowed, writeDir() will handle retrying until the
        // update succeeds.  Otherwise, it will try only once and retries must
        // be handled here.
        //
        final int retryLimit = overwriteAllowed ? 1 : 5;

        //
        // Obtain the directory's current version at the outset.  If it's
        // changed by the time we try to write back the modified version (and
        // we're not allowing overwrites), retry up to the limit.
        //
        long readDirTime = 0;
        long writeDirTime = 0;
        ExponentialBackoff delayer = new ExponentialBackoff(2, 100);
        for (int retries = 0; retries < retryLimit; retries++) {
            long readStartTime = System.currentTimeMillis();
            BeehiveObjectId currentVersion = overwriteAllowed ?
                null : this.store.getLatestVersionId(false);
            OrderedProperties dir = readDir(accessorProfile, requestorPassword);
            readDirTime += System.currentTimeMillis() - readStartTime;

            //
            // Enforce the restriction implied by !overwriteAllowed.
            //
            if (!overwriteAllowed && dir.containsKey(name))
                throw new FileException.Exists();

            String dirent = String.format("%s:%s:%d",
                file.getNameSpaceId().toString(),
                file.getUniqueFileId().toString(),
                file.getSerialNumber());
            dir.setProperty(name, dirent);
            try {
                long writeStartTime =  System.currentTimeMillis();
                writeDir(dir, accessorProfile, requestorPassword, currentVersion);
                long endTime = System.currentTimeMillis();
                writeDirTime += endTime - writeStartTime;
                if (debug) {
                    //
                    // 1.  time spent in readDir
                    // 2.  time spend in writeDir
                    // 3.  overall time
                    //
                    System.err.printf("DirectoryImpl.link, %d, %d, %d%n",
                        readDirTime,
                        writeDirTime,
                        endTime - startTime);
                }
                return;
            } catch (FileException.RetriesExceeded e) {
                System.err.printf(
                    "DirectoryImpl.link: retries %d, backing off%n", retries);
                if (!overwriteAllowed)
                    delayer.backOff();
                continue;
            }
        }
    }

    public void unlink(String name, Credential accessorProfile,
            char[] requestorPassword)
        throws
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
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        checkValidFilename(name);
        synchronized (this) {
            OrderedProperties dir = readDir(accessorProfile, requestorPassword);
            dir.remove(name);
            writeDir(dir, accessorProfile, requestorPassword, null);
        }
    }

    //
    // This operation has linear cost in the size of the directory.
    //
    public void unlink(FileImpl file, Credential accessorProfile, char[] requestorPassword)
        throws
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
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // Here's where we pay (in extra code, not time complexity) for
        // providing compatibility with directories that contain old
        // (pre-serialNumber) entries.  Ignore the serial number in searching
        // for the file to remove, relying instead only on the representation
        // of its FileIdentifier.
        //
        String fileId = String.format("%s:%s",
            file.getNameSpaceId().toString(),
            file.getUniqueFileId().toString());
        synchronized (this) {
            OrderedProperties dir = readDir(accessorProfile, requestorPassword);
            Iterator<Map.Entry<Object, Object>> it = dir.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Object, Object> entry = it.next();
                Object valueObj = entry.getValue();
                if (!(valueObj instanceof String))
                    continue;
                String value = (String)valueObj;
                //
                // This test assumes that the string representations of all
                // uniqueFileIds have the same length.
                //
                if (!value.startsWith(fileId))
                    continue;

                //
                // Found it -- remove it.
                //
                it.remove();
                writeDir(dir, accessorProfile, requestorPassword, null);
                return;
            }
        }
    }

    /**
     * <p>
     *
     * Set this directory's parent ({@code ..}) directory to {@code
     * newParent}.
     *
     * </p><p>
     *
     * This method is intended for use as part of renaming this directory
     * itself.
     *
     * </p>
     */
    public void setParent(DirectoryImpl newParent, Credential accessorProfile, char[] requestorPassword)
        throws
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
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        if (newParent == null)
            throw new IllegalArgumentException("newParent must be non-null");
        FileImpl newParentImpl = newParent.store;
        String entry = String.format("%s:%s:%d",
            newParentImpl.getNameSpaceId().toString(),
            newParentImpl.getUniqueFileId().toString(),
            newParentImpl.getSerialNumber());
        synchronized (this) {
            OrderedProperties dir = readDir(accessorProfile, requestorPassword);
            dir.setProperty("..", entry);
            writeDir(dir, accessorProfile, requestorPassword, null);
        }
    }

    /**
     * Returns a {@code FileIdentifier} referring to the entry in this
     * directory corresponding to {@code name}.
     *
     * @param accessorProfile   the credential of the entity making the
     *                          inquiry
     * @param accessorPassword  the password for {@code accessorProfile}
     * @param name              the name within this directory of the file
     *                          being looked up
     *
     * @return  the identity of the file corresponding to {@code name}
     *
     * @throws FileException.BadVersion
     * @throws FileException.CapacityExceeded
     *      if this directory has grown so large that it cannot fit into memory
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.Deleted
     * @throws FileException.IOException
     *      if access to this directory fails
     * @throws FileException.NotFound
     *      if this directory does not contain an entry for {@code name}
     * @throws FileException.PermissionDenied
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    //
    // XXX: Why does this method take the accessor password in String form
    //      when most others in this class take it in char[] form?
    //
    public FileIdentifier getFileIdentifier(Credential accessorProfile, String accessorPassword, String name)
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
        OrderedProperties dir = readDir(accessorProfile, accessorPassword.toCharArray());
        String value = dir.getProperty(name);
        if (value == null) {
            throw new FileException.NotFound(name);
        }
        String[] objectIds = value.split(":");
        return new FileIdentifier(
            new BeehiveObjectId(objectIds[0]), new BeehiveObjectId(objectIds[1]));
    }

    /**
     * Return a sorted set containing the names of all files contained in this
     * directory, including {@code "."} and {@code ".."}.
     *
     * @return  a sorted set of the names of all files contained in this
     *          directory
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.Deleted
     * @throws FileException.DirectoryCorrupted
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.PermissionDenied
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public SortedSet<String> getAllNames(Credential accessorProfile, String accessorPassword)
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
        OrderedProperties dir = readDir(accessorProfile, accessorPassword.toCharArray());
        SortedSet<String> names = new TreeSet<String>();
        //
        // XXX: What non-string entries are included in the properties?  (Are
        //      there any?  As far as I can tell there's no code that adds
        //      any.)
        //
        //      This is probably just an artifact of the Properties class and
        //      its derivatives being defined as implementing Map<Object,
        //      Object> rather than Map<String, String>.
        //
        for (Object obj : dir.keySet()) {
            if (obj instanceof String) {
                String name = (String)obj;
                //
                // Don't include entries that are part of the directory's
                // implementation.
                //
                if (!name.contains("/"))
                    names.add(name);
            }
        }
        return names;
    }

    /**
     * Return a sorted set containing a directory entry for each file
     * contained in this directory, including {@code "."}  and {@code ".."}.
     *
     * @return  a sorted set of directory entries describing all the files in
     *          this directory
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.Deleted
     * @throws FileException.DirectoryCorrupted
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.PermissionDenied
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public SortedSet<Dirent> getDirents(Credential accessorProfile, String accessorPassword)
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
        OrderedProperties dir = readDir(accessorProfile, accessorPassword.toCharArray());
        SortedSet<Dirent> dirents = new TreeSet<Dirent>();

        for (Map.Entry<Object, Object> entry : dir.entrySet()) {
            Object keyObj = entry.getKey();
            if (!(keyObj instanceof String))
                continue;
            String key = (String)keyObj;
            //
            // Don't include entries that are part of the directory's
            // implementation.
            //
            if (key.contains("/"))
                continue;
            Object valueObj = entry.getValue();
            if (!(valueObj instanceof String))
                continue;
            String[] values = ((String)valueObj).split(":");
            //
            // Allow for old-format directories that don't contain serial
            // numbers.
            //
            long serialNumber = (values.length >= 3) ?
                Long.parseLong(values[2]) : 0;
            dirents.add(new Dirent(key, serialNumber));
        }
        return dirents;
    }

    public void purgeForever(Credential accessorProfile, char[] password) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DTokenMismatch,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        this.store.purgeForever(accessorProfile, password);
        this.dataEncodingVersion = 0;
    }

    public void markDeleted(Credential accessorProfile, char[] requestorPassword)
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
        this.store.markDeleted(accessorProfile, requestorPassword);
    }

    public boolean directoryExists()  throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.store.fileExists();
    }

    public static boolean isDirectory(FileImpl file) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // XXX: Factor the "is directory" attribute out from content type in
        //      general.  (Make IsDirectory be a hidden attribute that
        //      DirectoryImpl can set and interrogate through a
        //      package-private interface.)
        //
        if (file.getContentType().equals(FileProperties.CONTENT_TYPE_DIRECTORY))
            return true;
        return false;
    }

    /**
     * <p>
     *
     * Acquire the modification lock on this directory, thereby preventing all
     * invocations of operations that modify this directory except ones that
     * supply {@code invokerCredential} as their credential argument.
     *
     * </p><p>
     *
     * The caller of this method should ensure that {@code lockToken} contains
     * a value unique across space and time (so that two distinct locks never
     * have equal tokens).
     *
     * </p><p>
     *
     * Note that the lock this method establishes affects this directory only,
     * not any of its children.  If locking over the subtree rooted at this
     * directory is desired, consider using the locking interfaces provided by
     * {@link sunlabs.celeste.client.filesystem.CelesteFileSystem
     * CelesteFileSystem}.
     *
     * </p>
     *
     * @param invokerCredential the credential of the entity locking the file
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param lockToken         a string whose value distinguishes this lock
     *                          from all others across space and time
     * @param annotation        uninterpreted client-supplied information
     *                          associated with the lock
     */
    public void acquireModificationLock(Credential invokerCredential,
            char[] invokerPassword, String lockToken, Serializable annotation)
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
        this.store.acquireModificationLock(invokerCredential,
            invokerPassword, lockToken, annotation);
    }

    /**
     * Relinquish the modification lock on this directory, thereby allowing
     * unrestricted invocations of operations that modify this directory.
     *
     * @param invokerCredential the credential of the entity unlocking the
     *                          directory
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param lockToken         the string given as argument to the {@link
     *                          #acquireModificationLock(Credential, char[],
     *                          String, Serializable)
     *                          acquireModificationLock()} call that acquired
     *                          the lock being released
     */
    public void releaseModificationLock(Credential invokerCredential, char[] invokerPassword, String lockToken)
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
        this.store.releaseModificationLock(invokerCredential,
            invokerPassword, lockToken);
    }

    public AObjectVersionMapAPI.Lock inspectModificationLock(Credential invokerCredential, char[] invokerPassword)
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
        return this.store.inspectModificationLock(
            invokerCredential, invokerPassword);
    }

    /**
     * Returns a map containing values for each of the attributes contained in
     * {@code attributes} that appear in {@link FileAttributes#attributeSet}.
     * if {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    //
    // Given that the getFileImpl() method is public, this method isn't really
    // needed.  But if it becomes necessary to support a directory-specific
    // set of attributes (as opposed to file-specific ones) or if it becomes
    // necessary to re-interpret any attributes (as would happen should we
    // define ACLs that control directory operations), this method provides an
    // interception point.
    //
    public OrderedProperties getAttributes(Set<String> attributes,
            boolean refetch)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.store.getAttributes(attributes, refetch);
    }

    //
    // Throw an exception if name is not a valid file leaf name (in the POSIX
    // sense) or if name is one of the reserved directory entries ("." or
    // "..").
    //
    // (The last part of this method's name is chosen to match the term
    // "filename" in the POSIX specification.)
    //
    // XXX: The checks below are less strict than they perhaps should be.
    //      They allow names containing an embedded NUL byte, which is
    //      forbidden in POSIX-conformant file names.  Need to think about the
    //      potential interoperability issues.  (UTF-8 encoding allows
    //      arbitrary character sets while respecting this restriction.)
    //
    private void checkValidFilename(String name)
            throws FileException.InvalidName  {
        if (name == null || name.length() == 0 || name.contains("/"))
            throw new FileException.InvalidName();
        if (name.equals(".") || name.equals(".."))
            throw new FileException.InvalidName();
    }

    //
    // Write out the directory.  If initialPredicatedVersion is non-null,
    // insist that the write be to that version, otherwise retry repeatedly
    // until all steps of the operation succeed without conflict with
    // concurrent writers.
    //
    // Since multiple steps are required to accomplish the write (and we don't
    // have transaction support), the version may advance even upon failure.
    //
    // Failure is indicated by throwing FileException.RetriesExceeded.
    //
    // XXX: Add instrumentation to determine where the time's going.
    //
    private void writeDir(OrderedProperties dir,
            Credential accessorProfile, char[] requestorPassword,
            BeehiveObjectId initialPredicatedVersion)
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
            FileException.Runtime,
            FileException.RetriesExceeded,
            FileException.ValidationFailed {

        final int retryLimit = (initialPredicatedVersion == null) ? 5 : 1;

        //
        // XXX: What happens if dir.store() attempts to emit more bytes than
        //      will fit in a java array?  ArrayIndexOutOfBoundsException?
        //      Let's assume so...
        //
        ByteArrayOutputStream dir_out = new ByteArrayOutputStream();
        try {
            dir.store(dir_out, "Directory Contents v" +
                DirectoryImpl.defaultDataEncodingVersion);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new FileException.CapacityExceeded(e);
        } catch (IOException e) {
            throw new FileException.IOException(e);
        }
        ExtentBuffer buffer = ExtentBuffer.wrap(0L, dir_out.toByteArray());
        int length = buffer.remaining();

        //
        // The code below attempts to handle races arising from concurrent
        // attempts to update the directory.  Local locking doesn't suffice,
        // since code running on a different node could be one of the racers.
        // The strategy is to simulate a transaction, with each operation
        // predicated on the most recently observed local version of the
        // directory.  If an operation fails, a competing thread has raced in
        // and the transaction is aborted and retried from the beginning.
        //
        // XXX: The transaction simulation has two weaknesses.
        //
        //      First, it's possible to observe intermediate state.  The test
        //      below against the current length reduces this vulnerability by
        //      only truncating if necessary (thus eliminating an intermediate
        //      zero length state in some cases).
        //
        //      Second, it's vulnerable to livelock.  To ameliorate this
        //      problem, there's a backoff delay before retrying after an
        //      abort.
        //
        //      The synchronized block is intended to eliminate local races,
        //      so that the transaction proper is only required to guard
        //      against races between sets of mutually non-local threads.
        //
        BeehiveObjectId predicatedVersion =
            (initialPredicatedVersion != null) ?
                initialPredicatedVersion : this.store.getLatestVersionId(false);
        int retryCount = 0;
        ExponentialBackoff delayer = new ExponentialBackoff(2, 100);
        for (;;) {
            synchronized (this) {
                try {
                    if (this.store.getFileLength(false) > length) {
                        this.store.truncate(length, accessorProfile,
                                requestorPassword,
                                predicatedVersion);
                        //
                        // So far, so good.  Switch the predicated version to
                        // the new one resulting from the truncate.
                        //
                        predicatedVersion =
                            this.store.getLatestVersionId(false);
                    }
                    this.store.write(buffer, accessorProfile, requestorPassword, predicatedVersion);
                    //
                    // The update sequence has competed without interference
                    // from competing threads, so we're done.
                    //
                    return;
                } catch (FileException.RetriesExceeded e) {
                    //
                    // Predication failed on one of the operations attempted
                    // above.  Fall through to try again from the beginning,
                    // provided that there are retries left to do so.
                    //
                    delayer.backOff();

                }
            }
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
            predicatedVersion = this.store.getLatestVersionId(false);
        }
    }

    //
    // XXX: Add instrumentation to determine where the time's going.
    //
    private OrderedProperties readDir(Credential readerCredential, char[] readerPassword)
            throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.PermissionDenied,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed {
        //
        // Sanity check.  This implementation does not support directories
        // that have grown so large that their external in-file representation
        // can't fit in a Java byte array.  Since writeDir() prevents this
        // from occurring, finding this situation here implies that the
        // directory's been corrupted.
        //
        // XXX: By reading the file through an InputStream, this limitation
        //      could be relaxed.  But it's doubtful that performance for a
        //      hash table-based directory of that size would be acceptable.
        //
        long length = this.store.getFileLength();
        if (length > Integer.MAX_VALUE)
            throw new FileException.DirectoryCorrupted();
        byte[] data = new byte[(int)length];
        int pos = 0;
        int len = (int)length;
        while (len > 0) {
            //
            // XXX: Here's a place where read-by-versionId would be useful.
            //      Lacking it, there's risk that successive reads will draw
            //      from different versions, and therefore that inconsistent
            //      results will ensue.
            //
            int res = this.store.read(readerCredential, readerPassword, data,
                    pos, len, pos);
            if (res == 0)
                break; // end of file reached (e.g. if it got shortened)
            len -= res;
            pos += res;
        }
        if (pos == 0) {
            throw new IllegalStateException("retrieved data is zero length.");
        }

        ByteArrayInputStream props_in = new ByteArrayInputStream(data);
        OrderedProperties dir = new OrderedProperties();
        try {
            dir.load(props_in);
        } catch (IOException e) {
            throw new FileException.IOException(e);
        }

        //
        // XXX: There should be a version check here.  But until such time as
        //      the data encoding version information is captured as file
        //      metadata rather than as a property within the representation
        //      it's trying to characterize, there's little point.  (See the
        //      final XXX in the class-level comment.)
        //
        this.dataEncodingVersion = Integer.parseInt(dir.getProperty("/version"));

        return dir;
    }

    public FileImpl getFileImpl() {
        return this.store;
    }
}
