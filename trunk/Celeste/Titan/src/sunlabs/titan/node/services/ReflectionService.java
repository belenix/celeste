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
 * Please contact Oracle, 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan.node.services;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.Publishers.PublishRecord;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.InspectableObject;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.api.Reflection;
import sunlabs.titan.node.services.objectstore.PublishDaemon;

public final class ReflectionService extends AbstractTitanService implements Reflection, ReflectionServiceMBean {
    private final static long serialVersionUID = 1L;
    public final static String name = AbstractTitanService.makeName(ReflectionService.class, ReflectionService.serialVersionUID);

    public ReflectionService(TitanNode node) throws JMException {
        super(node, ReflectionService.name, "Beehive Reflection service");
    }

    public TitanObject retrieveObject(TitanMessage message) throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException {
        return this.node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    public TitanObject retrieveObject(final TitanGuid objectId) throws TitanMessage.RemoteException {
        TitanMessage reply = ReflectionService.this.node.sendToObject(objectId, ReflectionService.name, "retrieveObject", objectId);
        if (!reply.getStatus().isSuccessful())
            return null;

        try {
            return reply.getPayload(TitanObject.class, this.node);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public Reflection.ObjectInspect.Response inspectObject(TitanMessage message, Reflection.ObjectInspect.Request request) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException, BeehiveObjectStore.NotFoundException {
        Publish publish = this.node.getService(PublishDaemon.class);
        Set<PublishRecord> publishers = publish.getPublishers(message.subjectId);

        InspectableObject.Handler.Object object = (InspectableObject.Handler.Object) this.node.getObjectStore().get(TitanObject.class, message.subjectId);

        Reflection.ObjectInspect.Response response = new Reflection.ObjectInspect.Response(object.inspectAsXHTML(request.getUri(), request.getProps()), publishers);

        return response;
    }
    
    public XHTML.EFlow inspectObject(TitanGuid objectId, URI uri, Map<String,HTTP.Message> props) throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException {
    	Reflection.ObjectInspect.Request request = new Reflection.ObjectInspect.Request(objectId, uri, props);

    	if (this.log.isLoggable(Level.FINE)) {
    		this.log.fine("%s", uri);
    	}
    	TitanMessage reply = ReflectionService.this.node.sendToObject(objectId, ReflectionService.name, "inspectObject", request);

    	Reflection.ObjectInspect.Response response;
    	try {
    		response = reply.getPayload(Reflection.ObjectInspect.Response.class, this.node);
    		
    		XHTML.Table.Head thead = new XHTML.Table.Head();
    		thead.add(new XHTML.Table.Row(new XHTML.Table.Heading("Node Id"),
    				new XHTML.Table.Heading("Expires"),
    				new XHTML.Table.Heading("Time To Live"),
    				new XHTML.Table.Heading("Object Type")));
    		
    		XHTML.Table.Body tbody = new XHTML.Table.Body();
    		for (PublishRecord p : response.getPublisher()) {
    			tbody.add(new XHTML.Table.Row(p.toXHTMLTableData()));
    		}
    		return new XHTML.Div(new XHTML.Table(new XHTML.Table.Caption("Publishers"), tbody).setClass("Publishers"), response.getXhtml()).setClass("section");
    	} catch (TitanMessage.RemoteException e) {
    		if (e.getCause() instanceof BeehiveObjectStore.NotFoundException) {
    			throw new BeehiveObjectStore.NotFoundException(e);
    		}
    		throw new RuntimeException(e);
    	}
    }

    public String getObjectType(TitanGuid objectId) throws ClassCastException, ClassNotFoundException, RemoteException {
        return this.getObjectType(new ReflectionService.ObjectType.Request(new TitanGuidImpl(objectId))).getObjectType();
    }

    private ObjectType.Response getObjectType(ObjectType.Request request) throws ClassCastException, ClassNotFoundException, RemoteException {
        TitanMessage reply = this.node.sendToObject(request.getObjectId(), ReflectionService.name, "getObjectType", request);

        return reply.getPayload(ObjectType.Response.class, node);
    }

    public Reflection.ObjectType.Response getObjectType(TitanMessage message) throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException, TitanMessage.RemoteException {
            Reflection.ObjectType.Request request = message.getPayload(Reflection.ObjectType.Request.class, node);
            TitanGuid objectId = request.getObjectId();
            TitanObject object = this.node.getObjectStore().get(TitanObject.class, objectId);

            Reflection.ObjectType.Response response = new Reflection.ObjectType.Response(object.getObjectType());
            return response;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
}
