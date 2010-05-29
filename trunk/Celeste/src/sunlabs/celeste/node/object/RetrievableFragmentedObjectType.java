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


//public interface RetrievableFragmentedObjectType<T extends BeehiveObject> extends BeehiveObjectHandler {
//    public interface ObjectAPI extends BeehiveObjectHandler.ObjectAPI {
//
//    }
//
//    /**
//     * Given a FragmentMap for a {@link BeehiveObject} stored somewhere in the system, return a copy of that BeehiveObject.
//     * If the BeehiveObject cannot be located,
//     * reconstruct it from the fragments listed in the FragmentMap, store it locally, and return it.
//     *
//     * @param map
//     * @return The BeehiveObject retrieved from the object pool, or null if the object could not be retrieved.
//     * @throws ErasureCode.UnsupportedAlgorithmException
//     * @throws ErasureCode.NotRecoverableException
//     * @throws BeehiveObjectStore.DeletedObjectException
//     */
//    public T retrieveRemoteObject(StorableFragmentedObject.Handler.FragmentMap map)
//    throws ErasureCode.UnsupportedAlgorithmException, ErasureCode.NotRecoverableException, BeehiveObjectStore.DeletedObjectException;
//
//    /**
//     * Retrieve the local object described in the {@link BeehiveMessage}.
//     * <p>
//     * This is the "server" side of the retrieve and the object is always retrieved and returned even if it is deleted.
//     * </p>
//     * <p>
//     * The check for a deleted object is to be checked on the client side.
//     * </p>
//     */
//    public BeehiveMessage retrieveLocalObject(BeehiveMessage message);
//}
