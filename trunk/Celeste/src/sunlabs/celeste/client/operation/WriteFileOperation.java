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
import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.filesystem.simple.FileProperties;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;

public class WriteFileOperation extends UpdateOperation {
    private static final long serialVersionUID = 1L;
    public static final String name = "writeFile";

    private int length;
    private long fileOffset;

    /**
     * Creates a {@code WriteFileOperation} object encapsulating the fields
     * given as arguments.  Intended for use by subclass constructors, so that
     * they can supply an {@code operationName} matching their class.
     *
     * @param operationName         a name identifying this kind of operation
     * @param fileIdentifier        the {@link FileIdentifier} of the Celeste file to write.
     * @param agentId               the {@link TitanGuid} of the {@link Credential} authorising the operation
     * @param predicatedVObjectId   the object-id of the  of the version of
     *                              the file this operation is predicated upon
     * @param clientMetaData        client-supplied metadata to be attached to
     *                              the resulting file version
     * @param fileOffset            the offset within the file of the first
     *                              byte to be written
     * @param length                the number of bytes to be written
     */
    protected WriteFileOperation(
            String operationName,
            FileIdentifier fileIdentifier,
            TitanGuid agentId,
            TitanGuid predicatedVObjectId,
            ClientMetaData clientMetaData,
            long fileOffset, int length) {
        super(operationName, fileIdentifier, agentId, predicatedVObjectId, clientMetaData);

        this.fileOffset = fileOffset;
        this.length = length;
    }

    /**
     * Creates a {@code WriteFileOperation} object encapsulating the fields
     * given as arguments.
     *
     * @param fileIdentifier        the {@link FileIdentifier} of the file to write.
     * @param clientId              the object-id of the profiile
     *                              of the principal performing the operation
     * @param predicatedVObjectId   the object-id of the version of
     *                              the file this operation is predicated upon
     * @param clientMetaData        client-supplied metadata to be attached to
     *                              the resulting file version
     * @param fileOffset            the offset within the file of the first
     *                              byte to be written
     * @param length                the number of bytes to be written
     */
    public WriteFileOperation(
            FileIdentifier fileIdentifier,
            TitanGuid clientId,
            TitanGuid predicatedVObjectId,
            ClientMetaData clientMetaData,
            long fileOffset, int length) {
        this(WriteFileOperation.name, fileIdentifier, clientId, predicatedVObjectId, clientMetaData, fileOffset, length);
    }

    public long getFileOffset() {
        return this.fileOffset;
    }

    public int getLength() {
        return this.length;
    }

    @Override
    public TitanGuid getId() {
        TitanGuid id = super.getId()
        .add(Long.toString(this.fileOffset).getBytes())
        .add(Integer.toString(this.length).getBytes())
        ;
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.writeFile;
    }

    @Override
    public String toString() {
        String fileProperties = "";
        try {
            if (this.getClientMetaData().getContext() != null) {
                FileProperties props = new FileProperties(this.getClientMetaData().getContext());
                fileProperties = props.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String result = super.toString()
        + " version=" + Long.toString(WriteFileOperation.serialVersionUID)
        + " fileOffset=" + Long.toString(this.fileOffset)
        + " length=" + Integer.toString(this.length)
        + " props=" + fileProperties
        ;
        return result;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
    	XHTML.Table table = super.toXHTMLTable(uri, props);
        XHTML.Table.Body body = new XHTML.Table.Body();
        body.add(new XHTML.Table.Row(new XHTML.Table.Data("Offset"), new XHTML.Table.Data(this.fileOffset)));
        body.add(new XHTML.Table.Row(new XHTML.Table.Data("Length"), new XHTML.Table.Data(this.length)));
        
        table.add(body);
        return table;
    }
    
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.OutOfDateException, CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }
}
