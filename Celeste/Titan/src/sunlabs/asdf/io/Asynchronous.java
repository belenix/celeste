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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.asdf.io;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import sunlabs.asdf.jmx.JMX;

/**
 * Instances of this class are asynchronous channel input servers, each running as a single {@link Thread} accepting incoming connections,
 * creating an instance of {@link ChannelHandler.Factory} for each and looping waiting for I/O events to be raised on each connection
 * and dispatching them to their respective {@code ChannelHandler.Factory} instance.
 */
public class Asynchronous extends Thread implements Runnable, AsynchronousMBean {
    protected ServerSocketChannel server;
    private Selector selector;
    private ChannelHandler.Factory factory;
    private long highWaterMark;
    
    public Asynchronous(ServerSocketChannel serverSocketChannel, ChannelHandler.Factory factory) throws IOException {
        super();
        this.selector = Selector.open();
        this.factory = factory;
        this.server = serverSocketChannel;
        this.server.configureBlocking(false);
        this.highWaterMark = 0;
    }

    public void run() {
        try {
            this.server.register(this.selector, SelectionKey.OP_ACCEPT);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            return;
        }

        while (this.server.isOpen()) {
            try {
                Set<SelectionKey> selected;
                synchronized (this) {
                    selector.select(1000);
                    selected = selector.selectedKeys();
                }
                ArrayList<SelectionKey> selectedList = new ArrayList<SelectionKey>(selected);
                Collections.shuffle(selectedList);
                for (SelectionKey k : selectedList) {
                    if (k.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel) k.channel();
                        SocketChannel socketChannel = channel.accept();

                        SelectionKey selectionKey = null;
                        if (socketChannel != null) {
                            try {
                                socketChannel.configureBlocking(false);
                                selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
                                try {
                                    ChannelHandler handler = this.factory.newChannelHandler(selectionKey);
                                    selectionKey.attach(handler);
                                } catch (ChannelHandler.Exception e) {
                                    e.printStackTrace();
                                    try { socketChannel.close(); } catch (IOException e1) { e1.printStackTrace(); }
                                    if (selectionKey != null) {
                                        selectionKey.cancel();
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                try { socketChannel.close(); } catch (IOException e1) { e1.printStackTrace(); }
                            }
                        }
                        if (this.selector.keys().size() > this.highWaterMark) {
                            this.highWaterMark = this.selector.keys().size();
                        }
                    }

                    ChannelHandler handler = (ChannelHandler) k.attachment();
                    if (handler != null) {
                        handler.setExpirationTime(0);
                        if (k.isWritable()) {
                            handler.networkWrite();
                        }
                        if (k.isReadable()) {
                            handler.networkRead();
                        }

                        if (!k.channel().isOpen()) { // handler must have closed the connection.
                            try { k.channel().close(); } catch (IOException e) { e.printStackTrace(); }
                            k.cancel();
                        }
                        handler.resetExpirationTime(System.currentTimeMillis());
                    }
                }
                
                selected.clear();
                
                // Timeout idle connections here.
                long now = System.currentTimeMillis();

                synchronized (this.selector) {
                    for (SelectionKey key : this.selector.keys()) {
                        if (key.isValid()) {
                            ChannelHandler handler = (ChannelHandler) key.attachment();
                            if (handler != null) {
                                long expirationTime = handler.getExpirationTime();
                                if (expirationTime != 0 && now > expirationTime) {
//                                    System.out.printf("timeout close: %s%n", key);
                                    handler.close();
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {             
            }
        }
        // XXX Cleanup all state.
    }
    
    /**
     * Close and terminate.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        this.server.close();
    }
    
    /**
     * Return the {@link Set} containing the {@link SelectionKey} instances for each open connection.
     * @return The {@link Set} containing the {@link SelectionKey} instances for each open connection.
     */
    public Set<SelectionKey> getConnections() {
        return this.selector.keys();
    }

    public long getJMXConnectionCount() {
        return this.getConnections().size();
    }
    
    public long getJMXConnectionHighWater() {
        return this.highWaterMark;
    }
    
    private static class NodeX509TrustManager implements X509TrustManager {
        NodeX509TrustManager() { 
            //System.out.println("NodeX509TrustManager constructor");
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            //System.out.println("NodeX509TrustManager checkServerTrusted");
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            //System.out.println("NodeX509TrustManager checkclientTrusted");
        }
        public X509Certificate[] getAcceptedIssuers() {
            //System.out.println("NodeX509TrustManager getAcceptedIssuers");
            return new X509Certificate[0];
        }
    }      

    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) {
        long timeoutMillis = 5 * 1000;
        try {
            ServerSocketChannel serverSocketChannel1 = ServerSocketChannel.open();
            serverSocketChannel1.configureBlocking(true);
            serverSocketChannel1.socket().bind(new InetSocketAddress(8082));
            serverSocketChannel1.socket().setReuseAddress(true);
            Asynchronous unsecure = new Asynchronous(serverSocketChannel1, new HTTPChannelHandler.Factory(timeoutMillis));

            Thread a = new Thread(unsecure, "unsecure");
            a.start();
            
            SSLContext sslContext = null;
            String keyStoreName = "celeste.jks";
            String keyStorePassword = "celesteStore";
            String keyPassword = "celesteKey";
            
//            byte[] line = new byte[256];
//            System.out.printf("keyStoreName: '%s'> ", keyStoreName); System.out.flush();
//            int nread = System.in.read(line);
//            if (nread > 1) {
//                keyStoreName = new String(line, 0, nread).trim();
//            }
//
//            System.out.printf("keyStorePassword: '%s'> ", keyStorePassword); System.out.flush();
//            nread = System.in.read(line);
//            if (nread > 1) {
//                keyStorePassword = new String(line, 0, nread).trim();
//            }
//            
//            System.out.printf("keyPassword: '%s'> ", keyPassword); System.out.flush();
//            nread = System.in.read(line);
//            if (nread > 1) {
//                keyPassword = new String(line, 0, nread).trim();
//            }

            System.out.printf("keyStoreName '%s'%n", keyStoreName);
            System.out.printf("keyStorePassword '%s'%n", keyStorePassword);
            System.out.printf("keyPassword '%s'%n", keyStorePassword);
            System.out.printf("Plain connection on port %d%n", 8082);
            System.out.printf("SSL/TLS connection on port %d%n", 8084);
            
            try {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(new BufferedInputStream(new FileInputStream(keyStoreName)), keyStorePassword.toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(keyStore, keyPassword.toCharArray());
                KeyManager[] km = kmf.getKeyManagers();

                TrustManager[] tm = new TrustManager[1];
                tm[0] = new NodeX509TrustManager();

                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(km, tm, null);

                //
                // Test this service with:  openssl s_client -debug -connect 127.0.0.1:8084
                //
                ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();
                serverSocketChannel2.socket().bind(new InetSocketAddress(8084));
                serverSocketChannel2.socket().setReuseAddress(true);

                Asynchronous secure = new Asynchronous(serverSocketChannel2, new SSLEchoChannelHandler.Factory(sslContext, timeoutMillis));

                ObjectName jmxObjectName = JMX.objectName("com.sun.sunlabs.asdf", "Asynchronous");
                ManagementFactory.getPlatformMBeanServer().registerMBean(secure, jmxObjectName);

                Thread b = new Thread(secure, "secure");
                b.start();                

            } catch (GeneralSecurityException ex) {
                throw new IOException(ex);
            } catch (JMException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }            

            a.join();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

        }
    }

}
