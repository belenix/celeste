/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.titan.node.services.api;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.services.BeehiveService;
import sunlabs.titan.node.services.PublishDaemon.GetPublishers;
import sunlabs.titan.node.services.PublishDaemon.UnpublishObject;

/**
 * Implementors of this interface are extensions of the {@link BeehiveService} class and cause
 * {@link BeehiveObject} instances to be published to the rest of the system.
 */
public interface Publish {

    public interface Request extends Serializable {
        /**
         * If {@code true} this Publish is a backup for the root of the object's
         * {@link TitanGuid} and signals the helper method
         * {@link AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Request)}
         * to <b>not</b> continue making backup back-pointers.
         */
        public boolean isBackup();
        
        public NodeAddress getPublisherAddress();

        /**
         * Return a {@link Map<BeehiveObjectId,BeehiveObject.Metadata>} indexed by the object identifier
         * of the object to publish and the corresponding object metadata as the value.
         */
        public Map<TitanGuid,BeehiveObject.Metadata> getObjectsToPublish();

        public long getSecondsToLive();
    }

    public interface Response {

    }

    /**
     * Publish a {@link BeehiveObject}.
     * <p>
     * This transmits a single {@link PublishObjectMessage} to the root of the {@link TitanGuid} of the object being published.
     * </p>
     * <p>
     * The message travels across the network until it reaches the root of the object's {@link TitanGuid}.
     * When the root receives the message, the {@link AbstractObjectHandler} specified by name in the
     * meta-data accompanying the {@link PublishObjectMessage} (using the meta-data name
     * {@link ObjectStore#METADATA_TYPE}).
     * </p>
     * <p>
     * The {@link BeehiveMessage} returned from the {@link AbstractObjectHandler#publishObject} method is propagated back as a reply to the original
     * node publishing the object.
     * </p>
     * <p>
     * If the status encoded in the {@link BeehiveMessage} reply is any of the codes representing success, each node propagating the
     * reply is free to record a back-pointer to the object on the publishing node.
     * If the status encoded in the {@link BeehiveMessage} reply is NOT any of the codes representing success, each node propagating the
     * reply must not record a back-pointer to the object on the publishing node.
     * </p>
     */
    public BeehiveMessage publish(BeehiveObject object);

    /**
     * Transmit a {@link BeehiveMessage} to "unpublish" a {@link TitanGuid}.
     * <p>
     * This unpublish of an object-id <em>does not contain the object-type of the object</em>.
     * </p>
     * <p>
     * This is typically used where an object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     * </p>
     * @param objectId the object identifiers of the object to be unpublished
     * @return the reply {@link BeehiveMessage} from the root of object identifier
     */
    public BeehiveMessage unpublish(TitanGuid objectId, UnpublishObject.Type type);
    
    /**
     * Transmit a {@link BeehiveMessage} to "unpublish" a {@link BeehiveObject}.
     * <p>
     * This unpublish of an object <em>does contain the object-type of the object</em>.
     * </p>
     * <p>
     * This is typically used where an object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     * </p>
     * @param objectId the object identifiers of the object to be unpublished
     * @return the reply {@link BeehiveMessage} from the root of object identifier
     */
    public BeehiveMessage unpublish(BeehiveObject object, UnpublishObject.Type type);

    /**
     * Get the set of publishers of a specified {@link BeehiveObject}.
     * @param message
     * @return {@link BeehiveMessage} containing an instance of {@link GetPublishers.Response} as payload.
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws RemoteException
     */
	public BeehiveMessage getPublishers(BeehiveMessage message) throws ClassCastException, ClassNotFoundException, RemoteException;
	
    /**
     * Get the set of publishers of a specified {@link BeehiveObject}.
     * @param message
     * @return {@link Set}<{@link Publishers.PublishRecord}>
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

