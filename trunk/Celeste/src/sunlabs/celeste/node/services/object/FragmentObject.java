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
package sunlabs.celeste.node.services.object;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.util.DOLRStatus;

public final class FragmentObject extends AbstractObjectHandler implements FObjectType {
    private final static long serialVersionUID = 1L;

    private final static String name = AbstractTitanService.makeName(FragmentObject.class, FragmentObject.serialVersionUID);
    private final static Integer defaultStoreAttempts = Integer.valueOf(100);

    private final static int replicationStore = 2;
    private final static int replicationCache = 2;

    public class FObject extends AbstractTitanObject implements FObjectType.FObject {
        private static final long serialVersionUID = 1L;

        private int replicationMinimum;
        private int replicationCache;

        private byte[] data;

        public FObject(TitanGuid deleteTokenId, long timeToLive, TitanObject.Metadata metaData, byte[] data, ReplicationParameters replicationParams) {
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
        public TitanGuid getDataId() {
            return new TitanGuidImpl(this.data);
        }

        public int getReplicationMinimum() {
            return this.replicationMinimum;
        }

        public int getReplicationCache() {
            return this.replicationCache;
        }

        public void delete(TitanGuid profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.DeleteTokenException {
            this.data = new byte[0];

            DeleteableObject.ObjectDeleteHelper(this, profferedDeleteToken, timeToLive);
            //this.setTimeToLive(timeToLive);
            //this.setProperty(ObjectStore.METADATA_DELETETOKEN, profferedDeleteToken);

            BeehiveObjectStore.CreateSignatureVerifiedObject(this.getObjectId(), this);
        }
    }

    // This is a per-object lock signaling that an object is undergoing the delete process.
    private ObjectLock<TitanGuid> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already
    // deleting the specified object.
    private ObjectLock<TitanGuid> deleteLocalObjectLocks;

    private int storeAttempts;

    public FragmentObject(TitanNode node) throws JMException {
        super(node, FragmentObject.name, "Fragment Object Application");
        this.storeAttempts = FragmentObject.defaultStoreAttempts;

        this.publishObjectDeleteLocks = new ObjectLock<TitanGuid>();
        this.deleteLocalObjectLocks = new ObjectLock<TitanGuid>();
    }

    public int getStoreAttempts() {
        return this.storeAttempts;
    }

    public void setStoreAttempts(int count) {
        this.storeAttempts = count;
    }

    public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message, FObjectType.FObject fObject)  throws ClassNotFoundException, ClassCastException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException,
    BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception, BeehiveObjectStore.InvalidObjectIdException, BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.Exception {
        Publish.PublishUnpublishResponse reply = StorableObject.storeLocalObject(this, fObject, message);
        return reply;
    }

    public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws BeehiveObjectStore.NotFoundException {
        return this.node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * A deleted FObject will return null.
     * </p>
     * @throws RemoteException 
     * @throws ClassCastException 
     */
    public FragmentObject.FObject retrieve(TitanGuid objectId)
    throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException, ClassCastException {
        FragmentObject.FObject object = RetrievableObject.retrieve(this, FragmentObject.FObject.class, objectId);
        return object;
    }

    public Publish.PublishUnpublishResponse publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassCastException, ClassNotFoundException {
        //
        // Handle deleted objects.
        //
        DeleteableObject.publishObjectHelper(this, request);

        AbstractObjectHandler.publishObjectBackup(this, request);

        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());

    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public FObjectType.FObject create(TitanGuid deleteTokenId, long timeToLive, ReplicationParameters replicationParams, TitanObject.Metadata metaData, byte[] data) {
        return new FObject(deleteTokenId, timeToLive, metaData, data, replicationParams);
    }

    public FObjectType.FObject storeObject(FObjectType.FObject fObject)
    throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {

        TitanObject object = StorableObject.storeObject(this, fObject);
        return (FObjectType.FObject) object;
    }

    public ObjectLock<TitanGuid> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    /**
     * This method is invoked as the result of receiving a deleteLocalObject
     * {@link TitanMessage} or the receipt of a {@link PublishObjectMessage} containing valid
     * delete information.
     * @throws BeehiveObjectStore.DeletedObjectException 
     * @throws TitanMessage.RemoteException 
     * @throws ClassCastException 
     * @throws BeehiveObjectStore.NoSpaceException
     */
    public DeleteableObject.Response deleteLocalObject(TitanMessage message, DeleteableObject.Request request) throws ClassNotFoundException,
    BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.ObjectExistenceException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectStore.NotFoundException {
        if (this.deleteLocalObjectLocks.trylock(message.subjectId)) {
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", message.subjectId);
            }
            try {
                return DeleteableObject.deleteLocalObject(this, request, message);
            } finally {
                this.deleteLocalObjectLocks.unlock(message.subjectId);
            }
        }
        // Can't get the lock on this object because it is already busy.  Assume that it will be successful.
        return new DeleteableObject.Response();
    }

    public DOLRStatus deleteObject(TitanGuid objectId, TitanGuid profferedDeletionToken, long timeToLive) throws BeehiveObjectStore.NoSpaceException {
        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        TitanMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
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
    public TitanObject createAntiObject(DeleteableObject.Handler.Object object , TitanGuid profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {
        FObjectType.FObject fObject = FObjectType.FObject.class.cast(object);

        this.log.info("%s", object.getObjectId());

        fObject.delete(profferedDeleteToken, timeToLive);
        return object;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        return new XHTML.Div("nothing here");
    }
}
