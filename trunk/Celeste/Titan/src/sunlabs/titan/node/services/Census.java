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
package sunlabs.titan.node.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import sunlabs.asdf.web.XML.XML;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanNodeIdImpl;
import sunlabs.titan.node.services.census.SelectComparator;
import sunlabs.titan.util.OrderedProperties;

/**
 * A general purpose interface for collecting information about all of the nodes in the system.
 * <p>
 * Also used to select nodes in the system.
 * </p>
 * <p>
 * All nodes in the system periodically advertise their availability by
 * transmitting a {@link TitanMessage} {@code RouteToNode} {@link TitanMessage}
 * to the node {@link TitanGuid} identified by {@link Census#CensusKeeper}.
 * The message contains an instance of {@link OrderedProperties} listing the properties the
 * node is willing to share with subsequent queries and is used for matching queries to the
 * subset of nodes in the census.
 * </p>
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface Census extends TitanService {
    public interface Select {

        public interface Request extends TitanService.Request {

        }

        public interface Response extends TitanService.Response {
            
            public XML.Content toXML();
        }
    }

    public interface Report {

        public interface Request extends TitanService.Request {

        }

        public interface Response extends TitanService.Response {

        }
    }

    /**
     * The Census keeper is the node the receives messages sent to the {@code CensusKeeper} object-id.
     */
    public final static TitanNodeId CensusKeeper = TitanNodeIdImpl.ZERO;

    /** The time-stamp in seconds for this record as recorded by the receiver. */
    public final static String ReceiverTimestamp = "Census.ReceiverTimestamp";

    /** The time-stamp in seconds for this record as recorded by the sender. */
    public final static String SenderTimestamp = "Census.SenderTimestamp";

    /** The number of milliseconds for this record to live. */
    public final static String TimeToLiveSeconds = "Census.TimeToLiveSeconds";

    public final static String NodeAddress = "Census.NodeAddress";
    public final static String Version = "Census.Version";
    public final static String NodeRevision = "Census.NodeRevision";
    public final static String AdministrativeContact = "Census.AdministrativeContact";
    public final static String Location = "Census.Location";
    
    public final static String OperatingSystemLoadAverage = "Census.OperatingSystemLoadAverage";
    public static final String OperatingSystemArchitecture = "Census.OperatingSystemArchitecture";
    public static final String OperatingSystemName = "Census.OperatingSystemName";
    public static final String OperatingSystemVersion = "Census.OperatingSystemVersion";
    public final static String OperatingSystemAvailableProcessors = "Census.AvailableProcessors";

    public static final String ReportSerialNumber = "Census.SerialNumber";

    /**
     * Put all of the entries in the given {@link Map} {@code census} into the census data kept by this node.
     *
     * @param census this {@link Map} containing additional census data.
     */
    public void putAllLocal(Map<TitanNodeId,OrderedProperties> census);
    
    /**
     * Select {@code count} number of nodes by {@link TitanNodeId} from the Census,
     * excluding those nodes specified by {@code TitanNodeId} in the Set {@code exclude}.
     *
     * @param count the number of nodes to select.  If zero, all matching nodes are selected.
     * @param excludedNodes a {@link Set} of nodes to specifically exclude from the selection.
     * @param matchedAttributes an {@link OrderedProperties} instance containing the set of properties that selected nodes much match.
     * @return A {@link Map} of {@link TitanGuid} to {@link OrderedProperties} for each node selected.
     * @throws RemoteException if the remote node threw an Exception.
     * @throws ClassCastException if a ClassCastException which obtaining the internal response.
     */
    public Map<TitanNodeId,OrderedProperties> select(int count, Set<TitanNodeId> exclude, List<SelectComparator> comparatorList) throws ClassCastException;

    /**
     * Select {@code count} number of nodes by {@link TitanGuid} from the Census system.
     * If {@code count} is zero, then return all of the nodes in the system.
     *
     * @param count the number of nodes to select.
     * @return A {@link Map} of {@link TitanGuid} to {@link OrderedProperties} for each node selected.
     * @throws RemoteException if the remote node threw an Exception.
     * @throws ClassCastException if a ClassCastException which obtaining the internal response.
     */
    public Map<TitanNodeId,OrderedProperties> select(int count) throws ClassCastException, RemoteException;

    /**
     * Get the current system-wide Census data, using the specified node in the system to proxy the request.
     *
     * @param proxy the {@link NodeAddress} of the node used to proxy this request.
     * @param count the number of nodes to select
     * @param excludedNodes a {@link Set} of nodes to specifically exclude from the selection, or {@code null}.
     * @param matchedAttributes an {@link OrderedProperties} instance containing the set of properties that selected nodes much match, or {@code null}.
     * @return A {@link Map} of {@link TitanGuid} to {@link OrderedProperties} for each node selected.
     * @throws ClassNotFoundException 
     * @throws RemoteException if the remote node threw an Exception.
     * @throws ClassCastException if a ClassCastException which obtaining the internal response.
     */
    public Map<TitanNodeId, OrderedProperties> select(NodeAddress gateway, int count, Set<TitanNodeId> exclude, List<SelectComparator> comparatorList)
    throws ClassCastException, ClassNotFoundException, RemoteException;

}
