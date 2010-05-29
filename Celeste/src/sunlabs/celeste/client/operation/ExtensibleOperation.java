/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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
import java.net.URL;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;

public class ExtensibleOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "extension";

    private URL[] jarFileURLs;
    private String[] args;

    /**
     * Creates a {@code ExtensibleOperation} object encapsulating the fields given as arguments.
     *
     * @param fileIdentifier the {@link FileIdentifier} of the file
     * @param credentialId   the {@link BeehiveObjectId} of the {@link Credential} authorised to perform this operation
     * @param jarFileURLs    an array of {@link URL} instances refering to Java Jar files to load classes
     * @param args           an array of arbitrary, user-supplied Strings passed to the extension when it is started.
     */
    public ExtensibleOperation(FileIdentifier fileIdentifier, BeehiveObjectId credentialId, URL[] jarFileURLs, String[] args) {
        super(ExtensibleOperation.name, fileIdentifier, credentialId, null);
        this.jarFileURLs = jarFileURLs;
        this.args = args;
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId();
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.readFile;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("ExtensibleOperation ").append(super.toString());
        result.append(" version=").append(ExtensibleOperation.serialVersionUID);
        for (URL u : this.jarFileURLs) {
            result.append(" ").append(u.toString());
        }

        return result.toString();
    }
    /**
     * Get the array of {@link URL} instances that point to Java Jar files related to executing this operation.
     */
    public URL[] getJarFileURLs() {
        return this.jarFileURLs;
    }
    
    /**
     * Get the array of {@link String} instances that contain the client supplied arguments.
     */
    public String[] getArgs() {
    	return this.args;
    }
    
    /**
     * Set the array of {@link String} instances that contain the client supplied arguments.
     */
    public void setArgs(String[] args) {
    	this.args = args;
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.AccessControlException, CelesteException.VerificationException, CelesteException.CredentialException, CelesteException.NotFoundException,
        CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {
        return celeste.performOperation(this, ois);
    }
}
