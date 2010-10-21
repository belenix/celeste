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
package sunlabs.titan.api;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.XML.Content;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.util.DOLRLogger;

/**
 * Every {@link TitanNode} instance has a set of services which implement
 * the capabilities of the system.
 * Every message transmitted between nodes is a message destined
 * for a {@code TitanNode} service.
 * Methods in a class implementing this interface that have the parameter
 * signature of
 * <code><i>method</i>(TitanMessage message)</code>
 * are invoked by a {@link TitanMessage}.
 * <p>
 * There are (currently) three kinds of services.
 * <ul>
 * <li>
 * The first are autonomous threads maintaining parts of the Titan node state,
 * or participating in the exchange of Titan node information.
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
 * By convention the names of these kinds of services end with the String
 * <code>Type</code>.
 * </li>
 * </ul>
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface TitanService extends Serializable {
    
    public interface Operation<T extends Serializable> {
        public interface HTTP {

        }

        /**
         * Classes implementing the {@link TitanService.Operation} interface which also implement request/response
         * communications between instances on different {@link TitanNode} instances
         * implement this interface for requests sent to a {@code BeehiveObjectHandler}.
         */
        public interface Request extends Serializable {
            
        }

        /**
         * Classes implementing the {@link TitanService.Operation} interface which also implement request/response
         * communications between instances on different {@link TitanNode} instances
         * implement this interface for responses sent to a {@code BeehiveObjectHandler}.
         */
        public interface Response extends Serializable {
            
        }
        
        public T response(TitanMessage request);

    }

    /**
     * Classes implementing the TitanService interface which also implement request/response
     * communications between instances on different {@link TitanNode} instances
     * implement this interface for requests sent to a {@code BeehiveObjectHandler}.
     */
    public interface Request extends Serializable {
        
    }

    /**
     * Classes implementing the TitanService interface which also implement request/response
     * communications between instances on different {@link TitanNode} instances
     * implement this interface for responses sent to a {@code BeehiveObjectHandler}.
     */
    public interface Response extends Serializable {
        
        public XML.Content toXML();
    }

    /**
     * The configuration parameter name controlling the logging level of
     * this Service.
     * See {@link java.util.logging java.util.logging}
     */
    public final static String CONFIG_LOGLEVEL_NAME = "logLevel";
    
    /**
     * Invoke the specified method in this Service.
     * 
     * @param methodName the name of the method to invoke (See also {@link TitanMessage#getSubjectClassMethod()}.
     * @param request the inbound {@link TitanMessage} that induced this invocation.
     * @return a well-formed {@link TitanMessage} reply.
     */
    public TitanMessage invokeMethod(String methodName, TitanMessage request);

    /**
     * Get the human readable description of the service's status.
     */
    public String getStatus();

    /**
     * Get the name of this Titan Service.
     */
    public String getName();

    /**
     * Get the human readable description of the service.
     */
    public String getDescription();

    /**
     * Get the {@link TitanNode} of this {@code TitanService}.
     */
    public TitanNode getNode();

    /**
     * Get the {@link DOLRLogger} instance for this {@code TitanService}.
     */
    public DOLRLogger getLogger();

    /**
     * Start this {@code TitanService} and start its processing.
     */
    public void start();

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
     * Stop the {@code TitanService} and process no more messages.
     * <p>
     * No attempt is made to detect whether there are any current users of
     * this application.  An application stopping unexpectedly should be
     * viewed as just another transient failure, and the service should
     * be re-obtained via a call to {@link TitanNode#getService(String)}.
     * </p>
     * <p>
     * Implementations of this method must protect themselves from {@code start()} having
     * never been called or {@code stop()} being called more than once.
     * </p>
     */
    public void stop();
    
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
}
