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
package sunlabs.asdf.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of a map that is maintained in the local filesystem and not in memory.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class BackedMap<K,V/* extends Serializable*/> extends AbstractMap<String,V> implements Serializable {
    private final static long serialVersionUID = 1L;
    
    public final static boolean LEAVELOCKED = true;

    private String directory;

    /**
     * Given a Map key, construct a filesystem pathname for that key.
     *
     * @param key   the key for which a pathname is desired
     *
     * @return  the pathname corresponding to {@code key}
     */
    protected String makePath(String key) {
        StringBuilder result = new StringBuilder(this.directory).append(File.separatorChar);

        for (int i = 0; i < key.length(); i++) {

            char c = key.charAt(i);
            if (c == File.separatorChar) {
                result.append("%2f");
            } else if (c == '%') {
                result.append("%25");
            } else if (c == '+') {
                result.append("%2b");
            } else {
                result.append(c);
            }
            if (((i+1) % 4) == 0 && i < (key.length() - 1)) {
                result.append('+');
                result.append(File.separatorChar);
            }
        }

        return result.toString();
    }

    /**
     * Given a filesystem pathname, return it as a key to this map.
     * @param path
     */
    protected String makeKey(String path) {
        path = path.substring(this.directory.length());

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '+') {
                /**/
            } else if (c != File.separatorChar) {
                result.append(c);
            }
        }
        path = result.toString().replace("%2f", File.separator).replace("%25", "%").replace("%2b", "+");
        return path;
    }

    /**
     * Get all of the keys in this Map and return them in a Set.
     * 
     * @param dir
     */
    private synchronized Set<String> getAll(String dir) {
        Set<String> list = new HashSet<String>();

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

    public BackedMap(String directory, boolean initialise) throws AccessException {
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

    public synchronized void destroy() {
        this.delete(this.directory);
    }

    private synchronized void delete(String name) {
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
    public void clear() {
        synchronized (this) {
            for (String key: this.keySet()) {
                this.remove(key);
            }
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object arg0) {
        // XXX Lock this?
        synchronized (this) {
            return new File(this.makePath(arg0.toString())).exists();
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object arg0) {
        synchronized (this) {
            for (String key: this.keySet()) {
                FileInputStream in = null;
                try {
                    in = new FileInputStream(this.makePath(key));
                    ObjectInputStream os = new ObjectInputStream(in);
                    Object o = os.readObject();
                    if (o.equals(arg0))
                        return true;
                } catch (FileNotFoundException fileNotFound) {
                    /* */
                } catch (IOException IO) {
                    /*
                     * If this failed, it's likely because of some error that is likely to be persistent.
                     * Should the entry be removed?
                     */
                } catch (ClassNotFoundException classNotFound) {
                    /*
                     * If this failed, it's likely because the file is not a java serialized object.
                     */
                } finally {
                    if (in != null) try { in.close(); } catch (IOException ignore) { /**/ }
                }
            }
        }
        return false;
    }

    private class Entry implements Map.Entry<String,V> {
        private String key;

        public Entry(String key) {
            this.key = key;
        }

        public String getKey() {
            return this.key;
        }

        public V getValue() {
            return BackedMap.this.get(this.key);
        }

        public V setValue(V v) {
            V oldValue = this.getValue();
            BackedMap.this.put(this.key, v);
            return oldValue;
        }
    }

    /**
     * 
     * XXX This must be improved to NOT create an in-memory Set. Instead a "BackedSet" should be used which does not maintain the set in memory.
     */
    private class BackedSet extends HashSet<Map.Entry<String,V>> {
        private static final long serialVersionUID = 0;

        public BackedSet() {
            super();
        }

        /* (non-Javadoc)
         * @see java.util.HashSet#remove(java.lang.Object)
         */
        @Override
        public boolean remove(Object arg0) {
            BackedMap.this.remove(arg0);
            return super.remove(arg0);
        }

        /* (non-Javadoc)
         * @see java.util.AbstractSet#removeAll(java.util.Collection)
         */
        @SuppressWarnings(value="unchecked")
        @Override
        public boolean removeAll(Collection<?> c) {
            for (Object obj: c) {
                if (obj instanceof Map.Entry) {
                    Map.Entry<String,V> entry = (Map.Entry<String,V>) obj;
                    BackedMap.this.remove(entry.getKey());
                }
            }

            return super.removeAll(c);
        }

        /* (non-Javadoc)
         * @see java.util.AbstractCollection#retainAll(java.util.Collection)
         */
        @SuppressWarnings(value="unchecked")
        @Override
        public boolean retainAll(Collection<?> c) {
            Set<String> keySet = BackedMap.this.keySet();
            for (Object obj: c) {
                if (obj instanceof Map.Entry) {
                    Map.Entry<String,Object> entry = (Map.Entry<String,Object>) obj;
                    if (!keySet.contains(entry.getKey())) {
                        BackedMap.this.remove(entry.getKey());
                    }
                }
            }
            return super.retainAll(c);
        }

        @Override
        public void clear() {
            BackedMap.this.clear();
        }
    }

    /**
     */
    @Override
    public Set<Map.Entry<String,V>> entrySet() {

        // XXX Lock this
        Set<Map.Entry<String,V>> set = new BackedSet();

        for (String key: this.keySet()) {
            set.add(new Entry(key));
        }
        return set;
    }

    /* (non-Javadoc)
     * @see java.util.Map#get(java.lang.Object)
     */
    @SuppressWarnings(value="unchecked")
    @Override    
   public synchronized V get(Object arg0) throws ClassCastException, SecurityException {
        try {
            File file = new File(this.makePath(arg0.toString()));

            if (file.isFile()) {
                FileInputStream in = null;
                Object o = null;

                try {
                    in = new FileInputStream(file);
                    //
                    // There seems to be no way to express this cast safely; hence the
                    // @SuppressWarnings annotation above.
                    //
                    o = new ObjectInputStream(in).readObject();
                    return (V) o;
                } catch (FileNotFoundException fileNotFound) {
                    return null;
                } catch (IOException IO) {
                    file.delete();
                    throw new ClassCastException(this.makePath(arg0.toString()) + " " + IO.toString());
                } catch (ClassNotFoundException classNotFound) {
                    file.delete();
                    throw new ClassCastException(this.makePath(arg0.toString()) + " " + classNotFound.toString());
                } catch (ClassCastException e) {
                    System.err.printf("BackedMap.get ClassCastException: %s got %s%n", file.toString(), o.getClass().toString());

                } finally {
                    if (in != null) try { in.close(); } catch (IOException ignore) { /**/ }
                }
            }
        } catch (NullPointerException e) {
            /* */
        }

        return null;
   }
    
    /* (non-Javadoc)
     * @see java.util.Map#keySet()
     */
    @Override
    public Set<String> keySet() {
        return this.getAll(this.directory);
    }

    /* (non-Javadoc)
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @SuppressWarnings(value="unchecked")
    @Override
    public synchronized V put(String key, V value) {
        String pathName = this.makePath(key);
        File file = new File(pathName);
        V oldValue = null;

        // First, try to fetch the current value.
        if (file.isFile()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                //
                // There seems to be no way to express this cast safely; hence the
                // @SuppressWarnings annotation above.
                //
                oldValue = (V) new ObjectInputStream(in).readObject();
            } catch (FileNotFoundException notFound) {
                // it's okay, do nothing.
            } catch (IOException io) {
                System.err.println(io.getLocalizedMessage() + " " + file);
                io.printStackTrace();
                //file.delete();
            } catch (ClassNotFoundException classNotFound) {
                //
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignore) { }
            }
        }

        OutputStream out = null;
        try {
            file.getParentFile().mkdirs();

            out = new BufferedOutputStream(new FileOutputStream(file));
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(value);
        } catch (FileNotFoundException fileNotFound) {
            throw new IllegalArgumentException(fileNotFound);
        } catch (IOException io) {
            file.delete();
            throw new IllegalArgumentException(io);
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignore) { /**/ }
        }
        return oldValue;
    }

    /* (non-Javadoc)
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    public void putAll(Map<? extends String, ? extends V> arg0) {
        for (String key: arg0.keySet()) {
            this.put(key, arg0.get(key));
        }
    }

    /* (non-Javadoc)
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    public synchronized V remove(Object arg0) {
        V previousValue = this.get(arg0);
        if (previousValue != null) {
            File file = new File(this.makePath(arg0.toString()));
            if (!file.delete()) {
                throw new IllegalArgumentException("failed to delete " + file);
            }
            File parent = file.getParentFile();
            while (parent != null && !parent.toString().equals(this.directory) && parent.list().length == 0) {
                if (!parent.delete()) {
                    throw new IllegalArgumentException("failed to delete " + file);
                }
                parent = parent.getParentFile();
            }
        }
        return previousValue;
    }

    /* (non-Javadoc)
     * @see java.util.Map#size()
     */
    @Override
    public int size() {
        return this.keySet().size();
    }

    /* (non-Javadoc)
     * @see java.util.Map#values()
     */
    @Override
    public Collection<V> values() {
        Collection<V> collection = new HashSet<V>();
        for (String key: this.keySet()) {
            collection.add(this.get(key));            
        }
        return collection;
    }

    public static void main(String[] args) {
        try {
            BackedMap<String,String> map = new BackedMap<String,String>("/tmp/map", true);

            map.put("1234/", "A");
            map.put("12345+", "A");
            map.put("123456", "1");
            map.put("12", "2");
            map.put("22345", "3");
            map.put("323456", "4");

            System.out.println("created " + map.size() + " entries.");

            for (Map.Entry<String, String> entry : map.entrySet()) {
                System.out.println(" " + entry.getKey() + "=" + entry.getValue());
            }

            System.out.println("Test entrySet");

            Set<Map.Entry<String,String>> set = map.entrySet();
            for (Map.Entry<String,String> entry: set) {
                System.out.println(" key: " + entry.getKey() + "=" + entry.getValue());
                entry.setValue(entry.getKey());
            }

            System.out.println("Test keySet");
            for (Map.Entry<String, String> entry : map.entrySet()) {
                System.out.println("key: " + entry.getKey() + "=" + entry.getValue());
            }

            System.out.println("remove 12");
            set.remove("12");
            for (Map.Entry<String, String> entry : map.entrySet()) {
                System.out.println("key: " + entry.getKey() + "=" + entry.getValue());
            }
            map.remove("323456");            

            System.out.println("323456=" + map.get("323456"));

            //map.clear();

            for (String file: map.keySet()) {
                System.out.println("key: " + file);
            }
        } catch (BackedMap.AccessException e) {
            e.printStackTrace();
        }
    }
}
