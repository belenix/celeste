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
package sunlabs.beehive.util;

import java.util.concurrent.Callable;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;


/**
 * A {@code WeakMBeanRegistrar} is an intermediary between an MBean server and
 * the objects it manages.  A registrar creates weak proxies for each of its
 * MBeans and registers those proxies with its corresponding MBean server.
 * The proxies isolate their MBeans from the server, preventing the server from
 * keeping a given MBean alive when it would otherwise become unreferenced.
 * When a given MBean goes unreferenced, its registrar (eventually) notices
 * and unregisters the corresponding weak proxy from the server.
 */
public class WeakMBeanRegistrar {
    //
    // If JMX isn't enabled for the JVM, we don't want to mess around with
    // MBean registration at all.
    //
    private final static boolean registrationEnabled = (System.getProperty("com.sun.management.jmxremote") != null);

    /**
     * Creates a registrar associated with the MBean server denoted by {@code
     * server}.
     *
     * @param server    the MBean server against which this registrar will
     *                  register MBeans
     */
    public WeakMBeanRegistrar(MBeanServer server) {
        this.server = server;
        if (WeakMBeanRegistrar.registrationEnabled)
            this.proxyDispenser = new WeakProxyDispenser();
        else
            this.proxyDispenser = null;
    }

    /**
     * Register {@code mbean} with this registrar's server under the given
     * {@code name}, arranging for the mbean to be unregistered when it
     * becomes unreferenced.  {@code mbeanInterface} must be a class
     * representing an interface that {@code mbean} implements.
     *
     * @param name              the name by which this MBean registrar's
     *                          server is to refer to {@code bean}
     * @param mbean             the MBean to be registered
     * @param mbeanInterface    an interface that {@code MBean} implements
     */
    public <T> void registerMBean(ObjectName name, T mbean, Class<T> mbeanInterface) throws
                InstanceAlreadyExistsException,
                MBeanRegistrationException,
                NotCompliantMBeanException {
        if (!WeakMBeanRegistrar.registrationEnabled)
            return;

        T weakProxy = this.proxyDispenser.makeProxy(
            new UnreferencedHandler(name), mbean, mbeanInterface);
        //
        // Turn the proxy into something that the MBean server will recognize
        // as an MBean.
        //
        StandardMBean reBeanedProxy =
            new StandardMBean(weakProxy, mbeanInterface);
        this.server.registerMBean(reBeanedProxy, name);
    }

    /**
     * <p>
     *
     * Removes the registration for {@code name} from the MBean server.
     *
     * </p><p>
     *
     * Calling this method can reduce heap consumption by bypassing the need
     * for weak reference processing to discover that the MBean denoted by
     * {@code name} is no longer in use.
     *
     * </p>
     *
     * @param name  the name of the MBean to be unregistered
     *
     * @throws InstanceNotFoundException
     *      if {@code name} is not currently registered with the MBean server
     */
    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException {
        this.server.unregisterMBean(name);
    }

    //
    // Each registered MBean is accompanied underneath the covers by an
    // instance of UnreferencedHandler.  That instance's call() method is
    // invoked when the MBean becomes unreferenced (or when the MBean server
    // attempts to access if after it becomes unreferenced but before normal
    // unreferenced processing gets to the underlying weak reference).
    //
    private class UnreferencedHandler implements Callable<Object> {
        UnreferencedHandler(ObjectName name) {
            this.name = name;
        }

        public Object call() {
            //
            // The MBean proper has become unreferenced.  Tell the MBean
            // server to forget about the bean, accounting for the possibility
            // that the reference queue thread has already done so or that
            // registration never succeeded in the first place.
            //
            try {
//                System.err.printf(
//                    "WeakMBeanRegistrar: unregistering %s proxy%n",
//                    name.toString());
                WeakMBeanRegistrar.this.server.unregisterMBean(name);
            } catch (InstanceNotFoundException alreadyUnregistered) {
            } catch (MBeanRegistrationException nothingUsefulToDo) {
            }
            return null;
        }

        private final ObjectName name;
    }

    private final MBeanServer           server;
    private final WeakProxyDispenser    proxyDispenser;
}
