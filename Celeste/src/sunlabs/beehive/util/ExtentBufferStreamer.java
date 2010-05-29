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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import java.nio.ByteBuffer;

import java.util.Iterator;
import java.util.NoSuchElementException;

import sunlabs.beehive.BeehiveObjectId;

/**
 * Given various combinations of {@link BufferableExtent} or an offset and a
 * length, and an {@link ExtentBuffer} or {@link ExtentBufferMap}, this class
 * will render the data filling in holes with the fill-byte (zero by default,
 * see {@link #getFillByte()} and {@link #setFillByte}).
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class ExtentBufferStreamer extends BufferableExtentImpl
        implements Iterable<byte[]>, Serializable {
    private final static long serialVersionUID = 1L;

    //
    // Iteration support.
    //
    // Provides iterators that traverse the overall extent of the parent
    // ExtentBufferStreamer by handing back successive byte arrays containing
    // chunks of its contents.  Each chunk's length is determined by its
    // stride, which defaults to 1.
    //
    // The default is unfortunate, since handing back a succession of 1-byte
    // arrays is quite inefficient.  However, we need to choose something to
    // use for Iterable's no-arg iterator() method and any other value would
    // be indefensibly arbitrary.
    //
    private class ExtentBufferStreamerIterator implements Iterator<byte[]> {
        private final int stride;
        private long offset = ExtentBufferStreamer.this.getStartOffset();

        public ExtentBufferStreamerIterator() {
            this (1);
        }

        public ExtentBufferStreamerIterator(int stride) {
            if (stride <= 0)
                throw new IllegalArgumentException(
                    "stride must be positive");
            this.stride = stride;
        }

        public boolean hasNext() {
            long eo = ExtentBufferStreamer.this.getEndOffset();
            if (eo == Long.MIN_VALUE && this.offset != Long.MIN_VALUE)
                return true;
            return this.offset < eo;
        }

        public byte[] next() {
            if (!hasNext())
                throw new NoSuchElementException();

            //
            // The last iteration has to be constrained to avoid going past
            // the parent ExtentBufferStreamer's bounds.
            //
            long eo = ExtentBufferStreamer.this.getEndOffset();
            long proposedEndOffset = this.offset + stride;
            if (proposedEndOffset > eo || (proposedEndOffset < 0 &&
                    proposedEndOffset != Long.MIN_VALUE))
                proposedEndOffset = eo;
            Extent boundsAsExtent =
                new ExtentImpl(this.offset, proposedEndOffset);
            BufferableExtent bounds = new BufferableExtentImpl(boundsAsExtent);
            this.offset += stride;

            //
            // Rather than redoing the dirty work of marching over whatever
            // ExtentBuffers may be hiding within bounds, reuse the
            // renderExtentBufferMap() method, pointing it at a streamer
            // that's been constrained to this iteration's bounds.
            //
            ExtentBufferMap strideMap =
                ExtentBufferStreamer.this.map.intersect(bounds);
            //
            // XXX: Debug
            //
            //System.out.printf("original map: %s%n", map.asString());
            //System.out.printf("strideMap: %s%n", strideMap.asString());
            return new ExtentBufferStreamer(bounds, strideMap).
                renderExtentBufferMap();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final ExtentBufferMap map;
    private byte fillByte = 0;

    public ExtentBufferStreamer(BufferableExtent bounds, ExtentBufferMap map) {
        super(bounds);
        this.checkExtentBufferMapBounds(map);
        this.map = map;
    }

    public ExtentBufferStreamer(long offset, int length, ExtentBufferMap map) {
        super(offset, length);
        this.checkExtentBufferMapBounds(map);
        this.map = map;
    }

    public ExtentBufferStreamer(BufferableExtent bounds, ExtentBuffer extentBuffer) {
        this(bounds, new ExtentBufferMap());
        this.checkExtentBounds(extentBuffer);
        this.map.put(extentBuffer);
    }

    public ExtentBufferStreamer(long offset, int length, ExtentBuffer extentBuffer) {
        this(offset, length, new ExtentBufferMap());
        this.checkExtentBounds(extentBuffer);
        this.map.put(extentBuffer);
    }

    public void setFillByte(byte fillByte) {
        this.fillByte = fillByte;
    }
    
    public byte getFillByte() {
        return this.fillByte;
    }

    /**
     * Verify that {@code extent} fits within this {@code
     * ExtentBufferStreamer}'s bounds, throwing {@code
     * IllegalArgumentException} if it doesn't.
     *
     * @param extent    the extent to be checked for containment
     *
     * @throws IllegalArgumentException
     *      if {@code extent} doesn't fit within this {@code
     *      ExtentBufferStreamer}
     */
    public void checkExtentBounds(Extent extent) {
        if (!this.contains(extent))
            throw new IllegalArgumentException(String.format(
                "%s not contained within %s",
                new ExtentImpl(extent).toString(),
                super.toString()));
    }

    //
    // Verify that extentBufferMap fits within this ExtentBufferStreamer's
    // bounds, throwing IllegalArgumentException if it doesn't.
    //
    private void checkExtentBufferMapBounds(ExtentBufferMap extentBufferMap) {
        if (extentBufferMap == null)
            throw new IllegalArgumentException(
                "extentBufferMap must be non-null");
        if (extentBufferMap.isVacuous())
            return;
        for (BufferableExtent extent : extentBufferMap.values())
            this.checkExtentBounds(extent);
    }

    @Override
    public String toString() {
        return String.format("ExtentBufferStreamer: %s %s", super.toString(), this.map.asString());
    }

    public long streamExtentBufferMap(OutputStream out) throws IOException {
        long offset = this.getStartOffset();
        final int length = this.getLength();
        
        //
        // Iterate through the set of ExtentBuffers in this map.
        //
        // XXX: Beware: inside the loop, pos refers to a position within the
        //      extent buffer at hand; outside, it refers to a position in
        //      this.  The two are not the same!
        //
        long lastOffset = 0;
        int pos = 0;
        for (ExtentBuffer eb : this.map.values()) {
            long startOffset = eb.getStartOffset();
            if (startOffset < lastOffset) {
                System.err.printf("XXX offset %d is less than last offset %d%n", startOffset, lastOffset);
            }

            //
            // Pad to the beginning of eb.
            //
            if (offset < startOffset) {
                System.err.printf("padding to initial offset %d%n", startOffset - offset);
            }
            for ( ; offset < startOffset; offset++)
                out.write(this.fillByte);

            //
            // Pad from there to eb's current position.
            //
            int startPos = eb.position();
            if (pos < startPos) {
                System.err.printf("padding to current position %d%n", startPos - pos);
            }
            for (pos = 0; pos < startPos; pos++, offset++)
                out.write(this.fillByte);

            //
            // Emit this buffer.
            //
            if (eb.hasArray()) {
                byte[] array = eb.array();
                int arrayOffset = eb.arrayOffset();
                int ebLength = eb.remaining();
                out.write(array, arrayOffset + startPos, ebLength);
                pos = startPos + ebLength;
                offset += ebLength;
            } else {
                //
                // Do it the hard way.
                //
                for (pos = startPos; pos < eb.limit(); pos++, offset++)
                    out.write(eb.get(pos));
            }
        }

        //
        // Pad to the end.
        //
        for (pos = (int)(offset - this.getStartOffset()); pos < length; pos++) {
            out.write(this.fillByte);
        }

        return length;
    }

    public byte[] render() {
        return this.renderExtentBufferMap();
    }

    private byte[] renderExtentBufferMap() {
        final int length = this.getLength();
        byte[] buffer = new byte[length];
        long offset = this.getStartOffset();
        int pos = 0;

        //
        // Iterate through this map's ExtentBuffers (which will be presented
        // in ascending order).  Fill leading, intersitial, and trailing gaps
        // with the fillByte.
        //
        // (Since buffer is initialized to bytes of zeroes, the fill steps
        // could be bypassed when the fillByte is 0.)
        //
        long lastOffset = 0;
        
        for (ExtentBuffer eb : this.map.values()) {
            long startOffset = eb.getStartOffset();
            if (startOffset < lastOffset) {
                System.err.printf("XXX offset %d is less than last offset %d%n", startOffset, lastOffset);
            }

            //
            // Pad to the beginning of eb.
            //
            for ( ; offset < startOffset; offset++, pos++)
                buffer[pos] = this.fillByte;

            //
            // Pad from there to eb's current position.
            //
            int ebStartPos = eb.position();
            int ebPos = 0;
            for (ebPos = 0; pos < ebStartPos; pos++, offset++, ebPos++)
                buffer[pos] = this.fillByte;

            //
            // Emit this buffer.
            //
            eb.duplicate().get(buffer, pos, eb.remaining());
            pos += eb.remaining();
            offset += eb.remaining();
        }

        //
        // Pad to the end.
        //
        for (pos = (int)(offset - this.getStartOffset()); pos < length; pos++)
            buffer[pos] = this.fillByte;

        return buffer;
    }

    /**
     * Provides an iterator that traverses the overall span of its parent
     * {@code EventBufferStreamer} by handing back successive single byte
     * arrays for each iteration.  The iterator does not support the {@code
     * remove()} method.
     *
     * @return  an iterator the yields a one-byte array containing the next
     *          byte at each iteration
     */
    public Iterator<byte[]> iterator() {
        return new ExtentBufferStreamerIterator();
    }

    /**
     * Provides an iterator that traverses the overall span of its parent
     * {@code EventBufferStreamer} by handing back successive byte arrays of
     * length {@code stride} for each iteration, except possibly the last
     * where the returned array might be shorter.  The iterator does not
     * support the {@code remove()} method.
     *
     * @param stride    a positive value giving the number bytes to return in
     *                  each iteration's array.
     *
     * @return  an iterator the yields an array containing the next
     *          {@code stride} bytes at each iteration
     */
    public Iterator<byte[]> iterator(int stride) {
        return new ExtentBufferStreamerIterator(stride);
    }

    public static void main(String[] args) {
        try {
            ExtentBufferMap map = new ExtentBufferMap();

            /* abcdefghijklmnopqrstuvwxyz */
            /* ___def___jkl___pqr___vwxyz */
            /* ___456___ABC__E */

            ExtentBuffer eb03_06 = new ExtentBuffer(3, ByteBuffer.wrap("def".getBytes()));
            ExtentBuffer eb09_12 = new ExtentBuffer(9, ByteBuffer.wrap("jkl".getBytes()));
            ExtentBuffer eb15_18 = new ExtentBuffer(15, ByteBuffer.wrap("pqr".getBytes()));

            map.put(eb03_06);
            map.put(eb09_12);
            map.put(eb15_18);

            ExtentBufferStreamer render;
            byte[] result;
            ByteArrayOutputStream bos;

            ExtentBuffer buffer = new ExtentBuffer(0, ByteBuffer.wrap("abcdefghijklmnopqrstuvwxyz".getBytes()));
            render = new ExtentBufferStreamer(0, 30, buffer);
            bos = new ByteArrayOutputStream();
            render.streamExtentBufferMap(bos);
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i] == 0 ? '_' : result[i])); } System.out.println();

            System.out.println("map: " + map.toString());

            render = new ExtentBufferStreamer(0, 20, map);
            bos = new ByteArrayOutputStream();
            render.streamExtentBufferMap(bos);
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i] == 0 ? '_' : result[i])); } System.out.println();

            //
            // Shorten one of map's extent buffers by adjusting its position
            // and limit.  Then redo the test above to verify that fill bytes
            // covering part of an extent buffer's array are emitted properly.
            //
            eb09_12.position(1).limit(2);
            render = new ExtentBufferStreamer(0, 20, map);
            bos = new ByteArrayOutputStream();
            render.streamExtentBufferMap(bos);
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i] == 0 ? '_' : result[i])); } System.out.println();

            //
            // ExtentBufferStreamers no longer accept maps that extend beyond
            // the streams's bounds.  Thus, the two tests below are no longer
            // valid.
            //

            //render = new ExtentBufferStreamer(5, 5, map);
            //bos = new ByteArrayOutputStream();
            //render.streamTo(new DataOutputStream(bos));
            //result = bos.toByteArray();
            //for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            //for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i] == 0 ? '_' : result[i])); } System.out.println();

            //render = new ExtentBufferStreamer(7, 5, map);
            //bos = new ByteArrayOutputStream();
            //render.streamTo(new DataOutputStream(bos));
            //result = bos.toByteArray();
            //for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            //for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i] == 0 ? '_' : result[i])); } System.out.println();

            //
            // Try out the iterator.
            //
            System.out.printf("%nIteration through the above with stride 6:%n");
            Iterator<byte[]> it =
                new ExtentBufferStreamer(0, 20, map).iterator(6);
            while (it.hasNext()) {
                for (byte b : it.next())
                    System.out.printf("%c ", (b == 0) ? '-' : (char)b);
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
