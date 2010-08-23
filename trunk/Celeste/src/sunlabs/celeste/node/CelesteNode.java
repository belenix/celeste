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
 * Please contact Oracle, 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additionalinformation or
 * have any questions.
 */

package sunlabs.celeste.node;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;

import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.util.OrderedProperties;

/**
 * <p>
 * A {@code CelesteNode} instance provides access to a low level file
 * abstraction through a specific Titan node.  At this level, there is no
 * notion of a hierarchical file system name space.  Files act simply as data
 * containers.  File names are simply the object-id of their
 * {@link sunlabs.celeste.node.services.object.AnchorObjectHandler.AObject}s.
 * </p>
 * <p>
 * An {@code AnchorObject} object-id is formed from the object-id of the file creator's {@link Credential} and the hash
 * (represented by an object-id) of an arbitrary string (like a file-name)
 * supplied by the file's ({@code AnchorObject}'s) creator.)
 * </p>
 * <p>
 * Read and write operations operate over explicitly specified ranges; no
 * information about a given client's access history to a given file is
 * maintained.  Higher level abstractions build on this layer to provide more
 * traditional file system semantics.
 * </p>
 */
public final class CelesteNode extends BeehiveNode /*implements CelesteAPI*/ {
    public final static String PACKAGE = CelesteNode.class.getPackage().getName();
    public final static String SERVICE_PKG = PACKAGE + ".services";
    public final static String OBJECT_PKG = "sunlabs.celeste.node.services.object";
    public final static String BEEHIVE_OBJECT_PKG = "sunlabs.titan.node.services.object";

    //    public static final boolean bObjectPrefetchNext = true;
    //    public static final boolean bObjectCacheRetrieved = false;

    public CelesteNode(OrderedProperties properties) throws IOException, ConfigurationException {
        super(properties);

        // Load and start any applications that have long-running operations.
        // Any other applications will be loaded lazily as needed.

        CelesteClientDaemon clientProtocolDaemon = (CelesteClientDaemon) this.getService(CelesteClientDaemon.class);
    }

    public static void main(String[] args) {
        // Read this command line argument as a URL to fetch configuration properties.

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);
        try {
            OrderedProperties p = new OrderedProperties(new URL(args[0]));
            CelesteNode node = new CelesteNode(p);
            Thread thread = node.start();

            System.out.printf("%s [%d ms] %s%n", dateFormat.format(new Date()),
                    System.currentTimeMillis() - Long.parseLong(node.getProperty(BeehiveNode.StartTime.getName())), node.toString());
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (ConfigurationException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    public InetSocketAddress getInetSocketAddress() {
        return this.getNodeAddress().getInternetworkAddress();
    }
}
