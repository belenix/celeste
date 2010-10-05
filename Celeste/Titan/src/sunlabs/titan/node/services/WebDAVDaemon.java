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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.management.JMException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.ajax.dojo.Dojo;
import sunlabs.asdf.web.http.AbstractURINameSpace;
import sunlabs.asdf.web.http.ClassLoaderBackend;
import sunlabs.asdf.web.http.FileSystemBackend;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTP.Message;
import sunlabs.asdf.web.http.HTTP.Server;
import sunlabs.asdf.web.http.HTTPServer;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.asdf.web.http.HttpUtil.PathName1;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.TappedInputStream;
import sunlabs.asdf.web.http.WebDAV;
import sunlabs.asdf.web.http.WebDAV.Backend;
import sunlabs.asdf.web.http.WebDAVNameSpace;
import sunlabs.asdf.web.http.WebDAVServerMain;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.NodeKey;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.node.services.api.MessageService;
import sunlabs.titan.node.services.api.Reflection;
import sunlabs.titan.util.OrderedProperties;

/**
 * This class implements the Titan HTTP management interface.
 * <p>
 * Although this is named "webdav" it is not really a webdav interface (yet).
 * </p>
 *
 */
public final class WebDAVDaemon extends AbstractTitanService implements MessageService, WebDAVDaemonMBean {
    private final static long serialVersionUID = 1L;

//            /** The URL of the base location of a Dojo installation. */
//             public final static Attributes.Prototype DojoRoot = new Attributes.Prototype(WebDAVDaemon.class, "DojoRoot", "http://o.aolcdn.com/dojo/1.5.0",
//            "The URL of the base location of a Dojo installation.");
//
//            /** The relative path of the Dojo script. */
//             public final static Attributes.Prototype DojoJavascript = new Attributes.Prototype(WebDAVDaemon.class, "DojoJavascript", "dojo/dojo.xd.js", "The relative path of the Dojo script.");
//
//            /** The value of the {@code djConfig} Dojo configuration parameter. */
//            public final static Attributes.Prototype DojoConfig = new Attributes.Prototype(WebDAVDaemon.class, "DojoConfig",
//                    "isDebug: false, parseOnLoad: true, baseUrl: './', useXDomain: true, modulePaths: {'sunlabs': '/dojo/1.3.1/sunlabs'}",
//            "The value of the djConfig Dojo configuration parameter");

    /** The URL of the base location of a Dojo installation. */
    public final static Attributes.Prototype DojoRoot = new Attributes.Prototype(WebDAVDaemon.class, "DojoRoot", "dojo/dojo-release-1.5.0",
    "The URL of the base location of a Dojo installation.");

    /** The relative path of the Dojo script. */
    public final static Attributes.Prototype DojoJavascript = new Attributes.Prototype(WebDAVDaemon.class, "DojoJavascript", "dojo/dojo.js", "The relative path of the Dojo script.");

    /** The value of the Dojo {@code djConfig} configuration parameter. */
    public final static Attributes.Prototype DojoConfig = new Attributes.Prototype(WebDAVDaemon.class, "DojoConfig",
            "isDebug: false, parseOnLoad: true, baseUrl: './dojo/dojo-release-1.5.0/dojo/', useXDomain: false, modulePaths: {'sunlabs': '/dojo/1.3.1/sunlabs'}",
    "The value of the Dojo djConfig configuration parameter");
    

    /** The Dojo theme name. */
    public final static Attributes.Prototype DojoTheme = new Attributes.Prototype(WebDAVDaemon.class, "DojoTheme", "tundra", "The Dojo theme name.");

    /** The TCP port number this node listens on for incoming HTTP connections. */
    public final static Attributes.Prototype ServerSocketPort = new Attributes.Prototype(WebDAVDaemon.class, "ServerSocketPort", 12001,
            "The TCP port number this node listens on for incoming HTTP connections.");
    
    /** The pathname prefix for the HTTP server to use when serving files. */
    public final static Attributes.Prototype ServerRoot = new Attributes.Prototype(WebDAVDaemon.class, "ServerRoot", "web",
            "The pathname prefix for the HTTP server to use when serving files.");
    
    /** The maximum number of concurrent clients. */
    public final static Attributes.Prototype ServerSocketMaximum = new Attributes.Prototype(WebDAVDaemon.class, "ServerSocketMaximum", 1030,
            "The maximum number of concurrent clients.");
    
    /** The number of milliseconds a connection may be idle before it is closed. */
    public final static Attributes.Prototype ServerSocketTimeoutMillis = new Attributes.Prototype(WebDAVDaemon.class, "ServerSocketTimeoutMillis", Time.secondsInMilliseconds(60),
            "The number of milliseconds a connection may be idle before it is closed.");
    
    /** The maximum queue length for incoming connections. The connection is refused if a connection indication arrives when the queue is full. */
    public static final Attributes.Prototype ServerSocketBacklog = new Attributes.Prototype(WebDAVDaemon.class, "ServerSocketBacklog",
            4,
    "The maximum queue length for incoming connections. The connection is refused if a connection indication arrives when the queue is full.");

    /** CSS files to be loaded by the Node inspector */
    public final static Attributes.Prototype InspectorCSS = new Attributes.Prototype(WebDAVDaemon.class, "InspectorCSS", "/css/DOLRStyle.css,/css/BeehiveColours.css",
            "The css file to use for the node inspector interface.");
    
    /** JavaScript files to be loaded by the node inspector interface */
    public final static Attributes.Prototype InspectorJS = new Attributes.Prototype(WebDAVDaemon.class, "InspectorJS", "/js/DOLRScript.js",
            "The JavaScript file to load for the node inspector interface.");

    /** The protocol to use, specify either 'http' or 'https' */
    public static final Attributes.Prototype Protocol = new Attributes.Prototype(WebDAVDaemon.class, "Protocol",
            "https",
    "The protocol to use, specify either 'http' or 'https'");

    public class TitanNodeMessageURINameSpace extends AbstractURINameSpace implements HTTP.URINameSpace {
        public TitanNodeMessageURINameSpace(HTTP.Server server, WebDAV.Backend backend) {
            super(server, backend);

            this.add(HTTP.Request.Method.GET, new HttpGet(this.server, this.backend));
            this.add(HTTP.Request.Method.PUT, new HttpPut(this.server, this.backend));
            this.add(HTTP.Request.Method.POST, new HttpPost(this.server, this.backend));
        }

        private class HttpGet implements HTTP.Request.Method.Handler {

            public HttpGet(Server server, Backend backend) {
                // TODO Auto-generated constructor stub
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)  {
                System.out.printf("TitanNodeMessageURINameSpace.HttpGet: %s%n", request);
                // TODO Auto-generated method stub
                return null;
            }                
        }

        private class HttpPut implements HTTP.Request.Method.Handler {

            public HttpPut(Server server, Backend backend) {
                // TODO Auto-generated constructor stub
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)  {
                // TODO Auto-generated method stub
                return null;
            }                
        }

        private class HttpPost implements HTTP.Request.Method.Handler {

            public HttpPost(Server server, Backend backend) {
                // TODO Auto-generated constructor stub
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)  {
                //                    System.out.printf("TitanNodeMessageURINameSpace.HttpPost%n%s%n", request);

                try {
                    TitanMessage message = TitanMessage.newInstance(request.getMessage().getBody().toInputStream());

                    TitanMessage response = WebDAVDaemon.this.node.receive(message);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    response.writeObject(new DataOutputStream(bos));
                    bos.close();
                    byte[] bytes = bos.toByteArray();
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.RawByteArray(new HttpHeader.ContentType(InternetMediaType.Application.OctetStream), bytes));
                } catch (IOException e) {
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s", e));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("%s", e));
                }
            }                
        }
    }          

    public class TitanNodeURINameSpace extends AbstractURINameSpace implements HTTP.URINameSpace {
        public TitanNodeURINameSpace(HTTP.Server server, WebDAV.Backend backend) {
            super(server, backend);
            
            HTTP.Request.Method.Handler get = new HttpGet(this.server, this.backend);
            
            this.add(HTTP.Request.Method.GET, get);
            this.add(HTTP.Request.Method.HEAD, get);
            this.add(HTTP.Request.Method.PUT, new HttpPut(this.server, this.backend));
            this.add(HTTP.Request.Method.POST, new HttpPost(this.server, this.backend));
        }
        
        /**
         * Instances of this class are invoked by the Phidoux web server to process an
         * HTTP protocol GET request from a client.
         */
        private class HttpGet implements HTTP.Request.Method.Handler {
            public HttpGet(HTTP.Server server, WebDAV.Backend backend) {
                super();
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
                try {
                    HttpContent.Multipart.FormData props = new HttpContent.Multipart.FormData(request.getURI());
                    return WebDAVDaemon.this.generateResponse(request, request.getURI(), props.getMap());
                }  catch (UnsupportedEncodingException unsupportedEncoding) {
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(unsupportedEncoding.getLocalizedMessage()));
                }
            }
        }

        /**
         * Instances of this class are invoked by the Phidoux web server to process an
         * HTTP protocol POST request from a client.
         */
        private class HttpPost implements HTTP.Request.Method.Handler {
            public HttpPost(HTTP.Server server, WebDAV.Backend backend) {
                super();
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
                try {
                    HTTP.Message.Body.MultiPart.FormData props = request.getMessage().decodeMultiPartFormData();
                    HttpHeader.UserAgent userAgent = (HttpHeader.UserAgent) request.getMessage().getHeader(HTTP.Message.Header.USERAGENT);
                    if (userAgent == null)
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(HttpHeader.USERAGENT + " header missing."));

                    return WebDAVDaemon.this.generateResponse(request, request.getURI(), props);
                } catch (IOException e) {
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.getLocalizedMessage() + " while reading request"));
                } catch (HTTP.BadRequestException e) {
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(e.getLocalizedMessage() + " while reading request"));
                }
            }
        }

        /**
         * Instances of this class are invoked to process an
         * HTTP protocol PUT request from a client.
         */
        private class HttpPut implements HTTP.Request.Method.Handler {
            public HttpPut(HTTP.Server server, WebDAV.Backend backend) {
                super();
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
                try {
                    HTTP.Message.Body.MultiPart.FormData avPairs = request.getMessage().decodeMultiPartFormData();
                    HttpHeader.UserAgent userAgent = (HttpHeader.UserAgent) request.getMessage().getHeader(HTTP.Message.Header.USERAGENT);
                    if (userAgent == null)
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(HttpHeader.USERAGENT + " header missing."));

                    System.out.println("PUT: " + request.getURI());
                    System.out.println("Path: " + request.getURI().getPath());
                    System.out.println("Query: " + request.getURI().getQuery());
                    Map<String,String> query = request.getURLEncoded();
                    for (String key : query.keySet()) {
                        System.out.println(key + " " + query.get(key));
                    }

                    return WebDAVDaemon.this.generateResponse(request, request.getURI(), avPairs);
                } catch (IOException e) {
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.getLocalizedMessage() + " while reading request"));
                } catch (HTTP.BadRequestException e) {
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(e.getLocalizedMessage() + " while reading request"));
                }
            }
        }
    }
    
    private class HTTPConnectionServer extends Thread {
        HTTPConnectionServer() throws IOException {
            super(WebDAVDaemon.this.node.getNodeId() + "." + WebDAVDaemon.this.getName());
        }

        @Override
        public void run() {
            ServerSocketChannel serverSocketChannel = null;

            try {                
                serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.configureBlocking(true);
                serverSocketChannel.socket().bind(new InetSocketAddress(WebDAVDaemon.this.node.getConfiguration().asInt(WebDAVDaemon.ServerSocketPort)));
                serverSocketChannel.socket().setReuseAddress(true);
            } catch (IOException e) {
                WebDAVDaemon.this.log.severe("Cannot accept connections: %s", e);
                return;
            }

            //  ExecutorService executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(WebDAVDaemon.this.clientMaximum,
            //  new SimpleThreadFactory(WebDAVDaemon.this.node.getObjectId() + ":" + WebDAVDaemon.this.getName() + ".Server"));
            
            // Figure out if we are running from a Jar file, or from the file system and setup the backend(s) appropriately.
            WebDAV.Backend backend;
            URL root = WebDAVServerMain.class.getClass().getResource("/");
            if (root == null) {
                backend = new ClassLoaderBackend(WebDAVServerMain.class.getClassLoader(), "web/");
            } else {
                backend = new FileSystemBackend("web/");                
            }

            while (true) {
                try {
                    SocketChannel clientSocket = serverSocketChannel.accept();
                    clientSocket.socket().setKeepAlive(false);
                    clientSocket.socket().setSoTimeout(WebDAVDaemon.this.node.getConfiguration().asInt(WebDAVDaemon.ServerSocketTimeoutMillis));

                    clientSocket.socket().setReceiveBufferSize(16*1024);
                    clientSocket.socket().setSendBufferSize(16*1024);

                    HTTPServer server = new HTTPServer(clientSocket.socket().getInputStream(), clientSocket.socket().getOutputStream());

                    server.setName(WebDAVDaemon.this.node.getNodeId().toString() + ":" + server.getName());

                    HTTP.URINameSpace messageNameSpace = new TitanNodeMessageURINameSpace(server, null);

                    HTTP.URINameSpace inspectorURINameSpace = new TitanNodeURINameSpace(server, null);
                    HTTP.URINameSpace fileURINameSpace = new WebDAVNameSpace(server, backend);
                    server.addNameSpace(new URI("/"), inspectorURINameSpace);
                    server.addNameSpace(new URI("/xsl"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/css"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/js"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/images"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/dojo"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/message"), messageNameSpace); 
                    server.start();
                    //executor.submit(server);
                } catch (SocketException e) {
                    WebDAVDaemon.this.log.severe("Connection failed: %s", e);
                } catch (URISyntaxException e) {
                    WebDAVDaemon.this.log.severe("Cannot setup connection: %s", e);
                } catch (IOException e) {
                    WebDAVDaemon.this.log.severe("Cannot setup connection: %s", e);
                }
            }
            // loop accepting connections and dispatching new thread to handle incoming requests.
        }
    }
    
    /**
     * This class handles unsecured inbound connections of several types to URLs of different 'namespaces'.
     *
     */
    private class HTTPSConnectionServer extends Thread  {
        private SSLContext sslContext;
        private SSLServerSocketFactory factory;

        public HTTPSConnectionServer() throws IOException {
            super(WebDAVDaemon.this.node.getThreadGroup(), WebDAVDaemon.this.node.getNodeId() + "." + WebDAVDaemon.this.getName());
            
            try {
                this.sslContext = WebDAVDaemon.this.node.getNodeKey().newSSLContext();
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
        }

        @Override
        public void run() {
            SSLServerSocket serverSocket = null;
            try {
                serverSocket = (SSLServerSocket) this.factory.createServerSocket(WebDAVDaemon.this.node.getConfiguration().asInt(WebDAVDaemon.ServerSocketPort),
                        WebDAVDaemon.this.node.getConfiguration().asInt(WebDAVDaemon.ServerSocketBacklog));

                serverSocket.setReuseAddress(true);
            } catch (IOException e) {
                if (WebDAVDaemon.this.log.isLoggable(Level.SEVERE)) {
                    WebDAVDaemon.this.node.getLogger().severe("Connection accept Thread terminated: %s", e);
                }
                // Terminate this thread, which means this service is no longer accepting connections.
                return;
            }

            //  ExecutorService executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(WebDAVDaemon.this.clientMaximum,
            //  new SimpleThreadFactory(WebDAVDaemon.this.node.getObjectId() + ":" + WebDAVDaemon.this.getName() + ".Server"));
            WebDAV.Backend backend;

            URL root = WebDAVServerMain.class.getClass().getResource("/");
            if (root == null) {
                backend = new ClassLoaderBackend(WebDAVServerMain.class.getClassLoader(), "web/");
            } else {
                backend = new FileSystemBackend("web/");                
            }

            while (true) {
                SSLSocket sslSocket = null;
                if (WebDAVDaemon.this.log.isLoggable(Level.FINEST)) {
                    WebDAVDaemon.this.log.finest("Accepting connections %s", serverSocket);
                }
                try {
                    sslSocket = (SSLSocket) serverSocket.accept();
                    sslSocket.setTcpNoDelay(true);
                    sslSocket.setKeepAlive(false);
                    sslSocket.setSoTimeout(WebDAVDaemon.this.node.getConfiguration().asInt(WebDAVDaemon.ServerSocketTimeoutMillis));
                    sslSocket.setReceiveBufferSize(16*1024);
                    sslSocket.setSendBufferSize(16*1024);
                    if (WebDAVDaemon.this.log.isLoggable(Level.FINEST)) {
                        WebDAVDaemon.this.log.finest("Accepted connection %s serverClosed=%b", sslSocket, serverSocket.isClosed());
                    }

                    HTTPServer server = new HTTPServer(sslSocket.getInputStream(), sslSocket.getOutputStream());

                    server.setName(WebDAVDaemon.this.node.getNodeId().toString() + ":" + server.getName());

                    HTTP.URINameSpace messageNameSpace = new TitanNodeMessageURINameSpace(server, null);

                    HTTP.URINameSpace titanNodeURINameSpace = new TitanNodeURINameSpace(server, null);
                    HTTP.URINameSpace fileURINameSpace = new WebDAVNameSpace(server, backend);
                    server.addNameSpace(new URI("/"), titanNodeURINameSpace);
                    server.addNameSpace(new URI("/xsl"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/css"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/js"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/images"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/dojo"), fileURINameSpace); // This namespace is in the filesystem/jar file
                    server.addNameSpace(new URI("/message"), messageNameSpace); 
                    server.start();

                    if (WebDAVDaemon.this.log.isLoggable(Level.FINEST)) {
                        WebDAVDaemon.this.log.finest("Dispatched connection %s serverClosed=%b", sslSocket, serverSocket.isClosed());
                    }
                } catch (IOException e) {
                    if (WebDAVDaemon.this.log.isLoggable(Level.SEVERE)) {
                        WebDAVDaemon.this.node.getLogger().severe("Connection accept Thread terminated: %s serverSocket=%s serverClosed=%b", e, serverSocket, serverSocket.isClosed());
                    }
                    return;
                } catch (URISyntaxException e) {
                    if (WebDAVDaemon.this.log.isLoggable(Level.SEVERE)) {
                        WebDAVDaemon.this.node.getLogger().severe("Connection accept Thread terminated: %s", e);
                    }
                    return;
                } finally {
                    // Should close the connection manager's server socket.
                }            
            }
            // loop accepting connections and dispatching new thread to handle incoming requests.
        }
    }

    /**
     * Produce an {@link sunlabs.asdf.web.xml XHTML.Anchor} element that links to a XHTML document that
     * inspects the {@link TitanObject} identified by the given {@link TitanGuidImpl}.
     * 
     * See {@link WebDAVDaemon} for the dispatch of the HTTP request the link induces.
     */
    public static XHTML.Anchor inspectObjectXHTML(TitanGuid objectId) {
        XHTML.Anchor link = new XHTML.Anchor(Xxhtml.Attr.HRef("/inspect/" + objectId))
        	.add(objectId.toString())
        	.setClass("ObjectId")
        	.setTitle("Inspect Object");
        return link;
    }
    

    public static XHTML.Anchor inspectServiceXHTML(String name) {
    	XHTML.Anchor link = new XHTML.Anchor(Xxhtml.Attr.HRef("/service/" + name))
    	.add(name)
    	.setClass("ServiceName")
    	.setTitle("Inspect Service");
    	return link;
    }

    /**
     * Produce an {@link sunlabs.asdf.web.xml XHTML.Anchor} element that links to a HTML document that
     * inspects the {@link TitanNodeImpl} identified by the given {@link NodeAddress}.
     * 
     * See {@link WebDAVDaemon} for the dispatch of the HTTP request the link induces.
     * @throws MalformedURLException 
     */
    public static XHTML.Anchor inspectNodeXHTML(NodeAddress address) {
    	return new XHTML.Anchor("%s", address.getObjectId()).setHref(address.getInspectorInterface()).setClass("NodeId");
    }
        
    private static String revision = Release.ThisRevision();
    
    transient private Thread daemon;

    /*
     * This is the method to use to transmit a TitanMessage to a NodeAddress
     */
    private Method transmitMethod;

    public WebDAVDaemon(final TitanNode node) throws JMException, SecurityException, NoSuchMethodException {
        super(node, AbstractTitanService.makeName(WebDAVDaemon.class, WebDAVDaemon.serialVersionUID), "Titan http/https Interface");

        node.getConfiguration().add(WebDAVDaemon.ServerSocketPort);
        node.getConfiguration().add(WebDAVDaemon.ServerRoot);
        node.getConfiguration().add(WebDAVDaemon.ServerSocketMaximum);
        node.getConfiguration().add(WebDAVDaemon.ServerSocketTimeoutMillis);
        node.getConfiguration().add(WebDAVDaemon.InspectorCSS);
        node.getConfiguration().add(WebDAVDaemon.InspectorJS);
        node.getConfiguration().add(WebDAVDaemon.ServerSocketBacklog);
        node.getConfiguration().add(WebDAVDaemon.Protocol);
        node.getConfiguration().add(WebDAVDaemon.DojoConfig);
        node.getConfiguration().add(WebDAVDaemon.DojoJavascript);
        node.getConfiguration().add(WebDAVDaemon.DojoRoot);
        node.getConfiguration().add(WebDAVDaemon.DojoTheme);

        // Setup the transmit method. (See transmit(NodeAddress, TitanMessage).
        if (WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.Protocol).equals("https")) {
            this.transmitMethod = this.getClass().getMethod("transmitHTTPS", NodeAddress.class, TitanMessage.class);
        } else {
            this.transmitMethod = this.getClass().getMethod("transmitHTTP", NodeAddress.class, TitanMessage.class);
        }

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.ServerSocketPort));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.ServerRoot));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.ServerSocketMaximum));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.ServerSocketTimeoutMillis));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.InspectorCSS));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.InspectorJS));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.ServerSocketBacklog));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.Protocol));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.DojoConfig));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.DojoJavascript));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.DojoRoot));
            this.log.config("%s", node.getConfiguration().get(WebDAVDaemon.DojoTheme));
        }
    }

    /**
     * This produces the resulting {@link HTTP.Response} to the client interface.
     */
    private HTTP.Response generateResponse(HTTP.Request request, final URI uri, Map<String, Message> map) {
        //
        // Synthesize a URL namespace for parts of the Node.
        //
        // /map is the neighbour map
        // /service/<name> is for the service named <name>
        // /gateway is gateway information about this node.
        // /object/<objectId> fetches the named DOLR object as a application/octet-stream
        // storeObject
        // retrieveObject
        // findObject
        // invokeObject
        // deleteObject
        //
        
        try {
            if (!uri.getPath().equals("/")) {
                if (uri.getPath().equals("/map")) {
                	return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(this.node.getNeighbourMap().toString() + this.node.getNodeAddress() + "\n"));
                } else if (uri.getPath().startsWith("/service/")) {
                	XHTML.EFlow folio = this.node.getServiceFramework().toXHTML(uri, map);
                	if (folio != null) {
                		XHTML.Body body = new XHTML.Body(folio);
                		return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(this.makeDocument(name, body, null, null)));
                	}
                	return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Unknown service: " + name));
                } else if (uri.getPath().startsWith("/census")) {
                    Census census = this.node.getService(CensusDaemon.class);
                    Map<TitanNodeId,OrderedProperties> list = census.select(0);

                    StringBuilder string = new StringBuilder().append(list.size()).append("\n");
                    for (TitanNodeId node : new TreeSet<TitanNodeId>(list.keySet())) {
                        string.append(node.toString()).append("\n");
                    }

                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(string.toString()));
                } else if (uri.getPath().startsWith("/gateway")) {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(this.node.getConfiguration().toString()));
                } else if (uri.getPath().startsWith("/config")) {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(this.node.getConfiguration().toString()));
                } else if (uri.getPath().startsWith("/reflect")) {
                    Reflection reflection = this.node.getService(ReflectionService.class);
                    if (reflection != null) {
                        return new HttpResponse(HTTP.Response.Status.OK,
                                new HttpContent.Text.HTML(this.makeDocument("Reflection", new XHTML.Body(reflection.toXHTML(uri, map)), null, null))); 
                    }
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Unknown service: " + name));
                } else if (uri.getPath().startsWith("/route-table")) {
                    XML.Document document = this.makeXMLDocument(this.node.getNeighbourMap().toXML(), "/xsl/beehive-route-table.xsl");
                    // The intent here was to produce XML with an XML stylesheet for client-side translation to XHTML.
                    // That doesn't work out very well in current browsers.
                    // So until they get better, getRouteTableAsXML produces XML and then here we perform the translation producing a String containing the XHTML.
                    // However, force the content type of the response to be text/html and NOT application/xml.
                    String query = request.getURI().getQuery();
                    if (query != null && query.equals("xml")) {
                        HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.XML(document));
                        return response;
                    }
                    
                    String xml = applyXSLT("/xsl/beehive-route-table.xsl", document);
                    HttpContent.Text.XML content = new HttpContent.Text.XML(xml);
                    content.setContentType(new HttpHeader.ContentType(InternetMediaType.Text.HTML));
                    return new HttpResponse(HTTP.Response.Status.OK, content);
                } else if (uri.getPath().startsWith("/node")) {
                    // The intent here was to produce XML with an XML stylesheet for client-side translation to XHTML.
                    // That doesn't work out very well in current browsers.
                    // So until they get better, getRouteTableAsXML produces XML and then here we perform the translation producing a String containing the XHTML.
                    // However, force the content type of the response to be text/html and NOT application/xml.
                    XML.Document document = this.makeXMLDocument(this.node.toXML(), "/xsl/node.xsl");
                    String query = request.getURI().getQuery();
                    if (query != null && query.equals("xml")) {
                        HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.XML(document));
                        return response;
                    }
                    
                    String xml = applyXSLT("/xsl/node.xsl", document);
                    HttpContent.Text.XML content = new HttpContent.Text.XML(xml);
                    content.setContentType(new HttpHeader.ContentType(InternetMediaType.Text.HTML));
                    return new HttpResponse(HTTP.Response.Status.OK, content);
                } else if (uri.getPath().startsWith("/objectType")) {
                    String objectId = uri.getPath().substring("/objectType/".length());

                    Reflection reflection = this.node.getService(ReflectionService.class);
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(reflection.getObjectType(new TitanGuidImpl(objectId))));
                } else if (uri.getPath().startsWith("/inspect/")) {
                	TitanGuid objectId = new TitanGuidImpl(uri.getPath().substring("/inspect/".length()));
                	Reflection reflection = this.node.getService(ReflectionService.class);
                	try {
                		XHTML.EFlow eflow = reflection.inspectObject(objectId, uri, map);
                		return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(this.makeDocument(objectId.toString(), new XHTML.Body(eflow), null, null)));      
                	} catch (BeehiveObjectStore.NotFoundException e) {
                		return new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain(HTTP.Response.Status.NOT_FOUND.toString() + " " + objectId.toString()));
                	}
                }

                // Read a file from the local file system, or from the jar file that this is running from.
                try {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.RawInputStream(WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.ServerRoot), uri.getPath()));
                } catch (java.io.FileNotFoundException e) {
                    System.err.printf("%s%n", request);
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain(e.toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.toString()));
                }
            }

            //
            String action = HttpMessage.asString(map.get("action"), null);

            if (action != null) {
                if (action.equals("disconnect")) {
                    this.node.getNeighbourMap().remove(new NodeAddress(HttpMessage.asString(map.get("address"), null)));
                } else if (action.equals("unpublish-object")) {
                    String objectId = HttpMessage.asString(map.get("objectId"), null);

                    if (objectId == null || objectId.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing object-id"));
                    }
                    this.node.removeLocalObject(new TitanGuidImpl(objectId));

                    XHTML.Document result = makeDocument(this.node.getNodeId().toString(), new XHTML.Body(this.node.toXHTML(uri, map)), null, null);
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(result));
                } else if (action.equals("remove-object")) {
                    String oid = HttpMessage.asString(map.get("objectId"), null);

                    if (oid == null || oid.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing object-id"));
                    }
                    TitanGuid objectId = new TitanGuidImpl(oid);
                    this.node.getObjectStore().lock(objectId);
                    try {
                        this.node.getObjectStore().remove(objectId);
                        XHTML.Document result = makeDocument(this.node.getNodeId().toString(), new XHTML.Body(this.node.toXHTML(uri, map)), null, null);
                        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(result));
                    } finally {
                        this.node.getObjectStore().unlock(objectId);
                    }
                } else if (action.equals("die")) {
                    this.node.stop();
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("should be dead"));
                } else if (action.equals("connect")) {
                    String location = HttpMessage.asString(map.get("node"), "");
                    if (location.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing node specification"));
                    }
                    // This is the same algorithm as in TitanNodeImpl.start().
                    // This should be coalesced.
                    try {
                        //
                        // If location is a URL, use Locator to get a NodeAddress.
                        // If location is a NodeAddress add directly
                        // Otherwise, fail
                        //
                        BufferedReader r = null;
                        try {
                            URL url = new URL(location + "/gateway");
                            OrderedProperties gatewayOptions = new OrderedProperties((InputStream) url.openConnection().getContent());
                            location = gatewayOptions.getProperty(TitanNodeImpl.NodeAddress.getName());
                        } catch (MalformedURLException e) {
                            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Malformed gateway address: " + location));
                        } finally {
                            if (r != null) try { r.close(); } catch (IOException ignore) { }
                        }
                        // this might be a bad idea if the node we are adding has the same prefix as our own
                        // suddenly our own top-level entry in the map is replaced by somebody else
                        // however, conditional adding is also not necessarily what we want, since we may be
                        // trying to manually coalesce two disjoint networks -- trying to fill a slot that is already
                        // filled by another node of our own network.
                        // so, we potentially break things, and then immediately fix them
                        //this.node.getNeighbourMap().put(new NodeAddress(location));
                        //this.node.getNeighbourMap().put(this.node.getAddress());
                        this.node.getNeighbourMap().add(new NodeAddress(location));
                        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(makeDocument(this.node.getNodeId().toString(), new XHTML.Body(this.node.toXHTML(uri, map)), null, null)));
                    } catch (IOException e) {
                        return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.toString() + " " + e.getLocalizedMessage()));
                    }
                    // unknown "action"
                }
            }

            XHTML.Document result = makeDocument(this.node.getNodeId().toString(), new XHTML.Body(this.node.toXHTML(uri, map)), null, null);

//            try {
//                OutputStream out = new FileOutputStream("WebDAVDaemon.out");
//                out.write(result.toString().getBytes());
//                out.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(result));
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.toString() + " " + e.getLocalizedMessage()));
        }
    }

    /**
     * This produces a uniform {@link XHTML.Document} for all purposes.
     *
     * @param title
     * @param body
     * @return
     */
    private XHTML.Document makeDocument(String title, XHTML.Body body, XHTML.Link[] styleSheets, XHTML.Script[] scripts) throws URISyntaxException {
        List<XHTML.Link> styleLinks = new LinkedList<XHTML.Link>();

        for (String url : this.node.getConfiguration().get(WebDAVDaemon.InspectorCSS).asStringArray(",")) {
            styleLinks.add(Xxhtml.Stylesheet(url).setMedia("all"));
        }
        if (styleSheets != null){
        	for (XHTML.Link l : styleSheets) {
        		styleLinks.add(l);
        	}
        }

        List<XHTML.Script> scriptLinks = new LinkedList<XHTML.Script>();
        for (String url : this.node.getConfiguration().get(WebDAVDaemon.InspectorJS).asStringArray(",")) {
            scriptLinks.add(new XHTML.Script("text/javascript").setSource(url));
        }
        if (scripts != null){
        	for (XHTML.Script l : scripts) {
        		scriptLinks.add(l);
        	}
        }

        Dojo dojo = new Dojo(this.node.getConfiguration().asString(WebDAVDaemon.DojoRoot),
                this.node.getConfiguration().asString(WebDAVDaemon.DojoJavascript),
                this.node.getConfiguration().asString(WebDAVDaemon.DojoTheme)
                );


        dojo.setConfig(this.node.getConfiguration().asString(WebDAVDaemon.DojoConfig));
        dojo.requires("dojo.parser", "sunlabs.StickyTooltip", "dijit.ProgressBar");

        XHTML.Head head = new XHTML.Head();
        dojo.dojoify(head);
        head.add(styleLinks.toArray(new XHTML.Link[0]));
        head.add(scriptLinks.toArray(new XHTML.Script[0]));
        head.add(new XHTML.Title(title));

        XHTML.Div validate = new XHTML.Div(new XHTML.Anchor("validate").setHref(XHTML.SafeURL("http://validator.w3.org/check/referrer")).setClass("bracket"));

        XHTML.Div copyright = new XHTML.Div(new XHTML.Div(revision).setClass("release"),
                new XHTML.Div(Copyright.miniNotice).setClass("copyright"));

        body.add(copyright, validate);
        body.setClass(dojo.getTheme()).addClass(name);
        
        XML.ProcessingInstruction stylesheet = new XML.ProcessingInstruction("xml-stylesheet");
        stylesheet.addAttribute(new XML.Attr("type", InternetMediaType.Application.XSLT), new XML.Attr("href", "/xsl/beehive-route-table.xsl" ));

        XHTML.Document document = new XHTML.Document();
        document.add(stylesheet);
        document.add(new XHTML.Html(head, body));
        
        return document;
    }

    @Override
    public synchronized void start() {
        if (this.isStarted()) {
            return;
        }
        super.start();
        
        if (this.daemon != null) {
            if (WebDAVDaemon.this.log.isLoggable(Level.WARNING)) {
                WebDAVDaemon.this.log.warning("Already started");
            }
            // We've already been started
            return;
        }

        this.setStatus("Initializing");

        try {
            if (WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.Protocol).equals("https")) {
                this.daemon = new HTTPSConnectionServer();
            } else {
                this.daemon = new HTTPConnectionServer();
            }
            this.daemon.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Thread getServerThread() {
        return (Thread) this.daemon;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
        
    public XML.Document makeXMLDocument(XML.Content xml, String...stylesheets) {
        XML.Document document = new XML.Document();
        for (String s : stylesheets) {
            XML.Node stylesheet = new XML.ProcessingInstruction("xml-stylesheet")
                .addAttribute(new XML.Attr("type", InternetMediaType.Application.XSLT), new XML.Attr("href", s));
            document.append(stylesheet);
        }
        document.append(xml);

        return document;
    }
    
    /**
     * Open a file or a resource in a jar file.
     * <p>
     * If the application is running in a Jar file, open the named resource in the jar file.
     * Otherwise, open the same named file in the filesystem.
     * </p>
     * @param absolutePath the path of the resource or file to open. 
     * @throws FileNotFoundException
     */
    public InputStream openInputResource(String absolutePath) throws FileNotFoundException {
        URL root = this.getClass().getResource("/");

        //    String absolutePath = "/" + localRoot + "/" + localPath;
        if (root == null) { // We are inside of a .jar file.
            // paths beginning with '/' are matched directly with the path in the jar file.
            // Otherwise, it is prefixed by the package name.
            String fullPath = "/" + WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.ServerRoot) + absolutePath;
            InputStream inputStream = this.getClass().getResourceAsStream(fullPath);
            if (inputStream == null) {
                throw new FileNotFoundException(fullPath);
            }
            return inputStream;
        }
        
        File file = new File(new File(WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.ServerRoot)).getAbsolutePath(), new PathName1(absolutePath).toString());
        return new FileInputStream(file);
    }
    
    public class Resolver implements URIResolver {
        HttpUtil.PathName dirname;
        
        public Resolver(HttpUtil.PathName dirname) {
            this.dirname = dirname;
        }

        public Source resolve(String href, String base) throws TransformerException {
            HttpUtil.PathName root = new HttpUtil.PathName(WebDAVDaemon.this.node.getConfiguration().asString(WebDAVDaemon.ServerRoot));
            
            HttpUtil.PathName ref = new HttpUtil.PathName(href);
            if (ref.isAbsolute()) {
                return new StreamSource(new File(root.append(ref).toString()));
            }
            if (base == null) {
                return new StreamSource(new File(root.append(dirname.append(href)).toString()));
            }
            // XXX Not sure of what to do here.  Need an example of how we get here.
            return null;
        }
    }
    
    /**
     * Apply the given XSLT stylesheet to the given XML.Document.
     * 
     * @param stylesheet
     * @param document
     * @return A String containing the result of the XLST transformation.
     * @throws FileNotFoundException if the stylesheet could not be opened as a file or Jar resource.
     */
    public String applyXSLT(String stylesheet, XML.Document document) throws FileNotFoundException {
        InputStream inputStream = openInputResource(stylesheet);
        StreamSource xslSource = new javax.xml.transform.stream.StreamSource(inputStream);
        StreamSource xmlSource = new javax.xml.transform.stream.StreamSource(new ByteArrayInputStream(document.toString().getBytes()));

        TransformerFactory tFactory = TransformerFactory.newInstance();
        tFactory.setURIResolver(new Resolver(new HttpUtil.PathName(stylesheet).dirName()));
        try {

            Templates templates = tFactory.newTemplates(xslSource);

            Transformer transformer = templates.newTransformer();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new javax.xml.transform.stream.StreamResult(bos);
            transformer.transform(xmlSource, result);
            return bos.toString();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
            return null;      
        } catch (TransformerException e) {
            e.printStackTrace();
            return null;         
        }
    }

    public static HttpsURLConnection TitanHttpsURLConnection(NodeKey nodeKey, String urlString) throws UnrecoverableKeyException, KeyManagementException,
    NoSuchAlgorithmException, KeyStoreException, IOException {
        SSLContext SSL_CONTEXT = nodeKey.newSSLContext();
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        };
        connection.setHostnameVerifier(hv);
        return connection;
    }
    
    public TitanMessage transmitHTTP(NodeAddress addr, TitanMessage message) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            message.writeObject(new DataOutputStream(bos));
            bos.close();
            byte[] bytes = bos.toByteArray();

            String s = String.format("http://%s:%d/message/%s/%s",
                    addr.getInspectorInterface().getHost(), addr.getInspectorInterface().getPort(),
                    message.getSubjectClass(),
                    message.getSubjectClassMethod());
            
            HttpURLConnection connection = (HttpURLConnection) new URL(s).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty(HttpHeader.CONTENTTYPE, InternetMediaType.Application.OctetStream.toString());
            connection.setRequestProperty(HttpHeader.CONTENTLENGTH, Integer.toString(bytes.length));

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            
            OutputStream out = connection.getOutputStream();
            out.write(bytes);
            out.close();

            //Get Response    
            InputStream is = connection.getInputStream();
            try {
                TitanMessage response = TitanMessage.newInstance(is);
                return response;
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                is.close(); // XXX cache this socket....
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public TitanMessage transmitHTTPS(NodeAddress addr, TitanMessage message) {
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
//                System.out.println("HostnameVerifier: verify(" + urlHostName + ", " + session.getPeerHost() + ")");
                return true;
            }
        };
        
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            message.writeObject(new DataOutputStream(bos));
            bos.close();
            byte[] bytes = bos.toByteArray();
            
            //  Hacking the messageURL into the inspectorURL here.

            String s = String.format("https://%s:%d/message/%s/%s",
                    addr.getInspectorInterface().getHost(), addr.getInspectorInterface().getPort(),
                    message.getSubjectClass(), message.getSubjectClassMethod());
            HttpsURLConnection connection = TitanHttpsURLConnection(this.node.getNodeKey(), s);

            connection.setRequestMethod("POST");
            connection.setRequestProperty(HttpHeader.CONTENTTYPE, InternetMediaType.Application.OctetStream.toString());
            connection.setRequestProperty(HttpHeader.CONTENTLENGTH, Integer.toString(bytes.length));

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setHostnameVerifier(hv);

            OutputStream out = connection.getOutputStream();
            out.write(bytes);
            out.close();

            //Get Response    
            InputStream is = connection.getInputStream();
            try {
                TitanMessage response = TitanMessage.newInstance(is);
                return response;
            } finally {
                is.close(); // XXX cache this socket....
            }
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (KeyManagementException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (KeyStoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            
        }
        
        return null;
    }

    public TitanMessage transmit(NodeAddress addr, TitanMessage message) {
        try {
            return (TitanMessage) this.transmitMethod.invoke(this, addr, message);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
