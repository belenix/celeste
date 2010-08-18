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
package sunlabs.titan.node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

import sunlabs.asdf.util.AbstractStoredMap;
import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.Units;
import sunlabs.asdf.util.AbstractStoredMap.OutOfSpace;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.exception.BeehiveException;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.services.PublishDaemon.UnpublishObject;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.xml.TitanXML;
import sunlabs.titan.node.services.xml.TitanXML.XMLObject;
import sunlabs.titan.node.services.xml.TitanXML.XMLObjectStore;

/**
 * <p>
 * This class represents the Beehive object store.
 * </p>
 * <p>
 * The ObjectStore does three things:
 * <ul>
 * <li>Stores objects in the local store,</li>
 * <li>Retrieves objects from the local store,</li>
 * <li>Iterates through objects in the local store.</li>
 * </ul>
 * </p>
 * <p>
 * When storing objects in the store,
 * it is either the creation of a new object which does
 * not exist in the local store,
 * or storing a replacement of an existing object
 * with an anti-object or a signature verified object.
 * </p>
 * <p>
 * Creating a new object consists of:
 * <ul>
 *  <li>first validating the object to be stored.
 *   (throws InvalidObjectException(DeleteTokenException, InvalidObjectIdException))</li>
 *  <li>locking the object store on the new object's objectId</li>
 *  <li>ensuring the object does not already exist (throws ObjectExistenceException)</li>
 *  <li>and then creating it in place. (throws NoSpaceException, IOException)</li>
 * </ul>
 * If the object is returned as a result, the object store lock should be left in place.
 * </p>
 * <p>
 * Storing a replacement object consists of
 * <ul>
 *  <li>first validating the object to be stored. (throws InvalidObjectException(DeleteTokenException, InvalidObjectIdException))</li>
 *  <li>locking the object store on the object's objectId,</li>
 *  <li>ensuring the object already exists (throws ObjectExistenceException)</li>
 *  <li>storing the object in the local store. (throws NoSpaceException, IOException)</li>
 * </ul>
 * If the object is returned as a result, the object store lock should be left in place.
 * </p>
 * <p>
 * Objects in the object store are either of the type that is static in nature and are maintained
 * in a backing store, or they are of the type that is dynamic in nature and the actual content
 * of the object is produced when the object is fetched.
 * </p>
 * <p>
 * The object store presents two levels of interfaces to the objects in the store.
 * The first level is an interface to the programmatic representatives of objects
 * in the store.  This interface is characterised by the method names
 * <code>get</code>*, <code>put</code>*, and <code>remove</code>.
 * </p>
 * <p>
 * The object store also maintains the lists of object pointers which are used to
 * locate object stored on other Beehive Nodes.
 * </p>
 * <p>
 * The object store also maintains the list of objects for which  this Node is the root.
 * </p>
 */
public final class BeehiveObjectStore implements ObjectStore {

    /**
     *
     *
     */
    abstract public static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;

        public Exception() {
            super();
        }

        public Exception(Throwable cause) {
            super(cause);
        }

        public Exception(String message) {
            super(message);
        }

        public Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class InvalidObjectException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public InvalidObjectException() {
            super();
        }

        public InvalidObjectException(String format, Object...args) {
            super(String.format(format, args));
        }

        public InvalidObjectException(String message) {
            super(message);
        }

        public InvalidObjectException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * Thrown when an object that is supposed to already exist does not,
     * or when an object that is not supposed to exist does.
     *
     */
    public static class ObjectExistenceException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public ObjectExistenceException() {
            super();
        }

        public ObjectExistenceException(String format, Object...args) {
            super(String.format(format, args));
        }

        public ObjectExistenceException(String message) {
            super(message);
        }

        public ObjectExistenceException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * Thrown when an object that is supposed to already exist does not,
     * or when an object that is not supposed to exist does.
     *
     */
    public static class NotFoundException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public NotFoundException() {
            super();
        }

        public NotFoundException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(Throwable reason) {
            super(reason);
        }
    }

    /**
     *
     *
     */
    public static class NoSpaceException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public NoSpaceException() {
            super();
        }

        public NoSpaceException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NoSpaceException(String message) {
            super(message);
        }

        public NoSpaceException(Throwable reason) {
            super(reason);
        }
    }

    /**
     *
     *
     */
    public static class DeleteTokenException extends BeehiveObjectStore.Exception {
        private static final long serialVersionUID = 1L;
        public DeleteTokenException(String message) {
            super(message);
        }

        public DeleteTokenException(String format, Object...args) {
            super(String.format(format, args));
        }

        public DeleteTokenException(Throwable reason) {
            super(reason);
        }
    }

    /**
     *
     *
     */
    public static class InvalidObjectIdException extends BeehiveException {
        private static final long serialVersionUID = 1L;
        public InvalidObjectIdException(String message) {
            super(message);
        }

        public InvalidObjectIdException(String format, Object...args) {
            super(String.format(format, args));
        }

        public InvalidObjectIdException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * Signal that the proffered BeehiveObject is
     * not acceptable to the object store.
     *
     */
    public static class UnacceptableObjectException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public UnacceptableObjectException() {
            super();
        }

        public UnacceptableObjectException(String format, Object...args) {
            super(String.format(format, args));
        }

        public UnacceptableObjectException(String message) {
            super(message);
        }

        public UnacceptableObjectException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * Signal that the desired BeehiveObject has been deleted.
     *
     * A deleted BeehiveObject has meta-data such that
     * the {@code deleteToken != null && deleteTokenId != null && deleteTokenId.equals(deleteToken.getObjectId())}
     */
    public static class DeletedObjectException extends BeehiveObjectStore.Exception {
        private final static long serialVersionUID = 1L;
        public DeletedObjectException() {
            super();
        }

        public DeletedObjectException(String format, Object...args) {
            super(String.format(format, args));
        }

        public DeletedObjectException(String message) {
            super(message);
        }

        public DeletedObjectException(Throwable reason) {
            super(reason);
        }
    }


    //private final ObjectLock<BeehiveObjectId> locks;
    private final ObjectLock<BeehiveObjectId> locks;

    private final BeehiveNode node;

    public static class FileObjectStore3 extends AbstractStoredMap<BeehiveObjectId,BeehiveObject> {

        /**
         * Construct a new FileObjectStore3 instance with the specified capacity.
         * 
         * @param root the {@link File} instance representing the top-level of the filesystem heirarchy for the stored object store.
         * @param capacity a String specifying the maximum size of the object store.
         * @throws IOException
         * @throws NumberFormatException if {@code capacity} cannot be parsed into a number representing size.
         */
    	public FileObjectStore3(File root, String capacity) throws IOException, NumberFormatException {
    		super(root, capacity.compareTo("unlimited") == 0 ? AbstractStoredMap.CAPACITY_UNLIMITED : Units.parseByte(capacity));
    	}

    	public File keyToFile(File root, BeehiveObjectId key) {
    		String s = key.toString();
    		StringBuilder result = new StringBuilder();
    		result.append(s.substring(0, 5)).append(File.separatorChar).append(s);
    		return new File(root, result.toString());
    	}	

    	public BeehiveObjectId fileToKey(File file) {
    		return new BeehiveObjectId(file.getName());		
    	}
    }
    
    private final FileObjectStore3 fileStore;

    /**
     * Create a local Beehive object store.  The object store
     * consists of both objects stored locally as well as the
     * back-pointers to objects stored elsewhere.
     */
    public BeehiveObjectStore(final BeehiveNode node, String objectStoreCapacity) throws IOException {
        this.node = node;
        this.fileStore = new FileObjectStore3(new File(node.getSpoolDirectory() + File.separator + "object-store" + File.separator + "object"), objectStoreCapacity);

        this.locks = new ObjectLock<BeehiveObjectId>();
    }

    public boolean containsObject(final BeehiveObjectId objectId) {
        return this.fileStore.contains(objectId);
    }

    public <C extends BeehiveObject> C tryGetAndLock(final Class<? extends C> klasse, final BeehiveObjectId objectId)
    throws ClassCastException, BeehiveObjectStore.NotFoundException {
        synchronized (this.locks) {
            if (this.locks.trylock(objectId)) {
                return this.get(klasse, objectId);
            }
        }
        return null;
    }
    
    public <C extends BeehiveObject> C get(final Class<? extends C> klasse, final BeehiveObjectId objectId)
    throws ClassCastException, BeehiveObjectStore.NotFoundException {
        try {
            if (objectId.equals(this.node.getObjectId())) {
                this.node.getLogger().warning("Why am I fetching my own node objectid?");
                new Throwable().printStackTrace();
            }
            BeehiveObject object = this.fileStore.get(objectId);

            BeehiveObjectId computedObjectId = BeehiveObjectStore.ObjectId(object);
            if (computedObjectId.equals(objectId)) {
                return klasse.cast(object);
            }
            this.node.getLogger().warning("Requested %s %s does not match computed objectId (%s)", object.getObjectType(), objectId, computedObjectId);
            new Throwable().printStackTrace();
            // fall through
        } catch (InterruptedIOException e) {
            if (this.node.getLogger().isLoggable(Level.WARNING)) {
                this.node.getLogger().warning("%s %s (%s)", e, objectId, klasse.getName());
            }
        } catch (ClassNotFoundException e) {
            if (this.node.getLogger().isLoggable(Level.WARNING)) {
                this.node.getLogger().warning("%s %s (%s)", e, objectId, klasse.getName());
            }
        } catch (FileNotFoundException e) {
           // fall through
        } catch (IOException e) {
            if (this.node.getLogger().isLoggable(Level.WARNING)) {
                this.node.getLogger().warning("%s %s (%s)", e, objectId, klasse.getName());
            }
        } catch (BeehiveObjectStore.InvalidObjectIdException e) {
            if (this.node.getLogger().isLoggable(Level.WARNING)) {
                this.node.getLogger().warning("%s %s (%s)", e.toString(), objectId, klasse.getName());
            }
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            if (this.node.getLogger().isLoggable(Level.FINE)) {
                this.node.getLogger().fine("%s: Remove local object %s", e.toString(), objectId);
            }
        }

        // The requested object is not found in the object store.
        // Unpublish the object-id in case there exists some backpointer to this
        // node for the specified object-id somewhere in the system.
        if (this.node.getLogger().isLoggable(Level.FINE)) {
            this.node.getLogger().fine("Object %s (%s) not found.", objectId, klasse.getName());
        }

        Publish app = (Publish) this.node.getService("sunlabs.titan.node.services.PublishDaemon");
        app.unpublish(objectId, UnpublishObject.Type.REQUIRED);
        throw new BeehiveObjectStore.NotFoundException("Object %s (%s) not found.", objectId, klasse.getName());
    }
    
    public <C extends BeehiveObject> C getAndLock(final Class<? extends C> klasse, final BeehiveObjectId objectId)
    throws ClassCastException, BeehiveObjectStore.NotFoundException {
        synchronized (this.locks) {
            this.lock(objectId);
            try {
                return this.get(klasse, objectId);
            } catch (BeehiveObjectStore.NotFoundException e) {
                this.unlock(objectId);
                throw e;
            } catch (ClassCastException e) {
                this.unlock(objectId);
                throw e;
            }
        }
    }

    private SortedSet<BeehiveObjectId> sortedKeySet() {
        SortedSet<BeehiveObjectId> set = new TreeSet<BeehiveObjectId>(new BeehiveObjectId.IdComparator());
        for (BeehiveObjectId objectId : this) {
            set.add(objectId);
        }

        return set;
    }

    public BeehiveObjectId create(BeehiveObject object)
    throws InvalidObjectException, ObjectExistenceException, NoSpaceException, UnacceptableObjectException {

        try {
            // Set the object's object-id by force, to ensure that it is correct.
            BeehiveObjectId computedObjectId = BeehiveObjectStore.ObjectId(object);
            object.setObjectId(computedObjectId);

            boolean needToPublish = false;

            if (this.trylock(computedObjectId)) {
                try {
                    if (!this.containsObject(computedObjectId)) {
                        this.put(object);
                        needToPublish = true;
                        return computedObjectId;
                    }
                    // fall through to throwing the Exception below
                } finally {
                    // Unlock the locked object.  If the needToPublish flag is set
                    // then we created it and need to unlock with the unlock method
                    // that emits the PublishObjectMessage.  Otherwise, we didn't
                    // create it and we just need to silently unlock.
                    if (needToPublish) {
                        if (!this.unlock(object).getStatus().isSuccessful()) {
                            throw new BeehiveObjectStore.InvalidObjectException(computedObjectId.toString());
                        }
                    } else {
                        this.unlock(computedObjectId);
                    }
                }
            }
            // If we couldn't get the lock on this object, then some other Thread
            // has a lock on it and assume that it has already been created.
            throw new BeehiveObjectStore.ObjectExistenceException("Object already exists. ObjectId=" + computedObjectId);
        } catch (DeleteTokenException e) {
            throw new InvalidObjectException(e);
        } catch (InvalidObjectIdException e) {
            throw new InvalidObjectException(e);
        } catch (IOException e){
            throw new BeehiveObjectStore.UnacceptableObjectException(e);
        }
    }

    public BeehiveObjectId update(BeehiveObject object) throws InvalidObjectException, ObjectExistenceException, NoSpaceException, UnacceptableObjectException {
        try {
            BeehiveObjectId actualObjectId = BeehiveObjectStore.ObjectId(object);
            object.setObjectId(actualObjectId);

            this.locks.assertLock(actualObjectId);
            if (!this.containsObject(actualObjectId)) {
                throw new BeehiveObjectStore.ObjectExistenceException("%s not found",  actualObjectId);
            }
            this.put(object);
            return actualObjectId;
        } catch (IOException e) {
            throw new UnacceptableObjectException(e);
        } catch (DeleteTokenException e) {
            throw new InvalidObjectException(e);
        } catch (InvalidObjectIdException e) {
            throw new InvalidObjectException(e);
        }
    }

    /**
     * Store the given {@link BeehiveObject} in the local store.
     * The object's {@link BeehiveObjectId} is computed and set in the given object.
     * <p>
     * There is no locking.
     * </p>
     * @param object
     * @throws BeehiveObjectStore.InvalidObjectException
     * @throws IOException
     */
    // XXX This should not throw IllegalArgumentException in place of IOException and NoSpaceException
    private BeehiveObject put(BeehiveObject object) throws BeehiveObjectStore.InvalidObjectException, IOException {
        try {
            object.setProperty(BeehiveObjectStore.METADATA_CREATEDTIME, Time.currentTimeInSeconds());
            this.fileStore.put(object.getObjectId(), object);
            return object;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
//        } catch (BeehiveObjectStore.NoSpaceException noSpace) {
//            throw new IllegalArgumentException(noSpace);
        } catch (IllegalStateException e) {
            throw e;
        } catch (OutOfSpace e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * NB: There is a small vulnerability here: If the object is stored in the local object store, then the {@link #unlock(BeehiveObject)}
     * method performs the publish, and the object's handler signals a failure (unacceptable, or whatever), and at that moment this node
     * reboots just before removing the now stored object, upon restarting the object will be published again due to the normal object publishing mechanism.
     * But if during the reboot, the root node was also rebooted or replaced it could be possible that the locally stored, previously unacceptable object
     * will be permitted to remain because the object's root handler will not have the necessary information to reject this object.
     * 
     */
    public BeehiveObjectId store(BeehiveObject object)
    throws BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException {

        try {
            BeehiveObjectId actualObjectId = BeehiveObjectStore.ObjectId(object);
            object.setObjectId(actualObjectId);

            this.locks.assertLock(actualObjectId);
            this.put(object);
            return actualObjectId;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (BeehiveObjectStore.InvalidObjectIdException e) {
            throw new InvalidObjectException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new InvalidObjectException(e);
        }
    }

    public boolean remove(BeehiveObject object) {
        return this.remove(object.getObjectId());
    }

    public boolean remove(BeehiveObjectId objectId) {
        this.locks.assertLock(objectId);
		//        if (this.locks.lockerId(objectId) != Thread.currentThread().getId()) {
		//            throw new IllegalStateException("Object must be locked");
		//        }

        if (this.node.getLogger().isLoggable(Level.FINE)) {
            this.node.getLogger().fine("%s", objectId);
        }
        return this.fileStore.remove(objectId);
    }

    /**
     * <p>
     * Configure the given BeehiveObject to have a signature verified object-id
     * equal to the given BeehiveObjectId.
     * </p>
     * <p>
     * The given BeehiveObject must can prepared with the metadata properties
     * {@code ObjectStore.METADATA_DELETETOKENID}
     * set.
     * </p>
     */
    public static BeehiveObject CreateSignatureVerifiedObject(BeehiveObjectId objectId, BeehiveObject object) throws BeehiveObjectStore.DeleteTokenException {
        String deleteTokenId = object.getDeleteTokenId().toString();

        BeehiveObjectId dataHash = object.getDataId();

        BeehiveObjectId voucher = new BeehiveObjectId(deleteTokenId)
        .add(objectId)
        .add(dataHash);

        object
        .setProperty(ObjectStore.METADATA_OBJECTID, objectId)
        .setProperty(ObjectStore.METADATA_DATAHASH, dataHash)
        .setProperty(ObjectStore.METADATA_VOUCHER, voucher);
        object.setObjectId(objectId);

//        System.err.printf("ObjectId %5.5s... Voucher %5.5s... deleteId %5.5s... dataId %5.5s...\n", objectId, voucher, deleteTokenId, dataHash);

        return object;
    }

    /**
     * Calculate the {@link BeehiveObjectId} for the given {@link BeehiveObject} in accordance
     * with the description of object verification in the documentation
     * for the {@code BeehiveObject} interface.
     * 
     * See {@link BeehiveObject}.
     *
     * @param object the {@code BeehiveObject} to compute the {@code BeehiveObjectId}.
     * @throws BeehiveObjectStore.InvalidObjectIdException if a valid BeehiveObjectId
     *         cannot be calculated.
     * @throws BeehiveObjectStore.DeleteTokenException if the Delete Token hash
     *         (specified by the {@link ObjectStore#METADATA_DELETETOKENID}
     *         property in the meta-data) is either missing, or (if present)
     *         does not match the hash of the Delete Token itself (specified
     *         by the {@code ObjectStore#METADATA_DELETETOKENID} property
     *         in the meta-data) does not match the Delete Token Hash
     */
    public static BeehiveObjectId ObjectId(BeehiveObject object) throws BeehiveObjectStore.InvalidObjectIdException, BeehiveObjectStore.DeleteTokenException {
        BeehiveObjectId dataHash = object.getDataId();
        BeehiveObjectId deleteTokenId = object.getDeleteTokenId();

        BeehiveObjectId voucher = object.getPropertyAsObjectId(ObjectStore.METADATA_VOUCHER, null);
        BeehiveObjectId deleteToken = object.getPropertyAsObjectId(ObjectStore.METADATA_DELETETOKEN, null);

        //
        // If a voucher is present in the meta-data, then this object is either
        // Signature/Hash or Delete-Token verifiable. In these cases, the
        // object's objectId is explicitly set in the metadata, and it is
        // verified by ensuring that the computed cummulative hash of the
        // ObjectStore.DeleteTokenId, the ObjectStore.ObjectId, and the
        // ObjectStore.DataHash properties is equal to the voucher.
        //
        // XXX Signatures are incompletely implemented.
        //
        BeehiveObjectId objectId;
        if (voucher != null) {
            objectId = object.getPropertyAsObjectId(ObjectStore.METADATA_OBJECTID, null);
            if (objectId == null) {
                System.err.println("************** OBJECT MISSING OBJECTID IN METADATA");
            }
            if (deleteTokenId.add(objectId).add(dataHash).equals(voucher)) {
                // If the delete token is exposed, check for the correct conditions for a Delete Token verifiable object.
                if (deleteToken != null) {
                    if (!deleteTokenId.equals(deleteToken.getObjectId())) {
                        System.err.printf("%s deleteTokenId=%s, deleteToken=%s\n", object.getObjectType(), deleteTokenId, deleteToken);
                        throw new BeehiveObjectStore.DeleteTokenException("Delete-token does not match delete-token-object-id");
                    }
                }
            } else {
                System.err.println(object.getObjectType());
                System.err.printf("ObjectId %5.5s... Voucher %5.5s... deleteId %5.5s... dataId %5.5s...\n", objectId, voucher, deleteTokenId, dataHash);

                throw new BeehiveObjectStore.InvalidObjectIdException("Signature/Hash attestation failed: " +
                        String.format("ObjectId %5.5s... Voucher %5.5s... deleteId %5.5s... dataId %5.5s...\n", objectId, voucher, deleteTokenId, dataHash));
            }
        } else { //This object is Data verifiable only.
            objectId = dataHash.add(deleteTokenId);
        }
        return objectId;
    }

    /**
     * Try to obtain a lock on the given Object-Id. No assumption is made about
     * whether or not the Object-Id corresponds to a real object.
     * @param objectId
     * @return
     */
    private boolean trylock(BeehiveObjectId objectId) {
        return this.locks.trylock(objectId);
    }

    /**
     * Obtain a lock on the given Object-Id.  No assumption is made about
     * whether or not the Object-Id corresponds to a real object.
     *
     * If the object-id is not locked, lock it and return.
     * If the object-id is locked and the lock is already held by this Thread,
     * then simply return. Otherwise, queue for the lock to be released by a
     * call to unlock() with this object-id.
     */
    public void lock(BeehiveObjectId objectId) {
        this.lock(objectId, false);
    }

    private void lock(BeehiveObjectId objectId, boolean trace) {
        this.locks.lock(objectId, trace);
    }

    /**
     * Release a lock on the given {@link BeehiveObjectId}.
     * No assumption is made about whether or not {@code objectId} actually
     * corresponds to a real object in the object store.
     * <p>
     * Note that the unlocked object is NOT published or unpublished.
     * To automatically publish or unpublish an object use
     * {@link BeehiveObjectStore#unlock(BeehiveObject) unlock(BeehiveObject)}
     * </p>
     */
    public void unlock(BeehiveObjectId objectId) {
        this.unlock(objectId, false);
    }

    public void unlock(BeehiveObjectId objectId, boolean trace) {
        this.locks.unlock(objectId, trace);
    }

    /**
     * Unlock the given object, by its {@link BeehiveObject#getObjectId()}.
     * <p>
     * If the object is in the local store transmit a Publish Object message.
     * If the object is NOT in the local store transmit an Unpublish Object message.
     * </p>
     */
    public BeehiveMessage unlock(BeehiveObject object) {
        // The object must be locked by the invoking thread.
        this.locks.assertLock(object.getObjectId());

        // After all the processing that this BeehiveObject has gone through,
        // if it still exists in the object store, then we emit a Publish message.
        // Otherwise, we emit an Unpublish message.
        if (this.fileStore.contains(object.getObjectId())) {
            // XXX Could cache this object temporarily in memory in case another thread is waiting for it.
            // Otherwise the other Thread will have to fetch it from disk again.
            //
            return this.unlockAndPublish(object);
        }

        return this.unlockAndUnpublish(object, UnpublishObject.Type.REQUIRED, false);
    }

    /**
     * Unlock a {@link BeehiveObject} and transmit a {@link PublishObjectMessage}.
     * <p>
     * This transmits a single {@code PublishObjectMessage} to the root of the objectId
     * of the object being published.
     * </p><p>
     * The message travels across the DHT until it reaches the root of the
     * objectId.
     * When the root receives the message, the AbstractObjectType specified in
     * the metadata accompanying the {@code PublishObjectMessage} (using the key
     * {@code ObjectStore.METADATA_TYPE}) is dispatched <em>if</em> the
     * corresponding AbstractObjectType implementation implements the
     * {link BeehiveMessage#publishObject(BeehiveObject message) publichObject} method.
     * </p><p>
     * The {@link BeehiveMessage} returned from the {@code publishObject} method is
     * propagated back as a reply to the original node publishing the object.
     * </p><p>
     * If the status encoded in the BeehiveMessage reply is any of the codes
     * representing success, each node propagating the reply is free to record
     * a backpointer to the object on the publishing node.
     * If the status encoded in the BeehiveMessage reply is NOT any of the codes
     * representing success, each node propagating the reply must not record
     * a backpointer to the object on the publishing node.
     * </p>
     */
    private BeehiveMessage unlockAndPublish(BeehiveObject object) {
        return this.unlockAndPublish(object, false);
    }

    /**
     * Returns the BeehiveMessage result from the object's handler publish method.
     * @param object
     * @param trace
     * @return
     */
    private BeehiveMessage unlockAndPublish(BeehiveObject object, boolean trace) {
    	try {
    		Publish publish = (Publish) this.node.getService("sunlabs.titan.node.services.PublishDaemon");

    		BeehiveMessage result = publish.publish(object);

    		if (!result.getStatus().isSuccessful()) {
    			// This publish was *not* successful we must not store the
    			// object. Forcibly remove it without emitting an unpublish message.
    			if (this.node.getLogger().isLoggable(Level.FINE)) {
    				this.node.getLogger().fine("%s signaling to not store object %s", result.getStatus().toString(), object.getObjectId());
    			}
    			this.fileStore.remove(object.getObjectId());
    		}
    		return result;
    	} finally {
    		this.unlock(object.getObjectId(), false);
    	}
    }

    /**
     * Unpublish the given {@link BeehiveObject}, returning the reply {@link BeehiveMessage} from the root of the object's {@link BeehiveObjectId}.
     * 
     * @param object
     * @param type
     * @param trace
     * @return
     */
    private BeehiveMessage unlockAndUnpublish(BeehiveObject object, UnpublishObject.Type type, boolean trace) {
    	try {
    		Publish publisher = (Publish) this.node.getService("sunlabs.titan.node.services.PublishDaemon");
    		BeehiveMessage result = publisher.unpublish(object, type);
    		return result;
    	} finally {
    		this.unlock(object.getObjectId(), false);    		
    	}
    }

    /**
     * Produce an iterator for all objects in the Object Store.
     */
    public Iterator<BeehiveObjectId> iterator() {
        return this.fileStore.iterator();
    }
    
    public XMLObjectStore toXML() {
        TitanXML xml = new TitanXML();

        long currentTimeSeconds = Time.currentTimeInSeconds();
        Set<BeehiveObjectId> objects = this.sortedKeySet();
        XMLObjectStore result = xml.newXMLObjectStore();
        for (BeehiveObjectId objectId : objects) {
            try {
                BeehiveObject object = this.fileStore.get(objectId);

                XMLObject o = xml.newXMLObject(object.getObjectId(),
                        object.getObjectType(),
                        object.isDeleted(),
                        this.fileStore.sizeOf(objectId),
                        Long.toString((object.getCreationTime() + object.getTimeToLive()) - currentTimeSeconds),
                        object.getProperty(ObjectStore.METADATA_REPLICATION_STORE),
                        object.getProperty(ObjectStore.METADATA_REPLICATION_LOWWATER),
                        object.getProperty(ObjectStore.METADATA_REPLICATION_HIGHWATER) == null ? "?" : object.getProperty(ObjectStore.METADATA_REPLICATION_HIGHWATER));
                result.add(o);
            } catch (ClassCastException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }            
        }

        return result;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        Set<BeehiveObjectId> objects = this.sortedKeySet();

        XHTML.Table.Row header = new XHTML.Table.Row();

        int columns = 2;
        for (int i = 0; i < columns; i++) {
            header.add(new XHTML.Table.Heading("Object Identifier"),
                    new XHTML.Table.Heading("Size"),
                    new XHTML.Table.Heading("Replication"),
                    new XHTML.Table.Heading("Time To Live"),
                    new XHTML.Table.Heading("")
            );
        }

        int column = 0;
        XHTML.Table.Head thead = new XHTML.Table.Head(header);
        XHTML.Table.Body tbody = new XHTML.Table.Body();
        XHTML.Table.Row row = new XHTML.Table.Row();

        long currentTimeSeconds = Time.currentTimeInSeconds();

        XHTML.Table.Data objectIdCell = null;

        // The actions specified here are handled in the WebDAVDaemon.java client interface.

        for (BeehiveObjectId objectId : objects) {
            try {
                BeehiveObject dolrObject = this.fileStore.get(objectId);

                XHTML.Anchor deleteButton = new XHTML.Anchor("X")
                .setHref("?action=unpublish-object&objectId=" + objectId)
                .setTitle("Delete and Unpublish (induces object replication remediation)")
                .setClass("toggle-button on");

                XHTML.Anchor removeButton = new XHTML.Anchor("R")
                .setHref("?action=remove-object&objectId=" + objectId)
                .setTitle("Remove without Unpublishing (simulates an object failure)")
                .setClass("toggle-button on");

                XHTML.Anchor inspectButton = WebDAVDaemon.inspectObjectXHTML(objectId).setTitle(dolrObject.getObjectType());

                objectIdCell = new XHTML.Table.Data(inspectButton).setClass("objectId");

                if (dolrObject.isDeleted()) {
                    objectIdCell.addClass("deleted");
                }

                String remainingTimeToLive = (dolrObject.getTimeToLive() == BeehiveObject.INFINITE_TIME_TO_LIVE) ?
                		"forever"
                		: Long.toString((dolrObject.getCreationTime() + dolrObject.getTimeToLive()) - currentTimeSeconds); 
                String replicationInfo = String.format("%s &le; %s &le; %s",
                		dolrObject.getProperty(ObjectStore.METADATA_REPLICATION_LOWWATER),
                		dolrObject.getProperty(ObjectStore.METADATA_REPLICATION_STORE),
                		dolrObject.getProperty(ObjectStore.METADATA_REPLICATION_HIGHWATER) == null ? "?" : dolrObject.getProperty(ObjectStore.METADATA_REPLICATION_HIGHWATER));
                row.add(objectIdCell,
                        new XHTML.Table.Data("%d", this.fileStore.sizeOf(objectId)),
                        new XHTML.Table.Data(replicationInfo),
                        new XHTML.Table.Data(remainingTimeToLive),
                        new XHTML.Table.Data(deleteButton, removeButton)
                );
                if (++column > 1) {
                    tbody.add(row);
                    row = new XHTML.Table.Row();
                    column = 0;
                    objectIdCell = null;
                }

            } catch (IOException skip) {
                /**/
            } catch (ClassNotFoundException skip) {
                /**/
            }
        }
        if (row.getChildren().size() > 0) {
            tbody.add(row);
        }

        XHTML.Table.Caption caption = new XHTML.Table.Caption("%s: %d used, %d available",
        		Units.longToCapacityString(this.fileStore.getSpoolCapacity()),
        		this.fileStore.computeCurrentSpoolSize(),
        		this.fileStore.getSpoolAvailable());
//        		this.fileStore.getCacheHit);
        XHTML.Table table = new XHTML.Table(caption, thead, tbody);
        table.setClass("objectStore");
        table.setId("objectStore");
        return new XHTML.Div(table).setClass("section");
    }
}
