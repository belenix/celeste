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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/**
 * An extensible class implementing {@link ChannelHandler} that performs SSL/TLS handshaking and communication via {@link SSLEngine}.
 */
public abstract class SSLContextChannelHandler extends UnsecureChannelHandler implements ChannelHandler {
    private static boolean trace = false;
    
    private static void Print(String format, Object...args) {
        System.out.printf("%s: %s%n", Thread.currentThread().getName(), String.format(format, args));
        System.out.flush();
    }
    
    protected SSLEngine sslEngine;
    protected Executor executor;

    public SSLContextChannelHandler(SelectionKey selectionKey, SSLEngine sslEngine, Executor executor, long timeoutMillis) throws IOException {
        super(selectionKey,
                sslEngine.getSession().getPacketBufferSize(),
                sslEngine.getSession().getPacketBufferSize(),
                sslEngine.getSession().getApplicationBufferSize(),
                sslEngine.getSession().getApplicationBufferSize(),
                timeoutMillis);
        this.sslEngine = sslEngine;
        this.executor = executor;

        this.socketChannel.configureBlocking(false);
    }
    
    public void close() throws IOException {
        HandshakeStatus handShakeStatus = this.sslEngine.getHandshakeStatus();
        if (trace) Print("close: %s", handShakeStatus);
        
        // If the state is waiting for input (NEED_UNWRAP) just close the channel and cancel the key.
        if (handShakeStatus == HandshakeStatus.NEED_UNWRAP) {
            this.socketChannel.close();
            this.selectionKey.cancel();            
        }
        synchronized (this.outApplicationData) {
            this.sslEngine.closeOutbound();

            while(!this.sslEngine.isOutboundDone()) {
                this.outApplicationData.flip();
                this.outNetworkData.clear();

                SSLEngineResult result = this.sslEngine.wrap(this.outApplicationData, this.outNetworkData);
                if (trace) Print("close: %s", result);

                this.outNetworkData.flip();
                
                switch (result.getStatus()) {
                case BUFFER_OVERFLOW:                                                                           
                    this.outNetworkData = ByteBuffer.allocate(this.outNetworkData.capacity()+100);
                    break;

                default:
                    while(this.outNetworkData.hasRemaining()) {
                        try {
                            int nwritten = this.socketChannel.write(this.outNetworkData);
                            if (nwritten == -1) {
                                //the channel has been closed
                            }
                            if (nwritten == 0) {
                                //nothing was written
                            }
                        } catch(IOException e) {

                        }
                    }
                    this.outNetworkData.compact();
                }
            }
//            this.sslEngine.closeInbound();
        }
    }

    private HandshakeStatus doNeedUnwrap() throws IOException {
        int nread = this.socketChannel.read(this.inNetworkData);
        if (trace) Print("  doNeedUnwrap: nread %d", nread);
        this.inNetworkData.flip();

        if (!this.inNetworkData.hasRemaining()) {
            this.inNetworkData.clear();
            if (nread == -1) {
                throw new EOFException("End of stream");
            }
            return this.sslEngine.getHandshakeStatus();
        }
        
        // Repeatedly unwrap() while the engine status continues to be NEED_UNWRAP and there is still enough data to be processed in inNetworkData. 
        synchronized (this.inApplicationData) {
            SSLEngineResult result = null;
            do {
                result = this.sslEngine.unwrap(this.inNetworkData, this.inApplicationData);
                if (trace) Print("  doNeedUnwrap -> %s %s (consumed=%d, produced=%d)", result.getHandshakeStatus(), result.getStatus(), result.bytesConsumed(), result.bytesProduced());
                if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                    if (trace) Print("  doNeedUnwrap close %s and cancel %s%n", this.socketChannel, this.selectionKey);
                    this.socketChannel.close();
                    this.selectionKey.cancel();
                    return result.getHandshakeStatus();
                }
            } while (result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP && result.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW);
            this.inNetworkData.compact();

            return result.getHandshakeStatus();
        }
    }

    private SSLEngineResult doNeedWrap() throws IOException {
        SSLEngineResult result = null;
        do {
            synchronized (this.outApplicationData) {
                this.outApplicationData.flip();
                try {
                    result = this.sslEngine.wrap(this.outApplicationData, this.outNetworkData);
                } finally {
                    this.outApplicationData.compact();
                }
            }
            if (trace) Print("  doNeedWrap -> %s %s (consumed %d, produced %d)", result.getHandshakeStatus(), result.getStatus(), result.bytesConsumed(), result.bytesProduced());

            this.outNetworkData.flip();
            try {
                while (this.outNetworkData.hasRemaining()) {
                    if (this.socketChannel.write(this.outNetworkData) == -1) {
                        throw new EOFException("Channel closed");
                    }
                }
            } finally {
                this.outNetworkData.compact();
            }
        } while (result.getHandshakeStatus() == HandshakeStatus.NEED_WRAP && result.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW);

        return result;
    }

    /**
     * Read data from the Channel and hand off to the application layer.
     * 
     * @return the {@link HandshakeStatus} of the {@link SSLEngine}.
     * @throws IOException
     */
    private HandshakeStatus doRead() throws IOException {
        int nread = this.socketChannel.read(this.inNetworkData);
        if (trace) Print("doRead(): nread %d", nread);
        
        this.inNetworkData.flip();
        if (!this.inNetworkData.hasRemaining()) {
            this.inNetworkData.clear();
            this.inApplicationData.flip();
            if (this.inApplicationData.hasRemaining())
                this.input(this.inApplicationData);
            this.inApplicationData.compact();
            if (nread == -1) {
                this.socketChannel.close();
                this.selectionKey.cancel();
            }
            return this.sslEngine.getHandshakeStatus();
        }

        // Repeatedly unwrap() while the engine status continues to be NOT_HANDSHAKING and there is still data to be processed in inNetworkData. 
        SSLEngineResult result = null;
        synchronized (this.inApplicationData) {
            // Repeatedly unwrap the input data until we determine that we either need more data to continue (BUFFER_UNDERFLOW)
            // or we are no longer in handshake mode (NOT_HANDSHAKING).
            do {
                result = this.sslEngine.unwrap(this.inNetworkData, this.inApplicationData);
                this.inApplicationData.flip();
                if (this.inApplicationData.hasRemaining())
                    this.input(this.inApplicationData);
                this.inApplicationData.compact();
                if (trace) { Print("  doRead -> %s %s (consumed=%d, produced=%d) inApplicationData=%s", result.getStatus(), result.getHandshakeStatus(), result.bytesConsumed(), result.bytesProduced(), this.inApplicationData); }
                if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    // this is okay, we passed the application data above.
                } else if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                    return result.getHandshakeStatus();
                } else if (result.getStatus() == SSLEngineResult.Status.OK) {
                    //
                } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    //
                }
            } while (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING && result.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW);
            this.inNetworkData.compact();

//            this.inApplicationData.flip();
//            if (this.inApplicationData.hasRemaining())
//                this.input(this.inApplicationData);
//            this.inApplicationData.compact();
        }

        return result.getHandshakeStatus();
    }
    

    public void networkRead() {
//        System.out.printf("SSLContextSecureChannelHandler.networkRead(): %n");

        try {
            HandshakeStatus nextHandshake = null;
            HandshakeStatus handshake = this.sslEngine.getHandshakeStatus();

            if (trace) Print("networkRead(): %s", handshake);
            
            while (this.socketChannel.isOpen()) {
                switch (handshake) {
                case NEED_UNWRAP:
                    nextHandshake = doNeedUnwrap();
                    if (nextHandshake == HandshakeStatus.NEED_UNWRAP)
                        return;
                    break;

                case NEED_TASK:
                    if (this.executor == null) {
                        Runnable task = null;
                        do {
                            task = this.sslEngine.getDelegatedTask();
                            if (task != null) {
                                task.run();
                            }
                        } while (task != null);
                    } else {
                        Runnable task = null;
                        do {
                            task = this.sslEngine.getDelegatedTask();
                            if (task != null) {
                                this.executor.execute(task);
                            }
                        } while (task != null);
                    }
                    
                    nextHandshake = this.sslEngine.getHandshakeStatus();
                    break;

                case NEED_WRAP:
                    SSLEngineResult result = doNeedWrap();
                    if (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING && result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        this.socketChannel.close();
                        this.selectionKey.cancel();
                        return;
                    }
                    nextHandshake = result.getHandshakeStatus();
                    break;

                case FINISHED:
                case NOT_HANDSHAKING:
                    nextHandshake = doRead();
                    if (nextHandshake == HandshakeStatus.NOT_HANDSHAKING)
                        return;
                    break;

                default:
                    return;
                }

                if (trace) Print("%s -> %s", handshake, nextHandshake);
                handshake = nextHandshake;
            }
        } catch (IOException e) {
            if (trace) Print("%s", e);
            try { this.close(); } catch (IOException f) { }
        }
    }

    public void networkWrite() {
        synchronized (this.outApplicationData) {
            this.outApplicationData.flip();
            try {
                while (this.outApplicationData.hasRemaining()) {
                    SSLEngineResult result = this.sslEngine.wrap(this.outApplicationData, this.outNetworkData);
                    switch (result.getStatus()) {
                    case OK:
                    case BUFFER_OVERFLOW:
                        this.outNetworkData.flip();
                        while (this.outNetworkData.hasRemaining()) {
                            if (this.socketChannel.write(this.outNetworkData) == -1) {
                                try { this.close(); } catch (IOException e) { /**/ }
                                break;
                            }
                        }
                        this.outNetworkData.compact();
                        continue;

                    case BUFFER_UNDERFLOW: // XXX This shouldn't be set by the wrap() method.
                        //                        Print("BUFFER_UNDERFLOW: leaving application data residue %s%n", this.outApplicationData);
                        break;

                    case CLOSED:
                        //                        Print("CLOSED: leaving application data residue %s%n", this.outApplicationData);
                        break;

                    default:
                        Print("Unhandled %s in SSLContextChannelHandler.networkWrite(). Notify the developers.%n", result);
                        new Throwable().printStackTrace();
                        break;                        
                    }
                }
            } catch (SSLException e) {
                e.printStackTrace();
            } catch (EOFException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                this.outApplicationData.compact();
                if (this.outApplicationData.position() > 0) {
                    Print("leaving application data residue %s%n", this.outApplicationData);
                }
            }
        }
    }
}
