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

package sunlabs.celeste.client.operation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;

/**
 * Create a Celeste file.
 * <p>
 * Celeste files exist within the Celeste system as versioned instances where
 * each version represents a change to the file. Files are changed through
 * writing data, or modifying meta-data such as permissions.
 * </p>
 * <p>
 * Celeste file names are a 2-tuple consisting of the {@link BeehiveObjectId}
 * of the file's name-space and a {@link BeehiveObjectId} file-identifier unique
 * within the name-space (see {@link NewNameSpaceOperation}).  All Celeste file
 * interactions name the file by supplying both of these values.
 * </p>
 * <p>
 * A set of parameters, supplied at file creation time, govern the existence of the file:
 * <ul>
 * <li>The maximum time-to-live, expressed in seconds from the moment the creation takes place.</li>
 * <li>The {@link BeehiveObjectId} of the file's delete-token (see <b>Deleting Files in the Celeste Peer-to-Peer Storage System</b>)</li>
 * <li>The block-size of the underlying blocks that comprise the file.</li>
 * <li>The {@code BeehiveObjectId} of the Celeste credential representing the owner of the file.</li>
 * <li>The {@code BeehiveObjectId} of the Celeste credential representing the group of the file.</li>
 * <li>The {@link ReplicationParameters} governing the low-level replication requirements for components of the file.</li>
 * <li>The {@link CelesteACL} instance establishing the initial access-control parameters for the file.</li>
 * <li>A boolean value signifying whether or not subsequent writes to this file are to be signed by the writer.</li>
 * </ul>
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class CreateFileOperation extends UpdateOperation {
    private static final long serialVersionUID = 4L;

    public static final String name = "create-file";

//    /**
//     * The default maximum number of bytes for a file's {@code BlockObject} objects.
//     */
//    public static int defaultBlockObjectSize = 8*1024*1024;
    /**
     * The default {@link ReplicationParameters} to use when creating a file.
     */
    public static ReplicationParameters defaultReplicationParameters =
        new ReplicationParameters("AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2;AObjectVersionMap.Params=1,1");
    /**
     * The default {@link BeehiveObjectId} of the {@link Credential} representing the group ownership of a created file.
     */
    public static BeehiveObjectId defaultGroupId = null;
    /**
     * The default {@link CelesteACL} of a created file.
     */
    public static CelesteACL defaultAccessControl = null;

    private BeehiveObjectId deleteTokenId;
    private final BeehiveObjectId ownerId;
    private final BeehiveObjectId groupId;
    private final int blockObjectSize;
    private final ReplicationParameters replicationParameters;
    private CelesteACL acl;
    private final long timeToLive;
    private final boolean signWrites;

    /**
     * Creates a {@code CreateFileOperation} object encapsulating the fields
     * given as arguments.
     *
     * @param requestorId       The {@code BeehiveObjectId} of the requestor's {@code Credential} (See {@link Credential#getObjectId()}.
     * @param fileIdentifier    The {@link FileIdentifier} of the file to create.
     * @param deleteTokenId     The {@code BeehiveObjectId} the file's delete token.
     * @param timeToLive        The maximum number of seconds that this file will exist.
     * @param blockObjectSize   The maximum size of the data blocks for this file.
     * @param replicationParams The String representation of the replication parameters (See {@link ReplicationParameters}) to be used for the file.
     * @param clientMetaData    The {@link ClientMetaData} instance containing client-supplied metadata for this file.
     * @param ownerId           The {@code BeehiveObjectId} of the {@link Credential} recorded as owning the file's initial version
     * @param groupId           The {@code BeehiveObjectId} of the {@link Credential} recorded as the group of the initial file version
     * @param acl               The access control list (See {@link CelesteACL}) to be attached to the file's initial version.
     * @param signWrites        If {@code true}, all modifications to the file require that the data to be signed.
     *
     * @see ReplicationParameters
     * @see CelesteACL
     */
    public CreateFileOperation(BeehiveObjectId requestorId,
            FileIdentifier fileIdentifier,
            BeehiveObjectId deleteTokenId,
            long timeToLive,
            int blockObjectSize,
            String replicationParams,
            ClientMetaData clientMetaData,
            BeehiveObjectId ownerId,
            BeehiveObjectId groupId,
            CelesteACL acl,
            boolean signWrites) {

        this(requestorId, fileIdentifier, deleteTokenId, timeToLive, blockObjectSize, new ReplicationParameters(replicationParams), clientMetaData, ownerId,
             groupId,
             acl,
             signWrites);
    }

    /**
     * Creates a {@code CreateFileOperation} object encapsulating the fields
     * given as arguments.
     *
     * @param requestorId       The {@code BeehiveObjectId} of the requestor's {@code Credential} (See {@link Credential#getObjectId()}.
     * @param fileIdentifier    The {@link FileIdentifier} of the file to create.
     * @param deleteTokenId     The {@code BeehiveObjectId} the file's delete token.
     * @param timeToLive        The maximum number of seconds that this file will exist.
     * @param blockObjectSize   The maximum size of the data blocks for this file.
     * @param replicationParams The {@link ReplicationParameters} to be used for the file.
     * @param clientMetaData a  {@link ClientMetaData} instance containing client-supplied metadata for this file.
     * @param ownerId           The {@code BeehiveObjectId} of the {@link Credential} recorded as owning the file's initial version
     * @param groupId           The {@code BeehiveObjectId} of the {@link Credential} recorded as the group of the initial file version
     * @param acl               The access control list (See {@link CelesteACL}) to be attached to the file's initial version.
     * @param signWrites        If {@code true}, all modifications to the file require the data to be signed.
     *
     * @see ReplicationParameters
     * @see CelesteACL
     */
    public CreateFileOperation(BeehiveObjectId requestorId,
            FileIdentifier fileIdentifier,
            BeehiveObjectId deleteTokenId,
            long timeToLive,
            int blockObjectSize,
            ReplicationParameters replicationParams,
            ClientMetaData clientMetaData,
            BeehiveObjectId ownerId,
            BeehiveObjectId groupId,
            CelesteACL acl,
            boolean signWrites) {
        super(CreateFileOperation.name, fileIdentifier, requestorId, null, clientMetaData);

        this.deleteTokenId = deleteTokenId;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.blockObjectSize = blockObjectSize;
        this.replicationParameters = replicationParams;
        this.acl = acl;
        this.timeToLive = timeToLive;
        this.signWrites = signWrites;
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId()
        .add(this.deleteTokenId)
        .add(this.replicationParameters.toByteArray())
        .add(this.getOwnerId())
        .add(this.getGroupId());
        if (this.getACL() != null)
            id.add(acl.toString());
        return id;
    }

    //
    // N.B.  Stating that createFile is required as a privilege to invoke the
    // corresponding operation is a bit of a lie, since no ACL check is
    // actually performed on the operation (there being no existing
    // ACL-bearing entity to check against).
    //
    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.createFile;
    }

    @Override
    public String toString() {
        return String.format(
                "%s deleteTokenId=%s bObjectSize=%d timeToLive=%d replicationParameters=%s ownerId=%s groupId=%s acl=%s",
                super.toString(), this.deleteTokenId.toString(),
                this.blockObjectSize,
                this.timeToLive,
                this.replicationParameters,
                (this.ownerId != null) ? this.ownerId.toString() : "[null]",
                        (this.groupId != null) ? this.groupId.toString() : "[null]",
                                (this.acl != null) ? this.acl.toString() : "[null]");
    }

    public BeehiveObjectId getDeleteTokenId() {
        return this.deleteTokenId;
    }

    public void setDeleteTokenId(BeehiveObjectId deleteTokenId) {
        this.deleteTokenId = deleteTokenId;
    }

    public ReplicationParameters getReplicationParams() {
        return replicationParameters;
    }
    public BeehiveObjectId getOwnerId() {
        return this.ownerId;
    }

    public BeehiveObjectId getGroupId() {
        return this.groupId;
    }

    public CelesteACL getACL() {
        return this.acl;
    }

    public int getBObjectSize() {
        return this.blockObjectSize;
    }

    public long getTimeToLive() {
        return this.timeToLive;
    }

    public boolean getSignWrites() {
        return this.signWrites;
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException, CelesteException.AccessControlException, CelesteException.IllegalParameterException,
    CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.NotFoundException, CelesteException.NoSpaceException {
        return celeste.performOperation(this, ois);
    }
}
