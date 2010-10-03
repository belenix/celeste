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
package sunlabs.titan.node.services.api;

import sunlabs.titan.api.TitanService;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.TitanMessage;

public interface MessageService extends TitanService {

    /**
     * Transmit a {@link TitanMessage} directly to a {@link NodeAddress} and return the reply.
     * If the destination {@code NodeAddress} is unresponsive or cannot be reached, the return value is {@code null}.
     *
     * <p>
     * This method should throw Exceptions to signal failures rather than returning {@code null}.
     * </p>
     */
    public TitanMessage transmit(NodeAddress addr, TitanMessage message) /*throws InterruptedException*/;
    
    /**
     * This returns the Thread listening for incoming messages.
     * 
     * @return the Thread listening for incoming messages.
     */
    public Thread getServerThread();
}
