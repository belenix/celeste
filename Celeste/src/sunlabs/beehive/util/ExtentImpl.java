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
package sunlabs.beehive.util;

import static java.lang.Long.MIN_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.Serializable;


/**
 * {@code ExtentImpl} is an implementation of the {@code Extent} interface
 * (and nothing more).
 */
public class ExtentImpl implements Extent, Serializable {
    private final static long serialVersionUID = 0L;

    private final long  startOffset;
    private final long  endOffset;

    /**
     * Create a new extent.
     *
     * @param startOffset   the extent's starting offset
     * @param endOffset     the extent's ending offset ({@code Long.MIN_VALUE}
     *                      for an extent that extends as far as possible)
     */
    public ExtentImpl(long startOffset, long endOffset) {
        if (startOffset < 0 || (endOffset < 0 && endOffset != MIN_VALUE))
            throw new IllegalArgumentException(
                "argument(s) out of range");
        if (endOffset < startOffset && endOffset != MIN_VALUE)
            throw new IllegalArgumentException(
                "end offset precedes start offset");
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    /**
     * Create a new extent whose bounds are the same as {@code other}'s.
     *
     * @param other the extent to duplicate
     */
    public ExtentImpl(Extent other) {
        if (other == null)
            throw new IllegalArgumentException("null argument");
        this.startOffset = other.getStartOffset();
        this.endOffset = other.getEndOffset();
    }

    public long getStartOffset() {
        return this.startOffset;
    }

    public long getEndOffset() {
        return this.endOffset;
    }

    public boolean intersects(Extent other) {
        long tso = this.getStartOffset();
        long teo = this.getEndOffset();
        long oso = other.getStartOffset();
        long oeo = other.getEndOffset();

        //
        // Intersection is based on the sets of positions determined by the
        // extents being intersected.  Since the set associated with a vacuous
        // region is empty, the intersection of a vacuous extent with any
        // other extent is empty.  Thus, if either extent is vacuous, we must
        // return false.
        //
        if (oso == oeo || tso == teo)
            return false;

        //
        // The extents _don't_ intersect if either one of them starts beyond
        // the other's end, so return the negation of that test.
        //
        return !((oso >= teo && teo != Long.MIN_VALUE) ||
            (tso >= oeo && oeo != Long.MIN_VALUE));
    }

    public boolean contains(Extent other) {
        long tso = this.getStartOffset();
        long teo = this.getEndOffset();
        long oso = other.getStartOffset();
        long oeo = other.getEndOffset();

        //
        // Containment is based on the sets of positions determined by the
        // extents being compared.  Since the set associated with a vacuous
        // region is empty, a vacuous extent cannot contain positions not
        // contained in any other extent.  Thus if other is vacuous, we must
        // return true.
        //
        if (oso == oeo)
            return true;

        if (tso > oso)
            return false;
        if (teo == Long.MIN_VALUE)
            return true;
        if (oeo == Long.MIN_VALUE)
            return false;
        return teo >= oeo;
    }

    public boolean isVacuous() {
        return this.getStartOffset() == this.getEndOffset();
    }

    public Extent intersection(Extent other) {
        long thisStart = this.getStartOffset();
        long thisEnd = this.getEndOffset();
        long otherStart = other.getStartOffset();
        long otherEnd = other.getEndOffset();

        //
        // Check for disjointness.
        //
        if (!this.intersects(other))
            throw new IllegalArgumentException(
                String.format(
                    "disjoint extents: this [%d, %d), extent [%d %d)",
                    thisStart, thisEnd, otherStart, otherEnd));

        long start = max(thisStart, otherStart);
        long end = (thisEnd == Long.MIN_VALUE) ? otherEnd :
            (otherEnd == Long.MIN_VALUE) ? thisEnd :
            min(thisEnd, otherEnd);

        return new ExtentImpl(start, end);
    }

    public int compareTo(Extent other) {
        long myValue = this.getStartOffset();
        long otherValue = other.getStartOffset();
        return myValue < otherValue ? -1 :
            myValue == otherValue ? 0 : 1;
    }

    @Override
    public String toString() {
        long eo = this.getEndOffset();
        String eos = (eo == Long.MIN_VALUE) ? "infinity" : String.valueOf(eo);
        return String.format("[%d, %s)",
            this.getStartOffset(), eos);
    }
}
