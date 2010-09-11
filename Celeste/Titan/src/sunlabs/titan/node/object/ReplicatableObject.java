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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.services.api.Publish;

/**
 * A ReplicatableObject is an object in the object pool that is replicated and the pool
 * maintains a lower and upper bound on the number of copies of the object in the pool.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class ReplicatableObject {
	public interface Handler<T extends ReplicatableObject.Handler.Object> extends StorableObject.Handler<T> {
	    /**
	     * Replicate a {@link TitanObject} that implements the {@link ReplicatableObject.Handler.Object}
	     * interface.
	     * <p>
	     * Implementations of this method are expected to cause an additional copy of the specified
	     * object to be created in the object pool.
	     * Typically, the specified object is in the receiving node's object store, but may be located
	     * anywhere.  
	     * This method is invoked as a result of receiving a {@link TitanMessage} containing an instance
	     * of {@link ReplicatableObject.Replicate.Request} as the message payload.  The request contains a list
	     * of nodes known to already have a copy of the object, therefore implementations of this method must
	     * avoid using those nodes.
	     * </p>
	     * @param message the received {@link TitanMessage} containing an instance
         *        of {@link ReplicatableObject.Replicate.Request} as the message payload.
	     * @return The reply {@code BeehiveMessage}.
	     * @throws ClassCastException 
	     * @throws ClassNotFoundException
	     * @throws BeehiveObjectStore.NotFoundException
	     * @throws BeehiveObjectStore.DeletedObjectException
         * @throws BeehiveObjectStore.NoSpaceException
         * @throws BeehiveObjectStore.UnacceptableObjectException
         * @throws BeehiveObjectPool.Exception
	     */
	    public ReplicatableObject.Replicate.Response replicateObject(TitanMessage message) throws ClassNotFoundException, ClassCastException,
	    BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception;
	    
	    /**
	     * Specifies the specific behaviours of instances of {@link TitanObject} that implement {@link ReplicatableObject.Handler.Object}.
	     */
		public interface Object extends StorableObject.Handler.Object {

		}
	}
	
	public static class Replicate {
	    /**
	     * Construct a request to replicate an object.
	     */
	    public static class Request implements Serializable {
            private static final long serialVersionUID = 1;
                
            private TitanGuid objectId;
            private Set<TitanNodeId> excludedNodes;
            
            /**
             * Construct a request to replicate the object identified by {@code objectId} which is currently
             * being published by the nodes specified in the {@link Set} {@code publishers}.
             */
	        public Request(TitanGuid objectId, Set<TitanNodeId> excludedNodes) {
	            this.objectId = objectId;
	            this.excludedNodes = excludedNodes;
	        }
	        
	        /**
	         * Get the {@link Set} of {@link TitanNodeId} instances of the nodes to not use when replicating the object.
	         * <p>
	         * Typically this consists of the nodes currently publishing the object, as they are already contributing a replica.
	         *  </p>
	         * (See {@link #getObjectId()).
	         */
	        public Set<TitanNodeId> getExcludedNodes() {
	            return this.excludedNodes;
	        }

            /**
             * Get the {@link TitanGuid} of the object specified in this request.
             */
	        public TitanGuid getObjectId() {
	            return this.objectId;
	        }
	    }
	    
	    public static class Response implements Serializable {
	        private static final long serialVersionUID = 1;
	            
	        public Response() {
	            
	        }
	    }
	}

	/**
	 * A helper function for classes implementing the {@link ReplicatableObject.Handler} interface to
	 * maintain the minimum number of {@link ReplicatableObject.Handler.Object} copies to keep in the object pool.
	 * <p>
	 * Must only be called by the root of the object id.
	 * </p>
	 */
	public static void unpublishObjectRootHelper(ReplicatableObject.Handler<? extends ReplicatableObject.Handler.Object> handler, Publish.PublishUnpublishRequest request) {
	    try {
	        for (TitanGuid objectId : request.getObjects().keySet()) {
	            Set<Publishers.PublishRecord> publishers = handler.getNode().getObjectPublishers().getPublishers(objectId);
	            Publishers.PublishRecord bestPublisher = null;

	            if (handler.getLogger().isLoggable(Level.FINE)) {
	                handler.getLogger().fine("objectId=%s", objectId);
	            }

	            // Compose the set of node that already have a copy of the object.
	            // Include the node that is unpublishing the object, as we cannot ask it to replicate it.
	            Set<TitanNodeId> publisherSet = new HashSet<TitanNodeId>();

	            publisherSet.add(request.getPublisherAddress().getObjectId());

	            for (Publishers.PublishRecord publisher : publishers) {
	                publisherSet.add(publisher.getNodeId());
	                if (handler.getLogger().isLoggable(Level.FINE)) {
	                    handler.getLogger().fine("  publisher=%s", publisher.getNodeId());
	                }
	                if (!publisher.getNodeId().equals(request.getPublisherAddress().getObjectId())) {
	                    if (bestPublisher == null || bestPublisher.getObjectTTL() < publisher.getObjectTTL()) {
	                        bestPublisher = publisher;
	                    }
	                }
	            }

	            if (bestPublisher != null) {
	                try {
	                    if (handler.getLogger().isLoggable(Level.FINE)) {
	                        handler.getLogger().fine("best publisher=%s object ttl=%d", bestPublisher.getNodeId(), bestPublisher.getObjectTTL());
	                    }
	                    handler.getNode().sendToNodeExactly(bestPublisher.getNodeId(), bestPublisher.getObjectType(), "replicateObject", new Replicate.Request(objectId, publisherSet));
	                    break;
	                } catch (TitanNode.NoSuchNodeException e) {
	                    e.printStackTrace();
	                    // keep trying.
	                }
	            } else {
	                if (handler.getLogger().isLoggable(Level.FINE)) {
	                    handler.getLogger().fine("No publishers of object %s remaining", objectId);
	                }
	            }
	        }
	    } catch (ClassCastException e) {
	        e.printStackTrace();
	    } catch (ClassNotFoundException e) {
	        e.printStackTrace();
	    } catch (RemoteException e) {
	        e.printStackTrace();
	    }               
	}

//	public static class MakeReplicas {
//		public static class Request implements Serializable {
//			private final static long serialVersionUID = 1L;
//
//			private int count;
//			private long timeToLive;
//			private Set<Publishers.PublishRecord> publishers;
//
//			public Request(Set<Publishers.PublishRecord> publishers, int count, long timeToLive) {
//				this.publishers = publishers;
//				this.count = count;
//				this.timeToLive = timeToLive;
//			}
//
//			public Set<Publishers.PublishRecord> getPublishers() {
//				return this.publishers;
//			}
//
//			public int getCount() {
//				return this.count;
//			}
//
//			public long getTimeToLive() {
//				return this.timeToLive;
//			}
//		}
//        
//        public static class Response implements Serializable {
//                private final static long serialVersionUID = 1L;
//                
//        }
//	}

//	public static void makeReplicas(BeehiveService handler, Set<Publishers.PublishRecord> publishers, Publishers.PublishRecord publisher, int count, long timeToLive) throws NoSuchNodeException {
//		System.out.printf("makeReplicas: publisher=%s count=%d timeToLive=%d%n", publisher, count, timeToLive);
//		System.out.printf("makeReplicas: %s%n", publishers);
//		
//		MakeReplicas.Request request = new MakeReplicas.Request(publishers, count, timeToLive);
//		handler.getNode().sendToNodeExactly(publisher.getNodeId(), publisher.getObjectType(), "replicate", request);
//		
//	}
}
