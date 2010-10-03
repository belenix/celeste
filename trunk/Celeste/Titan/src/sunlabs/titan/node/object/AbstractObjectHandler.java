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
package sunlabs.titan.node.object;

import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.util.AbstractStoredMap.OutOfSpace;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.api.Publish;

/**
 * The base class for all Beehive object-handler implementations.
 *
 * This class contains helper methods for implementors.
 */
public abstract class AbstractObjectHandler extends AbstractTitanService implements TitanObjectHandler {
    private final static long serialVersionUID = 1L;

    /**
     * Instantiate a new object handler.
     *
     * @param node
     * @param name
     * @param description
     */
    public AbstractObjectHandler(TitanNode node, String name, String description) throws JMException {
        super(node, name, description);
    }
    
    /**
     * This method transmits the {@link sunlabs.titan.node.services.api.Publish.PublishUnpublishRequest PublishUnpublishRequest}
     * to nodes that are next in line to succeed this node, should this node leave the system.
     *
     * @param handler the {@link AbstractObjectHandler} of the caller.
     * @param publishRequest The incoming {@code Publish.PublishUnpublishRequest}
     *
     * XXX How many copies of a publish record to push around?
     */
    public static void publishObjectBackup(AbstractObjectHandler handler, Publish.PublishUnpublishRequest publishRequest) throws ClassNotFoundException {
    	if (publishRequest.isBackup()) {
    		for (Map.Entry<TitanGuid,TitanObject.Metadata> entry : publishRequest.getObjects().entrySet()) {
    			if (handler.getLogger().isLoggable(Level.FINEST)) {
    				handler.getLogger().finest("backup backpointer %s -> %s", entry.getKey(), publishRequest.getPublisherAddress().getObjectId());
    			}
    			Publishers.PublishRecord record =
    				new Publishers.PublishRecord(entry.getKey(), publishRequest.getPublisherAddress(), entry.getValue(), publishRequest.getSecondsToLive());
    			try {
                    handler.getNode().getObjectPublishers().update(entry.getKey(), record);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    handler.getNode().getObjectPublishers().remove(entry.getKey());
                } catch (IOException e) {
                    handler.getNode().getObjectPublishers().remove(entry.getKey());
                } catch (OutOfSpace e) {
                    handler.getNode().getObjectPublishers().remove(entry.getKey());
                }
    		}
    	} else {
    		SortedSet<NodeAddress> successorSet = handler.getNode().getNeighbourMap().successorSet();
    		NodeAddress[] nodes = new NodeAddress[successorSet.size()];

    		successorSet.toArray(nodes);
    		publishRequest.setBackup(true);
    		for (int i = 0; i < nodes.length && i < 2; i++) {
    			if (!nodes[i].getObjectId().equals(handler.getNode().getNodeId())) {
        			if (handler.getLogger().isLoggable(Level.FINEST)) {
        				handler.getLogger().finest("publish backup to %s", nodes[i].getObjectId());
        			}
    				handler.getNode().sendToNode(nodes[i].getObjectId(), handler.getName(), "publishObject", publishRequest);
    			}
    		}
    	}
    }
}
