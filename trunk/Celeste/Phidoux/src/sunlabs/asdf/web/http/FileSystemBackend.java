package sunlabs.asdf.web.http;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

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
import sunlabs.asdf.web.http.HttpUtil.PathName;

/**
 * 
 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public class FileSystemBackend implements WebDAV.Backend {

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
    

    
    public static class FileSystemResource extends WebDAV.AbstractResource implements WebDAV.Resource.DAV1, WebDAV.Resource.DAV2, WebDAV.Resource.DAV3, WebDAV.Resource {
        
        /**
         * Produce the File instance for a property storage for the given resource.
         * 
         * @param resource
         * @return
         * @throws InternalServerErrorException
         * @throws UnauthorizedException
         * @throws GoneException
         * @throws ConflictException
         */
        public static File propertyFile(String fullPath) {
            File file = new File(fullPath);
            
            File propertyFile;
            if (file.isDirectory()) {
                propertyFile = new File(fullPath, "properties~");
            } else {
                propertyFile = new File(String.format("%s~", fullPath));
            }

            return propertyFile;
        }
        
        public static File propertyFile(WebDAV.Resource resource) {
            return propertyFile(resource.getURI().getPath());
        }
        
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

        @Override
        public OutputStream asOutputStream() throws InternalServerErrorException, GoneException, MethodNotAllowedException, InsufficientStorageException,
        NotFoundException, UnauthorizedException, ConflictException, BadRequestException, LockedException, ForbiddenException {
            try {
                OutputStream stream = null;

                File file = new File(this.fullPath);
                stream = new FileOutputStream(file);

                return stream;
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
            File file = new File(this.fullPath);
            return file.lastModified();
        }

        @Override
        public long getLastModified() {
            File file = new File(this.fullPath);
            return file.lastModified();
        }

        public HTTP.Response.Status copy(URI destination) throws HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.NotFoundException,
        HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.MethodNotAllowedException, HTTP.ForbiddenException, HTTP.BadRequestException,
        HTTP.LockedException, HTTP.PreconditionFailedException {
            try {
                System.out.printf("  copy %s -> %s%n", this.getURI(), destination);
                
                WebDAV.Resource destResource = this.getBackend().getResource(this.getURI().resolve(destination), this.getIdentity());
                if (this.isCollection()) {
                    if (destResource.exists()) {
                        destResource.delete();
                        destResource.createCollection();
                        return HTTP.Response.Status.NO_CONTENT;
                    }
                    destResource.createCollection();

                    this.copyProperties(propertyFile(this), propertyFile(destResource));
                    return HTTP.Response.Status.CREATED;
                } else {
                    if (destResource.exists()) {
                        InputStream in = this.asInputStream();
                        OutputStream out = destResource.asOutputStream();
                        try {
                            // Perhaps the resource should do the job of copying itself to another resource. That way it can be observant of optimal buffer sizes, etc.
                            HttpUtil.transferTo(in, out, 1024*1024);
                        } finally {
                            try { in.close(); } catch (IOException e) { }
                            try { out.close(); } catch (IOException e) { }
                        }

                        this.copyProperties(propertyFile(this), propertyFile(destResource));
                        return HTTP.Response.Status.NO_CONTENT;
                    } else {
                        destResource.create(this.getContentType());
                    }
                    InputStream in = this.asInputStream();
                    OutputStream out = destResource.asOutputStream();
                    try {
                        // Perhaps the resource should do the job of copying itself to another resource. That way it can be observant of optimal buffer sizes, etc.
                        HttpUtil.transferTo(in, out, 1024*1024);
                    } finally {
                        try { in.close(); } catch (IOException e) { }
                        try { out.close(); } catch (IOException e) { }
                    }
                    this.copyProperties(propertyFile(this), propertyFile(destResource));
                    return HTTP.Response.Status.CREATED;
                }
            } catch (IOException e) {
                throw new HTTP.InternalServerErrorException(this.getURI().toString(), e);
            } finally {

            }
        }

        public WebDAV.Resource create(InternetMediaType contentType) throws BadRequestException, ConflictException, GoneException, InternalServerErrorException, InsufficientStorageException,
        LockedException, MethodNotAllowedException, NotFoundException, UnauthorizedException, ForbiddenException {
            PathName path = new PathName(this.fullPath);
            try {
                File parent = new File(path.parent().toString());
                if (!parent.exists()) {
                    throw new HTTP.ConflictException(this.getURI(), "Parent does not exist.");
                }

                File thisFile = new File(this.fullPath);
                if (thisFile.exists()) {
                    throw new HTTP.MethodNotAllowedException(this.getURI(), "Resource already exists.", this.getAccessAllowed());
                }
                thisFile.createNewFile();
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
            return this;
        }

        public void delete() throws InternalServerErrorException, InsufficientStorageException, NotFoundException, GoneException, UnauthorizedException, ConflictException {
            File file = new File(this.fullPath);
            if (file.delete() == false) {
                File propertyFile = propertyFile(this);
                propertyFile.delete();
                throw new HTTP.NotFoundException(this.getURI());                
            }
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

            PathName path = new PathName(this.fullPath);
            File parent = new File(path.parent().toString());
            if (!parent.exists()) {
                throw new HTTP.ConflictException(this.getURI(), "Parent does not exist.");
            }
            
            File thisCollection = new File(this.fullPath);
            if (thisCollection.mkdir() == false) {
                throw new HTTP.MethodNotAllowedException(this.getURI(), "Not created", this.getAccessAllowed());
            } else {
                ;
            }
        }

        public Collection<sunlabs.asdf.web.http.WebDAV.Resource> getCollection()
        throws InternalServerErrorException, NotFoundException, UnauthorizedException, GoneException, ConflictException {

            Collection<WebDAV.Resource> result = new HashSet<WebDAV.Resource>();

            File file = new File(this.fullPath);
            for (String name : file.list()) {
                WebDAV.Resource resource = 
                    new FileSystemResource(this.backend, this.documentRoot.toString(), URI.create(new PathName(this.getURI().getPath()).append(name).toString()), this.getIdentity());
                result.add(resource);         
            }

            return result;
        }

        public boolean isCollection() {
            File file = new File(this.fullPath);
            return file.isDirectory();                
        }

        @Override
        public HTTP.Response.Status move(URI destination)  throws InternalServerErrorException, BadRequestException, GoneException,
        InsufficientStorageException, ConflictException, NotFoundException, UnauthorizedException, PreconditionFailedException, LockedException, ForbiddenException {
            File file = new File(this.fullPath);            
            File dest = new File(this.documentRoot.append(destination.getPath()).toString());
            if (dest.exists()) {
                throw new HTTP.ConflictException(destination, "Destination already exists.");
            }
            
            File fromProps = FileSystemResource.propertyFile(this.fullPath);
            File toProps = FileSystemResource.propertyFile(dest.getPath());
            this.copyProperties(fromProps, toProps);
            
            if (file.renameTo(dest)) {
                fromProps.delete();
                return HTTP.Response.Status.CREATED;
            }
            toProps.delete();
            throw new HTTP.InternalServerErrorException();
        }
        
        public Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> getProperties()
        throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
            try {
                Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> result = new HashMap<WebDAV.Resource.Property.Name,WebDAV.Resource.Property>();

              if (this instanceof WebDAV.Resource.DAV2) {
                  result.put(WebDAV.Resource.DAV2.lockDiscovery, new WebDAV.Resource.Property(WebDAV.Resource.DAV2.lockDiscovery, "<dav:lockdiscovery xmlns:dav=\"DAV:\" />", false));
              }
                
                // Load the properties from the properties storage file
                // Then override them with the live values below
                
                File propertyFile;
                if (this.isCollection()) {
                    propertyFile = new File(this.fullPath, "properties~");
                } else {
                    propertyFile = new File(String.format("%s~", this.fullPath));
                }

                Properties props = new Properties();
                
                try {
                    InputStream stream = new FileInputStream(propertyFile);
                    try {
                        props.load(stream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try { stream.close(); } catch (IOException e) { } 
                    }
                } catch (FileNotFoundException e) {
                    // ignore, there is no property file.
                }
                for (Object key : props.keySet()) {
                    WebDAV.Resource.Property prop = new WebDAV.Resource.Property(new WebDAV.Resource.Property.Name(key.toString()), props.getProperty(key.toString()), false);
                    result.put(prop.getName(), prop);
                }

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

                result.put(WebDAV.Resource.DAV1.creationDate, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.creationDate, WebDAV.RFC3339Timestamp(this.getCreationDate()), true));

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
                result.put(WebDAV.Resource.DAV1.getLastModified, new WebDAV.Resource.Property(WebDAV.Resource.DAV1.getLastModified, WebDAV.RFC3339Timestamp(this.getLastModified()), true));


                return result;
            } finally {

            }
        }
        
        public void setProperties(Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> properties)  throws HTTP.InternalServerErrorException,
        HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, HTTP.InsufficientStorageException {
            try {                
                Properties props = new Properties();

                for (WebDAV.Resource.Property p : properties.values()) {                    
                    props.setProperty(p.getCanonicalName(), p.getValue());
                }

                File propertyFile;
                if (this.isCollection()) {
                    propertyFile = new File(this.fullPath, "properties~");
                } else {
                    propertyFile = new File(String.format("%s~", this.fullPath));
                }

                OutputStream out = new FileOutputStream(propertyFile);
                try {
                    props.store(out, "");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try { out.close(); } catch (IOException e) { } 
                }
            } catch (IOException e) {
                throw new HTTP.InternalServerErrorException(this.getURI(), e);
            } finally {

            }
        }
        
        private boolean copyProperties(File fromProps, File toProps) {
            Properties props = new Properties();
            InputStream in = null;
            OutputStream out = null;
            try {
                try {
                    in = new FileInputStream(fromProps);
                    out = new FileOutputStream(toProps);
                    props.load(in);
                    props.store(out, "");
                } catch (IOException e) {
                    toProps.delete();
                    return false;
                }
            } finally {
                try { if (in != null) in.close(); } catch (IOException e) {} 
                try { if (out != null) out.close(); } catch (IOException e) {} 
            }
            return true;
        }        
    }    
}
