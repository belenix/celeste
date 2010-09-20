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

/**
 * Trick SSL into accepting arbitrary certificates
 *
 * This code allows us to use SSL without established certificate authorities.
 * Certificate authenticity is instead validated after connection
 * establishment, when the actual {@link TitanGuidImpl} of the peer node can be
 * retrieved and compared to the hash of the provided X509 SSL certificate.
 */
package sunlabs.titan.node;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

public class NodeX509TrustManager implements X509TrustManager {
    public NodeX509TrustManager() { 
        //System.out.println("NodeX509TrustManager constructor");
    }
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        //System.out.println("NodeX509TrustManager checkServerTrusted");
    }
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        //System.out.println("NodeX509TrustManager checkclientTrusted");
    }
    public X509Certificate[] getAcceptedIssuers() {
        //System.out.println("NodeX509TrustManager getAcceptedIssuers");
        return new X509Certificate[0];
    }
}
