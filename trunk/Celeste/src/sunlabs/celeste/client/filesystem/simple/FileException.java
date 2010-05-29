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

import sunlabs.celeste.client.filesystem.PathName;

/**
 * {@code FileException} and its nested subclasses capture information
 * pertaining to a failure in executing a {@link FileImpl} operation.
 */
//
// XXX: Perhaps this class should extend IOException rather than Exception.
//      That would let methods that currently throw either FileException or
//      IOException be declared to throw just IOException but still get the
//      fine discrimination that FileException's intended to provide.
//
// XXX: Consider making FileException itself an abstract class, so that only
//      instances of int nested subclasses can be thrown.
//
// XXX: In many circumstances it would be helpful to have a FileException
//      instance capture the identity of the file to which it applies.  But
//      how?  It would be straightforward to use a FileIdentifier for this
//      purpose.  But when working at the level of CelesteFileSystem, one is
//      interested in PathNames rather than FileIdentifiers, and getting from
//      a FileIdentifier to a PathName requires a search through the file
//      system name space.  The trouble is that FileExceptions are commonly
//      thrown from deep levels where PathNames aren't available.
//      
//      So what to do?  One (unpleasant) possibility is to have upper layers
//      capture exceptions from lower levels, add PathNames to them where
//      possible, and re-throw them.  Another is to allow setting both
//      FileIdentifier and PathName information in FileExceptions, to set
//      whatever's available when the exception is thrown, but not to take
//      heroic measures to add higher level information.  (Merging the lower
//      level FileImpl code into CelesteFileSystem is another possibility,
//      but it's not clear that doing so would completely alleviate the
//      problem.)
//
public class FileException extends Exception {
    //
    // XXX: how does one generate these numbers?
    //
    static private final long serialVersionUID = 0;

    private final static String BAD_VERSION = "File has wrong version number";
    private final static String CAPACITY_EXCEEDED = "Operation is too large to handle";
    private final static String CELESTE_FAILED = "Celeste operation failed";
    private final static String CELESTE_INACCESSIBLE = "Celeste inaccessible";
    private final static String CREDENTIAL_PROBLEM = "Problem with credential";
    private final static String DIRECTORY_CORRUPTED = "Directory contents corrupted";
    private final static String DTOKEN_MISMATCH = "Failed to retrieve correct delete token";
    private final static String EXISTS = "File or directory exists (but should not)";
    private final static String FILE_DELETED = "File has been deleted";
    private final static String FILE_NOT_FOUND = "File not found";
    private final static String FILE_SYSTEM_NOT_FOUND = "File system not found";
    private final static String INVALID_CONTENT_TYPE = "Content type is invalid";
    private final static String INVALID_NAME = "Invalid file name";
    private final static String INVALID_OP = "Operation is invalid, or not yet provided";
    private final static String IO_EXCEPTION = "Underlying IOException";
    private final static String IS_DIRECTORY = "Is a directory";
    private final static String LOCKED = "File is locked for modifications";
    private final static String NOT_DIRECTORY = "Not a directory";
    private final static String NOT_LOCKED = "File is not locked";
    private final static String NOT_EMPTY = "Directory is non-empty";
    private final static String RETRIES_EXCEEDED = "Too many retries for operation";
    private final static String PERMISSION_DENIED = "Permission denied";
    private final static String RUNTIME = "Runtime error";
    private final static String VALIDATION_FAILED = "File did not validate";

    /**
     * Create a file exception not covered by one of the nested {@code
     * FileException} subclasses.
     */
    public FileException(String text) {
        super(text);
    }

    /**
     * Create a file exception with the given cause that is not covered by one
     * of the nested {@code FileException} subclasses.
     */
    public FileException(String text, Throwable cause) {
        super(text, cause);
    }

    public FileException(Throwable cause) {
        super(cause);
    }

    //
    // Selected FileExceptions expressed as subclasses, so that they can
    // be detected without recourse to examining their detail strings.
    //

    /**
     * Signals that the file against which the exception was thrown has its
     * metadata encoded in a format that the file system implementation does
     * not understand.  This situation could occur when an old version of the
     * file system implementation is confronted with a file that has been
     * written by a newer version of the implementation.
     */
    public static class BadVersion extends FileException {
        private final static long serialVersionUID = 1L;
        public BadVersion() {
            super(BAD_VERSION);
        }

        public BadVersion(Throwable cause) {
            super(BAD_VERSION, cause);
        }
    }

    /**
     * Signals that there is insufficient space in the Celeste object pool to
     * store a file or directory or that a directory would grow too large for
     * the file system implementation to handle.
     */
    public static class CapacityExceeded extends FileException {
        private final static long serialVersionUID = 1L;
        public CapacityExceeded() {
            super(CAPACITY_EXCEEDED);
        }

        public CapacityExceeded(Throwable cause) {
            super(CAPACITY_EXCEEDED, cause);
        }
    }

    /**
     * Signals that Celeste was unable to perform a properly presented
     * operation.  Possible causes include i/o errors and message class
     * mismatches between a Celeste server node and a client-resident proxy.
     */
    public static class CelesteFailed extends FileException {
        private final static long serialVersionUID = 1L;
        public CelesteFailed() {
            super(CELESTE_FAILED);
        }

        public CelesteFailed(String message) {
            super(message);
        }

        public CelesteFailed(Throwable cause) {
            super(CELESTE_FAILED, cause);
        }
    }

    public static class CelesteInaccessible extends FileException {
        private final static long serialVersionUID = 1L;
        public CelesteInaccessible() {
            super(CELESTE_INACCESSIBLE);
        }

        public CelesteInaccessible(Throwable cause) {
            super(CELESTE_INACCESSIBLE, cause);
        }
    }

    public static class CredentialProblem extends FileException {
        private final static long serialVersionUID = 1L;
        public CredentialProblem() {
            super(CREDENTIAL_PROBLEM);
        }

        public CredentialProblem(Throwable cause) {
            super(CREDENTIAL_PROBLEM, cause);
        }
    }

    public static class Deleted extends FileException {
        private final static long serialVersionUID = 1L;

        public Deleted() {
            super(FILE_DELETED);
        }

        public Deleted(Throwable cause) {
            super(FILE_DELETED, cause);
        }
    }

    /**
     * Signals that the contents of some directory encountered while carrying
     * out an operation have been corrupted.  This situation could occur if
     * the directory is written to by non-directory code.
     */
    public static class DirectoryCorrupted extends FileException {
        private final static long serialVersionUID = 1L;

        public DirectoryCorrupted() {
            super(DIRECTORY_CORRUPTED);
        }

        public DirectoryCorrupted(Throwable cause) {
            super(DIRECTORY_CORRUPTED, cause);
        }
    }

    public static class DTokenMismatch extends FileException {
        private final static long serialVersionUID = 1L;

        public DTokenMismatch() {
            super(DTOKEN_MISMATCH);
        }

        public DTokenMismatch(Throwable cause) {
            super(DTOKEN_MISMATCH, cause);
        }
    }

    public static class Exists extends FileException {
        private final static long serialVersionUID = 1L;

        public Exists() {
            super(EXISTS);
        }

        public Exists(Throwable cause) {
            super(EXISTS, cause);
        }
    }

    public static class FileSystemNotFound extends FileException {
        private final static long serialVersionUID = 1L;

        public FileSystemNotFound() {
            super(FILE_SYSTEM_NOT_FOUND);
        }

        public FileSystemNotFound(Throwable cause) {
            super(FILE_SYSTEM_NOT_FOUND, cause);
        }

        public FileSystemNotFound(String name) {
            super(String.format("%s: %s", FILE_SYSTEM_NOT_FOUND, name));
        }
    }

    public static class InvalidContentType extends FileException {
        private final static long serialVersionUID = 1L;

        public InvalidContentType() {
            super(INVALID_CONTENT_TYPE);
        }

        public InvalidContentType(Throwable cause) {
            super(INVALID_CONTENT_TYPE, cause);
        }
    }

    public static class InvalidName extends FileException {
        private final static long serialVersionUID = 1L;

        public InvalidName() {
            super(INVALID_NAME);
        }

        public InvalidName(String description) {
            super(String.format("%s: %s", INVALID_NAME, description));
        }

        public InvalidName(Throwable cause) {
            super(INVALID_NAME, cause);
        }
    }

    public static class InvalidOp extends FileException {
        private final static long serialVersionUID = 1L;

        public InvalidOp() {
            super(INVALID_OP);
        }

        public InvalidOp(Throwable cause) {
            super(INVALID_OP, cause);
        }
    }

    public static class IOException extends FileException {
        private final static long serialVersionUID = 1L;

        public IOException() {
            super(IO_EXCEPTION);
        }

        public IOException(Throwable cause) {
            super(IO_EXCEPTION, cause);
        }
    }

    public static class IsDirectory extends FileException {
        private final static long serialVersionUID = 1L;

        public IsDirectory() {
            super(IS_DIRECTORY);
        }

        public IsDirectory(Throwable cause) {
            super(IS_DIRECTORY, cause);
        }
    }

    public static class Locked extends FileException {
        private final static long serialVersionUID = 1L;

        public Locked() {
            super(LOCKED);
        }

        public Locked(Throwable cause) {
            super(LOCKED, cause);
        }
    }

    //
    // An enumeration of possible reasons why this exception might be thrown
    // (work in progress):
    //
    // -    In a context where a directory was required, an object of some
    //      other type was found.
    //
    public static class NotDirectory extends FileException {
        private final static long serialVersionUID = 1L;

        public NotDirectory() {
            super(NOT_DIRECTORY);
        }

        public NotDirectory(Throwable cause) {
            super(NOT_DIRECTORY, cause);
        }
    }

    public static class NotEmpty extends FileException {
        private final static long serialVersionUID = 1L;

        public NotEmpty() {
            super(NOT_EMPTY);
        }

        public NotEmpty(Throwable cause) {
            super(NOT_EMPTY, cause);
        }
    }

    //
    // An enumeration of possible reasons why this exception might be thrown
    // (work in progress):
    //
    // -    A component in a path leading to a file is nonexistent.
    //
    public static class NotFound extends FileException {
        private final static long serialVersionUID = 1L;

        public NotFound() {
            super(FILE_NOT_FOUND);
        }

        public NotFound(Throwable cause) {
            super(FILE_NOT_FOUND, cause);
        }

        public NotFound(PathName path) {
            super(String.format("%s: %s", FILE_NOT_FOUND, path));
        }

        public NotFound(String name) {
            super(String.format("%s: %s", FILE_NOT_FOUND, name));
        }
    }

    public static class NotLocked extends FileException {
        private final static long serialVersionUID = 1L;

        public NotLocked() {
            super(NOT_LOCKED);
        }

        public NotLocked(Throwable cause) {
            super(NOT_LOCKED, cause);
        }
    }

    public static class RetriesExceeded extends FileException {
        private final static long serialVersionUID = 1L;

        public RetriesExceeded() {
            super(RETRIES_EXCEEDED);
        }

        public RetriesExceeded(Throwable cause) {
            super(RETRIES_EXCEEDED, cause);
        }
    }

    //
    // An enumeration of possible reasons why this exception might be thrown
    // (work in progress):
    //
    // -    The file code system has invoked a Celeste operation and Celeste
    //      has responded with a VerificationException, indicating that it
    //      detected an internal inconsistency in the information we handed it
    //      characterizing the operation.  From the caller's perspective, this
    //      means that the file system implementation erred by constructing a
    //      bogus operation invocation.
    //
    //      (This situation might better be described by an InternalError
    //      exception, except that "Error" isn't appropriate because recovery
    //      is presumably possible.)
    //
    //  -   An unchecked exception was caught while executing a method and was
    //      converted to a checked exception by wrapping it in an instance of
    //      this class.  (Again, InternalError might be a better name for an
    //      exception thrown in these circumstance, but with the caveat
    //      mentioned above.)
    //
    public static class Runtime extends FileException {
        private final static long serialVersionUID = 1L;

        public Runtime() {
            super(RUNTIME);
        }

        public Runtime(Throwable cause) {
            super(RUNTIME, cause);
        }
    }

    /**
     * Signals that the access control list of a file on which an operation
     * was invoked did not grant permission for the operation.
     */
    public static class PermissionDenied extends FileException {
        private final static long serialVersionUID = 1L;

        public PermissionDenied() {
            super(PERMISSION_DENIED);
        }

        public PermissionDenied(Throwable cause) {
            super(PERMISSION_DENIED, cause);
        }
    }

    /**
     * Signals that the file system implementation has detected an
     * inconsistency in file metadata returned to it from Celeste.  E.g.,
     * information that is supposed to remain constant from one version of a
     * file to the next has changed.
     */
    public static class ValidationFailed extends FileException {
        private final static long serialVersionUID = 1L;

        public ValidationFailed() {
            super(VALIDATION_FAILED);
        }

        public ValidationFailed(Throwable cause) {
            super(VALIDATION_FAILED, cause);
        }
    }
}
