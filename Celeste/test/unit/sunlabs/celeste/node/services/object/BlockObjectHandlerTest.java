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
package sunlabs.celeste.node.services.object;

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

import sunlabs.beehive.BeehiveObjectId;

import sunlabs.beehive.api.BeehiveObject;

import sunlabs.beehive.util.BufferableExtent;
import sunlabs.beehive.util.BufferableExtentImpl;
import sunlabs.beehive.util.ExtentBuffer;
import sunlabs.beehive.util.ExtentBufferMap;

import sunlabs.celeste.client.ReplicationParameters;

import static org.junit.Assert.*;

//
// Although its name suggests more generality, this class tests only BObject
// behavior.
//
public class BlockObjectHandlerTest {
    //
    // Verify that we can construct a BObject and that we can retrieve the
    // stuff we put into it.
    //
    @Test
    public final void testConstructBlockObject0()  throws
            IOException, ClassNotFoundException {
        BufferableExtent bounds = new BufferableExtentImpl(10L, 30);
        ByteBuffer b15_20 = ByteBuffer.wrap("fghij".getBytes());
        ExtentBuffer eb15_20 = new ExtentBuffer(15L, b15_20);
        ExtentBufferMap data = new ExtentBufferMap();
        data.put(eb15_20);

        //
        // Quick sanity check: Verify that data has an extent buffer in it.
        //
        assertEquals("data map should have one entry",
            data.size(), 1);

        //
        // Create a BObject from bounds and data.
        //
        BlockObjectHandler.BObject blockObject =
            new BlockObjectHandler.BObject(
                bounds, data, (BeehiveObject.Metadata)null,
                new BeehiveObjectId(), 10L,
                new ReplicationParameters("ErasureCoderReplica/2"));

        //
        // Examine the BObject.
        //
        ExtentBufferMap retrievedData = blockObject.getDataAsExtentBufferMap();
        assertEquals("retrieved data map should have one entry",
            retrievedData.size(), 1);

        //
        // Serialize the blockObject and recreate it from the serialized form.
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(blockObject);
        out.flush();
        baos.flush();

        byte[] serialized = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream in = new ObjectInputStream(bais);
        BlockObjectHandler.BObject deserializedBObject =
            (BlockObjectHandler.BObject)in.readObject();

        out.close();
        baos.close();
        in.close();
        bais.close();

        //
        // Examine the deserialized BObject.
        //
        ExtentBufferMap deserializedData =
            deserializedBObject.getDataAsExtentBufferMap();
        assertEquals("retrieved data map should have one entry",
            deserializedData.size(), 1);
        ExtentBuffer eb = deserializedData.get(15L);
        assertNotNull("deserialized data map should have entry at key 15L", eb);
        int pos = eb.offsetToPosition(17L);
        assertEquals("expect correct contents at offset 17L",
            eb.get(pos), (byte)'h');
    }
}
