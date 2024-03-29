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

import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.XHTMLInspectable;
import sunlabs.titan.node.TitanObjectStoreImpl.DeleteTokenException;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.InspectableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.util.DOLRStatus;

/**
 * An AnchorObject is a {@link TitanObject} that represents, and contains information about,
 * a specific Celeste file.
 * 
 * @see VersionObject
 * @see BlockObject
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface AnchorObject {

    public interface Handler extends RetrievableObject.Handler<AnchorObject.Object>, InspectableObject.Handler<AnchorObject.Object>,
    DeleteableObject.Handler<AnchorObject.Object>, ReplicatableObject.Handler<AnchorObject.Object> {
        public AnchorObject.Object.Version makeVersion(TitanGuid generationId, long serialNumber);

        public AnchorObject.Object create(FileIdentifier fileIdentifier,
                ReplicationParameters replicationParams,
                TitanGuid deleteTokenHash,
                long timeToLive,
                int bObjectSize,
                boolean signWrites);
        
        public DOLRStatus delete(FileIdentifier fileIdentifier, TitanGuid deletionToken, long timeToLive) throws IOException, TitanObjectStoreImpl.NoSpaceException,
            ClassCastException, RemoteException, DeleteTokenException, ClassNotFoundException;

        /**
         * Retrieve the {@link AnchorObject} given the Celeste {@link FileIdentifier}.
         *
         * @param fileIdentifier
         * @return An instance of the {@code AnchorObject}.
         * @throws TitanObjectStoreImpl.DeletedObjectException  If the {@code AnchorObject} exists, but has been deleted.
         * @throws TitanObjectStoreImpl.NotFoundException If the {@code AnchorObject} was not found.
         * @throws RemoteException 
         * @throws ClassCastException 
         * @throws ClassNotFoundException 
         */
        public AnchorObject.Object retrieve(FileIdentifier fileIdentifier) throws TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NotFoundException, ClassCastException, ClassNotFoundException;
    }
    
    /**
     * A file anchor object implements this interface.
     *
     */
    public interface Object extends RetrievableObject.Handler.Object, DeleteableObject.Handler.Object, InspectableObject.Handler.Object, ReplicatableObject.Handler.Object {
        // AObject meta-data parameters
        /**
         * The property name of the number of copies of the object to store before the object is considered committed to the object pool.
         */
        public final static String REPLICATIONPARAM_STORE_NAME = "AObject.Replication.Store";

        /**
         * The property name of the number of copies of the object that are to be maintained automatically in the object pool.
         * (UNIMPLEMENTED-TBD).
         */
        public final static String REPLICATIONPARAM_LOWWATER_NAME = "AObject.Replication.LowWater";

        /**
         * A file's version is represented by a unique {@link VersionObject}.
         * Every {@link VersionObject} contains a generation identifier, and a serial number.
         * The generation number is to distinguish between different
         * evolutions of a file, and the serial number is a simple, monotonically increasing number.
         */
        public interface Version extends XHTMLInspectable, Serializable {
            public TitanGuid getGeneration();

            public long getSerialNumber();
        }

        /**
         * Get this file's {@link ReplicationParameters}.
         */
        public ReplicationParameters getReplicationParameters();

        /**
         * The maximum number of bytes for any {@link BlockObject}.
         */
        public int getBObjectSize();

        /**
         * Get the {@link TitanGuid} of this object's delete-token.
         */
        public TitanGuid getDeleteTokenId();

        /**
         * Return true if modifications to the file represented by this {@link AnchorObject}
         * must be signed by the client/requestor.
         */
        public boolean getSignWrites();

        /**
         * Set the {@link AObjectVersionService} parameters for the mutable object system.
         *
         * @param params
         */
        public void setAObjectVersionMapParams(AObjectVersionMapAPI.Parameters params);

        /**
         * Get the {@link AObjectVersionService} parameters for the mutable object system.
         */
        public AObjectVersionMapAPI.Parameters getAObjectVersionMapParams();
    }
}
