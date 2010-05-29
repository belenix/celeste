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
package sunlabs.asdf.web.http;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPClient;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpRequest;
import sunlabs.asdf.web.http.HttpResponse;

/**
 * <p>
 * An embeddable HTTP Client.
 * </p>
 * <p>
 * Instances of this class are connected to an HTTP server via simple TCP connection.
 * Messages are exchanged between client and server.
 * Clients compose {@link HttpRequest} instances, transmit them to the server
 * via the {@link HTTPClient#sendRequest} method which returns a resultant
 * {@link HttpResponse} instance from the server.
 * </p> 
 *
 */
public class HTTPClient implements HTTP.Client, Closeable {
    private SocketChannel channel;
    private InetSocketAddress server;
    private Set<HttpHeader> defaultHeaders;
    
    public HTTPClient(InetSocketAddress server) throws java.net.UnknownHostException {
        this.server = server;
        if (this.server.isUnresolved()) {
            throw new java.net.UnknownHostException(server.toString());
        }
        this.defaultHeaders = new HashSet<HttpHeader>();

        try { 
            this.defaultHeaders.add(new HttpHeader.Host(this.server.getAddress().getHostAddress() + ":" + this.server.getPort()));
        } catch (HTTP.BadRequestException e) {
            throw new RuntimeException(e);
        }
    }

    
    public void close() {
        synchronized (this.channel) {
            try {
                this.channel.close();
            } catch (IOException ignore) {
                /**/
            }
        }
    }
    
    public void disconnect() {
        this.close();
    }
    
    public void connect() throws IOException {
        this.channel = SocketChannel.open(this.server);        
    }

    public boolean usingProxy() {
        return false;
    }
    
    public InetSocketAddress getServer() {
        return this.server;
    }
    
    /**
     * Add this header which will be included in every {@link HttpRequest}
     * sent by this {@link HTTPClient} instance.
     * Headers explicitly specified in the {@link HttpRequest} in a
     * subsequent invocation of the {@link HTTPClient#sendRequest} method
     * will override the default header added here.
     * @param header
     */
    public void addDefaultHeader(HttpHeader header) {
        this.defaultHeaders.add(header);
    }
    
    /**
     * Send the HttpRequest to the HTTP server, returning the response.
     * 
     * @param request
     * @throws IOException
     */
    public HttpResponse sendRequest(HTTP.Request request) throws IOException, HTTP.BadRequestException {
        if (this.channel == null) {
            throw new IllegalStateException("HTTPClient is not connected to server.");
        }
        // Insert any missing headers from the defaultHeaders list.
        for (HttpHeader header : this.defaultHeaders) {
            if (request.getMessage().getHeader(header.getName()) == null)
                request.getMessage().addHeader(header);
        }
        synchronized (this.channel) {
            request.writeTo(new DataOutputStream(Channels.newOutputStream(this.channel)));
            return HttpResponse.getInstance(new PushbackInputStream(Channels.newInputStream(this.channel)));
        }
    }
    
//    public static void main(String[] args) {
//
//        try {
//            System.out.println(new URI("/").getPath());
//            HTTPClient client = new HTTPClient(new InetSocketAddress("127.0.0.1", 12001));
//            client.connect();
//            HttpRequest request = new HttpRequest(HTTP.Request.Method.GET, new URI("/"), new HttpHeader[0], (HttpContent) null);
//            HttpResponse response = client.sendRequest(request);
//            response.writeTo(new DataOutputStream(System.out));
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }
}
