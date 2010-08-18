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
package sunlabs.titan.node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.titan.node.util.DOLRLogger;

// FUTURE: It has been suggested that Celeste/Beehive may need both SSL and
// plain connections to other nodes, simultaneously. To go further, I think
// you would need
//
//  - a second BeehiveService listening on a different port
//    (one for SSL connections, one for plain connections)
//
//  - a policy for determining which connections to accept on each port
//    (by configurable address range?)
//
//  - a policy for determining which port for outgoing connections
//    (by configurable address range?)
//
//  - use an instance of each ConnectionManager subclass (and therefore
//    two socket caches).
//
//  - modify ConnectionManger to be a concrete class which forwards
//    method calls to the instance of the appropriate subclass.


/**
 * Base class for managing Beehive inter-node connections.
 */
abstract class ConnectionManager {

    /**
     * The maximum number of yet-to-be-accepted incoming
     * server connections to permit.
     * 
     * @see ServerSocket#ServerSocket(int, int)
     */ 
    // Increased this number from 10 to 40, to make SSLSessionCache more
    // stable it also makes sense in the light of many parallel
    // connection attempts, as they can be seen in the Beehive network
    // setup phase.
    protected static int beehiveServerSocketBacklog = 0;

    /**
     * The number of seconds a client connection to a server should
     * wait until it's assumed that the server is not going to respond.
     * 
     * @see java.net.Socket#setSoTimeout
     */
    protected static int beehiveSocketTimeout = 0;

    protected static int beehiveNeighbourSocketCacheSize = 1024;

    /**
     * Return a {@code ServerSocket} for a BeehiveService instance to
     * listen on. The returned socket will have properties set according to
     * the connection type.
     */
    public abstract ServerSocket getServerSocket() throws IOException;
    
    /**
     * Return a {@code ServerSocket} for a BeehiveService instance to
     * listen on. The returned socket will have properties set according to
     * the connection type.
     */
    public abstract ServerSocketChannel getServerSocketChannel() throws IOException;

    /**
     * Accept a connection on the specified server socket and return the
     * new socket. The returned socket will have properties set according
     * to the connection type.
     */
    public abstract Socket accept(ServerSocket serverSocket) throws IOException;

    /**
     * Return true if the socket cache used by this connection manager is
     * empty.
     */
    public abstract boolean isSocketCacheEmpty();

    /**
     * If the socket cache contains a socket bound to the specified node
     * address, remove it from the cache and return it. Otherwise, create a
     * new socket bound to that node address.
     */
    public abstract Socket getAndRemove(NodeAddress addr) throws Exception;

    /**
     * Add the specified socket (assumed to be bound to the specified node
     * address) to the socket cache, removing any old entries if present.
     */
    public abstract void addAndEvictOld(NodeAddress addr, Socket socket);

    /**
     * Drop a socket from the socket cache.
     */
    public abstract void disposeItem(NodeAddress addr, Socket socket);

    /**
     * Log exception messages if deemed interesting by this connection
     * manager.
     */
    public abstract void logException(DOLRLogger log, Exception e, Socket s);

    /**
     * Return an XHTML description of the socket cache.
     */
    public abstract XHTML.EFlow getStatisticsAsXHTML();

    /**
     * Create and return a connection manager of the specified type.
     */
    static ConnectionManager getInstance(String type, NodeAddress nodeAddress, NodeKey nodeKey)
        throws IOException {

        if ("ssl".equalsIgnoreCase(type)) {
            return new SSLConnectionManager(nodeAddress, nodeKey);
        } else if ("plain".equalsIgnoreCase(type)) {
            return new PlainConnectionManager(nodeAddress);
        } else {
            throw new IllegalArgumentException("unknown connection type");
        }
    }
}
