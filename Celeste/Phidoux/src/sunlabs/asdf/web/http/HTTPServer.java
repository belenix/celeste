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
package sunlabs.asdf.web.http;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPServerMBean;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpRequest;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.ServerNameSpace;
import sunlabs.asdf.web.http.TappedInputStream;
import sunlabs.asdf.web.http.TappedOutputStream;

/**
 * A basic, embeddable HTTP service.
 * <p>
 * Each instance of this class handles one interactive session with a client.
 * All session configuration such as timeouts, buffer sizes, and so forth, are configured outside of this class.
 * </p>
 * It might be more flexible to simply implement Runnable and NOT extend Thread.
 * Creators of this class can wrap the resulting instance in a Thread, or submit to other thread running facilities.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class HTTPServer extends Thread implements HTTP.Server, Runnable, HTTPServerMBean {
    public static boolean debugPrintRequests = false;

    protected final static String VERSION = "HTTP/1.1";

    private boolean trace;
    
    private Map<URI,HTTP.URINameSpace> handlers;
    
    private Socket socket;
    private Logger logger;
    private ObjectName jmxObjectName;
    private PushbackInputStream inputStream;
    private DataOutputStream outputStream;

    /**
     * Create an instance of an HTTP service using an {@link InputStream} as the source of input {@link HTTP.Request}
     * messages and {@link OutputStream} as the sink for output {@link HTTP.Response} messages.
     * <p>
     * Before using the resulting instance for receiving HTTP requests, you must add
     * name-space handlers via the {@link HTTPServer#addNameSpace(URI, HTTP.URINameSpace)}
     * method and start the service by invoking the {@link #start()} method.
     * </p>
     * @throws MalformedObjectNameException 
     * @throws NotCompliantMBeanException 
     * @throws MBeanRegistrationException 
     * @throws InstanceAlreadyExistsException 
     */
    public HTTPServer(InputStream input, OutputStream output) {
        super(Thread.currentThread().getThreadGroup(), "HttpServer");
        this.socket = null;
        this.trace = false;
        this.handlers = new HashMap<URI,HTTP.URINameSpace>();
        try {
            this.addNameSpace(new URI("*"), new ServerNameSpace(this, null));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        this.inputStream = new PushbackInputStream(input);
        this.outputStream = new DataOutputStream(new BufferedOutputStream(output));
    }
    
    /**
     * Create an instance of an HTTP service using a {@link SocketChannel} as the input/output socket.
     * <p>
     * Before using the resulting instance for receiving HTTP requests, you must add
     * name-space handlers via the {@link HTTPServer#addNameSpace(URI, sunlabs.asdf.web.http.HTTP.URINameSpace)}
     * method and start the service by invoking the {@link #start()} method.
     * </p>
     *
     * @param channel
     */
    public HTTPServer(SocketChannel channel) throws IOException {
        this(channel.socket().getInputStream(), channel.socket().getOutputStream());

        this.setName(String.format("HttpServer_%s_%d<->%s_%d",
                channel.socket().getLocalAddress(),
                channel.socket().getLocalPort(),
                channel.socket().getInetAddress(),
                channel.socket().getPort()));

        this.socket = channel.socket();
//
//        this.jmxObjectName = JMX.objectName("sunlabs.asdf.http.HTTPServer", this.getName());
//        ManagementFactory.getPlatformMBeanServer().registerMBean(this, this.jmxObjectName);
    }

    public HTTPServer(SocketChannel channel, OutputStream inputTap, OutputStream outputTap) throws IOException {
        this(new TappedInputStream(channel.socket().getInputStream(), inputTap), new TappedOutputStream(channel.socket().getOutputStream(), outputTap));

        this.socket = channel.socket();

        this.setName(String.format("HttpServer_%s_%d<->%s_%d",
                this.socket.getLocalAddress(),
                this.socket.getLocalPort(),
                this.socket.getInetAddress(),
                socket.getPort()));

//        this.socket = channel.socket();
//
//        this.jmxObjectName = JMX.objectName("sunlabs.asdf.http.HTTPServer", this.getName());
//        ManagementFactory.getPlatformMBeanServer().registerMBean(this, this.jmxObjectName);
    }

    /**
     * Add the given {@link URI} as the namespace prefix of the URIs to be handled by the {@link HTTP.URINameSpace}.
     * <p>
     * All URIs that begin with this prefix are handled by the given {@code HTTP.NameSpace}.
     * </p>
     * 
     * @param root the root {@link URI} of the name-space.
     * @param handler the {@link HTTP.URINameSpace} that services the root name-space.
     */
    public void addNameSpace(URI root, HTTP.URINameSpace handler) {
        this.handlers.put(root, handler);
    }
    
    /**
     * Get the {@link HTTP.URINameSpace} handler for the given URI.
     *  
     * @param uri
     * @return the {@code HTTP.NameSpace} handler for the given URI.
     */
    public HTTP.URINameSpace getURINameSpace(URI uri) {
        String longestPath = "";
        URI longestKey = null;
        
        String uriPath = uri.getPath();
        for (URI key : this.handlers.keySet()) {
            if (uriPath.startsWith(key.getPath())) {
                if (key.getPath().length() > longestPath.length()) {
                    longestPath = key.getPath();
                    longestKey = key;
                }
            }
        }
        if (longestKey == null)
            return null;
        return this.handlers.get(longestKey);
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Set the current value of the trace flag.
     * If set to {@code true}, {@link HTTP.Request} and {@link HTTP.Response} objects are logged.
     * See {@link #setLogger(Logger)}.
     */
    public void setTrace(boolean value) {
        this.trace = value;
    }
    
    /**
     * Get the current value of the trace flag.
     * If set to {@code true}, {@link HTTP.Request} and {@link HTTP.Response} objects are logged.
     * @return the current value of the trace flag.
     */
    public boolean getTrace() {
        return this.trace;
    }
    
    public void log(HTTP.Request request) {
        
        System.out.flush();
        System.err.flush();
        if (this.logger != null) {
            StringBuilder logMessage = new StringBuilder("Request: ").append(Thread.currentThread().getName()).append("\n");

            logMessage.append(request.toString());

            this.logger.info(logMessage.toString());
            System.out.flush();
            System.err.flush();
        }
    }
    
    public void log(HTTP.Response response) {
        System.out.flush();
        System.err.flush();
        if (this.logger != null) {
            StringBuilder logMessage = new StringBuilder("Response: ").append(Thread.currentThread().getName()).append("\n");
            logMessage.append(response.toString());
            this.logger.info(logMessage.toString());
            System.out.flush();
            System.err.flush();
        }
    }

    public class Identity implements HTTP.Identity {
        private Map<String,String> properties;
        
        public Identity() {
            this.properties = new HashMap<String,String>();
        }
        
        /**
         * Construct an identity representation from an {@link HTTP.Message.Header.Authorization} object.
         * <p>
         * The parameter {@code authorization} may be {@code null} in which case the constructor is functionally equivalent to
         * {@link HTTPServer.Identity#Identity()}.
         * </p>
         * @param authorization
         * @throws HTTP.BadRequestException
         */
        public Identity(HTTP.Message.Header.Authorization authorization) throws HTTP.BadRequestException {
            this();
            if (authorization != null) {
                this.properties.put(HTTP.Identity.NAME, authorization.getBasicUserName());
                this.properties.put(HTTP.Identity.PASSWORD, authorization.getBasicPassword());
            }
        }
        
        /**
         * Construct an identity representation from the supplied name and password.
         *
         * @param name
         * @param password
         * @throws HTTP.BadRequestException
         */
        public Identity(String name ,String password) {
            this();
            this.properties.put(HTTP.Identity.NAME, name);
            this.properties.put(HTTP.Identity.PASSWORD, password);
        }

        public String getName() {
            return this.properties.get(HTTP.Identity.NAME);
        }

        public String getPassword() {
            return this.properties.get(HTTP.Identity.PASSWORD);
        }

        public String toString() {
            return String.format("name=%s password=%s", this.getName(), this.getPassword());
        }

        public String getProperty(String name, String defaultValue) {
            String value = this.properties.get(name);
            return (value == null) ? defaultValue : value;
        }
    }

    public HTTP.Response dispatch(HTTP.Request request) throws HTTP.BadRequestException {
        HTTP.Response response = null;

        // Since every resource can have a different behaviour, map the request URI to a corresponding URIHandler which implements that behaviour.
        HTTP.URINameSpace resourceHandler = this.getURINameSpace(request.getURI());
        if (resourceHandler == null) {
            response = new HttpResponse(HTTP.Response.Status.NOT_FOUND, new HttpContent.Text.Plain("%s not found%n", request.getURI()));
        } else {
            HTTP.Identity identity = new Identity((HTTP.Message.Header.Authorization) request.getMessage().getHeader(HTTP.Message.Header.AUTHORIZATION));
            response = resourceHandler.dispatch(request, identity);
        }
        
        if (response == null) {
            response = new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR, new HttpContent.Text.Plain("Internal Server Error\nNull response from dispatch.\nReport this as a bug.\n"));
        }

        // If the response's HttpMessage doesn't specify a Server: header, add one here.
        if (response.getMessage().getHeader(HTTP.Message.Header.SERVER) == null) {
            response.getMessage().addHeader(new HttpHeader.Server(this.getClass().getCanonicalName()));
        }

        return response;
    }

    @Override
    public void run() {
        try {
            for (;;) {
                try {
                    HTTP.Request request = HttpRequest.getInstance(this.inputStream);
                    if (this.getTrace()) {
                        this.log(request);
                    }

                    HTTP.Response response = this.dispatch(request);

                    // Guard against a poorly implemented handler.
                    //
                    // If the client request stipulated that the connection is to be closed,
                    // then set a Connection: close header in the response, send the response
                    // and close the connection.
                    HttpHeader.Connection connection = (HttpHeader.Connection) request.getMessage().getHeader(HTTP.Message.Header.CONNECTION);
                    if (connection != null && connection.contains("close")) {
                        response.getMessage().addHeader(HttpHeader.Connection.CLOSE);

                        if (request.getMethod().equals(HTTP.Request.Method.HEAD)) {
                            response.writeHeadTo(this.outputStream);
                        } else {
                            response.writeTo(this.outputStream);
                        }
                        if (this.getTrace()) {
                            this.log(response);
                        }
                        return; // this causes this thread to terminate and close the connection.
                    } else {
                        // The client request did not stipulate that the connection should be closed,
                        // so just send the response leaving the connection open.

                        if (response.getMessage().getBody() != null) {
                            // If the content-length is unknown (equal to -1), then the connection is to be closed.
                            // Alternatively, the content should be sent chunked.
                            long contentLength = response.getMessage().getBody().contentLength();
                            if (contentLength == -1) {
                                response.getMessage().addHeader(HttpHeader.Connection.CLOSE);
                            }
                        }

                        if (request.getMethod().equals(HTTP.Request.Method.HEAD)) {
                            response.writeHeadTo(this.outputStream);
                        } else {
                            response.writeTo(this.outputStream);
                        }
                        if (this.getTrace()) {
                            this.log(response);
                        }
                        this.outputStream.flush();

                        // After sending the response, check it to see if it contained
                        // a "Connection: close" header and if so, close the connection.
                        connection = (HttpHeader.Connection) response.getMessage().getHeader(HTTP.Message.Header.CONNECTION);
                        if (connection != null && connection.contains("close")) {
                            return; // this causes this thread to terminate and close the connection.
                        }
                    }
                } catch (HTTP.BadRequestException e) {
                    HTTP.Response response = e.getResponse();
                    response.writeTo(this.outputStream);
                    if (this.getTrace()) {
                        this.log(response);
                    }
                }
            }
        } catch (EOFException e) {
            // Something happened to our connection to the other side.
            // We can't do anything about it, so close the client and return
            // terminating this thread.
            if (this.logger != null) {
                System.out.flush();
                System.err.flush();
                this.logger.info(String.format("harmless %s: client closed the connection.", e.toString()));
            }
        } catch (IOException e) {
            // Something happened to our connection to the other side.
            // We can't do anything about it, so close the client and return
            // terminating this thread.
            if (this.logger != null) {
                System.out.flush();
                System.err.flush();
                this.logger.info(String.format("harmless %s: closed.", e.toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (this.outputStream != null) this.outputStream.close(); } catch (IOException exception) { /**/ }
            try { if (this.inputStream != null) this.inputStream.close(); } catch (IOException exception) { /**/ }
            try { if (this.socket != null) this.socket.close(); } catch (IOException exception) { /**/ }
            try {
                if (this.jmxObjectName != null)
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(this.jmxObjectName);
            } catch (InstanceNotFoundException e) {

            } catch (MBeanRegistrationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    // XXX This is only an HTTP server and here we are claiming to respond to WebDAV methods.
    public Collection<HTTP.Request.Method> getAccessAllowed() {
        Collection<HTTP.Request.Method> result = new HashSet<HTTP.Request.Method>();
        result.add(HTTP.Request.Method.GET);
        result.add(HTTP.Request.Method.PUT);
        result.add(HTTP.Request.Method.POST);
        result.add(HTTP.Request.Method.HEAD);
        result.add(HTTP.Request.Method.OPTIONS);
        result.add(HTTP.Request.Method.DELETE);
        result.add(HTTP.Request.Method.PROPFIND);
        result.add(HTTP.Request.Method.PROPPATCH);
        result.add(HTTP.Request.Method.COPY);
        result.add(HTTP.Request.Method.MOVE);
        result.add(HTTP.Request.Method.LOCK);
        result.add(HTTP.Request.Method.UNLOCK);
        
        return result;
    }
}
