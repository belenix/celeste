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
package sunlabs.beehive.node;

import java.io.Serializable;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import sunlabs.beehive.BeehiveObjectId;

/**
 * Every Beehive node has three addresses, each for a different purpose.
 * <ol>
 * <li>The first is simply the Node object-id which is meaningful only in the
 * context of Beehive routing.
 * Messages are routed to these object-ids by the Beehive nodes.
 * </li>
 * <li>
 * The second is the IP address and TCP port number of a Beehive Node's server.
 * This is the actual TCP-level connection between Beehive Nodes.
 * </li>
 * <li>
 * The third is the TCP port number of the node's Beehive client interface.
 * The BeehiveNode's client interface is an HTTP-protocol interface to the Beehive Node.
 * </li>
 * </ol>
 *
 */
public final class NodeAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The object-id of this node */
    private BeehiveObjectId nodeId;

    /** The local address and port number of the node's Beehive server. */
//    private InetSocketAddress localAddress;

    /** The system-wide address the node. */
    private InetSocketAddress internetworkAddress;

    /** The URL of the Beehive node's HTTP interface */
    private URL httpInterface;

    /**
     *
     */
    private NodeAddress() {
    }

    /**
     * Compose a new NodeAddress instance with the given the node's object-id,
     * the Beehive server local network address, and the client interface network
     * address.
     *
     * @param nodeId
     * @param internetworkAddress
     * @param port
     * @param httpPort
     * @throws NumberFormatException
     * @throws UnknownHostException
     */
    public NodeAddress(BeehiveObjectId nodeId, String internetworkAddress, int port, int httpPort) throws NumberFormatException, UnknownHostException {
        this();
        this.nodeId = nodeId;
        this.internetworkAddress = new InetSocketAddress(internetworkAddress, port);
        try {
            this.httpInterface = new URL("http://" + this.internetworkAddress.getAddress().getHostAddress() + ":" + httpPort);
        } catch (MalformedURLException e) {
            throw new NumberFormatException(e.toString());
        }
    }

    /**
     * Create a NodeAddress instance from a String of the same format as {@link NodeAddress#toString()}.
     * The format of the input string is:
     * <pre>
     * 	ObjectId:ServerPort:HTTPPort:IpAddress
     * </pre>
     * @throws UnknownHostException
     */
    public NodeAddress(String s) throws NumberFormatException, UnknownHostException {
        this();
        String[] field = s.split(":", 4);
        this.nodeId = new BeehiveObjectId(field[0]);
//        this.localAddress = new InetSocketAddress(field[3], Integer.parseInt(field[1]));
        this.internetworkAddress = new InetSocketAddress(field[3], Integer.parseInt(field[1]));
        try {
            this.httpInterface = new URL("http://" +  this.internetworkAddress.getAddress().getHostAddress() + ":" + Integer.parseInt(field[2]));
        } catch (MalformedURLException e) {
            throw new NumberFormatException(e.toString() + " " + s);
        }
    }

    public BeehiveObjectId getObjectId() {
        return this.nodeId;
    }

    public int getBeehivePort() {
        return this.internetworkAddress.getPort();
    }

    public InetSocketAddress getInternetworkAddress() {
        return this.internetworkAddress;
    }

    public URL getHTTPInterface() {
        return this.httpInterface;
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
     * The format is:
     * <i>nodeId</i><tt>:</tt><i>server-port</i><tt>:</tt><i>http-port</i><tt>:</tt><i>ip-address</i></pre>
     */
    public String format() {
        StringBuilder result = new StringBuilder(this.nodeId.toString())
        .append(":").append(this.internetworkAddress.getPort())
        .append(":").append(this.httpInterface.getPort())
        .append(":").append(this.internetworkAddress.getAddress().getHostAddress())
        ;
        return result.toString();
    }
}
