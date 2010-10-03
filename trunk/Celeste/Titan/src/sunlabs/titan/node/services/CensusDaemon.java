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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
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

import javax.management.JMException;

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
import sunlabs.titan.Release;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanNodeIdImpl;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.util.OrderedProperties;

/**
 * The {@link TitanNode} census service.
 * <p>
 * Periodically transmit this node's census information to the
 * root node of object-id {@link Census#CensusKeeper}.
 * </p>
 * <p>
 * If this node is the root if the object-id {@link Census#CensusKeeper}, collect the
 * received node information.
 * </p>
 */
public final class CensusDaemon extends AbstractTitanService implements Census, CensusDaemonMBean {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(CensusDaemon.class, CensusDaemon.serialVersionUID);

    /** The number of seconds between each Census report transmitted by this {@link TitanNode}. */
    public final static Attributes.Prototype ReportRateSeconds = new Attributes.Prototype(CensusDaemon.class,
            "ReportRateSeconds",
            60,
    "The number of seconds between each Census report transmitted by this TitanNode.");

    private static String release = Release.ThisRevision();

    transient private ReportDaemon daemon;

    /**
     * A single Census report.
     * <p>
     * Every {@link TitanNode} periodically transmits an instance of {@link Report.Request}
     * reporting on various properties and receiving {@link Report.Response} in reply.
     * </p>
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
        }

        private static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private NodeAddress address;
            private Map<TitanNodeId,OrderedProperties> census;

            public Response(NodeAddress address, Map<TitanNodeId,OrderedProperties> census) {
                this.address = address;
                this.census = new HashMap<TitanNodeId,OrderedProperties>(census);
            }

            public NodeAddress getAddress() {
                return this.address;
            }

            /**
             * Get the Census information returned from the Census keeper.
             * This can be all or part of the Census information maintained by the Census keeper
             * and is intended for TitanNode's to cache in the interest of faster lookup of TitanNodes in the system.
             * @return the Census information returned from the Census keeper.
             */
            public Map<TitanNodeId,OrderedProperties> getCensus() {
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
            private Set<TitanNodeId> exclude;
            private OrderedProperties match;

            /**
             *
             * @see Census#select(int, Set, OrderedProperties)
             */
            public Request(int count, Set<TitanNodeId> exclude, OrderedProperties match) {
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
             * Get the {@link Set} of {@link TitanGuid}s to exclude from the result of this select query.
             */
            public Set<TitanNodeId> getExcluded() {
                return this.exclude;
            }

            /**
             * Get the {@link OrderedProperties} instance that contains the matching properties for nodes in this select query.
             */
            public OrderedProperties getMatch() {
                return this.match;
            }

            public String toString() {
                StringBuilder result = new StringBuilder(this.getClass().getName()).append(" ")
                .append(count).append(" ").append(exclude).append(" ").append(match);
                return result.toString();
            }
        }

        private static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private Map<TitanNodeId,OrderedProperties> censusData;

            public Response(Map<TitanNodeId,OrderedProperties> censusData) {
                this.censusData = censusData;
            }

            public Map<TitanNodeId,OrderedProperties> getCensusData() {
                return this.censusData;
            }

            public String toString() {
                StringBuilder result = new StringBuilder(this.getClass().getName()).append(" ").append(this.censusData);
                return result.toString();
            }
        }
    }

    // This ought to just be the Dossier file.  But the Dossier may have information that is not up-to-date.
    private SortedMap<TitanNodeId,OrderedProperties> catalogue;

    /**
     * For each census report in the given {@link Map}, if it does not already exist add it to the current census.
     * Otherwise, if there is already a report in the current census so not replace it.
     */
    public void putAllLocal(Map<TitanNodeId,OrderedProperties> census) {
        synchronized (this.catalogue) {
            for (TitanNodeId nodeId : census.keySet()) {
                if (!this.catalogue.containsKey(nodeId)) {
                    this.catalogue.put(nodeId, census.get(nodeId));
                }
            }
        }
    }

    /**
     * Randomly select {@code count} nodes, excluding those present in the  {@link Set} {@code exclude},
     * that match properties specified in the {@link OrderedProperties} instance.
     *
     * @param count the number of nodes to select. A count of zero means to return the entire set of nodes.
     * @param exclude the {@code Set} of nodes to exclude from the result, or {@code null}.
     * @param match the {@code OrderedProperties} to match (unimplemented).
     */
    private Map<TitanNodeId,OrderedProperties> selectFromCatalogue(int count, Set<TitanNodeId> exclude, OrderedProperties match) {
        Map<TitanNodeId,OrderedProperties> result = new HashMap<TitanNodeId,OrderedProperties>();
        if (count == 0) {
            synchronized (this.catalogue) {
                result.putAll(this.catalogue);
            }
        } else {
            synchronized (this.catalogue) {
                List<TitanNodeId> nodes = new LinkedList<TitanNodeId>(this.catalogue.keySet());
                Collections.shuffle(nodes, new Random(System.currentTimeMillis()));

                if (exclude != null) {
                    for (TitanNodeId id : nodes) {
                        if (!exclude.contains(id)) {
                            result.put(id, this.catalogue.get(id));
                            if (result.size() == count) {
                                break;
                            }
                        }
                    }
                } else {
                    for (TitanNodeId id : nodes) {
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

    public CensusDaemon(TitanNode node) throws JMException {
        super(node, CensusDaemon.name, "Catalogue all Nodes");

        node.getConfiguration().add(CensusDaemon.ReportRateSeconds);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s",node.getConfiguration().get(CensusDaemon.ReportRateSeconds));
        }

        this.catalogue = Collections.synchronizedSortedMap(new TreeMap<TitanNodeId,OrderedProperties>());
    }

    /**
     * Receive a {@link CensusDaemon.Report.Request} from a {@link TitanNode}.
     * The report must contain a positive value for the {@link Census#TimeToLiveMillis} property.
     * <p>
     * If a report does not exist, create one.  If a report already exists, this will
     * update it.  An individual report is the only way an existing report can be updated.
     * </p>
     * <p>
     * Bulk reporting does not update an existing report, as it only creates missing reports.
     * </p>
     * @param message
     */
    public Report.Response report(TitanMessage message) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException {
        if (message.isTraced()) {
            this.log.info(message.traceReport());
        }

        Report.Request request = message.getPayload(Report.Request.class, this.node);

        OrderedProperties properties = request.getProperties();
        properties.setProperty(Census.Timestamp, System.currentTimeMillis());
        if (properties.contains(Census.TimeToLiveMillis)) {
            throw new IllegalArgumentException(String.format("Report.Request failed to include the property %s%n", Census.TimeToLiveMillis));
        }

        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("from %s", message.getSource().getObjectId());
        }

        synchronized (this.catalogue) {
            this.catalogue.put(message.getSource().getObjectId(), properties);
            Report.Response response = new Report.Response(CensusDaemon.this.node.getNodeAddress(), this.catalogue);
            return response;
        }

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
    }

    /**
     * Respond to a {@link CensusDaemon.Select.Request} operation.
     *
     */
    public Select.Response select(TitanMessage message) throws ClassNotFoundException, ClassCastException, TitanMessage.RemoteException {

        Select.Request request = message.getPayload(Select.Request.class, this.node);
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", request);
        }
        Map<TitanNodeId,OrderedProperties> list = this.selectFromCatalogue(request.getCount(), request.getExcluded(), request.getMatch());

        Select.Response response = new Select.Response(list);
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", response);
        }
        return response;
    }

    public interface ReportDaemonMBean extends ThreadMBean {
        public long getReportRateSeconds();
        public void setRefreshRate(long refreshRate);
        public String getInfo();
    }

    /**
     * This Thread periodically transmits a {@link CensusDaemon.Report.Request} containing this node's Census data to the Census keeper.
     *
     */
    private class ReportDaemon extends Thread implements ReportDaemonMBean, Serializable {
        private final static long serialVersionUID = 1L;

        private OrderedProperties myProperties;

        private long lastReportTime;

        protected ReportDaemon() throws JMException {
            super(CensusDaemon.this.node.getThreadGroup(), CensusDaemon.this.node.getNodeId() + ":" + CensusDaemon.this.getName() + ".daemon");
            this.setPriority(Thread.MIN_PRIORITY);

            this.myProperties = new OrderedProperties();

            AbstractTitanService.registrar.registerMBean(JMX.objectName(CensusDaemon.this.jmxObjectNameRoot, "daemon"), this, ReportDaemonMBean.class);
        }

        @Override
        public void run() {
            while (!interrupted()) {
                this.lastReportTime = System.currentTimeMillis();

                // Fill in this node's Census properties and value here...
                myProperties.setProperty(Census.TimeToLiveMillis, Time.secondsInMilliseconds(this.getReportRateSeconds() * 2));

                Report.Request request = new Report.Request(CensusDaemon.this.node.getNodeAddress(), myProperties);
                TitanMessage result = CensusDaemon.this.node.sendToNode(Census.CensusKeeper, CensusDaemon.this.getName(), "report", request);

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
                                CensusDaemon.this.log.severe("%s when parsing Response.", e.getCause().toString());
                            }
                        }
                    } else {
                        if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                            CensusDaemon.this.log.severe("Census report bad status: %s", result.getStatus());
                        }
                    }
                }

                // Expire/Clean up the local Census data.
                SortedMap<TitanNodeId,OrderedProperties> newCatalogue = new TreeMap<TitanNodeId,OrderedProperties>();;
                synchronized (CensusDaemon.this.catalogue) {
                    for (TitanNodeId nodeId : CensusDaemon.this.catalogue.keySet()) {
                        OrderedProperties report = CensusDaemon.this.catalogue.get(nodeId);
                        long lastUpdateTime = report.getPropertyAsLong(Census.Timestamp, 0);
                        long timeToLive = report.getPropertyAsLong(Census.TimeToLiveMillis, 0);
                        if ((lastUpdateTime + timeToLive) > System.currentTimeMillis()) {
                            newCatalogue.put(nodeId, report);
                        }
                    }
                    CensusDaemon.this.catalogue = newCatalogue;
                }

                long now = System.currentTimeMillis();

                CensusDaemon.this.setStatus(String.format("Report @ %s", Time.ISO8601(now + this.getReportRateSeconds())));

                synchronized (this) {
                    long wakeupTimeMillis = System.currentTimeMillis() + Time.secondsInMilliseconds(this.getReportRateSeconds());
                    while (true) {
                        long delta = wakeupTimeMillis - System.currentTimeMillis();
                        try {
                            this.wait(delta);
                            break;
                        } catch (InterruptedException e) {
                            //
                        }
                    }
                }
            }

            setStatus("stopped");
            return;
        }

        public String getInfo() {
            return "Last run time: " + new Date(this.lastReportTime);
        }

        public long getReportRateSeconds() {
            return CensusDaemon.this.node.getConfiguration().asLong(CensusDaemon.ReportRateSeconds);
        }

        public void setRefreshRate(long refreshPeriodMillis) {
            CensusDaemon.this.node.getConfiguration().set(CensusDaemon.ReportRateSeconds, refreshPeriodMillis);
        }
    }

    public void runDaemon() {
        synchronized (this.daemon) {
            this.daemon.notifyAll();
        }
    }

    @Override
    public synchronized void start() {
        if (this.isStarted()) {
            return;
        }
        super.start();

        if (this.daemon == null) {
            try {
                this.daemon = new ReportDaemon();
            } catch (JMException e) {
                this.stop();
                if (CensusDaemon.this.log.isLoggable(Level.SEVERE)) {
                    CensusDaemon.this.log.severe("Cannot start: %s", e);
                }
            }
            this.daemon.start();
        }
    }

    public Map<TitanNodeId,OrderedProperties> select(int count) throws ClassCastException {
        return this.select(count, new HashSet<TitanNodeId>(), null);
    }

    public Map<TitanNodeId,OrderedProperties> select(int count, Set<TitanNodeId> exclude, OrderedProperties match) throws ClassCastException {
        TimeProfiler timeProfiler = new TimeProfiler("CensusDaemon.select");
        try {
            Select.Request request = new Select.Request(count, exclude, match);

            TitanMessage reply = CensusDaemon.this.node.sendToNode(Census.CensusKeeper, CensusDaemon.this.getName(), "select", request);

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

    public Map<TitanNodeId,OrderedProperties> select(NodeAddress gateway, int count, Set<TitanNodeId> exclude, OrderedProperties match) {
        Select.Request request = new Select.Request(count, exclude, match);

        TitanMessage message = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.node.getNodeAddress(),
                Census.CensusKeeper,
                TitanGuidImpl.ANY,
                CensusDaemon.name,
                "select",
                TitanMessage.Transmission.UNICAST,
                TitanMessage.Route.LOOSELY,
                request
        );

        TitanMessage reply = this.node.getMessageService().transmit(gateway, message);
        if (reply  == null) {
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
            String defaultNodeAddress = new NodeAddress(new TitanNodeIdImpl("1111111111111111111111111111111111111111111111111111111111111111"), "127.0.0.1", 12001, new URL("http", "127.0.0.1", 12002, "")).format();


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
                    Map<TitanNodeId,OrderedProperties> list =  this.select(n, new HashSet<TitanNodeId>(), null);
                    XHTML.Table.Body tbody = new XHTML.Table.Body();
                    for (TitanGuid nodeId : list.keySet()) {
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
                    new XHTML.Table.Row(new XHTML.Table.Data("Refresh Rate (ms)"), new XHTML.Table.Data(this.node.getConfiguration().asLong(CensusDaemon.ReportRateSeconds))),
                    new XHTML.Table.Row(new XHTML.Table.Data("Set Configuration"), new XHTML.Table.Data(setButton)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Add"), new XHTML.Table.Data(addButton), new XHTML.Table.Data(addressField))
            )));

            XHTML.Table.Body dataTableBody = new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Heading("Node"),
                            new XHTML.Table.Heading("Properties")));

            synchronized (this.catalogue) {
                for (TitanGuid nodeId : this.catalogue.keySet()) {
                    OrderedProperties data = this.catalogue.get(nodeId);
                    String controlURL = null;
                    try {
                        controlURL = new NodeAddress(data.getProperty(Census.NodeAddress)).getInspectorInterface().toExternalForm();
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
            XHTML.Div body = new XHTML.Div(new sunlabs.asdf.web.XML.XHTML.Heading.Para(e.toString()));
            return body;
        }
    }
}
