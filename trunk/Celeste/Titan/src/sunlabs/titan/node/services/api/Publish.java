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
package sunlabs.titan.node.services.api;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.PublishDaemon.GetPublishers;

/**
 * Implementors of this interface are extensions of the {@link AbstractTitanService} class and cause
 * {@link TitanObject} instances to be published to the rest of the system.
 */
public interface Publish {

    /**
     * Instances of this class contain the data necessary to publish or unpublish one or more {@link TitanObject}'s.
     * @see sunlabs.titan.node.services.api.Publish.PublishUnpublishResponse
     * 
     * @author Glenn Scott - Oracle Sun Labs.
     */
    public interface PublishUnpublishRequest extends Serializable {
        /**
         * If {@code true} this {@code Publish.PublishUnpublishRequest} is a backup for the root of the object's
         * {@link TitanGuid}.
         */
        // @see {@link sunlabs.titan.node.object AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Publish.PublishUnpublishRequest)}.
        public boolean isBackup();
        
        /**
         * Get the {@link NodeAddress} of the sender of this Request.
         * @return the {@code NodeAddress} of the sender of this Request.
         */
        public NodeAddress getPublisherAddress();

        /**
         * Return a {@code Map} indexed by the object identifier
         * of the object to publish and the corresponding object metadata as the value.
         */
        public Map<TitanGuid,TitanObject.Metadata> getObjects();

        /**
         * Get the number of seconds that any publish record that results from this Request should exist.
         * 
         * @return the number of seconds that any publish record that results from this Request should exist.
         */
        public long getSecondsToLive();

        /**
         * Set the backup flag to {@code value}.
         * See {@link #isBackup()}
         */
        public void setBackup(boolean b);
    }

    /**
     * The Response to a {@link Publish.PublishUnpublishRequest}.
     * 
     * @see Publish.PublishUnpublishRequest
     * @author Glenn Scott - Oracle Sun Labs.
     */
    public interface PublishUnpublishResponse {
        public Set<TitanGuid> getObjectIds();
        
        public NodeAddress getRootNodeAddress();
    }

    /**
     * Publish a {@link TitanObject}.
     * <p>
     * This transmits a single {@link PublishObjectMessage} to the root of the {@link TitanGuid} of the object being published.
     * </p>
     * <p>
     * The message travels across the network until it reaches the root of the object's {@link TitanGuid}.
     * When the root receives the message, the {@link AbstractObjectHandler} specified by name in the
     * meta-data accompanying the {@link PublishObjectMessage} (using the meta-data name
     * {@link ObjectStore#METADATA_CLASS}).
     * </p>
     * <p>
     * The {@link TitanMessage} returned from the {@link AbstractObjectHandler#publishObject} method is propagated back as a reply to the original
     * node publishing the object.
     * </p>
     * <p>
     * If the status encoded in the {@link TitanMessage} reply is any of the codes representing success, each node propagating the
     * reply is free to record a back-pointer to the object on the publishing node.
     * If the status encoded in the {@link TitanMessage} reply is NOT any of the codes representing success, each node propagating the
     * reply must not record a back-pointer to the object on the publishing node.
     * </p>
     * @throws sunlabs.titan.node.BeehiveObjectStore.Exception 
     * @throws BeehiveObjectPool.Exception 
     * @throws ClassNotFoundException 
     * @throws ClassCastException 
     */
    public Publish.PublishUnpublishResponse publish(TitanObject object) throws ClassCastException, ClassNotFoundException, BeehiveObjectPool.Exception, sunlabs.titan.node.BeehiveObjectStore.Exception;

    /**
     * Transmit a {@link TitanMessage} to "unpublish" a {@link TitanGuid}.
     * <p>
     * This unpublish of an object-id <em>does not contain the object-type of the object</em>.
     * </p>
     * <p>
     * This is typically used where an object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     * </p>
     * @param objectId the object identifiers of the object to be unpublished
     * @return the reply {@link TitanMessage} from the root of object identifier
     */
    public TitanMessage unpublish(TitanGuid objectId);
    
    /**
     * Transmit a {@link TitanMessage} to "unpublish" a {@link TitanObject}.
     * <p>
     * This unpublish of an object <em>does contain the object-type of the object</em>.
     * See also, {@link Publish#unpublish(TitanGuid)}.
     * </p>
     * <p>
     * This is typically used where an object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     * </p>
     * @param object the object to be unpublished
     * @return the reply {@link TitanMessage} from the root of object identifier
     * @throws BeehiveObjectStore.Exception 
     * @throws BeehiveObjectPool.Exception 
     * @throws ClassNotFoundException 
     * @throws ClassCastException 
     * @throws BeehiveObjectStore.Exception 
     */
    public Publish.PublishUnpublishResponse unpublish(TitanObject object) throws ClassCastException, ClassNotFoundException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception;

    /**
     * Get the set of publishers of a specified {@link TitanObject}. 
     * @param message
     * @return {@link TitanMessage} containing an instance of {@link sunlabs.titan.node.services.PublishDaemon.GetPublishers.Response} as payload.
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws RemoteException
     */
	public TitanMessage getPublishers(TitanMessage message) throws ClassCastException, ClassNotFoundException, RemoteException;
	
    /**
     * Get the set of publishers of a specified {@link TitanObject}.
     * @param objectId the {@link TitanGuid} of the object.
     * @return {@link Set}<{@link sunlabs.titan.node.Publishers.PublishRecord}>
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws RemoteException
     */
    public Set<Publishers.PublishRecord> getPublishers(TitanGuid objectId) throws ClassNotFoundException, ClassCastException;

//    /**
//     * Maintain the persistence of an object given a PublishObject message that supplies the persistency information.
//     *
//     * Persistence may be one of several types.
//     * <ul>
//     * <li><tt>Star</tt> <i>minimumCopies</i>,<i>maximumCopies</i>,<i>nAttempts</i></li>
//     * With the minimum and maximum number of copies, and the total number of attempts to store specified,
//     * store copies of the object named in the PublishObject message in random locations in the DOLR.
//     * <p>
//     * Specifying the maximum number of copies may cause the system to fail during disconnected operation.
//     * You may not have enough copies such that the disconnected segment will have a copy to recover.
//     * </p>
//     * <p>
//     * There also may not be enough nodes in the Beehive to achieve the minimum number of copies.
//     * If, for example, the minimum number of copies is 10, and the number of nodes is 9 or less,
//     * the system will not be able to actually store 10 copies.
//     * </p>
//     * <li><tt>Ring</tt></li>
//     * </ul>
//     * <p>
//     * This should distinguish between objects that are published as temporary or permanent objects in the publisher's object store.
//     * Correspondingly, the publisher of an object should be able to specify how many, of either temporary or permanent stored, copies should be made.
//     * Only publishers that are publishing a permanent object shall be authoritative for the number of copies to maintain.
//     * This is to address the slow expiration/demotion of objects as they decay from the system.
//     * </p>
//     * @param value
//     * @param message
//     */
////    public void persist(String value, DOLRMessage message);
}

