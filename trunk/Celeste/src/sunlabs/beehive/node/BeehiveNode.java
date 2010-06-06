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
 * Please contact Oracle, 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.beehive.node;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import sunlabs.asdf.io.AsynchronousMBean;
import sunlabs.asdf.io.ChannelHandler;
import sunlabs.asdf.io.SSLContextChannelHandler;
import sunlabs.asdf.io.UnsecureChannelHandler;
import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.jmx.ServerSocketMBean;
import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.Units;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.XML.Content;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.Copyright;
import sunlabs.beehive.Release;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.ObjectStore;
import sunlabs.beehive.api.Service;
import sunlabs.beehive.api.management.NodeMBean;
import sunlabs.beehive.node.BeehiveMessage.RemoteException;
import sunlabs.beehive.node.object.AbstractObjectHandler;
import sunlabs.beehive.node.object.BeehiveObjectHandler;
import sunlabs.beehive.node.services.BeehiveService;
import sunlabs.beehive.node.services.PublishDaemon;
import sunlabs.beehive.node.services.RoutingDaemon;
import sunlabs.beehive.node.services.WebDAVDaemon;
import sunlabs.beehive.node.services.api.Census;
import sunlabs.beehive.node.services.api.Publish;
import sunlabs.beehive.node.services.xml.TitanXML;
import sunlabs.beehive.node.services.xml.TitanXML.XMLNode;
import sunlabs.beehive.node.services.xml.TitanXML.XMLObjectStore;
import sunlabs.beehive.node.services.xml.TitanXML.XMLRoutingTable;
import sunlabs.beehive.node.util.DOLRLogger;
import sunlabs.beehive.node.util.DOLRLoggerMBean;
import sunlabs.beehive.util.DOLRStatus;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.beehive.util.WeakMBeanRegistrar;

/**
 * Instances of this class are complete Beehive nodes.
 * Each node listens for incoming connections from other nodes, processes and routes messages,
 * manages a local object store, and provides a framework for extending functionality. 
 */
public class BeehiveNode implements NodeMBean {
    
    public static class PlainChannelHandler extends UnsecureChannelHandler implements ChannelHandler {
        public static class Factory implements ChannelHandler.Factory {
            private BeehiveNode node;
            private long timeoutMillis;

            public Factory(BeehiveNode node) {
                this.node = node;
                this.timeoutMillis = Time.secondsInMilliseconds(this.node.configuration.asInt(BeehiveNode.ClientTimeoutSeconds));
            }
            
            public ChannelHandler newChannelHandler(SelectionKey selectionKey) {
                return new PlainChannelHandler(this.node, selectionKey, this.timeoutMillis);
            }            
        }


        private enum ParserState {
            READ_HEADER_LENGTH,
            READ_HEADER,
            READ_PAYLOAD_LENGTH,
            READ_PAYLOAD,            
        };
        private ParserState state;
        private BeehiveNode node;
        private ByteBuffer header;
        private ByteBuffer payload;
        
        public PlainChannelHandler(BeehiveNode node, SelectionKey selectionKey, long timeoutMillis) {
            super(selectionKey, 4096, 4096, 4096, 4096, timeoutMillis);
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
                            BeehiveMessage request = new BeehiveMessage(this.header.array(), this.payload.array());
                            RequestHandler service = new RequestHandler(this.node, request, this);
                            service.start();
                            this.state = ParserState.READ_HEADER_LENGTH; 
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            }
        }
    }
    
    public interface ConnectionServer {
        public XHTML.EFlow toXHTML();

        public void start();

        public void terminate();
    }
    
    public static class RequestHandler extends Thread implements Runnable {
        private BeehiveMessage request;
        private ChannelHandler channel;
        private BeehiveNode node;
        
        public RequestHandler(BeehiveNode node, BeehiveMessage request, ChannelHandler channel) {
            super(Thread.currentThread().getName());
            this.node = node;
            this.request = request;
            this.channel = channel;                
        }

        public void run() {
            try {
                // The client-side puts its end of this connection into its socket cache.
                // So we setup a timeout on our side such that if the timeout expires, we simply terminate this connection.
                // The client will figure that out once it tries to reuse this connection and it is closed and must setup a new connection.

                if (this.node.log.isLoggable(Level.FINE)) {
                    this.node.log.fine("Request: %s", request);
                }
                
                BeehiveMessage myResponse = this.node.receive(this.request);
                
                if (this.node.log.isLoggable(Level.FINE)) {
                    this.node.log.fine("Response: %s", request);
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                myResponse.writeObject(dos);
                dos.flush();
                this.channel.output(ByteBuffer.wrap(bos.toByteArray()));
            } catch (ClosedChannelException e) {
                
            } catch (EOFException exception) {
                if (this.node.log.isLoggable(Level.FINE)) {
                    this.node.log.fine("Closed by client.%n");
                }
                // ignore this exception, just abandon this connection,
            } catch (IOException exception) {
                if (this.node.log.isLoggable(Level.WARNING)) {
                    this.node.log.warning("%s", exception);
                }
                // ignore this exception, just abandon this connection,
            } catch (Exception e) {
                if (this.node.log.isLoggable(Level.WARNING)) {
                    this.node.log.warning("%s", e);
                    e.printStackTrace();
                }
            } finally {
                // Done with this Channel, so set it backup for a timeout.
                this.channel.resetExpirationTime(System.currentTimeMillis());
            }
        }
    }

    public interface PlainServerMBean extends ThreadMBean {
        public long getConnectionCount();        
    }
    
    private class PlainServer extends sunlabs.asdf.io.Asynchronous implements ConnectionServer, PlainServerMBean {
        private ObjectName jmxObjectName;

        public PlainServer(ChannelHandler.Factory handlerFactory) throws IOException, InstanceAlreadyExistsException, NotCompliantMBeanException, MBeanRegistrationException, MalformedObjectNameException {
            super(connMgr.getServerSocketChannel(), handlerFactory);

            if (BeehiveNode.this.jmxObjectName != null) {
                this.jmxObjectName = JMX.objectName(BeehiveNode.this.jmxObjectName, "PlainServer");
                BeehiveNode.registrar.registerMBean(this.jmxObjectName, this, PlainServerMBean.class);
            }
        }

        public XHTML.Table toXHTML() {
            // TODO Auto-generated method stub
            return null;
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
    }
    
    public static class SSLChannelHandler extends SSLContextChannelHandler implements ChannelHandler {
        public static class Factory implements ChannelHandler.Factory {
            private BeehiveNode node;
            private SSLContext sslContext;
            private long timeoutMillis;
            private ExecutorService executor;

            public Factory(BeehiveNode node, SSLContext sslContext) {
                this.node = node;
                this.sslContext = sslContext;
                this.timeoutMillis = Time.secondsInMilliseconds(this.node.configuration.asInt(BeehiveNode.ClientTimeoutSeconds));
                this.executor = node.tasks; // Use the node thread pool
            }
            
            public ChannelHandler newChannelHandler(SelectionKey selectionKey) throws IOException {
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                engine.setNeedClientAuth(true);
                ChannelHandler handler = new SSLChannelHandler(this.node, selectionKey, engine, this.executor, this.timeoutMillis);
                return handler;
            }            
        }

        private enum ParserState {
            READ_HEADER_LENGTH,
            READ_HEADER,
            READ_PAYLOAD_LENGTH,
            READ_PAYLOAD,            
        };
        private ParserState state;
        private BeehiveNode node;
        private ByteBuffer header;
        private ByteBuffer payload;
        
        public SSLChannelHandler(BeehiveNode node, SelectionKey selectionKey, SSLEngine engine, ExecutorService executor, long timeoutMillis) throws IOException {
            super(selectionKey, engine, executor, timeoutMillis);
            this.node = node;
            this.state = ParserState.READ_HEADER_LENGTH;
        }

        public void input(ByteBuffer data) {
//            if (true) {
//                try {
//                    java.security.cert.Certificate[] certificates = this.sslEngine.getSession().getPeerCertificates();
//                    //System.out.printf("input from %s%n", new BeehiveObjectId(certificates[0].getPublicKey()));
//                } catch (SSLPeerUnverifiedException e) {
//                    e.printStackTrace();
//                }
//            }

            // This code works because it understands the layout of the transmitted BeehiveMessage.
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
                            BeehiveMessage request = new BeehiveMessage(this.header.array(), this.payload.array());
                            RequestHandler service = new RequestHandler(this.node, request, this);
                            this.node.clientTasks.execute(service);
                            //service.start();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        this.state = ParserState.READ_HEADER_LENGTH; 
                    }

                    break;
                }
            }
        }
    }
    
    public interface SSLServerMBean extends ThreadMBean, AsynchronousMBean {
    }
    
    private class SSLServer extends sunlabs.asdf.io.Asynchronous implements ConnectionServer, SSLServerMBean {
        private ObjectName jmxObjectName;

        public SSLServer(ServerSocketChannel socketChannel, ChannelHandler.Factory handlerFactory)
        throws IOException, InstanceAlreadyExistsException, NotCompliantMBeanException, MBeanRegistrationException, MalformedObjectNameException {
            super(socketChannel, handlerFactory);
            
            this.setName(Thread.currentThread().getName() + ".SSLServer");
            
            if (BeehiveNode.this.jmxObjectName != null) {
                this.jmxObjectName = JMX.objectName(BeehiveNode.this.jmxObjectName, SSLServer.class.getName());
                BeehiveNode.registrar.registerMBean(this.jmxObjectName, this, SSLServerMBean.class);
            }
        }

        public XHTML.Table toXHTML() {
            // TODO Auto-generated method stub
            return null;
        }

        public void terminate() {
            try {
                this.close();
            } catch (IOException e) {

            }
        }  
    }
    
    
    /**
     * Each BeehiveNode has a server dispatching a new BeehiveService to
     * handle each inbound connection.
     * 
     * Initiators of connections to this Node may place their sockets into a cache
     * (see {@link BeehiveNode#transmit(BeehiveMessage)} leaving the socket open and ready for use for a subsequent message. 
     */
    private class BeehiveServer2 extends Thread implements ConnectionServer, BeehiveServer2MBean {

        /**
         * Per thread Socket handling.
         */
        public class BeehiveService2 implements Runnable, BeehiveService2MBean {
            private Socket socket;
            private ObjectName jmxObjectName;
            private long lastActivityMillis;

            public BeehiveService2(ObjectName jmxObjectNameRoot, final Socket socket) throws InstanceAlreadyExistsException,
            NotCompliantMBeanException, MBeanRegistrationException, MalformedObjectNameException {
                this.socket = socket;

//                if (jmxObjectNameRoot != null) {
//                    this.jmxObjectName = JMX.objectName(jmxObjectNameRoot, this.socket.hashCode());
//                    BeehiveNode.registrar.registerMBean(this.jmxObjectName, this, BeehiveService2MBean.class);
//                }
            }

            public void run() {
                try {
                    this.socket.setSoTimeout((int) Time.secondsInMilliseconds(BeehiveNode.this.configuration.asInt(BeehiveNode.ClientTimeoutSeconds)));

                    while (!this.socket.isClosed()) {
                        // The client-side puts its end of this connection into its socket cache.
                        // So we setup a timeout on our side such that if the timeout expires, we simply terminate this connection.
                    	// The client will figure that out once it tries to reuse this connection and it is closed and must setup a new connection.

                        try {
                            BeehiveMessage request = BeehiveMessage.newInstance(this.socket.getInputStream());

                            this.lastActivityMillis = System.currentTimeMillis();

                            BeehiveMessage myResponse = BeehiveNode.this.receive(request);
                            DataOutputStream dos = new DataOutputStream(this.socket.getOutputStream());
                            myResponse.writeObject(dos);
                            dos.flush();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                            this.socket.close();
                        }
                        // If the executor pool is full and there are client requests in the queue, then exit freeing up this Thread.
                        synchronized (BeehiveServer2.this.executor) {
                            if (BeehiveServer2.this.executor.getActiveCount() == BeehiveServer2.this.executor.getMaximumPoolSize() && BeehiveServer2.this.executor.getQueue().size() > 0) {
                            	if (BeehiveNode.this.log.isLoggable(Level.WARNING)) {
                            		BeehiveNode.this.log.warning("Inbound connection congestion: active=%d max=%d queue=%d",
                            				BeehiveServer2.this.executor.getActiveCount(),
                            				BeehiveServer2.this.executor.getMaximumPoolSize(),
                            				BeehiveServer2.this.executor.getQueue().size());
                            	}
                                break;
                            }
                        }
                    }
                } catch (java.net.SocketTimeoutException exception) {
                    // ignore this exception, just abandon this connection.
                    if (BeehiveNode.this.log.isLoggable(Level.FINE)) {
                        BeehiveNode.this.log.fine("Closing idle connection: %s", exception);
                    }
                } catch (EOFException exception) {
                	if (BeehiveNode.this.log.isLoggable(Level.FINE)) {
                		BeehiveNode.this.log.fine("Closed by client. Idle %s.", Time.formattedElapsedTime(System.currentTimeMillis() - this.lastActivityMillis));
                	}
                	// ignore this exception, just abandon this connection,
                } catch (IOException exception) {
                    if (BeehiveNode.this.log.isLoggable(Level.WARNING)) {
                        BeehiveNode.this.log.warning("%s", exception);
                        exception.printStackTrace();
                    }
                    // ignore this exception, just abandon this connection,
                } catch (Exception e) {
                    if (BeehiveNode.this.log.isLoggable(Level.WARNING)) {
                        BeehiveNode.this.log.warning("%s", e);
                        e.printStackTrace();
                    }
                } finally {
                	try { this.socket.close(); } catch (IOException ignore) { /**/ }
                	// If the accept() loop is waiting because the executor is full, this will notify it to try again.
                	synchronized (BeehiveServer2.this.executor) {
                		BeehiveServer2.this.executor.notify();
                	}
                }
            }
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
                thread.setName(String.format("%s-beehive-%d", this.name, this.counter));
                this.counter++;
                return thread;
            }
        }

        private ThreadPoolExecutor executor;
        private ObjectName jmxObjectName;

        public BeehiveServer2() throws IOException, InstanceAlreadyExistsException, NotCompliantMBeanException, MBeanRegistrationException, MalformedObjectNameException {
            super(new ThreadGroup(BeehiveNode.this.getThreadGroup(), "Beehive Server2"), BeehiveNode.this.getThreadGroup().getName());

            this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(BeehiveNode.this.configuration.asInt(BeehiveNode.ClientMaximum),
                    new SimpleThreadFactory(BeehiveNode.this.getThreadGroup().getName()));

            if (BeehiveNode.this.jmxObjectName != null) {
                this.jmxObjectName = JMX.objectName(BeehiveNode.this.jmxObjectName, "BeehiveServer2");
                BeehiveNode.registrar.registerMBean(this.jmxObjectName, this, BeehiveServer2MBean.class);
            }
        }

        @Override
        public void run() {
            ServerSocket serverSocket = null;
            try {
                serverSocket = BeehiveNode.this.connMgr.getServerSocket();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            for (;;) {
//                BeehiveNode.this.log.info("BeehiveServer2 pool %d threads.", this.executor.getActiveCount());
                synchronized (this.executor) {
                    while (this.executor.getActiveCount() == this.executor.getMaximumPoolSize()) {
                        try {
                            BeehiveNode.this.log.info("BeehiveServer2 pool full with %d active threads (%d maximum).  Waiting on %s.", this.executor.getActiveCount(), this.executor.getMaximumPoolSize(), this);
                            this.executor.wait();
                        } catch (InterruptedException e) {}
                    }
                }
                Socket socket = null;
//                BeehiveNode.this.log.info("Accepting new connection on %s", serverSocket);
                try {
                    socket = connMgr.accept(serverSocket);

//                    BeehiveNode.this.log.info("Accepted connection %s serverClosed=%b", socket, serverSocket.isClosed());

                    synchronized (this.executor) {
                        this.executor.submit(new BeehiveService2(this.jmxObjectName, socket));
                    }
//                    BeehiveNode.this.log.info("Submitted connection %s serverClosed=%b", socket, serverSocket.isClosed());
                } catch (IOException e) {
                    BeehiveNode.this.log.info("Error on %s serverClosed=%b", serverSocket, serverSocket.isClosed());
                    e.printStackTrace();
                    return;
                } catch (InstanceAlreadyExistsException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return;
                } catch (NotCompliantMBeanException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return;
                } catch (MBeanRegistrationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return;
                } catch (MalformedObjectNameException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return;
                } finally {
                    // Should close the connection manager's server socket.
                }
            }
        }

        public void terminate() {

        }
        
        public XHTML.EFlow toXHTML() {
        	XHTML.Table.Body tbody = new XHTML.Table.Body();
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Maximum Clients"), new XHTML.Table.Data(this.executor.getMaximumPoolSize())));
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Active Clients"), new XHTML.Table.Data(this.executor.getActiveCount())));
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("High Water Mark"), new XHTML.Table.Data(this.executor.getLargestPoolSize())));
        	
        	return new XHTML.Table(tbody);
        }

        public long getJMXActiveCount() {
            return this.executor.getActiveCount();
        }

        public long getJMXKeepAliveTime() {
            return this.executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
        }

        public long getJMXLargestPoolSize() {
            return this.executor.getLargestPoolSize();
        }
    }
    
    public interface BeehiveServer2MBean extends ThreadMBean, ServerSocketMBean {
        public long getJMXActiveCount();
        public long getJMXKeepAliveTime();
        public long getJMXLargestPoolSize();
    }

    /**
     * BeehiveService reads messages from the input
     * socket and dispatches each to the local node for processing.
     */

    public interface BeehiveService2MBean extends ServerSocketMBean {

    }

    public static class NoSuchNodeException extends Exception {
        private static final long serialVersionUID = 1L;

        public NoSuchNodeException() {
            super();
        }

        public NoSuchNodeException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NoSuchNodeException(Throwable cause) {
            super(cause);
        }
    }

    public static class ConfigurationException extends Exception {
        private static final long serialVersionUID = 1L;

        public ConfigurationException() {
            super();
        }

        public ConfigurationException(String format, Object...args) {
            super(String.format(format, args));
        }

        public ConfigurationException(Throwable cause) {
            super(cause);
        }
    }

    public static class SimpleThreadFactory implements ThreadFactory {
        private String name;
        private long counter;

        public SimpleThreadFactory(String name) {
            this.name = name;
            this.counter = 0;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(String.format("%s-pool-%03d", this.name, this.counter));
            this.counter++;
            return thread;
        }
    }

    protected final static String beehiveNodeBackingStorePrefix = "node-";
    private static final String APP_PKG = "sunlabs.beehive.node.services";

    /**
     *
     */
    public final static Attributes.Prototype LogFileSize = new Attributes.Prototype(BeehiveNode.class, "LogFileSize", 1024*1024,
            "The maximum size a logfile is allowed to grow before being 'turned-over.'");
    public final static Attributes.Prototype LogFileCount = new Attributes.Prototype(BeehiveNode.class, "LogFileCount", 10,
            "The maximum number of turned-over logfiles to keep.");

    /** This node's {@link Release} String. This Attribute is generated and is not configurable. */
    public final static Attributes.Prototype Version = new Attributes.Prototype(BeehiveNode.class, "Version", Release.ThisRevision(),
            "The build version.");

    /** The full pathname to a local directory to use as the "spool" directory for this node. */
    public final static Attributes.Prototype LocalFileSystemRoot = new Attributes.Prototype(BeehiveNode.class, "LocalFileSystemRoot", "/tmp/celeste",
            "The local directory to use for this node's data.");

    /** The full pathname of a Java KeyStore file containing this BeehiveNode's keys and credential. */
    public final static Attributes.Prototype KeyStoreFileName = new Attributes.Prototype(BeehiveNode.class, "KeyStoreFileName", null,
            "The full pathname of a Java KeyStore file containing this BeehiveNode's keys and credential.");

//    /** The local address to use.  If unset, then use use all interfaces. */
//    public final static Attributes.Prototype LocalNetworkAddress = new Attributes.Prototype(BeehiveNode.class, "LocalNetworkAddress");

    /** The system-wide IP address to use.  If unset, the use the LocalNetworkAddress. */
    public final static Attributes.Prototype InterNetworkAddress = new Attributes.Prototype(BeehiveNode.class, "InterNetworkAddress", null,
            "The system-wide IP address to use.");

    /** The TCP port to use for listening for and accepting other BeehiveNode traffic. */
    public final static Attributes.Prototype Port = new Attributes.Prototype(BeehiveNode.class, "Port", 12000,
            "The TCP port number that this node listens on for incoming BeehiveMessages.");

    //public final static boolean useSerialization = false;

    public final static Attributes.Prototype ClientMaximum = new Attributes.Prototype(BeehiveNode.class, "ClientMaximum",
            20,
            "The maximum number of of simultaneous neighbour connections.");
    
    /** The maximum number of milliseconds a client connection can be idle before it is considered unused and can be closed.
     */
    public final static Attributes.Prototype ClientTimeoutSeconds = new Attributes.Prototype(BeehiveNode.class, "ClientTimeoutSeconds",
            Time.minutesInSeconds(1),
            "The maximum number of milliseconds a client connection can be idle before it is considered unused and can be closed.");

    public final static Attributes.Prototype GatewayURL = new Attributes.Prototype(BeehiveNode.class, "GatewayURL",
            null,
            "The full HTTP URL of the gateway for this node to use.");

    public final static Attributes.Prototype GatewayRetryDelayMillis = new Attributes.Prototype(BeehiveNode.class, "GatewayRetryDelayMillis",
            Time.secondsInMilliseconds(30),
            "The number of milliseconds to wait before retrying to contact the gateway.");

    /** The inter-node connection type */
    public final static Attributes.Prototype ConnectionType = new Attributes.Prototype(BeehiveNode.class, "ConnectionType", "ssl",
            "The inter-node connection type. Either 'plain' or 'ssl'.");

    public final static Attributes.Prototype ClientPoolSize = new Attributes.Prototype(BeehiveNode.class, "BeehiveNodeClientPoolSize", 10,
            "The number of Threads to allocate to provide processing of inbound internode requests.");
    
    public final static Attributes.Prototype TaskPoolSize = new Attributes.Prototype(BeehiveNode.class, "BeehiveNodeTaskPoolSize", 20,
            "The number of Threads to allocate to provide processing of asynchronous activities.");

    /** This node's {@link NodeAddress}. This Attribute is generated and is not configurable.
     *  See {@link BeehiveNode#InterNetworkAddress} and {@link BeehiveNode#LocalNetworkAddress}. */
    public final static Attributes.Prototype NodeAddress = new Attributes.Prototype(BeehiveNode.class, "NodeAddress", null, null);

    public final static Attributes.Prototype ObjectStoreCapacity = new Attributes.Prototype(BeehiveNode.class, "ObjectStoreMaximum", "unlimited",
            "The maximum allowed size for the local object-store.");

    public final static Attributes.Prototype DojoRoot = new Attributes.Prototype(WebDAVDaemon.class, "DojoRoot", "http://o.aolcdn.com/dojo/1.4.1");
    public final static Attributes.Prototype DojoJavascript = new Attributes.Prototype(WebDAVDaemon.class, "DojoJavascript", "dojo/dojo.xd.js");
    public final static Attributes.Prototype DojoTheme = new Attributes.Prototype(WebDAVDaemon.class, "DojoTheme", "tundra");
    
    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    private final static WeakMBeanRegistrar registrar = new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    private ConnectionManager connMgr;

    private ObjectName jmxObjectName;

    private Publishers objectPublishers;

    private BeehiveObjectId networkObjectId;

    private long startTime;

    private ThreadGroup threadGroup;

    private ConnectionServer server;

    private ApplicationFramework services;

    private final DOLRLogger log;

    private final String spoolDirectory;
    
    /** This Node's public/private key information. */
    private final NodeKey nodeKey;
    
    /** Dossier on every neighbour ever known by this {@code BeehiveNode}. */
    private Dossier dossier;

    /** This Node's address(es) */
    private final NodeAddress address;

    /** This Node's neighbour map */
    private NeighbourMap map;

    /** This Node's object store */
    private final ObjectStore store;

    private ScheduledThreadPoolExecutor tasks;
    private ScheduledThreadPoolExecutor clientTasks;

    public Attributes configuration;

    public final static String copyright = Copyright.miniNotice;

    public BeehiveNode(Properties properties) throws IOException, ConfigurationException {
        this.configuration = new Attributes();
        this.configuration.update(properties);
        this.configuration.add(BeehiveNode.LocalFileSystemRoot);
        this.configuration.add(BeehiveNode.LogFileSize);
        this.configuration.add(BeehiveNode.LogFileCount);
        this.configuration.add(BeehiveNode.KeyStoreFileName);
        this.configuration.add(BeehiveNode.ClientPoolSize);
        this.configuration.add(BeehiveNode.TaskPoolSize);
//        this.configuration.add(BeehiveNode.LocalNetworkAddress);
        this.configuration.add(BeehiveNode.InterNetworkAddress);
        this.configuration.add(BeehiveNode.Port);
        this.configuration.add(BeehiveNode.ClientMaximum);
        this.configuration.add(BeehiveNode.ClientTimeoutSeconds);
        this.configuration.add(BeehiveNode.GatewayURL);
        this.configuration.add(BeehiveNode.GatewayRetryDelayMillis);
        this.configuration.add(BeehiveNode.ConnectionType);
        this.configuration.add(BeehiveNode.NodeAddress);
        this.configuration.add(BeehiveNode.ObjectStoreCapacity);
        this.configuration.add(BeehiveNode.Version);
        this.configuration.add(BeehiveNode.DojoRoot);
        this.configuration.add(BeehiveNode.DojoJavascript);
        this.configuration.add(BeehiveNode.DojoTheme);

        Thread.currentThread().setName("main");

        String localFsRoot = this.configuration.asString(BeehiveNode.LocalFileSystemRoot);
        // Create this node's "spool" directory before much of the rest of the node is brought up.
        File rootDirectory = new File(localFsRoot);
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs();
        }

//        String localInterfaceAddress = this.configuration.asString(BeehiveNode.LocalNetworkAddress);

        String internetworkAddress = this.configuration.asString(BeehiveNode.InterNetworkAddress);

        int localBeehivePort = this.configuration.asInt(BeehiveNode.Port);

        //
        // First, establish the object-id that this node will have.
        // By convention the object-id is based upon the public key of this node.
        // The public key is generated via the class NodeKey.
        // The public key is converted into an array
        // of bytes and hashed by BeehiveObjectId() to produce the object-id.
        //

        String keyStoreFileName = null;
        // If the keyStoreFileName has not been specified in the configuration, then we must construct one here.
        if (this.configuration.isUnset(BeehiveNode.KeyStoreFileName)) {
            // Be careful what characters you use here to separate the components
            // of the file name.  For example, using a ':' in a file name causes
            // Windows to either truncate the name at the colon, or throw an exception.
            //
            keyStoreFileName = localFsRoot + File.separator + "keys-" + internetworkAddress + "_" + localBeehivePort + ".jks";
            this.configuration.set(BeehiveNode.KeyStoreFileName, keyStoreFileName);
        } else {
            keyStoreFileName = this.configuration.asString(BeehiveNode.KeyStoreFileName);
        }

        this.nodeKey = new NodeKey(keyStoreFileName, internetworkAddress, localBeehivePort);
        this.address = new NodeAddress(this.nodeKey.getObjectId(), internetworkAddress, localBeehivePort, this.configuration.asInt(WebDAVDaemon.Port));

        this.configuration.set(BeehiveNode.NodeAddress, this.address.format());

        // Give the main Thread for this node a name and setup a ThreadGroup.
        // This is to distinguish this node from other nodes when they are
        // all running in one JVM.
        Thread.currentThread().setName(this.getObjectId().toString() + ".main");
        this.threadGroup = new ThreadGroup(this.getObjectId().toString());

        this.tasks = new ScheduledThreadPoolExecutor(this.configuration.asInt(BeehiveNode.TaskPoolSize), new BeehiveNode.SimpleThreadFactory(this.getObjectId().toString()));
        
        this.clientTasks = new ScheduledThreadPoolExecutor(this.configuration.asInt(BeehiveNode.ClientPoolSize), new BeehiveNode.SimpleThreadFactory(this.getObjectId().toString()));

        // Since the spool directory is a "user" supplied value, we must ensure that it is formatted properly.
        this.spoolDirectory = localFsRoot + File.separator + BeehiveNode.beehiveNodeBackingStorePrefix + this.getObjectId();
        new File(this.spoolDirectory).mkdirs();

        this.log = new DOLRLogger("sunlabs.beehive.node.BeehiveNode", this.getObjectId(), this.spoolDirectory,
                this.configuration.asInt(BeehiveNode.LogFileSize),  this.configuration.asInt(BeehiveNode.LogFileCount));

        try {
            this.jmxObjectName = JMX.objectName("com.sun.sunlabs.beehive", this.address.getObjectId().toString());
            BeehiveNode.registrar.registerMBean(this.jmxObjectName, this, NodeMBean.class);
            BeehiveNode.registrar.registerMBean(JMX.objectName(this.jmxObjectName, "log"), this.log, DOLRLoggerMBean.class);

            try {
                this.dossier = new Dossier(this.spoolDirectory);
            } catch (BackedObjectMap.AccessException e) {
                throw new RuntimeException(e);
            }

            this.connMgr = ConnectionManager.getInstance(this.configuration.asString(BeehiveNode.ConnectionType), this.getNodeAddress(), this.nodeKey);
            // Still some problems with the new async I/O mechanism and SSL.  Soaks up a lot of memory.
            if (false) {
                this.server = new BeehiveServer2();
            } else {
                if (this.configuration.asString(BeehiveNode.ConnectionType).equalsIgnoreCase("plain")) {
                    this.server = new PlainServer(new PlainChannelHandler.Factory(this));
                } else if (this.configuration.asString(BeehiveNode.ConnectionType).equalsIgnoreCase("ssl")) {
                    if (true) {
                        this.server = new BeehiveServer2();
                    } else {
                        try {
                            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                            kmf.init(this.nodeKey.getKeyStore(), this.nodeKey.getKeyPassword());
                            KeyManager[] km = kmf.getKeyManagers();

                            TrustManager[] tm = new TrustManager[1];
                            tm[0] = new NodeX509TrustManager();
                            SSLContext sslContext;
                            sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(km, tm, null);
                            sslContext.getServerSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
                            sslContext.getClientSessionContext().setSessionCacheSize(SSLConnectionManager.beehiveSSLSessionCacheSize);
                            sslContext.getClientSessionContext().setSessionTimeout(SSLConnectionManager.beehiveClientSSLSessionTimeout);
                            sslContext.getServerSessionContext().setSessionTimeout(SSLConnectionManager.beehiveServerSSLSessionTimeout);


                            ServerSocketChannel serverSocketChannel2 = ServerSocketChannel.open();

                            serverSocketChannel2.socket().setReuseAddress(true);
                            serverSocketChannel2.socket().bind(this.address.getInternetworkAddress());
                            this.server = new SSLServer(serverSocketChannel2, new SSLChannelHandler.Factory(this, sslContext));
                        } catch (java.security.NoSuchAlgorithmException exception) {
                            throw new IOException(exception.toString());
                        } catch (java.security.KeyStoreException exception) {
                            throw new IOException(exception.toString());
                        } catch (java.security.KeyManagementException exception) {
                            throw new IOException(exception.toString());
                        } catch (java.security.UnrecoverableKeyException exception) {
                            throw new IOException(exception.toString());
                        } catch (java.net.BindException exception) {
                            System.out.printf("%s port %d%n", exception, this.address.getInternetworkAddress());
                            throw new IOException(exception.toString());
                        }
                    }
                } else {
                    throw new BeehiveNode.ConfigurationException("Unknown connection type '%s' Must be either 'ssl' or 'plain'");
                }
            }

            this.map = new NeighbourMap(this);
            this.store = new BeehiveObjectStore(this, this.configuration.asString(BeehiveNode.ObjectStoreCapacity));
            this.objectPublishers = new Publishers(this, this.spoolDirectory);

            this.services = new ApplicationFramework(this,
                    new DOLRLogger("sunlabs.beehive.node.ApplicationFramework",
                            getObjectId(), getSpoolDirectory(),
                            this.configuration.asInt(BeehiveNode.LogFileSize), this.configuration.asInt(BeehiveNode.LogFileCount)));

            // Load and start the services that have long-running operations.
            // Any other services will be loaded lazily as needed.
            // For initial bootstrapping, we expect to find these classes on
            // the local CLASSPATH.
            this.getService(APP_PKG + ".RoutingDaemon");
            this.getService("sunlabs.beehive.node.services.object.AppClassObjectType");
            this.getService(APP_PKG + ".PublishDaemon");
            this.getService(APP_PKG + ".RetrieveObjectService");
            this.getService(APP_PKG + ".ReflectionService");
            this.getService(APP_PKG + ".CensusDaemon");
            this.getService(APP_PKG + ".WebDAVDaemon");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        } catch (InstanceAlreadyExistsException e) {
            throw new RuntimeException(e);
        } catch (NotCompliantMBeanException e) {
            throw new RuntimeException(e);
        } catch (MBeanRegistrationException e) {
            throw new RuntimeException(e);
        }

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", this.configuration.get(BeehiveNode.ClientMaximum));
            this.log.config("%s", this.configuration.get(BeehiveNode.ClientTimeoutSeconds));
            this.log.config("%s", this.configuration.get(BeehiveNode.ConnectionType));
            this.log.config("%s", this.configuration.get(BeehiveNode.DojoJavascript));
            this.log.config("%s", this.configuration.get(BeehiveNode.DojoRoot));
            this.log.config("%s", this.configuration.get(BeehiveNode.DojoTheme));
            this.log.config("%s", this.configuration.get(BeehiveNode.GatewayRetryDelayMillis));
            this.log.config("%s", this.configuration.get(BeehiveNode.GatewayURL));
            this.log.config("%s", this.configuration.get(BeehiveNode.InterNetworkAddress));
            this.log.config("%s", this.configuration.get(BeehiveNode.KeyStoreFileName));
            this.log.config("%s", this.configuration.get(BeehiveNode.LocalFileSystemRoot));
//            this.log.config("%s", this.configuration.get(BeehiveNode.LocalNetworkAddress));
            this.log.config("%s", this.configuration.get(BeehiveNode.LogFileSize));
            this.log.config("%s", this.configuration.get(BeehiveNode.LogFileCount));
            this.log.config("%s", this.configuration.get(BeehiveNode.NodeAddress));
            this.log.config("%s", this.configuration.get(BeehiveNode.ObjectStoreCapacity));
            this.log.config("%s", this.configuration.get(BeehiveNode.Port));
            this.log.config("%s", this.configuration.get(BeehiveNode.TaskPoolSize));
            this.log.config("%s", this.configuration.get(BeehiveNode.ClientPoolSize));
            this.log.config("%s", this.configuration.get(BeehiveNode.Version));
        }
    }

    /**
     * Run the given {@link Runnable} on this node's {@link ExecutorService}
     * @param runnable
     * @throws RejectedExecutionException if {@code runnable} cannot be accepted for execution
     */
    public void execute(Runnable runnable) throws RejectedExecutionException {
        this.tasks.execute(runnable);
    }

    public ApplicationFramework getServiceFramework() {
        return this.services;
    }

    public String getBeehiveProperties() {
        return this.configuration.toString();
    }

    public Dossier getDossier() {
        return this.dossier;
    }

    /**
     * Return the JMX name of this Node.
     */
    public ObjectName getJMXObjectName() {
        return this.jmxObjectName;
    }

    /**
     * Get the {@link DOLRLogger} for this {@link BeehiveNode}
     */
    public DOLRLogger getLogger() {
        return this.log;
    }

    /**
     * Get this Node's {@link NeighbourMap NeighbourMap}.
     */
    public NeighbourMap getNeighbourMap() {
        return this.map;
    }

    /**
     * Get the object-id of the network that this Node is connected to.
     */
    public BeehiveObjectId getNetworkObjectId() {
        return this.networkObjectId;
    }

    /**
     * Get this Node's {@link NodeAddress NodeAddress}.
     */
    public NodeAddress getNodeAddress() {
        return this.address;
    }

    /**
     * Return this Node's object-id.
     */
    public BeehiveObjectId getObjectId() {
        return this.address.getObjectId();
    }

    public Publishers getObjectPublishers() {
        return this.objectPublishers;
    }

    /**
     * Get this Beehive Node's {@link ObjectStore}.
     */
    public ObjectStore getObjectStore() {
        return this.store;
    }

    /**
     * Get the named Beehive node {@link Service}.
     */
    public Service getService(final String serviceName) {
        return this.services.get(serviceName);
    }

    /**
     * Get (dynamically loading and instantiating, if necessary) an instance of the named class cast to the given {@link Service}.
     *
     * @param <C>
     * @param klasse
     * @param serviceName
     * @return an instance of the named class cast to the given {@link Service}
     * @throws ClassCastException if the loaded class is <em>not</em> an instance of {@code klasse}.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public <C> C getService(Class<? extends C> klasse, final String serviceName) throws ClassCastException, ClassNotFoundException {
        return klasse.cast(this.services.get(serviceName));
    }

    /**
     * Return the local file-system directory this Node uses for storing various
     * kinds of data.
     */
    public String getSpoolDirectory() {
        return this.spoolDirectory;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public ThreadGroup getThreadGroup() {
        return this.threadGroup;
    }

    public long jmxGetBeehiveServerMaxIdleTime() {
        return this.configuration.asInt(BeehiveNode.ClientTimeoutSeconds);
    }

    /**
     * Get this Node's object-id as a String.
     */
    public String jmxGetObjectIdAsString() {
        return this.address.getObjectId().toString();
    }

    /**
     * Process a {@link BeehiveMessage} as input to this Node.
     * <p>
     * If the destination is this Node, then invoke the specified application
     * and return the resulting {@code BeehiveMessage} response.
     * </p>
     * <p>
     * Otherwise, if the {@code BeehiveMessage} is a PublishObject or UnpublishObject
     * message, then process via the receivePublishObject() or
     * receiveUnpublishObject() method and return the result.
     * </p>
     * <p>
     * If the {@code BeehiveMessage} is a multicast RouteToObject message, then process it
     * via the receiveRouteToObject() method and return the result.
     * </p>
     * <p>
     * If it is any other type of multicast {@code BeehiveMessage}, just process it by
     * invoking the specified application, which is responsible for further
     * transmission along the routing path.
     * </p>
     * <p>
     * If the {@code BeehiveMessage} can be routed on
     * to its destination transmit it and return the resulting {@code BeehiveMessage}
     * response.
     * </p>
     * <p>Otherwise, if this Node is the root of the {@code BeehiveMessage}'s
     * destination, and we must invoke the specified application and return
     * the result.
     * </p>
     * <p>
     * Finally, if all of the previous conditions are not met,
     * then invoke this Node's transmit() method to transmit
     * the message to the next hop.
     * </p>
     *
     * <p>
     * POSSIBLE NEW RULES
     * </p>
     * <p>
     * A unicast message is transmitted to its destination WITHOUT any
     * intermediate node processing other than routing to the next hop.  A
     * multicast message is subjected to processing by each intermediate node
     * along the routing path.
     * </p>
     * <p>
     * When a message reaches its routing terminus, a message routed exactly
     * must arrive on the node with the exact destination objectId specified
     * in the message.  Otherwise, a message is handled normally.
     * <p>
     * @param request
     * @return - The answering {@link BeehiveMessage} in response.
     */
    public BeehiveMessage receive(BeehiveMessage request) {
        BeehiveObjectId destinationNodeId = request.getDestinationNodeId();

        if (destinationNodeId.equals(this.getObjectId())) {
            // Any message other than route-to-node or route-to-object would indicate an ObjectId collision.
            if (request.isTraced()) {
                BeehiveNode.this.log.info("recv1(%s)", request.traceReport());
            }
            return this.services.sendMessageToApp(request);
        }

        BeehiveMessage.Type type = request.getType();

        if (type == BeehiveMessage.Type.PublishObject) {
            return this.receivePublishObject(request);
        }

        if (type == BeehiveMessage.Type.UnpublishObject) {
            return this.receiveUnpublishObject(request);
        }

        if (type == BeehiveMessage.Type.RouteToObject) {
            return this.receiveRouteToObject(request);
        }

        // The message is a RouteToNode message.

        if (request.isTraced()) {
            BeehiveNode.this.log.info("recv: %s", request.traceReport());
        }
        if (this.map.getRoute(destinationNodeId) == null) {
            // This node is the root of the destinationNodeId then we
            // must check to see if the message was sent to a particular node exactly.
            // If so, and we are NOT that node, return a reply indicating failure.
            // Otherwise, accept the message.
            if (request.isExactRouting() && !destinationNodeId.equals(this.getObjectId())) {
                return request.composeReply(this.address, new BeehiveNode.NoSuchNodeException("%s", destinationNodeId.toString()));
            }
            BeehiveMessage result = this.services.sendMessageToApp(request);

            if (request.isTraced()) {
                BeehiveNode.this.log.info("reply(%s)", result.traceReport());
            }
            return result;
        }

        BeehiveMessage result = this.transmit(request);

        return result;
    }

    /**
     * Receive a {@link PublishObjectMessage}.
     * <p>
     * If this node is just a node on the path the {@link PublishObjectMessage} takes,
     * simply forward the message along the route to its next hop.
     * The returned message, ultimately from the root of the published object-id <em>O<sub>R</sub></em> signals either success or failure.
     * If successful, this node creates a back-pointer recording the object-id and the publishing node, and returns the reply received from the root.
     * If not successful, this node returns the reply received from the root and does not create a back-pointer.
     * </p>
     * <p>
     * If this node is the root of the published object object-id, then dispatch the message to the {@link BeehiveObjectHandler} corresponding to the
     * published object's {@link AbstractObjectHandler}.
     * The {@link AbstractObjectHandler} is responsible for constructing and returning the reply {@link BeehiveMessage}.
     * </p>
     * <p>
     * PublishObjectMessages, while multicast in nature (ie. processed at each hop along the route to the destination node),
     * aren't really handled like multicast messages in that only the destination node invokes the published object {@link AbstractObjectHandler}.
     * True multicast messages are received by the associated {@link AbstractObjectHandler} at each node all along the routing path.
     * </p>
     * @see Publishers
     *
     * @param message The incoming {@link PublishObjectMessage}
     */
    private BeehiveMessage receivePublishObject(BeehiveMessage message) {
        BeehiveMessage rootReply;

        if (message.isTraced()) {
            BeehiveNode.this.log.info("recv: %s", message.traceReport());
        }

        // If this message can be routed further, transmit it and simply return the reply.
        if (this.map.getRoute(message.getDestinationNodeId()) != null) {
            rootReply = this.transmit(message);
        } else {
            // This node is the root for the published object.
            // Perform all the mandatory "rootness" functions here,
            // then hand this off to the object handler's publishObject(BeehiveMessage message)
            // the application specified in the published object's metadata.

            rootReply = this.services.sendMessageToApp(message);
        }

        // If the response from the root node signaled success, then get the Publish.Request
        // from the original message and store a back-pointer to the published object.
        // Otherwise, do NOT store a back-pointer.
        if (rootReply.getStatus().isSuccessful()) {
            try {
                Publish.Request request = message.getPayload(Publish.Request.class, this);
                for (Map.Entry<BeehiveObjectId,BeehiveObject.Metadata> entry : request.getObjectsToPublish().entrySet()) {
                    BeehiveNode.this.log.finest("%s->%s ttl=%ds", entry.getKey(), request.getPublisherAddress(), request.getSecondsToLive());
                    this.objectPublishers.update(entry.getKey(),
                            new Publishers.PublishRecord(entry.getKey(), request.getPublisherAddress(), entry.getValue(), request.getSecondsToLive()));
                }
            } catch (ClassNotFoundException e) {
                // bad request...
                e.printStackTrace();
            } catch (ClassCastException e) {
                // bad request...
                e.printStackTrace();
            } catch (RemoteException e) {
                // bad request.
                e.printStackTrace();
            }
        }

        if (message.isTraced()) {
            BeehiveNode.this.log.info("recv(%s) reply(%s) return", message.traceReport(), rootReply.traceReport());
        }

        return rootReply;
    }

    /**
     * Receive a {@link RouteToObjectMessage}.
     * The object-id of the desired {@link BeehiveObject} is encoded in the
     * {@link BeehiveMessage#subjectId subjectId} of the received
     * BeehiveMessage.
     * <ol>
     * <li>If (a) This is the target node, or (b) the target
     * object is in the local store then dispatch to the specified
     * application with the data in the {@code RouteToObjectMessage} and
     * the target objectId as arguments. The dispatched application
     * will formulate the reply.</li>
     *
     * <li>If a back-pointer for this object exists on this node,
     * then compose a unicast {@code RouteToObjectMessage} to the publisher
     * with the data in the original {@code RouteToObjectMessage}
     * as the data for the (now new) {@code RouteToNodeMessage}.
     * In this case, the destination is the publisher of the object and
     * the target remains the original objectId.</li>
     *
     * <li>If (case 3) this node can route the RouteToObjectMessage further, then
     * forward it, accept the reply and be done.</li>
     *
     * <li>If the {@code RouteToObjectMessage} cannot be routed further, then this node
     * is the root. Dispatch to the specified application with the
     * data in the {@code RouteToObjectMessage} and the target objectId
     * as arguments. The dispatched application will formulate the reply.</li>
     * </ol>
     */
    private BeehiveMessage receiveRouteToObject(BeehiveMessage request) {
        BeehiveMessage response;

        if (request.isTraced()) {
            BeehiveNode.this.log.info("recv: %s", request.traceReport());
        }

        // 1a
        if (request.subjectId.equals(this.getObjectId())) {
            //this.log.finest(request.subjectId + " 1a: I am target for application: " + request.getSubjectClass());
            return this.services.sendMessageToApp(request);
        }

        // We have the object locally, so send the message to the specified BeehiveService.
        if (this.store.containsObject(request.subjectId)) {
            //this.log.finest(request.subjectId + " 1b: I have objectId for application: " + request.getSubjectClass());
            return this.services.sendMessageToApp(request);
        }

        // 2
        //
        // If we have (one or more) publishers for the subjectId specified in the message, send a RouteToNode message proxying
        // the original RouteToObject message. If the message indicates success, return the reply to the originator of the request
        // otherwise signal failure.
        //

        // Any message that contains a serialized object that this JVM does not have in its class-path will
        // throw an exception in the request.get() below.  When transmitting object instances that contain
        // a serialized object with a class not known to every node JVM, send the class name as a string
        // and have the object contain a ClassLoader that understands where to get the class.  See the note in BeehiveMessage.
        //

        BeehiveMessage proxyMessage;
        proxyMessage = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                request.getSource(),
                BeehiveObjectId.ANY,
                request.subjectId,
                request.getSubjectClass(),
                request.getSubjectClassMethod(),
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.EXACTLY,
                new byte[0]);
        proxyMessage.setRawPayload(request.getRawPayLoad());

        // Avoid a possible ConcurrentModificationException due to a publisher reporting that the object is not found and as a consequence
        // we remove it from the set of publishers while concurrently using that set to iterate through the publishers.
        // Simply create a new Set with the same content.
        
        Set<Publishers.PublishRecord> publishers = new HashSet<Publishers.PublishRecord>();
        publishers.addAll(this.objectPublishers.getPublishers(request.subjectId));
        
        for (Publishers.PublishRecord publisher : publishers) {
            proxyMessage.setDestinationNodeId(publisher.getNodeId());
            if (request.isTraced()) {
                BeehiveNode.this.log.info("proxy msg=%5.5s... -> %s", request.getMessageId(), publisher.getNodeId());
            }

            if ((response = this.transmit(proxyMessage)) != null) {
                if (response.getStatus() == DOLRStatus.SERVICE_UNAVAILABLE) {
                    this.log.info("Unavailable publisher: " + publisher);
                } else if (response.getStatus() == DOLRStatus.NOT_FOUND) {
                    // The object was not found at the node we expected.
                    // Delete that node from the publisher list and try again.
                    // XXX We should reduce our reputation assessment of that node for not informing us of the object's removal.
                    this.objectPublishers.remove(request.subjectId, publisher.getNodeId());
                    this.log.info("Bad publisher: " + publisher + " responder=" + response.getSource().format());
                } else if (response.getStatus().isSuccessful()) {
                    BeehiveMessage reply;
                    // The idea here is to convey the serialized data from the replying node,
                    // without having to deserialize it and reserialize it just to retransmit
                    // it to the original requestor.  Also, as a result of not deserializing
                    // the data, we don't have to worry about having a class loader that understands
                    // the serialized data.
                    // reply = request.proxyReply(this.getNodeAddress(), response.getStatus(), response.getRawPayLoad());
                    reply = request.composeReply(this.getNodeAddress(), response.getStatus());
                    reply.setRawPayload(response.getRawPayLoad());
                    reply.setMulticast(Boolean.FALSE);
                    //this.log.exiting("RouteToObject(2) done: " + request.getSubjectClass() + " " + request.subjectId + " from " + request.getSource().getObjectId() + " from " + request.getSource().getObjectId() + " " + reply.getStatus());
                    return reply;
                } else {
                    this.log.info("%5.5s...: %s failed. %s", request.getMessageId(), publisher, response.getStatus());

                }
                // The response did not signal success, so keep trying.
            }
        }

        // 3
        // We weren't able to handle the proxying of the incoming message.  If we are NOT the root, then forward the message on.
        // Otherwise we hand the message up to the BeehiveService specified in the message and let it handle it.
        if (this.map.getRoute(request.getDestinationNodeId()) != null) {
            //this.log.fine(request.objectId + " 3: I am forwarding");
            return this.transmit(request);
        }

        //this.log.fine(request.objectId + " 4: I am root");
        return this.services.sendMessageToApp(request);
    }

    /**
     * Receive an {@link UnpublishObjectMessage}.
     * <p>
     * Delete any back-pointers to the object, and forward the message on to the next hop
     * along the routing path.  If this node terminates the routing path,
     * delete any root reference and invoke the named application.
     * </p>
     * <p>
     * Each {@code UnublishObjectMessage}, while multicast in nature, is not really
     * handled like multicast messages.  True multicast messages are received
     * by the associated application all along the routing path.
     * UnpublishObject messages cause the associated application to receive the
     * message only at the end of the routing path (the root).
     * </p>
     * @param request The received {@link UnpublishObjectMessage}
     */
    private BeehiveMessage receiveUnpublishObject(BeehiveMessage request) {
        if (request.isTraced()) {
            BeehiveNode.this.log.info("recv %s: objectId=%s", request.traceReport(), request.getObjectId());
        }

        try {
            PublishDaemon.UnpublishObject.Request unpublishRequest = request.getPayload(PublishDaemon.UnpublishObject.Request.class, this);
            for (BeehiveObjectId objectId : unpublishRequest.getObjectIds()) {

                // Remove the unpublished object from this node's publisher records.
                this.getObjectPublishers().remove(objectId, request.getSource().getObjectId());

                // Route the message on to the root, or (if this node is the root) hand it out to the
                // handler specified in the incoming BeehiveMessage.
                if (this.map.getRoute(request.getDestinationNodeId()) != null) {
                    return this.transmit(request);
                }

                return this.services.sendMessageToApp(request);
            }
        } catch (ClassCastException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // XXX problem here.
        return null;
    }

    /**
     * Remove the specified stored object from this node.
     * <p>
     * Issue a Beehive UnpublishObject message
     * </p>
     * @param objectId
     */
    public boolean removeLocalObject(final BeehiveObjectId objectId) throws BeehiveObjectStore.NotFoundException {
        BeehiveObject object = this.store.getAndLock(BeehiveObject.class, objectId);
        if (object == null) {
            return false;
        }
        try {
            this.store.remove(object);
        } finally {
            this.store.unlock(object);
        }
        return true;
    }

    public BeehiveMessage replyTo(BeehiveMessage message, DOLRStatus replyStatus) {
        return this.replyTo(message, replyStatus, null);
    }

    public BeehiveMessage replyTo(BeehiveMessage message, DOLRStatus replyStatus, Serializable serializable) {
        BeehiveMessage reply = message.composeReply(this.getNodeAddress(), replyStatus, serializable);
        return reply;
    }

    public XHTML.EFlow resourcesToXHTML() {
        Runtime r = Runtime.getRuntime();

        // <div style="width:400px" annotate="true" maximum="200" id="setTestBar" progress="20" dojoType="dijit.ProgressBar"></div>
        XHTML.Div usedMemoryBar = (XHTML.Div) new XHTML.Div().setStyle("width:100%")
            .setId("setTestBar")
            .addAttribute(new XML.Attr("annotate", "true"))
            .addAttribute(new XML.Attr("progress", Long.toString(r.totalMemory() - r.freeMemory())))
            .addAttribute(new XML.Attr("maximum", Long.toString(r.maxMemory())))
            .addAttribute(new XML.Attr("dojoType", "dijit.ProgressBar"));

        XHTML.Div allocatedMemoryBar = (XHTML.Div) new XHTML.Div().setStyle("width:100%")
            .setId("setTestBar2")
            .addAttribute(new XML.Attr("annotate", "true"))
            .addAttribute(new XML.Attr("progress", Long.toString(r.totalMemory())))
            .addAttribute(new XML.Attr("maximum", Long.toString(r.maxMemory())))
            .addAttribute(new XML.Attr("dojoType", "dijit.ProgressBar"));

        // Executors
        XHTML.Table.Body executorBody = new XHTML.Table.Body(new XHTML.Table.Row());

        XHTML.Div executors = new XHTML.Div(new XHTML.Table(new XHTML.Table.Caption("Executors"), executorBody));

        XHTML.Table clientListener = (XHTML.Table) this.server.toXHTML();
        
        XHTML.Table.Body javaBody = new XHTML.Table.Body(
                new XHTML.Table.Row(new XHTML.Table.Data("Available Processors"), new XHTML.Table.Data(r.availableProcessors())),
                new XHTML.Table.Row(new XHTML.Table.Data("Max/Allocated Memory %s", Units.longToCapacityString(r.maxMemory())), new XHTML.Table.Data(allocatedMemoryBar)),
                new XHTML.Table.Row(new XHTML.Table.Data("Used Memory %%"), new XHTML.Table.Data(usedMemoryBar), new XHTML.Table.Data())
        );

        XHTML.Table javaTable = new XHTML.Table(new XHTML.Table.Caption("Java Environment"), javaBody).setClass("JavaEnvironment");

        return new XHTML.Table(
                new XHTML.Table.Caption("Resources"),
                new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data(javaTable, this.connMgr.getStatisticsAsXHTML(), executors), new XHTML.Table.Data(this.threadResources())),
                    new XHTML.Table.Row(new XHTML.Table.Data(clientListener), new XHTML.Table.Data())
                ));
    }

    public BeehiveMessage routeToNodeMulticast(BeehiveObjectId nodeId, String klasse, String method, Serializable data) {
        BeehiveMessage msg = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                klasse,
                method,
                BeehiveMessage.Transmission.MULTICAST,
                BeehiveMessage.Route.LOOSELY,
                data);

        BeehiveMessage reply = this.receive(msg);
        return reply;
    }

    /**
     * Send the given {@link Serializable} {@code data} via a
     * {@link BeehiveMessage.Type#RouteToNode} to the {@link BeehiveNode}
     * that is the root of the {@link BeehiveObjectId} {@code objectId}.
     *
     * @param klasse The name of the {@link BeehiveService} to handle the reception of this message.
     * @param method The name of the method to invoke in {@code klasse} on the receiving node.
     * @param data Serializable data as the input parameter to the {@code method} method.
     */
    public BeehiveMessage sendToNode(BeehiveObjectId nodeId, String klasse, String method, Serializable data) {
        BeehiveMessage msg = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                klasse,
                method,
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.LOOSELY,
                data);

        BeehiveMessage reply = this.receive(msg);
        return reply;
    }
    
    public BeehiveMessage sendToNode(BeehiveObjectId nodeId, BeehiveObjectId objectId, String klasse, String method, Serializable data) {
        BeehiveMessage msg = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                objectId,
                klasse,
                method,
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.LOOSELY,
                data);

        BeehiveMessage reply = this.receive(msg);
        return reply;
    }

    /**
     * Transmit a {@link BeehiveMessage.Type#RouteToNode}
     * message to the specified node object-id.
     *
     * @param nodeId the {@link BeehiveObjectId} of the destination node.
     * @param objectClass the {@link String} name of the destination object handler class.
     * @param method the {@link String} name of the method to invoke.
     * @param data the payload of this message.
     * @throws NoSuchNodeException if the receiver cannot route the message further and is not the specified destination object-id.
     */
    public BeehiveMessage sendToNodeExactly(BeehiveObjectId nodeId, String objectClass, String method, Serializable data) throws NoSuchNodeException {
        BeehiveMessage msg = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                objectClass,
                method,
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.EXACTLY,
                data);

        BeehiveMessage reply = this.receive(msg);
        if (reply.getStatus().equals(DOLRStatus.SERVICE_UNAVAILABLE)) {
            this.getLogger().info("NoSuchNode: %5.5s...", nodeId);
            throw new BeehiveNode.NoSuchNodeException("%s %s", nodeId, reply.getStatus());
        }
        return reply;
    }

    /**
     * Send the given {@link Serializable} {@code data} to the {@link BeehiveObject}
     * specified by {@link BeehiveObjectId} {@code objectId}.
     * The object must be of the {@link BeehiveObjectHandler} {@code klasse} and the
     * method invoked is specified by String {@code method}.
     *
     * @param objectId The {@link BeehiveObjectId} of the target {@link BeehiveObject}.
     * @param klasse The name of the {@link BeehiveObjectHandler} to use.
     * @param method The name of the method to invoke.
     * @param data The data to transmit to the object.
     */
    public BeehiveMessage sendToObject(BeehiveObjectId objectId, String klasse, String method, Serializable data) {
        // It would be interesting to have a MULTICAST route-to-object
        // message which is sent to each known object.
        BeehiveMessage message = new BeehiveMessage(
                BeehiveMessage.Type.RouteToObject,
                this.getNodeAddress(),
                objectId,
                objectId,
                klasse,
                method,
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.LOOSELY,
                data);
        BeehiveMessage reply = this.receive(message);
        return reply;
    }
    
    public BeehiveMessage sendToObject(BeehiveObjectId objectId, String klasse, String method, Serializable data, boolean traced) {
        // It would be interesting to have a MULTICAST route-to-object
        // message which is sent to each known object.
        BeehiveMessage message = new BeehiveMessage(
                BeehiveMessage.Type.RouteToObject,
                this.getNodeAddress(),
                objectId,
                objectId,
                klasse,
                method,
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.LOOSELY,
                data);
        message.setTraced(traced);
        BeehiveMessage reply = this.receive(message);
        return reply;
    }

    /**
     * Start the Beehive Node processing.
     * This consists of initialising all of the listening sockets,
     * establishing our first connection via a Join operation,
     * and booting each of the internal applications in this Node.
     */
    public Thread start() {
        this.startTime = System.currentTimeMillis();

        try {
            NodeAddress gateway = null;

            // If the gateway is specified, connect to it and get the initial configuration information.
            // If the gateway is not specified, then this node is the first node in a system.
            if (!this.configuration.isUnset(BeehiveNode.GatewayURL)) {
                while (true) {
                    try {
                        OrderedProperties gatewayOptions = new OrderedProperties(new URL(this.configuration.asString(BeehiveNode.GatewayURL) + "/gateway"));
                        gateway = new NodeAddress(gatewayOptions.getProperty(BeehiveNode.NodeAddress.getName()));
                        break;
                    } catch (MalformedURLException fatal) {
                        fatal.printStackTrace();
                        System.exit(1);
                    } catch (IOException e) {
                        this.log.severe("%s", this.address.getHTTPInterface());
                        this.log.severe("Gateway: %s: %s.  Will retry in %sms%n",
                                this.configuration.asString(BeehiveNode.GatewayURL) + "/gateway",
                                e.toString(),
                                this.configuration.asLong(BeehiveNode.GatewayRetryDelayMillis));
                    }
                    try {
                        Thread.sleep(this.configuration.asLong(BeehiveNode.GatewayRetryDelayMillis));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                this.networkObjectId = new BeehiveObjectId();
            }

            if (gateway != null) {
                RoutingDaemon routingDaemon = (RoutingDaemon) this.getService("sunlabs.beehive.node.services.RoutingDaemon");
                RoutingDaemon.JoinOperation.Response join = routingDaemon.join(gateway);
                if (join == null) {
                    throw new RuntimeException(String.format("Cannot join with gateway: %s. Cannot start this node.", gateway.format()));
                }
                this.networkObjectId = join.getNetworkObjectId();

                Census census = (Census) this.getService("sunlabs.beehive.node.services.CensusDaemon");

                Map<BeehiveObjectId,OrderedProperties> list = census.select(gateway, 0, null, null);
                census.putAllLocal(list);
            }


            // Start the server listening for incoming connections from other nodes to this node.
            this.server.start();

            // Let the service framework know we're started and know who our
            // neighbors are - it is now safe to start arbitrary services.
            this.services.fullyStarted();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return (Thread) this.server;
    }

    public void stop() {
        for (String app : this.services.keySet()) {
            Service application = this.services.get(app);
            if (application != null)
                application.stop();
        }

        this.server.terminate();
    }

    /**
     * Recursively format the {@link ThreadGroup} and insert {@link XHTML.Table.Row}
     * into the given {@link XHTML.Table.Body} for each {@link Thread} in the {@code ThreadGroup}.
     * @param group the ThreadGroup to format
     * @param tbody the {@link XHTML.Table.Body} to insert rows.
     * @return The updated {@link XHTML.Table.Body}
     */
    private XHTML.Table.Body threadGroupToXHTML(ThreadGroup group, XHTML.Table.Body tbody) {
        XHTML.Table.Row row = new XHTML.Table.Row(new XHTML.Table.Data(group.getName()).setColumnSpan(5)).setClass("threadGroup");

        tbody.add(row);

        Thread[] thread = new Thread[group.activeCount()];
        group.enumerate(thread, false);

        tbody.add(new XHTML.Table.Row(
                new XHTML.Table.Heading(" Id"),
                 new XHTML.Table.Heading("Pri"),
                 new XHTML.Table.Heading("State"),
                 new XHTML.Table.Heading("Name")));
        for (int i = 0; i < thread.length; i++) {
            if (thread[i] != null) {
                tbody.add(new XHTML.Table.Row(
                        new XHTML.Table.Data(thread[i].getId()).add(thread[i].isDaemon() ? "d" : " "),
                        new XHTML.Table.Data(thread[i].getPriority()),
                        new XHTML.Table.Data(thread[i].getState()),
                        new XHTML.Table.Data(XHTML.CharacterEntity.escape(thread[i].getName()))));
            }
        }

        ThreadGroup[] groups = new ThreadGroup[group.activeGroupCount()];
        group.enumerate(groups);
        for (int i = 0; i < groups.length; i++) {
            threadGroupToXHTML(groups[i], tbody);
        }
        return tbody;
    }

    private XHTML.EFlow threadResources() {

        XHTML.Table.Body tbody = new XHTML.Table.Body();
        this.threadGroupToXHTML(this.threadGroup, tbody);
        XHTML.Table threadTable = new XHTML.Table(new XHTML.Table.Caption("Threads"), tbody).setClass("threads");
        return threadTable;
    }

//    private static XHTML.Table displayHorizontalPercentageBar(long percentage) {
//      XHTML.Div div = new XHTML.Div(XHTML.CharacterEntity.nbsp).setStyle("width: " + percentage + "%; background: red;'");
//      return new XHTML.Table(new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(div).setStyle("background: none green;")))).setStyle("width: 100%;");
//    }

    /**
     * Produce an {@link sunlabs.asdf.web.XML.XHTML.EFlow XHTML.EFlow} element containing an XHTML formatted representation of this node.
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) throws URISyntaxException {
        long currentTime = System.currentTimeMillis();
        long upTime = currentTime - this.startTime;
        long upDays = Time.millisecondsToDays(upTime);
        long upHours = Time.millisecondsToHours(upTime % Time.DAYS_IN_MILLISECONDS);
        long upMinutes = Time.millisecondsToMinutes(upTime % Time.HOURS_IN_MILLISECONDS);
        long upSeconds = Time.millisecondsToSeconds(upTime % Time.MINUTES_IN_MILLISECONDS);

        XHTML.Span uptimeSpan = new XHTML.Span("Uptime: %dd %dh %dm %ds", upDays, upHours, upMinutes, upSeconds);
        XHTML.Span dateTimeSpan = new XHTML.Span("Time: %1$td/%1$tm/%1$ty %1$tH:%1$tM:%1$tS", new Date());
        XHTML.Div metaInfo = new XHTML.Div(new XHTML.Heading.H1(this.address.getObjectId()), new XHTML.Break(), uptimeSpan, dateTimeSpan);

        new XHTML.Div(this.resourcesToXHTML()).setClass("section");
        
//        XML.Content routeTable = this.map.toXML();

        // Browser-side XSLT process is in terrible shape.
        // Do the processing here and one day, when the browsers catch up, remove this code and have the browser do the translation.
      

        XHTML.Div page = new XHTML.Div(
                metaInfo,
                this.services.toXHTML(uri, props),
                new XHTML.Div(this.map.toXHTML(uri, props)).setClass("section"),
//                new XHTML.Div(routeTable.toString()),
                this.store.toXHTML(uri, props),
//                new XHTML.Div(this.objectPublishers.toXHTML(uri, props)).setClass("section"),
//                new XHTML.Div(this.controlsToXHTML()).setClass("section"),
                new XHTML.Div(this.resourcesToXHTML()).setClass("section"),
                new XHTML.Para("This page composed in: " + (System.currentTimeMillis() - currentTime) + "ms"));

        return page;
    }

    public XML.Content toXML() {

        TitanXML xml = new TitanXML();

        long currentTime = System.currentTimeMillis();
        long upTime = currentTime - this.startTime;
        long upDays = Time.millisecondsToDays(upTime);
        long upHours = Time.millisecondsToHours(upTime % Time.DAYS_IN_MILLISECONDS);
        long upMinutes = Time.millisecondsToMinutes(upTime % Time.HOURS_IN_MILLISECONDS);
        long upSeconds = Time.millisecondsToSeconds(upTime % Time.MINUTES_IN_MILLISECONDS);

        XMLNode result = xml.newXMLNode(this.address.getObjectId(), String.format("Uptime: %dd %dh %dm %ds", upDays, upHours, upMinutes, upSeconds));
        result.bindNameSpace();
        
        result.add(this.map.toXML());
        result.add(this.services.toXML());
        result.add(this.store.toXML());
        return result;
    }

    /**
     * Transmit a message to its destination.
     *
     * <p>
     * Note: Use this method and {@link #transmit(NodeAddress, BeehiveMessage)} to
     * transmit a message ONLY IF you want this node to not receive the message.
     * Use {@link BeehiveNode#receive receive} instead.
     * </p>
     */
    public BeehiveMessage transmit(BeehiveMessage message) {
        message.timeToLive++;
        if (message.timeToLive >= this.map.n_tables) {
            this.log.severe("Message exceeded time-to-live: " + message.toString());
            new Exception().printStackTrace();
            return null;
        }

        message.timestamp = System.currentTimeMillis();
        if (message.getDestinationNodeId().equals(this.getObjectId())) {
            return this.receive(message);
        }

        BeehiveMessage reply;
        NodeAddress neighbour;
        while ((neighbour = this.map.getRoute(message.getDestinationNodeId())) != null) {
            if ((reply = this.transmit(neighbour, message)) != null) {
                return reply;
            }
            this.log.info("Removing dead neighbour %s", neighbour.format());

            // XXX Should also clean out any other remaining cached sockets to this neighbour.
            this.map.remove(neighbour);
        }

        // If there is no next hop, then this node is the root of the destination objectId.
        // If the message is to be routed exactly then send back an error.
        if (message.isExactRouting()) {
            return message.composeReply(this.getNodeAddress(), DOLRStatus.NOT_FOUND);
        }

        return this.receive(message);
    }

    /**
     * Transmit a {@link BeehiveMessage} directly to a {@link NodeAddress} and return the reply.
     * If the destination address is unresponsive or cannot be reached, the return value is {@code null}.
     *
     * <p>
     * This method should throw Exceptions to signal failures rather than returning null.
     * </p>
     */
    public BeehiveMessage transmit(NodeAddress addr, BeehiveMessage message) /*throws InterruptedException*/ {
        while (true) {
            Socket socket = null;
            boolean socketValid = true;
            try {
                socket = this.connMgr.getAndRemove(addr);

                if (message.isTraced()) {
                    BeehiveNode.this.log.info("%s to %s", message.traceReport(), addr.format());
                }

                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                message.writeObject(out);
                out.flush();

                BeehiveMessage response = BeehiveMessage.newInstance(socket.getInputStream());
                if (response.isTraced()) {
                    BeehiveNode.this.log.info("recv: %s, reply: %ss", message.traceReport(), response.traceReport());
                }
                return response;
            } catch (InterruptedIOException e) {
                this.log.fine("harmless %s (bytes transfered=%d) address %s %s.%s", e.toString(), e.bytesTransferred, addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketValid = false;
                return null;  // return and don't continue to try, but don't disconnect the node.
            } catch (java.net.NoRouteToHostException e) {
                this.log.warning("%s dropping node %s(%s) %s.%s", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketValid = false;
                return null; // return and don't continue to try.
            } catch (java.net.ConnectException e) {
                this.log.warning("dropping node %s node %s %s.%s", e.toString(), addr.format(), message.getSubjectClass(), message.getSubjectClassMethod());
                socketValid = false;
                return null; // return and don't continue to try.
            } catch (java.net.SocketException e) {
                this.log.fine("harmless %s node %s(%s) %s.%s", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketValid = false;
                // close this socket and try again with a new one.
            } catch (IOException e) {
                this.log.fine("harmless %s node %s(%s) %s.%s", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                socketValid = false;
                // close this socket and try again with a new one.
            } catch (Exception e) {
                this.log.warning("unexpected %s node %s(%s) %s.%s.", e.toString(), addr.format(), socket, message.getSubjectClass(), message.getSubjectClassMethod());
                e.printStackTrace();
                //
                // One of the cases above will catch anything that actually
                // occurs.  But SSLSocketCache.Factory's newInstance() method
                // overrides one that's specified to throw Exception, so it must
                // be handled here.
                //
                socketValid = false;
                // close this socket and try again with a new one.
            } finally {
                if (socketValid) {
                    // The socket is (still) good, so return the socket to the cache.
                    this.connMgr.addAndEvictOld(addr, socket);
                } else {
                    if (socket != null)
                        this.connMgr.disposeItem(addr, socket);
                }
            }
        }
    }
    
    public static void main(String[] args) {

        // Read this command line argument as a URL to fetch configuration properties.

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);
        try {
            OrderedProperties p = new OrderedProperties(new URL(args[0]));
            BeehiveNode node = new BeehiveNode(p);
            Thread thread = node.start();

            System.out.printf("%s [%d ms] %s%n", dateFormat.format(new Date()), System.currentTimeMillis() - node.getStartTime(), node.toString());
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (ConfigurationException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
