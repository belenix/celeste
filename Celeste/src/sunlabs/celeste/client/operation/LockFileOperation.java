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

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;

/**
 * Lock a Celeste file.
 * <p>
 * Locks may be recursive if the new lock is from the same client identifier as the current lock.
 * Unlocking a lock requires an equal number of {@link UnlockFileOperation}s.
 * </p>
 * <p>
 * Locks may be set with a corresponding, client supplied, lock token.
 * A subquent recursive lock must have this same token to be successful, otherwise the operation will throw {@link CelesteException.FileLocked}.
 * </p>
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class LockFileOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "lockFile";

    public enum Type { WRITE, READWRITE };

    private Type type;

    private String token;

    private Serializable annotation;

    /**
     * Construct a Celeste LockFileOperation.
     * <p>
     * A lock can only be established on the most recent version of a file.
     * </p>
     *
     * @param fileIdentifier    the {@link FileIdentifier} of the file to lock
     * @param clientId          the {@link BeehiveObjectId} of the client's
     *                          credential
     * @param vObjectId         a {@link BeehiveObjectId} predicate
     *                          specifying the version of the file expected to
     *                          be current
     * @param annotation        uninterpreted client-supplied information
     *                          associated with the lock
     */
    public LockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId, String token,
            BeehiveObjectId vObjectId, Serializable annotation) {
        super(LockFileOperation.name, fileIdentifier, clientId, vObjectId);
        this.type = Type.WRITE;
        this.token = token;
        this.annotation = annotation;
    }

    /**
     * Construct a Celeste LockFileOperation.
     * <p>
     * A lock can only be established on the most recent version of a file.
     * </p>
     * @param fileIdentifier The {@link FileIdentifier} of the file to lock.
     * @param clientId The {@link BeehiveObjectId} of the client's credential.
     * @param vObjectId The {@link BeehiveObjectId} predicate specifying the expected recent version of the file.
     */
    public LockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId, String token, BeehiveObjectId vObjectId) {
        this(fileIdentifier, clientId, token, vObjectId, null);
    }

    /**
     * Construct a Celeste LockFileOperation.
     *
     * @param fileIdentifier The {@link FileIdentifier} of the file to lock.
     * @param clientId The {@link BeehiveObjectId} of the client's credential
     * @param token The lock "token" distinguishing this lock from others on the lock stack.
     */
    public LockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId, String token) {
        this(fileIdentifier, clientId, token, null);
    }

    /**
     * Construct a Celeste LockFileOperation
     * @param fileIdentifier The {@link FileIdentifier} of the file to lock.
     * @param clientId The {@link BeehiveObjectId} of the client's credential
     */
    public LockFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId) {
        this(fileIdentifier, clientId, null, null);
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.lockFile;
    }

    public Type getType() {
        return this.type;
    }

    /**
     * Get the client supplied lock token.
     * <p>
     * A {@code null} value indicates that there is no token associated with this operation.
     * </p>
     * <p>
     * Note that a recursive lock must have the same client identifier and lock token.
     * </p>
     */
    public String getToken() {
        return this.token;
    }

    /**
     * Get the client supplied data to associate with this lock.
     * <p>
     * A {@code null} value indicates that there is no data to associate with this operation.
     * </p>
     */
    public Serializable getClientAnnotation() {
        return this.annotation;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" type=").append(this.type).append(" token=").append(this.token);
        if (this.annotation != null)
            result.append("annotation=").append(this.annotation.toString());
        return result.toString();
    }
}
