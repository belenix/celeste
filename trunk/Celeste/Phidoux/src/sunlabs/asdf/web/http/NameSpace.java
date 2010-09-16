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
package sunlabs.asdf.web.http;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpContent;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.HTTP.Request.Method;

/**
 * A fully functional, extensible, implementation of the {@link HTTP.NameSpace} interface.
 * <p>
 * Implementors can either extend this class, overriding methods implemented in this class,
 * or instantiate this class and install their own {@link HTTP.Request.Method.Handler} objects,
 * via the {@link #add(Method, sunlabs.asdf.web.http.HTTP.Request.Method.Handler)} method.
 * </p>
 * <p>
 * This class creates a default {@link HTTP.Request.Method.Handler} for the method {@code OPTIONS}.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class NameSpace implements HTTP.NameSpace {
    private Map<HTTP.Request.Method,HTTP.Request.Method.Handler> methods;
    protected HTTP.Server server;
    protected HTTP.Backend backend;
    
    public NameSpace(HTTP.Server server, HTTP.Backend backend) {
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
    
    public HTTP.Response dispatch(HTTP.Request request, HTTP.Identity identity) {
        HTTP.Request.Method.Handler methodHandler = this.methods.get(request.getMethod());
        if (methodHandler == null) {
            return new HttpResponse(HTTP.Response.Status.NOT_IMPLEMENTED, new HttpContent.Text.Plain("%s is not implemented.\n", request.getMethod()));
        }
        HTTP.Response response = null;
        try {
            response = methodHandler.execute(request, identity);
            if (response == null) {
                response = new HttpResponse(HTTP.Response.Status.INTERNAL_SERVER_ERROR,
                        new HttpContent.Text.Plain("Internal handler for %s (%s)method returned null\n", request.getMethod(), methodHandler));
            }
        } catch (HTTP.UnauthorizedException e) {
            // Authorization was either required, or incorrect. Reply signaling that authorisation is required.
            response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
            response.getMessage().addHeader(new HttpHeader.WWWAuthenticate(e.getAuthenticate()));
        } catch (HTTP.Exception e) {
            response = new HttpResponse(e.getStatus(), new HttpContent.Text.Plain("%s\n", e.toString()));
        }
        return response;
    }

    /**
     * Get the {@link HTTP.Server} instance for this name space.
     * @return the {@link HTTP.Server} instance for this name space.
     */
    public HTTP.Server getServer() {
        return this.server;
    }

    /**
     * Get the {@link HTTP.Backend} instance for this name space.
     * @return the {@link HTTP.Backend} instance for this name space.
     */
    public HTTP.Backend getBackend() {
        return this.backend;
    }

    public Collection<HTTP.Request.Method> getAccessAllowed() {
        return this.methods.keySet();
    } 
    
    public class Options implements HTTP.Request.Method.Handler {
        private HTTP.Server server;

        public Options(HTTP.Server server, HTTP.Backend backend) {
            super();
            this.server = server;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) throws
          HTTP.BadRequestException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.ConflictException {

            // A URI of "*" is about this server, not a particular named resource.
            // Think of "*" as the URI of the server.
            if (request.getURI().getPath().equals("*")) {
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK);
                response.getMessage().addHeader(
                        new HttpHeader.Date(),
                        new HttpHeader.Allow(this.server.getAccessAllowed()),
                        new HttpHeader.Connection("Keep-Alive"),
                        new HttpHeader.DAV("1,2")
                );

                return response;
            }
            throw new HTTP.NotFoundException(request.getURI());
        }
    }       
}
