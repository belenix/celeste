/* -*- mode: jde tab-width: 2; c-basic-indent: 2; indent-tabs-mode: nil -*- */
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This class represents an HTTP request sent from an HTTP client to an HTTP server.
 * <p>
 * An HTTP client constructs an instance of HttpRequest, fills in the appropriate
 * parts and transmits it over the network connection to an HTTP server.
 * The HTTP server acts on the request and transmits an {@link HttpResponse} in reply.
 * </p>
 * <p>
 * Note that a client cannot send a request with a message body and not specify the Content-Length header.
 * See <cite>RFC 2616 &sect; 4.4</cite>:
 * <blockquote>Closing the connection cannot be used to indicate
 * the end of a request body, since that would leave no possibility
 * for the server to send back a response.</blockquote>
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class HttpRequest implements HTTP.Request {
    private static final long serialVersionUID = 1L;
    
    private HTTP.Request.Method method;
    private URI requestURI;
    private byte[] httpVersion;
    private HTTP.Message message;

    public static HttpRequest getInstance(InputStream in) throws HTTP.BadRequestException, EOFException, IOException {
        return HttpRequest.getInstance(in, null);
    }
    
    /**
     * Factory method to create an {@link HttpRequest}
     * instance from reading a {@link PushbackInputStream} containing a well-formed HTTP request.
     * <p>
     * Only the header is consumed from the {@link PushbackInputStream}.
     * The body is left on the stream for subsequent processing.
     * </p>
     * 
     * @throws IOException is an IOException occurred.
     * @throws EOFException if and attempt to read past EOF occurred.
     * @throws HTTP.BadRequestException if the request cannot be properly parsed.
     */
    public static HttpRequest getInstance(InputStream in, OutputStream out) throws HTTP.BadRequestException, EOFException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // Absorb zero-length input lines.
        // Parse the first non-zero length input line as an HTTP Request header.
        do {
            bos.reset();
            HttpUtil.transferToUntilIncludedSequence(in, HttpUtil.CRNL, bos);
        } while (bos.size() <= 2);

        try {
            String tokens[] = bos.toString().split(" ");
            if (tokens.length != 3) {
                throw new HTTP.BadRequestException(String.format("Malformed request line %s%n", bos.toString()));
            }
            HTTP.Request.Method method = HTTP.Request.Method.getInstance(tokens[0]);
            if (method == null)
                throw new HTTP.BadRequestException(String.format("Unsupported method '%s'", tokens[0]));
            URI requestURI = new URI(tokens[1]);
            String httpVersion = tokens[2].trim();
            HTTP.Message message = HttpMessage.getRequestInstance(in, out);
            return new HttpRequest(method, requestURI, httpVersion, message);         
        } catch (URISyntaxException e) {
            throw new HTTP.BadRequestException(String.format("Bad Request-URI: %s%n", e));
        }
    }
    
    /**
     * Create an HTTP client to HTTP server request.
     *
     * @param method the {@link HTTP.Request.Method} of this request.
     * @param uri the {@link URI} that identifies the resource upon which to apply the request.
     * @param message an object implementing the {@link HTTP.Message} interface.
     */
    public HttpRequest(HTTP.Request.Method method, URI uri, String version, HTTP.Message message) {
        this.method = method;
        this.requestURI = uri;
        this.httpVersion = version.getBytes();
        this.message = message;
    }
    
    /**
     * Convenience constructor equivalent to {@link HttpRequest#HttpRequest(HTTP.Request.Method, URI, String, HTTP.Message)}
     * with the value of {@link HTTPServer#VERSION} as the {@code String} parameter.
     */
    public HttpRequest(HTTP.Request.Method method, URI uri, HTTP.Message message) {
        this(method, uri, HTTPServer.VERSION, message);
    }

    /**
     * Convenience constructor equivalent to invoking
     * {@link #HttpRequest(HTTP.Request.Method, URI, String, HTTP.Message)} 
     * with the value of the {@code HTTP.Message} as a new {@link HttpMessage#HttpMessage(Map)} of the given Map of headers.
     */
    public HttpRequest(HTTP.Request.Method method, URI uri, Map<String,HTTP.Message.Header> headers) {
        this(method, uri, HTTPServer.VERSION, new HttpMessage(headers));
        
    }

    public HTTP.Message getMessage() {
        return this.message;
    }

    public HTTP.Request.Method getMethod() {
        return this.method;
    }

    public URI getURI() {
        return this.requestURI;
    }
    
    public Map<String,String> getURLEncoded() throws UnsupportedEncodingException {
        Map<String,String> map = new HashMap<String,String>();
        String query = this.requestURI.getQuery();
        if (query != null) {
            query = URLDecoder.decode(query, "8859_1");
            String[] st = query.split("&");

            for (int i = 0; i < st.length; i++) {
                String[] av = st[i].split("=", 2);
                String key = av[0];
                String value = (av.length > 1) ? av[1] : "";
                map.put(key, value);
            }
        }
        return map;
    }

    @Override
    public String toString() {
        String CRNL = "\r\n";
        StringBuilder result = new StringBuilder(this.method.toString()).append(" ").append(this.requestURI).append(" ").append(new String(this.httpVersion)).append(CRNL);

        for (Entry<String,HTTP.Message.Header> entry : this.getMessage().getHeaders().entrySet()) {
            result.append(entry.getValue().toString()).append(CRNL);
        }
        result.append(CRNL);
        
        HTTP.Message.Body body = this.getMessage().getBody();
        if (body != null) {
            result.append(body.toString());
        } else {
        }
        return result.toString();
    }

    public long writeTo(DataOutputStream out) throws IOException {
        byte[] uriBytes = this.requestURI.toString().getBytes();

        out.write(this.method.toString().getBytes());
        out.write(HttpUtil.SPACE);
        out.write(uriBytes);
        out.write(HttpUtil.SPACE);
        out.write(this.httpVersion);
        out.write(HttpUtil.CRNL);
        
        long length = this.method.toString().getBytes().length +
            HttpUtil.SPACE.length +
            uriBytes.length +
            HttpUtil.SPACE.length +
            this.httpVersion.length + 
            HttpUtil.CRNL.length;
        length += this.message.writeTo(out);
        
        out.flush();
        
        return length;
    }

//    private void readObject(java.io.ObjectInputStream stream)  throws IOException, ClassNotFoundException {
//        
//    }
//    
//    private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
//        stream.writeObject(this.method);
//        stream.writeObject(this.requestURI);
//        stream.writeObject(this.message);        
//    }
//
//    private void readObjectNoData() throws ObjectStreamException {
//        
//    }
    
    public static void main(String[] args) throws Exception {
        String request = "GET / HTTP/1.1\r\n"
            + "Host:\r\n"
            + "\t127.0.0.1:12345\r\n"
            + "Content-Length: 11\r\n"
            + "\r\n"
            + "Hello World";
        ByteArrayInputStream bin = new ByteArrayInputStream(request.getBytes());
        HttpRequest r = HttpRequest.getInstance(bin);
        
        System.out.printf("%s", r.toString());
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(r);
        bout.close();
        
        ByteArrayInputStream b2 = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(b2);
        Object o = in.readObject();
        System.out.printf("%s", o.toString());
        
    }
}
