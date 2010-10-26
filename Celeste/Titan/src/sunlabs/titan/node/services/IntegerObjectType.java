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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.MutableObject;
import sunlabs.titan.node.services.api.Publish;

public class IntegerObjectType extends AbstractObjectHandler implements MutableObject.Handler<MutableObject.Handler.ObjectAPI> {
    private static final long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(IntegerObjectType.class, IntegerObjectType.serialVersionUID);

    /*
     * Instances of this class are actually object replicas,
     * for which a quorum of a subset need to be examined to determine the actual state of the object.
     */
    public static class IntegerObject extends AbstractTitanObject implements MutableObject.Handler.ObjectAPI {
        private static final long serialVersionUID = 1L;

        public IntegerObject(TitanGuid deleteTokenId, long timeToLive) {
            super(IntegerObjectType.class, deleteTokenId, timeToLive);
        }

        @Override
        public TitanGuid getDataId() {
            return new TitanGuidImpl("1".getBytes());
        }
    }

    /**
     * Each object is really an object history.
     * Creating a new MutableObject means we construct and store 3f+2t+1 copies of the same new object.
     */
    public IntegerObjectType.IntegerObject create(TitanGuid objectId) {
        return null;
    }

    public IntegerObjectType.IntegerObject create(TitanObject object) {
        return null;
    }
    
    public MutableObject.CreateOperation.Response createObjectHistory(TitanMessage message, MutableObject.CreateOperation.Request request) throws ClassCastException, ClassNotFoundException {

        if (this.log.isLoggable(Level.FINE)) {
            this.log.info("%s", request.getReplicaId());
        }        MutableObject.CreateOperation.Response result = new MutableObject.CreateOperation.Response(null);
        return result;
    }

    public IntegerObjectType(TitanNode node) throws JMException {
        super(node, IntegerObjectType.name, "Mutable Object");
    }

    public MutableObject.SetOperation.Response setObjectHistory(TitanMessage message, MutableObject.SetOperation.Request request) {
        return null;
    }

    public MutableObject.GetOperation.Response getObjectHistory(TitanMessage message, MutableObject.GetOperation.Request request) {
        return null;
    }

    public Publish.PublishUnpublishResponse publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public ByteBuffer setValue(TitanGuid objectId, ByteBuffer predicatedValue, ByteBuffer value) {
        // Should this already be created?
        return null;
    }

    public ByteBuffer getValue(TitanGuid objectId) {
        return null;
    }

    public TitanMessage storeLocalObject(TitanMessage message) {
        return null;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        return new XHTML.Div("nothing here");
    }
}
