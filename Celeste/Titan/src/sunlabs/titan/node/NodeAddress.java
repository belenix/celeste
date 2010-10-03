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
package sunlabs.titan.node;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import sunlabs.titan.api.TitanNodeId;

/**
 * Every Titan node has three addresses, each for a different purpose.
 * <ol>
 * <li>The first is simply the Node {@link TitanNodeId} which is meaningful only in the context of Titan routing.
 * Messages are routed to these object-ids by the Titan nodes.
 * </li>
 * <li>
 * The second is the IP address and TCP port number of a Beehive Node's server.
 * This is the actual TCP-level connection between Beehive Nodes.
 * </li>
 * <li>
 * The third is the TCP port number of the node's Beehive client interface.
 * The TitanNode's client interface is an HTTP-protocol interface to the Beehive Node.
 * </li>
 * </ol>
 *
 */
public final class NodeAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The object-id of this node */
    private TitanNodeId nodeId;

//    /** The system-wide address the node. */
//    private InetSocketAddress internetworkAddress;

    /** The URL of the TitanNode's inspector interface */
    private URL inspectorURL;

    private URL messageURL;

    /**
     *
     */
    private NodeAddress() {
    }
    
    public NodeAddress(TitanNodeId nodeId, String internetworkAddress, int port, URL inspectorURL) throws MalformedURLException {
        this(nodeId, new URL(inspectorURL.getProtocol(), internetworkAddress, port, ""), inspectorURL);
    }

    public NodeAddress(TitanNodeId nodeId, URL messageURL, URL inspectorURL) {
        this.nodeId = nodeId;
        this.messageURL = messageURL;
        this.inspectorURL = inspectorURL;
    }
    
    /**
     * Create a NodeAddress instance from a String of the same format as {@link NodeAddress#toString()}.
     * The format of the input string is:
     * <pre>
     * 	ObjectId:ServerPort:HTTPPort:IpAddress
     * </pre>
     * @throws UnknownHostException
     * 
     * 
        StringBuilder result = new StringBuilder(this.nodeId.toString())
        .append(";").append(this.internetworkAddress.getAddress().getHostAddress())
        .append(";").append(this.internetworkAddress.getPort())
        .append(";").append(this.inspectorURL)
        
     */
    public NodeAddress(String s) throws MalformedURLException, NumberFormatException, UnknownHostException {
        this();
        String[] field = s.split(";", 4);

        this.nodeId = new TitanNodeIdImpl(field[0]);
        this.messageURL = new URL(field[1]);
        //this.internetworkAddress = new InetSocketAddress(field[1], Integer.parseInt(field[2]));
        this.inspectorURL = new URL(field[2]);
    }

    public TitanNodeId getObjectId() {
        return this.nodeId;
    }

    public URL getMessageURL() {
        return this.messageURL;
    }
    
//    public int getPort() {
//        return this.internetworkAddress.getPort();
//    }

//    /**
//     * Get the {@link InetSocketAddress} of the server port of this {@code NodeAddress}. 
//     * @return the {@link InetSocketAddress} of the server port of this {@code NodeAddress}. 
//     */
//    public InetSocketAddress getInternetworkAddress() {
//        return this.internetworkAddress;
//    }

    public URL getInspectorInterface() {
        return this.inspectorURL;
    }

    /**
     * The hashCode of a NodeAddress is the hashCode of it's object-id.
     */
    @Override
    public int hashCode() {
        return this.nodeId.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NodeAddress) {
            return this.nodeId.equals(((NodeAddress) o).nodeId);
        }

        return false;
    }

    /**
     * Produce a String representation of this NodeAddress.
     *
     */
    public String format() {
        StringBuilder result = new StringBuilder(this.nodeId.toString())
        .append(";").append(this.messageURL)
        .append(";").append(this.inspectorURL)
        ;
        return result.toString();
    }
}
