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

import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.titan.api.TitanGuid;

/**
 * {@code UpdateOperation} is the base class for all operations that update or
 * create a Celeste file.  It adds client-supplied metadata to the basic
 * {@link AbstractCelesteOperation} class.
 */
//
// N.B.  Does not override toString(), since the ClientMetaData class doesn't
// provide a customized version of that method, and thus adding the output of
// Object.toString() for that metadata isn't informative.
//
public abstract class UpdateOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    private final ClientMetaData clientMetaData;

    public UpdateOperation(
            String operationName,
            FileIdentifier fileIdentifier,
            TitanGuid clientId,
            TitanGuid predicatedVersionId,
            ClientMetaData clientMetaData) {
        super(operationName, fileIdentifier, clientId, predicatedVersionId);
        this.clientMetaData = clientMetaData;
    }

    public ClientMetaData getClientMetaData() {
        return this.clientMetaData;
    }

    @Override
    public TitanGuid getId() {
        return super.getId().add(this.clientMetaData.getId());
    }
}
