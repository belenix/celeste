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
 * Please contact Oracle, 16 Network Circle, MenloPark, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.titan.node.object;

import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.TitanObject;

/**
 * {@link TitanObject} and {@link TitanObjectHandler} classes implementing the interfaces specified
 * in this class implement the capability of objects in the object pool to be
 * inspected.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class InspectableObject {
    public interface Handler<T extends InspectableObject.Handler.Object> extends TitanObjectHandler {
        public interface Object extends TitanObjectHandler.ObjectAPI {
            /**
             * Produce an {@link  sunlabs.asdf.web.xml XHTML.EFlow} element consisting of the inspectable elements of this
             * {@link InspectableObject.Handler.Object}.
             * @param uri The HTTP Request URI.
             * @param props URL-encoded properties from the request.
             */
            public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props);
        }
    }
}
