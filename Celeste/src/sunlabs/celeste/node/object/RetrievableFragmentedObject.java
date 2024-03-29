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
package sunlabs.celeste.node.object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;

import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.node.erasurecode.ErasureCode;
import sunlabs.celeste.node.services.object.FObjectType;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.object.TitanObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;

public final class RetrievableFragmentedObject {
    public interface Handler<T extends TitanObject> extends TitanObjectHandler {
        public interface Object extends TitanObjectHandler.ObjectAPI {

        }

        /**
         * Given a FragmentMap for a {@link BeehiveObject} stored somewhere in the system, return a copy of that BeehiveObject.
         * If the BeehiveObject cannot be located,
         * reconstruct it from the fragments listed in the FragmentMap, store it locally, and return it.
         *
         * @param map
         * @return The BeehiveObject retrieved from the object pool, or null if the object could not be retrieved.
         * @throws ErasureCode.UnsupportedAlgorithmException
         * @throws ErasureCode.NotRecoverableException
         * @throws TitanObjectStoreImpl.DeletedObjectException
         */
        public T retrieveRemoteObject(StorableFragmentedObject.Handler.FragmentMap map)
        throws ErasureCode.UnsupportedAlgorithmException, ErasureCode.NotRecoverableException, TitanObjectStoreImpl.DeletedObjectException;

        /**
         * Retrieve the local object described in the {@link TitanMessage}.
         * <p>
         * This is the "server" side of the retrieve and the object is always retrieved and returned even if it is deleted.
         * </p>
         * <p>
         * The check for a deleted object is to be checked on the client side.
         * </p>
         */
        public TitanMessage retrieveLocalObject(TitanMessage message);
    }


    /**
     * Retrieve the specified object from the DOLR.
     * The result is the DOLRObject if successfully found, null if not.
     * If the DOLRObject is successfully retrieved but has a valid,
     * exposed delete-token, a DOLRObjectStore.DeletedException is thrown.
     */
    public static TitanObject retrieveRemoteObject(RetrievableFragmentedObject.Handler<? extends RetrievableFragmentedObject.Handler.Object> objectType, TitanGuid objectId)
    throws TitanObjectStoreImpl.DeletedObjectException {
        TitanMessage reply = objectType.getNode().sendToObject(objectId, objectType.getName(), "retrieveLocalObject", objectId);

        if (!reply.getStatus().isSuccessful()) {
            return null;
        }
        try {
            TitanObject object = reply.getPayload(TitanObject.class, objectType.getNode());
            if (object != null) {
                if (!DeleteableObject.deleteTokenIsValid(object.getMetadata())) {
                    return object;
                }
                throw new TitanObjectStoreImpl.DeletedObjectException();
            } else {
                objectType.getLogger().info("ObjectId=%s not found", objectId);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (ClassCastException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static TitanObject retrieveRemoteObject(RetrievableFragmentedObject.Handler<? extends RetrievableFragmentedObject.Handler.Object> objectType, StorableFragmentedObject.FragmentMap map)
    throws ClassCastException, ClassNotFoundException, ErasureCode.UnsupportedAlgorithmException, ErasureCode.NotRecoverableException,
        TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NotFoundException {
        TitanObject object = retrieveRemoteObject(objectType, map.getObjectId());
        if (object != null) {
            if (DeleteableObject.deleteTokenIsValid(object.getMetadata())) {
                return null;
            }
            return object;
        }

        // Failure to retrieve the object directly means we need to reconstruct it from fragments

        TitanGuid[] frags = map.getFragments();
        ErasureCode erasureCoder = map.getErasureCoder();
        objectType.getNode().getLogger().info("retrieveRemoteObject: reconstructing " + map.getObjectId() + " using " + erasureCoder);

        if (erasureCoder.getFragmentCount() != frags.length) {
            throw new ErasureCode.NotRecoverableException("FragmentMap contains different number of fragments specified by the erasure-coder");
        }

        FObjectType.FObject[] fObjects = new FObjectType.FObject[erasureCoder.getMinimumFragmentCount()];

        FObjectType fragmentObjectHandler;
        try {
            fragmentObjectHandler = (FObjectType) objectType.getNode().getService(CelesteNode.OBJECT_PKG + ".FObjectType");
        } catch (NullPointerException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        } catch (IllegalArgumentException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        } catch (NoSuchMethodException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        } catch (InstantiationException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        } catch (IllegalAccessException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        } catch (InvocationTargetException e1) {
            throw new ErasureCode.NotRecoverableException(e1);
        }

        FObjectType.FObject fObject;

        int goodFragmentCount = 0;
        for (TitanGuid fragmentObjectId : frags) {
            fObject = fragmentObjectHandler.retrieve(fragmentObjectId);
            if ((fObjects[goodFragmentCount] = fObject) == null) {
                objectType.getNode().getLogger().info("Fragment fetch failed: " + fragmentObjectId);
            } else {
                goodFragmentCount++;
            }
            if (goodFragmentCount >= erasureCoder.getMinimumFragmentCount()) {
                objectType.getNode().getLogger().info("Enough fragments: " + goodFragmentCount);
                break;
            }
        }

        if (goodFragmentCount < erasureCoder.getMinimumFragmentCount()) {
            throw new ErasureCode.NotRecoverableException("Insufficient fragments " + map.getObjectId());
        }

        try {
            byte[][] fragments = new byte[goodFragmentCount][];
            for (int i = 0; i < fragments.length; i++) {
                fragments[i] = fObjects[i].getContents();
            }
            try {
                byte[] data = erasureCoder.decodeData(fragments);
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bis);
                TitanObject o = (TitanObject) ois.readObject();
                return o;
            } catch (ClassCastException e) {
                e.printStackTrace();
                throw new ErasureCode.NotRecoverableException(e);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new ErasureCode.NotRecoverableException(e);
            }
        } catch (IOException io) {
            throw new ErasureCode.NotRecoverableException(io);
        } catch (ErasureCode.InsufficientDataException insufficientData) {
            insufficientData.printStackTrace();
            throw new ErasureCode.NotRecoverableException(insufficientData.toString());
        }
    }
}
