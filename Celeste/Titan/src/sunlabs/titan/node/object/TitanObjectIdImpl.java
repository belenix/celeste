/*
 * Copyright 2010 Oracle. All Rights Reserved.
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
package sunlabs.titan.node.object;

import java.nio.ByteBuffer;
import java.security.PublicKey;

import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObjectId;

public class TitanObjectIdImpl extends TitanGuidImpl implements TitanObjectId {
    private static final long serialVersionUID = 1L;
    
    public TitanObjectIdImpl() {
        super();
    }

    /**
     * Construct a unique duplicate of the given object-id.
     * @param objectId the object-id to duplicate
     */
    public TitanObjectIdImpl(TitanGuid objectId) {
        super(objectId);
    }
    
    /**
     * Construct a new {@code TitanObjectIdImpl} instance from a String representation of the identifier in hex.
     * <p>
     * The given value must be a contiguous string of hexadecimal numerals {@link TitanGuidImpl#n_digits} in length.
     * </p>
     */
    public TitanObjectIdImpl(String hexValue) throws NumberFormatException {
        super(hexValue);
    }

    /**
     * Construct from an arbitrary byte array.
     *
     * The byte array contains data to hash to produce the identifier.
     */
    public TitanObjectIdImpl(byte[] data) {
        super(data);
    }

    /**
     * Construct from an arbitrary {@link ByteBuffer}.
     * <p>
     * The {@code ByteBuffer} contains data to hash to produce the identifier.
     * </p>
     */
    public TitanObjectIdImpl(ByteBuffer buffer) {
        super(buffer);
    }

    /**
     * Construct from a {@link PublicKey} instance.
     */
    public TitanObjectIdImpl(PublicKey key) {
        super(key);
    }
}
