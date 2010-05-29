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

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;

/**
 * <p>
 *
 * A cache for items of type {@code V} that are accessed via keys of type
 * {@code K}.  The items can be in either an active or inactive state.  When
 * active, they are not contained in the cache.  Upon becoming inactive, they
 * can be placed in the cache, for later retrieval and use when a client needs
 * to obtain and activate (thereby making it ready for use) an object.
 *
 * </p><p>
 *
 * The cache holds all the items with a given key in a set, called the key's
 * <em>entry</em>.  The cache's capacity is calculated in terms of the number
 * of (non-empty) entries it holds, and evictions from the cache remove an
 * entry and all the items it contains.
 *
 * </p><p>
 *
 * The class provides {@code activate()} and {@code deactivate()} methods for
 * activating and deactivating items as they are removed from the cache (or
 * are created initially) and placed in the cache.  The default
 * implementation of {@code activate()} simply indicates success and that of
 * {@code deactivate()} does nothing, but subclasses may override them as
 * appropriate.  Note that these methods are called only as part of the
 * processing for {@code getAndRemove()} and {@code addAndEvictOld()}.  Using
 * other methods to change the cache's contents does not induce calls to these
 * methods.
 *
 * </p><p>
 *
 * Underneath the covers a cache instance keeps track of the items it has
 * handed out (via the {@code getAndRemove()} method) but not yet reclaimed
 * (via the {@code addAndEvictOld()} and {@code disposeItem()} methods}.  If
 * told to do so, via a constructor argument, the cache can allow items
 * currently in use to be reused (by returning them from {@code
 * getAndRemove()}).  However, the default behavior is to impose exclusive use
 * on each item, so that it is available to only one client at a time.  A
 * given item is activated and deactivated only at first use and last
 * relinquish over a span of non-exclusive use.
 *
 * </p><p>
 *
 * The class also provides a {@code disposeItem()} method that is used to
 * clean up items belonging to entries that are evicted from the cache.
 * Eviction results in removing the cache's reference to the evicted entry and
 * its associated items, so that the items potentially become eligible for
 * garbage collection.  (This effect is in contrast to that of {@code
 * getAndRemove()}, which <em>transfers</em> an item reference from the cache
 * to its client.)  Eviction occurs in two circumstances:  when {@code
 * addAndEvictOld()} returns {@code true} and thereby evicts the eldest cache
 * entry and when {@code dispose()} is called to clean up the entire cache in
 * preparation for garbage collection.  Client code may also choose to invoke
 * {@code disposeItem()} directly on an active item; this is appropriate when
 * an item becomes unusable for some reason and should be discarded
 * immediately with no further interaction with the cache.  The cache makes no
 * attempt to forbid a shared item from being disposed; users must coordinate
 * among themselves to ensure sensible behavior.
 *
 * </p><p>
 *
 * This class's implementation of {@code disposeItem()} does nothing.
 * Subclasses that cache objects requiring cleanup (such as calling a {@code
 * close()} method) when an entry is evicted from the cache should override it
 * to do that cleanup.
 *
 * </p><p>
 *
 * The cache can hold multiple objects for each distinct key value; each
 * resides in the set associated with the key's entry.  A consequence of this
 * possibility is that {@code getAndRemove()} can exhibit linear behavior.
 *
 * </p><p>
 *
 * The cache synchronizes modifications made through its {@code getAndRemove()},
 * {@code addAndEvictOld()}, and {@code dispose()} methods.
 * Modifications made through other {@code Map} methods (which are not
 * recommended) require explicit synchronization.
 *
 * </p><p>
 *
 * This class provides a default implementation of the
 * {@link #removeEldestEntry(Map.Entry) removeEldestEntry()}
 * method that monitors
 * whether the cache has exceeded its capacity.  If so, the method calls
 * {@link #disposeItem(Object, Object) disposeItem()}
 * on all the items of the
 * the eldest entry and returns {@code true}.  Subclasses overriding this
 * method should have it call {@code disposeItem()} on all of the entry's
 * items whenever the method returns {@code true}.
 *
 * </p><p>
 *
 * To prevent leakage of cacheable items, the following idiom should be used
 * where feasible:
 *
 * <pre>      V v = null;
 *      K key = ...;
 *      try {
 *          v = cache.getAndRemove(key);
 *          // Use v...
 *      } catch (Exception e)
 *          // Handle getAndRemove() failure...
 *      } finally {
 *          if (v != null)
 *              cache.addAndEvictOld(key, v);
 *      }</pre>
 *
 * </p>
 */
public class LRUCache<K, V> extends LinkedHashMap<K, Set<V>>
        implements LRUCacheMBean {
    /**
     * The {@code Factory} interface packages a method that is invoked when an
     * object is requested from a {@code LRUCache} that contains no entry for
     * a given key.  The method is expected to produce an object suitable for
     * satisfying the request.  The object will be activated before being
     * returned to its requester.
     */
    public interface Factory<K, V> {
        /**
         * Create a new instance of an object that can be placed in a {@code
         * LRUCache} upon deactivation.
         *
         * @param key   the key under which the new object should be stored
         *
         * @return  the newly created object
         *
         * @throws Exception
         *      if object creation failed (classes implementing this interface
         *      will likely refine this exception to be more specific)
         */
        public V newInstance(K key) throws Exception;
    }

    /**
     * {@code ActivationException} is thrown when no object activates
     * successfully during a call to
     * {@link LRUCache#getAndRemove(Object) getAndRemove()}.
     */
    public static class ActivationException extends Exception {
        private final static long serialVersionUID = 1L;
    }

    //
    // The target type for the (revised) activeObjects map.  Calling code is
    // responsible for overall synchronization, but, just to be sure, the
    // count field is protected locally.
    //
    private static class CountedItem<V> {
        public final V  item;
        private int     count;

        public CountedItem(V item) {
            this.item = item;
            //
            // The creator gets a reference; therefore, the starting count
            // value is 1.
            //
            this.count = 1;
        }

        public synchronized void addUse() {
            this.count++;
        }

        //
        // Return true if there are no more active uses of this's item.
        //
        public synchronized boolean relinquishUse() {
            if (this.count > 0)
                this.count--;
            return this.count == 0;
        }
    }

    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    private final static WeakMBeanRegistrar registrar =
        new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    //
    // XXX: Here's a place where the Java type system forces us into lies.
    //      Since this class inherits from LinkedHashMap, which is
    //      Serializable, it must itself be Serializable.  Except that in
    //      reality it's not, because of the factory field immediately below.
    //      Since we can't properly express the truth, we lie and claim that
    //      the factory is transient.
    //
    private final static long               serialVersionUID = 1L;
    private transient final Factory<K, V>   factory;

    //
    // true if each item obtained from the cache can be used by only one
    // client at a time.
    //
    private final boolean                   exclusiveItemUse;

    //
    // The cache's capacity.  This is the maximum number of entries
    // (containing inactive items) the cache can contain.
    //
    private int capacity;

    //
    // A map that tracks objects that the cache has handed out and that are
    // still active.  The value set pairs objects with counts of active users.
    //
    // If exclusiveItemUse is false, then the value for a given key will be a
    // singleton (or empty) with the count of the singleton CountedItem
    // recording how many clients share the item.  But if exclusiveItemUse is
    // true, there can be many things associated with a key, each having a
    // count value of one.
    //
    private Map<K, Set<CountedItem<V>>> activeObjects =
        new HashMap<K, Set<CountedItem<V>>>();

    //
    // The difference in the number of (successful) calls to getAndRemove()
    // and addAndEvictOld().  This is (or ought to be) the sum of the counts
    // in the CountedItems residing in the sets that are the members of the
    // value set for activeObjects.
    //
    private int itemsInUse = 0;

    //
    // The name by which this instance is known to JMX.
    //
    private final ObjectName jmxObjectName;

    //
    // Performance instrumentation counters.
    //
    private int hits = 0;
    private int shareHits = 0;
    private int misses = 0;
    private int evictions = 0;
    private int disposes = 0;

    /**
     * Create a new {@code LRUCache} with the given {@code capacity} and with
     * the given {@code factory} for producing new cacheable items when
     * needed.  The cache will dispense a given item to at most one user at a
     * time.
     *
     * @param capacity  the number of items the cache can hold
     * @param factory   a closure for producing a new cacheable item when a
     *                  request is made that the cache cannot satisfy with the
     *                  items currently in the cache
     */
    public LRUCache(int capacity, Factory<K, V> factory) {
        this(capacity, factory, true, null);
    }

    /**
     * Create a new {@code LRUCache} with the given {@code capacity} and with
     * the given {@code factory} for producing new cacheable objects when
     * needed.  {@code jmxObjectNamePrefix} is the prefix to use in forming
     * the cache's JMX object name.  {@code jmxObjectNamePrefix} may be {@code
     * null}, in which case {@code LRUCache}'s package name is used in its
     * place.  If {@code exclusiveItemUse} is {@code true}, the cache will
     * dispense a given item to at most one user at a time; otherwise, it
     * will, if possible, satisfy requests with already-active items.
     *
     * @param capacity              the number of objects the cache can hold
     * @param factory               a closure for producing a new cacheable
     *                              object when a request is made that the
     *                              cache cannot satisfy with the objects
     *                              currently in the cache
     * @param exclusiveItemUse      {@code true} if outstanding items for a
     *                              given key must all be distinct objects
     * @param jmxObjectNamePrefix   the leading part of the new instance's
     *                              name for JMX management purposes
     */
    public LRUCache(int capacity, Factory<K, V> factory,
            boolean exclusiveItemUse, ObjectName jmxObjectNamePrefix) {
        this(capacity, factory, exclusiveItemUse, jmxObjectNamePrefix, null);
    }

    /**
     * Create a new {@code LRUCache} with the given {@code capacity} and with
     * the given {@code factory} for producing new cacheable objects when
     * needed.  {@code jmxObjectNamePrefix} is the prefix to use in forming
     * the cache's JMX object name.  {@code jmxObjectNamePrefix} may be {@code
     * null}, in which case {@code LRUCache}'s package name is used in its
     * place.  If {@code exclusiveItemUse} is {@code true}, the cache will
     * dispense a given item to at most one user at a time; otherwise, it
     * will, if possible, satisfy requests with already-active items.
     *
     * @param capacity              the number of objects the cache can hold
     * @param factory               a closure for producing a new cacheable
     *                              object when a request is made that the
     *                              cache cannot satisfy with the objects
     *                              currently in the cache
     * @param exclusiveItemUse      {@code true} if outstanding items for a
     *                              given key must all be distinct objects
     * @param jmxObjectNamePrefix   the leading part of the new instance's
     *                              name for JMX management purposes
     *                              
     * @param mbeanInterface        the MBean interface through which the
     *                              MBean server should manage this object; if
     *                              {@code null}, the {@code LRUCacheMBean}
     *                              interface is used
     *
     * @throws ClassCastException
     *      if {@code this} does not implement {@code mbeanInterface}
     */
    protected LRUCache(int capacity, Factory<K, V> factory,
            boolean exclusiveItemUse, ObjectName jmxObjectNamePrefix,
            Class<?> mbeanInterface) {
        super(capacity, (float) 0.75, true);
        this.capacity = capacity;
        this.exclusiveItemUse = exclusiveItemUse;
        this.factory = factory;

        //
        // JMX initialization
        //
        if (mbeanInterface == null)
            mbeanInterface = LRUCacheMBean.class;
        this.jmxObjectName = this.registerMBean(
            mbeanInterface, jmxObjectNamePrefix, null);
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
     * @param mbeanInterface    the MBean interface through which the MBean
     *                          server should access this object
     * @param prefix            a prefix to use in forming the name to be used
     *                          for the server to refer to this object
     * @param disambiguator     a string to use to avoid naming collisions
     *
     * @return  the {@code ObjectName} with which this instance was registered
     *          or {@code null} if registration failed
     *
     * @throws IllegalArgumentException
     *      if {@code mbeanInterface} is null or is not an interface class
     * @throws ClassCastException
     *      if {@code this} is not an instance of {@code mbeanInterface}
     */
    //
    // XXX: Ideally should be a static method of the JMX class, but since it
    //      extracts and uses this's package and class names, it can't be.
    //      (But we could pass this as an additional argument to this method
    //      to remedy that problem.)
    //
    // XXX: Private for now, since it seems to do a good enough job that
    //      subclasses don't themselves need to worry about JMX
    //      instrumentation.  But may well have to become protected, so that
    //      subclasses can take control of their own JMX destinies.  (At that
    //      point, the constructors above will have to be refactored to avoid
    //      registering with an MBean server if a subclass is going to so so.)
    //
    private <T> ObjectName registerMBean(
            Class<T> mbeanInterface,
            ObjectName prefix,
            String disambiguator) {
        //
        // Verify that mbeanInterface and this match up properly.
        //
        if (mbeanInterface == null || !mbeanInterface.isInterface())
            throw new IllegalArgumentException(
                "mbeanInterface argument must be a non-null interface class");
        T thisAsT = mbeanInterface.cast(this);

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
                LRUCache.registrar.registerMBean(
                    jmxObjectName, thisAsT, mbeanInterface);
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
                LRUCache.registrar.registerMBean(
                    jmxObjectName, thisAsT, mbeanInterface);
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

    //
    // LRUCacheMBean methods
    //

    public int getCapacity() {
        return this.capacity;
    }

    public synchronized int getCacheHits() {
        return this.hits;
    }

    public synchronized int getCacheShareHits() {
        return this.shareHits;
    }

    public synchronized int getCacheMisses() {
        return this.misses;
    }

    public synchronized int getCacheEvictions() {
        return this.evictions;
    }

    public synchronized int getItemsDisposed() {
        return this.disposes;
    }

    public synchronized int getCacheActiveCount() {
        return this.itemsInUse;
    }

    public void setCapacity(int capacity) {
        if (capacity < 0)
            throw new IllegalArgumentException(
                "new capacity must be greater non-negative");
        synchronized (this) {
            int oldCapacity = this.capacity;
            this.capacity = capacity;
            if (capacity >= oldCapacity)
                return;

            //
            // Force entries out of the cache until capacity is reached.  The
            // loop considers entries in least to most recently used order.
            //
            for (Map.Entry<K, Set<V>> entry : this.entrySet()) {
                if (!this.removeEldestEntry(entry))
                    break;
            }
        }
    }

    //
    // XXX: Perhaps this method needs to be renamed.
    //
    /**
     * <p>
     *
     * Get an item and return it in an active state.  If the cache permits
     * non-exclusive item use and one matching {@code key} is already in use,
     * return it.  Otherwise attempt to get and activate an inactive item.  If
     * there aren't any in the cache, create and activate a new one.  The
     * newly activated object is removed from the cache.
     *
     * </p><p>
     *
     * If activation is unsuccessful for a candidate item in the cache, it is
     * discarded (via {@link #disposeItem(Object, Object) disposeItem()}) and
     * another is obtained.  If that fails, a new one is created.  If
     * activating such a new item fails, the method throws {@code
     * ActivationException}.
     *
     * <p>
     *
     * @param key   the lookup key used to fetch a cached object
     *
     * @return  the fetched object or {@code null} if no fetched or created
     *          object can be successfully activated
     *
     * @throws ActivationException
     *      if it was not possible to find an object that could be activated
     *      successfully
     * @throws Exception
     *      if a new object needed to be created and its factory threw an
     *      exception
     */
    public V getAndRemove(K key) throws Exception {
        synchronized (this) {
            //
            // Attempt to reuse a non-exclusive active item.
            //
            if (!this.exclusiveItemUse) {
                //
                // See whether the object's already in use.  If so, simply
                // bump its reference count and return it.
                //
                Set<CountedItem<V>> countedItems =
                    this.activeObjects.get(key);
                if (countedItems != null && countedItems.size() != 0) {
                    assert countedItems.size() == 1;
                    CountedItem<V> ct = countedItems.iterator().next();
                    ct.addUse();
                    this.itemsInUse++;
                    this.addShareHit();
                    return ct.item;
                }
            }

            //
            // Attempt to satisfy the request from the cache.
            //
            Set<V> itemsForKey = this.get(key);
            if (itemsForKey != null) {
                if (itemsForKey.size() != 0) {
                    //
                    // The request can potentially be satisfied from the
                    // cache.  Grab an item and attempt to activate it until
                    // there are no more or activation succeeds.
                    //
                    Iterator<V> itemIterator = itemsForKey.iterator();
                    while (itemIterator.hasNext()) {
                        V v = itemIterator.next();
                        itemIterator.remove();
                        //
                        // If the item set for this entry has become empty,
                        // avoid confusing the underlying LinkedHashMap LRU
                        // machinery by removing the entry from the map.
                        // (Note that the loop will terminate in this case.)
                        //
                        if (itemsForKey.size() == 0)
                            this.remove(key);
                        if (this.activate(v)) {
                            this.addHit();
                            this.addCountedItem(key, v);
                            this.itemsInUse++;
                            return v;
                        } else {
                            this.disposeItem(key, v);
                        }
                    }
                } else {
                    //
                    // Avoid confusing the underlying LinkedHashMap LRU
                    // machinery by removing the empty set from the map.
                    //
                    this.remove(key);
                }
            }

            //
            // No existing item satisfies the request.  Make a new one.
            //
            // This LRUCache instance is locked while the factory newInstance() is working.
            V v = this.factory.newInstance(key);
            if (this.activate(v)) {
                this.addMiss();
                this.addCountedItem(key, v);
                this.itemsInUse++;
                return v;
            } else {
                this.disposeItem(key, v);
                throw new ActivationException();
            }
        }
    }

    /**
     * Add an active object to the cache, inactivating it and possibly
     * evicting some other object from the cache.
     *
     * @param key   the object's key
     * @param v     the object to be inactivated and added
     */
    public void addAndEvictOld(K key, V v) {
        synchronized (this) {
            Set<CountedItem<V>> countedItems = this.activeObjects.get(key);
            if (countedItems == null || countedItems.size() == 0)
                throw new IllegalStateException(
                    "no active object with key " + key.toString());

            //
            // Find the item being relinquished.  Linear search, but at least
            // if exclusiveItemUse is false there should only be one item to
            // search.
            //
            Iterator<CountedItem<V>> it = countedItems.iterator();
            while (it.hasNext()) {
                CountedItem<V> ci = it.next();
                if (ci.item != v)
                    continue;

                //
                // XXX: Probably ought to sanity check the resulting value
                //      after the decrement.  It had better remain
                //      non-negative!
                //
                this.itemsInUse--;
                //
                // We're done if there's a use remaining after relinquishing
                // this one.
                //
                if (!ci.relinquishUse())
                    return;

                //
                // Deactivate the item and transfer it from activeObjects to
                // the LRU map.
                //
                this.deactivate(v);
                this.addItemToEntry(key, v);
                it.remove();
                return;
            }
            assert false;   // not reached
        }
    }

    /**
     * Reclaim all resources associated with this cache by invoking
     * {@link #disposeItem(Object, Object) disposeItem()}
     * on every item of every
     * entry in its entry set and removing all items from the map.
     */
    //
    // XXX: What about items in use?  Ideally, there shouldn't be any, but can
    //      that be counted on?
    //
    public synchronized void dispose() {
        Iterator<Map.Entry<K, Set<V>>> it = this.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Set<V>> entry = it.next();
            it.remove();

            K key = entry.getKey();
            for (V v : entry.getValue()) {
                this.disposeItem(key, v);
            }
        }
    }

    /**
     * <p>
     *
     * Determine whether or not the eldest entry in the cache should be
     * removed.  See the documentation for the superclass's
     * {@link java.util.LinkedHashMap#removeEldestEntry(Map.Entry) removeEldestEntry()}
     * method for the specification of this method.  Note
     * that entries consist of sets of cached items associated with the item's
     * key.
     *
     * </p><p>
     *
     * This class's implementation arranges to remove the entry if retaining
     * it would otherwise exceed the map's capacity.  In this case, it invokes
     * {@link #disposeItem(Object, Object) disposeItem()} on all the items
     * associated with the entry's key.  Note that capacity is calculated in
     * terms of the number of keys in the map, rather than the number of items
     * present in the cache.
     *
     * </p><p>
     *
     * Subclasses that override this method should first determine whether the
     * entry is to be removed, and, if so, then invoke {@code disposeItem()}
     * on the entry's items, as described above.
     *
     * </p>
     *
     * @param entry a key-value pair describing the map's eldest entry
     *
     * @return  {@code true} if the entry (and contained cached items)
     *          described by {@code entry} should be removed, {@code false}
     *          otherwise
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, Set<V>> entry) {
        boolean remove = this.size() > this.capacity;
        if (remove) {
            K key = entry.getKey();
            for (V v : entry.getValue()) {
                this.disposeItem(key, v);
            }
        }
        return remove;
    }

    /**
     * <p>
     *
     * Activate an item that either has been removed from the cache or has
     * been just created with the cache's factory.
     *
     * </p><p>
     *
     * This default implementation simply returns success.  Subclasses may
     * override this method to provide whatever activation processing they
     * find to be appropriate.
     *
     * </p>
     *
     * @param v the item to be activated
     *
     * @return  {@code true} if the object was successfully activated and
     *          {@code false} otherwise
     */
    protected boolean activate(V v) {
        return true;
    }

    /**
     * <p>
     *
     * Deactivate an item that is about to be placed in the cache.
     *
     * </p><p>
     *
     * This default implementation does nothing.  Subclasses may override this
     * method to provide whatever deactivation processing they find to be
     * appropriate.
     *
     * </p>
     *
     * @param v the item to be deactivated
     */
    protected void deactivate(V v) {
        //
        // This default implementation does nothing.
        //
    }

    /**
     * <p>
     *
     * Perform cleanup actions on the item {@code v} whose key is {@code key}.
     * The method should leave the item ready for garbage collection.  After
     * invoking it, this cache instance will retain no reference to the
     * obejct.
     *
     * </p><p>
     *
     * This default implementation does nothing other than record that the
     * item is not in active use and update the cache statistics entries for
     * cache evictions and item disposals.  Subclasses may override this
     * method to provide whatever cleanup processing they find to be
     * appropriate, such as invoking {@code close()} or {@code dispose()} on
     * it.  Any such override should call this method as part of its
     * processing.
     *
     * </p>
     *
     * @param key   the item's key
     * @param v     the item to be disposed of
     */
    protected synchronized void disposeItem(K key, V v) {
        this.addEviction();
        this.addItemDisposed();
        this.activeObjects.remove(v);
    }

    //
    // Add an item to an entry in this map.  Assumes the caller holds suitable
    // locks.
    //
    private void addItemToEntry(K key, V v) {
        Set<V> itemsForKey = this.get(key);
        if (itemsForKey == null) {
            itemsForKey = new HashSet<V>();
            this.put(key, itemsForKey);
        }
        itemsForKey.add(v);
    }

    //
    // Add a counted item to the active objects map.  Assumes the caller
    // holds suitable locks.
    //
    private void addCountedItem(K key, V v) {
        Set<CountedItem<V>> countedItems = this.activeObjects.get(key);
        if (countedItems == null) {
            countedItems = new HashSet<CountedItem<V>>();
            this.activeObjects.put(key, countedItems);
        }
        countedItems.add(new CountedItem<V>(v));
    }

    //
    // Methods for updating performance counters.
    //

    private synchronized void addHit() {
        this.hits++;
    }

    private synchronized void addShareHit() {
        this.shareHits++;
    }

    private synchronized void addMiss() {
        this.misses++;
    }

    private synchronized void addEviction() {
        this.evictions++;
    }

    private synchronized void addItemDisposed() {
        this.disposes++;
    }
}
