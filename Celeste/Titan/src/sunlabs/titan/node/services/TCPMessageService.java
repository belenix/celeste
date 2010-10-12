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
package sunlabs.titan.node.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ConcurrentModificationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import sunlabs.asdf.io.ChannelHandler;
import sunlabs.asdf.io.UnsecureChannelHandler;
import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.jmx.ServerSocketMBean;
import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Attributes.Prototype;
import sunlabs.asdf.util.Time;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.SSLSocketCache;
import sunlabs.titan.node.SocketCache;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.TitanNodeImpl.ConfigurationException;
import sunlabs.titan.node.services.api.MessageService;

public class TCPMessageService extends AbstractTitanService implements MessageService {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(TCPMessageService.class, TCPMessageService.serialVersionUID);
    
    /**  */
    private final static Attributes.Prototype SocketCacheSize = new Attributes.Prototype(TCPMessageService.class, "SocketCacheSize",
            1024,
            "The inter-node connection type. Either 'plain' or 'ssl'.");
    /** The number of seconds a client connection to a server will wait before it times-out.
     * 
     * @see java.net.Socket#setSoTimeout
     */
    private final static Attributes.Prototype SocketTimeoutSeconds = new Attributes.Prototype(TCPMessageService.class, "SocketTimeoutSeconds",
            0,
            "The number of seconds a client connection to a server will wait before it times-out.");
    
    /** The inter-node connection type. Either 'plain' or 'ssl'. */
    public final static Attributes.Prototype ConnectionType = new Attributes.Prototype(TCPMessageService.class, "ConnectionType",
            "ssl", // this must match up with the debugging conditions in transmit().
            "The inter-node connection type. Either 'plain' or 'ssl'.");
    
    /** The maximum number of of simultaneous neighbour connections. */
    public final static Attributes.Prototype ClientMaximum = new Attributes.Prototype(TCPMessageService.class, "ClientMaximum",
            20,
            "The maximum number of of simultaneous neighbour connections.");
    
    /** The maximum number of seconds a client connection can be idle before it is considered unused and can be closed. */
    public final static Attributes.Prototype ClientTimeoutSeconds = new Attributes.Prototype(TCPMessageService.class, "ClientTimeoutSeconds",
            Time.minutesInSeconds(11),
            "The maximum number of seconds a client connection can be idle before it is considered unused and can be closed.");    

    private Connector connection;
    private ThreadPoolExecutor executor;
    
    public TCPMessageService(TitanNode node) throws JMException, IOException, ConfigurationException {
        super(node, TCPMessageService.name, "Titan Message Transceiver");

        node.getConfiguration().add(TCPMessageService.ConnectionType);
        node.getConfiguration().add(TCPMessageService.SocketCacheSize);
        node.getConfiguration().add(TCPMessageService.SocketTimeoutSeconds);
        node.getConfiguration().add(TCPMessageService.ClientMaximum);
        node.getConfiguration().add(TCPMessageService.ClientTimeoutSeconds);

        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(TCPMessageService.this.node.getConfiguration().asInt(TCPMessageService.ClientMaximum),
                new SimpleThreadFactory(TCPMessageService.this.node.getThreadGroup().getName()));

        if (this.node.getConfiguration().asString(TCPMessageService.ConnectionType).equalsIgnoreCase("plain")) {
            this.connection = new PlainConnector(this, new PlainChannelHandler.Factory(this.node));
        } else if (this.node.getConfiguration().asString(TCPMessageService.ConnectionType).equalsIgnoreCase("ssl")) {
            this.connection = new SSLConnector(this);
        } else {
            node.getLogger().config("Misconfigured %s.  Must be either 'plain' or 'ssl'", node.getConfiguration().get(TCPMessageService.ConnectionType));
            throw new TitanNodeImpl.ConfigurationException("Misconfigured %s.  Must be either 'plain' or 'ssl'",
                    node.getConfiguration().get(TCPMessageService.ConnectionType));
        }

        node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.ConnectionType));
        node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.SocketCacheSize));
        node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.SocketTimeoutSeconds));
        node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.ClientMaximum));
        node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.ClientTimeoutSeconds));
    }
    
    /**
     * Overrides of this method must protect themselves if start() is called
     * multiple times, and if it is called by two threads at the same time.
     */
    @Override
    public synchronized void start() {
        if (this.isStarted()) {
            return;
        }
        super.start();
        
        Thread t = (Thread) this.connection;
        if (!t.isAlive()) {
            t.start();
        }
    }
    
    public Thread getServerThread() {
        return (Thread) this.connection;
    }
    
    private interface Connector {
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
    }
    
    private class SimpleThreadFactory implements ThreadFactory {
        private String name;
        private long counter;

        public SimpleThreadFactory(String name) {
            this.name = name;
            this.counter = 0;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(String.format("%s-titan-%d", this.name, this.counter));
            this.counter++;
            return thread;
        }
    }
   
    
    public static class PlainChannelHandler extends UnsecureChannelHandler implements ChannelHandler {
        public static final Prototype ApplicationInputSize = new Attributes.Prototype(PlainConnector.class, "ApplicationInputSize",
                4096,
        "The buffer size of the 'plain' connection network input buffer.");;

        public static final Prototype ApplicationOutputSize = new Attributes.Prototype(PlainConnector.class, "ApplicationOutputSize",
                4096,
        "The buffer size of the 'plain' connection application output buffer.");;

        public static final Prototype NetworkOutputSize = new Attributes.Prototype(PlainConnector.class, "NetworkOutputSize",
                4096,
        "The buffer size of the 'plain' connection network output buffer.");;

        public static final Prototype NetworkInputSize = new Attributes.Prototype(PlainConnector.class, "NetworkInputSize",
                4096,
        "The buffer size of the 'plain' connection network input buffer.");

        public static class Factory implements ChannelHandler.Factory {
            private TitanNode node;
            private long timeoutMillis;
            private int networkInputSize;
            private int networkOutputSize;
            private int applicationOutputSize;
            private int applicationInputSize;

            public Factory(TitanNode node) {
                this.node = node;

                node.getConfiguration().add(PlainChannelHandler.NetworkInputSize);
                node.getConfiguration().add(PlainChannelHandler.NetworkOutputSize);
                node.getConfiguration().add(PlainChannelHandler.ApplicationOutputSize);
                node.getConfiguration().add(PlainChannelHandler.ApplicationInputSize);

                this.timeoutMillis = Time.secondsInMilliseconds(this.node.getConfiguration().asInt(TCPMessageService.ClientTimeoutSeconds));
                this.networkInputSize = this.node.getConfiguration().asInt(PlainChannelHandler.NetworkInputSize);
                this.networkOutputSize = this.node.getConfiguration().asInt(PlainChannelHandler.NetworkOutputSize);
                this.applicationOutputSize = this.node.getConfiguration().asInt(PlainChannelHandler.ApplicationOutputSize);
                this.applicationInputSize = this.node.getConfiguration().asInt(PlainChannelHandler.ApplicationInputSize);

                node.getLogger().config("%s", node.getConfiguration().get(TCPMessageService.ClientTimeoutSeconds));
                node.getLogger().config("%s", node.getConfiguration().get(PlainChannelHandler.NetworkInputSize));
                node.getLogger().config("%s", node.getConfiguration().get(PlainChannelHandler.NetworkOutputSize));
                node.getLogger().config("%s", node.getConfiguration().get(PlainChannelHandler.ApplicationOutputSize));
                node.getLogger().config("%s", node.getConfiguration().get(PlainChannelHandler.ApplicationInputSize));
            }

            public ChannelHandler newChannelHandler(SelectionKey selectionKey) {
                return new PlainChannelHandler(this.node, selectionKey, this.networkInputSize, this.networkOutputSize, this.applicationInputSize, this.applicationOutputSize, this.timeoutMillis);
            }            
        }

        private static class RequestHandler implements Runnable {
            private TitanMessage request;
            private ChannelHandler channel;
            private TitanNode node;

            public RequestHandler(TitanNode node, TitanMessage request, ChannelHandler channel) {
                this.node = node;
                this.request = request;
                this.channel = channel;                
            }

            public void run() {
                try {
                    if (this.node.getLogger().isLoggable(Level.FINEST)) {
                        this.node.getLogger().finest("Request: %s", request);
                    }

                    TitanMessage myResponse = this.node.receive(this.request);

                    if (this.node.getLogger().isLoggable(Level.FINEST)) {
                        this.node.getLogger().finest("Response: %s", myResponse);
                    }

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    myResponse.writeObject(dos);
                    dos.flush();
                    this.channel.output(ByteBuffer.wrap(bos.toByteArray()));
                } catch (ClosedChannelException e) {

                } catch (EOFException exception) {
                    if (this.node.getLogger().isLoggable(Level.FINE)) {
                        this.node.getLogger().fine("Closed by client.%n");
                    }
                    // ignore this exception, just abandon this connection,
                } catch (IOException exception) {
                    if (this.node.getLogger().isLoggable(Level.WARNING)) {
                        this.node.getLogger().warning("%s", exception);
                    }
                    // ignore this exception, just abandon this connection,
                    //                } catch (Exception e) {
                    //                    if (this.node.getLogger().isLoggable(Level.WARNING)) {
                    //                        this.node.getLogger().warning("%s", e);
                    //                        e.printStackTrace();
                    //                    }
                } finally {
                    // Done with this Channel, so set it back up for a timeout.
                    this.channel.resetExpirationTime(System.currentTimeMillis());
                }
            }
        }

        private enum ParserState {
            READ_HEADER_LENGTH,
            READ_HEADER,
            READ_PAYLOAD_LENGTH,
            READ_PAYLOAD,            
        };
        private ParserState state;
        private TitanNode node;
        private ByteBuffer header;
        private ByteBuffer payload;

        public PlainChannelHandler(TitanNode node, SelectionKey selectionKey, int networkInputSize, int networkOutputSize, int applicationInputSize, int applicationOutputSize, long timeoutMillis) {
            super(selectionKey, networkInputSize, networkOutputSize, applicationOutputSize, applicationInputSize, timeoutMillis);
            this.node = node;
            this.state = ParserState.READ_HEADER_LENGTH;
        }

        public void input(ByteBuffer data) {
            while (data.hasRemaining()) {
                switch (this.state) {
                case READ_HEADER_LENGTH:
                    if (data.remaining() < 4)
                        return;

                    int headerLength = data.getInt();
                    this.header = ByteBuffer.wrap(new byte[headerLength]);
                    this.state = ParserState.READ_PAYLOAD_LENGTH;                    
                    break;

                case READ_HEADER:
                    ByteBuffer subBuffer = data.slice();
                    int newLimit = Math.min(this.header.remaining(), data.remaining());
                    subBuffer.limit(newLimit);

                    this.header.put(subBuffer);                        

                    // Advance the position of data past the bytes copied.
                    int newPosition = data.position() + newLimit;
                    data.position(newPosition);
                    if (this.header.remaining() == 0) {
                        this.state = ParserState.READ_PAYLOAD;
                    }
                    break;

                case READ_PAYLOAD_LENGTH:
                    if (data.remaining() < 4)
                        return;

                    int payLoadLength = data.getInt();
                    
                    this.payload = ByteBuffer.wrap(new byte[payLoadLength]);
                    this.state = ParserState.READ_HEADER;
                    break;

                case READ_PAYLOAD:
                    subBuffer = data.slice();
                    newLimit = Math.min(this.payload.remaining(), data.remaining());
                    subBuffer.limit(newLimit);

                    this.payload.put(subBuffer);                        

                    // Advance the position of data past the bytes copied.
                    newPosition = data.position() + newLimit;
                    data.position(newPosition);
                    if (this.payload.remaining() == 0) {
                        try {
                            TitanMessage request = new TitanMessage(this.header.array(), this.payload.array());
                            RequestHandler service = new RequestHandler(this.node, request, this);
                            new Thread(service).start();
                            this.state = ParserState.READ_HEADER_LENGTH; 
                        } catch (IOException e) {
                            e.printStackTrace();
                            try { this.close(); } catch (IOException e2) { }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                            try { this.close(); } catch (IOException e2) { }
                        }
                    }
                    break;
                }
            }
        }
    }

    public interface PlainConnectorMBean extends ThreadMBean {
        public long getConnectionCount();        
    }
 
    public static class PlainConnector extends sunlabs.asdf.io.Asynchronous implements Connector, PlainConnectorMBean {
        private ObjectName jmxObjectName;
        private SocketCache sockets;         // a cache of idle sockets

        public PlainConnector(TCPMessageService service, ChannelHandler.Factory handlerFactory) throws IOException, JMException {
            super(ServerSocketChannel.open(), handlerFactory);
            
            this.server.socket().setReuseAddress(true);
            this.server.socket().bind(
            new InetSocketAddress(service.node.getNodeAddress().getMessageURL().getHost(), service.node.getNodeAddress().getMessageURL().getPort()));

            this.sockets = new SocketCache(service.node.getConfiguration().asInt(TCPMessageService.SocketCacheSize),
                    service.node.getConfiguration().asInt(TCPMessageService.SocketTimeoutSeconds));
            
            if (service.jmxObjectNameRoot != null) {
                this.jmxObjectName = JMX.objectName(service.jmxObjectNameRoot, "PlainServer");
                TCPMessageService.registrar.registerMBean(this.jmxObjectName, this, PlainConnectorMBean.class);
            }
        }
        
        public void start() {
            super.start();
        }
        
        public void terminate() {
            try {
                this.close();
            } catch (IOException e) {

            }
        }

        public long getConnectionCount() {
            return this.getConnections().size();            
        }

        public Socket getAndRemove(NodeAddress address) throws Exception {
            return this.sockets.getAndRemove(address);
        }

        public void addAndEvictOld(NodeAddress addr, Socket socket) {
            this.sockets.addAndEvictOld(addr, socket);
        }

        public void disposeItem(NodeAddress addr, Socket socket) {
            this.sockets.disposeItem(addr, socket);
        }
    }

    /**
     * Each {@code TitanNode} has a server dispatching a new {@link TCPMessageService.RequestHandler} to
     * handle each inbound connection.
     * 
     * Other TitanNodes initiating connections to this TitanNode may place their sockets into a cache
     * (see {@link TitanNodeImpl#transmit(TitanMessage)} leaving the socket open and ready for use for a subsequent message. 
     */
    private static class SSLConnector extends Thread implements Connector, SSLConnectorMBean {

        /** The maximum queue length for incoming connections. The connection is refused if a connection indication arrives when the queue is full. */
        public static final Prototype ServerSocketBacklog = new Attributes.Prototype(PlainConnector.class, "ServerSocketBacklog",
                4,
        "The maximum queue length for incoming connections. The connection is refused if a connection indication arrives when the queue is full.");
        /**
         * Per thread Socket handling, reads messages from the input
         * socket and dispatches each to the local node for processing.
         */
        public class SocketHandler implements Runnable, SocketHandlerMBean {
            private Socket socket;
            private long lastActivityMillis;
            private TCPMessageService service;
            
            public SocketHandler(TCPMessageService service, ObjectName jmxObjectNameRoot, final Socket socket) throws JMException, IOException {
                this.socket = socket;
                this.service = service;
            }

            public void run() {
                try {
                    this.socket.setSoTimeout((int) Time.secondsInMilliseconds(this.service.node.getConfiguration().asInt(TCPMessageService.SocketTimeoutSeconds)));

                    while (!this.socket.isClosed()) {
                        // The client-side puts its end of this connection into its socket cache.
                        // So we setup a timeout on our side such that if the timeout expires, we simply terminate this connection.
                        // The client will figure that out once it tries to reuse this connection and it is closed and must setup a new connection.

                        try {
                            TitanMessage request = TitanMessage.newInstance(new BufferedInputStream(this.socket.getInputStream()));

                            this.lastActivityMillis = System.currentTimeMillis();

                            TitanMessage myResponse = this.service.node.receive(request);
                            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(this.socket.getOutputStream()));
                            try {
                                myResponse.writeObject(dos);
                            } catch (ConcurrentModificationException e) {
                                if (this.service.getLogger().isLoggable(Level.SEVERE)) {
                                    this.service.getLogger().severe("%s reading input TitanMessage from %s%n", e.toString(), this.socket);
                                    e.printStackTrace();
                                }
                            }
                            dos.flush();
                        } catch (ClassNotFoundException e) {
                            if (this.service.getLogger().isLoggable(Level.WARNING)) {
                                this.service.getLogger().warning("%s reading input TitanMessage from %s%n", e.toString(), this.socket);
                            }
                            this.socket.close();
                        }
                        // If the executor pool is full and there are client requests in the queue, then exit freeing up this Thread.
                        synchronized (this.service.executor) {
                            if (this.service.executor.getActiveCount() == this.service.executor.getMaximumPoolSize() && this.service.executor.getQueue().size() > 0) {
                                if (this.service.node.getLogger().isLoggable(Level.WARNING)) {
                                    this.service.getLogger().warning("Inbound connection congestion: active=%d max=%d queue=%d",
                                            this.service.executor.getActiveCount(),
                                            this.service.executor.getMaximumPoolSize(),
                                            this.service.executor.getQueue().size());
                                }
                                break;
                            }
                        }
                    }
                } catch (java.net.SocketTimeoutException exception) {
                    // ignore this exception, just abandon this connection.
                    if (this.service.getLogger().isLoggable(Level.FINE)) {
                        this.service.getLogger().fine("Closing idle connection: %s", exception);
                    }
                } catch (EOFException exception) {
                    if (this.service.getLogger().isLoggable(Level.FINE)) {
                        this.service.getLogger().fine("Closed by client. Idle %s.", Time.formattedElapsedTime(System.currentTimeMillis() - this.lastActivityMillis));
                    }
                    // ignore this exception, just abandon this connection,
                } catch (IOException exception) {
                    if (this.service.getLogger().isLoggable(Level.WARNING)) {
                        this.service.getLogger().warning("%s", exception);
                    }
                    // ignore this exception, just abandon this connection,
                } catch (Exception e) {
                    if (this.service.node.getLogger().isLoggable(Level.WARNING)) {
                        this.service.node.getLogger().warning("%s", e);
                    }
                } finally {
                    try { this.socket.close(); } catch (IOException ignore) { /**/ }
                    // If the accept() loop is waiting because the executor is full, this will notify it wakeup and try again.
                    synchronized (this.service.executor) {
                        this.service.executor.notify();
                    }
                }
            }
        }

        private ObjectName jmxObjectName;
        private SSLServerSocketFactory factory;
        private TCPMessageService service;
        private SSLSocketCache sockets;
        private SSLContext sslContext;
        private InetAddress inetAddress;
        
        public SSLConnector(TCPMessageService service) throws IOException, JMException {
            super(new ThreadGroup(service.node.getThreadGroup(), "SSLServer"), service.node.getThreadGroup().getName());
            this.service = service;

            this.service.node.getConfiguration().add(SSLConnector.ServerSocketBacklog);
            // All of this should be in the Node instance where it can be used by other things.

            try {
                this.sslContext = service.node.getNodeKey().newSSLContext();
            } catch (UnrecoverableKeyException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (KeyManagementException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (KeyStoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            this.factory = this.sslContext.getServerSocketFactory();
            this.inetAddress = InetAddress.getByName(this.service.node.getNodeAddress().getMessageURL().getHost());
            
            this.sockets = new SSLSocketCache(this.service.node.getConfiguration().asInt(TCPMessageService.SocketCacheSize), this.sslContext,
                    this.service.node.getConfiguration().asInt(TCPMessageService.SocketTimeoutSeconds));

            if (this.service.jmxObjectNameRoot != null) {
                this.jmxObjectName = JMX.objectName(this.service.jmxObjectNameRoot, "SSLConnector");
                TCPMessageService.registrar.registerMBean(this.jmxObjectName, this, SSLConnectorMBean.class);
            }
            this.service.node.getLogger().config("%s", this.service.node.getConfiguration().get(SSLConnector.ServerSocketBacklog));
        }

        @Override
        public void run() {
//            System.err.printf("SSLServer%d.run %s %d alreadyRunning=%b%n", this.hashCode(), this.node.getNodeAddress().format(), this.node.hashCode(), this.alreadyRunning);
//            if (this.alreadyRunning) {
//                System.err.printf("SSLServer already running%n");
//                new Throwable().printStackTrace();
//                return;
//            }
//            this.alreadyRunning = true;
            
            SSLServerSocket serverSocket = null;
            try {
                serverSocket = (SSLServerSocket) this.factory.createServerSocket(this.service.node.getNodeAddress().getMessageURL().getPort(),
                        this.service.node.getConfiguration().asInt(SSLConnector.ServerSocketBacklog),
                        this.inetAddress);

                serverSocket.setNeedClientAuth(true);
                serverSocket.setReuseAddress(true);
            } catch (IOException e) {
               this.service.node.getLogger().severe("Connection accept Thread terminated: %s", e);
                // Terminate this thread, which means this service is no longer accepting connections.
                return;
            }

            while (true) {
                if (this.service.log.isLoggable(Level.FINE)) {
                    this.service.log.fine("SSLServer pool %d active threads.", this.service.executor.getActiveCount());
                }
                synchronized (this.service.executor) {
                    while (this.service.executor.getActiveCount() == this.service.executor.getMaximumPoolSize()) {
                        try {
                            if (this.service.log.isLoggable(Level.WARNING)) {
                                this.service.node.getLogger().warning("SSLServer pool full with %d active threads (%d maximum).  Waiting on %s.",
                                        this.service.executor.getActiveCount(), this.service.executor.getMaximumPoolSize(), this);
                            }
                            this.service.executor.wait();
                        } catch (InterruptedException e) {}
                    }
                }
                if (this.service.log.isLoggable(Level.FINE)) {
                    this.service.log.fine("Accepting new connections on %s", serverSocket);
                }
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);

                    if (this.service.log.isLoggable(Level.FINE)) {
                        this.service.log.fine("Accepted connection %s serverClosed=%b", socket, serverSocket.isClosed());
                    }

                    synchronized (this.service.executor) {
                        this.service.executor.submit(new SocketHandler(this.service, jmxObjectName, socket));
                    }

                    if (this.service.log.isLoggable(Level.FINE)) {
                        this.service.log.fine("Dispatched connection %s serverClosed=%b", socket, serverSocket.isClosed());
                    }
                } catch (IOException e) {
                    this.service.node.getLogger().severe("Connection accept Thread terminated: %s serverSocket=%s serverClosed=%b", e, serverSocket, serverSocket.isClosed());
                    e.printStackTrace();
                    return;
                } catch (JMException e) {
                    this.service.node.getLogger().severe("Connection accept Thread terminated: %s", e);
                    e.printStackTrace();
                    return;
                } finally {
                    // Should close the connection manager's server socket.
                }
            }
        }

        public long getJMXActiveCount() {
            return this.service.executor.getActiveCount();
        }

        public long getJMXKeepAliveTime() {
            return this.service.executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
        }

        public long getJMXLargestPoolSize() {
            return this.service.executor.getLargestPoolSize();
        }

        public Socket getAndRemove(NodeAddress address) throws Exception {
            return this.sockets.getAndRemove(address);
        }

        public void addAndEvictOld(NodeAddress addr, Socket socket) {
            this.sockets.addAndEvictOld(addr, socket);
        }

        public void disposeItem(NodeAddress addr, Socket socket) {
            this.sockets.disposeItem(addr, socket);
        }
    }
    
    public interface SSLConnectorMBean extends ThreadMBean, ServerSocketMBean {
        public long getJMXActiveCount();
        public long getJMXKeepAliveTime();
        public long getJMXLargestPoolSize();
    }

    public interface SocketHandlerMBean extends ServerSocketMBean {

    }

    /**
     * 
     * Transmit a {@link TitanMessage} directly to a {@link NodeAddress} and return the reply.
     * If the destination address is unresponsive or cannot be reached, the return value is {@code null}.
     *
     * <p>
     * This method should throw Exceptions to signal failures rather than returning null.
     * </p>
     *
     * This handles both the SSL and non-SSL connections through the classes implementing the Connector interface.
     */
    public TitanMessage transmit(NodeAddress addr, TitanMessage message) /*throws InterruptedException*/ {
        while (true) {
            Socket socket = null;
            boolean socketIsGood = true;
            try {
                socket = this.connection.getAndRemove(addr);

                if (message.isTraced()) {
                    this.log.info("%s to %s", message.traceReport(), addr.format());
                }

                System.out.printf("TCPMessageService.transmitSocket: %s%n", socket);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                message.writeObject(out);
                out.flush();

                TitanMessage response = TitanMessage.newInstance(socket.getInputStream());
                if (response.isTraced()) {
                    this.log.info("recv: %s, reply: %ss", message.traceReport(), response.traceReport());
                }
                return response;
            } catch (InterruptedIOException e) {
                this.log.warning("%s disconnecting node %s(%s.%s)", e.toString(), addr.format(), message.getSubjectClass(), message.getSubjectClassMethod());
                socketIsGood = false;
                return null;  // return and don't continue to try, but don't disconnect the node.
            } catch (java.net.NoRouteToHostException e) {
                this.log.warning("%s disconnecting node %s(%s.%s)", e.toString(), addr.format(), message.getSubjectClass(), message.getSubjectClassMethod());
                socketIsGood = false;
                return null; // return and don't continue to try.
            } catch (java.net.ConnectException e) {
                this.log.warning("%s disconnecting node %s(%s.%s)", e.toString(), addr.format(), message.getSubjectClass(), message.getSubjectClassMethod());
                socketIsGood = false;
                return null; // return and don't continue to try.
            } catch (java.net.SocketException e) {
                this.log.warning("%s retry node %s(%s) %s.%s", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketIsGood = false;
                // close this socket and try again with a new one.
            } catch (IOException e) {
                this.log.warning("%s retry node %s(%s) %s.%s", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketIsGood = false;
                // close this socket and try again with a new one.
            } catch (Exception e) {
                this.log.warning("%s retry node %s(%s) %s.%s.", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                e.printStackTrace();
                //
                // One of the cases above will catch anything that actually
                // occurs.  But SSLSocketCache.Factory's newInstance() method
                // overrides one that's specified to throw Exception, so it must
                // be handled here.
                //
                socketIsGood = false;
                // close this socket and try again with a new one.
            } finally {
                if (socketIsGood) {
                    // The socket is (still) good, so return the socket to the cache.
                    this.connection.addAndEvictOld(addr, socket);
                } else {
                    if (socket != null)
                        this.connection.disposeItem(addr, socket);
                }
            }
        }
    }

    
}
