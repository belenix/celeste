/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle, 16 Network Circle, MenloPark, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.titan.node;

import java.io.IOException;

import java.net.Socket;

import javax.net.SocketFactory;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XHTML.Table;
import sunlabs.titan.util.LRUCache;

/**
 * Provides LRU caches of {@code Socket}s that are keyed by the IP address
 * and port number.
 *
 */
public class SocketCache extends LRUCache<SocketCache.Key, Socket> {
    private final static long serialVersionUID = 0L;

    /**
     * An immutable tuple class that uses a NodeAddress
     * to form a {@code SocketCache} lookup key.
     */
    public static class Key {
        public final NodeAddress address;

        public Key(NodeAddress address) {
            this.address = address;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key))
                return false;
            Key otherKey = (Key) other;
            return this.address.equals(otherKey.address);
        }

        @Override
        public int hashCode() {
            return this.address.hashCode();
        }
    }

    //
    // Factory class for creating a Socket when the cache doesn't have a
    // suitable one.
    //
    protected static class Factory implements LRUCache.Factory<Key, Socket> {
        private final SocketFactory socketFactory;
        private final int socketTimeOutInMs;  // unit is milliseconds

        /**
         * @param timeoutInSeconds   the timeout, in <em>seconds</em>, to set for
         *                  newly created sockets (see {@link Socket#setSoTimeout(int)}
         *
         */
        public Factory(int timeoutInSeconds) {
            this(SocketFactory.getDefault(), timeoutInSeconds);
        }

        protected Factory(SocketFactory socketFactory, int timeoutInSeconds) {
            this.socketFactory = socketFactory;
            this.socketTimeOutInMs = timeoutInSeconds * 1000;
        }

        public Socket newInstance(Key key) throws IOException {
            Socket s = socketFactory.createSocket(key.address.getMessageURL().getHost(), key.address.getMessageURL().getPort());
            s.setSoTimeout(this.socketTimeOutInMs);
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);
            return s;
        }
    }

    private final static int    defaultCapacity = 16;

    /**
     * Create an LRU cache capable of holding {@code capacity} sockets
     * that share the socket {@code timeout}.
     *
     * @param capacity  the maximum number of sockets the cache can hold
     */
    protected SocketCache(int capacity, Factory factory) {
        super(capacity, factory);
    }

    /**
     * Create an LRU cache capable of holding {@code capacity} sockets
     * that share the socket {@code timeout}.
     *
     * @param capacity  the maximum number of sockets the cache can hold
     * @param timeout   the timeout, in seconds, to set for newly created
     *                  sockets
     */
    public SocketCache(int capacity, int timeout) {
        this(capacity, new Factory(timeout));
    }

    /**
     * Create an LRU cache with a default capacity that holds sockets that
     * share the socket {@code timeout}.
     *
     * @param timeout   the timeout, in seconds, to set for newly created
     *                  sockets
     */
    public SocketCache(int timeout) {
        this(SocketCache.defaultCapacity, timeout);
    }

    /**
     * A convenience version of {@link LRUCache#getAndRemove(Object)
     * getAndRemove()} that packages its arguments to form a suitable lookup
     * key.
     */
    public Socket getAndRemove(NodeAddress address)
    throws Exception {
        Key key = new Key(address);
        return this.getAndRemove(key);
    }

    /**
     * A convenience version of {@link LRUCache#addAndEvictOld(Object, Object)
     * addAndEvictOld()} that packages its arguments to form a suitable lookup
     * key.
     */
    public void addAndEvictOld(NodeAddress address, Socket s) {
        Key key = new Key(address);
        this.addAndEvictOld(key, s);
    }

    /**
     * Override the superclass's behavior for {@code activate()} by verifying
     * that the socket to be activated is still in a usable state.
     *
     * @param s the socket to activate
     *
     * @return  {@code true} if the socket is in a usable state and {@code
     *          false} otherwise
     */
    @Override
    protected boolean activate(Socket s) {
        //
        // Make sure the socket is still in a usable state.
        //
        if (s.isClosed() || s.isInputShutdown() || s.isOutputShutdown()) {
//            System.out.printf(
//                "SocketCache: socket for %s closed or shut down%n",
//                s.getInetAddress().toString());
            return false;
        }
        return true;
    }

    /**
     * Augment the superclass's behavior for {@code disposeItem()} by
     * closing the socket.
     *
     * @param key   the key the socket was stored under
     * @param s     the socket to be disposed of
     */
    @Override
    protected void disposeItem(Key key, Socket s) {
        try {
            s.close();
        } catch (IOException ignore) {
        }
        super.disposeItem(key, s);
    }

    /**
     * A convenience version of {@link #disposeItem(Key, Socket)} that
     * packages its arguments to form a suitable lookup key.
     */
    public void disposeItem(NodeAddress address, Socket s) {
        Key key = new Key(address);
        this.disposeItem(key, s);
    }

    /**
     * Return an {@link sunlabs.asdf.web.xml XHTML.Eflow} element whose contents describe this cache's performance.
     *
     * @return an {@link sunlabs.asdf.web.xml XHTML.Eflow} element whose contents describe this cache's performance.
     */
    public XHTML.EFlow getStatisticsAsXHTML() {
        return getStatisticsAsXHTML("Socket Cache");
    }

    /**
     * Return an XHTML table whose contents describe this cache's performance.
     *
     * @return an XHTML table of performance statistics
     */
    protected XHTML.EFlow getStatisticsAsXHTML(String caption) {
        int capacity = this.getCapacity();
        int evictions = this.getCacheEvictions();
        int hits = this.getCacheHits();
        int misses = this.getCacheMisses();
        int active = this.getCacheActiveCount();
        int accesses = hits + misses;
        int performance = (accesses == 0) ? 0 : hits * 100 / accesses;

        //
        // Build the table, adding a class attribute to each cell in the right
        // column identifying that cell as containing an integer statistic (so
        // that the cell can be properly formatted).
        //
        return new Table(new Table.Caption(caption),
                new Table.Body(
                        new Table.Row(new Table.Data("Total Sockets Requested"), new Table.Data(accesses)),
                        new Table.Row(new Table.Data("Total Cache Hits"), new Table.Data(hits)),
                        new Table.Row(new Table.Data("Cache Performance"), new Table.Data("%d%%", performance)),
                        new Table.Row(new Table.Data("Maximum Cache Size"), new Table.Data(capacity)),
                        new Table.Row(new Table.Data("Current Cache Size"), new Table.Data(this.size())),
                        new Table.Row(new Table.Data("Evictions from Cache"), new Table.Data(evictions)),
                        new Table.Row(new Table.Data("Sockets in Use"), new Table.Data(active))
                )
        ).setClass("socket-cache");
    }
}
