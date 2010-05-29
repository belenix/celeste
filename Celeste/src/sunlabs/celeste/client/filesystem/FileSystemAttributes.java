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

package sunlabs.celeste.client.filesystem;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 *
 * Provides a nested class that groups together definitions of names that
 * identify various attributes associated with {@link CelesteFileSystem}
 * file systems.
 *
 * </p><p>
 *
 * See {@link FileAttributes} for attributes pertaining to individual files
 * within Celeste file systems.
 *
 * </p>
 */
public class FileSystemAttributes {
    /**
     * <p>
     *
     * Groups together definitions of names that identify various attributes
     * associated with {@link CelesteFileSystem} file systems.  Attributes
     * range from being completely read-only, through being settable only at
     * file creation time, to being settable at any time.
     *
     * </p><p>
     *
     * All attribute values must be presented as {@code String} values.  The
     * documentation for each individual attribute describes the attribute's
     * actual type and (where not obvious) how to convert to and from that
     * type.
     *
     * </p>
     */
    public static class Names {
        //
        // Prevent instantiation.
        //
        private Names() {
        }

        /**
         * The name of the maintain serial numbers attribute, which dictates
         * whether or not the files in its associated file system each have a
         * unique serial (<em>inode</em>) number.  This attribute is settable
         * at file system creation time, but is not subsequently modifiable.
         */
        public static final String MAINTAIN_SERIAL_NUMBERS_NAME =
            "MaintainSerialNumbers";
    }

    //
    // Prevent instantiation.
    //
    private FileSystemAttributes() {
    }

    /**
     * An unmodifiable set containing the names of all file system attributes.
     */
    public static final Set<String> attributeSet = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(new String[] {
            Names.MAINTAIN_SERIAL_NUMBERS_NAME
    })));
}
