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
 * A Hierarchical File-system is a file-system organised
 * into directories of files and other directories.
 */
public interface HierarchicalFileSystem extends FileSystem {
    public Directory createDirectory(PathName path) throws FileException;

    public void deleteDirectory(PathName path) throws FileException;

    public interface Directory {
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
            FileException.ValidationFailed;
    }
}
