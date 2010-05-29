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
package sunlabs.asdf.jmx;

import java.net.SocketException;

public interface SocketMBean {
    /**
     * Get the size of the input buffer of the TCP network interface for this server.
     */
    public int getReceiveBufferSize() throws SocketException;
    
    /**
     * Set the input buffer size for this HTTP server.
     * This must be set before calling the server's start() method.
     * @param size
     */
    public void setReceiveBufferSize(int size) throws SocketException;
    
    /**
     * Get the size of the output buffer of the TCP network interface for this server.
     */

    public int getSendBufferSize() throws SocketException;
    
    /**
     * Set the size of the output buffer of the TCP network interface for this server.
     */
    public void setSendBufferSize(int size) throws SocketException;
}
