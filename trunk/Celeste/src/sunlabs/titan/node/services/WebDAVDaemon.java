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
package sunlabs.titan.node.services;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
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
import sunlabs.asdf.web.XML.XML.Document;
import sunlabs.asdf.web.ajax.dojo.Dojo;
import sunlabs.asdf.web.http.ClassLoaderBackend;
import sunlabs.asdf.web.http.FileSystemBackend;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPServer;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.WebDAV;
import sunlabs.asdf.web.http.WebDAVNameSpace;
import sunlabs.asdf.web.http.WebDAVServerMain;
import sunlabs.asdf.web.http.HTTP.Request;
import sunlabs.asdf.web.http.HTTP.Request.Method;
import sunlabs.asdf.web.http.HttpUtil.PathName1;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.NeighbourMap;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.node.services.api.Reflection;
import sunlabs.titan.util.OrderedProperties;

/**
 * This class implements the Beehive HTTP management interface.
 * <p>
 * Although this is named "webdav" it is not (yet).
 * </p>
 *
 */
public final class WebDAVDaemon extends BeehiveService implements WebDAVDaemonMBean {
    private final static long serialVersionUID = 1L;

    private class Daemon extends Thread  {
        /**
         * Instances of this class are invoked to process an
         * HTTP protocol GET request from a client.
         */
        private class HttpGet implements HTTP.Request.Method.Handler {
            public HttpGet(HTTP.Server server, WebDAV.Backend backend) {
                super();
            }

            public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) {
                try {
                    HttpContent.Multipart.FormData props = new HttpContent.Multipart.FormData(request.getURI());
                    return WebDAVDaemon.this.generateResponse(request, request.getURI(), props);
                }  catch (UnsupportedEncodingException unsupportedEncoding) {
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(unsupportedEncoding.getLocalizedMessage()));
                }
            }
        }

        /**
         * Instances of this class are invoked to process an
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

//        public class SimpleThreadFactory implements ThreadFactory {
//            private String name;
//            private long counter;
//
//            public SimpleThreadFactory(String name) {
//                this.name = name;
//                this.counter = 0;
//            }
//
//            public Thread newThread(Runnable r) {
//                Thread thread = new Thread(r);
//                thread.setName(String.format("%s-pool-%03d", this.name, this.counter));
//                this.counter++;
//                return thread;
//            }
//        }

        public class BeehiveNodeNameSpace implements HTTP.NameSpace {
            private Map<HTTP.Request.Method,HTTP.Request.Method.Handler> methods;
            private HTTP.Server server;
            private WebDAV.Backend backend;

            public BeehiveNodeNameSpace(HTTP.Server server, WebDAV.Backend backend) {
                this.server = server;
                this.backend = backend;

                this.methods = new HashMap<HTTP.Request.Method,HTTP.Request.Method.Handler>();
                this.add(HTTP.Request.Method.GET, new HttpGet(this.server, this.backend));
                this.add(HTTP.Request.Method.PUT, new HttpPut(this.server, this.backend));
                this.add(HTTP.Request.Method.POST, new HttpPost(this.server, this.backend));
//                this.add(HTTP.Request.Method.OPTIONS, new WebDAVOptions(this.server, this.backend));
            }

            public void add(HTTP.Request.Method method, HTTP.Request.Method.Handler methodHandler) {
                this.methods.put(method, methodHandler);
            }
            
            public HTTP.Request.Method.Handler get(HTTP.Request.Method method) {
                return this.methods.get(method);
            }

            public HTTP.Response dispatch(HTTP.Request request, HTTP.Identity identity) {
                HTTP.Request.Method.Handler methodHandler = this.methods.get(request.getMethod());
                HTTP.Response response = null;
                try {
                    response = methodHandler.execute(request, identity);
                } catch (HTTP.UnauthorizedException e) {
                    // There was no Authorization header and one is required.  Reply signaling that authorisation is required.
                    response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
                    response.getMessage().addHeader(new HttpHeader.WWWAuthenticate(e.getAuthenticate()));
                } catch (HTTP.Exception e) {
                    response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
                }
                return response;
            }

            public Collection<Method> getAccessAllowed() {
                // TODO Auto-generated method stub
                return null;
            }
        }
        
        Daemon() {
            super(WebDAVDaemon.this.node.getObjectId() + "." + WebDAVDaemon.this.getName());
        }
        
        @Override
        public void run() {
            try {                
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.configureBlocking(true);
                serverSocketChannel.socket().bind(new InetSocketAddress(WebDAVDaemon.this.node.configuration.asInt(WebDAVDaemon.Port)));
                serverSocketChannel.socket().setReuseAddress(true);
//                ExecutorService executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(WebDAVDaemon.this.clientMaximum,
//                        new SimpleThreadFactory(WebDAVDaemon.this.node.getObjectId() + ":" + WebDAVDaemon.this.getName() + ".Server"));

                WebDAV.Backend backend;
                
                URL root = WebDAVServerMain.class.getClass().getResource("/");
                if (root == null) {
                    backend = new ClassLoaderBackend(WebDAVServerMain.class.getClassLoader(), "web/");
                } else {
                    backend = new FileSystemBackend("web/");                
                }

                while (true) {
                    SocketChannel clientSocket = serverSocketChannel.accept();
                    clientSocket.socket().setKeepAlive(false);
                    clientSocket.socket().setSoTimeout(WebDAVDaemon.this.node.configuration.asInt(WebDAVDaemon.ClientTimeoutMillis));

                    clientSocket.socket().setReceiveBufferSize(16*1024);
                    clientSocket.socket().setSendBufferSize(16*1024);
                    HTTPServer server = new HTTPServer(clientSocket);
                    
                    server.setName(WebDAVDaemon.this.node.getObjectId().toString() + ":" + server.getName());
                    
                    HTTP.NameSpace handler = new BeehiveNodeNameSpace(server, null);
                    HTTP.NameSpace fileHandler = new WebDAVNameSpace(server, backend);
                    server.addNameSpace(new URI("/"), handler);
                    server.addNameSpace(new URI("/xsl"), fileHandler);
                    server.addNameSpace(new URI("/css"), fileHandler);
                    server.addNameSpace(new URI("/js"), fileHandler);
                    server.addNameSpace(new URI("/images"), fileHandler);
                    server.addNameSpace(new URI("/dojo"), fileHandler);
                    server.start();
                    //executor.submit(server);
                }
            } catch (java.net.BindException e) {
                System.err.printf("%s: Cannot open a server socket on this host.%n", e.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace ();
            }
            // loop accepting connections and dispatching new thread to handle incoming requests.
        }
    }


    /**
     * Produce an {@link XHTML.Anchor} element that links to a HTML document that
     * inspects the {@link BeehiveObject} identified by the given {@link BeehiveObjectId}.
     * 
     * See {@link WebDAVDaemon} for the dispatch of the HTTP request the link induces.
     */
    public static XHTML.Anchor inspectObjectXHTML(BeehiveObjectId objectId) {
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
     * Produce an {@link XHTML.Anchor} element that links to a HTML document that
     * inspects the {@link BeehiveNode} identified by the given {@link NodeAddress}.
     * 
     * See {@link WebDAVDaemon} for the dispatch of the HTTP request the link induces.
     */
    public static XHTML.Anchor inspectNodeXHTML(NodeAddress address) {
    	return new XHTML.Anchor("%s", address.getObjectId()).setHref(address.getHTTPInterface()).setClass("NodeId");
    }
    
    /**
     * Return a formatted String containing {@code timeInMillis} represented as a readable time.
     */
    public static String formatTime(long timeInMillis) {
    	return String.format("%1$tFZ%1$tT", timeInMillis);
    }
    
    private static String revision = Release.ThisRevision();

    public final static Attributes.Prototype Port = new Attributes.Prototype(WebDAVDaemon.class, "Port", 12001, "The port number this node listens for incoming HTTP connections.");
    public final static Attributes.Prototype ServerRoot = new Attributes.Prototype(WebDAVDaemon.class, "Root", "web", "The pathname prefix for the HTTP server to use when serving files.");
    public final static Attributes.Prototype ClientMaximum = new Attributes.Prototype(WebDAVDaemon.class, "ClientMaximum", 4, "The maximum number of concurrent clients.");
    public final static Attributes.Prototype ClientTimeoutMillis = new Attributes.Prototype(WebDAVDaemon.class, "ClientTimeoutMillis", Time.secondsInMilliseconds(60), "The number of milliseconds a connection may be idle before it is closed.");

    /** CSS files to be loaded by the Node inspector */
    public final static Attributes.Prototype InspectorCSS = new Attributes.Prototype(WebDAVDaemon.class, "InspectorCSS", "/css/DOLRStyle.css,/css/BeehiveColours.css", "The css file to use for the node inspector interface.");
    /** JavaScript files to be loaded by the Node inspector */
    public final static Attributes.Prototype InspectorJS = new Attributes.Prototype(WebDAVDaemon.class, "InspectorJS", "/js/DOLRScript.js", "The JavaScript file to load for the node inspector interface.");
    
    transient private Daemon daemon;

    public WebDAVDaemon(final BeehiveNode node)
    throws MalformedURLException, MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, BeehiveService.makeName(WebDAVDaemon.class, WebDAVDaemon.serialVersionUID), "Beehive WebDAV Interface");

        node.configuration.add(WebDAVDaemon.Port);
        node.configuration.add(WebDAVDaemon.ServerRoot);
        node.configuration.add(WebDAVDaemon.ClientMaximum);
        node.configuration.add(WebDAVDaemon.ClientTimeoutMillis);
        node.configuration.add(WebDAVDaemon.InspectorCSS);
        node.configuration.add(WebDAVDaemon.InspectorJS);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.configuration.get(WebDAVDaemon.Port));
            this.log.config("%s", node.configuration.get(WebDAVDaemon.ServerRoot));
            this.log.config("%s", node.configuration.get(WebDAVDaemon.ClientMaximum));
            this.log.config("%s", node.configuration.get(WebDAVDaemon.ClientTimeoutMillis));
            this.log.config("%s", node.configuration.get(WebDAVDaemon.InspectorCSS));
            this.log.config("%s", node.configuration.get(WebDAVDaemon.InspectorJS));
        }
    }

    /**
     * This produces the resulting {@link HTTPResponse} to the client interface.
     *
     */
    private HTTP.Response generateResponse(HTTP.Request request, final URI uri, HTTP.Message.Body.MultiPart.FormData props) {
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
                	XHTML.EFlow folio = this.node.getServiceFramework().toXHTML(uri, props);
                	if (folio != null) {
                		XHTML.Body body = new XHTML.Body(folio);
                		return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(this.makeDocument(name, body, null, null)));
                	}
                	return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Unknown service: " + name));
                } else if (uri.getPath().startsWith("/census")) {
                    Census census = this.node.getService(CensusDaemon.class);
                    Map<BeehiveObjectId,OrderedProperties> list = census.select(0);

                    StringBuilder string = new StringBuilder().append(list.size()).append("\n");
                    for (BeehiveObjectId node : new TreeSet<BeehiveObjectId>(list.keySet())) {
                        string.append(node.toString()).append("\n");
                    }

                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(string.toString()));
                } else if (uri.getPath().startsWith("/gateway")) {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(this.node.getBeehiveProperties()));
                } else if (uri.getPath().startsWith("/config")) {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(this.node.getBeehiveProperties()));
                } else if (uri.getPath().startsWith("/reflect")) {
                    Reflection reflection = this.node.getService(ReflectionService.class);
                    if (reflection != null) {
                    	XHTML.Document document = reflection.toDocument(uri, props);

                    	return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(document));
                    }
                    return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Unknown service: " + name));
                } else if (uri.getPath().startsWith("/route-table")) {
                    XML.Document document = this.getRouteTableAsXMLDocument(request);
                    // The intent here was to produce XML with an XML stylesheet for client-side translation to XHTML.
                    // That doesn't work out very well in current browsers.
                    // So until they get better, getRouteTableAsXML produces XML and then here we perform the translation producing a String containing the XML.
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
                    XML.Document document = this.getNodeAsXMLDocument(request);
                    String query = request.getURI().getQuery();
                    if (query != null && query.equals("xml")) {
                        HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.XML(document));
                        return response;
                    }
                    
                    String xml = applyXSLT("/xsl/node.xsl", document);
                    System.out.printf("%s%n", xml);
                    HttpContent.Text.XML content = new HttpContent.Text.XML(xml);
                    content.setContentType(new HttpHeader.ContentType(InternetMediaType.Text.HTML));
                    return new HttpResponse(HTTP.Response.Status.OK, content);
                } else if (uri.getPath().startsWith("/objectType")) {
                    String objectId = uri.getPath().substring("/objectType/".length());

                    Reflection reflection = this.node.getService(ReflectionService.class);
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain(reflection.getObjectType(new BeehiveObjectId(objectId))));
                } else if (uri.getPath().startsWith("/inspect/")) {
                	BeehiveObjectId objectId = new BeehiveObjectId(uri.getPath().substring("/inspect/".length()));
                	Reflection reflection = this.node.getService(ReflectionService.class);
                	try {
                		XHTML.EFlow eflow = reflection.inspectObject(objectId, uri, props);
                		return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(this.makeDocument(objectId.toString(), new XHTML.Body(eflow), null, null)));      
                	} catch (BeehiveObjectStore.NotFoundException e) {
                		return new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain(HTTP.Response.Status.NOT_FOUND.toString() + " " + objectId.toString()));
                	}
                }

                // Read a file from the local file system, or from the jar file that this is running from.
                try {
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.RawInputStream(WebDAVDaemon.this.node.configuration.asString(WebDAVDaemon.ServerRoot), uri.getPath()));
                } catch (java.io.FileNotFoundException e) {
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain(e.toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                    return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain(e.toString()));
                }
            }

            //
            String action = HttpMessage.asString(props.get("action"), null);

            if (action != null) {
                if (action.equals("disconnect")) {
                    this.node.getNeighbourMap().remove(new NodeAddress(HttpMessage.asString(props.get("address"), null)));
                } else if (action.equals("unpublish-object")) {
                    String objectId = HttpMessage.asString(props.get("objectId"), null);

                    if (objectId == null || objectId.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing object-id"));
                    }
                    this.node.removeLocalObject(new BeehiveObjectId(objectId));

                    XHTML.Document result = makeDocument(this.node.getObjectId().toString(), new XHTML.Body(this.node.toXHTML(uri, props)), null, null);
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(result));
                } else if (action.equals("remove-object")) {
                    String oid = HttpMessage.asString(props.get("objectId"), null);

                    if (oid == null || oid.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing object-id"));
                    }
                    BeehiveObjectId objectId = new BeehiveObjectId(oid);
                    this.node.getObjectStore().lock(objectId);
                    try {
                        this.node.getObjectStore().remove(objectId);
                        XHTML.Document result = makeDocument(this.node.getObjectId().toString(), new XHTML.Body(this.node.toXHTML(uri, props)), null, null);
                        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(result));
                    } finally {
                        this.node.getObjectStore().unlock(objectId);
                    }
                } else if (action.equals("die")) {
                    this.node.stop();
                    return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.Plain("should be dead"));
                } else if (action.equals("connect")) {
                    String location = HttpMessage.asString(props.get("node"), "");
                    if (location.equals("")) {
                        return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain("Bad Request: Missing node specification"));
                    }
                    try {
                        //
                        // If location is a URL, use Locator to get a NodeAddress.
                        // If location is a NodeAddress add directly
                        // Otherwise, fail
                        //
                        BufferedReader r = null;
                        try {
                            OrderedProperties gatewayOptions = new OrderedProperties(new URL(location + "/gateway"));
                            location = gatewayOptions.getProperty(BeehiveNode.NodeAddress.getName());
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
                        return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(makeDocument(this.node.getObjectId().toString(), new XHTML.Body(this.node.toXHTML(uri, props)), null, null)));
                    } catch (IOException e) {
                        return new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                                new HttpContent.Text.Plain(e.toString() + " " + e.getLocalizedMessage()));
                    }
                    // unknown "action"
                }
            }

            XHTML.Document result = makeDocument(this.node.getObjectId().toString(), new XHTML.Body(this.node.toXHTML(uri, props)), null, null);

            try {
                OutputStream out = new FileOutputStream("WebDAVDaemon.out");
                out.write(result.toString().getBytes());
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

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

        for (String url : this.node.configuration.get(WebDAVDaemon.InspectorCSS).asStringArray(",")) {
            styleLinks.add(Xxhtml.Stylesheet(url).setMedia("all"));
        }
        if (styleSheets != null){
        	for (XHTML.Link l : styleSheets) {
        		styleLinks.add(l);
        	}
        }

        List<XHTML.Script> scriptLinks = new LinkedList<XHTML.Script>();
        for (String url : this.node.configuration.get(WebDAVDaemon.InspectorJS).asStringArray(",")) {
            scriptLinks.add(new XHTML.Script("text/javascript").setSource(url));
        }
        if (scripts != null){
        	for (XHTML.Script l : scripts) {
        		scriptLinks.add(l);
        	}
        }

        Dojo dojo = new Dojo(this.node.configuration.asString(BeehiveNode.DojoRoot),
                this.node.configuration.asString(BeehiveNode.DojoJavascript),
                this.node.configuration.asString(BeehiveNode.DojoTheme)
                );

        dojo.setConfig("isDebug: false, parseOnLoad: true, baseUrl: './', useXDomain: true, modulePaths: {'sunlabs': '/dojo/1.3.1/sunlabs'}");
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
    public void start()
    throws CertificateException, SignatureException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, IOException {
        if (this.daemon != null) {
            // We've already been started
            return;
        }

        this.setStatus("initializing");

        this.daemon = new Daemon();
        this.daemon.start();

        this.setStatus("idle");
    }

    @Override
    public void stop() {
//        this.setStatus("stopping");
//        if (this.daemon != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.daemon);
//            }
//            this.daemon.interrupt(); // Logged.
//            this.daemon = null;
//        }
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
    

    private Document getNodeAsXMLDocument(Request request) {
        XML.Content xml = this.node.toXML();

        XML.Node stylesheet = new XML.ProcessingInstruction("xml-stylesheet").addAttribute(new XML.Attr("type", InternetMediaType.Application.XSLT), new XML.Attr("href", "/xsl/node.xsl" ));

        XML.Document document = new XML.Document();
        document.append(stylesheet);
        document.append(xml);

        System.out.printf("%s%n", XML.formatXMLDocument(document.toString()));
        return document;
    }
    
    private XML.Document getRouteTableAsXMLDocument(HTTP.Request request) throws FileNotFoundException {
        NeighbourMap map = this.node.getNeighbourMap();

        XML.Content xml = map.toXML();

        // Browser-side XSLT process is in terrible shape.
        // Do the processing here and one day, when the browsers catch up, remove this code and have the browser do the translation.
        XML.Node stylesheet = new XML.ProcessingInstruction("xml-stylesheet")
            .addAttribute(new XML.Attr("type", InternetMediaType.Application.XSLT), new XML.Attr("href", "/xsl/beehive-route-table.xsl" ));

        XML.Document document = new XML.Document();
        document.append(stylesheet);
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
     * @return 
     * @throws FileNotFoundException
     */
    public InputStream openInputResource(String absolutePath) throws FileNotFoundException {
        URL root = this.getClass().getResource("/");

        //    String absolutePath = "/" + localRoot + "/" + localPath;
        if (root == null) { // We are inside of a .jar file.
            // paths beginning with '/' are matched directly with the path in the jar file.
            // Otherwise, it is prefixed by the package name.
            String fullPath = "/" + WebDAVDaemon.this.node.configuration.asString(WebDAVDaemon.ServerRoot) + absolutePath;
            InputStream inputStream = this.getClass().getResourceAsStream(fullPath);
            if (inputStream == null) {
                throw new FileNotFoundException(fullPath);
            }
            return inputStream;
        }
        
        File file = new File(new File(WebDAVDaemon.this.node.configuration.asString(WebDAVDaemon.ServerRoot)).getAbsolutePath(), new PathName1(absolutePath).toString());
        return new FileInputStream(file);
    }
    
    public class Resolver implements URIResolver {
        HttpUtil.PathName dirname;
        
        public Resolver(HttpUtil.PathName dirname) {
            this.dirname = dirname;
        }

        public Source resolve(String href, String base) throws TransformerException {
            HttpUtil.PathName root = new HttpUtil.PathName(WebDAVDaemon.this.node.configuration.asString(WebDAVDaemon.ServerRoot));
            
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
}
