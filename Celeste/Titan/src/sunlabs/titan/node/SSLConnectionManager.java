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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import sunlabs.titan.node.util.DOLRLogger;

/**
 * A manager for SSL connections.
 */
class SSLConnectionManager extends PlainConnectionManager {

    private SSLContext sslContext;

    /**
     * From {@link javax.net.ssl.SSLSessionContext#setSessionCacheSize
     * javax.net.ssl.SSLSessionContext.setSessionCacheSize}:
     * <blockquote>
     * Sets the size of the cache used for storing SSLSession objects grouped
     * under this SSLSessionContext.
     * </blockquote>
     */
    // This influences the size of the volatile SSL cache only.
    // References are weak, and in that cache may be lost due to garbage
    // collection at any time. If references are lost due to size-limitations
    // or expiry in these caches (and not due to the GC), they can not be used
    // by SSL anymore. Thus, make these caches large!
    //
    static int beehiveSSLSessionCacheSize = 1024;
    
    /**
     * The number of seconds for an SSL Session to live.
     */
    // These should be at least a long as the server maximum idle time.
    // The server should continue to reset the time to live when the
    // socket it used.
    static int beehiveServerSSLSessionTimeout = 60 * 60; // 1 hour
    static int beehiveClientSSLSessionTimeout = 60 * 60; // 1 hour

    // This is an added cache that holds strong references. We must
    // avoid the cost of establishing new SSL sessions.  This cache
    // should combine the sizes of both the client and the server-side
    // cache specified above (i.e. 2x the normal session cache size).
    // This should be the maximum number of servers plus the number of
    // client connections to neighbours.
//    private static int defaultRealSSLSessionCacheSize = 2048;


    SSLConnectionManager(NodeAddress nodeAddress, NodeKey nodeKey) throws IOException {

//            if (false) {
//            // Creating this socket before the node is ready to receive 
//            // causes callers to wait.
//            // This deadlocks a node trying to rejoin a network that already
//            // has knowledge of this node, but this node is re-joining.
//            this.serverSocket = (SSLServerSocket)
//                ssl_ssf.createServerSocket(
//                 BeehiveNode.this.getNodeAddress().getInetAddress().getPort(),
//                 serverSocketBacklog, this.inetAddress);
//            this.serverSocket.setNeedClientAuth(true);
//            this.serverSocket.setReuseAddress(true);
//            }
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(nodeKey.getKeyStore(), nodeKey.getKeyPassword());
            KeyManager[] km = kmf.getKeyManagers();

            TrustManager[] tm = new TrustManager[1];
            tm[0] = new NodeX509TrustManager();

            this.sslContext = SSLContext.getInstance("TLS");
            this.sslContext.init(km, tm, null);
            this.sslContext.getServerSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
            this.sslContext.getClientSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
            this.sslContext.getClientSessionContext().setSessionTimeout(SSLConnectionManager.beehiveClientSSLSessionTimeout);
            this.sslContext.getServerSessionContext().setSessionTimeout(SSLConnectionManager.beehiveServerSSLSessionTimeout);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IOException(exception.toString());
        } catch (java.security.KeyStoreException exception) {
            throw new IOException(exception.toString());
        } catch (java.security.KeyManagementException exception){
            throw new IOException(exception.toString());
        } catch (java.security.UnrecoverableKeyException exception){
            throw new IOException(exception.toString());
        }
//        this.sessionCache =
//            new RealSSLSessionCache(Node.defaultRealSSLSessionCacheSize);

        setup(nodeAddress, this.sslContext.getServerSocketFactory(),
            new SSLSocketCache(SSLConnectionManager.beehiveNeighbourSocketCacheSize, this.sslContext, ConnectionManager.beehiveSocketTimeout));
    }

    @Override
    public ServerSocket getServerSocket() throws IOException {
        SSLServerSocket serverSocket = (SSLServerSocket)super.getServerSocket();
        serverSocket.setNeedClientAuth(true);
        serverSocket.setReuseAddress(true);
        return serverSocket;
    }

    @Override
    public Socket accept(ServerSocket serverSocket) throws IOException {
        SSLSocket socket = (SSLSocket) super.accept(serverSocket);
        // No socket reuse is possible.
        // If there was a session reuse, a handshake will
        // nevertheless have to take place. However, cached sockets
        // on the server side are reflected by threads (as
        // compared to an explicit socket cache) on the client side,
        // thus the while the processing loop invoked will stay
        // active as long as this socket is in the
        // server-side cache.
                    
        // We do need to do an explicit SSL handshake here,
        // which will be synchronous,
        // and may cost more or less,
        // depending on whether a session is being resumed or not.
        socket.startHandshake();
        // handshake now done explicitly, since 'getSession()' does
        // hide some errors (by quietly invalidating the socket), without
        // throwing any sort of exception or accessible error message
        return socket;
    }

    @Override
    public void logException(DOLRLogger log, Exception e, Socket s) {
        if (e instanceof SSLPeerUnverifiedException) {
            // we still need to find out why this croaked... :-(
            e.printStackTrace();
            log.info(e.toString() + " " + s);
        }
    }

//     public XHTML.Div SSLCacheToXHTML(String inspect) {
//         return this.sessionCache.toXHTML(
//             this.sslContext.getClientSessionContext(),
//             this.sslContext.getServerSessionContext(),
//             inspect);
//     }
}
