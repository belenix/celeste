package sunlabs.asdf.web.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;

import sunlabs.asdf.web.http.HTTP.Authenticate;
import sunlabs.asdf.web.http.HTTP.BadRequestException;
import sunlabs.asdf.web.http.HTTP.ConflictException;
import sunlabs.asdf.web.http.HTTP.ForbiddenException;
import sunlabs.asdf.web.http.HTTP.GoneException;
import sunlabs.asdf.web.http.HTTP.Identity;
import sunlabs.asdf.web.http.HTTP.InsufficientStorageException;
import sunlabs.asdf.web.http.HTTP.InternalServerErrorException;
import sunlabs.asdf.web.http.HTTP.LockedException;
import sunlabs.asdf.web.http.HTTP.MethodNotAllowedException;
import sunlabs.asdf.web.http.HTTP.NotFoundException;
import sunlabs.asdf.web.http.HTTP.PreconditionFailedException;
import sunlabs.asdf.web.http.HTTP.Request;
import sunlabs.asdf.web.http.HTTP.Response;
import sunlabs.asdf.web.http.HTTP.UnauthorizedException;
import sunlabs.asdf.web.http.HTTP.Response.Status;
import sunlabs.asdf.web.http.WebDAV.Resource;

public class ClassLoaderBackend implements WebDAV.Backend {
    
    public String documentRoot;
    public ClassLoader classLoader;

    public ClassLoaderBackend(ClassLoader classLoader, String documentRoot) {
        this.classLoader = classLoader;
        this.documentRoot = documentRoot;
    }

    public Resource getResource(URI uri, Identity identity) throws InternalServerErrorException, GoneException, UnauthorizedException {
        return new ClassLoaderResource(this, uri, identity, this.documentRoot, this.classLoader);
    }

    public Authenticate getAuthentication(URI uri) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public static class ClassLoaderResource extends WebDAV.AbstractResource implements WebDAV.Resource {

        private HttpUtil.PathName documentRoot;
        private String fullPath;
        private ClassLoader classLoader;

        public ClassLoaderResource(WebDAV.Backend backend, URI uri, Identity identity, String documentRoot, ClassLoader classLoader) {
            super(backend, uri, identity);

            System.out.printf("ClassResourceResource %s%n", uri);
            this.documentRoot = new HttpUtil.PathName(documentRoot);
            String path = this.getURI().getPath();
            if (path.compareTo("/") == 0)
                path = "index.html";
            this.fullPath = path;
            this.classLoader = classLoader;
        }

        public void createCollection() throws MethodNotAllowedException,
                InternalServerErrorException, InsufficientStorageException,
                GoneException, NotFoundException, UnauthorizedException,
                ConflictException, ForbiddenException, BadRequestException,
                LockedException {
            throw new HTTP.MethodNotAllowedException(this.getURI(), "Creation not allowed", this.getAccessAllowed());
        }

        public Collection<Resource> getCollection()
        throws InternalServerErrorException, NotFoundException, UnauthorizedException, GoneException, ConflictException {

            Collection<WebDAV.Resource> result = new HashSet<WebDAV.Resource>();
         
            String path = "/" + this.documentRoot.append(this.fullPath).toString();
//            URL url = this.klasse.getResource(path);

            return result;
        }

        public long getContentLength() throws InternalServerErrorException, NotFoundException, GoneException, UnauthorizedException, ConflictException {

            this.getAccessAllowed();

            String path = "/" + this.documentRoot.append(this.fullPath).toString();

            if (this.getClass().getResource(path) != null) {
                InputStream inputStream = this.classLoader.getResourceAsStream(this.documentRoot.append(this.fullPath).toString());
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
            throw new HTTP.NotFoundException(this.getURI());
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
        
        public boolean isCollection() throws InternalServerErrorException,
                UnauthorizedException, GoneException, ConflictException {
            // TODO Auto-generated method stub
            return false;
        }

        public Status move(URI destination)
                throws InternalServerErrorException, BadRequestException,
                GoneException, InsufficientStorageException, ConflictException,
                NotFoundException, UnauthorizedException,
                PreconditionFailedException, LockedException,
                ForbiddenException {
            // TODO Auto-generated method stub
            return null;
        }

        public InputStream asInputStream() throws InternalServerErrorException,
        GoneException, MethodNotAllowedException, NotFoundException,
        UnauthorizedException, ConflictException, BadRequestException {

            try {
                InputStream inputStream = null;

                inputStream = this.classLoader.getResourceAsStream(this.documentRoot.append(this.fullPath).toString());
                if (inputStream == null) {
                    throw new HTTP.NotFoundException(this.getURI());
                }
                // Because of something strange in the FilteredInputStream returned by getResourceAsStream,
                // it is only partially formed if the resource is a jar file "directory."  Try to induce the NPE
                // that is indicative of this problem and just return an empty InputStream if this is the case.
                try {
                    inputStream.available();
                } catch (NullPointerException e) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                return inputStream;
            } catch (IOException e) {
                e.printStackTrace();
                throw new HTTP.NotFoundException(this.getURI());
            }
        }

        public sunlabs.asdf.web.http.HTTP.Resource create(
                InternetMediaType contentType) throws BadRequestException,
                ConflictException, GoneException, InternalServerErrorException,
                InsufficientStorageException, LockedException,
                MethodNotAllowedException, NotFoundException,
                UnauthorizedException, ForbiddenException {
            // TODO Auto-generated method stub
            return null;
        }

        public void delete() throws InternalServerErrorException,
                InsufficientStorageException, NotFoundException, GoneException,
                UnauthorizedException, ConflictException {
            // TODO Auto-generated method stub
            
        }

        public boolean exists() throws InternalServerErrorException, UnauthorizedException, ConflictException {
            URL url = this.classLoader.getResource(this.documentRoot.append(this.fullPath).toString());
            return url != null ? true : false;
        }

        public Response post(Request request, Identity identity)
                throws InternalServerErrorException, NotFoundException,
                GoneException, UnauthorizedException, ConflictException {
            // TODO Auto-generated method stub
            return null;
        }
        
    }

}
