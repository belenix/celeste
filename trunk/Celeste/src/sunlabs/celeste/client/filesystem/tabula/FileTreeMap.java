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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.EnumSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;

import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.util.ACL;
import sunlabs.celeste.util.ACL.ACLException;

/**
 * Maintains an association between path names and references to corresponding
 * file system objects.  In essence, this class implements multi-level
 * directories.
 */
//
// (Note that "Tree" in the class name refers to the hierarchical name space
// arising from path names, not to the implementation technique the class
// happens to use.)
//
public class FileTreeMap extends TreeMap<PathName, FileTreeMap.OccupantInfo> {
    private static final long serialVersionUID = 0L;

    /**
     * The {@code version} field identifies the version of the external
     * representation of instances of this class.
     */
    public static final String version = String.format("%s;%d",
        FileTreeMap.class.getName(), FileTreeMap.serialVersionUID);

    /**
     * The {@code OwnershipAccessor} interface bundles together methods for
     * obtaining owner and group ids of file system objects.
     */
    public interface OwnershipAccessor {
        /**
         * Returns the object identifier of the owner of the associated file
         * system object.
         */
        public BeehiveObjectId getOwner();

        /**
         * Returns the object identifier of the group of the associated file
         * system object.
         */
        public BeehiveObjectId getGroup();
    }

    /**
     * The {@code Ownership} interface extends {@code OwnershipAccessor} with
     * matching setter methods.
     */
    public interface Ownership extends OwnershipAccessor {
        /**
         * Sets the object identifier that names the owner of the associated
         * file system object.
         *
         * @param owner the new owner id
         */
        public void setOwner(BeehiveObjectId owner);

        /**
         * Sets the object identifier that names the group of the associated
         * file system object.
         *
         * @param group the new group id
         */
        public void setGroup(BeehiveObjectId group);
    }

    /**
     * {@code OccupantInfo} holds directly accessible (available without
     * reference to the underlying Celeste file store) information pertinent
     * to a file system object (occupant) referenced in a {@code FileTreeMap}
     * instance.
     */
    //
    // The motivation is to capture all and only that part of the occupant's
    // overall state that's needed for FileTreeMap operations.
    //
    // XXX: As I implement more and more of lookup(), the amount of state
    //      required here keeps growing.  One hopes it doesn't grow
    //      overwhelming.
    //
    public static class OccupantInfo implements Serializable {
        private static final long serialVersionUID = 0L;

        /**
         * The unique identifier for the file system object that this {@code
         * OccupantInfo} object describes.
         */
        public final FileIdentifier fid;
        private Lock lock;

        public OccupantInfo(FileIdentifier fid, Lock lock) {
            if (fid == null)
                throw new IllegalArgumentException("fid must be non-null");
            this.fid = fid;
            this.lock = lock;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }

        public Lock getLock() {
            return this.lock;
        }

        //
        // XXX: Temporary expedient.  We don't have FileOccupants implemented
        //      yet.  When we do, this method should turn abstract, since we
        //      won't want bare Occupants to be exposed any more.  For now,
        //      code evaluating ACLs against Occupants other than
        //      DirectoryOccupants will find that owner and group are null.
        //
        public CelesteACL.FileAttributeAccessor getFileAttributeAccessor() {
            return new CelesteACL.FileAttributeAccessor() {
                public BeehiveObjectId getOwner() {
                    return null;
                }
                public BeehiveObjectId getGroup() {
                    return null;
                }
            };
        }

        //
        // XXX: Perhaps equality (and therefore the hash code) should be based
        //      only on the fid field.
        //

        @Override
        public boolean equals(Object o) {
            synchronized(this) {
                if (!(o instanceof OccupantInfo))
                    return false;
                OccupantInfo other = (OccupantInfo)o;
                if ((this.lock == null) != (other.lock == null))
                    return false;
                return this.fid.equals(other.fid) &&
                    (this.lock == null || this.lock.equals(other.lock));
            }
        }

        @Override
        public int hashCode() {
            synchronized(this) {
                int lockCode = this.lock == null ? 0 : this.lock.hashCode();
                return lockCode ^ this.fid.hashCode();
            }
        }
    }

    /**
     * A specialization of {@code OccupantInfo} that contains additional
     * information describing directories.
     */
    public static class DirectoryInfo extends OccupantInfo
            implements Ownership {
        private static final long serialVersionUID = 0L;

        //
        // An impedance matcher to allow ACL evaluation to get hold of owner
        // and group information when evaluating a DirectoryACL.
        //
        private class DirectoryInfoAttributeAccessor implements
                CelesteACL.FileAttributeAccessor {
            public BeehiveObjectId getOwner() {
                return DirectoryInfo.this.getOwner();
            }
            public BeehiveObjectId getGroup() {
                return DirectoryInfo.this.getGroup();
            }
        }

        private BeehiveObjectId owner;
        private BeehiveObjectId group;
        private DirectoryACL acl;

        public DirectoryInfo(FileIdentifier fid, Lock lock,
                BeehiveObjectId owner, BeehiveObjectId group,
                DirectoryACL acl) {
            super(fid, lock);
            this.owner = owner;
            this.group = group;
            this.acl = acl;
        }

        public void setOwner(BeehiveObjectId owner) {
            this.owner = owner;
        }

        public BeehiveObjectId getOwner() {
            return this.owner;
        }

        public void setGroup(BeehiveObjectId group) {
            this.group = group;
        }

        public BeehiveObjectId getGroup() {
            return this.group;
        }

        public void setACL(DirectoryACL acl) {
            if (acl == null)
                throw new IllegalArgumentException("acl must be non-null");
            this.acl = acl;
        }

        public DirectoryACL getACL() {
            return this.acl;
        }

        @Override
        public CelesteACL.FileAttributeAccessor getFileAttributeAccessor() {
            return new DirectoryInfoAttributeAccessor();
        }
    }

    /**
     * {@code FileInfo} holds directly accessible (available without reference
     * to the underlying Celeste file store) information pertinent to a file
     * referenced in a {@code FileTreeMap} instance.
     */
    //
    // There's nothing beyond what a basic occupant needs.  The
    // TabulaFileSystem implementation defers access control decisions to
    // Celeste (acting on the owner, group, and ACL held in the metadata held
    // within Celeste's file representation), so there's no reason to keep
    // that information here.
    //
    public static class FileInfo extends OccupantInfo {
        private static final long serialVersionUID = 0L;

        public FileInfo(FileIdentifier fid, Lock lock) {
            super(fid, lock);
        }
    }

    /**
     * Returns the occupant denoted by {@code path}.  If {@code checkLock} is
     * {@code true}, verify that no ancestor of {@code path} in the name space
     * is locked by a principal other than {@code cred}; furthermore, if
     * {@code lockToCheck} is non-{@code null}, verify that no ancestor of
     * {@code path} has a conflicting lock.  (Note that in this last case, the
     * ownership check is made against {@code lockToCheck.locker} rather than
     * against {@code cred.getObjectId()}.
     *
     * @param path          the path name of the desired file system name
     *                      space occupant
     * @param cred          the credential to be used to validate access
     * @param checkLock     if {@code true} the presence of locks not owned by
     *                      {@code cred} along {@code path} should prevent
     *                      access
     * @param lockToCheck   if {@code checkLock} is non-{@code null}, further
     *                      constrains the lock check to prevent access for
     *                      conflicting locks
     *
     * @throws FSException.Locked
     *         if {@code checkLock} is {@code true} and the file system object
     *         named by some component of the path exists, but is locked by an
     *         entity other than the one denoted by {@code cred}, or {@code
     *         lockToCheck} is non-{@code null} as well and it conflicts with
     *         a location along {@code path}; in both cases calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     * @throws FSException.NotDirectory
     *         if the file system object named by a non-leaf component of
     *         {@code path} exists and has no lock forbidding access, but is
     *         not a directory; calling {@link FSException#getPath()
     *         getPath()} on the exception gives the first offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     */
    //
    // Note that NotFound preempts other exceptions that might be thrown.
    // Then comes PermissionDenied.  Finally comes Locked.
    //
    public Occupant lookup(PathName path, Credential cred, boolean checkLock,
            Lock lockToCheck)
        throws
            FSException.Locked,
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        synchronized (this) {
            for (HierarchicalFileSystem.FileName ancestorPath : path) {
                OccupantInfo info = this.get(ancestorPath);
                if (info == null) {
                    //
                    // This slot in the name space is unoccupied.  Throw
                    // NotFound to announce the fact.
                    //
                    throw new FSException.NotFound(ancestorPath);
                }
                //
                // Is this occupant a directory?  For this method's purposes,
                // non-directories are only legitimate as path's last
                // component.
                //
                // XXX: This statement assumes that there's no symlink or
                //      referral support.
                //
                boolean isDirectory = info instanceof DirectoryInfo;
                boolean isLastComponent = path.equals(ancestorPath);
                if (!isDirectory  && !isLastComponent) {
                    throw new FSException.NotDirectory(ancestorPath);
                }

                //
                // Obtain the Occupant at this slot.
                //
                // XXX: This explicit case discrimination is ugly.  There's
                //      got to be a better way...
                //
                FileIdentifier fid = info.fid;
                Occupant occupant = null;
                if (isDirectory) {
                    DirectoryInfo dirInfo = (DirectoryInfo)info;
                    occupant = new Directory(fid, dirInfo.getACL());
                } else {
                    //
                    // XXX: Need to write the File class...  For now, just use
                    //      a plain Occupant.
                    //
                    occupant = new Occupant(fid);
                }

                //
                // Check the occupant's locking status.
                //
                Lock lock = info.getLock();
                if (lock != null && checkLock) {
                    //
                    // If lockToCheck is null, then verify that the lock's
                    // owner matches cred.  Otherwise, the lock token and
                    // depth must be checked as well.
                    //
                    if (lockToCheck == null) {
                        if (!lock.locker.equals(cred.getObjectId()))
                            throw new FSException.Locked(ancestorPath, lock);
                    } else {
                        if (lockToCheck.conflictsWith(lock))
                            throw new FSException.Locked(ancestorPath, lock);
                    }
                }

                if (isLastComponent)
                    return occupant;

                //
                // Do we have permission to perform a lookup operation on this
                // directory?
                //
                DirectoryInfo dirInfo = (DirectoryInfo)info;
                try {
                    dirInfo.getACL().check(DirectoryACL.DirectoryOps.lookup,
                        info.getFileAttributeAccessor(), cred.getObjectId());
                } catch (ACLException e) {
                    throw new FSException.PermissionDenied(ancestorPath, e);
                }
            }
        }
        //
        // Not reached.
        //
        return null;
    }

    /**
     * Returns the contents of the directory named by {@code path}, in the
     * form of a set of paths leading to the directory's entries.
     *
     * @param path  the path name of the directory whose contents are to be
     *              listed
     * @param cred  the credential to be used to validate access
     *
     * @return a set of path names listing {@code path}'s contents
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any component of {@code
     *         path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies access to the principal that {@code cred} denotes;
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the offending component
     */
    public SortedSet<PathName> list(PathName path, Credential cred) throws
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        return this.doList(path, true, cred);
     }

    /**
     * Returns a set of path names containing all descendants of {@code path}
     * in this file system.
     *
     * @param path  the path name of the directory whose descendants are to be
     *              returned
     * @param cred  the credential to be used to validate access
     *
     * @return a set of path names listing {@code path}'s descendants
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any component of {@code
     *         path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies access to the principal that {@code cred} denotes;
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the offending component
     */
    //
    // XXX: Proper descendants or all descendants?
    //
    public SortedSet<PathName> listDescendants(PathName path, Credential cred)
        throws
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        return this.doList(path, false, cred);
    }

    /**
     * Returns a set
     *
     * @param path          the path name of the directory whose contents are
     *                      to be listed
     * @param directOnly    if {@code true}, return direct descendants only;
     *                      otherwise, return return paths for the entire
     *                      subtree rooted at {@code path}
     * @param cred          the credential to be used to validate access
     *
     * @return a set of path names listing {@code path}'s contents
     *
     * @throws FSException.NotDirectory
     *         if the file system object named by any component of {@code
     *         path} exists, but is not a directory; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         offending component
     * @throws FSException.NotFound
     *         if the file system name space position at some component of
     *         {@code path} is unoccupied; calling {@link
     *         FSException#getPath() getPath()} on the exception gives the
     *         first missing component
     * @throws FSException.PermissionDenied
     *         if the access control list for some component of {@code path}
     *         denies access to the principal that {@code cred} denotes;
     *         calling {@link FSException#getPath() getPath()} on the
     *         exception gives the offending component
     */
    private SortedSet<PathName> doList(PathName path, boolean directOnly,
            Credential cred)
        throws
            FSException.NotDirectory,
            FSException.NotFound,
            FSException.PermissionDenied {
        if (path == null)
            throw new IllegalArgumentException("path must be non-null");

        synchronized(this) {
            //
            // Verify that path names a directory.  Since this operation does
            // not modify state, locks along path should not prevent access.
            //
            Occupant occupant = null;
                try {
                    occupant = this.lookup(path, cred, false, null);
                } catch (FSException.Locked e) {
                    //
                    // Since we've asked that locks not be checked, this
                    // exception should not occur.
                    //
                    e.printStackTrace(System.err);
                }
            if (!(occupant instanceof Directory))
                throw new FSException.NotDirectory(path);

            return path.getDescendants(
                (SortedSet<PathName>)this.keySet(), directOnly);
        }
    }

    //
    // XXX: Is the factoring right for storeInFile() and fetchFromFile()?
    //      Perhaps they should just serialize and deserialize (to byte
    //      arrays) rather than drag in all the FileImpl machinery.
    //

    /**
     * Returns a byte array containing the serialization of this instance.
     */
    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            //
            // This exception should not occur, since the i/o was done
            // in memory.
            //
            e.printStackTrace(System.err);
            return null;
        } finally {
            try { oos.close(); } catch (IOException ignore) {}
            try { baos.close(); } catch (IOException ignore) {}
        }
    }

    /**
     * Reconstructs a {@code FileTreeMap} instance from its serialization.
     *
     * @param serialization a byte array resulting from a call to {@link
     *                      #serialize() serialize()}
     *
     * @return a reconstituted file tree map
     */
    public static FileTreeMap deserialize(byte[] serialization) throws
            FSException.InternalFailure {
        FileTreeMap result = null;
        ByteArrayInputStream bais = new ByteArrayInputStream(serialization);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(bais);
            result = (FileTreeMap)ois.readObject();
        } catch (IOException e) {
            //
            // This exception should not occur, since the i/o was done
            // in memory.
            //
            e.printStackTrace(System.err);
        } catch (ClassNotFoundException e) {
            //
            // Something's gone wrong in the store/fetch sequence, e.g.  the
            // file got garbled or there's version skew.
            //
            throw new FSException.InternalFailure(null, e);
        } finally {
            try {
                if (ois != null)
                    ois.close();
            } catch (IOException ignore) {
            }
            try { bais.close(); } catch (IOException ignore) {}
        }
        return result;
    }

    /**
     * Returns the path name of a lock conflicting with {@code proposedLock}
     * at {@code path} if there is one and {@code null} otherwise.
     *
     * @param path          the base of a subtree in the file system name
     *                      space that is to be examined for locks conflicting
     *                      with {@code proposedLock}
     * @param proposedLock  the lock to be checked for conflicts
     *
     * @return  a lock conflicting with {@code proposedLock} if there is one
     *          and {@code null} otherwise
     */
    public PathName conflictingLock(PathName path, Lock proposedLock) {
        synchronized(this) {
            if (proposedLock == null)
                throw new IllegalArgumentException(
                    "proposedLockArgument must be non-null");
            //
            // Iterate over the subtree rooted at path looking for a conflict.
            // Rather than reifying the sub-map representing the subtree, the
            // iteration proceeds over the entire tree, discarding leading
            // paths that aren't part of the subtree and stopping when the
            // iteration leaves the subtree.
            //
            boolean foundSubtreeRoot = false;
            for (Map.Entry<PathName, OccupantInfo> entry : this.entrySet()) {
                PathName p = entry.getKey();
                //
                // Skip until we reach the path.
                //
                if (!foundSubtreeRoot) {
                    if (!path.equals(p))
                        continue;
                    foundSubtreeRoot = true;
                }
                //
                // If path is not an ancestor of p, we've passed beyond the
                // subtree rooted at path, so we're done.
                //
                if (!path.isAncestor(p))
                    break;

                Lock lock = entry.getValue().getLock();
                if (lock == null)
                    continue;
                //
                // Having found a lock in the subtree, check it for a
                // conflict.
                //
                if (proposedLock.conflictsWith(lock))
                    return p;
            }
            return null;
        }
    }

    //
    // XXX: Testing/debugging code
    //
    private final static BeehiveObjectId nameSpaceId = new BeehiveObjectId();
    private final static BeehiveObjectId owner = new BeehiveObjectId();
    private final static BeehiveObjectId group = new BeehiveObjectId();
    private final static DirectoryACL acl = new DirectoryACL(
        new DirectoryACL.DirectoryACE(new CelesteACL.AllMatcher(),
            EnumSet.allOf(DirectoryACL.DirectoryOps.class),
            ACL.Disposition.grant)
    );

    private static FileIdentifier newFid() {
        return new FileIdentifier(nameSpaceId, new BeehiveObjectId());
    }

    //
    // Exercise lookup() and list() a bit.
    //
    public static void main(String[] args) throws Exception {
        Credential cred = new Profile_("me", "my-password".toCharArray());
        FileTreeMap fs = new FileTreeMap();
        PathName root = new PathName("/");
        PathName a = new PathName("/a");
        PathName ab = new PathName("/a/b");
        PathName abc = new PathName("/a/b/c");
        PathName abcf = new PathName("/a/b/c/f");
        PathName abd = new PathName("/a/b/d");
        PathName abe = new PathName("/a/b/e");
        Lock abdLock = new Lock(cred.getObjectId(), Lock.Depth.ZERO, "a token");
        Lock abeLock =
            new Lock(new BeehiveObjectId(), Lock.Depth.ZERO, "a token");

        fs.put(new PathName("/"),
            new DirectoryInfo(newFid(), null, owner, group, acl));
        fs.put(new PathName("/a"),
            new DirectoryInfo(newFid(), null, owner, group, acl));
        fs.put(new PathName("/a/b"),
            new DirectoryInfo(newFid(), null, owner, group, acl));
        fs.put(new PathName("/a/b/c"),
            new DirectoryInfo(newFid(), null, owner, group, acl));
        fs.put(new PathName("/a/b/c/f"),
            new OccupantInfo(newFid(), null));
        fs.put(new PathName("/a/b/d"),
            new OccupantInfo(newFid(), abdLock));
        fs.put(new PathName("/a/b/e"),
            new OccupantInfo(newFid(), abeLock));

        try {
            Occupant occupant = fs.lookup(ab, cred, false, null);
            System.out.printf("/a/b --> %s%n", occupant);
            occupant = fs.lookup(abc, cred, true, null);
            System.out.printf("/a/b/c --> %s%n", occupant);
            //
            // Expected to fail (due to missing path name component)...
            //
            try {
                occupant = fs.lookup(new PathName("/b/x"), cred, true, null);
            } catch (FSException.NotFound nf) {
                System.err.printf("/b/x --> NotFound: %s%n", nf.getPath());
            }
            occupant = fs.lookup(abd, cred, true, null);
            System.out.printf("/a/b/d --> %s%n", occupant);
            //
            // Expected to fail (due to conflicting locker)...
            //
            try {
                occupant = fs.lookup(new PathName("/a/b/e"), cred, true, null);
            } catch (FSException.Locked l) {
                System.err.printf("/a/b/e --> Locked: %s%n", l.getPath());
            }
            SortedSet<PathName> abEntries = fs.list(ab, cred);
            System.out.printf("/a/b entries: %s%n", abEntries);
        } catch (FSException e) {
            e.printStackTrace(System.err);
        }
    }
}
