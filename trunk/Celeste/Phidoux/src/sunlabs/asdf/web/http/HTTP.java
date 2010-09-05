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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import sunlabs.asdf.web.http.HTTP.Message.Header.If.Conditional;

/**
 * The HTTP class contains interfaces, definitions and helper classes for implementing an 
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.html">Hypertext Transfer Protocol -- HTTP/1.1 </a>
 * compliant HTTP server.
 * <p>
 * This class is in flux as it will incorporate support for WebDAV as a super-set of functionality.
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class HTTP {

    abstract public static class AbstractResource implements HTTP.Resource {
        private Backend backend;
        private URI uri;
        private Identity identity;

        public AbstractResource(HTTP.Backend backend, URI uri, HTTP.Identity identity) {
            this.backend = backend;
            this.uri = uri;
            this.identity = identity;
        }

        public HTTP.Backend getBackend() {
            return this.backend;
        }

        /**
         * Always returns {@link InternetMediaType.Application.OctetStream}.
         * Subclasses override this method to supply more accurate responses.
         * <p>
         * {@inheritDoc}
         * </p>
         */
        public InternetMediaType getContentType()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            return InternetMediaType.Application.OctetStream;
        }

        public Identity getIdentity() {
            return this.identity;
        }

        public URI getURI() {
            return this.uri;
        }
    }

    /**
     * An HTTP client.
     * <p>
     * An HTTP client transmits an {@link HTTP.Request} to an {@link HTTP.Server} and waits to receive an {@link HTTP.Response} in reply.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Client {
        
    }

    /**
     * An identity that distinguishes one external entity from another.
     * <p>
     * Identity may be, for example, the user name supplied as part of HTTP authentication,
     * a cookie set in headers of an HTTP.Request, an extension header set or source address of the request.
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     *
     */
    public interface Identity /*extends Map<String,String>*/ {
        public static final String NAME = "Name";
        public static final String PASSWORD = "Pasword";

        public String getProperty(String name, String defaultValue);
    }
    
    /**
     * Classes implementing this interface control authentication information and are used to generate a challenge in a challenge/response protocol.
     * <p>
     * Resources may require authentication information to authorize access.
     * See RFC 2617 HTTP Authentication: Basic and Digest Access Authentication
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Authenticate {
        /**
         * Generate an RFC 2617 compliant challenge for use in the {@code WWWAuthenticate:} header.
         * @return an RFC 2617 compliant challenge for use in the {@code WWWAuthenticate:} header.
         */
        public String generateChallenge();
        
    }
    
    /**
     * An HTTP server.
     * <p>
     * The server is responsible for connection management with clients.
     * Typically an HTTP server iterates reading an {@link HTTP.Request},
     * processing the {@code HTTP.Request} and writing an {@link HTTP.Response} back to the client.
     * </p>
     * <p>
     * Client requests and server responses each contain information about how the connection is to be managed for the duration of the transaction.
     * </p>
     * <p>
     * This HTTP server has the notion of a NameSpace that is assigned to a particular URL prefix.
     * For example, the server may have two NameSpace instances, one assigned to the URL prefix {@code /webdav} and another to the URL prefix {@code /http}.
     * A {@link NameSpace} implements a particular protocol such as HTTP or WebDAV. 
     * </p>
     * <p>
     * NameSpaces use instances of classes implementing {@link HTTP.Backend} which implement access to resources that
     * implement methods that the NameSpace expects to use.
     * For example, the WebDAV NameSpace expects to work with resources that have WebDAV related methods.
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Server extends Runnable {
        /**
         * Get the set of {@link HTTP.Request.Methods} that this server will respond to.
         */
        public Collection<HTTP.Request.Method> getAccessAllowed();
        
        /**
         * Set the {@link Logger} for this Server.
         */
        public void setLogger(Logger logger);
        
        /**
         * Add the given {@link URI} as the namespace prefix of the URIs to be handled by the given {@link HTTP.NameSpace}.
         * <p>
         * All URIs that begin with this prefix are handled by the given {@code HTTP.NameSpace}.
         * </p>
         * 
         * @param root the root {@link URI} of the name-space.
         * @param handler the {@link HTTP.NameSpace} that services the root name-space.
         */
        public void addNameSpace(URI root, HTTP.NameSpace handler);
        
        /**
         * Set the current value of the trace flag.
         * If set to {@code true}, {@link HTTP.Request} and {@link HTTP.Response} objects are logged.
         * See {@link #setLogger(Logger)}.
         */
        public void setTrace(boolean value); 
    }
    
    /**
     * Classes implementing this interface are responsible of taking a single, complete {@link HTTP.Request} instance
     * and producing a corresponding complete {@link HTTP.Response} instance.
     * <p>
     * An HTTP server partitions it's URI name-space into different sub-name-spaces, each with it's own set of methods used to manipulate it.
     * For example, a server may synthesize a URI name-space of {@code /rfc2616/} and {@code /rfc4918/} and correspondingly setup a basic HTTP
     * compliant {@code URIHandler} for the name-space {@code /rfc2616/} and a WebDAV compliant {@code URIHandler} for the name-space {@code /rfc4818/}.
     * In turn, the HTTP server takes inbound {@link HTTP.Request} instances prefixed with the different name-spaces and dispatches them
     * (See {@link #dispatch(Request)}) based on the respective URI in the {@code HTTP.Request}.
     * <p>
     * See also <cite><a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html#sec5.2">RFC 2616, &sect;5.2 The Resource Identified by a Request</a></cite>
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface NameSpace {

        /**
         * Add the given {@link HTTP.Request.Method.Handler} to this name-space.
         * Subsequent invocations of the {@link #dispatch(Request, Identity)} method with {@link HTTP.Request} instances containing the
         * {@link HTTP.Request.Method} {@code method} will be dispatched to the {@code methodHandler} specified here. 
         * @param method The {@link HTTP.Request.Method} to associate with {@code methodHandler}.
         * @param methodHandler An object implementing {@link HTTP.Request.Method.Handler}.
         */
        public void add(HTTP.Request.Method method, HTTP.Request.Method.Handler methodHandler);
        
        /**
         * Get the {@link HTTP.Request.Method.Handler} that this name-space will use to handle the given {@link HTTP.Request.Method}.
         * @param method The {@link HTTP.Request.Method} to get the {@link HTTP.Request.Method.Handler} object.
         * @returnTthe {@link HTTP.Request.Method.Handler} that this name-space will use to handle the given {@link HTTP.Request.Method}
         */
        public HTTP.Request.Method.Handler get(HTTP.Request.Method method);
        
        /**
         * Dispatch the given {@link HTTP.Request} to this URI handler and return the resulting {@link HTTP.Response}.
         * 
         * @param request
         * @return
         */
        public HTTP.Response dispatch(HTTP.Request request, HTTP.Identity identity);        

        /**
         * Get the set of {@link HTTP.Request.Methods} that this server will respond to.
         */
        public Collection<HTTP.Request.Method> getAccessAllowed();
    }
  
    /**
     * Classes implementing this interface provide the prescribed methods to abstract access to "resources" (See {@link HTTP.Resource}).
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Backend {
        /**
         * Create an instance of a class representing an HTTP resource identified by the given URI.
         * <p>
         * This instance represents the resource given by the URI, which may or may not already exist in the backend.
         * Check for the presence of the resource before accessing it.  See {@link HTTP.Resource#exists()}.
         * </p>
         * 
         * @param uri The {@link URI} of the resource
         * @param identity The {@link HTTP} of the accessor. 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws BadRequestException 
         */
        public HTTP.Resource getResource(URI uri, HTTP.Identity identity) throws
          HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.BadRequestException;
        
        /**
         * Create an instance of a class implementing the {@link HTTP.Authenticate} interface which,
         * upon invocation of {@link HTTP.Authenticate#generateChallenge()},
         * generates an authentication challenge that  corresponds to resources managed by this backend.
         * 
         * @param uri the URI of the resource requiring authentication.
         * @return an instance of a class implementing the {@link HTTP.Authenticate} interface which,
         *         upon invocation of {@link HTTP.Authenticate#generateChallenge()},
         *         generates an authentication challenge corresponding to the given {@link URI} managed by this backend.
         */
        public HTTP.Authenticate getAuthentication(URI uri);
    }
    
    /**
     * An {@code HTTP.Request} is transmitted by an HTTP client to an {@link HTTP.Server}.
     * <p>
     * An HTTP client constructs an instance of of a class implementing {@code HTTP.Request},
     * filling in the appropriate parts and ultimately transmits it to an HTTP server.
     * The HTTP server acts on the request and transmits an {@link HTTP.Response} in reply.
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Request {
        /**
         * Return this request's method.
         */
        public HTTP.Request.Method getMethod();

        /**
         * Return this request's URI.
         * 
         * See also {@link HttpUtil#parseURLQuery(String)}.
         */
        public URI getURI();
        
        /**
         * Return this request's {@link HTTP.Message}.
         */
        public HTTP.Message getMessage();
        
        /**
         * If this request was accompanied by {@code x-www-form-urlencoded} data as part of the request URI (see {@link #getURI()}),
         * the data is decoded and available as an instance of {@link Map<String,String>}, where the {@code Map} key is the attribute's
         * name, and the mapped value is the attribute's specified value.
         */
        public Map<String,String> getURLEncoded() throws UnsupportedEncodingException;
        
        /**
         * Write this request as a complete and properly formatted HTTP Request on the given {@link DataOutputStream}.
         * 
         * @param out the {@code DataOutputStream} to write
         * @return the number of bytes written.
         * @throws IOException if {@code DataOutputStream} throws {@code IOException}.
         */
        // XXX This may go away.
        public long writeTo(DataOutputStream out) throws IOException;
        
        
        /**
         * An {@code HTTP.Method}, as part of an {@link HTTP.Request}, indicates the operation that is to be performed on the resource identified by the request URI.
         * Other uses of the HTTP.Method are in HTTP.Response messages indicating what methods are permitted on a particular resource.
         * <p>
         * The proper use of this class is to get the method from the pre-defined set of methods:
         * </p>
         * <pre>
         * HTTP.Request.Method postMethod = HTTP.Request.Method.stringToMethod.get("POST");
         * </pre>
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public class Method {
            private String name;

            private Method(String name) {
                this.name = name;
            }

            @Override
            public String toString() {
                return this.name;
            }

            // RFC 2616
            public static final Method GET = new Method("GET");
            public static final Method PUT = new Method("PUT");
            public static final Method POST = new Method("POST");
            public static final Method HEAD = new Method("HEAD");
            public static final Method OPTIONS = new Method("OPTIONS");
            public static final Method DELETE = new Method("DELETE");
            public static final Method TRACE = new Method("TRACE");
            public static final Method CONNECT = new Method("CONNECT");

            // RFC 4918
            public static final Method PROPFIND  = new Method("PROPFIND");
            public static final Method PROPPATCH  = new Method("PROPPATCH");
            public static final Method MKCOL  = new Method("MKCOL");
            public static final Method COPY  = new Method("COPY");
            public static final Method MOVE  = new Method("MOVE");
            public static final Method LOCK  = new Method("LOCK");
            public static final Method UNLOCK  = new Method("UNLOCK");
            
            // RFC 3253
            public static final Method LABEL  = new Method("LABEL");
            public static final Method VERSION_CONTROL  = new Method("VERSION-CONTROL");
            public static final Method REPORT  = new Method("REPORT");
            public static final Method CHECKIN  = new Method("CHECKIN");
            public static final Method CHECKOUT  = new Method("CHECKOUT");
            public static final Method UNCHECKOUT  = new Method("UNCHECKOUT");
            public static final Method MKWORKSPACE  = new Method("MKWORKSPACE");
            public static final Method UPDATE  = new Method("UPDATE");
            public static final Method MERGE  = new Method("MERGE");
            public static final Method BASELINE_CONTROL  = new Method("BASELINE-CONTROL");
            public static final Method MKACTIVITY  = new Method("MKACTIVITY");
            
            /**
             * A Map from a String containing a method name to an instance of {@link HTTP.Request.Method},
             * or {@code null} if there is no such method in the map.
             * 
             * @see HTTP.Request.Method#getInstance(String)
             */
            public static Map<String,HTTP.Request.Method> stringToMethod = new HashMap<String,HTTP.Request.Method>();
            static {
                stringToMethod.put(HTTP.Request.Method.GET.toString(), HTTP.Request.Method.GET);
                stringToMethod.put(HTTP.Request.Method.PUT.toString(), HTTP.Request.Method.PUT);
                stringToMethod.put(HTTP.Request.Method.POST.toString(), HTTP.Request.Method.POST);
                stringToMethod.put(HTTP.Request.Method.HEAD.toString(), HTTP.Request.Method.HEAD);
                stringToMethod.put(HTTP.Request.Method.OPTIONS.toString(), HTTP.Request.Method.OPTIONS);
                stringToMethod.put(HTTP.Request.Method.DELETE.toString(), HTTP.Request.Method.DELETE);
                stringToMethod.put(HTTP.Request.Method.TRACE.toString(), HTTP.Request.Method.TRACE);
                stringToMethod.put(HTTP.Request.Method.CONNECT.toString(), HTTP.Request.Method.CONNECT);

                stringToMethod.put(HTTP.Request.Method.PROPFIND.toString(), HTTP.Request.Method.PROPFIND);
                stringToMethod.put(HTTP.Request.Method.PROPPATCH.toString(), HTTP.Request.Method.PROPPATCH);
                stringToMethod.put(HTTP.Request.Method.MKCOL.toString(), HTTP.Request.Method.MKCOL);
                stringToMethod.put(HTTP.Request.Method.COPY.toString(), HTTP.Request.Method.COPY);
                stringToMethod.put(HTTP.Request.Method.MOVE.toString(), HTTP.Request.Method.MOVE);
                stringToMethod.put(HTTP.Request.Method.LOCK.toString(), HTTP.Request.Method.LOCK);
                stringToMethod.put(HTTP.Request.Method.UNLOCK.toString(), HTTP.Request.Method.UNLOCK);
                
                stringToMethod.put(HTTP.Request.Method.LABEL.toString(), HTTP.Request.Method.LABEL);
                stringToMethod.put(HTTP.Request.Method.VERSION_CONTROL.toString(), HTTP.Request.Method.VERSION_CONTROL);
                stringToMethod.put(HTTP.Request.Method.REPORT.toString(), HTTP.Request.Method.REPORT);
                stringToMethod.put(HTTP.Request.Method.CHECKIN.toString(), HTTP.Request.Method.CHECKIN);
                stringToMethod.put(HTTP.Request.Method.CHECKOUT.toString(), HTTP.Request.Method.CHECKOUT);
                stringToMethod.put(HTTP.Request.Method.UNCHECKOUT.toString(), HTTP.Request.Method.UNCHECKOUT);
                stringToMethod.put(HTTP.Request.Method.MKWORKSPACE.toString(), HTTP.Request.Method.MKWORKSPACE);
                stringToMethod.put(HTTP.Request.Method.UPDATE.toString(), HTTP.Request.Method.UPDATE);
                stringToMethod.put(HTTP.Request.Method.MERGE.toString(), HTTP.Request.Method.MERGE);
                stringToMethod.put(HTTP.Request.Method.BASELINE_CONTROL.toString(), HTTP.Request.Method.BASELINE_CONTROL);
                stringToMethod.put(HTTP.Request.Method.MKACTIVITY.toString(), HTTP.Request.Method.MKACTIVITY);
            }
            
            /**
             * Return an instance of the object representing the method corresponding to the given String. containing an HTTP Request method name.
             * @param method
             * @return An instance of the object representing the method corresponding to the given String. containing an HTTP Request method name.
             */
            public static HTTP.Request.Method getInstance(String method) {
                return HTTP.Request.Method.stringToMethod.get(method);
            }
            

            /**
             * An HTTP Request handler is responsible for taking an incoming
             * {@link HTTP.Request} performing the processing necessary and returning a valid {@link HTTP.Response}.
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface Handler {
                /**
                 * Produce an {@link HTTP.Response} as the result of processing an {@link HTTP.Request} specifying a matching {@link HTTP.Method}.
                 * <p>
                 * Developers of classes that implement this interface must adhere to the semantics of the
                 * Exceptions thrown in order to ensure that correct error result are returned to the client. 
                 * </p>
                 * @param request the {@link HTTP.Request} to perform.
                 * @param identity the {@link HTTP.Identity} of the requestor.
                 * 
                 * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
                 * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
                 * @throws HTTP.GoneException if the resource once was, but is no longer, available.
                 * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
                 * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
                 * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
                 * @throws HTTP.InsufficientStorageException
                 * @throws HTTP.LockedException
                 * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
                 * @throws HTTP.ForbiddenException
                 * @throws HTTP.UnsupportedMediaTypeException
                 * @throws HTTP.PreconditionFailedException 
                 */
                public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) throws
                HTTP.UnauthorizedException,
                HTTP.InternalServerErrorException,
                HTTP.GoneException,
                HTTP.NotFoundException,
                HTTP.BadRequestException,
                HTTP.ConflictException,
                HTTP.InsufficientStorageException,
                HTTP.LockedException,
                HTTP.MethodNotAllowedException,
                HTTP.ForbiddenException,
                HTTP.UnsupportedMediaTypeException,
                HTTP.PreconditionFailedException;
            }
        }
    }
    
    /**
     * Implementations of this interface represent an <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.html">HTTP 1.1 (RFC 2616)</a> resource.
     * 
     * HTTP resources are network data objects or services that can be identified by a URI,
     * as defined in <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.2">&sect;3.2</a>.
     * Resources may be available in multiple representations (e.g. multiple languages, data formats, size, and resolutions) or vary in other ways. 
     * <p>
     * Resources represented by this interface are abstract in the sense that the resource may or may not actually exist.
     * Subsequent operations using the resource may require the resource to exist (or not exist) and as a result
     * will signal appropriate success or errors (See {@link HTTP.Exception} and {@link HTTP.Response.Status}).
     * </p>
     * <p>
     * An HTTP resource provides methods that implement the basic set/get access.
     * For example, a filesystem HTTP.Resource provide mechanisms to determine what methods are supported by the file,
     * the read/write interface to the file, and so forth.
     * </p> 
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Resource {
        /**
         * Get the {@link HTTP.Backend} that contains this resource.
         * 
         * @return the {@link HTTP.Backend} that contains this resource.
         */
        public HTTP.Backend getBackend();

        /**
         *  Get the {@link HTTP.Identity} that this using this resource.
         * 
         * @return the {@link HTTP.Identity} that this using this resource.
         */
        public HTTP.Identity getIdentity();

        /**
         * Get the {@link URI} for this resource.
         * <p>
         * The URI is relative and contains only the path portion of the name.
         * </p>
         * @return the {@link URI} for this resource.
         */
        public URI getURI();

        /**
         * Create this resource.
         * <p>
         * The resource must not already exist.
         * If the resource already exists, this method will throw {@link HTTP.MethodNotAllowedException}.
         * </p>
         * 
         * @param contentType the content-type of the resource.  (See {@link InternetMediaType}).
         * 
         * @return this resource.
         * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.LockedException The parent collection was locked.
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ForbiddenException the request is valid but cannot be performed.
         */
        public HTTP.Resource create(InternetMediaType contentType) throws
        HTTP.BadRequestException,
        HTTP.ConflictException,
        HTTP.GoneException,
        HTTP.InternalServerErrorException,
        HTTP.InsufficientStorageException,
        HTTP.LockedException,
        HTTP.MethodNotAllowedException,
        HTTP.NotFoundException,
        HTTP.UnauthorizedException,
        HTTP.ForbiddenException;
        
        /**
         * Check for the existence of this resource.
         * 
         * This method may also have side effects to permit caching of results, etc. 
         * 
         * @return {@code true} if this resource exists, {@code false} otherwise.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public boolean exists() throws HTTP.InternalServerErrorException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get the {@link HTTP.Request.Method} methods that are allowed for this resource.
         * 
         * @return a Collection<HTTP.Request.Method> containing the methods that are allowed for this resource.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource named by the given {@code URI} once was, but is no longer available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public Collection<HTTP.Request.Method> getAccessAllowed()
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException;

        /**
         * Get the content-type (from Section 14.17 of [RFC2616])of this resource as it would be returned by a GET request without accept headers.
         * 
         * @return the {@link InternetMediaType} of this resource.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public InternetMediaType getContentType()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;

        /**
         * Get the number of octets of data available from this resource.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found, or the property does not exist for this resource.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public long getContentLength() 
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Delete this resource.
         * 
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         * @throws HTTP.BadRequestException 
         */
        public void delete()
        throws HTTP.MethodNotAllowedException, HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.NotFoundException,
               HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.BadRequestException;
        

        /**
         * Get an {@link InputStream} instance from which subsequent input operations will read bytes from this resource.
         * 
         * @return an {@link InputStream} from which subsequent input operations will read bytes from this resource.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
         */
        public InputStream asInputStream()
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.MethodNotAllowedException, HTTP.NotFoundException, HTTP.UnauthorizedException,
               HTTP.ConflictException, BadRequestException;
        
        /**
         * Get an {@link OutputStream} instance to which subsequent output operations will write bytes to this resource.
         * 
         * @return an {@link OutputStream} instance to which subsequent output operations will write bytes to this resource.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         */
        public OutputStream asOutputStream()
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.MethodNotAllowedException, HTTP.InsufficientStorageException,
               HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.BadRequestException, HTTP.LockedException, HTTP.ForbiddenException;
        
        /**
         * A container for the state of a resource.
         * 
         */
        public static class State {
            private URI resourceURI;
            private EntityTag entityTag;
            private Set<State.Token> stateTags;
            
            public State(URI resourceURI, EntityTag entityTag, Set<State.Token> stateTags) {
                this.resourceURI = resourceURI;
                this.entityTag = entityTag;
                this.stateTags = stateTags;                
            }
            
            public State(URI resourceURI, EntityTag entityTag) {
                this(resourceURI, entityTag, new HashSet<State.Token>());
            }
            
            public State(URI resourceURI) {
                this(resourceURI, null);
            }
            
            public URI getURI() {
                return this.resourceURI;
            }

            public void add(HTTP.Resource.State.Token token) {
                this.stateTags.add(token);
            }

            public EntityTag getEntityTag() {
                return this.entityTag;
            }

            /**
             * 
             * @return
             */
            public Set<State.Token> getStateTokens() {
                return this.stateTags;                
            }
            
            public String toString() {
                StringBuilder result = new StringBuilder(this.resourceURI.toASCIIString()).append(" ");
                if (this.entityTag != null)
                    result.append(this.entityTag.toString()).append(" ");

                String space = "";
                for (State.Token token : this.stateTags) {
                    result.append(space).append(token.toString());
                    space = " ";
                }
                
                return result.toString();
            }
            
            /**
             * 
             *
             */
            public interface Token extends HTTP.Message.Header.If.Operand {
                
            }
            
            /*
             * Coded-URL   = "<" absolute-URI ">" 
             *   ; No linear whitespace (LWS) allowed in Coded-URL
             *   ; absolute-URI defined in RFC 3986, Section 4.3
             */
            public static class CodedURL implements HTTP.Resource.State.Token {
                private URI absoluteURI;
                
                /**
                 * Construct a new CodedURL from a string representation containing the coded-url <code>"&lt;" uri "&gt;"</code> 
                 * 
                 * @param codedURL
                 * @throws HTTP.BadRequestException
                 */
                public CodedURL(String codedURL) throws HTTP.BadRequestException {
                    try {
                    codedURL = codedURL.trim();
                        this.absoluteURI = new URI(codedURL.substring(1, codedURL.length()-1));
                    } catch (URISyntaxException e) {
                        throw new HTTP.BadRequestException(String.format("Bad Coded-URL: '%s'", codedURL));
                    }
                }
                
                public CodedURL(URI codedURL) {
                    this.absoluteURI = codedURL;
                }
                
                public URI getCodedURL() {
                    return this.absoluteURI;
                }

                public boolean match(HTTP.Message.Header.If.Operand other) {
                    if (other instanceof CodedURL) {
                        return this.absoluteURI.equals(((CodedURL) other).absoluteURI);
                    }
                    return false;
                }
                
                @Override
                public int hashCode() {
                    return this.absoluteURI.hashCode();
                }

                @Override
                public boolean equals(Object other) {
                    if (other == null)
                        return false;
                    if (this == other)
                        return true;
                    // XXX Reorganise this so this method doesn't have to understand HTTP.Message.Header.If.ConditionTerm                    
                    if (other instanceof HTTP.Message.Header.If.ConditionTerm) {
                        HTTP.Message.Header.If.ConditionTerm o = (HTTP.Message.Header.If.ConditionTerm) other;
                        HTTP.Message.Header.If.Operand operand = o.getOperand();
                        if (operand instanceof CodedURL) {
                            return this.equals(operand);
                        }
                        return false;                        
                    }
                    if (other instanceof URI) {
                        return this.absoluteURI.equals(other);
                    }
                    if (other instanceof CodedURL) {
                        return this.absoluteURI.equals(((CodedURL) other).absoluteURI);
                    }
                    
                    return false;                
                }
                
                @Override
                public String toString() {
                    return "<" + this.absoluteURI + ">";
                }
            }
            
            /*
             * From  Section 3.11 of [RFC2616]:
             * entity-tag = [ weak ] opaque-tag
             * weak       = "W/"
             * opaque-tag = quoted-string
             */
            public static class EntityTag implements HTTP.EntityTag {
                private boolean weak;
                private String tag;
                
                public EntityTag(String entityTag) {
                    if (entityTag.startsWith("W/")) {
                        this.weak = true;
                        entityTag = entityTag.substring(2);
                    }
                    this.tag = entityTag;                
                }
                
                public boolean match(HTTP.Message.Header.If.Operand other) {
                    if (other instanceof EntityTag) {
                        EntityTag entityTag = (EntityTag) other; 
                        if (this.weak != entityTag.weak)
                            return false;
                       return this.tag.equals(entityTag.tag);
                    }
                    return false;
                }
                
                @Override
                public int hashCode() {
                    return this.tag.hashCode();
                }

                @Override
                public boolean equals(Object other) {
                    if (other == null)
                        return false;
                    if (this == other)
                        return true;
                    if (other instanceof EntityTag) {
                        EntityTag o = (EntityTag) other;
                        return this.weak == o.weak && this.tag.equals(o.tag);
                    }
                    
                    return false;
                }
                
                public String toString() {
                    return String.format("[%s%s]", this.weak ? "W/" : "", this.tag);
                }
            }

            public void add(HTTP.Resource.State.EntityTag entityTag) {
               this.entityTag = entityTag;                
            }
        }

        public HTTP.Resource.State getResourceState()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        public HTTP.Response post(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
    }
    
    /**
     * An HTTP Response is transmitted from an {@link HTTP.Server} to the client.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     *
     */
    public interface Response {

        /**
         * Get the {@link HTTP.Message} contained in this response.
         * 
         * @return the {@link HTTP.Message} contained in this response.
         */
        public HTTP.Message getMessage();
        
        /**
         * Get the {@link HTTP.Response.Status} contained in this response.
         * 
         * @return the {@link HTTP.Response.Status} contained in this response.
         */
        public HTTP.Response.Status getStatus();

        /**
         * Get the HTTP Version contained in this response.
         * 
         * @return the HTTP Version contained in this response.
         */
        public String getVersion();
        
        /**
         * Write the head of this response as a well formed HTTP response message.
         * 
         * @param out
         * @return
         * @throws IOException
         */
        public DataOutputStream writeHeadTo(DataOutputStream out) throws IOException;
        
        // XXX This may go away.
        public long writeTo(DataOutputStream out) throws IOException;
        
        
        /**
         * <p>From <cite><a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1">RFC 2616 &sect;6.1.1 Status Code and Reason Phrase</a></cite></p>
         * <blockquote>
         * The Status-Code element is a 3-digit integer result code of the attempt to understand and satisfy the request.
         * These codes are fully defined in section 10.
         * The Reason-Phrase is intended to give a short textual description of the Status-Code.
         * The Status-Code is intended for use by automata and the Reason-Phrase is intended for the human user.
         * The client is not required to examine or display the Reason- Phrase.
         * </blockquote>
         * <blockquote>
         * The first digit of the Status-Code defines the class of response.
         * The last two digits do not have any categorization role. There are 5 values for the first digit:
         * <ul>
         * <li>1xx: Informational - Request received, continuing process</li>
         * <li>2xx: Success - The action was successfully received, understood, and accepted</li>
         * <li>3xx: Redirection - Further action must be taken in order to complete the request</li>
         * <li>4xx: Client Error - The request contains bad syntax or cannot be fulfilled</li>
         * <li>5xx: Server Error - The server failed to fulfill an apparently valid request</li>
         * </ul>
         * </blockquote>
         * <p>
         * The descriptions below are taken (sometimes abbreviated) from <a href="http://en.wikipedia.org/wiki/HTTP_status">Wikipedia List of HTTP status codes</a>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public enum Status {
            /**
             * The server has received the request headers,
             * and that the client should proceed to send the request body (in case of a request which needs to be sent; for example, a POST request).
             */
            CONTINUE("CONTINUE", 100),
            SWITCHING_PROTOCOLS("SWITCHING_PROTOCOLS", 101),
            
            OK("OK", 200),
            CREATED("Created", 201),
            ACCEPTED("Accepted", 202),
            NON_AUTHORITATIVE_INFORMATION("Non-Authoritative Information", 203),
            NO_CONTENT("No Content", 204),
            RESET_CONTENT("Reset Content", 205),
            PARTIAL_CONTENT("Partial Content", 206),
            /**
             * Indicates that there were multiple status values generated as a result of the operation
             * for resources other than the resource identified in the {@link HTTP.Request} header.
             * <p>
             * The status values are encoded as XML data in the body of the {@link HTTP.Response}.
             * </p>
             */
            MULTI_STATUS("Multi Status", 207), // WebDAV extension
            
            MULTIPLE_CHOICES("Multiple Choices", 300),
            MOVE_PERMANENTLY("Moved Permanently", 301),
            FOUND("Found", 302),
            SEE_OTHER("See Other", 303),
            NOT_MODIFIED("Not Modified", 304),
            USE_PROXY("Use Proxy", 305),
            UNUSED_306("Unused 306", 306),
            TEMPORARY_REDIRECT("Temporary Redirect", 307),

            BAD_REQUEST("Bad Request", 400),
            UNAUTHORIZED("Unauthorized", 401),
            PAYMENT_REQUIRED("Payment Required", 402),
            FORBIDDEN("Forbidden", 403),
            NOT_FOUND("Not Found", 404),
            METHOD_NOT_ALLOWED("Method Not Allowed", 405),
            NOT_ACCEPTABLE("Not Acceptable", 406),
            PROXY_AUTHENTICATION_REQUIRED("Proxy Authentication Required", 407),
            REQUEST_TIMEOUT("Request Timeout", 408),
            CONFLICT("Conflict", 409),
            GONE("Gone", 410),
            LENGTH_REQUIRED("Length Required", 411),
            
            /**
             * From <cite><a href="http://webdav.org/specs/rfc4918.html#rfc.section.12.1">RFC 4918 12.1 412 Precondition Failed</a></cite>
             * 
             * <blockquote>
             * Any request can contain a conditional header defined in HTTP (If-Match, If-Modified-Since, etc.)
             * or the "If" or "Overwrite" conditional headers defined in this specification.
             * If the server evaluates a conditional header, and if that condition fails to hold, then this error code MUST be returned.
             * On the other hand, if the client did not include a conditional header in the request, then the server MUST NOT use this status code.
             * </blockquote>
             */
            PRECONDITION_FAILED("Precondition Failed", 412),
            REQUEST_ENTITY_TOO_LARGE("Request Entity Too Large", 413),
            REQUEST_URI_TOO_LONG("Request-URI Too Long", 414),
            UNSUPPORTED_MEDIA_TYPE("Unsupported Media Type", 415),
            REQUESTED_RANGE_NOT_SATISFIABLE("Requested Range Not Satisfiable", 416),
            EXPECTATION_FAILED("Expectation Failed",  417),
            UNPROCESSABLE_ENTITY("Unprocessable Entity",  422),

            /**
             * From <cite><a href="http://webdav.org/specs/rfc4918.html#STATUS_423">RFC 4918 11.3 423 Locked</a></cite>
             * 
             * <blockquote>
             * The 423 (Locked) status code means the source or destination resource of a method is locked.
             * This response SHOULD contain an appropriate precondition or postcondition code, such as 'lock-token-submitted' or 'no-conflicting-lock'.
             * </blockquote>
             * See also {@link #PRECONDITION_FAILED}.
             */
            LOCKED("Locked",  423), // WebDAV extension
            FAILED_DEPENDENCY("Failed Dependency",  424), // WebDAV extension
            
            /** 
             * A generic error message, given when no more specific message is suitable.
             */
            INTERNAL_SERVER_ERROR("Internal Server Error", 500),
            /**
             * The server either does not recognise the request method, or it lacks the ability to fulfill the request.
             */
            NOT_IMPLEMENTED("Not Implemented",  501),
            /**
             *  The server was acting as a gateway or proxy and received an invalid response from the downstream server.
             */
            BAD_GATEWAY("Bad Gateway",  502),
            /**
             * The server is currently unavailable (because it is overloaded or down for maintenance). Generally, this is a temporary state.
             */
            SERVICE_UNAVAILABLE("Service Unavailable", 503),
            /**
             * The server was acting as a gateway or proxy and did not receive a timely request from the downstream server.
             */
            GATEWAY_TIMEOUT("Gateway Timeout",  504),
            /**
             * The server does not support the HTTP protocol version used in the request.
             */
            HTTP_VERSION_NOT_SUPPORTED("HTTP Version Not Supported",  505),
            /**
             * Transparent content negotiation for the request, results in a circular reference.
             * Also Negotiates (RFC 2295)
             */
            VARIANT("Variant", 506),
            /**
             * The method could not be performed on the resource because the server is unable to store the representation needed.
             */
            INSUFFICIENT_STORAGE("Insufficient Storage",  507),
            /**
             * This status code, while used by many servers, is not specified in any RFCs.
             */
            BANDWIDTH_EXCEEDED("Bandwidth exceeeded", 509),
            /**
             * Further extensions to the request are required for the server to fulfill it. 
             */
            NOT_EXTENDED("Not Extended", 510)
            ;
            
            public static int INFORMATIONAL = 1;
            public static int SUCCESSFUL = 2;
            public static int REDIRECTION = 3;
            public static int CLIENT_ERROR = 4;
            public static int SERVER_ERROR = 5;

            
            private String version;
            private int code;
            private String reasonPhrase;
            
            private Status(String version, int code, String reasonPhrase) {
                this.version = version;
                this.code = code;
                this.reasonPhrase = reasonPhrase;
            }
            
            private Status(String reasonPhrase, int code) {
                this("HTTP/1.1", code, reasonPhrase);
            }
            
//            public Status newStatus(String statusLine) {
//                String[] tokens = statusLine.split(" ", 3);
//                String version = tokens[0];
//                int code = Integer.parseInt(tokens[1]);
//                String reasonPhrase = tokens[2];
//                
//                Status existingStatus = Status.fromEncoding(code);
//                if (existingStatus != null)
//                    return existingStatus;
//                return new Status(version, code, reasonPhrase);
//            }
            
            public String getReasonPhrase() {
                return this.reasonPhrase;
            }
            
            public int getStatusCode() {
                return this.code;
            }
            
            public String toString() {
                return String.format("%d %s", this.code, this.reasonPhrase);
            }
            
            public long getStatusClass() {
                return (this.code / 100);
            }
            
            public String getVersion() {
                return this.version;
            }
            
            public boolean isSuccessful() {
                return (this.getStatusClass() == HTTP.Response.Status.SUCCESSFUL);
            }
            
            public static Status fromStatusCode(int code) {
                for (Status s: Status.values()) {
                    if (code == s.code)
                        return s;
                }
                return null;
            }
        }
    }
    
    /**
     * Implementations of this interface represent an HTTP message.
     * (See also, {@link HTTP.Request} and {@link HTTP.Response}).
     * <p>
     * An {@code HTTP.Message} consists of a set of headers and arbitrary data comprising the body.
     * The body length, format and encoding are specified by headers in the message.
     * </p>
     * <p>
     * The body length, in octets, is specified by the {@code Content-Length} header.
     * If the {@code Content-Length} header is missing then the body length is indeterminate and the message
     * must contain a {@code Connection: close} header, which signals the recipient to read from the input stream until EOF.
     * </p>
     * <p>
     * The body format is specified by the {@code Content-Type} header.
     * If this header is missing, the body is considered {@code Content-Type: application/octet-stream}.
     * </p>
     * <p>
     * If the body contains structured data according to the rules specified by the header {@code Content-Type: multipart/form-data}
     * specifications, then that data is further parsed into the constituent parts, each part another instance of {@link HTTP.Message}.
     * </p>
     * Confer: <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html">http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html</a>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Message {

        /**
         * Produce a Map containing all of the {@link HTTP.Message.Header} instances in this Message.
         * <p>
         * Implementations may also generate, correct, and validate headers in this Message before the Map is returned.
         * </p>
         * @return a Map containing all of the {@link HTTP.Message.Header} instances in this Message.
         */
        public Map<String,HTTP.Message.Header> getHeaders();

        /**
         * Add the given {@link HTTP.Message.Header} instances to this message.
         */
        public HTTP.Message addHeader(HTTP.Message.Header...header);
        
//        public <C extends HTTP.Message.Header> C getHeader(Class<? extends C> klasse);

        /**
         * Get the {@link HTTP.Message.Header} {@code name} from this {@code HTTP.Message}.
         * <p>
         * If the header is not present in this message, {@code null} is returned.
         * </p>
         * @param name The String value of the name of the requested {@code HTTP.Header}.
         * @return The {@link HTTP.Message.Header} {@code name} from this {@code HTTP.Message}.
         */
        public HTTP.Message.Header getHeader(String name);

        public HTTP.Message.Header.ContentType getContentType();
        
        /**
         * Get the body of this message.
         * 
         * @return
         */
        public HTTP.Message.Body getBody();

        public void setBody(HTTP.Message.Body body);
        
        public HTTP.Message.Body.MultiPart.FormData decodeMultiPartFormData() throws IOException, HTTP.BadRequestException;

        // XXX This may go away.
        public long writeTo(OutputStream out) throws IOException;

        // XXX This may go away.
        public long writeHeadTo(OutputStream out) throws IOException;
        
        /**
         * From <cite><a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">RFC 2616 &sect;4.2 Message Headers</a></cite>
         * <blockquote>
         * HTTP header fields, which include general-header (section 4.5), request-header (section 5.3), response-header (section 6.2),
         * and entity-header (section 7.1) fields, follow the same generic format as that given in Section 3.1 of RFC 822 [9].
         * Each header field consists of a name followed by a colon (":") and the field value. Field names are case-insensitive.
         * The field value MAY be preceded by any amount of LWS, though a single SP is preferred.
         * Header fields can be extended over multiple lines by preceding each extra line with at least one SP or HT.
         * </blockquote>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface Header {

            public String getName();

            /**
             * Get the value of this header's field-value.
             * <p>
             * Classes implementing this interface or sub-interfaces for specific HTTP headers,
             * must be prepared to recompute the field value based on the constituent parts
             * of the header.
             * </p>
             */
            public String getFieldValue();

            /**
             * Set the field-value for this header.
             * <p>
             * No validation of the String is performed.
             * </p>
             * @param value the new field-value of this header. 
             */
            public void setFieldValue(String value);
            
            /**
             * Append additional field-value data to this header.
             *
             * @param value
             */
            public void append(String value);
            

            /**
             * Return {@code true} if this header is permitted to be specified multiple times in the {@link HTTP.Message}.
             * If {@code true}, then multiple adds of this header will be included in the message.
             * If {@code false}, then the last added instance of this header will replace the previous one.
             */
            public boolean multipleHeadersAllowed();
            
            // XXX This may go away.
            public long writeTo(OutputStream out) throws IOException;
            
            public interface Parameter {

                public String getAttribute();
                
                public String getValue();
            }            
            
            // Manifest constants for all headers.
            public static final String ACCEPT = "Accept";
            public static final String ACCEPTCHARSET = "Accept-Charset";
            public static final String ACCEPTENCODING = "Accept-Encoding";
            public static final String ACCEPTLANGUAGE = "Accept-Language";
            public static final String ACCEPTRANGES = "Accept-Ranges";
            public static final String AGE = "Age";
            public static final String ALLOW = "Allow";
            public static final String AUTHORIZATION = "Authorization";
            public static final String CACHECONTROL = "Cache-Control";
            public static final String CONNECTION = "Connection";
            public static final String CONTENTDISPOSITION = "Content-Disposition";
            public static final String CONTENTENCODING = "Content-Encoding";
            public static final String CONTENTLANGUAGE = "Content-Language";
            public static final String CONTENTLOCATION = "Content-Location";
            public static final String CONTENTMD5 = "Content-MD5";
            public static final String CONTENTRANGE = "Content-Range";
            public static final String CONTENTLENGTH = "Content-Length";
            public static final String CONTENTTYPE = "Content-Type";
            public static final String DATE = "Date";
            public static final String ETAG = "ETag";
            public static final String EXPECT = "Expect";
            public static final String EXPIRES = "Expires";
            public static final String FROM = "From";
            public static final String HOST = "Host";
            public static final String IFMATCH = "If-Match";
            public static final String IFNONEMATCH = "If-None-Match";
            public static final String IFRANGE = "If-Range";
            public static final String IFUNMODIFIEDSINCE = "If-Unmodified-Since";
            public static final String KEEPALIVE = "Keep-Alive";
            public static final String LASTMODIFIED = "Last-Modified";
            public static final String LOCATION = "Location";
            public static final String MAXFORWARDS = "Max-Forwards";
            public static final String PRAGMA = "Pragma";
            public static final String PROXYAUTHENTICATION = "Proxy-Authenticate";
            public static final String PROXYAUTHORIZATION = "Proxy-Authorization";
            public static final String RANGE = "Range";
            public static final String REFERER = "Referer";
            public static final String RETRYAFTER = "Retry-After";
            public static final String SERVER = "Server";
            public static final String TE = "TE";
            public static final String TRAILER = "Trailer";
            public static final String TRANSFERENCODING = "Transfer-Encoding";
            public static final String UPGRADE = "Upgrade";
            public static final String USERAGENT = "User-Agent";
            public static final String VARY = "Vary";
            public static final String VIA = "Via";
            public static final String WARNING = "Warning";
            public static final String WWWAUTHENTICATE = "WWW-Authenticate";

            // WebDAV Headers
            public static final String DAV = "DAV";
            public static final String DEPTH = "Depth";
            public static final String DESTINATION = "Destination";
            public static final String IF = "If";
            public static final String LOCKTOKEN = "Lock-Token";
            public static final String OVERWRITE = "Overwrite";
            public static final String TIMEOUT = "TimeOut";
            public static final String TIMETYPE = "TimeType";
            
            // Microsoft Headers
            public static final String TRANSLATE = "Translate";
            public static final String UNLESSMODIFIEDSINCE = "Unless-Modified-Since";

            // XXX This should use HTTP.Authorise to contain and control the authorization data.
            public interface Authorization extends HTTP.Message.Header {
                /**
                 * Return {@code true} if this header's field value is valid.
                 */
                public boolean valid();

                /**
                 * Get the <em>scheme</em> field-value component of this {@code Authorization} header.
                 * @throws BadRequestException if the field values are incorrectly formatted.
                 */
                public String getScheme() throws HTTP.BadRequestException;

                /**
                 * Get the <em>parameter</em> field-value component of this Authorization header.
                 * @throws BadRequestException if the field values are incorrectly formatted.
                 */
                public String getParameters() throws HTTP.BadRequestException;

                /**
                 * Get the Basic Authentication parameters from this Authentication header.
                 * @return A String array containing the colon separated parts of the Authentication parameters.
                 * @throws BadRequestException if the Authorization scheme is not {@code Basic} or the field values are incorrectly formatted.
                 */
                public String[] getBasicParameters() throws HTTP.BadRequestException;

                public String getBasicUserName() throws HTTP.BadRequestException;

                public String getBasicPassword() throws HTTP.BadRequestException;
                
            }
            
            /**
             * See <a href="http://www.ietf.org/rfc/rfc2183.txt">
             * RFC 2183 - Communicating Presentation Information in Internet Messages: The Content-Disposition Header Field
             * </a>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface ContentDisposition extends HTTP.Message.Header {

                /**
                 * Return the disposition type specified by the {@code Content-Disposition} header.
                 */
                public String getType();

                /**
                 * Return a Map containing the parameters and their values, keyed by the parameter names.
                 */
                public Map<String,HTTP.Message.Header.Parameter> getParameters();

                /**
                 * Return the named parameter.
                 */
                public HTTP.Message.Header.Parameter getParameter(String name);
            }

            /**
             * From <cite><a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.17">RFC 2616 &sect;14.17</a></cite>
             * <blockquote>
             * The Content-Type entity-header field indicates the media type of the entity-body sent to the recipient or,
             * in the case of the HEAD method, the media type that would have been sent had the request been a GET. 
             * </blockquote>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface ContentType extends HTTP.Message.Header {

                public InternetMediaType getType();

                public Map<String,HTTP.Message.Header.Parameter> getParameters();

                public HTTP.Message.Header.Parameter getParameter(String name);
            }
            
            /**
             * <p>
             * An {@code If} header specifies a conditional expression which, if it evaluates to true, signals that a request may be peformed.
             * Otherwise, if the expression evaluates to false, the request fails with a status code signalling the reason for failure
             * (For example, {@link HTTP.Response.Status.PRECONDITION_FAILED} or {@link HTTP.Response.Status.LOCKED}).
             * </p>
             * <p>
             * In short, the conditional expression {@link Conditional} is a map, containing one or more mappings each
             * one a URI mapped to a <em>condition set</em>.
             * The {@code If} header syntax permits a URI to be left unspecified as shorthand notation for the target URI of the request.
             * </p>
             * <p>
             * Each condition set contains one or more conditions.
             * For a condition set to evaluate to true, only one of the conditions in the set must evaluate to true.
             * </p>
             * <p>
             * Each condition contains one or more <em>state-tokens</em> as terms.
             * For a condition to evaluate to true, all of the state-tokens must match the corresponding states of the resource identified by the URI.
             * The syntax of the {@code If} header permits a state-token to be optionally prefixed by the characters
             * {@code Not} which negates the sense of the match.
             * </p>
             * 
             * From <cite>RFC 4816 <b>&sect;10.4</b></cite>
             * <blockquote>
             * The <code>If</code> request header is intended to have similar functionality to the <code>If-Match</code>
             * header defined in Section 14.24 of [RFC2616].
             * However, the <code>If</code> header handles any state token as well as <code>ETags</code>.
             * A typical example of a state token is a lock token, and lock tokens are the only state tokens defined in this specification.
             * </blockquote>
             * <blockquote>
             * The If header has two distinct purposes:
             * <ul>
             * <li>
             * The first purpose is to make a request conditional by supplying a series of state lists with conditions that match tokens and ETags to a specific resource.
             * If this header is evaluated and all state lists fail, then the request MUST fail with a 412 (Precondition Failed) status.
             * On the other hand, the request can succeed only if one of the described state lists succeeds.
             * The success criteria for state lists and matching functions are defined in Sections 10.4.3 and 10.4.4.
             * </li>
             * <li>
             * Additionally, the mere fact that a state token appears in an If header means that it has been "submitted" with the request.
             * In general, this is used to indicate that the client has knowledge of that state token.
             * The semantics for submitting a state token depend on its type (for lock tokens, please refer to Section 6).
             * </li>
             * </blockquote>
             * <blockquote>
             * Note that these two purposes need to be treated distinctly:
             * a state token counts as being submitted independently of whether the server actually has evaluated the state list it appears in,
             * and also independently of whether or not the condition it expressed was found to be true.
             * </blockquote>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface If extends HTTP.Message.Header {
                public HTTP.Message.Header.If.Conditional getConditional() throws HTTP.BadRequestException;
                
                /**
                 * Get the Collection of state-tokens submitted with this If header.
                 * 
                 * @return the Collection of state-tokens submitted with this If header.
                 * @throws HTTP.BadRequestException
                 */
                public Collection<HTTP.Resource.State.Token> getSubmittedStateTokens() throws HTTP.BadRequestException;
                
                public boolean evaluate(HTTP.Resource...affectedResources)
                throws HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;

                /**
                 * The entire set of conditions specified in an If: header.
                 * 
                 * <table style="border: 1px solid black;">
                 * <tr><td>&lt;uri<sub>1</sub>&gt;</td><td>(</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>...</td><td>)</td><td></td></tr>
                 * <tr><td>&lt;uri<sub>2</sub>&gt;</td><td>(</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>...</td><td>)</td><td>||</td></tr>
                 * <tr><td></td><td>(</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>...</td><td>)</td><td>||</td></tr>
                 * <tr><td></td><td>(</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>...</td><td>)</td><td></td></tr>
                 * <tr><td>&lt;uri<sub>3</sub>&gt;</td><td>(</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>term</td><td>&amp;&amp;</td><td>...</td><td>)</td></tr>
                 * </table>
                 */
                public interface Conditional {
                    /**
                     * Evaluate this {@link Conditional}, returning {@code true} if it contains matching state-tokens and entity-tag
                     * for each resource specified in the given Map {@code resourceStates}.
                     * 
                     * Callers must supply the URI to use for the default resource.
                     * Typically this is the request-URI.
                     * 
                     * @param requestURI
                     * @param resourceStates
                     * @return {@code true} if it contains matching state-tokens and entity-tag for each resource specified in the given Map {@code resourceStates}.
                     */
                    public boolean evaluate(URI requestURI, Map<URI,HTTP.Resource.State> resourceStates);
                    
                    /**
                     * Return the number of {@link ConditionSet} instances in this {@link Conditional}.
                     * 
                     * @return The number of {@link ConditionSet} instances in this {@link Conditional}.
                     */
                    public int size();
                    
                    /**
                     * Get the {@link HTTP.Message.Header.If.ConditionSet} for the named {@link URI} {@code resourceTag}.
                     * 
                     * @param resourceTag
                     * @return
                     */
                    public HTTP.Message.Header.If.ConditionSet getConditionSet(URI resourceTag);

                }
                
                public interface ConditionSet {
                    public Collection<HTTP.Message.Header.If.Condition> getConditions();
                    
                    public void add(HTTP.Message.Header.If.Condition conditions);
                    
                    /**
                     * For a condition set to evaluate to true, only one of the conditions in the set must evaluate to true.
                     * 
                     * @param resourceState
                     * @return
                     */
                    public boolean matches(HTTP.Resource.State resourceState);

                    public boolean evaluate(HTTP.Resource defaultResource)
                    throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
                }

                /**
                 * Programmatic form of If conditional "(" term term term ... ")"
                 * Programmatic form of Resource "(" state token token token etag ")"
                 * 
                 */
                public interface Condition {

                    public void add(HTTP.Message.Header.If.ConditionTerm term);
                    
                    public Collection<HTTP.Message.Header.If.ConditionTerm> getTerms();

                    /**
                     * Get all of the state-tokens in this Condition.
                     * 
                     * @return a Collection containing all of the state-tokens in this Condition.
                     */
                    public Collection<HTTP.Resource.State.Token> getStateTokens();

                    /**
                     * 10.4.4 Matching State Tokens and ETags
                     * <p>
                     * When performing If header processing, the definition of a matching state token or entity tag is as follows:
                     * </p>
                     * <p>
                     * Identifying a resource: The resource is identified by the URI along with the token,
                     * in tagged list production, or by the Request-URI in untagged list production.
                     * </p>
                     * <p>
                     * Matching entity tag: Where the entity tag matches an entity tag associated with the identified resource.
                     * Servers MUST use either the weak or the strong comparison function defined in Section 13.3.3 of [RFC2616].
                     * </p>
                     * <p>
                     * Matching state token: Where there is an exact match between the state token in the If header
                     * and any state token on the identified resource. A lock state token is considered to match if
                     * the resource is anywhere in the scope of the lock.
                     * </p>
                     * @param other
                     * @return
                     */
                    public boolean matches(HTTP.Resource.State resourceState);
                }

                /**
                 * A Condition is comprised of either an entity-tag or a State-token (implementations of the Java interface TokenTag),
                 * and a flag to indicate if the logical sense of a match is to be negated.
                 * <p>
                 * A Condition that consists of a single entity-tag or state-token evaluates to true if the resource matches the described state
                 * (where the individual matching functions are defined below in Section 10.4.4).
                 * Prefixing it with "Not" reverses the result of the evaluation (thus, the "Not" applies only to the subsequent entity-tag or State-token).
                 *</p>
                 * 
                 * @see HTTP.Message.Header.If.ConditionTerm.Term
                 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
                 */
                public interface ConditionTerm {
                    /**
                     * Return true if the logical sense of this condition is negated.
                     * 
                     * @return true if the logical sense of this condition is negated.
                     */
                    public boolean isNegated();
                    
                    /**
                     * Get this ConditionTerm's operand.
                     * 
                     * @return this ConditionTerm's operand.
                     */
                    public HTTP.Message.Header.If.Operand getOperand();
                    
                    /**
                     * Return {@code true} if this Condition matches any of the {@link HTTP.Resource.State.Token} in the collection {@code terms}.
                     * @param terms
                     * @return {@code true} if this Condition matches any of the {@link HTTP.Resource.State.Token} in the collection {@code terms}.
                     */
                    public boolean matches(Collection<HTTP.Resource.State.Token> terms);
                }
                
                /**
                 * Either a State-token or Entity-tag
                 * <p>
                 * Classes implementing this interface are expected to be operands when matching with
                 * {@link ConditionTerm} instances in {@link HTTP.Message.Header.If} headers.
                 * </p>
                 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
                 */
                public interface Operand {
                    public boolean match(Operand other);
                                      
                }
            }

            /**
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface LockToken {
                public URI getCodedURL() throws HTTP.BadRequestException;
            }

            /**
             * From <cite>RFC 4816 <b>&sect;10.6</b></cite>
             * <blockquote>
             * The Overwrite request header specifies whether the server should overwrite a resource mapped to the destination URL during a COPY or MOVE.
             * A value of "F" states that the server must not perform the COPY or MOVE operation if the destination URL does map to a resource.
             * If the overwrite header is not included in a COPY or MOVE request,
             * then the resource MUST treat the request as if it has an overwrite header of value "T".
             * While the Overwrite header appears to duplicate the functionality of using an "If-Match: *" header (see [RFC2616]),
             * If-Match applies only to the Request-URI, and not to the Destination of a COPY or MOVE.
             * </blockquote>
             * <blockquote>
             * If a COPY or MOVE is not performed due to the value of the Overwrite header, the method MUST fail with a 412 (Precondition Failed) status code.
             * The server MUST do authorization checks before checking this or any conditional header.
             * </blockquote>
             * <blockquote>
             * All DAV-compliant resources MUST support the Overwrite header.
             * </blockquote>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface Overwrite extends HTTP.Message.Header {

                public boolean getOverwrite();
            }
            
            /**
             * From <cite><a href="http://webdav.org/specs/rfc4918.html#rfc.section.10.7">RFC 4816 <b>&sect;10.7</b><a/></cite>
             * <blockquote>
             * Clients MAY include Timeout request headers in their LOCK requests.
             * However, the server is not required to honor or even consider these requests.
             * Clients MUST NOT submit a Timeout request header with any method other than a LOCK method.
             * 
             * The "Second" TimeType specifies the number of seconds that will elapse between granting of the lock at the server,
             * and the automatic removal of the lock.
             * The timeout value for TimeType "Second" MUST NOT be greater than 2^32-1.
             * </blockquote>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface Timeout extends HTTP.Message.Header {
                public int getTimeOut();
            }

            /**
             * 
             * From <cite><a href="http://webdav.org/specs/rfc4918.html#HEADER_Depth">RFC 4816 <b>&sect;10.2</b><a/></cite>
             * <blockquote>
             * The Depth request header is used with methods executed on resources that could potentially have internal members to indicate
             * whether the method is to be applied only to the resource ("Depth: 0"), to the resource and its internal members only ("Depth: 1"),
             * or the resource and all its members ("Depth: infinity").
             * </blockquote>
             * <blockquote>
             * The Depth header is only supported if a method's definition explicitly provides for such support.
             * </blockquote>
             * <blockquote>
             * The following rules are the default behavior for any method that supports the Depth header.
             * A method may override these defaults by defining different behavior in its definition.
             * </blockquote>
             * <blockquote>
             * Methods that support the Depth header may choose not to support all of the header's values and may define, on a case-by-case basis,
             * the behavior of the method if a Depth header is not present.
             * For example, the MOVE method only supports "Depth: infinity", and if a Depth header is not present,
             * it will act as if a "Depth: infinity" header had been applied.
             * </blockquote>
             * <blockquote>
             * Clients MUST NOT rely upon methods executing on members of their hierarchies in any particular order or on the execution being atomic
             * unless the particular method explicitly provides such guarantees.
             * </blockquote>
             * <blockquote>
             * Upon execution, a method with a Depth header will perform as much of its assigned task as possible and then return a response specifying
             * what it was able to accomplish and what it failed to do.
             * </blockquote>
             * <blockquote>
             * So, for example, an attempt to COPY a hierarchy may result in some of the members being copied and some not.
             * </blockquote>
             * <blockquote>
             * By default, the Depth header does not interact with other headers. That is, each header on a request with a Depth header MUST
             * be applied only to the Request-URI if it applies to any resource, unless specific Depth behavior is defined for that header.
             * </blockquote>
             * <blockquote>
             * If a source or destination resource within the scope of the Depth header is locked in such a way as to prevent the successful
             * execution of the method, then the lock token for that resource MUST be submitted with the request in the If request header.
             * </blockquote>
             * <blockquote>
             * The Depth header only specifies the behavior of the method with regards to internal members.
             * If a resource does not have internal members, then the Depth header MUST be ignored.
             * </blockquote>
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             *
             */
            public interface Depth extends HTTP.Message.Header {
                public static enum Level {
                    /**
                     * Corresponds to the WebDAV Depth header of 0 which signals that the
                     * operation applies only to the named resource.
                     */
                    ONLY {
                        public String toString() {
                            return "0";
                        }
                    },
                    /**
                     * Corresponds to the WebDAV Depth header of 1 which signals that the
                     * operation applies to the named resource and its internal members.
                     */
                    ALL {
                        public String toString() {
                            return "1";
                        }
                    },
                    /**
                     * Corresponds to the WebDAV Depth header of 'infinity' which signals
                     * that the operation applies to the named resource and all of its members.
                     */
                    INFINITY {
                        public String toString() {
                            return "infinity";
                        }
                    }
                };

                /**
                 * Get the {@link HTTP.Message.Header.Depth.Level} of this Depth header.
                 * 
                 * @return The Depth.Level of this Depth header.
                 * @throws HTTP.BadRequestException if the Depth header is malformed.
                 */
                public HTTP.Message.Header.Depth.Level getLevel() throws HTTP.BadRequestException;
            }
            
            /**
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface Destination extends HTTP.Message.Header {
                /**
                 * 
                 * @return
                 * @throws HTTP.BadRequestException if the header could not be parsed due to malformed syntax.
                 */
                public URI getURI() throws HTTP.BadRequestException;
            }

            /**
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public interface ETag extends HTTP.Message.Header {
                /**
                 * 
                 * @return this ETag header's EntityTag.
                 */
                public HTTP.EntityTag getEntityTag();                
            }
        }
        
        /**
         * <cite>RFC 2616, <b>&sect;4.3</b></cite>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface Body {
            /**
             * Get the {@link HTTP.Message.Header.ContentType} of this body's data.
             */
            public HTTP.Message.Header.ContentType getContentType();
            
            /**
             * Set the {@link HTTP.Message.Header.ContentType} of this body's data.
             */
            public void setContentType(HTTP.Message.Header.ContentType type);

            /**
             * Get the length of the body as the number of 8-bit octets.
             * <p>
             * If the length is not known, this value will be {@code -1}.
             * </p>
             */
            public long contentLength();

            /**
             * Get an {@link InputStream} instance from which the data in this body may be read.
             */
            public InputStream toInputStream();
            
            public long writeTo(OutputStream out) throws IOException;
            
            public interface MultiPart extends HTTP.Message.Body, Iterable<HTTP.Message> {

                public interface FormData extends Serializable, HTTP.Message.Body, Map<String,HTTP.Message> {
                
                }
            }

            /**
             * Set the content length for this body.
             * <p>
             * Normally, the content-length is set by the constructor of classes implementing this interface,
             * or dynamically base on internal computation.
             * </p>
             */
            public void setContentLength(long length);
        }        
    }
    
    
    /**
     * Signals that the request could not be understood by the server due to malformed syntax
     * From <cite>RFC 2616, <b>&sect;10.5.3</b></cite>
     * <blockquote>
     * The server, while acting as a gateway or proxy, received an invalid response from the upstream server it accessed in attempting to fulfill the request.
     * </blockquote>
     * Additionally, WebDAV <cite>RFC 4918 <b>&sect;9.8.5</b></cite> specifies
     * <blockquote>
     * 502 (Bad Gateway) - This may occur when the destination is on another server, repository, or URL namespace.
     * Either the source namespace does not support copying to the destination namespace, or the destination namespace refuses to accept the resource.
     * The client may wish to try GET/PUT and PROPFIND/PROPPATCH instead.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class BadGatewayException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;
        
        public BadGatewayException() {
            super(HTTP.Response.Status.BAD_GATEWAY);
        }

        public BadGatewayException(String message) {
            super(HTTP.Response.Status.BAD_GATEWAY, message);
        }

        public BadGatewayException(String message, Throwable cause) {
            super(HTTP.Response.Status.BAD_GATEWAY, message, cause);
        }

        public BadGatewayException(Throwable cause) {
            super(HTTP.Response.Status.BAD_GATEWAY, cause);
        }
    }
    
    /**
     * Signals that the request could not be understood by the server due to malformed syntax.
     * From <cite>RFC 2616, <b>&sect;10.4.1</b></cite>:
     * <blockquote>
     * The request could not be understood by the server due to malformed syntax. The client SHOULD NOT repeat the request without modifications.
     * </blockquote> 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class BadRequestException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;
        
        public BadRequestException() {
            super(HTTP.Response.Status.BAD_REQUEST);
        }

        public BadRequestException(String message) {
            super(HTTP.Response.Status.BAD_REQUEST, message);
        }

        public BadRequestException(String message, Throwable cause) {
            super(HTTP.Response.Status.BAD_REQUEST, message, cause);
        }

        public BadRequestException(Throwable cause) {
            super(HTTP.Response.Status.BAD_REQUEST, cause);
        }
        
        public BadRequestException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.BAD_REQUEST, uri, cause);
        }

        public BadRequestException(URI uri, String message) {
            super(HTTP.Response.Status.BAD_REQUEST, uri, message);
        }
    }

    /**
     * Signals that an conflict occurred while trying to complete the request method.
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.10</b></cite>:
     * </p>
     * <blockquote>
     * The request could not be completed due to a conflict with the current state of the resource.
     * This code is only allowed in situations where it is expected that the user might be able to resolve the conflict and resubmit the request.
     * The response body SHOULD include enough information for the user to recognize the source of the conflict.
     * Ideally, the response entity would include enough information for the user or user agent to fix the problem;
     * however, that might not be possible and is not required.
     * </blockquote>
     * <blockquote>
     * Conflicts are most likely to occur in response to a PUT request.
     * For example, if versioning were being used and the entity being PUT included changes to a resource which
     * conflict with those made by an earlier (third-party) request, the server might use the 409 response to
     * indicate that it can't complete the request. In this case, the response entity would likely contain a list
     * of the differences between the two versions in a format defined by the response Content-Type. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class ConflictException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public ConflictException() {
            super(HTTP.Response.Status.CONFLICT);
        }

        public ConflictException(String message) {
            super(HTTP.Response.Status.CONFLICT, message);
        }
        
        public ConflictException(Throwable reason) {
            super(HTTP.Response.Status.CONFLICT, reason);
        }

        public ConflictException(String message, Throwable cause) {
            super(HTTP.Response.Status.CONFLICT, message, cause);
        }

        public ConflictException(URI uri, String message) {
            super(HTTP.Response.Status.CONFLICT, uri, message);
        }
        
        public ConflictException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.CONFLICT, uri, cause);
        }
        public ConflictException(URI uri, String message, Throwable cause) {
            super(HTTP.Response.Status.CONFLICT, uri, message, cause);
        }
    }
    
    /**
     * Signals that an HTTP.Request will not be fulfilled.
     * <p>
     * Included in instances of this class are the authentication type and realm required.
     * See RFC 2617.
     * </p>
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.4</b></cite>:
     * <blockquote>
     * The server understood the request, but is refusing to fulfill it.
     * Authorization will not help and the request SHOULD NOT be repeated.
     * If the request method was not HEAD and the server wishes to make public why the request has not been fulfilled,
     * it SHOULD describe the reason for the refusal in the entity.
     * If the server does not wish to make this information available to the client, the status code 404 (Not Found) can be used instead. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class ForbiddenException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public ForbiddenException() {
            super(HTTP.Response.Status.FORBIDDEN);
        }

        public ForbiddenException(Throwable reason) {
            super(HTTP.Response.Status.FORBIDDEN, reason);
        }

        public ForbiddenException(URI uri, String message) {
            super(HTTP.Response.Status.FORBIDDEN, uri, message);
        }

        public ForbiddenException(URI uri, String message, Throwable cause) {
            super(HTTP.Response.Status.FORBIDDEN, uri, message, cause);
        }

        public ForbiddenException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.FORBIDDEN, uri, cause);
        }
    }


    public static class ExpectationFailedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public ExpectationFailedException() {
            super(HTTP.Response.Status.EXPECTATION_FAILED);
        }
        public ExpectationFailedException(Throwable reason) {
            super(HTTP.Response.Status.EXPECTATION_FAILED, reason);
        }
         public ExpectationFailedException(String message, Throwable cause) {
             super(HTTP.Response.Status.EXPECTATION_FAILED, message, cause);
         }

         public ExpectationFailedException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.EXPECTATION_FAILED, uri, cause);
        }
    }


    public static class GatewayTimeoutException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public GatewayTimeoutException() {
            super(HTTP.Response.Status.GATEWAY_TIMEOUT);
        }
        public GatewayTimeoutException(Throwable reason) {
            super(HTTP.Response.Status.GATEWAY_TIMEOUT, reason);
        }
         public GatewayTimeoutException(String message, Throwable cause) {
             super(HTTP.Response.Status.GATEWAY_TIMEOUT, message, cause);
         }

         public GatewayTimeoutException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.GATEWAY_TIMEOUT, uri, cause);
        }
    }


    public static class HTTPVersionNotSupportedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public HTTPVersionNotSupportedException() {
            super(HTTP.Response.Status.HTTP_VERSION_NOT_SUPPORTED);
        }
        public HTTPVersionNotSupportedException(Throwable reason) {
            super(HTTP.Response.Status.HTTP_VERSION_NOT_SUPPORTED, reason);
        }
         public HTTPVersionNotSupportedException(String message, Throwable cause) {
             super(HTTP.Response.Status.HTTP_VERSION_NOT_SUPPORTED, message, cause);
         }

         public HTTPVersionNotSupportedException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.HTTP_VERSION_NOT_SUPPORTED, uri, cause);
        }
    }


    public static class LengthRequiredException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public LengthRequiredException() {
            super(HTTP.Response.Status.LENGTH_REQUIRED);
        }
        public LengthRequiredException(Throwable reason) {
            super(HTTP.Response.Status.LENGTH_REQUIRED, reason);
        }
         public LengthRequiredException(String message, Throwable cause) {
             super(HTTP.Response.Status.LENGTH_REQUIRED, message, cause);
         }

         public LengthRequiredException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.LENGTH_REQUIRED, uri, cause);
        }
    }

    /**
     * From <cite>RFC 2616, <b>&sect;10.4.4</b></cite>:
     * <blockquote>
     * The resource identified by the request is only capable of generating response entities which have content characteristics not acceptable according
     * to the accept headers sent in the request.
     * <p>
     * Unless it was a HEAD request, the response SHOULD include an entity containing a list of available entity characteristics and location(s)
     * from which the user or user agent can choose the one most appropriate.
     * The entity format is specified by the media type given in the Content-Type header field.
     * Depending upon the format and the capabilities of the user agent, selection of the most appropriate choice MAY be performed automatically.
     * However, this specification does not define any standard for such automatic selection.
     * </p>
     * <p>
     * <tt>Note: HTTP/1.1 servers are allowed to return responses which are
     * not acceptable according to the accept headers sent in the
     * request. In some cases, this may even be preferable to sending a
     * 406 response. User agents are encouraged to inspect the headers of
     * an incoming response to determine if it is acceptable.
     * </tt></p>
     * <p>
     * If the response could be unacceptable, a user agent SHOULD temporarily stop receipt of more data and query the user for a decision on further actions.
     * </p>
     * 
     *
     */
    public static class NotAcceptableException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public NotAcceptableException() {
            super(HTTP.Response.Status.NOT_ACCEPTABLE);
        }
        public NotAcceptableException(Throwable reason) {
            super(HTTP.Response.Status.NOT_ACCEPTABLE, reason);
        }
         public NotAcceptableException(String message, Throwable cause) {
             super(HTTP.Response.Status.NOT_ACCEPTABLE, message, cause);
         }

         public NotAcceptableException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.NOT_ACCEPTABLE, uri, cause);
        }
    }


    public static class NotImplementedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public NotImplementedException() {
            super(HTTP.Response.Status.NOT_IMPLEMENTED);
        }
        public NotImplementedException(Throwable reason) {
            super(HTTP.Response.Status.NOT_IMPLEMENTED, reason);
        }
         public NotImplementedException(String message, Throwable cause) {
             super(HTTP.Response.Status.NOT_IMPLEMENTED, message, cause);
         }

         public NotImplementedException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.NOT_IMPLEMENTED, uri, cause);
        }
    }


    public static class PaymentRequiredException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public PaymentRequiredException() {
            super(HTTP.Response.Status.PAYMENT_REQUIRED);
        }
        public PaymentRequiredException(Throwable reason) {
            super(HTTP.Response.Status.PAYMENT_REQUIRED, reason);
        }
         public PaymentRequiredException(String message, Throwable cause) {
             super(HTTP.Response.Status.PAYMENT_REQUIRED, message, cause);
         }

         public PaymentRequiredException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.PAYMENT_REQUIRED, uri, cause);
        }
    }

    public static class ProxyAuthenticationRequiredException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public ProxyAuthenticationRequiredException() {
            super(HTTP.Response.Status.PROXY_AUTHENTICATION_REQUIRED);
        }
        public ProxyAuthenticationRequiredException(Throwable reason) {
            super(HTTP.Response.Status.PROXY_AUTHENTICATION_REQUIRED, reason);
        }
         public ProxyAuthenticationRequiredException(String message, Throwable cause) {
             super(HTTP.Response.Status.PROXY_AUTHENTICATION_REQUIRED, message, cause);
         }

         public ProxyAuthenticationRequiredException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.PROXY_AUTHENTICATION_REQUIRED, uri, cause);
        }
    }


    public static class RequestEntityTooLargeException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public RequestEntityTooLargeException() {
            super(HTTP.Response.Status.REQUEST_ENTITY_TOO_LARGE);
        }
        public RequestEntityTooLargeException(Throwable reason) {
            super(HTTP.Response.Status.REQUEST_ENTITY_TOO_LARGE, reason);
        }
         public RequestEntityTooLargeException(String message, Throwable cause) {
             super(HTTP.Response.Status.REQUEST_ENTITY_TOO_LARGE, message, cause);
         }

         public RequestEntityTooLargeException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.REQUEST_ENTITY_TOO_LARGE, uri, cause);
        }
    }


    public static class RequestTimeoutException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public RequestTimeoutException() {
            super(HTTP.Response.Status.REQUEST_TIMEOUT);
        }
        public RequestTimeoutException(Throwable reason) {
            super(HTTP.Response.Status.REQUEST_TIMEOUT, reason);
        }
         public RequestTimeoutException(String message, Throwable cause) {
             super(HTTP.Response.Status.REQUEST_TIMEOUT, message, cause);
         }

         public RequestTimeoutException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.REQUEST_TIMEOUT, uri, cause);
        }
    }


    public static class RequestURITooLongException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public RequestURITooLongException() {
            super(HTTP.Response.Status.REQUEST_URI_TOO_LONG);
        }
        public RequestURITooLongException(Throwable reason) {
            super(HTTP.Response.Status.REQUEST_URI_TOO_LONG, reason);
        }
         public RequestURITooLongException(String message, Throwable cause) {
             super(HTTP.Response.Status.REQUEST_URI_TOO_LONG, message, cause);
         }

         public RequestURITooLongException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.REQUEST_URI_TOO_LONG, uri, cause);
        }
    }


    public static class RequestedRangeNotSatisfiableException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public RequestedRangeNotSatisfiableException() {
            super(HTTP.Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        public RequestedRangeNotSatisfiableException(Throwable reason) {
            super(HTTP.Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE, reason);
        }
         public RequestedRangeNotSatisfiableException(String message, Throwable cause) {
             super(HTTP.Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE, message, cause);
         }

         public RequestedRangeNotSatisfiableException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE, uri, cause);
        }
    }


    public static class ServiceUnavailableException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public ServiceUnavailableException() {
            super(HTTP.Response.Status.SERVICE_UNAVAILABLE);
        }
        public ServiceUnavailableException(Throwable reason) {
            super(HTTP.Response.Status.SERVICE_UNAVAILABLE, reason);
        }
         public ServiceUnavailableException(String message, Throwable cause) {
             super(HTTP.Response.Status.SERVICE_UNAVAILABLE, message, cause);
         }

         public ServiceUnavailableException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.SERVICE_UNAVAILABLE, uri, cause);
        }
    }

    
    /**
     * Signals that the resource identified in the HTTP.Request is gone.
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.11</b></cite>:
     * <blockquote>
     * The requested resource is no longer available at the server and no forwarding address is known.
     * This condition is expected to be considered permanent.
     * Clients with link editing capabilities SHOULD delete references to the Request-URI after user approval.
     * If the server does not know, or has no facility to determine, whether or not the condition is permanent,
     * the status code 404 (Not Found) SHOULD be used instead. This response is cacheable unless indicated otherwise.
     * </blockquote>
     * <blockquote>
     * The 410 response is primarily intended to assist the task of web maintenance by notifying the recipient that
     * the resource is intentionally unavailable and that the server owners desire that remote links to that resource be removed.
     * Such an event is common for limited-time, promotional services and for resources belonging to individuals no longer working at the server's site.
     * It is not necessary to mark all permanently unavailable resources as "gone" or to keep the mark for
     * any length of time -- that is left to the discretion of the server owner. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class GoneException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public GoneException() {
            super(HTTP.Response.Status.GONE);
        }
        
        public GoneException(String message) {
            super(HTTP.Response.Status.GONE, message);
        }
        
        public GoneException(URI uri) {
            super(HTTP.Response.Status.GONE, uri.toString());
        }

        public GoneException(String message, Throwable reason) {
            super(HTTP.Response.Status.GONE, message, reason);
        }

        public GoneException(Throwable reason) {
            super(HTTP.Response.Status.GONE, reason);
        }

        public GoneException(URI uri, Throwable reason) {
            super(HTTP.Response.Status.GONE, uri, reason);
        }

        public GoneException(URI uri, String message) {
            super(HTTP.Response.Status.GONE, uri, message);
        }

        public GoneException(URI uri, String message, Throwable reason) {
            super(HTTP.Response.Status.GONE, uri, message, reason);
        }
    }

    
    /**
     * Signals that the server encountered an unexpected condition which prevented it from fulfilling the request.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class InternalServerErrorException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public InternalServerErrorException() {
            super(HTTP.Response.Status.INTERNAL_SERVER_ERROR);
        }

        public InternalServerErrorException(Throwable reason) {
            super(HTTP.Response.Status.INTERNAL_SERVER_ERROR, reason);
        }

        public InternalServerErrorException(String message, Throwable cause) {
            super(HTTP.Response.Status.INTERNAL_SERVER_ERROR, message, cause);
        }
        
        public InternalServerErrorException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.INTERNAL_SERVER_ERROR, uri, cause);
        }
        
        public InternalServerErrorException(URI uri, String message, Throwable cause) {
            super(HTTP.Response.Status.INTERNAL_SERVER_ERROR, uri, message, cause);
        }
    }

    /**
     * Signals that an insufficient amount of storage is available to complete the {@link HTTP.Request}.
     * <p>
     * From <cite>RFC 4918, <b>&sect;11.5</b></cite>:
     * </p>
     * <blockquote>
     * The 507 (Insufficient Storage) status code means the method could not be performed on the resource because
     * the server is unable to store the representation needed to successfully complete the request.
     * This condition is considered to be temporary. If the request that received this status code was the result of a user action,
     * the request MUST NOT be repeated until it is requested by a separate user action.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class InsufficientStorageException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public InsufficientStorageException(URI uri, String message) {
            super(HTTP.Response.Status.INSUFFICIENT_STORAGE, uri, message);
        }

        public InsufficientStorageException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.INSUFFICIENT_STORAGE, uri, cause);
        }
    }

    /**
     * Signals that the source or destination resources are locked and the request method cannot be completed.
     * <p>
     * From <cite>RFC 4918, <b>&sect;11.5</b></cite>:
     * </p>
     * <blockquote>
     * The 423 (Locked) status code means the source or destination resource of a method is locked.
     * This response SHOULD contain an appropriate precondition or postcondition code, such as 'lock-token-submitted' or 'no-conflicting-lock'.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class LockedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;
        
        public LockedException(URI uri, String message) {
            super(HTTP.Response.Status.LOCKED, uri, message);
        }

        public LockedException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.LOCKED, uri, cause);
        }
    }

    /**
     * Signals that the request method could not be applied to the request URI.
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.6</b></cite>:
     * <blockquote>
     * The method specified in the Request-Line is not allowed for the resource identified by the Request-URI.
     * The response MUST include an Allow header containing a list of valid methods for the requested resource. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class MethodNotAllowedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public MethodNotAllowedException(Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED);
        }
        
        public MethodNotAllowedException(String message, Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED, message);
        }
        
        public MethodNotAllowedException(String message, Throwable reason, Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED, message, reason);
        }
        
        public MethodNotAllowedException(Throwable reason, Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED, reason);
        }
        
        public MethodNotAllowedException(URI uri, String message, Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED, uri, message);
        }
        public MethodNotAllowedException(URI uri, Throwable reason, Collection<HTTP.Request.Method> allowed) {
            super(HTTP.Response.Status.METHOD_NOT_ALLOWED, uri, reason);
        }
    }
    
    /**
     * Signals that the resource identified in the HTTP.Request could not be found.
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.5</b></cite>:
     * <blockquote>
     * The server has not found anything matching the Request-URI. No indication is given of whether the condition is temporary or permanent.
     * The 410 (Gone) status code SHOULD be used if the server knows, through some internally configurable mechanism,
     * that an old resource is permanently unavailable and has no forwarding address.
     * This status code is commonly used when the server does not wish to reveal exactly why the request has been refused,
     * or when no other response is applicable. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class NotFoundException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public NotFoundException(URI uri) {
            super(HTTP.Response.Status.NOT_FOUND, uri);
        }
        
        public NotFoundException(URI uri, String message) {
            super(HTTP.Response.Status.NOT_FOUND, uri, message);
        }
        
        public NotFoundException(URI uri, Throwable cause) {
            super(HTTP.Response.Status.NOT_FOUND, uri, cause);
        }
        
        public NotFoundException(URI uri, String message, Throwable cause) {
            super(HTTP.Response.Status.NOT_FOUND, uri, message, cause);
        }
    }
    

    /**
     * Signals that one or more preconditions failed before performing the operation on the resource identified in the {@link HTTP.Request}.
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.13</b></cite>:
     * <blockquote>
     * The precondition given in one or more of the request-header fields evaluated to false when it was tested on the server.
     * This response code allows the client to place preconditions on the current resource metainformation (header field data)
     * and thus prevent the requested method from being applied to a resource other than the one intended. 
     * </blockquote>
     * Further, <cite>RFC 4918, <b>&sect;9.8.5</b></cite>:
     * <blockquote>
     * 412 (Precondition Failed) - A precondition header check failed, e.g., the Overwrite header is "F" and the destination URL is already mapped to a resource.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class PreconditionFailedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;

        public PreconditionFailedException(URI uri) {
            super(HTTP.Response.Status.PRECONDITION_FAILED, uri);
        }
        
        public PreconditionFailedException(URI uri, String message) {
            super(HTTP.Response.Status.PRECONDITION_FAILED, uri, message);
        }
        
        public PreconditionFailedException(URI uri, Throwable reason) {
            super(HTTP.Response.Status.PRECONDITION_FAILED, uri, reason);
        }
    }
    
    /**
     * Signals that an {@link HTTP.Request} requires authorisation before it can be performed or, if authentication information was supplied, it is incorrect.
     * <p>
     * Included in instances of this class are the authentication type and realm required.
     * See RFC 2617.
     * </p>
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.2</b></cite>:
     * <blockquote>
     * The request requires user authentication.
     * The response MUST include a WWW-Authenticate header field (section 14.47) containing a challenge applicable to the requested resource.
     * The client MAY repeat the request with a suitable Authorization header field (section 14.8).
     * If the request already included Authorization credentials, then the 401 response indicates that authorization has been refused for those credentials.
     * If the 401 response contains the same challenge as the prior response, and the user agent has already attempted authentication at least once,
     * then the user SHOULD be presented the entity that was given in the response, since that entity might include relevant diagnostic information.
     * HTTP access authentication is explained in "HTTP Authentication: Basic and Digest Access Authentication" [43].
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class UnauthorizedException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;
        private HTTP.Authenticate authenticate;

        public UnauthorizedException(URI uri, HTTP.Authenticate authenticate) {
            super(HTTP.Response.Status.UNAUTHORIZED, uri);
            this.authenticate = authenticate;
        }
        
        /**
         * Construct an {@link UnathorizedException} that signals authorisation is required and either none was presented,
         * or it was incorrect, or insufficient, and include an embedded {@link Throwable} as a reason for this exception, and an instance
         * of {@link HTTP.Authenticate} suitable for generating a challenge response for use in a retry.
         * @param message an explanatory message about the authorization required.
         * @param reason the cause (which is saved for later retrieval by the getCause() method). (A null value is permitted,
         *        and indicates that the cause is nonexistent or unknown.)
         * @param authenticate an instance of {@link HTTP.Authenticate} suitable for generating a challenge response for use in a retry.
         */
        public UnauthorizedException(URI uri, Throwable reason, HTTP.Authenticate authenticate) {
            super(HTTP.Response.Status.UNAUTHORIZED, uri, reason);
            this.authenticate = authenticate;
        }

        public UnauthorizedException(URI uri, String message, HTTP.Authenticate authenticate) {
            super(HTTP.Response.Status.UNAUTHORIZED, uri, message);
            this.authenticate = authenticate;
        }

        public UnauthorizedException(URI uri, String message, Throwable reason, HTTP.Authenticate authenticate) {
            super(HTTP.Response.Status.UNAUTHORIZED, uri, message, reason);
            this.authenticate = authenticate;
        }
        
        /**
         * Get the {@link HTTP.Authenticate} object that accompanies this exception.
         * @return the {@link HTTP.Authenticate} object that accompanies this exception.
         */
        public HTTP.Authenticate getAuthenticate() {
            return this.authenticate;
        }
    }
    
    /**
     * Signals that the server is refusing to service the request because the entity of the
     * request is in a format not supported by the requested resource for the requested method. 
     * <p>
     * From <cite>RFC 2616, <b>&sect;10.4.16</b></cite>:
     * <blockquote>
     * The server is refusing to service the request because the entity of the request is
     * in a format not supported by the requested resource for the requested method. 
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class UnsupportedMediaTypeException extends HTTP.Exception {
        private static final long serialVersionUID = 1L;
        
        public UnsupportedMediaTypeException() {
            super(HTTP.Response.Status.UNSUPPORTED_MEDIA_TYPE);
        }
        
        public UnsupportedMediaTypeException(String message) {
            super(HTTP.Response.Status.UNSUPPORTED_MEDIA_TYPE, message);
        }
        
        public UnsupportedMediaTypeException(String message, Throwable reason) {
            super(HTTP.Response.Status.UNSUPPORTED_MEDIA_TYPE, message, reason);
        }
    }
    
    /**
     * An HTTP entity-tag
     * From <cite>RFC 2616, <b>&sect;3.11</b></cite>:
     * <blockquote>
     * Entity tags are used for comparing two or more entities from the same requested resource.
     * HTTP/1.1 uses entity tags in the ETag (section 14.19), If-Match (section 14.24),
     * If-None-Match (section 14.26), and If-Range (section 14.27) header fields.
     * The definition of how they are used and compared as cache validators is in section 13.3.3.
     * An entity tag consists of an opaque quoted string, possibly prefixed by a weakness indicator.
     * </blockquote>
     * <blockquote>
     * <pre>
     * entity-tag = [ weak ] opaque-tag
     * weak       = "W/"
     * opaque-tag = quoted-string
     * </pre>
     * </blockquote>
     * <blockquote>
     * A "strong entity tag" MAY be shared by two entities of a resource only if they are equivalent by octet equality.
     * A "weak entity tag," indicated by the "W/" prefix,
     * MAY be shared by two entities of a resource only if the entities are equivalent and could be substituted for
     * each other with no significant change in semantics.
     * A weak entity tag can only be used for weak comparison.
     * </blockquote>
     * <blockquote>
     * An entity tag MUST be unique across all versions of all entities associated with a particular resource.
     * A given entity tag value MAY be used for entities obtained by requests on different URIs.
     * The use of the same entity tag value in conjunction with entities obtained by requests on different URIs does not imply the equivalence of those entities.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static interface EntityTag extends HTTP.Message.Header.If.Operand/*, HTTP.Message.Header.IfMatch.ConditionTerm, HTTP.Message.Header.IfNoneMatch.ConditionTerm, HTTP.Message.Header.IfRange.ConditionTerm */ {
        
    }

    /**
     * 
     * An HTTP Exception signals a failure of an HTTP method as applied to a resource.
     * <p>
     * If classes extending this abstract class do not provide an
     * {@link HTTP.Response} instance to be used to transmit to the client (see {@link #setResponse(Response)} and {@link #getResponse()}),
     * a default {@code HTTP.Response} is generated.
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public abstract static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;
        private HTTP.Response.Status status;
        private URI uri;
        private HTTP.Response provisionalResponse;
        
        public Exception(HTTP.Response.Status status) {
            super();
            this.status = status;
        }
        
        public Exception(HTTP.Response.Status status, String message) {
            super(message);
            this.status = status;
        }            
    
        public Exception(HTTP.Response.Status status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }
        
        public Exception(HTTP.Response.Status status, Throwable cause) {
            super(cause);
            this.status = status;
        }        

        public Exception(HTTP.Response.Status status, URI uri) {
            super();
            this.status = status;
            this.uri = uri;
        }
        
        public Exception(HTTP.Response.Status status, URI uri, String message) {
            super(message);
            this.status = status;
            this.uri = uri;
        }            
    
        public Exception(HTTP.Response.Status status, URI uri, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
            this.uri = uri;
        }
        
        public Exception(HTTP.Response.Status status, URI uri, Throwable cause) {
            super(cause);
            this.status = status;
            this.uri = uri;
        }
        
        /**
         * Get the URI that this Exception is related to.
         * 
         * @return the URI that this Exception is related to.
         */
        public URI getURI() {
            return this.uri;
        }

        /**
         * Set the URI that this Exception is related to.
         * 
         * @return this HTTP.Exception.
         */
        public HTTP.Exception setURI(URI uri) {
            this.uri = uri;
            return this;
        }
        
        /**
         * Get the {@link HTTP.Status} corresponding to this Exception.
         * 
         * @return the {@link HTTP.Status} corresponding to this Exception.
         */
        public HTTP.Response.Status getStatus() {
            return this.status;
        }
        
        /**
         * Get a well-formed {@link HTTP.Response} suitable for transmitting to the client communicating this Exception.
         * 
         * @return a well-formed {@link HTTP.Response} communicating this Exception.
         */
        public HTTP.Response getResponse() {
            if (this.provisionalResponse == null)
                this.provisionalResponse = new HttpResponse(status, new HttpContent.Text.Plain(this.toString()));
            return this.provisionalResponse;
        }
        
        public HTTP.Response setResponse(HTTP.Response response) {
            this.provisionalResponse = response;
            return this.provisionalResponse;
        }
    
        public String toString() {
            StringBuilder result = new StringBuilder(this.getStatus().toString()).append(" ");
            if (this.uri != null) {
                result.append(this.uri.toString()).append("\r\n");
            }
    
            if (this.getMessage() != null)
                result.append(this.getMessage());
            
            Throwable cause = this.getCause();
            if (cause != null) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                PrintWriter p = new PrintWriter(bout);
                cause.printStackTrace(p);
                p.close();
                result.append("\r\n").append(bout.toString());
            }
    
            return result.toString();
        }
        
        public static void format(String name, String status) {
            System.out.printf("%n");
            System.out.printf("%n");
            System.out.printf("public static class %sException extends HTTP.Exception {%n", name);
            System.out.printf("    private static final long serialVersionUID = 1L;%n");
            System.out.printf("%n");
            System.out.printf("    public %sException() {%n", name);
            System.out.printf("        super(%s);%n", status);
            System.out.printf("    }%n");

            System.out.printf("    public %sException(Throwable reason) {%n", name);
            System.out.printf("        super(%s, reason);%n", status);
            System.out.printf("    }%n");

            System.out.printf("     public %sException(String message, Throwable cause) {%n", name);
            System.out.printf("         super(%s, message, cause);%n", status);
            System.out.printf("     }%n");
            System.out.printf("%n");
            System.out.printf("     public %sException(URI uri, Throwable cause) {%n", name);
            System.out.printf("        super(%s, uri, cause);%n", status);
            System.out.printf("    }%n");
            System.out.printf("}%n");
        }
        public static void main(String[] args) {

            format("ExpectationFailed", "HTTP.Response.Status.EXPECTATION_FAILED");
            format("GatewayTimeout", "HTTP.Response.Status.GATEWAY_TIMEOUT");
            format("HTTPVersionNotSupported", "HTTP.Response.Status.HTTP_VERSION_NOT_SUPPORTED");
            format("LengthRequired", "HTTP.Response.Status.LENGTH_REQUIRED");
            format("NotAcceptable", "HTTP.Response.Status.NOT_ACCEPTABLE");
            format("NotImplemented", "HTTP.Response.Status.NOT_IMPLEMENTED");
            format("PaymentRequired", "HTTP.Response.Status.PAYMENT_REQUIRED");
            format("PreconditionFailed", "HTTP.Response.Status.PRECONDITION_FAILED");
            format("ProxyAuthenticationRequired", "HTTP.Response.Status.PROXY_AUTHENTICATION_REQUIRED");
            format("RequestEntityTooLarge", "HTTP.Response.Status.REQUEST_ENTITY_TOO_LARGE");
            format("RequestTimeout", "HTTP.Response.Status.REQUEST_TIMEOUT");
            format("RequestURITooLong", "HTTP.Response.Status.REQUEST_URI_TOO_LONG");
            format("RequestedRangeNotSatisfiable", "HTTP.Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE");
            format("ServiceUnavailable", "HTTP.Response.Status.SERVICE_UNAVAILABLE");
        }
    }
}
