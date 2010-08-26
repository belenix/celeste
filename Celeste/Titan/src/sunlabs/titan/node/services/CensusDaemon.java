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

import java.io.Serializable;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.TimeProfiler;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.Release;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.util.OrderedProperties;

/**
 * The Beehive node census service.
 * <p>
 * Periodically transmit this node's census information to the
 * root node of object-id {@link Census#CensusKeeper}.
 * </p>
 * <p>
 * If this node is the root if the object-id {@link Census#CensusKeeper}, collect the
 * received node information.
 * </p>
 */
public final class CensusDaemon extends BeehiveService implements Census, CensusDaemonMBean {
    private final static long serialVersionUID = 1L;
    private final static String name = BeehiveService.makeName(CensusDaemon.class, CensusDaemon.serialVersionUID);

    public final static Attributes.Prototype ReportRateMillis = new Attributes.Prototype(CensusDaemon.class,
            "ReportRateMillis",
            Time.minutesInMilliseconds(10),
            "The number of milliseconds between each Census report from this node.");

    private static String release = Release.ThisRevision();

    transient private ReportDaemon daemon;

//    private static class BulkReport {
//        private static class Request implements Serializable {
//            private final static long serialVersionUID = 1L;
//
//            private Map<BeehiveObjectId,OrderedProperties> census;
//
//            public Request(Map<BeehiveObjectId,OrderedProperties> census) {
//                this.census = census;
//            }
//
//            public Map<BeehiveObjectId,OrderedProperties> getCensus() {
//                return this.census;
//            }
//        }
//
//        private static class Response implements Serializable {
//            private final static long serialVersionUID = 1L;
//
//            public Response() {
//
//            }
//        }
//    }

    /**
     * A single Census report.
     */
    private static class Report {
    	private static class Request implements Serializable {
    		private final static long serialVersionUID = 1L;

    		private NodeAddress address;
    		private OrderedProperties properties;

    		public Request() {
    			this.properties = new OrderedProperties();
    		}

    		public Request(NodeAddress address, OrderedProperties properties) {
    			this();
    			this.address = address;
    			this.properties = properties;
    			this.properties.setProperty(Census.NodeAddress, address.format());
    			this.properties.setProperty(Census.Version, CensusDaemon.serialVersionUID);
    			this.properties.setProperty(Census.NodeRevision, CensusDaemon.release);
    		}

    		public OrderedProperties getProperties() {
    			return this.properties;
    		}

    		public NodeAddress getAddress() {
    			return this.address;
    		}
    	}

    	private static class Response implements Serializable {
    		private final static long serialVersionUID = 1L;

    		private NodeAddress address;
    		private Map<BeehiveObjectId,OrderedProperties> census;

    		public Response(NodeAddress address, Map<BeehiveObjectId,OrderedProperties> census) {
    			this.address = address;
                this.census = census;
            }

            public NodeAddress getAddress() {
                return this.address;
            }

            public Map<BeehiveObjectId,OrderedProperties> getCensus() {
                return this.census;
        }
    }
    }

    /**
     * A Select query on the Census data
     */
    private static class Select {
        /**
         *
         * @see Census#select(int, Set, OrderedProperties)
         */
        private static class Request implements Serializable {
            private final static long serialVersionUID = 1L;

            private int count;
            private Set<BeehiveObjectId> exclude;
            private OrderedProperties match;

            /**
             *
             * @see Census#select(int, Set, OrderedProperties)
             */
            public Request(int count, Set<BeehiveObjectId> exclude, OrderedProperties match) {
                this.count = count;
                this.exclude = exclude;
                this.match = match;
            }

            /**
             * Get the number of entries to select from the Census data.
             */
            public int getCount() {
                return this.count;
            }

            /**
             * Get the {@link Set} of {@link BeehiveObjectId}s to exclude from the result of this select query.
             */
            public Set<BeehiveObjectId> getExcluded() {
                return this.exclude;
            }

            /**
             * Get the {@link OrderedProperties} instance that contains the matching properties for nodes in this select query.
             */
            public OrderedProperties getMatch() {
                return this.match;
            }
        }

        private static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private Map<BeehiveObjectId,OrderedProperties> censusData;

            public Response(Map<BeehiveObjectId,OrderedProperties> censusData) {
                this.censusData = censusData;
            }

            public Map<BeehiveObjectId,OrderedProperties> getCensusData() {
                return this.censusData;
            }
            }
        }

    // This ought to just be the Dossier file.  But the Dossier may have information that is not up-to-date.
    private SortedMap<BeehiveObjectId,OrderedProperties> catalogue;

    /**
     * For each census report in the given {@link Map}, if it does not already exist add it to the current census.
     * Otherwise, if there is already a report in the current census so not replace it.
     */
    public void putAllLocal(Map<BeehiveObjectId,OrderedProperties> census) {
        synchronized (this.catalogue) {
            for (BeehiveObjectId nodeId : census.keySet()) {
                if (!this.catalogue.containsKey(nodeId)) {
                    this.catalogue.put(nodeId, census.get(nodeId));
                }
            }
        }
    }

    /**
     * Randomly select {@code count} nodes, excluding those specified by {@link BeehiveObjectId}
     * in the {@link Set} {@code exclude}, that match properties specified in the {@link OrderedProperties} instance.
     *
     * @param count the number of nodes to select. A count of zero means to return the entire set of nodes.
     * @param exclude the {@code Set} of nodes to exclude from the result, or {@code null}.
     * @param match the {@code OrderedProperties} to match (unimplemented).
     */
    private Map<BeehiveObjectId,OrderedProperties> selectFromCatalogue(int count, Set<BeehiveObjectId> exclude, OrderedProperties match) {
        Map<BeehiveObjectId,OrderedProperties> result = new HashMap<BeehiveObjectId,OrderedProperties>();
        if (count == 0) {
            synchronized (this.catalogue) {
                result.putAll(this.catalogue);
            }
        } else {
            synchronized (this.catalogue) {
                List<BeehiveObjectId> nodes = new LinkedList<BeehiveObjectId>(this.catalogue.keySet());
                Collections.shuffle(nodes, new Random(System.currentTimeMillis()));

                if (exclude != null) {
                    for (BeehiveObjectId id : nodes) {
                        if (!exclude.contains(id)) {
                            result.put(id, this.catalogue.get(id));
                            if (result.size() == count) {
                                break;
                            }
                        }
                    }
                } else {
                    for (BeehiveObjectId id : nodes) {
                        result.put(id, this.catalogue.get(id));
                        if (result.size() == count) {
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    public CensusDaemon(BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, CensusDaemon.name, "Catalogue all Nodes");

        node.configuration.add(CensusDaemon.ReportRateMillis);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s",node.configuration.get(CensusDaemon.ReportRateMillis));
        }

        this.catalogue = Collections.synchronizedSortedMap(new TreeMap<BeehiveObjectId,OrderedProperties>());
    }

    /**
     * Receive a {@link Report.Request} from a {@link BeehiveNode}.
     * The report must contain a positive value for the {@link Census#TimeToLive} property.
     * <p>
     * If a report does not exist, create one.  If a report already exists, this will
     * update it.  An individual report is the only way an existing report can be updated.
     * </p>
     * <p>
     * Bulk reporting does not update an existing report, as it only creates missing reports.
     * </p>
     * @param message
     */
    
    public Report.Response report(BeehiveMessage message) throws ClassCastException, ClassNotFoundException, BeehiveMessage.RemoteException {
        if (message.isTraced()) {
            this.log.info(message.traceReport());
        }

        Report.Request request = message.getPayload(Report.Request.class, this.node);

        OrderedProperties properties = request.getProperties();
        properties.setProperty(Census.Timestamp, System.currentTimeMillis());
        if (properties.contains(Census.TimeToLive)) {
            throw new IllegalArgumentException(String.format("Report.Request failed to include the property %s%n", Census.TimeToLive));
        }

        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("from %s", message.getSource().getObjectId());
        }

        this.catalogue.put(message.getSource().getObjectId(), properties);

        //            // Update the Dossier...
        //            Dossier.Entry dossier = CensusDaemon.this.node.getDossier().getEntryAndLock(message.getSource());
        //            try {
        //              CensusDaemon.this.node.getDossier().put(dossier);
        //            } finally {
        //              if (!dossier.getNodeAddress().equals(message.getSource())) {
        //                  System.err.printf("Unlocking %s vs %s%n", message.getSource(), dossier.getNodeAddress());
        //              }
        //              CensusDaemon.this.node.getDossier().unlockEntry(dossier);
        //            }

        // XXX Catalogue is not synchronized here, but we also don't want to lock it for a long time...
        Report.Response response = new Report.Response(CensusDaemon.this.node.getNodeAddress(), this.catalogue);
        return response;
    }

    /**
     * Respond to a {@link CensusDaemon.Select.Request} operation.
     *
     */
    public Select.Response select(BeehiveMessage message) throws ClassNotFoundException, ClassCastException, BeehiveMessage.RemoteException {
        Select.Request request = message.getPayload(Select.Request.class, this.node);
        Map<BeehiveObjectId,OrderedProperties> list = this.selectFromCatalogue(request.getCount(), request.getExcluded(), request.getMatch());

        Select.Response response = new Select.Response(list);
        return response;
    }

    public interface ReportDaemonMBean extends ThreadMBean {
        public long getRefreshRate();
        public void setRefreshRate(long refreshRate);
        public String getInfo();
    }

    /**
     * This daemon periodically transmits a {@link CensusDaemon.Report.Request} containing this node's Census data to the Census keeper.
     *
     */
    private class ReportDaemon extends Thread implements ReportDaemonMBean, Serializable {
        private final static long serialVersionUID = 1L;

        private OrderedProperties myProperties;

        private long lastReportTime;
        private long lastReportDuration;

        protected ReportDaemon()
        throws MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
            super(CensusDaemon.this.node.getThreadGroup(), CensusDaemon.this.node.getObjectId() + ":" + CensusDaemon.this.getName() + ".daemon");
            this.setPriority(Thread.MIN_PRIORITY);

            this.myProperties = new OrderedProperties();

            BeehiveService.registrar.registerMBean(JMX.objectName(CensusDaemon.this.jmxObjectNameRoot, "daemon"), this, ReportDaemonMBean.class);
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    this.lastReportTime = System.currentTimeMillis();

                    // Fill in this node's Census properties and value here...
                    myProperties.setProperty(Census.TimeToLive, this.getRefreshRate() * 2);

                    // XXX If we are sending this report which gets routed back to this node, there appears to be some deadlock.
                    Report.Request request = new Report.Request(CensusDaemon.this.node.getNodeAddress(), myProperties);
                    BeehiveMessage result = CensusDaemon.this.node.sendToNode(Census.CensusKeeper, CensusDaemon.this.getName(), "report", request);

                    if (result != null) {
                        if (result.getStatus().isSuccessful()) {
                            try {
                                Report.Response response = result.getPayload(Report.Response.class, CensusDaemon.this.node);
                                CensusDaemon.this.putAllLocal(response.getCensus());
                            } catch (ClassNotFoundException e) {
                                if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                                    CensusDaemon.this.log.severe("%s when parsing Response.", e.toString());
                                }
                            } catch (ClassCastException e) {
                                if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                                    CensusDaemon.this.log.severe("%s when parsing Response.", e.toString());
                                }
                            } catch (RemoteException e) {
                                if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                                    CensusDaemon.this.log.severe("%s when parsing Response.", e.toString());
                                }
                            }
                        } else {
                            if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                                CensusDaemon.this.log.severe("Census report bad status: %s", result.getStatus());
                            }
                        }
                    }

                    // Expire/Clean up the local Census data.
                    SortedMap<BeehiveObjectId,OrderedProperties> newCatalogue = new TreeMap<BeehiveObjectId,OrderedProperties>();;
                    synchronized (CensusDaemon.this.catalogue) {
                        for (BeehiveObjectId nodeId : CensusDaemon.this.catalogue.keySet()) {
                            OrderedProperties report = CensusDaemon.this.catalogue.get(nodeId);
                            long lastUpdateTime = report.getPropertyAsLong(Census.Timestamp, 0);
                            long timeToLive = report.getPropertyAsLong(Census.TimeToLive, 0);
                            if ((lastUpdateTime + timeToLive) > System.currentTimeMillis()) {
                                newCatalogue.put(nodeId, report);
                            }
                        }
                        CensusDaemon.this.catalogue = newCatalogue;
                    }

                    long now = System.currentTimeMillis();

                    CensusDaemon.this.setStatus(String.format("Report @ %1$td/%1$tm/%1$ty %1$tH:%1$tM:%1$tS", new Date(now + this.getRefreshRate())));

                    this.lastReportDuration = System.currentTimeMillis() - this.lastReportTime;
                    synchronized (this) {
                        this.wait(this.getRefreshRate());
                    }
                }
            } catch (InterruptedException stopThread) {
                // Do nothing, let the thread stop.
            }

            setStatus("stopped");
            return;
        }

        public String getInfo() {
            return "Last run time: " + new Date(this.lastReportTime) + " duration: " + this.lastReportDuration + "s";
        }

        public long getRefreshRate() {
            return CensusDaemon.this.node.configuration.asLong(CensusDaemon.ReportRateMillis);
        }

        public void setRefreshRate(long refreshPeriodMillis) {
            CensusDaemon.this.node.configuration.set(CensusDaemon.ReportRateMillis, refreshPeriodMillis);
        }
    }

    public void runDaemon() {
        synchronized (this.daemon) {
            this.daemon.notifyAll();
        }
    }

    @Override
    public synchronized void start() {
        this.setStatus("initializing");
        if (this.daemon == null) {
            try {
                this.daemon = new ReportDaemon();
            } catch (MalformedObjectNameException e) {
                e.printStackTrace();
            } catch(MBeanRegistrationException e) {
                e.printStackTrace();
            } catch (NotCompliantMBeanException e) {
                e.printStackTrace();
            } catch (InstanceAlreadyExistsException e) {
                e.printStackTrace();
            }
            this.daemon.start();
        }
    }

    @Override
    public void stop() {
        this.setStatus("stopping");
//        synchronized (this.daemon) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.daemon);
//            }
//
//            this.daemon.interrupt(); // Logged
//            this.daemon.notifyAll();
//            this.daemon = null;
//        }
    }

    public Map<BeehiveObjectId,OrderedProperties> select(int count) throws ClassCastException {
        return this.select(count, new HashSet<BeehiveObjectId>(), null);
    }

    public Map<BeehiveObjectId,OrderedProperties> select(int count, Set<BeehiveObjectId> exclude, OrderedProperties match) throws ClassCastException {
        TimeProfiler timeProfiler = new TimeProfiler("CensusDaemon.select");
        try {
            Select.Request request = new Select.Request(count, exclude, match);

            BeehiveMessage reply = CensusDaemon.this.node.sendToNode(Census.CensusKeeper, CensusDaemon.this.getName(), "select", request);

            try {
                Select.Response response = reply.getPayload(Select.Response.class, this.node);
                return response.getCensusData();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return null;
        } finally {
            timeProfiler.stamp("fini");
            timeProfiler.printCSV(System.out);
        }
    }

    public Map<BeehiveObjectId,OrderedProperties> select(NodeAddress gateway, int count, Set<BeehiveObjectId> exclude, OrderedProperties match) {
        Select.Request request = new Select.Request(count, exclude, match);

        BeehiveMessage message = new BeehiveMessage(BeehiveMessage.Type.RouteToNode,
                this.node.getNodeAddress(),
                Census.CensusKeeper,
                BeehiveObjectId.ANY,
                CensusDaemon.name,
                "select",
                BeehiveMessage.Transmission.UNICAST,
                BeehiveMessage.Route.LOOSELY,
                request
        );

        BeehiveMessage reply;
        if ((reply = this.node.transmit(gateway, message)) == null) {
            return null;
        }

        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("responder %s", reply.getSource().format());
        }

        try {
            Select.Response response = reply.getPayload(Select.Response.class, this.node);
            return response.getCensusData();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (ClassCastException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        try {
            String defaultNodeAddress = new NodeAddress(new BeehiveObjectId("1111111111111111111111111111111111111111111111111111111111111111"), "127.0.0.1", 12001, 12002).format();


            String action = HttpMessage.asString(props.get("action"), null);
            if (action != null) {
                if (action.equals("stop")) {
                    this.stop();
                } else if (action.equals("start")) {
                    this.start();
                } else if (action.equals("reset")) {
                    synchronized (this.catalogue) {
                        this.catalogue.clear();
                    }
                } else if (action.equals("run")) {
                    // This will fall through and format the page, but will likely not be updated yet with the response.
                    synchronized (this.daemon) {
                        this.daemon.notifyAll();
                    }
                } else if (action.equals("select")) {
                    // This is just for manual testing.
                    int n = HttpMessage.asInteger(props.get("N"), 6);
                    Map<BeehiveObjectId,OrderedProperties> list =  this.select(n, new HashSet<BeehiveObjectId>(), null);
                    XHTML.Table.Body tbody = new XHTML.Table.Body();
                    for (BeehiveObjectId nodeId : list.keySet()) {
                        tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(nodeId)));
                    }
                    XHTML.Table table = new XHTML.Table(tbody);
                    XHTML.Div div = new XHTML.Div(new XHTML.Heading.H2(CensusDaemon.name), new XHTML.Div(table).setClass("section"));

                    return div;
                } else if (action.equals("set-config")) {
                    this.log.setLevel(Level.parse(HttpMessage.asString(props.get("LoggerLevel"), this.log.getEffectiveLevel().toString())));

                    // you can not set this -- it is set in the response from the cataloguer node to limit global network load
                    // this.refreshRate = this.properties.getInteger("refreshRate", CatalogueNodes.defaultRefreshRate);
                    this.log.config("Run-time parameters: loggerLevel=" + this.log.getEffectiveLevel().toString()
                    /* + " refreshRate=" + this.properties.getString("refreshRate", "?")*/);

                } else {
                    return new XHTML.Para("Unknown action: " + action);
                }
            }

            XML.Attribute nameAction = new XML.Attr("name", "action");

            XHTML.Input start = new XHTML.Input(XHTML.Input.Type.SUBMIT)
            .setName("action").setValue("start").setTitle("Start the census");
            XHTML.Input stop = new XHTML.Input(XHTML.Input.Type.SUBMIT)
            .setName("action").setValue("stop").setTitle("Stop the census");
            XHTML.Input controlButton = (this.daemon == null) ? start : stop;

            XHTML.Input setButton = new XHTML.Input(XHTML.Input.Type.SUBMIT)
            .setName("action").setValue("set-config").setTitle("Set Parameters");

            XHTML.Input resetButton = new XHTML.Input(XHTML.Input.Type.SUBMIT)
            .setName("action").setValue("reset").setTitle("Reset the collected census");

            XHTML.Input go = new XHTML.Input(XHTML.Input.Type.SUBMIT)
            .setName("action").setValue("run").setTitle("Send census report now.");

            XHTML.Input addressField = Xxhtml.InputText(new XML.Attr("name", "NodeAddress"),
                    new XML.Attr("value", defaultNodeAddress),
                    new XML.Attr("title", "NodeAddress"),
                    new XML.Attr("size", Integer.toString(defaultNodeAddress.length())));

            XHTML.Input addButton = Xxhtml.InputSubmit(
                    nameAction,
                    new XML.Attr("value", "add"),
                    new XML.Attr("title", "add specified node"));

            XHTML.Form controls = new XHTML.Form("").setMethod("get").setEncodingType("application/x-www-url-encoded")
            .add(new XHTML.Table(new XML.Attr("class", "controls"))
            .add(new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data(""), new XHTML.Table.Data(controlButton, XHTML.CharacterEntity.nbsp, go, XHTML.CharacterEntity.nbsp, resetButton)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Logging Level"), new XHTML.Table.Data(Xxhtml.selectJavaUtilLoggingLevel("LoggerLevel", this.log.getEffectiveLevel()), XHTML.CharacterEntity.nbsp, this.log.getName())),
                    new XHTML.Table.Row(new XHTML.Table.Data("Refresh Rate (ms)"), new XHTML.Table.Data(this.node.configuration.asLong(CensusDaemon.ReportRateMillis))),
                    new XHTML.Table.Row(new XHTML.Table.Data("Set Configuration"), new XHTML.Table.Data(setButton)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Add"), new XHTML.Table.Data(addButton), new XHTML.Table.Data(addressField))
            )));

            XHTML.Table.Body dataTableBody = new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Heading("Node"),
                            new XHTML.Table.Heading("Properties")));

            for (BeehiveObjectId nodeId : this.catalogue.keySet()) {
                OrderedProperties data = this.catalogue.get(nodeId);
                String controlURL = null;
                try {
                    controlURL = new NodeAddress(data.getProperty(Census.NodeAddress)).getHTTPInterface().toExternalForm();
                } catch (UnknownHostException e) {
                    controlURL = data.getProperty(Census.NodeAddress);
                }

                XHTML.Anchor link = new XHTML.Anchor(nodeId).setHref(XHTML.SafeURL(controlURL)).setClass("objectId");

                XHTML.Table.Body propertyBody = new XHTML.Table.Body();
                for (Object key : data.keySet()) {
                    propertyBody.add(new XHTML.Table.Row(new XHTML.Table.Data(key), new XHTML.Table.Data(data.getProperty(key.toString()))));

                }
                dataTableBody.add(new XHTML.Table.Row(new XHTML.Table.Data(link), new XHTML.Table.Data(new XHTML.Table(propertyBody))));
            }

            XHTML.Table dataTable = new XHTML.Table(new XHTML.Table.Caption("Data (%d entries)", this.catalogue.size()), dataTableBody).setClass("census");

            XHTML.Table table = new XHTML.Table(new XHTML.Table.Caption("Application Control"),
                    new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(controls))));

            XHTML.Div body = new XHTML.Div(
                    new XHTML.Heading.H2(CensusDaemon.name),
                    new XHTML.Div(table).setClass("section"),
                    new XHTML.Div(dataTable).setClass("section")
            );

            return body;
        } catch (NumberFormatException e1) {
            e1.printStackTrace();
            XHTML.Div body = new XHTML.Div(new sunlabs.asdf.web.XML.XHTML.Heading.Para(e1.toString()));
            return body;
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
            XHTML.Div body = new XHTML.Div(new sunlabs.asdf.web.XML.XHTML.Heading.Para(e1.toString()));
            return body;
        }
    }
}
