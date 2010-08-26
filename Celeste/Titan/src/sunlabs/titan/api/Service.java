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
package sunlabs.titan.api;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.util.DOLRLogger;

/**
 * Every {@link TitanNode} instance has a set of services which implement
 * the capabilities of the system.
 * Every message transmitted between Beehive Nodes is a message destined
 * for a Beehive service.
 * Methods in a class implementing this interface that have the parameter
 * signature of
 * <code><i>method</i>(BeehiveMessage message)</code>
 * are invoked by a {@link BeehiveMessage}.
 * <p>
 * There are (currently) three kinds of services.
 * <ul>
 * <li>
 * The first are autonomous threads maintaining parts of the Beehive node state,
 * or participating in the exchange of Beehive node information.
 * Examples of these kinds of services maintain the routing tables and the
 * periodic retransmitting of object publish messages.
 * By convention the names of these kinds of services end with the String
 * <code>Daemon</code>.
 * </li>
 * <li>
 * The second are services that perform a function as the result of
 * receiving a message which neither creates or modifies an object in the
 * object pool, nor runs as a daemon thread in the node.
 * For example, a service that retrieves an object from the object pool.
 * By convention the names of these kinds of services end with the String
 * <code>Function</code>.
 * </li>
 * <li>
 * The third are the classes supplying the code for stored objects.
 * For example the Celeste {@link sunlabs.celeste.node.services.object.AnchorObjectHandler} class.
 * By convention the names of these kinds of services end with the String
 * <code>Type</code>.
 * </li>
 * </ul>
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface Service extends Serializable {

    /**
     * The configuration parameter name controlling the logging level of
     * this Service.
     * See {@link java.util.logging java.util.logging}
     */
    public final static String CONFIG_LOGLEVEL_NAME = "logLevel";
    
    /**
     * Invoke the specified method in this Service.
     * 
     * @param methodName the name of the method to invoke (See also {@link BeehiveMessage#getSubjectClassMethod()}.
     * @param request the inbound {@link BeehiveMessage} that induced this invocation.
     * @return a well-formed {@link BeehiveMessage} reply.
     */
    public BeehiveMessage invokeMethod(String methodName, BeehiveMessage request);

    /**
     * Get the human readable description of the service's status.
     */
    public String getStatus();

    /**
     * Get the name of this Beehive Service.
     */
    public String getName();

    /**
     * Get the human readable description of the service.
     */
    public String getDescription();

    /**
     * Get the {@link BeehiveNode} of this {@code Service}.
     */
    public BeehiveNode getNode();

    /**
     * Get the {@link DOLRLogger} instance for this {@code Service}.
     */
    public DOLRLogger getLogger();

    /**
     * Start this {@code Service} and start its processing.
     */
    public void start() throws Exception;

    /**
     * Restart this {@code Service}.
     * <p>
     * Restarting typically consists of stopping and starting the Service.
     * Service implementors need to arrange for all cleanup and
     * re-initialisation requirements of their services.
     * </p>
     */
    public void restart() throws Exception;

    /**
     * Stop the {@code Service} and process no more messages.
     * <p>
     * No attempt is made to detect whether there are any current users of
     * this application.  An application stopping unexpectedly should be
     * viewed as just another transient failure, and the service should
     * be re-obtained via a call to {@link BeehiveNode#getService(String)}.
     * </p>
     * <p>
     * Implementations of this method must protect themselves from {@code start()} having
     * never been called or {@code stop()} being called more than once.
     * </p>
     */
    public void stop();
    
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
}
