package sunlabs.asdf.web.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

/**
 * An RFC 4918 compliant WebDAV Server
 *
 */
public class WebDAVServer extends HTTPServer implements WebDAV.Server {

    public WebDAVServer(InputStream input, OutputStream output) {
        super(input, output);
    }

    public WebDAVServer(SocketChannel channel) throws IOException, MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        super(channel);
    }

    public WebDAVServer(SocketChannel channel, OutputStream inputTap, OutputStream outputTap)
    throws IOException, MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        super(channel, inputTap, outputTap);
    }
}
