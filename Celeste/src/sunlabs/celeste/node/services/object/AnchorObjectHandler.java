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
import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.services.AObjectVersionService;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanObjectStoreImpl.NotFoundException;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.MutableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.objectstore.PublishDaemon;
import sunlabs.titan.util.DOLRStatus;

public final class AnchorObjectHandler extends AbstractObjectHandler implements AnchorObject.Handler {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(AnchorObjectHandler.class, AnchorObjectHandler.serialVersionUID);

    private final static int defaultReplicationStore = 3;
    private final static int defaultReplicationLowWater = 3;

    /**
     * This class represents the top-level data structure
     * (stored in the Beehive object pool) for a stored Celeste file.
     */
    public static final class AObject extends AbstractTitanObject implements AnchorObject.Object {
        private static final long serialVersionUID = 1L;

        /**
         * A file's Version is the combination of generationId and serial-number.
         */
        public static class Version implements AnchorObject.Object.Version {
            private final static long serialVersionUID = 1L;

            private final TitanGuid generationId;
            private final long serialNumber;

            protected Version(TitanGuid generationId, long serialNumber) {
                this.generationId = generationId;
                this.serialNumber = serialNumber;
            }

            public Version(String string) {
                if (string == null)
                    throw new IllegalArgumentException("AObject version cannot be null");
                String[] tokens = string.split("/");
                this.generationId = tokens[0].equals("null") ? null : new TitanGuidImpl(tokens[0]);
                this.serialNumber = Long.parseLong(tokens[1]);
            }

            public TitanGuid getGeneration() {
                return this.generationId;
            }

            public long getSerialNumber() {
                return this.serialNumber;
            }

            @Override
            public boolean equals(java.lang.Object other) {
                if (other == null)
                    return false;
                if (!(other instanceof Version))
                    return false;
                if (this.compare((Version) other) != 0)
                    return false;
                return true;
            }

            /**
             * Compare this Version with another, returning 0 if equivalent.
             *
             * When comparing Versions, null is a wild-card and matches anything.
             */
            public int compare(AnchorObject.Object.Version other) {
                if (other == null)
                    return 0;

                if (other.getGeneration() == null) {
                    if (other.getSerialNumber() == this.serialNumber)
                        return 0;
                    return 1;
                }

                if (this.generationId != null) {
                    if (!this.generationId.equals(other.getGeneration())) {
                        return 1;
                    }
                    if (this.serialNumber != 0) {
                        if (this.serialNumber != other.getSerialNumber())
                            return 2;
                    }
                    return 0;
                }

                if (this.serialNumber != 0) {
                    if (this.serialNumber != other.getSerialNumber()) {
                        return 2;
                    }
                }

                return 0;
            }

            @Override
            public String toString() {
                return String.valueOf(this.generationId) + "/" + this.serialNumber;
            }

            public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
                return new XHTML.Span(new XHTML.Italic(String.valueOf(this.generationId) + " / " + Long.toString(this.serialNumber)));
            }
            
        }
        
        private ReplicationParameters replicationParams;
        private int bObjectSize;
        private final FileIdentifier fileIdentifier;
        private boolean signWrites;
        private AObjectVersionMapAPI.Parameters aObjectVersionMapParams;

        public AObject(FileIdentifier fileIdentifier, ReplicationParameters replicationParams,
                TitanGuid deleteTokenId, long timeToLive, int bObjectSize, boolean signWrites) {
            super(AnchorObjectHandler.class, deleteTokenId, timeToLive);

            this.replicationParams = replicationParams;

            this.setProperty(TitanObjectStore.METADATA_REPLICATION_STORE,
                    replicationParams.getAsInteger(AnchorObject.Object.REPLICATIONPARAM_STORE_NAME, AnchorObjectHandler.defaultReplicationStore));
            this.setProperty(TitanObjectStore.METADATA_REPLICATION_LOWWATER,
                    replicationParams.getAsInteger(AnchorObject.Object.REPLICATIONPARAM_LOWWATER_NAME, AnchorObjectHandler.defaultReplicationLowWater));

            this.bObjectSize = bObjectSize;

            this.fileIdentifier = fileIdentifier;
            this.objectId = fileIdentifier.getObjectId();
            this.signWrites = signWrites;
        }

        @Override
        public TitanGuid getDataId() {
            return new TitanGuidImpl(Integer.toString(this.bObjectSize).getBytes()).add(this.fileIdentifier.getObjectId());
        }

        public void delete(TitanGuid profferedDeleteToken, long timeToLive) throws TitanObjectStoreImpl.DeleteTokenException {
            DeleteableObject.ObjectDeleteHelper(this, profferedDeleteToken, timeToLive);
        }

        public ReplicationParameters getReplicationParameters() {
            return this.replicationParams;
        }

        public int getBObjectSize() {
            return this.bObjectSize;
        }

        public boolean getSignWrites() {
            return this.signWrites;
        }

        public void setAObjectVersionMapParams(AObjectVersionMapAPI.Parameters params) {
            this.aObjectVersionMapParams = params;
        }

        public AObjectVersionMapAPI.Parameters getAObjectVersionMapParams() {
            return this.aObjectVersionMapParams;
        }
        
        public String toString() {
        	StringBuilder result = new StringBuilder();
        	result.append("AObject ").append(this.getObjectId());
        	return result.toString();
        }

        public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props) {

            XHTML.Table.Body tbody = new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data("File"),      new XHTML.Table.Data(this.fileIdentifier)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Object Id"),    new XHTML.Table.Data(this.getObjectId())),
                    new XHTML.Table.Row(new XHTML.Table.Data("Replication Parameters"), new XHTML.Table.Data(this.replicationParams)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Block Object Size"), new XHTML.Table.Data("%d", this.getBObjectSize())),
                    new XHTML.Table.Row(new XHTML.Table.Data("AnchorObject->VersionObject"), new XHTML.Table.Data(this.getAObjectVersionMapParams().toXHTMLTable()))
            );
            
            XHTML.Div result = (XHTML.Div) super.toXHTML(uri, props);
            result.add(new XHTML.Div(new XHTML.Table(new XHTML.Table.Caption("Anchor Object"), tbody)).setClass("section").addClass("AnchorObject"));
            
            return result;
        }
    }

    // This is a per-object lock signaling that an object is undergoing the delete process.
    private ObjectLock<TitanGuid> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already deleting the specified object.
    private ObjectLock<TitanGuid> deleteLocalObjectLocks;

    public AnchorObjectHandler(TitanNode node) throws JMException {
        super(node, AnchorObjectHandler.name, "Celeste Anchor Object Handler");
        this.publishObjectDeleteLocks = new ObjectLock<TitanGuid>();
        this.deleteLocalObjectLocks = new ObjectLock<TitanGuid>();
    }

    /**
     * Given a generation-id (as a {@link TitanGuid} and a serial-number,
     * generate and return a {@link AnchorObject.Object}.
     */
    public AnchorObject.Object.Version makeVersion(TitanGuid generationId, long serialNumber) {
        return new AObject.Version(generationId, serialNumber);
    }

    public Publish.PublishUnpublishResponse publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassNotFoundException, ClassCastException {
        if (message.isTraced()) {
            this.log.finest("recv: %s", message.traceReport());
        }
        //            Publish.PublishUnpublishRequest request = message.getPayload(Publish.PublishUnpublishRequest.class, this.node);

        // Handle deleted objects.
        DeleteableObject.publishObjectHelper(this, request);

        AbstractObjectHandler.publishObjectBackup(this, request);

        // Reply signifying success, the value of the Publish.Response() is inconsequential.
        // See the storeObject() method below.
        Publish.PublishUnpublishResponse result =
            new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress(), new HashSet<TitanGuid>(request.getObjects().keySet()));

        //        if (message.isTraced()) {
        //            this.log.finest("reply %s %s", result.getStatus(), result.traceReport());
        //        }
        //        result.setTraced(message.isTraced());

        return result;    
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassNotFoundException, ClassCastException {
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", request);
        }
        ReplicatableObject.unpublishObjectRootHelper(this, request);

        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }   

    public AnchorObject.Object create(FileIdentifier fileIdentifier,
            ReplicationParameters replicationParams,
            TitanGuid deleteTokenId,
            long timeToLive,
            int bObjectSize,
            boolean signWrites) {
        return new AObject(fileIdentifier, replicationParams, deleteTokenId, timeToLive, bObjectSize, signWrites);
    }

    public DOLRStatus delete(FileIdentifier fileIdentifier, TitanGuid deletionToken, long timeToLive)
    throws IOException, TitanObjectStoreImpl.NoSpaceException, ClassCastException, TitanMessage.RemoteException, TitanObjectStoreImpl.DeleteTokenException, ClassNotFoundException {
        return this.deleteObject(fileIdentifier.getObjectId(), deletionToken, timeToLive);
    }

    public AnchorObject.Object retrieve(FileIdentifier fileIdentifier) throws TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NotFoundException, ClassCastException, ClassNotFoundException {
        AnchorObject.Object aObject = this.retrieve(fileIdentifier.getObjectId());
        return aObject;
    }

    public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws TitanObjectStoreImpl.NotFoundException {
        return node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    public AObject retrieve(TitanGuid objectId) throws ClassCastException, ClassNotFoundException, TitanObjectStoreImpl.NotFoundException, TitanObjectStoreImpl.DeletedObjectException, ClassCastException {
        return RetrievableObject.retrieve(this, AObject.class, objectId);
    }

    public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message, AnchorObject.Object aObject) throws ClassNotFoundException, ClassCastException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.DeleteTokenException,
    TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, TitanObjectStoreImpl.InvalidObjectIdException, TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.Exception {
        if (this.log.isLoggable(Level.FINER)) {
            this.log.finest("%s", message.traceReport());
        }
        Publish.PublishUnpublishResponse reply = StorableObject.storeLocalObject(this, aObject, message);
        return reply;
    }

    public AnchorObject.Object storeObject(AnchorObject.Object aObject)
    throws IOException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.DeleteTokenException, TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, ClassCastException, ClassNotFoundException {

        aObject = (AnchorObject.Object) TitanObjectStoreImpl.CreateSignatureVerifiedObject(aObject.getObjectId(), aObject);

        return (AnchorObject.Object) StorableObject.storeObject(this, aObject);
    }

    public DeleteableObject.Response deleteLocalObject(TitanMessage message, DeleteableObject.Request request) throws ClassNotFoundException, TitanObjectStoreImpl.DeleteTokenException,
    TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException,
    TitanObjectStoreImpl.UnacceptableObjectException, NotFoundException {
        if (this.deleteLocalObjectLocks.trylock(message.subjectId)) {
            try {
                if (this.log.isLoggable(Level.FINER)) {
                    this.log.finest("%s", message.traceReport());
                }
                return DeleteableObject.deleteLocalObject(this, request, message);
            } finally {
                this.deleteLocalObjectLocks.unlock(message.subjectId);
            }
        }

        return new DeleteableObject.Response();
    }

    public DOLRStatus deleteObject(TitanGuid objectId, TitanGuid profferedDeletionToken, long timeToLive)
    throws TitanObjectStoreImpl.NoSpaceException, ClassNotFoundException, ClassCastException, TitanMessage.RemoteException, TitanObjectStoreImpl.DeleteTokenException {

        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", objectId.toString());
        }

        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        TitanMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
        reply.getPayload(Serializable.class, this.node);
        return reply.getStatus();
    }

    public TitanObject createAntiObject(DeleteableObject.Handler.Object object, TitanGuid profferedDeleteToken, long timeToLive)  throws TitanObjectStoreImpl.NoSpaceException,
    TitanObjectStoreImpl.DeleteTokenException {

        AnchorObject.Object aObject = AnchorObject.Object.class.cast(object);

        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", aObject.getObjectId());
        }

        // We have the AObject - start the deletion sequence (unless it was
        // already started)  If we couldn't delete the VObject, we can't
        // proceed with the deletion sequence. If we are the original deleter,
        // whoever called invoke(...) will get the error code back, and will
        // have to try again later. If we are not the original deleter, we will
        // try deleting the AObject again when we get a message from the root
        // to do so
        // XXX SHOULD MARK THE AOBJECT AS DELETED, BUT NOT USING deletedObjs!
        // XXX (EXPOSE TOKEN AND CHANGE APPLICATION NAME?)

        try {
            AObjectVersionMapAPI aObjectVersionHandler = this.node.getService(AObjectVersionService.class);

            VersionObject.Object.Reference vObjectReference = aObjectVersionHandler.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams()).getReference();

            // Since right now everything works in sequence (and not in parallel), when
            // we delete a version, the return value actually represents the result of
            // the last operation in the sequence (I.e., the deletion of the earliest version
            // reachable). If we get an error message on some early version, we don't mind,
            // since we stored the deletion objects and it will trigger the process again.
            // However, we cannot continue with the sequence if the next version is
            // unavailable. Right now, there's no mechanism to differentiate between the
            // return values, meaning that if we get a NOT_FOUND value, we can't really tell
            // what version wasn't found. So, we check to see if we can reach the next
            // version, and thus if we get NOT_FOUND when executing the deletion sequence,
            // we assume that it's for an earlier version and ignore it.
            // XXX THIS IS NOT A GOOD WAY OF ACHIEVING THIS GOAL, AS IT IS NOT ATOMIC

            VersionObject vObjectHandler = this.node.getService(VersionObjectHandler.class);
            vObjectHandler.deleteObject(vObjectReference.getObjectId(), profferedDeleteToken, timeToLive);

            aObject.delete(profferedDeleteToken, timeToLive);
            try {
                aObjectVersionHandler.deleteValue(aObject.getObjectId(), profferedDeleteToken, aObject.getAObjectVersionMapParams(), timeToLive);
            } catch (MutableObject.DeletedException e) {
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("Harmeless: FileVersionMap already deleted for %s", aObject.getObjectId());
                }
            }

            return aObject;
        } catch (MutableObject.InsufficientResourcesException e) {
            e.printStackTrace();
        } catch (MutableObject.NotFoundException e) {
            e.printStackTrace();
        } catch (MutableObject.ProtocolException e) {
            e.printStackTrace();
        } catch (MutableObject.ExistenceException e) {
            e.printStackTrace();
        } catch (MutableObject.PredicatedValueException e) {
            e.printStackTrace();
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }

    public ObjectLock<TitanGuid> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    public ReplicatableObject.Replicate.Response replicateObject(TitanMessage message) throws ClassNotFoundException, ClassCastException,
    TitanObjectStoreImpl.NotFoundException, TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception {
        try {
            ReplicatableObject.Replicate.Request request = message.getPayload(ReplicatableObject.Replicate.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("replicate %s", request.getObjectId());
            }
            AnchorObject.Object aObject = this.retrieve(request.getObjectId());

            // XXX It would be good to have the Request contain a Map and not a Set, and then just use keySet().
            // XXX Why do this at all, just use the set.
            Set<TitanNodeId> excludeNodes = new HashSet<TitanNodeId>();
            for (TitanNodeId publisher : request.getExcludedNodes()) {
                excludeNodes.add(publisher);                
            }
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("excluding %s", excludeNodes);
            }

            aObject.setProperty(TitanObjectStore.METADATA_SECONDSTOLIVE, aObject.getRemainingSecondsToLive(Time.currentTimeInSeconds()));

            StorableObject.storeObject(this, aObject, 1, excludeNodes, null);

            return new ReplicatableObject.Replicate.Response();
        } catch (TitanMessage.RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }
}
