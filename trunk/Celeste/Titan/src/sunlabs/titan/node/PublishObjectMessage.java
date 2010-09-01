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
package sunlabs.titan.node;

import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.services.PublishDaemon;

/**
 * Compose the Beehive Publish-Object message.
 * 
 * PublishObjectMessages are always marked as multicast messages and while multicast in nature
 * (ie. processed at each hop along the route to the destination node),  aren't really handled
 * like multicast messages in that only the destination node invokes the published object {@link AbstractObjectHandler}.
 * True multicast messages are received by the associated {@link AbstractObjectHandler} at each node all along the routing path.
 * </p>
 */
public final class PublishObjectMessage extends BeehiveMessage {
    private final static long serialVersionUID = 1L;

    public PublishObjectMessage(
            NodeAddress source,
            TitanGuid destination,
            String subjectClass,
            String subjectClassMethod,
            PublishDaemon.PublishObject.Request publishRequest) {
        super(BeehiveMessage.Type.PublishObject,
                source,
                new TitanNodeIdImpl(destination),
                destination,
                subjectClass,
                subjectClassMethod,
                BeehiveMessage.Transmission.MULTICAST,
                BeehiveMessage.Route.LOOSELY,
                publishRequest);
    }
}
