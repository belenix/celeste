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
package sunlabs.asdf.web.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.HashMap;
import java.util.Map;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.asdf.web.http.InternetMediaType;

/*
 * Some cleanup using the Content-Length header needs to be done.
 * 
 * Generally, if the content length is known at the time of construction of an instance of this class,
 * the Content-Length header must be set.
 * This allows the message to be sent without having to close the connection to signal
 * the end of the message body.
 * 
 * Otherwise, the content sits available on an InputStream and will be read until
 * EOF when sending this HTTPMessage to an output stream via the writeTo() method.
 * 
 * Be aware that receiving an HttpMessage with a missing Content-Length header means
 * that the content is terminated by an EOF and we may not have sufficient memory to
 * load the entire message body into an array.
 */
/**
 * A fully functional, extensible, implementation of the {@link HTTP.Message} interface.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public final class HttpMessage implements HTTP.Message {
    /**
     * Internal collection of {@link HTTP.Message.Header} instances for this message.
     * The map is keyed by the <b>lower case</b> header name.
     */
    private Map<String,HTTP.Message.Header> headers;
    private HTTP.Message.Body content;

    /**
     * Construct an {@link HttpMessage} from an {@link PushbackInputStream}.
     * <p>
     * This only reads the headers of the message, leaving the remainder of the
     * message body, if any, on the {@link PushbackInputStream}. Subsequent interactions with
     * this {@code HttpMessage} may induce reading the input stream to interpret or store the message body.
     * </p>
     * <p>
     * Users of this class should never assume that a message actually contains a body.
     * An attempt to obtain a non-existent body from the input-stream will hang because no data will be available. 
     * </p>
     * @throws IOException
     */
    public static HTTP.Message getInstance(PushbackInputStream in) throws IOException, HTTP.BadRequestException {
        HTTP.Message message = new HttpMessage();
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            out.reset();
            HttpUtil.transferToUntilExcludedSequence(in, HttpUtil.CRNL, out);
            if (out.size() == 0)
                break;

            while (true) {
                int c = in.read();
                if (c == '\t' || c == ' ') {
                    out.write(c);
                    HttpUtil.transferToUntilExcludedSequence(in, HttpUtil.CRNL, out);
                } else {
                    in.unread(c);
                    break;
                }
            }

            if (out.size() > 0) {
                message.addHeader(HttpHeader.getInstance(out.toString()));
            } else {
                break;
            }            
        }
        HttpHeader.ContentType contentTypeHeader;
        
        try {
           contentTypeHeader = (HttpHeader.ContentType) message.getHeader(HTTP.Message.Header.CONTENTTYPE);
        } catch (ClassCastException e) {
            e.printStackTrace();
            for (String key : message.getHeaders().keySet()) {
                System.out.printf("%s -> %s: %s%n", key, message.getHeader(key), message.getHeader(key).getClass());                
            }
            throw e;
        }

        HttpHeader.TransferEncoding transferEncoding = (HttpHeader.TransferEncoding) message.getHeader(HTTP.Message.Header.TRANSFERENCODING);
        
        // If this is a regular body, we can just read it normally.
        // If this is a Transfer-Encoded body, then we need a way to parse the encoding to get the actual data.
        if (transferEncoding != null) {
            message.setBody(new HttpContent.TransferEncodedInputStream(contentTypeHeader, in, transferEncoding));
        } else {    
            message.setBody(new HttpContent.RawInputStream(contentTypeHeader, in));
        }

        HttpHeader.ContentLength contentLengthHeader = (HttpHeader.ContentLength) message.getHeader(HTTP.Message.Header.CONTENTLENGTH);
        if (contentLengthHeader != null) {
            message.getBody().setContentLength(contentLengthHeader.getLength());
        }
        return message;
    }
    
    private HttpMessage() {
        this.headers = new HashMap<String,HTTP.Message.Header>();        
    }

    /**
     * Construct an HTTPMessage from an Map of {@link HTTP.Message.Header} instances and an {@link HTTP.Message.Body} instance.
     * 
     * @see HttpHeader
     * @see HttpContent
     * @param headers A Map containing {@link HTTP.Message.Header} instances keyed by the header name.
     * @param content An {@link HTTP.Message.Body} instance.
     */
    public HttpMessage(Map<String,HTTP.Message.Header> headers, HTTP.Message.Body content) {
        this();
        for (HTTP.Message.Header h : headers.values()) {
            this.addHeader(h);
        }
        this.content = content;        
    }
    
    /**
     * Construct an {@code HttpMessage} from an Map of {@link HTTP.Message.Header} instances.
     * <p>
     * This message contains no body.
     * </p>
     * @see HttpHeader
     * @param headers A Map containing {@link HTTP.Message.Header} instances keyed by the header name.
     */
    public HttpMessage(Map<String,HTTP.Message.Header> headers) {
        this(headers, null);
    }

    /**
     * Construct an {@code HTTPMessage} from an {@link HTTP.Message.Body} instance.
     * The resulting {@code HTTPMessage} has no headers.
     * <p>
     * Equivalent to calling:
     * <pre>
     * {@code HttpMessage(new HashMap<String,HTTP.Message.Header>(), content)}
     * </pre>
     * </p>
     * 
     * @see HttpMessage#addHeader(sunlabs.asdf.web.http.HTTP.Message.Header...)
     * @see HttpMessage#HttpMessage(Map, sunlabs.asdf.web.http.HTTP.Message.Body)
     * @see HttpContent
     * @param content An {@link HTTP.Message.Body} instance.
     */
    public HttpMessage(HTTP.Message.Body body) {
        this(new HashMap<String,HTTP.Message.Header>(), body);
    }
    
    /**
     * Construct an HttpMessage from a byte array containing the entirety of the message.
     * A {@code Content-Length} header is created and set to the length of the byte array.
     * 
     * @param bytes - the byte array containing the message
     *
     * @throws HttpHeader.InvalidFormatException
     */
    public HttpMessage(byte[] bytes) throws IOException, HTTP.BadRequestException {
        this(new PushbackInputStream(new ByteArrayInputStream(bytes)));
    }
    
    /**
     * Construct an {@link HttpMessage} from an {@link PushbackInputStream}.
     * <p>
     * This only reads the headers of the message, leaving the remainder of the
     * message body, if any, on the {@link PushbackInputStream}. Subsequent interactions with
     * this {@code HttpMessage} may induce reading the input stream to interpret or store the message body.
     * </p>
     * <p>
     * Users of this class should never assume that a message actually contains a body.
     * An attempt to obtain a non-existent body from the input-stream will hang because no data will be available. 
     * </p>
     * @throws IOException
     */
    @Deprecated
    private HttpMessage(PushbackInputStream in) throws IOException, HTTP.BadRequestException {
        this();
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            out.reset();
            HttpUtil.transferToUntilExcludedSequence(in, HttpUtil.CRNL, out);
            if (out.size() == 0)
                break;

            while (true) {
                int c = in.read();
                if (c == '\t' || c == ' ') {
                    out.write(c);
                    HttpUtil.transferToUntilExcludedSequence(in, HttpUtil.CRNL, out);
                } else {
                    in.unread(c);
                    break;
                }
            }

            if (out.size() > 0) {
                this.addHeader(HttpHeader.getInstance(out.toString()));
            } else {
                break;
            }            
        }
        HttpHeader.ContentType contentTypeHeader;
        
        try {
           contentTypeHeader = (HttpHeader.ContentType) this.getHeader(HTTP.Message.Header.CONTENTTYPE);
        } catch (ClassCastException e) {
            e.printStackTrace();
            for (String key : this.headers.keySet()) {
                System.out.printf("%s -> %s: %s%n", key, this.headers.get(key), this.headers.get(key).getClass());                
            }
            throw e;
        }

        HttpHeader.TransferEncoding transferEncoding = (HttpHeader.TransferEncoding) this.getHeader(HTTP.Message.Header.TRANSFERENCODING);
        
        // If this is a regular body, we can just read it normally.
        // If this is a Transfer-Encoded body, then we need a way to parse the encoding to get the actual data.
        if (transferEncoding != null) {
            this.content = new HttpContent.TransferEncodedInputStream(contentTypeHeader, in, transferEncoding);
        } else {    
            this.content = new HttpContent.RawInputStream(contentTypeHeader, in);
        }

        HttpHeader.ContentLength contentLengthHeader = (HttpHeader.ContentLength) this.getHeader(HTTP.Message.Header.CONTENTLENGTH);
        if (contentLengthHeader != null) {
            this.content.setContentLength(contentLengthHeader.getLength());
        }
    }
    
    public HTTP.Message.Body getBody() {
        return this.content;
    }
    
    public void setBody(HTTP.Message.Body body) {
        this.content = body;
    }

    public HTTP.Message.Body.MultiPart.FormData decodeMultiPartFormData() throws IOException, HTTP.BadRequestException {
        HTTP.Message.Header.ContentType contentTypeHeader = this.getContentType();

        InternetMediaType contentType = contentTypeHeader.getType();
        if (InternetMediaType.Application.XWWWFormURLEncoded.equals(contentType)) {
            return new HttpContent.Multipart.FormData(this.getBody().toString());
        } else if (InternetMediaType.Multipart.FormData.equals(contentType)) {
            return new HttpContent.Multipart.FormData(contentTypeHeader.getParameter("boundary").getValue(), this.getBody().toInputStream());
        }

        return new HttpContent.Multipart.FormData();
    }
    
    
    /**
     * <blockquote>
     * Any HTTP/1.1 message containing an entity-body SHOULD include
     * a <code>Content-Type</code> header field defining the media type of that body.
     * If and only if the media type is not given by a Content-Type field,
     * the recipient MAY attempt to guess the media type via inspection of
     * its content and/or the name extension(s) of the URI used to identify the resource.
     * If the media type remains unknown, the recipient SHOULD treat it as type "<code>application/octet-stream</code>".
     * </blockquote>
     */
    public HTTP.Message.Header.ContentType getContentType() {
        HttpHeader.ContentType contentType = (HttpHeader.ContentType) this.getHeader(HTTP.Message.Header.CONTENTTYPE);
        if (contentType == null)
            contentType = new HttpHeader.ContentType(InternetMediaType.Application.OctetStream);
        return contentType;        
    }
    
    /**
     * Add the given {@link HTTP.Message.Header} instances to this message.
     * <p>
     * From <cite><a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">RFC 2616 &sect; 4.2</a></cite>
     * </p>
     * <blockquote>
     * Multiple message-header fields with the same field-name MAY be present in a message if and only if
     * the entire field-value for that header field is defined as a comma-separated list [i.e., #(values)].
     * It MUST be possible to combine the multiple header fields into one "field-name: field-value" pair,
     * without changing the semantics of the message, by appending each subsequent field-value to the first,
     * each separated by a comma. The order in which header fields with the same field-name are received is
     * therefore significant to the interpretation of the combined field value,
     * and thus a proxy MUST NOT change the order of these field values when a message is forwarded.
     * </blockquote> 
     * <p>
     * If multiple occurrences of a header are not allowed (see {@link sunlabs.asdf.web.http.HTTP.Message.Header#multipleHeadersAllowed()}),
     * a previous value of the header is replaced with the value given.
     * </p>
     * <p>
     * Otherwise, if multiple occurrences of a header are permitted,
     * this appends the field values in accordance with the specification.
     * To replace a header for which multiple occurrences of a header are permitted,
     * the caller must remove the header (see {@link #removeHeader(String)}) before using this method.
     * </p>
     * @param header The HTTP.Message.Header instances to add to this message.
     */
    public HttpMessage addHeader(HTTP.Message.Header...header) {
        for (HTTP.Message.Header h : header) {
            if (h != null) {
                String headerName = h.getName().toLowerCase(); // the map is keyed by the lower-case header name.
                HTTP.Message.Header existingHeader = this.headers.get(headerName);
                if (existingHeader == null) {
                    this.headers.put(headerName, h);
                } else {
                    if (existingHeader.multipleHeadersAllowed()) {
                        existingHeader.append(h.getFieldValue());
                    } else {
                        this.headers.put(headerName, h);
                    }
                }
            }
        }
        return this;
    }

//    @SuppressWarnings("unchecked")
//    public <C extends HTTP.Message.Header> C getHeader(Class<? extends C> klasse) {
//        return (C) this.headers.get(klasse);        
//    }
    
    public HTTP.Message.Header getHeader(String name) {
        return this.headers.get(name.toLowerCase());        
    }
    
    /**
     * Remove the specified {@link HTTP.Message.Header} with the given name from this message.
     * @param name the name of the header to remove.
     * @return The removed {@link HttpHeader}, or {@code null} if it was not present.
     */
    public HTTP.Message.Header removeHeader(String name) {
        return this.headers.remove(HttpHeader.nameToClassMap.get(name.toLowerCase()));
    }
    
    public Map<String,HTTP.Message.Header> getHeaders() {
        // Compute the missing headers.
        if (this.content != null) {
            // Note that setting a content type header for the message, will override whatever content type is established in this HTTP.Message.Body.
            if (this.getHeader(HTTP.Message.Header.CONTENTTYPE) == null) {
                this.addHeader(this.content.getContentType());
            }
            // Set the authoritative value of the content length, overriding any content-length header that *might* have been set in this HTTP.Message.
            long contentLength = this.content.contentLength();
            if (contentLength != -1) {
                // Override any Content-Length header already set in the message.
                this.addHeader(new HttpHeader.ContentLength(contentLength));
            } else {
                // Because the content length is not known (equal to -1), force the connection to close.
                // XXX this can cause unnecessary connection closes on requests that have no body and no content-length header.
                this.addHeader(new HttpHeader.Connection("close"));
            }

            if (this.content instanceof HttpContent.Multipart.FormData) {
                HttpContent.Multipart.FormData body = (HttpContent.Multipart.FormData) this.content;
                this.addHeader(new HttpHeader.ContentType(InternetMediaType.Multipart.FormData, new HttpHeader.Parameter("boundary", body.getBoundaryString())));
            }
        }

        return this.headers;
    }
    
    /**
     * Write this HttpMessage header to the given {@link DataOutputStream}.
     * <p>
     * If this message does not have a {@code Content-Type} header already set,
     * one is created based upon the encapsulated {@link HTTP.Message.Body}.
     * </p>
     * <p>
     * Any {@code Content-Length} header set for this HttpMessage will be overridden
     * by the content length reported by the encapsulated {@code HTTP.Message.Body}.
     * </p>
     * <p>
     * If the encapsulated {@code HTTP.Message.Body} does not report a content
     * length (signified by a value of {@code -1}), any {@code Content-Length} header is removed from this message
     * and the {@code Connection} header is set to {@code close}.
     * </p>
     * @param out The {@link DataOutputStream} to write on.
     * @return The number of bytes written.
     * @throws IOException if the underlying output threw an {@code IOException}.
     */
    public long writeHeadTo(OutputStream out) throws IOException {            
        long length = 0;
        
        for (Map.Entry<String,HTTP.Message.Header> entry : this.getHeaders().entrySet()) {
            length += entry.getValue().writeTo(out);
            out.write(HttpUtil.CRNL, 0, HttpUtil.CRNL.length);
            length += HttpUtil.CRNL.length;
        }
        
        out.write(HttpUtil.CRNL);
        length += HttpUtil.CRNL.length;
        return length;
    }
    
    public long writeTo(OutputStream out) throws IOException {
        long length = this.writeHeadTo(out);
        if (this.content != null) {
            length += this.content.writeTo(out);
        }
        
        return length;
    }
    
    /**
     * Given an {@link HttpMessage} containing a serialized java object,
     * deserialize the object and return it.
     * @param formData
     * @param defaultValue
     * @throws ClassNotFoundException
     */
    public static <C extends Object> C asObject(final Class<? extends C> klasse, HttpMessage formData, C defaultValue) throws ClassCastException, ClassNotFoundException {
        if (formData == null)
            return defaultValue;
        try {
            ObjectInputStream in = new ObjectInputStream(formData.content.toInputStream());
            C result = klasse.cast(in.readObject());
            in.close();
            return result;
        } catch (IOException e) {
            throw new ClassCastException();
        }
    }

    /**
     * Given an object, which will be assumed to be a FormData object,
     * return the data portion of the object as a String.
     */    
    public static String asString(HTTP.Message message, Object defaultValue) /*throws IOException*/ {
        return (message == null ? ((defaultValue == null) ? null : defaultValue.toString()) : message.getBody().toString());
    }

    /**
     * Given a FormData object,
     * return the data portion of the object as an int.
     */
    public static int asInteger(HTTP.Message message, int defaultValue) /* throws IOException */ {
        String value = HttpMessage.asString(message, null);
        return (value == null ? defaultValue : Integer.parseInt(value));
    }
    
    /**
     * Given a FormData object,
     * return the data portion of the object as a long.
     */
    public static long asLong(HTTP.Message message, long defaultValue) throws IOException {
        String value = HttpMessage.asString(message, null);
        return (value == null ? defaultValue : Long.parseLong(value));
    }

    public static void main(String[] args) {
        try {
            HttpMessage m = new HttpMessage();
            m.addHeader(HttpHeader.getInstance("Authorization: Basic YXNkZjphc2Rm"));

            for (String key : HttpHeader.nameToClassMap.keySet()) {
                System.out.printf("%s %s%n", key, HttpHeader.nameToClassMap.get(key));
            }

            for (Map.Entry<String, HTTP.Message.Header> entry : m.headers.entrySet()) {
                System.out.printf("'%s' %s  %s%n", entry.getKey(), entry.getValue(), m.headers.get(entry.getKey()));
            }

            HTTP.Message.Header.Authorization a = (HTTP.Message.Header.Authorization) m.getHeader("authorization");
            System.out.printf("%s%n", a);

            a = (HTTP.Message.Header.Authorization) m.getHeader(HTTP.Message.Header.AUTHORIZATION);
            System.out.printf("%s%n", a);        


            Map<String,String> map = new HashMap<String,String>();
            map.put("authorization".toLowerCase(), "bar");

            System.out.printf("foo=%s%n", map.get(HTTP.Message.Header.AUTHORIZATION.toLowerCase()));
        } catch (Exception e) {
        e.printStackTrace();
        }
    }
}
