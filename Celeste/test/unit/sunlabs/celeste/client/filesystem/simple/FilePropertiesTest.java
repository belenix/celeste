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
package sunlabs.celeste.client.filesystem.simple;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

//
// XXX: The tests below concentrate on the mergeInto(), splitOut(), and
//      removePrefixed() methods.  The class's other methods should be tested
//      as well.
//
public class FilePropertiesTest {
    private final static String pfx = "prefix";

    //
    // Populate a new FileProperties map with the given keys, with
    // corresponding values obtained by appending sfx to each key.
    //
    private static FileProperties populate(String sfx, String... keys) {
        FileProperties props = new FileProperties();
        for (String key : keys)
            props.put(key, key + sfx);
        return props;
    }

    //
    // Verify some basic properties of put() and mergeInto():
    //  -   putting entries with distinct keys into an empty map results in a
    //      map with that number of keys
    //  -   calling mergeInto() with maps that have overlapping keys results
    //      in a map whose number of entries is the sum of the number of input
    //      map entries
    //  -   keys from the map given as argument to mergeInto() appear properly
    //      prefixed in the resulting map.
    //
    @Test
    public final void testMergeIn0() {
        FileProperties props = populate("", "a", "b", "c");
        assertEquals("original map has three keys",
            props.size(), 3);
        FileProperties mergees = populate("-mergees", "a", "d");
        assertEquals("mergee map has two keys",
            mergees.size(), 2);
        props.mergeInto(pfx, mergees);
        assertEquals("map after mergeInto has five keys",
            props.size(), 5);

        int prefixedCount = 0;
        for (Object keyAsObject : props.keySet()) {
            String key = (String)keyAsObject;
            if (key.startsWith(pfx))
                prefixedCount++;
        }
        assertEquals(
            "# of prefixed keys in resulting map should equal # in arg map",
            prefixedCount, mergees.size());

        for (Object keyAsObject : mergees.keySet()) {
            String key = (String)keyAsObject;
            String prefixedKey = String.format("%s/%s", pfx, key);
            String mergedValue = props.get(prefixedKey);
            assertNotNull("mergee key must be represented in result",
                mergedValue);
            assertEquals("values from mergee map must match in merged map",
                mergedValue, mergees.get(key));
        }
    }

    //
    // (Assuming that testMergeIn0 succeeds) verify some basic properties of
    // splitOut():
    //  -   The FileProperties object resulting from merging in a set of
    //      properties and then splitting them back out has the same key-value
    //      pairs as the FileProperties object that was merged in.
    //
    @Test
    public final void testSplitOut0() {
        FileProperties props = populate("", "a", "b", "c");
        FileProperties mergees = populate("-mergees", "a", "d");
        props.mergeInto(pfx, mergees);
        FileProperties retrieved = props.splitOut(pfx);

        assertEquals(
            "split out properties must have same cardinality as originals",
            mergees.size(), retrieved.size());

        for (Object keyAsObject : mergees.keySet()) {
            String key = (String)keyAsObject;
            String retrievedValue = retrieved.get(key);
            assertNotNull(
                "split out property must exist for each mergee key",
                retrievedValue);
            assertEquals("retrieved value must equal original mergeee value",
                retrievedValue, mergees.get(key));
        }
    }

    @Test
    public final void testRemovedPrefixed0() {
        //
        // props0 is a copy of the original state of props.
        //
        FileProperties props = populate("", "a", "b", "c");
        FileProperties props0 = populate("", "a", "b", "c");
        FileProperties mergees = populate("-mergees", "a", "d");
        props.mergeInto(pfx, mergees);
        props.removePrefixed(pfx);

        assertEquals(
            "merging then removing must yield same cardinality as original",
            props.size(), props0.size());

        for (Object keyAsObject : props0.keySet()) {
            String key = (String)keyAsObject;
            String value = props.get(key);
            assertNotNull(
                "each original key must still exist in props",
                value);
            assertEquals(
                "values after mergeInto/removePrefixed must equal originals",
                value, props0.get(key));
        }
    }
}
