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
package sunlabs.titan.node.services;

import java.net.URI;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.services.api.RetrieveObject;

public final class RetrieveObjectService extends BeehiveService implements RetrieveObject, RetrieveObjectServiceMBean {
    private final static long serialVersionUID = 1L;
    public final static String name = BeehiveService.makeName(RetrieveObjectService.class, RetrieveObjectService.serialVersionUID);

    public RetrieveObjectService(BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, RetrieveObjectService.name, "Retrieve objects from the object pool");
    }

    public BeehiveMessage retrieveLocalObject(BeehiveMessage message) {
        try {
            BeehiveObject dolrData = this.node.getObjectStore().get(BeehiveObject.class, message.subjectId);
            return message.composeReply(this.node.getNodeAddress(), dolrData);

//        // For some reason, we failed to get the object that we've been asked
//        // for. Perform a complete delete of that object (whether or not we
//        // actually have it) which will cleanup and intermediate state as well
//        // as transmitting an UnpublishObject message for this object to clean
//        // up any misconceptions that this object is here.
//        this.node.removeLocalObject(message.subjectId);
        } catch (BeehiveObjectStore.NotFoundException e) {
            return message.composeReply(this.node.getNodeAddress(), e);

        }
    }

    public BeehiveObject retrieveObject(final BeehiveObjectId objectId) {
        BeehiveMessage reply = RetrieveObjectService.this.node.sendToObject(objectId, RetrieveObjectService.name, "retrieveLocalObject", objectId);
        if (!reply.getStatus().isSuccessful())
            return null;

        try {
            return reply.getPayload(BeehiveObject.class, this.node);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
}
