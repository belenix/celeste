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

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

//
// The tests below are white box tests in the sense that they depend on the
// behavior of the Value class.
//
public class LRUCacheTest {
    //
    // The kind of thing that the caches in the tests below will hold.  Holds
    // just enough state to:
    //  -   allow the cache's activate() and deactivate() behavior to be
    //      tested
    //  -   allow individual instances to be identified and distinguished from
    //      one another
    //  -   determine whether or not an instance has been disposed of
    //
    private static class Value {
        private static int nextId = 0;
        private final int id = Value.nextId++;
        private boolean active = false;
        private boolean disposed = false;

        public int getId() {
            return this.id;
        }
        
        public boolean isActive() {
            return this.active;
        }

        public void setActive(boolean b) {
            this.active = b;
        }

        public boolean isDisposed() {
            return this.disposed;
        }

        public void dispose() {
            this.disposed = true;
        }
    }

    private static class TestFactory implements LRUCache.Factory<Long, Value> {
        public Value newInstance(Long key) {
            return new Value();
        }
    }

    private static class TestCache extends LRUCache<Long, Value> {
        public TestCache(int capacity) {
            super(capacity, new TestFactory());
        }

        @Override
        protected boolean activate(Value v) {
            v.setActive(true);
            return true;
        }

        @Override
        protected void deactivate(Value v) {
            v.setActive(false);
        }

        @Override
        protected void disposeItem(Long key, Value value) {
            value.dispose();
        }

        //
        // Dump the cache's entries in iteration order.  (Intended to be used
        // for debugging should a test fail.)
        //
        public void dumpEntries(PrintStream os, String tag) {
            os.printf("map entries: %s%n", tag);
            for (Map.Entry<Long, Set<Value>> entry : this.entrySet()) {
                os.printf("\tKey: %2d%n", entry.getKey());
            }
        }
    }

    //
    // Verify that:
    //  -   A newly created cache is empty.
    //  -   The cache delivers an active object.
    //
    @Test
    public final void testGetAndRemove0() {
        TestCache cache = new TestCache(2);
        assertEquals("cache should start with no entries",
            cache.size(), 0);
        Value value = null;
        try {
            value = cache.getAndRemove(0L);
        } catch (Exception e) {
            assertTrue("unexpected exception", false);
        }
        assertEquals("cache should still have no entries after getAndRemove()",
            cache.size(), 0);
        final int id = value.getId();
        assertTrue("object obtained from cache is active", value.isActive());
    }

    //
    // Verify that:
    //  -   Putting an active object back into the cache deactivates it.
    //  -   The cache then has one entry.
    //
    // The test is structured to repeat the setup accomplished by the one
    // above.
    //
    @Test
    public final void testAddAndEvictOld0() {
        TestCache cache = new TestCache(2);
        Value value = null;
        try {
            value = cache.getAndRemove(0L);
        } catch (Exception e) {
            assertTrue("unexpected exception", false);
        }
        cache.addAndEvictOld(0L, value);
        assertTrue("object returned to cache is inactive", !value.isActive());
        assertEquals("cache should have one entry after addAndEvictOld()",
            cache.size(), 1);
    }

    //
    // Verify that, after putting an active object back into an empty cache
    // (where the cache created the object originally):
    //  -   Getting an entry using the same key yields the origianl entry.
    //  -   That entry is once again active.
    //  -   The cache is again empty.
    //
    // The test is structured to repeat the setup accomplished by the one
    // above.
    //
    @Test
    public final void testGetAndRemove1() {
        TestCache cache = new TestCache(2);
        Value value = null;
        try {
            value = cache.getAndRemove(0L);
        } catch (Exception e) {
            assertTrue("unexpected exception", false);
        }
        final int id = value.getId();
        cache.addAndEvictOld(0L, value);
        Value value2 = null;
        try {
            value2 = cache.getAndRemove(0L);
        } catch (Exception e) {
            assertTrue("unexpected exception", false);
        }
        assertEquals(
            "after returning to the cache, refetch should return the original",
            id, value2.getId());
        assertTrue("object re-obtained from cache is active",
            value2.isActive());
        assertEquals("cache should again be empty",
            cache.size(), 0);
    }

    //
    // Verify that:
    //  -   When the cache reaches capacity, its eldest entry is evicted.
    //  -   The evicted entry is disposed of.
    //  -   The remaining entries have not been disposed of.
    //
    @Test
    public final void testDisposeEntry0() {
        TestCache cache = new TestCache(2);
        Value value0 = null;
        Value value1 = null;
        Value value2 = null;
        try {
            value0 = cache.getAndRemove(0L);
            value1 = cache.getAndRemove(1L);
            value2 = cache.getAndRemove(2L);
        } catch (Exception e) {
            assertTrue("unexpected exception", false);
        }
        //
        // Just for fun, put the values back in reverse order.  Adding value0
        // should cause value2 to be evicted, since the capacity is 2.
        //
        cache.addAndEvictOld(2L, value2);
        cache.addAndEvictOld(1L, value1);
        cache.addAndEvictOld(0L, value0);
        assertEquals("capacity 2 cache should have 2 entries after adding 3",
            cache.size(), 2);
        assertTrue("least recently accessed item should be disposed",
            value2.isDisposed());
        assertTrue("remaining items should not be disposed",
            !value0.isDisposed() && !value1.isDisposed());
    }

    //
    // XXX: Add tests to:
    //  -   Check behavior with multiple entries accessed via a single key.
    //  -   Check behavior with multiple keys.
    //  -   Verify that disposing of the cache evicts and disposes all its
    //      entries.
    //  -   Sanity check behavior for non-exclusive item use caches.
    //
}
