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
package sunlabs.celeste.node.services;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XHTML.EFlow;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.celeste.client.operation.LockFileOperation;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject.Metadata;
import sunlabs.titan.node.AbstractBeehiveObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveObjectStore.InvalidObjectException;
import sunlabs.titan.node.BeehiveObjectStore.NoSpaceException;
import sunlabs.titan.node.Publishers.PublishRecord;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.MutableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.util.DOLRStatus;

/**
 * An implementation and adaptation of
 * <ul>
 * <li>[1] "'Fault-Scalable Byzantine Fault-Tolerant Services",
 * Micheal Abd-El-Malek, Gregory R. Ganger, Garth R. Goodson, Michael K Reiter, Jay J. Wylie.
 * </li>
 * <li>
 * [2] "Correctness of the Read/Conditional-Write and Query/Update protocols",
 * Micheal Abd-El-Malek, Gregory R. Ganger, Garth R. Goodson, Michael K Reiter, Jay J. Wylie.
 * CMU-PDL-05-107
 * </li>
 * </ul>
 * <b>This is an out-of-date description</b>
 * <p>
 * A difference between the system described in [1] and [2], is that in the Beehive DOLR environment,
 * the set of servers is dynamic instead of fixed.
 * In the Beehive DOLR servers are initially located by using non-specific object-id identifiers for
 * servers.
 * Because of the way the Beehive message routing works, the ssytem will always find the node with the
 * object-id closest to the identifier (in DHT algebra).
 * Once located, the node that responds is bound to the set of servers participating in the Q/U protocol quorum.
 * If any server participating in the Q/U protocol quorum becomes unavailable,
 * another server will be automatically located to participate in the missing server's stead.
 * Because the new server is necessarily not up-to-date, the Q/U protocol described in [1] and [2]
 * is modified to bind the replacement server into the set of servers participating in the Q/U protocol.
 *
 * Every Linearizer must always be suspicious that there is another set of nodes,
 * running in some partitioned portion of the network which is also acting as a
 * Linearizer for the same object Id.
 * In anticipation of disambiguating multiple Linearizers, each set, or universe,
 * possesses a <q>Generation</q> number, derived from the set of servers currnetly.
 * </p>
 * <p>
 * While we can always find a node to take on the role of a server,
 * we are not guaranteed that this node previously participated in Linearizer activity for a specific object-id.
 * A node can take on three states in relation to its involvment in Linearizer activity for an object-id.
 * </p>
 * <ul>
 * <li>New: the node has no previous Linearizer information for the object-id.
 *   If the classification of the ObjectHistorySet is BARRIER and an inline repair can be made,
 *   then the repairing server(s) simply set the object history as dictated by the ObjectHistorySet.
 *   Otherwise, because of the suspicion that there is another Linearizer,
 *   a new generation number needs to be created and a new version of the object is created.
 * </li>
 * <li>Current: the generation number matches the generation number of all other members of the Linearizer.
 *   The node has a history which can be used to ensure the proper advancement of the linearised object.
 *   This is the normal expectations of the environment in [1] and [2].</li>
 * <li>Reunited: the generation number does not match the generation number of all other members of the Linearizer.
 *   The history of the mismatched node contains information about the branched history of the linearised object.
 *   This information needs to be recorded, and the node needs to be made up-to-date according to the protocol in [1] and [2].
 *   (What happens if, for example, every node has a different generation number?)
 *   A new generation number needs to be generated.
 * </li>
 * </ul>
 * <p>
 * It is possible to loose information about the branched history of an object if,
 * for example, every node that was previously participating in a Linearizer for an object were replaced,
 * leaving only New nodes which would have no previous information about the object's value.
 * </p>
 */
public class AObjectVersionService extends AbstractObjectHandler implements AObjectVersionMapAPI {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(AObjectVersionService.class, AObjectVersionService.serialVersionUID);

    /**
     * A lock on a Celeste file.
     *
     */
    public static class Lock implements AObjectVersionMapAPI.Lock {
        private static final long serialVersionUID = 1L;
        
        private int referenceCount;
        private TitanGuid lockerId;
        private String token;
        private Serializable annotation;
        private LockFileOperation.Type type;

        public Lock(LockFileOperation.Type type, TitanGuid lockerId, int referenceCount, String token, Serializable annotation) {
            this.type = type;
            this.lockerId = lockerId;
            this.token = token;
            this.referenceCount = referenceCount;
            this.annotation = annotation;
        }

        public Serializable getClientAnnotation() {
            return this.annotation;
        }

        public int getLockCount() {
            return this.referenceCount;
        }
        
        public String getToken() {
            return this.token;
        }

        public TitanGuid getLockerObjectId() {
            return this.lockerId;
        }
        
        public String toString() {
            return String.format("%s %s %s %d", this.type, this.lockerId, this.token, this.referenceCount);
        }        

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (other == null)
                return false;
            if (!(other instanceof Lock))
                return false;

            Lock otherLock = (Lock) other;
            
            if (this.type != otherLock.type)
                return false;
            
            if (this.referenceCount != otherLock.referenceCount)
                return false;
            if (this.annotation == null) {
                if (otherLock.annotation != null)
                    return false;
            } else if (!this.annotation.equals(otherLock.annotation)) {
                return false;
            }
            
            if (this.lockerId == null) {
                if (otherLock.lockerId != null)
                    return false;
            } else if (!this.lockerId.equals(otherLock.lockerId)) {
                return false;
            }
          
            return true;
        }

        public LockFileOperation.Type getType() {
          return this.type;
        }
        
    }
    /**
     * This is the value stored in the AObjectVersionService.
     */
    public static class Value extends AObjectVersionMapAPI.Value {
        private static final long serialVersionUID = 1L;

        private VersionObject.Object.Reference reference;
        private AObjectVersionMapAPI.Lock lock;

        public Value(VersionObject.Object.Reference reference, AObjectVersionMapAPI.Lock lock) {
            super();
            this.reference = reference;
            this.lock = lock;
        }
        
        public Value clone() {
            return new Value(this.reference, this.lock);
        }

        public VersionObject.Object.Reference getReference() {
            return this.reference;
        }
        
        public AObjectVersionMapAPI.Lock getLock() {
            return this.lock;
        }

        public String format() {
            if (this.lock == null)
                return String.format("%s", this.reference);
            
            return String.format("%s %s", this.reference, this.lock.toString());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (other == null)
                return false;
            if (other instanceof AObjectVersionService.Value) {
                AObjectVersionService.Value o = (AObjectVersionService.Value) other;

                if (!this.reference.equals(o.reference)) {
                    return false;
                }

                if (this.lock != null) {
                    if (!this.lock.equals(o.lock))
                        return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s locker=%s", this.reference, this.lock);
        }

		public EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
			XHTML.Table.Body tbody = new XHTML.Table.Body();
			if (this.getDeleteToken() != null)
			    tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("DeletionInfo"), new XHTML.Table.Data("%s %ds", this.getDeleteToken(), this.getDeletedSecondsToLive())));
			tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Reference"), new XHTML.Table.Data(this.reference.toXHTML(uri, props))));
			tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Locker"), new XHTML.Table.Data(this.lock == null ? "none" : this.lock.toString())));
			
			return new XHTML.Table(tbody);
		}
    }

    /**
     * A Fault-Scalable Byzantine Fault-Tolerant Object
     *
     * @author Glenn Scott - Sun Microsystems Laboratories
     */
    public static class FSBFTObject extends AbstractBeehiveObject implements AObjectVersionMapAPI.ObjectAPI {
        private static final long serialVersionUID = 1L;

        private MutableObject.ObjectHistory history;

        /**
         * Construct a Fault-Scalable Byzantine Fault-Tolerant Object replica.
         * <p>
         * The system may have only one of this object.  Any attempt to store
         * another object with this object-id MUST fail.
         * </p>
         * @param history
         * @param timeToLive
         * @param deleteToken
         */
        public FSBFTObject(MutableObject.ObjectHistory history, long timeToLive, TitanGuid deleteTokenId) {
            super(AObjectVersionService.class, deleteTokenId, timeToLive);
            this.history = history;
            this.setProperty(ObjectStore.METADATA_REPLICATION_STORE, 1);
            this.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER, 1);
            this.setProperty(ObjectStore.METADATA_REPLICATION_HIGHWATER, 1);
        }

        @Override
        public TitanGuid getDataId() {
            return new TitanGuidImpl("always the same".getBytes());
        }

        public void setObjectHistory(MutableObject.ObjectHistory history) {
            this.history = history;
        }

        public MutableObject.ObjectHistory getObjectHistory() {
            return this.history;
        }

		public EFlow inspectAsXHTML(URI uri, Map<String, HTTP.Message> props) {
        	XHTML.Table.Body tbody = new XHTML.Table.Body();
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(this.history.toXHTML(uri, props))));
        	
        	XHTML.Table table = new XHTML.Table(new XHTML.Table.Caption("History"), tbody);

            XHTML.Div result = super.toXHTML(uri, props);
            result.add(new XHTML.Div(table).setClass("section").addClass("FSBFTObject"));

            return result;
		}
    }

    public static class Parameters extends MutableObject.Params implements AObjectVersionMapAPI.Parameters {
        private final static long serialVersionUID = 1L;

        public Parameters(String parameterSpec) {
            super(parameterSpec);
        }
    }

    public AObjectVersionService(TitanNode node) throws JMException {
        super(node, AObjectVersionService.name, "AObject Version Service");
    }

    public AObjectVersionMapAPI.Parameters createAObjectVersionMapParams(String parameterSpec) {
        return new Parameters(parameterSpec);
    }

    public void createValue(TitanGuid objectId, TitanGuid deleteTokenId, AObjectVersionMapAPI.Parameters params, long timeToLive) throws
    MutableObject.InsufficientResourcesException, MutableObject.ExistenceException, MutableObject.ProtocolException {
        MutableObject.createValue(this, new MutableObject.ObjectId(objectId), deleteTokenId, params, timeToLive);
    }    

    public void deleteValue(TitanGuid objectId, TitanGuid deleteToken, AObjectVersionMapAPI.Parameters params, long timeToLive)
    throws MutableObject.InsufficientResourcesException, MutableObject.ExistenceException, MutableObject.ProtocolException, MutableObject.NotFoundException, 
    MutableObject.PredicatedValueException, MutableObject.ObjectHistory.ValidationException, MutableObject.DeletedException {
        MutableObject.deleteValue(this, new MutableObject.ObjectId(objectId), deleteToken, params, timeToLive);
    }

    /**
     * Create an initial {@link MutableObject.ObjectHistory} replica on this node.
     * If the replica already exists, do not replace it and return an error.
     *
     * DOLRStatus.OK                    ObjectHistory accepted
     * DOLRStatus.CONFLICT              ObjectHistory already exists and has
     *                                  history in it.
     * DOLRStatus.NOT_ACCEPTABLE        ObjectHistory is rejected by the
     *                                  ObjectStore as UnacceptableObject,
     *                                  InvalidObjectIdException,
     *                                  DeleteTokenException
     * DOLRStatus.INTERNAL_SERVER_ERROR ClassNotFoundException, IOException
     *
     * @param message
     */
    public TitanMessage createObjectHistory(TitanMessage message) {
        try {
            // The message contains a CreateOperation.Request as the payload.
            MutableObject.CreateOperation.Request request = message.getPayload(MutableObject.CreateOperation.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.info("%s", request.getReplicaId());
            }

            // Construct an storable object history object.
            MutableObject.ObjectHistory initialObjectHistory = new MutableObject.ObjectHistory(request.getObjectId(), request.getReplicaId());
            initialObjectHistory.add(new MutableObject.TimeStamp());
            AObjectVersionService.FSBFTObject object = new AObjectVersionService.FSBFTObject(initialObjectHistory, request.getTimeToLive(),  request.getDeleteTokenId());

            try {
                BeehiveObjectStore.CreateSignatureVerifiedObject(request.getReplicaId(), object);
                try {
                    TitanGuid objectId = AObjectVersionService.this.node.getObjectStore().create(object);
                    AObjectVersionService.this.node.getObjectStore().get(AObjectVersionService.FSBFTObject.class, objectId);

                    assert (objectId.equals(request.getReplicaId()));

                    return message.composeReply(this.getNode().getNodeAddress(), new MutableObject.CreateOperation.Response(initialObjectHistory));
                } catch (BeehiveObjectStore.ObjectExistenceException e) {
                    try {
                        object = AObjectVersionService.this.node.getObjectStore().getAndLock(AObjectVersionService.FSBFTObject.class, request.getReplicaId());
                        try {
                            if (!object.getObjectHistory().isInitial()) {
                                if (this.log.isLoggable(Level.INFO)) {
                                    this.log.info("Already exists. replicaId %5.5s...: %5.5s...", request.getReplicaId(), object.getObjectHistory().toString());
                                }
                                return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.CONFLICT);
                            }
                        } finally {
                            AObjectVersionService.this.node.getObjectStore().unlock(request.getReplicaId());
                        }
                        if (this.log.isLoggable(Level.FINE))
                            this.log.info("Already initialized. replicaId %s ", request.getReplicaId());
                        return message.composeReply(this.getNode().getNodeAddress(), new MutableObject.CreateOperation.Response(initialObjectHistory));
                    } catch (BeehiveObjectStore.NotFoundException notFound) {
                        // The object existed, now we can't find it.
                        notFound.printStackTrace();
                        return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
                    }
                } catch (NoSpaceException e) {
                    e.printStackTrace();
                    return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
                } catch (InvalidObjectException e) {
                    e.printStackTrace();
                    return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
                } catch (ClassCastException e) {
                    e.printStackTrace();
                    return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
                } catch (BeehiveObjectStore.NotFoundException e) {
                    e.printStackTrace();
                    return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
                }
            } catch (BeehiveObjectStore.UnacceptableObjectException e) {
                this.log.info("BeehiveObjectStore.UnacceptableObjectException %s", request.getReplicaId());
                return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.CONFLICT);
            } catch (BeehiveObjectStore.DeleteTokenException e) {
                e.printStackTrace();
                return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
        } catch (ClassCastException e) {
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.BAD_REQUEST);
        } catch (RemoteException e) {
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.BAD_REQUEST);
        }
    }

    /**
     * Get the specified {@link MutableObject.ObjectHistory} replica from this node.
     * Replica objectIds are a function of the original objectId
     * plus an integer all hashed to produce the replica objectId.
     *
     * <p>
     * If the named replica is not available from this node, the result status is {@link DOLRStatus#NOT_FOUND}.
     * </p>
     * <p>
     * The caller must figure out if the DOLRStatus.NOT_FOUND status requires a new
     * replica to be created (because some previous replica became unavailable).
     * </p>
     */
    public TitanMessage getObjectHistory(TitanMessage message) {
        try {
            MutableObject.GetOperation.Request request = message.getPayload(MutableObject.GetOperation.Request.class, this.node);
            try {
                TitanGuid replicaId = request.getReplicaId();
                AObjectVersionService.FSBFTObject linearizerObject =
                    AObjectVersionService.this.node.getObjectStore().get(AObjectVersionService.FSBFTObject.class, replicaId);

                MutableObject.GetOperation.Response response = new MutableObject.GetOperation.Response(linearizerObject.getObjectHistory());

                return message.composeReply(this.getNode().getNodeAddress(), response);
            } catch (ClassCastException e) {
                e.printStackTrace();
                return message.composeReply(this.getNode().getNodeAddress(), e);
            } catch (BeehiveObjectStore.NotFoundException e) {
                if (this.log.isLoggable(Level.FINE))
                    this.log.fine("Replica expected, but not found locally: replicaId=%s", request.getReplicaId());
                return message.composeReply(this.getNode().getNodeAddress(), e);
            }
        } catch (ClassNotFoundException e) {
            this.log.info("Malformed MutableObject.GetOperation.Request from node %s%n", message.getSource().format());
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), e);
        } catch (ClassCastException e) {
            this.log.info("Malformed MutableObject.GetOperation.Request from node %s%n", message.getSource().format());
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), e);
        } catch (RemoteException e) {
            this.log.info("Bad result from MutableObject.GetOperation.Request from node %s%n", message.getSource().format());
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), e);
        }
    }

    private void removeLocalObjectHistory(TitanGuid objectId) {
//        this.linearizedObjects.remove(objectId.toString());
    }

    public TitanMessage setObjectHistory(TitanMessage message) {
        try {
            MutableObject.SetOperation.Request request = message.getPayload(MutableObject.SetOperation.Request.class, this.node);
            //this.log.info("Update: %s ohsId=%s %s", message.subjectId, request.getObjectHistorySet().getHash(), request.getObjectHistorySet().state);
            
            // The request is inconsistent...
            if (!request.objectHistoryId.equals(request.getObjectHistorySet().getObjectId())) {
            	if (this.log.isLoggable(Level.SEVERE)) {
            		this.log.severe("Confused OHS replica=%s expected=%s vs %s", message.subjectId, request.objectHistoryId, request.getObjectHistorySet().getObjectId());
            		this.log.severe(request.toString());
            	}
            }
            AObjectVersionService.FSBFTObject object = null;
            try {
                object = AObjectVersionService.this.node.getObjectStore().getAndLock(AObjectVersionService.FSBFTObject.class, message.subjectId);
                MutableObject.ObjectHistory objectHistory = object.getObjectHistory();

                objectHistory.setValue(this.log, message.getSource().getObjectId(), request.getObjectHistorySet(), request.getValue());
                object.setObjectHistory(objectHistory);

                BeehiveObjectStore.CreateSignatureVerifiedObject(message.subjectId, object);
                TitanGuid objectId = AObjectVersionService.this.node.getObjectStore().update(object);

                assert(objectId.equals(message.subjectId));

                MutableObject.SetOperation.Response response = new MutableObject.SetOperation.Response(objectHistory);
                return message.composeReply(this.getNode().getNodeAddress(), response);
            } catch (ClassCastException e) {
            	if (this.log.isLoggable(Level.WARNING)) {
            		this.log.warning("Node %s sent request to set object %s which is NOT an AObjectVersionService.FSBFTObject instance.", message.getSource(),  message.subjectId);
            		e.printStackTrace();
            	}
            	return message.composeReply(this.getNode().getNodeAddress(), e);
            } finally {
                if (object != null)
                    try {
                        AObjectVersionService.this.node.getObjectStore().unlock(object);
                    } catch (sunlabs.titan.node.BeehiveObjectStore.Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (sunlabs.titan.node.BeehiveObjectPool.Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
            }
        } catch (ClassNotFoundException e) {
            return message.composeReply(this.getNode().getNodeAddress(), e);
        } catch (MutableObject.ObjectHistory.OutOfDateException failure) {
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.CONFLICT);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.BAD_REQUEST);
        } catch (BeehiveObjectStore.InvalidObjectException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.BAD_REQUEST);
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
        } catch (BeehiveObjectStore.NotFoundException e) {
            this.log.info("%s Not Found", message.subjectId);
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_FOUND);
        } catch (BeehiveObjectStore.NoSpaceException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
        } catch (IOException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), e);
        } catch (BeehiveObjectStore.ObjectExistenceException e) {
            e.printStackTrace();
            return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
        } catch (ClassCastException e) {
            return message.composeReply(this.getNode().getNodeAddress(), e);
        } catch (RemoteException e) {
            return message.composeReply(this.getNode().getNodeAddress(), e.getCause());
        }
    }

    public AObjectVersionMapAPI.Value getValue(TitanGuid objectId, AObjectVersionMapAPI.Parameters params)
    throws MutableObject.InsufficientResourcesException, MutableObject.NotFoundException, MutableObject.ProtocolException {
        return (AObjectVersionMapAPI.Value) MutableObject.getValue(this, new MutableObject.ObjectId(objectId), params);
    }

    public AObjectVersionMapAPI.Value setValue(TitanGuid objectId, AObjectVersionMapAPI.Value predicatedValue, AObjectVersionMapAPI.Value value,
            AObjectVersionMapAPI.Parameters params)
    throws MutableObject.PredicatedValueException, MutableObject.InsufficientResourcesException,
    		MutableObject.ObjectHistory.ValidationException, MutableObject.ProtocolException, MutableObject.DeletedException {
        if (this.log.isLoggable(Level.FINE))
            this.log.fine("%s %s -> %s", objectId, predicatedValue, value);
        return (AObjectVersionMapAPI.Value) MutableObject.setValue(this, new MutableObject.ObjectId(objectId), predicatedValue, value, params);
    }

    public AObjectVersionMapAPI.Value newValue(VersionObject.Object.Reference value, AObjectVersionMapAPI.Lock lock) {
        return new AObjectVersionService.Value(value, lock);
    }

    public AObjectVersionMapAPI.Value newValue(VersionObject.Object.Reference value) {
        return new AObjectVersionService.Value(value, null);
    }

    /**
     * A response of {@link DOLRStatus#CONFLICT} signifies that the published object is in conflict
     * with the permitted number of copies of that same object in the system.  The publisher
     * is obligated to not publish the object again, and all intermediate nodes are obligated
     * to remove any back-pointer to this object.
     */
    public Publish.PublishUnpublishResponse publishObject(TitanMessage message) throws ClassNotFoundException, ClassCastException, BeehiveObjectStore.ObjectExistenceException {
    	try {
    	    Publish.PublishUnpublishRequest publishRequest = message.getPayload(Publish.PublishUnpublishRequest.class, this.node);;
    		
            // Because the message may come from the root of a published object, in it's attempts make backup copies of the publish record,
            // the message.source can be different than the publisher encoded in the request.
    	    
            //
            // All of this is just to ensure there is only one of these objects in the pool at a time.
            // For each published object in the request...
    	    // Don't add a new object if it differs from the objects we already have with this object-id.
            for (Map.Entry<TitanGuid, Metadata> entry : publishRequest.getObjects().entrySet()) {
                Set<PublishRecord> alreadyPublishedObjects = this.node.getObjectPublishers().getPublishers(entry.getKey());
                if (alreadyPublishedObjects.size() >= 1) { // The number used here is the total number allowed in the system.
                    for (PublishRecord record : alreadyPublishedObjects) {
                        if (!record.getNodeId().equals(publishRequest.getPublisherAddress().getObjectId())) {
                            throw new BeehiveObjectStore.ObjectExistenceException();                            
                        }
                    }
                }
            }
            //
            // All of this is just to ensure there is only one of these objects in the pool at a time.
            // For each published object in the request...
//    		for (Map.Entry<TitanGuid, TitanObject.Metadata> entry : publishRequest.getObjects().entrySet()) {
//    			// This should be part of a new ReplicatedObject helper.
//    			Set<Publishers.PublishRecord> publisherSet = this.node.getObjectPublishers().getPublishersAndLock(entry.getKey());
//    			try {
//    				if (publisherSet != null) {
//    					// Only permit one of each AObjectVersionService.Object to exist in the system.
//    					// If the existing set of publishers of the published object exists and has one (or more) publishers already,
//    					// look through the publisher set to see if this publish message is from one of the existing publishers.
//    					// If so, then it's okay.  Otherwise, return a failure status.
//    					// Note that when a PublishObjectMessage results in a fail status,
//    					// all nodes are obligated to NOT store any backpointers and the publishing node is obligated to remove the object.
//    					if (publisherSet.size() >= 1) {
//    						for (Publishers.PublishRecord publisher : publisherSet) {
//    							//this.log.info("Existing publisher " + publisher.getNodeId() + " id=" + message.getObjectId().toString());
//    							if (!publisher.getNodeId().equals(publishRequest.getPublisherAddress().getObjectId())) {
//    								//this.log.info("Root sees too many copies of " + message.getObjectId().toString());
//    							    throw new BeehiveObjectStore.ObjectExistenceException();
////    								return message.composeReply(this.getNode().getNodeAddress(), DOLRStatus.CONFLICT, new BeehiveObjectStore.ObjectExistenceException());
//    							} else {
//    								//this.log.info("Republish " + message.getObjectId().toString() + " from " + message.getSource().getNodeId().toString());
//    							}
//    						}
//    					}
//    				}
//    			} finally {
//    				this.node.getObjectPublishers().unlock(entry.getKey());
//    			}
//    		}

    		AbstractObjectHandler.publishObjectBackup(this, publishRequest);
        } catch (RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }

        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage msg) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        String defaultKey =   "1111111111111111111111111111111111111111111111111111111111111111";
        String defaultValue = "5555555555555555555555555555555555555555555555555555555555555555";
        try {
            String action = HttpMessage.asString(props.get("action"), null);

//          for (Iterator<String> i = props.keySet().iterator(); i.hasNext(); /**/ ) {
//          String key = i.next();
//          System.out.println(key + "='" + HttpMessage.asString(props.get(key), "") + "'");
//          }

            if (action != null) {
                if (action.equals("set-config")) {
//                  this.logLevel.setValue(HttpMessage.asString(props.get("LoggerLevel"), this.log.getEffectiveLevel().toString()));
//                  this.log.setLevel(Level.parse(this.logLevel.getValueAsString()));
                    this.log.config("Set run-time parameters: loggerLevel=" + this.log.getEffectiveLevel().toString());
                } else if (action.equals("set")) {
                    TitanGuid objectId = new TitanGuidImpl(HttpMessage.asString(props.get("key"), defaultKey));
                    String value = HttpMessage.asString(props.get("value"), defaultValue);

                    MutableObject.Value predicatedValue = MutableObject.getValue(this, new MutableObject.ObjectId(objectId), new Parameters("1,1"));
                    this.log.info("get value %s", predicatedValue);
                    MutableObject.setValue(this, new MutableObject.ObjectId(objectId), predicatedValue, new MutableObject.GenericObjectValue(value), new Parameters("1,1"));
                } else if (action.equals("test-reset")) {
                    String key = HttpMessage.asString(props.get("key"), defaultKey);
                    this.ResetLocalHistory(new TitanGuidImpl(key));
//                } else if (action.equals("test-truncate")) {
//                    String key = HttpMessage.asString(props.get("key"), defaultKey);
//                    this.TruncateLocalHistory(new TitanGuid(key));
                } else if (action.equals("create")) {
                    TitanGuid objectId = new TitanGuidImpl(HttpMessage.asString(props.get("key"), defaultKey));
                    MutableObject.createValue(this, new MutableObject.ObjectId(objectId), new TitanGuidImpl("deleteMe".getBytes()), new Parameters("1,1"), Time.minutesInSeconds(5));
                } else if (action.equals("get")) {
                    TitanGuid objectId = new TitanGuidImpl(HttpMessage.asString(props.get("key"), defaultKey));

                    MutableObject.Value s = MutableObject.getValue(this, new MutableObject.ObjectId(objectId), new Parameters("1,1"));
                    XHTML.EFlow flow = new XHTML.Para(s.format());

                    XHTML.Div body = new XHTML.Div(new XHTML.Heading.H1("%s&nbsp;%s", AObjectVersionService.name, this.getNode().getNodeId()), flow);
                    return body;
                } else if (action.equals("test-ohs")) {
                    TitanGuid objectId = new TitanGuidImpl(HttpMessage.asString(props.get("key"), defaultKey));
                    MutableObject.ObjectHistorySet ohs = MutableObject.getObjectHistorySet(this, new Parameters("1,1"), new MutableObject.ObjectId(objectId));

                    XHTML.Div body = new XHTML.Div(
                            new XHTML.Heading.H1("%s&nbsp;%s", AObjectVersionService.name, this.getNode().getNodeId()),
                            new XHTML.Preformatted(ohs.toString()));
                    return body;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new XHTML.Para(e.toString());
        }

        XML.Attribute nameAction = new XML.Attr("name", "action");

        XHTML.Input set = Xxhtml.InputSubmit(
                nameAction,
                new XML.Attr("value", "set-config"),
                new XML.Attr("title", "Set Parameters"));

        XHTML.Input testCreate = Xxhtml.InputSubmit(
                nameAction,
                new XML.Attr("value", "create"),
                new XML.Attr("title", "Create"));

        XHTML.Input testGet = Xxhtml.InputSubmit(
                nameAction,
                new XML.Attr("value", "get"),
                new XML.Attr("title", "Get"));

        XHTML.Input testOHS = Xxhtml.InputSubmit(
                nameAction,
                new XML.Attr("value", "test-ohs"),
                new XML.Attr("title", "Test OHS"));

        XHTML.Input testWrite = Xxhtml.InputSubmit(
                nameAction,
                new XML.Attr("value", "set"),
                new XML.Attr("title", "Test Set"));

        XHTML.Input writeKey = Xxhtml.InputText(new XML.Attr("name", "key"),
                new XML.Attr("value", defaultKey),
                new XML.Attr("title", "Test Write"),
                new XML.Attr("size", "65"));

        XHTML.Input writeValue = Xxhtml.InputText(
                new XML.Attr("name", "value"),
                new XML.Attr("value", defaultValue),
                new XML.Attr("title", "Test Write"),
                new XML.Attr("size", "65")
        );

        XHTML.Form configuration = new XHTML.Form("").setMethod("get").setEncodingType("application/x-www-url-encoded")
        .add(new XHTML.Table(
                new XHTML.Table.Body(
                        new XHTML.Table.Row(new XHTML.Table.Data("Logging Level"), new XHTML.Table.Data(Xxhtml.selectJavaUtilLoggingLevel("LoggerLevel", this.log.getEffectiveLevel()))),
                        new XHTML.Table.Row(new XHTML.Table.Data("Set Configuration"), new XHTML.Table.Data(set)),
                        new XHTML.Table.Row(new XHTML.Table.Data("Key"), new XHTML.Table.Data(writeKey)),
                        new XHTML.Table.Row(new XHTML.Table.Data("Value"), new XHTML.Table.Data(writeValue)),
                        new XHTML.Table.Row(new XHTML.Table.Data(testCreate, testWrite, testGet, testOHS), new XHTML.Table.Data(""))
                )).setClass("controls"));

        XHTML.Table data = null;

        String logdata = null;
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(this.log.getLogfile(0));
            logdata = Xxhtml.inputStreamToString(fin);
            fin.close();
        } catch (IOException e) {
            logdata = e.toString();
        } finally {
            if (fin != null) try { fin.close(); } catch (IOException ignore) { }
        }
        XHTML.Table logfile = new XHTML.Table(new XHTML.Table.Caption("Logfile"),
                new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(new XHTML.Preformatted(new XML.Attr("class", "logfile")).add(logdata)))));

        XHTML.Div body = new XHTML.Div(
                new XHTML.Heading.H1(AObjectVersionService.name + " " + this.getNode().getNodeId()),
                new XHTML.Div(configuration).setClass("section"),
                new XHTML.Div(data).setClass("section"),
                new XHTML.Div(logfile).setClass("section"));
        return body;
    }

    /**
     * Reset the local ObjectHistory for the given key.
     * @param objectId
     */
    public void ResetLocalHistory(TitanGuid objectId) {
        this.removeLocalObjectHistory(objectId);
    }

//    /**
//     * Remove the last TimeStamp from this Node's history of the specified object.
//     *
//     * @param objectId
//     */
//    public void TruncateLocalHistory(TitanGuid objectId) {
//        MutableObject.ObjectHistory oldHistory = this.getLocalObjectHistory(objectId);
//        MutableObject.ObjectHistory newHistory =  new MutableObject.ObjectHistory(objectId, oldHistory.getServerObjectId());
//
//        TimeStamp u = null; // Good grief...
//        for (TimeStamp t : oldHistory) {
//            System.out.println(": " + t.toString());
//            if (u != null) {
//                newHistory.add(u);
//            }
//            u = t;
//        }
//        this.setLocalObjectHistory(objectId, newHistory);
//    }

    public static void main(String args[]) throws Exception {
        try {
            FSBFTObject l = new FSBFTObject(new MutableObject.ObjectHistory(), 10, new TitanGuidImpl("foo".getBytes()));
            ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream("/tmp/deletethis"));
            o.writeObject(l);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}
