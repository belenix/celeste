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
package sunlabs.titan.node.object;

import java.io.Serializable;

import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.UnpublishObjectMessage;
import sunlabs.titan.node.services.api.Publish;

/**
 * Objects in the Titan object pool have data (the state of the object) and
 * behaviour (operations that get, set, or otherwise modify the state of the object).
 * <p>
 * Every {@code TitanObject} is an instantiated Java object that implements
 * the {@code TitanObject} interface.
 * These Java objects may store the state in the object itself or act as a
 * proxy to some external object outside of the Beehive object store.
 * Each {@code TitanObject} implementation controls the access to its data
 * through explicit access methods devised by the designer of the class
 * implementing the {@code TitanObject} interface.
 * </p>
 * <p>
 * {@code TitanObject} designers must also implement an "object handler"
 * which implements high-level manipulation of whole {@code TitanObject} through the object pool.
 * Examples of typical responsibilities of an object handler is
 * the creation of the object in the object pool,
 * maintaining the required replication of an object in the object pool,
 * and the transmission of a message to an object and its reply through the object pool.
 * All interactions with {@code BeehiveObject}s in the object pool are conducted
 * through {@code BeehiveObjectHandler} classes.
 * Each handler provides the specific functions to be applied to their corresponding
 * {@code TitanObject} instances.
 * </p>
 * <p>
 * The relationship between {@code BeehiveObjectHandler}s and {@code BeehiveObject}s are typically
 * one-to-one (and probably the most orderly approach), but it is possible to compose
 * a {@code BeehiveObjectHandler} for a set of different {@code TitanObject} implementations.
 * </p>
 * <p>
 * </p>
 *
 * @see TitanObject
 * @see TitanService
 */
public interface BeehiveObjectHandler/*<T extends BeehiveObjectHandler.ObjectAPI>*/ extends TitanService {
    /**
     * Every Beehive object handled by instances of {@code BeehiveObjectHandler}
     * must implement this interface.
     */
    public interface ObjectAPI extends TitanObject {

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
     * If the status of the resultant reply {@link TitanMessage} does <em>NOT</em> indicate success, the publishing {@link TitanNode} and all inter-hop nodes
     * are obligated to <em>NOT</em> store backpointers and the storing node <em>MUST NOT</em> store the object.
     * Furthermore, if the object is already stored on the publishing node, it must be removed.
     * </p>
     * <p>
     * Note: Implementors of this method must be mindful that the object being
     * published may not exist in the system yet.  Furthermore, because the
     * publisher of the object will have locked its copy of the object
     * until this method returns, attempts to fetch the object in this
     * method will result in deadlock if there is only one object in the system.
     * </p>
     * @param message
     * @return The reply {@link Publish.PublishUnpublishResponse}.
     * @throws ClassCastException 
     * @throws ClassNotFoundException 
     * @throws BeehiveObjectPool.Exception
     * @throws BeehiveObjectStore.Exception
     */
    public Publish.PublishUnpublishResponse publishObject(TitanMessage message) throws ClassNotFoundException, ClassCastException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception;

    /**
     * Receive and process a {@link UnpublishObjectMessage} for a
     * {@link BeehiveObjectHandler.ObjectAPI} instance.
     * <p>
     * Every node that receives this message which also has a back-pointer
     * to the specified {@link TitanObject} <em>MUST</em> delete that
     * back-pointer and forward this message along the routing path.
     * </p>
     *
     * @param message
     * @return The reply {@link Publish.PublishUnpublishResponse}.
     * @throws ClassCastException 
     * @throws ClassNotFoundException 
     */
    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message) throws ClassNotFoundException, ClassCastException;
}
