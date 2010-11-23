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
package sunlabs.titan.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.net.ssl.HttpsURLConnection;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.util.AbstractStoredMap;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.Units;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.api.TitanServiceFramework;
import sunlabs.titan.api.management.NodeMBean;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.TitanObjectHandler;
import sunlabs.titan.node.object.nodeobject.NodeObject;
import sunlabs.titan.node.object.nodeobject.NodeObjectHandler;
import sunlabs.titan.node.services.HTTPMessageService;
import sunlabs.titan.node.services.ReflectionService;
import sunlabs.titan.node.services.RoutingDaemon;
import sunlabs.titan.node.services.api.MessageService;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.census.CensusService;
import sunlabs.titan.node.services.objectstore.PublishDaemon;
import sunlabs.titan.node.services.xml.TitanXML;
import sunlabs.titan.node.services.xml.TitanXML.XMLNode;
import sunlabs.titan.node.util.DOLRLogger;
import sunlabs.titan.node.util.DOLRLoggerMBean;
import sunlabs.titan.util.DOLRStatus;
import sunlabs.titan.util.OrderedProperties;
import sunlabs.titan.util.WeakMBeanRegistrar;

/**
 * Instances of this class are complete Beehive nodes.
 * Each node listens for incoming connections from other nodes, processes and routes messages,
 * manages a local object store, and provides a framework for extending functionality. 
 */
public class TitanNodeImpl implements TitanNode, NodeMBean {

    public static class ConfigurationException extends java.lang.Exception {
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

    private static class SimpleThreadFactory implements ThreadFactory {
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

    protected final static String nodeBackingStorePrefix = "node-";
    
    /** The full pathname to a local directory to use as the "spool" directory for this node. */
    public final static Attributes.Prototype LocalFileSystemRoot = new Attributes.Prototype(TitanNodeImpl.class, "LocalFileSystemRoot", "/tmp/titan",
            "The local directory to use for this TitanNode's data.");
    
    /** The maximum number of turned-over logfiles to keep. */
    public final static Attributes.Prototype LogFileCount = new Attributes.Prototype(TitanNodeImpl.class, "LogFileCount", 10,
            "The maximum number of turned-over logfiles to keep.");
    
    /** The maximum size a logfile is allowed to grow before being 'turned-over.' */
    public final static Attributes.Prototype LogFileSize = new Attributes.Prototype(TitanNodeImpl.class, "LogFileSize", 1024*1024,
            "The maximum size a logfile is allowed to grow before being 'turned-over.'");

    /** This node's {@link Release} String. This Attribute is generated and is not configurable. */
    public final static Attributes.Prototype Version = new Attributes.Prototype(TitanNodeImpl.class, "Version", Release.ThisRevision(),
            "The node's build version. This Attribute is generated and is not configurable.");

    /** The full pathname of a Java KeyStore file containing this TitanNode's keys and credential. */
    public final static Attributes.Prototype KeyStoreFileName = new Attributes.Prototype(TitanNodeImpl.class, "KeyStoreFileName", null,
            "The full pathname of a Java KeyStore file containing this TitanNode's keys and credential.");

//    /** The local address to use.  If unset, then use use all interfaces. */
//    public final static Attributes.Prototype LocalNetworkAddress = new Attributes.Prototype(TitanNode.class, "LocalNetworkAddress");

    /** The system-wide IP address to use.  If unset, the use the LocalNetworkAddress. */
    public final static Attributes.Prototype InterNetworkAddress = new Attributes.Prototype(TitanNodeImpl.class, "InterNetworkAddress", null,
            "The local IP address of this TitanNode.");

    /** The TCP port to use for the MessageService to listen for and accept incoming TitanMessage traffic. */
    public final static Attributes.Prototype Port = new Attributes.Prototype(TitanNodeImpl.class, "Port", 12000,
            "The TCP port to use for the MessageService to listen for and accept incoming TitanMessage traffic.");

    /** The full HTTP URL of the gateway TitanNode for this TitanNode to use to join the system. */
    public final static Attributes.Prototype GatewayURL = new Attributes.Prototype(TitanNodeImpl.class, "GatewayURL",
            null,
            "The full HTTP URL of the gateway TitanNode for this TitanNode to use to join the system.");

    /** The number of seconds to wait before retrying to contact the gateway. */
    public final static Attributes.Prototype GatewayRetryDelaySeconds = new Attributes.Prototype(TitanNodeImpl.class, "GatewayRetryDelaySeconds",
            30,
            "The number of seconds to wait before retrying to contact the gateway.");

    /** The number of Threads to allocate to provide processing of inbound internode requests. */
    public final static Attributes.Prototype ClientPoolSize = new Attributes.Prototype(TitanNodeImpl.class, "TitanNodeClientPoolSize", 10,
            "The number of Threads to allocate to provide processing of inbound internode requests.");
    
    /** The number of Threads to allocate to provide processing of asynchronous activities. */
    public final static Attributes.Prototype TaskPoolSize = new Attributes.Prototype(TitanNodeImpl.class, "TitanNodeTaskPoolSize", 20,
            "The number of Threads to allocate to provide processing of asynchronous activities.");

    /** This {@link TitanNode}'s {@link NodeAddress}. This Attribute is generated and is not configurable.  See {@link TitanNodeImpl#InterNetworkAddress}. */
    public final static Attributes.Prototype NodeAddress = new Attributes.Prototype(TitanNodeImpl.class, "NodeAddress", null,
            "This TitanNode's NodeAddress. This Attribute is generated and is not configurable. See TitanNodeImpl#InterNetworkAddress");

    /** The maximum allowed size for the local object-store. */
    public final static Attributes.Prototype ObjectStoreCapacity = new Attributes.Prototype(TitanNodeImpl.class, "ObjectStoreMaximum", "unlimited",
            "The maximum allowed size for the local object-store.");
    
    /** The local start time of this TitanNode.  This Attribute is generated and is not configurable. */
    public final static Attributes.Prototype StartTime = new Attributes.Prototype(TitanNodeImpl.class, "StartTime", 0,
            "The local start time of this TitanNode.  This Attribute is generated and is not configurable.");
    
    /** The class name of the MessageService to use. See {@link MessageService}. */
    public final static Attributes.Prototype MessageService = new Attributes.Prototype(TitanNodeImpl.class, "MessageService", HTTPMessageService.class.getName(),
            "The class name of the MessageService to use.");
    
    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    private final static WeakMBeanRegistrar registrar = new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    private ObjectName jmxObjectName;

    private Publishers objectPublishers;

    private TitanGuid networkObjectId;

    private long startTime;

    private ThreadGroup threadGroup;

    private TitanServiceFramework services;

    private final DOLRLogger log;

    private final String spoolDirectory;
    
    /** This Node's public/private key information. */
    private final NodeKey nodeKey;

    /** This Node's address(es) */
    private final NodeAddress address;

    /** This Node's neighbour map */
    private NeighbourMap map;

    /** This Node's object store */
    private final TitanObjectStore store;

    private ScheduledThreadPoolExecutor tasks;
//    private ScheduledThreadPoolExecutor clientTasks;

    public Attributes configuration;

    private MessageService messageService;

    public final static String copyright = Copyright.miniNotice;

    public TitanNodeImpl(Properties properties) throws IOException, ConfigurationException, AbstractStoredMap.OutOfSpace {
        this.configuration = new Attributes();
        this.configuration.update(properties);
        this.configuration.add(TitanNodeImpl.LocalFileSystemRoot);
        this.configuration.add(TitanNodeImpl.LogFileSize);
        this.configuration.add(TitanNodeImpl.LogFileCount);
        this.configuration.add(TitanNodeImpl.KeyStoreFileName);
        this.configuration.add(TitanNodeImpl.ClientPoolSize);
        this.configuration.add(TitanNodeImpl.TaskPoolSize);
        this.configuration.add(TitanNodeImpl.InterNetworkAddress);
        this.configuration.add(TitanNodeImpl.Port);
        this.configuration.add(TitanNodeImpl.GatewayURL);
        this.configuration.add(TitanNodeImpl.GatewayRetryDelaySeconds);
        this.configuration.add(TitanNodeImpl.NodeAddress);
        this.configuration.add(TitanNodeImpl.ObjectStoreCapacity);
        this.configuration.add(TitanNodeImpl.Version);
        this.configuration.add(TitanNodeImpl.MessageService);
        // Add some of the configuration parameters of the required services here because we need them below.
        this.configuration.add(HTTPMessageService.ServerSocketPort);
        this.configuration.add(HTTPMessageService.Protocol);

        String localFsRoot = this.configuration.asString(TitanNodeImpl.LocalFileSystemRoot);
        // Create this node's "spool" directory before the rest of the node is setup.
        File rootDirectory = new File(localFsRoot);
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs();
        }

        if (this.configuration.isUnset(TitanNodeImpl.InterNetworkAddress)) {
            this.configuration.set(TitanNodeImpl.InterNetworkAddress, InetAddress.getLocalHost().getHostAddress());
        } else {
            String internetworkAddress = this.configuration.asString(TitanNodeImpl.InterNetworkAddress);
            InetAddress address = InetAddress.getByName(this.configuration.asString(TitanNodeImpl.InterNetworkAddress));
            // Check for sanity in the specification of the InterNetworkAddress.
            NetworkInterface netInterface = NetworkInterface.getByInetAddress(address);
            if (netInterface == null) {
                System.err.printf("WARNING %s=%s does not specify an IP address on this host.%n", TitanNodeImpl.InterNetworkAddress.getName(), internetworkAddress);
            }
        }
        String internetworkAddress = this.configuration.asString(TitanNodeImpl.InterNetworkAddress);

        int localBeehivePort = this.configuration.asInt(TitanNodeImpl.Port);

        //
        // First, establish the object-id that this node will have.
        // By convention the object-id is based upon the public key of this node.
        // The public key is generated via the class NodeKey.
        // The public key is converted into an array
        // of bytes and hashed by TitanGuid() to produce the object-id.
        //

        String keyStoreFileName = null;
        // If the keyStoreFileName has not been specified in the configuration parameters,
        // then we must construct one here and set it.
        if (this.configuration.isUnset(TitanNodeImpl.KeyStoreFileName)) {
            // Be careful what characters you use here to separate the components
            // of the file name.  For example, using a ':' in a file name causes
            // Windows to either truncate the name at the colon, or throw an exception.
            //
            keyStoreFileName = localFsRoot + File.separator + "keys-" + internetworkAddress + "_" + localBeehivePort + ".jks";
            this.configuration.set(TitanNodeImpl.KeyStoreFileName, keyStoreFileName);
        } else {
            keyStoreFileName = this.configuration.asString(TitanNodeImpl.KeyStoreFileName);
        }

        this.nodeKey = new NodeKey(keyStoreFileName, internetworkAddress, localBeehivePort);

        URL nodeInspectorURL = new URL(this.configuration.asString(HTTPMessageService.Protocol), internetworkAddress, this.configuration.asInt(HTTPMessageService.ServerSocketPort), "");
        URL messageURL = new URL(this.configuration.asString(HTTPMessageService.Protocol), internetworkAddress, localBeehivePort, "");
        this.address = new NodeAddress(new TitanNodeIdImpl(this.nodeKey.getObjectId()), messageURL, nodeInspectorURL);
        
        this.configuration.set(TitanNodeImpl.NodeAddress, this.address.format());

        // Give the main Thread for this node a name and setup a ThreadGroup.
        // This is to distinguish this node from other nodes when they are
        // all running in one JVM.
        this.threadGroup = new ThreadGroup(this.getNodeId().toString());

        this.tasks = new ScheduledThreadPoolExecutor(this.configuration.asInt(TitanNodeImpl.TaskPoolSize), new TitanNodeImpl.SimpleThreadFactory(this.getNodeId().toString()));
        
//        this.clientTasks = new ScheduledThreadPoolExecutor(this.configuration.asInt(TitanNodeImpl.ClientPoolSize), new TitanNodeImpl.SimpleThreadFactory(this.getNodeId().toString()));

        // Since the spool directory is a "user" supplied value, we must ensure that it is formatted properly.
        this.spoolDirectory = localFsRoot + File.separator + TitanNodeImpl.nodeBackingStorePrefix + this.getNodeId();
        new File(this.spoolDirectory).mkdirs();

        this.log = new DOLRLogger(TitanNodeImpl.class.getName(), this.getNodeId(), this.spoolDirectory,
                this.configuration.asInt(TitanNodeImpl.LogFileSize),  this.configuration.asInt(TitanNodeImpl.LogFileCount));

        try {
            this.jmxObjectName = JMX.objectName("com.oracle.sunlabs.titan", this.address.getObjectId().toString());
            TitanNodeImpl.registrar.registerMBean(this.jmxObjectName, this, NodeMBean.class);
            TitanNodeImpl.registrar.registerMBean(JMX.objectName(this.jmxObjectName, "log"), this.log, DOLRLoggerMBean.class);

            this.map = new NeighbourMap(this);
            // XXX Should the object store be part of PublishDaemon? Or vice-versa?
            this.store = new TitanObjectStoreImpl(this, this.configuration.asString(TitanNodeImpl.ObjectStoreCapacity));
            this.objectPublishers = new Publishers(this, this.spoolDirectory);

//            if (true) {
                this.services = new SimpleServiceFramework(this);
//            } else {
//                this.services = new ApplicationFramework(this,
//                        new DOLRLogger(ApplicationFramework.class.getName(),
//                                getNodeId(), getSpoolDirectory(),
//                                this.configuration.asInt(TitanNodeImpl.LogFileSize), this.configuration.asInt(TitanNodeImpl.LogFileCount)));
//            }

            // Load the services that are integral to the operation of the node.
            // Services are not started until the node is started.
            // Any other services will be loaded lazily as needed.
            // For initial bootstrapping, we expect to find these classes on
            // the local CLASSPATH.

            // This will cause the class implementing the MessageService to be loaded.
            this.messageService = (MessageService) this.getService(this.configuration.asString(TitanNodeImpl.MessageService));

            /**
             * Create an object that represents this node.
             * This is experimental.
             */
            NodeObject.Handler nodeObjectHandler = this.getService(NodeObjectHandler.class);
            nodeObjectHandler.createObject();
            
            this.getService(HTTPMessageService.class);
            this.getService(RoutingDaemon.class);
            this.getService(PublishDaemon.class);
            this.getService(ReflectionService.class);
            this.getService(CensusService.class);
        } catch (JMException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (TitanObjectStoreImpl.InvalidObjectException e) {
            throw new RuntimeException(e);
        } catch (TitanObjectStoreImpl.ObjectExistenceException e) {
            throw new RuntimeException(e);
        } catch (TitanObjectStoreImpl.NoSpaceException e) {
            throw new RuntimeException(e);
        } catch (TitanObjectStoreImpl.UnacceptableObjectException e) {
            throw new RuntimeException(e);
        } catch (TitanObjectStoreImpl.DeleteTokenException e) {
            throw new RuntimeException(e);
        }

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", this.configuration.get(TitanNodeImpl.GatewayRetryDelaySeconds));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.GatewayURL));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.InterNetworkAddress));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.KeyStoreFileName));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.LocalFileSystemRoot));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.LogFileSize));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.LogFileCount));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.MessageService));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.NodeAddress));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.ObjectStoreCapacity));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.Port));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.TaskPoolSize));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.ClientPoolSize));
            this.log.config("%s", this.configuration.get(TitanNodeImpl.Version));
        }
    }

    public Attributes getConfiguration() {
        return this.configuration;
    }
    
    /**
     * Run the given {@link Runnable} on this node's {@link ExecutorService}
     * @param runnable
     * @throws RejectedExecutionException if {@code runnable} cannot be accepted for execution
     */
    public void execute(Runnable runnable) throws RejectedExecutionException {
        this.tasks.execute(runnable);
    }

    public TitanServiceFramework getServiceFramework() {
        return this.services;
    }

    public String getProperties() {
        return this.configuration.toString();
    }
    
    /**
     * Return the JMX name of this Node.
     */
    public ObjectName getJMXObjectName() {
        return this.jmxObjectName;
    }

    /**
     * Get the {@link DOLRLogger} for this {@link TitanNodeImpl}
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
    public TitanGuid getNetworkObjectId() {
        return this.networkObjectId;
    }

    /**
     * Get this Node's {@link NodeAddress NodeAddress}.
     */
    public NodeAddress getNodeAddress() {
        return this.address;
    }

    public TitanNodeId getNodeId() {
        return this.address.getObjectId();
    }

    public NodeKey getNodeKey() {
        return this.nodeKey;
    }
    
    public Publishers getObjectPublishers() {
        return this.objectPublishers;
    }

    /**
     * Get this Node's {@link TitanObjectStore}.
     */
    public TitanObjectStore getObjectStore() {
        return this.store;
    }
    
    public String getProperty(String propertyName) {
        return this.configuration.get(new Attributes.Prototype(propertyName, null, null)).asString();
    }
    
    /**
     * Get (dynamically loading and instantiating, if necessary) an instance of the named class cast to the given {@link TitanService}.
     *
     * @param <C>
     * @param klasse The {@link Class} of the {@link TitanService} to get.
     * @return an instance of the named class cast to the given {@link TitanService} represented by {@code klasse}.
     * @throws ClassCastException if the loaded class is <em>not</em> an instance of {@code klasse}.
     */
    public <C> C getService(Class<? extends C> klasse) {
        try {
            return klasse.cast(this.getService(klasse.getName()));
        } catch (ClassNotFoundException e) {
            // This cannot happen.  The class is clearly handed in by the caller.
            throw new RuntimeException(e);
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }        
    }    

    /**
     * Get (dynamically loading and instantiating, if necessary) an instance of the named class cast to the given {@link TitanService}.
     *
     * @param serviceName
     * @return an instance of the named class cast to the given {@link TitanService}
     * @throws NullPointerException 
     * @throws ClassCastException if the loaded class is <em>not</em> an instance of {@code klasse}.
     * @throws ClassNotFoundException if the class cannot be found.
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws IllegalArgumentException 
     */
    public TitanService getService(final String serviceName) throws NullPointerException, ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return this.services.get(serviceName);
    }

    /**
     * Return the local file-system directory this Node uses for storing various
     * kinds of data.
     */
    public String getSpoolDirectory() {
        return this.spoolDirectory;
    }

    public ThreadGroup getThreadGroup() {
        return this.threadGroup;
    }

    /**
     * Get this Node's object-id as a String.
     */
    public String jmxGetObjectIdAsString() {
        return this.address.getObjectId().toString();
    }

    /**
     * Process a {@link TitanMessage} as input to this Node.
     * <p>
     * If the destination is this Node, then invoke the specified application
     * and return the resulting {@code BeehiveMessage} response.
     * </p>
     * <p>
     * Otherwise, if the {@code TitanMessage} is a PublishObject or UnpublishObject
     * message, then process via the receivePublishObject() or
     * receiveUnpublishObject() method and return the result.
     * </p>
     * <p>
     * If the {@code TitanMessage} is a multicast RouteToObject message, then process it
     * via the {@link #receiveRouteToObject(TitanMessage)} method and return the result.
     * </p>
     * <p>
     * If it is any other type of multicast {@code TitanMessage}, just process it by
     * invoking the specified application, which is responsible for further
     * transmission along the routing path.
     * </p>
     * <p>
     * If the {@code TitanMessage} can be routed on
     * to its destination transmit it and return the resulting {@code TitanMessage}
     * response.
     * </p>
     * <p>Otherwise, if this Node is the root of the {@code TitanMessage}'s
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
     * @return - The answering {@link TitanMessage} in response.
     */
    public TitanMessage receive(TitanMessage request) {
        if (this.log.isLoggable(Level.FINEST) || request.isTraced()) {
            this.log.finest("%s", request.toString());
        }
        
        try {
            TitanNodeId destinationNodeId = request.getDestinationNodeId();

            if (destinationNodeId == null) {
                throw new RuntimeException("destination node is null%n");
            }

            if (destinationNodeId.equals(this.getNodeId())) {
                // Any message other than route-to-node or route-to-object would indicate an ObjectId collision.
                return this.services.dispatch(request);
            }

            TitanMessage.Type type = request.getType();

            if (type == TitanMessage.Type.PublishObject) {
                return this.receivePublishObject(request);
            }

            if (type == TitanMessage.Type.UnpublishObject) {
                return this.receiveUnpublishObject(request);
            }

            if (type == TitanMessage.Type.RouteToObject) {
                return this.receiveRouteToObject(request);
            }

            // The message is a RouteToNode message.

            if (this.map.getRoute(destinationNodeId) == null) {
                // This node is the root of the destinationNodeId then we
                // must check to see if the message was sent to a particular node exactly.
                // If so, and we are NOT that node, return a reply indicating failure.
                // Otherwise, accept the message.
                if (request.isExactRouting() && !destinationNodeId.equals(this.getNodeId())) {
                    this.log.finest("routed to nonexistent node %s%n", destinationNodeId);

                    return request.composeReply(this.address, new TitanNode.NoSuchNodeException(destinationNodeId, "%s->%s", this.address.format(), destinationNodeId.toString()));
                }
                TitanMessage result = this.services.dispatch(request);

                if (request.isTraced()) {
                    this.log.finest("reply(%s)", result.traceReport());
                }
                return result;
            }

            TitanMessage result = this.transmit(request);

            return result;
        } catch (ClassCastException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (ClassNotFoundException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (TitanMessage.RemoteException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (IllegalArgumentException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (NoSuchMethodException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (InstantiationException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (IllegalAccessException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } catch (InvocationTargetException e) {
            this.log.severe("Internal message payload %s. Message %s%n", e, request);
            return request.composeReply(this.address, e);
        } finally {

        }
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
     * If this node is the root of the published object object-id, then dispatch the message to the {@link TitanObjectHandler} corresponding to the
     * published object's {@link AbstractObjectHandler}.
     * The {@link AbstractObjectHandler} is responsible for constructing and returning the reply {@link TitanMessage}.
     * </p>
     * <p>
     * PublishObjectMessages, while multicast in nature (ie. processed at each hop along the route to the destination node),
     * aren't really handled like multicast messages in that only the destination node invokes the published object {@link AbstractObjectHandler}.
     * True multicast messages are received by the associated {@link AbstractObjectHandler} at each node all along the routing path.
     * </p>
     * @see Publishers
     *
     * @param message The incoming {@link PublishObjectMessage}
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     * @throws IllegalArgumentException 
     */
    private TitanMessage receivePublishObject(TitanMessage message) throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (message.isTraced() || this.getLogger().isLoggable(Level.FINEST)) {
            this.log.finest("recv: %s", message.traceReport());
        }

        // If this message can be routed further, transmit it and simply return the reply.
        TitanMessage rootReply;
        if (this.map.getRoute(message.getDestinationNodeId()) != null) {
            rootReply = this.transmit(message);
        } else {
            // This node is the root for the published object.
            // Perform all the mandatory "rootness" functions here,
            // then hand this off to the object handler's publishObject(BeehiveMessage message)
            // the application specified in the published object's metadata.

            rootReply = this.services.dispatch(message);
        }

        // If the response from the root node signaled success, then get the Publish.Request
        // from the original message and store a back-pointer to the published object.
        // Otherwise, do NOT store a back-pointer.
        if (rootReply.getStatus().isSuccessful()) {
            try {
                Publish.PublishUnpublishRequest request = message.getPayload(Publish.PublishUnpublishRequest.class, this);
                for (Map.Entry<TitanGuid,TitanObject.Metadata> entry : request.getObjects().entrySet()) {
                    if (this.getLogger().isLoggable(Level.FINEST)) {
                        this.log.finest("%s->%s ttl=%ds", entry.getKey(), request.getPublisherAddress(), request.getSecondsToLive());
                    }
                    try {
                        this.objectPublishers.update(entry.getKey(),
                                new Publishers.PublishRecord(entry.getKey(), request.getPublisherAddress(), entry.getValue(), request.getSecondsToLive()));
                    } catch (IllegalStateException e) {
                        this.objectPublishers.remove(entry.getKey());
                    } catch (IOException e) {
                        this.objectPublishers.remove(entry.getKey());
                    } catch (AbstractStoredMap.OutOfSpace e) {
                        this.objectPublishers.remove(entry.getKey());
                    }
                }
            } catch (ClassNotFoundException e) {
                this.log.severe("Internal message payload %s. Message %s%n", e, message);
            } catch (ClassCastException e) {
                this.log.severe("Internal message payload %s. Message %s%n", message);
            } catch (RemoteException e) {
                this.log.severe("Internal message payload %s. Message %s%n", message);
            }
        }

        if (message.isTraced()) {
            this.log.info("recv(%s) reply(%s) return", message.traceReport(), rootReply.traceReport());
        }

        return rootReply;
    }

    /**
     * Receive a {@link RouteToObjectMessage}.
     * The object-id of the desired {@link TitanObject} is encoded in the
     * {@link TitanMessage#subjectId subjectId} of the received
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
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     * @throws IllegalArgumentException 
     */
    private TitanMessage receiveRouteToObject(TitanMessage request) throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (request.isTraced() || this.getLogger().isLoggable(Level.FINE)) {
            this.log.fine("recv: %s", request.traceReport());
        }

        // 1a
        if (request.subjectId.equals(this.getNodeId())) {
            //this.log.finest(request.subjectId + " 1a: I am target for application: " + request.getSubjectClass());
            return this.services.dispatch(request);
        }

        // We have the object locally, so send the message to the specified BeehiveService.
        if (this.store.containsObject(request.subjectId)) {
            //this.log.finest(request.subjectId + " 1b: I have objectId for application: " + request.getSubjectClass());
            return this.services.dispatch(request);
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
        TitanMessage proxyMessage = new TitanMessage(TitanMessage.Type.RouteToNode,
                request.getSource(),
                TitanNodeIdImpl.ANY,
                request.subjectId,
                request.getSubjectClass(),
                request.getSubjectClassMethod(),
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.EXACTLY,
                new byte[0]);
        proxyMessage.setRawPayload(request.getRawPayLoad());

        // To avoid a possible ConcurrentModificationException due to a publisher reporting that the object is not found and as a
        // consequence of that unpublish message we remove it from the set of publishers while concurrently using that set here
        // to iterate through the publishers, simply create a duplicate of the publisher Set.
        
        Set<Publishers.PublishRecord> publishers = new HashSet<Publishers.PublishRecord>();
        publishers.addAll(this.objectPublishers.getPublishers(request.subjectId));

        TitanMessage response;
        for (Publishers.PublishRecord publisher : publishers) {
            proxyMessage.setDestinationNodeId(publisher.getNodeId());
            if (request.isTraced()) {
                this.log.info("proxy msg=%5.5s... -> %s", request.getMessageId(), publisher.getNodeId());
            }

            // These checks need to be updated in synchrony with the results and exceptions embodied in the new form of TitanMessages,
            // where the status contains less information and more information is expressed in exceptions and return values.

            response = this.transmit(proxyMessage);
            
            if (response != null) {
                if (response.getStatus().isSuccessful()) {
                    return response;
                } else {
                    if (request.isTraced() || this.getLogger().isLoggable(Level.FINE)) {
                        this.log.fine("%5.5s...: %s failed. %s", request.getMessageId(), publisher, response.getStatus());
                    }
                    // We don't remove the bad publisher here because we are expecting the node
                    // that doesn't have the object to issue a remedial unpublish object. 
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
        return this.services.dispatch(request);
    }

    /**
     * Receive and process an {@link UnpublishObjectMessage}.
     * <p>
     * Delete any back-pointers to the object, and forward the message on to the next hop
     * along the routing path.  If this node terminates the routing path,
     * invoke the {@link TitanObjectHandler#unpublishObject(TitanMessage)} method of the object.
     * </p>
     * <p>
     * Each {@code UnublishObjectMessage}, while multicast in nature, is not really
     * handled like multicast messages.  True multicast messages are received
     * by the associated application all along the routing path.
     * UnpublishObject messages cause the associated application to receive the
     * message only at the root, or end, of the routing path.
     * </p>
     * @param request The received {@link UnpublishObjectMessage}
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws NoSuchMethodException 
     * @throws IllegalArgumentException 
     */
    private TitanMessage receiveUnpublishObject(TitanMessage request) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException, IllegalArgumentException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (request.isTraced() || this.getLogger().isLoggable(Level.FINE)) {
            this.log.info("recv %s: objectId=%s", request.traceReport(), request.getObjectId());
        }

        Publish.PublishUnpublishRequest unpublishRequest = request.getPayload(Publish.PublishUnpublishRequest.class, this);
        for (TitanGuid objectId : unpublishRequest.getObjects().keySet()) {
            // Remove the unpublished object from this node's publisher records.
            try {
                this.getObjectPublishers().remove(objectId, request.getSource().getObjectId());
            } catch (IllegalStateException e) {
                this.getObjectPublishers().remove(objectId);
            } catch (IOException e) {
                this.getObjectPublishers().remove(objectId);
            } catch (AbstractStoredMap.OutOfSpace e) {
                this.getObjectPublishers().remove(objectId);
            }
        }
        if (this.map.getRoute(request.getDestinationNodeId()) != null) {
            // Route the message on to the root.
            return this.transmit(request);
        } else {
            // This node is the root hand it out to the handler specified in the incoming BeehiveMessage.
            return this.services.dispatch(request);                
        }
    }
    
    /**
     * Transmit a {@link TitanMessage} to its destination, routing the {@code TitanMessage} through this {@link TitanNode}'s routing table.
     * <p>
     * Note: To transmit a message that is not first routed through this {@code TitanNode}'s routing table, but to a specific {@code TitanNode},
     * use the method {@link MessageService#transmit(NodeAddress, TitanMessage)} implemented by the
     * class specified in the {@link TitanNodeImpl#MessageService} configuration parameters.
     * </p>
     * <p>
     * Otherwise, the best practice is to use {@link TitanNodeImpl#receive(TitanMessage)} instead.
     * </p>
     */
    public TitanMessage transmit(TitanMessage message) {
        message.timeToLive--;
        if (message.timeToLive < 0) {
            this.log.severe("Message exceeded time-to-live: " + message.toString());
            new Exception().printStackTrace();
            return null;
        }

        message.timestamp = System.currentTimeMillis();
        if (message.getDestinationNodeId().equals(this.getNodeId())) {
            return this.receive(message);
        }

        TitanMessage reply;
        NodeAddress neighbour;
        while ((neighbour = this.getNeighbourMap().getRoute(message.getDestinationNodeId())) != null) {
            reply = this.messageService.transmit(neighbour, message);
            if (reply != null) {
                return reply;
            }
            this.log.warning("Removing dead neighbour %s", neighbour.format());

            // XXX Should also clean out any other remaining cached sockets to this neighbour.
            this.getNeighbourMap().remove(neighbour);
        }

        // If there is no next hop, then this node is the root of the destination objectId.
        // If the message is to be routed exactly then send back an error.
        if (message.isExactRouting()) {
            return message.composeReply(this.getNodeAddress(), DOLRStatus.NOT_FOUND);
        }

        return this.receive(message);
    }

    /**
     * Remove the specified stored object from this node.
     * <p>
     * Issue a Beehive UnpublishObject message
     * </p>
     * @param objectId
     */
    public boolean removeLocalObject(final TitanGuid objectId) throws ClassNotFoundException, TitanObjectStoreImpl.NotFoundException, TitanObjectStoreImpl.Exception, BeehiveObjectPool.Exception {
        TitanObject object = this.store.getAndLock(TitanObject.class, objectId);
        if (object == null) {
            return false;
        }
        try {
            this.store.remove(object);
            return true;
        } finally {
            this.store.unlock(object);
        }
    }

    public TitanMessage replyTo(TitanMessage message, Serializable serializable) {
        return message.composeReply(this.getNodeAddress(), serializable);
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
        XHTML.Table.Body executorBody = new XHTML.Table.Body(
                new XHTML.Table.Row(new XHTML.Table.Data("ActiveCount"), new XHTML.Table.Data(this.tasks.getActiveCount())),
                new XHTML.Table.Row(new XHTML.Table.Data("CorePoolSize"), new XHTML.Table.Data(this.tasks.getTaskCount())),
                new XHTML.Table.Row(new XHTML.Table.Data("CompletedTaskCount"), new XHTML.Table.Data(this.tasks.getCompletedTaskCount())),
                new XHTML.Table.Row(new XHTML.Table.Data("CorePoolSize"), new XHTML.Table.Data(this.tasks.getCorePoolSize())),
                new XHTML.Table.Row(new XHTML.Table.Data("Queue"), new XHTML.Table.Data(this.tasks.getQueue()))
                );

        XHTML.Div executors = new XHTML.Div(new XHTML.Table(new XHTML.Table.Caption("Executors"), executorBody));

        //XHTML.Table clientListener = (XHTML.Table) this.server.toXHTML();
        
        XHTML.Table.Body javaBody = new XHTML.Table.Body(
                new XHTML.Table.Row(new XHTML.Table.Data("Available Processors"), new XHTML.Table.Data(r.availableProcessors())),
                new XHTML.Table.Row(new XHTML.Table.Data("Max/Allocated Memory %s", Units.longToCapacityString(r.maxMemory())), new XHTML.Table.Data(allocatedMemoryBar)),
                new XHTML.Table.Row(new XHTML.Table.Data("Used Memory %%"), new XHTML.Table.Data(usedMemoryBar), new XHTML.Table.Data())
        );

        XHTML.Table javaTable = new XHTML.Table(new XHTML.Table.Caption("Java Environment"), javaBody).setClass("JavaEnvironment");

        return new XHTML.Table(
                new XHTML.Table.Caption("Resources"),
                new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data(javaTable, /*this.connMgr.getStatisticsAsXHTML(),*/ executors), new XHTML.Table.Data(this.threadResources()))
                    //new XHTML.Table.Row(new XHTML.Table.Data(clientListener), new XHTML.Table.Data())
                ));
    }

    public TitanMessage routeToNodeMulticast(TitanNodeId nodeId, String klasse, String method, Serializable data) {
        TitanMessage msg = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                klasse,
                method,
                TitanMessage.Transmission.MULTICAST,
                TitanMessage.Route.LOOSELY,
                data);

        TitanMessage reply = this.receive(msg);
        return reply;
    }

    public TitanMessage sendToMethod(String klasse, String method, Serializable data) {
        TitanMessage request = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                this.getNodeId(),
                this.getNodeId(),
                klasse,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                data);

            try {
                return this.services.dispatch(request);
            } catch (IllegalArgumentException e) {
                return request.composeReply(this.getNodeAddress(), e);
            } catch (ClassNotFoundException e) {
                return request.composeReply(this.getNodeAddress(), e);
            } catch (NoSuchMethodException e) {
                return request.composeReply(this.getNodeAddress(), e);
            } catch (InstantiationException e) {
                return request.composeReply(this.getNodeAddress(), e);
            } catch (IllegalAccessException e) {
                return request.composeReply(this.getNodeAddress(), e);
            } catch (InvocationTargetException e) {
                return request.composeReply(this.getNodeAddress(), e);
            }
    }
    
    public TitanMessage sendToNode(TitanNodeId nodeId, String klasse, String method, Serializable data) {
        TitanMessage msg = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                klasse,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                data);

        TitanMessage reply = this.receive(msg);
        return reply;
    }
    
    public TitanMessage sendToNode(TitanNodeId nodeId, TitanGuid objectId, String klasse, String method, Serializable data) {
        TitanMessage msg = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                objectId,
                klasse,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                data);

        TitanMessage reply = this.receive(msg);
        return reply;
    }

    public TitanMessage sendToNodeExactly(TitanNodeId nodeId, String objectClass, String method, Serializable data) throws NoSuchNodeException, ClassCastException, RemoteException, ClassNotFoundException {
        TitanMessage msg = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                nodeId,
                objectClass,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.EXACTLY,
                data);

        TitanMessage reply = this.receive(msg);
        
        if (reply.getStatus().equals(DOLRStatus.THROWABLE)) {
            try {
                reply.getPayload(Serializable.class, this);
            } catch (RemoteException e) {
                if (e.getCause() instanceof TitanNode.NoSuchNodeException) {
                    throw (TitanNode.NoSuchNodeException) e.getCause();
                }
                throw e;
            }            
        }
        if (reply.getStatus().equals(DOLRStatus.SERVICE_UNAVAILABLE)) {
            this.getLogger().info("NoSuchNodeException: %5.5s...", nodeId);
            throw new TitanNode.NoSuchNodeException(nodeId, "%s %s", nodeId, reply.getStatus());
        }
        return reply;
    }

    public TitanMessage sendToNodeExactly(TitanNodeId nodeId, TitanGuid objectId, String objectClass, String method, Serializable data) throws NoSuchNodeException, ClassCastException, RemoteException, ClassNotFoundException {
        TitanMessage msg = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.getNodeAddress(),
                nodeId,
                objectId,
                objectClass,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.EXACTLY,
                data);

        TitanMessage reply = this.receive(msg);
        
        if (reply.getStatus().equals(DOLRStatus.THROWABLE)) {
            try {
                reply.getPayload(Serializable.class, this);
            } catch (RemoteException e) {
                if (e.getCause() instanceof TitanNode.NoSuchNodeException) {
                    throw (TitanNode.NoSuchNodeException) e.getCause();
                }
                throw e;
            }            
        }
        if (reply.getStatus().equals(DOLRStatus.SERVICE_UNAVAILABLE)) {
            this.getLogger().info("NoSuchNodeException: %5.5s...", nodeId);
            throw new TitanNode.NoSuchNodeException(nodeId, "%s %s", nodeId, reply.getStatus());
        }
        return reply;
    }
    public TitanMessage sendToObject(TitanGuid objectId, String klasse, String method, Serializable data) {
        // It would be interesting to have a MULTICAST route-to-object
        // message which is sent to each known object.
        TitanMessage message = new TitanMessage(
                TitanMessage.Type.RouteToObject,
                this.getNodeAddress(),
                new TitanNodeIdImpl(objectId),
                objectId,
                klasse,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                data);
        TitanMessage reply = this.receive(message);
        return reply;
    }
    
    public TitanMessage sendToObject(TitanGuid objectId, String klasse, String method, Serializable data, boolean traced) {
        // It would be interesting to have a MULTICAST route-to-object
        // message which is sent to each known object.
        TitanMessage message = new TitanMessage(
                TitanMessage.Type.RouteToObject,
                this.getNodeAddress(),
                new TitanNodeIdImpl(objectId),
                objectId,
                klasse,
                method,
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                data);
        message.setTraced(traced);
        TitanMessage reply = this.receive(message);
        return reply;
    }
    
    /**
     * Start the node.
     * This consists of initialising all of the listening sockets,
     * establishing our first connection via a Join operation,
     * and booting each of the internal applications in this Node.
     */
    public Thread start() {
        this.startTime = System.currentTimeMillis();
        this.configuration.set(StartTime, System.currentTimeMillis());

        try {
            NodeAddress gateway = null;

            // If the gateway is specified, connect to it and get the initial configuration information.
            // If the gateway is not specified, then this node is the first node in a system.
            if (!this.configuration.isUnset(TitanNodeImpl.GatewayURL)) {
                while (true) {
                    URL url = new URL(this.configuration.asString(TitanNodeImpl.GatewayURL) + "/gateway");
                    if (!url.getProtocol().equals(this.configuration.asString(HTTPMessageService.Protocol))) {                        
                        this.log.warning("%s is specified to use the '%s' protocol, while %s is configured to respond to the '%s' protocol. Using %s.",
                                TitanNodeImpl.GatewayURL.getName(),
                                url.getProtocol(),
                                HTTPMessageService.Protocol.getName(),
                                this.configuration.asString(HTTPMessageService.Protocol),
                                this.configuration.asString(HTTPMessageService.Protocol));
                    }

                    try {                        
                        if (this.configuration.asString(HTTPMessageService.Protocol).equals("https")) {
                            String gatewayURL = String.format("https://%s:%d/gateway", url.getHost(), url.getPort());
                            HttpsURLConnection connection = HTTPMessageService.TitanHttpsURLConnection(this.getNodeKey(), gatewayURL);
                            OrderedProperties gatewayOptions = new OrderedProperties((InputStream) connection.getContent());
                            gateway = new NodeAddress(gatewayOptions.getProperty(TitanNodeImpl.NodeAddress.getName()));
                        } else {
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            OrderedProperties gatewayOptions = new OrderedProperties((InputStream) connection.getContent());
                            gateway = new NodeAddress(gatewayOptions.getProperty(TitanNodeImpl.NodeAddress.getName()));
                        }
                        break;
                    } catch (MalformedURLException fatal) {
                        throw new RuntimeException(fatal);
                    } catch (IOException e) {
                        this.log.severe("%s: %s will retry in %ss%n",
                                e.toString(), this.address.getInspectorInterface(), this.configuration.asLong(TitanNodeImpl.GatewayRetryDelaySeconds));
                    } catch (UnrecoverableKeyException e) {
                        throw new RuntimeException(e);
                    } catch (KeyManagementException e) {
                        throw new RuntimeException(e);
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    } catch (KeyStoreException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Thread.sleep(Time.secondsInMilliseconds(this.configuration.asLong(TitanNodeImpl.GatewayRetryDelaySeconds)));
                    } catch (InterruptedException e) {
                        /* */
                    }
                }
            } else {
                // If this node has no gateway, then it is the first node and gets to choose the network object-id.
                this.networkObjectId = new TitanGuidImpl();
            }

            if (gateway != null) {
                RoutingDaemon routingDaemon = this.getService(RoutingDaemon.class);
                RoutingDaemon.JoinOperation.Response join = routingDaemon.join(gateway);
                if (join == null) {
                    throw new RuntimeException(String.format("Cannot join with gateway: %s. Cannot start this node.", gateway.format()));
                }
                this.networkObjectId = join.getNetworkObjectId();
            }

            // Start up the services. For those Services that must be started in
            // order, start them manually here. In particular the MessageService
            // must be started first so it is able to receive and send messages.
            // Be careful, however, to start the MessageService AFTER the join
            // operation (which happens above). If a node rebooted, neighbour
            // nodes would still have an entry for the rebooted node in their
            // routing table. As a result the new node's join message would
            // actually be routed to itself.  Therefore, if it was accepting
            // inbound connections before it had completed the join process, it
            // would hang or return an empty routing table.
            // This means that the join operation must happen before the node
            // starts accepting inbound connections.

            // Start the server listening for incoming connections from other nodes to this node.
            this.messageService.start();

            // Let the service framework know we're started and know who our
            // neighbors are - it is now safe to start arbitrary services.
            this.services.startAll();

            synchronized (this) {
                this.notify();
            }
            return this.messageService.getServerThread();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        for (String name : this.services.keySet()) {
            try {
                TitanService application = this.services.get(name);
                if (application != null)
                    application.stop();
            } catch (NullPointerException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (ClassNotFoundException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (IllegalArgumentException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (NoSuchMethodException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (InstantiationException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (IllegalAccessException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            } catch (InvocationTargetException e) {
                if (this.log.isLoggable(Level.WARNING)) {
                    this.log.warning("Stopping %s resulted in %s", name, e);
                }
            }
        }
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
    
    public String toString() {
        return this.address.format();
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
        XHTML.Span dateTimeSpan = new XHTML.Span("Current Time: %1$td/%1$tm/%1$ty %1$tH:%1$tM:%1$tS", new Date());
        XHTML.Div metaInfo = new XHTML.Div(new XHTML.Heading.H1(this.address.getObjectId()), new XHTML.Break(), uptimeSpan, dateTimeSpan);

        new XHTML.Div(this.resourcesToXHTML()).setClass("section");
        
//        XML.Content routeTable = this.map.toXML();

        // Browser-side XSLT process is in terrible shape.
        // Do the processing here and one day, when the browsers catch up, remove this code and have the browser do the translation.

        XHTML.Div page = new XHTML.Div(
                metaInfo,
                this.services.toXHTML(uri, props),
                new XHTML.Div(this.map.toXHTML(uri, props)).setClass("section"),
                this.store.toXHTML(uri, props),
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

    public MessageService getMessageService() {
        return this.messageService;
    }

    /**
     * Run a single node.
     * Configuration parameters from the node are fetched from URLs supplied as the arguments to this class method.
     */    
    public static void main(String[] args) {
        // Read this command line argument as a URL to fetch configuration properties.

        OrderedProperties configurationProperties = new OrderedProperties();
        try {
            for (int i = 0; i < args.length; i++) {
                configurationProperties.load(new URL(args[i]));
            }
            TitanNodeImpl node = new TitanNodeImpl(configurationProperties);
            Thread thread = node.start();

            System.out.printf("%s [%s] %s%n", Time.ISO8601(System.currentTimeMillis()),
                    Time.formattedElapsedTime(System.currentTimeMillis() - Long.parseLong(node.getProperty(TitanNodeImpl.StartTime.getName()))), node.toString());
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
        } catch (AbstractStoredMap.OutOfSpace e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
