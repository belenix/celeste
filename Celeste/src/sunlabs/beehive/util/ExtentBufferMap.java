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
package sunlabs.beehive.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>
 *
 * The {@code ExtentBufferMap} class specializes {@link ExtentMap} to hold an
 * ordered set of {@link ExtentBuffer}s, each indexed by its starting offset.
 * It provides methods to access the {@link java.nio.ByteBuffer}s that back
 * the extents in a given instance, to calculate the aggregate buffer
 * capacity, and to modify the map by including or excluding the information
 * from a specified extent.
 *
 * </p><p>
 *
 * Note that although this class implements {@link Extent}, it does
 * <em>not</em> implement {@link BufferableExtent}.  To do so would require
 * the overall span covered by an instance to be representable in an {@code
 * int}.  Although the sum of the lengths of the individual {@code
 * ExtentBuffer}s mapped in an instance will likely meet this constraint, the
 * possibility of inter-extent gaps precludes requiring that its overall
 * bounds meet the constraint.  Moreover, since this class does not implement
 * {@code BufferableExtent}, it also does not implement {@code ExtentBuffer},
 * which implies that (in contrast to the possibility of nesting {@code
 * ExtentMap}s) it is not possible to nest one {@code ExtentBufferMap} within
 * another.
 *
 * </p>
 */
public class ExtentBufferMap extends ExtentMap<ExtentBuffer> {
    private final static long serialVersionUID = 0L;

    /**
     * Construct an empty extent buffer map.
     */
    public ExtentBufferMap() {
        super();
    }

    /**
     * Construct a copy of an existing extent buffer map.
     *
     * @param map   the existing {@code ExtentBufferMap} to be copied
     *
     * @throws NullPointerException
     *      if {@code map} is {@code null}
     */
    public ExtentBufferMap(ExtentBufferMap map) {
        this();
        for (ExtentBuffer eb : map.values())
            this.put(eb);
    }

    /**
     * Return the aggregate capacity of all the buffers in this extent buffer
     * map.
     *
     * @return  the sum of the capacities of the this map's entries
     */
    public long getCapacities() {
        long capacity = 0;
        for (ExtentBuffer eb : this.values())
            capacity += eb.capacity();
        return capacity;
    }

    /**
     * Return the total amount of unconsumed data held in all the buffers in
     * this extent buffer map.
     *
     * @return  the sum of the remaining data in this map's entries
     *
     * @see ExtentBuffer#remaining
     */
    public long getRemaining() {
        long remaining = 0;
        for (ExtentBuffer eb : this.values())
            remaining += eb.remaining();
        return remaining;
    }

    /**
     * Add {@code extent} to this map, making room for it if necessary by
     * removing extents that overlap completely with it and replacing
     * partially overlapping extents with non-overlapping slices that abut it.
     * If the replacement for a partially overlapping extent is {@link
     * Extent#isVacuous() vacuous}, it is omitted from the modified map.
     *
     * @param extent    the new extent that is to replace ones that overlap it
     *                  in this map
     */
    public void replaceExtents(ExtentBuffer extent) {
        //
        // Check for a degenerate case; don't let vacuous extents needlessly
        // fragment the map.
        //
        if (extent.isVacuous())
            return;

        ExtentMap<ExtentBuffer> overlapMap = this.removeOverlaps(extent);
        //
        // With the overlapping extents (if any) out of the way, there's now
        // room to accommodate extent.
        //
        this.put(extent);
        if (overlapMap.size() == 0) {
            //
            // Nothing overlapped.  We're done.
            //
            return;
        }

        //
        // The extents in overlapMap may protrude beyond the span that extent
        // covers at either or both ends.  The protruding parts of such spans
        // need to remain in the overall buffer map.  Create ExtentBuffers for
        // those spans as views onto the pre-existing protruding extents and
        // add them back into the map.  To avoid disturbing these extents as
        // viewed from other maps in which they might appear, duplicate them
        // first.
        //
        // As an additional wrinkle, the overlap map could have just a single
        // entry, which implies that that single entry could protrude from
        // both ends.  Handle this situation by first working with the low
        // end, stripping off any protrusion on that side and replacing the
        // the entry in the overlap map with that (duplicated and) stripped
        // entry.  Then find and handle the entry on the map's high end (which
        // in the case of a single entry will be the modified low entry).
        //
        ExtentBuffer eb = overlapMap.get(overlapMap.firstKey());
        int delta = (int)(extent.getStartOffset() - eb.getStartOffset());
        if (delta > 0) {
            ExtentBuffer protrusion = eb.duplicate().limit(delta).slice();
            this.put(protrusion);
            //
            // Remove eb from the overlap map and replace it with a trimmed
            // version.
            //
            overlapMap.remove(eb.getStartOffset());
            ExtentBuffer trimmedExtent = eb.duplicate().position(delta).slice();
            if (!trimmedExtent.isVacuous())
                overlapMap.put(trimmedExtent);
        }
        eb = overlapMap.get(overlapMap.lastKey());
        long extentEnd = extent.getEndOffset();
        if (extentEnd == Long.MIN_VALUE) {
            //
            // Extent extends as far as possible, so nothing can protrude
            // beyond its end.  Thus, we're done.
            //
            return;
        }
        //
        // We now know that extentEnd is within positive integer range of eb's
        // start position.
        //
        long ebEnd = eb.getEndOffset();
        if (ebEnd > extentEnd || ebEnd == Long.MIN_VALUE) {
            //
            // eb extends beyond extent.  Therefore, there's a non-vacuous
            // protrusion, since we know that eb's start offset precedes
            // extentEnd (otherwise eb wouldn't be in overlapMap).  The
            // protrusion starts at extentEnd, so move the position up to
            // that point before slicing.
            //
            ExtentBuffer protrusion = eb.duplicate().
                position((int)(extentEnd - eb.getStartOffset())).slice();
            this.put(protrusion);
        }
    }

    /**
     * Return an extent buffer map that contains views onto those extent
     * buffers from this map that intersect with extent.  The views of
     * interior extents are complete (via {@link ExtentBuffer#duplicate()})
     * and the views of protruding extents are partial, containing only the
     * intersecting portions.  {@link Extent#isVacuous() Vacuous} extent
     * buffers are omitted from the resulting map.
     *
     * @param extent    the extent determining the bounds of the intersection
     *
     * @return  a map containing this {@code ExtentBufferMap}'s intersection
     *          with {@code extent}
     */
    public ExtentBufferMap intersect(Extent extent) {
        ExtentMap<ExtentBuffer> overlapMap = this.getOverlapping(extent);
        ExtentBufferMap intersectionMap = new ExtentBufferMap();
        if (overlapMap.size() == 0)
            return intersectionMap;

        if (overlapMap.size() == 1) {
            ExtentBuffer eb = overlapMap.get(overlapMap.firstKey());
            ExtentBuffer slice = eb.intersect(extent);
            if (!slice.isVacuous())
                intersectionMap.put(slice);
            return intersectionMap;
        }

        //
        // Add the extents from overlapMap to intersectionMap, except at the
        // beginning and end, where entries might extend beyond the
        // intersection.
        //
        for (ExtentBuffer eb : overlapMap.values()) {
            if (eb == overlapMap.get(overlapMap.firstKey()) ||
                    eb == overlapMap.get(overlapMap.lastKey())) {
                //
                // eb might protrude on one side or the other of extent, but
                // not on both sides (so it won't be considered here twice).
                // Use the extent buffer intersect() method to trim off any
                // such protrusion and put the result into the map.
                //
                ExtentBuffer intersection = eb.intersect(extent);
                if (!intersection.isVacuous())
                    intersectionMap.put(intersection);
                continue;
            }

            if (!eb.isVacuous())
                intersectionMap.put(eb.duplicate());
        }

        return intersectionMap;
    }

    /**
     * Return an array of duplicates of the byte buffers backing the extents
     * in this map, ordered by increasing offset.  The returned array does not
     * reflect any non-contiguous sections of the map; that is, i/o to or from
     * the buffers in the array will skip over holes in the map.  Note also
     * that the returned buffers will be full (having position 0 and limit
     * equal to capacity) and thus cover their entire extents only if the
     * corresponding buffers in the map do.
     *
     * @return  an offset-ordered array of duplicates of the {@code
     *          ByteBuffer}s backing this map
     *
     * @see java.nio.ByteBuffer#duplicate   ByteBuffer.duplicate()
     */
    public ByteBuffer[] getBuffers() {
        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>(this.size());
        for (ExtentBuffer eb : this.values())
            buffers.add(eb.getByteBuffer().duplicate());
        return buffers.toArray(new ByteBuffer[buffers.size()]);
    }

    /**
     * <p>
     *
     * Return a string representation of this {@code ExtentBufferMap}.
     *
     * </p><p>
     *
     * This method is useful only when the map is known to be modest in size
     * and to have only modestly sized extents with string-representable
     * contents.  It makes no attempt to convert bytes into a viewable
     * representation and thus is primarily intended to be used for debugging.
     *
     * </p>
     *
     * @return  a string representation of the extent buffer map
     */
    public String asString() {
        return this.asString(true);
    }

    /**
     * <p>
     *
     * Return a string representation of this {@code ExtentBufferMap}.  If
     * {@code includeContents} is {@code true} the representation includes
     * each contained extent buffer's contents.  In all cases, the
     * representation includes each contained extent buffer's bounds.
     *
     * </p><p>
     *
     * When {@code includeContents} is set, this method is useful only when
     * the map is known to be modest in size and to have only modestly sized
     * extents with string-representable contents.  It makes no attempt to
     * convert bytes into a viewable representation and thus is primarily
     * intended to be used for debugging.
     *
     * </p>
     *
     * @param includeContents   if {@code true} include buffer contents in the
     *                          returned string; otherwise, return a string
     *                          representation of only the contained extents'
     *                          bounds
     *
     * @return  a string representation of the extent buffer map
     */
    public String asString(boolean includeContents) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d extent(s):%n", this.size()));
        for (ExtentBuffer eb : this.values()) {
            sb.append(eb.asString(includeContents)).append(" ");
        }
        return sb.toString();
    }
}
