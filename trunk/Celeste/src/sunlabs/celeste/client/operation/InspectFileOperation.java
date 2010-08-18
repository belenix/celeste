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
import sunlabs.titan.BeehiveObjectId;

public class InspectFileOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "inspectFile";

    /**
     * Obtain the metadata for a Celeste file.
     * 
     * @param fileIdentifier the {@link FileIdentifier} of the file.
     * @param authorisationId the {@link BeehiveObjectId} of the {@link Credential} authorising this operation
     */
    public InspectFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId authorisationId, BeehiveObjectId vObjectId) {
        super(InspectFileOperation.name, fileIdentifier, authorisationId, vObjectId);
    }

    /**
     * Obtain the metadata for a Celeste file.
     * 
     * @param fileIdentifier the {@link FileIdentifier} of the file.
     * @param authorisationId the {@link BeehiveObjectId} of the {@link Credential} authorising this operation
     */
    public InspectFileOperation(FileIdentifier fileIdentifier, BeehiveObjectId authorisationId) {
        this(fileIdentifier, authorisationId, null);
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId();
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.inspectFile;
    }

    public String toString() {
        String result = super.toString()
        + " version=" + Long.toString(InspectFileOperation.serialVersionUID);
        return result;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois)
    throws IOException, ClassNotFoundException, CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException  {
        return celeste.performOperation(this, ois);
    }
}
