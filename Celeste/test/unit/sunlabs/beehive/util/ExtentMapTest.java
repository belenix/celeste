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

import static java.lang.Long.MIN_VALUE;

import static org.junit.Assert.*;

public class ExtentMapTest {
    private ExtentMap<Extent>   extentMap;

    //
    // Some extents that can be added to extentMap in various combinations.
    //
    private static final Extent e00_10  = new ExtentImpl(0L, 10L);
    private static final Extent e00_inf = new ExtentImpl(0L, MIN_VALUE);
    private static final Extent e05_15  = new ExtentImpl(5L, 15L);
    private static final Extent e10_20  = new ExtentImpl(10L, 20L);
    private static final Extent e15_25  = new ExtentImpl(15L, 25L);
    private static final Extent e15_inf = new ExtentImpl(15L, MIN_VALUE);
    private static final Extent e30_40  = new ExtentImpl(30L, 40L);
    private static final Extent e30_55  = new ExtentImpl(30L, 55L);
    private static final Extent e50_inf = new ExtentImpl(50L, MIN_VALUE);

    @Before
    public void setUp() {
        this.extentMap = new ExtentMap<Extent>();
    }

    //
    // Tests of the empty map
    //

    @Test
    public final void testExtentMapConstructor() {
        assertEquals("expect empty map", this.extentMap.size(), 0);
    }

    @Test
    public final void testEmptyIsContiguous() {
        assertTrue("expect empty map to be contiguous",
            this.extentMap.isContiguous());
    }

    //
    // Tests of a single-entry map
    //

    @Test
    public final void testSingleEntryBounds_1() {
        this.extentMap.put(e00_10);
        assertEquals("expect start offset of 0",
            this.extentMap.getStartOffset(), 0L);
        assertEquals("expect end offset of 10",
            this.extentMap.getEndOffset(), 10L);
    }

    @Test
    public final void testSingleEntryBounds_2() {
        this.extentMap.put(e15_inf);
        assertEquals("expect start offset of 15",
            this.extentMap.getStartOffset(), 15L);
        assertEquals("expect end offset of Long.MIN_VALUE",
            this.extentMap.getEndOffset(), MIN_VALUE);
    }

    @Test
    public final void testSingleEntryIsContiguous() {
        this.extentMap.put(e00_10);
        assertTrue("expect single entry map to be contiguous",
            this.extentMap.isContiguous());
    }

    @Test(expected=IllegalArgumentException.class)
    public final void testAddOverlapToSingleFront() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e10_20);
    }

    @Test(expected=IllegalArgumentException.class)
    public final void testAddOverlapToSingleBack() {
        this.extentMap.put(e10_20);
        this.extentMap.put(e15_25);
    }

    @Test(expected=IllegalArgumentException.class)
    public final void testAddOverlapToSingleFrontandBack() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e00_inf);
    }

    //
    // Tests of double-entry maps
    //

    @Test
    public final void testDoubleEntryBounds_1() {
        this.extentMap.put(e00_10);
        this.extentMap.put(e15_25);
        assertEquals("expect start offset of 0",
            this.extentMap.getStartOffset(), 0L);
        assertEquals("expect end offset of 25",
            this.extentMap.getEndOffset(), 25L);
    }

    @Test
    public final void testDoubleEntryBounds_2() {
        this.extentMap.put(e00_10);
        this.extentMap.put(e15_inf);
        assertEquals("expect start offset of 0",
            this.extentMap.getStartOffset(), 0L);
        assertEquals("expect end offset of Long.MIN_VALUE",
            this.extentMap.getEndOffset(), MIN_VALUE);
    }

    @Test
    public final void testDoubleEntryContiguous_1() {
        this.extentMap.put(e05_15);
        this.extentMap.put(e15_25);
        assertTrue("expect two adjacent extents to be contiguous",
            this.extentMap.isContiguous());
    }

    @Test
    public final void testDoubleEntryContiguous_2() {
        this.extentMap.put(e05_15);
        this.extentMap.put(e15_inf);
        assertTrue("expect two adjacent extents to be contiguous",
            this.extentMap.isContiguous());
    }

    //
    // Tests of multiple-entry maps
    //

    @Test
    public final void testGetOffset_1() {
        this.extentMap.put(e00_10);
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        Extent e = this.extentMap.getExtent(5L);
        assertTrue("retrieve by offset gets original extent",
            e != null && e == e00_10);
    }

    @Test
    public final void testGetOffset_2() {
        this.extentMap.put(e00_10);
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        this.extentMap.put(e50_inf);
        Extent e = this.extentMap.getExtent(700L);
        assertTrue("retrieve by offset gets original extent",
            e != null && e == e50_inf);
    }

    @Test
    public final void testGetOffset_3() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        this.extentMap.put(e50_inf);
        Extent e = this.extentMap.getExtent(0L);
        assertTrue("retrieve by offset at hole gets null",
            e == null);
    }

    @Test
    public final void testGetOffset_4() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        this.extentMap.put(e50_inf);
        Extent e = this.extentMap.getExtent(45L);
        assertTrue("retrieve by offset at hole gets null",
            e == null);
    }

    @Test(expected=IllegalArgumentException.class)
    public final void testGetIllegalOffset_1() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        this.extentMap.put(e50_inf);
        Extent e = this.extentMap.getExtent(-60L);
    }

    @Test(expected=IllegalArgumentException.class)
    public final void testGetIllegalOffset_2() {
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        this.extentMap.put(e50_inf);
        Extent e = this.extentMap.getExtent(MIN_VALUE);
    }

    //
    // There should be more tests that do variations on this one.
    //
    @Test
    public final void testGetOverlappingBounds() {
        this.extentMap.put(e00_10);
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        ExtentMap<Extent> overlapMap =
            this.extentMap.getOverlapping(e30_55);
        assertEquals("expect one entry in the overlap map",
            overlapMap.size(), 1);
        assertTrue("expect specific extent in overlap map",
            overlapMap.get(overlapMap.firstKey()) == e30_40);
    }

    //
    // There should be more tests that do variations on this one.
    //
    @Test
    public final void testGetContiguousFrom() {
        this.extentMap.put(e05_15);
        this.extentMap.put(e15_25);
        this.extentMap.put(e30_40);
        ExtentMap<Extent> contiguousMap =
            this.extentMap.getContiguousFrom(10L);
        assertEquals("expect two entries in the contiguous map",
            contiguousMap.size(), 2);
        assertTrue("expect the contiguous map to be contiguous",
            contiguousMap.isContiguous());
        assertTrue("expect specific bounds on the contiguous map",
            contiguousMap.getStartOffset() == 5L &&
                contiguousMap.getEndOffset() == 25L);
    }
}
