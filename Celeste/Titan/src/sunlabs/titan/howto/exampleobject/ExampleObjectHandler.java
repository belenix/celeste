package sunlabs.titan.howto.exampleobject;

import java.net.URI;
import java.util.Map;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML.EFlow;
import sunlabs.asdf.web.http.HTTP.Message;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.api.Publish.PublishUnpublishResponse;

public class ExampleObjectHandler extends AbstractObjectHandler implements ExampleObject.Handler {
    private static final long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(ExampleObjectHandler.class, ExampleObjectHandler.serialVersionUID);
    
    public static class Object extends AbstractTitanObject implements ExampleObject.Object {
        private static final long serialVersionUID = 1L;

        public Object(TitanGuid deleteTokenId, long timeToLive) {
            super(ExampleObjectHandler.class, deleteTokenId, timeToLive);
        }

        @Override
        public TitanGuid getDataId() {
            return new TitanGuidImpl(TitanGuidImpl.ZERO);
        }
        
        public TitanGuid getObjectId() {
            return new TitanGuidImpl(TitanGuidImpl.ZERO);
        }


        public EFlow inspectAsXHTML(URI uri, Map<String, Message> props) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public ExampleObjectHandler(TitanNode node) throws JMException {
        super(node, ExampleObjectHandler.name, "Node Object");
    }

    public PublishUnpublishResponse publishObject(TitanMessage message)  throws ClassNotFoundException, ClassCastException,
        BeehiveObjectPool.Exception, BeehiveObjectStore.Exception {

        return null;
    }

    public PublishUnpublishResponse unpublishObject(TitanMessage message) throws ClassNotFoundException, ClassCastException {

        return null;
    }

    public void createObject() throws BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.ObjectExistenceException,
    BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, ClassNotFoundException, BeehiveObjectStore.DeleteTokenException {
        ExampleObject.Object object = new ExampleObjectHandler.Object(TitanGuidImpl.ZERO, TitanObject.INFINITE_TIME_TO_LIVE);
        object = (ExampleObject.Object) BeehiveObjectStore.CreateSignatureVerifiedObject(object.getObjectId(), object);
        this.node.getObjectStore().create(object);        
    }
}
