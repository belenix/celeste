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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

//
// XXX: ExtentBufferMap has _lots_ of behavior that's not tested here.
//      Obviously, there's opportunity to write many more tests.
//
// XXX: Add tests for maps that contain vacuous extents at either or both
//      ends (so that from an ExtentBuffer perspective the map has holes at
//      one or both ends).
//
public class ExtentBufferMapTest {
    //
    // Naming scheme for tests of the ReplaceExtents() method:
    //      testReplaceExtents{L,}{R,}[n]
    // Where:
    //      "L" is present if an overlapping extent protrudes to the left.
    //      "R" is present if an overlapping extent protrudes to the right.
    //      n is an integer designating which in a series of such tests it is.
    //

    //
    // Verify that, after replacing the highest-offset extent in a two entry
    // map with one that fits entirely within the replacee's interior:
    //  -   The resulting map has the correct number of extents.
    //  -   The replacement extent appears within the resulting map.
    //
    // XXX: There are more things that can and should be checked here.
    //
    @Test
    public final void testReplaceExtentsLR0() {
        ByteBuffer b00_01 = ByteBuffer.wrap("0".getBytes());
        ByteBuffer b01_09 = ByteBuffer.wrap("12345678".getBytes());
        ExtentBuffer eb00_01 = new ExtentBuffer(0L, b00_01);
        ExtentBuffer eb01_09 = new ExtentBuffer(1L, b01_09);
        ExtentBufferMap m00_09 = new ExtentBufferMap();
        m00_09.put(eb00_01);
        m00_09.put(eb01_09);

        ByteBuffer b05_08 = ByteBuffer.wrap("XYZ".getBytes());
        ExtentBuffer eb05_08 = new ExtentBuffer(5L, b05_08);

        m00_09.replaceExtents(eb05_08);
        assertEquals("resulting map should have 4 extents",
            m00_09.size(), 4);
        assertEquals(
            "the extent starting at offset 5 in the result " +
            " should be the one given as the replacement",
            m00_09.get(5L), eb05_08);
    }

    //
    // Verify that, after replacing a hole and the highest-offset extent in a
    // two entry map with one that leaves a byte of the replaced extent
    // protruding:
    //  -   The resulting map has the correct number of entries.
    //  -   The protrusion from the high end of the map properly appears in
    //      the resulting map.
    //
    // This test recreates the circumstances of a bug observed while testing
    // the BufferCache class.
    //
    @Test
    public final void testReplaceExtentsR0() {
        ByteBuffer b00_01 = ByteBuffer.wrap("0".getBytes());
        ByteBuffer b02_04 = ByteBuffer.wrap("23".getBytes());
        ExtentBuffer eb00_01 = new ExtentBuffer(0L, b00_01);
        ExtentBuffer eb02_04 = new ExtentBuffer(2L, b02_04);
        ExtentBufferMap m00_04 = new ExtentBufferMap();
        m00_04.put(eb00_01);
        m00_04.put(eb02_04);

        ByteBuffer b01_03 = ByteBuffer.wrap("ab".getBytes());
        ExtentBuffer eb01_03 = new ExtentBuffer(1L, b01_03);

        m00_04.replaceExtents(eb01_03);
        assertEquals("resulting map should have 3 extents",
            m00_04.size(), 3);
        assertEquals(
            "the extent starting at offset 1 in the result " +
            " should be the one given as the replacement",
            m00_04.get(1L), eb01_03);
        //
        // XXX: Verify tail's contents as well as its bounds?
        //
        Extent tail = m00_04.get(m00_04.lastKey());
        assertTrue("resulting map should have an extent for protrusion",
            tail.getStartOffset() == 3L && tail.getEndOffset() == 4L);
    }

    //
    // Verify that intersect() properly handles maps containing extent
    // buffers whose positions and limits are not necessarily equal to (resp.)
    // 0 and their capacities.
    //
    // This test recreates the circumstances of a bug observed while testing
    // the ExtentBufferStreamer class.
    //
    @Test
    public void testIntersectChopped() {
        ByteBuffer b03_06 = ByteBuffer.wrap("def".getBytes());
        ByteBuffer b09_12 = ByteBuffer.wrap("jkl".getBytes());
        ByteBuffer b15_18 = ByteBuffer.wrap("pqr".getBytes());
        ExtentBuffer eb03_06 = new ExtentBuffer( 3L, b03_06);
        ExtentBuffer eb09_12 = new ExtentBuffer( 9L, b09_12);
        ExtentBuffer eb15_18 = new ExtentBuffer(15L, b15_18);
        ExtentBufferMap fullMap = new ExtentBufferMap();
        fullMap.put(eb03_06);
        fullMap.put(eb09_12);
        fullMap.put(eb15_18);

        //
        // Shorten the middle buffer in the map.
        //
        eb09_12.position(1).limit(2);

        //
        // Intersect fullMap against an extent that will chop up the first
        // contained extent buffer and leave the second two intact.
        //
        ExtentBufferMap choppedMap = fullMap.intersect(new ExtentImpl(4L, 30L));

        ExtentBuffer eb = choppedMap.get(9L);
        assertNotNull("chopped map should still contain the middle extent", eb);
        assertEquals("middle extent should have position 1",
            eb.position(), 1);
        assertEquals("middle extent should have limit 2",
            eb.limit(), 2);
    }

    //
    // Verify that serialization followed by deserialization works properly
    // for a simple (but not completely trivial) map.
    //
    @Test
    public final void testSerialization0() throws
            IOException, ClassNotFoundException {
        ByteBuffer b00_01 = ByteBuffer.wrap("0".getBytes());
        ByteBuffer b02_04 = ByteBuffer.wrap("23".getBytes());
        ExtentBuffer eb00_01 = new ExtentBuffer(0L, b00_01);
        ExtentBuffer eb02_04 = new ExtentBuffer(2L, b02_04);
        ExtentBufferMap m00_04 = new ExtentBufferMap();
        m00_04.put(eb00_01);
        m00_04.put(eb02_04);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(m00_04);
        out.flush();
        baos.flush();

        byte[] serialized = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream in = new ObjectInputStream(bais);
        ExtentBufferMap n00_04 = (ExtentBufferMap)in.readObject();

        out.close();
        baos.close();
        in.close();
        bais.close();

        //
        // Some spot checks:  verify that:
        //  -   the map contains an extent buffer containing offset 3
        //  -   the byte at that position has value '3'
        //
        ExtentBuffer fb02_04 = n00_04.getExtent(3L);
        assertNotNull("expect an extent including offset 3", fb02_04);
        int pos = fb02_04.offsetToPosition(3);
        assertEquals("expect correct contents at offset 3",
            fb02_04.get(pos), (byte)'3');
    }
}
