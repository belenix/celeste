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
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;

/**
 * Read a Celeste file.
 * 
 */
public class ReadFileOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "readFile";

    private final long offset;
    private final int length;

    /**
     * Creates a {@code ReadFileOperation} object encapsulating the fields
     * given as arguments.  Intended for use by subclass constructors, so that
     * they can supply an {@code operationName} matching their class.
     *
     * @param fileIdentifier The {@link FileIdentifier} of the file.
     * @param clientId      the {@link TitanGuid} of the {@link Credential} authorising the operation
     * @param vObjectId     the object-id of the version of the
     *                      file this operation is to read, or {@code null} if
     *                      it is to read the latest version
     * @param offset        the starting offset within the file of the span to
     *                      be read
     * @param length        the length of the span to be read (or {@code -1L}
     *                      to read to the end of file)
     */
    protected ReadFileOperation(String operationName, FileIdentifier fileIdentifier, TitanGuid clientId, TitanGuid vObjectId, long offset, int length) {
        super(operationName, fileIdentifier, clientId, vObjectId);
        this.offset = offset;
        this.length = length;
    }

    /**
     * Creates a {@code ReadFileOperation} for reading the a particular version
     * of a Celeste file.
     *
     * @param fileIdentifier The {@link FileIdentifier} of the file.
     * @param clientId      the {@link TitanGuid} of the {@link Credential} authorising the operation
     * @param vObjectId     the object-id of the version of the
     *                      file this operation is to read, or {@code null} if
     *                      it is to read the latest version
     * @param offset        the starting offset within the file of the span to
     *                      be read
     * @param length        the length of the span to be read (or {@code -1L}
     *                      to read to the end of file)
     */
    public ReadFileOperation(FileIdentifier fileIdentifier, TitanGuid clientId, TitanGuid vObjectId, long offset, int length) {
        this(ReadFileOperation.name, fileIdentifier, clientId, vObjectId, offset, length);
    }


    /**
     * Creates a {@code ReadFileOperation} for reading the current version
     * of a Celeste file.
     *
     * @param fileIdentifier The {@link FileIdentifier} of the file.
     * @param clientId      the {@link TitanGuid} of the {@link Credential} authorising the operation
     * @param offset        the starting offset within the file of the span to
     *                      be read.
     * @param length        the length of the span to be read (or {@code -1L}
     *                      to read to the end of file).
     */
    public ReadFileOperation(FileIdentifier fileIdentifier, TitanGuid clientId, long offset, int length) {
        this(fileIdentifier, clientId, null, offset, length);
    }
    
    @Override
    public TitanGuid getId() {
        TitanGuid id = super.getId();
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.readFile;
    }

    public String toString() {
        String result = super.toString()
        + " version=" + Long.toString(ReadFileOperation.serialVersionUID)
        + " offset=" + this.offset
        + " length=" + this.length;
        return result;
    }

    public long getOffset() {
        return this.offset;
    }

    public int getLength() {
        return this.length;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.DeletedException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException {
        return celeste.performOperation(this, ois);
    }
}
