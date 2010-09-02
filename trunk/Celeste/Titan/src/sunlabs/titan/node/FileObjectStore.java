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
package sunlabs.titan.node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.Iterator;

import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.Units;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanGuid;

/**
 * This class implements the Beehive object store for a single node, storing objects in files.
 * 
 * Object sizes are limited to Integer.MAX_VALUE;
  */
public final class FileObjectStore<K,V> implements Iterable<TitanGuid> {
    private final static long CAPACITY_UNLIMITED = -1;

    private final File objectStoreDirectory;
    private long capacityLimit;
    private long currentSpoolSize;

    /**
     * @param node                  the node for which this {@code
     *                              FileObjectStore} stores objects
     * @param objectStoreCapacity   the maximum number of bytes that this
     *                              object store may consume
     */
    public FileObjectStore(File spoolDirectory, String objectStoreCapacity) throws IOException {
        this.objectStoreDirectory = spoolDirectory;

        if (!this.objectStoreDirectory.exists()) {
            if (!this.objectStoreDirectory.mkdirs())
                throw new IOException("Cannot create " + this.objectStoreDirectory.toString());
        }
        if (objectStoreCapacity.compareTo("unlimited") == 0) {
            this.capacityLimit = CAPACITY_UNLIMITED;
        } else {
            this.capacityLimit = Units.parseByte(objectStoreCapacity);
            if (this.capacityLimit == -1)
                this.capacityLimit = CAPACITY_UNLIMITED;
        }

        this.currentSpoolSize = this.computeCurrentSpoolSize();
    }

    /**
     * Compute the total amount of storage space consumed by this FileObjectStore.
     * <p>
     * This is an expensive operation, so use carefully.
     * </p>
     * <p>
     * Note that this computes the space consumed by the stored objects,
     * but does not include the overhead consumed by the file system or other "housekeeping" information.
     * </p>
     */
    public long computeCurrentSpoolSize() {
        long size = 0;

        for (TitanGuid objectId : this) {
            size += this.sizeOf(objectId);
        }
        return size;
    }
    
    /**
     * Return the number of bytes consumed by the object store.
     * <p>
     * If the current size is not known, {@link #computeCurrentSpoolSize()} is called to compute it.
     * </p>
     */
    public synchronized long getCurrentSpoolSize() {
    	if (this.currentSpoolSize == -1)
    		this.computeCurrentSpoolSize();
    	return this.currentSpoolSize;
    }
    
    /**
     * Get the maximum number of bytes that this file store is allowed to consume.
     */
    public long getSpoolCapacity() {
    	return this.capacityLimit;
    }

    /**
     * Get the number of bytes that are available for this file store to consume.
     * @return
     */
    public long getSpoolAvailable() {
    	return this.capacityLimit - this.currentSpoolSize;
    }

    public boolean contains(TitanGuid objectId) {
        return new File(this.objectStoreDirectory, objectId.toString()).exists();
    }

    /**
     * Return an existing {@link TitanObject} instance for the specified {@link TitanGuid}.
     * <p>
     * Return {@code null} if the specified object does not exist.
     * </p>
     *
     * @param objectId  the BeehiveObjectId of the object to fetch
     *
     * @throws IOException if the underlying system throws {@code IOException}.
     * @throws ClassCastException if the stored object is not of the expected class {@code klasse}.
     * @throws ClassNotFoundException if the stored object deserialises to a non-existent class.
     * @throws FileNotFoundException if the file storing the object does not exist.
     */
//    public <C> C get(Class<? extends C> klasse, BeehiveObjectId objectId)
//    throws IOException, ClassCastException, ClassNotFoundException, FileNotFoundException {
//        FileInputStream fin = null;
//        File objectFile = new File(this.objectStoreDirectory, objectId.toString());
//        try {
//            fin = new FileInputStream(objectFile);
//            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fin, 8*1024));
//            C result = klasse.cast(ois.readObject());
//            return result;
//        } catch (FileNotFoundException e) {
//            throw e;
//        } catch (IOException io) {
//            System.err.printf("%s: %s size=%d removed.%n", io.toString(), objectFile, objectFile.length());
//            io.printStackTrace();
//            this.remove(objectId);
//            throw io;
//        } finally {
//            try { if (fin != null) fin.close(); } catch (IOException ignore) { /* XXX should log this. */ }
//        }
//    }
    
    public TitanObject get(TitanGuid objectId)
    throws IOException, ClassCastException, ClassNotFoundException, FileNotFoundException {
        FileInputStream fin = null;
        File objectFile = new File(this.objectStoreDirectory, objectId.toString());
        try {
            fin = new FileInputStream(objectFile);
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fin, 8*1024));
            TitanObject result = (TitanObject) (ois.readObject());
            return result;
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException io) {
            System.err.printf("%s: %s size=%d removed.%n", io.toString(), objectFile, objectFile.length());
            io.printStackTrace();
            this.remove(objectId);
            throw io;
        } finally {
            try { if (fin != null) fin.close(); } catch (IOException ignore) { /* XXX should log this. */ }
        }
    }

    /**
     * Commit the specified {@link TitanObject} to the backing-store.
     */
    public void put(TitanGuid key, TitanObject object) throws BeehiveObjectStore.NoSpaceException, IOException {
        TitanGuid objectId = object.getObjectId();

        long originalObjectLength = this.sizeOf(objectId);
        long objectLength = 0;

        FileOutputStream fout = null;
        File objectFile = new File(this.objectStoreDirectory, objectId.toString());
        object.setProperty(BeehiveObjectStore.METADATA_CREATEDTIME, Time.currentTimeInSeconds());

        try {
            fout = new FileOutputStream(objectFile);
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(fout, 8*1024));
            oos.writeObject(object);
            oos.flush();
            oos.close();
        } catch (IOException failedWrite) {
            failedWrite.printStackTrace();
            this.remove(object.getObjectId());
            throw failedWrite;
        } finally {
            if (fout != null) try { fout.close(); } catch (IOException ignore) { }
        }

        this.currentSpoolSize += (objectLength - originalObjectLength);

        // Ensure that there is space to store the data and the metaData.
        // It's a shame this is done this way.  It would be better to check
        // this *before* the object has been stored.  But that requires knowing
        // the length of the object ahead of time, which is an expense to calculate.
        if (this.capacityLimit != CAPACITY_UNLIMITED) {
            if (this.currentSpoolSize > this.capacityLimit) {
                this.remove(objectId);
                throw new BeehiveObjectStore.NoSpaceException("Object store full.");
            }
        }
    }

    /**
     * Remove an object from the backing store.
     * <p>
     * @param objectId
     * @return TRUE if the object existed, FALSE if not.
     */
    public boolean remove(TitanGuid objectId) {
        File localFile = new File(this.objectStoreDirectory, objectId.toString());
        if (localFile.exists()) {
            this.currentSpoolSize -= localFile.length();
            localFile.delete();
            return true;
        }
        return false;
    }

    /**
     * Return the number of bytes consumed storing the named object.
     */
    public long sizeOf(TitanGuid objectId) {
        File localFile = new File(this.objectStoreDirectory, objectId.toString());
        if (localFile.exists()) {
            return localFile.length();
        }
        return 0L;
    }

    private static class StoredObjectFilter implements FilenameFilter {
        // XXX Only include file names that are really objects.
        public boolean accept(File dir, String name) {
            return TitanGuidImpl.IsValid(name);
        }
    }

    private final static class StaticDataIterator implements Iterator<TitanGuid> {
        private final String[] list;
        private int index;
        private TitanGuid nextObjectId;

        public StaticDataIterator(FileObjectStore factory) {
            super();
            this.list = factory.objectStoreDirectory.list(new FileObjectStore.StoredObjectFilter());
            if (this.list != null) {
                this.nextObjectId = this.list.length > 0 ? new TitanGuidImpl(this.list[0]) : null;
                this.index = 1;
            } else {
                this.nextObjectId = null;
            }
        }

        public TitanGuid next() {
            TitanGuid id = this.nextObjectId;
            this.nextObjectId = (this.index >= this.list.length) ? null : new TitanGuidImpl(this.list[this.index++]);
            return id;
        }

        public boolean hasNext() {
            return this.nextObjectId != null;
        }

        public void remove() {
            return ;
        }
    }

    /**
     * Return an Iterator over all the objects maintained.
     */
    public Iterator<TitanGuid> iterator() {
        return new StaticDataIterator(FileObjectStore.this);
    }
}
