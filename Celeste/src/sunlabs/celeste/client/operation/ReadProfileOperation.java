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
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.BeehiveObjectId;

public class ReadProfileOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 3L;

    public static final String name = "read-credential";

    /**
     * Creates a {@code ReadProfileOperation} object for reading the credential
     * named by {@code credentialId}.
     *
     * @param credentialId the {@link BeehiveObjectId} of the {@code Credential} to retrieve
     */
    public ReadProfileOperation(BeehiveObjectId credentialId) {
        super(ReadProfileOperation.name, new FileIdentifier(credentialId, BeehiveObjectId.ZERO), BeehiveObjectId.ZERO, null);
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId();
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.readProfile;
    }

    public BeehiveObjectId getCredentialId() {
        return this.getFileIdentifier().getNameSpaceId();
    }
    
    public String toString() {
        String result = super.toString()
        + " version=" + Long.toString(ReadProfileOperation.serialVersionUID)
        + " credentialId=" + this.clientId;
        return result;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException, CelesteException.CredentialException,
           CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException {
        return celeste.performOperation(this, ois);
    }
}
