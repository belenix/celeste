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

import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Map;

import javax.management.AttributeChangeNotification;
import javax.management.JMException;
import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.api.management.TitanServiceMBean;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.util.DOLRLogger;
import sunlabs.titan.node.util.DOLRLoggerMBean;
import sunlabs.titan.util.WeakMBeanRegistrar;

/**
 * A TitanService invoked by the reception of a {@link TitanMessage}
 * is responsible for interpreting and processing that message, and
 * ultimately returning a {@link TitanMessage} reply.
 *
 * </p><p>
 *
 * Each {@code TitanService} has a name which is specified in the body of
 * each {@link TitanMessage}.
 * [NB:  In the Tapestry paper, each Applications has a "GUID", just like the Nodes
 * and stored Objects, but in this implementation Applications are simply identified
 * with a String (with no whitespace) as a name.]
 *
 * </p><p>
 *
 * Every {@code TitanService} has an associated filesystem directory in which it may
 * store private data for the application's use.  Example uses of the
 * filesystem directory are to store data which is persistent across restarts
 * of the {@code TitanService} (or Titan Node), keeping a log file of the application's
 * activities, and so forth.
 *
 * </p><p>
 *
 * Similarly, each {@code TitanService} has a HTTP URL which it can use to interact
 * with an administrator.  Example uses of the HTTP URL is to produce a HTML
 * web page to display the current state of the application, setting and
 * reseting runtime parameters, and to display portions of the log file(s).
 * </p>
 */
public abstract class AbstractTitanService extends NotificationBroadcasterSupport implements TitanService, TitanServiceMBean {
    private final static long serialVersionUID = 1L;

    //
    // Arrange to use weak references for registrations with the MBean server.
    //
    protected final static WeakMBeanRegistrar registrar = new WeakMBeanRegistrar(ManagementFactory.getPlatformMBeanServer());

    public final static Attributes.Prototype LogFileSize = new Attributes.Prototype(AbstractTitanService.class, "LogFileSize", 8*1024*1024,
            "The maximum number of bytes to allow a log file to grow before it is turned over");
    public final static Attributes.Prototype LogFileCount = new Attributes.Prototype(AbstractTitanService.class, "LogFileCount", 10,
            "The maximum number log files to keep.");

    /** The {@code Node} instance that this Application belongs to. */
    protected final TitanNode node;

    /** The name of this {@code Service} */
    protected final String name;

    // JMX variables and parameters
    protected long jmxEventCounter;
    protected final ObjectName jmxObjectNameRoot;

    private String status;

    /** A human-readable description of this {@code Service}. */
    private String description;

    private byte[] applicationData;

    protected final DOLRLogger log;

    /**
     * 
     * @param node
     * @param applicationName
     * @param description
     * @throws JMException
     */
    public AbstractTitanService(TitanNode node, String applicationName, String description) throws JMException {
        if (applicationName == null || applicationName.matches("\\s"))
            throw new IllegalArgumentException("applicationName must be non-null and cannot contain white space");

        node.getConfiguration().add(AbstractTitanService.LogFileSize);
        node.getConfiguration().add(AbstractTitanService.LogFileCount);

        this.name = applicationName;
        this.node = node;
        this.description = description;
        this.status = "created";
        this.jmxEventCounter = 0;

        // Create the private "spool" directory for this Service.
        new File(this.getSpoolDirectory()).mkdirs();

        this.log = new DOLRLogger(applicationName, node.getNodeId(), this.getSpoolDirectory(),
                node.getConfiguration().asInt(AbstractTitanService.LogFileSize), node.getConfiguration().asInt(AbstractTitanService.LogFileCount));
        //this.log.load();

        if (node.getJMXObjectName() != null) {
            this.jmxObjectNameRoot = JMX.objectName(node.getJMXObjectName(), applicationName);

            AbstractTitanService.registrar.registerMBean(this.jmxObjectNameRoot, this, TitanServiceMBean.class);
            AbstractTitanService.registrar.registerMBean(JMX.objectName(this.jmxObjectNameRoot, "log"), this.log, DOLRLoggerMBean.class);
        } else {
            this.jmxObjectNameRoot = null;
        }
    }
    
    public TitanMessage invokeMethod(String methodName, TitanMessage request) {
        try {
            Object object = this.getClass().getMethod(methodName, TitanMessage.class).invoke(this, request);
            if (object instanceof TitanMessage) {
                System.out.printf("%s.%s returns deprecated return value%n", this.getClass(), methodName);
                return (TitanMessage) object;
            }
            return request.composeReply(this.node.getNodeAddress(), (Serializable) object);
        } catch (IllegalArgumentException e) {
            this.log.severe(e.toString());
            e.printStackTrace();
            return request.composeReply(this.node.getNodeAddress(), e);
        } catch (SecurityException e) {
            this.log.severe(e.toString());
            e.printStackTrace();
            return request.composeReply(this.node.getNodeAddress(), e);
        } catch (IllegalAccessException e) {
            this.log.severe(e.toString());
            e.printStackTrace();
            return request.composeReply(this.node.getNodeAddress(), e);
        } catch (InvocationTargetException e) {
            this.log.severe(e.toString());
            e.printStackTrace();
            return request.composeReply(this.node.getNodeAddress(), e.getCause());
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return request.composeReply(this.node.getNodeAddress(), e);
        }
    }

    public void setConfig() {

    }

    public DOLRLogger getLogger() {
        return this.log;
    }

    /**
     * Get the status description of this Service.
     * @return A String instance containing a human readable description of the service's status.
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Set the status of this Service to the String {@code newStatus}.
     * Return the value of the previous status String.
     *
     * @param newStatus
     * @return A String instance containing a human readable description of the service's status.
     */
    public String setStatus(String newStatus) {
        String oldStatus = this.status;
        this.status = newStatus;
        return oldStatus;
    }

    /**
     * Get the name of this Service as a String.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return Returns the description String of this Service.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * @param description The description String to set for this Service.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the arbitrary data associated with this Service.
     */
    public byte[] getData() {
        return this.applicationData;
    }

    /**
     * Set the arbitrary data associated with this Service.
     */
    public void setData(byte[] d) {
        this.applicationData = d;
    }

    /**
     * Get the path-name of the associated directory that is used to
     * store or cache data for this {@link TitanService}.
     */
    public String getSpoolDirectory() {
        return this.node.getSpoolDirectory() + File.separator + "applications" + File.separator + this.name + File.separator;
    }

    /**
     * Get the {@link BeehiveNode} instance of this Service.
     */
    public TitanNode getNode() {
        return this.node;
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[] {
                AttributeChangeNotification.ATTRIBUTE_CHANGE
        };
        String name = AttributeChangeNotification.class.getName();
        String description = "An attribute of this MBean has changed";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[] { info };
    }

    /**
     * Compose and send a JMX {@link AttributeChangeNotification}.
     *
     * @param message
     * @param attributeName
     * @param oldValue
     * @param newValue
     */
    protected synchronized void sendJMXNotification(String message, String attributeName, Object oldValue, Object newValue) {

        // Construct a notification that describes the change.  The
        // "source" of a notification is the ObjectName of the MBean
        // that emitted it.  But an MBean can put a reference to
        // itself ("this") in the source, and the MBean server will
        // replace this with the ObjectName before sending the
        // notification on to its clients.
        //
        // For good measure, we maintain a sequence number for each
        // notification emitted by this MBean.
        //
        // The oldValue and newValue parameters to the constructor are
        // of type Object, so we are relying on Tiger's autoboxing
        // here.
        if (message == null)
            message = "Update " + attributeName;

        if (this.jmxObjectNameRoot != null) {
            System.out.println(this.jmxObjectNameRoot.toString() + " new value: " + newValue.getClass().getCanonicalName());

            this.sendNotification(new AttributeChangeNotification(this.jmxObjectNameRoot,
                    this.jmxEventCounter++,
                    System.currentTimeMillis(),
                    message,
                    attributeName,
                    newValue.getClass().getCanonicalName(),
                    oldValue,
                    newValue));
        }
    }

    protected void sendJMXNotification(String attributeName, Object oldValue, Object newValue) {
        this.sendJMXNotification(null, attributeName, oldValue, newValue);
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

    	XHTML.Table.Body tbody = new XHTML.Table.Body();
    	// This will work when the Services no longer have the version # in the name.
    	for (String name : this.node.getConfiguration().keySet()) {
    		if (name.startsWith(this.getName())) {
    			tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(name), new XHTML.Table.Data(String.valueOf(this.node.getConfiguration().get(name).getValue()))));
    		}
    	}

    	XHTML.Div div = new XHTML.Div().setClass("section").addClass("BeehiveService");
    	div.add(new XHTML.Heading.H1(WebDAVDaemon.inspectNodeXHTML(this.node.getNodeAddress())));
    	div.add(new XHTML.Span(this.getName()));
    	if (tbody.getChildren().size() > 0) {
    		XHTML.Table configurationTable = new XHTML.Table(new XHTML.Table.Caption("Configuration Values"), tbody).addClass("striped");
    		div.add(configurationTable);
    	}
    	return div;
    }

    /**
     * <p>
     * Overrides of this method must protect themselves if start() is called
     * multiple times, and if it is called by two threads at the same time.
     * </p>
     */
    public synchronized void start() throws Exception {
        this.setStatus("idle");
    }

    /**
     * Restart this service by invoking the Service's {@link #stop()} and {@link #start()} methods.
     */
    public synchronized void restart() throws Exception {
        this.stop();
        this.start();
    }

    public synchronized void stop() {
        this.setStatus("stopped");
    }

    public static String makeName(Class<? extends TitanService> c, long version) {
        return AbstractTitanService.makeName(c.getCanonicalName(), version);
    }

    public static String makeName(String canonicalClassName, long version) {
        //
        // XXX: Perhaps should check that canonicalClassName is well-formed as
        //      a class name.
        //
        return canonicalClassName + ";" + version;
    }

    /**
     * Given a name created by {@link AbstractTitanService#makeName(Class, long)},
     * extract two String tokens representing the original class name
     * and version.
     * <p>
     * If the given name has not been created by makeName, it will
     * not contain the additional token.  If this happens, the returned
     * array contains only one element, which is the same as the input name.
     * </p>
     */
    public static String[] getNameTokens(String name) {
        return name.split(";", 2);
    }
}
