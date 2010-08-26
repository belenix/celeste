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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLEchoChannelHandler extends SSLContextChannelHandler {
    public static class Factory implements ChannelHandler.Factory {
        private SSLContext sslContext;
        private long timeoutMillis;
        private ExecutorService executor;

        public Factory(SSLContext sslContext, long timeoutMillis) {
            this.sslContext = sslContext;
            this.timeoutMillis = timeoutMillis;
            this.executor = Executors.newFixedThreadPool(10);
        }

        public ChannelHandler newChannelHandler(SelectionKey selectionKey) throws IOException {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);

            ChannelHandler handler = new SSLEchoChannelHandler(selectionKey, engine, this.executor, this.timeoutMillis);

            return handler;
        }
    }

    private int cummulative;

    public SSLEchoChannelHandler(SelectionKey selectionKey, SSLEngine sslEngine, ExecutorService executor, long timeoutMillis) throws IOException {
        super(selectionKey, sslEngine, executor, timeoutMillis);
        this.cummulative = 0;
    }
    
    public void input(ByteBuffer data) {
        this.cummulative += data.remaining();
//        System.out.printf("SSLEchoChannelHandler.input(%s) cummulative=%d%n", data, this.cummulative);
        
        this.output(data);
    }

    @Override
    public void close() throws IOException {
//        System.out.printf("SSLEchoChannelHandler.close(): %d bytes transferred%n", this.cummulative);
        super.close();
    }
}
