/*
 * Copyright 2010 Sun Microsystems, Inc. All Rights Reserved.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Stack;
import java.util.logging.Logger;

import sunlabs.asdf.web.http.HTTP.Authenticate;
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
import sunlabs.asdf.web.http.HTTP.Resource;
import sunlabs.asdf.web.http.HTTP.Response;
import sunlabs.asdf.web.http.HTTP.UnauthorizedException;

/**
 * This is a work in progress.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class WebDAVServerMain {
    
    private static class FileBackend /*extends WebDAV.AbstractBackend*/ implements WebDAV.Backend {
        
        public static class FileResource extends WebDAV.AbstractResource implements WebDAV.Resource {
            private HttpUtil.PathName documentRoot;
            private String fullPath;

            public FileResource(WebDAV.Backend backend, String documentRoot, URI uri, HTTP.Identity identity) {
                super(backend, uri, identity);
                this.documentRoot = new HttpUtil.PathName(documentRoot);
                String path = this.getURI().getPath();
                if (path.compareTo("/") == 0)
                    path = "index.html";
                this.fullPath = path;
            }

            public InputStream asInputStream() throws InternalServerErrorException,
                    GoneException, MethodNotAllowedException, NotFoundException,
                    UnauthorizedException, ConflictException, BadRequestException {

                try {
                    InputStream inputStream = null;
                    if (this.isJarResource()) {
                        String path = "/" + this.documentRoot.append(this.fullPath).toString();
                        URL url = this.getClass().getResource(path);
                        inputStream = this.getClass().getResourceAsStream(path);
                        inputStream = url.openStream();
                        if (inputStream == null) {
                            throw new HTTP.NotFoundException(this.getURI());
                        }
                        // Because of something strange in the FilteredInputStream returned by getResourceAsStream,
                        // it is only partially formed if the resource is a jar file "directory."  Try to induce the NPE
                        // that is indicative of this problem and just return an empty InputStream.
                        try {
                            inputStream.available();
                        } catch (NullPointerException e) {
                            return new ByteArrayInputStream(new byte[0]);
                        }
                    } else {
                        File file = new File(this.documentRoot.append(this.fullPath).toString());
                        inputStream = new FileInputStream(file);
                    }
                    return inputStream;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    throw new HTTP.NotFoundException(this.getURI());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new HTTP.NotFoundException(this.getURI());
                }
            }

            /**
             * {@inheritDoc}
             * 
             * This implementation tries to deduce the content-type from the file-name extension (suffix) using the URI path of this resource.
             */
            public InternetMediaType getContentType()
            throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
                InternetMediaType result = InternetMediaType.getByFileExtension(InternetMediaType.getExtension(this.fullPath));
                if (result == null)
                    result = InternetMediaType.Application.OctetStream;
                return result;
            }
            
            public Resource create(InternetMediaType contentType)
                    throws BadRequestException, ConflictException, GoneException,
                    InternalServerErrorException, InsufficientStorageException,
                    LockedException, MethodNotAllowedException, NotFoundException,
                    UnauthorizedException, ForbiddenException {
                // TODO Auto-generated method stub
                return null;
            }

            public void delete() throws InternalServerErrorException,
                    InsufficientStorageException, NotFoundException, GoneException,
                    UnauthorizedException, ConflictException {
                // TODO Auto-generated method stub
            }

            private boolean isJarResource() {
                URL root = this.getClass().getResource("/");

                if (root == null) { // We are inside of a .jar file.
                    return true;
                }
                
                return false;
            }

            public boolean exists() throws InternalServerErrorException, UnauthorizedException, ConflictException {

                if (this.isJarResource()) {
                    String path = "/" + this.documentRoot.append(this.fullPath).toString();
                    return this.getClass().getResource(path) != null ? true : false;
                } 

                File file = new File(this.documentRoot.append(this.fullPath).toString());
                return file.exists() ? true : false;
            }

            public Response post(HTTP.Request request, HTTP.Identity identity)
                    throws InternalServerErrorException, NotFoundException,
                    GoneException, UnauthorizedException, ConflictException {
                // TODO Auto-generated method stub
                return null;
            }

            public long getContentLength() throws InternalServerErrorException, NotFoundException, GoneException, UnauthorizedException, ConflictException {

                this.getAccessAllowed();
                
                if (this.isJarResource()) {
                    String path = "/" + this.documentRoot.append(this.fullPath).toString();
                    
                    if (this.getClass().getResource(path) != null) {
                        InputStream inputStream = this.getClass().getResourceAsStream(path);
                        if (inputStream != null) {
                            try {
                                return inputStream.available();
                            } catch (IOException e) {
                                throw new HTTP.InternalServerErrorException(e);
                            } catch (NullPointerException e) {
                                return 0;
                            }
                        }
                    }
                } else {
                    File file = new File(this.documentRoot.append(this.fullPath).toString());
                    if (file.exists()) {
                        return file.length();
                    }
                }
                
                throw new HTTP.NotFoundException(this.getURI());
            }

            public void createCollection() throws MethodNotAllowedException,
                    InternalServerErrorException, InsufficientStorageException,
                    GoneException, NotFoundException, UnauthorizedException,
                    ConflictException, ForbiddenException, BadRequestException,
                    LockedException {
                throw new HTTP.MethodNotAllowedException(this.getURI(), "Creation not allowed", this.getAccessAllowed());
            }

            public Collection<sunlabs.asdf.web.http.WebDAV.Resource> getCollection()
                    throws InternalServerErrorException, NotFoundException, UnauthorizedException, GoneException, ConflictException {
                
                Collection<WebDAV.Resource> result = new HashSet<WebDAV.Resource>();
                if (this.isJarResource()) {
                    String path = "/" + this.documentRoot.append(this.fullPath).toString();
                    URL url = this.getClass().getResource(path);

                    return result;
                } 

                File file = new File(this.documentRoot.append(this.fullPath).toString());
                for (String name : file.list()) {
                    try {
                    WebDAV.Resource resource = 
                        new FileResource(this.backend, this.documentRoot.toString(), new URI(new HttpUtil.PathName(this.fullPath).append(name).toString()), this.getIdentity());
                    result.add(resource);
                    } catch (URISyntaxException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        
                    }                
                }
                
                return result;
            }
            
            public boolean isCollection() throws InternalServerErrorException, UnauthorizedException, GoneException, ConflictException {
                if (this.isJarResource()) {
                    return false;
                } 

                File file = new File(this.documentRoot.append(this.fullPath).toString());
                return file.isDirectory();                
            }

            public HTTP.Response.Status move(URI destination)
                    throws InternalServerErrorException, BadRequestException,
                    GoneException, InsufficientStorageException, ConflictException,
                    NotFoundException, UnauthorizedException,
                    PreconditionFailedException, LockedException,
                    ForbiddenException {
                // TODO Auto-generated method stub
                return null;
            }
        }
        
        private String documentRoot;
        
        public FileBackend(String docRoot) {
            this.documentRoot = docRoot;            
        }
        
        public Authenticate getAuthentication(URI uri) {
            return null;
        }

        public WebDAV.Resource getResource(URI uri, sunlabs.asdf.web.http.HTTP.Identity identity) throws InternalServerErrorException, GoneException, UnauthorizedException {
            return new FileResource(this, this.documentRoot, uri, identity);
        }
    }

    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }

    public static void main(String args[]) {       

        int httpPort = 8081;
        int clientTimeOutMillis = 10000;
        
        Stack<String> options = new Stack<String>();
        for (int i = args.length - 1; i >= 0; i--) {
            options.push(args[i]);
        }

        while (!options.empty()) {
            if (!options.peek().startsWith("--"))
                break;
            String option = options.pop();
            if (option.equals("--port")) {
                httpPort = Integer.parseInt(options.pop());
            } else if  (option.equals("--client-timeout")) {
                clientTimeOutMillis = Integer.parseInt(options.pop());
            } else if (option.equals("--help")) {
                System.out.printf("Arguments: [--port <port>] [--client-timeout <millis>]%n");
                System.exit(1);
            }
        }

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.socket().bind(new InetSocketAddress(httpPort));
            serverSocketChannel.socket().setReuseAddress(true);

            String localAddress = InetAddress.getLocalHost().getHostAddress();

            System.out.printf("WebDAV Server. http://%s:%d%n", localAddress, httpPort);

            WebDAV.Backend backend = new FileBackend("docroot/");
            
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                Socket socket = socketChannel.socket();
                socket.setSoTimeout(clientTimeOutMillis);
                socket.setReceiveBufferSize(8192);
                socket.setSendBufferSize(8192);

                HTTPServer server = new HTTPServer(socketChannel);
                server.setTrace(true);
                HTTP.NameSpace nameSpace = new WebDAVNameSpace(server, backend);

                server.addNameSpace(new URI("/"), nameSpace);
                server.setLogger(Logger.getLogger(WebDAVServerMain.class.getName()));
                server.start();
            }
        } catch (Exception e) {
            System.out.flush();
            e.printStackTrace();
        }
    }
}
