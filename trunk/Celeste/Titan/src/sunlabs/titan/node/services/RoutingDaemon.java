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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.JMException;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.Dossier;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.Reputation;

/**
 * A Beehive Node routing table maintenance daemon.
 *
 * Iterate through our neighbour map, introducing ourselves, updating
 * our own map.
 */
public final class RoutingDaemon extends AbstractTitanService implements RoutingDaemonMBean {

    public interface IntroductionMBean extends ThreadMBean {
        public String getJMXCurrentIntroductionRate();
        public long getIntroductionRate();
        public String getLastRunDuration();
        public String getLastRunTime();
        public String getTimeToNextRun();
        public void setIntroductionRate(long seconds);
        public void wakeup();
    }
    
    /**
     * Periodically re-introduce this node to each of its neighbours in the routing table.
     */
    private class Introduction extends Thread implements IntroductionMBean {
        // "currentIntroductionRate" is a ramp which starts low (not zero!) and doubles at each iteration
        // reaching its maximum value which is limited to NeighbourmapApplication.this.refreshRate.
        private long currentIntroductionRate;
        private long lastRunTime;
        private long lastRunDuration;
        private long wakeUpTime;
        private final ObjectName jmxObjectName;

        Introduction() throws JMException {
            super(RoutingDaemon.this.node.getThreadGroup(), RoutingDaemon.this.node.getNodeId() + " " + RoutingDaemon.name + ".Introduction");
            this.setPriority(Thread.NORM_PRIORITY);
            this.currentIntroductionRate = this.getIntroductionRate();
            this.wakeUpTime = 0;

            if (RoutingDaemon.this.jmxObjectNameRoot != null) {
                this.jmxObjectName = JMX.objectName(RoutingDaemon.this.jmxObjectNameRoot, "Introduction");
                ManagementFactory.getPlatformMBeanServer().registerMBean(this, this.jmxObjectName);
            } else {
                this.jmxObjectName = null;
            }
        }

        public String getJMXCurrentIntroductionRate() {
            return String.format("%dms", this.currentIntroductionRate);
        }

        public long getIntroductionRate() {
            return RoutingDaemon.this.node.getConfiguration().asLong(RoutingDaemon.IntroductionRateSeconds);
        }

        public String getLastRunDuration() {
            return String.format("%dms", this.lastRunDuration);
        }

        public String getLastRunTime() {
            return new Date(this.lastRunTime).toString();
        }

        public String getTimeToNextRun() {
            if (this.wakeUpTime != 0){
                return new Date(this.wakeUpTime).toString();
            }
            return "";
        }

        @Override
        public void run() {
            try {
                // Iterate through our neighbour map, reintroducing ourselves, updating
                // our own map from data obtained from our neighbours.
                //
                // For each level in our routing table, send a Beehive Ping message to that node.
                // In return that node will return a reply containing its routing table.
                // We will use that first level to (re) populate our routing table.

                while (!interrupted()) {
                    RoutingDaemon.this.setStatus(String.format("Running"));
                    this.lastRunTime = System.currentTimeMillis();
                    RoutingDaemon.this.log.finest("Running");

                    Set<NodeAddress> neighbours = RoutingDaemon.this.node.getNeighbourMap().keySet();

                    if (neighbours.size() > 1) {
                        Set<NodeAddress> potentialNeighbours = new HashSet<NodeAddress>();

                        for (NodeAddress address : neighbours) {
                            try {
                                PingOperation.Response pong = RoutingDaemon.this.ping(address, new PingOperation.Request(new byte[0]));
                                potentialNeighbours.addAll(pong.getNeighbourSet());
                            } catch (Exception e) {
                                RoutingDaemon.this.log.fine("Failed: " + address);
                                RoutingDaemon.this.node.getNeighbourMap().remove(address);
                            }
                            // In the interest of being nice, throttle ourselves.
//                            synchronized (this) {
//                                this.wait(Time.secondsInMilliseconds(2));
//                            }
                        }
                        //potentialNeighbours.remove(NeighbourMapApplication.this.node.getAddress().toString());

                        // At this point, the Set "nodes" contains every NodeAddress
                        // that is a neighbour of our neighbours, excluding this node.
                        // For each of these NodeAddresses that is not already
                        // in the local routing table, perform a Join with them.
                        for (NodeAddress potentialNeighbour : potentialNeighbours) {
                            if (!RoutingDaemon.this.node.getNeighbourMap().keySet().contains(potentialNeighbour)) {
                                try {
                                    RoutingDaemon.this.node.getNeighbourMap().add(potentialNeighbour);
                                } catch (Exception reportAndIgnore) {
                                    reportAndIgnore.printStackTrace();
                                    RoutingDaemon.this.log.finest(potentialNeighbour.toString() + " " + reportAndIgnore.toString());
                                }
                            }
                        }
                    } else {
                        // If we have nothing in our neighbour map, we need to bump
                        // the reunion daemon to try to find something.
                        if (RoutingDaemon.this.reunion != null) {
                            RoutingDaemon.this.log.fine(String.format("Neighbour map empty.  Notifying Reunion daemon."));
                            synchronized (RoutingDaemon.this.reunion) {
                                RoutingDaemon.this.reunion.notifyAll();
                            }
                        } else {
                            RoutingDaemon.this.log.fine(String.format("Neighbour map empty.  No Reunion daemon."));
                        }
                    }

                    long introductionRateMillis = Time.secondsInMilliseconds(RoutingDaemon.this.node.getConfiguration().asLong(RoutingDaemon.IntroductionRateSeconds));;

                    if (this.currentIntroductionRate < introductionRateMillis) {
                        this.currentIntroductionRate *= 2;
                    }
                    if (this.currentIntroductionRate > introductionRateMillis) {
                        this.currentIntroductionRate = introductionRateMillis;
                    }

                    long currentTime = System.currentTimeMillis();
                    this.lastRunDuration = currentTime - this.lastRunTime;

                    long sleepTime = Math.max(this.currentIntroductionRate - this.lastRunDuration, 0);
                    this.wakeUpTime = currentTime + sleepTime;

                    RoutingDaemon.this.setStatus("Wakeup " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(this.wakeUpTime)));

                    RoutingDaemon.this.setStatus(String.format("Wakeup %1$td/%1$tm/%1$ty %1$tH:%1$tM:%1$tS", new Date(this.wakeUpTime)));

                    RoutingDaemon.this.log.fine(String.format("Desired introductionRate=%ss currentIntroductionRate=%dms elasped time=%dms, sleepTime=%dms",
                            RoutingDaemon.this.node.getConfiguration().asLong(RoutingDaemon.IntroductionRateSeconds),
                            this.currentIntroductionRate, this.lastRunDuration, sleepTime));

                    if (sleepTime < 0) {
                        sleepTime = 0;
                        this.currentIntroductionRate = this.lastRunDuration;
                    }
                    synchronized (this) {
                        this.wait(sleepTime);
                    }
                    this.wakeUpTime = 0;
                } // while
            } catch (InterruptedException e) {
                // Do nothing, let the thread stop.
            }

            if (this.jmxObjectName != null) {
                try {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(this.jmxObjectName);
                } catch (JMException ignore) {
                    
                }
            }

            setStatus("stopped");
            return;
        }

        public void setIntroductionRate(long seconds) {
            RoutingDaemon.this.node.getConfiguration().set(RoutingDaemon.IntroductionRateSeconds, seconds);
        }

        public void wakeup() {
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    /**
     * The payload of a Join message.
     *
     */
    public static class JoinOperation {
        public static class Request implements Serializable {
            private final static long serialVersionUID = 1L;

            public Request() {
            }
        }

        /**
         * A Join operation response.
         *
         * The Join operation response contains:
         * <ul>
         * <li>The {@link TitanGuid} of the Beehive network.</li>
         * <li>A {@link Set} containing this node's routing table.</li>
         * <li>A {@link Map} of this node's object publishers.</li>
         * </ul>
         */
        public static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private TitanGuid networkObjectId;
            private Set<NodeAddress> routingTable;
            private Map<TitanGuid,Set<Publishers.PublishRecord>> publishRecords;

            public Response(TitanGuid networkObjectId, Set<NodeAddress> map, Map<TitanGuid,Set<Publishers.PublishRecord>> roots) {
                this.networkObjectId = networkObjectId;
                this.routingTable = map;
                this.publishRecords = roots;
            }

            public Set<NodeAddress> getMap() {
                return this.routingTable;
            }

            public TitanGuid getNetworkObjectId() {
                return this.networkObjectId;
            }

            /**
             * Return the encapsulated Map of {@link TitanGuid}s to {@link Set}s
             * of {@link sunlabs.titan.node.Publishers.PublishRecord} instances.
             */
            public Map<TitanGuid,Set<Publishers.PublishRecord>> getPublishRecords() {
                return this.publishRecords;
            }

            public void setMap(Set<NodeAddress> map) {
                this.routingTable = map;
            }
        }
    }

    /**
     *
     */
    public static class PingOperation {
        /**
         * The request form of a Ping operation.
         *
         */
        public static class Request implements Serializable {
            private final static long serialVersionUID = 1L;

            private byte[] data;

            public Request(byte[] data) {
                this.data = data;
            }

            public byte[] getData() {
                return this.data;
            }
        }

        /**
         * A ping response contains the respondent's routing table and the set of object publishers.
         *
         */
        public static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private Set<NodeAddress> neighbourSet;
            private Map<TitanGuid,Set<Publishers.PublishRecord>> publishers;

            public Response(Set<NodeAddress> neighbourMap, Map<TitanGuid,Set<Publishers.PublishRecord>> publisherMap) {
                this.neighbourSet = neighbourMap;
                this.publishers = publisherMap;
            }

            /**
             * Get the {@link Set} of {@link NodeAddress} instances from this response.
             */
            public Set<NodeAddress> getNeighbourSet() {
                return this.neighbourSet;
            }

            /**
             * Get the {@code Map<BeehiveObjectId,Map<BeehiveObjectId,Publishers.Publisher>>}
             * instances from this response.
             */
            public Map<TitanGuid,Set<Publishers.PublishRecord>> getPublishRecords() {
                return this.publishers;
            }
        }
    }

    private class Reunion extends Thread implements ReunionMBean {
        private long lastRunTime;
        private long lastRunDuration;
        private final ObjectName jmxObjectName;

        protected Reunion() throws JMException {
            super(RoutingDaemon.this.node.getThreadGroup(), RoutingDaemon.this.node.getNodeId() + ":" + RoutingDaemon.this.getName() + ".ReunionDaemon");
            this.setPriority(Thread.NORM_PRIORITY);
            if (RoutingDaemon.this.jmxObjectNameRoot != null) {
                this.jmxObjectName = JMX.objectName(RoutingDaemon.this.jmxObjectNameRoot, "ReunionDaemon");
                ManagementFactory.getPlatformMBeanServer().registerMBean(this, this.jmxObjectName);
            } else {
                this.jmxObjectName = null;
            }
        }

//        public String getInfo() {
//            return "Last run time: " + new Date(this.lastRunTime) + " duration: " + this.lastRunDuration + "ms";
//        }

        public String getLastRunDuration() {
            return Long.toString(this.lastRunDuration) + "ms";
        }

        public String getLastRunTime() {
            return new Date(this.lastRunTime).toString();
        }

        public long getReunionRate() {
            return RoutingDaemon.this.node.getConfiguration().asLong(RoutingDaemon.ReunionRateSeconds);
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    if (RoutingDaemon.this.log.isLoggable(Level.FINE)) {
                        RoutingDaemon.this.log.fine("Sleeping %dms", this.getReunionRate());
                    }
                    synchronized (this) {
                        this.wait(this.getReunionRate());
                    }

                    this.lastRunTime = System.currentTimeMillis();
                    for (Map.Entry<TitanGuid,Dossier.Entry> mapEntry : RoutingDaemon.this.node.getNeighbourMap().getDossier().entrySet()) {
                        // Since we don't lock the Dossier, the next statement may fail to produce the Entry because it's been deleted.
                        Dossier.Entry entry = mapEntry.getValue();
                        if (entry != null) {
                            NodeAddress address = entry.getNodeAddress();
                            if (address != null) {
                                if (RoutingDaemon.this.log.isLoggable(Level.FINE)) {
                                    RoutingDaemon.this.log.fine("Reunion %s", address.format());
                                }

                                //  Reunite only with nodes that we're not already connected to.
                                if (!RoutingDaemon.this.node.getNeighbourMap().keySet().contains(address)) {
                                    if (!address.getObjectId().equals(RoutingDaemon.this.node.getNodeId())) {
                                        try {
                                            if (RoutingDaemon.this.ping(address, new PingOperation.Request(new byte[0])) != null) {
                                                /**/
                                            } else {
                                                if (RoutingDaemon.this.log.isLoggable(Level.FINE)) {
                                                    RoutingDaemon.this.log.fine("fail %s", address.format());
                                                }
                                                // Get dossier entry: if it is too old, then delete it
                                                long tooOld = System.currentTimeMillis() - RoutingDaemon.this.node.getConfiguration().asLong(RoutingDaemon.DossierTimeToLive);
                                                Dossier.Entry e = RoutingDaemon.this.node.getNeighbourMap().getDossier().getEntryAndLock(address);
                                                try {
                                                    if (e.getTimestamp() < tooOld) {
                                                        if (RoutingDaemon.this.log.isLoggable(Level.FINEST)) {
                                                            RoutingDaemon.this.log.finest("Removing old Dossier %s", address.format());
                                                        }
                                                        RoutingDaemon.this.node.getNeighbourMap().getDossier().removeEntry(e);
                                                    }
                                                } finally {
                                                    RoutingDaemon.this.node.getNeighbourMap().getDossier().unlockEntry(e);
                                                }
                                            }
                                        } catch (Exception reportAndIgnore) {
                                            reportAndIgnore.printStackTrace();
                                            if (RoutingDaemon.this.log.isLoggable(Level.FINE)) {
                                                RoutingDaemon.this.log.fine("fail %s %s", address.format(), reportAndIgnore.toString());
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (RoutingDaemon.this.log.isLoggable(Level.WARNING)) {
                                    RoutingDaemon.this.log.warning("Dossier entry for %s had no address.", mapEntry.getKey());
                                }
                            }
                        }
                        this.lastRunDuration = System.currentTimeMillis() - this.lastRunTime;

                        synchronized (this) {
                            this.wait(Time.minutesInMilliseconds(1));
                        }
                    }


                } // while
            } catch (InterruptedException e) {
                // Do nothing, let the thread stop.
            }

            if (this.jmxObjectName != null) {
                try {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(this.jmxObjectName);
                } catch (JMException ignore) {

                }
            }
            return;
        }

        public void setReunionRate(long seconds) {
           RoutingDaemon.this.node.getConfiguration().set(RoutingDaemon.ReunionRateSeconds, seconds);
        }

        public void wakeup() {
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    public interface ReunionMBean extends ThreadMBean {
        public String getLastRunDuration();
        public String getLastRunTime();
        public long getReunionRate();
        public void setReunionRate(long refreshRate);
        public void wakeup();
    }
    private final static long serialVersionUID = 1L;

    private final static String name = AbstractTitanService.makeName(RoutingDaemon.class, RoutingDaemon.serialVersionUID);

    /** The number of seconds between iterations of the neighbour map introduction */
    private final static Attributes.Prototype IntroductionRateSeconds = new Attributes.Prototype(RoutingDaemon.class, "IntroductionRateSeconds",
            Time.minutesInSeconds(10),
            "The number of seconds between iterations of the neighbour map introduction.");

    /** The number of  milliseconds between iterations of the neighbour map reunion */
    private final static Attributes.Prototype ReunionRateSeconds = new Attributes.Prototype(RoutingDaemon.class, "ReunionRateSeconds",
                Time.hoursInSeconds(1),
                "The number of seconds between iterations of the neighbour map reunion.");

    /**
     * The number of milliseconds until a {@link Dossier.Entry} becomes
     * old and is removed.
     */
    private final static Attributes.Prototype DossierTimeToLive = new Attributes.Prototype(RoutingDaemon.class, "DossierTimeToLive",
            Time.daysToMilliseconds(30),
            "The number of milliseconds until a Dossier.Entry becomes old and is removed.");

    transient private Introduction introductionDaemon;

    transient private Reunion reunion;

    public RoutingDaemon(final BeehiveNode node) throws JMException {
        super(node, RoutingDaemon.name, "Maintain routing table.");
        
        node.configuration.add(RoutingDaemon.IntroductionRateSeconds);
        node.configuration.add(RoutingDaemon.DossierTimeToLive);
        node.configuration.add(RoutingDaemon.ReunionRateSeconds);

        Map<String,Integer> mapReputationRequirements = Reputation.newCoefficients();
        mapReputationRequirements.put(Dossier.LATENCY, new Integer(50));
        mapReputationRequirements.put(Dossier.PUBLISHER, new Integer(25));
        mapReputationRequirements.put(Dossier.ROUTING, new Integer(25));

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", this.node.getConfiguration().get(RoutingDaemon.IntroductionRateSeconds));
            this.log.config("%s", this.node.getConfiguration().get(RoutingDaemon.ReunionRateSeconds));
            this.log.config("%s", this.node.getConfiguration().get(RoutingDaemon.DossierTimeToLive));
            if (this.node.getConfiguration().get(BeehiveNode.ClientTimeoutSeconds).asLong() < this.node.getConfiguration().get(RoutingDaemon.IntroductionRateSeconds).asLong()
                    || this.node.getConfiguration().get(BeehiveNode.ClientTimeoutSeconds).asLong() < this.node.getConfiguration().get(RoutingDaemon.ReunionRateSeconds).asLong()) {
                this.log.config("Warning %s is less than either %s or %s and will result in unnecessary close and reopen of connections.",
                        BeehiveNode.ClientTimeoutSeconds,
                        RoutingDaemon.IntroductionRateSeconds,
                        RoutingDaemon.ReunionRateSeconds);
            }
        }
    }

    /**
     * Receive and process a Join message.
     *
     * <p>
     * Route a message to the next hop for the destination node object-id.
     * If no more hops, then this node is the root node for the joining node
     * objectId so we must package up our routing table and send it back.
     * </p>
     * <p>
     * Otherwise, wait for the response and append our routing table and respond.
     * The original sender should get a "fully" populated map suitable for it
     * to drop into place.
     * </p>
     */
    public BeehiveMessage join(BeehiveMessage message) throws ClassNotFoundException, ClassCastException, BeehiveMessage.RemoteException {
        //this.log.entering(message.toString());

        BeehiveMessage reply;

        if (this.node.getNeighbourMap().getRoute(message.getDestinationNodeId()) != null) {
            // This clause is executed on the non-root nodes of the joining
            // object-id.  ONLY after the root has created a response: we only
            // augment the data in the response.
            reply = this.node.transmit(message);
            JoinOperation.Response response = reply.getPayload(JoinOperation.Response.class, this.node);
            Set<NodeAddress> addresses = response.getMap();
            addresses.addAll(this.node.getNeighbourMap().keySet());
            response.setMap(addresses);
            reply = this.node.replyTo(message, response);
        } else {
            // This clause is executed on the root node of the joining object-id.
            Map<TitanGuid,Set<Publishers.PublishRecord>> objectRoots = new Hashtable<TitanGuid,Set<Publishers.PublishRecord>>();

            for (TitanGuid objectId : this.node.getObjectPublishers().keySet()) {
                if (this.node.getNeighbourMap().isRoot(objectId)) {
                    objectRoots.put(objectId, this.node.getObjectPublishers().getPublishers(objectId));
                }
            }

            JoinOperation.Response response = new JoinOperation.Response(this.node.getNetworkObjectId(), this.node.getNeighbourMap().keySet(), objectRoots);

            reply = this.node.replyTo(message, response);
        }

        return reply;
    }

    /**
     *
     * "Top-side" {@link AbstractTitanService} method to perform a "join" with a Beehive system,
     * using the given {@link NodeAddress NodeAddress} gateway as the joining server for the network.
     *
     */
    public JoinOperation.Response join(NodeAddress gateway) throws IOException {

        JoinOperation.Request request = new JoinOperation.Request();

        // Join messages must be multicast so each node along the routing path
        // has an opportunity to annotate the response from the root.
        BeehiveMessage message = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.node.getNodeAddress(),
                this.node.getNodeId(),
                TitanGuidImpl.ANY,
                RoutingDaemon.name,
                "join",
                BeehiveMessage.Transmission.MULTICAST,
                BeehiveMessage.Route.LOOSELY,
                request
        );
//        message.setTraced(true);

        // Don't bother to join with ourselves.
        if (gateway.equals(this.node.getNodeAddress())) {
            return null;
        }

        BeehiveMessage reply;
        if ((reply = this.node.transmit(gateway, message)) == null) {
            return null;
        }

        try {
            JoinOperation.Response response = reply.getPayload(JoinOperation.Response.class, this.node);

            Map<TitanGuid,Set<Publishers.PublishRecord>> publishRecords = response.getPublishRecords();
            this.log.finest("%d publish records", publishRecords.size());
            for (TitanGuid objectId : publishRecords.keySet()) {
                Set<Publishers.PublishRecord> publishers = publishRecords.get(objectId);
                this.log.finest("Adding publish record %s", publishers);
                this.node.getObjectPublishers().update(objectId, publishers);
            }

            // For each NodeAddress in the reply, add it to our local neighbour-map.
            for (NodeAddress nodeAddress : response.getMap()) {
                if (!nodeAddress.equals(this.node.getNodeAddress())) {
                    this.node.getNeighbourMap().add(nodeAddress);
                }
            }

            // At this point this node should be setup and ready to participate.
            // Object publishers has been copied from the old-root.
            // The neighbour-map is populated.
            // The census data is inherited.
            // The next time the introduction daemon runs, this node will announce its debut.

            // ping the old root and get an updated set of parameters...

            return response;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Respond to a ping message.
     * <p>
     * This method can be used with both UNICAST and MULTICAST messages.
     * If we are not the terminal or destination node for the message,
     * forward the message to the next hop.
     * If we are the terminal or destination node for this message,
     * reply with a message containing a {@link PingOperation.Response}.
     * </p>
     * <p>
     * Add the pinging node to our neighbour table.
     * </p>
     */
    public BeehiveMessage ping(BeehiveMessage message) throws ClassNotFoundException, ClassCastException, RemoteException {
        this.node.getNeighbourMap().add(message.getSource());

        BeehiveMessage reply;
        PingOperation.Request request = message.getPayload(PingOperation.Request.class, this.node);

        if (this.node.getNeighbourMap().isRoot(message.getDestinationNodeId())) {
            //
            // Respond with our NeighbourMap and our set of
            // object backpointers.
            //
            Map<TitanGuid,Set<Publishers.PublishRecord>> publishers = new HashMap<TitanGuid,Set<Publishers.PublishRecord>>();

            for (TitanGuid objectId : this.node.getObjectPublishers().keySet()) {
                HashSet<Publishers.PublishRecord> includedPublishers = new HashSet<Publishers.PublishRecord>();
                publishers.put(objectId, includedPublishers);
            }
            PingOperation.Response response = new PingOperation.Response(this.node.getNeighbourMap().keySet(), publishers);
            reply = this.node.replyTo(message, response);
        } else {
            reply = this.node.transmit(message);
        }

        return reply;
    }

    /**
     * Transmit a ping message to the given {@link NodeAddress} {@code target}.
     *
     * @param target the destination {@link NodeAddress} of the {@link BeehiveNode} to ping.
     * @param request the {@link PingOperation.Request} to transmit to the destination {@code BeehiveNode}
     */
    public PingOperation.Response ping(NodeAddress target, PingOperation.Request request) throws IOException, RemoteException {
        long startTime = System.currentTimeMillis();
        this.log.finest("%s", target.format());
        BeehiveMessage reply = this.node.sendToNode(target.getObjectId(), RoutingDaemon.name, "ping", request);
        long latency = System.currentTimeMillis() - startTime;
        this.log.finest("%s response %dms", target.format(), latency);

        if (reply != null) {
            if (reply.getStatus().isSuccessful()) {
                try {
                    PingOperation.Response response = reply.getPayload(PingOperation.Response.class, this.node);

                    Dossier.Entry e = RoutingDaemon.this.node.getNeighbourMap().getDossier().getEntryAndLock(target);
                    try {
                        e.setTimestamp().addSample(Dossier.LATENCY, latency).success(Dossier.AVAILABLE);
                        RoutingDaemon.this.node.getNeighbourMap().getDossier().put(e);
                    } finally {
                        RoutingDaemon.this.node.getNeighbourMap().getDossier().unlockEntry(e);
                    }

                    for (TitanGuid objectId : response.getPublishRecords().keySet()) {
                        Set<Publishers.PublishRecord> publisherSet = response.getPublishRecords().get(objectId);
                        this.node.getObjectPublishers().update(objectId, publisherSet);
                    }
                    return response;
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (ClassCastException e) {
                    e.printStackTrace();
                }
            }
        }

        RoutingDaemon.this.node.getNeighbourMap().getDossier().failure(target, Dossier.AVAILABLE);
        return null;
    }

    @Override
    public synchronized void start() throws Exception {
        if (this.introductionDaemon == null) {
            this.setStatus("start");
            this.introductionDaemon = new Introduction();
            this.introductionDaemon.start();
        }
        if (this.reunion == null) {
            this.reunion = new Reunion();
            this.reunion.start();
        }
    }

    @Override
    public void stop() {
//        this.setStatus("stopped");
//        if (this.introductionDaemon != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.introductionDaemon);
//            }
//            this.introductionDaemon.interrupt(); // Logged
//            this.introductionDaemon = null;
//        }
//
//        if (this.reunion != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.reunion);
//            }
//            this.reunion.interrupt(); // Logged
//            this.reunion = null;
//        }
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        String action = HttpMessage.asString(props.get("action"), null);

        if (action != null) {
            if (action.equals("stop")) {
                this.stop();
            } else if (action.equals("run")) {
                this.introductionDaemon.wakeup();
            } else if (action.equals("disconnect")) {
                try {
                    this.node.getNeighbourMap().remove(new NodeAddress(HttpMessage.asString(props.get("address"), null)));
                } catch (NumberFormatException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        XHTML.Button start = new XHTML.Button("Start").setType(XHTML.Button.Type.SUBMIT)
        .setName("action").setValue("start").setTitle("Start maintenance");

        XHTML.Button stop = new XHTML.Button("Stop").setType(XHTML.Button.Type.SUBMIT)
        .setName("action").setValue("stop").setTitle("Stop maintenance");

        XHTML.Button controlButton = (this.introductionDaemon == null) ? start : stop;

        XHTML.Button set = new XHTML.Button("Set Configuration").setType(XHTML.Button.Type.SUBMIT)
        .setName("action").setValue("set-config").setTitle("Set Configuration Parameters");

        XHTML.Button go = new XHTML.Button("Run").setType(XHTML.Button.Type.SUBMIT)
        .setName("action").setValue("run").setTitle("Run Now");

        XHTML.Form configuration = new XHTML.Form("").setMethod("get").setEncodingType("application/x-www-url-encoded");

        configuration.add(new XHTML.Table(
                new XHTML.Table.Body(
                        new XHTML.Table.Row(new XHTML.Table.Data(""), new XHTML.Table.Data(controlButton)),
                        new XHTML.Table.Row(new XHTML.Table.Data("Logging Level"), new XHTML.Table.Data(Xxhtml.selectJavaUtilLoggingLevel("LoggerLevel", this.log.getEffectiveLevel()))),
                        new XHTML.Table.Row(new XHTML.Table.Data(RoutingDaemon.IntroductionRateSeconds.getName()),
                                new XHTML.Table.Data(Xxhtml.inputUnboundedInteger(RoutingDaemon.IntroductionRateSeconds.getName(), this.node.getConfiguration().asLong(RoutingDaemon.IntroductionRateSeconds)))),
                        new XHTML.Table.Row(new XHTML.Table.Data(""), new XHTML.Table.Data(set)),
                        new XHTML.Table.Row(new XHTML.Table.Data(""), new XHTML.Table.Data(go))
                )).setClass("controls"));

        String logdata = null;
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(this.log.getLogfile(0));
            logdata = Xxhtml.inputStreamToString(fin);
        } catch (IOException e) {
            logdata = e.toString();
        } finally {
            if (fin != null) try { fin.close(); } catch (IOException ignore) { }
        }

        XHTML.Table logfile = new XHTML.Table(new XHTML.Table.Caption("Logfile"),
                new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(new XHTML.Preformatted(logdata).setClass("logfile")))));

        XHTML.Div body = new XHTML.Div(
                new XHTML.Heading.H1(RoutingDaemon.name + " " + this.node.getNodeId()),
                new XHTML.Div(this.node.getNeighbourMap().toXHTML(uri, props)).setClass("section"),
                new XHTML.Div(configuration).setClass("section"),
                new XHTML.Div(logfile).setClass("section"));

        return body;
    }
}
