/*
 * Copyright 2010 Oracle. All Rights Reserved.
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
package sunlabs.titan.node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

import sunlabs.asdf.util.AbstractStoredMap;
import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.util.DOLRLogger;

/**
 * Object location in the Titan Object Pool.
 * <p>
 * Nodes that possess objects periodically publish the availability of each object by routing a
 * {@link PublishObjectMessage} containing the information about the object and the node that possesses it.
 * This message is routed through the system leaving behind <em>back-pointers</em> consisting of
 * the map <em>O&rarr;N</em> stored at each node along the route of the message.  It is
 * not necessary for each node along the route to store the mapping, but it is necessary for
 * the root node of each object to store the mapping.
 * The message ultimately arrives on a node that cannot route the PublishObjectMessage further.
 * That node is the <em>root</em> node of the published object's {@link TitanGuidImpl}, <em>O<sub>r</sub></em>.
 * </p>
 * <p>
 * Sending a message to an object is broken into two parts: A {@link TitanMessage.Type#RouteToNode}
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
 * @author Glenn Scott - Oracle Sun Labs
 */
public class Publishers extends AbstractStoredMap<TitanGuid, HashSet<Publishers.PublishRecord>> {
    private final static long serialVersionUID = 1L;

    public final static String METADATA_PUBLISHERTTL = "Publishers.PublisherTimeToLive";

    private ObjectLock<TitanGuid> locks;

    private TitanNodeImpl node;

    /**
     * A Publisher object is a record of a single binding of a {@link TitanObject} with a node advertising the object's
     * availability and the metadata that that node maintains for the published object. Nodes advertise the
     * availability of objects by emitting {@link PublishObjectMessage} messages which are ultimately received by the "root" of the object identifier. 
     * <p>
     * The record consists of:
     * <ul>
     * <li>The {@link NodeAddress} of the node possessing the object.</li>
     * <li>The {@link TitanObject.Metadata} of the object.</li>
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
        	result[2] = new XHTML.Table.Heading("TTL");
        	result[3] = new XHTML.Table.Heading("Class");
        	return result;            
        }
        
        private TitanGuid objectId;
        private NodeAddress publisher;
        private TitanObject.Metadata metaData;
        /** The absolute time (in seconds) when this record must be removed (expires) */
        private long expireTimeSeconds;

        /**
         * Construct a PublishRecord instance binding the given {@link TitanGuidImpl} of an object
         * to the given {@link NodeAddress} of a node advertising its availability.
         *
         * @param objectId the {@link TitanGuidImpl} of the {@link TitanObject}
         * @param publisher the {@link NodeAddress} of the publishing {@link TitanNodeImpl}
         * @param metaData the complete {@link TitanObject.Metadata} of the published {@code TitanObject}
         * @param  recordSecondsToLive The system time, in seconds, when this record must be removed.
         */
        public PublishRecord(TitanGuid objectId, NodeAddress publisher, TitanObject.Metadata metaData, long recordSecondsToLive) {
            this.objectId = objectId;
            this.publisher = publisher;
            this.metaData = metaData;
            this.expireTimeSeconds = Time.millisecondsInSeconds(System.currentTimeMillis()) + recordSecondsToLive;
            assert this.expireTimeSeconds > 0;
        }

        @Override
        public boolean equals(Object other) {
            // Two Publisher instances are equal if they have equal {@link TitanGuid} and node-id {@link TitanNodeId} values.
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
         * Get the {@link TitanGuid} of the published object.
         */
        public TitanGuid getObjectId() {
            return this.objectId;
        }

        /**
         * Get the {@link TitanNodeId} of the publishing node.
         */
        public TitanNodeId getNodeId() {
            return this.publisher.getObjectId();
        }

        /**
         * Get the published {@link TitanObject.Metadata} for this object.
         * @return the published {@link TitanObject.Metadata} for this object.
         */
        public TitanObject.Metadata getMetadata() {
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
         * @see System#currentTimeMillis()
         * @return the absolute time (in milliseconds) that this {@code PublishRecord} instance will expire.
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

        /**
         * Return {@code true} if this Publishers.Record is a deleted object.
         * <p>
         * The published metadata contains a valid delete-token.
         * </p>
         * @return {@code true} if this Publishers.Record is a deleted object.
         */
        public boolean isDeleted() {
            return DeleteableObject.deleteTokenIsValid(this.metaData);
        }

        public String getObjectClass() {
            return this.metaData.getProperty(ObjectStore.METADATA_CLASS, null);
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
            StringBuilder result = new StringBuilder("objectId=");
            result.append(this.objectId).append(" node=").append(this.getNodeId()).append(" ").append(this.getExpireTimeSeconds()).append(" ").append(this.getObjectTTL()).append(" ").append(this.getObjectClass());
            return result.toString();
        }

        public XHTML.Table.Data[] toXHTMLTableData() {
        	XHTML.Anchor a = WebDAVDaemon.inspectNodeXHTML(this.publisher);

        	String secondsToLive = (this.getObjectTTL() == TitanObject.INFINITE_TIME_TO_LIVE) ? "&infin;" : Long.toString(this.getObjectTTL());
        	String xmlClass = DeleteableObject.deleteTokenIsValid(this.metaData) ? "deleted" : "undeleted";
        	XHTML.Table.Data[] result = new XHTML.Table.Data[4];

        	result[0] = new XHTML.Table.Data(a).setClass(xmlClass);

        	result[1] = new XHTML.Table.Data("%s (%+ds)", Time.ISO8601(Time.secondsInMilliseconds(this.getExpireTimeSeconds())),
        	        this.getExpireTimeSeconds() - Time.currentTimeInSeconds());
        	result[2] = new XHTML.Table.Data("%s", secondsToLive);
        	result[3] = new XHTML.Table.Data(WebDAVDaemon.inspectServiceXHTML(this.getObjectClass()));
        	return result;
        }
    }

    public Publishers(TitanNodeImpl node, String spoolDirectory) throws IOException, IllegalStateException, AbstractStoredMap.OutOfSpace {
        super(new File(spoolDirectory + File.separatorChar + "object-publishers"), Long.MAX_VALUE);
        this.node = node;

        this.locks = new ObjectLock<TitanGuid>();
    }

    /**
     * For each object-id in the ObjectPublisher back-pointer list,
     * examine the list of publishers,
     * decrementing the time-to-live for each by the frequency in seconds of this repeating process.
     * If the time-to-live is zero or negative, remove that publisher from the list of publishers.
     * @param log
     * @return
     */
    public long expire(DOLRLogger log) {
        long count = 0;
        for (TitanGuid objectId : this) {
            Set<Publishers.PublishRecord> publishers = this.getPublishersAndLock(objectId);
            try {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("begin %s %d publishers", objectId, publishers.size());
                }
                boolean modified = false;
                HashSet<Publishers.PublishRecord> newPublishers = new HashSet<Publishers.PublishRecord>(publishers);
                for (Publishers.PublishRecord record : publishers) {
                    long secondsUntilExpired = record.getExpireTimeSeconds() - Time.currentTimeInSeconds();
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("%s %s expireTime=%d dt=%+d", objectId, record.getNodeAddress().getObjectId(), record.getExpireTimeSeconds(), secondsUntilExpired);
                    }
                    if (secondsUntilExpired <= 0) {
                        newPublishers.remove(record);
                        modified = true;
                    }
                }
                if (modified) {
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("end %s %d publishers", objectId, newPublishers.size());
                    }
                    try {
                        this.put(objectId, newPublishers);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        log.severe("IOException when storing.");
                        this.remove(objectId);
                    } catch (AbstractStoredMap.OutOfSpace e) {
                        log.severe("Out of storage space");
                        this.remove(objectId);
                    }
                }
            } finally {
                this.unlock(objectId);
            }
        }

        return count;
    }
    
    /**
     * <p>
     * Get the {@link HashSet} of {@link Publishers.PublishRecord} instances for the given {@link TitanGuid}.
     * <em>There result is NOT locked.</em>
     * </p>
     * <p>
     * If there are no Publishers of the given {@code TitanGuid}, return an empty {@code Set}.
     * </p>
     *
     * @param objectId the {@code TitanGuid} of the published object.
     *
     * @return  a {@code HashSet} consisting of all the {@code Publishers.Publisher}
     * instances for the given {@code TitanGuid}.
     */
    public HashSet<Publishers.PublishRecord> getPublishers(TitanGuid objectId) {
        HashSet<Publishers.PublishRecord> set = this.getPublishersAndLock(objectId);
        try {
            return set;
        } finally {
            this.unlock(objectId);
        }
    }

    /**
     * <p>
     * Get the {@link HashSet} of {@link Publishers.PublishRecord} instances of the given {@link TitanGuid}.
     * <em>There result is locked and MUST be unlocked when the caller has finished manipulating.</em>
     * </p>
     * <p>
     * If there are no Publishers of the given {@code TitanGuid}, return an empty {@code Set}.
     * </p>
     * @param objectId the {@code TitanGuid} of the published object.
     *
     * @return  a {@code HashSet} consisting of all the {@code Publishers.Publisher} instances for the given {@code TitanGuid}.
     *
     * @throws IllegalStateException if an attempt to lock a {@code TitanGuid}} more than once.
     */
    private HashSet<Publishers.PublishRecord> getPublishersAndLock(TitanGuid objectId) throws IllegalStateException {
        this.locks.lock(objectId);
        try {
            try {
                return super.get(objectId);
            } catch (FileNotFoundException e) {
                return new HashSet<Publishers.PublishRecord>();                
            } catch (IOException e) {
                this.remove(objectId);
                return new HashSet<Publishers.PublishRecord>();                
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } catch (ClassCastException e) {
            this.locks.unlock(objectId);
            throw e;
        }
    }

    /**
     * Unlock the given {@link TitanGuid}.
     * The {@code objectId} must already be locked by the current {@link Thread}.
     * @throws IllegalStateException if the {@code objectId} is not locked.
     */
    private boolean unlock(TitanGuid objectId) {
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
     * @param objectId the {@link TitanGuid} of the object.
     * @param set the {@link Set} containing all of the publishers of {@code objectId}.
     * @throws AbstractStoredMap.OutOfSpace 
     * @throws IOException 
     * @throws IllegalStateException if the {@code objectId} is not locked.
     */
    public void put(TitanGuid objectId, HashSet<Publishers.PublishRecord> set) throws IllegalStateException, IOException, AbstractStoredMap.OutOfSpace {
        synchronized (this.locks) {
            this.locks.assertLock(objectId);
            
            if (set.size() != 0) {
                super.put(objectId, set);
//                this.publishers.put(objectId, set);
            } else {
                super.remove(objectId);
//                this.publishers.remove(objectId);
            }
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
     * @throws AbstractStoredMap.OutOfSpace 
     * @throws IOException 
     * @throws IllegalStateException 
     */
    public void update(Publishers.PublishRecord publisher) throws IllegalStateException, IOException, AbstractStoredMap.OutOfSpace {
        HashSet<Publishers.PublishRecord> oldSet = (HashSet<Publishers.PublishRecord>) this.getPublishersAndLock(publisher.getObjectId());
        try {
        	oldSet.remove(publisher);
            if (oldSet.add(publisher)) {
                this.put(publisher.getObjectId(), oldSet);
            }
        } finally {
            this.unlock(publisher.getObjectId());
        }
    }
    
    public void update(TitanGuid objectId, Publishers.PublishRecord record) throws IllegalStateException, IOException, AbstractStoredMap.OutOfSpace {
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
     * @throws AbstractStoredMap.OutOfSpace 
     * @throws IOException 
     * @throws IllegalStateException 
     */
    public void update(TitanGuid objectId, Set<Publishers.PublishRecord> publisherSet) throws IllegalStateException, IOException, AbstractStoredMap.OutOfSpace {
        HashSet<Publishers.PublishRecord> set = (HashSet<Publishers.PublishRecord>) this.getPublishersAndLock(objectId);
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
     * @throws AbstractStoredMap.OutOfSpace 
     * @throws IOException 
     * @throws IllegalStateException 
     */
    public void remove(TitanGuid objectId, TitanNodeId publisherId) throws IllegalStateException, IOException, AbstractStoredMap.OutOfSpace {
        HashSet<Publishers.PublishRecord> newSet = new HashSet<Publishers.PublishRecord>();
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
        SortedSet<TitanGuid> objectIds = new TreeSet<TitanGuid>();
        for (TitanGuid objectId : this) {
            objectIds.add(objectId);
        }

        XHTML.Table.Data emptyCell = new XHTML.Table.Data();

        XHTML.Table.Head thead = new XHTML.Table.Head();
        thead.add(new XHTML.Table.Row(new XHTML.Table.Heading("Titan Guid")).add(PublishRecord.toXHTMLTableHeading()));
        
        XHTML.Table.Body tbody = new XHTML.Table.Body();
        for (TitanGuid objectId : objectIds) {
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
        XHTML.Table publishers = new XHTML.Table(new XHTML.Table.Caption("Published Object Records"), thead, tbody).setId("objectPublishers").setClass("Publishers");

        return publishers;
    }

    @Override
    public File keyToFile(File root, TitanGuid key) {
        String s = key.toString();
        StringBuilder result = new StringBuilder();
        result.append(s.substring(0, 5)).append(File.separatorChar).append(s);
        return new File(root, result.toString());
    }   

    @Override
    public TitanGuid fileToKey(File file) {
        return new TitanGuidImpl(file.getName());       
    }
}
