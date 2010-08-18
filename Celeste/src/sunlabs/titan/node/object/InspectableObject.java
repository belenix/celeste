package sunlabs.titan.node.object;

import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.BeehiveObject;

/**
 * {@link BeehiveObject} and {@link BeehiveObjectHandler} classes implementing the interfaces specified
 * in this class implement the capability of objects in the object pool to be
 * inspected.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class InspectableObject {
    public interface Handler<T extends InspectableObject.Handler.Object> extends BeehiveObjectHandler {
        public interface Object extends BeehiveObjectHandler.ObjectAPI {
            /**
             * Produce an {@link XHTML.EFlow} element consisting of the inspectable elements of this {@link InspectableObject.Handler.Object}.
             * @param uri The HTTP Request URI.
             * @param props URL-encoded properties from the request.
             */
            public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props);

        }
    }
}
