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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import sunlabs.asdf.web.XML.DAV;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP.ForbiddenException;
import sunlabs.asdf.web.http.HTTP.LockedException;
import sunlabs.asdf.web.http.HTTP.MethodNotAllowedException;
import sunlabs.asdf.web.http.HTTP.Message.Header.Depth.Level;
import sunlabs.asdf.web.http.WebDAV.DAVAllprop;
import sunlabs.asdf.web.http.WebDAV.DAVLockDiscovery;
import sunlabs.asdf.web.http.WebDAV.DAVMultistatus;
import sunlabs.asdf.web.http.WebDAV.DAVResponse;
import sunlabs.asdf.web.http.WebDAV.Resource;

/**
 *  A basic RFC 4918 implementation of WebDAV.
 *  
 *  @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsytems, Inc.
 *
 */
public class WebDAVNameSpace extends NameSpace {
    protected WebDAV.Backend backend;

    public static Document parseXMLBody(HTTP.Request request) throws HTTP.BadRequestException, HTTP.InternalServerErrorException {

        try {
            // XXX There is some problem in the interaction between the HTTP.Message.Body.toInputStream() and the DocumentBuilder that makes the builder hang waiting for input.
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            request.getMessage().getBody().writeTo(new DataOutputStream(os));
            return parseXMLBody(new ByteArrayInputStream(os.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document parseXMLBody(byte[] bytes) throws HTTP.BadRequestException, HTTP.InternalServerErrorException {
        return parseXMLBody(new ByteArrayInputStream(bytes));
    }

    public static Document parseXMLBody(InputStream in) throws HTTP.BadRequestException, HTTP.InternalServerErrorException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setCoalescing(true);
            dbf.setIgnoringComments(true);
            dbf.setNamespaceAware(true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(in);
            in.close();
            doc.getDocumentElement().normalize();
            return doc;
        } catch (ParserConfigurationException e) {
            throw new HTTP.InternalServerErrorException(e.toString(), e);            
        } catch (SAXException e) {
            throw new HTTP.BadRequestException(e.toString());            
        } catch (IOException e) {
            throw new HTTP.InternalServerErrorException(e.toString(), e);
        }
    }
    

    /**
     * Pretty print a {@link NodeList}
     *
     * @param nodes
     * @param indentation
     */
    public static void displayNodeList(NodeList nodes, int indentation) {
        if (nodes != null) {
            StringBuilder x = new StringBuilder();
            for (int i = 0; i < indentation; i++) {
                x.append(" ");
            }
            String indent = x.toString();
            for (int s = 0; s < nodes.getLength(); s++) {
                Node node = nodes.item(s);
                System.out.printf("%stype=%3d name=%s value='%s' '%s'%n", indent, node.getNodeType(), node.getNodeName(), node.getNodeValue(), node.getTextContent());
                NodeList children = node.getChildNodes();
                if (children.getLength() > 0) {
                    displayNodeList(children, indentation+2);
                }
            }
        }
    }
    
    public static void displayElements(Element element, int indentation) {
        StringBuilder x = new StringBuilder();
        for (int i = 0; i < indentation; i++) {
            x.append(" ");
        }
        String indent = x.toString();

        System.out.printf("%snsURI='%s' localName='%s'%n", indent, element.getNamespaceURI(), element.getLocalName());
        NodeList nodes = element.getChildNodes();
        for (int s = 0; s < nodes.getLength(); s++) {
            Node node = nodes.item(s);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                System.out.printf("%snsURI='%s' localName='%s' nodeValue=%s%n", indent, node.getNamespaceURI(), node.getLocalName(), node.getNodeValue());
                NodeList children = node.getChildNodes();
                if (children.getLength() > 0) {
                    displayNodeList(children, indentation+2);
                }
            }
        }
    }
    
    protected static boolean isLocked(HTTP.Resource resource, HTTP.Message.Header.If ifHeader)
    throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.BadRequestException {
        HTTP.Resource.State state = resource.getResourceState();

        // Each state-token must be in the ifHeader
        if (state.getStateTokens().size() == 0)
            return false;

        if (ifHeader == null) {
            return true;
        }

        Collection<HTTP.Resource.State.Token> submittedStateTokens = ifHeader.getSubmittedStateTokens();
        for (HTTP.Resource.State.Token stateToken : state.getStateTokens()) {
            if (submittedStateTokens.contains(stateToken) == false) {
                return true;
            }
        }
        return false;            
    }

    /**
     *  A basic RFC 4918 implementation of WebDAV.
     *  
     * @param server An instance of an {@link HTTP.Server}.
     * @param backend An instance of a {@link WebDAV.Backend}.
     */
    public WebDAVNameSpace(HTTP.Server server, WebDAV.Backend backend) {
        super(server, backend);

        this.backend = backend;

        this.add(HTTP.Request.Method.COPY, new WebDAVCopy(this.server, this.backend));
        this.add(HTTP.Request.Method.DELETE, new WebDAVDelete(this.server, this.backend));
        this.add(HTTP.Request.Method.GET, new WebDAVGet(this.server, this.backend));
        this.add(HTTP.Request.Method.HEAD, new WebDAVGet(this.server, this.backend));
        this.add(HTTP.Request.Method.LOCK, new WebDAVLock(this.server, this.backend));
        this.add(HTTP.Request.Method.MKCOL, new WebDAVMkcol(this.server, this.backend));
        this.add(HTTP.Request.Method.MOVE, new WebDAVMove(this.server, this.backend));
        this.add(HTTP.Request.Method.POST, new WebDAVPost(this.server, this.backend));
        this.add(HTTP.Request.Method.PROPFIND, new WebDAVPropfind(this.server, this.backend));
        this.add(HTTP.Request.Method.PROPPATCH, new WebDAVProppatch(this.server, this.backend));
        this.add(HTTP.Request.Method.PUT, new WebDAVPut(this.server, this.backend));
        this.add(HTTP.Request.Method.OPTIONS, new WebDAVOptions(this.server, this.backend));
        this.add(HTTP.Request.Method.UNLOCK, new WebDAVUnlock(this.server, this.backend));
    }

    /**
     *
     * 201 (Created) - The source resource was successfully copied. The COPY operation resulted in the creation of a new resource.
     * 204 (No Content) - The source resource was successfully copied to a preexisting destination resource.
     * 207 (Multi-Status) - Multiple resources were to be affected by the COPY, but errors on some of them prevented the operation from taking place. Specific error messages, together with the most appropriate of the source and destination URLs, appear in the body of the multi-status response. For example, if a destination resource was locked and could not be overwritten, then the destination resource URL appears with the 423 (Locked) status.
     * 403 (Forbidden) - The operation is forbidden. A special case for COPY could be that the source and destination resources are the same resource.
     * 409 (Conflict) - A resource cannot be created at the destination until one or more intermediate collections have been created. The server MUST NOT create those intermediate collections automatically.
     * 412 (Precondition Failed) - A precondition header check failed, e.g., the Overwrite header is "F" and the destination URL is already mapped to a resource.
     * 423 (Locked) - The destination resource, or resource within the destination collection, was locked. This response SHOULD contain the 'lock-token-submitted' precondition element.
     * 502 (Bad Gateway) - This may occur when the destination is on another server, repository, or URL namespace. Either the source namespace does not support copying to the destination namespace, or the destination namespace refuses to accept the resource. The client may wish to try GET/PUT and PROPFIND/PROPPATCH instead.
     * 507 (Insufficient Storage) - The destination resource does not have sufficient space to record the state of the resource after the execution of this method.
     *
     */
    public static class WebDAVCopy implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVCopy(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.UnauthorizedException, HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.GoneException,
        HTTP.NotFoundException, HTTP.PreconditionFailedException, HTTP.ConflictException, HTTP.InsufficientStorageException, HTTP.MethodNotAllowedException,
        HTTP.ForbiddenException, HTTP.LockedException {
            // Ensure that the request contains a Destination: header
            HTTP.Message.Header.Destination destinationHeader = (HTTP.Message.Header.Destination) request.getMessage().getHeader(HTTP.Message.Header.DESTINATION);
            if (destinationHeader == null) {
                throw new HTTP.BadRequestException("Destination header must be present.");
            }

            if (request.getURI().getFragment() != null) {
                throw new HTTP.BadRequestException(String.format("Cannot COPY from URI with a fragment: %s", request.getURI().toString()));
            }

            URI destinationURI = destinationHeader.getURI();
            if (destinationURI.getFragment() != null) {
                throw new HTTP.BadRequestException(String.format("Cannot COPY to URI with a fragment: %s%n", destinationURI));
            }
              
            HTTP.Message.Header.Depth depthHeader = (HTTP.Message.Header.Depth) request.getMessage().getHeader(HTTP.Message.Header.DEPTH);                
            HTTP.Message.Header.Depth.Level depth = (depthHeader != null) ? depthHeader.getLevel() : HTTP.Message.Header.Depth.Level.INFINITY;
            HTTP.Message.Header.Overwrite overwriteHeader = (HTTP.Message.Header.Overwrite) request.getMessage().getHeader(HTTP.Message.Header.OVERWRITE);
            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

            WebDAV.Resource source = this.backend.getResource(request.getURI(), identity);
            WebDAV.Resource destination = this.backend.getResource(destinationHeader.getURI(), identity);

            HTTP.Response.Status status = HTTP.Response.Status.INTERNAL_SERVER_ERROR;
            if (destination.exists()) {
                System.out.printf("Copy: %s exists as a %s%n", destination, destination.isCollection() ? "collection" : "file");
                if (!overwriteHeader.getOverwrite()) {
                    System.out.printf("Copy: do not overwrite %s%n", destination);
                    throw new HTTP.PreconditionFailedException(destination.getURI(), "Attempted to overwrite existing resource, and overwrite flag is false.");
                }

                // Delete the destination.
                System.out.printf("Copy: overwrite %s%n", destination);
                WebDAVNameSpace.RecursiveOperation deleteOperation = new WebDAVNameSpace.DeleteOperation(ifHeader);

                DAVMultistatus result = WebDAVNameSpace.treeWalk2(destination, HTTP.Message.Header.Depth.Level.INFINITY, deleteOperation);
                DAVMultistatus resultErrors = result.getErrors();
                if (resultErrors.size() != 0) {
                    if (resultErrors.size() == 1) {
                        for (DAVResponse r : resultErrors) {
                            return new HttpResponse(r.getStatus().getStatus());
                        }     
                    }

                    DAV.MultiStatus multiStatus = resultErrors.toXML(new DAV());
                    multiStatus.bindNameSpace();
                    XML.Document document = new XML.Document(multiStatus);
                    return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
                }
                status = HTTP.Response.Status.NO_CONTENT;
            } else {
                status = HTTP.Response.Status.CREATED;
            }

            WebDAVNameSpace.RecursiveOperation copy = this.new CopyOperation(request.getURI(), destinationHeader.getURI(), overwriteHeader.getOverwrite(), ifHeader);

//            if (false) {
//                Map<String,DAVResponse> result = WebDAVNameSpace.treeWalk(source, depth, copy);
//
//                // Compute the set of error responses.
//                Map<String,DAVResponse> resultErrors = new HashMap<String,DAVResponse>();
//                for (Map.Entry<String,DAVResponse> entry : result.entrySet()) {
//                    if (!entry.getValue().getStatus().isSuccessful()) {
//                        resultErrors.put(entry.getKey(), entry.getValue());
//                    }
//                }
//                // If there were no errors, respond with either NO_CONTENT or CREATED, depending on whether or not the destination already existed, or was created.
//                if (resultErrors.size() == 0) {
//                    return new HttpResponse(status);
//                }
//
//                if (resultErrors.size() == 1) {
//                    for (Map.Entry<String,DAVResponse> entry : resultErrors.entrySet()) {
//                        return new HttpResponse(entry.getValue().getStatus().getStatus());
//                    }
//                }
//
//                DAV dav = new DAV();
//                DAV.MultiStatus multiStatus = dav.newMultiStatus();
//                for (String path : resultErrors.keySet()) {
//                    DAVResponse r = result.get(path);
//                    multiStatus.add(r.toXML(dav));
//                }
//
//                multiStatus.bindNameSpace();
//                XML.Document document = new XML.Document(multiStatus);
//                return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
//            } else {
                DAVMultistatus result = WebDAVNameSpace.treeWalk2(source, depth, copy);
                DAVMultistatus resultErrors = result.getErrors();
                if (resultErrors.size() != 0) {
                    if (resultErrors.size() == 1) {
                        for (DAVResponse r : resultErrors) {
                            return new HttpResponse(r.getStatus().getStatus());
                        }     
                    }

                    DAV.MultiStatus multiStatus = resultErrors.toXML(new DAV());
                    multiStatus.bindNameSpace();
                    XML.Document document = new XML.Document(multiStatus);
                    return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
                }

                return new HttpResponse(status);
//            }
        }
        
        public class CopyOperation implements WebDAVNameSpace.RecursiveOperation {
            private URI fromRoot;
            private URI destinationRoot;
            private boolean overWrite;
            private HTTP.Message.Header.If ifHeader;

            // This is a local copy operation.  It does not copy to other servers.
            public CopyOperation(URI fromRoot, URI toRoot, boolean overWrite, HTTP.Message.Header.If ifHeader) {
                this.fromRoot = fromRoot;
                this.destinationRoot = toRoot;
                this.overWrite = overWrite;
                this.ifHeader = ifHeader;
            }

            public WebDAV.DAVResponse preOperation(WebDAV.Resource source, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                // Compute the shared name of the source with the destination.
                // Copying /a/b/c from the source collection /a to collection /z results in a new resource /z/b/c
                int length = this.fromRoot.getPath().length();
                String relativeName = source.getURI().getPath().substring(length);
                if (relativeName.length() != 0) {
                    relativeName = "/" + relativeName;                    
                }

                try {
                    if (ifHeader != null) {
                        if (ifHeader.evaluate(source) == false) {
                            throw new HTTP.PreconditionFailedException(source.getURI(), "If condition failed.");
                        }
                        // XXX Is the same conditional supposed to be applied to the destination too??
                    }

                    URI destinationURI = new URI(this.destinationRoot.getPath() + relativeName).normalize();
                    WebDAV.Resource destination = source.getBackend().getResource(destinationURI, source.getIdentity());

                    if (WebDAVNameSpace.isLocked(destination, this.ifHeader)) {
                        return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.LOCKED));                    
                    }

                    HTTP.Response.Status status;

                    if (destination.exists()) {
                        if (this.overWrite == false) {
                            return new WebDAV.DAVResponse(destination.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.PRECONDITION_FAILED));
                        }
                        destination.delete();
                        status = HTTP.Response.Status.NO_CONTENT;
                    } else {
                        status = HTTP.Response.Status.CREATED;
                    }

                    if (source.isCollection()) {
                        destination.createCollection();
                    }
                    return new WebDAV.DAVResponse(destination.getURI(), new WebDAV.DAVStatus(status));
                } catch (URISyntaxException e) {
                    return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.BAD_REQUEST), this.destinationRoot.getPath() + relativeName);
                } catch (HTTP.Exception e) {
//                    System.out.printf("pre: %s%n", e.toString());
                    return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }

            public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
                int length = this.fromRoot.getPath().length();

                String relativeName = resource.getURI().getPath().substring(length);
                if (relativeName.length() != 0) {
                    relativeName = "/" + relativeName;                    
                }
                try {
                    URI destinationURI = new URI(this.destinationRoot.getPath() + relativeName).normalize();
                    try {
                        if (resource.isCollection()) {
                            return preResponse;
                        } else {
                            resource.copy(destinationURI);                            
                        }

                        return preResponse;
                    } catch (HTTP.Exception e) {
                        return new WebDAV.DAVResponse(e.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                    }
                } catch (URISyntaxException e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.BAD_REQUEST));
                }
            }
        }
    }

    public static class WebDAVDelete implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVDelete(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) throws HTTP.UnauthorizedException,
        HTTP.InternalServerErrorException,
        HTTP.GoneException,
        HTTP.NotFoundException,
        HTTP.BadRequestException,
        HTTP.ConflictException,
        HTTP.InsufficientStorageException,
        HTTP.LockedException,
        HTTP.MethodNotAllowedException  {

            if (request.getURI().getFragment() != null) {
                throw new HTTP.BadRequestException(request.getURI(), "Cannot delete URI with a fragment.");
            }

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

            WebDAVNameSpace.RecursiveOperation delete = new DeleteOperation(ifHeader);

            DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, resource.isCollection() ? HTTP.Message.Header.Depth.Level.INFINITY : HTTP.Message.Header.Depth.Level.ONLY, delete);
            DAVMultistatus resultErrors = result.getErrors();
            if (resultErrors.size() != 0) {
                if (resultErrors.size() == 1) {
                    for (DAVResponse r : resultErrors) {
                        return new HttpResponse(r.getStatus().getStatus());
                    }     
                }

                DAV.MultiStatus multiStatus = resultErrors.toXML(new DAV());
                multiStatus.bindNameSpace();
                XML.Document document = new XML.Document(multiStatus);
                return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
            }

            return new HttpResponse(HTTP.Response.Status.NO_CONTENT);
        }
    }

    public static class DeleteOperation implements WebDAVNameSpace.RecursiveOperation {
        private HTTP.Message.Header.If ifHeader;
        
        public DeleteOperation(HTTP.Message.Header.If ifHeader) {
            this.ifHeader = ifHeader;
        }
        

        public WebDAV.DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
            try {
                if (ifHeader != null) {
                    if (ifHeader.evaluate(resource) == false) {
                        throw new HTTP.PreconditionFailedException(resource.getURI(), "If condition failed.");
                    }
                }

                if (WebDAVNameSpace.isLocked(resource, this.ifHeader)) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.LOCKED));                    
                }

                return null;
            } catch (HTTP.Exception e) {
                return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()), e.getMessage());
            } finally {

            }
        }

        public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
            try {
                resource.delete();
                return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.OK));
            } catch (HTTP.Exception e) {
                return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()), e.getMessage());
            }
        }
    }

    /**
     * General implementation of a WebDAV GET request handler.
     *
     */
    public static class WebDAVGet implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVGet(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        // @URI("/*");
        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) throws
        HTTP.UnauthorizedException,
        HTTP.InternalServerErrorException,
        HTTP.GoneException,
        HTTP.NotFoundException,
        HTTP.BadRequestException,
        HTTP.ConflictException,
        HTTP.InsufficientStorageException,
        HTTP.LockedException,
        HTTP.MethodNotAllowedException {

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);
            if (!resource.exists()) {
                throw new HTTP.NotFoundException(request.getURI());
            }
            
            Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> properties = resource.getProperties();

            if (resource.isCollection()) {
                // Compose a web page with a table containing the names of the resources in the collection.
                XHTML.Body body = new XHTML.Body(new XHTML.Heading.H1(request.getURI()));

                XHTML.Table.Body tbody = new XHTML.Table.Body();
                Collection<WebDAV.Resource> contents = resource.getCollection();
                for (WebDAV.Resource r : contents) {
                    XHTML.Anchor link = new XHTML.Anchor(r.getDisplayName()).setHref(r.getURI());
                    tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(link), new XHTML.Table.Data(r.getContentType()), new XHTML.Table.Data(r.getContentLength())));
                }
                body.add(new XHTML.Table(tbody));
                XHTML.Document document = new XHTML.Document(new XHTML.Html(new XHTML.Head(), body));

                HTTP.Message.Body content = new HttpContent.Text.HTML(document);
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, content);
                WebDAV.Resource.Property eTag = properties.get(WebDAV.Resource.DAV2.getETag);

                response.getMessage().addHeader(new HttpHeader.ETag(eTag.getValue()));

                return response;
            } else {
                //                long offset = 0;
                InputStream input = resource.asInputStream();
                // XXX Fix this to support Range: and arbitrary length data.
                //                input.seek(offset);
                InternetMediaType contentType = resource.getContentType();

                // XXX This should check the resource's getContentLength() method to see if the content-length is known.
                // XXX If it is known, this should use the HttpContent.RawInputStream() constructor which specifies the content-length.
                HttpContent.RawInputStream content = new HttpContent.RawInputStream(new HttpHeader.ContentType(contentType), input);
                content.setContentLength(resource.getContentLength());
                content.setCloseInputStream(false);
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, content);
                WebDAV.Resource.Property eTag = properties.get(WebDAV.Resource.DAV2.getETag);
                response.getMessage().addHeader(new HttpHeader.ETag(eTag.getValue()));

                return response;                
            }
        }
    }

    public static class WebDAVHead implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVHead(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        // @URI("/*");
        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity) throws
        HTTP.UnauthorizedException,
        HTTP.InternalServerErrorException,
        HTTP.GoneException,
        HTTP.NotFoundException,
        HTTP.BadRequestException,
        HTTP.ConflictException,
        HTTP.InsufficientStorageException,
        HTTP.LockedException,
        HTTP.MethodNotAllowedException {

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            if (resource.isCollection()) {
                XHTML.Body body = new XHTML.Body(new XHTML.Para(""));

                XHTML.Table.Body tbody = new XHTML.Table.Body();
                Collection<WebDAV.Resource> contents = resource.getCollection();
                for (WebDAV.Resource r : contents) {
                    tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(r.getURI())));
                }
                body.add(new XHTML.Table(tbody));
                XHTML.Document document = new XHTML.Document(new XHTML.Html(new XHTML.Head(), body));

                HTTP.Message.Body content = new HttpContent.Text.HTML(document);
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, content);

                return response;
            } else {
                //                long offset = 0;
                InputStream input = resource.asInputStream();
                // XXX Fix this to support Range: and arbitrary length data.
                //                input.seek(offset);
                InternetMediaType contentType = resource.getContentType();

                HttpContent.RawInputStream content = new HttpContent.RawInputStream(new HttpHeader.ContentType(contentType), input);
                content.setCloseInputStream(true);
                HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK, content);

                return response;                
            }
        }
    }


    public static class WebDAVLock implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVLock(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HttpResponse execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException,
               HTTP.ConflictException, HTTP.LockedException, HTTP.InsufficientStorageException, HTTP.MethodNotAllowedException, HTTP.PreconditionFailedException, HTTP.ForbiddenException {

            // A missing Depth header indicated a Depth of 'infinity'
            HTTP.Message.Header.Depth depthHeader = (HTTP.Message.Header.Depth) request.getMessage().getHeader(HTTP.Message.Header.DEPTH);
            HTTP.Message.Header.Depth.Level depth = (depthHeader != null) ? depthHeader.getLevel() : HTTP.Message.Header.Depth.Level.INFINITY;
            if (depth == HTTP.Message.Header.Depth.Level.ALL) {
                throw new HTTP.BadRequestException("Values other than 0 or infinity MUST NOT be used with the Depth header on a LOCK method.");
            }

            HTTP.Message.Header.Timeout timeOutHeader = (HTTP.Message.Header.Timeout) request.getMessage().getHeader(HTTP.Message.Header.TIMEOUT);
            WebDAV.DAVTimeout timeout = timeOutHeader == null ? new WebDAV.DAVTimeout(60) : new WebDAV.DAVTimeout(timeOutHeader.getTimeOut());
            
            // XXX Fix this up.  There are problems with the interface relationships between the regular WebDAV.Resource and the extensions DAV1, DAV2, etc.
            WebDAV.Resource r = this.backend.getResource(request.getURI(), identity);
            if (!(r instanceof WebDAV.Resource.DAV2)) {
                throw new HTTP.MethodNotAllowedException(r.getAccessAllowed());
            }            
            
            WebDAV.Resource.DAV2 resource = (WebDAV.Resource.DAV2) r;

            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);
            if (ifHeader != null) {
                if (ifHeader.evaluate(resource) == false) {
                    throw new HTTP.PreconditionFailedException(request.getURI(), "If condition failed.");
                }
            }
            
            if (request.getMessage().getBody() == null || request.getMessage().getBody().contentLength() < 1) {
                // Refresh an existing lock.
                
                if (ifHeader == null || ifHeader.getSubmittedStateTokens().size() != 1) {
                    throw new HTTP.PreconditionFailedException(request.getURI(), "RFC 4918 Section 9.10.2: Request MUST specify which lock to refresh by using the 'If' header with a single lock token.");
                }

                // Find the existing lock that has the given state-token as the lock-token.
                for (HTTP.Resource.State.Token stateToken : ifHeader.getSubmittedStateTokens()) { // this one-time iterator loop is only to take apart the Collection that contains only one state-token. 
                    Map<WebDAV.Resource,DAVLockDiscovery> resourceLocks = resource.getLocks();
                    if (resourceLocks.size() == 0) {
                        throw new HTTP.PreconditionFailedException(resource.getURI(), "Resource is not locked.  No refresh.");
                    }
                    System.out.printf("locks: %s%n", resourceLocks);
                    for (WebDAV.Resource lockedResource : resourceLocks.keySet()) {
                        WebDAV.DAVLockDiscovery lock = resourceLocks.get(lockedResource);
                        // For each of the lockdiscovery sets, {@code lock}, find the lock with the state-token as the lock-token.
                        // If it's found, update it (refresh it) and put it back with it's corresponding resource.

                        WebDAV.DAVActiveLock activeLock = lock.get(stateToken);
                        if (activeLock != null) {
                            activeLock.setTimeout(timeout);
                            resource.setLock(lock);
                            DAV dav = new DAV();
                            XML.Node body = dav.newProp(lock.toXML(dav));
                            body.bindNameSpace();

                            return new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.XML(new XML.Document(body)));
                        }
                    }
                    throw new HTTP.PreconditionFailedException(request.getURI(), String.format("Resource not in the scope of lock token %s", stateToken));
                }
            }
            
            // Parse the XML request body.
            Document doc = parseXMLBody(request);
            
            Element lockElement = doc.getDocumentElement();
            
            if (lockElement.getLocalName().compareTo("lockinfo") == 0) {
                WebDAV.DAVLockInfo lockInfo = new WebDAV.DAVLockInfo(lockElement.getChildNodes());

                if (!resource.exists()) {
                    // Resource is "unmapped".
                    // 9.10.4 Locking Unmapped URLs
                    // A successful LOCK method MUST result in the creation of an empty resource that is locked (and that is not a collection)
                    // when a resource did not previously exist at that URL. Later on, the lock may go away but the empty resource remains.
                    // Empty resources MUST then appear in PROPFIND responses including that URL in the response scope.
                    // A server MUST respond successfully to a GET request to an empty resource, either by using a 204 No Content response,
                    // or by using 200 OK with a Content-Length header indicating zero length
                    resource.create(InternetMediaType.Application.OctetStream);

                    WebDAV.DAVLockDiscovery existingLock = resource.getLock();

                    // Generate a lockdiscovery property.

                    URI lockToken = URI.create("opaquelocktoken:" + java.util.UUID.randomUUID());
                    //                    URI lockToken = URI.create("opaquelocktoken:" + new java.util.UUID(0, 1));
                    WebDAV.DAVLockRoot lockRoot = new WebDAV.DAVLockRoot(request.getURI());

                    WebDAV.DAVActiveLock activeLock =
                        new WebDAV.DAVActiveLock(lockInfo.getScope(), lockInfo.getType(), depth, lockInfo.getOwner(), timeout, new WebDAV.DAVLockToken(lockToken), lockRoot);
                    existingLock.add(activeLock);

                    resource.setLock(existingLock);

                    // Compose and return the current value of the lockdiscovery property.
                    WebDAV.DAVLockDiscovery lockDiscovery = new WebDAV.DAVLockDiscovery(activeLock);
                    DAV dav = new DAV();
                    XML.Node body = dav.newProp(lockDiscovery.toXML(dav));
                    body.bindNameSpace();

                    HttpContent content = new HttpContent.Text.XML(new XML.Document(body));

                    HttpResponse response = new HttpResponse(HTTP.Response.Status.CREATED, content);
                    response.getMessage().addHeader(new HttpHeader.LockToken(lockToken));
                    return response;
                }

                WebDAV.DAVLockDiscovery existingLock = resource.getLock();
                if (existingLock.size() > 0) {
                    System.out.printf("Already locked%n%s%n", XML.formatXMLDocument(existingLock.toXML(new DAV()).bindNameSpace().toString()));
                    for (WebDAV.DAVActiveLock lock : existingLock) {
                        if (lock.getLockScope().isExclusive()) {
                            throw new HTTP.LockedException(resource.getURI(), XML.formatXMLDocument(existingLock.toXML(new DAV()).bindNameSpace().toString()));
                        } else { // lock is a shared lock
                            if (lockInfo.getScope().isExclusive()) {
                                throw new HTTP.LockedException(resource.getURI(), XML.formatXMLDocument(existingLock.toXML(new DAV()).bindNameSpace().toString()));
                            }
                        }
                        // Add another shared lock to this resource.
                        URI lockToken = URI.create("opaquelocktoken:" + java.util.UUID.randomUUID());
                        //                      URI lockToken = URI.create("opaquelocktoken:" + new java.util.UUID(0, 1));
                        WebDAV.DAVLockRoot lockRoot = new WebDAV.DAVLockRoot(new WebDAV.DAVHref(request.getURI()));
                        WebDAV.DAVActiveLock activeLock = new WebDAV.DAVActiveLock(lockInfo.getScope(), lockInfo.getType(), depth, lockInfo.getOwner(), timeout,
                                new WebDAV.DAVLockToken(lockToken), lockRoot);
                        existingLock.add(activeLock);

                        resource.setLock(existingLock);

                        // Compose and return the current value of the lockdiscovery property.
                        WebDAV.DAVLockDiscovery lockDiscovery = existingLock;
                        DAV dav = new DAV();
                        XML.Node body = dav.newProp(lockDiscovery.toXML(dav));
                        body.bindNameSpace();

                        HttpContent content = new HttpContent.Text.XML(new XML.Document(body));

                        HttpResponse response = new HttpResponse(HTTP.Response.Status.OK, content);
                        response.getMessage().addHeader(new HttpHeader.LockToken(lockToken));
                        return response;
                    }
                } else {
                    // Generate a lockdiscovery property.

                    URI lockToken = URI.create("opaquelocktoken:" + java.util.UUID.randomUUID());
                    //                    URI lockToken = URI.create("opaquelocktoken:" + new java.util.UUID(0, 1));
                    WebDAV.DAVLockRoot lockRoot = new WebDAV.DAVLockRoot(new WebDAV.DAVHref(request.getURI()));

                    WebDAV.DAVActiveLock activeLock = new WebDAV.DAVActiveLock(lockInfo.getScope(), lockInfo.getType(), depth, lockInfo.getOwner(), timeout,
                            new WebDAV.DAVLockToken(lockToken), lockRoot);
                    existingLock.add(activeLock);

                    resource.setLock(existingLock);

                    // Compose and return the current value of the lockdiscovery property.
                    WebDAV.DAVLockDiscovery lockDiscovery = new WebDAV.DAVLockDiscovery(activeLock);
                    DAV dav = new DAV();
                    XML.Node body = dav.newProp(lockDiscovery.toXML(dav));
                    body.bindNameSpace();

                    HttpContent content = new HttpContent.Text.XML(new XML.Document(body));

                    HttpResponse response = new HttpResponse(HTTP.Response.Status.OK, content);
                    response.getMessage().addHeader(new HttpHeader.LockToken(lockToken));
                    return response;
                }
            }

            HttpResponse response = new HttpResponse(HTTP.Response.Status.NOT_IMPLEMENTED);
            return response;
        }
    }

    public static class WebDAVMkcol implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVMkcol(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

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
        HTTP.UnsupportedMediaTypeException {
            HTTP.Message.Body content = request.getMessage().getBody();

            if (content.contentLength() > 0) {
                throw new HTTP.UnsupportedMediaTypeException();
            }

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            resource.createCollection();

            HTTP.Response response = new HttpResponse(HTTP.Response.Status.CREATED, new HttpContent.Text.Plain(String.format("Created %s", request.getURI())));
            return response;
        }
    }

    public static class WebDAVMove implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVMove(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HttpResponse execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.BadRequestException, HTTP.UnauthorizedException, HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException,
        HTTP.PreconditionFailedException, HTTP.ConflictException, HTTP.InsufficientStorageException, HTTP.MethodNotAllowedException, HTTP.ForbiddenException,
        HTTP.LockedException {

            // Ensure that the request contains a Destination: header
            HTTP.Message.Header.Destination destinationHeader = (HTTP.Message.Header.Destination) request.getMessage().getHeader(HTTP.Message.Header.DESTINATION);
            if (destinationHeader == null) {
                throw new HTTP.BadRequestException("Destination header must be present.");
            }

            if (request.getURI().getFragment() != null) {
                throw new HTTP.BadRequestException(String.format("Cannot MOVE from URI with a fragment: %s", request.getURI().toString()));
            }

            URI destinationURI = destinationHeader.getURI();
            if (destinationURI.getFragment() != null) {
                throw new HTTP.BadRequestException(String.format("Cannot MOVE to URI with a fragment: %s%n", destinationURI));
            }

            // Determine the Depth for this request.                
            HTTP.Message.Header.Depth depthHeader = (HTTP.Message.Header.Depth) request.getMessage().getHeader(HTTP.Message.Header.DEPTH);                
            HTTP.Message.Header.Depth.Level depth = (depthHeader != null) ? depthHeader.getLevel() : HTTP.Message.Header.Depth.Level.INFINITY;

            HTTP.Message.Header.Overwrite overwriteHeader = (HTTP.Message.Header.Overwrite) request.getMessage().getHeader(HTTP.Message.Header.OVERWRITE);

            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

            WebDAV.Resource source = this.backend.getResource(request.getURI(), identity);
            WebDAV.Resource destination = this.backend.getResource(destinationHeader.getURI(), identity);

            HTTP.Response.Status status = HTTP.Response.Status.INTERNAL_SERVER_ERROR;
            if (destination.exists()) {
                System.out.printf("%s exists as a %s%n", destination, destination.isCollection() ? "collection" : "file");
                if (!overwriteHeader.getOverwrite()) {
                    System.out.printf("do not overwrite %s%n", destination);
                    throw new HTTP.PreconditionFailedException(destination.getURI(), "Attempted to overwrite existing resource, and overwrite flag is false.");
                }

                // Delete the destination.
                System.out.printf("overwrite %s%n", destination);
                WebDAVNameSpace.RecursiveOperation delete = new WebDAVNameSpace.DeleteOperation(ifHeader);

                DAVMultistatus result = WebDAVNameSpace.treeWalk2(destination, HTTP.Message.Header.Depth.Level.INFINITY, delete);
                DAVMultistatus resultErrors = result.getErrors();
                if (resultErrors.size() != 0) {
                    if (resultErrors.size() == 1) {
                        for (DAVResponse r : resultErrors) {
                            return new HttpResponse(r.getStatus().getStatus());
                        }     
                    }

                    DAV.MultiStatus multiStatus = resultErrors.toXML(new DAV());
                    multiStatus.bindNameSpace();
                    XML.Document document = new XML.Document(multiStatus);
                    return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
                }

                status = HTTP.Response.Status.NO_CONTENT;
            } else {
                status = HTTP.Response.Status.CREATED;
            }

            WebDAVNameSpace.RecursiveOperation copy = this.new MoveOperation(request.getURI(), destinationHeader.getURI(), overwriteHeader.getOverwrite(), ifHeader);

            DAVMultistatus result = WebDAVNameSpace.treeWalk2(source, depth, copy);
            DAVMultistatus resultErrors = result.getErrors();
            if (resultErrors.size() != 0) {
                if (resultErrors.size() == 1) {
                    for (DAVResponse r : resultErrors) {
                        return new HttpResponse(r.getStatus().getStatus());
                    }     
                }

                DAV.MultiStatus multiStatus = resultErrors.toXML(new DAV());
                multiStatus.bindNameSpace();
                XML.Document document = new XML.Document(multiStatus);
                return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
            }
            return new HttpResponse(status);
        }


        public class MoveOperation implements WebDAVNameSpace.RecursiveOperation {
            private URI fromRoot;
            private URI destinationRoot;
            private boolean overWrite;
            private HTTP.Message.Header.If ifHeader;

            // This is a local copy operation.  It does not copy to other servers.
            public MoveOperation(URI fromRoot, URI toRoot, boolean overWrite, HTTP.Message.Header.If ifHeader) {
                this.fromRoot = fromRoot;
                this.destinationRoot = toRoot;
                this.overWrite = overWrite;
                this.ifHeader = ifHeader;
            }

            public WebDAV.DAVResponse preOperation(WebDAV.Resource source, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                // Compute the shared name of the source with the destination.
                // Moving /a/b/c from the source collection /a to collection /z results in a new resource /z/b/c
                int length = this.fromRoot.getPath().length();
                String relativeName = source.getURI().getPath().substring(length);
                if (relativeName.length() != 0) {
                    relativeName = "/" + relativeName;                    
                }
                
                try {
                    if (ifHeader != null) {
                        if (ifHeader.evaluate(source) == false) {
                            throw new HTTP.PreconditionFailedException(source.getURI(), "If condition failed.");
                        }
                    }

                    if (WebDAVNameSpace.isLocked(source, this.ifHeader)) {
                        return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.LOCKED));                    
                    }


                    URI destinationURI = new URI(this.destinationRoot.getPath() + relativeName).normalize();
                    WebDAV.Resource destination = source.getBackend().getResource(destinationURI, source.getIdentity());
                    HTTP.Response.Status status;
                    
                    if (destination.exists()) {
                        if (this.overWrite == false) {
                            return new WebDAV.DAVResponse(destination.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.PRECONDITION_FAILED));
                        }
                        destination.delete();
                        status = HTTP.Response.Status.NO_CONTENT;
                    } else {
                        status = HTTP.Response.Status.CREATED;
                    }
                    
                    if (source.isCollection()) {
                        destination.createCollection();
                    }
                    return new WebDAV.DAVResponse(destination.getURI(), new WebDAV.DAVStatus(status));
                } catch (URISyntaxException e) {
                    return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.BAD_REQUEST), this.destinationRoot.getPath() + relativeName);
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(source.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }

            public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
                int length = this.fromRoot.getPath().length();

                String relativeName = resource.getURI().getPath().substring(length);
                if (relativeName.length() != 0) {
                    relativeName = "/" + relativeName;                    
                }
                try {
                    URI destinationURI = new URI(this.destinationRoot.getPath() + relativeName).normalize();
                    try {
                        if (resource.isCollection()) {
                            resource.delete();
                            return preResponse;
                        } else {
                            resource.move(destinationURI);                            
                        }

                        return preResponse;
                    } catch (HTTP.Exception e) {
                        return new WebDAV.DAVResponse(e.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                    }
                } catch (URISyntaxException e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.BAD_REQUEST));
                }
            }
        }
    }

    public static class WebDAVOptions implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVOptions(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.BadRequestException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.InternalServerErrorException,
        HTTP.InsufficientStorageException, HTTP.ConflictException {

            // A URI of "*" is about this server, not a particular named resource.
            // Think of "*" as the name of the server.
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

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);
            
            Collection<String> compliance = resource.getComplianceClass();
            
            HTTP.Response response = new HttpResponse(HTTP.Response.Status.OK);
            response.getMessage().addHeader(
                    new HttpHeader.Date(),
                    new HttpHeader.Connection("Keep-Alive"),
//                    new HttpHeader.DAV(compliance),
                    new HttpHeader.Allow(resource.getAccessAllowed()));
            
            if (compliance.size() > 0) {
                response.getMessage().addHeader(new HttpHeader.DAV(compliance));
            }
            
            return response;
        }
    }

    public static class WebDAVPost implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVPost(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.PreconditionFailedException, HTTP.InsufficientStorageException, LockedException, MethodNotAllowedException, ForbiddenException {
            try {
                HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

//                HTTP.Message.Body.MultiPart.FormData avPairs = request.getMessage().decodeMultiPartFormData();
                
                WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

                if (ifHeader != null) {
                    if (ifHeader.evaluate(resource) == false) {
                        throw new HTTP.PreconditionFailedException(resource.getURI(), "If condition failed.");
                    }
                }

                if (isLocked(resource, ifHeader)) {
                    return new HttpResponse(HTTP.Response.Status.LOCKED);                
                }

                HTTP.Response response = resource.post(request, identity);
                return response;
                
//                return new HttpResponse(status, new HttpContent.Text.Plain(String.format("%s %s%n", status, resource.getURI())));
//
//                // here are the attribute value pairs we found
//                XHTML.Table.Body tbody = new XHTML.Table.Body(
//                        new XHTML.Table.Row(
//                                new XHTML.Table.Heading("form.name"),
//                                new XHTML.Table.Heading("form.data")));
//
//                for (Map.Entry<String,HTTP.Message> entry : avPairs.entrySet()) {
//                    tbody.add(new XHTML.Table.Row(
//                            new XHTML.Table.Data(entry.getKey()),
//                            new XHTML.Table.Data(HttpMessage.asString(entry.getValue(), ""))));
//                }
//
//                XHTML.Table table = new XHTML.Table(tbody).setStyle("border: 1px solid black;");
//
//                String user = HttpMessage.asString(avPairs.get("user"), "");
//                String home = HttpMessage.asString(avPairs.get("home"), "");
//
//                XHTML.Anchor link = new XHTML.Anchor("http://%s@127.0.0.1:8080/%s", user, home).setHref("http://%s@127.0.0.1:8080/%s", user, home);
//
//                XHTML.Body body = new XHTML.Body(Xxhtml.TextFormat(request.toString()), table, link);
//
//                // We must always return an HttpResponse
//                HttpResponse response = new HttpResponse(HTTP.Response.Status.OK, new HttpContent.Text.HTML(new XHTML.Document(new XHTML.Html(new XHTML.Head(), body))));
//
//                return response;
            } catch (HTTP.BadRequestException e) {
                System.out.flush();
                e.printStackTrace();
                return (null);
            }
        }
    }

    /**
     * <p>From <a href="http://webdav.org/specs/rfc4918.html#METHOD_PROPFIND">RFC 4914 &sect;9.1 PROPFIND Method</a>
     * <blockquote>
     * The PROPFIND method retrieves properties defined on the resource identified by the Request-URI,
     * if the resource does not have any internal members, or on the resource identified by the Request-URI and potentially its member resources,
     * if the resource is a collection that has internal member URLs.
     * All DAV-compliant resources MUST support the PROPFIND method and the propfind XML element (Section 14.20)
     * along with all XML elements defined for use with that element.
     * </blockquote>
     * <blockquote>
     * A client MUST submit a Depth header with a value of "0", "1", or "infinity" with a PROPFIND request.
     * Servers MUST support "0" and "1" depth requests on WebDAV-compliant resources and SHOULD support "infinity" requests.
     * In practice, support for infinite-depth requests MAY be disabled, due to the performance and security concerns associated with this behavior.
     * Servers SHOULD treat a request without a Depth header as if a "Depth: infinity" header was included.
     * </blockquote>
     * <blockquote>
     * A client may submit a 'propfind' XML element in the body of the request method describing what information is being requested. It is possible to:
     * <ul>
     * <li>Request particular property values, by naming the properties desired within the 'prop' element
     *     (the ordering of properties in here MAY be ignored by the server),</li>
     * <li>Request property values for those properties defined in this specification (at a minimum) plus dead properties,
     *     by using the 'allprop' element (the 'include' element can be used with 'allprop' to instruct the server to also include additional
     *     live properties that may not have been returned otherwise),</li>
     * <li>Request a list of names of all the properties defined on the resource, by using the 'propname' element.</li>
     * </ul>
     * </blockquote>
     * <blockquote>
     * A client may choose not to submit a request body. An empty PROPFIND request body MUST be treated as if it were an 'allprop' request.
     * </blockquote>
     * <blockquote>
     * Note that 'allprop' does not return values for all live properties.
     * WebDAV servers increasingly have expensively-calculated or lengthy properties (see [RFC3253] and [RFC3744]) and do not return all properties already.
     * Instead, WebDAV clients can use propname requests to discover what live properties exist, and request named properties when retrieving values.
     * For a live property defined elsewhere, that definition can specify whether or not that live property would be returned in 'allprop' requests.
     * </blockquote>
     * <blockquote>
     * All servers MUST support returning a response of content type text/xml or application/xml that contains a multistatus XML
     * element that describes the results of the attempts to retrieve the various properties.
     * </blockquote>
     * <blockquote>
     * If there is an error retrieving a property, then a proper error result MUST be included in the response.
     * A request to retrieve the value of a property that does not exist is an error and MUST be noted with a 'response' XML
     * element that contains a 404 (Not Found) status value.
     * </blockquote>
     * <blockquote>
     * Consequently, the 'multistatus' XML element for a collection resource MUST include a 'response' XML element for each member URL of the collection,
     * to whatever depth was requested. It SHOULD NOT include any 'response' elements for resources that are not WebDAV-compliant. Each 'response'
     * element MUST contain an 'href' element that contains the URL of the resource on which the properties in the prop XML element are defined.
     * Results for a PROPFIND on a collection resource are returned as a flat list whose order of entries is not significant.
     * Note that a resource may have only one value for a property of a given name, so the property may only show up once in PROPFIND responses.
     * </blockquote>
     * <blockquote>
     * Properties may be subject to access control. In the case of 'allprop' and 'propname' requests,
     * if a principal does not have the right to know whether a particular property exists,
     * then the property MAY be silently excluded from the response.
     * </blockquote>
     * <blockquote>
     * Some PROPFIND results MAY be cached, with care, as there is no cache validation mechanism for most properties.
     * This method is both safe and idempotent (see Section 9.1 of [RFC2616]).
     * </blockquote>
     *
     */
    public static class WebDAVPropfind implements HTTP.Request.Method.Handler {
        protected WebDAV.Backend backend;

        public WebDAVPropfind(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.backend = backend;
        }

        public HttpResponse execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.UnauthorizedException, HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.BadRequestException, HTTP.ConflictException {

            // A missing Depth header indicated a Depth of 'infinity'
            HTTP.Message.Header.Depth depthHeader = (HTTP.Message.Header.Depth) request.getMessage().getHeader(HTTP.Message.Header.DEPTH);
            if (depthHeader == null) {
                throw new HTTP.BadRequestException("Missing Depth: header. See RFC 4918 Section 9.1.");
            }
            HTTP.Message.Header.Depth.Level depth = depthHeader.getLevel();

            // Parse the XML request body.
            Document doc = parseXMLBody(request);

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            Element propfindElement = doc.getDocumentElement();
            NodeList propfindNodes = propfindElement.getChildNodes();

            for (int i = 0; i < propfindNodes.getLength(); i++) {
                Node n = propfindNodes.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    if (n.getLocalName().compareTo("prop") == 0) {
                        WebDAVNameSpace.RecursiveOperation propfindOperation = new PropfindPropOperation(new WebDAV.DAVProp(n.getChildNodes()));

                        DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, depth, propfindOperation);

                        DAV.MultiStatus multiStatus = result.toXML(new DAV());
                        multiStatus.bindNameSpace();
                        XML.Document document = new XML.Document(multiStatus);
                        return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
                    } else if (n.getLocalName().compareTo("allprop") == 0) {
                        WebDAVNameSpace.RecursiveOperation propfindAllPropOperation = new PropfindAllpropOperation(new WebDAV.DAVAllprop(n.getChildNodes()));

                        DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, depth, propfindAllPropOperation);

                        DAV.MultiStatus multiStatus = result.toXML(new DAV());
                        multiStatus.bindNameSpace();
                        XML.Document document = new XML.Document(multiStatus);
                        return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));                               
                    } else if (n.getLocalName().compareTo("propname") == 0) {
                        WebDAVNameSpace.RecursiveOperation propfindOperation = new PropfindPropnameOperation(new WebDAV.DAVAllprop(n.getChildNodes()));
                        
                        DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, depth, propfindOperation);

                        DAV.MultiStatus multiStatus = result.toXML(new DAV());
                        multiStatus.bindNameSpace();
                        XML.Document document = new XML.Document(multiStatus);
                        return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));                        
                    } else if (n.getLocalName().compareTo("include") == 0) {
                        WebDAVNameSpace.RecursiveOperation propfindOperation = new PropfindIncludeOperation(new WebDAV.DAVInclude(n.getChildNodes()));
                        

                        DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, depth, propfindOperation);

                        DAV.MultiStatus multiStatus = result.toXML(new DAV());
                        multiStatus.bindNameSpace();
                        XML.Document document = new XML.Document(multiStatus);
                        return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
                    } else {
                        throw new HTTP.BadRequestException(String.format("Unknown propfind element '%s'", n.getLocalName()));
                    }
                }
            }
            throw new HTTP.BadRequestException();
        }
        
        public class PropfindAllpropOperation implements WebDAVNameSpace.RecursiveOperation {
            public PropfindAllpropOperation(DAVAllprop davAllprop) {

            }

            public DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, DAVResponse preResult) {
                try {
                    Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> resourceProperties = resource.getProperties();
                    WebDAV.DAVPropstat propstat = new WebDAV.DAVPropstat(new WebDAV.DAVProp(resourceProperties.values()), new WebDAV.DAVStatus(HTTP.Response.Status.OK));
                    return new WebDAV.DAVResponse(resource.getURI(), propstat);
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }

            public DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                // TODO Auto-generated method stub
                return null;
            }
        }

        public class PropfindIncludeOperation implements WebDAVNameSpace.RecursiveOperation {
            private WebDAV.DAVInclude include;

            public PropfindIncludeOperation(WebDAV.DAVInclude include) {
                this.include = include;
            }

            public WebDAV.DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                return null;
            }

            public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
                try {
                    Map<HTTP.Response.Status,Collection<WebDAV.Resource.Property>> statusMap = new HashMap<HTTP.Response.Status,Collection<WebDAV.Resource.Property>>();

                    Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> resourceProperties = resource.getProperties();

                    //                    for (WebDAV.Resource.Property.Name n : resourceProperties.keySet()) {
                    //                        WebDAV.Resource.Property p = resourceProperties.get(n);
                    //                        System.out.printf("GET: %s = %s%n", n, p, p.getValue());
                    //                    }

                    for (WebDAV.Resource.Property p : this.include) {
                        WebDAV.Resource.Property property = resourceProperties.get(p.getName());
                        if (property == null) {
                            Collection<WebDAV.Resource.Property> c = statusMap.get(HTTP.Response.Status.NOT_FOUND);
                            if (c == null) {
                                c = new LinkedList<WebDAV.Resource.Property>();
                                statusMap.put(HTTP.Response.Status.NOT_FOUND, c);
                            }
                            c.add(p);
                        } else {
                            Collection<WebDAV.Resource.Property> c = statusMap.get(HTTP.Response.Status.OK);
                            if (c == null) {
                                c = new LinkedList<WebDAV.Resource.Property>();
                                statusMap.put(HTTP.Response.Status.OK, c);
                            }
                            c.add(property);                     
                        }
                    }

                    WebDAV.DAVResponse response = new WebDAV.DAVResponse(resource.getURI());;
                    for (HTTP.Response.Status status : statusMap.keySet()) {
                        Collection<WebDAV.Resource.Property> result = statusMap.get(status);
                        response.add(new WebDAV.DAVPropstat(new WebDAV.DAVProp(result), new WebDAV.DAVStatus(status)));
                    }
                    return response;
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }
        }

        public static class PropfindPropOperation implements WebDAVNameSpace.RecursiveOperation {
            private WebDAV.DAVProp prop;

            public PropfindPropOperation(WebDAV.DAVProp prop) {
                this.prop = prop;
            }

            public WebDAV.DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                return null;
            }

            public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
                try {
                    Map<HTTP.Response.Status,Collection<WebDAV.Resource.Property>> statusMap = new HashMap<HTTP.Response.Status,Collection<WebDAV.Resource.Property>>();

                    Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> resourceProperties = resource.getProperties();

//                    for (WebDAV.Resource.Property.Name n : resourceProperties.keySet()) {
//                        WebDAV.Resource.Property p = resourceProperties.get(n);
//                        System.out.printf("GET: %s = %s%n", n, p, p.getValue());
//                    }

                    for (WebDAV.Resource.Property p : this.prop) {
                        WebDAV.Resource.Property property = resourceProperties.get(p.getName());
                        if (property == null) {
                            Collection<WebDAV.Resource.Property> c = statusMap.get(HTTP.Response.Status.NOT_FOUND);
                            if (c == null) {
                                c = new LinkedList<WebDAV.Resource.Property>();
                                statusMap.put(HTTP.Response.Status.NOT_FOUND, c);
                            }
                            c.add(p);
                        } else {
                            Collection<WebDAV.Resource.Property> c = statusMap.get(HTTP.Response.Status.OK);
                            if (c == null) {
                                c = new LinkedList<WebDAV.Resource.Property>();
                                statusMap.put(HTTP.Response.Status.OK, c);
                            }
                            c.add(property);                     
                        }
                    }

                    WebDAV.DAVResponse response = new WebDAV.DAVResponse(resource.getURI());
                    for (HTTP.Response.Status status : statusMap.keySet()) {
                        Collection<WebDAV.Resource.Property> result = statusMap.get(status);
                        response.add(new WebDAV.DAVPropstat(new WebDAV.DAVProp(result), new WebDAV.DAVStatus(status)));
                    }
                    return response;
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }
        }


        public class PropfindPropnameOperation implements WebDAVNameSpace.RecursiveOperation {
            public PropfindPropnameOperation(DAVAllprop davAllprop) {

            }

            public DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, DAVResponse preResult) {
                try {
                    Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> resourceProperties = resource.getProperties();
                    WebDAV.DAVProp prop = new WebDAV.DAVProp(resourceProperties.keySet(), 0);
                    WebDAV.DAVPropstat propstat = new WebDAV.DAVPropstat(prop, new WebDAV.DAVStatus(HTTP.Response.Status.OK));
                    return new WebDAV.DAVResponse(resource.getURI(), propstat);
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                }
            }

            public DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                // TODO Auto-generated method stub
                return null;
            }
        }
    }

    /**
     * 
     * <p>From <cite><a href="http://webdav.org/specs/rfc4918.html#METHOD_PROPPATCH">RFC 4914 &sect;9.2 PROPPATCH Method</a></cite>
     * <blockquote>
     * The PROPPATCH method processes instructions specified in the request body to set and/or remove properties defined on the resource
     * identified by the Request-URI.
     * </blockquote>
     * <blockquote>
     * All DAV-compliant resources MUST support the PROPPATCH method and MUST process instructions that are specified using the propertyupdate,
     * set, and remove XML elements. Execution of the directives in this method is, of course, subject to access control constraints. DAV-compliant
     * resources SHOULD support the setting of arbitrary dead properties.
     * </blockquote>
     * <blockquote>
     * The request message body of a PROPPATCH method MUST contain the propertyupdate XML element.
     * </blockquote>
     * <blockquote>
     * Servers MUST process PROPPATCH instructions in document order (an exception to the normal rule that ordering is irrelevant).
     * Instructions MUST either all be executed or none executed.
     * Thus, if any error occurs during processing, all executed instructions MUST be undone and a proper error result returned.
     * Instruction processing details can be found in the definition of the set and remove instructions in Sections 14.23 and 14.26.
     * </blockquote>
     * <blockquote>
     * If a server attempts to make any of the property changes in a PROPPATCH request
     * (i.e., the request is not rejected for high-level errors before processing the body),
     * the response MUST be a Multi-Status response as described in Section 9.2.1.
     * </blockquote>
     * <blockquote>
     * This method is idempotent, but not safe (see Section 9.1 of [RFC2616]). Responses to this method MUST NOT be cached.
     * </blockquote>
     */
    public static class WebDAVProppatch implements HTTP.Request.Method.Handler {

        protected WebDAV.Backend backend;

        public WebDAVProppatch(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.backend = backend;
        }

        public HTTP.Response execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.PreconditionFailedException {

            // A missing Depth header indicated a Depth of '0'
            HTTP.Message.Header.Depth depthHeader = (HTTP.Message.Header.Depth) request.getMessage().getHeader(HTTP.Message.Header.DEPTH);
            HTTP.Message.Header.Depth.Level depth = (depthHeader != null) ? depthHeader.getLevel() : HTTP.Message.Header.Depth.Level.ONLY;
            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            // Parse the XML request body.
            Document doc = parseXMLBody(request);
            Element propPatchElement = doc.getDocumentElement();
            WebDAV.DAVPropertyUpdate p = new WebDAV.DAVPropertyUpdate(propPatchElement.getChildNodes());

            WebDAVNameSpace.RecursiveOperation propertyUpdateOperation = new PropertyUpdateOperation(p, ifHeader);


            DAVMultistatus result = WebDAVNameSpace.treeWalk2(resource, depth, propertyUpdateOperation);
            if (result.size() == 1) {
                for (DAVResponse r : result) {
                    if (r.getStatus() != null) {
                        return new HttpResponse(r.getStatus().getStatus());
                    }
                }     
            }

            DAV.MultiStatus multiStatus = result.toXML(new DAV());
            multiStatus.bindNameSpace();
            XML.Document document = new XML.Document(multiStatus);
            return new HttpResponse(HTTP.Response.Status.MULTI_STATUS, new HttpContent.Text.XML(document));
        }
        
        public static class PropertyUpdateOperation implements WebDAVNameSpace.RecursiveOperation {
            private WebDAV.DAVPropertyUpdate propertyUpdate;
            private HTTP.Message.Header.If ifHeader;

            public PropertyUpdateOperation(WebDAV.DAVPropertyUpdate prop, HTTP.Message.Header.If ifHeader) {
                this.propertyUpdate = prop;
                this.ifHeader = ifHeader;
            }

            public WebDAV.DAVResponse preOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
                try {
                    if (ifHeader != null) {
                        if (ifHeader.evaluate(resource) == false) {
                            throw new HTTP.PreconditionFailedException(resource.getURI(), "If condition failed.");
                        }
                    }

                    if (WebDAVNameSpace.isLocked(resource, this.ifHeader)) {
                        return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.LOCKED));                    
                    }
                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                } finally {

                }

                return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(HTTP.Response.Status.OK));
            }

            public WebDAV.DAVResponse postOperation(Resource resource, Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResponse) {
                try {
                    Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> resourceProperties = resource.getProperties();

                    Map<HTTP.Response.Status,Collection<WebDAV.Resource.Property.Name>> statusToNames= new HashMap<HTTP.Response.Status,Collection<WebDAV.Resource.Property.Name>>();

                    for (WebDAV.DAVPropertyUpdate.SubElement todo : this.propertyUpdate.getUpdates()) {
                        if (todo instanceof WebDAV.DAVSet) {
                            for (WebDAV.Resource.Property p : todo.getProp()) {
                                System.out.printf("todo: set: %s %s%n", todo, p);       
                                WebDAV.Resource.Property resourceProperty = resourceProperties.get(p.getName());
                                HTTP.Response.Status status = HTTP.Response.Status.OK;
                                if (resourceProperty == null || !resourceProperty.isProtected()) {
                                    resourceProperties.put(p.getName(), p);
                                } else {
                                    status = HTTP.Response.Status.FORBIDDEN;                                    
                                }
                                Collection<WebDAV.Resource.Property.Name> a = statusToNames.get(status);
                                if (a == null) {
                                    a = new LinkedList<WebDAV.Resource.Property.Name>();
                                    statusToNames.put(status, a);
                                }
                                a.add(p.getName());
                            }
                        } else if (todo instanceof WebDAV.DAVRemove) {
                            for (WebDAV.Resource.Property p : todo.getProp()) {
                                System.out.printf("todo: remove: %s %s%n", todo, p);    
                                WebDAV.Resource.Property resourceProperty = resourceProperties.get(p.getName());   
                                HTTP.Response.Status status = HTTP.Response.Status.OK;
                                if (resourceProperty == null || !resourceProperty.isProtected()) {
                                    resourceProperties.remove(p.getName());
                                } else {
                                    status = HTTP.Response.Status.FORBIDDEN;                                    
                                }
                                Collection<WebDAV.Resource.Property.Name> a = statusToNames.get(status);
                                if (a == null) {
                                    a = new LinkedList<WebDAV.Resource.Property.Name>();
                                    statusToNames.put(status, a);
                                }
                                a.add(p.getName());
                            }
                        } else {
                            throw new HTTP.BadRequestException(todo.toXML(new DAV()).toString());
                        }
                    }

                    WebDAV.DAVResponse response = new WebDAV.DAVResponse(resource.getURI());
                    for (HTTP.Response.Status status : statusToNames.keySet()) {
                        Collection<WebDAV.Resource.Property.Name> a = statusToNames.get(status);
                        WebDAV.DAVPropstat propstat = new WebDAV.DAVPropstat(new WebDAV.DAVProp(a, (int) 0), new WebDAV.DAVStatus(status));
                        response.add(propstat);
                    }                    

                    resource.setProperties(resourceProperties);
                    return response;

                } catch (HTTP.Exception e) {
                    return new WebDAV.DAVResponse(resource.getURI(), new WebDAV.DAVStatus(e.getStatus()));
                } finally {

                }
            }
        }
    }

    public static class WebDAVPut implements HTTP.Request.Method.Handler {
        protected HTTP.Server server;
        protected WebDAV.Backend backend;

        public WebDAVPut(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.server = server;
            this.backend = backend;
        }

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
        HTTP.ForbiddenException, HTTP.PreconditionFailedException {
            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);

            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);

            if (ifHeader != null) {
                if (ifHeader.evaluate(resource) == false) {
                    throw new HTTP.PreconditionFailedException(resource.getURI(), "If condition failed.");
                }
            }

            if (isLocked(resource, ifHeader)) {
                return new HttpResponse(HTTP.Response.Status.LOCKED);                
            }

            if (!resource.exists()) {
                try {
                    resource.create(request.getMessage().getContentType().getType());
                } catch (HTTP.ConflictException e) {
                    throw new HTTP.MethodNotAllowedException(resource.getAccessAllowed());
                }
            }

            try {
                OutputStream out = resource.asOutputStream();
                request.getMessage().getBody().writeTo(new DataOutputStream(out));
                out.close();
            } catch (IOException e) {
                throw new HTTP.InternalServerErrorException(e);
            }
            
            return new HttpResponse(HTTP.Response.Status.CREATED, new HttpContent.Text.Plain(String.format("Created %s %s%n", resource.getContentType(), resource.getURI())));
        }

    }

    public static class WebDAVUnlock implements HTTP.Request.Method.Handler {

        protected WebDAV.Backend backend;

        public WebDAVUnlock(HTTP.Server server, WebDAV.Backend backend) {
            super();
            this.backend = backend;
        }

        public HttpResponse execute(HTTP.Request request, HTTP.Identity identity)
        throws HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException,
               HTTP.MethodNotAllowedException, HTTP.ConflictException, HTTP.PreconditionFailedException {
            
            HTTP.Message.Header.LockToken lockToken = (HTTP.Message.Header.LockToken) request.getMessage().getHeader(HTTP.Message.Header.LOCKTOKEN);
            HTTP.Message.Header.If ifHeader = (HTTP.Message.Header.If) request.getMessage().getHeader(HTTP.Message.Header.IF);
            
            WebDAV.Resource resource = this.backend.getResource(request.getURI(), identity);
            if (!(resource instanceof WebDAV.Resource.DAV2)) {
                throw new HTTP.MethodNotAllowedException(resource.getAccessAllowed());
            }

            WebDAV.Resource.DAV2 dav2Resource = ( WebDAV.Resource.DAV2) resource;            

            if (ifHeader != null) {
                if (ifHeader.evaluate(dav2Resource) == false) {
                    throw new HTTP.PreconditionFailedException(resource.getURI(), "If condition failed.");
                }
            }

            try {
                WebDAV.DAVLockDiscovery locks = dav2Resource.getLock();
                WebDAV.DAVLockDiscovery newLocks = new WebDAV.DAVLockDiscovery();
                boolean updated = false;
                for (URI key : locks.keySet()) {
                    System.out.printf("%s,:%s%n", key, locks.get(key));
                    if (key.equals(lockToken.getCodedURL())) {
                        updated = true; // we elide this entry.
                    } else {
                        System.out.printf("%s%n", locks.get(key));
                        newLocks.add(locks.get(key));
                    }
                }
                if (!updated) {
                    HttpResponse response = new HttpResponse(HTTP.Response.Status.BAD_REQUEST); // this is what Apache does.
                    return response; 
                }
                dav2Resource.setLock(newLocks);
            } catch (HTTP.Exception e) {
                return new HttpResponse(e.getStatus());
            } finally {

            }

            HttpResponse response = new HttpResponse(HTTP.Response.Status.NO_CONTENT);
            return response; 
        }
    }

    /**
     * Recursively apply the methods {@link RecursiveOperation#preOperation(WebDAV.Resource, HTTP.Message.Header.Depth.Level, WebDAVNameSpace.RecursiveOperation)}
     * and {@link RecursiveOperation#postOperation(WebDAV.Resource resource, HTTP.Message.Header.Depth.Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResult)}
     * to the given resource.
     * <p>
     * The <em>pre</em> operation is applied immediately before the resource is recursively entered,
     * and the <em>post</em> operation is applied immediately after the recursion into the collection returned.
     * </p>
     * <p>
     * If the {@link WebDAV.DAVResponse} value returned from the <em>pre</em> operation contains an unsuccessful status code,
     * no further processing of the resource is performed.
     * Otherwise, the successful {@code WebDAV.DAVResponse} value (including the value {@code null}) is a
     * parameter to the subsequent invocation of the <em>post</em> operation.
     * </p
     * <p>
     * The depth of the recursion is governed by the given {@link HTTP.Message.Header.Depth.Level}.
     * </p>
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static interface RecursiveOperation {
        /**
         * This method is applied to a resource immediately before the resource is recursively entered.
         * <p>
         * If the {@link WebDAV.DAVResponse} value returned from this method contains an unsuccessful status code,
         * no further processing of the resource is performed.
         * Otherwise, the successful {@code WebDAV.DAVResponse} value (including the value {@code null}) is a
         * parameter to the subsequent invocation of the <em>post</em> operation.
         * </p>
         * @param resource The target resource
         * @param depthLevel The depth level of the recursion.
         * @param operation The operation being performed.
         * @return On success: {@code null} or any {@link WebDAV.DAVResponse} instance containing a succcess status.
         *         On failure: any {@link WebDAV.DAVResponse} instance containing a NON-succcess status.
         */
        public WebDAV.DAVResponse preOperation(WebDAV.Resource resource, HTTP.Message.Header.Depth.Level depthLevel, WebDAVNameSpace.RecursiveOperation operation);
        public WebDAV.DAVResponse postOperation(WebDAV.Resource resource, HTTP.Message.Header.Depth.Level depthLevel, WebDAVNameSpace.RecursiveOperation operation, WebDAV.DAVResponse preResult);
    }
    
    public static WebDAV.DAVMultistatus treeWalk2(WebDAV.Resource resource, HTTP.Message.Header.Depth.Level depthLevel, WebDAVNameSpace.RecursiveOperation operation) {
        WebDAV.DAVMultistatus response = new WebDAV.DAVMultistatus();

        try {
            DAVResponse preResult = operation.preOperation(resource, depthLevel, operation);
            // If the pre-operation returns a result that is not successful, terminate.
            if (preResult != null && !preResult.getStatus().isSuccessful()) {
                response.add(preResult);
                return response;
            }

            if (resource.isCollection()) {
                if (depthLevel == HTTP.Message.Header.Depth.Level.INFINITY) {
                    Collection<WebDAV.Resource> collection = resource.getCollection();
                    for (WebDAV.Resource r : collection) {
                        response.add(treeWalk2(r, depthLevel, operation));
                    }
                } else if (depthLevel == HTTP.Message.Header.Depth.Level.ALL) {
                    Collection<WebDAV.Resource> collection = resource.getCollection();
                    for (WebDAV.Resource r : collection) {
                        response.add(treeWalk2(r, HTTP.Message.Header.Depth.Level.ONLY, operation));
                    }
                } else {
                    /**/
                }
            }
            DAVResponse postResult = operation.postOperation(resource, depthLevel, operation, preResult);
            response.add(postResult);
        } catch (HTTP.Exception e){
            response.add(new WebDAV.DAVResponse(e.getURI(), new WebDAV.DAVStatus(e.getStatus()), e.getMessage()));
        }
        return response;
    }
}
