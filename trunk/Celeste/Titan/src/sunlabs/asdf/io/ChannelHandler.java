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
import java.nio.channels.SocketChannel;

/**
 * An asynchronous I/O handler for a single SocketChannel.
 * <p>
 * Classes implementing this interface represent the I/O state of a single {@link SocketChannel}.
 * </p>
 * 
 */
public interface ChannelHandler {
    /**
     * A generic Exception expressing Exception conditions in a {@link ChannelHandler}.
     *
     */
    public static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;
        
        public Exception(String message) {
           super(message);
        }
    }

    /**
     * Create a new {@link ChannelHandler}.
     *
     */
    public interface Factory {
        /**
         * Produce a new {@link ChannelHandler} to handle I/O from a Channel represented by the given {@link SelectionKey}.
         * 
         * @param selectionKey
         * @return A new ChannelHandler from the given {@link SelectionKey}.
         * @throws ChannelHandler.Exception if an error occurs when creating the new {@link ChannelHandler} instance.
         * @throws IOException if an I/O error occurs.
         */
        ChannelHandler newChannelHandler(SelectionKey selectionKey) throws ChannelHandler.Exception, IOException;
    }

    /**
     * Read data from this ChannelHandler's Channel and process this data to produce application input data.
     * If application input data is produced, invoke {@link #input(ByteBuffer)} with the application data.
     * See {@link #input(ByteBuffer)}.
     * <p>
     * If the Channel signals that it has reached end-of-stream (see {@link java.nio.channels.ReadableByteChannel#read(ByteBuffer dst)}),
     * this method is responsible for closing, or otherwise handling the channel.
     * </p>
     */
    public void networkRead();

    /**
     * Write data to this {@code ChannelHandler}'s output Channel.
     * <p>
     * Output data is supplied by previous invocations of {@link #output(ByteBuffer)}.
     * </p>
     */
    public void networkWrite();

    /**
     * Perform a data input consisting of data in the given {@link ByteBuffer}.
     * See {@link #networkRead()}.
     * 
     * @param data
     */
    void input(ByteBuffer data);

    /**
     * Perform data output consisting of data in the given {@link ByteBuffer}.
     * 
     * @param data
     * @throws IOException
     */
    public void output(ByteBuffer data) throws IOException;

    /**
     * Close this {@code ChannelHandler}'s channel and cancels the associated {@link SelectionKey}.
     * @throws IOException 
     */
    public void close() throws IOException;

    /**
     * Get this ChannelHandler instances expiration time expressed in milliseconds since midnight, January 1, 1970 UTC.
     * 
     * @return this ChannelHandler instances expiration time.
     */
    public long getExpirationTime();

    /**
     * Reset this ChannelHandlers expiration time.
     * Typically, the new expiration time is the value of {@code now}
     * (expressed in milliseconds since midnight, January 1, 1970 UTC)
     * plus whatever offset the ChannelHandler instance applies.
     * See {@link System#currentTimeMillis()}.
     * 
     * @param now the current time expressed in milliseconds since midnight, January 1, 1970 UTC.
     * @return the new expiration time in milliseconds since midnight, January 1, 1970 UTC.
     */
    public long resetExpirationTime(long now);
    
    /**
     * Set the time (expressed in milliseconds since midnight, January 1, 1970 UTC) that this ChannelHandler will close the underlying Channel.
     * <p>
     * By convention when this ChannelHandler is constructed, the caller must set the expiration time (see {@link #resetExpirationTime(long)}.
     * </p>
     * <p>
     * When the {@link ChannelHandler} determines that its Channel is busy, and may be busy for some time,
     * calling {@link #setExpirationTime(long)} with a value 0 of zero inhibits the automatic timeout mechanism.
     * </p>
     * @param time the time (expressed in milliseconds since midnight, January 1, 1970 UTC) that this ChannelHandler will close the underlying Channel.
     * @return the time (expressed in milliseconds since midnight, January 1, 1970 UTC) that this ChannelHandler will close the underlying Channel.
     */
    public long setExpirationTime(long time);
    
    /**
     * Set the input buffer for the application data.
     * <p>
     * Use this with caution, setting the input buffer too small could result in insufficient buffer space to process incoming data.
     * (See, for example, {@link SSLContextChannelHandler}).
     * </p>
     * @param buffer the new ByteBuffer to use.
     * @return the previous ByteBuffer.
     */
    public ByteBuffer setApplicationInput(ByteBuffer buffer);
}
