/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.titan.util;

import java.io.Serializable;


/**
 * {@code BufferableExtentImpl} is an implementation of the {@code
 * BufferableExtent} interface.  It also includes a method for checking
 * whether an arbitrary {@code Extent} has bounds tight enough to allow it to
 * be represented as a {@code BufferableExtent}.
 */
public class BufferableExtentImpl implements BufferableExtent, Serializable {
    private final static long serialVersionUID = 0L;

    private final long  startOffset;
    private final int   length;

    /**
     * Create a new bufferable extent.
     *
     * @param startOffset   the bufferable extent's starting offset
     * @param length        the bufferable extent's length
     */
    public BufferableExtentImpl(long startOffset, int length) {
        if (startOffset < 0 || length < 0)
            throw new IllegalArgumentException(
                "argument(s) out of range");
        //
        // Verify that startOffset isn't so close to Long.MAX_VALUE that
        // adding length takes it past Long.MIN_VALUE (that is beyond the
        // maximally extending extent).
        //
        long endOffset = startOffset + length;
        if (endOffset < 0 && endOffset != Long.MIN_VALUE)
            throw new IllegalArgumentException(
                "argument(s) out of range");

        this.startOffset = startOffset;
        this.length = length;
    }

    /**
     * Create a new bufferable extent whose bounds are the same as {@code
     * other}'s.
     *
     * @param other the extent to duplicate
     * 
     * @throws IllegalArgumentException
     *      if {@code other} spans too great a range to be representable as a
     *      {@code BufferableExtent}
     *
     */
    public BufferableExtentImpl(Extent other) {
        if (other == null)
            throw new IllegalArgumentException("null argument");
        if (!BufferableExtentImpl.isBufferable(other))
            throw new IllegalArgumentException(
                "not representable as a BufferableExtent");

        this.startOffset = other.getStartOffset();
        long length = other.getEndOffset() - other.getStartOffset();
        this.length = (int)length;
    }

    public int getLength() {
        return this.length;
    }

    public long getStartOffset() {
        return this.startOffset;
    }

    public long getEndOffset() {
        return this.startOffset + this.length;
    }

    public boolean intersects(Extent other) {
        return (new ExtentImpl(this)).intersects(other);
    }

    public boolean contains(Extent other) {
        return (new ExtentImpl(this)).contains(other);
    }

    public boolean isVacuous() {
        return this.getStartOffset() == this.getEndOffset();
    }

    public Extent intersection(Extent other) {
        return (new ExtentImpl(this)).intersection(other);
    }

    public int compareTo(Extent other) {
        long myValue = this.getStartOffset();
        long otherValue = other.getStartOffset();
        return myValue < otherValue ? -1 :
            myValue == otherValue ? 0 : 1;
    }

    @Override
    public String toString() {
        return String.format("[%d # %d)",
            this.getStartOffset(), this.getLength());
    }

    /**
     * Return {@code true} if {@code extent}'s bounds span a small enough
     * reach that it could be represented as a {@code BufferableExtent} and
     * {@code false} otherwise.
     *
     * @param extent    the {@code Extent} whose bounds are to be checked
     *
     * @throws IllegalArgumentException
     *      if {@code extent} is {@code null}
     */
    public static boolean isBufferable(Extent extent) {
        if (extent == null)
            throw new IllegalArgumentException("argument must not be null");
        long length = extent.getEndOffset() - extent.getStartOffset();
        //
        // Verify that extent's length is representable as a non-negative int.
        // (This check works even if extent.getEndOffset() == Long.MIN_VALUE).
        //
        return length >= 0 && length <= Integer.MAX_VALUE;
    }
}
