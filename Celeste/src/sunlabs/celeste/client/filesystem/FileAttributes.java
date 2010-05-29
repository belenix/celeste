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
import java.util.Map;
import java.util.Set;

import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.node.services.object.BlockObject;

/**
 * <p>
 *
 * Provides a nested class that groups together definitions of names that
 * identify various attributes associated with {@link CelesteFileSystem}
 * files, along with methods for classifying those attributes into categories.
 *
 * </p><p>
 *
 * See {@link FileSystemAttributes} for attributes pertaining to Celeste file
 * systems themselves.
 *
 * </p>
 */
//
// XXX: Ought to be expanded to include attributes, such as file size that
//      Celeste itself maintains.  Doing so raises the issue of agreement for
//      attribute names as defined in Celeste and as defined here.  (We
//      already have this issue for ACL_NAME.  Celeste maintains a file's ACL
//      as part of its own metadata for a given file.  The name used to refer
//      to the ACL in that metadata is CelesteAPI.VOBJECT_ACL_NAME.  Perhaps
//      this simply illustrates that the same attribute can appear at
//      different levels of abstraction and that code wishing to access the
//      attribute needs to be mindful of which level the access applies to.
//      It's easy to get confused because each level uses OrderedProperties to
//      hold its attributes.)
//
public class FileAttributes {
    /**
     * <p>
     *
     * Groups together definitions of names that identify various attributes
     * associated with {@link CelesteFileSystem} files.  Attributes range from
     * being completely read-only, through being settable only at file
     * creation time, to being settable at any time.
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
         * <p>
         *
         * The name of the access control list attribute, which dictates what
         * permissions a given file operation requires.  This attribute is
         * settable at file creation time and is subsequently modifiable.
         *
         * </p><p>
         *
         * To convert to and from this attribute's native {@code CelesteACL}
         * form, use the {@link
         * sunlabs.celeste.node.CelesteACL#toEncodedString()
         * CelesteACL.toEncodedString()} and {@link
         * sunlabs.celeste.node.CelesteACL#getEncodedInstance(String)
         * CelesteACL.getEncodedInstance} methods.
         *
         * </p>
         */
        public static final String ACL_NAME = "ACL";

        /**
         * <p>
         *
         * The name of the block size attribute, which determines the size of
         * the {@link BlockObject}s used to store the file's contents in Celeste.  This
         * attribute is settable at file creation time, but is not
         * subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of an {@code int} value.
         *
         * </p>
         */
        public static final String BLOCK_SIZE_NAME = "BlockSize";

        /**
         * <p>
         *
         * The name of the buffer cache enabled attribute, which determines
         * whether or not data from a given modification to the file will be
         * stored locally in the client-side buffer cache.  This attribute is
         * transitory, applying only to the handle used to access the file, as
         * opposed to the file itself.  It may be set at any time.
         *
         * </p><p>
         *
         * This attribute is encoded as the {@code String} representation of
         * a {@code boolean} value.
         *
         * </p>
         */
        public static final String CACHE_ENABLED_NAME = "CacheEnabled";

        /**
         * The name of the client metadata attribute, which records
         * client-supplied metadata that the file system implementation simply
         * stores and retrieves upon request, without itself interpreting or
         * using the metadata.  This attribute is settable at file creation
         * time and is subsequently modifiable.
         */
        //
        // XXX: Need to describe encoding, but OrderedProperties should grow
        //      more convenient methods for doing so first.
        //
        public static final String CLIENT_METADATA_NAME = "ClientMetadata";

        /**
         * The name of the content type attribute, which records the file's
         * MIME type.  This attribute is settable at file creation time and is
         * subsequently modifiable.
         */
        public static final String CONTENT_TYPE_NAME = "ContentType";

        /**
         * <p>
         *
         * The name of the file creation time attribute.  This attribute is
         * set by the file system implementation when a file is created and is
         * not subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String CREATED_TIME_NAME = "CreatedTime";

        /**
         * <p>
         *
         * The name of the deletion time to live attribute, which gives the
         * number of seconds each Celeste object used to represent a given
         * file will remain within Celeste after a file has been deleted.
         * This attribute is settable at file creation time and can be
         * modified from then until the file is deleted.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String DELETION_TIME_TO_LIVE_NAME =
            "DeletionTimeToLive";

        /**
         * <p>
         *
         * The name of the file serial number (<em>inode number</em>)
         * attribute.  This attribute is set by the file system implementation
         * when a file is created and is not subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String FILE_SERIAL_NUMBER_NAME = "FileSerialNumber";

        /**
         * <p>
         *
         * The name of the <q>is deleted</q> attribute.  This attribute is set
         * by the file system implementation to reflect a file's deletion
         * status and cannot otherwise be modified.
         *
         * </p><p>
         *
         * This attribute is encoded as the {@code String} representation of
         * a {@code boolean} value.
         *
         * </p>
         */
        public static final String IS_DELETED_NAME = "Deleted";

        /**
         * <p>
         *
         * The name of the metadata changed time attribute, which records
         * when file metadata was last changed.  This attribute is updated as
         * necessary by the file system implementation and cannot otherwise be
         * modified.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String METADATA_CHANGED_TIME_NAME =
            "MetadataChangedTime";

        /**
         * <p>
         *
         * The name of the file modification time attribute.  This attribute is
         * set by the file system implementation when a file is created and
         * updated whenever that file is modified.  It cannot otherwise be
         * modified.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String MODIFIED_TIME_NAME = "ModifiedTime";

        /**
         * <p>
         *
         * The name of the file replication parameters attribute, which
         * dictates how much redundancy Celeste should maintain when storing a
         * file.  This attribute is settable at file creation time, but is not
         * subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as a semicolon-separated string of
         * <i>attribute</i>=<i>value</i> pairs describing how the various
         * Celeste objects comprising a given file are to be replicated.
         *
         * </p>
         */
        public static final String REPLICATION_PARAMETERS_NAME =
            "ReplicationParameters";

        /**
         * <p>
         *
         * The name of the <q>data must be signed</q> attribute, which
         * dictates whether operations that modify the file must be digitally
         * signed.  This attribute is settable at file creation time, but is
         * not subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as the {@code String} representation of
         * a {@code boolean} value.
         *
         * </p>
         */
        public static final String SIGN_MODIFICATIONS_NAME = "SignModifications";

        /**
         * <p>
         *
         * The name of the time to live attribute, which gives the number of
         * seconds each Celeste object used to represent a given file will
         * remain within Celeste.  This attribute is settable at file creation
         * time, but is not subsequently modifiable.
         *
         * </p><p>
         *
         * This attribute is encoded as the decimal {@code String}
         * representation of a {@code long} value.
         *
         * </p>
         */
        public static final String TIME_TO_LIVE_NAME = "TimeToLive";
    }

    /**
     * An enumeration whose elements name the various spans over which file
     * attributes may be set.
     */
    public enum WhenSettable {
        /**
         * The attribute is never explicitly settable, but may only be
         * inspected.
         *
         * @see #neverSet
         */
        never,

        /**
         * The attribute may be set only when its corresponding file is
         * created.
         *
         * @see #creationSet
         */
        atCreation,

        /**
         * the attribute may be set at any time during its corresponding
         * file's lifetime.
         *
         * @see #settableSet
         */
        duringLifetime
    }

    //
    // Prevent instantiation.
    //
    private FileAttributes() {
    }

    /**
     * An unmodifiable set containing the names of all file attributes.
     */
    public static final Set<String> attributeSet = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(new String[] {
            Names.ACL_NAME,
            Names.BLOCK_SIZE_NAME,
            Names.CACHE_ENABLED_NAME,
            Names.CLIENT_METADATA_NAME,
            Names.CONTENT_TYPE_NAME,
            Names.CREATED_TIME_NAME,
            Names.DELETION_TIME_TO_LIVE_NAME,
            Names.FILE_SERIAL_NUMBER_NAME,
            Names.IS_DELETED_NAME,
            Names.METADATA_CHANGED_TIME_NAME,
            Names.MODIFIED_TIME_NAME,
            Names.REPLICATION_PARAMETERS_NAME,
            Names.SIGN_MODIFICATIONS_NAME,
            Names.TIME_TO_LIVE_NAME
    })));

    /**
     * An unmodifiable set containing the names of all file attributes that
     * can be set when a file is created.  That is, the set of attribute
     * names corresponding to {@code WhenSettable.atCreation}.
     */
    public static final Set<String> creationSet = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(new String[] {
            Names.ACL_NAME,
            Names.BLOCK_SIZE_NAME,
            Names.CACHE_ENABLED_NAME,
            Names.CLIENT_METADATA_NAME,
            Names.CONTENT_TYPE_NAME,
            Names.DELETION_TIME_TO_LIVE_NAME,
            Names.REPLICATION_PARAMETERS_NAME,
            Names.SIGN_MODIFICATIONS_NAME,
            Names.TIME_TO_LIVE_NAME
    })));

    /**
     * An unmodifiable set containing the names of all file attributes that
     * cannot be set when a file is created.  That is, the set of attribute
     * names corresponding to {@code WhenSettable.never}.
     */
    public static final Set<String> neverSet = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(new String[] {
            Names.CREATED_TIME_NAME,
            Names.FILE_SERIAL_NUMBER_NAME,
            Names.IS_DELETED_NAME,
            Names.METADATA_CHANGED_TIME_NAME,
            Names.MODIFIED_TIME_NAME
    })));

    /**
     * An unmodifiable set containing the names of all file attributes that
     * can be set only when a file is created.
     */
    public static final Set<String> onlyAtCreationSet = Collections.
        unmodifiableSet(new HashSet<String>(Arrays.asList(new String[] {
            Names.BLOCK_SIZE_NAME,
            Names.REPLICATION_PARAMETERS_NAME,
            Names.SIGN_MODIFICATIONS_NAME,
            Names.TIME_TO_LIVE_NAME
    })));

    /**
     * An unmodifiable set containing the names of all file attributes that
     * can be set at arbitrary times during a file's lifetime.  That is, the
     * set of attribute names corresponding to {@code
     * WhenSettable.duringLifetime}.
     */
    public static final Set<String> settableSet = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(new String[] {
            Names.ACL_NAME,
            Names.CACHE_ENABLED_NAME,
            Names.CLIENT_METADATA_NAME,
            Names.CONTENT_TYPE_NAME,
            Names.DELETION_TIME_TO_LIVE_NAME
    })));

    /**
     * Returns a copy of {@code originalAttrs} that contains only mappings for
     * attributes that may be set under the circumstances named by {@code
     * which}.
     *
     * @param which         the category of attribute mappings to select from
     *                      {@code originalAttrs}
     * @param originalAttrs the attribute map from which to select mappings
     *
     * @return  a restriction of the original properties map containing only
     *          the selected properties
     */
    public static OrderedProperties filterAttributes(WhenSettable which,
            OrderedProperties originalAttrs) {
        if (originalAttrs == null)
            return null;

        Set<String> attrNames = null;
        switch (which) {
        case never:
            attrNames = neverSet;
            break;
        case atCreation:
            attrNames = creationSet;
            break;
        case duringLifetime:
            attrNames = settableSet;
            break;
        }

        OrderedProperties filteredAttrs = new OrderedProperties();
        for (Map.Entry<Object, Object> entry : originalAttrs.entrySet()) {
            String name = (String)entry.getKey();
            if (attrNames.contains(name))
                filteredAttrs.put(name, entry.getValue());
        }

        return filteredAttrs;
    }
}
