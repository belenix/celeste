package sunlabs.titan.node.object;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.services.BeehiveService;
import sunlabs.titan.node.services.PublishDaemon;

/**
 * A ReplicatableObject is an object in the object pool that is replicated and the pool
 * maintains a lower and upper bound on the number of copies of the object in the pool.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class ReplicatableObject {
	public interface Handler<T extends ReplicatableObject.Handler.Object> extends StorableObject.Handler<T> {
	    /**
	     * Replicate a {@link BeehiveObject} that implements the {@link ReplicatableObject.Handler.Object}
	     * interface.
	     * <p>
	     * Implementations of this method are expected to cause an additional copy of the specified
	     * object to be created in the object pool.
	     * Typically, the specified object is in the receiving node's object store, but may be located
	     * anywhere.  
	     * This method is invoked as a result of receiving a {@link BeehiveMessage} containing an instance
	     * of {@link ReplicatableObject.Replicate.Request} as the message payload.  The request contains a list
	     * of nodes known to already have a copy of the object, therefore implementations of this method must
	     * avoid using those nodes.
	     * </p>
	     * @param message the received {@link BeehiveMessage} containing an instance
         *        of {@link ReplicatableObject.Replicate.Request} as the message payload.
	     * @return The reply {@code BeehiveMessage}.
	     * @throws BeehiveMessage.RemoteException 
	     * @throws ClassCastException 
	     * @throws ClassNotFoundException 
	     */
	    public BeehiveMessage replicateObject(BeehiveMessage message) throws ClassNotFoundException, ClassCastException, BeehiveMessage.RemoteException;
	    
	    /**
	     * Specifies the specific behaviours of instances of {@link BeehiveObject} that implement {@link ReplicatableObject.Handler.Object}.
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
                
            private BeehiveObjectId objectId;
            private Set<BeehiveObjectId> excludedNodes;
            
            /**
             * Construct a request to replicate the object identified by {@code objectId} which is currently
             * being published by the nodes specified in the {@link Set} {@code publishers}.
             */
	        public Request(BeehiveObjectId objectId, Set<BeehiveObjectId> excludedNodes) {
	            this.objectId = objectId;
	            this.excludedNodes = excludedNodes;
	        }
	        
	        /**
	         * Get the {@link Set} of {@link BeehiveObjectId} instances of the nodes to not use when replicating the object.
	         * <p>
	         * Typically this consists of the nodes currently publishing the object, as they are already contributin a replica.
	         *  </p>
	         * (See {@link #getObjectId()).
	         */
	        public Set<BeehiveObjectId> getExcludedNodes() {
	            return this.excludedNodes;
	        }

            /**
             * Get the {@link BeehiveObjectId} of the object specified in this request.
             */
	        public BeehiveObjectId getObjectId() {
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
	public static void unpublishObjectRootHelper(BeehiveService handler, BeehiveMessage message) {
	    try {
	        PublishDaemon.UnpublishObject.Request request = message.getPayload(PublishDaemon.UnpublishObject.Request.class, handler.getNode());

	        for (BeehiveObjectId objectId : request.getObjectIds()) {
	            Set<Publishers.PublishRecord> publishers = handler.getNode().getObjectPublishers().getPublishers(objectId);
	            Publishers.PublishRecord bestPublisher = null;

	            if (handler.getLogger().isLoggable(Level.FINE)) {
	                handler.getLogger().fine("objectId=%s", objectId);
	            }

	            // Compose the set of node that already have a copy of the object.
	            // Include the node that is unpublishing the object, as we cannot ask it to replicate it.
	            Set<BeehiveObjectId> publisherSet = new HashSet<BeehiveObjectId>();
	            publisherSet.add(message.getSource().getObjectId());

	            for (Publishers.PublishRecord publisher : publishers) {
	                publisherSet.add(publisher.getNodeId());
	                if (handler.getLogger().isLoggable(Level.FINE)) {
	                    handler.getLogger().fine("  publisher=%s", publisher.getNodeId());
	                }
	                if (!publisher.getNodeId().equals(message.getSource())) {
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
	                } catch (BeehiveNode.NoSuchNodeException e) {
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
