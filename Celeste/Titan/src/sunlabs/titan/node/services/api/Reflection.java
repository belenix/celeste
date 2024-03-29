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
package sunlabs.titan.node.services.api;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanObjectStoreImpl.NotFoundException;
import sunlabs.titan.node.Publishers.PublishRecord;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;

public interface Reflection extends TitanService {
	@Deprecated
    public TitanObject retrieveObject(final TitanGuid objectId) throws RemoteException;

    public static class ObjectType {
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;

            private TitanGuid objectId;

            public Request(TitanGuid objectId) {
                this.objectId = objectId;
            }

            public TitanGuid getObjectId() {
                return this.objectId;
            }
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;

            private String objectType;

            public Response(String type) {
                this.objectType = type;
            }

            public String getObjectType() {
                return this.objectType;
            }
        }
    }

    public static class ObjectInspect {
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;

            private TitanGuid objectId;
            private URI uri;
            private Map<String,HTTP.Message> props;

            public Request(TitanGuid objectId, URI uri, Map<String,HTTP.Message> props) {
                this.objectId = objectId;
                this.uri = uri;
                this.props = props;                
            }

            public TitanGuid getObjectId() {
                return this.objectId;
            }

			public URI getUri() {
				return this.uri;
			}

			public Map<String, HTTP.Message> getProps() {
				return this.props;
			}
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;

            private XHTML.EFlow xhtml;
            private Set<PublishRecord> publishers;

            public Response(XHTML.EFlow xhtml, Set<PublishRecord> publishers) {
                this.xhtml = xhtml;
                this.publishers = publishers;
            }

            public XHTML.EFlow getXhtml() {
                return this.xhtml;
            }

            public Set<PublishRecord> getPublisher() {
                return this.publishers;
            }
        }
    }
    
    public ObjectInspect.Response inspectObject(TitanMessage message, Reflection.ObjectInspect.Request request) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException, NotFoundException;
    
    public XHTML.EFlow inspectObject(TitanGuid objectId, URI uri, Map<String,HTTP.Message> props) throws ClassCastException, ClassNotFoundException, TitanObjectStoreImpl.NotFoundException;
    
    /**
     * Get the name of the type of the object identified by {@link TitanGuid} {@code objectId}.
     * 
     * @param objectId
     * @return the String name of the type of the {@link TitanObject} specified by the parameter {@code objectId}.
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws RemoteException 
     */
    public String getObjectType(final TitanGuid objectId) throws ClassCastException, ClassNotFoundException, RemoteException;

    /**
     * Responder to a BeehiveMessage sent to this node.
     * 
     * @param message the received BeehiveMessage containing an instance of {@link Reflection.ObjectInspect.Request} as the payload.
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws TitanObjectStoreImpl.NotFoundException
     * @throws TitanMessage.RemoteException 
     */
    public ObjectType.Response getObjectType(final TitanMessage message) throws ClassCastException, ClassNotFoundException, TitanObjectStoreImpl.NotFoundException, TitanMessage.RemoteException;
}
