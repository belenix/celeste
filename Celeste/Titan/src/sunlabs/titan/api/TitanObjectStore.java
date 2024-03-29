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
package sunlabs.titan.api;

import java.io.IOException;

import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanObjectStoreImpl.InvalidObjectException;
import sunlabs.titan.node.TitanObjectStoreImpl.NoSpaceException;
import sunlabs.titan.node.TitanObjectStoreImpl.UnacceptableObjectException;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.UnpublishObjectMessage;
import sunlabs.titan.node.object.TitanObjectHandler;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.xml.TitanXML.XMLObjectStore;

// Not sure this interface is that useful as opposed to just using the class definition

public interface TitanObjectStore extends XHTMLInspectable, Iterable<TitanGuid> {
    /** If set, this object must not be cached */
    public final static String METADATA_UNCACHABLE = "ObjectStore.Uncachable";

    /** The {@link TitanObjectHandler} that controls this object. Mandatory. */
    public final static String METADATA_CLASS = "ObjectStore.Type";

//    /** If set, this object is a persistent object. */
//    @Deprecated
//    public final static String METADATA_PERSISTENT = "ObjectStore.Replication.KeepAlive";

    /**
     * The name of an object meta-data property that specifies the
     * number of unique copies to be made when the object is stored
     */
    public final static String METADATA_REPLICATION_STORE = "ObjectStore.Replication.Store";

    /**
     * The number of lazily cached copies that are to be made.
     */
    public final static String METADATA_REPLICATION_LOWWATER = "ObjectStore.Replication.LowWater";

    /**
     * The maximum number of lazily cached copies permitted.
     */
    public final static String METADATA_REPLICATION_HIGHWATER = "ObjectStore.Replication.HighWater";

    /**
     * The number of seconds remaining for this object to exist on the node hosting it.
     */
    public final static String METADATA_SECONDSTOLIVE = "ObjectStore.SecondsToLive";

    /**
     * The time that this object was created (or updated) according to the local node's clock (in seconds).
     * <i>(ObjectStore.CreatedTime + ObjectStore.TimeToLive)</i> is the time (in seconds) that this object will be removed from this node.
     */
    public final static String METADATA_CREATEDTIME = "ObjectStore.CreatedTime";

    // Verifiable Object Management
    /** The secret Delete Token. */
    public final static String METADATA_DELETETOKEN = "ObjectStore.DeleteToken";

    /** The {@link TitanGuid} of the (secret) Delete Token. */
    public final static String METADATA_DELETETOKENID = "ObjectStore.DeleteTokenId";
    public final static String METADATA_DATAHASH = "ObjectStore.DataHash";
    public final static String METADATA_OBJECTID = "ObjectStore.ObjectId";
    public final static String METADATA_VOUCHER = "ObjectStore.Voucher";
    
    /**
     * Obtain a lock on the given {@link TitanGuid}.
     * The given {@code TitanGuid} may or may not refer to an object already in the local object store.
     * <ul>
     * <li>If the object-id is not locked, lock it and return.</li>
     * <li>If the object-id is locked and the lock is already held by this Thread, then simply return.</li>
     * <li>Otherwise, queue for the lock to be released by a call to {@link #unlock(TitanGuid)} {@link #unlock(TitanObject)} with this object-id (or object).</li>
     * </ul>
     * @see #unlock(TitanGuid)
     * @see #unlock(TitanObject)
     */
    public void lock(TitanGuid objectId);

    /**
     * True, if the local object store contains the object named by {@code objectId}.
     */
    public boolean containsObject(TitanGuid objectId);

    /**
     * Create and publish an object in the local object store.
     *
     * @param object

     * @throws TitanObjectStoreImpl.InvalidObjectException The {@link TitanObject} is invalid because of incorrect parameters.
     * @throws TitanObjectStoreImpl.ObjectExistenceException The {@link TitanObject} already exists.
     * @throws TitanObjectStoreImpl.NoSpaceException There is no space on this node for the {@link TitanObject}
     * @throws TitanObjectStoreImpl.UnacceptableObjectException The {@link TitanObject} is unacceptable as it is presented due
     *         to some inconsistency or the object's {@link TitanObjectHandler} rejected it during publishing.
     * @throws ClassNotFoundException 
     */
    public TitanGuid create(TitanObject object) throws TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException,
        TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.UnacceptableObjectException, ClassNotFoundException;

    /**
     * Given a {@link TitanGuid} get the corresponding {@link TitanObject} from the local object store.
      * <p>
     * If the object is not found, the node
     * will perform a clean-up and <em>unpublish</em> of the specified {@code TitanGuid}
     * lest the system have some residual, but incorrect, location information for the
     * object-id and {@link sunlabs.titan.node.TitanObjectStoreImpl.NotFoundException} is thrown.
     * </p>
     * <p>
     * The {@link TitanObject} instance returned from this method cannot be put back in the object store
     * (ie. the subject of a {@link TitanObjectStore#store(TitanObject) store} or
     * {@link TitanObjectStore#update(TitanObject) update} method).
     * </p>
     * <p>
     * See
     * {@link TitanObjectStore#getAndLock getAndLock}
     * and
     * {@link TitanObjectStore#tryGetAndLock tryGetAndLock}
     * to obtain an instance of a {@code BeehiveObject}
     * that you can put back in the object store.
     * </p>
     * @param klasse
     * @param objectId
     * @throws ClassCastException
     */
    public <C extends TitanObject> C get(final Class<? extends C> klasse, final TitanGuid objectId) throws ClassCastException,
        TitanObjectStoreImpl.NotFoundException;

    /**
     * Given a {@link TitanGuid} get the corresponding {@link TitanObject} from the local object store.
     * <p>
     * If the object is not found, the node
     * will perform a clean-up and <em>unpublish</em> of the specified {@code TitanGuid}
     * lest the system have some residual, but incorrect, location information for the
     * object-id and {@link TitanObjectStoreImpl.NotFoundException} is thrown.
     * </p>
     * <p>
     * The {@code TitanObject} instance returned from this method is locked and must be explicitly unlocked
     * (see {@link TitanObjectStore#unlock(TitanObject)})
     * </p>
     * <p>
     * See
     * {@link TitanObjectStore#get} to obtain an instance of a {@link TitanObject} which is not locked.
     * </p>
     *
     * @param objectId the {@link TitanGuid} of the stored object to retrieve.
     * @throws ClassCastException if the stored object is not the specified class.
     * @throws TitanObjectStoreImpl.NotFoundException if the stored object is not found
     */
    public <C extends TitanObject> C getAndLock(final Class<? extends C> klasse, final TitanGuid objectId) throws ClassCastException,
        TitanObjectStoreImpl.NotFoundException;

    /**
     * Given a {@link TitanGuid} try to obtain a locked instance of the corresponding
     * {@link TitanObject} from the local object store.
     * (See {@link #getAndLock(Class, TitanGuid)})
     * <p>
     * If the object cannot be immediately locked, the return result is {@code null}.
     * </p>
     *
     * @param objectId the {@link TitanGuid} of the stored object to retrieve.
     * @throws ClassCastException if the stored object is not the specified class.
     * @throws TitanObjectStoreImpl.NotFoundException if the stored object is not found
     */
    public <C extends TitanObject> C tryGetAndLock(final Class<? extends C> klasse, final TitanGuid objectId) throws ClassCastException,
        TitanObjectStoreImpl.NotFoundException;

    /**
     * Store (either create or update as appropriate) the given {@link TitanObject} in the object store.
     * The object must be already locked by the current thread (see {@link #lock(TitanGuid)}).
     * <p>
     * As a side-effect, the {@link TitanGuid} of the object is computed and set via {@link TitanObject#setObjectId(TitanGuid)}.
     * </p>
     */
    public TitanGuid store(TitanObject object) throws InvalidObjectException, NoSpaceException, UnacceptableObjectException;

    /**
     * Update an existing {@link TitanObject} in the object store.
     * The object <em>must</em> already exist.
     * The object <em>must</em> be locked by the current Thread (see {@link #lock(TitanGuid)}).
     * @throws TitanObjectStoreImpl.InvalidObjectException
     * @throws TitanObjectStoreImpl.ObjectExistenceException
     * @throws TitanObjectStoreImpl.NoSpaceException
     * @throws UnacceptableObjectException
     * @throws IOException;
     */
    public TitanGuid update(TitanObject object) throws TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException, TitanObjectStoreImpl.NoSpaceException, UnacceptableObjectException;

    /**
     * Remove a (locked) {@link TitanObject} from the local object store.
     * <p>
     * The {@link TitanObject} must be locked by the object store.
     * (see {@link #getAndLock(Class, TitanGuid)}, and {@link #lock(TitanGuid)}
     * </p>
     */
    public boolean remove(TitanObject object) throws IllegalArgumentException;

    /**
     * Remove a (locked) {@link TitanGuid} from the local object store.  An Unpublish message is NOT sent.
     * <p>
     * The  {@link TitanGuid} must be locked by the object store.
     * (see {@link #getAndLock(Class, TitanGuid)}, and {@link #lock(TitanGuid)}
     * </p>
     */
    public boolean remove(TitanGuid objectId) throws IllegalArgumentException;

    /**
     * Unlock a previously locked object in the object store.
     * <p>
     * NOTE: this method does NOT cause a Publish or Unpublish message to be emitted.
     * </p>
     * @see TitanObjectStore#unlock(TitanObject)
     *
     * @param objectId the {@link TitanGuid} of the {@link TitanObject} to lock.
     */
    public void unlock(TitanGuid objectId);

    /**
     * Unlock the given object and if the object is in the local store, emit a {@link PublishObjectMessage}.
     * If the object is NOT in the local store, emit an {@link UnpublishObjectMessage}.
     * @throws TitanObjectStoreImpl.Exception 
     * @throws BeehiveObjectPool.Exception 
     * @throws ClassNotFoundException 
     *
     * @see TitanObjectStore#unlock(TitanGuid)
     */
    public Publish.PublishUnpublishResponse unlock(TitanObject object) throws ClassNotFoundException, TitanObjectStoreImpl.Exception, BeehiveObjectPool.Exception;

    public XMLObjectStore toXML();
}
