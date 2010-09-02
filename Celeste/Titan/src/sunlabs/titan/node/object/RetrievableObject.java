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
package sunlabs.titan.node.object;

import java.io.IOException;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.util.DOLRStatus;

/**
 * {@link TitanObject} and {@link BeehiveObjectHander} classes implementing the interfaces specified
 * in this class implement the capability of objects to be
 * retrieved from the Beehive object pool.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class RetrievableObject {

    /**
     * Every class that extends {@link AbstractObjectHandler} that implements this interface
     * will retrieve objects of this {@link TitanObject} type.
     *
     * @param <T>
     */
    public interface Handler<T extends RetrievableObject.Handler.Object> extends BeehiveObjectHandler {
        public interface Object extends BeehiveObjectHandler.ObjectAPI {

        }

        /**
         * Retrieve the {@link TitanObject} named {@code objectId} from the Beehive object pool.
         * <p>
         * Classes implementing this method may or may not,
         * depending upon the semantics of the object and the object's retrieval,
         * check that the object is deleted.
         * </p>
         * @throws BeehiveObjectStore.DeletedObjectException if the object has a valid,  exposed delete-token in the meta data.
         * @throws BeehiveObjectStore.NotFoundException if the object could not be found.
         * @throws RemoteException 
         * @throws ClassCastException 
         */
        public T retrieve(TitanGuid objectId)
        throws BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException, ClassCastException;

        /**
         * Retrieve the local object described in the message.
         * <p>
         * This is the "server" side of the retrieve and the object
         * is always retrieved and returned even if it is deleted.
         * </p>
         * <p>
         * The check for a deleted object is to be checked on the client side.
         * </p>
         */
        public BeehiveMessage retrieveLocalObject(BeehiveMessage message)
        throws IOException;
    }

    /**
     * Retrieve the local object described in the BeehiveMessage.
     * <p>
     * This is the "server" side of the retrieve and the object
     * is always retrieved and returned even
     * if it is in its deleted (anti-object) form.
     * </p>
     * <p>
     * The check for a deleted object is to be performed on the client side.
     * </p>
     */
    public static BeehiveMessage retrieveLocalObject(BeehiveObjectHandler handler, BeehiveMessage message) {
        TitanNode node = handler.getNode();

        try {
            TitanObject object = node.getObjectStore().get(TitanObject.class, message.subjectId);
            return message.composeReply(node.getNodeAddress(), object);
        } catch (BeehiveObjectStore.NotFoundException e) {
            return message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_FOUND, e);
        }
    }

    /**
     * Retrieve the specified {@link TitanObject} from the Beehive object pool.
     * The result is the object if successfully found, cast to the given {@link Class} {@code klasse}
     * (which must be an extension of the {@link TitanObject} class).
     * Otherwise, {@link BeehiveObjectStore.NotFoundException} is thrown.
     * If the object is successfully retrieved but has a valid,
     * exposed delete-token, a {@link BeehiveObjectStore.DeletedObjectException} is thrown.
     *
     * @throws ClassCastException if the retrieved object cannot be cast to the given {@code Class}.
     * @throws BeehiveObjectStore.NotFoundException if the object cannot be found in the object pool
     * @throws BeehiveObjectStore.DeletedObjectException if the object was found but has a valid delete-token in the metadata.
     * @throws RemoteException 
     */
    public static <C extends TitanObject> C retrieve(RetrievableObject.Handler<? extends RetrievableObject.Handler.Object> handler,
            Class<? extends C> klasse,
            TitanGuid objectId)
    throws ClassCastException, BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException {

        BeehiveMessage reply = handler.getNode().sendToObject(objectId, handler.getName(), "retrieveLocalObject", objectId);
        if (reply.getStatus().equals(DOLRStatus.NOT_FOUND)) {
            throw new BeehiveObjectStore.NotFoundException("Object %s not found", objectId);
        }

        try {
            C object = reply.getPayload(klasse, handler.getNode());
            if (!DeleteableObject.deleteTokenIsValid(object.getMetadata())) {
                return object;
            }
            throw new BeehiveObjectStore.DeletedObjectException();
        } catch (ClassCastException e) {
            throw e;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
