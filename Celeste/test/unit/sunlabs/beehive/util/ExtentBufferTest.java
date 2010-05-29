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
// This small collection of tests ought to be greatly expanded.
//
public class ExtentBufferTest {
    //
    // Verify that an extent buffer resulting from the constructor:
    //  -   Has position 0 and limit equal to the ByteBuffer's length.
    //  -   Has its stated start offset.
    //  -   Has an end offset consistent with its start offset and capacity.
    //  -   Has capacity the same as its limit.
    //  -   Has the correct contents at some chosen position.
    //
    // N.B.  Don't modify this test without modifying testSerializationBasic()
    // to match.
    //
    @Test
    public final void testBasic0() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer eb = new ExtentBuffer(20L, buffer);
        assertEquals("expect start offset of 20", 20L, eb.getStartOffset());
        assertEquals("expect end offset of 30", 30L, eb.getEndOffset());
        assertEquals("expect position of 0", 0, eb.position());
        assertEquals("expect limit of 10", 10, eb.limit());
        assertEquals("expect capacity of 10", 10, eb.capacity());
        assertEquals("expect correct contents at position 4",
            (byte)'4', eb.get(4));
    }

    //
    // Verify that it's possible to use the ExtentBuffer constructor to
    // create an extent that butts up against the Long.MIN_VALUE boundary.
    //
    @Test
    public final void testConstructorLimit0() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        int cap = buffer.capacity();
        long lastStart = Long.MAX_VALUE - cap + 1;
        ExtentBuffer eb = new ExtentBuffer(lastStart, buffer);
        assertEquals("end offset should be Long.MIN_VALUE",
            Long.MIN_VALUE, eb.getEndOffset());
    }

    //
    // Verify that it's not possible to use the ExtentBuffer constructor to
    // create an extent that crosses over the Long.MIN_VALUE boundary.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testConstructorLimit1() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        int cap = buffer.capacity();
        long lastStart = Long.MAX_VALUE - cap + 1;
        ExtentBuffer eb = new ExtentBuffer(lastStart + 4, buffer);
    }

    //
    // Verify that the intersect() method throws IllegalArgumentException when
    // given an argument extent that's disjoint with the base extent buffer.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testIntersectDisjoint0() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer ebBase = new ExtentBuffer(20L, buffer);
        Extent e30_35 = new ExtentImpl(30L, 35L);
        ExtentBuffer ebIntersect = ebBase.intersect(e30_35);
        assertTrue("exception should already have been thrown", false);
    }

    //
    // XXX: Some of the tests below are really checking that disjointness
    //      checks are performed correctly and that intersections are
    //      calculated correctly.  To the extent that these tests checks
    //      properties of extents as opposed to extent buffers, they ought to
    //      be factored out into a set of tests for ExtentImpl (except where
    //      ExtentBuffer has to re-implement things in ExtentImpl).
    //

    //
    // Verify that an extent buffer obtained with the intersect() method:
    //  -   Has the proper start and end offsets.
    //  -   Has a position of 0 and limit and capacity equal to the size of
    //      the intersection.
    //  -   Properly shares storage with the extent buffer that it's based on
    //
    @Test
    public final void testIntersect0() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer ebBase = new ExtentBuffer(20L, buffer);
        Extent e25_35 = new ExtentImpl(25L, 35L);
        ExtentBuffer ebIntersect = ebBase.intersect(e25_35);

        assertEquals("expect start offset of 25",
            25L, ebIntersect.getStartOffset());
        assertEquals("expect end offset of 30",
            30L, ebIntersect.getEndOffset());
        assertEquals("expect position of 0", 0, ebIntersect.position());
        assertEquals("expect limit of 5", 5, ebIntersect.limit());
        assertEquals("expect capacity of 5", 5, ebIntersect.capacity());

        ebIntersect.position(1).put((byte)'a');

        assertEquals("expect correct modification at base position 6",
            (byte)'a', ebBase.get(6));
        assertEquals("expect correct contents at position 2",
            (byte)'7', ebIntersect.get(2));
    }

    //
    // Verify that an extent buffer obtained with the intersect() method:
    //  -   Has the proper start and end offsets.
    //  -   Has a position of 0 and limit and capacity equal to the size of
    //      the intersection.
    //  -   Properly shares storage with the extent buffer that it's based on
    // This test differs from the previous one in that the intersecting extent
    // extends to "infinity", and that other positions are sampled for data
    // integrity.
    //
    @Test
    public final void testIntersect1() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer ebBase = new ExtentBuffer(20L, buffer);
        Extent e25_inf = new ExtentImpl(25L, Long.MIN_VALUE);
        ExtentBuffer ebIntersect = ebBase.intersect(e25_inf);

        assertEquals("expect start offset of 25",
            25L, ebIntersect.getStartOffset());
        assertEquals("expect end offset of 30",
            30L, ebIntersect.getEndOffset());
        assertEquals("expect position of 0", 0, ebIntersect.position());
        assertEquals("expect limit of 5", 5, ebIntersect.limit());

        ebIntersect.position(0).put((byte)'a');

        assertEquals("expect correct modification at base position 6",
            (byte)'a', ebBase.get(5));
        assertEquals("expect correct contents at position 4",
            (byte)'9', ebIntersect.get(4));
    }

    //
    // Verify that an extent buffer obtained with the intersect() method from
    // one that has had its position and limit shifted (with those positions
    // and limits falling within the intersection):
    //  -   Has the proper start and end offsets.
    //  -   Has a position matching the offset of the original position.
    //  -   Has a limit matching the offset of the original limit.
    //
    @Test
    public final void testIntersectShiftedPos0() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer ebBase = new ExtentBuffer(20L, buffer);

        ebBase.position(7).limit(9);

        Extent e25_35 = new ExtentImpl(25L, 35L);
        ExtentBuffer ebIntersect = ebBase.intersect(e25_35);

        assertEquals("expect start offset of 25",
            25L, ebIntersect.getStartOffset());
        assertEquals("expect end offset of 30",
            30L, ebIntersect.getEndOffset());
        assertEquals("expect position of 2", 2, ebIntersect.position());
        assertEquals("expect limit of 4", 4, ebIntersect.limit());
        assertEquals("expect capacity of 5", 5, ebIntersect.capacity());
    }

    //
    // Verify that:
    // -    Intersecting a vacuous extent buffer against an extent that shares
    //      its lower bound throws the proper exception.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testIntersectEmpty0() {
        ByteBuffer buffer = ByteBuffer.wrap("".getBytes());
        ExtentBuffer eb = new ExtentBuffer(10L, buffer);
        Extent e10_20 = new ExtentImpl(10L, 20L);

        assertEquals("expect start offset of 10", 10L, eb.getStartOffset());
        assertEquals("expect end offset of 10", 10L, eb.getEndOffset());
        assertEquals("expect position of 0", 0, eb.position());
        assertEquals("expect limit of 0", 0, eb.limit());
        assertEquals("expect capacity of 0", 0, eb.capacity());

        ExtentBuffer intersection = eb.intersect(e10_20);
    }

    //
    // Verify that:
    // -    Intersecting a non-empty extent buffer against a vacuous extent
    //      that shares its lower bound throws the proper exception.
    //
    @Test(expected=IllegalArgumentException.class)
    public final void testIntersectEmpty1() {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer eb = new ExtentBuffer(10L, buffer);
        Extent e10_10 = new ExtentImpl(10L, 10L);

        ExtentBuffer intersection = eb.intersect(e10_10);
    }

    //
    // XXX: Add intersect() tests that check a base extent buffer whose end
    //      offset is Long.MIN_VALUE against:  a "normal" extent, and an
    //      extent whose end offset is LONG.MIN_VALUE.
    //

    //
    // Serialization tests
    //

    //
    // Verify that a simple extent buffer that occupies all of its underlying
    // array is reconstituted properly.  The specifc checks made on the
    // deserialized extent buffer are the same as those performed in
    // testBasic0().
    //
    @Test
    public final void testSerializationBasic() throws
            IOException, ClassNotFoundException {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer eb0 = new ExtentBuffer(20L, buffer);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(eb0);
        out.flush();
        baos.flush();

        byte[] serialized = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream in = new ObjectInputStream(bais);
        ExtentBuffer eb1 = (ExtentBuffer)in.readObject();

        out.close();
        baos.close();
        in.close();
        bais.close();

        //
        // eb0 was declared identically to eb in testBasic0.  Therefore, eb1
        // should satisfy all the properties expected of eb in that test.
        //
        assertEquals("expect start offset of 20", 20L, eb1.getStartOffset());
        assertEquals("expect end offset of 30", 30L, eb1.getEndOffset());
        assertEquals("expect position of 0", 0, eb1.position());
        assertEquals("expect limit of 10", 10, eb1.limit());
        assertEquals("expect capacity of 10", 10, eb1.capacity());
        assertEquals("expect correct contents at position 4",
            (byte)'4', eb1.get(4));
    }

    //
    // Verify that an ExtentBuffer with non-zero position() is properly
    // serialized and deserialized.
    //
    @Test
    public final void testSerializationPositionNonZero() throws
            IOException, ClassNotFoundException {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer eb0 = new ExtentBuffer(20L, buffer);
        eb0.position(2).limit(5);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(eb0);
        out.flush();
        baos.flush();

        byte[] serialized = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream in = new ObjectInputStream(bais);
        ExtentBuffer eb1 = (ExtentBuffer)in.readObject();

        out.close();
        baos.close();
        in.close();
        bais.close();

        //
        // The deserialized buffer should satisfy conditions very similar to
        // those for the SerializationBasic test above.  The position and limit
        // of the received buffer should match those induced by that calls made
        // before serializing.  The vaild range of the underlying byte array
        // should be confined to that span.  Perhaps this could be checked by
        // seeing whether those exterior bytes are zero, but I'm hesitant to
        // cont on that property, preferring to view them as simply being
        // undefined. 
        //
        assertEquals("expect start offset of 20", 20L, eb1.getStartOffset());
        assertEquals("expect end offset of 30", 30L, eb1.getEndOffset());
        assertEquals("expect position of 2", 2, eb1.position());
        assertEquals("expect limit of 5", 5, eb1.limit());
        assertEquals("expect capacity of 10", 10, eb1.capacity());
        assertEquals("expect correct contents at position 2",
            (byte)'4', eb1.get(4));
    }

    //
    // Verify that a sliced ExtentBuffer is properly serialized and
    // deserialized.
    //
    @Test
    public final void testSerializationSlice() throws
            IOException, ClassNotFoundException {
        ByteBuffer buffer = ByteBuffer.wrap("0123456789".getBytes());
        ExtentBuffer eb0 = new ExtentBuffer(20L, buffer);
        eb0.position(2).limit(5);
        eb0 = eb0.slice();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(eb0);
        out.flush();
        baos.flush();

        byte[] serialized = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream in = new ObjectInputStream(bais);
        ExtentBuffer eb1 = (ExtentBuffer)in.readObject();

        out.close();
        baos.close();
        in.close();
        bais.close();

        //
        // Because of the slice() call above, the start and ending offsets
        // should be correspondingly changed from eb0's original values.  The
        // positions at which given values occur should also be shifted.
        //
        assertEquals("expect start offset of 22", 22L, eb1.getStartOffset());
        assertEquals("expect end offset of 25", 25L, eb1.getEndOffset());
        assertEquals("expect limit of 3", 3, eb1.limit());
        assertEquals("expect capacity of 3", 3, eb1.capacity());
        assertEquals("expect correct contents at position 2",
            (byte)'4', eb1.get(2));
    }
}
