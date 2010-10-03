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
package sunlabs.titan.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;

/**
 * Classes implementing this interface provide a run-time inspection mechanism
 * which produces a inspectable representation of the class data.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface XHTMLInspectable {
    /**
     * Return an {@link sunlabs.asdf.web.xml XHTML.EFlow} instance containing the representation of
     * the implementing class's instance data.
     * 
     * @param uri the HTTP/WebDaV request {@link URI}
     * @param props a {@link Map} of named attributes as {@link String} and {@link sunlabs.asdf.web.http HTTP.Message} instances as values, if the incoming request contained
     *  encoded data (eg. HTTP POST or PUT).
     *
     * @throws URISyntaxException
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
}
