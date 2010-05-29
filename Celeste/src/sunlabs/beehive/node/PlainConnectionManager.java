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
package sunlabs.beehive.node;

import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;

import javax.net.ServerSocketFactory;

import sunlabs.asdf.web.XML.XHTML.EFlow;
import sunlabs.beehive.node.util.DOLRLogger;


/**
 * A manager for plain connections.
 */
class PlainConnectionManager extends ConnectionManager {

    private NodeAddress nodeAddress;
    private InetAddress inetAddress;
    private ServerSocketFactory serverSocketFactory;
    private SocketCache sockets;         // a cache of idle sockets

    /**
     * No arg constructor for superclasses (which should call setup() to
     * complete initialization).
     */
    protected PlainConnectionManager() {
    }

    /**
     * Create a connection manager which manages plain sockets.
     *
     * @param nodeAddress provides IP address and port to which a server
     * socket returned by {@link #getServerSocket} will be bound.
     */
    PlainConnectionManager(NodeAddress nodeAddress) throws IOException {

        this.setup(nodeAddress, ServerSocketFactory.getDefault(),
            new SocketCache(ConnectionManager.beehiveNeighbourSocketCacheSize, ConnectionManager.beehiveSocketTimeout));
    }

    /**
     * Specify the node address, socket factory and socket cache this
     * instance will use. This method should be called by constructors.
     */
    protected void setup(NodeAddress nodeAddress, ServerSocketFactory serverSocketFactory, SocketCache socketCache) {
        this.nodeAddress = nodeAddress;
        this.serverSocketFactory = serverSocketFactory;
        this.sockets = socketCache;
    }

    @Override
    public ServerSocket getServerSocket() throws IOException {
        return this.serverSocketFactory.createServerSocket(this.nodeAddress.getBeehivePort(), ConnectionManager.beehiveServerSocketBacklog, this.inetAddress);
    }

    @Override
    public ServerSocketChannel getServerSocketChannel() throws IOException {
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.socket().setReuseAddress(true);
        ss.socket().bind(this.nodeAddress.getInternetworkAddress());
        ss.configureBlocking(false);
        return ss;
    }

    @Override
    public Socket accept(ServerSocket serverSocket) throws IOException {
        Socket socket = serverSocket.accept();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        return socket;
    }

    @Override
    public boolean isSocketCacheEmpty() {
        return this.sockets.size() == 0;
    }

    @Override
    public Socket getAndRemove(NodeAddress addr) throws Exception {
        Socket result = this.sockets.getAndRemove(addr);
        return result;
    }

    @Override
    public void addAndEvictOld(NodeAddress addr, Socket socket) {
        this.sockets.addAndEvictOld(addr, socket);
    }

    @Override
    public void disposeItem(NodeAddress addr, Socket socket) {
        this.sockets.disposeItem(addr, socket);
    }

    @Override
    public EFlow getStatisticsAsXHTML() {
        return this.sockets.getStatisticsAsXHTML();
    }

    @Override
    public void logException(DOLRLogger log, Exception e, Socket s) {
        // Nothing interesting to log.
    }
}
