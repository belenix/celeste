/**
 * 
 */
package sunlabs.titan.node.services.census;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.titan.Release;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanNodeIdImpl;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.Census;
import sunlabs.titan.node.services.HTTPMessageService;
import sunlabs.titan.util.OrderedProperties;

/**
 * An extensible system-wide census service.
 * <p>
 * The system census is produced by having each node periodically transmit to the root-node of {@link TitanNodeId}
 * of all zeros a {@link Census.Report.Request} containing a {@code Map} holding key-value pairs containing arbitrary data. 
 * </p>
 * <p>
 * The service also provides a method to select matching {@code Census.Report} instances matching the union of a set of {@link SelectComparator}
 * instances specifying keys, values and operations such as equal, less, greater, etc.
 * The programmatic API is available through the {@link Census#select(int)}, {@link Census#select(int, Set, List)} and
 * {@link Census#select(NodeAddress, int, Set, List)} methods
 * and a REST API is available through the {@link CensusService#select(TitanMessage, sunlabs.asdf.web.http.HTTP.Request)}
 * API invoked via the Titan Message services.
 * </p>
 * <p>
 * Using the {@link HTTPMessageService} the simplest query returns the entire census as an XML document:
 * <code>http://host:port/titan-node-urn/sunlabs.titan.node.services.census.CensusService.select</code>
 * Where <code>host</code> and <code>port</code> are the host name or IP-address and the port of the HTTP service (see {@link HTTPMessageService}),
 * <code>titan-node-urn</code> is the URN of the node to perform the selection
 * (typically <code>urn:titan-nid-256:~0000000000000000000000000000000000000000000000000000000000000000</code> or equivalent).
 * </p>
 * <p>
 * Selection is governed further by specifying two URL query parameters: <code>count</code> and <code>select</code>.
 * </p>
 * <p>
 * The <code>count</code> query parameter limits the number of census reports to the specified integer value.
 * For example, <code>count=2</code> will return no more than 2 reports.
 * </p>
 * <p>
 * The <code>select</code> query parameter specifies requirements for matching name/value pairs in the reports by stipulating the name of
 * the attribute, a comparison operator and a value to compare.  The query parameter may consist of multiple specifications separated by the comma character.
 * For a match to occur, all specifications must evaluate to {@code true}.
 * </p>
 * <p>
 * For example, <code>select=Census.ReceiverTimestamp%3E1287765221</code> returns all census reports newer than the timestamp 1287765221.
 * Similarly, <code>select=Census.ReceiverTimestamp%3E1287765221,Census.OperatingSystemLoadAverage%3C%3D2.0</code> returns all census reports
 * newer than the timestamp 1287765221 and for which the load average is less than or equal to 2.0.
 * </p>
 * <p>
 * Note the encoding of the special characters '<' as %3E, '=' as %3D and '>' as %3E.
 * </p>
 * <p>
 * Taken together, a valid query looks like this:
 * 
 * <pre>
 * https://127.0.0.1:12001/rest
 *     /urn:titan-nid-256:~0000000000000000000000000000000000000000000000000000000000000000
 *     /sunlabs.titan.node.services.census.CensusService.select
 *     ?count=2&select=Census.ReceiverTimestamp%3E1287765221,Census.OperatingSystemLoadAverage%3C%3D2.0
 * </pre>
 *  
 * @author Glenn Scott - Sun Labs, Oracle
 */
public class CensusService extends AbstractTitanService implements Census {
    private final static long serialVersionUID = 1L;

    private final static String name = AbstractTitanService.makeName(CensusService.class, CensusService.serialVersionUID);

    /** The number of seconds between each Census report transmitted by this {@link TitanNode}. */
    public final static Attributes.Prototype ReportRateSeconds = new Attributes.Prototype(CensusService.class,
            "ReportRateSeconds", 10,
    "The number of seconds between each Census report transmitted by this node.");

    /** The number of milliseconds in deviation between timestamps sent by a sender versus the receiver of a Census report. */
    public final static Attributes.Prototype ClockSlopToleranceSeconds = new Attributes.Prototype(CensusService.class,
            "ClockSlopToleranceSeconds", Time.minutesInSeconds(1),
    "The number of milliseconds of deviation between timestamps sent by a sender versus the receiver of a CensusService report");

    /** The list of the {@link CensusReportGenerator} classes, invoked in order. */
    public final static Attributes.Prototype ReportGenerators = new Attributes.Prototype(CensusService.class,
            "ReportGenerators",
            "sunlabs.titan.node.services.census.BasicReport",
    "The list of the CensusReportGenerator classes, invoked in order.");
    
    private static String release = Release.ThisRevision();

    /**
     * A single Census report.
     * <p>
     * Every {@linkTitanNode} periodically transmits an instance of {@link Report.Request}
     * reporting on various properties and receiving {@link Report.Response} in reply.
     * </p>
     */
    private static class Report implements Census.Report {
        private static class Request implements Census.Report.Request {
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
                this.properties.setProperty(Census.Version, CensusService.serialVersionUID);
                this.properties.setProperty(Census.NodeRevision, CensusService.release);
            }

            public OrderedProperties getProperties() {
                return this.properties;
            }
            
            public String toString() {
                return new StringBuilder("Request: ").append(this.address.format()).append(" ").append(this.properties.toString()).toString();
            }
        }

        private static class Response implements Census.Report.Response {
            private final static long serialVersionUID = 1L;

            private NodeAddress address;
            private Map<TitanNodeId,OrderedProperties> census;

            public Response(NodeAddress address, Map<TitanNodeId,OrderedProperties> census) {
                this.address = address;
                this.census = new HashMap<TitanNodeId,OrderedProperties>(census);
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

            public XML.Content toXML() {
                // TODO Auto-generated method stub
                return null;
            }
        }
    }

    /**
     * A Select query on the Census data
     *
     * @see Census#select(int)
     * @see Census#select(int, Set, List)
     * @see Census#select(NodeAddress, int, Set, List)
     */
    private static class Select implements Census.Select {
        private static class Request implements Census.Select.Request {
            private final static long serialVersionUID = 1L;

            private int count;
            private Set<TitanNodeId> exclude;
            List<SelectComparator> comparatorList;

            /**
             *
             * @see Census#select(int, Set, OrderedProperties)
             */
            public Request(int count, Set<TitanNodeId> exclude, List<SelectComparator> match) {
                this.count = count;
                this.exclude = exclude;
                this.comparatorList = match;
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
            public List<SelectComparator> getMatch() {
                return this.comparatorList;
            }

            public String toString() {
                StringBuilder result = new StringBuilder(this.getClass().getName())
                .append(" ").append(count)
                .append(" ").append(exclude)
                .append(" ").append(this.comparatorList);
                return result.toString();
            }
        }

        private static class Response implements Census.Select.Response {
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
            
            public XML.Content toXML() {
                SelectXML xml = new SelectXML();

                SelectXML.XMLResponse response = xml.newReponse();
                for (TitanNodeId nodeId : this.censusData.keySet()) {
                    SelectXML.XMLReport report = xml.newReport(nodeId);
                    for (Object key : this.censusData.get(nodeId).keySet()) {
                        SelectXML.XMLProperty property = xml.newProperty(key, this.censusData.get(nodeId).get(key));
                        report.add(property);
                    }
                    response.add(report);
                }                

                response.bindNameSpace();
                return response;
            }
        }
    }
    
    public static class SelectXML extends XML.ElementFactoryImpl {
        public final static String XmlNameSpace = "http://labs.oracle.com/" + SelectXML.class.getCanonicalName();

        public XMLProperty newProperty(Object key, Object value) {
            XMLProperty result = new SelectXML.XMLProperty(this, key.toString(), value.toString());
            result.setNameSpace(this.getNameSpace());    
            return result;
        }

        public XMLReport newReport(TitanNodeId nodeId) {
            XMLReport result = new SelectXML.XMLReport(this, nodeId);
            result.setNameSpace(this.getNameSpace());    
            return result;
        }

        public XMLResponse newReponse() {
            XMLResponse result = new SelectXML.XMLResponse(this);
            result.setNameSpace(this.getNameSpace());    
            return result;
        }

        public SelectXML() {
            this(new XML.NameSpace("CensusService", SelectXML.XmlNameSpace));
        }
        
        public SelectXML(XML.NameSpace nameSpacePrefix) {
            super(nameSpacePrefix);
        }
        /*
         * <response>
         *   <report nodeId="">
         *     <property name="name" value="value" />
         *   </report>
         *   ...
         *   <report nodeId="">
         *     <property name="name" value="value" />
         *   </report>
         * </response>
         */
        public static class XMLResponse extends XML.Node implements XML.Content {
            private static final long serialVersionUID = 1L;
            public static final String name = "select-response";
            public interface SubElement extends XML.Content {}
            
            public XMLResponse(XML.ElementFactory factory) {
                super(XMLResponse.name, XML.Node.EndTagDisposition.REQUIRED, factory);
            }
            
            public XMLResponse add(XMLResponse.SubElement... content) {
                super.append(content);
                return this;
            }
            
            public XMLResponse add(String...content) {
                super.addCDATA((Object[]) content);
                return this;
            }
        }
        
        public static class XMLReport extends XML.Node implements XML.Content, XMLResponse.SubElement {
            private static final long serialVersionUID = 1L;
            public static final String name = "report";
            public interface SubElement extends XML.Content {}
            
            public XMLReport(XML.ElementFactory factory, TitanNodeId nodeId) {
                super(XMLReport.name, XML.Node.EndTagDisposition.REQUIRED, factory);
                this.addAttribute(new XML.Attr("titanNodeId", nodeId));
            }
            
            public XMLReport add(XMLReport.SubElement... content) {
                super.append(content);
                return this;
            }
            
            public XMLReport add(String...content) {
                super.addCDATA((Object[]) content);
                return this;
            }
        }
        
        public static class XMLProperty extends XML.Node implements XML.Content, XMLReport.SubElement {
            private static final long serialVersionUID = 1L;
            public static final String name = "property";
            public interface SubElement extends XML.Content {}
            
            public XMLProperty(XML.ElementFactory factory, String name, String value) {
                super(XMLProperty.name, XML.Node.EndTagDisposition.ABBREVIABLE, factory);
                this.addAttribute(new XML.Attr("name", name), new XML.Attr("value", value));
            }
        }
    }

    transient private ReportDaemon daemon;

    // This ought to just be the Dossier file.  But the Dossier may have information that is not up-to-date.
    private SortedMap<TitanNodeId,OrderedProperties> catalogue;

    private List<CensusReportGenerator> reportGenerators;
    
    protected CensusService(TitanNode node, String name, String description) throws JMException, ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        super(node, name, description);
        this.catalogue = Collections.synchronizedSortedMap(new TreeMap<TitanNodeId,OrderedProperties>());

        node.getConfiguration().add(CensusService.ClockSlopToleranceSeconds);
        node.getConfiguration().add(CensusService.ReportRateSeconds);
        node.getConfiguration().add(CensusService.ReportGenerators);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s",node.getConfiguration().get(CensusService.ReportRateSeconds));
        }

        this.catalogue = Collections.synchronizedSortedMap(new TreeMap<TitanNodeId,OrderedProperties>());
        
        this.reportGenerators = new LinkedList<CensusReportGenerator>();
        String tokens[] = node.getConfiguration().get(CensusService.ReportGenerators).asStringArray(",[ \t]*");
        
        for (String token : tokens) {
            Class<?> klasse = this.getClass().getClassLoader().loadClass(token);            
            Constructor<?> constructor = this.getConstructor(klasse, new Class<?>[][] {
                    { CensusService.class },
                    { Census.class }                    
            });
            CensusReportGenerator reportGenerator = (CensusReportGenerator) constructor.newInstance(this);
            this.reportGenerators.add(reportGenerator);            
        }
        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.getConfiguration().get(CensusService.ClockSlopToleranceSeconds));
            this.log.config("%s", node.getConfiguration().get(CensusService.ReportRateSeconds));
            this.log.config("%s", node.getConfiguration().get(CensusService.ReportGenerators));
        }
    }
    
    /**
     * This is like {@link Class#getConstructor(Class...)} except that it takes an array of
     * parameter specifications and finds the first matching set matching a Constructor.
     * @see Class#getConstructor(Class...)
     * 
     * @param klasse 
     * @param parameterTypes an array of arrays containing constructor parameter types.
     * @return the Constructor that matches the first set of parameters in the {@code parameterTypes} argument.
     * @throws SecurityException
     * @throws NoSuchMethodException if no matching Constructor could be found.
     */
    public Constructor<?> getConstructor(Class<?> klasse, Class<?>[][] parameterTypes) throws SecurityException, NoSuchMethodException {
        for (Class<?>[] params : parameterTypes) {
            try {
                Constructor<?> constructor = klasse.getConstructor(params);
                return constructor;
            } catch (NoSuchMethodException e) {
                // ignore and continue
            }
        }
        throw new NoSuchMethodException(String.format("No suitable constructor for %s", klasse));
    }

    public CensusService(TitanNode node) throws JMException, ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        this(node, CensusService.name, "Catalogue all Nodes");
    }
    
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
     * @param comparatorList the {@code OrderedProperties} to match (unimplemented).
     */
    private Map<TitanNodeId,OrderedProperties> selectFromCatalogue(int count, Set<TitanNodeId> exclude, List<SelectComparator> comparatorList) {
        Map<TitanNodeId,OrderedProperties> result = new HashMap<TitanNodeId,OrderedProperties>();
        // count == 0 means return the entire Census data.
        if (count == 0) {
            synchronized (this.catalogue) {
                result.putAll(this.catalogue);
            }
            return result;
        }

        synchronized (this.catalogue) {
            List<TitanNodeId> nodes = new LinkedList<TitanNodeId>(this.catalogue.keySet());
            Collections.shuffle(nodes, new Random(System.currentTimeMillis()));

            for (TitanNodeId id : nodes) {
                if (!exclude.contains(id) && applySelectComparators(this.catalogue.get(id), comparatorList)) {
                    result.put(id, this.catalogue.get(id));
                    if (result.size() == count) {
                        break;
                    }
                }
            }
        }

        return result;
    }
    
    public boolean applySelectComparators(OrderedProperties orderedProperties, List<SelectComparator> comparatorList) {
        for (SelectComparator comparator: comparatorList) {
            if (!comparator.match(orderedProperties.getProperty(comparator.getName())))
                return false;
        }
        return true;
    }

    /**
     * Receive a {@link CensusService.Report.Request} from a {@link TitanNode}.
     * The report must contain a positive value for the {@link Census#TimeToLiveMillis} property.
     * <p>
     * If a report does not already exist, create it.
     * If a report already exists, this will update it
     * An individual report is the only way an existing report can be updated.
     * </p>
     * <p>
     * Bulk reporting does not update an existing reports.
     * </p>
     * This must be modified to allow bulk updating of existing reports by updating only those reports with a larger timestamp than the existing report.
     * @param message
     */
    public Report.Response report(TitanMessage message, Report.Request request) {
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", request);
        }

        OrderedProperties properties = request.getProperties();

        if (!properties.containsKey(Census.TimeToLiveSeconds)) {
            throw new IllegalArgumentException(String.format("Report.Request failed to include the property %s%n", Census.TimeToLiveSeconds));
        }
        if (!properties.containsKey(Census.SenderTimestamp)) {
            throw new IllegalArgumentException(String.format("Report.Request failed to include the property %s%n", Census.SenderTimestamp));
        }

        long senderTimeStamp = properties.getPropertyAsLong(Census.SenderTimestamp, 0);
        long receiverTimeStamp = Time.millisecondsInSeconds(System.currentTimeMillis());
        long clockSlop = this.getNode().getConfiguration().get(CensusService.ClockSlopToleranceSeconds).asLong();
        long hi = receiverTimeStamp + clockSlop;
        long low = receiverTimeStamp - clockSlop;

        if (senderTimeStamp >= low && senderTimeStamp <= hi) {
            properties.setProperty(Census.ReceiverTimestamp, receiverTimeStamp);

            synchronized (this.catalogue) {
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("update %s", message.getSource().getObjectId());
                }
                this.catalogue.put(message.getSource().getObjectId(), properties);
                Report.Response response = new Report.Response(CensusService.this.node.getNodeAddress(), this.catalogue);
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("updated %s", this.catalogue.toString());
                }
                return response;
            }
        }
        throw new IllegalArgumentException(String.format("Timestamps differ by more than allowed amount %ds", clockSlop));
    }
    
    public Report.Response report() throws ClassCastException, RemoteException, ClassNotFoundException {
        OrderedProperties report = new OrderedProperties();
        
        for (CensusReportGenerator generator : this.reportGenerators) {
            report.putAll(generator.report());
        }
        
        Report.Request request = new Report.Request(CensusService.this.node.getNodeAddress(), report);
        
        // XXX Make this a multicast message again, so nodes will cache partial info.
        TitanMessage result = CensusService.this.node.sendToNode(Census.CensusKeeper, CensusService.this.getName(), "report", request);
        return result.getPayload(Report.Response.class, this.node);
    }

    public HTTP.Response select(TitanMessage message, HTTP.Request httpRequest) {
        try {
            int count = 0;
            Set<TitanNodeId> excluded = new HashSet<TitanNodeId>();
            List<SelectComparator> comparatorList = new LinkedList<SelectComparator>();

            Map<String,String> params = null;
            if (httpRequest.getMethod().equals(HTTP.Request.Method.GET)) {
                params = httpRequest.getURLEncoded();
            }
            if (params != null) {
                for (String key : params.keySet()) {
                    System.out.printf("%s = %s%n", key, params.get(key));
                    if (key.equals("count")) {
                        count = Integer.valueOf(params.get(key));
                    } else if (key.equals("select")) {
                        String value = params.get(key);
                        for (String token : value.split(",")) {
                            SelectComparator comparator = new SelectComparator(token);
                            comparatorList.add(comparator);
                        }
                    } else if (key.equals("exclude")) {
                        for (String token : params.get(key).split(",")) {
                            excluded.add(new TitanNodeIdImpl(token));                            
                        }                        
                    }
                }
            }

            Select.Response response = new Select.Response(this.selectFromCatalogue(count, excluded, comparatorList));
            XML.Content xml = response.toXML();
            XML.Document document = new XML.Document(XML.ProcessingInstruction.newStyleSheet(String.format("/xsl/%s.xsl", Select.class.getName())), xml);

            //        String xml = applyXSLT("/xsl/titan/CensusService/Select.xsl", document);

            return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.XML(XML.formatXMLDocument(document.toString())));
        } catch (UnsupportedEncodingException e) {
            return new HttpResponse(HTTP.Response.Status.BAD_REQUEST, new HttpContent.Text.Plain(e.toString()));
        } finally {

        }
    }
    
    /**
     * Respond to a {@link CensusService.Select.Request} operation.
     *
     */
    public Select.Response select(TitanMessage message, Select.Request request) {
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", request);
        }
        Map<TitanNodeId,OrderedProperties> list = this.selectFromCatalogue(request.getCount(), request.getExcluded(), new LinkedList<SelectComparator>());

        Select.Response response = new Select.Response(list);

        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", response);
        }
        return response;
    }
    
    public interface ReportDaemonMBean extends ThreadMBean {
        public long getReportRateSeconds();
        public void setReportRateSeconds(long refreshRate);
        public String getInfo();
    }

    /**
     * This Thread periodically transmits a {@link CensusService.Report.Request} containing this node's Census data to the Census keeper.
     */
    private class ReportDaemon extends Thread implements ReportDaemonMBean, Serializable {
        private final static long serialVersionUID = 1L;

        private long lastReportTime;
        // XXX This needs to be maintained as a persistent value across node restarts.  Otherwise the serial number will always start at zero and collide with itself.
        // Alternatively, use the time-stamp of the report as the serial number.
        private long serialNumber;

        protected ReportDaemon() throws JMException {
            super(CensusService.this.node.getThreadGroup(), CensusService.this.node.getNodeId() + ":" + CensusService.this.getName() + ".daemon");
            this.setPriority(Thread.MIN_PRIORITY);

            this.serialNumber = 0;

            AbstractTitanService.registrar.registerMBean(JMX.objectName(CensusService.this.jmxObjectNameRoot, "daemon"), this, ReportDaemonMBean.class);
        }

        @Override
        public void run() {
            while (!interrupted()) {
                this.lastReportTime = System.currentTimeMillis();
                try {
                    Report.Response response = CensusService.this.report();
                    CensusService.this.putAllLocal(response.getCensus());
                } catch (ClassNotFoundException e) {
                    if (CensusService.this.log.isLoggable(Level.SEVERE)) {
                        CensusService.this.log.severe(e);
                    }
                } catch (ClassCastException e) {
                    if (CensusService.this.log.isLoggable(Level.SEVERE)) {
                        CensusService.this.log.severe(e);
                    }
                } catch (RemoteException e) {
                    if (CensusService.this.log.isLoggable(Level.SEVERE)) {
                        CensusService.this.log.severe(e);
                    }
                }

                // Expire/Clean up the local Census data.
                SortedMap<TitanNodeId,OrderedProperties> newCatalogue = new TreeMap<TitanNodeId,OrderedProperties>();
                synchronized (CensusService.this.catalogue) {
                    for (TitanNodeId nodeId : CensusService.this.catalogue.keySet()) {
                        OrderedProperties report = CensusService.this.catalogue.get(nodeId);
                        long receiverTimestamp = report.getPropertyAsLong(Census.ReceiverTimestamp, 0);
                        long timeToLiveSeconds = report.getPropertyAsLong(Census.TimeToLiveSeconds, 0);
                        if ((receiverTimestamp + timeToLiveSeconds) > Time.millisecondsInSeconds(System.currentTimeMillis())) {
                            newCatalogue.put(nodeId, report);
                        }
                    }
                    CensusService.this.catalogue = newCatalogue;
                }

                long now = System.currentTimeMillis();

                CensusService.this.setStatus(String.format("Report @ %s", Time.ISO8601(now + this.getReportRateSeconds())));

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
            return CensusService.this.node.getConfiguration().asLong(CensusService.ReportRateSeconds);
        }

        public void setReportRateSeconds(long reportRateSeconds) {
            CensusService.this.node.getConfiguration().set(CensusService.ReportRateSeconds, reportRateSeconds);
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
                if (CensusService.this.log.isLoggable(Level.SEVERE)) {
                    CensusService.this.log.severe("Cannot start: %s", e);
                }
            }
            this.daemon.start();
        }
    }

    public Map<TitanNodeId,OrderedProperties> select(int count) throws ClassCastException {
        return this.select(count, new HashSet<TitanNodeId>(), new LinkedList<SelectComparator>());
    }

    public Map<TitanNodeId,OrderedProperties> select(int count, Set<TitanNodeId> exclude, List<SelectComparator> comparatorList) throws ClassCastException {
        TimeProfiler timeProfiler = new TimeProfiler("CensusDaemon.select");
        try {
            Select.Request request = new Select.Request(count, exclude, comparatorList);

            TitanMessage reply = CensusService.this.node.sendToNode(Census.CensusKeeper, CensusService.this.getName(), "select", request);

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

    public Map<TitanNodeId,OrderedProperties> select(NodeAddress gateway, int count, Set<TitanNodeId> exclude, List<SelectComparator> comparatorList) throws ClassCastException, ClassNotFoundException, RemoteException {
        Select.Request request = new Select.Request(count, exclude, comparatorList);

        TitanMessage message = new TitanMessage(TitanMessage.Type.RouteToNode,
                this.node.getNodeAddress(),
                Census.CensusKeeper,
                TitanGuidImpl.ANY,
                CensusService.name,
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
            this.log.finest("responds %s", reply.getSource().format());
        }

        Select.Response response = reply.getPayload(Select.Response.class, this.node);
        return response.getCensusData();
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
                    int count = HttpMessage.asInteger(props.get("N"), 6);
                    Map<TitanNodeId,OrderedProperties> list =  this.select(count, new HashSet<TitanNodeId>(), new LinkedList<SelectComparator>());
                    XHTML.Table.Body tbody = new XHTML.Table.Body();
                    for (TitanGuid nodeId : list.keySet()) {
                        tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(nodeId)));
                    }
                    XHTML.Table table = new XHTML.Table(tbody);
                    XHTML.Div div = new XHTML.Div(new XHTML.Heading.H2(CensusService.name), new XHTML.Div(table).setClass("section"));

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

            XHTML.Input start = new XHTML.Input(XHTML.Input.Type.SUBMIT).setName("action").setValue("start").setTitle("Start the census");
            XHTML.Input stop = new XHTML.Input(XHTML.Input.Type.SUBMIT).setName("action").setValue("stop").setTitle("Stop the census");
            XHTML.Input controlButton = (this.daemon == null) ? start : stop;

            XHTML.Input setButton = new XHTML.Input(XHTML.Input.Type.SUBMIT).setName("action").setValue("set-config").setTitle("Set Parameters");

            XHTML.Input resetButton = new XHTML.Input(XHTML.Input.Type.SUBMIT).setName("action").setValue("reset").setTitle("Reset the collected census");

            XHTML.Input go = new XHTML.Input(XHTML.Input.Type.SUBMIT).setName("action").setValue("run").setTitle("Send census report now.");

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
                    new XHTML.Table.Row(new XHTML.Table.Data("Report Rate (seconds)"), new XHTML.Table.Data(this.node.getConfiguration().asLong(CensusService.ReportRateSeconds))),
                    new XHTML.Table.Row(new XHTML.Table.Data("Set Configuration"), new XHTML.Table.Data(setButton)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Add"), new XHTML.Table.Data(addButton), new XHTML.Table.Data(addressField))
            )));

            XHTML.Table.Body dataTableBody = new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Heading("Node"), new XHTML.Table.Heading("Properties")));

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
                    new XHTML.Heading.H2(CensusService.name),
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

    public Map<TitanNodeId, OrderedProperties> select(int count,
            Set<TitanNodeId> excludedNodes, OrderedProperties matchedAttributes)
            throws ClassCastException {
        // TODO Auto-generated method stub
        return null;
    }
}
