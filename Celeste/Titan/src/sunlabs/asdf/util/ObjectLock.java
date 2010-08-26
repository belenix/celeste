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
package sunlabs.asdf.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Map;

/**
 * @param <T> The type of the object to lock.
 */
public class ObjectLock<T> extends Hashtable<T,ObjectLock.Locker> {
    private static final long serialVersionUID = 1L;
    
    /*
     * The intention here is to allow only one reference to an object
     * stored on disk to be "active" at any given time.
     * When the instance is no longer used, it must be unlocked.
     * The finalize() method will unlock an object when it is no longer
     * referenced (if the finalize() method is actually called).
     * A subsequent attempt to create an instance of StaticData refering
     * to the same name will wait on the busy-list if that name is already
     * in the busy-list.
     * 
     * I'm not sure, at the moment, what the difference is between this and
     * a java.concurrent.Semaphore.
     * 
     * For starters, this code predates Semaphore's availability, it can help
     * track down the holders of locks, ... 
     */
    public static class Locker {
        protected Throwable throwable;
        protected Thread owner;
        
        public Locker(Throwable throwable, Thread owner) {
            this.throwable = throwable;
            this.owner = owner;
        }
        
        public String toString() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            this.throwable.printStackTrace(new PrintStream(out));
            return new String(out.toByteArray());
        }
    }
    
    public ObjectLock() {
        super(110, 0.75F);
    }
    
    /**
     * Assert that the current {@link Thread} has a lock on the given object.
     * 
     * @param key
     * @throws IllegalStateException if the object is not locked or, if it is locked, but not by the current Thread.
     */
    public Locker assertLock(final T key) throws IllegalStateException {
        synchronized (this) {
            Locker locker = this.get(key);
            if (locker == null || locker.owner.getId() != Thread.currentThread().getId()) {
                throw new IllegalStateException(String.format("The current Thread has not locked object %s", key));
            }
            return locker;
        }
    }
    
    /**
     * Lock an object.
     * <p>
     * This must be eventually followed by a {@link #unlock(Object)} on the same object.
     * </p>
     * @throws IllegalStateException if the current Thread attempts to lock and item that it has already locked.
     */
    public T lock(final T key) throws IllegalStateException {
        this.lock(key, false);
        return key;
    }

    /**
     * Lock an object.
     * <p>
     * This must be eventually followed by a {@link #unlock(Object)} on the same object.
     * </p>
     * @throws IllegalStateException if the current Thread attempts to lock and item that it has already locked.
     */
    public void lock(final T key, boolean trace) throws IllegalStateException {
        Locker locker;
        int waitTimeMs = 5000;
        boolean reportable = false;
        
        long startTime = System.currentTimeMillis();
        
        long elapsedTime = 0;
        
        synchronized (this) {
            long currentThreadId = Thread.currentThread().getId();
            while ((locker = this.get(key)) != null) {
                // Throw an IllegalStateException if there is already a lock on this object held by this Thread.
                // XXX Make this reentrant.
                if (locker.owner.getId() == currentThreadId) {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    PrintStream p = new PrintStream(bout);
                    
                    p.printf("%1$tFZ%1$tT %2$d Attempted to recursively lock %3$s%n", startTime, currentThreadId, key);
                    new Throwable().printStackTrace(p);
                    p.printf("%d originally aquired lock%n%s", currentThreadId, locker.toString());
                    p.close();
                    System.err.print(bout.toString());
                    throw new IllegalStateException("Attempted to recursively lock " + key);
                }
                // If this is the second time we've waited, complain.
                if (elapsedTime > waitTimeMs) {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    PrintStream p = new PrintStream(bout);

                    p.printf("%1$tF@%1$tT %2$s Contention?        Thread %3$d waiting for %4$dms. %5$d holds lock '%6$s'%n",
                    		System.currentTimeMillis(), Thread.currentThread().getName(), currentThreadId, elapsedTime, locker.owner.getId(), key);
                    p.printf("Thread %d blocked:", currentThreadId);
                    new Throwable().printStackTrace(p);
                    p.printf("Thread %d originally acquired lock:%n%s",  locker.owner.getId(), locker.toString());
                    p.printf("Thread %d is now:%n",  locker.owner.getId());
                    printStackTrace(p,  locker.owner);
                    p.close();
                    System.err.print(bout.toString());
                    reportable = true;
                }
                
                while (true) {
                    try {
                        this.wait(waitTimeMs);
                        break;
                    } catch (InterruptedException e) {
                        /**/
                    }
                }
                elapsedTime = System.currentTimeMillis() - startTime;
            }

            if (reportable) {
            	System.err.printf("%1$tFZ%1$tT Contention broken. Thread %2$d waited for  %3$dms for lock '%4$s'%n",
            			System.currentTimeMillis(), currentThreadId, elapsedTime, key);
            }

            this.put(key, new Locker(new Throwable(), Thread.currentThread()));
        }
    }
    
    /**
     * Try to obtain the lock on the given object.
     *
     * @param key
     * @return true if the lock was successfully acquired, false if not.
     */
    public boolean trylock(final T key) {        
        long currentThreadId = Thread.currentThread().getId();
        synchronized (this) {
            Locker locker = this.get(key);
            
            if (locker == null) {
                this.put(key, new Locker(new Throwable(), Thread.currentThread()));
                return true;
            }
            if (locker.owner.getId() == currentThreadId) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Unlock a previously locked object identified by {@code key}.
     * <p>
     * Return true if the current Thread had the lock.
     * Return false if this object was not locked or if this Thread did not hold the lock. 
     * </p>
     * @param key
     * @throws IllegalStateException if the object identified by {@code key} is not locked by the current Thread.
     */
    public boolean unlock(final T key) throws IllegalStateException {
        return this.unlock(key, false);
    }

    /**
     * Unlock the object identified by {@code key}.

     * @param key
     * @param trace
     * @return {@code true} if successfully unlocked.
     * @throws IllegalStateException if the object identified by {@code key} is not locked by the current Thread.
     */
    public boolean unlock(final T key, boolean trace) throws IllegalStateException {
    	synchronized (this) {
    		this.assertLock(key);

    		if (this.remove(key) == null) {
    			System.out.printf("Unlocking an non-existent lock: '%s'%n", key);
    		}
    		this.notify();
    	}
    	return true;
    }
    
    public String toString() {
        StringBuilder s = new StringBuilder("Locks:\n");
        for (T key : this.keySet()) {
            s.append("  '").append(key).append("' ").append(this.get(key).toString()).append("\n");
        }
        return s.toString();
    }
    
    public void printStackTrace(PrintStream p, Thread thread) {
    	Map<Thread,StackTraceElement[]> map = Thread.getAllStackTraces();
    	if (thread.isAlive()) {
    		StackTraceElement[] trace = map.get(thread);
    		if (trace != null) {
    			for (StackTraceElement e : trace) {
    				p.print("        at ");
    				p.println(e.toString());
    			}
    		} else {
    			p.printf("Thread %s has no stack backtrace%n", thread.getId());
    		}
    	} else {
    		p.printf("Thread %s is not alive%n", thread.getId());        		
    	}
    }
    
    public static class Task extends Thread {
    	private ObjectLock<String> locks;
    	
    	public Task(ObjectLock<String> locks) {
    		super();
    		this.locks = locks;
    	}
    	
    	public void run() {
    		System.out.printf("%s run%n", this.getName());
    		long startTime = System.currentTimeMillis();
    		
    		this.locks.lock("lock");
    		System.out.printf("%s got lock after %sms.%n", this.getName(), System.currentTimeMillis() - startTime);
    		this.locks.unlock("lock");
    	}
    }
    
    public static void main(String[] args) throws Exception {
    	ObjectLock<String> locks = new ObjectLock<String>();

    	System.out.printf("Main Thread %d%n", Thread.currentThread().getId());
    	
		locks.lock("lock");
    	for (int i = 0; i < 100; i++) {
    		Task t = new Task(locks);
    		t.start();
    	}
    	
    	Thread.sleep(2000);
		locks.unlock("lock");
    	
    	System.out.printf("Main exit%n");
    }
}
