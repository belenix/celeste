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

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.client.operation.AbstractCelesteOperation;
import sunlabs.celeste.client.operation.CelesteOperation;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.Credential;
import sunlabs.titan.exception.BeehiveException;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.object.AccessControlledObject;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.ExtensibleObject;
import sunlabs.titan.node.object.InspectableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;

/**
 * A {@code VersionObject} is a {@link BeehiveObject} that contains the information about
 * a particular version of a Celeste file. All operations that change a file or its state
 * generate a new version and as a consequence a new {@code VersionObject}.
 *
 * @see AnchorObject
 * @see BlockObject
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface VersionObject extends
        StorableObject.Handler<VersionObject.Object>,
        RetrievableObject.Handler<VersionObject.Object>,
        InspectableObject.Handler<VersionObject.Object>,
        DeleteableObject.Handler<VersionObject.Object>,
        AccessControlledObject.Handler<VersionObject.Object>,
        ReplicatableObject.Handler<VersionObject.Object>,
        ExtensibleObject.Handler<VersionObject.Object> {

    public class BadManifestException extends BeehiveException {
        private final static long serialVersionUID = 1L;

        public BadManifestException() {
            super();
        }

        public BadManifestException(String format, java.lang.Object...args) {
            super(String.format(format, args));
        }

        public BadManifestException(String message) {
            super(message);
        }

        public BadManifestException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * A {@code VersionObject} manifest contains a {@link SortedMap} of {@link BlockObject.Object.Reference}
     * instances, as well as the starting offset and length of the map.
     * 
     * @see #getManifest()
     */
    public interface Manifest extends Serializable/*, SortedMap<Long, BlockObject.Object.Reference>*/ {
        public interface Request extends Serializable {
            public long getOffset();
            public int getLength();
        }

        public SortedMap<Long, BlockObject.Object.Reference> getManifest();
        public long getOffset();
        public long getLength();
    }

    public interface Object extends
            StorableObject.Handler.Object,
            RetrievableObject.Handler.Object,
            InspectableObject.Handler.Object,
            Iterable<BlockObject.Object.Reference>,
            DeleteableObject.Handler.Object,
            AccessControlledObject.Handler.Object,
            ExtensibleObject.Handler.Object,
            ReplicatableObject.Handler.Object
            /* TriggeredObject.Handler.Object */
            {
        // VObject meta-data parameters
        public final static String REPLICATIONPARAM_STORE = "VObject.Replication.Store";
        public final static String REPLICATIONPARAM_LOWWATER = "VObject.Replication.LowWater";
//
//        // Writer's Responsibility
//        public final static String NAMESPACEID_NAME = "VObject.NameSpaceId";
//        public final static String FILEID_NAME = "VObject.FileId";
//        public final static String DELETETOKENID_NAME = "VObject.DeleteTokenId";
//        public final static String REPLICATIONPARAMS_NAME = "VObject.ReplicationParameters";
//
//        // Composer's Responsibility
//        public final static String FILELENGTH_NAME = "VObject.FileLength";
//        public final static String FRAGMENT_HEAD_NAME = "VObject.FragmentHead";
//        // private final static String FRAGMENT_TREE_NAME = "VObject.FragmentTree";
//        public final static String OWNER_NAME = "VObject.Owner";
//        public final static String GROUP_NAME = "VObject.Group";
//        public final static String ACL_NAME = "VObject.ACL";
//
//        public final static String PREVIOUSVOBJECT_NAME = "VObject.previousVObject";
//
//        public final static String VERSION_NAME = "VObject.Version";
//
//        public final static String WRITERS_SIGNATURE_NAME = "VObject.WritersSignature";
//        public final static String CLIENTMETADATA_NAME = "VObject.ClientMetaData";

        public interface Reference extends Serializable {
            public BeehiveObjectId getObjectId();

            public AnchorObject.Object.Version getVersion();
            
            public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
        }

        public VersionObject.Object.Reference makeReference();

        /**
         * Truncate this VObject to the specified offset by deleting all
         * BObjects greater than or equal to the given offset.
         *
         * @param offset
         */
        public void truncate(Long offset);

        public SortedMap<Long,BlockObject.Object.Reference> getBObjectList();

        /**
         * Get a {@link SortedMap} containing the set of {@link BlockObject.Object.Reference} keyed by their offset in the file.
         *
         * @param fileStart the starting position in the file
         * @param fileStop the ending position in the file.
         */
        public SortedMap<Long,BlockObject.Object.Reference> getExtent(long fileStart, long fileStop);

        /**
         * Get a Map of all the BlockObject references the contain data starting at
         * {@code offset} and continuing for {@code length} bytes.
         *
         * The {@code offset} cannot be negative nor greater than the length of the file.
         *
         * A negative {@code length} indicates the length between {@code offset} and the end-of-file.
         */
        public Manifest getManifest(long offset, long length) throws VersionObject.BadManifestException;

        /**
         * Add the given BlockObject to this VersionObject list of BlockObjects.
         *
         * BlockObjects are stored in a sorted list.
         * If the added BlockObject extends the file's length beyond the value
         * of fileSize, the fileSize is updated.
         */
        public void addBObject(BlockObject.Object.Reference newBObject);

        /**
         * Get the {@link BlockObject.Object.Reference} to the {@link BlockObject} that contains the specified offset
         * in the original file.
         *
         * @param offset
         */
        public BlockObject.Object.Reference getBObjectReference(long offset);

        /**
         * Get the {@link AnchorObject.Object.Version} for this {@link VersionObject}.
         */
        public AnchorObject.Object.Version getVersion();

        /**
         * Set the {@link AnchorObject.Object.Version} for this {@link VersionObject}.
         * @param version
         */
        public void setVersion(AnchorObject.Object.Version version);

        /**
         *
         */
        public Object.Reference getPreviousVObject();

        /**
         *
         */
        public void setPreviousVObjectReference(Object.Reference vReference);

        /**
         * Get the size, in bytes, of the entire file represented by this VersionObject.
         */
        public long getFileSize();

        /**
         * Use with caution.
         *
         * @param fileSize The fileSize to set.
         */
        public void setFileSize(long fileSize);

        /**
         * @return Returns the signature.
         */
        public Credential.Signature getSignature();

        /**
         * @param signature The signature to set.
         */
        public void setSignature(Credential.Signature signature);

        /**
         * @return the {@link CelesteOperation} that resulted in this version.
         */
        public CelesteOperation getCelesteOperation();

        /**
         */
        public void setCelesteOperation(AbstractCelesteOperation context);

        /**
         * @return the client private context (NOT parse-able by Celeste) of this version
         */
        public ClientMetaData getClientMetaData();

        /**
         */
        public void setClientMetaData(ClientMetaData context);

        public BeehiveObjectId getAnchorObjectId();
        
        /**
         * Get the replication parameters that represents how this
         * VersionObject and its BlockObjects are stored.
         *
         * @return this version's replication parameters
         */
        public ReplicationParameters getReplicationParameters();

        /**
         * Get the {@link BeehiveObjectId} of this version's owner's {@link Credential}.
         */
        public BeehiveObjectId getOwner();

        /**
         * Set the {@link BeehiveObjectId} of this version's owner {@link Credential}.
         */
        public void setOwner(BeehiveObjectId owner);

        /**
         * Get the {@link BeehiveObjectId} of this version's group {@link Credential}.
         */
        public BeehiveObjectId getGroup();

        /**
         * Set the {@link BeehiveObjectId} of this version's group {@link Credential}.
         */
        public void setGroup(BeehiveObjectId group);

        /**
         * Return the access control list recorded in this version.
         */
        public CelesteACL getACL();

        /**
         * Set the file access control list recorded in this version.
         */
        public void setACL(CelesteACL acl);
        
        public List<Trigger> getPreTrigger();
        public void setPreTrigger(List<Trigger> list);

        public List<Trigger> getPostTrigger();
        public void setPostTrigger(List<Trigger> list);
    }
    
    public interface Trigger {
        
    }

    /**
     * Create a new Version Object composed from the supplied parameters.
     *
     * @param anchorObjectId The {@link BeehiveObjectId} of the {@link AnchorObject} of this file.
     * @param replicationParams This file's {@link ReplicationParameters}
     * @param createOperation The originating {@link CreateFileOperation} that induced the creation of this Version Object.
     * @param clientMetaData The {@link ClientMetaData} associated with this version of the file.
     * @param signature The client signature of the operation.
     */
    public VersionObject.Object create(BeehiveObjectId anchorObjectId,
            ReplicationParameters replicationParams,
            CreateFileOperation createOperation,
            ClientMetaData clientMetaData,
            Credential.Signature signature);

    /**
     * Get the VersionObject {@link Manifest} from the identified by {@link BeehiveObjectId}
     * for the range starting at {@code offset} and continue for {@code length} bytes.
     *
     * @param objectId
     * @param offset
     * @param length
     * @throws VersionObject.BadManifestException
     * @throws ClassCastException
     * @throws BeehiveObjectStore.DeletedObjectException
     * @throws BeehiveObjectStore.NotFoundException
     * @throws RemoteException 
     */
    public Manifest getManifest(BeehiveObjectId objectId, long offset, long length)
    throws VersionObject.BadManifestException, ClassCastException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException, RemoteException;
}
