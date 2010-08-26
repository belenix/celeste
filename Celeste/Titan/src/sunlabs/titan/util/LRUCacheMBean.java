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
package sunlabs.titan.util;


/**
 * The JMX management interface for the {@code LRUCache} class.
 */
public interface LRUCacheMBean {
    /**
     * Return this cache's capacity
     *
     * @return this cache's capacity
     */
    public int getCapacity();

    /**
     * Return the number of cache hits.
     *
     * @return  the number of times {@code getAndRemove()} found an entry in
     *          the cache
     */
    public int getCacheHits();

    /**
     * Return the number of hits obtained by reusing an already active item.
     *
     * @return  the number of times {@code getAndRemove()} found an active
     *          item that could be reused
     */
    public int getCacheShareHits();

    /**
     * Return the number of cache misses.
     *
     * @return  the number of times {@code getAndRemove()} had to call its
     *          factory to create a new entry
     */
    public int getCacheMisses();

    /**
     * Return the number of entries evicted from the cache.
     *
     * @return  the number of times {@code disposeItem()} has been called
     */
    public int getCacheEvictions();

    /**
     * Return the number of times {@link LRUCache#disposeItem(Object, Object)
     * disposeItem()} has been called for items associated with this cache,
     * either explicitly or as part of the processing for one of the other
     * {@code LRUCache} methods.
     *
     * @return  the number of {@code disposeItem()} calls.
     */
    public int getItemsDisposed();

    /**
     * Return the number of active entities (ones that have been fetched from
     * the cache but not yet returned to it).
     *
     * @return  the number of currently active entities
     */
    public int getCacheActiveCount();

    /**
     * Set the cache's capacity to the value given by the argument.  If the
     * new capacity is less than the old, remove the cache's eldest entries
     * (by calling {@link
     * sunlabs.titan.util.LRUCache#removeEldestEntry(java.util.Map.Entry)
     * removeEldestEntry()}) until the number of remaining entries does not
     * exceed the capacity.
     *
     * @param capacity  the cache's new capacity
     *
     * @throws IllegalArgumentException
     *      if {@code capacity} is a negative value
     */
    public void setCapacity(int capacity);
}
