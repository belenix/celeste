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
 * Please contact Oracle, 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.oracle.com if you need additional
 * information or have any questions.
 */
package sunlabs.titan.node.object;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.services.CensusDaemon;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.util.DOLRStatus;
import sunlabs.titan.util.OrderedProperties;

/**
 * {@link TitanObject} and {@link BeehiveObjectHander} classes implementing the interfaces specified
 * in this class implement the capability of objects to be
 * stored in the Beehive object pool.
 * <p>
 * A class implementing {@link StorableObject.Handler} interface implements the  required methods for the two sides of the operation --
 * the requester and the responder.  In operation, the requester composes the Request to store the TitanObject and transmits that Request to a responder.
 * The responder does whatever is necessary to store the object locally and returns the Publish.Response returned as a result of publishing the stored object.
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public final class StorableObject {
    
    public static class Store /* implements TitanService.Thing */ {
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;
            private TitanObject object;
            
            public Request(TitanObject object) {
                this.object = object;
            }
            
            public TitanObject getObject() {
                return this.object;
            }
        }
        
        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;
            private TitanGuid objectId;
            
            public Response(TitanGuid objectId) {
                this.objectId = objectId;
            }
            
            public TitanGuid getObjectId() {
                return this.objectId;
            }
        }
    }
    
    
    /**
     * {@link BeehiveObjectHandler}'s {@link TitanObject}'s that are storable in the Beehive object pool implement implement this interface.
     *
     * @param <T>
     */
    public interface Handler<T extends StorableObject.Handler.Object> extends BeehiveObjectHandler {
        public interface Object extends BeehiveObjectHandler.ObjectAPI {

        }
        /**
         * Store the given {@link StorableObject.Handler.Object} in the Beehive object pool.
         * The object will be stored on a randomly selected node.
         * <p>
         * When this method returns, the object can be reused but modifications to the
         * object are not reflected in the copy stored in the object pool.
         * Implementors of classes implementing this interface must be aware and ensure
         * that if the object is queued for storing or cached, a copy of the object is made.
         * </p>
         *
         * @param object The object to store.
         * @return The object stored with the object-id set.
         * @throws IOException if an underlying IOException was thrown while trying to store the object.
         * @throws BeehiveObjectStore.NoSpaceException if there is no space in the object pool to store this object.
         * @throws BeehiveObjectStore.DeleteTokenException if the delete-token encoding for this object is not well-formed
         * @throws BeehiveObjectStore.UnacceptableObjectException 
         * @throws BeehiveObjectPool.Exception 
         */
        public T storeObject(T object) throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException,
            BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception;

        /**
         * <p>
         * Store the object supplied in the {@link TitanMessage} on this node.
         * </p>
         * <p>
         * If the local node cannot store the object because there is no space,
         * the reply message must return {@link DOLRStatus#NOT_ACCEPTABLE}.
         * </p>
         * @param message
         * @return The reply BeehiveMessage containing the entire result of the operation.
         * @throws ClassCastException 
         * @throws ClassNotFoundException 
         * @throws BeehiveObjectPool.Exception 
         * @throws BeehiveObjectStore.UnacceptableObjectException 
         * @throws BeehiveObjectStore.DeleteTokenException 
         * @throws BeehiveObjectStore.NoSpaceException 
         * @throws BeehiveObjectStore.InvalidObjectException 
         * @throws BeehiveObjectStore.InvalidObjectIdException 
         * @throws BeehiveObjectStore.Exception 
         */
        public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message) throws ClassNotFoundException, ClassCastException,
            BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.UnacceptableObjectException,
            BeehiveObjectPool.Exception, BeehiveObjectStore.InvalidObjectIdException, BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.Exception;
    }
    
    /**
     * Helper method to store an object on the local node.
     * Typically, this helper method is invoked on the receiver-side of a store object operation.
     * <p>
     * The returned {@link TitanMessage} is the message from the object's root node indicating the status of the publish operation of the stored object.
     * The payload of the returned message is either an instance of {@link PublishDaemon.PublishObject.PublishUnpublishResponseImpl}
     * or an {@link Exception} (see {@link TitanMessage#getPayload(Class, TitanNodeImpl)}.
     * </p>
     * @param handler The instance implementing {@link StorableObject.Handler<? extends StorableObject.Handler.Object>} invoking this method.
     * @param object An instance implementing {@link StorableObject.Handler.Object} to store.
     * @param message
     * @return The reply {@link TitanMessage} containing the response from the {@link BeehiveObjectHandler#publishObject(TitanMessage)} method on the root node for this {@code object}.
     * @throws TitanMessage.RemoteException encapsulating an Exception thrown by this node.
     */
    public static Publish.PublishUnpublishResponse storeLocalObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, StorableObject.Handler.Object object, TitanMessage message) throws
    ClassNotFoundException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectStore.DeleteTokenException,
    BeehiveObjectStore.InvalidObjectIdException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.InvalidObjectException,
    BeehiveObjectPool.Exception, BeehiveObjectStore.Exception {
        TitanNode node = handler.getNode();

        // Ensure that that the object's METADATA_TYPE is set in its metadata.
        object.setProperty(ObjectStore.METADATA_CLASS, handler.getName());

        node.getObjectStore().lock(BeehiveObjectStore.ObjectId(object));
        Publish.PublishUnpublishResponse response = null;
        try {
            node.getObjectStore().store(object);
        } finally {
            try {
                response = node.getObjectStore().unlock(object);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw e;
            } catch (sunlabs.titan.node.BeehiveObjectStore.Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw e;
            } catch (sunlabs.titan.node.BeehiveObjectPool.Exception e) {
                //System.err.printf("%s: cannot store the object, and response is null%n", e);
                // TODO Auto-generated catch block
                //e.printStackTrace();
                throw e;
            }
        }

        if (message.isTraced() || handler.getLogger().isLoggable(Level.FINEST)) {
            handler.getLogger().finest("recv(%5.5s...) stored %s", message.getMessageId(), object.getObjectId());
        }
        return response;
    }

    /**
     * Store a given {@link StorableObject.Handler.Object} object in the global object store.
     * The number of copies to store is presented in the object's meta-data as
     * the value of the property
     * {@link sunlabs.titan.api.ObjectStore#METADATA_REPLICATION_STORE ObjectStore.METADATA_REPLICATION_STORE}
     * Each copy is stored on a different node.
     *
     * If a node is selected to store the object and that node already has an object with the same
     * {@link TitanGuid}, the node replaces the copy with the new copy and signals that the object already existed.
     * <p>
     * If the property
     * {@link sunlabs.titan.api.ObjectStore#METADATA_REPLICATION_STORE ObjectStore.METADATA_REPLICATION_STORE}
     * specifies a count greater than the number of nodes in the system,
     * you run the risk of never terminating.
     * </p>
     * @param handler
     * @param object
     * @throws IOException
     * @throws BeehiveObjectStore.UnacceptableObjectException 
     */
    public static StorableObject.Handler.Object storeObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, StorableObject.Handler.Object object)
    throws BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {
        int nReplicas = Integer.parseInt(object.getProperty(ObjectStore.METADATA_REPLICATION_STORE, "1"));
        // For now, parallel stores is turned off by passing null as the executor to storeObject().
        // It can generate a large number of Threads during big writes with no benefit because the publish operation is ultimately sequential due to its locking.
        //ExecutorService executor = Executors.newFixedThreadPool(nReplicas);
        return StorableObject.storeObject(handler, object, nReplicas, new HashSet<TitanNodeId>(), null);
    }

    /**
     * Store a {@link StorableObject.Handler.Object} object in the global object store.
     * <p>
     * The number of copies to store is governed by the value {@code nReplicas}
     * (note that the value of the object's metadata value {@link ObjectStore.METADATA_REPLICATION_STORE} does not have to be equal to {@code nReplicas}.)
     * Each copy is stored on a different node, excluding the nodes specified in the {@link Set} {@code excludedNodes}.
     * </p>
     * <p>
     * If a node is selected to store the object and that node already has an object with the same
     * {@link TitanGuid}, the node replaces the copy with the new copy and signals that the object already existed.
     * </p>
     * <p>
     * If the value of {@code nReplicas} is larger than the number of nodes in the system, this method
     * will ultimately throw {@link BeehiveObjectStore.NoSpaceException}.
     * </p>
     * @param handler The instance of the {@link StorableObject.Handler} that is invoking this method.
     * @param object The {@link StorableObject.Handler} to store.
     * @param nReplicas The number of replicas to store.
     * @param nodesToExclude The {@code Set} of nodes to exclude from the candidate set of nodes to store the object.
     * @param executorService An instance of {@link ExecutorService} to use to store the {@code nReplicas} in parallel, or {@code null} to store the objects serially.
     * @throws IOException
     * @throws BeehiveObjectStore.NoSpaceException
     * @throws BeehiveObjectStore.UnacceptableObjectException
     * @throws BeehiveObjectPool.Exception 
     */
    public static StorableObject.Handler.Object storeObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler,
            StorableObject.Handler.Object object,
            int nReplicas,
            Set<TitanNodeId> nodesToExclude,
            ExecutorService executorService)
    throws BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {
        object.setProperty(ObjectStore.METADATA_CLASS, handler.getName());
        object.setProperty(ObjectStore.METADATA_DATAHASH, object.getDataId());

        Census census = (Census) handler.getNode().getService(CensusDaemon.class);

        // Make a local copy to be modified below.
        Set<TitanNodeId> excludedNodes = new HashSet<TitanNodeId>(nodesToExclude);

        for (int successfulStores = 0; successfulStores < nReplicas; /**/) {
            // Get enough nodes from Census to store the object.
            Map<TitanNodeId,OrderedProperties> nodes = census.select(nReplicas - successfulStores, excludedNodes, null);
            if (nodes.size() == 0) {
                throw new BeehiveObjectStore.NoSpaceException("No node found to store object %s", object.getObjectId());
            }
            if (handler.getLogger().isLoggable(Level.FINE)) {
                handler.getLogger().fine("%s on nodes: %s.", object.getObjectId(), nodes.keySet());
            }

            LinkedList<FutureTask<TitanMessage>> tasks = new LinkedList<FutureTask<TitanMessage>>();
            CountDownLatch latch = new CountDownLatch(nodes.size());
            for (TitanNodeId destination : nodes.keySet()) {
                excludedNodes.add(destination);
                FutureTask<TitanMessage> task = new StoreTask(handler, object, destination, latch);
                tasks.add(task);
                if (executorService != null)
                    executorService.execute(task);
                else
                    task.run();
            }

            // Wait for all of the stores to complete.
            boolean complainedAboutTime = false;
            for (;;) {
                try {
                    if (latch.await(10000, TimeUnit.MILLISECONDS))
                        break;
                    for (FutureTask<TitanMessage> task : tasks) {
                        if (!task.isDone()) {
                            handler.getLogger().warning("(thread=%d) waiting for task %s",  Thread.currentThread().getId(), task.toString());
                        }                        
                    }

                    complainedAboutTime = true;
                } catch (InterruptedException e) {
                    /**/
                }
            }
            if (complainedAboutTime) {
                handler.getLogger().warning("(id=%d) waiting done.", Thread.currentThread().getId());
            }

            // Collect the results from each of the Tasks started above, counting the successful stores.
            for (FutureTask<TitanMessage> task : tasks) {
                try {
                    TitanMessage publishResult = task.get();
                    if (true) {
                        try {
                            // XXX Strictly speaking, the publish response will contain either specific Titan exceptions, or other Java exceptions.  The test
                            // XXX for BeehiveObjectPool.Exception below is overbroad and should be replaced with tests for the specific exceptions known to be
                            // XXX thrown by the publishObject() method.
                            Publish.PublishUnpublishResponse response = publishResult.getPayload(Publish.PublishUnpublishResponse.class, handler.getNode());
                            for (TitanGuid objectId : response.getObjectIds()) { // should only be one object-id.
                                object.setObjectId(objectId);
                                break;
                            }
                            successfulStores++;
                        } catch (TitanMessage.RemoteException e) {
                            System.err.printf("StoreObject %s%n", e.getCause());
                            if (e.getCause() instanceof BeehiveObjectPool.Exception) {
                                // These exceptions are fatal to the whole store, so immediately get out of here throwing the rest away.
                                throw (BeehiveObjectPool.Exception) e.getCause();
                            } else if (e.getCause() instanceof BeehiveObjectStore.Exception) {
                                // These exceptions are related just to the node we asked to store the object.  Continue.
                                
                            } else {
                                e.printStackTrace();
                            }
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        TitanGuid objectId = null;
                        if (publishResult.getStatus().isSuccessful()) {
                            objectId = publishResult.subjectId;
                            object.setObjectId(objectId);
                        } else {
                            if (!publishResult.equals(objectId)) {
                                handler.getLogger().severe(String.format("Inconsistent object-id: Expected %s got %s", objectId, publishResult));
                            }
                        }
                        successfulStores++;
                    }
                } catch (ExecutionException e) {
                    // The Exceptions here are wrapped at least two deep.  The ExecutionException from the Task, and the embedded TitanMessage.RemoteException.
                    System.err.printf("StoreObject:ExecutionException %s %s%n", e.getCause(), e.getCause().getClass());
                    if (e.getCause() instanceof TitanMessage.RemoteException) {
                        if (e.getCause().getCause() instanceof BeehiveObjectPool.Exception) {
                            // These exceptions are fatal to the whole store, so immediately get out of here throwing the rest away.
                            throw (BeehiveObjectPool.Exception) e.getCause().getCause();
                        } else if (e.getCause().getCause() instanceof BeehiveObjectStore.Exception) {
                            // These exceptions are related just to the node we asked to store the object.  Continue.
                        } else {
                            e.getCause().printStackTrace();
                        }
                    } else if (e.getCause() instanceof BeehiveObjectPool.DisallowedDuplicateException) {
                        System.err.printf("StoreObject:ExecutionException DisallowedDuplicateException%n");                        
                    } else if (e.getCause() instanceof BeehiveObjectPool.Exception) {
                        System.err.printf("StoreObject:ExecutionException BeehiveObjectPool.Exception%n");
                        // These exceptions are fatal to the whole store, so immediately get out of here throwing the rest away.
                        throw (BeehiveObjectPool.Exception) e.getCause();
                    } else if (e.getCause() instanceof BeehiveObjectStore.Exception) {
                        // These exceptions are related just to the node we asked to store the object.  Continue.
                        
                    } else {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    // the task was interrupted and did not complete.
                    e.printStackTrace();
                }
            }
        }

        return object;
    }
    


    /**
     * Wrap an invocation of the {@link StorableObject.Store} in a {@link FutureTask} object.
     */
    public static class StoreTask extends FutureTask<TitanMessage> {
        private TitanObject object;
        private TitanGuid destination;

        public StoreTask(StorableObject.Handler<? extends StorableObject.Handler.Object>  handler, TitanObject object, TitanNodeId destination, CountDownLatch latch) {
            super(new StorableObject.StoreTask.Store(handler, object, destination, latch));
            this.object = object;
            this.destination = destination;
        }

        @Override
        public String toString() {
            return String.format("StoreTask: %s on %s", this.object, this.destination);
        }

        /**
         * A simple Callable to store a BeehiveObject on a destination node.
         */
        private static class Store implements Callable<TitanMessage> {
            private  StorableObject.Handler<? extends StorableObject.Handler.Object>  handler;
            private TitanNodeId destination;
            private TitanObject object;
            private CountDownLatch latch;

            public Store(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, TitanObject object, TitanNodeId destination, CountDownLatch latch) {
                this.latch = latch;
                this.handler = handler;
                this.destination = destination;
                this.object = object;
            }

            public TitanMessage call() throws TitanNode.NoSuchNodeException, ClassCastException, ClassNotFoundException, TitanMessage.RemoteException {
                try {
                    TitanMessage reply = this.handler.getNode().sendToNodeExactly(this.destination, this.handler.getName(), "storeLocalObject", this.object);
              
                    return reply;
                } finally {
                    if (this.latch != null)
                        this.latch.countDown();
                }
            }
        }
    }
}
