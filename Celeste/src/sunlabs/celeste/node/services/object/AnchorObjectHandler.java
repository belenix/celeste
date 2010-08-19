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

package sunlabs.celeste.node.services.object;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.node.services.AObjectVersionService;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.node.AbstractBeehiveObject;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.BeehiveObjectStore.DeletedObjectException;
import sunlabs.titan.node.BeehiveObjectStore.NoSpaceException;
import sunlabs.titan.node.BeehiveObjectStore.NotFoundException;
import sunlabs.titan.node.BeehiveObjectStore.UnacceptableObjectException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.MutableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.BeehiveService;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.util.DOLRStatus;

public final class AnchorObjectHandler extends AbstractObjectHandler implements AnchorObject {
    private final static long serialVersionUID = 1L;
    private final static String name = BeehiveService.makeName(AnchorObjectHandler.class, AnchorObjectHandler.serialVersionUID);

    private final static int defaultReplicationStore = 3;
    private final static int defaultReplicationLowWater = 3;

    /**
     * This class represents the top-level data structure
     * (stored in the Beehive object pool) for a stored Celeste file.
     */
    public static final class AObject extends AbstractBeehiveObject implements AnchorObject.Object {
        private static final long serialVersionUID = 1L;

        /**
         * A file's Version is the combination of generationId and serial-number.
         */
        public static class Version implements AnchorObject.Object.Version {
            private final static long serialVersionUID = 1L;

            private final BeehiveObjectId generationId;
            private final long serialNumber;

            protected Version(BeehiveObjectId generationId, long serialNumber) {
                this.generationId = generationId;
                this.serialNumber = serialNumber;
            }

            public Version(String string) {
                if (string == null)
                    throw new IllegalArgumentException("AObject version cannot be null");
                String[] tokens = string.split("/");
                this.generationId = tokens[0].equals("null") ? null : new BeehiveObjectId(tokens[0]);
                this.serialNumber = Long.parseLong(tokens[1]);
            }

            public BeehiveObjectId getGeneration() {
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
                BeehiveObjectId deleteTokenId, long timeToLive, int bObjectSize, boolean signWrites) {
            super(AnchorObjectHandler.class, deleteTokenId, timeToLive);

            this.replicationParams = replicationParams;

            this.setProperty(ObjectStore.METADATA_REPLICATION_STORE,
                    replicationParams.getAsInteger(AnchorObject.Object.REPLICATIONPARAM_STORE_NAME, AnchorObjectHandler.defaultReplicationStore));
            this.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER,
                    replicationParams.getAsInteger(AnchorObject.Object.REPLICATIONPARAM_LOWWATER_NAME, AnchorObjectHandler.defaultReplicationLowWater));

            this.bObjectSize = bObjectSize;

            this.fileIdentifier = fileIdentifier;
            this.objectId = fileIdentifier.getObjectId();
            this.signWrites = signWrites;
        }

        @Override
        public BeehiveObjectId getDataId() {
            return new BeehiveObjectId(Integer.toString(this.bObjectSize).getBytes()).add(this.fileIdentifier.getObjectId());
        }

        public void delete(BeehiveObjectId profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.DeleteTokenException {
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
    private ObjectLock<BeehiveObjectId> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already deleting the specified object.
    private ObjectLock<BeehiveObjectId> deleteLocalObjectLocks;

    public AnchorObjectHandler(BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, AnchorObjectHandler.name, "Celeste Anchor Object Handler");
        this.publishObjectDeleteLocks = new ObjectLock<BeehiveObjectId>();
        this.deleteLocalObjectLocks = new ObjectLock<BeehiveObjectId>();
    }
    
    @Override
    public void setConfig() {
        // No configurable parameters here (yet).
    }

    /**
     * Given a generation-id (as a {@link BeehiveObjectId} and a serial-number,
     * generate and return a {@link AnchorObject.Object}.
     */
    public AnchorObject.Object.Version makeVersion(BeehiveObjectId generationId, long serialNumber) {
        return new AObject.Version(generationId, serialNumber);
    }

    public BeehiveMessage publishObject(BeehiveMessage message) {
        if (message.isTraced()) {
            this.log.finest("recv: %s", message.traceReport());
        }
        try {
        	PublishDaemon.PublishObject.Request publishRequest = message.getPayload(PublishDaemon.PublishObject.Request.class, this.node);

            // Handle deleted objects.
            DeleteableObject.publishObjectHelper(this, publishRequest);

            AbstractObjectHandler.publishObjectBackup(this, publishRequest);

            // Reply signifying success, the value of the Publish.Response() is inconsequential.
            // See the storeObject() method below.
            BeehiveMessage result = message.composeReply(this.node.getNodeAddress(), new PublishDaemon.PublishObject.Response(new HashSet<BeehiveObjectId>(publishRequest.getObjectsToPublish().keySet())));
//            BeehiveMessage result = message.composeReply(this.node.getNodeAddress(), new PublishDaemon.PublishObject.Response());

            if (message.isTraced()) {
                this.log.finest("reply %s %s", result.getStatus(), result.traceReport());
            }
            result.setTraced(message.isTraced());

            return result;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (ClassCastException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        }
    }

    public BeehiveMessage unpublishObject(BeehiveMessage message) {
        try {
            PublishDaemon.UnpublishObject.Request request = message.getPayload(PublishDaemon.UnpublishObject.Request.class, this.getNode());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s -> %s", request.getObjectIds(), message.getSource());
            }
            ReplicatableObject.unpublishObjectRootHelper(this, message);
            
            return message.composeReply(this.node.getNodeAddress());
        } catch (ClassCastException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        }
//        return message.composeReply(this.node.getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
    }   

    public AnchorObject.Object create(FileIdentifier fileIdentifier,
            ReplicationParameters replicationParams,
            BeehiveObjectId deleteTokenId,
            long timeToLive,
            int bObjectSize,
            boolean signWrites) {
        return new AObject(fileIdentifier, replicationParams, deleteTokenId, timeToLive, bObjectSize, signWrites);
    }

    public DOLRStatus delete(FileIdentifier fileIdentifier, BeehiveObjectId deletionToken, long timeToLive)
    throws IOException, BeehiveObjectStore.NoSpaceException {
        return this.deleteObject(fileIdentifier.getObjectId(), deletionToken, timeToLive);
    }

    public AnchorObject.Object retrieve(FileIdentifier fileIdentifier)
    throws BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException, ClassCastException {
        AnchorObject.Object aObject = this.retrieve(fileIdentifier.getObjectId());
        return aObject;
    }

    public BeehiveMessage retrieveLocalObject(BeehiveMessage message) {
        return RetrievableObject.retrieveLocalObject(this, message);
    }

    public AObject retrieve(BeehiveObjectId objectId)
    throws BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException, ClassCastException {
        return RetrievableObject.retrieve(this, AObject.class, objectId);
    }

    public BeehiveMessage storeLocalObject(BeehiveMessage message) {
        if (this.log.isLoggable(Level.FINER)) {
            this.log.finest("%s", message.traceReport());
        }
        try {
            AnchorObject.Object aObject = message.getPayload(AnchorObject.Object.class, this.node);
            BeehiveMessage reply = StorableObject.storeLocalObject(this, aObject, message);
            return reply;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(node.getNodeAddress(), DOLRStatus.BAD_REQUEST);
        } catch (ClassCastException e) {
            e.printStackTrace();
            return message.composeReply(node.getNodeAddress(), DOLRStatus.BAD_REQUEST);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(node.getNodeAddress(), DOLRStatus.BAD_REQUEST);
        }
    }

    public AnchorObject.Object storeObject(AnchorObject.Object aObject)
    throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception{

        aObject = (AnchorObject.Object) BeehiveObjectStore.CreateSignatureVerifiedObject(aObject.getObjectId(), aObject);

        return (AnchorObject.Object) StorableObject.storeObject(this, aObject);
    }

    public BeehiveMessage deleteLocalObject(BeehiveMessage message) throws ClassNotFoundException {
        if (this.deleteLocalObjectLocks.trylock(message.subjectId)) {
            try {
                if (this.log.isLoggable(Level.FINER)) {
                    this.log.finest("%s", message.traceReport());
                }
                return DeleteableObject.deleteLocalObject(this, message.getPayload(DeleteableObject.Request.class, this.node), message);
            } catch (ClassCastException e) {
                e.printStackTrace();
                return message.composeReply(node.getNodeAddress(), e);
            } catch (RemoteException e) {
                e.printStackTrace();
                return message.composeReply(node.getNodeAddress(), e);
            } finally {
                this.deleteLocalObjectLocks.unlock(message.subjectId);
            }
        }

        return message.composeReply(this.node.getNodeAddress());
    }

    public DOLRStatus deleteObject(BeehiveObjectId objectId, BeehiveObjectId profferedDeletionToken, long timeToLive)
    throws BeehiveObjectStore.NoSpaceException {

        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", objectId.toString());
        }

        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        BeehiveMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
        return reply.getStatus();
    }

    public BeehiveObject createAntiObject(DeleteableObject.Handler.Object object, BeehiveObjectId profferedDeleteToken, long timeToLive)
    throws IOException, ClassCastException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {

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
        }
        return null;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }

    public ObjectLock<BeehiveObjectId> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    public BeehiveMessage replicateObject(BeehiveMessage message) {
        try {
            ReplicatableObject.Replicate.Request request = message.getPayload(ReplicatableObject.Replicate.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("replicate %s", request.getObjectId());
            }
            AnchorObject.Object aObject = this.retrieve(request.getObjectId());

            // XXX It would be good to have the Request contain a Map and not a Set, and then just use keySet().
            Set<BeehiveObjectId> excludeNodes = new HashSet<BeehiveObjectId>();
            for (BeehiveObjectId publisher : request.getExcludedNodes()) {
                excludeNodes.add(publisher);                
            }
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("excluding %s", excludeNodes);
            }

            aObject.setProperty(ObjectStore.METADATA_SECONDSTOLIVE, aObject.getRemainingSecondsToLive(Time.currentTimeInSeconds()));
            
            StorableObject.storeObject(this, aObject, 1, excludeNodes, null);
            
            return message.composeReply(this.node.getNodeAddress());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (DeletedObjectException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (ClassCastException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (NoSpaceException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (IOException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (UnacceptableObjectException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (BeehiveObjectPool.Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        }

        return message.composeReply(this.node.getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
    }
}
