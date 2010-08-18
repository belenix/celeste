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
package sunlabs.titan.node.object;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.BeehiveObjectStore.DeleteTokenException;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.util.DOLRStatus;

/**
 * {@link BeehiveObject} and {@link BeehiveObjectHander} classes implementing the interfaces specified
 * in this class implement the capability of objects in the object pool to be
 * deleted.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public final class DeleteableObject {


    /**
     * The interface that must be implemented to support object-types that are deleteable.
     *
     */
    public interface Handler<T extends DeleteableObject.Handler.Object> extends BeehiveObjectHandler {
        public interface Object extends BeehiveObjectHandler.ObjectAPI {
            /**
             * Delete this object.
             * <p>
             * Implementations of this method are responsible for setting the fields in this object suitable for deleteing.
             * (See {@link DeleteableObject#MakeDeletable(BeehiveObject, BeehiveObjectId)}, and {@link DeleteableObject.Handler.createAntiObject}.
             * </p>
             * @param profferedDeleteToken The {@link BeehiveObjectId} of the delete-token for this object.
             * @param timeToLive The number of seconds for this object to continue to exist in the anti-object form.
             * @throws BeehiveObjectStore.DeleteTokenException if {@code profferedDeleteToken} does not match
             *          the objects' {@link ObjectStore#METADATA_DELETETOKENID}
             */
            public void delete(BeehiveObjectId profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.DeleteTokenException;
        }

        /**
         * <p>
         * Delete the specified {@link BeehiveObject} using the {@code deletionToken} as the authenticator.
         * The anti-objects created exist for {@code timeToLive} seconds.
         * </p>
         * <p>
         * Deletion consists of:
         * <ol>
         * <li>The object should be fetched and if found, its components are subjected to whatever cleanup is necessary to effect the deletion.</li>
         * <li>A suitable anti-object is created in the object pool to ensure future deletion of the object should it reappear somewhere in the object pool.</li>
         * </ol>
         * </p>
         * <p>
         * The object pool won't store an object with an exposed delete-token and which also contains data in the body of the object.
         * This is an attempt to ensure that deletion isn't improperly implemented and anti-objects
         * still contain data from the original object.
         * However, it is conceivable that some information in the anti-object is necessary to
         * ensure proper future deletion of a real object.
         * In that case, the DOLR object store might be more relaxed in the enforcement of zero-length data.
         * </p>
         * <p>
         * It has been considered that if the object to delete is not retrievable,
         * it is possible that the object exists on a DOLRNode that is not currently
         * connected to the DOLR and creating a provisional anti-object would cause
         * the currently disconnected object to be deleted when it is reintroduced to the DOLR.
         * Unfortunately, there is no way to distinguish between an object that does not exist
         * and one that is just currently disconnected.
         * Creating a provisional anti-object would also enable an attack on a future object
         * that doesn't yet exist, but might exist in the future.
         * </p>
         * <p>
         * As a consequence we only create anti-objects for objects that we can currently retrieve.
         * </p>
         * <p>
         * <ul>
         * <li>DOLRStatus.OK Deletion was successful.</li>
         * <li>DOLRStatus.FORBIDDEN Object specified by objectId does not have a delete-token-id and thus can never be deleted.</li>
         * <li>DOLRStatus.UNAUTHORIZED The given delete-token does not match the objects' delete-token-id.</li>
         * <li>DOLRStatus.GONE The DOLRObject already has an exposed delete-token and it is the same as the given delete-token.</li>
         * <li>DOLRStatus.NOT_FOUND The DOLRObject specified by objectId was not found in the DOLR.</li>
         * </ul>
         * </p>
         */
        public DOLRStatus deleteObject(BeehiveObjectId objectId, BeehiveObjectId deletionToken, long timeToLive)
        throws IOException, BeehiveObjectStore.NoSpaceException;

        /**
         * This method is invoked as the result of receiving a deleteLocalObject
         * {@link BeehiveMessage} or the receipt of a {@link PublishObjectMessage} containing valid
         * delete information.
         * @param message
         * @return A reply {@link BeehiveMessage}
         */
        public BeehiveMessage deleteLocalObject(BeehiveMessage message) throws ClassNotFoundException;

        /**
         * Every {@link DeleteableObject.Handler#publishObject(BeehiveMessage)} implementation must have a per-object lock
         * to protect against modification by simultaneous reception of deletion PublishObjectMessage messages.
         */
        public ObjectLock<BeehiveObjectId> getPublishObjectDeleteLocks();

        /**
         * <p>
         * Object type classes that implement of this interface must be able to produce
         * an anti-object that effects the deletion of the original object.
         * </p>
         * <p>
         * Object type classes that have special behaviour for objects that are deleted,
         * implement that special behaviour in this method.
         * </p>
         *
         * @param object The original {@link BeehiveObject} (implementing {@link DeleteableObject.Handler.Object}) to be used as the template for the anti-object
         * @param profferedDeleteToken The delete-token used as the authenticator of the anti-object.
         * @return The anti-object form of the orignal {@link BeehiveObject}
         * @throws IOException
         */
        public BeehiveObject createAntiObject(DeleteableObject.Handler.Object object, BeehiveObjectId profferedDeleteToken, long timeToLive)
        throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException;
    }

    public static void ObjectDeleteHelper(DeleteableObject.Handler.Object object, BeehiveObjectId deleteToken, long timeToLive) {
        object.setTimeToLive(timeToLive);
        object.setProperty(ObjectStore.METADATA_DELETETOKEN, deleteToken);
    }

    public static BeehiveObject MakeDeletable(DeleteableObject.Handler.Object object, BeehiveObjectId deleteTokenId) {
        object.setDeleteTokenId(deleteTokenId);
        return object;
    }

    /**
     * <p>
     * Given a delete-token and a delete-token-id,
     * ensure that the delete-token-id is equal to the object-id of the
     * delete-token.
     * </p>
     * <p>
     * Validate the deletion information presented in the message.
     * It must be self-consistent in that the object-id of the
     * provisional delete-token is equal to the delete-token-id presented
     * in the message.
     * </p>
     *
     * @param deleteToken
     * @param deleteTokenId
     * @return true if the delete-token and the delete-token-id match.
     */
    public static boolean deleteTokenIsValid(BeehiveObjectId deleteToken, BeehiveObjectId deleteTokenId) {
        return deleteToken != null && deleteTokenId != null && deleteTokenId.equals(deleteToken.getObjectId());
    }

    public static class Request implements Serializable {
        private final static long serialVersionUID = 1L;

        private BeehiveObjectId objectId;
        private BeehiveObjectId deleteToken;
        private long timeToLive;

        /**
         * Construct a Request to delete a particular object in the object pool.
         * 
         * @param objectId The {@link BeehiveObjectId} of the object to delete.
         * @param deleteToken The exposed delete-token.
         * @param timeToLive The number of seconds the anti-object form must exist.
         */
        public Request(BeehiveObjectId objectId, BeehiveObjectId deleteToken, long timeToLive) {
            this.objectId = objectId;
            this.deleteToken = deleteToken;
            this.timeToLive = timeToLive;
        }

        public BeehiveObjectId getObjectId() {
            return objectId;
        }

        public BeehiveObjectId getDeleteToken() {
            return deleteToken;
        }

        public long getTimeToLive() {
            return timeToLive;
        }

    }

    /**
     * <p>
     * Given the BeehiveObject.Metadata extract the delete token and
     * delete-token-id in the metadata and validate them.
     * Return {@code true} if the hash of the delete token is
     * equal to the delete-token objectId.
     * </p>
     * @param metaData
     * @return true if the delete-token and the delete-token-id in the metaData match signifying that the object is the anti-object form.
     */
    public static boolean deleteTokenIsValid(BeehiveObject.Metadata metaData) {
        BeehiveObjectId deleteToken = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null);
        BeehiveObjectId deleteTokenId = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKENID, null);
        return DeleteableObject.deleteTokenIsValid(deleteToken, deleteTokenId);
    }

    /**
     * Helper function for implementors of the {@link BeehiveObjectHandler#publishObject(BeehiveMessage)}
     * method of classes that implement the {@link DeleteableObject} interface.
     * <p>
     * The given {@link PublishDaemon.PublishObject.Request} {@code publishRequest}
     * is examined for any {@link BeehiveObject}s that have been deleted (signified by an exposed
     * delete-token, see {@link DeleteableObject.deleteTokenIsValid}),
     * and for those that have, it performs the deletion checks and operation.  
     * </p>
     * @param publishRequest
     */
    public static List<DOLRStatus> publishObjectHelper(DeleteableObject.Handler<? extends DeleteableObject.Handler.Object> handler, PublishDaemon.PublishObject.Request publishRequest) {

    	List<DOLRStatus> result = new LinkedList<DOLRStatus>();
    	
        for (Map.Entry<BeehiveObjectId,BeehiveObject.Metadata> entry : publishRequest.getObjectsToPublish().entrySet()) {
            if (DeleteableObject.deleteTokenIsValid(entry.getValue())) {
            	result.add(publishObjectHelper(handler, entry.getKey(), publishRequest.getPublisherAddress().getObjectId(), entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Obtain a publish-object-delete lock on the object identified by {@code objectId} and
     * invoke {@link DeleteableObject.deleteBackPointers2} with the given {@code handler},
     * {@code objectId}, {@code publisherNodeId} and {@link metaData}.
     */
    private static DOLRStatus publishObjectHelper(DeleteableObject.Handler<? extends DeleteableObject.Handler.Object> handler, BeehiveObjectId objectId, BeehiveObjectId publisherNodeId, BeehiveObject.Metadata metaData) {
        // this.log.info("anti-object %s from %s", message.subjectId, message.getSource().getObjectId());
        // We get here because some node has published a valid anti-object.
        if (handler.getPublishObjectDeleteLocks().trylock(objectId)) {
            try {
                //
                // For each node publishing this object, and that node is NOT publishing the anti-object form, send it a
                // deleteLocalObjectMessage.
                DeleteableObject.deleteBackPointers2(handler, objectId, publisherNodeId, metaData);
            } catch (DeleteTokenException e) {
                e.printStackTrace();
                return DOLRStatus.BAD_REQUEST;
            } finally {
                handler.getPublishObjectDeleteLocks().unlock(objectId);
            }
        }
        return DOLRStatus.OK;
    }

    /**
     * Given an object and a object-id to be used as the delete-token for
     * the given object,
     * determine if the delete-token is the objects' delete-token.
     *
     * @param metaData
     * @param profferedDeleteToken
     * @return status reflecting the result of the determination
     * <ul>
     * <li>DOLRStatus.OK Deletion may proceed.</li>
     * <li>DOLRStatus.FORBIDDEN Object does not have a delete-token-id and thus can never be deleted.</li>
     * <li>DOLRStatus.UNAUTHORIZED The given delete-token does not match the object's delete-token-id.</li>
     * <li>DOLRStatus.GONE The DOLRObject already has an exposed delete-token and it is the same as the given delete-token.</li>
     * </ul>
     */
    public static DOLRStatus objectIsDeleteable(BeehiveObject.Metadata metaData, BeehiveObjectId profferedDeleteToken) {
        BeehiveObjectId objectDeleteTokenId = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKENID, null);

        if (objectDeleteTokenId == null) {
            // Cannot delete an object that doesn't have a delete-token-id
            return DOLRStatus.FORBIDDEN;
        }

        BeehiveObjectId objectDeleteToken = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null);
        if (objectDeleteToken != null && objectDeleteToken.equals(profferedDeleteToken)) {
            // If there is already a delete-token specified in this object, and
            // it is the same as the one we are trying to set, then we have
            // fetched the anti-object which means it's already in place.
            return DOLRStatus.GONE;
        }

        BeehiveObjectId profferedDeleteTokenId = profferedDeleteToken.getObjectId();

        if (!objectDeleteTokenId.equals(profferedDeleteTokenId)) {
            // Proffered delete-token-object-id does not match object's delete-token-object-id
            return DOLRStatus.UNAUTHORIZED;
        }

        return DOLRStatus.OK;
    }

    /**
     * Transmit {@code deleteLocalObject} {@link BeehiveMessage}s to all nodes that
     * are publishing the object specified by the given object-id.
     * <p>
     * Does not send messages to the local {@link BeehiveNode} nor to the node
     * identified by the given object-id publisherId.
     * </p>
     * @param objectType The {@link DeleteableObject.Handler} of the object to delete.
     * @param objectId The object-id of the object to delete.
     * @param excludedPublisherId The object-id of the publisher of the
     *        original {@link PublishObjectMessage} that induced this method call.
     * @param metaData The {@link BeehiveObject.Metadata} of the {@link PublishObjectMessage} that induced this method call.
     * @throws BeehiveObjectStore.DeleteTokenException The delete-token in the given metadata fails the validation test (see {@link DeleteableObject#deleteTokenIsValid}).
     */
    public static void deleteBackPointers2(DeleteableObject.Handler<? extends DeleteableObject.Handler.Object> objectType,
            BeehiveObjectId objectId,
            BeehiveObjectId excludedPublisherId,
            BeehiveObject.Metadata metaData)
    throws BeehiveObjectStore.DeleteTokenException {
        //objectType.getLogger().info(objectType.getName() + " " + objectId.toString() + " excluding " + excludedPublisherId);
        // Check that the deletion information contained in the PublishObjectMessage is self-consistent.
        if (!DeleteableObject.deleteTokenIsValid(metaData)) {
            throw new BeehiveObjectStore.DeleteTokenException("Invalid delete token");
        }

        BeehiveObjectId profferedDeleteToken = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null);
        BeehiveNode node = objectType.getNode();

        // Delete all copies of this object that the given DOLRNode has backpointers.
        // Do not send deleteLocalObject messages to the local node (which is unnecessary as it's already deleting this object),
        // nor the excludedPublisherId (because it is already publishing the deleted object), nor to publishers of the anti-object form
        // of the object to delete.
        Set<Publishers.PublishRecord> publishers = new HashSet<Publishers.PublishRecord>(node.getObjectPublishers().getPublishers(objectId));
        

        DeleteableObject.Request request = new DeleteableObject.Request(objectId,
                metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null),
                metaData.getPropertyAsLong(ObjectStore.METADATA_SECONDSTOLIVE, BeehiveObject.INFINITE_TIME_TO_LIVE));

        if (publishers != null) {
            for (Publishers.PublishRecord publisher : publishers) {
                if (!publisher.getNodeId().equals(node.getObjectId())) {
                    if (!publisher.getNodeId().equals(excludedPublisherId)) { // not the excludedPublisher node
                        if (publisher.getDeleteToken() == null || !publisher.getDeleteToken().equals(profferedDeleteToken.toString())) {  // not aleady deleted.
                            node.sendToNode(publisher.getNodeId(), objectId, objectType.getName(),  "deleteLocalObject", request);
                        }
                    }
                }
            }
        }
    }
    
//    public static void deleteBackPointers2(DeleteableObject.Handler<? extends DeleteableObject.Handler.Object> objectType, BeehiveObjectId objectId, BeehiveObjectId excludedPublisherId, BeehiveObject.Metadata metaData)
//    throws BeehiveObjectStore.DeleteTokenException {
//        //objectType.getLogger().info(objectType.getName() + " " + objectId.toString() + " excluding " + excludedPublisherId);
//        // Check that the deletion information contained in the PublishObjectMessage is self-consistent.
//        if (!DeleteableObject.deleteTokenIsValid(metaData)) {
//            throw new BeehiveObjectStore.DeleteTokenException("Invalid delete token");
//        }
//
//        BeehiveObjectId profferedDeleteToken = metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null);
//
//        // Delete all copies of this object that the given DOLRNode has backpointers.
//        // Do not send deleteLocalObject messages to the local node (which is unnecessary as it's already deleting this object),
//        // nor the excludedPublisherId (because it is already publishing the deleted object), nor to publishers of the anti-object form
//        // of the object to delete.
//        Map<BeehiveObjectId,Publishers.PublishRecord> publishers = objectType.getNode().getObjectPublishers().getPublishersXXX(objectId);
//
//        DeleteableObject.Request request = new DeleteableObject.Request(objectId,
//                metaData.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null),
//                metaData.getPropertyAsLong(ObjectStore.METADATA_SECONDSTOLIVE, BeehiveObject.INFINITE_TIME_TO_LIVE));
//
//        if (publishers != null) {
//            for (Map.Entry<BeehiveObjectId, Publishers.PublishRecord> entry : publishers.entrySet()) {
//            	Publishers.PublishRecord publisher = entry.getValue();
//                if (!publisher.getNodeId().equals(objectType.getNode().getObjectId())) {
//                    if (!publisher.getNodeId().equals(excludedPublisherId)) { // not the excludedPublisher node
//                        if (publisher.getDeleteToken() == null || !publisher.getDeleteToken().equals(profferedDeleteToken.toString())) {  // not aleady deleted.
//                            objectType.getNode().sendToNode(publisher.getNodeId(), objectType.getName(), "deleteLocalObject", request);
//                        }
//                    }
//                }
//            }
//        }
//    }

    public static BeehiveMessage deleteLocalObject(DeleteableObject.Handler<? extends DeleteableObject.Handler.Object> objectType, DeleteableObject.Request request, BeehiveMessage message) {

//      objectType.getLogger().info("%5.5s... id=%5.5s... ttl=%ss from %s",
//              message.getMessageId(),
//              message.subjectId,
//              metaData.getProperty(DOLRObjectStore.METADATA_SECONDSTOLIVE),
//              message.getSource().toString());

      BeehiveObjectId profferedDeleteToken = request.getDeleteToken();
      BeehiveObjectId objectId = message.subjectId;
      DOLRStatus status;
      
      if (objectId.equals(objectType.getNode().getObjectId())) {
          System.out.printf("object id is node id%n");
          System.out.printf("%s%n", message.traceReport());
          System.out.printf("%s%n", message.toString());
          return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
      }

      BeehiveObject localObject = null;
      try {
          localObject = objectType.getNode().getObjectStore().getAndLock(BeehiveObject.class, objectId);
//      if (localObject == null) {
//          objectType.getLogger().info("%s not local", objectId);
//          return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_FOUND);
//      }
          status = DeleteableObject.objectIsDeleteable(localObject.getMetadata(), profferedDeleteToken);
          if (status.equals(DOLRStatus.GONE)) { // Object is already deleted.
              return message.composeReply(objectType.getNode().getNodeAddress());
          }
          if (status.equals(DOLRStatus.FORBIDDEN)) { // Object is not deleteable.
              objectType.getLogger().info("Forbidden deletion");
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.FORBIDDEN);
          }
          if (status.equals(DOLRStatus.UNAUTHORIZED)) { // Object is not deleteable with the proffered delete token.
              objectType.getLogger().info("Unauthorized deletion");
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.UNAUTHORIZED);
          }

          try {
              // Take the time-to-live from the message passed in.
              // If it is not set, then we take it from the method parameter.

              long timeToLive = request.getTimeToLive();

              BeehiveObject antiObject = objectType.createAntiObject((DeleteableObject.Handler.Object) localObject, profferedDeleteToken, timeToLive);
              if (antiObject != null) {
                  objectType.getNode().getObjectStore().update(antiObject);
                  if (antiObject.getObjectId().equals(localObject.getObjectId())) {
                      return message.composeReply(objectType.getNode().getNodeAddress());
                  }
                  System.err.println("Object-id of anti-object does not match original object.");
                  return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.EXPECTATION_FAILED);
              }
              objectType.getLogger().info("antiObject failed %s", objectId);
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_FOUND);
          } catch (BeehiveObjectStore.NoSpaceException noSpace) {
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, noSpace);
          } catch (BeehiveObjectStore.InvalidObjectException e) {
              e.printStackTrace();
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
          } catch (BeehiveObjectStore.ObjectExistenceException e) {
              e.printStackTrace();
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
          } catch (IOException io) {
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR, io);
          } catch (BeehiveObjectStore.UnacceptableObjectException e) {
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
          } catch (BeehiveObjectStore.DeleteTokenException e) {
              e.printStackTrace();
              return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE, e);
          }
      } catch (ClassCastException e) {
          objectType.getLogger().info("%s not of type BeehiveObject", objectId);
          return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_FOUND);
    } catch (BeehiveObjectStore.NotFoundException e) {
        if (objectType.getLogger().isLoggable(Level.FINE)) {
            objectType.getLogger().fine("%s not local", objectId);
        }
        return message.composeReply(objectType.getNode().getNodeAddress(), DOLRStatus.NOT_FOUND);
    } finally {
        if (localObject != null)
            objectType.getNode().getObjectStore().unlock(localObject);
      }
  }
}
