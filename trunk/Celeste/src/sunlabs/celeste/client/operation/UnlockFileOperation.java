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
import sunlabs.titan.api.Credential;

/**
 * Unlock a previously Locked Celeste file.
 * <p>
 * Locks are advisory only.  Any client can unlock a locked file.
 * </p>
 * <p>
 * If an {@code UnlockFileOperation} specifies a token, the token must match the token on the current lock.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class UnlockFileOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "unlockFile";

    private String token;
    
    /**
     * Construct a Celeste UnlockFileOperation.
     * 
     * @param fileIdentifier The {@link FileIdentifier} of the file to unlock.
     * @param clientId The the {@link BeehiveObjectId} of the {@link Credential} authorising the operation.
     * @param vObjectId The {@link BeehiveObjectId} predicate specifying the expected recent version of the file.
     */
    public UnlockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId, String token, BeehiveObjectId vObjectId) {
        super(UnlockFileOperation.name, fileIdentifier, clientId, vObjectId);
        this.token = token;
    }

    /**
     * Construct a Celeste LockFileOperation.
     * 
     * @param fileIdentifier The {@link FileIdentifier} of the file to lock.
     * @param clientId The {@link BeehiveObjectId} of the client's credential 
     * @param token The lock "token" distinguishing this lock from others on the lock stack. 
     */
    public UnlockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId, String token) {
        this(fileIdentifier, clientId, token, null);
    }
    
    /**
     * Construct a Celeste UnlockFileOperation.
     * 
     * @param fileIdentifier The {@link FileIdentifier} of the file to unlock.
     * @param clientId The {@link BeehiveObjectId} of the client's credential 
     */
    public UnlockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId) {
        this(fileIdentifier, clientId, null, null);
    }

    /**
     * Get the client supplied lock token.
     * <p>
     * A {@code null} value indicates that there is no token associated with this operation.
     * </p>
     */
    public String getToken() {
        return this.token;
    }
    
    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileNotLocked, CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.lockFile;
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" token=").append(this.token);
        return result.toString();
    }
}
