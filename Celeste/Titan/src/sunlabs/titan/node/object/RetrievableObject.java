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

import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;

/**
 * Classes implementing {@link TitanObject} and extending {@link sunlabs.titan.node.object.AbstractObjectHandler}
 * implement the capability of objects to be stored in the Titan object pool.
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
    public interface Handler<T extends RetrievableObject.Handler.Object> extends TitanObjectHandler {
        public interface Object extends TitanObjectHandler.ObjectAPI {

        }

        /**
         * Retrieve the {@link TitanObject} named {@code objectId} from the Titan object pool.
         * <p>
         * Classes implementing this method may or may not,
         * depending upon the semantics of the object and the object's retrieval,
         * check that the object is deleted.
         * </p>
         * @throws ClassCastException 
         * @throws ClassNotFoundException 
         * @throws BeehiveObjectStore.DeletedObjectException if the object has a valid,  exposed delete-token in the meta data.
         * @throws BeehiveObjectStore.NotFoundException if the object could not be found.
         */
        public T retrieve(TitanGuid objectId) throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException;

        /**
         * Retrieve the local object specified in the message.
         * <p>
         * This is the "server" side of the retrieve and the object
         * is always retrieved and returned even if it is deleted.
         * </p>
         * <p>
         * The check for a deleted object is to be checked on the client side.
         * </p>
         * @throws BeehiveObjectStore.NotFoundException 
         */
        public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws IOException, BeehiveObjectStore.NotFoundException;
    }

    /**
     * Retrieve the specified {@link TitanObject} from the Titan object pool.
     * The result is the object if successfully found, cast to the given {@link Class} {@code klasse}
     * (which must be an extension of the {@link TitanObject} class).
     * Otherwise, {@link BeehiveObjectStore.NotFoundException} is thrown.
     * If the object is successfully retrieved but has a valid exposed delete-token, a {@link BeehiveObjectStore.DeletedObjectException} is thrown.
     *
     * @throws ClassCastException if the retrieved object cannot be cast to the given {@code Class}.
     * @throws BeehiveObjectStore.NotFoundException if the object cannot be found in the object pool
     * @throws BeehiveObjectStore.DeletedObjectException if the object was found but has a valid delete-token in the metadata.
     */
    public static <C extends TitanObject> C retrieve(RetrievableObject.Handler<? extends RetrievableObject.Handler.Object> handler,
            Class<? extends C> klasse,
            TitanGuid objectId)
    throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException {

        TitanMessage reply = handler.getNode().sendToObject(objectId, handler.getName(), "retrieveLocalObject", objectId);

        try {
            C object = reply.getPayload(klasse, handler.getNode());
            if (!DeleteableObject.deleteTokenIsValid(object.getMetadata())) {
                return object;
            }
            throw new BeehiveObjectStore.DeletedObjectException();
        } catch (RemoteException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BeehiveObjectStore.NotFoundException) {
                throw (BeehiveObjectStore.NotFoundException) cause;
            }
            throw new RuntimeException(e);
        }
    }
}
