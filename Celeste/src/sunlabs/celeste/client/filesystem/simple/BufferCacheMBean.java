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

/**
 * A collection of attributes giving statistics pertinent to {@code
 * BufferCache} operations.  Also includes an operation for flushing the
 * cache.
 */
public interface BufferCacheMBean {

    /**
     * Return the total number of reads issued against this {@code
     * BufferCache} instance.
     *
     * @return  the total number of reads
     */
    public long getReads();

    /**
     * Return the aggregate number of reads issued against all {@code
     * BufferCache} instances.
     *
     * @return  the total number of reads for all instances
     */
    public long getAggregateReads();

    /**
     * Return the total number of asynchronous reads issued against this
     * {@code BufferCache} instance.
     *
     * @return  the total number of asynchronous reads
     */
    public long getAsyncReads();

    /**
     * Return the aggregate number of asynchronous reads issued against all
     * {@code BufferCache} instances.
     *
     * @return  the total number of asynchronous reads for all instances
     */
    public long getAggregateAsyncReads();

    /**
     * Return the total number of reads issued against this {@code
     * BufferCache} instance that were statisfied from the cache.
     *
     * @return  the total number of reads satisfied from the cache
     */
    public long getCached();

    /**
     * Return the aggregate number of reads issued against all {@code
     * BufferCache} instances that were satisfied from the cache.
     *
     * @return  the total number of reads for all instances satisfied from the
     *          cache
     */
    public long getAggregateCached();

    /**
     * Return the total number of writes issued against this {@code
     * BufferCache} instance.
     *
     * @return  the total number of writes
     */
    public long getWrites();

    /**
     * Return the aggregate number of writes issued against all {@code
     * BufferCache} instances.
     *
     * @return  the total number of writes for all instances
     */
    public long getAggregateWrites();

    /**
     * Return the total number of truncates issued against this {@code
     * BufferCache} instance.
     *
     * @return  the total number of truncates
     */
    public long getTruncates();

    /**
     * Return the aggregate number of truncates issued against all {@code
     * BufferCache} instances.
     *
     * @return  the total number of truncates for all instances
     */
    public long getAggregateTruncates();

    /**
     * Return the total number of attribute update issued against this {@code
     * BufferCache} instance.
     *
     * @return  the total number of attribute updates
     */
    public long getAttrUpdates();

    /**
     * Return the aggregate number of attribute updates issued against all
     * {@code BufferCache} instances.
     *
     * @return  the total number of attribute updates for all instances
     */
    public long getAggregateAttrUpdates();

    /**
     * Return the total number of bytes read through this {@code BufferCache}
     * instance.
     *
     * @return  the total number of bytes read
     */
    public long getBytesRead();

    /**
     * Return the aggregate number of bytes read from all {@code BufferCache}
     * instances.
     *
     * @return  the total number of bytes read for all instances
     */
    public long getAggregateBytesRead();

    /**
     * Return the total number of bytes read directly from this {@code
     * BufferCache} instance.
     *
     * @return  the total number of bytes read directly from the cache
     */
    public long getBytesCached();

    /**
     * Return the aggregate number of bytes read directly from the cache for
     * all {@code BufferCache} instances.
     *
     * @return  the total number of bytes read for all instances directly from
     *          the cache
     */
    public long getAggregateBytesCached();

    /**
     * Return the total number of bytes written through this {@code
     * BufferCache} instance.
     *
     * @return  the total number of bytes written
     */
    public long getBytesWritten();

    /**
     * Return the aggregate number of bytes writtenthrough all {@code
     * BufferCache} instances.
     *
     * @return  the total number of bytes written for all instances
     */
    public long getAggregateBytesWritten();

    /**
     * Discard all cached data from this {@code BufferCache} instance.
     */
    public void flush();

    /**
     * Discard cached data from this {@code BufferCache} instance.  If {@code
     * retainCurrentVersion} is {@code true} retain data cached for the file
     * version that's current at the time of the call; otherwise, discard all
     * data.
     *
     * @param retainCurrentVersion  if {@code true}, retain data cached for
     *                              the current version while discarding data
     *                              belonging only to previous versions;
     *                              otherwise, discard all cached data
     */
    public void flush(boolean retainCurrentVersion);

    /**
     * Reports whether or not read, write, and truncate operations will add
     * new data to the cache.
     *
     * @return  {@code true} if i/o operations will add new data to the cache
     *          and {@code false} otherwise
     */
    public boolean isCacheEnabled();

    /**
     * Controls whether or not read, write, and truncate operations will add
     * new data to the cache.
     *
     * @param enable    {@code true} to enable adding new data to the cache
     *                  and {@code false} to prevent new data from being added
     */
    public void setCacheEnabled(boolean enable);
}
