package sunlabs.celeste.client.application.titan;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.WebDAV;
import sunlabs.asdf.web.http.HTTP.Authenticate;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.filesystem.FileException;

public class CelesteBackend implements WebDAV.Backend {
    private InetSocketAddress celeste;
    private final CelesteProxy.Cache proxyCache;
    private HTTP.Authenticate authenticate;

    public static Map<Class<? extends Exception>,Class<? extends Exception>> fileExceptionToHTTPException = new HashMap<Class<? extends Exception>,Class<? extends Exception>>();
    static {
        fileExceptionToHTTPException.put(FileException.CelesteInaccessible.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.CredentialProblem.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.CapacityExceeded.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.IOException.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.CelesteFailed.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.FileSystemNotFound.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.NotDirectory.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.NotFound.class, HTTP.NotFoundException.class);
        fileExceptionToHTTPException.put(FileException.Runtime.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.BadVersion.class, HTTP.InternalServerErrorException.class);
        fileExceptionToHTTPException.put(FileException.Deleted.class, HTTP.GoneException.class);
    }

    public CelesteBackend(InetSocketAddress celeste, CelesteProxy.Cache proxyCache) {
        this.celeste = celeste;
        this.proxyCache = proxyCache;
        this.authenticate = new CelesteAuthenticate();
    }

    public WebDAV.Resource getResource(URI uri, HTTP.Identity identity) throws HTTP.InternalServerErrorException, HTTP.GoneException, HTTP.UnauthorizedException {
        try {
            if (identity.getProperty(HTTP.Identity.NAME, null) == null) {
                throw new HTTP.UnauthorizedException(uri, this.getAuthentication(uri));
            }
            return new CelesteResource(this, this.celeste, this.proxyCache, uri, identity);
        } catch (FileException.CelesteInaccessible e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.CredentialProblem e) {
            throw new HTTP.UnauthorizedException(uri, e, this.getAuthentication(uri));
        } catch (FileException.CapacityExceeded e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.IOException e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.CelesteFailed e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.FileSystemNotFound e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.Runtime e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.BadVersion e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.Deleted e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.DirectoryCorrupted e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        } catch (FileException.PermissionDenied e) {
            throw new HTTP.UnauthorizedException(uri, e, this.getAuthentication(uri));
        } catch (FileException.ValidationFailed e) {
            throw new HTTP.InternalServerErrorException(uri, e);
        }
    }

    /**
     * An implementation of the {@link HTTP.Authenticate} interface suitable for Celeste authentication.
     */
    public static class CelesteAuthenticate implements HTTP.Authenticate {
        private String realm;

        public CelesteAuthenticate() {
            this.realm = "Celeste";
        }

        public String generateChallenge() {
            return String.format("Basic realm=\"%s\"", this.realm);
        }            
    }

    public static String getCelesteNameSpaceName(URI uri) {
        return getCelesteNameSpaceName(uri.getPath());
    }

    public static String getCelesteNameSpaceName(String pathName) {
        String path = pathName.replaceAll("/+", "/");
        String[] tokens = path.split("/", 3);
        String fileSystemName = tokens[1];
        return fileSystemName;
    }

    public static String getCelesteFileName(URI uri) {
        return CelesteBackend.getCelesteFileName(uri.getPath());
    }

    public static String getCelesteFileName(String pathName) {
        String path = pathName.replaceAll("/+", "/");
        String[] tokens = path.split("/", 3);
        return "/" + (tokens.length >= 3 ? tokens[2] : "");
    }

    public Authenticate getAuthentication(URI uri) {
        return this.authenticate;
    }
    
    public InetSocketAddress getCeleste() {
        return this.celeste;
    }
    
    public CelesteProxy.Cache getProxyCache() {
        return this.proxyCache;
    }
}
