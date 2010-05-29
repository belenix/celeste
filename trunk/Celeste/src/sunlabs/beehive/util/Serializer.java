/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A base class to provide self-serialization capability to classes that
 * extend it.
 */
public class Serializer {

    /**
     * Serialize an object into a byte array and return it.
     *
     * @return a byte array containing the serialized form of the object
     *
     * @throws IOException if the object cannot be serialized
     */
    public static byte[] toByteArray(Object o) throws IOException {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	new ObjectOutputStream(baos).writeObject(o);
	return baos.toByteArray();
    }

    /**
     * Serialize this object into a byte array and return it.
     *
     * @return a byte array containing the serialized form of this object
     *
     * @throws IOException if the object cannot be serialized
     */
    public byte[] toByteArray() throws IOException {
	return toByteArray(this);
    }

    /**
     * Deserialize an object from the specified byte array. 
     *
     * @param buf the byte array containing the object to be deserialized
     *
     * @return an object deserialized from the byte array
     *
     * @throws IOException if the object cannot be deserialized, including
     * if the object's class is not available in the current classloader.
     */
    public static Object fromByteArray(byte[] buf) throws IOException {
	ByteArrayInputStream bais = new ByteArrayInputStream(buf);
	try {
	    return new ObjectInputStream(bais).readObject();
	} catch (ClassNotFoundException e) {
	    throw new IOException("decoding object in buffer", e);
	}
    }

    /**
     * Deserialize an object of the specified class from the specified byte
     * array.
     *
     * @param buf the byte array containing the object to be deserialized.
     *
     * @return an object deserialized from the byte array
     *
     * @throws IOException if the object cannot be deserialized, including
     * if the object's class is not available in the current classloader,
     * or if the object is not of the specified type.
     */
    public static <C> C fromByteArray(byte[] buf, Class <? extends C> cls)
	throws IOException {

	Object o = fromByteArray(buf);
	if (o != null && !cls.isAssignableFrom(o.getClass())) {
	    throw new IOException("unexpected object in buffer");
	}
	return cls.cast(o);
    }
}
