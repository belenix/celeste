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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExtentBufferRender /*implements Streamable*/ {
    private long offset;
    private int length;
    private ExtentBufferMap map;
    private ExtentBuffer extentBuffer;
    private byte fillByte = '_';
    
    public ExtentBufferRender(int offset, int length, ExtentBufferMap map) {
        this.offset = offset;
        this.length = length;
        this.map = map;
    }
    
    public ExtentBufferRender(int offset, int length, ExtentBuffer extentBuffer) {
        this.offset = offset;
        this.length = length;
        this.extentBuffer = extentBuffer;
    }
    
    public void setFillByte(byte fillByte) {
        this.fillByte = fillByte;
    }
    
    public long streamLength() {
        return 0;
    }
    
    public long streamTo(DataOutputStream out) throws IOException {
        if (this.extentBuffer != null)
            return this.streamExtentBuffer(out);
        if (this.map != null)
            return this.streamExtentBufferMap(out);
        throw new IllegalArgumentException();
    }

    public long streamExtentBuffer(DataOutputStream out) throws IOException {
        for (int i = 0; i < extentBuffer.getStartOffset(); i++) {
            out.write(this.fillByte);
        }
        long index = extentBuffer.getStartOffset(); 
        int ebStart = (int) ((this.offset + index) - extentBuffer.getStartOffset());
        int ebLength = (int) Math.min(this.length - index, this.extentBuffer.getLength() - ebStart);
        ByteBuffer b = this.extentBuffer.getByteBuffer();
        out.write(b.array(), b.arrayOffset() + b.position(), ebLength);
        index += ebLength;
        
        // Pad out the remaining length, if any.
        for (long i = index; i < this.length; i++)
            out.write(this.fillByte);
        
        return 0;
    }
    
    public long streamExtentBufferMap(DataOutputStream out) throws IOException {
        long index = 0;

        // Iterate through the set of ExtentBuffers in this map.
        for (long key : this.map.keySet()) {
            ExtentBuffer eb = this.map.get(key);

            // Terminate this loop if the remaining ExtentBuffers are out of range.
            if (eb.getStartOffset() >= (this.offset + this.length)) {
//                System.out.println("done: " + eb.asString() + " out of range");
                break;
            }
            // If a part, or all, of this ExtentBuffer is in range, we must figure out which part of the buffer we need to deal with.
            if (eb.getStartOffset() >= this.offset && eb.getStartOffset() < (this.offset + this.length)
                    || eb.getEndOffset() >= this.offset && eb.getEndOffset() < (this.offset + this.length)) {

                // We have three cases to deal with:

                if ((this.offset + index) > eb.getStartOffset()) {
                    // We copy this ExtentBuffer inset from its start
                    int ebStart = (int) ((this.offset + index) - eb.getStartOffset());
                    int ebLength = (int) Math.min(this.length - index, eb.getLength() - ebStart);
//                    System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] A copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    out.write(b.array(), b.arrayOffset() + b.position() + ebStart, ebLength);
                    index += ebLength;
                } else if ((this.offset + index) == eb.getStartOffset()){
                    int ebStart = 0;
                    int ebLength = (int) Math.min(this.length - index, eb.getLength() - ebStart);
                    //System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] B copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    out.write(b.array(), b.arrayOffset() + b.position() + ebStart, ebLength);
                    index += ebLength;
                } else {
                    //System.out.println("fill " + (this.offset + index) + " to " + eb.getStartOffset());
                    while ((this.offset + index) < eb.getStartOffset()) {
                        out.write(this.fillByte);
                        index++;
                    }
                    int ebStart = 0;
                    int ebLength = (int) Math.min(this.length - index, eb.getLength() - ebStart);
                    //System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] C copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    out.write(b.array(), b.arrayOffset() + b.position() + ebStart, ebLength);
                    index += ebLength;
                }
            } else {
//                System.out.println(eb.asString() + " " + eb.getStartOffset() + " NOT in range: [" + this.offset + ":" +  (this.offset + this.length) + "]");
            }
        }

        // Pad out the remaining length, if any.
        for (long i = index; i < this.length; i++)
            out.write(this.fillByte);

        return this.length;
    }
    
    public byte[] render() {
        if (this.extentBuffer != null)
            return this.renderExtentBuffer();
        if (this.map != null)
            return this.renderExtentBufferMap();
        throw new IllegalArgumentException();
    }
    
    private byte[] renderExtentBuffer() {
        byte[] buffer = new byte[length];
        int index = 0;
        
        for (int i = 0; i < extentBuffer.getStartOffset(); i++) {
            buffer[index++] = this.fillByte;
        }
        int ebStart = (int) ((this.offset + index) - extentBuffer.getStartOffset());
        int ebLength = Math.min(this.length - index, this.extentBuffer.getLength() - ebStart);
        ByteBuffer b = this.extentBuffer.getByteBuffer();
        System.arraycopy(b.array(), b.arrayOffset() + b.position(), buffer, index, ebLength);
        index += ebLength;
        
        // Pad out the remaining length, if any.
        for (int i = index; i < buffer.length; i++)
            buffer[i] = this.fillByte;
        return buffer;        
    }
    
    private byte[] renderExtentBufferMap() {
        byte[] buffer = new byte[this.length];
        int index = 0;

        // Iterate through the set of ExtentBuffers in this map.
        for (long key : this.map.keySet()) {
            ExtentBuffer eb = this.map.get(key);

            // Terminate this loop if the remaining ExtentBuffers are out of range.
            if (eb.getStartOffset() >= (this.offset + this.length)) {
                System.out.println("done: " + eb.asString() + " out of range");
                break;
            }
            // If a part, or all, of this ExtentBuffer is in range, we must figure out which part of the buffer we need to deal with.
            if (eb.getStartOffset() >= this.offset && eb.getStartOffset() < (this.offset + this.length)
               || eb.getEndOffset() >= this.offset && eb.getEndOffset() < (this.offset + this.length)) {

                // We have three cases to deal with:

                if ((this.offset + index) > eb.getStartOffset()) {
                    // We copy this ExtentBuffer inset from its start
                    int ebStart = (int) ((this.offset + index) - eb.getStartOffset());
                    int ebLength = Math.min(this.length - index, eb.getLength() - ebStart);
                    System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] A copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    System.arraycopy(b.array(), b.arrayOffset() + b.position() + ebStart, buffer, index, ebLength);
                    index += ebLength;
                } else if ((this.offset + index) == eb.getStartOffset()){
                    int ebStart = 0;
                    int ebLength = Math.min(this.length - index, eb.getLength() - ebStart);
                    System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] B copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    System.arraycopy(b.array(), b.arrayOffset() + b.position() + ebStart, buffer, index, ebLength);
                    index += ebLength;
                } else {
                    System.out.println("fill " + (this.offset + index) + " to " + eb.getStartOffset());
                    while ((this.offset + index) < eb.getStartOffset())
                        buffer[index++] = this.fillByte;
                    int ebStart = 0;
                    int ebLength = Math.min(this.length - index, eb.getLength() - ebStart);
                    System.out.println(eb.asString() + " in range: [" + this.offset + ":" +  (this.offset + this.length) + "] C copy " + eb.asString() + " ebStart=" + ebStart + " ebLength=" + ebLength);
                    ByteBuffer b = eb.getByteBuffer();
                    System.arraycopy(b.array(), b.arrayOffset() + b.position() + ebStart, buffer, index, ebLength);
                    index += ebLength;
                }
            } else {
                System.out.println(eb.asString() + " " + eb.getStartOffset() + " NOT in range: [" + this.offset + ":" +  (this.offset + this.length) + "]");
            }
        }
        
        // Pad out the remaining length, if any.
        for (int i = index; i < buffer.length; i++)
            buffer[i] = this.fillByte;
        
        return buffer;
    }
    
    public static void main(String[] args) {
        try {
            ExtentBufferMap map = new ExtentBufferMap();

            ExtentBuffer buffer;

            buffer = new ExtentBuffer(4, ByteBuffer.wrap("45".getBytes()));
            map.put(buffer);

            byte[] two = "78".getBytes();
            buffer = new ExtentBuffer(7, ByteBuffer.wrap(two));
            map.put(buffer);

            byte[] three = "bcd".getBytes();
            buffer = new ExtentBuffer(11, ByteBuffer.wrap(three));
            map.put(buffer);


            ExtentBufferRender render;
            byte[] result;
            ByteArrayOutputStream bos;

            render = new ExtentBufferRender(0, 20, buffer);
            //result = render.render();
            bos = new ByteArrayOutputStream();
            render.streamTo(new DataOutputStream(bos));
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i])); } System.out.println();

            System.out.println("map: " + map.toString());

            render = new ExtentBufferRender(0, 20, map);
//            result = render.render();
            bos = new ByteArrayOutputStream();
            render.streamTo(new DataOutputStream(bos));
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i])); } System.out.println();

            render = new ExtentBufferRender(5, 5, map);
//            result = render.render();
            bos = new ByteArrayOutputStream();
            render.streamTo(new DataOutputStream(bos));
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i])); } System.out.println();

            render = new ExtentBufferRender(7, 5, map);
            //result = render.render();
            bos = new ByteArrayOutputStream();
            render.streamTo(new DataOutputStream(bos));
            result = bos.toByteArray();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3d", i)); } System.out.println();
            for (int i = 0; i < result.length; i++) { System.out.print(String.format("%3c", result[i])); } System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
