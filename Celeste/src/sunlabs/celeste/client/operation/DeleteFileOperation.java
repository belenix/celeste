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
import sunlabs.titan.api.TitanGuid;

public class DeleteFileOperation extends AbstractCelesteOperation {
    private final static long serialVersionUID = 1L;
    public final static String name = "delete-file";

    private TitanGuid deleteToken;
    private long timeToLive;

    public DeleteFileOperation(FileIdentifier fileIdentifier, TitanGuid clientId, TitanGuid deleteToken, long timeToLive) {
        super(DeleteFileOperation.name, fileIdentifier, clientId, null);
        this.deleteToken = deleteToken;
        this.timeToLive = timeToLive;
    }

    public TitanGuid getDeleteToken() {
        return this.deleteToken;
    }
    
    public long getTimeToLive() {
        return this.timeToLive;
    }
    
    @Override
    public TitanGuid getId() {
        TitanGuid id = super.getId()
        .add(this.deleteToken);
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.deleteFile;
    }
    
    public String toString() {
        return super.toString() + " deleteToken=" + this.deleteToken;
    }
    
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
        CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException  {
        return celeste.performOperation(this, ois);
    }
}
