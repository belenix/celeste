/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle., 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.celeste.client.filesystem;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;

import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.FileIdentifier;

/**
 * An interface to a file system.
 *
 * File systems contain names for files, their interfaces represented by {@code FileSystem.File}.
 */
public interface FileSystem {
    
    /**
     * A FileSystem Node is any nameable entity within the FileSystem.
     * It can be a regular file, a directory, anything that can be named.
     *
     */
    public interface Node {
        /**
         * Return {@code true} if this {@link Node} is present in the file system.
         * 
         * @return {@code true} if this {@link Node} is present in the file system, {@code false} otherwise.
         * @throws FileException.BadVersion
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.IOException
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         */
        public boolean exists() throws FileException.BadVersion, FileException.CelesteFailed, FileException.CelesteInaccessible,
        FileException.IOException, FileException.Runtime, FileException.ValidationFailed;

        public OrderedProperties getAttributes(Set<String> attrNames) throws
        FileException.BadVersion,
        FileException.CelesteFailed,
        FileException.CelesteInaccessible,
        FileException.IOException,
        FileException.NotFound,
        FileException.Runtime,
        FileException.ValidationFailed;

        /**
         * Return the time that this file was last modified.  The time is
         * reported with respect to the local clock of the entity that
         * modified the file.
         *
         * @return  the file's modification time, expressed in milliseconds
         *          since midnight, January 1, 1970 UTC
         */
        public long lastModified() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed;
        
        /**
         * <p>
         * Returns the properties associated with this file, in the form of
         * an {@code OrderedProperties} map containing {@code String} names
         * and corresponding String values.
         * </p><p>
         * (A file property is a string-valued item associated with a given
         * file that the file system implementation stores with the file and
         * retrieves on request, but otherwise does not use or interpret.)
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
                FileException.ValidationFailed;

        public InternetMediaType getContentType() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed;

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
                FileException.ValidationFailed;
    }

    abstract public class Exception extends java.lang.Exception {
       

        private static final long serialVersionUID = 1L;
        
        public Exception() {
            super();
        }
        
        public Exception(String message) {
            super(message);
        }

        public Exception(Throwable cause) {
            super(cause);
        }
        
        public Exception(String format, Object...args) {
            super(String.format(format, args));
        }        
    }
    
    public class AlreadyExistsException extends FileSystem.Exception {
        private static final long serialVersionUID = 1L;

        public AlreadyExistsException() {
            super();
        }

        public AlreadyExistsException(String message) {
            super(message);
        }

        public AlreadyExistsException(Throwable cause) {
            super(cause);
        }

        public AlreadyExistsException(String format, Object... args) {
            super(format, args);
        }
    }
    
    public interface FileName {
        /**
         * Return the extension part of this file name.
         * he extension is the suffix characters following the final {@code '.'} character.
         * If the extension is missing, return the empty string.
         *
         * @return the extension part of this file name.
         */
        public String getNameExtension();
        
        public FileName append(String name);
    }
    
    public interface File extends FileSystem.Node {
        abstract public class Exception extends java.lang.Exception {
            private static final long serialVersionUID = 1L;
            
            public Exception() {
                super();
            }
            
            public Exception(String message) {
                super(message);
            }

            public Exception(Throwable cause) {
                super(cause);
            }
            
            public Exception(String format, Object...args) {
                super(String.format(format, args));
            }        
        }




        /**
         * <p>
         * Replaces any existing properties associated with this file with the
         * ones contained in {@code clientProperties}, creating a new version
         * of the file that differs from the previous version only in the
         * properties and in the
         * {@link FileAttributes.Names#METADATA_CHANGED_TIME_NAME metadata changed time} attribute.
         * </p><p>
         * (A file property is a string-valued item associated with a given
         * file that the file system implementation stores with the file and
         * retrieves on request, but otherwise does not use or interpret.)
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
                FileException.ValidationFailed;


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
            FileException.ValidationFailed;

        public FileSystem.File position(long newPosition);

        public void close();

        
        public long read(ByteBuffer[] dsts, int offset, int length);

        public int read(ByteBuffer dst) throws IOException;

        public int read(ByteBuffer dst, long position) throws IOException;

        public FileSystem.File truncate(long size) throws IOException;

        public int write(ByteBuffer src, long position) throws IOException;

        public long write(ByteBuffer[] srcs, int offset, int length);

        public int write(ByteBuffer src) throws IOException;

        public long position();

        // XXX This should be in the metadata and not a specialized method.
        public int getBlockSize() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.IOException,
                FileException.NotFound,
                FileException.Runtime,
                FileException.ValidationFailed;

        /**
         * Tests if this file is a directory (see {@link HierarchicalFileSystem.Directory}).
         *
         * @return {@code true} if this file is a directory.
         */
        public boolean isDirectory();

        /**
         * Return this file's {@code FileIdentifier}, its identifier within
         * the underlying Celeste file store.
         *
         * @return this file's file-identifier
         * @throws FileException.NotFound if this File does not have a FileIdentifier (file 
         */
        public FileIdentifier getFileIdentifier() throws FileException.NotFound;

        public InputStream getInputStream() throws
        FileException.BadVersion,
        FileException.CelesteFailed,
        FileException.CelesteInaccessible,
        FileException.IOException,
        FileException.IsDirectory,
        FileException.NotFound,
        FileException.Runtime,
        FileException.ValidationFailed;

        public OutputStream getOutputStream(boolean b, int bufferSize) throws
        FileException.BadVersion,
        FileException.CelesteFailed,
        FileException.CelesteInaccessible,
        FileException.IOException,
        FileException.IsDirectory,
        FileException.NotFound,
        FileException.Runtime,
        FileException.ValidationFailed,
        IOException;
    }
    
}
