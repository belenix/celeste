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

/**
 * A {@code TitanGuid} is how things (anything and everything) are named in a Titan system.
 */
public interface TitanGuid extends Comparable<TitanGuid>, Serializable {
    /**
     * Compose a new {@code TitanGuid} combining this {@code TitanGuid} with the TitanGuid {@code other}.
     * @param other
     * @return A new {@code TitanGuid} combining this {@code TitanGuid} with the TitanGuid {@code other}.
     */
    public TitanGuid add(TitanGuid other);

    /**
     * Compose a new {@code TitanGuid} combining this {@code TitanGuid} with the String {@code string}.
     * @param other
     * @return A new {@code TitanGuid} combining this {@code TitanGuid} with the String {@code string}.
     */
    public TitanGuid add(String string);

    /**
     * Compose a new {@code TitanGuid} combining this {@code TitanGuid} with the long {@code value}.
     * @param other
     * @return A new {@code TitanGuid} combining this {@code TitanGuid} with the long {@code value}.
     */
    public TitanGuid add(long value);
    
    /**
     * Compose a new {@code TitanGuid} combining this {@code TitanGuid} with the {@link ByteBuffer} {@code data}.
     * @param other
     * @return A new {@code TitanGuid} combining this {@code TitanGuid} with the {@link ByteBuffer} {@code data}.
     */
    public TitanGuid add(ByteBuffer data);
    
    /**
     * Compose a new {@code TitanGuid} combining this {@code TitanGuid} with the byte array {@code data}.
     * @param other
     * @return A new {@code TitanGuid} combining this {@code TitanGuid} with the byte array {@code data}.
     */
    public TitanGuid add(byte[] data);

    /**
     * Get this {@code TitanGuid} encoded into an array of bytes.
     * @return This {@code TitanGuid} encoded into an array of bytes.
     */
    public byte[] getBytes();

    /**
     * Get the integer value of the n'th digit of this {@code TitanGuid} counting from the left (most-significant digit) to the right.
     * @param i
     * @return the integer value of the n'th digit of this {@code TitanGuid} counting from the left (most-significant digit) to the right.
     */
    public int digit(int i);

    /**
     * Compute the {@code TitanGuid} of this {@code TitanGuid}.
     * @return The {@code TitanGuid} of this {@code TitanGuid}.
     */
    public TitanGuid getGuid();

    /**
     * Return the number of digits that are shared, counting from left to right, by this {@code TitanGuid} and the {@code TitanGuid} other.
     * 
     * @param other The {@code TitanGuid} to compare
     * @return The number of digits (from left to right) that are shared
     *         by {@code TitanGuid} and the {@code TitanGuid} other.
     */
    public int sharedPrefix(TitanGuid other);

    /**
     * Encode the routing table "distance" of this object-id with another.
     * <p>
     * The value 0 indicates equality. Another other value is an integer encoding of
     * the length of the shared digit prefix and the difference between this object-id and the other at the first non-shared digit.
     * </p>
     * @param other
     */
    public int distance(final TitanGuid other);
    
    /**
     * 
     * @return
     */
    short getHopCount();
}
