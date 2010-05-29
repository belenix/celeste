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
package sunlabs.beehive.node;

import java.security.PublicKey;

import java.io.IOException;

import java.net.Socket;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.node.util.DOLRLogFormatter;

/**
 * Provides LRU caches of {@code SSLSocket}s that are keyed by the IP address
 * and port number.
 * 
 */
public final class SSLSocketCache extends SocketCache {

    private final static long serialVersionUID = 0L;

    //
    // Factory class for creating an SSLSocket when the cache doesn't have a
    // suitable one.
    //
    private static class Factory extends SocketCache.Factory {

        /**
         * @param timeoutInSeconds   the timeout, in <em>seconds</em>, to set for
         *                  newly created sockets (see {@link SSLSocket#setSoTimeout(int)}
         *                  
         */
        public Factory(SSLContext context, int timeoutInSeconds) {
            super(context.getSocketFactory(), timeoutInSeconds);
        }

        @Override
        public SSLSocket newInstance(Key key) throws IOException {
            SSLSocket s = (SSLSocket)super.newInstance(key);
            s.startHandshake(); // synchronous for the initial handshake.

            if (true) {
                // A Node's object-id is a hash of the Node's SSL/TLS public
                // key. If a node that we previously knew about (it was in the
                // neighbour map) disappeared, we may not know that it
                // disappeared until now when, as we attempt to connect to it,
                // we discover that the object-id we are expecting does not
                // match the object-id as computed from the public key.
                // In this case we need to return a failure.
                SSLSession sslSession = s.getSession();
                java.security.cert.Certificate[] certificates = sslSession.getPeerCertificates();

                // Only the first certificate is the peer certificate.
                // The rest are certificate authorities. See the javadoc for getPeerCertificate().

                BeehiveObjectId peerNodeId = new BeehiveObjectId(certificates[0].getPublicKey());
                if (!key.address.getObjectId().equals(peerNodeId)) {
                    PublicKey publicKey = certificates[0].getPublicKey();
                    StringBuilder logMessage = new StringBuilder("Warning: Node object-id does not validate from SSL public-key: expected ")
                        .append(key.address.getObjectId())
                        .append(" but computed:\n   ")
                        .append(peerNodeId)
                        .append(" from key:\n")
                        .append(DOLRLogFormatter.prettyPrint("   ", "%03o ", publicKey.getEncoded(), 32))
                        .append(certificates[0].toString());
                    System.err.println(logMessage.toString());
                    System.err.flush();
                    return s;
                }
            }
            
            return s;
        }
    }

    private final static int    defaultCapacity = 16;

    /**
     * Create an LRU cache capable of holding {@code capacity} SSL sockets
     * that share the given SSL {@code context} and socket {@code timeout}.
     *
     * @param capacity  the maximum number of sockets the cache can hold
     * @param context   the {@code SSLContext} that the cache uses when it
     *                  needs to create a new socket
     *                  
     * @param timeout   the timeout, in seconds, to set for newly created
     *                  sockets
     */
    public SSLSocketCache(int capacity, SSLContext context, int timeout) {
        super(capacity, new Factory(context, timeout));
    }

    /**
     * Create an LRU cache with a default capacity that holds SSL sockets that
     * share the given SSL {@code context} and socket {@code timeout}.
     *
     * @param context   the {@code SSLContext} that the cache uses when it
     *                  needs to create a new socket
     */
    public SSLSocketCache(SSLContext context, int timeout) {
        this(SSLSocketCache.defaultCapacity, context, timeout);
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
        if (s == null) {
            System.err.printf("SSLSocketCache.activate(): trying to use a null Socket%n");
            return false;
        }
        
        if (!super.activate(s)) {
            return false;
        }
        SSLSession session = ((SSLSocket)s).getSession();
        if (!session.isValid()) {
//            System.out.printf("SSLSocketCache.activate: session invalidated %s while it waited in the cache.%n", s.toString());
            return false;
        }

        return true;
    }

    @Override
    public XHTML.EFlow getStatisticsAsXHTML() {
        return getStatisticsAsXHTML("SSL Socket Cache");
    }
    
    public static void main(String[] args) throws NumberFormatException, Exception {

        String keyStore = "celeste.jks";
        
        try {
            NodeKey nodeKey = new NodeKey(keyStore, "10.0.1.2", 12001);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(nodeKey.getKeyStore(), nodeKey.getKeyPassword());
            KeyManager[] km = kmf.getKeyManagers();

            TrustManager[] tm = new TrustManager[1];
            tm[0] = new NodeX509TrustManager();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(km, tm, null);
            sslContext.getServerSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
            sslContext.getClientSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
            sslContext.getClientSessionContext().setSessionTimeout(SSLConnectionManager.beehiveClientSSLSessionTimeout);
            sslContext.getServerSessionContext().setSessionTimeout(SSLConnectionManager.beehiveServerSSLSessionTimeout);
            
            SSLSocketCache cache = new SSLSocketCache(sslContext, 5000);

            Socket socket = cache.getAndRemove(new NodeAddress(new BeehiveObjectId(), "127.0.0.1", 8084, 9999));
            
            byte[] message = "Hello World".getBytes();
            socket.getOutputStream().write(message, 0, message.length);

            byte[] buf = new byte[100];
            while (true) {
                int nread = socket.getInputStream().read(buf, 0, buf.length);
                System.out.write(buf, 0, nread);
            }

        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IOException(exception.toString());
        } catch (java.security.KeyStoreException exception) {
            throw new IOException(exception.toString());
        } catch (java.security.KeyManagementException exception){
            throw new IOException(exception.toString());
        } catch (java.security.UnrecoverableKeyException exception){
            throw new IOException(exception.toString());
        }
    }
}
