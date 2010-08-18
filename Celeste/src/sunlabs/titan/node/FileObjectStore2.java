package sunlabs.titan.node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
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
import java.util.Stack;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.TimeProfiler;
import sunlabs.asdf.util.Units;
import sunlabs.titan.BeehiveObjectId;

//
// This is like a Map BeehiveObjectId->File
//
public class FileObjectStore2<K/* extends FileObjectStore2.Key*/,V extends Serializable> implements Iterable<K> {    
	public interface Key {
		public String getBackedMapKey();
	}
	    
    private final static long CAPACITY_UNLIMITED = -1;
    
    public long getCacheHit;
    private Map<K,byte[]> getCache;
    private int cacheSize = 10;
    
    private int ioBufferSize = 8*1024;
	private File root;
    private long capacityLimit;
    private Long currentSpoolSize;
    private ObjectLock<File> locks;

	public FileObjectStore2(File root, String capacity) throws IOException, NumberFormatException {
		this.root = root;
		if (!this.root.exists()) {
			if (!this.root.mkdirs()) {
				throw new IOException("Cannot create " + root);
			}
		}
		
	    if (capacity.compareTo("unlimited") == 0) {
            this.capacityLimit = CAPACITY_UNLIMITED;
        } else {
            this.capacityLimit = Units.parseByte(capacity);
            if (this.capacityLimit == -1)
                this.capacityLimit = CAPACITY_UNLIMITED;
        }
	    
	    this.locks = new ObjectLock<File>();

	    this.getCache = Collections.synchronizedMap(new LinkedHashMap<K,byte[]>(this.cacheSize + 1, .75F, true) {
	    	private static final long serialVersionUID = 1L;

	    	// This method is called just after a new entry has been added
	    	public boolean removeEldestEntry(Map.Entry eldest) {
	    		return size() > FileObjectStore2.this.cacheSize;
	    	}
	    });
		this.getCacheHit = 0;

        this.currentSpoolSize = this.computeCurrentSpoolSize();
        System.out.printf("current size %d%n", this.currentSpoolSize);
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
    	return this.capacityLimit;
    }

    /**
     * Get the number of bytes that are available for this file store to consume.
     */
    public long getSpoolAvailable() {
    	return this.capacityLimit - this.currentSpoolSize;
    }
	
    private String keyToName(K key) {
        String s = key.toString();
        StringBuilder result = new StringBuilder();
        result.append(s.substring(0, 5)).append(File.separatorChar).append(s);
        return result.toString();
    }
	
	@SuppressWarnings("unchecked")
	public K nameToKey(String path) {
		return (K) new BeehiveObjectId(path);		
	}

	/**
	 * If IllegalStateException is thrown unlock the whole thing and try again.
	 * @param file
	 * @throws IllegalStateException
	 */
	private void lockFile(File file) throws IllegalStateException {
		if (file == null || file.equals(this.root))
			return;

		lockFile(file.getParentFile());
		this.locks.lock(file);
	}
	
	private void unlockFile(File file) {
		if (file == null || file.equals(this.root))
			return;
		this.locks.unlock(file);
		unlockFile(file.getParentFile());
	}
	
	@SuppressWarnings("unchecked")
	public void put(K key, V object) throws IOException, UnsupportedOperationException, ClassCastException, NullPointerException, IllegalArgumentException {
		String pathName = keyToName(key);
		File file = new File(this.root, pathName);

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bout);
		oos.writeObject(object);
		oos.close();
		byte[] serializedObject = bout.toByteArray();
		
		try {
			this.lockFile(file);
			try {
//				System.out.printf("%s put cached %s%n", Thread.currentThread(), key);
				this.getCache.put(key, serializedObject);
				// I think this object gets modified by AObjectVersionService after it is stored, thereby modifying the copy in the cache.
				
				file.getParentFile().mkdirs();

				long originalObjectLength = file.length();

				if (false) {
					try {
						oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file), this.ioBufferSize));
						oos.writeObject(object);
					} catch (IOException failedWrite) {
						failedWrite.printStackTrace();
						this.remove(key);
						throw failedWrite;
					} finally {
						if (oos != null) try { oos.close(); } catch (IOException ignore) { }
					}
				} else {
					OutputStream out = null;
					try {
						out = new FileOutputStream(file);
						out.write(serializedObject);
					} catch (IOException failedWrite) {
						failedWrite.printStackTrace();
						this.remove(key);
						throw failedWrite;
					} finally {
						if (out != null) try { out.close(); } catch (IOException ignore) { ignore.printStackTrace(); }
					}
				}

				long currentObjectLength = file.length();

				synchronized (this.currentSpoolSize) {
					this.currentSpoolSize += (currentObjectLength - originalObjectLength);
					if (this.capacityLimit != FileObjectStore2.CAPACITY_UNLIMITED && this.currentSpoolSize > this.capacityLimit) {
						this.remove(file);
						throw new IOException("Object Store full");
					}
				}
			} finally {
				this.unlockFile(file);
			}
		} catch (IllegalStateException e) {
			this.unlockFile(file);
			throw new IllegalArgumentException(e);
		}
	}

//	public <C> C get(Class<? extends C> klasse, K key) throws IOException, ClassCastException, ClassNotFoundException, FileNotFoundException {
//		String pathName = keyToName(key);
//		File file = new File(this.root, pathName);
//
//		ObjectInputStream ois = null; 
//		try {
//			this.lockFile(file);
//			try {
//				ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file), 8*1024));
//				C result = klasse.cast(ois.readObject());
//				return result;
//			} catch (IOException io) {
//				System.err.printf("%s: %s size=%d removing.%n", io.toString(), file, file.length());
//				io.printStackTrace();
//				this.remove(key);
//				throw io;
//			} finally {
//				try { if (ois != null) ois.close(); } catch (Exception ignore) { ignore.printStackTrace(); }
//				this.unlockFile(file);
//			}
//		} catch (IllegalStateException e) {
//			this.unlockFile(file);
//			throw new IllegalArgumentException(e);
//		}
//	}
	
	private byte[] serialize(Serializable o) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(o);
			oos.close();
			return bos.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private <C extends Serializable> C deserialize(final Class<? extends C> klasse, final byte[] bytes) {
		ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
		try {
			ObjectInputStream ois = new ObjectInputStream(bin);
			return klasse.cast(ois.readObject());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private <C extends Serializable> C deepCopy(final Class<? extends C> klasse, final Serializable object) {
		return klasse.cast(this.deserialize(Serializable.class, this.serialize(object)));		
	}
	
	@SuppressWarnings("unchecked")
	public V get(K key) throws IOException, ClassCastException, ClassNotFoundException, FileNotFoundException {
		String pathName = keyToName(key);
		File file = new File(this.root, pathName);
		
		ObjectInputStream ois = null; 
		try {
			this.lockFile(file);
			
			try {
				V result = null;
				if (this.getCache.containsKey(key)) {
					this.getCacheHit++;
//					System.out.printf("%s get cached %s%n", Thread.currentThread(), key);
					result = (V) this.deserialize(Serializable.class, this.getCache.get(key));
					return result;
				} else {
//					System.out.printf("%s get %s%n", Thread.currentThread(), key);					
				}
				
				ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file), this.ioBufferSize));
				return (V) ois.readObject();
			} catch (IOException io) {
				this.getCache.remove(key);
				this.remove(file);
				throw io;
			} finally {
				try { if (ois != null) ois.close(); } catch (Exception ignore) { ignore.printStackTrace(); }
				this.unlockFile(file);
			}
		} catch (IllegalStateException e) {
			this.unlockFile(file);
			throw new IllegalArgumentException(e);
		}
	}

	public boolean contains(K key) {
		String pathName = keyToName(key);
		File file = new File(this.root, pathName);
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
		
		String pathName = keyToName(key);
		File file = new File(this.root, pathName);
		this.lockFile(file);
		try {
			this.getCache.remove(key);
			this.remove(file);
		} finally {
			this.unlockFile(file);
		}

		return result;
	}
	
	private boolean remove(File file) {
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
		String pathName = keyToName(key);
		return new File(this.root, pathName).length();
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
						this.next = FileObjectStore2.this.nameToKey(file.getName());
						//this.next = new K(file.getName());
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
	 * Return an Iterator over all the objects maintained.
	 */
	public Iterator<K> iterator() {
		return new RegularFileIterator(this.root);
	}

    private static class BeehiveObjectIdFilter implements FilenameFilter {
        // XXX Only include file names that are really objects.
        public boolean accept(File dir, String name) {
            return BeehiveObjectId.IsValid(name);
        }
    }
    
    public static void main(String[] args) throws Exception {
    	System.setProperty("enableProfiling", "true");

    	TimeProfiler profiler = new TimeProfiler();
    	FileObjectStore2<BeehiveObjectId,BeehiveObjectId> os = new FileObjectStore2<BeehiveObjectId,BeehiveObjectId>(new File("/var/tmp/test/"), "10M");
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler.toString());

    	BeehiveObjectId id;

    	id = new BeehiveObjectId("1111111111111111111111111111111111111111111111111111111111111111");
    	os.put(id, id);
    	id = new BeehiveObjectId("1111111111111111111111111111111111111111111111111111111111111112");
    	os.put(id, id);
    	os.remove(id);


    	id = new BeehiveObjectId();
    	profiler = new TimeProfiler();
    	for (int i = 0; i < 10; i++) {
    		for (int j = 0; j < 10; j++) {
    			os.put(id, id);
    			id = new BeehiveObjectId();
    		}
    		profiler.stamp("%d", i);
    	}
    	System.out.printf("%s%n", profiler.toString());

    	int count = 0;
    	profiler = new TimeProfiler();
    	for (BeehiveObjectId key : os) {
    		id = os.get(key);
    		count++;
    		//			System.out.printf("%s %s %s%n", file.getName(), file.getAbsolutePath(), id);
    	}
    	profiler.stamp("fini %d", count);
    	System.out.printf("%s%n", profiler.toString());


    	profiler = new TimeProfiler();
    	for (BeehiveObjectId key : os) {
    		os.remove(key);
    	}
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler.toString());
    }
}
