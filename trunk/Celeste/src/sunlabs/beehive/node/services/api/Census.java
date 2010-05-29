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
package sunlabs.beehive.node.services.api;

import java.util.Map;
import java.util.Set;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.node.BeehiveMessage;
import sunlabs.beehive.node.NodeAddress;
import sunlabs.beehive.node.BeehiveMessage.RemoteException;
import sunlabs.beehive.util.OrderedProperties;

/**
 * A general purpose interface for collecting information about all of the nodes in the system.
 * <p>
 * Also used to select nodes in the system.
 * </p>
 * <p>
 * All nodes in the system periodically advertise their availability by
 * transmitting a {@link BeehiveMessage} {@code RouteToNode} {@link BeehiveMessage}
 * to the node {@link BeehiveObjectId} identified by {@link Census#CensusKeeper}.
 * The message contains an instance of {@link OrderedProperties} listing the properties the
 * node is willing to share with subsequent queries and is used for matching queries to the
 * subset of nodes in the census.
 * </p>
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface Census {
    /**
     * The Census keeper is the node the receives messages sent to the {@code CensusKeeper} object-id.
     *
     */
    public final static BeehiveObjectId CensusKeeper = BeehiveObjectId.ZERO;

    /**
     * The time-stamp for this record.
     */
    public final static String Timestamp = "Census.Timestamp";

    /**
     * The number of milliseconds for this record to live.
     */
    public final static String TimeToLive = "Census.TimeToLive";

    public final static String NodeAddress = "Census.NodeAddress";
    public final static String Version = "Census.Version";
    public final static String NodeRevision = "Census.NodeRevision";

    /**
     * Put all of the entries in the given {@link Map} {@code census} into the census data kept by this node.
     *
     * @param census this {@link Map} containing additional census data.
     */
    public void putAllLocal(Map<BeehiveObjectId,OrderedProperties> census);

    /**
     * Select {@code count} number of nodes by {@link BeehiveObjectId} from the Census,
     * excluding those nodes specified by {@link BeehiveObjectId} in the Set {@code exclude}.
     *
     * @param count the number of nodes to select.  If zero, all nodes are selected.
     * @param exclude a {@link Set} of nodes to specifically exclude from the selection.
     * @param match an {@link OrderedProperties} instance containing the set of properties that selected nodes much match.
     * @return A {@link Map} of {@link BeehiveObjectId} to {@link OrderedProperties} for each node selected.
     * @throws RemoteException if the remote node threw an Exception.
     * @throws ClassCastException 
     */
    public Map<BeehiveObjectId,OrderedProperties> select(int count, Set<BeehiveObjectId> exclude, OrderedProperties match) throws ClassCastException;

    /**
     * Select {@code count} number of nodes by {@link BeehiveObjectId} from the Census system.
     * If {@code count} is zero, then return all of the nodes in the system.
     *
     * @param count the number of nodes to select.
     * @return A {@link Map} of {@link BeehiveObjectId} to {@link OrderedProperties} for each node selected.
     * @throws RemoteException if the remote node threw an Exception.
     * @throws ClassCastException 
     */
    public Map<BeehiveObjectId,OrderedProperties> select(int count) throws ClassCastException, RemoteException;

    /**
     * Get the current system-wide Census data, using an already existing node in the system to proxy the request.
     *
     * @param gateway the {@link NodeAddress} of the node used to proxy this request.
     * @param count the number of nodes to select
     * @param exclude a {@link Set} of nodes to specifically exclude from the selection, or {@code null}.
     * @param match an {@link OrderedProperties} instance containing the set of properties that selected nodes much match, or {@code null}.
     * @return A {@link Map} of {@link BeehiveObjectId} to {@link OrderedProperties} for each node selected.
     */
    public Map<BeehiveObjectId,OrderedProperties> select(NodeAddress gateway, int count, Set<BeehiveObjectId> exclude, OrderedProperties match);
}
