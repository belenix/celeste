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


import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.celeste.client.filesystem.PathName;

/**
 * An interface to a file system.
 *
 * File systems contain files, their interfaces represented by
 * {@code FileSystem.File}.
 */
public interface FileSystem {
    public abstract class File extends java.nio.channels.FileChannel  {
        /**
         * Return the time that this file was last modified.  The time is
         * reported with respect to the local clock of the entity that
         * modified the file.
         *
         * @return  the file's modification time, expressed in milliseconds
         *          since midnight, January 1, 1970 UTC
         */
        abstract public long lastModified() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed;

        abstract public InternetMediaType getContentType() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed;

        abstract public void setContentType(InternetMediaType type) throws
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
    }

    public FileSystem.File getFile(PathName path) throws FileException;

    public FileSystem.File createFile(PathName path) throws FileException;

    public void deleteFile(PathName path) throws FileException;
}
