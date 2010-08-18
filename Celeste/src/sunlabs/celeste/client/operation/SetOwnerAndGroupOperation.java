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

package sunlabs.celeste.client.operation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.BeehiveObjectId;

public class SetOwnerAndGroupOperation extends UpdateOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "set-user-and-group";

    private final BeehiveObjectId ownerId;
    private final BeehiveObjectId groupId;

    /**
     * Creates a {@code SetUserAndGroupOperation} object encapsulating the
     * fields given as arguments.
     *
     * @param fileIdentifier    The {@link FileIdentifier} of the file to set owner and group information.
     * @param clientId              the {@link BeehiveObjectId} of the {@link Credential} authorising the operation
     * @param predicatedVObjectId   the object-id of the version of
     *                              the file this operation is predicated upon
     * @param clientMetaData        client-supplied metadata to be attached to
     *                              the resulting file version
     * @param ownerId               the object-id of the file's new
     *                              owner
     * @param groupId               the object-id of the file's new
     *                              group
     */
    public SetOwnerAndGroupOperation(
            FileIdentifier fileIdentifier,
            BeehiveObjectId clientId,
            BeehiveObjectId predicatedVObjectId,
            ClientMetaData clientMetaData,
            BeehiveObjectId ownerId,
            BeehiveObjectId groupId) {
        super(SetOwnerAndGroupOperation.name, fileIdentifier, clientId, predicatedVObjectId, clientMetaData);
        this.ownerId = ownerId;
        this.groupId = groupId;
    }

    public BeehiveObjectId getOwnerId() {
        return this.ownerId;
    }

    public BeehiveObjectId getGroupId() {
        return this.groupId;
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId()
        .add(this.getOwnerId())
        .add(this.getGroupId());
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.setUserAndGroup;
    }

    @Override
    public String toString() {
        return String.format(
                "%s ownerId=%s groupId=%s",
                super.toString(), 
                (this.ownerId != null) ? this.ownerId.toString() : "[null]",
                        (this.groupId != null) ? this.groupId.toString() : "[null]");
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }
}
