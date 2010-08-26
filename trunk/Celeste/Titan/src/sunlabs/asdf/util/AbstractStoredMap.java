/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.asdf.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Stack;

/*
 * This is like a Map Key->File
 */
abstract public class AbstractStoredMap<K,V extends Serializable> implements Iterable<K> {
    public final static long CAPACITY_UNLIMITED = Long.MAX_VALUE;
    
    public static class OutOfSpace extends Exception {
        private static final long serialVersionUID = 1L;

        public OutOfSpace() {
            super();
        }

        public OutOfSpace(String format, Object...args) {
            super(String.format(format, args));
        }

        public OutOfSpace(String message) {
            super(message);
        }

        public OutOfSpace(Throwable reason) {
            super(reason);
        }
    }
    
    public long getCacheHit;
    private Map<K,byte[]> getCache;
    private int cacheSize = 5;
    
    private int ioBufferSize = 8*1024;
	private File root;
    private long maxCapacity;
    private Long currentSpoolSize;
    private ObjectLock<File> locks;

    public AbstractStoredMap(File root, long capacity) throws IOException, NumberFormatException {
    	this.root = root;
    	if (!this.root.exists()) {
    		if (!this.root.mkdirs()) {
    			throw new IOException("Cannot create " + root);
    		}
    	}

    	this.maxCapacity = capacity;
    	this.currentSpoolSize = -1L;

    	this.locks = new ObjectLock<File>();

//    	this.setCacheSize(this.cacheSize);

    	this.currentSpoolSize = this.computeCurrentSpoolSize();
    }
    
    public void setCacheSize(int size) {
        this.cacheSize = size;
        this.getCache = Collections.synchronizedMap(new LinkedHashMap<K,byte[]>(this.cacheSize + 1, .75F, true) {
            private static final long serialVersionUID = 1L;

            // This method is called just after a new entry has been added
            public boolean removeEldestEntry(Map.Entry<K,byte[]> eldest) {
                return size() > AbstractStoredMap.this.cacheSize;
            }
        });
        this.getCacheHit = 0;
    }
    
    public void setIOBufferSize(int size) {
        this.ioBufferSize = size;
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

        for (K key : this) {
            size += this.sizeOf(key);
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
    	return this.maxCapacity;
    }

    /**
     * Get the number of bytes that are available for this file store to consume.
     * @return
     */
    public long getSpoolAvailable() {
    	return this.maxCapacity - this.currentSpoolSize;
    }
	
    abstract public File keyToFile(File root, K key);
	
	abstract public K fileToKey(File file);

	/**
	 * Lock the given {@link File} including the parent directories up to, but not including the "root" of the store.
	 *
	 * @param file the {@code File} to lock.
	 * @throws IllegalStateException if the current Thread attempts to lock and item that it has already locked.
	 */
	private void lockFile(File file) throws IllegalStateException {
		if (file == null || file.equals(this.root))
			return;

		lockFile(file.getParentFile());
		this.locks.lock(file);
	}
	
	/**
	 * Unlock the {@link File} identified by {@code file}.
	 * 
	 * @param file
	 * @throws IllegalStateException if the current Thread did not have the lock.
	 */
	private void unlockFile(File file) throws IllegalStateException {
		if (file == null || file.equals(this.root))
			return;
		this.locks.unlock(file);
		unlockFile(file.getParentFile());
	}
	
	/**
	 * Put the given {@code object} in the store with the given @{link key}.
	 * <p>
	 * If the store specifies a maximum size and there is no room to put the given object, any existing object already in the store with the same key is not changed.
	 * The invoker must explicitly remove the old object from the store.
	 * </p>
	 * @param key key with which the specified value is to be associated
	 * @param object the value to be associated with the specified key
	 * @throws IOException
	 * @throws IllegalStateException
	 * @throws OutOfSpace If there is no room in the store for the object.
	 */
	public void putCache(K key, V object) throws IOException, IllegalStateException, OutOfSpace {
	    File file = this.keyToFile(this.root, key);

	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bout);
	    oos.writeObject(object);
	    oos.close();
	    byte[] serializedObject = bout.toByteArray();

	    this.lockFile(file);
	    try {
	        // Check to see if we have room for this object.
	        long originalObjectLength = file.length();
	        synchronized (this.currentSpoolSize) {
	            long newSize = this.currentSpoolSize + (serializedObject.length - originalObjectLength);
	            if (newSize > this.maxCapacity) {
	                // If the object store is full, and this is trying to update an object already stored, remove it.
	                throw new AbstractStoredMap.OutOfSpace("Object Store full");
	            }
	            this.currentSpoolSize = newSize;
	        }

	        if (this.getCache != null) {
	            // System.out.printf("%s put cached %s%n", Thread.currentThread(), key);
	            this.getCache.put(key, serializedObject);
	        }

	        file.getParentFile().mkdirs();

	        OutputStream out = null;
	        try {
	            out = new FileOutputStream(file);
	            out.write(serializedObject);
	        } catch (IOException failedWrite) {
	            this.remove(key);
	            throw failedWrite;
	        } finally {
	            if (out != null) try { out.close(); } catch (IOException ignore) { ignore.printStackTrace(); }
	        }
	    } finally {
	        this.unlockFile(file);
	    }
	}

	public void put(K key, V object) throws IOException, IllegalStateException, OutOfSpace {
	    File file = this.keyToFile(this.root, key);

	    this.lockFile(file);
	    try {
	        file.getParentFile().mkdirs();
	        long originalObjectLength = file.length();

	        ObjectOutputStream oos = null;
	        try {
	            oos = new ObjectOutputStream(new FileOutputStream(file));
	            oos.writeObject(object);
	            oos.close();
	        } catch (IOException failedWrite) {
	            this.removeFile(file);
	            throw failedWrite;
	        } finally {
	            if (oos != null) try { oos.close(); } catch (IOException ignore) { ignore.printStackTrace(); }
	        }

	        // Check to see if we have room for this object.
	        synchronized (this.currentSpoolSize) {
	            long newSize = this.currentSpoolSize + (file.length() - originalObjectLength);
	            if (newSize > this.maxCapacity) {
	                // If the object store is full, and this is trying to update an object already stored, remove it.
	                this.removeFile(file);
	                throw new AbstractStoredMap.OutOfSpace("Object Store full");
	            }
	            this.currentSpoolSize = newSize;
	        }
	    } finally {
	        this.unlockFile(file);
	    }
	}
	
	private <C extends Serializable> C deserialize(final Class<? extends C> klasse, final byte[] bytes) {
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
			return klasse.cast(ois.readObject());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		} finally {
		    try { ois.close(); } catch (IOException e) { e.printStackTrace(); }
		}
	}
	
	@SuppressWarnings("unchecked")
	public V get(K key) throws IOException, ClassCastException, ClassNotFoundException, FileNotFoundException {
	    File file = keyToFile(this.root, key);

	    ObjectInputStream ois = null; 

	    try { this.lockFile(file); } catch (IllegalStateException e) { }
	    try {
	        V result = null;
	        if (this.getCache != null) {
	            if (this.getCache.containsKey(key)) {
	                this.getCacheHit++;
	                //					System.out.printf("%s get cached %s%n", Thread.currentThread(), key);
	                result = (V) this.deserialize(Serializable.class, this.getCache.get(key));
	                return result;
	            } else {
	                //					System.out.printf("%s get %s%n", Thread.currentThread(), key);					
	            }
	        }
	        try {
	            ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file), this.ioBufferSize));
	            return (V) ois.readObject();
	        } catch (IOException io) {
	            if (this.getCache != null) {
	                this.getCache.remove(key);
	            }
	            this.removeFile(file);
	            throw io;
	        } finally {
	            try { if (ois != null) ois.close(); } catch (Exception ignore) { ignore.printStackTrace(); }
	        }
	    } finally {
	        try { this.unlockFile(file); } catch (IllegalStateException e) { e.printStackTrace(); }
	    }
	}

	public boolean contains(K key) {
	    File file = keyToFile(this.root, key);

		try {
			this.lockFile(file);
			try {
				return file.exists();
			} finally {
				this.unlockFile(file);
			}
		} catch (IllegalStateException e) {
			this.unlockFile(file);
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Remove the value identified by {@code key} from this map.
	 * 
	 * @param key
	 * @return true if the map contained the value identified by {@code key}.
	 */
	public boolean remove(K key) {
		boolean result = false;
		
		File file = keyToFile(this.root, key);

		this.lockFile(file);
		try {
		    if (this.getCache != null) {
		        this.getCache.remove(key);
		    }
		    this.removeFile(file);
		} finally {
		    this.unlockFile(file);
		}

		return result;
	}
	
	private boolean removeFile(File file) {
		boolean result = false;

		if (file.exists()) {
			long originalObjectLength = file.length();
			file.delete();
			this.currentSpoolSize -= originalObjectLength;
			result = true;
		}
		File parent = file.getParentFile();
		while (!parent.equals(this.root)) {
			String[] list = parent.list();
			parent.delete();
			if (list != null && list.length > 0)
				break;
			parent = parent.getParentFile();
		}

		return result;
	}
	
	public long sizeOf(K key) {
		File file = keyToFile(this.root, key);
        this.lockFile(file);
        try {
            return file.length();
        } finally {
            this.unlockFile(file);
        }
	}
		
	private final class RegularFileIterator implements Iterator<K> {

		private class Directory implements Iterator<String> {
			private File path;
			private String[] files;
			private int index;

			public Directory(File path) {
				this.path = path;
				this.files = path.list();
				this.index = 0;
			}

			public String next() {
				if (this.index < this.files.length) {
					return this.path.getAbsolutePath() + File.separator + this.files[this.index++];
				}
				throw new NoSuchElementException();
			}

			public boolean hasNext() {
				if (this.index < this.files.length)
					return true;
				return false;
			}

			public String toString() {
				return String.format("{path=%s %d [%d]='%s'}", this.path, this.files.length, this.index, this.index < this.files.length ? this.files[this.index] : "");
			}

			public void remove() {	
			}
		}
		
		private Stack<Directory> directories;
		private K next;

		public RegularFileIterator(File root) {
			super();
			this.directories = new Stack<Directory>();
			this.directories.push(new Directory(root));
			this.next = null;
		}
		
		public K next() {
			if (this.hasNext()) {
				K result = this.next;
				this.next = null;
				return result;
			}
			throw new NoSuchElementException();
		}
		
		public boolean hasNext() {
			if (this.next != null) {
				return true;
			}

			while (!this.directories.isEmpty()) {
				Directory directory = this.directories.peek();
				if (!directory.hasNext()) {
					this.directories.pop();
				} else {
					File file = new File(directory.next());
					if (file.isDirectory()) {
						this.directories.push(new Directory(file));
					} else if (file.isFile()) {
						this.next = AbstractStoredMap.this.fileToKey(file);
						return true;
					}
				}
			}
			return false;
		}

		public void remove() {
			return ;
		}
		
		public String toString() {
			StringBuilder result = new StringBuilder();
			for (Directory s : this.directories) {
				result.append(s.toString()).append(" ");
			}
			
			return result.toString();
		}
	}

	/**
	 * Return an Iterator over all the objects in this map.
	 */
	public Iterator<K> iterator() {
		return new RegularFileIterator(this.root);
	}
	
	private static class BackedMap extends AbstractStoredMap<Long,Long> {

		public BackedMap(File root, String capacity) throws IOException, NumberFormatException {
			super(root, Units.parseByte(capacity));
		}

		@Override
		public File keyToFile(File root, Long key) {
		    String s = key.toString();
		    StringBuilder result = new StringBuilder();
		    result.append(s.substring(0, 5)).append(File.separatorChar).append(s);
		    return new File(root, result.toString());
		}

        @Override
		public Long fileToKey(File file) {
			return new Long(file.getName());
		}
    }
    
    public static void main(String[] args) throws Exception {
    	System.setProperty("enableProfiling", "true");

    	TimeProfiler profiler = new TimeProfiler();
    	BackedMap os = new BackedMap(new File("/var/tmp/test/"), "10M");
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler.toString());

    	Long id;

    	id = new Long("1");
    	os.put(id, id);
    	id = new Long("2");
    	os.put(id, id);
    	os.remove(id);

    	Random r = new Random();

    	id = r.nextLong();
    	profiler = new TimeProfiler();
    	for (int i = 0; i < 10; i++) {
    		for (int j = 0; j < 10; j++) {
    			os.put(id, id);
    			id =  r.nextLong();
    		}
    		profiler.stamp("%d", i);
    	}
    	System.out.printf("%s%n", profiler.toString());

    	int count = 0;
    	profiler = new TimeProfiler();
    	for (Long key : os) {
    		id = os.get(key);
    		count++;
    		//			System.out.printf("%s %s %s%n", file.getName(), file.getAbsolutePath(), id);
    	}
    	profiler.stamp("fini %d", count);
    	System.out.printf("%s%n", profiler.toString());


    	profiler = new TimeProfiler();
    	for (Long key : os) {
    		os.remove(key);
    	}
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler.toString());
    }
}
