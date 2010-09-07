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
package sunlabs.celeste.client.operation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL.CelesteOps;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.TitanGuid;

public class InspectLockOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;
    public static final String name = "inspectLock";

    /**
     * @param fileIdentifier The {@link FileIdentifier} of the file lock to inspect.
     * @param clientId The {@link TitanGuid} of the client's credential.
     */
    public InspectLockOperation(FileIdentifier fileIdentifier, TitanGuid clientId) {
        super(InspectLockOperation.name, fileIdentifier, clientId, null);
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }

    @Override
    public CelesteOps getRequiredPrivilege() {
        // TODO Auto-generated method stub
        return null;
    }

}
