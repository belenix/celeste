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
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.BeehiveObjectId;

public class NewCredentialOperation extends UpdateOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "NewCredential";

    private BeehiveObjectId credentialId;
    private ReplicationParameters replicationParams;

    /**
     * Use this constructor to aggregate the necessary meta-data for creating
     * a credential.
     *
     * @param credentialId
     * @param deleteTokenId
     * @param replicationParams
     */
    public NewCredentialOperation(BeehiveObjectId credentialId, BeehiveObjectId deleteTokenId, String replicationParams) {
        this(credentialId, deleteTokenId, new ReplicationParameters(replicationParams));
    }

    public NewCredentialOperation(BeehiveObjectId credentialId, BeehiveObjectId deleteTokenId, ReplicationParameters replicationParams) {
        //
        // A credential defines its own name space; hence the first use of
        // credentialId below.  As a bootstrapping measure, it's created under
        // its own authority, which accounts for the second use.
        //
        super(NewCredentialOperation.name, new FileIdentifier(credentialId, BeehiveObjectId.ZERO), credentialId, null, new ClientMetaData());
        this.credentialId = credentialId;
        this.replicationParams = replicationParams;
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId()
        .add(this.credentialId)
        .add(this.replicationParams.toByteArray())
        ;
        return id;
    }

    //
    // N.B.  Stating that newProfile is required as a privilege to invoke the
    // corresponding operation is a bit of a lie, since no ACL check is
    // actually performed on the operation (there being no existing
    // ACL-bearing entity to check against).
    //
    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.newProfile;
    }

    @Override
    public String toString() {
        String result = super.toString()
        + " credentialId=" + this.credentialId;
        return result;
    }

    public BeehiveObjectId getProfileId() {
        return this.credentialId;
    }

    public ReplicationParameters getReplicationParameters() {
        return this.replicationParams;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
        throws IOException, ClassNotFoundException, CelesteException.AccessControlException,
           CelesteException.CredentialException, CelesteException.IllegalParameterException,
           CelesteException.AlreadyExistsException, CelesteException.RuntimeException,
           CelesteException.NoSpaceException, CelesteException.VerificationException {
        return celeste.performOperation(this, ois);
    }
}
