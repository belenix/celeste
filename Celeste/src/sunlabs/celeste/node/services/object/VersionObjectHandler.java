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
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.client.operation.AbstractCelesteOperation;
import sunlabs.celeste.client.operation.CelesteOperation;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.object.ExtensibleObject;
import sunlabs.celeste.util.ACL;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanObjectStoreImpl.DeletedObjectException;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.HTTPMessageService;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.objectstore.PublishDaemon;
import sunlabs.titan.util.DOLRStatus;

public final class VersionObjectHandler extends AbstractObjectHandler implements VersionObject {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(VersionObjectHandler.class, VersionObjectHandler.serialVersionUID);

    private final static int replicationStore = 2;
    private final static int replicationCache = 2;

    /**
     * This class represents a Version Object (VObject).
     * A VObject contains all the information necessary to represent and reconstruct
     * a particular version of a file.
     * <p>
     */
    public static final class VObject extends AbstractTitanObject implements VersionObject.Object, Iterable<BlockObject.Object.Reference> {
        private static final long serialVersionUID = 2L;

        //
        // XXX: How should ACLs be handled for encoding and decoding?  They fall
        //      in the same logical category as owner and group.  My best guess is
        //      that they're all composer responsibility, since (except for
        //      operations that explicitly change them) they must carry forward
        //      from a predicated version to the successor version composed from
        //      it.  That's all well and good, but what are the implications for
        //      encoding?  (Unlike owner and group, ACL values cannot be
        //      reconstituted from a string representation; we must rely on
        //      serialization/deserialization instead.)  Perhaps there's a way to
        //      incorporate them into one of the other segments, but giving them a
        //      segment of their own is straightforward.
        //

        private AnchorObject.Object.Version version;

        private VersionObject.Object.Reference previousVObject;
//        private VObjectTypeAPI.VObject.Reference lastVObjectInChain;

        private long fileSize;

        // Stuff for maintaining the BObjects for this version.
        private TreeMap<Long,BlockObject.Object.Reference> bObjectList;
        private BlockObject.Object.Reference lastBObject;

//        private StorableFragmentedObject.Handler.FragmentMap fragmentHead;

        private TitanGuid anchorObjectId;
        
        private Credential.Signature signature;
        private AbstractCelesteOperation celesteOperation;
        private ClientMetaData clientMetaData;

        private ReplicationParameters replicationParams;

        protected int replicationStore;

        protected int replicationCache;

        private int bObjectSize;

        //
        // Each version has an owner and a group.  Their presence allows Celeste
        // files to participate in POSIX-like permission checking to guard
        // operation invocations.  Each version also has an ACL, which records the
        // specific permissions required to invoke each operation.
        //
        private TitanGuid owner = null;
        private TitanGuid group = null;
        private CelesteACL acl = null;
        
//        private Trigger preOperationTrigger;
//
//        private Trigger postOperationTrigger;

        /**
         * A reference to an object is all that is needed to discriminate
         * the function or relationship of object from all other objects like this one.
         *
         * For example, a {@link BlockObject} has an object-id, which is sufficient to
         * discriminate it from all other objects in the object pool, but it
         * also has an offset and a length which discriminates it from all
         * other {@code BlockObject}s that comprise the original file version.
         *
         * As another example, a {@link VersionObject} has an object-id, which is sufficient
         * to discriminate it from all other objects in the pool, but it also
         * has a generation number and serial number which discriminates it
         * rom all other {@code VersionObject}s that comprise the original file.
         */
        public static class Reference implements VersionObject.Object.Reference, Serializable {
            public final static long serialVersionUID = 1L;

            private final AnchorObject.Object.Version version;
            private TitanGuid objectId;

            public Reference(AnchorObject.Object.Version version, TitanGuid objectId) {
                this.objectId = objectId;
                this.version = version;
            }

            public Reference(String reference) {
                String[] tokens = reference.split(":");
                this.version = new AnchorObjectHandler.AObject.Version(tokens[0]);
                this.objectId = new TitanGuidImpl(tokens[1]);
            }

            @Override
            public boolean equals(java.lang.Object other) {
                if (this == other)
                    return true;
                if (other == null)
                    return false;
                if (other instanceof Reference) {
                    Reference o = (Reference) other;
                    if (this.version.equals(o.version) && this.objectId.equals(o.objectId)) {
                        return true;
                    }
                }
                return false;
            }

            public TitanGuid getObjectId() {
                return this.objectId;
            }

            public AnchorObject.Object.Version getVersion() {
                return this.version;
            }

            @Override
            public String toString() {
                StringBuilder result = new StringBuilder(this.version.toString())
                .append(":")
                .append(this.objectId.toString());
                return result.toString();
            }

            public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
                XHTML.Table.Body tbody = new XHTML.Table.Body()
                .add(new XHTML.Table.Row(new XHTML.Table.Data(HTTPMessageService.inspectObjectXHTML(this.objectId))))
                .add(new XHTML.Table.Row(new XHTML.Table.Data(this.version.toXHTML(uri, props))))
                ;
                return new XHTML.Table(tbody);
            }
        }

        /**
         * Create a {@code VObject} representing the first version of a new file.
         */
        protected VObject(
                TitanGuid anchorObjectId,
                ReplicationParameters replicationParams,
                CreateFileOperation createOperation,
                ClientMetaData clientData,
                Credential.Signature signature) {
            super(VersionObjectHandler.class, createOperation.getDeleteTokenId(), createOperation.getTimeToLive());

            this.anchorObjectId = anchorObjectId;
            this.replicationParams = replicationParams;
            this.celesteOperation = createOperation;
            this.clientMetaData = clientData;
            this.signature = signature;

            this.replicationStore = replicationParams.getAsInteger(VersionObject.Object.REPLICATIONPARAM_STORE, VersionObjectHandler.replicationStore);
            this.replicationCache = replicationParams.getAsInteger(VersionObject.Object.REPLICATIONPARAM_LOWWATER, VersionObjectHandler.replicationCache);
            this.setProperty(TitanObjectStore.METADATA_REPLICATION_STORE, this.replicationStore);
            this.setProperty(TitanObjectStore.METADATA_REPLICATION_LOWWATER, this.replicationCache);

            this.version = new AnchorObjectHandler.AObject.Version(null, 0);
            this.bObjectList = new TreeMap<Long,BlockObject.Object.Reference>();
            this.fileSize = 0;

            this.bObjectSize = createOperation.getBObjectSize();

            //
            // Obtain the owner, group, and ACL fields for this initial version
            // from the data embedded in the create operation.  Subsequent
            // versions (except ones created by operations that explicitly change
            // these fields) carry them forward from the previous version.
            //
            this.owner = createOperation.getOwnerId();
            this.group = createOperation.getGroupId();
            this.acl = createOperation.getACL();
        }

        @Override
        public TitanGuid getDataId() {
            return this.getDeleteTokenId()
            	.add(this.getAnchorObjectId())
                .add(this.getVersion().getGeneration())
                .add(String.valueOf(this.getVersion().getSerialNumber()));
        }

        public void delete(TitanGuid profferedDeleteToken, long timeToLive) throws TitanObjectStoreImpl.DeleteTokenException {
            DeleteableObject.ObjectDeleteHelper(this, profferedDeleteToken, timeToLive);
        }

        public boolean checkAccess(TitanGuid clientId, CelesteACL.CelesteOps privilege) {
            CelesteACL acl = this.getACL();
            if (acl != null) {
                try {
                    CelesteACL.CelesteFileAttributeAccessor accessor =
                        new CelesteACL.CelesteFileAttributeAccessor(this);
                    acl.check(privilege, accessor, clientId);
                } catch (ACL.ACLException e) {
                    //
                    // Here we assume that the privilege name matches that of the
                    // operation being checked.
                    //
                    return false;
                }
            }
            return true;
        }

        public VersionObject.Object.Reference makeReference() {
            return new VersionObjectHandler.VObject.Reference(this.getVersion(), this.getObjectId());
        }

        public SortedMap<Long,BlockObject.Object.Reference> getBObjectList() {
            return this.bObjectList;
        }

        public SortedMap<Long,BlockObject.Object.Reference> getExtent(long fileStart, long fileStop) {
            Long startKey = Long.valueOf(fileStart);
            SortedMap<Long,BlockObject.Object.Reference> map = this.bObjectList.headMap(startKey);
            if (map == null) {
                return null;
            }

            if (!map.isEmpty()) {
                Long lastKey = map.lastKey();
                BlockObject.Object.Reference reference = map.get(lastKey);
                if (fileStart > reference.getFileOffset() && fileStart < (reference.getFileOffset() + reference.getLength())) {
                    startKey = lastKey;
                }
            }

            return this.bObjectList.subMap(startKey, Long.valueOf(fileStop));
        }

        /**
         * The result returned from the getManifest() method.
         *
         * @author Glenn Scott
         */
        private static class Manifest /*extends TreeMap<Long,BlockObject.Object.Reference>*/ implements VersionObject.Manifest {
            private static final long serialVersionUID = 1L;

            private SortedMap<Long,BlockObject.Object.Reference> manifest;
            private long offset;
            private long length;

            public Manifest(SortedMap<Long,BlockObject.Object.Reference> manifest, long offset, long length) {
                this.manifest = manifest;
                this.offset = offset;
                this.length = length;
            }

            public SortedMap<Long, BlockObject.Object.Reference> getManifest() {
                return this.manifest;
            }

            public long getOffset() {
                return this.offset;
            }

            public long getLength() {
                return this.length;
            }
        }
        
        public Manifest getManifest(long offset, long length) throws VersionObject.BadManifestException {
            if (offset < 0 || offset > this.getFileSize()) {
                throw new VersionObject.BadManifestException("Offset %d exceeds file size %d", offset, this.getFileSize());
            }

            if (length == -1 || (offset + length) > this.getFileSize()) {
                length = (int) (this.getFileSize() - offset);
            }

            Long startKey = Long.valueOf(offset);
            SortedMap<Long,BlockObject.Object.Reference> map = this.bObjectList.headMap(startKey);
            if (map == null) {
                return new Manifest(null, offset, length);
            }

            if (!map.isEmpty()) {
                Long lastKey = map.lastKey();
                BlockObject.Object.Reference reference = map.get(lastKey);
                if (offset > reference.getFileOffset() && offset < (reference.getFileOffset() + reference.getLength())) {
                    startKey = lastKey;
                }
            }

            long fileStop = offset + length;
            SortedMap<Long, BlockObject.Object.Reference> result = this.bObjectList.subMap(startKey, Long.valueOf(fileStop));

            return new Manifest(result, offset, length);
        }

        public synchronized void addBObject(BlockObject.Object.Reference newBObject) {
            Long fileOffset = Long.valueOf(newBObject.getFileOffset());
            this.bObjectList.put(fileOffset, newBObject);

            if (this.lastBObject == null) {
                this.lastBObject = newBObject;
                long lastByteOffsetOfBObject = this.lastBObject.getFileOffset() + this.lastBObject.getLength();
                if (lastByteOffsetOfBObject > this.fileSize)
                    this.fileSize = lastByteOffsetOfBObject;
            } else if (newBObject.getFileOffset() >= this.lastBObject.getFileOffset()) {
                this.lastBObject = newBObject;
                long lastByteOffsetOfBObject = this.lastBObject.getFileOffset() + this.lastBObject.getLength();
                if (lastByteOffsetOfBObject > this.fileSize)
                    this.fileSize = lastByteOffsetOfBObject;
            }
        }

        public BlockObject.Object.Reference getBObjectReference(long offset) {
            // If the BlockObject list is empty, then there is obviously no BObject at this offset.
            if (this.bObjectList == null || this.bObjectList.size() == 0) {
                return null;
            }

            if (offset >= (this.lastBObject.getFileOffset() + this.lastBObject.getLength())) {
                return null;
            }

            Long offsetAsLong = Long.valueOf(offset);

            // The BObject list is keyed by the offset in the file of each BlockObject.
            // First, attempt to use the offset as the key.

            BlockObject.Object.Reference reference = this.bObjectList.get(offsetAsLong);
            if (reference != null) {
                return reference;
            }

            //
            // Otherwise, the offset is within the extent of some BlockObject.
            //
            // Construct a sub-map of BObjects which offsets are less than the offset we are looking for.
            // The last key in that map will be the offset of the first BlockObject prior to the offset we are looking for.
            //
            // Construct another sub-map of BlockObjects which offsets are greater than or equal
            // to the first BlockObject prior to the offset we are looking for.
            // The first key of this map

//            if (true) {
                long desiredBObjectOffset = (offset / this.bObjectSize) * this.bObjectSize;
                reference = this.bObjectList.get(Long.valueOf(desiredBObjectOffset));
                return reference;
//            } else {
//
//                try {
//                    SortedMap<Long,BlockObject.Object.Reference> headMap = this.bObjectList.headMap(offsetAsLong);
//                    Long key = headMap.lastKey();
//
//                    SortedMap<Long,BlockObject.Object.Reference> tailMap = this.bObjectList.tailMap(key);
//                    key = tailMap.firstKey();
//
//                    // At this point key points directly at the BObject containing offset.
//                    reference = this.bObjectList.get(key);
//                    long bObjectOffset = key.longValue();
//                    if (offset >= bObjectOffset && offset < (bObjectOffset + reference.getLength())) {
//                        return reference;
//                    }
//                } catch (ClassCastException e) {
//                    System.err.println("bObjectList: " + this.bObjectList.size());
//                } catch (IllegalArgumentException e) {
//                    System.err.println("bObjectList: " + this.bObjectList.size());
//                } catch (NullPointerException e) {
//                    System.err.println("bObjectList: " + this.bObjectList.size());
//                }
//
//                return null;
//            }
        }

        private final static class BObjectIterator implements Iterator<BlockObject.Object.Reference> {
            private Iterator<BlockObject.Object.Reference> iterator;

            public BObjectIterator(TreeMap<Long,BlockObject.Object.Reference> map) {
                super();
                this.iterator = map.values().iterator();
            }

            public BlockObject.Object.Reference next() {
                return this.iterator.next();
            }

            public boolean hasNext() {
                return this.iterator.hasNext();
            }

            public void remove() {
                return ;
            }
        }

        public Iterator<BlockObject.Object.Reference> iterator() {
            return new VObject.BObjectIterator(this.bObjectList);
        }

        /**
         * Remove the BObject located at offset from the list of BObjects for this VObject.
         *
         * @param offset
         */
        private void deleteBObject(Long offset) {
            BlockObject.Object.Reference deletedBObject = this.bObjectList.get(offset);
            this.bObjectList.remove(offset);

            if (this.lastBObject.equals(deletedBObject)) {
                if (this.bObjectList.isEmpty()) {
                    this.lastBObject = null;
                } else {
                    this.lastBObject = this.bObjectList.get(this.bObjectList.lastKey());
                }
            }
        }

        public void truncate(Long offset) {
            /*
             * Iterate through
             * XXX This is terrible.  Advance to the first BObject that contains the offset and delete from there.
             */
            SortedMap<Long,BlockObject.Object.Reference> toDelete;
            while (true) {
                toDelete = this.bObjectList.tailMap(offset);
                if (toDelete.size() == 0)
                    break;
                this.deleteBObject(toDelete.firstKey());
                if (toDelete.size() > 1) {
                    this.deleteBObject(toDelete.lastKey());
                }
            }

            if (this.bObjectList.isEmpty()) {
                this.lastBObject = null;
            } else {
                this.lastBObject = this.bObjectList.get(this.bObjectList.lastKey());
            }


        }

        public AnchorObject.Object.Version getVersion() {
            return this.version;
        }

        public void setVersion(AnchorObject.Object.Version version) {
            this.version = version;
        }

        public VersionObject.Object.Reference getPreviousVObject() {
            return this.previousVObject;
        }

        public void setPreviousVObjectReference(VersionObject.Object.Reference vReference) {
            this.previousVObject = vReference;
        }

        public long getFileSize() {
            return this.fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public Credential.Signature getSignature() {
            return this.signature;
        }
        
        public void setSignature(Credential.Signature signature) {
            this.signature = signature;
        }

        public CelesteOperation getCelesteOperation() {
            return this.celesteOperation;
        }

        /**
         */
        public void setCelesteOperation(AbstractCelesteOperation context) {
            this.celesteOperation = context;
        }

        /**
         * @return Returns the non-Celeste (Client)'s private meta-data of
         * this version.
         */
        public ClientMetaData getClientMetaData() {
            return this.clientMetaData;
        }

        /**
         */
        public void setClientMetaData(ClientMetaData context) {
            this.clientMetaData = context;
        }

        public TitanGuid getAnchorObjectId() {
        	return this.anchorObjectId;
        }

        /**
         * Get the ReplicationParameters instance that governs the system's replication of this file.
         */
        public ReplicationParameters getReplicationParameters() {
            return this.replicationParams;
        }

        public int getBObjectSize() {
            return this.bObjectSize;
        }

        /**
         * Return the file owner recorded in this version.
         *
         * @return this version's owner
         */
        public TitanGuid getOwner() {
            return this.owner;
        }

        /**
         * Set the file owner recorded in this version.
         *
         * @param owner   the new owner
         */
        public void setOwner(TitanGuid owner) {
            this.owner = owner;
        }

        /**
         * Return the file group recorded in this version.
         *
         * @return this version's group
         */
        public TitanGuid getGroup() {
            return this.group;
        }

        /**
         * Set the file group recorded in this version.
         *
         * @param group   the new group
         */
        public void setGroup(TitanGuid group) {
            this.group = group;
        }

        /**
         * Return the ACL recorded in this version.
         *
         * @return this version's ACL
         */
        public CelesteACL getACL() {
            return this.acl;
        }

        /**
         * Set the file ACL recorded in this version.
         *
         * @param acl   the new ACL
         */
        public void setACL(CelesteACL acl) {
            this.acl = acl;
        }

        /**
         * Produce a printable representation of this VersionObject.
         */
        @Override
        public String toString() {
            String nl = "\n";
            StringBuilder result = new StringBuilder("VObject[id=" + this.getObjectId() + ",size=" + this.fileSize + "]").append(nl);

            for (Long offset : this.bObjectList.keySet()) {
                BlockObject.Object.Reference bObjectReference = this.bObjectList.get(offset);
                result.append(" ");
                result.append(String.format("%19d ", offset.longValue()));
                result.append(String.format("%19d ", bObjectReference.getLength()));
                result.append(bObjectReference.getObjectId());
                result.append(nl);
            }
            return result.toString();
        }

        public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props) {
            XHTML.Table.Body tbody = new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data("Anchor Object Identifier"), new XHTML.Table.Data(HTTPMessageService.inspectObjectXHTML(this.getAnchorObjectId()))),
                    new XHTML.Table.Row(new XHTML.Table.Data("File Length"), new XHTML.Table.Data(this.fileSize)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Version"), new XHTML.Table.Data(this.version)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Replication Parameters"), new XHTML.Table.Data("%s", this.replicationParams)),
                    new XHTML.Table.Row(new XHTML.Table.Data("Delete Token Identifier"), new XHTML.Table.Data(this.getDeleteTokenId()).setClass("ObjectId")),
                    new XHTML.Table.Row(new XHTML.Table.Data("File Owner Identifier"), new XHTML.Table.Data(HTTPMessageService.inspectObjectXHTML(this.owner))),
                    new XHTML.Table.Row(new XHTML.Table.Data("File Group Identifier"), new XHTML.Table.Data(HTTPMessageService.inspectObjectXHTML(this.group)))
            );

            if (this.clientMetaData != null) {
            	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Client Meta Data"),
            			new XHTML.Table.Data(new XHTML.Preformatted(HttpUtil.prettyFormat(this.clientMetaData.getContext(), 32)))));
            }
            
            if (this.acl != null) {
            	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("File Access Control"), new XHTML.Table.Data(this.acl)));            	
            }
            if (this.previousVObject != null) {
                tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Previous Version Object Identifier"),
                        new XHTML.Table.Data(this.previousVObject.toXHTML(uri, props))));            	
            }
            XHTML.Table versionObjectProperties = new XHTML.Table(new XHTML.Table.Caption("Parameters"), tbody).setClass("Parameters");

            XHTML.Table manifestTable = null;
            if (!this.bObjectList.isEmpty()) {
            	XHTML.Table.Head bObjectTHead = new XHTML.Table.Head(new XHTML.Table.Row(new XHTML.Table.Heading("Offset"),
                        new XHTML.Table.Heading("Length"),
                        new XHTML.Table.Heading("Block Object Identifier")));
                XHTML.Table.Body bObjectTableBody = new XHTML.Table.Body();

                for (Long offset : this.bObjectList.keySet()) {
                    BlockObject.Object.Reference bObjectReference = this.bObjectList.get(offset);
                    bObjectTableBody.add(new XHTML.Table.Row(new XHTML.Table.Data(offset),
                            new XHTML.Table.Data(bObjectReference.getLength()),
                            new XHTML.Table.Data(HTTPMessageService.inspectObjectXHTML(bObjectReference.getObjectId())))
                    );
                }
                manifestTable = new XHTML.Table(new XHTML.Table.Caption("Block Object Manifest"), bObjectTHead, bObjectTableBody).setClass("Manifest");
            }
            
            XHTML.Div operation = new XHTML.Div(this.celesteOperation != null ? this.celesteOperation.toXHTMLTable(uri, props) : "null").setClass("section");

            XHTML.Table table = new XHTML.Table(
            		new XHTML.Table.Caption("Version Object"),
                    new XHTML.Table.Body(
                            new XHTML.Table.Row(new XHTML.Table.Data(new XHTML.Div(versionObjectProperties).setClass("section"))),
                            new XHTML.Table.Row(new XHTML.Table.Data(operation)),
//                            new XHTML.Table.Row(this.signature != null ? new XHTML.Table.Data(this.signature) : empty),
                            new XHTML.Table.Row(new XHTML.Table.Data(new XHTML.Div(manifestTable).setClass("section")))));

            XHTML.Div result = (XHTML.Div) super.toXHTML(uri, props);
            result.add(new XHTML.Div(table).setClass("section").addClass("VersionObject"));

            return result;
        }

        public List<Trigger> getPostTrigger() {
            // TODO Auto-generated method stub
            return null;
        }

        public List<Trigger> getPreTrigger() {
            // TODO Auto-generated method stub
            return null;
        }

        public void setPostTrigger(List<Trigger> list) {
            // TODO Auto-generated method stub
            
        }

        public void setPreTrigger(List<Trigger> list) {
            // TODO Auto-generated method stub
            
        }
    }

    // This is a lock signaling that a published object is undergoing a deletion.
    private ObjectLock<TitanGuid> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already deleting the specified object.
    private ObjectLock<TitanGuid> deleteLocalObjectLocks;

    public VersionObjectHandler(TitanNode node) throws JMException {
        super(node, VersionObjectHandler.name, "Celeste Version Object Handler");

        this.publishObjectDeleteLocks = new ObjectLock<TitanGuid>();
        this.deleteLocalObjectLocks = new ObjectLock<TitanGuid>();
    }

    public VersionObject.Object create(TitanGuid anchorObjectId,
            ReplicationParameters replicationParams,
            CreateFileOperation createOperation,
            ClientMetaData clientMetaData,
            Credential.Signature signature) {
        return new VObject(anchorObjectId, replicationParams, createOperation, clientMetaData, signature);
    }

    public Publish.PublishUnpublishResponse publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassCastException, ClassNotFoundException {
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("VersionObject.publishObject(%s)%n", message);
        }

        //
        // Handle deleted objects.
        //
        DeleteableObject.publishObjectHelper(this, request);
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("VersionObject.publishObject(%s): step 1%n", message);
        }

        AbstractObjectHandler.publishObjectBackup(this, request);
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("VersionObject.publishObject(%s): step 2%n", message);
        }

        // Dup the getObjectsToPublish set as it's backed by a Map and is not serializable.
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress(), new HashSet<TitanGuid>(request.getObjects().keySet()));
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassCastException, ClassNotFoundException {
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s", request);
        }
        ReplicatableObject.unpublishObjectRootHelper(this, request);

        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message, VersionObject.Object vObject) throws ClassNotFoundException,
      ClassCastException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.DeleteTokenException,
      TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, TitanObjectStoreImpl.InvalidObjectIdException,
      TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.Exception {
        Publish.PublishUnpublishResponse reply = StorableObject.storeLocalObject(this, vObject, message);
        return reply;
    }

    public VersionObject.Object storeObject(VersionObject.Object vObject) throws IOException, TitanObjectStoreImpl.NoSpaceException,
      TitanObjectStoreImpl.DeleteTokenException, TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, ClassCastException, ClassNotFoundException {
        StorableObject.storeObject(this, vObject);
        return vObject;
    }

    public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws TitanObjectStoreImpl.NotFoundException {
        return this.node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    public ReplicatableObject.Replicate.Response replicateObject(TitanMessage message) throws ClassNotFoundException, ClassCastException,
    TitanObjectStoreImpl.NotFoundException, TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception {
        try {
            ReplicatableObject.Replicate.Request request = message.getPayload(ReplicatableObject.Replicate.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("replicate %s", request.getObjectId());
            }
            VersionObject.Object aObject = this.retrieve(request.getObjectId());

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
    
    /**
     * {@inheritDoc}
     * <p>
     * A deleted Version Object will return {@code null}.
     * </p>
     * @throws RemoteException 
     */
    public VersionObject.Object retrieve(TitanGuid objectId) throws ClassCastException, ClassNotFoundException, TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NotFoundException {
        return RetrievableObject.retrieve(this, VersionObject.Object.class, objectId);
    }

    public Manifest getManifest(TitanGuid objectId, long offset, long length)
    throws ClassCastException, ClassNotFoundException, VersionObject.BadManifestException, DeletedObjectException, TitanObjectStoreImpl.NotFoundException, RemoteException {
        // XXX Make this a request and send it to the object.
        VersionObject.Object vObject = this.retrieve(objectId);
        VersionObject.Manifest manifest = vObject.getManifest(offset, length);
        return manifest;
    }

    public DeleteableObject.Response deleteLocalObject(TitanMessage message, DeleteableObject.Request request)throws ClassNotFoundException,
    TitanObjectStoreImpl.DeleteTokenException, TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException, TitanObjectStoreImpl.UnacceptableObjectException, TitanObjectStoreImpl.NotFoundException {
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

        return new DeleteableObject.Response();
    }

    public ObjectLock<TitanGuid> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    public DOLRStatus deleteObject(TitanGuid objectId, TitanGuid profferedDeletionToken, long timeToLive)
    throws TitanObjectStoreImpl.NoSpaceException {

        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("%s %s %ds", objectId, profferedDeletionToken, timeToLive);
        }
        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        TitanMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
        if (!reply.getStatus().isSuccessful()) {
            this.log.fine("FAILED %s", objectId);
        }
        return reply.getStatus();
    }

    public TitanObject createAntiObject(DeleteableObject.Handler.Object object, TitanGuid profferedDeleteToken, long timeToLive) throws TitanObjectStoreImpl.NoSpaceException,
    TitanObjectStoreImpl.DeleteTokenException {

        //this.log.info("%s", object.getObjectId());

        VersionObject.Object vObject = VersionObject.Object.class.cast(object);

        VersionObject.Object.Reference previousVObject = vObject.getPreviousVObject();
        if (previousVObject != null) {
            TitanGuid previousVObjectId = previousVObject.getObjectId();
            this.deleteObject(previousVObjectId, profferedDeleteToken, timeToLive);
        }

        BlockObject bObjectHandler = this.node.getService(BlockObjectHandler.class);
        for (Long offset : vObject.getBObjectList().keySet()) {
            BlockObject.Object.Reference reference = vObject.getBObjectReference(offset);
            try {
                bObjectHandler.deleteObject(reference.getObjectId(), profferedDeleteToken, timeToLive);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        // Convert the given object to the deleted form.

        vObject.delete(profferedDeleteToken, timeToLive);

        return object;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
    
    public Serializable extensibleOperation(TitanMessage message, ExtensibleObject.Operation.Request request) throws ClassCastException, TitanMessage.RemoteException, SecurityException,
        IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return ExtensibleObject.extensibleOperation(this, request, message.getObjectId());
    }
    
    public <C> C extension(Class<? extends C> klasse, TitanGuid objectId, ExtensibleObject.Operation.Request op) throws ClassCastException, ClassNotFoundException, RemoteException {
        return ExtensibleObject.extension(this, klasse, objectId, op);
    }
}
