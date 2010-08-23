package sunlabs.asdf.web.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;

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

/**
 * 
 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class FileSystemBackend /*extends WebDAV.AbstractBackend*/ implements WebDAV.Backend {

    private String documentRoot;

    public FileSystemBackend(String docRoot) {
        this.documentRoot = docRoot;            
    }

    public HTTP.Authenticate getAuthentication(URI uri) {
        return null;
    }

    public WebDAV.Resource getResource(URI uri, HTTP.Identity identity) throws InternalServerErrorException, GoneException, UnauthorizedException {
        return new FileSystemResource(this, this.documentRoot, uri, identity);
    }
    
    public static class FileSystemResource extends WebDAV.AbstractResource implements WebDAV.Resource {
        private HttpUtil.PathName documentRoot;
        private String fullPath;

        /**
         * Construct a FileSystemResource named by the given {@link URI}.
         * The translated file name of the resource is produced by the concatenation of the
         * {@code String} {@code documentRoot} and the path portion of the {@code URI}.
         * 
         * @param backend
         * @param documentRoot
         * @param uri
         * @param identity
         */
        public FileSystemResource(WebDAV.Backend backend, String documentRoot, URI uri, HTTP.Identity identity) {
            super(backend, uri, identity);
            this.documentRoot = new HttpUtil.PathName(documentRoot);
            String path = this.getURI().getPath();
            if (path.compareTo("/") == 0)
                path = "index.html";
            this.fullPath = this.documentRoot.append(path).toString();
        }

        public InputStream asInputStream() throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.MethodNotAllowedException,
        HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.BadRequestException {
            try {
                InputStream inputStream = null;

                File file = new File(this.fullPath);
                inputStream = new FileInputStream(file);

                return inputStream;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new HTTP.NotFoundException(this.getURI());
            }
        }

        /**
         * {@inheritDoc}
         * 
         * This implementation tries to deduce the content-type from the file-name extension (suffix) using the URI path of this resource.
         */
        public InternetMediaType getContentType() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException,
        HTTP.UnauthorizedException, HTTP.ConflictException {
            InternetMediaType result = InternetMediaType.getByFileExtension(InternetMediaType.getExtension(this.fullPath));
            if (result == null)
                result = InternetMediaType.Application.OctetStream;
            return result;
        }
        
        @Override
        public long getCreationDate() {
            return System.currentTimeMillis();
        }

        @Override
        public long getLastModified() {
            File file = new File(this.fullPath);
            return file.lastModified();
        }

        public WebDAV.Resource create(InternetMediaType contentType) throws BadRequestException, ConflictException, GoneException, InternalServerErrorException, InsufficientStorageException,
        LockedException, MethodNotAllowedException, NotFoundException, UnauthorizedException, ForbiddenException {
            // TODO Auto-generated method stub
            return null;
        }

        public void delete() throws InternalServerErrorException, InsufficientStorageException, NotFoundException, GoneException, UnauthorizedException, ConflictException {
            // TODO Auto-generated method stub
        }

        public boolean exists() throws InternalServerErrorException, UnauthorizedException, ConflictException {
            File file = new File(this.fullPath);
            return file.exists() ? true : false;
        }

        public HTTP.Response post(HTTP.Request request, HTTP.Identity identity) throws InternalServerErrorException, NotFoundException,
        GoneException, UnauthorizedException, ConflictException {
            // TODO Auto-generated method stub
            return null;
        }

        public long getContentLength() throws InternalServerErrorException, NotFoundException, GoneException, UnauthorizedException, ConflictException {

            this.getAccessAllowed();

            File file = new File(this.fullPath);
            if (file.exists()) {
                return file.length();
            }

            throw new HTTP.NotFoundException(this.getURI());
        }

        public void createCollection() throws MethodNotAllowedException, InternalServerErrorException, InsufficientStorageException,
        GoneException, NotFoundException, UnauthorizedException, ConflictException, ForbiddenException, BadRequestException, LockedException {
            throw new HTTP.MethodNotAllowedException(this.getURI(), "Creation not allowed", this.getAccessAllowed());
        }

        public Collection<sunlabs.asdf.web.http.WebDAV.Resource> getCollection()
        throws InternalServerErrorException, NotFoundException, UnauthorizedException, GoneException, ConflictException {

            Collection<WebDAV.Resource> result = new HashSet<WebDAV.Resource>();

            File file = new File(this.fullPath);
            for (String name : file.list()) {
                try {
                    WebDAV.Resource resource = 
                        new FileSystemResource(this.backend, this.fullPath, new URI(name), this.getIdentity());
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
            File file = new File(this.fullPath);
            return file.isDirectory();                
        }

        public HTTP.Response.Status move(URI destination)
        throws InternalServerErrorException, BadRequestException, GoneException, InsufficientStorageException, ConflictException,
        NotFoundException, UnauthorizedException, PreconditionFailedException, LockedException, ForbiddenException {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
