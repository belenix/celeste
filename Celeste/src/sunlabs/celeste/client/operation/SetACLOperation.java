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

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;

public class SetACLOperation  extends UpdateOperation{
    private static final long serialVersionUID = 1L;

    public static final String name = "set-ACL";

    private final CelesteACL acl;

    /**
     * Creates a {@code SetUserACLOperation} object encapsulating the fields
     * given as arguments.
     *
     * @param fileIdentifier    The {@link FileIdentifier} of the file to set ACL information.
     * @param clientId              the {@link TitanGuid} of the {@link Credential} authorising the operation
     * @param predicatedVObjectId   the object-id of the version of
     *                              the file this operation is predicated upon
     * @param clientMetaData        client-supplied metadata to be attached to
     *                              the resulting file version
     * @param acl                   the new {@code CelesteACL} instance that
     *                              is to replace the existing one
     */
    public SetACLOperation(FileIdentifier fileIdentifier, TitanGuid clientId, TitanGuid predicatedVObjectId, ClientMetaData clientMetaData, CelesteACL acl) {
        super(SetACLOperation.name, fileIdentifier, clientId, predicatedVObjectId, clientMetaData);
        this.acl = acl;
    }

    public CelesteACL getACL() {
        return this.acl;
    }

    @Override
    public TitanGuid getId() {
        TitanGuid id = super.getId()
        .add(this.getACL().toByteArray());
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.setACL;
    }

    @Override
    public String toString() {
        return String.format(
                "%s acl=[%s]",
                super.toString(),
                (this.acl != null) ? this.acl.toString() : "[null]");
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }
}

