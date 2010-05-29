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

import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import sunlabs.beehive.BeehiveObjectId;
//import sunlabs.beehive.dolr.BeehiveObjectId_;
import sunlabs.beehive.util.Extent;
import sunlabs.beehive.util.ExtentBuffer;
import sunlabs.beehive.util.ExtentImpl;

import static org.junit.Assert.*;

import static sunlabs.celeste.client.filesystem.simple.BufferCache.Reader;
import static sunlabs.celeste.client.filesystem.simple.BufferCache.ReadResult;

//
// XXX: Need tests that exercise the code to update reference times, both
//      after doing a write (via predicatedWrite()) and a read.
//
//      After the isCached() methods are added to BufferCache, add
//      corresponding test methods to this class.  Hmmm...  They're now in and
//      one could do so, but only at the risk of writing tests that break when
//      code to support cache management policies is added later on.  (The
//      danger is that the policies could prevent data from being cached in
//      the first place or from persisting long enough for a test to find the
//      data in the cache.)
//
public class BufferCacheTest {

    //
    // A test harness implementation of the Reader class.  It uses a read-only
    // byte array as the backing store against which its call() method
    // executes.  (The byte array is declared in the surrounding class to
    // facilitate checks that extent buffers drawn from it have the correct
    // contents.)
    //
    // The reader can be told to fail or to delay its response for a stated
    // amount of time.  The latter is useful in setting up tests involving
    // concurrent readers.
    //
    private static class TestReader extends Reader {
        private final boolean   shouldFail;
        private final long      responseDelayMillis;

        public TestReader(boolean shouldFail, long responseDelayMillis) {
            super();
            this.shouldFail = shouldFail;
            this.responseDelayMillis = responseDelayMillis;
        }

        public TestReader(boolean shouldFail) {
            this(shouldFail, 0);
        }

        public TestReader() {
            this(false);
        }

        public Object call() throws Exception {
            if (shouldFail) {
                throw new FileException.CelesteFailed();
            }

            if (responseDelayMillis > 0) {
                try {
                    Thread.sleep(responseDelayMillis);
                } catch (InterruptedException ignore) { }
            }

            //
            // N.B. Minimal bounds checking.  Use properly!  (But does
            // accommodate attempted reads beyond end of file.)
            //
            Extent desiredExtent = this.getDesiredExtent();
            long start = desiredExtent.getStartOffset();
            long end = desiredExtent.getEndOffset();
            if (end == Long.MIN_VALUE)
                end = BufferCacheTest.bytes.length;

            ByteBuffer buffer = null;
            if (start < BufferCacheTest.bytes.length) {
                buffer = ByteBuffer.wrap(BufferCacheTest.bytes,
                    (int)start, (int)(end - start)).
                slice();
            } else {
                buffer = ByteBuffer.wrap("".getBytes());
            }
            ExtentBuffer extentBuffer = new ExtentBuffer(start, buffer);
            setResult(
                new ReadResult(BufferCacheTest.version, extentBuffer));
            return null;
        }
    }

    //
    // The contents of the "file" this reader is simulating.
    //
    private static final byte[] bytes =
        "0123456789abcdefghijklmnopqrstuvwxyz".getBytes();

    //
    // The version of the file being cached and a new version to be created in
    // some tests.
    //
    private final static BeehiveObjectId version =
        new BeehiveObjectId("version1".getBytes());
    private final static BeehiveObjectId newVersion =
        new BeehiveObjectId("version2".getBytes());

    //
    // Some useful extents and extent buffers.
    //

    private static final Extent e00_00 = new ExtentImpl(0L, 0L);

    private static final Extent e00_10 = new ExtentImpl(0L, 10L);
    private static final Extent e05_10 = new ExtentImpl(5L, 10L);

    private static final Extent e00_01 = new ExtentImpl(0L, 1L);
    private static final Extent e01_02 = new ExtentImpl(1L, 2L);
    private static final Extent e02_03 = new ExtentImpl(2L, 3L);
    private static final Extent e03_04 = new ExtentImpl(3L, 4L);
    private static final Extent e04_05 = new ExtentImpl(4L, 5L);
    private static final Extent e05_06 = new ExtentImpl(5L, 6L);
    private static final Extent e06_07 = new ExtentImpl(6L, 7L);
    private static final Extent e07_08 = new ExtentImpl(7L, 8L);
    private static final Extent[] extents00_08 = {
        e00_01, e01_02, e02_03, e03_04,
        e04_05, e05_06, e06_07, e07_08
    };

    private static final Extent e00_02 = new ExtentImpl(0L, 2L);
    private static final Extent e02_04 = new ExtentImpl(2L, 4L);
    private static final Extent e04_06 = new ExtentImpl(4L, 6L);
    private static final Extent e06_08 = new ExtentImpl(6L, 8L);
    private static final Extent[] extents00_08_by_2 = {
        e00_02, e02_04, e04_06, e06_08
    };

    private static final Extent e36_40 = new ExtentImpl(36L, 40L);

    private static final ExtentBuffer eb01_05 =
        ExtentBuffer.wrap(2L, "bcde".getBytes());
    private static final ExtentBuffer eb02_06 =
        ExtentBuffer.wrap(2L, "cdef".getBytes());

    //
    // Verify that the two extents given as argument have equal bounds.
    //
    private static boolean boundsMatch(Extent e1, Extent e2) {
        if (e1.getStartOffset() != e2.getStartOffset())
            return false;
        if (e1.getEndOffset() != e2.getEndOffset())
            return false;
        return true;
    }

    //
    // Verify that the contents of the extent buffer given as argument match
    // the corresponding sub-array of bytes.  Assumes that the ending offset
    // has been normalized to a positive value (as opposed to Long.MIN_VALUE).
    //
    private static boolean bytesMatch(ExtentBuffer extentBuffer) {
        long start = extentBuffer.getStartOffset();
        long end = extentBuffer.getEndOffset();
        int length = (int)(end - start);
        for (int i = 0; i < length; i++) {
            if (extentBuffer.get(i) != BufferCacheTest.bytes[(int)(start + i)]) {
                System.out.printf(
                    "bytesMatch mismatch:%n\thave \"%s\" (%c) pos %d%n" +
                    "\tneed \"%c\" at %d%n",
                    extentBuffer.asString(), extentBuffer.get(i),
                    extentBuffer.position(),
                    BufferCacheTest.bytes[(int)(start + i)],
                    i);
                return false;
            }
        }
        return true;
    }

    //
    // Verify basic behavior:
    //  -   That the cache can use the reader to access data
    //  -   That the resulting file version is the one requested
    //  -   That the resulting extent buffer has the correct bounds (and
    //      matches the entire requested extent)
    //  -   That the data delivered is correct
    //  -   That all chunks of the buffered data have cache policy information
    //      associated with them
    // (Note that the second and third conditions don't hold in general, but
    // should for the specific objects used for this test.)
    //
    @Test
    public final void testRead0() {
        BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        Extent desiredExtent = e00_10;
        ReadResult result = null;
        try {
            result = cache.read(version, desiredExtent, reader);
        } catch (FileException e) {
            assertTrue("no exception expected", false);
        }
        assertTrue("result should be non-null", result != null);
        assertTrue("result version should match requested version",
            BufferCacheTest.version.equals(result.version));
        ExtentBuffer extentBuffer = result.buffer;
        assertTrue("resulting buffer should be non-null",
            extentBuffer != null);
        assertTrue("bounds of resulting buffer must match requested extent",
            BufferCacheTest.boundsMatch(e00_10, extentBuffer));
        assertTrue("data should be correct",
            BufferCacheTest.bytesMatch(extentBuffer));
        cache.verifyPolicyInfo(result.version);
    }

    //
    // Verify that:
    //  -   Re-reading a sub-extent of one already read delivers correct
    //      bounds and data.
    //
    @Test
    public final void testRead1() {
        BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        Extent desiredExtent = e00_10;
        ReadResult result = null;
        try {
            result = cache.read(version, desiredExtent, reader);
        } catch (FileException e) {
            assertTrue("no exception expected", false);
        }
        desiredExtent = e05_10;
        try {
            result = cache.read(version, desiredExtent, reader);
        } catch (FileException e) {
            assertTrue("no exception expected", false);
        }
        ExtentBuffer extentBuffer = result.buffer;
        assertTrue("bounds of resulting buffer must match requested extent",
            BufferCacheTest.boundsMatch(e05_10, extentBuffer));
        assertTrue("data should be correct",
            BufferCacheTest.bytesMatch(extentBuffer));
    }

    //
    // Verify that:
    //  -   A zero-length read produces correct results
    //
    @Test
    public final void testRead2() {
        BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        Extent desiredExtent = e00_00;
        ReadResult result = null;
        try {
            result = cache.read(version, desiredExtent, reader);
        } catch (FileException e) {
            assertTrue("no exception expected", false);
        }
        assertTrue("result should be non-null", result != null);
        assertTrue("result version should match requested version",
            BufferCacheTest.version.equals(result.version));
        ExtentBuffer extentBuffer = result.buffer;
        assertTrue("resulting buffer should be non-null",
            extentBuffer != null);
        assertTrue("resulting buffer should be vacuous",
            extentBuffer.isVacuous());
    }

    //
    // Verify that:
    //  -   A read starting at the end of the file succeeds and returns a
    //      vacuous extent.
    //
    @Test
    public final void testRead3() {
        BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        Extent desiredExtent = e36_40;
        ReadResult result = null;
        try {
            result = cache.read(version, desiredExtent, reader);
        } catch (FileException e) {
            e.printStackTrace();
            assertTrue("no exception expected", false);
        }
        assertTrue("result should be non-null", result != null);
        assertTrue("result version should match requested version",
            BufferCacheTest.version.equals(result.version));
        ExtentBuffer extentBuffer = result.buffer;
        assertTrue("resulting buffer should be non-null",
            extentBuffer != null);
        assertTrue("resulting buffer should be vacuous",
            extentBuffer.isVacuous());
    }

    //
    // XXX: Need tests for requested ranges that exceed the size of the
    //      simulated file.  (But since much of the required logic must be put
    //      into the reader we supply, they might not reveal much...)
    //

    //
    // Verify that:
    //  -   A read requesting an extent that extends beyond the end of the
    //      simulated file succeds.
    //  -   The read returns correct bounds and data.
    //
    /* Implement me! */

    //
    // Verify that:
    //  -   A failure in a supplied Reader propagates as the proper exception.
    //
    @Test
    public final void testReadFail() {
        BufferCache cache = new BufferCache();
        TestReader reader = new TestReader(true);   // request failure
        Extent desiredExtent = e00_10;
        ReadResult result = null;
        try {
            result = cache.read(version, desiredExtent, reader);
            assertTrue("cache.read() call should not succeed", false);
        } catch (FileException e) {
            assertTrue("exception expected", true);
        } catch (Throwable t) {
            assertTrue("should not get a non-FileException Throwable", false);
        }
    }

    //
    // Verify that:
    //  -   A reader that delays completing its i/o for a particular span
    //      completes successfully after the delay.
    //  -   A second reader arriving after the first and requesting a trailing
    //      sub-span of the first reader's span completes successfully.
    //
    // Uses delays to (attempt to) force a particular exceution interleaving.
    // The main thread starts a second, which simulates lengthy i/o by
    // delaying two seconds.  The main thread itself delays half a second to
    // allow the second to reach its delay point (by which time the buffer
    // cache should have created an entry in its pending i/o map).  It then
    // proceeds to do a read of its own (which should block until the pending
    // i/o has completed).
    //
    @Test
    public final void testReadConcurrent0() {
        final BufferCache cache = new BufferCache();
        //
        // The first reader will delay two seconds before completing.
        //
        final TestReader reader1 = new TestReader(false, 2000);
        final Extent desiredExtent1 = e00_10;
        Thread t1 = new Thread("t1") {
            public void run() {
                ReadResult result1 = null;
                try {
                    result1 = cache.read(version, desiredExtent1, reader1);
                } catch (FileException e) {
                    assertTrue("no exception expected from thread 1", false);
                }
                ExtentBuffer extentBuffer1 = result1.buffer;
                //
                // XXX: Because they're hidden in a separate thread, the Junit
                //      framework doesn't see failures from these asserts.
                //      What to do?  (At least they do appear in the
                //      transcript.)
                //
                //      It seems that one needs to subclass
                //      org.junit.runner.Runner.  Unfortunately, that class's
                //      javadoc description is too sketchy to figure out how
                //      to do so.  Sigh...
                //
                assertTrue(
                    "bounds of resulting buffer must match requested extent",
                    BufferCacheTest.boundsMatch(e00_10, extentBuffer1));
                assertTrue("data1 should be correct",
                    BufferCacheTest.bytesMatch(extentBuffer1));
            }
        };

        TestReader reader2 = new TestReader();
        Extent desiredExtent2 = e05_10;
        ReadResult result2 = null;

        t1.start();

        //
        // Delay half a second to give t1 a chance to get rolling.
        //
        try {
            Thread.sleep(500);
        } catch (InterruptedException ohMy) {
            assertTrue("sleep should not be interrupted", false);
        }

        try {
            result2 = cache.read(version, desiredExtent2, reader2);
        } catch (FileException e) {
            assertTrue("no exception expected from main thread", false);
        }
        ExtentBuffer extentBuffer2 = result2.buffer;
        assertTrue("bounds of resulting buffer must match requested extent",
            BufferCacheTest.boundsMatch(e05_10, extentBuffer2));
        assertTrue("data2 should be correct",
            BufferCacheTest.bytesMatch(extentBuffer2));

        try {
            t1.join();
        } catch (InterruptedException ohMy) {
            assertTrue("join should not be interrupted", false);
        }
    }

    //
    // XXX: Add test of asynchronous behavior.
    //

    //
    // XXX: Need test that creates a bunch of tiny extents and then replaces
    //      several in the middle with another one.  (I.e., need test that
    //      verifies that the replaceExtents() bug in BufferCache has been
    //      properly fixed.)
    //

    //
    // Verify that:
    // -    After a write following a sequence or fragmentary reads, all the
    //      resulting extent buffers have proper policy information associated
    //      with them.
    // XXX: Ought to be able to add additional checks to this test and the
    //      next.  Do so.
    //
    @Test
    public final void testReadWrite0_1() {
        final BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        ReadResult result = null;
        //
        // Build up a fragmented set of extents.
        //
        for (Extent e : extents00_08) {
            try {
                result = cache.read(version, e, reader);
            } catch  (FileException fe) {
                assertTrue("no exception expected", false);
            }
        }
        //
        // Overwrite a chunk in the center.
        //
        cache.predicatedWrite(version, newVersion, eb02_06);
        cache.verifyPolicyInfo(version);
        cache.verifyPolicyInfo(newVersion);
    }

    //
    // As above, but with extents of width 2 and a mis-aligned write.
    //
    @Test
    public final void testReadWrite0_2() {
        final BufferCache cache = new BufferCache();
        TestReader reader = new TestReader();
        ReadResult result = null;
        //
        // Build up a fragmented set of extents.
        //
        for (Extent e : extents00_08_by_2) {
            try {
                result = cache.read(version, e, reader);
            } catch  (FileException fe) {
                assertTrue("no exception expected", false);
            }
        }
        //
        // Overwrite a chunk in the center.
        //
        cache.predicatedWrite(version, newVersion, eb01_05);
        cache.verifyPolicyInfo(version);
        cache.verifyPolicyInfo(newVersion);
    }
}
