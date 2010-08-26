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
package sunlabs.titan.util;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * <p>
 *
 * The {@code ExtentMap} class provides an ordered map holding a disjoint,
 * non-overlapping collection of extents.  For the purpose of determining
 * overlap, an empty extent is considered to occupy its
 * {@link sunlabs.titan.util.Extent#getStartOffset() starting offset}
 * and overlaps any other extent that has that offset as part of its inclusive
 * span.
 *
 * </p><p>
 *
 * The class includes methods for finding the extent covering a given offset,
 * retrieving the minimal sub-map that overlaps with a given extent, and for
 * removing such overlapping extents.
 *
 * </p><p>
 *
 * The class itself implements the {@code Extent} interface, so an entire
 * {@code ExtentMap} object itself acts an extent, with its bounds extending
 * from the lowest starting offset of its contained extents to the highest
 * ending offset of those extents.  (If the extent map is empty, attempts to
 * use it as an {@code Extent} will induce an {@code IllegalStateException}).
 *
 * </p>
 */
public class ExtentMap<E extends Extent> extends TreeMap<Long, E>
        implements Extent {
    private final static long serialVersionUID = -3719212290080441085L;

    /**
     * Construct an empty extent map.
     */
    public ExtentMap() {
        super();
    }

    /**
     * Construct an extent map that holds all the entries of {@code oldMap}.
     * If any of {@code oldMap}'s entries overlap, this constructor will throw
     * {@code IllegalStateException}.
     *
     * @param oldMap    a map of extents that are to be used to initialize
     *                  this {@code ExtentMap}
     *
     * @throws IllegalArgumentException
     *      if {@code oldMap} contains overlapping extents
     */
    public ExtentMap(SortedMap<Long, E> oldMap) {
        this();
        for (Map.Entry<Long, E> entry : oldMap.entrySet())
            this.put(entry.getKey(), entry.getValue());
    }

    //
    // XXX: Other constructors mirroring other TreeMap constructors?
    //

    /**
     * Return the extent from this map that covers {@code offset}, or {@code
     * null} if there is no such extent.
     *
     * @param offset    the offset at which an extent that covers it is to be
     *                  found
     *
     * @return  the extent that covers {@code offset} or {@code null} if no
     *          extent in the map covers it
     *
     * @throws IllegalArgumentException
     *      if {@code offset} isn't a valid offset (negative, including {@code
     *      Long.MIN_VALUE})
     */
    public synchronized E getExtent(long offset) {
        //
        // Check the argument for validity.  The test also catches
        // Long.MIN_VALUE, which marks the first offset past the maximum
        // offset that can be part of an extent, but cannot itself fall within
        // an extent.
        //
        if (offset < 0)
            throw new IllegalArgumentException("illegal offset " + offset);

        //
        // XXX: Linear search; could be improved to binary search if need be.
        //
        for (E extent : values()) {
            long eso = extent.getStartOffset();
            if (eso > offset)
                return null;
            long eeo = extent.getEndOffset();
            if (eeo != Long.MIN_VALUE && eeo <= offset)
                continue;
            return extent;
        }
        //
        // Not reached...
        //
        return null;
    }

    /**
     * Return a copy of the portion of this map that overlaps with {@code
     * extent}.
     *
     * @param extent    the extent for which overlapping extents are to be
     *                  returned as a view of this map
     *
     * @return  a copy of the portion of this map containing extents that
     *          overlap with {@code extent}
     */
    //
    // XXX: Ought to return a view rather than a copy.  But I haven't been
    //      able to work out the types in a way that the compiler will accept.
    //      (See commented out return statements at the end for what I'd like
    //      to accomplish.)
    // XXX: This method needs to be used with caution.  When the map contains
    //      extent buffers (as opposed to plain extents), the internal state
    //      of the buffers becomes of concern.  In particular, callers can
    //      affect the state of those buffers in ways that the original map's
    //      owner might not expect.  Stated differently, we might want to have
    //      ExtentBufferMap override this method to ensure that the returned
    //      map contains duplicates of the original map's ExtentBuffers.
    //
    public synchronized ExtentMap<E> getOverlapping(Extent extent) {
        //
        // Eliminate the trivial case of this map being empty.  Then the
        // submap must be empty as well.
        //
        if (this.isEmpty())
            return new ExtentMap<E>();

        boolean haveStart = false;
        boolean haveEnd = false;
        long fromKey = -1;
        long toKey = -1;
        long start = extent.getStartOffset();
        long end = extent.getEndOffset();

        //
        // Find the starting and ending keys that specify the submap to be
        // returned.  Note that the ending key must be the first key past the
        // last that's included in the desired range.
        //
        for (long key : this.keySet()) {
            E e = this.get(key);
            long curStart = e.getStartOffset();
            long curEnd = e.getEndOffset();

            //
            // Skip over leading extents that end before the start of the one
            // for which we're computing overlaps.
            //
            if (curEnd != Long.MIN_VALUE && curEnd <= start)
                continue;

            //
            // If the start of the extent under consideration falls after the
            // end of the one for which we're computing overlaps, we need
            // examine no more of the map.  Record it as the first past the
            // end of the submap we're computing.
            //
            if (curStart >= end && end != Long.MIN_VALUE) {
                haveEnd = true;
                toKey = key;
                break;
            }

            //
            // This extent is neither not far enough along in the sequence nor
            // too far along; thus, it must overlap the argument extent.  If
            // we haven't already noted the starting key of the submap, this
            // is it.
            //
            if (!haveStart) {
                haveStart = true;
                fromKey = key;
            }
        }

        //
        // XXX: I've been unable to find a way to write this code in a way
        //      both that the compiler accepts and that avoids constructing an
        //      extra copy of the extent map.
        //
        if (!haveStart) {
            //
            // If we never found the start of the submap, it must be empty.
            //
            //return (ExtentMap<E>)this.subMap(this.firstKey(), this.firstKey());
            return
                new ExtentMap<E>(this.subMap(this.firstKey(), this.firstKey()));
        }
        if (!haveEnd) {
            //
            // The submap extends all the way to the end.
            //
            //return (ExtentMap<E>)this.tailMap(fromKey);
            return new ExtentMap<E>(this.tailMap(fromKey));
        }
        //return (ExtentMap<E>)this.subMap(fromKey, toKey);
        return new ExtentMap<E>(this.subMap(fromKey, toKey));
    }

    /**
     * Remove all extents from this map that overlap with {@code extent},
     * returning a map containing the removed extents.
     *
     * @param extent    the extent against which no overlaps should remain
     */
    public synchronized ExtentMap<E> removeOverlaps(E extent) {
        ExtentMap<E> overlapMap = this.getOverlapping(extent);
        for (Long key : overlapMap.keySet())
            this.remove(key);
        return overlapMap;
    }

    /**
     * Return a new extent map containing extents from this map under the
     * constraint that {@code startOffset} is contained in its first extent
     * and that each successive extent is contiguous to its predecessor.  If
     * necessary to preserve contiguity and to obtain an extent map of maximal
     * length, the returned map may contain extents that are part of nested
     * extent maps.
     *
     * @param startOffset   the offset to be contained in the first extent of
     *                      the returned extent map
     *
     * @return  a contiguous extent map containing {@code startOffset} in its
     *          first extent
     */
    public synchronized ExtentMap<E> getContiguousFrom(long startOffset) {
        ExtentMap<E> contiguousMap = new ExtentMap<E>();
        if (this.isEmpty())
            return contiguousMap;
        getContiguousFrom(startOffset, this, contiguousMap);
        return contiguousMap;
    }

    //
    // Helper method for getContiguousFrom above.  Returns the end offset of
    // the contiguous extent it added to contiguousMap.  Assumes that
    // parentMap is non-empty.
    //
    private synchronized long getContiguousFrom(long startOffset, ExtentMap<E> parentMap,
            ExtentMap<E> contiguousMap) {
        //
        // Consider each extent in order.
        //
        boolean havePrev = false;
        long prevLast = 0;
        for (E e : parentMap.values()) {
            long eeo = e.getEndOffset();
            if (eeo <= startOffset && eeo != Long.MIN_VALUE) {
                //
                // This extent ends before startOffset and therefore isn't
                // part of the result map.
                //
                continue;
            }
            if (havePrev && prevLast != e.getStartOffset()) {
                //
                // This extent is not contiguous with the previous one.  We're
                // done.
                //
                return prevLast;
            }
            havePrev = true;
            prevLast = eeo;
            //
            // If this extent is itself an extent map, we need to consider
            // whether it's contiguous.  if so, it can be added directly;
            // otherwise, it must be considered recursively.  (Note that if
            // it's not contiguous, it must be non-empty.)
            //
            if (!(e instanceof ExtentMap)) {
                contiguousMap.put(e);
                continue;
            }
            //
            // There seems to be no way to express this cast safely; hence the
            // @SuppressWarnings annotation.
            //
            @SuppressWarnings(value="unchecked")
                ExtentMap<E> childMap = (ExtentMap<E>)e;
            if (childMap.isContiguous()) {
                contiguousMap.put(e);
                continue;
            }
            prevLast = getContiguousFrom(startOffset, childMap, contiguousMap);
        }
        //
        // Not reached.
        //
        return parentMap.getEndOffset();
    }

    /**
     * Return {@code true} if the extents in this map abut each other
     * directly, with no intervening gaps, and {@code false} otherwise.  An
     * empty map is considered to be contiguous.  If a map contains nested
     * extent maps, those nested maps must be internally contiguous for the
     * map as a whole to be contiguous.
     *
     * @return  {@code true} if this map's extents are contiguous and {@code
     *          false} otherwise
     */
    public synchronized boolean isContiguous() {
        //
        // Empty maps and ones with a single entry both qualify as contiguous.
        //
        if (this.size() <= 1)
            return true;

        boolean havePrevEnd = false;
        long prevEnd = -1;
        for (Extent e : this.values()) {
            //
            // If e is a nested extent, it must be internally contiguous (as
            // well has having its ends be contiguous with its neighbors).
            //
            if (e instanceof ExtentMap) {
                ExtentMap<?> em = (ExtentMap<?>)e;
                if (!em.isContiguous())
                    return false;
            }
            if (havePrevEnd) {
                if (prevEnd != e.getStartOffset())
                    return false;
            } else {
                havePrevEnd = true;
            }
            prevEnd = e.getEndOffset();
        }
        return true;
    }

    /**
     * Add {@code extent} to the map, using {@code extent.getStartOffset()} as
     * its key.
     *
     * @param extent    the extent to add to the map
     *
     * @throws IllegalArgumentException
     *      if {@code extent} overlaps an extent already present in this map
     */
    public synchronized void put(E extent) {
        put(extent.getStartOffset(), extent);
    }

    /**
     * @inheritDoc
     *
     * @throws IllegalArgumentException
     *      if {@code extent} overlaps an extent already present in this map
     */
    @Override
    public synchronized E put(Long startOffset, E extent) {
        //
        // Check consistency and non-overlap constraints.
        //
        if (extent.getStartOffset() != startOffset)
            throw new IllegalArgumentException(
                "startOffset != extent.getStartOffset()");
        checkNonOverlap(extent);

        return super.put(startOffset, extent);
    }

    /**
     * @inheritDoc
     *
     * @throws IllegalArgumentException
     *      if any of the extents in {@code map} overlap an extent already
     *      present in this map
     */
    @Override
    public synchronized void putAll(Map<? extends Long, ? extends E> map) {
        //
        // Check for non-overlap in each entry.
        //
        // XXX: Quadratic time; this may become a bottleneck.
        //
        for (E extent : map.values())
            checkNonOverlap(extent);

        super.putAll(map);
    }

    //
    // The Extent operations
    //

    /**
     * Returns the smallest of the starting offsets of the extents in this
     * map.
     *
     * @inheritDoc
     *
     * @throws IllegalStateException
     *      if this extent map is empty
     */
    public synchronized long getStartOffset() {
        if (this.isEmpty())
            throw new IllegalStateException(
                "start offset undefined for empty maps");
        return this.get(this.firstKey()).getStartOffset();
    }

    /**
     * Returns the largest of the ending offsets of the extents in this map or
     * {@code Long.MIN_VALUE} if the map's last extent extends to the largest
     * possible offset.
     *
     * @inheritDoc
     *
     * @throws IllegalStateException
     *      if this extent map is empty
     */
    public synchronized long getEndOffset() {
        if (this.isEmpty())
            throw new IllegalStateException(
                "end offset undefined for empty maps");
        return this.get(this.lastKey()).getEndOffset();
    }

    /**
     * @inheritDoc
     *
     * @throws IllegalStateException
     *      if this extent map is empty
     */
    public synchronized boolean intersects(Extent other) {
        if (this.isEmpty())
            throw new IllegalStateException(
                "intersects() undefined for empty maps");
        return new ExtentImpl(this).intersects(other);
    }

    public synchronized boolean contains(Extent other) {if (this.isEmpty())
        if (this.isEmpty())
            throw new IllegalStateException(
                "contains() undefined for empty maps");
        return new ExtentImpl(this).contains(other);
    }

    /**
     * Return {@code true} if this extent map is vacuous and {@code false}
     * otherwise.  An extent map is vacuous if it is either empty or vacuous
     * when viewed as an extent (that is, if its start and end offsets are
     * equal).
     *
     * @return  {@code true} if this extent is vacuous and {@code false}
     *          otherwise
     */
    public synchronized boolean isVacuous() {
        if (this.isEmpty())
            return true;
        return this.getStartOffset() == this.getEndOffset();
    }

    public Extent intersection(Extent other) {
        return new ExtentImpl(this).intersection(other);
    }

    /**
     * @inheritDoc
     *
     * @throws IllegalStateException
     *      if this extent map is empty
     */
    public synchronized int compareTo(Extent other) {
        long myValue = this.getStartOffset();
        long otherValue = other.getStartOffset();
        return myValue < otherValue ? -1 :
            myValue == otherValue ? 0 : 1;
    }

    /**
     * Return a string representation of the map that depicts all its
     * contained extents.
     *
     * @inheritDoc
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ExtentMap{");
        for (Extent e: values())
            sb.append(String.format("[%d, %d)",
                e.getStartOffset(), e.getEndOffset()));
        sb.append("}");
        return sb.toString();
    }

    //
    // Verify that the given Extent doesn't overlap one that's already
    // in the map.
    //
    private void checkNonOverlap(E extent) {
        //
        // XXX: Simple-minded linear search implementation for now.  If need
        //      be, it could be revised to do a binary search.
        //
        long startOffset = extent.getStartOffset();
        long endOffset = extent.getEndOffset();
        for (Extent e : this.values()) {
            long eso = e.getStartOffset();
            if (eso >= endOffset && endOffset != Long.MIN_VALUE) {
                //
                // The extent under consideration starts at or after the end
                // of the one being checked.  Since the map entries are
                // ordered, we're done.
                //
                break;
            }
            long eeo = e.getEndOffset();
            if (eeo <= startOffset && eeo != Long.MIN_VALUE) {
                //
                // The end of the extent under consideration falls before the
                // start of the one being checked.  No problem yet...
                //
                continue;
            }
            throw new IllegalArgumentException(String.format(
                "extent [%d, %d) overlaps existing map entry [%d, %d)",
                startOffset, endOffset, eso, eeo));
        }
    }
}
