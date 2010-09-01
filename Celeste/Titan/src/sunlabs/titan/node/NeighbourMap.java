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
package sunlabs.titan.node;

import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ReflectionException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.services.xml.TitanXML;
import sunlabs.titan.node.services.xml.TitanXML.XMLRoute;
import sunlabs.titan.node.services.xml.TitanXML.XMLRoutingTable;

/**
 * Each Node has a NeighbourMap which is the fundamental distributed
 * routing table.
 *
 */
public final class NeighbourMap {
    /**
     * <p>
     *
     * Comparator of Reputations.
     *
     * </p><p>
     *
     * This node always trusts itself, so anytime this node is compared to any
     * other node, this node has the better reputation.  Otherwise, if the
     * reputations of node A and node B are equal, the one with the lower
     * numeric value for its ObjectId is better.  Otherwise, simply return the
     * comparision of the two reputations.
     *
     * </p>
     */
    public class ReputationComparator implements Comparator<NodeAddress>, Serializable {
        private final static long serialVersionUID = 1L;

        protected Map<String,Integer> mapReputationRequirements;
        private NodeAddress node;

        public ReputationComparator() {
            super();
        }

        public ReputationComparator(BeehiveNode thisNode, Map<String,Integer> requirements) {
            super();
            this.node = thisNode.getNodeAddress();
            this.mapReputationRequirements = requirements;
        }

        public int compare(NodeAddress a, NodeAddress b) {
            if (a.equals(this.node)) {
                return Integer.MAX_VALUE;
            }
            if (b.equals(this.node)) {
                return Integer.MIN_VALUE;
            }
            if (a.equals(b))
                return 0;

            int A_reputation;
            int B_reputation;

            // Fetch the Dossier on these and compute and compare the reputations.
            Dossier.Entry entryA = NeighbourMap.this.getDossier().getEntryAndLock(a);
            try {
                A_reputation = entryA.computeReputation(this.mapReputationRequirements);
            } finally {
                NeighbourMap.this.getDossier().unlockEntry(entryA);
            }

            Dossier.Entry entryB = NeighbourMap.this.getDossier().getEntryAndLock(b);
            try {
                B_reputation = entryB.computeReputation(this.mapReputationRequirements);
            } finally {
                NeighbourMap.this.getDossier().unlockEntry(entryB);
            }

            if (A_reputation == B_reputation) {
                return a.getObjectId().compareTo(b.getObjectId());
            }

            return A_reputation - B_reputation;
        }
    }

    /**
     * The number of columns in a table.
     * This is equal to the length of the object-id in digits.
     * For 160-bit object-ids, routed by hexadecimal digits, this is 40.
     * For 256-bit object-ids, routed by hexadecimal digits, this is 64;
     */
    public int n_tables;

    /**
     * The number of rows in a table.
     * For object-ids that are routed by hexidecimal digits, this is 16.
     */
//    private int n_entries;

    /** The actual array of Neighbours that comprise the neighbour map. */
    //
    // The first index is level and the second is slot within level.
    //
    // The map is populated to maintain an invariant:  that the address of the
    // central node (the one given as argument to the constructor) itself is
    // stored at every level.  The implementation of the getRoute() method
    // below depends on a this invariant.
    //
    // Note that any node whose address differs from that of the central node
    // cannot occupy the same slot as the central node at any level.  (If so,
    // it wouldn't be stored at that level, but rather at some higher level.
    // At the highest level, the slots must differ, since otherwise the
    // addresses would be identical.)  Thus, having the central node appear at
    // each level can create no conflicts with attempts to store other nodes
    // at any level.
    //
    // The set obtained by indexing level and slot holds (no more than
    // maxDepth) neighbours that satisfy the routing criteria for those
    // indices.  It is sorted by reputation, so the next hop at each level
    // goes to the qualifying neighbour with the best reputation.
    //

    private SortedSet<NodeAddress>[][] routes;
    private NeighbourMap.ReputationComparator comparator;
    private int maxDepth;

    private Map<String,Integer> mapReputationRequirements;
    private final BeehiveNode node;

    /** Dossier on every neighbour ever known by this {@code BeehiveNode}. */
    private Dossier dossier;

//    private ObjectName jmxObjectName;

    /**
     * Constructor for the DOLR Neighbour Map.  The Neighbour Map
     * is the fundamental "routing table" for DOLR message routing.
     */
    public NeighbourMap(BeehiveNode node) throws
            InstanceAlreadyExistsException,
            NotCompliantMBeanException,
            MBeanRegistrationException,
            MalformedObjectNameException {
        this.n_tables = TitanGuidImpl.n_digits;

        this.node = node;

        this.mapReputationRequirements = Reputation.newCoefficients();
        this.mapReputationRequirements.put(Reputation.LATENCY, new Integer(50));
        this.mapReputationRequirements.put(Reputation.PUBLISHER, new Integer(25));
        this.mapReputationRequirements.put(Reputation.ROUTING, new Integer(25));
        
        try {
            this.dossier = new Dossier(node.getSpoolDirectory());
        } catch (BackedObjectMap.AccessException e) {
            throw new RuntimeException(e);
        }

        //
        // Unfortunately, it's not possible to allocate arrays with non-raw
        // types, but not doing so here induces an unchecked conversion.
        // Hence the dancing around with annotations.
        //
        @SuppressWarnings(value="unchecked")
            SortedSet<NodeAddress>[][] r2 = new TreeSet[TitanGuidImpl.n_digits][];
        this.routes = r2;
        for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
            @SuppressWarnings(value="unchecked")
                SortedSet<NodeAddress>[] r1 = new TreeSet[TitanGuidImpl.radix];
            this.routes[level] = r1;
            for (int digit = 0; digit < TitanGuidImpl.radix; digit++) {
                this.routes[level][digit] = new TreeSet<NodeAddress>(this.comparator);
            }
        }
        this.maxDepth = 8;

        this.comparator = new NeighbourMap.ReputationComparator(node, mapReputationRequirements);

        // A  map always contains at least the NodeAddress of this Node.
        this.addSelf();

//        if (node.getJMXObjectName() != null) {
//            this.jmxObjectName = JMX.objectName(node.getJMXObjectName(), "NeighbourMap");
//            ManagementFactory.getPlatformMBeanServer().registerMBean(this, this.jmxObjectName);
//        }
    }

    //
    // Seed the neighbour map by adding a route to self at every level.
    //
    // Assumes that there are no entries in the map yet.
    //
    private void addSelf() {
        NodeAddress address = this.node.getNodeAddress();
        for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
            int digit = address.getObjectId().digit(level);
            SortedSet<NodeAddress> set =
                new TreeSet<NodeAddress>(this.comparator);
            set.add(address);
            this.routes[level][digit] = set;
        }
    }

    /**
     * Conditionally add the specified Neighbour to the neighbour map(s).
     *
     * If the NodeAddress fills an otherwise empty slot in the routing table,
     * then add it.
     *
     * If the reputation of the NodeAddress is greater than the reputation of
     * the current NodeAddress at the right slot, then add it.
     */
    public boolean add(NodeAddress address) {
        //
        // If the address to add is this local Node's address, there's nothing
        // to do, since the routing table always contains an entry leading
        // back to this Node at every level and this Node (by construction)
        // has the best possible reputation (so it will preempt any other
        // possible address).
        //
        // XXX: Does this case ever actually occur?
        //
        if (address.equals(this.node.getNodeAddress()))
            return true;

        int level = this.node.getNodeId().sharedPrefix(address.getObjectId());
        int digit = address.getObjectId().digit(level);

        //
        // Construct a replacement Set for the nodes at [level,digit] by
        // copying the old set, minus the node we are adding (effectively a
        // remove).  We cannot simply use remove() because the comparator
        // compares reputations not node object-ids and we run the risk of
        // adding the same node to the set twice because the reputation
        // recorded in one is different than the reputation recorded in the
        // other.
        //
        // XXX: Throwing a perfectly good set away and reconstructing it
        //      bothers me.  As an alternative, how about iterating through
        //      the set looking for an element that explicitly
        //      equals(address) and declaring victory if one turns up?  If
        //      none does, then add the address.  This technique avoids using
        //      the comparator (except to order the iterator's traversal).
        //
        //      The reason for reconstructing the set is to force its ordering
        //      to follow the now-current reputation values for each of its
        //      contained addresses.  (In looking at the implementation of
        //      TreeSet, it becomes obvious that the class was not designed
        //      to handle comparators with non-deterministic, e.g.,
        //      time-varying, behavior.  Perhaps the ice we're skating on here
        //      is too thin...)
        //
        // XXX: Also, if the set is already filled with maxDepth elements, the
        //      code below simply gives up.  But instead, shouldn't it add the
        //      new element and then throw out the one with the worst
        //      reputation?  (We know the victim won't the the local node,
        //      since it's guaranteed to have maximal reputation; thus the
        //      routing table invariant will still be maintained.)
        //
        synchronized (this.routes) {
            TreeSet<NodeAddress> newSet = new TreeSet<NodeAddress>(this.comparator);
            for (NodeAddress n : this.routes[level][digit]) {
                if (!n.equals(address) && newSet.size() < this.maxDepth) {
                    newSet.add(n);
                }
            }
            newSet.add(address);
            this.routes[level][digit] = newSet;
        }

        return true;
    }

    /**
     * Get this NeighbourMap as a single {@link Set} instance of the unique
     * {@link NodeAddress} instances in the map.
     */
    public Set<NodeAddress> keySet() {
        Set<NodeAddress> set = new HashSet<NodeAddress>();

        synchronized (this.routes) {
            for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
                for (int digit = 0; digit < TitanGuidImpl.radix; digit++) {
                    set.addAll(this.routes[level][digit]);
                }
            }
        }
        return set;
    }

    /**
     * Produce a {@link SortedSet} of nodes from the local neighbour-map sorted by
     * DHT "proximity" in the order of nodes most likely to replace this node as a root.
     *
     */
    /*
     * For node 026137909DF5DCA874AB6746D3E03B50A020E72D586E2CDD62706E801FE991C7
     *
     * 026137909DF5DCA874AB6746D3E03B50A020E72D586E2CDD62706E801FE991C7:12018:12019:10.0.1.7 0
     * 0A2405E8F6ECDF6BCA46ED42329254694E3296137097A7D240199D2A499EB9FB:12016:12017:10.0.1.7 -1
     * 4F1C093881E662D8DD3076132888D57DC30AF9E02FAFF6D2876B7D67AA995D57:12006:12007:10.0.1.7 -1
     * 59CA2A9B3C33E8348CEE5E04B8D427C8E6D095FBBE73FAF8A10037D72DF25C9E:12002:12003:10.0.1.7 -1
     * 70BDA02F1C1AE133ED319C2BD5D870FA173A01CAB2CC9FF6CAF48380BF036D39:12010:12011:10.0.1.7 -1
     * 937E905EF46E7130C7DAE38E4B825B89E0D7060E958939B35114933E18EB0BCB:12008:12009:10.0.1.7 -1
     * 9A6C4BD1E00857CBB450B68D3A12FE2E2A45F1AF9DE3FBAC544B1DB5D75876BE:12004:12005:10.0.1.7 -1
     * 9FA065D1DC129FDA5299A6B878F34C5CD00E2DCA361C00DAA6B0AD777B7864EF:12000:12001:10.0.1.7 -1
     * D51346EB45D040E32685EC3400E99051E599719C1A9740BDF38E9A35C98E02DE:12014:12015:10.0.1.7 -1
     */
    public SortedSet<NodeAddress> successorSet() {

        SortedSet<NodeAddress> successors = new TreeSet<NodeAddress>(new RouteSuccession(NeighbourMap.this.node.getNodeId()));
        synchronized (this.routes) {
            for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
                for (int digit = 0; digit < TitanGuidImpl.radix; digit++) {
                    successors.addAll(this.routes[level][digit]);
                }
            }
        }

//        System.out.printf("%s%n",NeighbourMap.this.node.getObjectId());
//
//        System.out.printf("ordered %s%n",NeighbourMap.this.node.getObjectId());
//        for (NodeAddress k : successors) {
//            System.out.printf("   %s%n", k.format());
//        }

        return successors;
    }

    /**
     * Remove the neighbour node specified by {@code address} from this
     * NeighbourMap.
     *
     * @param address   the {@link NodeAddress} of the neighbour node to remove
     */
    public void remove(NodeAddress address) {
        this.node.getLogger().info(address.format());

        //
        // We never remove our own address.
        //
        if (address.equals(this.node.getNodeAddress()))
            return;

        int level = this.node.getNodeId().sharedPrefix(address.getObjectId());
        int digit = address.getObjectId().digit(level);

        SortedSet<NodeAddress> newSet = new TreeSet<NodeAddress>(this.comparator);
        synchronized (this.routes) {
            for (NodeAddress n : this.routes[level][digit]) {
                if (!n.equals(address))
                    newSet.add(n);
            }
            this.routes[level][digit] = newSet;
        }
    }

    private NodeAddress newGetRoute(TitanGuid destination, int hopCount) {
        if (hopCount >= (TitanGuidImpl.n_digits - 1)) {
            return null;
        }

        SortedSet<NodeAddress>[] R_n = this.routes[hopCount];
        int d = destination.digit(hopCount);
        SortedSet<NodeAddress> e = R_n[d];
        //
        // Search forward to the next non-vacuous entry (which must exist
        // since this node's address appears as an entry).  By construction,
        // that entry is responsible for routing the part of the address space
        // containing id.  (If it doesn't match id itself, then it acts as a
        // surrogate for the nonexistent, but desired entry.)
        //
        while (e.size() == 0) {
            d = (d + 1) % TitanGuidImpl.radix;
            e = R_n[d];
        }

        //
        // Pick the neighbour with the best reputation.
        //
        NodeAddress route = e.first();
        //System.out.printf("%s -> [%d,%d] hit: %s\n", destination, hopCount, d, route);

        if (this.node.getNodeAddress().equals(route)) {
            //
            // The next hop in routing this address is to this node itself.
            // Rather than forcing explicit communication with this node in
            // preparation for the next hop, short circuit and route the next
            // hop directly.
            //
            return this.newGetRoute(destination, hopCount + 1);
        }

//        System.out.println("getRoute: " + destination + " level=" + hopCount + " size=" + e.size() + " route=" + e + (e.size() > 0 ? e.first() : "null"));
        //
        // XXX: How can this test fail?
        //
        if (e.size() > 0) {
            return e.first();
        }
        return null;
    }

    public NodeAddress getRoute(TitanNodeId objectId) {
        return this.newGetRoute(objectId, 0);
    }

    /**
     * <p>
     * Return {@code true} if this {@code NeighbourMap} is the root of the given {@link TitanGuidImpl} {@code objectId}.
     * </p>
     * @param objectId
     * @return true if this neighbour map cannot route the given {@code BeehiveObjectId} to a neighbour.
     */
    public boolean isRoot(TitanGuid objectId) {
        return this.newGetRoute(objectId, 0) == null;
        //return this.getRoute(objectId) == null;
    }
    
    /**
     * Produce an XML representation of this {@code NeighbourMap}.
     * 
     * @return XML.Content contain the XML representation of this {@link NeighbourMap}.
     */
    public XMLRoutingTable toXML() {
        TitanXML xml = new TitanXML();

        XMLRoutingTable table = xml.newXMLRoutingTable(this.node.getNodeAddress().getObjectId(), 0, 64);
        table.bindNameSpace();

        for (int digit = 0; digit < TitanGuidImpl.radix; digit++) {
            for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
                if (this.routes[level][digit].size() == 0) {

                } else {
                    XMLRoute route = xml.newXMLRoute(digit, level);
                    for (NodeAddress n : this.routes[level][digit]) {
                        route.add(xml.newXMLRouteNode(n.getObjectId(), n.getInternetworkAddress().getAddress().toString(), n.getBeehivePort(), n.getHTTPInterface().getPort()));
                    }
                    table.add(route);
                }                
            }
        }
        
        return table;
    }

    /**
     * Produce an XHTML formatted representation of this NeighbourMap.
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

    	XHTML.Table.Row row = new XHTML.Table.Row(new XHTML.Table.Heading(""));
        
        for (int i = 0; i < TitanGuidImpl.n_digits; i++) {
        	row.add(new XHTML.Table.Heading(String.format("%02X", i)));
        }
        XHTML.Table.Head thead = new XHTML.Table.Head(row);
        
        XHTML.Table.Body tbody = new XHTML.Table.Body();

        boolean useDojo = true;

        for (int digit = 0; digit < TitanGuidImpl.radix; digit++) {
            row = new XHTML.Table.Row(new XHTML.Table.Data().add(Integer.toHexString(digit).toUpperCase()));
            for (int level = 0; level < TitanGuidImpl.n_digits; level++) {
                String cellId = String.format("n%01x%02x", digit, level);
                XHTML.Table.Data cell = new XHTML.Table.Data();
                if (this.routes[level][digit].size() == 0) {
                    cell.add(XHTML.CharacterEntity.nbsp);
                } else {
                    cell.setId(cellId);
                    if (useDojo) {
                        cell.add(this.routes[level][digit].size());

                        XHTML.Div dojoTooltip = new XHTML.Div().setClass("neighbour");
                        dojoTooltip.addAttribute(new XML.Attr("dojoType", "sunlabs.StickyTooltip")).addAttribute(new XML.Attr("connectId", cellId));
                        for (NodeAddress a : this.routes[level][digit]) {
                        	dojoTooltip.add(WebDAVDaemon.inspectNodeXHTML(a).add(" [X] [P]").add(new XHTML.Break()));
                        }
                        cell.add(dojoTooltip);
                    } else {
                        NodeAddress firstNode = this.routes[level][digit].first();
                        XHTML.Anchor link = new XHTML.Anchor(" ").add(this.routes[level][digit].size()).add(" ").setHref(XHTML.SafeURL(firstNode.getHTTPInterface()));
                        link.setTitle(firstNode.getObjectId());

                        cell.add(link);
                    }

                    cell.setClass("full");
                }
                row.add(cell);
            }
            tbody.add(row);
        }
        XHTML.Table table = new XHTML.Table(new XHTML.Table.Caption("Routing Table")).add(thead, tbody).setClass("neighbour-map");

        return table;
    }

    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        for (String name : this.mapReputationRequirements.keySet()) {
            list.add(new Attribute(name, this.mapReputationRequirements.get(name)));
        }

        return list;
    }
    
    public Dossier getDossier() {
        return this.dossier;
    }

    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList result = new AttributeList();
        for (Object attr : attributes) {
            Attribute a = (Attribute) attr;
            String name = a.getName();
            Object value = a.getValue();
            result.add(new Attribute(name, value));
        }


//      try {
//      save();
//      } catch (IOException e) {
//      return new AttributeList();
//      }
        return result;
    }

    public Object getAttribute(String attribute) throws
            AttributeNotFoundException,
            MBeanException,
            ReflectionException {
        System.out.println("getAttribute: " + attribute);
        Integer value = this.mapReputationRequirements.get(attribute);
        if (value != null) {
            System.out.println("getAttribute: return: " + value.toString());
            return value.toString();
        }
        if (attribute.compareTo("objectId") == 0)
            return new TitanGuidImpl();
        System.out.println("getAttribute: AttributeNotFoundException");
        throw new AttributeNotFoundException("No such property: " + attribute);
    }

    public void setAttribute(Attribute attribute) throws
            AttributeNotFoundException,
            InvalidAttributeValueException,
            MBeanException,
            ReflectionException {
        String name = attribute.getName();

        System.out.println("setAttribute: " + name);

        if (this.mapReputationRequirements.get(name) == null) {
            System.err.println("Attribute not found: " + name);
            throw new AttributeNotFoundException(name);
        }
        Object value = attribute.getValue();
        if (!(value instanceof String)) {
            System.err.println("Attribute not a String: " + value.getClass());
            throw new InvalidAttributeValueException("Attribute value not a string: " + value);
        }
        this.mapReputationRequirements.put(name, new Integer(value.toString()));
        System.out.println("setAttribute: done");
    }

    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        if (actionName.equals("reload") &&
                (params == null || params.length == 0) &&
                (signature == null || signature.length == 0)) {
            return null;
        }

        throw new ReflectionException(new NoSuchMethodException(actionName));
    }

    public MBeanInfo getMBeanInfo() {
        SortedMap<String,MBeanAttributeInfo> attributes = new TreeMap<String,MBeanAttributeInfo>();

        for (String name : this.mapReputationRequirements.keySet()) {
            attributes.put(name,
                    new MBeanAttributeInfo(
                    name,
                    "java.lang.String",
                    name,
                    true,   // isReadable
                    true,   // isWritable
                    false)); // isIs
        }

        MBeanOperationInfo[] opers = {
            new MBeanOperationInfo(
                    "reload",
                    "Reload properties from file",
                    null,   // no parameters
                    "void",
                    MBeanOperationInfo.ACTION)
        };
        return new MBeanInfo(
                this.getClass().getName(),
                "NeighborMap Manager MBean",
                attributes.values().toArray(new MBeanAttributeInfo[0]),
                null,  // constructors
                opers,
                null); // notifications
    }

    /**
     * Given a {@link TitanGuidImpl} {@code root} as the central node, compare successive {@code BeehiveObjectId}
     * instances ordering them as which would be the next in routing succession to replace the central node.
     * Note that this is determined from information in the local neighbor-map, not perfect global information.
     */
    public static class RouteSuccession implements Comparator<NodeAddress>, Serializable {
        private static final long serialVersionUID = 1L;
        private TitanGuid root;

        public RouteSuccession(TitanGuid root) {
            this.root = root;
        }
        /**
         * (non-Javadoc)
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        public int compare(NodeAddress a, NodeAddress b) {
            int aOrder = this.root.distance(a.getObjectId());
            int bOrder = this.root.distance(b.getObjectId());
            int order = (aOrder - bOrder);
            if (order == 0) {
                int hint = a.getObjectId().compareTo(b.getObjectId());
//                System.out.printf("RoutingComparator: %s %s %d %d  %d (%d)%n", a.getObjectId(), b.getObjectId(), aOrder, bOrder, order, hint);
                return hint;
            } else {
//                System.out.printf("RoutingComparator: %s %s %d %d  %d%n", a.getObjectId(), b.getObjectId(), aOrder, bOrder, order);
            }

            return (aOrder - bOrder);
        }
    }

    public static void main(String[] args) throws Exception {
        /*
         *
   0A2405E8F6ECDF6BCA46ED42329254694E3296137097A7D240199D2A499EB9FB:12016:12017:10.0.1.7
   4F1C093881E662D8DD3076132888D57DC30AF9E02FAFF6D2876B7D67AA995D57:12006:12007:10.0.1.7
   59CA2A9B3C33E8348CEE5E04B8D427C8E6D095FBBE73FAF8A10037D72DF25C9E:12002:12003:10.0.1.7
   70BDA02F1C1AE133ED319C2BD5D870FA173A01CAB2CC9FF6CAF48380BF036D39:12010:12011:10.0.1.7
   937E905EF46E7130C7DAE38E4B825B89E0D7060E958939B35114933E18EB0BCB:12008:12009:10.0.1.7
   9A6C4BD1E00857CBB450B68D3A12FE2E2A45F1AF9DE3FBAC544B1DB5D75876BE:12004:12005:10.0.1.7
   9FA065D1DC129FDA5299A6B878F34C5CD00E2DCA361C00DAA6B0AD777B7864EF:12000:12001:10.0.1.7
   D51346EB45D040E32685EC3400E99051E599719C1A9740BDF38E9A35C98E02DE:12014:12015:10.0.1.7
         */
        TitanGuidImpl myId = new TitanGuidImpl("9A6C4BD1E00857CBB450B68D3A12FE2E2A45F1AF9DE3FBAC544B1DB5D75876BE");
        TitanGuidImpl otherId = new TitanGuidImpl("937E905EF46E7130C7DAE38E4B825B89E0D7060E958939B35114933E18EB0BCB");

        Set<NodeAddress> nodes = new HashSet<NodeAddress>();
        nodes.add(new NodeAddress("026137909DF5DCA874AB6746D3E03B50A020E72D586E2CDD62706E801FE991C7:12018:12019:10.0.1.7"));
        nodes.add(new NodeAddress("9FA065D1DC129FDA5299A6B878F34C5CD00E2DCA361C00DAA6B0AD777B7864EF:12000:12001:10.0.1.7"));
        nodes.add(new NodeAddress("4F1C093881E662D8DD3076132888D57DC30AF9E02FAFF6D2876B7D67AA995D57:12006:12007:10.0.1.7"));
        nodes.add(new NodeAddress("59CA2A9B3C33E8348CEE5E04B8D427C8E6D095FBBE73FAF8A10037D72DF25C9E:12002:12003:10.0.1.7"));
        nodes.add(new NodeAddress("937E905EF46E7130C7DAE38E4B825B89E0D7060E958939B35114933E18EB0BCB:12008:12009:10.0.1.7"));
        nodes.add(new NodeAddress("70BDA02F1C1AE133ED319C2BD5D870FA173A01CAB2CC9FF6CAF48380BF036D39:12010:12011:10.0.1.7"));
        nodes.add(new NodeAddress("9A6C4BD1E00857CBB450B68D3A12FE2E2A45F1AF9DE3FBAC544B1DB5D75876BE:12004:12005:10.0.1.7"));
        nodes.add(new NodeAddress("D51346EB45D040E32685EC3400E99051E599719C1A9740BDF38E9A35C98E02DE:12014:12015:10.0.1.7"));
        nodes.add(new NodeAddress("0A2405E8F6ECDF6BCA46ED42329254694E3296137097A7D240199D2A499EB9FB:12016:12017:10.0.1.7"));

        SortedSet<NodeAddress> sorted = new TreeSet<NodeAddress>(new Comparator<NodeAddress>() {
            public int compare(NodeAddress o1, NodeAddress o2) {
                int result = o1.getObjectId().compareTo(o2.getObjectId());
                return result;
            }
        });

        myId.distance(otherId);



        for (NodeAddress k : nodes) {
            sorted.add(k);
        }

        for (NodeAddress k : sorted) {
            System.out.printf("%s%n", k.format());
        }

        SortedSet<NodeAddress> successors = new TreeSet<NodeAddress>(new RouteSuccession(myId));
        successors.addAll(nodes);
        NodeAddress[] orderedArray = new NodeAddress[nodes.size()];
        int i = 0;
        for (NodeAddress k : nodes) {
            orderedArray[i++] = k;
        }
        Arrays.sort(orderedArray, new RouteSuccession(myId));

        System.out.printf("ordered-----%s%n", myId);
        for (NodeAddress k : successors) {
            System.out.printf("%s%n", k.format());
        }
        System.out.printf("ordered-----%s%n", myId);
        for (int j = 0; j < orderedArray.length; j++) {
            System.out.printf("%s%n", orderedArray[j].format());
        }
    }
}
