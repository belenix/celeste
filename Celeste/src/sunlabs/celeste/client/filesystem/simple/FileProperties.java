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

package sunlabs.celeste.client.filesystem.simple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.ByteBuffer;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import sunlabs.titan.util.OrderedProperties;

// an alternate philosophy would be to have getters and setters for all these properties,
// and not expose the key names to other classes.

//
// XXX: Should implement Serializable.  Or perhaps this class should simply be
//      replaced with OrderedProperties.
//
public class FileProperties {
    public final static String CONTENT_TYPE_DIRECTORY = "X-Celeste/Directory";

    private OrderedProperties properties;

    public FileProperties() {
        this.properties = new OrderedProperties();
    }

    public FileProperties(byte [] serializedProps) throws java.io.IOException {
        ByteArrayInputStream props_in = new ByteArrayInputStream(serializedProps);
        this.properties = new OrderedProperties();
        this.properties.load(props_in);
        // may want to validate version and do some things to it
    }

    public String get(String key) {
        return this.properties.getProperty(key);
    }

    public void put(String key, String value) {
        this.properties.setProperty(key, value);
    }

    public int size() {
        return this.properties.size();
    }

    public Set<Object> keySet() {
        return properties.keySet();
    }

    public byte[] toByteArray() throws java.io.IOException {
        ByteArrayOutputStream props_out = new ByteArrayOutputStream();
        this.properties.store(props_out, "File Meta-Data Structure");
        return props_out.toByteArray();
    }
    
    public ByteBuffer toByteBuffer() {
        return this.properties.toByteBuffer();
    }

    //
    // Methods intended to support higher level clients to store their
    // metadata alongside that of FileProperties's primary client: FileImpl.
    // (See discussion in FileImpl for an explanation of why this is
    // desirable.)
    //
    // The implementations are naive and have complexity linear in the size of
    // the maps involved.  They're intended for modest amounts of metadata
    // under the control of higher level file system abstractions (not for
    // arbitrary user-supplied metadata).
    //

    /**
     * <p>
     *
     * Add the properties in {@code other} to this {@code FileProperties}
     * object, using a new key for each property obtained by prepending its
     * key in {@code other} with {code prefix} followed by {@code "/"}.
     *
     * </p><p>
     *
     * Provided that they keys in this {@code FileProperties} object are
     * known, this method can be used to add in another set of properties that
     * can later be recovered with the {@link #splitOut(String) splitOut()}
     * method.
     *
     * </p>
     *
     * @param prefix    the prefix to apply to the names of properties that
     *                  are merged in
     *                  
     * @param other     a set of properties (possibly {@code null}) to be
     *                  merged into this set of properties
     * 
     */
    public void mergeInto(String prefix, FileProperties other) {
        if (prefix.contains("/"))
            throw new IllegalArgumentException(
                "prefix may not contain \"/\"");

        //
        // Rather than forcing callers to check for this case, handle it
        // here.
        //
        if (other == null)
            return;

        //
        // XXX: The casting that's required here and in splitOut() is ugly!
        //      But it's the price we pay for having SimpleProperties restrict
        //      its keys and values to Strings from Objects.
        //
        for (Object otherKey : other.properties.keySet()) {
            String prefixedKey = String.format("%s/%s", prefix, (String)otherKey);
            this.put(prefixedKey, other.get((String)otherKey));
        }
    }

    /**
     * <p>
     *
     * Remove all properties whose keys start with {@code prefix} followed by
     * {@code "/"}.
     *
     * </p><p>
     *
     * This method can be used to undo the results of calling {@link
     * #mergeInto(String, FileProperties) mergeInto((prefix,
     * <em>someProps</em>}.
     *
     * @param prefix    the prefix for the set of properties to be removed
     */
    public void removePrefixed(String prefix) {
        if (prefix.contains("/"))
            throw new IllegalArgumentException(
                "prefix may not contain \"/\"");

        String prefixSlash = prefix + "/";
        Iterator<Map.Entry<Object, Object>> it =
            this.properties.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Object> entry = it.next();
            String key = (String)entry.getKey();
            if (!key.startsWith(prefixSlash))
                continue;
            it.remove();
        }
    }

    /**
     * <p>
     *
     * Find all the properties in this {@code FileProperties} object whose
     * keys start with {@code prefix} followed by {@code "/"} and return a new 
     *{@code FileProperties} object whose keys are the trailing substrings of
     * those selected with the prefix sequence stripped off, paired with the
     * corresponding values from the original.
     *
     * </p<p>
     *
     * This method is intended to be used in conjunction with {@link
     * #mergeInto(String, FileProperties) mergeInto()} to store and retrieve a
     * set of subsidiary properties.
     *
     * </p>
     */
    public FileProperties splitOut(String prefix) {
        if (prefix.contains("/"))
            throw new IllegalArgumentException(
                "prefix may not contain \"/\"");

        String prefixSlash = prefix + "/";
        int originalStart = prefixSlash.length();
        FileProperties recovered = new FileProperties();
        for (Object key : this.properties.keySet()) {
            String keyAsString = (String)key;
            if (!keyAsString.startsWith(prefixSlash))
                continue;
            String originalKey = keyAsString.substring(originalStart);
            recovered.put(originalKey, this.get(keyAsString));
        }
        return recovered;
    }

    @Override
    public String toString() {
        return String.format("FileProperties[%s]", properties.toString());
    }
}
