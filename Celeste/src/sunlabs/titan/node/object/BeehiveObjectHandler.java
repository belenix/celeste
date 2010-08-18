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
package sunlabs.titan.node.object;

import java.io.Serializable;

import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.Service;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.UnpublishObjectMessage;

/**
 * Objects in the Beehive object pool have data (the state of the object) and
 * behaviour (operations that get, set, or otherwise modify the state of the object).
 * <p>
 * Every {@code BeehiveObject} is an instantiated Java object that implements
 * the {@code BeehiveObject} interface.
 * These Java objects may store the state in the object itself or act as a
 * proxy to some external object outside of the Beehive object store.
 * Each {@code BeehiveObject} implementation controls the access to its data
 * through explicit access methods devised by the designer of the class
 * implementing the {@code BeehiveObject} interface.
 * </p>
 * <p>
 * {@code BeehiveObject} designers must also implement an "object handler"
 * which implements high-level manipulation of whole {@code BeehiveObject} through the object pool.
 * Examples of typical responsibilities of an object handler is
 * the creation of the object in the object pool,
 * maintaining the required replication of an object in the object pool,
 * and the transmission of a message to an object and its reply through the object pool.
 * All interactions with {@code BeehiveObject}s in the object pool are conducted
 * through {@code BeehiveObjectHandler} classes.
 * Each handler provides the specific functions to be applied to their corresponding
 * {@code BeehiveObject} instances.
 * </p>
 * <p>
 * The relationship between {@code BeehiveObjectHandler}s and {@code BeehiveObject}s are typically
 * one-to-one (and probably the most orderly approach), but it is possible to compose
 * a {@code BeehiveObjectHandler} for a set of different {@code BeehiveObject} implementations.
 * </p>
 * <p>
 * </p>
 *
 * @see BeehiveObject
 * @see Service
 */
public interface BeehiveObjectHandler/*<T extends BeehiveObjectHandler.ObjectAPI>*/ extends Service {
    /**
     * Every Beehive object handled by instances of {@code BeehiveObjectHandler}
     * must implement this interface.
     */
    public interface ObjectAPI extends BeehiveObject {

    }
    
    /**
     * Classes implementing this interface and providing request/response communication between instances on different {@link BeehiveNodes}
     * implement this interface for requests sent to a {@code BeehiveObjectHandler}.
     */
    public interface Request extends Serializable {
    	
    }

    /**
     * Classes implementing this interface and providing request/response communication between instances on different {@link BeehiveNodes}
     * implement this interface for requests sent from a {@code BeehiveObjectHandler}.
     */
    public interface Response extends Serializable {
    	
    }

    /**
     * Receive and process a {@link PublishObjectMessage} for a {@link BeehiveObjectHandler.ObjectAPI} instance.
     * <p>
     * If the status of the resultant reply BeehiveMessage does <em>NOT</em>
     * indicate success, the publishing BeehiveNode and all inter-hop nodes
     * are obligated to <em>NOT</em> store backpointers and the storing node
     * <em>MUST NOT</em> store the object, and silently remove the object if it
     * has already been stored on the publishing node.
     * </p>
     * <p>
     * Note: Implementors of this method must be mindful that the object being
     * published may not exist in the system yet.  Furthermore, because the
     * publisher of the object will have locked its copy of the object
     * until this method returns, attempts to fetch the object in this
     * method will result in deadlock if there is only one object in the system.
     * </p>
     * @param message
     * @return The reply {@link BeehiveMessage}
     */
    public BeehiveMessage publishObject(BeehiveMessage message);

    /**
     * Receive and process a {@link UnpublishObjectMessage} for a
     * {@link BeehiveObjectHandler.ObjectAPI} instance.
     * <p>
     * Every node that receives this message which also has a back-pointer
     * to the specified {@link BeehiveObject} <em>MUST</em> delete that
     * back-pointer and forward this message along the routing path.
     * </p>
     *
     * @param message
     * @return The reply {@link BeehiveMessage}
     */
    public BeehiveMessage unpublishObject(BeehiveMessage message);
}
