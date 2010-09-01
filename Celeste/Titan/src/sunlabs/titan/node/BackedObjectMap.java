/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import sunlabs.titan.TitanGuidImpl;

/**
 * An implementation of a map of objects that is maintained in the
 * local filesystem and not in memory.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class BackedObjectMap<K,V> implements Serializable {

    private final static long serialVersionUID = 1L;

    private String directory;

    /**
     * Given a Map key, construct a local file-system pathname for that key.
     *
     * @param key   the key for which a pathname is desired
     *
     * @return  the pathname corresponding to {@code key}
     */
    public String makePath(Object key) {
        String keyString = key.toString();
        assert keyString.length() > 9;
        String p1 = keyString.substring(0,4);
        String p2 = keyString.substring(4,8);
        String p3 = keyString.substring(8);

        StringBuilder result = new StringBuilder(this.directory);
        result.append(File.separatorChar);
        result.append(p1);
        result.append('+');
        result.append(File.separatorChar);
        result.append(p2);
        result.append('+');
        result.append(File.separatorChar);
        result.append(p3);
        return result.toString();
    }

    /**
     * Given a file-system pathname, return it as a key to this map.
     * @param path
     */
    protected K makeKey(String path) {
    	path = path.substring(this.directory.length() + 1);

    	if (path.charAt(4) != '+' ||
    			path.charAt(5) != File.separatorChar ||
    			path.charAt(10) != '+' ||
    			path.charAt(11) != File.separatorChar) {

    		throw new IllegalArgumentException("not a valid key");
    	}
    	StringBuilder key = new StringBuilder();
    	key.append(path.substring(0,4));
    	key.append(path.substring(6,10));
    	key.append(path.substring(12));
    	@SuppressWarnings("unchecked")
    	K keyId = (K) new TitanGuidImpl(key.toString());
    	return keyId;
    }

    /**
     * Get all of the keys in this Map and return them in a Set.
     *
     * @param dir
     */
    private Set<K> getAll(String dir) {
        Set<K> list = new HashSet<K>();

        // XXX Should lock this.
        File d = new File(dir);
        if (d.exists()) {
            for (File file : d.listFiles()) {
                if (file.isFile()) {
                    list.add(this.makeKey(file.getAbsolutePath()));
                } else if (file.isDirectory()) {
                    list.addAll(this.getAll(file.getAbsolutePath()));
                }
            }
        }

        return list;
    }

    public static class AccessException extends IOException {
        private static final long serialVersionUID = 1L;

        public AccessException(String message) {
            super(message);
        }
    }

    public BackedObjectMap(String directory, boolean initialise) throws AccessException {

//        this.locks = new ObjectLock<String>();

        File path = new File(directory);
        path.mkdirs();
        if (!path.exists() || !path.canWrite()) {
            throw new AccessException("Cannot create/write " + directory);
        }
        this.directory = path.getAbsolutePath();
        if (initialise) {
            this.delete(this.directory);
        }
    }

    public void destroy() {
        this.delete(this.directory);
    }

    private void delete(String name) {
        File file = new File(name);

        if (file.isDirectory()) {
            for (File f: file.listFiles()) {
                if (f.isDirectory()) {
                    this.delete(f.getAbsolutePath());
                } else {
                    f.delete();
                }
            }
        }
        file.delete();
    }

    /* (non-Javadoc)
     *
     */
//    @Override
    public void clear() {
        for (K key: this.keySet()) {
            this.remove(key);
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(TitanGuidImpl key) {
        return new File(this.makePath(key)).exists();
    }

//    /* (non-Javadoc)
//     * @see java.util.Map#containsValue(java.lang.Object)
//     */
//    public boolean containsValue(Object arg0) {
//        for (K key: this.keySet()) {
//            FileInputStream in = null;
//            try {
//                in = new FileInputStream(this.makePath(key));
//                ObjectInputStream os = new ObjectInputStream(in);
//                Object o = os.readObject();
//                if (o.equals(arg0))
//                    return true;
//            } catch (FileNotFoundException fileNotFound) {
//                /* */
//            } catch (IOException IO) {
//                /*
//                 * If this failed, it's likely because of some error
//                 * that is likely to be persistent.  Should the entry
//                 * be removed?
//                 */
//            } catch (ClassNotFoundException classNotFound) {
//                /*
//                 * If this failed, it's likely because the file is not
//                 * a java serialized object.
//                 */
//            } finally {
//                if (in != null) {
//                    try {
//                        in.close();
//                    } catch (IOException ignore) {
//                        /**/
//                    }
//                }
//            }
//        }
//        return false;
//    }

    private class Entry implements Map.Entry<K,V> {
        private K key;

        public Entry(K key) {
            this.key = key;
        }

        public K getKey() {
            return this.key;
        }

        public V getValue() {
            return BackedObjectMap.this.get(this.key);
        }

        public V setValue(V v) {
            V oldValue = this.getValue();
            BackedObjectMap.this.put(this.key, v);
            return oldValue;
        }
    }

    /**
     *
     * XXX This must be improved to NOT create an in-memory Set. Instead a
     * "BackedSet" should be used which does not maintain the set in
     * memory.
     */
    private class BackedSet extends HashSet<Map.Entry<K,V>> {
        private static final long serialVersionUID = 0;

        public BackedSet() {
            super();
        }

        /* (non-Javadoc)
         * @see java.util.HashSet#remove(java.lang.Object)
         */
        @Override
        public boolean remove(Object arg0) {
            BackedObjectMap.this.remove(arg0);
            return super.remove(arg0);
        }

        /* (non-Javadoc)
         * @see java.util.AbstractSet#removeAll(java.util.Collection)
         */
        @Override
        public boolean removeAll(Collection<?> c) {
            for (Object obj: c) {
                if (obj instanceof Map.Entry<?,?>) {
                    @SuppressWarnings("unchecked")
                    Map.Entry<K,V> entry = (Map.Entry<K,V>) obj;
                    BackedObjectMap.this.remove(entry.getKey());
                }
            }

            return super.removeAll(c);
        }

        /* (non-Javadoc)
         * @see java.util.AbstractCollection#retainAll(java.util.Collection)
         */
        @Override
        public boolean retainAll(Collection<?> c) {
            Set<K> keySet = BackedObjectMap.this.keySet();
            for (Object obj: c) {
                if (obj instanceof Map.Entry) {
                    @SuppressWarnings("unchecked")
                    Map.Entry<K,V> entry = (Map.Entry<K, V>) obj;
                    if (!keySet.contains(entry.getKey())) {
                        BackedObjectMap.this.remove(entry.getKey());
                    }
                }
            }
            return super.retainAll(c);
        }

        @Override
        public void clear() {
            BackedObjectMap.this.clear();
        }
    }

    /**
     */
    public Set<Map.Entry<K,V>> entrySet() {

        // XXX Lock this
        Set<Map.Entry<K,V>> set = new BackedSet();

        for (K key: this.keySet()) {
            set.add(new Entry(key));
        }
        return set;
    }

    /**
     *
     * A ClassCastException or a IOException reading the stored object will result
     * in the stored object being removed and a {@code null} returned.
     *
     * @throws ClassCastException if the stored object is not of type {@code V}.
     * @see java.util.Map#get(java.lang.Object)
     */
    public V get(Object arg0) throws ClassCastException, SecurityException {

        assert arg0 instanceof TitanGuidImpl;
        if (!(arg0 instanceof TitanGuidImpl)) {
            return null;
        }
        TitanGuidImpl key = (TitanGuidImpl) arg0;
        String k = this.makePath(key);
//        try {
            File file = new File(k);

            if (!file.exists())
                return null;

            if (file.isFile()) {
                FileInputStream in = null;
                Object o = null;

                try {
                    in = new FileInputStream(file);
                    o = new ObjectInputStream(in).readObject();
                    @SuppressWarnings(value="unchecked")
                    V tmp = (V) o;
                    return tmp;
                } catch (FileNotFoundException fileNotFound) {
                    return null;
                } catch (IOException IO) {
                    System.err.printf("BackedObjectMap.get: %s %s%n", IO.toString(), key);
                    IO.printStackTrace();
                    file.delete();
                    return null;
                } catch (ClassNotFoundException classNotFound) {
                    System.err.printf("BackedObjectMap.get: %s %s%n", classNotFound.toString(), key);
                    file.delete();
                    return null;
                } catch (ClassCastException e) {
                    System.err.printf("BackedObjectMap.get ClassCastException: %s got %s%n", file.toString(), (o != null ? o.getClass().toString() : "null"));
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ignore) {
                            /**/
                        }
                    }
                }
            }
//        } catch (NullPointerException e) {
//            /* */
//        }

            System.err.printf("BackedObjectMap.get: not a file: %s %s%n", key, k);
        return null;
   }

    /* (non-Javadoc)
     * @see java.util.Map#keySet()
     */
    public Set<K> keySet() {
        return this.getAll(this.directory);
    }

    /**
     * Associates the specified {@code value} with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced by the specified value.
     *
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     * @throws IllegalArgumentException if the backing store file cannot be created, or an {@link IOException} is caught.
     */
    public void put(K key, V value) throws IllegalArgumentException {
        String pathName = this.makePath(key);
        File file = new File(pathName);
        ObjectOutputStream os =  null;
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();

            os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            os.writeObject(value);
            os.flush();
        } catch (FileNotFoundException fileNotFound) {
            throw new IllegalArgumentException(fileNotFound);
        } catch (IOException io) {
            file.delete();
            throw new IllegalArgumentException(io);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("Please report this event to the developers%n");
            throw new RuntimeException(e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignore) {
                    /**/
                }
                if (!new File(pathName).exists()) {
                    System.err.printf("BackedObjectMap.put: %s created but now doesn't exist%n", pathName);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#putAll(java.util.Map)
     */
    public void putAll(Map<? extends K, ? extends V> arg0) {
        for (K key: arg0.keySet()) {
            this.put(key, arg0.get(key));
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#remove(java.lang.Object)
     */
    public V remove(Object arg0) {
        assert arg0 instanceof TitanGuidImpl;
        if (!(arg0 instanceof TitanGuidImpl)) {
            return null;
        }
        TitanGuidImpl key = (TitanGuidImpl) arg0;
        V previousValue = this.get(key);
        if (previousValue != null) {
            File file = new File(this.makePath(key));
            if (!file.delete()) {
                throw new IllegalArgumentException("failed to delete " + file);
            }
            File parent = file.getParentFile();
            while (parent != null && !parent.toString().equals(this.directory)) {
                if (!parent.delete()) {
                    // Assume parent is not empty.
                    break;
                }
                parent = parent.getParentFile();
            }
        }
        return previousValue;
    }

    /* (non-Javadoc)
     * @see java.util.Map#size()
     */
    public int size() {
        return this.keySet().size();
    }

    /* (non-Javadoc)
     * @see java.util.Map#values()
     */
    public Collection<V> values() {
        Collection<V> collection = new HashSet<V>();
        for (K key: this.keySet()) {
            collection.add(this.get(key));
        }
        return collection;
    }

    private static TitanGuidImpl key(String s) {
        return new TitanGuidImpl(s.getBytes());
    }

    public static void main(String[] args) {
        try {
            BackedObjectMap<TitanGuidImpl,String> map = new BackedObjectMap<TitanGuidImpl,String>("/tmp/map", true);

            map.put(key("1234/"), "A");
            map.put(key("12345+"), "A");
            map.put(key("123456"), "1");
            map.put(key("12"), "2");
            map.put(key("22345"), "3");
            map.put(key("323456"), "4");

            System.out.println("created " + map.size() + " entries.");

            for (Map.Entry<TitanGuidImpl, String> entry : map.entrySet()) {
                System.out.println(
                    " " + entry.getKey() + "=" + entry.getValue());
            }

            System.out.println("Test entrySet");

            Set<Map.Entry<TitanGuidImpl,String>> set = map.entrySet();
            for (Map.Entry<TitanGuidImpl,String> entry: set) {
                System.out.println(
                    " key: " + entry.getKey() + "=" + entry.getValue());
                entry.setValue(entry.getKey().toString());
            }

            System.out.println("Test keySet");
            for (Map.Entry<TitanGuidImpl, String> entry : map.entrySet()) {
                System.out.println(
                    "key: " + entry.getKey() + "=" + entry.getValue());
            }

            System.out.println("remove 12");
            set.remove(key("12"));
            for (Map.Entry<TitanGuidImpl, String> entry : map.entrySet()) {
                System.out.println(
                    "key: " + entry.getKey() + "=" + entry.getValue());
            }
            map.remove(key("323456"));

            System.out.println("323456=" + map.get(key("323456")));

            //map.clear();

            for (TitanGuidImpl file: map.keySet()) {
                System.out.println("key: " + file);
            }
        } catch (BackedObjectMap.AccessException e) {
            e.printStackTrace();
        }
    }
}
