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
package sunlabs.titan.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

//
// Thanks to Eamonn McManus (via his blog) for the code that this class is
// based on.
//
/**
 * <p>
 *
 * Each {@code WeakProxyDispenser} provides a method for generating weak
 * proxies for objects implementing a specified interface.
 *
 * </p><p>
 *
 * A weak proxy implements the same interface that its target object does,
 * working by internally maintaining a weak reference to its target and
 * forwarding invocations of the methods of that interface to the target
 * object through that reference.
 *
 * </p><p>
 *
 * To create a weak proxy, it is necessary to supply a callback for use when
 * the target of the proxy's weak reference becomes inaccessible.  This {@code
 * missingHandler} should perform any cleanup that might be required to handle
 * the target's absence.  The callback can be invoked either synchronously, as
 * part of a call made through the proxy, or asynchronously, as part of the
 * garbage collection sequence for weak references, and it should be written
 * to handle both possibilities.
 *
 * </p>
 */
public class WeakProxyDispenser {
    /**
     * Creates a new {@code WeakProxyDispenser}, with its own weak reference
     * queue for handling broken references.
     */
    public WeakProxyDispenser() {
        //
        // The dispenser should be able to handle arbitrary objects, so its
        // reference queue must be able to accept their greatest common
        // superclass, which is Object.
        //
        this.queue = new WeakReferenceQueue<Object>();
    }

    /**
     * Returns a proxy for {@code resource} that forwards calls to the methods
     * of the interface {@code T}, as described by the class {@code
     * interfaceClass} via a weak reference to the {@code resource} object.
     * Should the weak reference be broken because {@code resource} becomes
     * inaccessible, method invocations on the proxy are instead redirected to
     * become invocations of {@code missingHandler}'s {@code call()} method.
     * Setting the {@code otherInterfaces} argument to a non-empty list of
     * interfaces arranges for the resulting proxy to also handle invocations
     * of methods defined by those interfaces, provided that the interfaces
     * obey the restrictions described in {@link
     * java.lang.reflect.Proxy#getProxyClass(ClassLoader, Class...)
     * getProxyClass()}.
     *
     * @param <T>               {@code resource}'s type
     *
     * @param missingHandler    a {@code Callable} to clean things up when
     *                          {@code resource} is discovered to be
     *                          unreachable
     * @param resource          the object for which a weak proxy is desired
     * @param interfaceClass    an interface that {@code resource} implements
     * @param otherInterfaces   a list of other interfaces that {@code
     *                          resource} implements
     *
     * @return  a proxy for {@code resource}
     */
    public <T> T makeProxy(
            Callable<Object> missingHandler,
            T resource, Class<T> interfaceClass, Class<?>... otherInterfaces) {
        Handler<T> handler =
            new Handler<T>(resource, missingHandler, this.queue);
        Class<?>[] interfaces = new Class<?>[otherInterfaces.length + 1];
        interfaces[0] = interfaceClass;
        System.arraycopy(otherInterfaces, 0, interfaces, 1,
            otherInterfaces.length);
        Object proxy = Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            interfaces,
            handler);
        return interfaceClass.cast(proxy);
    }

    //
    // A version of the WeakReference class that adds a reference to a
    // Callable that's intended to be invoked when the weak reference is
    // broken or when the reference is pulled off its (dead) reference queue.
    //
    private static class WeakReferenceWithHandler<T> extends WeakReference<T> {
        public WeakReferenceWithHandler(T resource,
                Callable<Object> missingHandler,
                ReferenceQueue<? super T> queue) {
            super(resource, queue);
            this.missingHandler = missingHandler;
        }

        public final Callable<Object> missingHandler;
    }

    //
    // An implementation of InvocationHandler that passes calls through from
    // the proxy to the underlying object as long as the weak reference
    // leading to the object remains intact.  When the reference is broken, it
    // instead calls missingHandler to deal with the breakage.
    //
    // XXX: Probably could be redone to avoid storing missingHandler directly,
    //      instead digging it out from resourceRef when needed.
    //
    private static class Handler<T> implements InvocationHandler {
        public Handler(T resource, Callable<Object> missingHandler,
                ReferenceQueue<? super T> queue) {
            this.resourceRef = new WeakReferenceWithHandler<T>(
                resource, missingHandler, queue);
            this.missingHandler = missingHandler;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            T resource = this.resourceRef.get();
            if (resource == null) {
                System.err.printf("WeakProxyDispenser: vanished weak ref%n");
                return missingHandler.call();
            } else
                return method.invoke(resource, args);
        }

        private final WeakReference<T>  resourceRef;
        private final Callable<Object>  missingHandler;
    }

    //
    // An extension of ReferenceQueue that packages the queue together with a
    // thread for cleaning it.
    //
    private static class WeakReferenceQueue<T> extends ReferenceQueue<T> {
        public WeakReferenceQueue() {
            super();
            new Thread(this.queueRunner, "WeakReferenceQueue Runner").start();
        }

        private final Runnable queueRunner = new Runnable() {
            public void run() {
                while (true) {
                    WeakReferenceWithHandler<T> reference;
                    try {
                        //
                        // XXX: See whether it's possible to cast to a more
                        //      refined type.  Failing that, declaring the
                        //      local variable ref lets us use an annotation
                        //      to suppress the complaint the compiler would
                        //      otherwise emit.
                        //
                        @SuppressWarnings(value="unchecked")
                            WeakReferenceWithHandler<T> ref =
                                (WeakReferenceWithHandler<T>)
                                    WeakReferenceQueue.this.remove();
                        reference = ref;
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        reference.missingHandler.call();
                    } catch (Exception e) {
                        //
                        // The call method had better catch and handle
                        // Exception itself, because there's nothing that can
                        // be done here (other than crash and burn the whole
                        // JVM, which isn't particularly appealing, either).
                        //
                    }
                }
            }
        };
    }

    private final WeakReferenceQueue<Object> queue;
}
