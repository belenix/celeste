/*
 * Copyright 2010 Oracle. All Rights Reserved.
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
package sunlabs.asdf.web.http;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import sunlabs.asdf.web.http.HTTP.BadRequestException;
import sunlabs.asdf.web.http.HTTP.Server;
import sunlabs.asdf.web.http.HTTP.Request.Method;
import sunlabs.asdf.web.http.HTTP.Request.Method.Handler;
import sunlabs.asdf.web.http.WebDAV.Backend;

/**
 * A skeletal implementation of the {@link HTTP.URINameSpace} interface.
 *
 */
public abstract class AbstractURINameSpace implements HTTP.URINameSpace {
    protected Server server;
    protected Backend backend;
    protected Map<Method, Handler> methods;

    public AbstractURINameSpace(HTTP.Server server, WebDAV.Backend backend) {
        this.server = server;
        this.backend = backend;

        this.methods = new HashMap<HTTP.Request.Method,HTTP.Request.Method.Handler>();
    }

    public void add(HTTP.Request.Method method, HTTP.Request.Method.Handler methodHandler) {
        this.methods.put(method, methodHandler);
    }

    public HTTP.Request.Method.Handler get(HTTP.Request.Method method) {
        return this.methods.get(method);
    }
    
    public Collection<Method> getAccessAllowed() {
        return this.methods.keySet();
    }

    /**
     * This implementation catches an {@link HTTP.UnauthorizedException} thrown by the underlying {@link HTTP.Request.Method#equals(Object)}
     * method and forms an {@link HTTP.Response} instance signaling the client to authenticate itself.
     * @throws BadRequestException 
     * @see HttpHeader.WWWAuthenticate
     */
    public HTTP.Response dispatch(HTTP.Request request, HTTP.Identity identity) throws BadRequestException {
        HTTP.Request.Method.Handler methodHandler = this.methods.get(request.getMethod());
        if (methodHandler == null){
            throw new HTTP.BadRequestException(String.format("Unsupported method %s%n", request.getMethod()));
        }
        HTTP.Response response = null;
        try {
            response = methodHandler.execute(request, identity);
        } catch (HTTP.UnauthorizedException e) {
            // There was no Authorization header and one is required.  Reply signaling that authorisation is required.
            response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
            response.getMessage().addHeader(new HttpHeader.WWWAuthenticate(e.getAuthenticate()));
        } catch (HTTP.Exception e) {
            response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
        }
        return response;
    }
}
