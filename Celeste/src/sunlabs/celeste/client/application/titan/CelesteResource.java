package sunlabs.celeste.client.application.titan;

import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CONTENT_TYPE_NAME;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.WebDAV;
import sunlabs.asdf.web.http.HTTP.Identity;
import sunlabs.asdf.web.http.HTTP.Request;
import sunlabs.asdf.web.http.HTTP.Response;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.HierarchicalFileSystem;
import sunlabs.celeste.client.filesystem.tabula.PathName;

public class CelesteResource extends WebDAV.AbstractResource implements WebDAV.Resource.DAV1, WebDAV.Resource.DAV2, WebDAV.Resource.DAV3 /*WebDAV.Apache*/ {
    private final String fileSystemName;
    private CelesteFileSystem fileSystem;
    private CelesteFileSystem.File file;
    private final PathName path;
    private InetSocketAddress celeste;
    private CelesteProxy.Cache proxyCache;

    public CelesteResource(CelesteBackend backend, InetSocketAddress celeste, CelesteProxy.Cache proxyCache, URI requestURI, HTTP.Identity identity)
    throws FileException.BadVersion, FileException.CapacityExceeded, FileException.CelesteFailed, FileException.CelesteInaccessible,
    FileException.CredentialProblem, FileException.Deleted, FileException.DirectoryCorrupted, FileException.IOException,
    FileException.FileSystemNotFound, FileException.Runtime, FileException.PermissionDenied, FileException.ValidationFailed {
        super(backend, URI.create(requestURI.getRawPath()), identity);

        this.celeste = celeste;
        this.proxyCache = proxyCache;

        this.fileSystemName = CelesteBackend.getCelesteNameSpaceName(requestURI);
        this.path = new PathName(CelesteBackend.getCelesteFileName(requestURI));
    }

    private CelesteFileSystem getFileSystem() throws FileException.CelesteInaccessible, FileException.CredentialProblem, FileException.IOException,
    FileException.CelesteFailed, FileException.FileSystemNotFound, FileException.Runtime,
    FileException.BadVersion, FileException.Deleted, FileException.PermissionDenied, FileException.ValidationFailed, FileException.DirectoryCorrupted {
        if (this.fileSystem == null) {
            String name = this.getIdentity().getProperty(HTTP.Identity.NAME, null);
            String password = this.getIdentity().getProperty(HTTP.Identity.PASSWORD, null);
            this.fileSystem = new CelesteFileSystem(this.celeste, this.proxyCache, this.fileSystemName, name, password);
        }
        return this.fileSystem;
    }
    
    private boolean testPathName(PathName name) {
        try {
            this.getFileSystem().getFile(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private CelesteFileSystem.File getFile(PathName path) throws
      HTTP.InternalServerErrorException,
      HTTP.NotFoundException,
      HTTP.GoneException,
      HTTP.UnauthorizedException, HTTP.ConflictException {

        try {
            return this.getFileSystem().getFile(path);
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotDirectory e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.UnauthorizedException(this.getURI(), e, this.backend.getAuthentication(this.getURI()));
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        }
    }

    private CelesteFileSystem.File getFile() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        if (this.file == null) {
            this.file = this.getFile(this.path);
        }
        return this.file;
    }
    
    public void delete() throws HTTP.InternalServerErrorException, HTTP.InsufficientStorageException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        CelesteFileSystem.File thisFile = this.getFile();
        try {
            // I want to be able to change the delete time to live on a CelesteFileSyste.File.
//            OrderedProperties attrs = thisFile.getAttributes(null);
//            try {
//                attrs.store(System.out, "");
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            if (thisFile.isDirectory()) {
                this.getFileSystem().deleteDirectory(thisFile.getPathName());
            } else {
                this.getFileSystem().deleteFile(thisFile.getPathName());                    
            }
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InsufficientStorageException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.GoneException(this.getURI(), e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotDirectory e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.UnauthorizedException(this.getURI(), e, this.getBackend().getAuthentication(this.getURI()));
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.DTokenMismatch e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IsDirectory e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.NotEmpty e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.InvalidName e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Locked e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.RetriesExceeded e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        }
    }
    
    public boolean exists() throws HTTP.InternalServerErrorException, HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            if (!testPathName(this.path.getDirName()))
                throw new HTTP.ConflictException(this.getURI(), this.path.getDirName().toString());
            
            return this.getFile().exists();
        } catch (HTTP.GoneException e) {
            return false;
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (HTTP.NotFoundException e) {
            return false;
        }
    }
    
    public HTTP.Response.Status move(URI destination)
    throws HTTP.InternalServerErrorException, HTTP.BadRequestException, HTTP.GoneException, HTTP.InsufficientStorageException,
    HTTP.ConflictException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.PreconditionFailedException {
        CelesteFileSystem.File file = this.getFile();
        try {
            // Compute the pathname of the destination relative to the conventional "file-system" name in the URI.
            HierarchicalFileSystem.FileName dest = new PathName(CelesteBackend.getCelesteFileName(destination));
//            System.out.printf("move %s -> %s%n", file.getPathName(), dest);
            file.getFileSystem().renameFile(file.getPathName(), dest);
            return HTTP.Response.Status.CREATED;
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InsufficientStorageException(destination, e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.Deleted e) {
            throw new HTTP.GoneException(destination, e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.NotFoundException(destination, e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.NotDirectory e) {
            throw new HTTP.NotFoundException(destination, e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(destination, e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.UnauthorizedException(destination, e, this.getBackend().getAuthentication(this.getURI()));
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.IsDirectory e) {
            throw new HTTP.ConflictException(destination, e);
        } catch (FileException.NotEmpty e) {
            throw new HTTP.ConflictException(destination, e);
        } catch (FileException.InvalidName e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.Locked e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.RetriesExceeded e) {
            throw new HTTP.InternalServerErrorException(destination, e);
        } catch (FileException.Exists e) {
            throw new HTTP.PreconditionFailedException(destination, e);
        }
    }
    
    public long getCreationDate() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        return this.getFile().getCreationTime();
    }
    
    public long getContentLength() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            return this.getFile().length();
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (sunlabs.celeste.client.filesystem.FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.GoneException(this.getURI(), e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        }
    }
    
    public String getDisplayName() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        // XXX Use a property actually set in the file's metadata.
        // If that property is missing, generate it from the URL.
        return this.getFile().getPathName().getBaseName();
    }
    
    public long getLastModified() throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            return this.getFile().lastModified();
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (sunlabs.celeste.client.filesystem.FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } finally {

        }
    }

    @Override
    public Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> getProperties()
    throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> result = super.getProperties();
            
            Properties properties = this.getFile().getClientProperties();
            
            for (Object key : properties.keySet()) {
                WebDAV.Resource.Property.Name name = new WebDAV.Resource.Property.Name(key.toString());
                result.put(name, new WebDAV.Resource.Property(name, properties.getProperty(key.toString()), false));
            }
            
            return result;
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (sunlabs.celeste.client.filesystem.FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } finally {

        }
    }

    // XXX Fix this to not require the subclass to handle the dav properties.
    public void setProperties(Map<WebDAV.Resource.Property.Name,WebDAV.Resource.Property> properties)
    throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException, HTTP.LockedException, HTTP.InsufficientStorageException {
        try {
            super.setProperties(properties);
            
            OrderedProperties props = new OrderedProperties();
            
            for (WebDAV.Resource.Property p : properties.values()) {
              if (!p.isProtected()) {
                    props.setProperty(p.getName().toString(), p.getValue());
                }
            }

            this.getFile().setClientProperties(props);
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (sunlabs.celeste.client.filesystem.FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.GoneException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Locked e) {
            throw new HTTP.LockedException(this.getURI(), e);
        } catch (FileException.RetriesExceeded e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InsufficientStorageException(this.getURI(), e);
        } finally {

        }
    }
    
    public void createCollection() throws
      HTTP.MethodNotAllowedException,
      HTTP.InternalServerErrorException,
      HTTP.InsufficientStorageException,
      HTTP.GoneException,
      HTTP.UnauthorizedException,
      HTTP.ConflictException,
      HTTP.ForbiddenException,
      HTTP.BadRequestException,
      HTTP.LockedException {
        try {
            this.getFileSystem().createDirectory(this.path);
        } catch (FileException.Exists e) {
            throw new HTTP.MethodNotAllowedException(this.getURI(), "Resource already exists", this.getAccessAllowed());
        } catch (FileException.NotFound e) {
            throw new HTTP.ConflictException(this.getURI(), "Parent collection not found", e);
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InsufficientStorageException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.InvalidName e) {
            throw new HTTP.BadRequestException(this.getURI(), e);
        } catch (FileException.Locked e) {
            throw new HTTP.LockedException(this.getURI(), e);
        } catch (FileException.NotDirectory e) {
            throw new HTTP.ForbiddenException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.RetriesExceeded e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        }
    }
    
    public WebDAV.Resource create(InternetMediaType contentType) throws
      HTTP.BadRequestException,
      HTTP.ConflictException,
      HTTP.GoneException,
      HTTP.InternalServerErrorException,
      HTTP.InsufficientStorageException,
      HTTP.LockedException,
      HTTP.MethodNotAllowedException,
      HTTP.NotFoundException,
      HTTP.UnauthorizedException {
        try {
            OrderedProperties fileAttributes = new OrderedProperties();
            fileAttributes.setProperty(CONTENT_TYPE_NAME, contentType.toString());
            OrderedProperties clientProps = new OrderedProperties();
            this.getFileSystem().createFile(this.path, fileAttributes, clientProps);
            return this;
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Deleted e) {
            throw new HTTP.GoneException(this.getURI(), e);
        } catch (FileException.Exists e) {
           throw new HTTP.MethodNotAllowedException(this.getURI(), e, this.getAccessAllowed());
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.InvalidName e) {
            throw new HTTP.BadRequestException(this.getURI(), e);
        } catch (FileException.Locked e) {
            throw new HTTP.LockedException(this.getURI(), e);
        } catch (FileException.NotDirectory e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.ConflictException(this.getURI(), e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.UnauthorizedException(this.getURI(), e, this.getBackend().getAuthentication(this.getURI()));
        } catch (FileException.RetriesExceeded e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        }
    }
    
    public OutputStream asOutputStream() throws
      HTTP.InternalServerErrorException,
      HTTP.GoneException,
      HTTP.MethodNotAllowedException,
      HTTP.InsufficientStorageException,
      HTTP.NotFoundException,
      HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            CelesteFileSystem.File f = this.getFile();
            int blockSize = f.getBlockSize();
            OutputStream fout = f.getOutputStream(true, f.getBlockSize());
            return new BufferedOutputStream(fout, blockSize);
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IsDirectory e) {
            throw new HTTP.MethodNotAllowedException(this.getURI(), e, this.getAccessAllowed());
        } catch (IOException e) {
            // XXX This is because CelesteFileSytem.File.truncate(long size) turns all FileException instances into java.io.IOException.
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } finally {

        }
    }

    public InputStream asInputStream() throws
    HTTP.InternalServerErrorException,
    HTTP.GoneException,
    HTTP.MethodNotAllowedException,
    HTTP.NotFoundException,
    HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            CelesteFileSystem.File f = this.getFile();
            return new BufferedInputStream(f.getInputStream(), f.getBlockSize());
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.IsDirectory e) {
            throw new HTTP.MethodNotAllowedException(this.getURI(), e, this.getAccessAllowed());
        }
    }

    public boolean isCollection() throws HTTP.InternalServerErrorException, HTTP.UnauthorizedException, HTTP.GoneException, HTTP.ConflictException {
        try {
            this.file = this.getFile();
            return file.isDirectory();
        } catch (HTTP.NotFoundException e) {
            //
        }
        return false;
    }

    public Collection<WebDAV.Resource> getCollection() throws
    HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.GoneException, HTTP.ConflictException, HTTP.BadRequestException {

        if (this.isCollection()) {
            Collection<WebDAV.Resource> result = new LinkedList<WebDAV.Resource>();
            CelesteFileSystem.File f = this.getFile();
            if (f.isDirectory()) {
                CelesteFileSystem.Directory dir = (CelesteFileSystem.Directory) f;
                try {
                    String[] names = dir.list();
                    for (String name : names) {
                        if (!name.equals(".") && !name.equals("..")) {
                            URI uri = new URI(null, null, this.getURI().getRawPath() + "/" + name, null, null).normalize();
                            result.add(this.getBackend().getResource(uri, this.getIdentity()));
                        }
                    }
                } catch (FileException.BadVersion e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);
                } catch (FileException.DirectoryCorrupted e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.CelesteFailed e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.CelesteInaccessible e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.CredentialProblem e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.Deleted e) {
                    throw new HTTP.GoneException(this.getURI(), e);   
                } catch (FileException.IOException e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.NotFound e) {
                    throw new HTTP.NotFoundException(this.getURI(), e);  
                } catch (FileException.PermissionDenied e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);
                } catch (FileException.Runtime e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (FileException.ValidationFailed e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);   
                } catch (URISyntaxException e) {
                    throw new HTTP.InternalServerErrorException(this.getURI(), e);
                } finally {

                }
            }
            return result;
        }
        return null;
    }
    
    @Override
    public InternetMediaType getContentType()
    throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
        try {
            return this.getFile().getContentType();
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);   
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);   
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);   
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);   
        } catch (FileException.NotFound e) {
            throw new HTTP.NotFoundException(this.getURI(), e);  
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(this.getURI(), e);   
        } finally {

        }
    }

    @Override
    public Collection<String> getComplianceClass() throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException {
        Collection<String> result = super.getComplianceClass();
        result.add("<http://apache.org/dav/propset/fs/1>");
        return result;
    }

    public Response post(Request request, Identity identity) throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.NotFoundException, HTTP.UnauthorizedException, HTTP.ConflictException {
        // TODO Auto-generated method stub
        return null;
    }
}
