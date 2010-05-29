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

package sunlabs.celeste.node.services.object;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.ObjectStore;
import sunlabs.beehive.node.AbstractBeehiveObject;
import sunlabs.beehive.node.BeehiveMessage;
import sunlabs.beehive.node.BeehiveNode;
import sunlabs.beehive.node.BeehiveObjectStore;
import sunlabs.beehive.node.PublishObjectMessage;
import sunlabs.beehive.node.BeehiveMessage.RemoteException;
import sunlabs.beehive.node.object.AbstractObjectHandler;
import sunlabs.beehive.node.object.DeleteableObject;
import sunlabs.beehive.node.object.RetrievableObject;
import sunlabs.beehive.node.object.StorableObject;
import sunlabs.beehive.node.services.BeehiveService;
import sunlabs.beehive.node.services.PublishDaemon;
import sunlabs.beehive.util.DOLRStatus;
import sunlabs.celeste.client.ReplicationParameters;

public final class FragmentObject extends AbstractObjectHandler implements FObjectType {
    private final static long serialVersionUID = 1L;

    private final static String name = BeehiveService.makeName(FragmentObject.class, FragmentObject.serialVersionUID);
    private final static Integer defaultStoreAttempts = Integer.valueOf(100);

    private final static int replicationStore = 2;
    private final static int replicationCache = 2;

    public class FObject extends AbstractBeehiveObject implements FObjectType.FObject {
        private static final long serialVersionUID = 1L;

        private int replicationMinimum;
        private int replicationCache;

        private byte[] data;

        public FObject(BeehiveObjectId deleteTokenId, long timeToLive, BeehiveObject.Metadata metaData, byte[] data, ReplicationParameters replicationParams) {
            super(FragmentObject.class, deleteTokenId, timeToLive);
            this.data = data;

            this.replicationMinimum = replicationParams.getAsInteger(BlockObject.Object.REPLICATIONPARAM_MIN_NAME, FragmentObject.replicationStore);
            this.replicationCache = replicationParams.getAsInteger(BlockObject.Object.REPLICATIONPARAM_LOWWATER_NAME, FragmentObject.replicationCache);
            this.setProperty(ObjectStore.METADATA_REPLICATION_STORE, this.replicationMinimum);
            this.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER, this.replicationCache);
        }

        public byte[] dataAsByteArray() {
            return this.data;
        }

        public byte[] getContents() {
            return this.data;
        }

        @Override
        public BeehiveObjectId getDataId() {
            return new BeehiveObjectId(this.data);
        }

        public int getReplicationMinimum() {
            return this.replicationMinimum;
        }

        public int getReplicationCache() {
            return this.replicationCache;
        }

        public void delete(BeehiveObjectId profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.DeleteTokenException {
            this.data = new byte[0];

            DeleteableObject.ObjectDeleteHelper(this, profferedDeleteToken, timeToLive);
            //this.setTimeToLive(timeToLive);
            //this.setProperty(ObjectStore.METADATA_DELETETOKEN, profferedDeleteToken);

            BeehiveObjectStore.CreateSignatureVerifiedObject(this.getObjectId(), this);
        }
    }

    // This is a per-object lock signaling that an object is undergoing the delete process.
    private ObjectLock<BeehiveObjectId> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already
    // deleting the specified object.
    private ObjectLock<BeehiveObjectId> deleteLocalObjectLocks;

    private int storeAttempts;

    public FragmentObject(BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, FragmentObject.name, "FObject Application");
        this.storeAttempts = FragmentObject.defaultStoreAttempts;

        this.publishObjectDeleteLocks = new ObjectLock<BeehiveObjectId>();
        this.deleteLocalObjectLocks = new ObjectLock<BeehiveObjectId>();
    }

    public int getStoreAttempts() {
        return this.storeAttempts;
    }

    public void setStoreAttempts(int count) {
        this.storeAttempts = count;
    }

    public BeehiveMessage storeLocalObject(BeehiveMessage message) {
        try {
            FObjectType.FObject fObject = message.getPayload(FObjectType.FObject.class, this.node);
            BeehiveMessage reply = StorableObject.storeLocalObject(this, fObject, message);
            return reply;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(node.getNodeAddress(), e);
        }
    }

    public BeehiveMessage retrieveLocalObject(BeehiveMessage message) {
        return RetrievableObject.retrieveLocalObject(this, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * A deleted FObject will return null.
     * </p>
     * @throws RemoteException 
     * @throws ClassCastException 
     */
    public FragmentObject.FObject retrieve(BeehiveObjectId objectId)
    throws BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException, ClassCastException {
        FragmentObject.FObject object = RetrievableObject.retrieve(this, FragmentObject.FObject.class, objectId);
        return object;
    }

    public BeehiveMessage publishObject(BeehiveMessage message) {
        try {
            PublishDaemon.PublishObject.Request publishRequest = message.getPayload(PublishDaemon.PublishObject.Request.class, this.node);

            //
            // Handle deleted objects.
            //
            DeleteableObject.publishObjectHelper(this, publishRequest);

            AbstractObjectHandler.publishObjectBackup(this, publishRequest);

            return message.composeReply(this.node.getNodeAddress(), new PublishDaemon.PublishObject.Response());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        }
    }

    public BeehiveMessage unpublishObject(BeehiveMessage message) {
        return message.composeReply(this.node.getNodeAddress());
    }

    public FObjectType.FObject create(BeehiveObjectId deleteTokenId, long timeToLive, ReplicationParameters replicationParams, BeehiveObject.Metadata metaData, byte[] data) {
        return new FObject(deleteTokenId, timeToLive, metaData, data, replicationParams);
    }

    public FObjectType.FObject storeObject(FObjectType.FObject fObject) throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {

        BeehiveObject object = StorableObject.storeObject(this, fObject);
        return (FObjectType.FObject) object;
    }

    public ObjectLock<BeehiveObjectId> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    /**
     * This method is invoked as the result of receiving a deleteLocalObject
     * {@link BeehiveMessage} or the receipt of a {@link PublishObjectMessage} containing valid
     * delete information.
     */
    public BeehiveMessage deleteLocalObject(BeehiveMessage message) throws ClassNotFoundException {
        if (this.deleteLocalObjectLocks.trylock(message.subjectId)) {
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", message.subjectId);
            }
            try {
                return DeleteableObject.deleteLocalObject(this, message.getPayload(DeleteableObject.Request.class, this.node), message);
            } catch (RemoteException e) {
                e.printStackTrace();
                return message.composeReply(node.getNodeAddress(), e);
            } finally {
                this.deleteLocalObjectLocks.unlock(message.subjectId);
            }
        }

        return message.composeReply(this.node.getNodeAddress(), DOLRStatus.TEMPORARY_REDIRECT);
    }

    public DOLRStatus deleteObject(BeehiveObjectId objectId, BeehiveObjectId profferedDeletionToken, long timeToLive)
    throws BeehiveObjectStore.NoSpaceException, IOException {
        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        BeehiveMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
        return reply.getStatus();
    }

    /**
     * Override this method...
     *
     * @param object The given DOLRObject for which an anti-object is to be constructed and returned.
     * @param profferedDeleteToken  The delete-token to use for the anti-object.
     * @return The anti-object of the given DOLRObject.
     * @throws IOException
     */
    public BeehiveObject createAntiObject(DeleteableObject.Handler.Object object , BeehiveObjectId profferedDeleteToken, long timeToLive)
    throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {
        FObjectType.FObject fObject = FObjectType.FObject.class.cast(object);

        this.log.info("%s", object.getObjectId());

        fObject.delete(profferedDeleteToken, timeToLive);
        return object;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        return new XHTML.Div("nothing here");
    }
}
