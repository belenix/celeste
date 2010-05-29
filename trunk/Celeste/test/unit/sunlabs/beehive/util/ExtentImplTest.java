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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

import static java.lang.Long.MIN_VALUE;

public class ExtentImplTest {
    //
    // Tests to verify that bounds are properly checked and exceptions thrown
    // from the primary constructor.
    //

    //
    // Something starting at 0 with a non-maximal span.
    //
    @Test
    public final void testConstructorBounds0() {
        Extent e = new ExtentImpl(0L, 500L);
        assertEquals("expect start offset of 0", e.getStartOffset(), 0L);
        assertEquals("expect end offset of 500", e.getEndOffset(), 500L);
    }

    //
    // Something with a maximal span.
    //
    @Test
    public final void testConstructorBounds1() {
        Extent e = new ExtentImpl(30L, MIN_VALUE);
        assertEquals("expect start offset of 30", e.getStartOffset(), 30L);
        assertEquals("expect end offset of MIN_VALUE",
            e.getEndOffset(), MIN_VALUE);
    }

    //
    // A vacuous extent.
    //
    @Test
    public final void testConstructorBounds2() {
        Extent e = new ExtentImpl(5L, 5L);
        assertEquals("expect start offset of 5", e.getStartOffset(), 5L);
        assertEquals("expect end offset of 5", e.getEndOffset(), 5L);
    }

    //
    // Negative start offset.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testConstructorBounds3() {
        Extent e = new ExtentImpl(-1L, 5L);
    }

    //
    // MIN_VALUE as start offset.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testConstructorBounds4() {
        Extent e = new ExtentImpl(MIN_VALUE, 5L);
    }

    //
    // Negative, non-MIN_VALUE end offset.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testConstructorBounds5() {
        Extent e = new ExtentImpl(10L, -7L);
    }

    //
    // End before start.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testConstructorBounds6() {
        Extent e = new ExtentImpl(10L, 7L);
    }

    //
    // Test of the copy constructor
    //
    @Test
    public final void testCopyConstructor() {
        Extent e = new ExtentImpl(3L, 7L);
        Extent eCopy = new ExtentImpl(e);
        assertEquals("expect equal start offset",
            e.getStartOffset(), eCopy.getStartOffset());
        assertEquals("expect equal end offset",
            e.getEndOffset(), eCopy.getEndOffset());
    }

    //
    // Tests of the intersects() method
    //

    @Test
    public final void testIntersectsOverlap0() {
        Extent e00_10 = new ExtentImpl(0L, 10L);
        Extent e05_15 = new ExtentImpl(5L, 15L);
        assertTrue("protrusion at both ends intersects, case 0a",
            e00_10.intersects(e05_15));
        assertTrue("protrusion at both ends intersects, case 0b",
            e05_15.intersects(e00_10));
        assertTrue("non-vacuous extent self-intersects, case 0",
            e05_15.intersects(e05_15));
    }

    //
    // Same as above, but with one extent extending to "infinity".
    //
    @Test
    public final void testIntersectsOverlap1() {
        Extent e00_10  = new ExtentImpl(0L, 10L);
        Extent e05_inf = new ExtentImpl(5L, MIN_VALUE);
        assertTrue("protrusion at both ends intersects, case 1a",
            e00_10.intersects(e05_inf));
        assertTrue("protrusion at both ends intersects, case 1b",
            e05_inf.intersects(e00_10));
        assertTrue("non-vacuous extent self-intersects, case 1",
            e05_inf.intersects(e05_inf));
    }

    @Test
    public final void testIntersectsCommonEndpoint() {
        Extent e00_15 = new ExtentImpl(0L, 15L);
        Extent e05_10 = new ExtentImpl(5L, 10L);
        Extent e05_15 = new ExtentImpl(5L, 15L);
        assertTrue("common left endpoint intersects, case 1",
            e05_10.intersects(e05_15));
        assertTrue("common left endpoint intersects, case 2",
            e05_15.intersects(e05_10));
        assertTrue("common right endpoint intersects, case 1",
            e00_15.intersects(e05_15));
        assertTrue("common right endpoint intersects, case 2",
            e05_15.intersects(e00_15));
    }

    @Test
    public final void testIntersectsSubset() {
        Extent e00_15 = new ExtentImpl(0L, 15L);
        Extent e05_10 = new ExtentImpl(5L, 10L);
        assertTrue("extent and proper sub-extent intersect, case 1",
            e00_15.intersects(e05_10));
        assertTrue("extent and proper sub-extent intersect, case 2",
            e05_10.intersects(e00_15));
    }

    @Test
    public final void testIntersectsVacuous0() {
        Extent e00_10 = new ExtentImpl(0L, 10L);
        Extent e05_05 = new ExtentImpl(5L, 5L);
        Extent e05_15 = new ExtentImpl(5L, 15L);
        assertFalse("vacuous extent intersects in interior, case 0a",
            e00_10.intersects(e05_05));
        assertFalse("vacuous extent intersects in interior, case 0b",
            e05_05.intersects(e00_10));
        assertFalse("vacuous extent intersects at left end, case 0a",
            e05_15.intersects(e05_05));
        assertFalse("vacuous extent intersects at left end, case 0b",
            e05_05.intersects(e05_15));
    }

    //
    // As above, but with one extent extending to "infinity".
    //
    @Test
    public final void testIntersectsVacuous1() {
        Extent e10_10  = new ExtentImpl(10L, 10L);
        Extent e05_05  = new ExtentImpl(5L, 5L);
        Extent e05_inf = new ExtentImpl(5L, MIN_VALUE);
        assertFalse("vacuous extent intersects in interior, case 1a",
            e10_10.intersects(e05_inf));
        assertFalse("vacuous extent intersects in interior, case 1b",
            e05_inf.intersects(e10_10));
        assertFalse("vacuous extent intersects at left end, case 1a",
            e05_inf.intersects(e05_05));
        assertFalse("vacuous extent intersects at left end, case 1b",
            e05_05.intersects(e05_inf));
        assertFalse("vacuous extent self-intersects",
            e05_05.intersects(e05_05));
    }

    @Test
    public final void testNonIntersectsDisjoint() {
        Extent e00_05 = new ExtentImpl(0L, 5L);
        Extent e05_05 = new ExtentImpl(5L, 5L);
        Extent e05_15 = new ExtentImpl(5L, 15L);
        assertFalse("disjoint extents don't intersect, case 0",
            e00_05.intersects(e05_15));
        assertFalse("disjoint extents don't intersect, case 1",
            e05_15.intersects(e00_05));
        assertFalse("vacuous extent doesn't intersect at right end, case 0",
            e00_05.intersects(e05_05));
        assertFalse("vacuous extent doesn't intersect at right end, case 1",
            e05_05.intersects(e00_05));
    }

    //
    // Tests of the intersection() method
    //
    // XXX: The declarations below should change from ExtentImpl to Extent
    //      when the intersection() method is added to the Extent interface.
    //

    @Test
    public final void testIntersectionOverlap0() {
        ExtentImpl e00_10 = new ExtentImpl(0L, 10L);
        ExtentImpl e05_15 = new ExtentImpl(5L, 15L);
        Extent e = e00_10.intersection(e05_15);
        Extent f = e05_15.intersection(e00_10);
        assertTrue("intersection should be commutative",
            e.getStartOffset() == f.getStartOffset() &&
                e.getEndOffset() == f.getEndOffset());
        assertEquals("intersection start offset is 5",
            e.getStartOffset(), 5L);
        assertEquals("intersection end offset is 10",
            e.getEndOffset(), 10L);
    }

    //
    // Same as above, but with one extent extending to "infinity".
    //
    @Test
    public final void testIntersectionOverlap1() {
        ExtentImpl e00_10  = new ExtentImpl(0L, 10L);
        ExtentImpl e05_inf = new ExtentImpl(5L, MIN_VALUE);
        Extent e = e00_10.intersection(e05_inf);
        Extent f = e05_inf.intersection(e00_10);
        assertTrue("intersection should be commutative",
            e.getStartOffset() == f.getStartOffset() &&
                e.getEndOffset() == f.getEndOffset());
        assertEquals("intersection start offset is 5",
            e.getStartOffset(), 5L);
        assertEquals("intersection end offset is 10",
            e.getEndOffset(), 10L);
    }

    @Test
    public final void testIntersectionCommonEndpoint() {
        ExtentImpl e00_15 = new ExtentImpl(0L, 15L);
        ExtentImpl e05_10 = new ExtentImpl(5L, 10L);
        ExtentImpl e05_15 = new ExtentImpl(5L, 15L);
        Extent e = e05_10.intersection(e05_15);
        Extent f = e05_15.intersection(e05_10);
        assertTrue("intersection should be commutative (left)",
            e.getStartOffset() == f.getStartOffset() &&
                e.getEndOffset() == f.getEndOffset());
        assertEquals("intersection start offset is 5 (left)",
            e.getStartOffset(), 5L);
        assertEquals("intersection end offset is 10 (left)",
            e.getEndOffset(), 10L);

        e = e00_15.intersection(e05_15);
        f = e05_15.intersection(e00_15);
        assertTrue("intersection should be commutative (right)",
            e.getStartOffset() == f.getStartOffset() &&
                e.getEndOffset() == f.getEndOffset());
        assertEquals("intersection start offset is 5 (right)",
            e.getStartOffset(), 5L);
        assertEquals("intersection end offset is 15 (right)",
            e.getEndOffset(), 15L);
    }

    //
    // XXX: Could write additional test cases to match those of
    //      testIntersectsVacuous0(), but there seems to be little point.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testIntersectionVacuous0() {
        ExtentImpl e00_10 = new ExtentImpl(0L, 10L);
        ExtentImpl e05_05 = new ExtentImpl(5L, 5L);
        Extent e = e00_10.intersection(e05_05);
    }

    //
    // XXX: Could write additional test cases to match those of
    //      testNonIntersectsDisjount0(), but there seems to be little point.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testIntersectionDisjoint0() {
        ExtentImpl e00_05 = new ExtentImpl(0L, 5L);
        ExtentImpl e05_15 = new ExtentImpl(5L, 15L);
        Extent e = e00_05.intersection(e05_15);
    }
}
