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
package sunlabs.titan.node.services;

import java.net.URI;
import java.util.Map;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.services.api.RetrieveObject;

public final class RetrieveObjectService extends AbstractTitanService implements RetrieveObject, RetrieveObjectServiceMBean {
    private final static long serialVersionUID = 1L;
    public final static String name = AbstractTitanService.makeName(RetrieveObjectService.class, RetrieveObjectService.serialVersionUID);

    public RetrieveObjectService(TitanNode node) throws JMException {
        super(node, RetrieveObjectService.name, "Retrieve objects from the object pool");
    }

    public TitanMessage retrieveLocalObject(TitanMessage message) {
        try {
            TitanObject dolrData = this.node.getObjectStore().get(TitanObject.class, message.subjectId);
            return message.composeReply(this.node.getNodeAddress(), dolrData);

//        // For some reason, we failed to get the object that we've been asked
//        // for. Perform a complete delete of that object (whether or not we
//        // actually have it) which will cleanup and intermediate state as well
//        // as transmitting an UnpublishObject message for this object to clean
//        // up any misconceptions that this object is here.
//        this.node.removeLocalObject(message.subjectId);
        } catch (TitanObjectStoreImpl.NotFoundException e) {
            return message.composeReply(this.node.getNodeAddress(), e);

        }
    }

//    public TitanObject retrieveObject(final TitanGuidImpl objectId) {
//        TitanMessage reply = RetrieveObjectService.this.node.sendToObject(objectId, RetrieveObjectService.name, "retrieveLocalObject", objectId);
//        if (!reply.getStatus().isSuccessful())
//            return null;
//
//        try {
//            return reply.getPayload(TitanObject.class, this.node);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        } catch (ClassCastException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        } catch (RemoteException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
}
