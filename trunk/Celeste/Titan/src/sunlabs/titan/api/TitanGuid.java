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
package sunlabs.titan.api;

import java.io.Serializable;
import java.nio.ByteBuffer;

public interface TitanGuid extends Comparable<TitanGuid>, Serializable {
    public TitanGuid add(TitanGuid other);
    
    public TitanGuid add(String string);

    public TitanGuid add(long v);

    public TitanGuid add(ByteBuffer data);

    /**
     * Create a new {@code BeehiveObjectId} by combining more data to this {@code BeehiveObjectId}.
     *
     * @param data The data to combine.
     */
    public TitanGuid add(byte[] data);

    public byte[] getBytes();

    public int digit(int i);

    /**
     * Compute the object-id of this object-id.
     * @return The object-id of this object-id.
     */
    public TitanGuid getGuid();

    /**
     *
     * @param other
     * @return The number of digits (from left to right) that are shared
     *         between this object-id and <code>other</code>.
     */
    public int sharedPrefix(TitanGuid other);

    /**
     * Encode the routing table "distance" of this object-id with another.
     *
     * The value 0 indicates equality. Another other value is an integer encoding of
     * the length of the shared digit prefix and the difference between this object-id and the other at the first non-shared digit.
     *
     * @param other
     */
    public int distance(final TitanGuid other);
}
