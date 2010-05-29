/* -*- mode: jde tab-width: 2; c-basic-indent: 2; indent-tabs-mode: nil -*- */
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Map.Entry;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTPServer;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpUtil;

/**
 * This class represents an HTTP response from an HTTP server to a client.
 * The response consists of a status line followed by a CRNL,
 * a set of CRNL separated headers, a single CRNL, and arbitrary byte data.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public final class HttpResponse implements HTTP.Response {    
    
    public static HttpResponse getInstance(PushbackInputStream in) throws IOException, HTTP.BadRequestException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        do {
            bos.reset();
            HttpUtil.transferToUntilIncludedSequence(in, HttpUtil.CRNL, bos);
            if (bos.size() == 0) {
                throw new IOException("EOF from input stream");
            }
        } while (bos.size() <= 2);
        byte[] line = bos.toByteArray();

        String input = new String(line);
        String[] tokens = input.split(" ", 3);

        if (tokens.length != 3) {
            throw new IOException("Malformed HTTP request: " + input);
        }

        try {
            return new HttpResponse(HTTP.Response.Status.fromStatusCode(Integer.parseInt(tokens[1])), tokens[0], HttpMessage.getInstance(in));
        } catch (NumberFormatException e) {
            throw new IOException("Status code is non-numeric: \"" + input + "\"");
        }
    }
    
    
    private HTTP.Response.Status  status;
    private String version;
    private HTTP.Message message;
    
    public HttpResponse(HTTP.Response.Status status, String version, HTTP.Message message) {
        this.status = status;
        this.version = version;
        this.message = message;
    }
    
    /**
     * Create an HttpResponse instance, with an {@link HttpMessage} body set to the given {@link HttpContent}.
     * <p>
     * </P>
     * 
     * @param status - the HTTP protocol status code
     */
    public HttpResponse(HTTP.Response.Status status, HTTP.Message.Body content) {
        super();
        this.status = status;
        this.version = HTTPServer.VERSION;
        this.message = new HttpMessage(content);

        // These responses must never have content.
        if (status.getStatusClass() == 1 || status.equals(HTTP.Response.Status.NO_CONTENT) || status.equals(HTTP.Response.Status.NOT_MODIFIED)) {
            if (content != null) {
                throw new IllegalStateException(status.toString() + " must not include a message-body.");
            }
        }
        if (content == null) {
            this.message.addHeader(new HttpHeader.ContentLength(0));
        }
    }
    
    /**
     * Construct an {@link HttpResponse} object that contains an empty message.
     * @param status the status code for this response.
     */
    public HttpResponse(HTTP.Response.Status  status) {
        this(status, HTTPServer.VERSION, new HttpMessage((HttpContent) null));
        this.message.addHeader(new HttpHeader.ContentLength(0));
    }
    
    public HTTP.Message getMessage() {
        return this.message;
    }
    
    public HTTP.Response.Status getStatus() {
        return this.status;
    }
    
    public String getVersion () {
        return new String(this.version);
    }
    
    private long writeStatusLine(OutputStream out) throws IOException {
        byte[] statusCodeBytes = Integer.toString(this.status.getStatusCode()).getBytes();
        byte[] statusTextBytes = this.status.getReasonPhrase().getBytes();
        byte[] versionBytes = this.version.getBytes();
        out.write(versionBytes);
        out.write(HttpUtil.SPACE);
        out.write(statusCodeBytes);
        out.write(HttpUtil.SPACE);
        out.write(statusTextBytes);
        out.write(HttpUtil.CRNL);

        return versionBytes.length + HttpUtil.SPACE.length + statusCodeBytes.length + HttpUtil.SPACE.length + statusTextBytes.length + HttpUtil.CRNL.length;
    }
    
    /**
     * Write the HEAD of this HttpResponse to the given {@link DataOutputStream}.
     * 
     * See {@link HttpMessage#writeHeadTo(DataOutputStream)}
     * 
     * @param out
     * @return
     * @throws IOException
     */
    public DataOutputStream writeHeadTo(DataOutputStream out) throws IOException {
        this.writeStatusLine(out);
        this.message.writeHeadTo(out);
        out.flush();
        return out;
    }

    /**
     * Write this {@code HttpResponse} to the given {@link DataOutputStream}.
     * 
     * See {@link HttpMessage#writeHeadTo(DataOutputStream)}
     * 
     * @param out
     * @return
     * @throws IOException
     */
    public long writeTo(DataOutputStream out) throws IOException {        
        long length = this.writeStatusLine(out);
        length += this.message.writeTo(out);
        out.flush();
        return length;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(new String(this.version)).append(" ").append(this.status.toString()).append("\n");

        for (Entry<String,HTTP.Message.Header> entry : this.getMessage().getHeaders().entrySet()) {
            result.append(entry.getValue().toString()).append("\n");
        }
        result.append("\n");

        HTTP.Message.Body body = this.getMessage().getBody();
        if (body != null) {
            long contentLength = body.contentLength();

            if (contentLength > 0 && contentLength < 8192) {
                result.append(body.toString());
            } else {
                if (contentLength > 0) {
                    result.append(String.format(" (printing %d bytes of body suppressed.)", contentLength));
                } else {
                    //
                }
            }
        } else {
            result.append("(no body)\n");
        }
        return result.toString();
    }
}
