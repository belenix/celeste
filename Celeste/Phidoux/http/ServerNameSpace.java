/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.asdf.web.http;

import java.util.HashMap;
import java.util.Map;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpResponse;
import sunlabs.asdf.web.http.NameSpace;

/**
 * A generic name-space handler for the server.
 * <p>
 * The name-space for the server is signified by the URI "*"
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class ServerNameSpace extends NameSpace {
    private Map<HTTP.Request.Method,HTTP.Request.Method.Handler> methods;
    
    public ServerNameSpace(HTTP.Server server, HTTP.Backend backend) {
        super(server, backend);
        
        this.methods = new HashMap<HTTP.Request.Method,HTTP.Request.Method.Handler>();
        this.methods.put(HTTP.Request.Method.OPTIONS, new ServerNameSpace.Options(this.server, this.backend));
    }

    public static class Options implements HTTP.Request.Method.Handler {
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
