/*
 * Copyright 2010 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.asdf.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.Queue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class HTTPSChannelHandler extends SSLContextChannelHandler {
    public static class Factory implements ChannelHandler.Factory {
        private SSLContext sslContext;
        private long timeoutMillis;

        public Factory(SSLContext sslContext, long timeoutMillis) {
            this.sslContext = sslContext;
            this.timeoutMillis = timeoutMillis;
        }

        public ChannelHandler newChannelHandler(SelectionKey selectionKey) throws IOException {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);

            ChannelHandler handler = new HTTPSChannelHandler(selectionKey, engine, this.timeoutMillis);

            return handler;
        }
    }

    public static class ChannelRequest {

        public ChannelRequest(String header, HTTPSChannelHandler httpsChannelHandler) {
            // TODO Auto-generated constructor stub
        }
    }

    private Queue<ChannelRequest> outputQ;

    public HTTPSChannelHandler(SelectionKey selectionKey, SSLEngine sslEngine, long timeoutMillis) throws IOException {
        super(selectionKey, sslEngine, null, timeoutMillis);
        this.outputQ = new LinkedList<ChannelRequest>();
    }

    public static long cummulative = 0;
    public void input(ByteBuffer data) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();

        cummulative += data.remaining();
        System.out.printf("HTTPSChannelHandler.input: cummulative bytes %d %n", cummulative);
//        while (data.hasRemaining()) {
//            System.out.printf("%c", data.get());
//        }
        
        data.position(data.limit()); // Fake the consumption of the data.
        
        // Collect a header.
        // Once collected, place the header on the HTTP processing queue.
        // The queue must also contain a pointer back to this object that reads SSL encrypted data...
        // I want that to have the ReadableByteChannel.read(ByteBuffer data) interface
//        ChannelRequest request = new ChannelRequest(header.toString(), this); 
//        synchronized (this.outputQ) {
//            this.outputQ.add(request);
//            this.outputQ.notify();
//        }

        String response =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain\r\n" +
            "Connection: close\r\n" +
            "Server: HTTPChannelHandler\r\n" +
            "\r\n" +
            String.format("Hello World %d\r\n", System.currentTimeMillis());
        
//        this.output(ByteBuffer.wrap(response.getBytes()));
    }
}
