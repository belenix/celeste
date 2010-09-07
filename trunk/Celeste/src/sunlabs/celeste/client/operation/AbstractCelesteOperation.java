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

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;

/**
 * The base class implementing the {@link CelesteOperation} interface.
 *
 */
public abstract class AbstractCelesteOperation implements CelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "celeste-context";

    /**
     *
     */
    public static class OperationException extends Exception {
        private static final long serialVersionUID = 1L;

        public OperationException(String message) {
            super(message);
        }

        public OperationException(Throwable reason) {
            super(reason);
        }
    }

    protected String operationName;
    protected FileIdentifier fileIdentifier;
    protected TitanGuid clientId;
    protected TitanGuid predicatedVersionId;


    public AbstractCelesteOperation(String operationName, FileIdentifier fileIdentifier, TitanGuid clientId, TitanGuid predicatedVersionId) {
        this.operationName = operationName;
        this.fileIdentifier = fileIdentifier;
        this.clientId = clientId;
        this.predicatedVersionId = predicatedVersionId;
    }

    protected void setOperationName(String name) {
        this.operationName = name;
    }

    /**
     * Return a {@link TitanGuid} based on the content of this operation.
     */
    public TitanGuid getId() {
        TitanGuid result = new TitanGuidImpl(this.operationName.getBytes())
            .add(this.fileIdentifier.getNameSpaceId())
            .add(this.fileIdentifier.getFileId())
            .add(this.clientId)
            .add(this.predicatedVersionId);
        return result;
    }

    public String getOperationName() {
        return this.operationName;
    }

    public TitanGuid getClientId() {
        return this.clientId;
    }

    public TitanGuid getVObjectId() {
        return this.predicatedVersionId;
    }

    /**
     * Return the access control privilege required to invoke this operation.
     * </p>
     * <p>
     * Each concrete subclass must implement this method by mapping it onto
     * the corresponding privilege defined by the {@code
     * CelesteACL.CelesteOps} enumeration.
     * </p>
     *
     * @return  the privilege required to invoke this operation
     */
    public abstract CelesteACL.CelesteOps getRequiredPrivilege();

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(this.operationName)
            .append(": {").append(this.fileIdentifier).append(" } clientId=").append(this.clientId);
        result.append(" vObjectId=").append(this.predicatedVersionId);
        return result.toString();
    }
    
    /**
     * Produce an XHTML.Table containing the relevant fields in this class.
     * Sub-classes may use {@code super.toXHTMLTable(URI uri, Map<String,HttpMessage> props)} to generate an {@code XHTML.Table}
     * instance containing the fields from this super-class and may append additional rows to the XHTML.Table instance.
     * 
     */
    public XHTML.Table toXHTMLTable(URI uri, Map<String,HTTP.Message> props) {        
        XHTML.Table.Caption caption = new XHTML.Table.Caption("File Operation");
        XHTML.Table.Head thead = new XHTML.Table.Head();
        XHTML.Table.Body tbody = new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data("Name"), new XHTML.Table.Data(this.operationName)),
				new XHTML.Table.Row(new XHTML.Table.Data("File"), new XHTML.Table.Data(this.fileIdentifier)),
				new XHTML.Table.Row(new XHTML.Table.Data("ClientId"), new XHTML.Table.Data(this.clientId)),
				new XHTML.Table.Row(new XHTML.Table.Data("VersionObject"), new XHTML.Table.Data(this.predicatedVersionId)));
        
        XHTML.Table table = new XHTML.Table(caption, thead, tbody).setClass("FileOperation");
        return table;
    }

    public FileIdentifier getFileIdentifier() {
        return this.fileIdentifier;
    }
    
    abstract public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws Exception ;
}
