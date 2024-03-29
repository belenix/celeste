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
package sunlabs.celeste.node.object;

// XXX Eliminate this.  Beehive must define it's own access control from while applications (like Celeste) derive.
import sunlabs.celeste.node.CelesteACL;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.object.TitanObjectHandler;

/**
 * {@link BeehiveObject} and {@link BeehiveObjectHander} classes implementing the interfaces specified
 * in this class implement the capability of objects in the object pool to be
 * deleted.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class AccessControlledObject {

    public interface Handler<T extends AccessControlledObject.Handler.Object> {
        public interface Object extends TitanObjectHandler.ObjectAPI {
            /**
             * Check that the given client identifier can perform the given operation {@code privilege} on this object.
             *
             * @param clientId
             * @param privilege
             * @return {@code true} if access is permitted.
             */
            //
            // XXX: Should have void return type and throw
            //      BeehiveException.AccessDenied if the access check fails.
            //
            public boolean checkAccess(TitanGuid clientId, CelesteACL.CelesteOps privilege);
        }
    }
}
