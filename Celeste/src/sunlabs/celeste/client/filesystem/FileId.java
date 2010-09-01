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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.celeste.client.filesystem;

import sunlabs.titan.api.TitanGuid;

/**
 * <p>
 *
 * A {@code FileId} object encapsulates the information required to uniquely
 * identify a Celeste file.  This identity has three determinants:
 *
 * </p><ul>
 *
 * <li>
 *
 * The object-id of the Celeste confederation hosting the file.
 *
 * </li><li>
 *
 * The object-id of the {@link sunlabs.celeste.client.Profile_
 * profile} used to determine the name space in which the file resides.
 *
 * </li><li>
 *
 * The randomly generated object-id used to make the file unique
 * within the universe determined by the confederation and name space.
 *
 * </li>
 *
 * </ul>
 */
public class FileId {
    private final TitanGuid  networkId;
    private final TitanGuid  nameSpaceId;
    private final TitanGuid  uniqueId;

    /**
     * Create a new {@code FileId} from the given {@code celesteProxy} and
     * object-ids for the {@code nameSpaceId} and {@code uniqueId}.
     *
     * @param networkId     the object id of the desired Celeste confederation
     *                      
     * @param nameSpaceId   the object id of the profile defining the name
     *                      space in which the file resides
     * @param uniqueId      the object id uniquely identifying the file within
     *                      the Celeste confederation
     *
     * @throws IllegalArgumentException
     *      if any of the arguments are {@code null}
     */
    public FileId(TitanGuid networkId, TitanGuid nameSpaceId,
            TitanGuid uniqueId) {
        if (networkId == null || nameSpaceId == null || uniqueId == null)
            throw new IllegalArgumentException(
                "all arguments must be non-null");

        this.networkId = networkId;
        this.nameSpaceId = nameSpaceId;
        this.uniqueId = uniqueId;
    }

    /**
     * Return the object-id of the Celeste confederation to which
     * this {@code FileId} belongs.
     *
     * @return  the Celeste confederation's network id
     */
    public TitanGuid getNetworkId() {
        return this.networkId;
    }

    /**
     * Return the object-id of the profile in whose name space
     * the file denoted by this {@code FileId} resides.
     *
     * @return  the profile's id
     */
    public TitanGuid getNameSpaceId() {
        return this.nameSpaceId;
    }

    /**
     * Return the object-id used to uniquely identify the file
     * to which this {@code FileId} refers.
     *
     * @return  the unique id
     */
    public TitanGuid  getUniqueId() {
        return this.uniqueId;
    }

    /**
     * Two {@code FileId} objects are equal if and only if all their internal
     * components are equal.
     *
     * @inheritDoc
     */
    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (!(other instanceof FileId))
            return false;
        FileId id = (FileId) other;

        return this.networkId.equals(id.networkId) &&
            this.nameSpaceId.equals(id.nameSpaceId) &&
            this.uniqueId.equals(id.uniqueId);
    }

    @Override
    public int hashCode() {
        return this.networkId.hashCode() ^
            this.nameSpaceId.hashCode() ^
            this.uniqueId.hashCode();
    }

    //
    // XXX: Provide variant that truncates the object-ids down to a given
    //      prefix length?
    //
    @Override
    public String toString() {
        return String.format("FileId[%s, %s, %s]",
            this.networkId.toString(),
            this.nameSpaceId.toString(),
            this.uniqueId.toString());
    }

    /**
     * A variant of {@link #toString()} that emits only the first {@code
     * prefixLength} characters of each {@code TitanGuid} in the tuple
     * representing this {@code FileId}.
     *
     * @param prefixLength  the length to truncate {@code TitanGuid}s to
     *
     * @return  an abbreviated string representation of this {@code FileId}
     */
    public String toString(int prefixLength) {
        if (prefixLength <= 0)
            throw new IllegalArgumentException("prefixLength must be positive");
        //
        // Construct a format containing length specifiers for each of the
        // three fields that will ultimately be rendered.
        //
        // XXX: The C printf function allows the length specifiers to be given
        //      dynamically.  But the Java String.format() method doesn't
        //      appear to allow this.
        //
        // XXX: TitanGuid ought to supply a toString(prefixLength)
        //      method.
        //
        String format = String.format("FileId[%%%d.%ds, %%%d.%ds, %%%d.%ds]",
            prefixLength, prefixLength,
            prefixLength, prefixLength,
            prefixLength, prefixLength);
        return String.format(format, 
            this.networkId.toString(),
            this.nameSpaceId.toString(),
            this.uniqueId.toString());
    }
}
