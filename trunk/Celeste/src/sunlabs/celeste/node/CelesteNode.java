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

package sunlabs.celeste.node;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.util.OrderedProperties;

/**
 * <p>
 * A {@code CelesteNode} instance provides access to a low level file
 * abstraction through a specific Titan node.  At this level, there is no
 * notion of a hierarchical file system name space.  Files act simply as data
 * containers.  File names are simply the object-ids of their
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
public final class CelesteNode extends TitanNodeImpl /*implements CelesteAPI*/ {
    public final static String PACKAGE = CelesteNode.class.getPackage().getName();
    public final static String SERVICE_PKG = PACKAGE + ".services";
    public final static String OBJECT_PKG = "sunlabs.celeste.node.services.object";
    public final static String BEEHIVE_OBJECT_PKG = "sunlabs.titan.node.services.object";

    /**
     * Construct a new instance of CelesteNode, extending {@link TitanNodeImpl},
     * and adding {@link CelesteClientDaemon} as an additional {@link TitanService}.
     * @param properties The configuration properties for this node.
     * @throws IOException If {@link TitanNodeImpl} throws an {@code IOException}.
     * @throws ConfigurationException If {@link TitanNodeImpl} throws an {@code ConfigurationException}.
     */
    public CelesteNode(OrderedProperties properties) throws IOException, ConfigurationException {
        super(properties);

        this.getService(CelesteClientDaemon.class);
    }

    public static void main(String[] args) {
        // Read this command line argument as a URL to fetch configuration properties.

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // ISO 8601    
        OrderedProperties configurationProperties = new OrderedProperties();
        try {
            for (int i = 0; i < args.length; i++) {
                configurationProperties.load(new URL(args[i]));
            }
            CelesteNode node = new CelesteNode(configurationProperties);
            Thread thread = node.start();

            System.out.printf("%s [%d ms] %s%n", dateFormat.format(new Date()),
                    System.currentTimeMillis() - Long.parseLong(node.getProperty(TitanNodeImpl.StartTime.getName())), node.toString());
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
