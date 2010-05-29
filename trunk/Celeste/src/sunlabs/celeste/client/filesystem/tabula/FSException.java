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

import java.net.InetSocketAddress;

import sunlabs.celeste.client.filesystem.PathName;
import sunlabs.celeste.util.ACL.ACLException;

public abstract class FSException extends Exception {
    private static final long serialVersionUID = 0L;

    private final PathName path;

    protected FSException(PathName path) {
        super();
        this.path = path;
    }

    protected FSException(PathName path, String message) {
        super(message);
        this.path = path;
    }

    protected FSException(PathName path, Throwable cause) {
        super(cause);
        this.path = path;
    }

    protected FSException(PathName path, String message, Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    /**
     * Returns the path name of the file system name space occupant that
     * induced this exception.  Note that this path may be distinct from that
     * of the occupant on which the failing operation was invoked.
     *
     * @return  the {@code PathName} of the entity triggering this exception
     */
    public PathName getPath() {
        return this.path;
    }

    //
    // Nested subclasses describing specific failures
    //

    /**
     * Indicates a failure in communicating with the Celeste node that handles
     * requests from this file system handle.  The failure may be associated
     * with a request made on behalf of a specific occupant of the file system
     * name space, in which case {@link #getPath() getPath()} will return the
     * corresponding path name.  Calling the {@link #getAddress()
     * getAddress()} method gives the address of the problematic Celeste node.
     */
    public static class CommunicationFailure extends FSException {
        private static final long serialVersionUID = 0L;

        private final InetSocketAddress address;

        public CommunicationFailure(InetSocketAddress address) {
            super(null);
            this.address = address;
        }

        public CommunicationFailure(PathName path, InetSocketAddress address,
                Throwable cause) {
            super(path, cause);
            this.address = address;
        }

        public CommunicationFailure(PathName path, InetSocketAddress address) {
            super(path);
            this.address = address;
        }

        public InetSocketAddress getAddress() {
            return this.address;
        }
    }

    /**
     * Indicates that the credential or name space identified by calling
     * {@link #getName()} cannot be found within the Beehive object store.
     * This exception has no associated {@code PathName}.
     */
    //
    // Perhaps we need a companion CredentialUnobtainable exception to be
    // thrown when we know that the credential exists, but cannot successfully
    // access it.  (The TabulaFileSystem constructor could potentially throw
    // this exception, but currently lumps it in with CredentialNotFound.)
    //
    public static class CredentialNotFound extends FSException {
        private static final long serialVersionUID = 0L;

        private final String name;

        public CredentialNotFound(String name) {
            super(null);
            this.name = name;
        }

        public CredentialNotFound(String name, Throwable cause) {
            super(null, cause);
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    /**
     * Indicates that the position in the file system name space denoted by
     * {@link #getPath() getPath()} exists, but that the operation being
     * performed requires that it not exist.
     */
    public static class Exists extends FSException {
        private static final long serialVersionUID = 0L;

        public Exists(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates that an attempt to create a file system failed because that
     * file system already exists.  This exception has no associated {@code
     * PathName}.  The {@link #getName() getName()} method gives the name of
     * the file system in question.
     */
    public static class FileSystemExists extends FSException {
        private static final long serialVersionUID = 0L;

        private final String name;

        public FileSystemExists(String name) {
            super(null);
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    /**
     * Indicates that the file system identified by calling {@link #getName()}
     * cannot be found.  This exception has no associated {@code PathName}.
     * The {@link #getName() getName()} method gives the name of the file
     * system in question.
     */
    public static class FileSystemNotFound extends FSException {
        private static final long serialVersionUID = 0L;

        private final String name;

        public FileSystemNotFound(String name) {
            super(null);
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    /**
     * Indicates that an I/O operation did not complete successfully.  The
     * {@link #getCause() getCause()} method may provide additional
     * information about the failure.  The failure may be such that it can be
     * associated with no path name, in which case {@link #getPath()
     * getPath()} will return {@code null}.
     */
    public static class IO extends FSException {
        private static final long serialVersionUID = 0L;

        public IO(PathName path, Throwable cause) {
            super(path, cause);
        }
    }

    /**
     * Indicates that a failure occurred in the file system implementation or
     * within Celeste that the file system implementation cannot correct.  The
     * {@link #getCause() getCause()} method may provide additional
     * information about the failure.  The failure may be such that it can be
     * associated with no path name, in which case {@link #getPath()
     * getPath()} will return {@code null}.
     */
    public static class InternalFailure extends FSException {
        private static final long serialVersionUID = 0L;

        public InternalFailure(PathName path, Throwable cause) {
            super(path, cause);
        }
    }

    /**
     * Indicates that the underlying Celeste file store has insufficient
     * available space to allow an attempted write to it to succeed.  The
     * {@link #getCause() getCause()} method may provide additional
     * information about the failure.  The failure may be such that it can be
     * associated with no path name, in which case {@link #getPath()
     * getPath()} will return {@code null}.
     */
    public static class InsufficientSpace extends FSException {
        private static final long serialVersionUID = 0L;

        public InsufficientSpace(PathName path, Throwable cause) {
            super(path, cause);
        }
    }

    /**
     * Indicates that the entity denoted by {@link #getPath() getPath()} is a
     * directory, but is not allowed to be one.
     */
    public static class IsDirectory extends FSException {
        private static final long serialVersionUID = 0L;

        public IsDirectory(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates the entity denoted by {@link #getPath()} exists, but is
     * locked for modifications by an entity other than the caller.
     */
    public static class Locked extends FSException {
        private static final long serialVersionUID = 0L;

        private final Lock lock;

        public Locked(PathName path, Lock lock) {
            super(path);
            this.lock = lock;
        }

        /**
         * Returns information about the lock that triggered this exception.
         */
        public Lock getLock() {
            return this.lock;
        }
    }

    /**
     * Indicates the entity denoted by {@link #getPath() getPath()} exists and
     * was expected to be a directory, but was not.
     */
    public static class NotDirectory extends FSException {
        private static final long serialVersionUID = 0L;

        public NotDirectory(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates that an attempt was made to remove a non-empty directory;
     * {@link #getPath() getPath()} gives the directory's name.
     */
    public static class NotEmpty extends FSException {
        private static final long serialVersionUID = 0L;

        public NotEmpty(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates the entity denoted by {@link #getPath() getPath()} exists and
     * was expected to be a file reference, but was not.
     */
    public static class NotFile extends FSException {
        private static final long serialVersionUID = 0L;

        public NotFile(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates that the entity denoted by {@link #getPath() getPath()} was
     * expected to be present in the file system name space, but was not.
     */
    public static class NotFound extends FSException {
        private static final long serialVersionUID = 0L;

        public NotFound(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates that the entity denoted by {@link #getPath() getPath()} was
     * expected to be locked, but was not.
     */
    public static class NotLocked extends FSException {
        private static final long serialVersionUID = 0L;

        public NotLocked(PathName path) {
            super(path);
        }
    }

    /**
     * Indicates that the caller was not authorized to perform an operation on
     * {@link #getname() getName()}.  The {@link getACLException()
     * getACLException()} method can be called to obtain details of why
     * authorization was withheld.
     */
    public static class PermissionDenied extends FSException {
        private static final long serialVersionUID = 0L;

        private final ACLException aclException;

        public PermissionDenied(PathName path) {
            super(path, (Throwable)null);
            this.aclException = null;
        }

        public PermissionDenied(PathName path, ACLException aclException) {
            super(path, aclException);
            this.aclException = aclException;
        }

        public ACLException getACLException() {
            return this.aclException;
        }
    }
}
