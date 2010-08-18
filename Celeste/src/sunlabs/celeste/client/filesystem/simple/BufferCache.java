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

import java.lang.management.ManagementFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.XHTML.Table;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.util.Extent;
import sunlabs.titan.util.ExtentBuffer;
import sunlabs.titan.util.ExtentBufferMap;
import sunlabs.titan.util.ExtentImpl;
import sunlabs.titan.util.ExtentMap;
import sunlabs.titan.util.WeakMBeanRegistrar;

import static java.lang.Math.max;

//
// XXX: Should the class-level javadoc comment say anything about JMX (e.g.,
//      about what JMX names instances have, about the philosophy of
//      regarding JMX as being nice to have, but not essential, etc.)?
//
/**
 * <p>
 *
 * The {@code BufferCache} class provides caching services for clients (such
 * as {@link sunlabs.celeste.client.filesystem.simple.FileImpl FileImpl)}
 * that manage successive versions of a file, using {link BeehiveObjectId}s
 * to identify those versions.
 *
 * </p><p>
 *
 * TODO:  There's currently no way to evict entries from the cache.  The
 * internal {@code PolicyInfo} class captures basic information required for a
 * cache management policy, but policy code that uses this information does
 * not yet exist.
 *
 * </p>
 */
//
// A problem the design must solve:
//
//  Multiple readers may block waiting for i/o to complete on a given extent.
//  Between the time this extent becomes available and the time all blocked
//  readers have had a chance to access it, the extent must remain available.
//  (That is, it's not acceptable to have the extent age out and be discarded
//  during this period.)
//
//  Stated differently, until pending readers have drained, the buffer
//  resulting from the read must remain available to them.  (And to new
//  readers arriving during that interval.)
//
// XXX: It's time to add eviction code.  One thing it needs to help with policy
//      is accounting for how much space each cache entry uses.  We definitely
//      need this at the per-file level.  It would also potentially be useful
//      to have it at the per-file version level.  One policy that immediately
//      comes to mind is a cap on how much space the cache uses.  That policy
//      could offer different options about what to reap to stay under the cap
//      but the basic version has to be to throw out entire files, LRU first
//      until space is back within bounds.  (When adding to the cache, we'll
//      need to know how much we're proposing to add and, if necessary, toss
//      before adding.)
//
public class BufferCache implements BufferCacheMBean {
    /**
     * An immutable tuple class containing the results of a successful read.
     */
    public static class ReadResult {
        /**
         * The file version to which the result applies
         */
        public final BeehiveObjectId   version;
        /**
         * An extent buffer holding the results of the read
         */
        public final ExtentBuffer   buffer;

        /**
         * Construct a new {@code ReadResult} object.
         *
         * @param version   the file version to which the result applies
         * @param buffer    an extent buffer holding the results of the read
         */
        public ReadResult(BeehiveObjectId version, ExtentBuffer buffer) {
            this.version = version;
            this.buffer = buffer;
        }
    }

    //
    // Information associated with a given buffer that's pertinent to
    // implementing a buffer management policy.  For this implementation, it
    // consists simply of a LRU time.
    //
    private static class PolicyInfo {
        private long    referenceTime;

        public PolicyInfo(long referenceTime) {
            this.referenceTime = referenceTime;
        }

        public synchronized long getReferenceTime() {
            return this.referenceTime;
        }

        public synchronized void setReferenceTime(long referenceTime) {
            //
            // Enforce a monotonic non-decreasing update policy.
            //
            this.referenceTime = max(referenceTime, this.referenceTime);
        }
    }

    //
    // A PendingExtent records information pertinent to a read in progress.
    // While i/o is pending, it captures the region being read.  After the i/o
    // completes, it records the resulting buffer or the cause of i/o failure.
    //
    // After it is determined that the cache does not contain data for a given
    // pending extent, the extent is added to the pendingExtents map for the
    // file version in question.  Readers arriving before i/o completes block
    // on the extent's condition variable.  When i/o completes, the pending
    // extent is removed from its map and blocked readers are signalled.
    // These readers can use the resulting extent buffer directly or can find
    // it in the ExtentBufferMap for the resulting file version (unless a
    // concurrent reaper thread has already removed it; this possibility is
    // the motivation for recording the extent buffer in the PendingExtent
    // object).
    //
    private class PendingExtent extends ExtentImpl {
        private final static long serialVersionUID = 1L;

        private ReadResult      resolved = null;
        private Throwable       failureCause = null;

        //
        // A condition variable to go with the buffer cache lock.  Readers
        // waiting on this pendingExtent use it, so that disjoint sets of
        // readers can proceed concurrently.
        //
        private  final Condition cv = BufferCache.this.lock.newCondition();

        public PendingExtent(Extent extent) {
            super(extent);
        }

        public PendingExtent(long startOffset, long endOffset) {
            super(startOffset, endOffset);
        }

        public ReadResult getResolved() {
            return this.resolved;
        }

        public void setResolved(ReadResult result) {
            this.resolved = result;
        }

        public Throwable getFailureCause() {
            return this.failureCause;
        }

        public void setFailureCause(Throwable throwable) {
            this.failureCause = throwable;
        }

        public Condition getCondition() {
            return this.cv;
        }

        //
        // Bounds only...
        //
        // XXX: Ought to push this up into ExtentImpl.
        //
        @Override
        public String toString() {
            long eo = this.getEndOffset();
            String end = (eo == Long.MIN_VALUE) ?
                "infinity" : String.format("%d", eo);
            return String.format("[%d, %s)", this.getStartOffset(), end);
        }
    }

    /**
     * <p>
     *
     * {@code Reader} is a {@code Callable} with provision for recording the
     * results of the read that it's intended to perform.
     *
     *  </p><p>
     *
     * Before {@code call()} is invoked, the {@code desiredExtent} attribute
     * must have been set to designate the span to be read.  The
     * implementation of {@code call()} is expected to perform the read and
     * must either record its results by setting the {@code result} attribute
     * or throw an exception.
     *
     * </p>
     */
    public abstract static class Reader implements Callable<Object> {
        private Extent      desiredExtent = null;
        private ReadResult  result = null;

        public Extent getDesiredExtent() {
            return this.desiredExtent;
        }

        public void setDesiredExtent(Extent extent) {
            this.desiredExtent = extent;
        }

        public ReadResult getResult() {
            return this.result;
        }

        public void setResult(ReadResult result) {
            this.result = result;
        }
    }

    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    // XXX: This code sets up a per-object type registrar.  It'd be better to
    //      set up a per-MBean server registrar.  But that requires machinery
    //      that's not in place.
    //
    private final static WeakMBeanRegistrar registrar =
        new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    //
    // If cacheEnabled is false, read, write, and truncate operations will add
    // no new data to the cache.  However, existing cached data will remain
    // and will participate in satisfying those operations.  (The flush()
    // operations can be used in conjunction with setCacheEnabled(false) to
    // disable the cache entirely.)
    //
    // XXX: In the absence of effective flushing code, using true as a default
    //      can have dire consequences (OutOfmemoryErrors).  So the default is
    //      reversed to false below.
    //
    // Setting this to true causes the system to eventually run out of memory.
    private boolean cacheEnabled = false;

    //
    // For each file version we've encountered, the data-bearing buffers
    // currently in hand.  The map is linked, so that insertion order can be
    // determined.  (This determination is required for the variant of the
    // flush method that provides an option to retain the data for the most
    // recent version.)
    //
    private Map<BeehiveObjectId, ExtentBufferMap>  buffersByVersion =
        new LinkedHashMap<BeehiveObjectId, ExtentBufferMap>();

    //
    // The policy information for each buffer.  (Every ExtentBuffer in each of
    // the ExtentBufferMaps that are values of the buffersByVersion map above
    // should appear as a key in this map.)
    //
    // XXX: It would be preferable to fold this information into the extent
    //      buffers themselves.  But doing so prevents them from living in
    //      ExtentBufferMap instances.  (Why is a long, sorry tale of nasty
    //      interactions of the ExtentBufferMap implementation with the Java
    //      type system.)
    //
    // XXX: Needs to be a weak reference map.  We don't want to have to
    //      reference count PolicyInfo instances, because that's painful and
    //      error-prone.  Having an instance persist in this map after no
    //      ExtentBuffer keys it is harmless, so having the map maintain only
    //      weak references to its contents should work out nicely.
    //      (Reference counting comes up as an issue because its possible for
    //      a given ExtentBuffer to appear in the extent buffer map for more
    //      than one version of the file; this occurs naturally as part of
    //      handling writes.)
    //
    private Map<ExtentBuffer, PolicyInfo> policyInfo =
        new HashMap<ExtentBuffer, PolicyInfo>();

    //
    // Maps (per file version) of pending read requests.  Each is keyed by
    // extents that record the desired span of each i/o.
    //
    private Map<BeehiveObjectId, ExtentMap<PendingExtent>> pendingReadsByVersion =
        new HashMap<BeehiveObjectId, ExtentMap<PendingExtent>>();

    //
    // Used where we might otherwise synchronize on this, to allow for
    // finer-grained classes of waiters (one per pendingExtent).
    //
    private final Lock lock = new ReentrantLock();

    //
    // The name by which this instance is known to JMX.
    //
    private final ObjectName jmxObjectName;

    //
    // Statistics, for this cache individually and for all caches
    // collectively.
    //
    // "Reads" is the number of calls made to read().  "Cached" is the number
    // of those that were satisfied with cache-resident data.  "Writes" and
    // "Truncates" are similar.  "Bytes" prefixes the above to name fields
    // capturing the total number of bytes involved in the corresponding
    // operation, except for truncate, where the notion isn't useful.  "Async"
    // denotes a call that's specified to return asynchronously.  "Total"
    // denotes a collective total for all caches.
    //
    private static long totalReads = 0;
    private static long totalAsyncReads = 0;
    private static long totalCached = 0;
    private static long totalWrites = 0;
    private static long totalTruncates = 0;
    private static long totalAttrUpdates = 0;
    private static long totalBytesRead = 0;
    private static long totalBytesCached = 0;
    private static long totalBytesWritten = 0;

    private long reads = 0;
    private long asyncReads = 0;
    private long cached = 0;
    private long writes = 0;
    private long truncates = 0;
    private long attrUpdates = 0;
    private long bytesRead = 0;
    private long bytesCached = 0;
    private long bytesWritten = 0;

    /**
     * Construct a new {@code BufferCache} instance.
     */
    public BufferCache() {
        //
        // Default to using this class's package name as the prefix for this
        // instance's JMX ObjectName.
        //
        this((ObjectName)null);
    }

    /**
     * Construct a new {@code BufferCache} instance, using {@code
     * jmxObjectNamePrefix} as the prefix to use in forming its JMX object
     * name.  {@code jmxObjectNamePrefix} may be {@code null}, in which case
     * {@code BufferCache}'s package name is used in its place.  The newly
     * created instance registers itself with the platform MBean
     * server, ignoring any errors that the registration attempt may induce.
     *
     * @param jmxObjectNamePrefix   the leading part of the new instance's
     *                              name for JMX management purposes
     */
    //
    // XXX: I'm becoming rather skeptical that it ought to be the
    //      constructor's responsibility to register with an MBean server.
    //      First, which server?  The platform server is a convenient default,
    //      but it's not necessarily the server of choice.  Second, the entity
    //      using the cache is in a better position than the constructor to
    //      determine whether or how the cache should be managed.
    //
    //      Maybe providing a non-null object name prefix should be taken as a
    //      declaration of intent that registration is desired.
    //
    public BufferCache(ObjectName jmxObjectNamePrefix) {
        //
        // Everything is already initialized (via field initializers) except
        // for arranging for this instance to be observed through the MBean
        // server associated with the JVM we're running in.
        //
        this.jmxObjectName = this.registerMBean(jmxObjectNamePrefix, null);
    }

    /**
     * <p>
     *
     * Register this object with the MBean server denoted by {@code server},
     * using {@code prefix} to form the leading part of the object's name and
     * {@code disambiguator} to avoid collisions with an already existent
     * name.  {@code prefix} can be {@code null}, in which case this object's
     * package name becomes the prefix.  {@code disambiguator} can be {@code
     * null}, in which case a string representation of this object's has code
     * is used in its place.
     *
     * </p><p>
     *
     * Registration errors are silently ignored, so using this method is
     * appropriate only when JMX registration is desirable, but not essential.
     *
     * </p>
     *
     * @param server        the MBean server with which to register
     * @param prefix        a prefix to use in forming the name to be used for
     *                      the server to refer to this object
     * @param disambiguator a string to use to avoid naming collisions
     *
     * @return  the {@code ObjectName} with which this instance was registered
     *          or {@code null} if registration failed
     */
    //
    // XXX: Ideally should be a static method of the JMX class, but since it
    //      extracts and uses this's package and class names, it can't be.
    //
    // XXX: Private for now, since it seems to do a good enough job that
    //      subclasses don't themselves need to worry about JMX
    //      instrumentation.  But may well have to become protected, so that
    //      subclasses can take control of their own JMX destinies.  (At that
    //      point, the constructors above will have to be refactored to avoid
    //      registering with an MBean server if a subclass is going to so so.)
    //
    private ObjectName registerMBean(ObjectName prefix, String disambiguator) {
        ObjectName jmxObjectName = null;
        String packageName = null;
        String fullClassName = this.getClass().getName();
        int leafIndex = fullClassName.lastIndexOf(".") + 1;
        String className = fullClassName.substring(leafIndex);
        //
        // If prefix is null, use our package name as a default.
        //
        if (prefix == null)
            packageName = this.getClass().getPackage().getName();
        try {
            //
            // Register, retrying with the disambiguator if the attempt fails
            // because the name is already in use.
            //
            try {
                if (prefix == null) {
                    jmxObjectName = JMX.objectName(packageName, className);
                } else {
                    jmxObjectName = JMX.objectName(prefix, className);
                }
                BufferCache.registrar.registerMBean(jmxObjectName, this,
                    BufferCacheMBean.class);
            } catch (InstanceAlreadyExistsException e1) {
                //
                // Use this instance's hash code as a default
                // disambiguator.
                //
                if (disambiguator == null)
                    disambiguator = String.format("%d", this.hashCode());
                String disambiguated =
                    String.format("%s-%s", className, disambiguator);
                if (prefix == null) {
                    jmxObjectName = JMX.objectName(packageName, disambiguated);
                } else {
                    jmxObjectName = JMX.objectName(prefix, disambiguated);
                }
                BufferCache.registrar.registerMBean(jmxObjectName, this,
                    BufferCacheMBean.class);
            }
        } catch (MalformedObjectNameException e) {
            return null;
        } catch (MBeanRegistrationException e) {
            return null;
        } catch (NotCompliantMBeanException e) {
            return null;
        } catch (InstanceAlreadyExistsException e) {
            return null;
        }
        return jmxObjectName;
    }

    /**
     * Determine whether at least the first byte of {@code extent} is present
     * in the cache for the given {@code version} of the file being cached.
     * Return {@code true} if so and {@code false} otherwise.
     *
     * @return  {@code true} if a leading portion of {@code} extent is present
     *          in the cache and {@code false} otherwise
     *
     * @param version   the file version to check
     * @param extent    the extent to check
     */
    public boolean isCached(BeehiveObjectId version, Extent extent) {
        return isCached(version, extent, false);
    }

    /**
     * Determine whether at least the first byte (or, if {@code entire} is
     * {@code true}), the entire range) of {@code extent} is present in the
     * cache for the given {@code version} of the file being cached.  Return
     * {@code true} if so and {@code false} otherwise.
     *
     * @param version   the file version to check
     * @param extent    the extent to check
     * @param entire    {@code true} if the entire must range be present,
     *                  {@code false} if just its first byte must be present
     *
     * @return {@code true} if the designated portion of {@code} extent is
     *          present in the cache and {@code false} otherwise
     */
    public boolean isCached(BeehiveObjectId version, Extent extent,
            boolean entire) {
        this.lock();
        try {
            ExtentBufferMap bufferMap = this.getExtentBufferMap(version);
            synchronized (bufferMap) {
                ExtentBufferMap intersectionMap = bufferMap.intersect(extent);
                if (intersectionMap.size() == 0)
                    return false;
                if (extent.getStartOffset() != intersectionMap.getStartOffset())
                    return false;
                if (!entire)
                    return true;
                if (!intersectionMap.isContiguous())
                    return false;
                if (extent.getEndOffset() == intersectionMap.getEndOffset())
                    return true;
                return false;
            }
        } finally {
            this.unlock();
        }
    }

    /**
     * <p>
     *
     * Satisfy a read requesting data for {@code desiredExtent} by first
     * consulting buffers cached for the file version denoted by {@code
     * desiredVersion}, invoking {@code reader} to perform i/o if the cache
     * contains no suitable data.
     *
     * </p><p>
     *
     * Note that {@code desiredVersion} must name a specific file version, but
     * that {@code reader} is free to perform i/o against either a specific
     * version or the current version of the file.  If the latter, the data
     * returned may belong to a more recent version than {@code
     * desiredVersion}.
     *
     * </p><p>
     *
     * If it needs to invoke {@code reader.call()}, this method will have
     * first set the extent to be fetched by calling {@code
     * reader.setDesiredExtent()}.  The reader's implementation of {@code
     * call()} should perform the indicated i/o and record its results by
     * calling {@code reader.setResult()}.  If the i/o fails, the reader is
     * free to throw a {@link FileException}.  It is possible that {@code
     * reader} may may (re)configured and {@code call()}ed multiple times in
     * the course of satisfying this request.
     *
     * </p>
     *
     * @param desiredVersion    the file version to check for cached buffers
     *                          that might satisfy the read
     * @param desiredExtent     the range to be read
     *
     * @param reader            a {@code Reader} to be invoked if i/o is
     *                          required to satisfy the read request
     *
     * @return  a buffer holding the desired data and the file version from
     *          which that data originated, packaged as a {@code ReadResult}
     *          object
     *
     * @throws FileException
     *      if any of several possible things go wrong while attempting to
     *      satisfy the read
     */
    public ReadResult read(BeehiveObjectId desiredVersion, Extent desiredExtent,
            Reader reader)
        throws
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException {
        return read(desiredVersion, desiredExtent, reader, false);
    }

    /**
     * <p>
     *
     * Satisfy a read requesting data for {@code desiredExtent} by first
     * consulting buffers cached for the file version denoted by {@code
     * desiredVersion}, invoking {@code reader} to perform i/o if the cache
     * contains no suitable data.
     *
     * </p><p>
     *
     * Note that {@code desiredVersion} must name a specific file version, but
     * that {@code reader} is free to perform i/o against either a specific
     * version or the current version of the file.  If the latter, the data
     * returned may belong to a more recent version than {@code
     * desiredVersion}.
     *
     * </p><p>
     *
     * If it needs to invoke {@code reader.call()}, this method will have
     * first set the extent to be fetched by calling {@code
     * reader.setDesiredExtent()}.  The reader's implementation of {@code
     * call()} should perform the indicated i/o and record its results by
     * calling {@code reader.setResult()}.  If the i/o fails, the reader is
     * free to throw a {@link FileException}.  It is possible that {@code
     * reader} may may (re)configured and {@code call()}ed multiple times in
     * the course of satisfying this request.
     *
     * </p><p>
     *
     * If {@code async} is {@code true}, this method returns {@code null}
     * after having checked whether the desired extent is already present in
     * the cache and, if not, having initiated the i/o required to fetch it.
     * This usage pattern is intended for use in supporting read-ahead.
     *
     * </p>
     *
     * @param desiredVersion    the file version to check for cached buffers
     *                          that might satisfy the read
     * @param desiredExtent     the range to be read
     * @param reader            a {@code Reader} to be invoked if i/o is
     *                          required to satisfy the read request
     * @param async             only initiate the i/o for the read (if
     *                          necessary); don't wait for it to complete
     *
     * @return  a buffer holding the desired data and the file version from
     *          which that data originated, packaged as a {@code ReadResult}
     *          object, or {@code null} if {@code async} was set
     *
     * @throws FileException
     *      if any of several possible things go wrong while attempting to
     *      satisfy the read
     */
    //
    // XXX: Need to decide how to handle requests whose results wouldn't fit
    //      in an extent buffer.  We could demand that the caller supply a
    //      suitably constrained extent buffer argument, chop it down
    //      ourselves, or let the Reader worry about it.  After thinking about
    //      it, it probably ought to be the reader's responsibility to chop
    //      down extents.  (The reader also ought to be free to move the
    //      starting offset down to a convenient alignment boundary.)
    //
    public ReadResult read(BeehiveObjectId desiredVersion, Extent desiredExtent,
            Reader reader, boolean async)
        throws
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException {
        final long startOffset = desiredExtent.getStartOffset();

        //
        // Respond to a vacuous request with a vacuous result.
        //
        if (desiredExtent.isVacuous()) {
            ExtentBuffer eb = ExtentBuffer.wrap(startOffset, "".getBytes());
            synchronized (BufferCache.class) {
                totalReads++;
                reads++;
            }
            return new ReadResult(desiredVersion, eb);
        }

        //
        // This initial implementation uses coarse-grained locking.  But
        // accesses to lower-level data structures are (redundantly) protected
        // with lower-level locks, with the intent of making it easier to move
        // to finer-grained locking should that prove to be needed.
        //
        // The overall structure of the method is to repeatedly try to find a
        // buffer that can satisfy the read request, initiating a new read or
        // waiting for one in progress to complete if there's no suitable
        // buffer.  The loop is broken by exceptions resulting from a read
        // attempt or by finding a buffer that resolves the read.
        //
        for (;;) {
            this.lock();
            try {
                //
                // See whether the request can be satisfied from existing
                // cached buffers.
                //
                ExtentBufferMap bufferMap =
                    this.getExtentBufferMap(desiredVersion);
                ExtentBuffer resultBuffer = null;
                synchronized (bufferMap) {
                    ExtentBufferMap intersectionMap =
                        bufferMap.intersect(desiredExtent);
                    if (intersectionMap.size() > 0 &&
                            intersectionMap.getStartOffset() <= startOffset) {
                        //
                        // We have data on hand that can satisfy the request.
                        // In fact, the first buffer in intersectionMap holds
                        // at least the start of the requested data.  Return a
                        // view onto that buffer.  (At the cost of copying, we
                        // potentially could return data from the second and
                        // subsequent extents.  Doing so would also require a
                        // check that the extents are contiguous.  If we were
                        // to provide a version of read() whose results
                        // included an ExtentBufferMap, we could avoid the
                        // copy, but would still have to provide the
                        // contiguity check.)
                        //
                        ExtentBuffer first =
                            intersectionMap.get(intersectionMap.firstKey());
                        long firstOffset = first.getStartOffset();
                        resultBuffer = first.asReadOnlyBuffer().
                            position((int)(startOffset - firstOffset));
                    }
                }

                if (resultBuffer != null) {
                    if (async) {
                        //
                        // The data requested (or at least some part of it) is
                        // already in the cache.  Thus, there's no need to
                        // continue on and try to fetch it.
                        //
                        synchronized (BufferCache.class) {
                            totalReads++;
                            totalAsyncReads++;
                            totalCached++;
                            reads++;
                            asyncReads++;
                            cached++;
                        }
                        return null;
                    }
                    //
                    // Synchronization is required here to ensure that the
                    // selected buffer's reference time is monotonically
                    // non-decreasing.  (The as yet unimplemented weak
                    // reference scheme for the policyInfo map avoids
                    // synchronization issues revolving around ensuring that a
                    // map entry doesn't disappear just as it's being
                    // grabbed.)
                    //
                    synchronized (this.policyInfo) {
                        //
                        // Update the buffer's reference time and then return
                        // it.
                        //
                        updateReferenceTime(desiredVersion,
                            resultBuffer.getStartOffset(),
                            System.currentTimeMillis());
                        int capacity = resultBuffer.capacity();
                        synchronized (BufferCache.class) {
                            totalReads++;
                            reads++;
                            totalCached++;
                            cached++;
                            totalBytesRead += capacity;
                            bytesRead += capacity;
                            totalBytesCached += capacity;
                            bytesCached += capacity;
                        }
                        return new ReadResult(desiredVersion, resultBuffer);
                    }
                }
                //
                // XXX: Consider an else clause here that trims desiredExtent
                //      down to avoid re-reading any trailing portion that's
                //      already buffered.  (But it doesn't hurt to re-read,
                //      and since having an overlap at the end is likely to be
                //      quite rare, giving that case a compete treatment
                //      probably isn't worth it.)
                //

                //
                // I/o is necessary to satisfy the request.  However, it may
                // already be in progress.  If so, don't initiate a redundant
                // request, but rather wait for the pending request to
                // complete.
                //

                //
                // Is there an i/o already in progress that covers (at least)
                // the leading part of the desired range?
                //
                ExtentMap<PendingExtent> pendingReads = null;
                synchronized (pendingReadsByVersion) {
                    pendingReads = pendingReadsByVersion.get(desiredVersion);
                    if (pendingReads == null) {
                        pendingReads = new ExtentMap<PendingExtent>();
                        pendingReadsByVersion.put(desiredVersion, pendingReads);
                    }
                }
                PendingExtent pendingExtent = null;
                synchronized (pendingReads) {
                    pendingExtent = pendingReads.getExtent(startOffset);
                }
                if (pendingExtent == null) {
                    //
                    // No other readers are looking for data that starts
                    // within desiredExtent.  Thus, it falls to this reader to
                    // initiate i/o.  Note that this implementation makes no
                    // attempt to adjust the desired extent, but instead just
                    // passes the one it was given through.
                    //
                    reader.setDesiredExtent(desiredExtent);
                    pendingExtent = new PendingExtent(desiredExtent);
                    pendingReads.put(startOffset, pendingExtent);
                    doReadIO(reader, pendingReads, pendingExtent);
                }

                if (async) {
                    //
                    // The i/o has been initiated, so we're done.  (If it
                    // fails or delivers data for a new version, that's ok.
                    // Our caller will have to cope with whatever the
                    // resulting situation turns out to be.)
                    //
                    synchronized (BufferCache.class) {
                        totalReads++;
                        totalAsyncReads++;
                        reads++;
                        asyncReads++;
                    }
                    return null;
                }

                //
                // Wait for the pending read to complete.  If it succeeded,
                // and the read produced data for desiredVersion (as opposed
                // for a version that's sprung into existence since the last
                // time we checked), use the resulting buffer.  If it failed,
                // propagate the exception.  Otherwise, try again by taking
                // another turn through the loop.
                //
                try {
                    pendingExtent.getCondition().await();
                } catch (InterruptedException e) {
                    //
                    // Something disturbed our wait.  Retry from the
                    // beginning.  This might mean that we issue a redundant
                    // i/o, but that's something we can tolerate.
                    //
                    continue;
                }
                //
                // It's possible that the await() above returned spuriously.
                // (Its specification says that it can.)  If so, retry from
                // the beginning.  As in the InterruptedException case above,
                // this might lead to redundant i/o, but that's life.
                //
                synchronized (pendingReads) {
                    //
                    // If pendingExtent is still in the pendingReads map, then
                    // the wakeup was premature.
                    //
                    if (pendingReads.containsKey(startOffset))
                        continue;
                }
                Throwable t = pendingExtent.getFailureCause();
                if (t != null) {
                    if (t instanceof FileException)
                        throw (FileException)t;
                    //
                    // XXX; Can this happen?  By the time we get here,
                    //      Celeste-level exceptions should have been caught
                    //      and wrapped into FileException subclass instances.
                    //
                    if (t instanceof CelesteException.AccessControlException)
                        throw new FileException.PermissionDenied(t);
                    //
                    // Since the exception being handled here originated from
                    // the reader's call() method, and that method is supposed
                    // to confine itself to FileException subclasses for its
                    // checked exceptions, we should be left with unchecked
                    // exceptions or checked exceptions indicative of
                    // programming errors.  In both cases, wrapping with
                    // FileException.Runtime is appropriate.
                    //
                    throw new FileException.Runtime(t);
                }
                ReadResult result = pendingExtent.getResolved();
                if (desiredVersion.equals(result.version)) {
                    //
                    // The result may be a superset of what was requested.
                    // If it's non-vacuous, trim it down.
                    //
                    ExtentBuffer trimmedBuffer =
                        result.buffer.asReadOnlyBuffer();
                    if (!trimmedBuffer.isVacuous())
                        trimmedBuffer = trimmedBuffer.intersect(desiredExtent);
                    int capacity = trimmedBuffer.capacity();
                    synchronized (BufferCache.class) {
                        totalReads++;
                        reads++;
                        totalBytesRead += capacity;
                        bytesRead += capacity;
                    }
                    return new ReadResult(desiredVersion, trimmedBuffer);
                } else {
                    //
                    // The i/o revealed a new file version.  Switch to it,
                    // since we already know the cache had nothing for the
                    // previous version and therefore looking again won't
                    // help.
                    //
                    desiredVersion = result.version;
                }
            } finally {
                this.unlock();
            }
        }
    }

    /**
     * <p>
     *
     * Create a cache entry for {@code newVersion} by modifying the entry for
     * {@code oldVersion} with the update contained in {@code modifiedExtent}.
     *
     * </p><p>
     *
     * If the caller requires that the data in {@code modifiedExtent} not be
     * modifiable, it must ensure that property itself.  That is, this method
     * does not copy its data.
     *
     * </p><p>
     *
     * This method is intended to be called as part of a write operation that
     * is predicated on {@code oldVersion}, producing {@code newVersion},
     * after the write proper has succeeded.
     *
     * </p>
     *
     * @param oldVersion        the file version upon which the write was
     *                          predicated
     * @param newVersion        the new file version resulting from the write
     * @param modifiedExtent    the data written to create the new version
     */
    public void predicatedWrite(BeehiveObjectId oldVersion,
            BeehiveObjectId newVersion, ExtentBuffer modifiedExtent) {
        this.lock();
        try {
            ExtentBufferMap oldMap = this.buffersByVersion.get(oldVersion);
            ExtentBufferMap newMap = this.buffersByVersion.get(newVersion);
            if (newMap != null) {
                throw new IllegalStateException(
                    "buffer map for new version already exists");
            }
            newMap = (oldMap == null) ?
                new ExtentBufferMap() : new ExtentBufferMap(oldMap);
            this.buffersByVersion.put(newVersion, newMap);
            this.addToCache(new ReadResult(newVersion, modifiedExtent));
            int capacity = modifiedExtent.capacity();
            synchronized (BufferCache.class) {
                totalWrites++;
                writes++;
                totalBytesWritten += capacity;
                bytesWritten += capacity;
            }
        } finally {
            this.unlock();
        }
    }

    /**
     * <p>
     *
     * Create a cache entry for {@code newVersion} by modifying the entry for
     * {@code oldVersion} according to the truncate operation specified by
     * {@code newLength}.
     *
     * </p><p>
     *
     * This method is intended to be called as part of a truncate operation
     * that is predicated on {@code oldVersion}, producing {@code newVersion},
     * after the truncate proper has succeeded.
     *
     * @param oldVersion        the file version upon which the truncate was
     *                          predicated
     * @param newVersion        the new file version resulting from the
     *                          truncate
     * @param newLength         the length of the new file version
     */
    public void predicatedTruncate(BeehiveObjectId oldVersion,
            BeehiveObjectId newVersion, long newLength) {
        this.lock();
        try {
            ExtentBufferMap oldMap = this.buffersByVersion.get(oldVersion);
            ExtentBufferMap newMap = this.buffersByVersion.get(newVersion);
            if (newMap != null)
                throw new IllegalStateException(
                    "buffer map for new version already exists");
            if (oldMap == null || oldMap.size() == 0) {
                newMap = new ExtentBufferMap();
            } else {
                //
                // Data for the old version of the file remain valid in the
                // new up to the point of truncation.
                //
                newMap = this.intersect(oldMap, new ExtentImpl(0L, newLength));
            }
            this.buffersByVersion.put(newVersion, newMap);
            synchronized (BufferCache.class) {
                totalTruncates++;
                truncates++;
            }
        } finally {
            this.unlock();
        }
    }

    /**
     * <p>
     *
     * Create a cache entry for {@code newVersion} that duplicates the entry
     * for {@code oldVersion}.
     *
     * </p><p>
     *
     * This method is intended to be called as part of an operation predicated
     * on {@code oldVersion} that produces {@code newVersion}, modifies only
     * file attributes, and leaves file data unchanged.  It should be called
     * after the operation proper has succeeded.
     *
     * </p>
     *
     * @param oldVersion    the file version upon which the operation was
     *                      predicated
     * @param newVersion    the new file version resulting from the operation
     */
    public void predicatedAttributeChange(BeehiveObjectId oldVersion,
            BeehiveObjectId newVersion) {
        this.lock();
        try {
            ExtentBufferMap oldMap = this.buffersByVersion.get(oldVersion);
            ExtentBufferMap newMap = this.buffersByVersion.get(newVersion);
            if (newMap != null)
                throw new IllegalStateException(
                    "buffer map for new version already exists");
            //
            // Since the new version's data is identical to the old's, use the
            // same map for both (if possible).  That way, readers of both
            // versions get the benefits of entries induced by reads of either
            // version.
            //
            if (oldMap == null || oldMap.size() == 0)
                newMap = new ExtentBufferMap();
            else
                newMap = oldMap;
            this.buffersByVersion.put(newVersion, newMap);
            synchronized (BufferCache.class) {
                totalAttrUpdates++;
                attrUpdates++;
            }
        } finally {
            this.unlock();
        }
    }

    /**
     * Return an XHTML table containing statistics for this cache's
     * performance.
     *
     * @return  this cache's performance statistics, as an XHTML table
     */
    public Table getStatisticsAsXHTML() {
        XML.Attribute tableClass = new XML.Attr("class", "buffer-cache");
        XML.Attribute colTwoAttrs = new XML.Attr("class", "integer-statistic");
        synchronized (BufferCache.class) {
            return new Table(tableClass).add(new Table.Caption("Buffer Cache")).
                add(new Table.Body(
                    new Table.Row(
                        new Table.Data("Number of Reads"),
                        new Table.Data(colTwoAttrs).add("" + this.reads)),
                    new Table.Row(
                        new Table.Data("Number of Async Reads"),
                        new Table.Data(colTwoAttrs).add("" + this.asyncReads)),
                    new Table.Row(
                        new Table.Data("Number of Reads Satisfied from Cache"),
                        new Table.Data(colTwoAttrs).add("" + this.cached)),
                    new Table.Row(
                        new Table.Data("Number of Writes"),
                        new Table.Data(colTwoAttrs).add("" + this.writes)),
                    new Table.Row(
                        new Table.Data("Number of Truncates"),
                        new Table.Data(colTwoAttrs).add("" + this.truncates)),
                    new Table.Row(
                        new Table.Data("Number of Attribute Updates"),
                        new Table.Data(colTwoAttrs).add("" + this.attrUpdates)),
                    new Table.Row(
                        new Table.Data("Bytes Read Overall"),
                        new Table.Data(colTwoAttrs).add("" + this.bytesRead)),
                    new Table.Row(
                        new Table.Data("Bytes Read Directly from Cache"),
                        new Table.Data(colTwoAttrs).add("" + this.bytesCached)),
                    new Table.Row(
                        new Table.Data("Bytes Written"),
                        new Table.Data(colTwoAttrs).add("" + this.bytesWritten))
                )
            );
        }
    }

    /**
     * Return an XHTML table containing aggregate buffer cache performance
     * statistics.
     *
     * @return  aggregate cache performance statistics, as an XHTML table
     */
    public static Table getAggregateStatisticsAsXHTML() {
        XML.Attribute tableClass = new XML.Attr("class", "buffer-cache");
        XML.Attribute colTwoAttrs = new XML.Attr("class", "integer-statistic");
        synchronized (BufferCache.class) {
            return new Table(tableClass).add(
                    new Table.Caption("Aggregate Buffer Cache"),
                    new Table.Body(
                    new Table.Row(
                        new Table.Data("Number of Reads"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalReads)),
                    new Table.Row(
                        new Table.Data("Number of Async Reads"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalAsyncReads)),
                    new Table.Row(
                        new Table.Data("Number of Reads Satisfied from Cache"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalCached)),
                    new Table.Row(
                        new Table.Data("Number of Writes"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalWrites)),
                    new Table.Row(
                        new Table.Data("Number of Truncates"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalTruncates)),
                    new Table.Row(
                        new Table.Data("Number of Attribute Updates"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalAttrUpdates)),
                    new Table.Row(
                        new Table.Data("Bytes Read Overall"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalBytesRead)),
                    new Table.Row(
                        new Table.Data("Bytes Read Directly from Cache"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalBytesCached)),
                    new Table.Row(
                        new Table.Data("Bytes Written"),
                        new Table.Data(colTwoAttrs).add(
                            "" + BufferCache.totalBytesWritten))
                )
            );
        }
    }

    //
    // BufferCacheMBean methods
    //
    // Aggregate total are reported via instance methods (rather than static
    // methods) so that JMX instrumentation can access them.  (JMX doesn't
    // offer a way to access static information, right?)
    //

    public long getReads() {
        return this.reads;
    }

    public long getAggregateReads() {
        return BufferCache.totalReads;
    }

    public long getAsyncReads() {
        return this.asyncReads;
    }

    public long getAggregateAsyncReads() {
        return BufferCache.totalAsyncReads;
    }

    public long getCached() {
        return this.cached;
    }

    public long getAggregateCached() {
        return BufferCache.totalCached;
    }

    public long getWrites() {
        return this.writes;
    }

    public long getAggregateWrites() {
        return BufferCache.totalWrites;
    }

    public long getTruncates() {
        return this.truncates;
    }

    public long getAggregateTruncates() {
        return BufferCache.totalTruncates;
    }

    public long getAttrUpdates() {
        return this.attrUpdates;
    }

    public long getAggregateAttrUpdates() {
        return BufferCache.totalAttrUpdates;
    }

    public long getBytesRead() {
        return this.bytesRead;
    }

    public long getAggregateBytesRead() {
        return BufferCache.totalBytesRead;
    }

    public long getBytesCached() {
        return this.bytesCached;
    }

    public long getAggregateBytesCached() {
        return BufferCache.totalBytesCached;
    }

    public long getBytesWritten() {
        return this.bytesWritten;
    }

    public long getAggregateBytesWritten() {
        return BufferCache.totalBytesWritten;
    }

    public void flush() {
        this.flush(false);
    }

    public void flush(boolean retainCurrentVersion) {
        this.lock();
        try {
            //
            // If retainCurrentVersion is set, we need to know what the most
            // recent version is, so that we can avoid flushing it.  Since
            // buffersByVersion is set up to iterate in insertion order, the
            // last key delivered by iterating over its key set is what we
            // want.
            //
            BeehiveObjectId mostRecentVersion = null;
            if (retainCurrentVersion) {
                Iterator<BeehiveObjectId> keyIterator =
                    this.buffersByVersion.keySet().iterator();
                while (keyIterator.hasNext())
                    mostRecentVersion = keyIterator.next();
            }
            //
            // Discard the indicated entries from buffersByVersion.
            //
            // Note that all we're doing here is dropping references to
            // ExtentBufferMaps that in turn refer to ExtentBuffers holding
            // cached data.  The cached data could still be referenced by code
            // elsewhere that has done a read against the cache (or even a
            // write to it).  Also, retaining the most recent version can
            // implicitly retain ExtentBuffers that originated with older
            // versions; this is a consequence of sharing buffers for extents
            // that a file update didn't change.
            //
            Iterator<Map.Entry<BeehiveObjectId, ExtentBufferMap>> entryIterator =
                this.buffersByVersion.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map.Entry<BeehiveObjectId, ExtentBufferMap> entry =
                    entryIterator.next();
                //
                // This check will trigger only on the last iteration and only
                // then if mostRecentVersion was set non-null above.
                //
                if (entry.getKey().equals(mostRecentVersion))
                    continue;
                entryIterator.remove();
            }
        } finally {
            this.unlock();
        }
    }

    public boolean isCacheEnabled() {
        this.lock();
        try {
            return this.cacheEnabled;
        } finally {
            this.unlock();
        }
    }

    public void setCacheEnabled(boolean enable) {
        this.lock();
        this.cacheEnabled = enable;
        this.unlock();
    }

    //
    // Package-visibility methods
    //
    // Primarily intended as support code for JUnit tests of this class
    //

    /**
     * Ensure that every extent in the given file {@code version} has a
     * corresponding {@code PolicyInfo} entry, throwing {@code
     * IllegalStateException} if not.
     *
     * @param version   the file version to check
     *
     * @throws IllegalStateException
     *      if the given file version has a buffered extent without
     *      corresponding policy information
     */
    void verifyPolicyInfo(BeehiveObjectId version) {
        this.lock();
        try {
            ExtentBufferMap map = buffersByVersion.get(version);
            verifyPolicyInfo(map);
        } finally {
            this.unlock();
        }
    }

    /**
     * Ensure that every extent in the given file {@code version} has a
     * corresponding {@code PolicyInfo} entry, throwing {@code
     * IllegalStateException} if not.
     *
     * @param map   the extent buffer map to check
     *
     * @throws IllegalStateException
     *      if the given extent buffer map has an entry without corresponding
     *      policy information
     */
    void verifyPolicyInfo(ExtentBufferMap map) {
        if (map == null)
            return;
        this.lock();
        try {
            for (ExtentBuffer eb : map.values()) {
                if (policyInfo.get(eb) == null)
                    throw new IllegalStateException(String.format(
                        "no PolicyInfo for [%s, %s)",
                        eb.getStartOffset(),
                        eb.getEndOffset()));
            }
        } finally {
            this.unlock();
        }
    }

    //
    // Private methods
    //

    //
    // Lock manipulation.  Since there's only one lock per cache instance,
    // provide direct access to operations on it.
    //

    private void lock() {
        this.lock.lock();
    }

    private void unlock() {
        this.lock.unlock();
    }

    //
    // Perform the i/o embodied in reader.
    //
    private void doReadIO(final Reader reader,
            final ExtentMap<PendingExtent> pendingReads,
            final PendingExtent pendingExtent) {
        //
        // XXX: Could probably use some sort of Executor here rather than
        //      creating a thread directly.
        //
        new Thread("celeste-reader") {
            @Override
            public void run() {
                //
                // Start by doing the read without any synchronization.  (Any
                // thread that's interested on the results synchronizes on
                // pendingExtent and will block upon noticing that it's
                // present in the pendingReads map.)  Pulling the read out of
                // the synchronized span allows threads interested in getting
                // at an extent that doesn't overlap with pendingExtent to
                // proceed while the read's in progress.
                //
                Throwable failureCause = null;
                try {
                    reader.call();
                } catch (Throwable t) {
                    failureCause = t;
                }

                //
                // Update pendingExtent with the results of the read and
                // notify threads waiting for desiredExtent.
                //
                BufferCache.this.lock();
                try {
                    if (failureCause != null) {
                        pendingExtent.setFailureCause(failureCause);
                    } else {
                        ReadResult result = reader.getResult();
                        //
                        // Add the extent and matching PolicyInfo entry to the
                        // cache.
                        //
                        if (result != null)
                            addToCache(result);
                        //
                        // Even when the cache is enabled and the resulting
                        // extent has gone into it, the extent also must be
                        // recorded in pendingExtent.  Doing so is necessary
                        // to ensure that waiting readers have a chance to use
                        // the extent in the face of a concurrent thread
                        // that's purging old extents from the cache.
                        //
                        pendingExtent.setResolved(result);
                    }
                    //
                    // Now that the results of the read are posted to the
                    // cache, newly arriving readers should coordinate with
                    // the cache itself.  If the read failed, they'll have to
                    // try from scratch.  That is, this read is no longer
                    // pending.
                    //
                    pendingReads.remove(pendingExtent.getStartOffset());

                    //
                    // Let all threads waiting for the pending extent know
                    // that it is (or isn't, in the case of an exception)
                    // available.
                    //
                    pendingExtent.getCondition().signalAll();
                } finally {
                    BufferCache.this.unlock();
                }
            }
        }.start();
    }

    //
    // XXX: Need method to support FileImpl.truncate().  (But it's not
    //      essential, provided that we're willing to accept an extra
    //      interaction with Celeste for a read following a truncate.)
    //

    //
    // Add the extent buffer held in result to the cache.  Assumes the caller
    // has handled locking, which must cover a wide enough span to ensure that
    // the buffer map remains coordinated with the policyInfo map.
    //
    private void addToCache(ReadResult result) {
        assert result != null;

        if (!this.cacheEnabled)
            return;

        ExtentBufferMap bufferMap = this.getExtentBufferMap(result.version);

        //
        // Don't bother with null updates.
        //
        if (result.buffer.isVacuous())
            return;

        //
        // Add the new entry to the cache, being careful to maintain
        // policyInfo entries.
        //
        this.replaceExtents(bufferMap, result.buffer);
        //
        // Add policy information for the buffer.
        //
        // XXX: This information probably ought to be passed in as a parameter
        //      to this method (or enough information to construct it should).
        //
        PolicyInfo info = new PolicyInfo(System.currentTimeMillis());
        this.policyInfo.put(result.buffer, info);
    }

    //
    // An extension of BufferCacheMap.replaceExtents() that ensures that both
    // the extent replacing old ones and fragments of old ones that protrude
    // beyond the replaced range have proper PolicyInfo entries attached to
    // them.
    //
    // Assumes that the caller holds suitable locks.
    //
    private void replaceExtents(ExtentBufferMap map, ExtentBuffer entry) {
        //
        // Avoid the same degenerate case that
        // ExtentBufferMap.replaceExtents() does.
        //
        if (entry.isVacuous())
            return;

        long start = entry.getStartOffset();
        long end = entry.getEndOffset();
        //
        // Look for extents that will be split when replaceExtents() is called
        // below.  That is, look for protrusions.
        //
        ExtentBuffer left = null;
        ExtentBuffer right = null;
        ExtentMap<ExtentBuffer> overlapMap = map.getOverlapping(entry);
        if (overlapMap.size() == 1) {
            ExtentBuffer eb = overlapMap.get(overlapMap.firstKey());
            if (eb.getStartOffset() < start)
                left = eb;
            long ebEnd = eb.getEndOffset();
            if (end != Long.MIN_VALUE &&
                    (end < ebEnd || ebEnd == Long.MIN_VALUE))
                right = eb;
        } else if (overlapMap.size() > 1) {
            ExtentBuffer eb = overlapMap.get(overlapMap.firstKey());
            if (eb.getStartOffset() < start)
                left = eb;
            eb = overlapMap.get(overlapMap.lastKey());
            long ebEnd = eb.getEndOffset();
            if (end != Long.MIN_VALUE &&
                    (end < ebEnd || ebEnd == Long.MIN_VALUE))
                right = eb;
        }

        //
        // Perform the actual replacement and then add PolicyInfo entries as
        // required.  Accommodate the case where entry already has such info,
        // but don't demand it.
        //
        map.replaceExtents(entry);
        long now = System.currentTimeMillis();
        PolicyInfo info = this.policyInfo.get(entry);
        if (info != null) {
            info.setReferenceTime(now);
        } else {
            info = new PolicyInfo(now);
            this.policyInfo.put(entry, info);
        }
        if (left != null) {
            //
            // The extent replacing left shares left's starting offset, so it
            // can be found using that offset.
            //
            ExtentBuffer eb = map.getExtent(left.getStartOffset());
            info = new PolicyInfo(now);
            this.policyInfo.put(eb, info);
        }
        if (right != null) {
            //
            // The extent replacing right shares right's end offset, but not
            // its start offset.  Since right's replacement is known to be
            // non-vacuous, its end offset minus 1 (or Long.MAX_VALUE in the
            // degenerate case) will be in the replacement extent's interior.
            //
            long rightEnd = right.getEndOffset();
            long interiorOffset = (rightEnd == Long.MIN_VALUE) ?
                Long.MAX_VALUE : (rightEnd - 1);
            ExtentBuffer eb = map.getExtent(interiorOffset);
            info = new PolicyInfo(now);
            this.policyInfo.put(eb, info);
        }
    }

    //
    // An extension of BufferCacheMap.intersect() that ensures that all
    // entries in the returned map have proper PolicyInfo entries attached to
    // them.
    //
    // Assumes that the caller holds suitable locks.
    //
    private ExtentBufferMap intersect(ExtentBufferMap map, Extent extent) {
        ExtentBufferMap intersectionMap = map.intersect(extent);
        //
        // Every entry in intersectionMap has a counterpart in map (with the
        // entries at the ends not necessarily having the same bounds as their
        // counterparts).  For each of these entries, transcribe its
        // counterpart's policy info.
        //
        for (long key : intersectionMap.keySet()) {
            ExtentBuffer newBuffer = intersectionMap.get(key);
            ExtentBuffer oldBuffer = map.getExtent(key);
            assert oldBuffer != null : "no buffer for " + key;
            PolicyInfo oldInfo = this.policyInfo.get(oldBuffer);
            assert oldInfo != null :
                String.format(
                    "ExtentBuffer [%d, %d) without associated PolicyInfo",
                    oldBuffer.getStartOffset(), oldBuffer.getEndOffset());
            this.policyInfo.put(newBuffer,
                new PolicyInfo(oldInfo.getReferenceTime()));
        }
        return intersectionMap;
    }

    //
    // Get the ExtentBufferMap for version, creating it if necessary.
    //
    private ExtentBufferMap getExtentBufferMap(BeehiveObjectId version) {
        ExtentBufferMap bufferMap = null;
        synchronized (this.buffersByVersion) {
            bufferMap = this.buffersByVersion.get(version);
            if (bufferMap == null) {
                bufferMap = new ExtentBufferMap();
                this.buffersByVersion.put(version, bufferMap);
            }
        }
        return bufferMap;
    }

    //
    // Update the policy information for the ExtentBuffer covering the given
    // offset of version with referenceTime.
    //
    private void updateReferenceTime(BeehiveObjectId version, long offset,
            long referenceTime) {
        //
        // We synchronize against this (as opposed to something more
        // fine-grained) because we access multiple maps and the accesses must
        // be coordinated.
        //
        // XXX: Perhaps we should simply assume that our caller has done the
        //      requisite synchronization.
        //
        synchronized (this) {
            ExtentBuffer extentBuffer =
                this.getExtentBufferMap(version).getExtent(offset);
            if (extentBuffer == null)
                throw new IllegalStateException(
                    "" + offset + " not represented in map for " +
                    version.toString());
            PolicyInfo info = this.policyInfo.get(extentBuffer);
            if (info == null)
                throw new IllegalStateException(
                    "no policy info for " + extentBuffer.asString() +
                    " (" + extentBuffer + ")");
            info.setReferenceTime(referenceTime);
        }
    }
}
