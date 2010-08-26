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

/**
 * <p>
 *
 * The {@code Extent} interface describes a contiguous region of an underlying
 * sequentially indexable entity, such as a {@code ByteBuffer} or a file.  An
 * extent has a starting position relative to the overall beginning of the
 * sequence, given by {@code getStartOffset()} and an ending position, given
 * by {@code getEndOffset()} and defined as the offset relative to the overall
 * beginning of the sequence of the first position not in the extent.  (If the
 * extent extends to the maximum length possible, this offset will be
 * represented as {@code Long.MIN_VALUE}).  An extent can be considered to
 * define a partial view of its underlying sequence.
 *
 * </p><p>
 *
 * It is permissible for an extent to be vacuous, that is, for its start
 * offset to equal its end offset.  (But note that an extent with both start
 * and end offsets equal to {@code Long.MIN_VALUE} is not legal, since that
 * value is not permissible as a start offset.)
 *
 * </p><p>
 *
 * Classes implementing this interface must implement the {@code compareTo()}
 * method of the {@code Comparable} interface.  It is expected, although not
 * enforced, that the method's implementation compare starting offsets.  Such
 * an implementation allows a sequence's data to be captured in a sorted set
 * or map of extents, so that the data at a given offset can be located
 * efficiently.
 *
 * </p><p>
 *
 * Classes implementing this interface must enforce the constraint that
 * 
 * <pre>  (getEndOffset() == Long.MIN_VALUE || getEndOffset() >= getStartOffset()) &&
 *   getStartOffset() >= 0</pre>
 * 
 * and throw {@code IllegalArgumentException} for any operation that would
 * violate it.
 *
 * </p>
 */
public interface Extent extends Comparable<Extent> {
    /**
     * Return the offset in the underlying sequentially indexable entity
     * corresponding to offset {@code 0} of this extent.
     *
     * @return  the offset corresponding the beginning of this extent
     */
    public long getStartOffset();

    /**
     * Return the offset in the underlying sequentially indexable entity
     * corresponding to the first position beyond the end of this extent.
     *
     * @return  the offset corresponding to the first position beyond the
     *          extent's end
     */
    public long getEndOffset();

    /**
     * Return {@code true} if this extent and {@code other} are not disjoint
     * and {@code false} otherwise.  Two extents are disjoint if the sets of
     * positions determined by their beginning and ending offsets contain no
     * position in common.  Note that this definition implies that a vacuous
     * extent never intersects another extent, even itself.
     *
     * @param other the extent to be checked for intersection with this extent
     *
     * @return  {@code true} if the extents are non-disjoint and {@code false}
     *          otherwise
     */
    public boolean intersects(Extent other);

    /**
     * Return {@code true} if this extent contains {@code other} and {@code
     * false} otherwise.  Containment is based on the sets of offsets
     * determined by the extents being compared.  Thus, this method returns
     * {@code true} if {@code other}'s set is a subset of {@code this}'s set.
     *
     * @param other the extent to be checked for containment within this
     *              extent
     *
     * @return  {@code true} if {@code other} is contained within this extent
     */
    public boolean contains(Extent other);

    /**
     * Return {@code true} if this extent is vacuous and {@code false}
     * otherwise.  An extent is vacuous if its start offset equals its end
     * offset.
     *
     * @return  {@code true} if this extent is vacuous and {@code false}
     *          otherwise
     */
    public boolean isVacuous();

    /**
     * Return an extent that is the intersection of this extent with {@code
     * other}.  Throws {@code IllegalArgumentException} if the two extents are
     * disjoint.
     *
     * @param other the extent to intersect with this one
     *
     * @return  the intersection of this extent with {@code other}
     *
     * @throws IllegalArgumentException
     *      if the two extents are disjoint
     */
    public Extent intersection(Extent other);
}
