/*
 * Copyright 2008-2010 Sun Microsystems, Inc. All Rights Reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.naming.Name;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import sunlabs.asdf.web.XML.DAV;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.asdf.web.http.WebDAVNameSpace;
import sunlabs.asdf.web.http.HTTP.BadRequestException;
import sunlabs.asdf.web.http.HTTP.ConflictException;
import sunlabs.asdf.web.http.HTTP.ForbiddenException;
import sunlabs.asdf.web.http.HTTP.GoneException;
import sunlabs.asdf.web.http.HTTP.InsufficientStorageException;
import sunlabs.asdf.web.http.HTTP.InternalServerErrorException;
import sunlabs.asdf.web.http.HTTP.LockedException;
import sunlabs.asdf.web.http.HTTP.MethodNotAllowedException;
import sunlabs.asdf.web.http.HTTP.NotFoundException;
import sunlabs.asdf.web.http.HTTP.PreconditionFailedException;
import sunlabs.asdf.web.http.HTTP.UnauthorizedException;
import sunlabs.asdf.web.http.HTTP.Response.Status;
import sunlabs.asdf.web.http.HttpUtil.PathName;
import sunlabs.asdf.web.http.WebDAV.Resource.Property;

/**
 * An RFC 4918 compliant WebDAV Server
 * 
 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class WebDAV {
    
    public interface Server extends HTTP.Server {
        
    }

    /**
     * Abstract class providing a basic implementation of a WebDAV resource.
     * This implementation provides for resources that behave like HTTP resources.
     * Therefore some of the method implementations in this class do not act like WebDAV resources.
     *
     * <p>
     * Developers must provide their own implementations of these methods.
     * </p>
     */
    abstract public static class AbstractResource extends HTTP.AbstractResource implements WebDAV.Resource {
        protected WebDAV.Backend backend;
        
        public AbstractResource(WebDAV.Backend backend, URI uri, HTTP.Identity identity) {
            super(backend, uri, identity);
            this.backend = backend;
        }
        
        @Override
        public WebDAV.Backend getBackend() {
            return this.backend;
        }
        
        /**
         * This implementation throws {@link HTTP.ForbiddenException}.
         * <p>
         * Subclasses may override this method with their own implementation.
         * </p>
         */
        public void createCollection() throws HTTP.MethodNotAllowedException, HTTP.InternalServerErrorException, HTTP.InsufficientStorageException,
        HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.ForbiddenException, HTTP.BadRequestException,
        HTTP.LockedException {
            throw new HTTP.ForbiddenException(this.getURI(), "Cannot create collection resource");
        }
        
        public Collection<HTTP.Request.Method> getAccessAllowed() throws
        HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            Collection<HTTP.Request.Method> result = new HashSet<HTTP.Request.Method>();

            // By default, just include the standard set of HTTP operations.
            result.add(HTTP.Request.Method.HEAD);
            result.add(HTTP.Request.Method.GET);
            result.add(HTTP.Request.Method.POST);
            result.add(HTTP.Request.Method.PUT);
            result.add(HTTP.Request.Method.OPTIONS);
            result.add(HTTP.Request.Method.DELETE);
            result.add(HTTP.Request.Method.TRACE);
            result.add(HTTP.Request.Method.CONNECT);
            
            if (this instanceof WebDAV.Resource.DAV1) {
                if (this.isCollection()) {
                    result.add(HTTP.Request.Method.GET);
                    result.add(HTTP.Request.Method.HEAD);
                    result.add(HTTP.Request.Method.OPTIONS);
                    result.add(HTTP.Request.Method.DELETE);
                    result.add(HTTP.Request.Method.PROPFIND);
                    result.add(HTTP.Request.Method.PROPPATCH);
                    result.add(HTTP.Request.Method.MKCOL);
                    result.add(HTTP.Request.Method.COPY);
                    result.add(HTTP.Request.Method.MOVE);
                } else {
                    result.add(HTTP.Request.Method.GET);
                    result.add(HTTP.Request.Method.HEAD);
                    result.add(HTTP.Request.Method.OPTIONS);
                    result.add(HTTP.Request.Method.DELETE);
                    result.add(HTTP.Request.Method.PROPFIND);
                    result.add(HTTP.Request.Method.PROPPATCH);
                    result.add(HTTP.Request.Method.MKCOL);
                    result.add(HTTP.Request.Method.PUT);
                    result.add(HTTP.Request.Method.POST);
                    result.add(HTTP.Request.Method.COPY);
                    result.add(HTTP.Request.Method.MOVE);                
                }
            }
            
            if (this instanceof WebDAV.Resource.DAV2) {
                result.add(HTTP.Request.Method.LOCK);
                result.add(HTTP.Request.Method.UNLOCK);
            }

            return result;
        }
        
        public Collection<String> getComplianceClass() throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException {
            Collection<String> result = new LinkedList<String>();

            if (this instanceof WebDAV.Resource.DAV1) {
                result.add("1");
            }
            if (this instanceof WebDAV.Resource.DAV2) {
                result.add("2");
            }
            if (this instanceof WebDAV.Resource.DAV3) {
                result.add("3");
            }
            return result;
        }

        /**
         * {@inheritDoc}
         * 
         * This implementation will return the HTTP status {@link HTTP.MethodNotAllowedException}.
         * See also: {@link #getAccessAllowed()}.
         */
        public OutputStream asOutputStream()
                throws InternalServerErrorException, GoneException, MethodNotAllowedException, InsufficientStorageException,
                NotFoundException, UnauthorizedException, ConflictException, BadRequestException, LockedException, ForbiddenException {
            throw new HTTP.MethodNotAllowedException(this.getURI(), "Write protected", this.getAccessAllowed());
        }

        /**
         * Produce a ETag value consisting of the concatenation of the last modified time and content length.
         */
        public String getETag() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            return String.format("%s/%s", this.getLastModified(), this.getContentLength());
        }

        /**
         * {@inheritDoc}
         * 
         * This implementation returns the current system time.
         */
        public long getCreationDate() throws InternalServerErrorException, NotFoundException, GoneException, UnauthorizedException, ConflictException {
            return System.currentTimeMillis();
        }

        /**
         * {@inheritDoc}
         * 
         * This implementation returns 0.
         */
        public long getLastModified() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            return 0;
        }
        
        public WebDAV.DAVLockDiscovery getLock()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            Map<WebDAV.Resource.Property.Name, WebDAV.Resource.Property> properties = this.getProperties();
            WebDAV.Resource.Property lockDiscoveryProperty = properties.get(WebDAV.Resource.DAV2.lockDiscovery);
            if (lockDiscoveryProperty == null) {
                return null;
            }
            try {
                Document doc = WebDAVNameSpace.parseXMLBody(lockDiscoveryProperty.getValue().getBytes());
                Element element = doc.getDocumentElement();
                if (element.getLocalName().compareTo("lockdiscovery") == 0) {
                    WebDAV.DAVLockDiscovery activeLock = new WebDAV.DAVLockDiscovery(element.getChildNodes());
                    return activeLock;
                }
            } catch (HTTP.BadRequestException e) {
                throw new HTTP.InternalServerErrorException(this.getURI(), e.getMessage(), e.getCause());
            } finally {

            }
            return null;
        }
        
        public Map<WebDAV.Resource,WebDAV.DAVLockDiscovery> getLocks()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            Map<WebDAV.Resource,WebDAV.DAVLockDiscovery> result = new HashMap<WebDAV.Resource,WebDAV.DAVLockDiscovery>();
            
            if (this instanceof WebDAV.Resource.DAV2) {
                result.put(this, ((WebDAV.Resource.DAV2) this).getLock());                
            }
            
            for (WebDAV.Resource resource : this.getParents()) {
                if (resource instanceof WebDAV.Resource.DAV2) {
                    result.put(resource, ((WebDAV.Resource.DAV2) resource).getLock());
                }
            }

            return result;    
        }
        
        public Name getName() {
            // TODO Auto-generated method stub
            return null;
        }
        
        /**
         * {@inheritDoc}
         * 
         * This implementation simply returns the last component of this resource's {@link URI}.
         */
        public String getDisplayName() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            String result = new HttpUtil.PathName(this.getURI().getPath()).baseName();
            if (result.equals("")) {
                result = "/";
            }
            return result;
        }
        
        public List<WebDAV.Resource> getParents() throws HTTP.GoneException, HTTP.NotFoundException, HTTP.InternalServerErrorException, HTTP.UnauthorizedException {
            try {
            List<WebDAV.Resource> parents = new LinkedList<WebDAV.Resource>();

            PathName parentPath = new PathName(this.getURI()).dirName();
            while (parentPath.size() > 1) {
                parents.add(this.getBackend().getResource(URI.create(parentPath.toString()), this.getIdentity()));

                parentPath = parentPath.dirName();
            }
            
            return parents;
            } catch (HTTP.BadRequestException e) {
                throw new HTTP.InternalServerErrorException(this.getURI(), e);
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation tries to deduce the content-type from the file-name extension (suffix) using the URI path of this resource
         * (See {@link InternetMediaType#getByFileExtension(String)}.
         * </p>
         */
        public InternetMediaType getContentType()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            InternetMediaType result = InternetMediaType.getByFileExtension(InternetMediaType.getExtension(this.getURI().toString()));
            if (result == null)
                result = InternetMediaType.Application.OctetStream;
            return result;
        }
        
        /**
         * Get the entity-tag and all of the state-tokens that are relevant to this resource.
         */
        public HTTP.Resource.State getResourceState()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            // Get the "state" for this resource, then ask each of the ancestors to add their relevant state-tokens.

            HTTP.Resource.State result = new HTTP.Resource.State(this.getURI());

            if (this instanceof WebDAV.Resource.DAV2) {
                WebDAV.Resource.DAV2 dav2Resource = (WebDAV.Resource.DAV2) this;
                if (dav2Resource.exists()) {
                    result.add(new HTTP.Resource.State.EntityTag(this.getETag()));
                    WebDAV.DAVLockDiscovery locks = dav2Resource.getLock();
                    for (WebDAV.DAVActiveLock activeLock : locks) {
                        result.add(new HTTP.Resource.State.CodedURL(activeLock.getLockToken().getHref().getURI()));
                    }
                }
                
                for (WebDAV.Resource resource : dav2Resource.getParents()) {
                    if (resource instanceof WebDAV.Resource.DAV2) {
                        dav2Resource = (WebDAV.Resource.DAV2) resource;
                        WebDAV.DAVLockDiscovery locks = dav2Resource.getLock();
                        for (WebDAV.DAVActiveLock activeLock : locks) {
                            if (activeLock.getDepth().equals(HTTP.Message.Header.Depth.Level.INFINITY)) {
                                result.add(new HTTP.Resource.State.CodedURL(activeLock.getLockToken().getHref().getURI()));
                            }
                        }
                    }
                }
            }
            
            return result;
        }
        
        /**
         * This implementation throws {@link HTTP.MethodNotAllowedException}.
         * <p>
         * Subclasses may override this method with their own implementation.
         * </p>
         */
        public Status move(URI destination)
                throws HTTP.MethodNotAllowedException, InternalServerErrorException, BadRequestException, GoneException, InsufficientStorageException, ConflictException,
                NotFoundException, UnauthorizedException, PreconditionFailedException, LockedException, ForbiddenException {
            throw new HTTP.MethodNotAllowedException(this.getURI(), "Cannot move resource", this.getAccessAllowed()); 
        }
        
        public void setLock(WebDAV.DAVLockDiscovery activeLock)
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, InsufficientStorageException, HTTP.BadRequestException {
            DAV dav = new DAV();
            DAV.LockDiscovery activeLockXML = activeLock.toXML(dav);
            activeLockXML.bindNameSpace();

//            WebDAVNameSpace.parseXMLBody(activeLockXML.toString().getBytes()); // XXX just to prove that it is parsable.
            
            Map<WebDAV.Resource.Property.Name, WebDAV.Resource.Property> properties = this.getProperties();
            properties.put(WebDAV.Resource.DAV2.lockDiscovery, new WebDAV.Resource.Property(WebDAV.Resource.DAV2.lockDiscovery, XML.formatXMLElement(activeLockXML.toString(), 1), false));
            this.setProperties(properties);
        }

        public void setProperties(Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> properties)
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, HTTP.InsufficientStorageException {
            try {
                for (WebDAV.Resource.Property p : properties.values()) {
                    if (p.getName().equals("DAV:#getcontenttype")) {
                        // check to see if this is really different and invoke the call only if it is.
//                        this.getFile().setContentType(InternetMediaType.type(String.valueOf(p.getValue())));    
//                    } else if (p.getName().equals("DAV:#getcontenttype")) {
//                        this.getFile().setContentType(InternetMediaType.type(String.valueOf(p.getValue())));
                    }
                }
            } finally {
                
            }
        }

        @Override
        public String toString() {
            return String.format("%s", this.getURI());
        }

        /**
         * <p>
         * Subclasses override this method with an implementation that adds or replaces resource properties as appropriate.
         * </p>
         */
        public Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> getProperties()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            try {
                Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> result = new HashMap<WebDAV.Resource.Property.Name,WebDAV.Resource.Property>();

                if (this.isCollection())  { // no content length for collections.
                    result.put(WebDAV.Resource.DAV1.resourceType, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.resourceType, "<dav:collection xmlns:dav=\"DAV:\" />", true));
                } else {
                    try {
                        result.put(WebDAV.Resource.DAV1.getContentLength, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.getContentLength, String.valueOf(this.getContentLength()), true));
                    } catch (HTTP.Exception e) {
                        // Leave the property unset.
                    }
                    result.put(WebDAV.Resource.DAV1.resourceType, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.resourceType, "", true)); 
                }

                try {
                    result.put(WebDAV.Resource.DAV1.creationDate, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.creationDate, WebDAV.RFC3339Timestamp(this.getCreationDate()), true));
                } catch (HTTP.Exception e) {
                    // Leave the property unset.
                }
                try {
                    result.put(WebDAV.Resource.DAV1.displayName, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.displayName, this.getDisplayName(), true));
                } catch (HTTP.Exception e) {
                    // Leave the property unset.
                }
                try {
                    result.put(WebDAV.Resource.DAV1.getContentType, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.getContentType, this.getContentType().toString(), true));
                } catch (HTTP.Exception e) {
                    // Leave the property unset.
                }
                try {
                    result.put(WebDAV.Resource.DAV1.getETag, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.getETag, this.getETag(), true));
                } catch (HTTP.Exception e) {
                    // Leave the property unset.
                }
                try {
                    result.put(WebDAV.Resource.DAV1.getLastModified, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.getLastModified, WebDAV.RFC3339Timestamp(this.getLastModified()), true));
                } catch (HTTP.Exception e) {
                    // Leave the property unset.
                }
                
                if (this instanceof WebDAV.Resource.DAV2) {
                    result.put(WebDAV.Resource.DAV2.lockDiscovery, new WebDAV.Resource.Property(WebDAV.Resource.DAV2.lockDiscovery, "<dav:lockdiscovery xmlns:dav=\"DAV:\" />", false));
                }

                return result;
            } finally {

            }
        }
        
        public HTTP.Response.Status copy(URI destination) throws HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.NotFoundException,
        HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.MethodNotAllowedException, HTTP.ForbiddenException, HTTP.BadRequestException,
        HTTP.LockedException, HTTP.PreconditionFailedException {
            try {
                System.out.printf("copy %s -> %s%n", this.getURI(), destination);
                
                WebDAV.Resource destResource = this.getBackend().getResource(this.getURI().resolve(destination), this.getIdentity());
                if (this.isCollection()) {
                    if (destResource.exists()) {
                        destResource.delete();
                        destResource.createCollection();
                        return HTTP.Response.Status.NO_CONTENT;
                    }
                    destResource.createCollection();
                    return HTTP.Response.Status.CREATED;
                } else {
                    if (destResource.exists()) {
                        InputStream in = this.asInputStream();
                        OutputStream out = destResource.asOutputStream();
                        // Perhaps the resource should do the job of copying itself to another resource. That way it can be observant of optimal buffer sizes, etc.
                        HttpUtil.transferTo(in, out, 1024*1024);
                        in.close();
                        out.close();
                        return HTTP.Response.Status.NO_CONTENT;
                    } else {
                        destResource.create(this.getContentType());
                    }
                    InputStream in = this.asInputStream();
                    OutputStream out = destResource.asOutputStream();
                    // Perhaps the resource should do the job of copying itself to another resource. That way it can be observant of optimal buffer sizes, etc.
                    HttpUtil.transferTo(in, out, 1024*1024);
                    in.close();
                    out.close();
                    return HTTP.Response.Status.CREATED;
                }
            } catch (IOException e) {
                throw new HTTP.InternalServerErrorException(this.getURI().toString(), e);
            } finally {

            }
        }
    }
    
    /**
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Backend extends HTTP.Backend {

        /**
         * Create an instance of a class implementing the {@link WebDAV.Resource} interface that is represented by the given {@link URI}.
         * <p>
         * This instance represents the resource given by the URI, which may or may not already exist in the backend.
         * Check for the presence of the resource before accessing it.  See {@link WebDAV.Resource#exists()} and {@link WebDAV.Resource#create(InternetMediaType)}.
         * </p>
         * 
         * @param uri The {@link URI} of the resource
         * @param identity The {@link HTTP} of the accessor. 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws BadRequestException 
         */
        public WebDAV.Resource getResource(URI uri, HTTP.Identity identity)
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.UnauthorizedException, BadRequestException;
    }

    /**
     * Convenience method to produce a formatted String containing a time-stamp in accordance with
     * <a href="http://tools.ietf.org/html/rfc3339">RFC 3339 - Date and Time on the Internet: Timestamps</a>
     * @param millis the number of milliseconds since midnight, January 1, 1970 UTC. 
     * @return A formatted String containing a RFC 3339 formatted time-stamp.
     */
    public static String RFC3339Timestamp(long millis) {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        s.setTimeZone(TimeZone.getTimeZone("GMT:00"));
        return s.format(new Date(millis));
    }
    
    /**
     * <!ELEMENT activelock (lockscope, locktype, depth, owner?, timeout?, locktoken?, lockroot)>
     */
    public static class DAVActiveLock {
        private DAVLockScope lockScope;
        private DAVLockType lockType;
        private DAVDepth depth;
        private DAVOwner owner;
        private DAVTimeout timeout;
        private DAVLockToken lockToken;
        private DAVLockRoot lockRoot;
        
        public DAVActiveLock(DAVLockScope lockScope, DAVLockType lockType, HTTP.Message.Header.Depth.Level depth, DAVOwner owner, DAVTimeout timeout, DAVLockToken lockToken, DAVLockRoot lockRoot) {
            this.lockScope = lockScope;
            this.lockType = lockType;
            this.depth = new DAVDepth(depth);
            this.owner = owner;
            this.timeout = timeout;
            this.lockToken = lockToken;
            this.lockRoot = lockRoot;
        }
        
        public DAVActiveLock(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareToIgnoreCase("lockscope") == 0) {
                            this.lockScope = new DAVLockScope(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("locktype") == 0) {
                            this.lockType = new DAVLockType(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("depth") == 0) {
                            this.depth = new DAVDepth(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("timeout") == 0) {
                            this.timeout = new DAVTimeout(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("owner") == 0) {
                            this.owner = new DAVOwner(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("locktoken") == 0) {
                            this.lockToken = new DAVLockToken(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("lockroot") == 0) {
                            this.lockRoot = new DAVLockRoot(node.getChildNodes());
                        }
                    }
                }
            }
        }
        
        public DAVLockScope getLockScope() {
            return this.lockScope;
        }

        public void setLockScope(DAVLockScope lockScope) {
            this.lockScope = lockScope;
        }

        public DAVLockType getLockType() {
            return this.lockType;
        }

        public void setLockType(DAVLockType lockType) {
            this.lockType = lockType;
        }

        public DAVDepth getDepth() {
            return this.depth;
        }

        public void setDepth(DAVDepth depth) {
            this.depth = depth;
        }

        public DAVOwner getOwner() {
            return this.owner;
        }

        public void setOwner(DAVOwner owner) {
            this.owner = owner;
        }

        public DAVTimeout getTimeout() {
            return this.timeout;
        }

        public void setTimeout(DAVTimeout timeout) {
            this.timeout = timeout;
        }

        public DAVLockToken getLockToken() {
            return this.lockToken;
        }

        public void setLockToken(DAVLockToken lockToken) {
            this.lockToken = lockToken;
        }

        public DAVLockRoot getLockRoot() {
            return this.lockRoot;
        }

        public void setLockRoot(DAVLockRoot lockRoot) {
            this.lockRoot = lockRoot;
        }

        public DAV.ActiveLock toXML(DAV dav) {
            DAV.ActiveLock result = dav.newActiveLock();

            if (this.lockScope != null)
                result.add(this.lockScope.toXML(dav));
            if (this.lockType != null)
                result.add(this.lockType.toXML(dav));
            if (this.depth != null)
                result.add(this.depth.toXML(dav));
            if (this.owner != null)
                result.add(this.owner.toXML(dav));
            if (this.timeout != null)
                result.add(this.timeout.toXML(dav));
            if (this.lockToken != null)
                result.add(this.lockToken.toXML(dav));
            if (this.lockRoot != null)
                result.add(this.lockRoot.toXML(dav));

            return result;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (this.lockScope != null)
                result.append("scope=").append(this.lockScope.toString());
            if (this.lockType != null)
                result.append(" type=").append(this.lockType.toString());
            if (this.depth != null)
                result.append(" depth=").append(this.depth.toString());
            if (this.owner != null)
                result.append(" owner=").append(this.owner.toString());
            if (this.timeout != null)
                result.append(" timeout=").append(this.timeout.toString());
            if (this.lockToken != null)
                result.append(" token=").append(this.lockToken.toString());
            if (this.lockRoot != null)
                result.append(" root=").append(this.lockRoot.toString());
            
            return result.toString();
        }
    }
    
    public static class DAVDepth {
        private HTTP.Message.Header.Depth.Level depth;
        
        public DAVDepth(HTTP.Message.Header.Depth.Level depth) {
            this.depth = depth;
        }
        
        public DAVDepth(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                String string = node.getTextContent();
                if (string.compareTo("0") == 0) {
                    this.depth = HTTP.Message.Header.Depth.Level.ONLY;
                } else if (string.compareTo("1") == 0) {
                    this.depth = HTTP.Message.Header.Depth.Level.ALL;
                } else {
                    this.depth = HTTP.Message.Header.Depth.Level.INFINITY;
                }
            }
        }
        
        @Override
        public int hashCode() {
            return this.depth.hashCode();
        }
        
        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (other == this)
                return true;
            if (other instanceof HTTP.Message.Header.Depth.Level) {
                HTTP.Message.Header.Depth.Level o = (HTTP.Message.Header.Depth.Level) other;
                return o == other;
            }
            System.err.printf("*******  Cannot compare HTTP.Message.Header.Depth.Level with %s%n", other.getClass());
            return false;
        }
        
        public DAV.Depth toXML(DAV dav) {
            return dav.newDepth(this.depth);
        }
        
        public String toString() {
            return this.depth.toString();
        }
    }
    
    public static class DAVLockInfo {
        private DAVLockScope scope;
        private DAVLockType type;
        private DAVOwner owner;
        
        public DAVLockInfo(DAVLockScope scope, DAVLockType type, DAVOwner owner) {
            this.scope = scope;
            this.type = type;
            this.owner = owner;            
        }
        
        /**
         * Construct a new instance from a {@link org.w3c.dom.NodeList} as produced by an XML document parser.
         * @param nodeList the {@code org.w3c.dom.NodeList} containing the child nodes of the original "lockinfo" element.
         * @throws HTTP.BadRequestException a parse error occurred.
         */
        public DAVLockInfo(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareToIgnoreCase("lockscope") == 0) {
                            this.scope = new DAVLockScope(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("locktype") == 0) {
                            this.type = new DAVLockType(node.getChildNodes());
                        } else if (node.getLocalName().compareToIgnoreCase("owner") == 0) {
                            this.owner = new DAVOwner(node.getChildNodes());
                        }
                    }
                }
            }
        }

        public DAVLockScope getScope() {
            return this.scope;
        }

        public DAVLockType getType() {
            return this.type;
        }

        public DAVOwner getOwner() {
            return this.owner;
        }
        
        public DAV.LockInfo toXML(DAV dav) {
            return dav.newLockInfo(this.scope.toXML(dav), this.type.toXML(dav), this.owner.toXML(dav));
        }
        
    }
    
    /**
     * LockRoot contains the root URL of the lock, which is the URL through which the resource was addressed in the LOCK request.
     * <p>
     * The href element contains the root of the lock.
     * The server SHOULD include this in all {@code DAV:lockdiscovery} property values and the response to LOCK requests.
     * </p>
     */
    public static class DAVLockRoot {
        private DAVHref href;
        
        public DAVLockRoot(DAVHref href) {
            this.href = href;
        }

        /**
         * Convenience constructor equivalent to:
         * {@code DAVLockRoot(new DAVHref(lockToken))}
         * @param lockRoot the URI for this lock root.
         */
        public DAVLockRoot(URI lockRoot) {
            this(new DAVHref(lockRoot));
        }
        
        public DAVLockRoot(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareToIgnoreCase("href") == 0) {
                            this.href = new DAVHref(node.getChildNodes());                            
                        }
                    }
                }
            }
        }
        
        public DAV.LockRoot toXML(DAV dav) {
            return dav.newLockRoot(this.href.toXML(dav));
        }
        
        public String toString() {
            return this.href.toString();
        }
    }
    
    /**
     * LockScope specifies  whether a lock is an exclusive lock, or a shared lock.
     * <p>
     * <a href="http://webdav.org/specs/rfc4918.html#HEADER_Timeout">Timeout</a>
     * </p>
     */
    public static class DAVLockScope {
        private boolean shared;

        public DAVLockScope(boolean shared) {
            this.shared = shared;
        }

        public DAVLockScope(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI() != null && node.getNamespaceURI().equals("")) {
                        throw new HTTP.BadRequestException(String.format("Property cannot have empty namespace: %s", node.toString()));
                    }
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareTo("exclusive") == 0) {
                            this.shared = false;
                        } else {
                            this.shared = true;
                        }
                    }
                }
            }
        }
        
        public boolean isExclusive() {
            return !this.shared;
        }
        
        public boolean isShared() {
            return this.shared;
        }

        public DAV.LockScope toXML(DAV dav) {
            if (this.shared)
                return dav.newLockScope(dav.newShared());
            return dav.newLockScope(dav.newExclusive());            
        }
        
        public String toString() {
            return this.shared ? "shared" : "exclusive";
        }
    }
    
    
    public static class DAVHref {
        private URI uri;
        
        public DAVHref(URI uri) {
            this.uri = uri;            
        }
        
        public DAVHref(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            try {
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    this.uri = new URI(node.getTextContent());
                }
            } catch (DOMException e) {
                throw new HTTP.BadRequestException("malformed href element");
            } catch (URISyntaxException e) {
                throw new HTTP.BadRequestException("malformed href element");
            } finally {

            }
        }
        
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (this == other)
                return true;
            if (other instanceof DAVHref) {
                return this.uri.equals(((DAVHref) other).uri);
            }
            
            return false;
        }
        
        public URI getURI() {
            return this.uri;
        }
        
        public DAV.Href toXML(DAV dav) {
            return dav.newHref(this.uri);
        }
        
        public String toString() {
            return this.uri.toASCIIString();
        }
    }
    
    public static class DAVLockToken {
        private DAVHref href;
        
        public DAVLockToken(DAVHref href) {
            this.href = href;
        }
        
        /**
         * Convenience constructor equivalent to:
         * {@code DAVLockToken(new DAVHref(lockToken))}
         * @param lockToken the lock token for this element.
         */
        public DAVLockToken(URI lockToken) {
            this(new DAVHref(lockToken));
        }

        public DAVLockToken(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareToIgnoreCase("href") == 0) {
                            this.href = new DAVHref(node.getChildNodes());
                        }
                    }
                }
            }
        }
        
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (this == other)
                return true;
            if (other instanceof WebDAV.DAVLockToken) {
                return this.href.equals(((WebDAV.DAVLockToken) other).href);
            }
            if (other instanceof HTTP.Resource.State.CodedURL) {
                HTTP.Resource.State.CodedURL stateToken = (HTTP.Resource.State.CodedURL) other;
                return stateToken.getCodedURL().equals(this.href.uri);
            }
            if (other instanceof HTTP.Message.Header.If.Operand) {
                return other.equals(this.getHref().getURI());
            }
            
            return false;
        }
        
        public DAVHref getHref() {
            return this.href;
        }
        
        public DAV.LockToken toXML(DAV dav) {
            DAV.LockToken result = dav.newLockToken();
            if (this.href != null)
                result.add(this.href.toXML(dav));
            return result;
        }
        
        public String toString() {
            return this.href.toString();
        }
    }
    
    /**
     * <a href="http://webdav.org/specs/rfc4918.html#ELEMENT_locktype">Lock Type</a>
     *
     */
    public static class DAVLockType {
        private boolean write;
        
        public DAVLockType() {
            this.write = true;
        }
        
        public DAVLockType(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI().compareToIgnoreCase("DAV:") == 0) {
                        if (node.getLocalName().compareToIgnoreCase("write") == 0) {
                            this.write = true;
                        }
                    }
                }
            }
        }
        
        public DAV.LockType toXML(DAV dav) {
            if (this.write) {
                return dav.newLockType(dav.newWrite());
            }

            return dav.newLockType("");
        }
        
        public String toString() {
            return "write";
        }
    }
    
    /**
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */    
    public static class DAVAllprop {
        public DAVAllprop() {
            /**/
        }
        
        public DAVAllprop(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            this();

            if (nodeList.getLength() != 0) {
                throw new HTTP.BadRequestException("allprop cannot have content");
            }
        }
    }
    
    /**
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */  
    public static class DAVError {
        public DAVError() {
            
        }        
    }
    
    /**
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */  
    public static class DAVInclude implements Iterable<WebDAV.Resource.Property> {
        private Collection<WebDAV.Resource.Property> properties;

        public DAVInclude() {
            this.properties = new LinkedList<WebDAV.Resource.Property>();
        }

        public DAVInclude(WebDAV.Resource.Property property) {
            this();
            this.add(property);
        }

        public DAVInclude(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            this();

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI() != null && node.getNamespaceURI().equals("")) {
                        throw new HTTP.BadRequestException(String.format("Property cannot have empty namespace: %s", node.toString()));
                    }
                    this.add(new WebDAV.Resource.Property(node.getNamespaceURI(), node.getLocalName(), node.getChildNodes()));
                }
            }
        }

        public void add(WebDAV.Resource.Property...property) {
            for (WebDAV.Resource.Property p : property) {
                this.properties.add(p);
            }
        }

        public Collection<WebDAV.Resource.Property> getProperties() {
            return this.properties;
        }

        public Iterator<Property> iterator() {
            return this.properties.iterator();
        }

        public DAV.Include toXML(DAV d) {
            DAV.Include result = d.newInclude();
            for (WebDAV.Resource.Property p : this.properties) {
                result.add(p.toXML(d));
            }
            return result;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            for (WebDAV.Resource.Property p : this.properties) {
                result.append(p.toString());
            }
            return result.toString();
        }
    }
    
    /**
     * Describes the active locks on a resource
     * 
     * <blockquote>
     * Describes the active locks on a resource
     * MUST be protected. Clients change the list of locks through LOCK and UNLOCK, not through PROPPATCH.
     * The value of this property depends on the lock state of the destination, not on the locks of the source resource. Recall that locks are not moved in a MOVE operation.
     * Returns a listing of who has a lock, what type of lock he has, the timeout type and the time remaining on the timeout, and the associated lock token.
     * Owner information MAY be omitted if it is considered sensitive.
     * If there are no locks, but the server supports locks, the property will be present but contain zero 'activelock' elements.
     * If there are one or more locks, an 'activelock' element appears for each lock on the resource.
     * This property is NOT lockable with respect to write locks (Section 7).
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVLockDiscovery implements Iterable<WebDAV.DAVActiveLock> {
        private Map<URI,WebDAV.DAVActiveLock> activeLocks;
        
        public DAVLockDiscovery() {
            this.activeLocks = new HashMap<URI,WebDAV.DAVActiveLock>();
        }
        
        public DAVLockDiscovery(Map<URI,WebDAV.DAVActiveLock> activeLocks) {
            this();
            this.activeLocks.putAll(activeLocks);
        }
        
        public DAVLockDiscovery(Collection<WebDAV.DAVActiveLock> activeLocks, int xxx) {
            this();
            for (WebDAV.DAVActiveLock lock : activeLocks) {
                this.add(lock);
            }
        }
        
        public DAVLockDiscovery(WebDAV.DAVActiveLock activeLock) {
            this();
            this.add(activeLock);
        }

        public DAVLockDiscovery(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            this();
            
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getLocalName().compareTo("activelock") == 0) {
                        WebDAV.DAVActiveLock lock = new WebDAV.DAVActiveLock(node.getChildNodes());
                        this.add(lock);
                    }
                }
            }
        }
        
        public void add(WebDAV.DAVActiveLock...property) {
            for (WebDAV.DAVActiveLock p : property) {
                this.activeLocks.put(p.getLockToken().getHref().getURI(), p);
            }
        }

        public Set<URI> keySet() {
            return this.activeLocks.keySet();
        }
        
        public DAVActiveLock get(URI key) {
            return this.activeLocks.get(key);
        }
        
        public int size() {
            return this.activeLocks.size();
        }
        
        public DAV.LockDiscovery toXML(DAV d) {
            DAV.LockDiscovery result = d.newLockDiscovery();
            for (WebDAV.DAVActiveLock lock : this.activeLocks.values()) {
                result.add(lock.toXML(d));
            }
            return result;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder();
            for (WebDAV.DAVActiveLock lock : this.activeLocks.values()) {
                result.append(" ").append(lock.toString());
            }
            return result.toString();
        }

        public Iterator<DAVActiveLock> iterator() {
            return this.activeLocks.values().iterator();
        }

        public WebDAV.DAVActiveLock get(HTTP.Resource.State.Token stateToken) {
            if (stateToken instanceof HTTP.Resource.State.CodedURL) {
                HTTP.Resource.State.CodedURL token = (HTTP.Resource.State.CodedURL) stateToken;
                return this.activeLocks.get(token.getCodedURL());                
            }
            throw new IllegalArgumentException("parameter must be an instance of HTTP.Resource.State.CodedURL");
        }
    }
    
    /**
     * A {@code DAVMulitstatus} object contains zero or more {@link WebDAV.DAVResponse} objects.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */  
    public static class DAVMultistatus implements Iterable<WebDAV.DAVResponse> {
        private Collection<WebDAV.DAVResponse> response;
        private boolean onlyErrors;

        /**
         * Construct a new {@code DAVMultistatus} instance containing the initial Collection of {@link WebDAV.DAVResponse}
         * instances and only recording additional instances that are errors.
         * @param response The initial Collection of {@code WebDAV.DAVResponse} instances for this {@code DAVMultistatus} object.
         * @param onlyErrors If {@code true}, future invocations of the {@link WebDAV.DAVMultistatus#add(WebDAV.DAVMultistatus)}
         * or {@link WebDAV.DAVMultistatus#add(WebDAV.DAVResponse)}
         * method on this instance will only record {@code DAVResponse} objects that contain error status. 
         */
        public DAVMultistatus(Collection<WebDAV.DAVResponse> response, boolean onlyErrors) {
            this.response = response;
            this.onlyErrors = onlyErrors;
        }

        public DAVMultistatus(Collection<WebDAV.DAVResponse> response) {
            this(response, false);
        }
        
        public DAVMultistatus() {
            this(new LinkedList<WebDAV.DAVResponse>(), false);
        }
        
        public DAV.MultiStatus toXML(DAV d) {
            DAV.MultiStatus result = d.newMultiStatus();
            for (WebDAV.DAVResponse r : this.response) {
                result.add(r.toXML(d));
            }
            return result;
        }

        public Iterator<DAVResponse> iterator() {
            return this.response.iterator();
        }

        public void add(DAVResponse r) {
            if (this.onlyErrors) {
                if (!r.getStatus().getStatus().isSuccessful()) {
                    this.response.add(r);
                }                
            } else {
                this.response.add(r);
            }
        }

        public void add(DAVMultistatus multiStatus) {
            for (DAVResponse r : multiStatus) {
                this.add(r);
            }
        }
        
        /**
         * Return the total number of {@link DAVResponse} instances in this object.
         * 
         * @return the total number of {@link DAVResponse} instances in this object.
         */
        public int size() {
            return this.response.size();
        }

        /**
         * Create and return a new {@link WebDAV.DAVMultistatus} instance containing all of the {@link WebDAV.DAVResponse}
         * instances that contain a non-successful {@code DAVResponse}.
         * NOTE: This does not work for propstat responses.
         * @return a new {@link WebDAV.DAVMultistatus} instance containing all of the {@link WebDAV.DAVResponse}
         * instances that contain a non-successful {@code DAVResponse}.
         */
        public DAVMultistatus getErrors() {
            DAVMultistatus result = new DAVMultistatus(new LinkedList<WebDAV.DAVResponse>(), true);
            result.add(this);
            return result;
        }
    }

    /**
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVOwner {
        private XML.Node owner;
        
        public DAVOwner(String owner) {
            this.owner = new XML.Node(owner);
        }
        
        public DAVOwner(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    // XX This won't work.
                    this.owner = new XML.Node(node.getChildNodes());
                } else {
                    this.owner = new XML.Node(node.getTextContent());
                }
            }
        }
        
        public DAV.Owner toXML(DAV dav) {
            return dav.newOwner(this.owner);
        }
        
        public String toString() {
            return this.owner.toString();
        }
    }
    
    /**
     * <blockquote>
     * A generic container for properties defined on resources.
     * All elements inside a 'prop' XML element MUST define properties related to the resource,
     * although possible property names are in no way limited to those property names defined in this document or other standards.
     * This element MUST NOT contain text or mixed content
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVProp implements Iterable<WebDAV.Resource.Property> {
        private Collection<WebDAV.Resource.Property> properties;
        
        public DAVProp() {
            this.properties = new LinkedList<WebDAV.Resource.Property>();
        }
        
        public DAVProp(Collection<WebDAV.Resource.Property> properties) {
            this();
            this.properties.addAll(properties);
        }
        
        public DAVProp(Collection<WebDAV.Resource.Property.Name> names, int xxx) {
            this();
            for (WebDAV.Resource.Property.Name name : names) {
                this.add(new WebDAV.Resource.Property(name));
            }
        }
        
        public DAVProp(WebDAV.Resource.Property property) {
            this();
            this.add(property);
        }
        
        public DAVProp(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            this();

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getNamespaceURI() != null && node.getNamespaceURI().equals("")) {
                        throw new HTTP.BadRequestException(String.format("Property cannot have empty namespace: %s", node.toString()));
                    }
                    this.add(new WebDAV.Resource.Property(node.getNamespaceURI(), node.getLocalName(), node.getChildNodes()));
              }
          }
      }
        
        public void add(WebDAV.Resource.Property...property) {
            for (WebDAV.Resource.Property p : property) {
                this.properties.add(p);
            }
        }

        public void add(DAVProp other) {
            for (WebDAV.Resource.Property p : other.properties) {
                this.properties.add(p);
            }
        }
        
        public Collection<WebDAV.Resource.Property> getProperties() {
            return this.properties;
        }

        public Iterator<Property> iterator() {
            return this.properties.iterator();
        }
        
        public DAV.Prop toXML(DAV d) {
            DAV.Prop result = d.newProp();
            for (WebDAV.Resource.Property p : this.properties) {
                result.add(p.toXML(d));
            }
            return result;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder();
            for (WebDAV.Resource.Property p : this.properties) {
                result.append(p.toString());
            }
            return result.toString();
        }
    }
    
    /**
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVPropertyUpdate {
        public interface SubElement {
            public DAV.PropertyUpdate.SubElement toXML(DAV d);  

            public DAVProp getProp();
        }
        
        private Collection<DAVPropertyUpdate.SubElement> todo;
        
        public DAVPropertyUpdate() {
            this.todo = new LinkedList<DAVPropertyUpdate.SubElement>();
        }

        /**
         * Construct a DAVPropertyUpdate instance from a {@link org.w3c.dom.NodeList} instance.
         * @param nodeList
         * @throws HTTP.BadRequestException
         */
        public DAVPropertyUpdate(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            this();
            
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    if (node.getLocalName().compareTo("set") == 0) {
                        WebDAV.DAVSet setProp = new WebDAV.DAVSet(node.getChildNodes());
                        this.todo.add(setProp);
                    } else if (node.getLocalName().compareTo("remove") == 0) {
                        WebDAV.DAVRemove removeProp = new WebDAV.DAVRemove(node.getChildNodes());
                        this.todo.add(removeProp);
                    }
                }
            }
        }
        
        public Collection<DAVPropertyUpdate.SubElement> getUpdates() {
            return this.todo;
        }

        public DAV.PropertyUpdate toXML(DAV d) {
            DAV.PropertyUpdate result = d.newPropertyUpdate();
            for (DAVPropertyUpdate.SubElement e : this.todo) {
                result.add(e.toXML(d));
            }
            
            return result;
        }
    }
    
    /**
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVRemove implements DAVPropertyUpdate.SubElement {
        private DAVProp prop;

        public DAVRemove(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    this.prop = new WebDAV.DAVProp(node.getChildNodes());
                }
            }
        }

        public DAVRemove(DAVProp prop) {
            this.prop = prop;
        }

        public DAVProp getProp() {
            return this.prop;
        }

        public DAV.Remove toXML(DAV d) {
            DAV.Remove result = d.newRemove(this.prop.toXML(d));
            return result;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("set [");
            result.append(this.prop.toString()).append("]");

            return result.toString();
        }
    }
    
    /**
     * <pre>
     * &lt;!ELEMENT response (href, ((href*, status)|(propstat+)),
     *           error?, responsedescription? , location?) &gt;
     * </pre>
     * response href status
     * response href propstat+
     * 
     * <blockquote>
     * Holds a single response describing the effect of a method on resource and/or its properties.
     * </blockquote>
     * <blockquote>
     * The 'href' element contains an HTTP URL pointing to a WebDAV resource when used in the 'response' container.
     * A particular 'href' value MUST NOT appear more than once as the child of a 'response' XML element under a 'multistatus' XML element.
     * This requirement is necessary in order to keep processing costs for a response to linear time.
     * Essentially, this prevents having to search in order to group together all the responses by 'href'.
     * There are, however, no requirements regarding ordering based on 'href' values.
     * The optional precondition/postcondition element and 'responsedescription'
     * text can provide additional information about this resource relative to the request or result.
     * </blockquote>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVResponse {
        private URI href;
        private List<Collection<WebDAV.DAVPropstat>> propstat;
        private DAVStatus status;
        private String error;
        private String responseDescription;
        private DAVHref location;

        public DAVResponse(URI uri) {
            this.href = uri;
            this.propstat = new LinkedList<Collection<WebDAV.DAVPropstat>>();
        }
        
        public DAVResponse(URI uri, DAVStatus status, String responseDescription) {
            this(uri);
            this.status = status;
            this.responseDescription = responseDescription;
        }
        
        /**
         * Create a new instance specifying the URI this response applies to, and an associated status.
         * 
         * @param uri
         * @param status
         */
        public DAVResponse(URI uri, DAVStatus status) {
            this(uri, status, null);
        }

        public DAVResponse(URI uri, WebDAV.DAVPropstat...propstat) {
            this(uri);
            this.add(propstat);
        }
        
//        public DAVResponse(URI uri, Collection<WebDAV.DAVPropstat>...propstat) {
//            this(uri);
//            this.add(propstat);
//        }

        @SuppressWarnings("unchecked")
        public DAVResponse add(WebDAV.DAVPropstat...propstat) {
            Collection<WebDAV.DAVPropstat> collection = new LinkedList<WebDAV.DAVPropstat>();
            for (DAVPropstat c : propstat) {
                if (c != null) {
                    collection.add(c);
                }                
            }
            return this.add(collection);
        }
        
        public DAVResponse add(Collection<WebDAV.DAVPropstat>...propstat) {
            for (Collection<DAVPropstat> c : propstat) {
                if (c.size() > 0) {
                    this.propstat.add(c);
                }                
            }
            return this;
        }
        
        public URI getURI() {
            return this.href;
        }
        
        public DAVStatus getStatus() {
            return this.status;
        }
        
        /**
         * Return the Response description.
         * @return A String containing the Response description.
         */
        public String getResponseDescription() {
            return this.responseDescription;
        }
        
        public String getError() {
            return this.error;
        }
        
        public DAVHref getLocation() {
            return this.location;
        }
        
        public void setLocation(DAVHref location) {
            this.location = location;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (this.propstat != null) {
                result.append(this.href).append(" ").append(this.propstat.toString());
            } else {
                result.append(this.href).append(" \"").append(this.status.toString()).append("\"");
                if (this.responseDescription != null) {
                    result.append(" ").append(this.responseDescription);
                }
            }
            return result.toString();
        }
        
        public DAV.Response toXML(DAV d) {
            if (this.status == null) {
                DAV.Response result = d.newResponse(d.newHref(this.href));
                for (Collection<WebDAV.DAVPropstat> c : this.propstat) {
                    for (DAVPropstat propstat : c) {
                        result.add(propstat.toXML(d));                        
                    }
                }
                return result;
            }
            return d.newResponse(d.newHref(this.href), this.status.toXML(d));
        }
    }
    
    /**
     * <blockquote>
     * Groups together a prop (which may have multiple properties) and status element that
     * is associated with a particular 'href' element in a {@code response} element.
     * </blockquote>
     * <blockquote>
     * The propstat XML element MUST contain one prop XML element and one status XML element.
     * The contents of the prop XML element MUST only list the names of properties to which the result in the status element applies.
     * The optional precondition/postcondition element and 'responsedescription' text also apply to the properties named in 'prop'.
     * </blockquote>
     * <pre>
     * &lt;!ELEMENT propstat (prop, status, error?, responsedescription?) &gt;
     * </pre>
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVPropstat {
        private DAVStatus status;
        private DAVProp prop;
        private DAVError error;
        private String responseDescription;
        
        public DAVPropstat(DAVProp prop, DAVStatus status, DAVError error, String responseDescription) {
            this.status = status;
            this.prop = prop;
            this.error = error;
            this.responseDescription = responseDescription;
        }
        
        public DAVPropstat(DAVProp prop, DAVStatus status) {
            this(prop, status, null, null);
        }
        
        public DAVProp getProp() {
            return this.prop;
        }
        
        public DAVStatus getStatus() {
            return this.status;
        }
        
        public DAV.Propstat toXML(DAV d) {
            DAV.Propstat result = d.newPropstat(this.prop.toXML(d));
            result.add(this.status.toXML(d));
            return result;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder(this.prop.toString()).append(" status=\"").append(this.status.toString()).append("\"");
            if (this.error != null)
                result.append(" \"").append(this.error.toString()).append("\"");
            if (this.responseDescription != null)
                result.append(" \"").append(this.responseDescription.toString()).append("\"");

            return result.toString();
        }
    }

    /**
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVSet implements DAVPropertyUpdate.SubElement {
        private DAVProp prop;

        public DAVSet(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    this.prop = new WebDAV.DAVProp(node.getChildNodes());
                }
            }
        }

        public DAVSet(DAVProp prop) {
            this.prop = prop;
        }

        public DAVProp getProp() {
            return this.prop;
        }
        
        public DAV.Set toXML(DAV d) {
            DAV.Set result = d.newSet(this.prop.toXML(d));
            return result;
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder("set [");
            result.append(this.prop.toString()).append("]");

            return result.toString();
        }
    }
    
    /**
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class DAVStatus {
        private HTTP.Response.Status status;
        
        public DAVStatus(HTTP.Response.Status status) {
            this.status = status;
        }
        
        @Override
        public int hashCode() {
            return this.status.hashCode();
        }
        
        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (this == other)
                return true;
            if (other instanceof DAVStatus) {
                DAVStatus o = (DAVStatus) other;
                if (this.status == o.status)
                    return true;
            }
            return false;            
        }

        public boolean isSuccessful() {
            return this.status.isSuccessful();
        }

        public HTTP.Response.Status getStatus() {
            return this.status;
        }
        
        public DAV.Status toXML(DAV d) {
            return d.newStatus(this.toString());
        }
        
        public String toString() {
            return "HTTP/1.1 " + this.status.toString();
        }
    }
    
    /**
     * <a href="http://webdav.org/specs/rfc4918.html#HEADER_Timeout">Timeout</a>
     *
     */
    public static class DAVTimeout {
        private String timeout;
        
        /**
         * Create a timeout representing infinity.
         */
        public DAVTimeout() {
            this.timeout = "Infinite";
        }
        
        public DAVTimeout(int value) {
            this.timeout = String.format("Second-%d", value);
        }
        
        public DAVTimeout(org.w3c.dom.NodeList nodeList) throws HTTP.BadRequestException {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                String string = node.getTextContent();

                if (string.compareToIgnoreCase("Infinite") == 0) {
                    this.timeout = "Infinite";
                } else {
                    this.timeout = string;
                }     
            }
        }
        
        public DAV.Timeout toXML(DAV d) {
            return d.newTimeout(this.toString());
        }
        
        public String toString() {
            return this.timeout;
        }
    }
 
    
    /**
     * A WebDAV (RFC 4918) Resource.
     * <p>
     * A WebDAV resource is an {@link HTTP.Resource} extended to support WebDAV capabilities such as properties, moving, copying, etc.
     * </p>
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public interface Resource extends HTTP.Resource {  
        /**
         * Get the {@link WebDAV.Backend} that contains this resource.
         * 
         * @return the {@link WebDAV.Backend} that contains this resource.
         */
        public WebDAV.Backend getBackend();
        
        /**
         * Copy this resource to another named by {@code destination}.
         *
         * @return {@link HTTP.Response.Status#CREATED} if the source was successfully copied after the successful creation of a new destination resource.<br/>
         *         {@link HTTP.Response.Status#NO_CONTENT} if the source was successfully copied to a preexisting destination resource.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
         * @throws HTTP.LockedException the source or destination resource of a method is locked.
         * @throws HTTP.PreconditionFailedException if the destination resource already exists.
         * @throws HTTP.ForbiddenException the request is valid but cannot be performed.
         */
        public HTTP.Response.Status copy(URI destination)
        throws HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.NotFoundException, HTTP.GoneException,
               HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.MethodNotAllowedException, HTTP.ForbiddenException, HTTP.BadRequestException,
               HTTP.LockedException, HTTP.PreconditionFailedException, HTTP.ForbiddenException;
        
        /**
         * Move this resource to another name.
         * 
         * @param destination the URI (from which only the path component is used) of the new resource.
         * @return {@link HTTP.Response.Status#CREATED} upon successful creation of the new resource.
         * 
         * @throws HTTP.MethodNotAllowedException if the operation is not allowed for the resource.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.PreconditionFailedException if the destination resource already exists.
         * @throws HTTP.LockedException the source or destination resource of a method is locked.
         * @throws HTTP.ForbiddenException the request is valid but cannot be performed.
         */
        public HTTP.Response.Status move(URI destination)
        throws HTTP.MethodNotAllowedException, HTTP.InternalServerErrorException, HTTP.BadRequestException, HTTP.GoneException, HTTP.InsufficientStorageException,
        HTTP.ConflictException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.PreconditionFailedException, HTTP.LockedException, HTTP.ForbiddenException;
        
        /**
         * Get the member resources of a WebDAV collection.
         * 
         * @return a {@link Collection} containing the members of this WebDAV collection.
         *
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         */
        public Collection<WebDAV.Resource> getCollection()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.GoneException, HTTP.ConflictException, HTTP.BadRequestException;
              
        /**
         * Set the properties for this resource.
         * <p>
         * The given Map becomes the set of properties returned on a subsequent invocation of {@link #getProperties()}.
         * </p>
         * <p>
         * Implementors must be careful to ensure that properties are stored with the UTF-8 character set.  See {@link OutputStreamWriter}. 
         * </p>
         * 
         * @param properties the {@link Collection} of {@link WebDAV.Resource.Property} to set.
         *
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.LockedException the source or destination resource of a method is locked.
         */
        public void setProperties(Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> properties)
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, HTTP.InsufficientStorageException;
        
        /**
         * Get the properties for this resource.
         * 
         * @return This resource's properties as a Map of {@link WebDAV.Resource.Property.Name} to {@link WebDAV.Resource.Property} instances.
         * 
         * @throws HTTP.ConflictException if this resource cannot be accessed at the destination until one or more intermediate collections have been created.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         */
        public Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> getProperties()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Create a collection named by this resource.
         * <p>
         * The resource must not already exist in the backend.
         * If the resource already exists, this will throw {@link HTTP.MethodNotAllowedException}
         * containing the set of operations that this resource will respond to. 
         * </p>
         * 
         * @throws HTTP.MethodNotAllowedException MKCOL can only be executed on an unmapped URL.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.InsufficientStorageException the server is unable to store data needed to successfully complete the request.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException the collection cannot be made at the Request-URI until one or more intermediate collections have been created.
         * @throws HTTP.ForbiddenException the request is valid but the server does not allow the creation of collections at the given location in its URL namespace,
         *                                 or the parent collection of the Request-URI exists but cannot accept members.
         * @throws HTTP.BadRequestException if the request could not be understood by the server due to malformed syntax.
         * @throws HTTP.LockedException the source or destination resource of a method is locked.
         */
        public void createCollection()
        throws HTTP.MethodNotAllowedException, HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.GoneException,
        HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.ForbiddenException, HTTP.BadRequestException, HTTP.LockedException;
        
        /**
         * Return {@code true} if this resource is a collection.
         * 
         * @throws HTTP.ConflictException if this resource cannot be created at the destination until one or more intermediate collections have been created.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         */
        public boolean isCollection()
        throws HTTP.InternalServerErrorException, HTTP.UnauthorizedException, HTTP.GoneException, HTTP.ConflictException;
        
        /**
         * Get the Collection of String containing the DAV Compliance Classes that this resource implements.
         * <p>
         * From <cite><a href="http://webdav.org/specs/rfc4918.html#dav.compliance.classes">RFC 4918 18. DAV Compliance Classes</a></cite>
         * 
         * <blockquote>
         * A DAV-compliant resource can advertise several classes of compliance.
         * A client can discover the compliance classes of a resource by executing OPTIONS on the resource and examining the "DAV" header which is returned.
         * Note particularly that resources, rather than servers, are spoken of as being compliant.
         * That is because theoretically some resources on a server could support different feature sets. 
         * For example, a server could have a sub-repository where an advanced feature like versioning was supported,
         * even if that feature was not supported on all sub-repositories.
         * </blockquote>
         * 
         * @return a formatted String containing the DAV Compliance Classes that this resource implements.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public Collection<String> getComplianceClass()
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get an ordered {@link List} of all the parent resources of this resource.
         * 
         * @return an ordered {@link List} of all the parent resources of this resource.
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         */
        public List<WebDAV.Resource> getParents() throws HTTP.GoneException, HTTP.NotFoundException, HTTP.InternalServerErrorException, HTTP.UnauthorizedException;
        
        /**
         * Get the value of the {@code DAV:getcontentlength} property in the number of octets of data available from this resource.
         * <p>
         * The {@code DAV:getcontentlength} property <b>MUST</b> be defined on any DAV-compliant resource that returns the <code>Content-Length</code> header in response to a {@code GET}.
         * </p>
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found, or the property does not exist for this resource.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public long getContentLength() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get the value of {@code DAV:creationdate} property in milliseconds since midnight, January 1, 1970 UTC.
         * <p>
         * The {@code DAV:creationdate} should be defined on all DAV compliant resources.
         * If present, it contains an RFC 3339 timestamp of the moment when the resource was created.
         * Implementations that are incapable of persistently recording the creation date should instead return 0.
         * </p>
         * @return The value of {@code DAV:creationdate} property in milliseconds since midnight, January 1, 1970 UTC.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @return The String representation of the {@code DAV:creationdate} property
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found, or the property value cannot be determined.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public long getCreationDate() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get the {@code DAV:displayname} property.
         * <p>
         * The <code>DAV:displayname</code> property contains a description of the resource that is suitable for presentation to a user.
         * This property is defined on the resource, and hence should have the same value independent of the Request-URI used to retrieve it
         * (thus, computing this property based on the Request-URI is deprecated).
         * While generic clients might display the property value to end users,
         * client UI designers must understand that the method for identifying resources is still the URL.
         * <p>
         * Changes to <code>DAV:displayname</code> do not issue moves or copies to the server, but simply change a piece of meta-data on the individual resource.
         * Two resources can have the same <code>DAV:displayname</code> value even within the same collection.
         * </p>
         * 
         * @return The String representation of the <code>DAV:displayname</code> property.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if this resource cannot be found, or the property value  cannot be determined.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public String getDisplayName() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get the value of this resource's {@code DAV:getetag} property.
         * <p>
         * The getetag property MUST be defined on any DAV-compliant resource that returns the Etag header.
         * Refer to <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.19">Section 3.11 of RFC 2616</a>
         * for a complete definition of the semantics of an {@code ETag},
         * and to <a href="http://webdav.org/specs/rfc4918.html#etag">RFC 4918 Section 8.6</a> for a discussion of ETags in WebDAV.
         * </p>
         * 
         * @return The String representation of the {@code DAV:getetag} property.
         * 
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found, or the property value cannot be determined.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public String getETag() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
        
        /**
         * Get the value of the {@code DAV:getlastmodified} property in milliseconds since midnight, January 1, 1970 UTC.
         * <p>
         * The last-modified date on a resource SHOULD only reflect changes in the body (the GET responses) of the resource.
         * A change in a property only SHOULD NOT cause the last-modified date to change,
         * because clients MAY rely on the last-modified date to know when to overwrite the existing body.
         * The {@code DAV:getlastmodified} property MUST be defined on any DAV-compliant resource
         * that returns the {@code Last-Modified} header in response to a GET.
         * 
         * @return The value of the {@code DAV:getlastmodified} property in milliseconds since midnight, January 1, 1970 UTC.
         *
         * @throws HTTP.InternalServerErrorException if this method encounters an irrecoverable processing error that cannot be expressed more completely.
         * @throws HTTP.NotFoundException if the resource named by the given {@code URI} cannot be found, or the property value  cannot be determined.
         * @throws HTTP.GoneException if the resource once was, but is no longer, available.
         * @throws HTTP.UnauthorizedException if the given identity is not authorized to access this resource.
         * @throws HTTP.ConflictException if this resource cannot be located at the destination because one or more intermediate collections do not exist.
         */
        public long getLastModified() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;

        /**
         * A WebDAV Resource Property
         * See <a href="http://webdav.org/specs/rfc4918.html#dav.properties">15. DAV Properties</a>
         * <p>
         * Properties may be the subject of get, set, and remove.
         * </p>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */ 
        public class Property  {
            private Property.Name name;
            private String value;
            private boolean protect;
            
            /**
             * Construct a representation of an un-protected property.
             * @param name The {@link Property.Name} of the property.
             */
            public Property(Property.Name name) {
                this.name = name;
                this.value = null;
                this.protect = false;
            }

            /**
             * Construct a representation of a WebDAV Resource property.
             * 
             * @param name The {@link Property.Name} of the property.
             * @param value The String value of the property.
             * @param protect If {@code true} the property is a protected property, otherwise the property is an un-protected property.
             */
            public Property(Property.Name name, String value, boolean protect) {
                this.name = name;
                this.value = value;
                this.protect = protect;
            }
            
            /**
             * Construct a new Property instance named by the composite of {@code nameSpace} and {@code name}, assigning
             * the value from the given {@link org.w3c.dom.NodeList}.
             * 
             * @param nameSpace A String containing the XML namespace of this Property.
             * @param name A String containing the name of this Property;
             * @param nodes A NodeList containing the XML text value of this Property.
             * @see sunlabs.asdf.web.http.WebDAV.Resource.Property.Name
             */
            public Property(String nameSpace, String name, org.w3c.dom.NodeList nodes) {
                this.name = new Property.Name(nameSpace, name);
                this.value = null;
                
                if (nodes != null) {
                    Node node = nodes.item(0);
                    
                    if (node != null) {
                        this.value = node.getTextContent();
                    }
                }
            }
            
            /**
             * Construct a new Property instance named by the composite of {@code nameSpace} and {@code name}.
             * 
             * @param nameSpace A String containing the XML namespace of this Property.
             * @param name A String containing the name of this Property;
             * @param value A String containing the value of this Property.
             * @see sunlabs.asdf.web.http.WebDAV.Resource.Property.Name
             */
            public Property(String nameSpace, String name, String value) {
                this.name = new Property.Name(nameSpace, name);
                this.value = value == null ? null : value;
            }
            
            /**
             * Construct a new Property instance named by the composite of {@code nameSpace} and {@code name}, with no value.
             * 
             * @param nameSpace A String containing the XML namespace of this Property.
             * @param name A String containing the name of this Property.
             * @see sunlabs.asdf.web.http.WebDAV.Resource.Property.Name
             */
            public Property(String nameSpace, String name) {
                this.name = new Property.Name(nameSpace, name);
                this.value = null;
            }
            
            /**
             * Get the name of this property.
             * @return The name of this property.
             */
            public Property.Name getName() {
                return this.name;
            }
            
            /**
             * Get the canonical representation of this Property's full name.
             * 
             * @return The canonical representation of this Property's full name.
             */
            public String getCanonicalName() {
                return this.name.toString();
            }

            /**
             * Get the value of this property.
             * @return The value of this property.
             */
            public String getValue() {
                return this.value;
            }

            public boolean isLive() {
                return false;
            }

            /**
             * Return {@code true} if this property is protected.
             * 
             * @return {@code true} if this property is protected.
             */
            public boolean isProtected() {
                return this.protect;
            }
            
            /**
             * Produce a well-formed XML.Node instance for this property.
             *  
             * @param d
             */
            public XML.Node toXML(DAV d) {
                
                if (this.name.getNameSpace() != null) {
                    if (this.name.getNameSpace().compareTo(d.getNameSpace().getValue()) != 0) {
                        DAV x = new DAV(new XML.NameSpace("x", this.name.getNameSpace()));
                        XML.Node node = x.newElement(this.name.getName());
                        if (this.value != null) {
                            node.addCDATA(this.value);
                        }
                        node.setAttribute(node.getNameSpace());
                        return node;
                    }
                } else { // nameSpace == null
                    if (d.getNameSpace() != null) {
                        DAV x = new DAV(new XML.NameSpace(""));
                        XML.Node node = x.newElement(this.name.getName());
                        if (this.value != null) {
                            node.addCDATA(this.value);
                        }
                        node.removeAttribute(node.getNameSpace());
                        return node;
                    }
                }
                XML.Node node = d.newElement(this.name.getName());
                if (this.value != null) {
                    node.addCDATA(this.value);
                }
                return node;
            }
            
            public String toString() {
                if (this.value == null) {
                    return String.format("%s", this.name);
                }
                return String.format("%s=\"%s\"", this.name, this.value);
            }
            
            /**
             * A WebDAV Property name.
             * 
             * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
             */
            public static class Name {
                private String nameSpace;
                private String name;
                
                public Name(String nameSpace, String name) {
                    this.nameSpace = nameSpace;
                    this.name = name;
                }
                
                /**
                 * Create a new instance of the WebDAV resource property name, <code>canonicalName</code>.
                 * @param canonicalName
                 */
                public Name(String canonicalName) {
                    String[] tokens = Property.fromCanonicalName(canonicalName);
                    this.nameSpace = tokens[0];
                    this.name = tokens[1];
                }
                
                @Override
                public int hashCode() {
                    return this.toString().hashCode();
                }
                
                @Override
                public boolean equals(Object other) {
                    if (other == null)
                        return false;
                    if (this == other)
                        return true;
                    return this.toString().equals(other.toString());                    
                }
                
                public String getNameSpace() {
                    return this.nameSpace;
                }
                
                public String getName() {
                    return this.name;
                }
                
                public String toString() {
                    return WebDAV.Resource.Property.canonicalisePropertyName(this.nameSpace, this.name);
                }
            }
            
            /**
             * Given an XML namespace as a string, and a property name, return a String containing a canonicalised form of the combination.
             * <p>
             * If {@code nameSpace} is null, it's considered to be the empty (or "default")
             * namespace and is not included in the resulting canonical name consisting of just the {@code propertyName}.
             * </p>
             * @return a String containing a canonicalised form of a {@link WebDAV.Resource.Property}.
             * @param nameSpace A String containing the XML namespace of a Property.
             * @param propertyName A String containing the name of a Property.
             */
            public static String canonicalisePropertyName(String nameSpace, String propertyName) {
                if (nameSpace == null) {
                    return propertyName;
                }

                String result = String.format("{%s}%s", nameSpace, propertyName);
                return result;
            }
            
            /**
             * Convert a canonical property name into namespace and name components.
             *  
             * @see #canonicalisePropertyName(String, String)
             * @param canonicalName
             * @return a 2 element String array containing the property's namespace and name components
             */
            public static String[] fromCanonicalName(String canonicalName) {
                String[] tokens = canonicalName.split("}", 2);
                if (tokens.length > 1) {
                    return new String[] { tokens[0].substring(1), tokens[1] };
                } else {
                    return new String[] { null, tokens[0] };
                }

            }
        }
        
        /**
         * The "parent" interface for all resource compliance specification.
         * <p>
         * Sub-interfaces declare additional methods, properties and other requirements that implementations must provide.
         * </p>
         * 
         * <p>
         * From <cite><a href="http://webdav.org/specs/rfc4918.html#rfc.section.18">RFC 4918 &sect; 18. DAV Compliance Classes</a></cite>
         * </p>
         * <blockquote>
         * A DAV-compliant resource can advertise several classes of compliance.
         * A client can discover the compliance classes of a resource by executing OPTIONS on the resource and examining the "DAV" header which is returned.
         * Note particularly that resources, rather than servers, are spoken of as being compliant.
         * That is because theoretically some resources on a server could support different feature sets.
         * For example, a server could have a sub-repository where an advanced feature like versioning was supported, even if that
         * feature was not supported on all sub-repositories.
         * </blockquote>
         * <blockquote>
         * Since this document describes extensions to the HTTP/1.1 protocol, minimally all DAV-compliant resources, clients,
         * and proxies MUST be compliant with [RFC2616].
         * </blockquote>
         * <blockquote>
         * A resource that is class 2 or class 3 compliant must also be class 1 compliant.
         * </blockquote>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface Compliance {
            
        }
        
        /**
         * Classes implementing WebDAV compliance level 1 resources implement this interface.
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface DAV1 extends WebDAV.Resource, WebDAV.Resource.Compliance {

            public WebDAV.Resource.Property.Name creationDate = new WebDAV.Resource.Property.Name("DAV:", "creationdate");
            public WebDAV.Resource.Property.Name displayName = new WebDAV.Resource.Property.Name("DAV:", "displayname");
            public WebDAV.Resource.Property.Name getContentLength = new WebDAV.Resource.Property.Name("DAV:", "getcontentlength");
            public WebDAV.Resource.Property.Name getContentType = new WebDAV.Resource.Property.Name("DAV:", "getcontenttype");
            public WebDAV.Resource.Property.Name getETag = new WebDAV.Resource.Property.Name("DAV:", "getetag");
            public WebDAV.Resource.Property.Name getLastModified = new WebDAV.Resource.Property.Name("DAV:", "getlastmodified");
            public WebDAV.Resource.Property.Name resourceType = new WebDAV.Resource.Property.Name("DAV:", "resourcetype");
        }        

        /**
         * Classes implementing WebDAV compliance level 2 resources implement this interface.
         * <p>
         * From <cite><a href="http://webdav.org/specs/rfc4918.html#compliance-class-2">RFC 4918 18.2 Class 2</a></cite>
         * </p>
         * <blockquote>
         * A class 2 compliant resource MUST meet all class 1 requirements and support the LOCK method,
         * the DAV:supportedlock property, the DAV:lockdiscovery property, the Time-Out response header and the Lock-Token request header.
         * A class 2 compliant resource SHOULD also support the Timeout request header and the 'owner' XML element.
         * </blockquote>
         * <blockquote>
         * Class 2 compliant resources MUST return, at minimum, the values "1" and "2" in the DAV header on all responses to the OPTIONS method.
         * </blockquote>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface DAV2 extends WebDAV.Resource.DAV1 {
            
            /**
             * Get current value of the {@code lockdiscovery} property for this resource.
             * 
             * @return The DAVActiveLock representation of the current value of the {@code lockdiscovery} property for this resource.
             * @throws HTTP.InternalServerErrorException
             * @throws HTTP.NotFoundException
             * @throws HTTP.GoneException
             * @throws HTTP.UnauthorizedException
             * @throws HTTP.ConflictException
             * @throws HTTP.BadRequestException
             */
            public WebDAV.DAVLockDiscovery getLock()
            throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
            
            /**
             * Get all of the locks that affect this resource and return a Map,
             * keyed by the {@link WebDAV.Resource} holding the lock and the value the {@link WebDAV.DAVLockDiscovery} instance of that lock.
             * 
             * @return a Map, keyed by the {@link WebDAV.Resource} holding the lock and the value the {@link WebDAV.DAVLockDiscovery} instance of that lock. 
             * @throws HTTP.InternalServerErrorException
             * @throws HTTP.NotFoundException
             * @throws HTTP.GoneException
             * @throws HTTP.UnauthorizedException
             * @throws HTTP.ConflictException
             */
            public Map<WebDAV.Resource,WebDAV.DAVLockDiscovery> getLocks()
            throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
                
            /**
             * 
             * @param locks
             * @throws HTTP.InternalServerErrorException
             * @throws HTTP.NotFoundException
             * @throws HTTP.GoneException
             * @throws HTTP.UnauthorizedException
             * @throws HTTP.ConflictException
             * @throws HTTP.LockedException
             * @throws InsufficientStorageException
             * @throws HTTP.BadRequestException
             */
            public void setLock(WebDAV.DAVLockDiscovery locks) throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException,
                HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, InsufficientStorageException, HTTP.BadRequestException;
                
            public WebDAV.Resource.Property.Name lockDiscovery = new WebDAV.Resource.Property.Name("DAV:", "lockdiscovery");
            public WebDAV.Resource.Property.Name supportedLock = new WebDAV.Resource.Property.Name("DAV:", "supportedlock");
        }        

        /**
         * Classes implementing WebDAV compliance level 3 resources implement this interface.
         * 
         * From <cite><a href="http://webdav.org/specs/rfc4918.html#compliance-class-3">RFC 4918 18.3 Class 3</a></cite>
         * <blockquote>
         * A resource can explicitly advertise its support for the revisions to [RFC2518] made in this document.
         * Class 1 MUST be supported as well.
         * Class 2 MAY be supported.
         * Advertising class 3 support in addition to class 1 and 2 means that the server supports all the requirements in this specification.
         * Advertising class 3 and class 1 support, but not class 2, means that the server supports all the requirements in this specification
         * except possibly those that involve locking support.
         * </blockquote>
         * 
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
         */
        public interface DAV3 extends WebDAV.Resource.DAV1 {
            
        }

//        /**
//         * Return {@code true} if this resource is locked and the given {@link HTTP.Message.Header.If.Conditional} does not contain
//         * suitable lock information to override the lock.
//         * <p>
//         * Note that for a resource to support locking, it MUST implement the {@link WebDAV.Resource.DAV2} Java interface.
//         * Resource implementations that do not implement that interface cannot support locking and this method must always return {@code false}.
//         * </p>
//         * @param conditional
//         * @return {@code true} if this resource is locked and the given {@code conditionList} does not contain a suitable lock token to override the lock.
//         * @throws HTTP.InternalServerErrorException
//         * @throws HTTP.NotFoundException
//         * @throws HTTP.GoneException
//         * @throws HTTP.UnauthorizedException
//         * @throws HTTP.ConflictException
//         */
//        public boolean isLocked(HTTP.Message.Header.If.Conditional conditional)
//        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException;
    }
}
