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

package sunlabs.celeste.node.services.object;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.node.BeehiveObjectStore;
import sunlabs.beehive.node.object.DeleteableObject;
import sunlabs.beehive.node.object.RetrievableObject;
import sunlabs.beehive.node.object.StorableObject;
import sunlabs.celeste.client.ReplicationParameters;

/**
 * Methods used by the FObject Application.
 */
public interface FObjectType extends
        StorableObject.Handler<FObjectType.FObject>,
        RetrievableObject.Handler<FObjectType.FObject>,
//        InspectableObject.Handler<FObjectType.FObject>,
        DeleteableObject.Handler<FObjectType.FObject> {

    public static interface FObject extends StorableObject.Handler.Object, RetrievableObject.Handler.Object, DeleteableObject.Handler.Object/*, InspectableObject.Handler.Object*/ {
        public byte[] getContents();

        public void delete(BeehiveObjectId profferedDeleteToken, long timeToLive)
        throws BeehiveObjectStore.DeleteTokenException;

        public int getReplicationMinimum();

        public int getReplicationCache();
    }

    public FObjectType.FObject create(BeehiveObjectId deleteTokenId, long timeToLive, ReplicationParameters replicationParams, BeehiveObject.Metadata metaData, byte[] data);
}
