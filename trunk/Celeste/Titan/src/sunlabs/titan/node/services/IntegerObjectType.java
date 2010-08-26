/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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
import java.nio.ByteBuffer;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.node.AbstractBeehiveObject;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.MutableObject;

public class IntegerObjectType extends AbstractObjectHandler implements MutableObject.Handler<MutableObject.Handler.ObjectAPI> {
    private static final long serialVersionUID = 1L;
    private final static String name = BeehiveService.makeName(IntegerObjectType.class, IntegerObjectType.serialVersionUID);

    /*
     * Instances of this class are actually object replicas,
     * for which a quorum of a subset need to be examined to determine the actual state of the object.
     */
    public static class IntegerObject extends AbstractBeehiveObject implements MutableObject.Handler.ObjectAPI {
        private static final long serialVersionUID = 1L;

        public IntegerObject(BeehiveObjectId deleteTokenId, long timeToLive) {
            super(IntegerObjectType.class, deleteTokenId, timeToLive);
        }

        @Override
        public BeehiveObjectId getDataId() {
            return new BeehiveObjectId("1".getBytes());
        }
    }

    /**
     * Each object is really an object history.
     * Creating a new MutableObject means we construct and store 3f+2t+1 copies of the same new object.
     */
    public IntegerObjectType.IntegerObject create(BeehiveObjectId objectId) {
        return null;
    }

    public IntegerObjectType.IntegerObject create(BeehiveObject object) {
        return null;
    }

    public IntegerObjectType(BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, IntegerObjectType.name, "Mutable Object");
    }

    public BeehiveMessage setObjectHistory(BeehiveMessage message) {
        return null;
    }

    public BeehiveMessage getObjectHistory(BeehiveMessage message) {
        return null;
    }

    public BeehiveMessage publishObject(BeehiveMessage message) {
        return null;
    }

    public BeehiveMessage unpublishObject(BeehiveMessage message) {
        return null;
    }

    public ByteBuffer setValue(BeehiveObjectId objectId, ByteBuffer predicatedValue, ByteBuffer value) {
        // Should this already be created?
        return null;
    }

    public ByteBuffer getValue(BeehiveObjectId objectId) {
        return null;
    }

    public BeehiveMessage storeLocalObject(BeehiveMessage message) {
        return null;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        return new XHTML.Div("nothing here");
    }
}
