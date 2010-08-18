/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.titan.node.services.object;

import sunlabs.titan.api.Credential;
import sunlabs.titan.node.object.InspectableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;

//
// This interface captures the interactions of credentials in their Beehive
// object aspect with Beehive.
//
// Note that credentials are not deletable.
//
public interface CredentialObject extends
        StorableObject.Handler<Credential>,
        RetrievableObject.Handler<Credential>,
        InspectableObject.Handler<Credential>
{

    public interface Object {
        /**
         * The property name of the number of copies of the object to store
         * before the object is considered committed to the object pool.
         */
        public final static String REPLICATIONPARAM_STORE_NAME = "Credential.Replication.Store";

        /**
         * The property name of the number of copies of the object that are to
         * be maintained automatically in the object pool.
         * (UNIMPLEMENTED-TBD).
         */
        public final static String REPLICATIONPARAM_LOWWATER_NAME = "Credential.Replication.LowWater";

    }

    //
    // A typical interface of this sort will define an ___API interface here
    // that describes the local behavior of its corresponding object.  In our
    // case, that interface is Credential, which is defined as a standalone
    // interface rather than being nested here.
    //
}
