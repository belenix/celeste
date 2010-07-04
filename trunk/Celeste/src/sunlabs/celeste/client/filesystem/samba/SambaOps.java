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

package sunlabs.celeste.client.filesystem.samba;

import java.io.EOFException;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import sunlabs.asdf.util.Time;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.OrderedProperties;

import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.simple.DirectoryImpl.Dirent;
import sunlabs.celeste.client.filesystem.tabula.PathName;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.node.ProfileCache;


/**
 * Provides a set of system call-like file operations needed to support the
 * Samba protocol.
 */
//
// XXX: Probably should be generalized to support the set of file-related
//      POSIX system calls.
//
// Both the protocol and the POSIX file-related system calls are stateful, so
// the class maintains the requisite state.  This state consists of per-file
// information such as the file offset at which the next read or write will
// occur and file access modes, and of per-client (e.g., process) information,
// such as descriptor tables whose entries reference per-file information.
//
// XXX: Should the per-file state class be factored out into a separate file?
//      (The now-deleted filesystem.simple.FileDescriptor class was a feeble
//      attempt at this.)
//
// XXX: Ought to implement Closeable, with the close() method cleaning up
//      accumulated state (such as open files and directories).
//
// XXX: Need to re-examine the division of responsibility among FileImpl,
//      CelesteFileSystem, this class, SMBServer, and the celstore VFS plugin
//      for smbd.  Some of the things that celstore does (e.g., faking up
//      inode numbers) ought to be pushed back toward Celeste, with the goal
//      of coming closer to POSIX compliance (for clients that care about it).
//
public class SambaOps {
    /**
     * Instances of {@code Result} subclasses convey the results of Samba
     * operations back to their caller.  Before examining the specific results
     * of a non-void operation, callers should invoke {@code isSuccessful()}
     * to verify that the operation succeeded; the specific results are
     * meaningful only if so.  When an operation has failed, {@code
     * getFailure()} returns a {@code Throwable} that captures the failure's
     * cause.
     */
    //
    // XXX: Probably ought to replace Throwable with (a new) SyscallException
    //      (class) to convey failure information.
    //
    public static abstract class Result {
        //
        // If non-null, captures why the operation failed.
        //
        private Throwable failure = null;

        //
        // Result information common to all subclasses goes here.  That boils
        // down to whether or not the operation succeeded (and how it failed
        // if it did).
        //

        public Throwable getFailure() {
            return failure;
        }

        //
        // XXX: Perhaps this method should be given reduced visibility, since
        //      only Samba method implementations should use it.
        //
        public void setFailure(Throwable failure) {
            this.failure = failure;
        }

        public boolean isSuccessful() {
            return failure == null;
        }

        /**
         * Encapsulates a result bearing various file attributes.
         */
        //
        // N.B. Only attributes that Samba cares about are included here.
        //      This is far from the complete set of POSIX attributes.
        //
        // XXX: This code follows a long tradition in storing user and group
        //      ids as integral types.  But perhaps they should be stored as
        //      Strings or even as (Microsoft-style) sids.  But that's really
        //      a matter for an XXX of much larger scope than this one.
        //
        public static class Attrs extends Result {
            private final long serialNumber;
            private final long size;
            private final long uid;
            private final long gid;
            private final long modTime;
            private final long cTime;
            //
            // XXX: Need to provide definitions of the various mode bits.
            //
            private final int mode;

            /**
             * @param modTime   the time the file was last modified, expressed
             *                  in units of seconds
             * @param cTime     the time the file's metadata was last
             *                  changed, expressed in units of seconds
             */
            public Attrs(long serialNumber, long size, long uid, long gid,
                    long modTime, long cTime, int mode) {
                super();
                this.serialNumber = serialNumber;
                this.size = size;
                this.uid = uid;
                this.gid = gid;
                this.modTime = modTime;
                this.cTime = cTime;
                this.mode = mode;
            }

            //
            // A cop-out version for use with code that doesn't yet understand
            // the uid and gid fields.
            //
            /**
             * @param modTime   the time the file was last modified, expressed
             *                  in units of seconds
             * @param cTime     the time the file's metadata was last
             *                  changed, expressed in units of seconds
             */
            public Attrs(long serialNumber, long size, long modTime,
                    long cTime, int mode) {
                super();
                this.serialNumber = serialNumber;
                this.size = size;
                this.uid = -1;
                this.gid = -1;;
                this.modTime = modTime;
                this.cTime = cTime;
                this.mode = mode;
            }

            public long getSerialNumber() {
                return this.serialNumber;
            }

            public long getSize() {
                return this.size;
            }

            public long getUid() {
                return this.uid;
            }

            public long getGid() {
                return this.gid;
            }

            public long getModTime() {
                return this.modTime;
            }

            public long getCTime() {
                return this.cTime;
            }

            public int getMode() {
                return this.mode;
            }

            /**
             * Return an {@code Attr} whose fields all contain invalid values
             * and such that {@code getFailure()} will return {@code t}.
             */
            public static Attrs noAttrs(Throwable t) {
                Attrs none = new Attrs(-1L, -1L, -1L, -1L, -1L, -1L, 0);
                none.setFailure(t);
                return none;
            }
        }

        /**
         * <p>
         *
         * Encapsulates a result bearing a byte array.
         *
         * </p><p>
         *
         * If {@code getBytes() != null} holds, then {@code getLength() <=
         * getBytes().size} will hold as well.
         *
         * </p>
         */
        public static class Bytes extends Result {
            private final byte[] bytes;
            private final int length;

            public Bytes(byte[] bytes, int length) {
                if (bytes != null && bytes.length < length)
                    throw new IllegalArgumentException(
                        "length extends beyond bytes.size");
                this.bytes = bytes;
                this.length = length;
            }

            public byte[] getBytes() {
                return this.bytes;
            }

            public int getLength() {
                return this.length;
            }
        }

        /**
         * Encapsulates a directory entry result.
         */
        public static class DirentAndOffset extends Result {
            private final String componentName;
            private final long componentSerialNumber;
            private final long offset;

            public DirentAndOffset(String componentName,
                    long componentSerialNumber, long offset) {
                this.componentName = componentName;
                this.componentSerialNumber = componentSerialNumber;
                this.offset = offset;
            }

            //
            // Version for use when serial numbers for entries aren't
            // available.
            //

            public DirentAndOffset(String componentName, long offset) {
                this(componentName, -1L, offset);
            }

            public String getComponentName() {
                return this.componentName;
            }

            public long getComponentSerialNumber() {
                return this.componentSerialNumber;
            }

            public long getOffset() {
                return this.offset;
            }
        }

        /**
         * Encapsulates a file descriptor index result.
         */
        public static class FD extends Result {
            private final int fd;

            public FD(int fd) {
                super();
                this.fd = fd;
            }

            public int getFd() {
                return this.fd;
            }
        }

        /**
         * Encapsulates a length result.
         */
        public static class Length extends Result {
            private final int length;

            public Length(int length) {
                this.length = length;
            }

            public int getLength() {
                return this.length;
            }
        }

        /**
         * Encapsulates an offset (file or directory) result.
         */
        public static class Offset extends Result {
            private final long offset;

            public Offset(long offset) {
                this.offset = offset;
            }

            public long getOffset() {
                return this.offset;
            }
        }

        /**
         * Encapsulates a path name result.
         */
        public static class Path extends Result {
            private final String path;

            public Path(String path) {
                super();
                this.path = path;
            }

            public String getPath() {
                return this.path;
            }
        }

        /**
         * Encapsulates a result consisting of a single pathname component.
         */
        public static class PathComponent extends Result {
            private final String componentName;

            public PathComponent(String componentName) {
                this.componentName = componentName;
            }

            public String getComponentName() {
                return this.componentName;
            }
        }

        /**
         * Encapsulates a void result.
         */
        public static class Void extends Result {
            //
            // No behavior beyond that of Result itself.
            //
        }
    }

    /**
     * <p>
     *
     * The {@code SambaModes} class provides constants and static methods for
     * interpreting file modes.
     *
     * </p><p>
     *
     * This implementation is partial; a complete characterization requires
     * constants and methods not provided here.
     *
     * </p>
     */
    public static class SambaModes {
        //
        // Prevent instantiation.
        //
        private SambaModes() {
        }

        public static final int SM_isDirectory  = 0x4000;
        public static final int SM_isRegular    = 0x8000;

        public static int accessPermissions(int sambaModeBits) {
            return sambaModeBits & 0777;
        }

        public static int fileType(int sambaModeBits) {
            return sambaModeBits & 0xF000;
        }
    }

    /**
     * The {@code SambaFlags} class provides constants and static methods for
     * interpreting flags supplied to the {@code SambaOps} {@link
     * sunlabs.celeste.client.filesystem.samba.SambaOps#open(String, int, int)
     * open()} method.  The flags bear a deceptive resemblance to those
     * defined for the {@code open()} system call on POSIX systems, but
     * there's no guarantee that their values are identical.
     */
    public static class SambaFlags {
        //
        // Prevent instantiation.
        //
        private SambaFlags() {
        }

        /** Open for reading only */
        public static final int O_RDONLY    =  1;

        /** Open for writing only */
        public static final int O_WRONLY    =  2;

        /** Open for both reading and writing */
        public static final int O_RDWR      =  3;

        //
        // Selects the bits holding the read/write permissions given above.
        //
        private static final int rwMask = 3;

        /** Append mode (all writes forced to end of file) */
        public static final int O_APPEND    =  4;

        /** Open with file create (mode argument is relevant) */
        public static final int O_CREAT     =  8;

        /** Open with truncation */
        public final static int O_TRUNC     = 16;

        /** Exclusive open */
        public final static int O_EXCL      = 32;

        public static boolean readsAllowed(int flags) {
            int rwBits = flags & rwMask;
            return rwBits == O_RDONLY || rwBits == O_RDWR;
        }

        public static boolean writesAllowed(int flags) {
            int rwBits = flags & rwMask;
            return rwBits == O_WRONLY || rwBits == O_RDWR;
        }
    }

    /**
     * The {@code SambaSeek} enumeration provides constants defining seek
     * directives.  The values associated with the constants are consistent
     * with those defined for POSIX-conformant systems.
     */
    public enum SambaSeek {
        SEEK_SET(0),
        SEEK_CUR(1),
        SEEK_END(2);

        private final int value;

        SambaSeek(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    //
    // Names of properties appearing in OrderedProperties objects used as
    // SambaOps-specific client metadata.
    //
    // XXX: These (along with the support classes defined above) ought to move
    //      into a POSIX filesystem package, since that's what they really
    //      concern.  (Samba just piggybacks on them.)
    //
    // XXX: Now that Celeste itself has a notion of owner, group, and ACL,
    //      these properties need to be phased out in favor of using the
    //      Celeste facilities.
    //
    private final static String MODE_NAME = "FileMode";
    private final static String OWNER_NAME = "UidValue";
    private final static String GROUP_NAME = "GidValue";

    //
    // XXX: Use the SyscallException class to represent failure information?
    //

    //
    // Common behavior for open file and directory state information.
    //
    // XXX: Is a reference count needed?  That is, is it necessary to worry
    //      about properly supporting operations such as dup(), which can
    //      result in multiple references to the same underlying object?
    //      (Things like close on last reference come to mind.)
    //
    //      Such support doesn't appear to be needed to implement the Samba
    //      protocol.
    //
    //      Given this decision, once could contemplate using
    //      CelesteFileSystem.File's position attribute in place of the offset
    //      attribute defined below.  The fly in the ointment is directories:
    //      the implementation of the Opendirectory subclass relies on
    //      treating each entry in a directory as contributing an offset
    //      increment of 1.
    //
    private static abstract class OpenFileOrDir {
        private final HierarchicalFileSystem.File    file;
        private long                            offset = 0L;

        protected OpenFileOrDir(HierarchicalFileSystem.File file) {
            this.file = file;
        }

        protected synchronized long getOffset() {
            return this.offset;
        }

        protected synchronized void setOffset(long newOffset) {
            this.offset = newOffset;
        }

        public HierarchicalFileSystem.File getFile() {
            return this.file;
        }
    }

    //
    // Holds per-file state information.
    //
    // XXX: Should consider factoring this class and its subclasses out from
    //      SambaOps.java.  (This issue will become more pressing at such time
    //      as we add NFS support.  At that time, we'll also need to provide
    //      more complete semantics for things like file attributes, which
    //      will require the interfaces to become more complex than these
    //      classes are now.)
    //
    private static class OpenFile extends OpenFileOrDir {
        private final boolean   allowRead;
        private final boolean   allowWrite;
        private final boolean   appendOnly;

        public OpenFile(HierarchicalFileSystem.File file, boolean allowRead, boolean allowWrite, boolean appendOnly) {
            super(file);
            this.allowRead = allowRead;
            this.allowWrite = allowWrite;
            this.appendOnly = appendOnly;
        }

        public boolean readsAllowed() {
            return this.allowRead;
        }

        public boolean writesAllowed() {
            return this.allowWrite;
        }

        public boolean isAppendOnly() {
            return this.appendOnly;
        }
    }

    //
    // Holds per-directory state information and implements entry enumeration.
    //
    // Each instance binds the the version of its underlying directory that is
    // current at creation time, so that subsequent modifications to (or
    // deletion of) that directory do not affect sequences of readdir() calls.
    //
    // XXX: This implementation is not industrial strength, in that it relies
    //      on the CelesteFileSystem.Directory.list() method, which assumes
    //      that it's reasonable to ingest all the file names contained in a
    //      given directory into an array.  (But on the other hand, this
    //      assumption saves a lot of ugly implementation work.)
    //
    private static class OpenDirectory extends OpenFileOrDir {
        private final Dirent[]  dirents;

        public OpenDirectory(CelesteFileSystem.Directory directory) {
            //
            // Use the fact that CelesteFileSystem.Directory is a subclass of
            // CelesteFileSystem.File.
            //
            super(directory);
            Dirent[] dirents = null;
            try {
                dirents = directory.getDirents();
            } catch (FileException e) {
                //
                // No need to discriminate among FileException subclasses;
                // for this purpose, thry're all the same.
                //
                dirents = new Dirent[0];
            }
            this.dirents = dirents;
        }

        public Result.DirentAndOffset readdir() throws EOFException {
            synchronized (this) {
                long offset = this.getOffset();
                if (offset >= this.dirents.length)
                    throw new EOFException();
                Dirent dirent = this.dirents[(int)offset];
                //
                // N.B.  Assumes directory offsets increment by one per-entry,
                // as opposed to by the size of the entry as represented in
                // the underlying file.
                //
                this.setOffset(offset + 1);
                return new Result.DirentAndOffset(dirent.leafName,
                    dirent.serialNumber, offset);
            }
        }

        public void rewinddir() {
            synchronized (this) {
                this.setOffset(0L);
            }
        }

        //
        // On success returns the previous offset.  An attempt to seek beyond
        // the end of file is treated as a seek to end of file.
        //
        public long seekdir(long newOffset) throws IOException {
            synchronized (this) {
                if (newOffset < 0L)
                    throw new IOException("negative offset");
                if (newOffset > this.dirents.length)
                    newOffset = this.dirents.length;
                long oldOffset = this.getOffset();
                this.setOffset(newOffset);
                return oldOffset;
            }
        }

        public long telldir() {
            synchronized (this) {
                return this.getOffset();
            }
        }
    }

    //
    // Access to a Celeste file system.
    //
    // XXX: Do we need to support mounts?  If so, we'll need to manage access
    //      to a set of these, arranged as a hierarchy.
    //
    //      By analogy to the NFS server, mount supprt isn't necessary.  It's
    //      the client's responsibility (at least in NFS protocol versions <
    //      4) to support stitching name spaces together.  The server just
    //      needs to concern itself with the file system that an instance of
    //      itself is exporting.
    //
    private final CelesteFileSystem fileSystem;

    //
    // The root is the initial current working directory.
    //
    private PathName    cwdPath = PathName.ROOT;

    //
    // XXX: Need a map of OpenFile objects, indexed by descriptors
    //      (represented as ints), as well as code to manage it.  Perhaps
    //      another nested class...  For now, try writing it without
    //      encapsulation.
    //
    private final Map<Integer, OpenFileOrDir> openFilesOrDirectories =
        new HashMap<Integer, OpenFileOrDir>();
    private final BitSet descriptorsInUse = new BitSet();

    //
    // The constructor needs a reference to a CelesteFileSystem, as that's the
    // vehicle for getting access to Celeste files.
    //
    // XXX: Take arguments sufficient to identify the file system, and build
    //      it here?
    //
    public SambaOps(CelesteFileSystem fileSystem) {
        if (fileSystem == null)
            throw new IllegalArgumentException("fileSystem must be non-null");
        this.fileSystem = fileSystem;
    }

    //
    // Implementations of methods invokable via the Samba protocol.  To ease
    // search, they appear in alphabetical order.
    //

    public Result.Void chdir(String path) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                PathName pathName = new PathName(path).resolve(this.cwdPath);
                //
                // Even though we're not going to retain the new working
                // directory, look it up, so that errors (if any) are
                // triggered.
                //
                this.lookUpDirectory(path);
                this.cwdPath = pathName;
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void chmod(String fname, int mode) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                CelesteFileSystem.File file = this.lookUpFile(fname);
                //
                // Update the mode entry in the file's metadata.  Only the
                // access permission bits should be altered.
                //
                OrderedProperties props = file.getClientProperties();
                String modeString = props.getProperty(SambaOps.MODE_NAME);
                int newMode = Integer.parseInt(modeString);
                newMode &= ~SambaModes.accessPermissions(0xffffffff);
                newMode |= SambaModes.accessPermissions(mode);
                props.setProperty(SambaOps.MODE_NAME, String.valueOf(newMode));
                file.setClientProperties(props);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void chown(String fname, long uid, long gid) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                CelesteFileSystem.File file = this.lookUpFile(fname);
                //
                // NYI...
                //
                result.setFailure(new UnsupportedOperationException(
                    "chown not yet implemented"));
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void close(int fd) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                //
                // Note that, by calling close() unconditionally, the code
                // below assumes that descriptors have a 1-1 mapping to
                // CelesteFileSystem.File (or subclasses thereof) objects.
                // That is, this code will become incorrect if dup() or a
                // similar operation is added.
                //
                OpenFile of = getOpenFile(fd);
                of.getFile().close();
                this.openFilesOrDirectories.remove(fd);
                this.descriptorsInUse.clear(fd);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void closedir(int fd) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                //
                // Note that, by calling close() unconditionally, the code
                // below assumes that descriptors have a 1-1 mapping to
                // CelesteFileSystem.File (or subclasses thereof) objects.
                // That is, this code will become incorrect if dup() or a
                // similar operation is added.
                //
                OpenDirectory od = getOpenDirectory(fd);
                od.getFile().close();
                this.openFilesOrDirectories.remove(fd);
                this.descriptorsInUse.clear(fd);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void fchmod(int fd, int mode) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                HierarchicalFileSystem.File file = ofd.getFile();
                //
                // Update the mode entry in the file's metadata.  Only the
                // access permission bits should be altered.
                //
                OrderedProperties props = file.getClientProperties();
                String modeString = props.getProperty(SambaOps.MODE_NAME);
                int newMode = Integer.parseInt(modeString);
                newMode &= ~SambaModes.accessPermissions(0xffffffff);
                newMode |= SambaModes.accessPermissions(mode);
                props.setProperty(SambaOps.MODE_NAME, String.valueOf(newMode));
                file.setClientProperties(props);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void fchown(int fd, long uid, long gid) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                HierarchicalFileSystem.File file = ofd.getFile();
                //
                // NYI...
                //
                result.setFailure(new UnsupportedOperationException(
                    "fchown not yet implemented"));
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Attrs fstat(int fd) {
        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                HierarchicalFileSystem.File file = ofd.getFile();
                return this.doStat(file);
            }
        } catch (Throwable t) {
            return Result.Attrs.noAttrs(t);
        }
    }

    public Result.Void ftruncate(int fd, int offset) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                HierarchicalFileSystem.File file = ofd.getFile();
                file.truncate((long)offset);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Path getwd() {
        synchronized (this) {
            return new Result.Path(this.cwdPath.toString());
        }
    }

    public Result.Offset lseek(int fd, int offset, SambaSeek whence) {
        Result.Offset result = null;
        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                HierarchicalFileSystem.File file = ofd.getFile();

                switch (whence) {
                case SEEK_SET:
                    ofd.setOffset(offset);
                    break;
                case SEEK_CUR:
                    ofd.setOffset(ofd.getOffset() + offset);
                    break;
                case SEEK_END:
                    ofd.setOffset(file.length() + offset);
                    break;
                }

                result = new Result.Offset(ofd.getOffset());
            }
        } catch (Throwable t) {
            result = new Result.Offset(-1);
            result.setFailure(t);
        }

        return result;
    }

    //
    // Mkdir with unspecified credentials (uid and gid set to "don't know").
    //
    public Result.Void mkdir(String dirName, int mode) {
        return this.mkdir(dirName, -1, -1, mode);
    }

    public Result.Void mkdir(String dirName, long uid, long gid, int mode) {
        Result.Void result = new Result.Void();

        try {
            //
            // Annotate the new directory with metadata for owner, mode,
            // and so on.
            //
            OrderedProperties props = new OrderedProperties();
            int fullMode =
                SambaModes.accessPermissions(mode) | SambaModes.SM_isDirectory;
            props.setProperty(SambaOps.MODE_NAME, String.valueOf(fullMode));
            if (uid != -1)
                props.setProperty(SambaOps.OWNER_NAME, String.valueOf(uid));
            if (gid != -1)
                props.setProperty(SambaOps.GROUP_NAME, String.valueOf(gid));

            synchronized (this) {
                PathName dirPath = new PathName(dirName).resolve(this.cwdPath);
                this.fileSystem.createDirectory(dirPath, props);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    //
    // Open with unspecified credentials (uid and gid set to "don't know").
    //
    public Result.FD open(String fname, int flags, int mode) {
        return this.open(fname, -1, -1, flags, mode);
    }

    public Result.FD open(String fname, long uid, long gid, int flags,
            int mode) {
        Result.FD result = null;

        try {
            //
            // XXX: This synchronization protects only accesses make through
            //      this Samba connection.  The code is vulnerable to
            //      concurrent modifications made by other means.
            //
            synchronized (this) {
                PathName filePath = new PathName(fname).resolve(this.cwdPath);
                HierarchicalFileSystem.File file = null;

                //
                // Create the file if requested.
                //
                if ((flags & SambaFlags.O_CREAT) != 0) {
                    file = this.doCreat(filePath, uid, gid, flags, mode);
                }

                //
                // If we still don't have the file, try to dig it up.
                //
                // XXX: The FSInstance version of this code made provision for
                //      allowing a directory to be opened read-only (which
                //      Windows apparently allows) by copying its contents to
                //      a new anonymous file and returning a descriptor
                //      referring to that file.  We won't go there unless a
                //      use case we care about forces us to.
                //
                if (file == null) {
                    try {
                        file = this.lookUpFile(filePath);
                    } catch (IOException ignore) {
                    }
                }

                //
                // At this point, it's an error if file is still null.
                // (Creation, if requested, has already occurred.)
                //
                // XXX: Use SyscallException?
                //
                if (file == null) {
                    result = new Result.FD(-1);
                    result.setFailure(new IOException(
                        "no such file or directory"));
                    return result;
                }

                //
                // We disallow writes to directories.
                //
                if (file instanceof CelesteFileSystem.Directory) {
                    if (SambaFlags.writesAllowed(flags)) {
                    result = new Result.FD(-1);
                    result.setFailure(new IOException(
                        "directories may not be written to"));
                    return result;
                    }
                }

                if ((flags & SambaFlags.O_TRUNC) != 0)
                    file.truncate(0L);

                //
                // Set up an OpenFile instance for this file that respects the
                // access restrictions given in flags and allocate a file
                // descriptor for it.
                //
                OpenFile of = new OpenFile(file,
                    SambaFlags.readsAllowed(flags),
                    SambaFlags.writesAllowed(flags),
                    (flags & SambaFlags.O_APPEND) != 0);
                result = allocFd(of);
            }
        } catch (Throwable t) {
            result = new Result.FD(-1);
            result.setFailure(t);
        }

        return result;
    }

    public Result.FD opendir(String fname) {
        Result.FD result = null;

        try {
            synchronized (this) {
                CelesteFileSystem.Directory dir = this.lookUpDirectory(fname);

                //
                // Create a new OpenDirectory object and assign it a slot in
                // the openFilesOrDirectories table.  Return the resulting
                // index.
                //
                OpenDirectory od = new OpenDirectory(dir);
                result = allocFd(od);
            }
        } catch (Throwable t) {
            result = new Result.FD(-1);
            result.setFailure(t);
        }

        return result;
    }

    public Result.Bytes pread(int fd, int length, int offset) {
        Result.Bytes result = null;

        //
        // Same code as for read (see below), except that we use a variant of
        // CelesteFileSystem.read() that takes an explicit offset.
        //
        // XXX: Ought to factor this code!
        //
        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);

                //
                // Verify read access.
                //
                boolean canRead = false;
                if (ofd instanceof OpenDirectory)
                    canRead = true;
                else if (ofd instanceof OpenFile) {
                    OpenFile of = (OpenFile)ofd;
                    canRead = of.readsAllowed();
                }
                if (!canRead)
                    throw new IOException("reads disallowed");

                HierarchicalFileSystem.File file = ofd.getFile();
                ByteBuffer buffer = ByteBuffer.wrap(new byte[length]);
                int bytesRead = file.read(buffer, (long)offset);
                return new Result.Bytes(buffer.array(), bytesRead);
            }
        } catch (Throwable t) {
            result = new Result.Bytes((byte[])null, -1);
            result.setFailure(t);
        }

        return result;
    }

    public Result.Length pwrite(int fd, int offset, byte[] data) {
        return doWrite(fd, data, false, offset);
    }

    public Result.Bytes read(int fd, int length) {
        Result.Bytes result = null;

        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);

                //
                // Verify read access.
                //
                boolean canRead = false;
                if (ofd instanceof OpenDirectory)
                    canRead = true;
                else if (ofd instanceof OpenFile) {
                    OpenFile of = (OpenFile)ofd;
                    canRead = of.readsAllowed();
                }
                if (!canRead)
                    throw new IOException("reads disallowed");

                HierarchicalFileSystem.File file = ofd.getFile();
                //
                // Ought to be able to factor out common code for read and
                // pread...
                //
                ByteBuffer buffer = ByteBuffer.wrap(new byte[length]);
                int bytesRead = file.read(buffer);
                //
                // Advance the seek pointer.
                //
                // XXX: Note that directories being read as files will advance
                //      a different amount than would result from doing a
                //      readdir() call, resulting in a possible inconsistency
                //      when the same descriptor is used for both.  To fix
                //      this, we'd need to change OpenDirectory.readdir() to
                //      base the offsets it reports on the lengths of
                //      directory entries, rather than counting each as
                //      contributing an offset of 1.  On the other hand, it's
                //      perfectly legitimate to forbid reading directories,
                //      and that's proably the better resolution of this
                //      discrepancy.
                //
                ofd.setOffset(ofd.getOffset() + bytesRead);
                return new Result.Bytes(buffer.array(), bytesRead);
            }
        } catch (Throwable t) {
            result = new Result.Bytes((byte[])null, -1);
            result.setFailure(t);
        }

        return result;
    }

    public Result.DirentAndOffset readdir(int fd) {
        Result.DirentAndOffset result = null;

        //
        // The protocol with our peer allows us to get away with delivering
        // one entry per call, with the entry consisting just of the name of
        // the next file in the directory, along with its offset.
        //
        // Note that, although OpenDirectory.readdir() returns a Result value,
        // it doesn't catch failures and encapsulate them into that value, so
        // try/catch is necessary here.
        //
        //
        try {
            synchronized (this) {
                OpenDirectory od = getOpenDirectory(fd);
                return od.readdir();
            }
        } catch (Throwable t) {
            result = new Result.DirentAndOffset(null, -1);
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void rename(String fromName, String toName) {
        Result.Void result = new Result.Void();

        synchronized (this) {
            PathName pathFrom = new PathName(fromName);
            PathName pathTo = new PathName(toName);
            try {
                this.fileSystem.renameFile(pathFrom, pathTo);
            } catch (FileException e) {
                result.setFailure(new IOException("rename failed"));
            }
        }

        return result;
    }

    public Result.Void rewinddir(int fd) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                OpenDirectory od = getOpenDirectory(fd);
                od.rewinddir();
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void rmdir(String fname) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                PathName path = new PathName(fname);
                try {
                    this.fileSystem.deleteDirectory(path);
                } catch (FileException e) {
                    result.setFailure(new IOException("not removed", e));
                }
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void seekdir(int fd, long newOffset) {
        Result.Void result = new Result.Void();

        try {
            synchronized (this) {
                OpenDirectory od = getOpenDirectory(fd);
                //
                // Samba doesn't need the old offset.
                //
                od.seekdir(newOffset);
            }
        } catch (Throwable t) {
            result.setFailure(t);
        }

        return result;
    }

    public Result.Attrs stat(String fname) {
        try {
            CelesteFileSystem.File file = this.lookUpFile(fname);
            return this.doStat(file);
        } catch (IOException e) {
            return Result.Attrs.noAttrs(e);
        }
    }

    public Result.Offset telldir(int fd) {
        Result.Offset result = null;

        try {
            synchronized (this) {
                OpenDirectory od = getOpenDirectory(fd);
                result = new Result.Offset(od.telldir());
            }
        } catch (Throwable t) {
            result = new Result.Offset(-1L);
            result.setFailure(t);
        }

        return result;
    }

    public Result.Void unlink(String fname) {
        Result.Void result = new Result.Void();

        synchronized (this) {
            PathName path = new PathName(fname);
            try {
                this.fileSystem.deleteFile(path);
            } catch (FileException.NotFound e) {
                result.setFailure(new IOException("not found"));
            } catch (FileException e) {
                result.setFailure(new IOException("not removed"));
            }
        }

        return result;
    }

    public Result.Length write(int fd, byte[] data) {
        return doWrite(fd, data, true, -1);
    }

    //
    // Private support methods
    //

    //
    // Worker method for the O_CREAT case of open().  Assumes that caller
    // holds the lock on this.
    //
    // A value of -1 for uid or gid indicates that that credential is
    // unspecified.
    //
    // XXX: Need to define proper semantics for all credentials, including
    //      ones that are unspecified.
    //
    private HierarchicalFileSystem.File doCreat(PathName filePath, long uid, long gid, int flags, int mode)
                throws IOException {
        CelesteFileSystem.File file = null;
        try {
            file = this.lookUpFile(filePath);
        } catch (IOException ignore) {
        }

        boolean exclusive = (flags & SambaFlags.O_EXCL) != 0;
        if (exclusive && file != null) {
            throw new IOException(
                "exclusive create attempt for existent file");
        }

        if (file != null)
            return file;

        //
        // Need to create it.  Start by setting up file properties holding the
        // metadata we've been given.  The mode bits given as argument should
        // include only access permissions, and need to be extended to include
        // the file type.
        //
        OrderedProperties props = new OrderedProperties();
        int fullMode =
            SambaModes.accessPermissions(mode) | SambaModes.SM_isRegular;
        props.setProperty(SambaOps.MODE_NAME, String.valueOf(fullMode));
        if (uid != -1)
            props.setProperty(SambaOps.OWNER_NAME, String.valueOf(uid));
        if (gid != -1)
            props.setProperty(SambaOps.GROUP_NAME, String.valueOf(gid));

        try {
            return this.fileSystem.createFile(filePath, props);
        } catch (FileException e) {
            throw new IOException(e);
        }
    }

    //
    // Worker method for stat() and fstat().
    //
    // XXX: Might be cleaner to split this method into directory and (regular)
    //      file versions.
    //
    private Result.Attrs doStat(HierarchicalFileSystem.File file) {
        Result.Attrs result = null;

        try {
            //
            // XXX: Synchronizing at this level doesn't guarantee that clients
            //      at lower levels don't make accesses that result in
            //      inconsistent attributes.  What we need is a way to get at
            //      the underlying FileImpl object and ask it for attributes
            //      pertinent to its then-current version.
            //
            //      But synchronize anyway.  That at least ensures that
            //      clients accessing the file through this Samba server don't
            //      interfere with each other.
            //
            synchronized (this) {
                //
                // Note that it's possible that the file may have been
                // deleted.  If so, there are no meaningful attributes to
                // report.  This situation will be detected upon trying to
                // access them, which will induce an exception.
                //

                boolean isDir = file instanceof HierarchicalFileSystem.Directory;

//                long serialNumber = file.serialNumber();
                long serialNumber = 1234567890;

                //
                // To be consistent with the implementation of the seek
                // operations on directories, we need to report the number of
                // entries if this is a directory.
                //
                long size = 0;
                if (isDir) {
                    HierarchicalFileSystem.Directory directory = (HierarchicalFileSystem.Directory) file;
                    size = directory.list().length;
                } else {
                    size = file.length();
                }
                //
                // Times must be expressed in Unix-style seconds since the
                // epoch.
                //
                long modTime = SambaOps.toSecondsSinceEpoch(file.lastModified());
//                long cTime = SambaOps.toSecondsSinceEpoch(file.lastMetadataChanged());
                long cTime = 1;
                //
                // If modes have been stored in this file's metadata use them.
                // Otherwise, fake them up, choosing to provide unrestricted
                // access (which is consistent with the old FSInstance
                // implementation).
                //
                OrderedProperties props = file.getClientProperties();
                String modeString = props.getProperty(SambaOps.MODE_NAME);
                int mode;
                if (modeString != null) {
                    mode = Integer.parseInt(modeString);
                } else {
                    mode = SambaModes.accessPermissions(0xffffffff);
                    mode |= isDir ?
                        SambaModes.SM_isDirectory : SambaModes.SM_isRegular;
                }
                //
                // The notion of user and group id is a recent addition to
                // file metadata, so not all files will have these ids in
                // their metadata.  Cope accordingly, using -1 to indicate
                // "not present".
                //
                long uid = -1;
                String uidString = props.getProperty(SambaOps.OWNER_NAME);
                if (uidString != null)
                    uid = Long.parseLong(uidString);
                long gid = -1;
                String gidString = props.getProperty(SambaOps.GROUP_NAME);
                if (gidString != null)
                    gid = Long.parseLong(gidString);

                result = new Result.Attrs(
                    serialNumber, size, uid, gid, modTime, cTime, mode);
            }
        } catch (Throwable t) {
            result = Result.Attrs.noAttrs(t);
        }

        return result;
    }

    //
    // Worker method for write() and pwrite().  If updatePosition is false,
    // write at the position given by offset and don't change the file's seek
    // pointer to reflect the results of the write.  Otherwise, write at (and
    // update) the file seek pointer position.
    //
    private Result.Length doWrite(int fd, byte[] data, boolean updatePosition,
            int offset) {
        Result.Length result = null;

        try {
            synchronized (this) {
                checkFileDescriptor(fd);
                OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
                assert ofd != null;

                //
                // Verify write access.  Check for append only while at it.
                // Obtain a reference to the file.
                //
                boolean canWrite = false;
                boolean appendOnly = false;
                HierarchicalFileSystem.File file = null;
                if (ofd instanceof OpenDirectory)
                    canWrite = false;
                else if (ofd instanceof OpenFile) {
                    OpenFile of = (OpenFile)ofd;
                    canWrite = of.writesAllowed();
                    appendOnly = of.isAppendOnly();
                    file = of.getFile();
                }
                if (!canWrite)
                    throw new IOException("writes disallowed");
                assert file != null;

                ByteBuffer buffer = ByteBuffer.wrap(data);

                //
                // If not explicitly given, the write offset is the current
                // seek pointer.  However, if the the file is append only, the
                // write must be forced to the current end of file.
                //
                if (updatePosition)
                    offset = (int)ofd.getOffset();
                if (appendOnly)
                    offset = (int)file.length();

                int bytesWritten = file.write(buffer, offset);

                if (updatePosition)
                    ofd.setOffset(offset + bytesWritten);
                result = new Result.Length(bytesWritten);
            }
        } catch (Throwable t) {
            result = new Result.Length(-1);
            result.setFailure(t);
        }
        return result;
    }

    //
    // Utility method(s) for file descriptor manipulation
    //

    private Result.FD allocFd(OpenFileOrDir ofd) {
        synchronized (this) {
            int fd = this.descriptorsInUse.nextClearBit(0);
            this.openFilesOrDirectories.put(fd, ofd);
            this.descriptorsInUse.set(fd);
            return new Result.FD(fd);
        }
    }

    //
    // Given a path in string form, fetch the corresponding file, throwing a
    // suitable exception on failure.
    //
    // XXX: Need to add bullet-proofing for non-existent components (including
    //      the one at the leaf).
    //
    // XXX: For now, the implementation handles relative path names by
    //      resolving them against the absolute path leading to the current
    //      working directory and then relying on CelesteFileSystem to search
    //      the resulting absolute path starting from the root directory.  A
    //      more efficient alternative would be to extend
    //      CelesteFileSystem.getFile() to take a directory argument that is
    //      to be used as the starting point for relative paths.  (We would
    //      then also have to maintain this.cwd properly.)
    //
    private CelesteFileSystem.File lookUpFile(String path) throws IOException {
        synchronized (this) {
            PathName pathName = new PathName(path).resolve(this.cwdPath);
            try {
                return this.fileSystem.getFile(pathName);
            } catch (FileException e) {
                IOException ioe = new IOException("getFile failed (File Exception)");
                ioe.initCause(e);
                throw ioe;
            }
        }
    }

    //
    // As above, except with a pre-computed PathName argument.
    //
    private CelesteFileSystem.File lookUpFile(PathName pathName)
            throws IOException {
        try {
            return this.fileSystem.getFile(pathName);
        } catch (FileException e) {
            IOException ioe = new IOException("getFile failed (File Exception)");
            ioe.initCause(e);
            throw ioe;
        }
    }

    //
    // Given a path in string form, fetch the corresponding directory,
    // throwing a suitable exception on failure.
    //
    // XXX: Need to add bullet-proofing for non-existent components (including
    //      the one at the leaf).
    //
    private CelesteFileSystem.Directory lookUpDirectory(String path)
            throws IOException {
        synchronized (this) {
            CelesteFileSystem.File dirAsFile = this.lookUpFile(path);
            if (!(dirAsFile instanceof CelesteFileSystem.Directory))
                throw new IOException("not directory");
            CelesteFileSystem.Directory dir =
                (CelesteFileSystem.Directory)dirAsFile;
            return dir;
        }
    }

    //
    // As above, except with a pre-computed PathName argument.
    //
    // XXX: The two methods need better factoring.
    //
    private CelesteFileSystem.Directory lookUpDirectory(PathName pathName)
            throws IOException {
        synchronized (this) {
            CelesteFileSystem.File dirAsFile = this.lookUpFile(pathName);
            if (!(dirAsFile instanceof CelesteFileSystem.Directory))
                throw new IOException("not directory");
            CelesteFileSystem.Directory dir =
                (CelesteFileSystem.Directory)dirAsFile;
            return dir;
        }
    }

    //
    // Given a file descriptor, fetch the corresponding OpenDirectory,
    // throwing a suitable exception on failure.
    //
    private OpenDirectory getOpenDirectory(int fd) throws IOException {
        synchronized (this) {
            checkFileDescriptor(fd);
            OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
            assert ofd != null;
            if (!(ofd instanceof OpenDirectory))
                throw new IOException("not directory");
            OpenDirectory od = (OpenDirectory)ofd;
            return od;
        }
    }

    //
    // Given a file descriptor, fetch the corresponding OpenFile,
    // throwing a suitable exception on failure.
    //
    private OpenFile getOpenFile(int fd) throws IOException {
        synchronized (this) {
            checkFileDescriptor(fd);
            OpenFileOrDir ofd = this.openFilesOrDirectories.get(fd);
            assert ofd != null;
            if (!(ofd instanceof OpenFile))
                throw new IOException("not file");
            OpenFile of = (OpenFile)ofd;
            return of;
        }
    }

    //
    // Verify that fd is a valid file descriptor.  Caller is responsible for
    // synchronization.
    //
    private void checkFileDescriptor(int fd) throws IOException {
        if (fd < 0 || !this.descriptorsInUse.get(fd))
            throw new IOException("bad file descriptor");
    }

    //
    // Time conversion routine(s)
    //

    private static long toSecondsSinceEpoch(long sysTimeMillis) {
        long millisSinceEpoch = new Date(sysTimeMillis).getTime();
        return millisSinceEpoch / 1000;
    }

    //////////////////////////////////////////////////////////////////////////

    //
    // XXX: Ideally, the testing code that follows should be factored out into
    //      Junit tests.  But the test builds up enough state that it's not
    //      clear how to move it into the Junit framework.
    //
    //      As an alternative, it should perhaps be factored into a standalone
    //      class that's located in a hierarchy parallel to the hierarchy of
    //      Junit tests.
    //

    //////////////////////////////////////////////////////////////////////////

    private final static String hexDigits = "0123456789abcdef";
    private final static Random randVar = new Random();

    //
    // Return a string of the given length consisting of random hex disgits.
    //
    private static String randomHexDigitString(int length) {
        if (length <= 0)
            return "";

        StringBuilder sb = new StringBuilder();
        for (int position = 0; position < length; position++) {
            int index = SambaOps.randVar.nextInt(SambaOps.hexDigits.length());
            sb.append(hexDigits.charAt(index));
        }
        return sb.toString();
    }

    //
    // Stat and list the given file (which is assumed to be a directory),
    // reporting the results to System.out.
    //
    private static void statAndListDir(SambaOps so, String dirName)
            throws Throwable {
        SambaOps.statFile(so, dirName);
        Result.FD rOpendir = so.opendir(dirName);
        if (rOpendir.isSuccessful()) {
            int fd = rOpendir.getFd();
            try {
                //
                // Read components until EOF or real failure.
                //
                for (;;) {
                    Result.DirentAndOffset rReaddir = so.readdir(fd);
                    if (!rReaddir.isSuccessful()) {
                        Throwable cause = rReaddir.getFailure();
                        if (cause instanceof EOFException)
                            break;
                        throw cause;
                    }
                    System.out.printf("found \"%s/%s\"%n",
                        dirName.equals("/") ? "" : dirName,
                        rReaddir.getComponentName());
                }
            } finally {
                so.closedir(fd);
            }
        } else
            throw rOpendir.getFailure();
    }

    //
    // Stat the given file (which can be either a directory or a regular
    // file), reporting the results to System.out.
    //
    private static void statFile(SambaOps so, String fileName)
            throws Throwable{
        Result.Attrs rStat = so.stat(fileName);
        if (rStat.isSuccessful()) {
            System.out.printf("\"%s\":\tsize: %d, modTime: %d, mode: 0x%x%n",
                fileName,
                rStat.getSize(),
                rStat.getModTime(),
                rStat.getMode());
        } else
            throw rStat.getFailure();
    }


    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }

    /**
     * Executes a series of tests of the methods the class provides.
     */
    public static void main(String[] args) {
        String celesteAddress = "127.0.0.1:14000";
        String erasureCoder = "ErasureCodeReplica/2";

        //
        // Randomize the profile name, so that the test can be repeated
        // without encountering files left over from previous runs.
        //
        String profileName =
            String.format("SambaOps-tester#%s@celeste.sun.com",
                SambaOps.randomHexDigitString(10));
        System.out.printf("Using profile \"%s\"%n", profileName);

        try {
            CelesteAPI node = new CelesteProxy(makeAddress(celesteAddress), Time.secondsInMilliseconds(300), TimeUnit.MILLISECONDS);
            ProfileCache pcache = new ProfileCache(node);
            Credential p = null;
            p = pcache.get(profileName);
            if (p == null) {
                Profile_ profile = new Profile_(
                    profileName, "samba".toCharArray());
                NewCredentialOperation operation = new NewCredentialOperation(
                    profile.getObjectId(), BeehiveObjectId.ZERO, erasureCoder);
                Credential.Signature signature = profile.sign(
                    "samba".toCharArray(), operation.getId());

                node.newCredential(operation, signature, profile);
            }

            //
            // Let the profile do double duty as both creator and writer
            // profile.
            //
            // XXX: Will probably want to separate these two roles.  (But note
            //      that the only thing CelesteFileSystem does with the
            //      creator profile name is to stash it away as the name of
            //      the file system's mount point.  Access is governed only by
            //      the writer profile.)
            //
            CelesteFileSystem cfs = new CelesteFileSystem(
                makeAddress(celesteAddress), null, profileName, profileName,
                "samba");
            cfs.newFileSystem();

            SambaOps so = new SambaOps(cfs);

            //
            // Check the before and after state of / with respect to adding
            // subdirectories to it.  Interlave some operations on
            // non-directory files.
            //

            System.out.println("% pseudo-ls /");
            SambaOps.statAndListDir(so, "/");

            System.out.println("% mkdir /blurfl /zorch /blurfl/splat");
            Result.Void rMkdir = so.mkdir("/blurfl", 0777);
            if (!rMkdir.isSuccessful())
                throw new Exception(rMkdir.getFailure());
            rMkdir = so.mkdir("/zorch", 0777);
            if (!rMkdir.isSuccessful())
                throw new Exception(rMkdir.getFailure());
            rMkdir = so.mkdir("/blurfl/splat", 0777);
            if (!rMkdir.isSuccessful())
                throw new Exception(rMkdir.getFailure());
            System.out.println("% chdir blurfl");
            Result.Void rChdir = so.chdir("blurfl");
            if (!rChdir.isSuccessful())
                throw new Exception(rChdir.getFailure());
            Result.Path rPath = so.getwd();
            if (!rPath.isSuccessful())
                throw new Exception(rPath.getFailure());
            System.out.printf("%% pwd%n%s%n", rPath.getPath());

            //
            // Do some file operations.
            //
            System.out.println("% echo -n > leaf-vegetables");
            Result.FD rOpen = so.open("leaf-vegetables",
                SambaFlags.O_RDWR + SambaFlags.O_CREAT, 0777);
            if (!rOpen.isSuccessful())
                throw new Exception(rOpen.getFailure());
            int fd = rOpen.getFd();
            //
            // fd (deliberately) still open...
            //
            // Attempting to recreate what was just created should result in
            // failure.
            //
            System.out.println("% (set noclobber; echo -n > leaf-vegetables)");
            rOpen = so.open("leaf-vegetables",
                SambaFlags.O_RDWR + SambaFlags.O_CREAT + SambaFlags.O_EXCL,
                0777);
            if (rOpen.isSuccessful()) {
                throw new Exception(
                    "creat improperly succeeded on existent file");
            } else {
                System.out.println("leaf-vegetables: already exists");
            }
            //
            // Write some stuff to the file.
            //
            System.out.println("% echo cabbage > leaf-vegetables");
            so.write(fd, "cabbage\n".getBytes());
            System.out.println("% pseudo-ls leaf-vegetables");
            SambaOps.statFile(so, "leaf-vegetables");
            //
            // Ok, now close fd.
            //
            Result.Void rClose = so.close(fd);
            if (!rClose.isSuccessful())
                throw new Exception(rOpen.getFailure());
            //
            // Reopen with append mode set.
            //
            rOpen = so.open("leaf-vegetables",
                SambaFlags.O_WRONLY + SambaFlags.O_APPEND, 0777);
            if (!rOpen.isSuccessful())
                throw new Exception(rOpen.getFailure());
            fd = rOpen.getFd();
            //
            // Append to the file.
            //
            System.out.println("% echo lettuce >> leaf-vegetables");
            so.write(fd, "lettuce\n".getBytes());
            System.out.println("% pseudo-ls leaf-vegetables");
            SambaOps.statFile(so, "leaf-vegetables");
            //
            // Close fd again.
            //
            rClose = so.close(fd);
            if (!rClose.isSuccessful())
                throw new Exception(rOpen.getFailure());
            //
            // Re-open the file and read its contents.
            //
            System.out.println("% cat leaf-vegetables");
            //Result.Attrs rStat = so.stat("leaf-vegetables");
            //if (!rStat.isSuccessful())
            //    throw new Exception(rStat.getFailure());
            //int length = (int)rStat.getSize();
            rOpen = so.open("leaf-vegetables", SambaFlags.O_RDONLY, 0777);
            if (!rOpen.isSuccessful())
                throw new Exception(rOpen.getFailure());
            fd = rOpen.getFd();
            Result.Offset rOffset = so.lseek(fd, 0, SambaSeek.SEEK_END);
            if (!rOffset.isSuccessful())
                throw new Exception(rOffset.getFailure());
            int length = (int)rOffset.getOffset();
            Result.Bytes rBytes = so.read(fd, 512);
            if (!rBytes.isSuccessful())
                throw new Exception(rBytes.getFailure());
            System.out.printf("%s", new String(rBytes.getBytes(), 0, length));

            //
            // Do some more directory operations.  First, what's in /?
            //
            System.out.println("% pseudo-ls /");
            SambaOps.statAndListDir(so, "/");
            //
            // Now try to remove a couple directories, one with nothing in it
            // and the other with contained directories or files.
            //
            System.out.println("% rmdir ../zorch");
            Result.Void rRmdir = so.rmdir("../zorch");
            if (!rRmdir.isSuccessful())
                throw new Exception(rRmdir.getFailure());
            System.out.println("% cd ..");
            rChdir = so.chdir("..");
            if (!rChdir.isSuccessful())
                throw new Exception(rChdir.getFailure());
            System.out.println("rmdir blurfl");
            rRmdir = so.rmdir("blurfl");
            if (rRmdir.isSuccessful())
                throw new IOException(
                    "rmdir of non-empty directory succeeded!");
            System.out.println("blurfl: not empty");

            //
            // XXX: Add rename and unlink tests.
            //

            //
            // Report the resulting directory structure.
            //
            System.out.println("% pseudo-ls /");
            SambaOps.statAndListDir(so, "/");
            System.out.println("% pseudo-ls /blurfl");
            SambaOps.statAndListDir(so, "/blurfl");

            System.out.println("% ");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
