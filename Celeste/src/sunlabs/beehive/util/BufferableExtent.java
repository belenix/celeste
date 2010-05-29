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


/**
 * <p>
 *
 * The {@code BufferableExtent} interface describes extents that have the
 * additional property of being bufferable, that is, of covering a small
 * enough range that a buffer (with size restricted to the maximum number of
 * elements for a byte array, i.e., {@code Integer.MAX_VALUE}) can be
 * associated with it.
 *
 * </p><p>
 *
 * Since buffer sizes are constrained as described above, the length of a
 * bufferable extent is also so constrained.  Accordingly, this interface
 * provides a {@code getLength()} method that returns that length as an {@code
 * int}.
 *
 * </p><p>
 *
 * Classes implementing this interface must enforce the constraint that {@code
 * getEndOffset() - getStartOffset() <= Integer.MAX_VALUE}, throwing {@code
 * IllegalArgumentException} for any operation that would violate it.
 *
 * </p>
 */
public interface BufferableExtent extends Extent {
    /**
     * Return this bufferable extent's length, that is, the distance between
     * its start offset and its end offset.
     *
     * @return  this extent's length, expressed as a non-negative {@code int}.
     */
    public int getLength();
}
