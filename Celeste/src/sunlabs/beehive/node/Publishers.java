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
package sunlabs.beehive.node;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.ObjectStore;
import sunlabs.beehive.node.object.DeleteableObject;
import sunlabs.beehive.node.services.WebDAVDaemon;

/**
 * Object location in the Beehive Object Pool.
 * <p>
 * Nodes that possess objects periodically publish the availability of each object by routing a
 * {@link PublishObjectMessage} containing the information about the object and the node that possesses it.
 * This message is routed through the system leaving behind <em>back-pointers</em> consisting of
 * the map <em>O&rarr;N</em> stored at each node along the route of the message.  It is
 * not necessary for each node along the route to store the mapping, but it is necessary for
 * the root node of each object to store the mapping.
 * The message ultimately arrives on a node that cannot route the PublishObjectMessage further.
 * That node is the <em>root</em> node of the published object's {@link BeehiveObjectId}, <em>O<sub>r</sub></em>.
 * </p>
 * <p>
 * Sending a message to an object is broken into two parts: A {@link BeehiveMessage.Type#RouteToNode}
 * message is transmitted from the originating node using the object-id of the target object as the destination.
 * The {@code BeehiveMessage.Type.RouteToObject} message traverses the system until it arrives on a node that
 * has a previously stored back-pointer <em>O&rarr;N</em>.
 * The node receiving the {@code RouteToObject} message composes a new {code BeehiveMessage.Type#RouteToNode}
 * message containing the payload of the original RouteToObjectMessage and transmits the new RouteToNodeMessage to node <em>N</em>.
 * </p>
 * <p>
 * Node <em>N</em> performs the operation indicated in the RouteToNodeMessage and replies.
 * The node containing the map <em>O&rarr;N</em> receives the reply and repackages it as the response
 * to the originally received RouteToObjectMessage.
 * </p>
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class Publishers {
    private final static long serialVersionUID = 1L;

    public final static String METADATA_PUBLISHERTTL = "Publishers.PublisherTimeToLive";

    private ObjectLock<BeehiveObjectId> locks;

    private BackedObjectMap<BeehiveObjectId,Set<Publishers.PublishRecord>> publishers;
    private Map<BeehiveObjectId, Set<Publishers.PublishRecord>> byObjectId;
    private BeehiveNode node;

    /**
     * A Publisher object is a record of a single binding of a {@link BeehiveObject} with a node advertising the object's
     * availability and the metadata that that node maintains for the published object. Nodes advertise the
     * availability of objects by emitting {@link PublishObjectMessage} messages which are ultimately received by the "root" of the object identifier. 
     * <p>
     * The record consists of:
     * <ul>
     * <li>The {@link NodeAddress} of the node possessing the object.</li>
     * <li>The {@link BeehiveObject.Metadata} of the object.</li>
     * <li>The radius that this record should be shared among the local nodes neighbours. (deprecated)</li>
     * </ul>
     * </p>
     */
    public static class PublishRecord implements Serializable {
        private final static long serialVersionUID = 1L;

        public static XHTML.Table.Heading[] toXHTMLTableHeading() {
        	XHTML.Table.Heading[] result = new XHTML.Table.Heading[4];
        	result[0] = new XHTML.Table.Heading("Node Identifier");
        	result[1] = new XHTML.Table.Heading("Expire(&Delta)");
        	result[2] = new XHTML.Table.Heading("Object TTL");
        	result[3] = new XHTML.Table.Heading("Object Type");
        	return result;            
        }
        
        private BeehiveObjectId objectId;
        private NodeAddress publisher;
        private BeehiveObject.Metadata metaData;
        /** The absolute time (in seconds) when this record must be removed (expires) */
        private long expireTimeSeconds;

        /**
         * Construct a PublishRecord instance binding the given {@link BeehiveObjectId} of an object
         * to the given {@link NodeAddress} of a node advertising its availability.
         *
         * @param objectId the {@link BeehiveObjectId} of the {@link BeehiveObject}
         * @param publisher the {@link NodeAddress} of the publishing {@link BeehiveNode}
         * @param metaData the complete {@link BeehiveObject.Metadata} of the published {@code BeehiveObject}
         * @param  recordSecondsToLive The system time, in seconds, when this record must be removed.
         */
        public PublishRecord(BeehiveObjectId objectId, NodeAddress publisher, BeehiveObject.Metadata metaData, long recordSecondsToLive) {
            this.objectId = objectId;
            this.publisher = publisher;
            this.metaData = metaData;
            this.expireTimeSeconds = Time.millisecondsInSeconds(System.currentTimeMillis()) + recordSecondsToLive;
            assert this.expireTimeSeconds > 0;
        }

        @Override
        public boolean equals(Object other) {
            // Two Publisher instances are equal if they have equal {@link BeehiveObjectId} and node-id {@link BeehiveObjectId} values.
            if (this == other)
                return true;

            if (other instanceof PublishRecord) {
                PublishRecord otherPublisher = (PublishRecord) other;
                return this.publisher.getObjectId().equals(otherPublisher.publisher.getObjectId()) && this.objectId.equals(otherPublisher.objectId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.publisher.getObjectId().hashCode();
        }

        /**
         * Get the {@link BeehiveObjectId} of the published object.
         */
        public BeehiveObjectId getObjectId() {
            return this.objectId;
        }

        /**
         * Get the {@link BeehiveObjectId} of the publishing node.
         */
        public BeehiveObjectId getNodeId() {
            return this.publisher.getObjectId();
        }

        public BeehiveObject.Metadata getMetadata() {
            return this.metaData;
        }

        /**
         * Get the {@link NodeAddress} of the publishing node.
         */
        public NodeAddress getNodeAddress() {
            return this.publisher;
        }

        public String getMetadataProperty(String name, String defaultValue) {
            return this.metaData.getProperty(name, defaultValue);
        }

        /**
         * Get the absolute time (in milliseconds) that this {@code PublishRecord} instance will expire.
         * @return
         */
        public long getExpireTimeSeconds() {
            return this.expireTimeSeconds;
        }

        /**
         * Get the {@link ObjectStore#METADATA_SECONDSTOLIVE} in the metadata for the published object
         */
        public long getObjectTTL() {
            return Long.parseLong(this.getMetadataProperty(ObjectStore.METADATA_SECONDSTOLIVE, "-1"));
        }

        public boolean isDeleted() {
            return DeleteableObject.deleteTokenIsValid(this.metaData);
        }

        public String getObjectType() {
            return this.metaData.getProperty(ObjectStore.METADATA_TYPE, null);
        }

        /**
         * Get the delete token from the object metadata send by this publisher.
         * Return null if the delete token is not present.
         *
         * A non-null value signifies that the publisher has the anti-object form of the object.
         */
        public String getDeleteToken() {
            return this.metaData.getProperty(ObjectStore.METADATA_DELETETOKEN, null);
        }

        @Override
        public String toString() {
        	return this.objectId + "->" + this.getNodeId() + " " + this.getExpireTimeSeconds() + " " + this.getObjectTTL() + " " + this.getObjectType();
        }

        public XHTML.Table.Data[] toXHTMLTableData() {
        	XHTML.Anchor a = WebDAVDaemon.inspectNodeXHTML(this.publisher);

        	String secondsToLive = (this.getObjectTTL() == BeehiveObject.INFINITE_TIME_TO_LIVE) ? "forever" : Long.toString(this.getObjectTTL());
        	String xmlClass = DeleteableObject.deleteTokenIsValid(this.metaData) ? "deleted" : "undeleted";
        	XHTML.Table.Data[] result = new XHTML.Table.Data[4];

        	result[0] = new XHTML.Table.Data(a).setClass(xmlClass);

        	result[1] = new XHTML.Table.Data("%d(%+ds)", this.getExpireTimeSeconds(), this.getExpireTimeSeconds() - Time.currentTimeInSeconds());
        	result[2] = new XHTML.Table.Data("%s", secondsToLive);
        	result[3] = new XHTML.Table.Data(WebDAVDaemon.inspectServiceXHTML(this.getObjectType()));
        	return result;
        }
    }

    public Publishers(BeehiveNode node, String spoolDirectory) throws IOException {
        this.node = node;

        this.byObjectId = Collections.synchronizedMap(new HashMap<BeehiveObjectId,Set<Publishers.PublishRecord>>());

        // Load from the backed map.
        this.publishers = new BackedObjectMap<BeehiveObjectId,Set<Publishers.PublishRecord>>(spoolDirectory + File.separatorChar + "object-publishers", true);
        
        for (BeehiveObjectId objectId : this.publishers.keySet()) {
            Set<Publishers.PublishRecord> publishers = this.getPublishersAndLock(objectId);
            this.byObjectId.put(objectId, publishers);
        }

        this.locks = new ObjectLock<BeehiveObjectId>();
    }

    /**
     * <p>
     * Get the {@link Set} of {@link Map<BeehiveObjectId,Publishers.Publisher>} instances (NodeId&rarr;PublishRecord) for the given {@link BeehiveObjectId}.
     * <em>There result is NOT locked.</em>
     * </p>
     * <p>
     * If there are no Publishers of the given {@code BeehiveObjectId}, return an empty {@code Set}.
     * </p>
     *
     * @param objectId the {@code BeehiveObjectId} of the published object.
     *
     * @return  a {@code Set} consisting of all the {@code Publishers.Publisher}
     * instances for the given {@code BeehiveObjectId}.
     */
    public Set<Publishers.PublishRecord> getPublishers(BeehiveObjectId objectId) {
        Set<Publishers.PublishRecord> set = this.getPublishersAndLock(objectId);
        try {
            return set;
        } finally {
            this.unlock(objectId);
        }
    }

    /**
     * <p>
     * Get the {@link Set} of {@link Publishers.PublishRecord} instances of the given {@link BeehiveObjectId}.
     * <em>There result is locked and MUST be unlocked when the caller has finished manipulating.</em>
     * </p>
     * <p>
     * If there are no Publishers of the given {@code BeehiveObjectId}, return an empty {@code Set}.
     * </p>
     * @param objectId the {@code BeehiveObjectId} of the published object.
     *
     * @return  a {@code Set} consisting of all the {@code Publishers.Publisher} instances for the given {@code BeehiveObjectId}.
     *
     * @throws IllegalStateException if an attempt to lock a {@code BeehiveObjectId}} more than once.
     */
    public Set<Publishers.PublishRecord> getPublishersAndLock(BeehiveObjectId objectId) throws IllegalStateException {
        this.locks.lock(objectId);
        try {
            Set<Publishers.PublishRecord> set = this.byObjectId.get(objectId);
            if (set == null) {
                set = new HashSet<Publishers.PublishRecord>();
            }
            return set;
        } catch (ClassCastException e) {
            this.locks.unlock(objectId);
            throw e;
        }
    }

    /**
     * Unlock the given {@link BeehiveObjectId}.
     * The {@code objectId} must already be locked by the current {@link Thread}.
     * @throws IllegalStateException if the {@code objectId} is not locked.
     */
    public boolean unlock(BeehiveObjectId objectId) {
        return this.locks.unlock(objectId);
    }

    /**
     * <p>
     * Replace the current Publisher information for the given {@code objectId} with the given {@code set}.
     * </p>
     * <p>
     * The publisher information <em>must</em> be locked by the caller of this method.
     * </p>
     * <p>
     * If the given {@code set} is empty, the Publisher information for {@code objectId} is removed (rather than storing the empty set).
     * </p>
     *
     * @param objectId the {@link BeehiveObjectId} of the object.
     * @param set the {@link Set} containing all of the publishers of {@code objectId}.
     * @throws IllegalStateException if the {@code objectId} is not locked.
     */
    public void put(BeehiveObjectId objectId, Set<Publishers.PublishRecord> set) {
        synchronized (this.locks) {
            this.locks.assertLock(objectId);

            if (set.size() != 0) {
                this.byObjectId.put(objectId, set);
                this.publishers.put(objectId, set);
            } else {
                this.byObjectId.remove(objectId);
                this.publishers.remove(objectId);
            }
        }
    }

    /**
     * Return a new {@link Set} containing the {@link BeehiveObjectId}s of the objects that this node has seen advertised.
     *  The returned {@code Set} is unique and not subject to {@link ConcurrentModificationException} from an updated publisher record.
     */
    public Set<BeehiveObjectId> keySet() {
        synchronized (this.byObjectId) {
            Set<BeehiveObjectId> set = new HashSet<BeehiveObjectId>(this.byObjectId.keySet());
            return set;
        }
    }

    /**
     * Update an existing, or create a new, {@link Publishers.PublishRecord} record from the given {@code Publisher} instance.
     * <p>
     * This method will block if the current Set of publisher records is locked.
     * </p>
     * <p>
     * Unlock the lock on this publisher object-id.
     * </p>
     */
    public void update(Publishers.PublishRecord publisher) {
        Set<Publishers.PublishRecord> oldSet = this.getPublishersAndLock(publisher.getObjectId());
        try {
        	oldSet.remove(publisher);
            if (oldSet.add(publisher)) {
                this.put(publisher.getObjectId(), oldSet);
            }
        } finally {
            this.unlock(publisher.getObjectId());
        }
    }
    
    public void update(BeehiveObjectId objectId, Publishers.PublishRecord record) {
    	// XXX Should ensure that objectId is the same as record.getObjectId()
    	this.update(record);
    }

    /**
     * Add all of the Publishers in the given {@code publisherSet}, to the set of Publishers that are
     * already maintained for the given {@code objectId}.  The result is the union of
     * the existing set of {@code Publishers.Publisher} instances and the given set of new instances.
     * <p>
     * This method will block if the current Set of publisher records is locked.
     * </p>
     * @param objectId
     * @param publisherSet
     */
    public void update(BeehiveObjectId objectId, Set<Publishers.PublishRecord> publisherSet) {
        Set<Publishers.PublishRecord> set = this.getPublishersAndLock(objectId);
        try {
            if (set.addAll(publisherSet)) {
                this.put(objectId, set);
            }
        } finally {
            this.unlock(objectId);
        }
    }

    /**
     * Remove the publisher record identified by the given {@code objectId} and {@code publisherId}.
     * <p>
     * This method will block if the current Set of publisher records is locked.
     * </p>
     * @param objectId
     * @param publisherId
     */
    public void remove(BeehiveObjectId objectId, BeehiveObjectId publisherId) {
        Set<Publishers.PublishRecord> newSet = new HashSet<Publishers.PublishRecord>();
        Set<Publishers.PublishRecord> set = this.getPublishersAndLock(objectId);
        try {
            for (PublishRecord p : set) {
                if (!publisherId.equals(p.getNodeId())) {
                    newSet.add(p);
                }
            }
            this.put(objectId, newSet);
        } finally {
            this.unlock(objectId);
        }
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        SortedSet<BeehiveObjectId> objectIds = new TreeSet<BeehiveObjectId>();
        objectIds.addAll(this.keySet());

        XHTML.Table.Data emptyCell = new XHTML.Table.Data();

        XHTML.Table.Head thead = new XHTML.Table.Head();
        thead.add(new XHTML.Table.Row(new XHTML.Table.Heading("Object Identifier")).add(PublishRecord.toXHTMLTableHeading()));
        
        XHTML.Table.Body tbody = new XHTML.Table.Body();
        for (BeehiveObjectId objectId : objectIds) {
            Set<Publishers.PublishRecord> publisherSet = this.getPublishers(objectId);

            XHTML.Anchor inspectButton = WebDAVDaemon.inspectObjectXHTML(objectId);
            XHTML.Table.Data objectCell = new XHTML.Table.Data(inspectButton);
            if (this.node.getNeighbourMap().isRoot(objectId)) {
                objectCell.setClass("root");
            }

            for (PublishRecord publisher : publisherSet) {
                XHTML.Anchor publisherLink = WebDAVDaemon.inspectNodeXHTML(publisher.getNodeAddress());
                if (publisher.isDeleted()) {
                    publisherLink.addClass("deleted");
                }
                
                XHTML.Table.Row row =  new XHTML.Table.Row(objectCell).add(publisher.toXHTMLTableData());
                if (!objectCell.equals(emptyCell)) {
                    row.addClass("barrier");
                }
                tbody.add(row);
                objectCell = emptyCell;
            }
        }
        XHTML.Table publishers = new XHTML.Table(new XHTML.Table.Caption("Publish Records"), thead, tbody).setId("objectPublishers").setClass("Publishers");

        return publishers;
    }
}
