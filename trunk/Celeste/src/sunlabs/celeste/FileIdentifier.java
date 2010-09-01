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
package sunlabs.celeste;

import java.io.Serializable;

import sunlabs.celeste.node.services.object.AnchorObject;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;

/**
 * A Celeste File Identifier.
 * <p>
 * A File Identifier is a {@link BeehiveObjectId} that represents a single Celeste file.
 * The object-id is the composite of two {@code BeehiveObjectId} instances.  The first is
 * the {@code nameSpaceId}, the {@code BeehiveObjectId} of a {@link Credential}
 * (stored in the Celeste object pool).
 *
 * The second is the {@code fileId}, another {@code BeehiveObjectId} generated from user supplied data.
 * A typical example is a String consisting of a name for the file, converted into bytes and converted
 * into a {@code BeehiveObjectId} by the {@link BeehiveObjectId#BeehiveObjectId(byte[])} method.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class FileIdentifier implements Serializable {
    private static final long serialVersionUID = 1L;

    private TitanGuid nameSpaceId;
    private TitanGuid fileId;

    /**
     * Constructs a new {@code FileIdentifier} from the given {@code nameSpaceId} and {@code fileId}.
     *
     * @param nameSpaceId   the name space containing the file this identifier
     *                      represents
     * @param fileId        the unique identifier of the file this identifier
     *                      represents within its name space
     *
     * @throws IllegalArgumentException  if any of {@code nameSpaceId} and {@code fileId} are {@code null}
     */
    public FileIdentifier(TitanGuid nameSpaceId, TitanGuid fileId) {
        this.nameSpaceId = nameSpaceId;
        this.fileId = fileId;
    }

    /**
     * Return the {@link BeehiveObjectId} of this FileIdentifier's name-space.
     */
    public TitanGuid getNameSpaceId() {
        return nameSpaceId;
    }

    /**
     * Return the {@link BeehiveObjectId} of this FileIdentifier's file-id.
     */
    public TitanGuid getFileId() {
        return fileId;
    }

    /**
     * Return the {@link BeehiveObjectId} of the {@link AnchorObject.Object} representing the
     * file named by the combination of the {@code nameSpaceId} and {@code fileId}.
     */
    public TitanGuid getObjectId() {
        return new TitanGuidImpl((this.nameSpaceId.toString() + this.fileId.toString()).getBytes());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FileIdentifier))
            return false;
        FileIdentifier fileIdentifier = (FileIdentifier)other;
        return this.nameSpaceId.equals(fileIdentifier.nameSpaceId) && this.fileId.equals(fileIdentifier.fileId);
    }

    @Override public int hashCode() {
        return this.nameSpaceId.hashCode() ^ this.fileId.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("FileIdentifier: ");
        result.append(this.nameSpaceId.toString()).append("/").append(this.fileId.toString());
        return result.toString();
    }
}
