/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.beehive.node.object;

import java.io.IOException;
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

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.ObjectStore;
import sunlabs.beehive.node.BeehiveMessage;
import sunlabs.beehive.node.BeehiveNode;
import sunlabs.beehive.node.BeehiveObjectStore;
import sunlabs.beehive.node.services.api.Census;
import sunlabs.beehive.util.DOLRStatus;
import sunlabs.beehive.util.OrderedProperties;

/**
 * {@link BeehiveObject} and {@link BeehiveObjectHander} classes implementing the interfaces specified
 * in this class implement the capability of objects to be
 * stored in the Beehive object pool.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public final class StorableObject {
    /**
     * {@link BeehiveObjectHandler}'s {@link BeehiveObject}'s that are storable in the Beehive object pool implement implement this interface.
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
         */
        public T storeObject(T object)
        throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException;

        /**
         * <p>
         * Store the object supplied in the {@link BeehiveMessage} on this node.
         * </p>
         * <p>
         * If the local node cannot store the object because there is no space,
         * the reply message must return {@link DOLRStatus#NOT_ACCEPTABLE}.
         * </p>
         * @param message
         * @return The reply BeehiveMessage containing the entire result of the operation.
         */
        public BeehiveMessage storeLocalObject(BeehiveMessage message);
    }
    
    /**
     * Helper method to store an object on the local node.
     *
     * @param handler
     * @param object
     * @param message
     * @return the reply {@link BeehiveMessage} from the node storing the {@link BeehiveObject} {@code object}.
     */
    public static BeehiveMessage storeLocalObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, StorableObject.Handler.Object object, BeehiveMessage message) {
        BeehiveNode node = handler.getNode();

        // Ensure that that the object's TYPE is set in the metadata.
        object.setProperty(ObjectStore.METADATA_TYPE, handler.getName());

        try {
            node.getObjectStore().lock(BeehiveObjectStore.ObjectId(object));
            try {
                node.getObjectStore().store(object);
            } finally {
                node.getObjectStore().unlock(object);
            }
            if (message.isTraced() && handler.getLogger().isLoggable(Level.FINEST)) {
                handler.getLogger().finest("recv(%5.5s...) stored %s", message.getMessageId(), object.getObjectId());
            }

            BeehiveMessage result = message.composeReply(node.getNodeAddress(), object.getObjectId());
            result.subjectId = object.getObjectId(); // XXX Should be encoded in the return value.
            return result;
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
        } catch (BeehiveObjectStore.InvalidObjectIdException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
        } catch (BeehiveObjectStore.NoSpaceException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
        } catch (BeehiveObjectStore.InvalidObjectException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
        }
    }

    /**
     * Store a {@link StorableObject.Handler.Object} object in the global object store.
     * <p>
     * The number of copies to store is governed by the value {@code nReplicas} (node
     * that the value of the object's metadata value {@link ObjectStore.METADATA_REPLICATION_STORE} does not have to be equal to {@code nReplicas}.)
     * Each copy is stored on a different node, excluding the nodes specified in the {@link Set} {@code excludedNodes}.
     * </p>
     * <p>
     * If a node is selected to store the object and that node already has an object with the same
     * {@link BeehiveObjectId}, the node replaces the copy with the new copy and signals that the object already existed.
     * </p>
     * <p>
     * If the value of {@code nReplicas} is larger than the number of nodes in the system, this method
     * will ultimately throw {@link BeehiveObjectStore.NoSpaceException}.
     * </p>
     * @param handler The instance of the StorableObject.Handler that is invoking this method.
     * @param object The StorableObject.Handler.Object to store.
     * @param nReplicas The number of replicas to store.
     * @param exclude The Set of nodes to exclude from the candidate set of nodes to store the object.
     * @param executorService An instance of {@link ExecutorService} to use to store the {@code nReplicas} in parallel.
     * @throws IOException
     * @throws BeehiveObjectStore.NoSpaceException
     */
    public static StorableObject.Handler.Object storeObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler,
            StorableObject.Handler.Object object,
            int nReplicas,
            Set<BeehiveObjectId> exclude,
            ExecutorService executorService)
    throws IOException, BeehiveObjectStore.NoSpaceException {
        object.setProperty(ObjectStore.METADATA_TYPE, handler.getName());
        object.setProperty(ObjectStore.METADATA_DATAHASH, object.getDataId());

        Census census = (Census) handler.getNode().getService("sunlabs.beehive.node.services.CensusDaemon");

        Set<BeehiveObjectId> excludedNodes = new HashSet<BeehiveObjectId>(exclude);

        for (int successfulStores = 0; successfulStores < nReplicas; /**/) {
            // Get enough nodes from Census to store the object.
            Map<BeehiveObjectId, OrderedProperties> nodes = census.select(nReplicas - successfulStores, excludedNodes, null);
            if (nodes.size() == 0) {
                throw new BeehiveObjectStore.NoSpaceException("No node found to store object %s", object.getObjectId());
            }
            if (handler.getLogger().isLoggable(Level.FINE)) {
                handler.getLogger().fine("%s Selected nodes: %s.", object.getObjectId(), nodes);
            }

            LinkedList<FutureTask<BeehiveObjectId>> tasks = new LinkedList<FutureTask<BeehiveObjectId>>();
            CountDownLatch latch = new CountDownLatch(nodes.size());
            for (BeehiveObjectId destination : nodes.keySet()) {
                excludedNodes.add(destination);
                FutureTask<BeehiveObjectId> task = new StoreTask(handler, object, destination, latch);
                tasks.add(task);
                if (executorService != null)
                    executorService.execute(task);
                else
                    task.run();
            }

            // Now collect the results of all the store operations.
            boolean complained = false;
            for (;;) {
                try {
                    if (latch.await(10000, TimeUnit.MILLISECONDS))
                        break;
                    for (FutureTask<BeehiveObjectId> task : tasks) {
                        if (!task.isDone()) {
                            handler.getLogger().warning("(thread=%d) waiting for task %s",  Thread.currentThread().getId(), task.toString());
                        }                        
                    }

                    complained = true;
                } catch (InterruptedException e) {
                    /**/
                }
            }
            if (complained) {
                handler.getLogger().warning("(id=%d) waiting done.", Thread.currentThread().getId());
            }

            BeehiveObjectId objectId = null;

            // Collect the results from each of the Tasks started above, counting the successful stores..
            for (FutureTask<BeehiveObjectId> task : tasks) {
                try {
                    BeehiveObjectId result = task.get();
                    if (result != null) {
                        if (objectId == null) {
                            objectId = result;
                            object.setObjectId(result);
                        } else {
                            if (!result.equals(objectId)) {
                                handler.getLogger().severe(String.format("Inconsistent object-id: Expected %s got %s", objectId, result));
                            }
                        }
                        successfulStores++;
                    }
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // the task was interrupted and did not complete.
                    e.printStackTrace();
                }
            }
        }

        return object;
    }
    
    /**
     * Store a given {@link StorableObject.Handler.Object} object in the global object store.
     * The number of copies to store is presented in the object's meta-data as
     * the value of the property
     * {@link sunlabs.beehive.api.ObjectStore#METADATA_REPLICATION_STORE ObjectStore.METADATA_REPLICATION_STORE}
     * Each copy is stored on a different node.
     *
     * If a node is selected to store the object and that node already has an object with the same
     * {@link BeehiveObjectId}, the node replaces the copy with the new copy and signals that the object already existed.
     * <p>
     * If the property
     * {@link sunlabs.beehive.api.ObjectStore#METADATA_REPLICATION_STORE ObjectStore.METADATA_REPLICATION_STORE}
     * specifies a count greater than the number of nodes in the system,
     * you run the risk of never terminating.
     * </p>
     * @param handler
     * @param object
     * @throws IOException
     */
    public static StorableObject.Handler.Object storeObject(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, StorableObject.Handler.Object object)
    throws IOException, BeehiveObjectStore.NoSpaceException {
        int nReplicas = Integer.parseInt(object.getProperty(ObjectStore.METADATA_REPLICATION_STORE, "1"));
        // For now parallel stores is turned off.  It can generate a large number of Threads during big writes
        // with no benefit because the publish operation is ultimately sequential due to its locking.
        //ExecutorService executor = Executors.newFixedThreadPool(nReplicas);
        return StorableObject.storeObject(handler, object, nReplicas, new HashSet<BeehiveObjectId>(), null);
    }


    /**
     * Wrap an invocation of the {@link StorableObject.Store} in a {@link FutureTask} object.
     */
    public static class StoreTask extends FutureTask<BeehiveObjectId> {
        private BeehiveObject object;
        private BeehiveObjectId destination;

        public StoreTask(StorableObject.Handler<? extends StorableObject.Handler.Object>  handler, BeehiveObject object, BeehiveObjectId destination, CountDownLatch latch) {
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
        private static class Store implements Callable<BeehiveObjectId> {
            private  StorableObject.Handler<? extends StorableObject.Handler.Object>  handler;
            private BeehiveObjectId destination;
            private BeehiveObject object;
            private CountDownLatch latch;

            public Store(StorableObject.Handler<? extends StorableObject.Handler.Object> handler, BeehiveObject object, BeehiveObjectId destination, CountDownLatch latch) {
                this.latch = latch;
                this.handler = handler;
                this.destination = destination;
                this.object = object;
            }

            public BeehiveObjectId call() throws BeehiveNode.NoSuchNodeException {
                try {
                    BeehiveMessage reply = this.handler.getNode().sendToNodeExactly(this.destination, this.handler.getName(), "storeLocalObject", this.object);
                    return reply.getStatus().isSuccessful() ? reply.subjectId : null;
                } finally {
                    if (this.latch != null)
                        this.latch.countDown();
                }
            }
        }
    }
}