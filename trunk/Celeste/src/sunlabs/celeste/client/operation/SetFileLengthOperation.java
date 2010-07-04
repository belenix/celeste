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
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;


public class SetFileLengthOperation extends UpdateOperation {
    private static final long serialVersionUID = 1L;
    public static final String name = "set-file-length";

    private long length;

    /**
     * Use this constructor to encode the necessary meta-data information for
     * Celeste whenever you do a file update.
     *
     * @param fileIdentifier The {@link FileIdentifier} of the file to set length.
     * @param agentId the {@link BeehiveObjectId} of the {@link Credential} authorising the operation
     * @param predicatedVObjectId
     * @param length
     */
    public SetFileLengthOperation(FileIdentifier fileIdentifier, BeehiveObjectId agentId, BeehiveObjectId predicatedVObjectId, ClientMetaData clientMetaData, long length) {
        super(SetFileLengthOperation.name, fileIdentifier, agentId, predicatedVObjectId, clientMetaData);
        
        this.length = length;
    }

    public long getLength() {
        return this.length;
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId()
        .add(Long.toString(this.length));
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.setFileLength;
    }

    @Override
    public String toString() {
        String result = super.toString() 
        + " Length=" + Long.toString(this.length);
        return result;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.NoSpaceException,
        CelesteException.VerificationException, CelesteException.OutOfDateException, CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }
}
