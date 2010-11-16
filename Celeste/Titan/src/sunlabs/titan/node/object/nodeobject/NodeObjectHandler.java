package sunlabs.titan.node.object.nodeobject;

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
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.api.Publish.PublishUnpublishResponse;

public class NodeObjectHandler extends AbstractObjectHandler implements NodeObject.Handler {
    private static final long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(NodeObjectHandler.class, NodeObjectHandler.serialVersionUID);
    
    public static class Object extends AbstractTitanObject implements NodeObject.Object {
        private static final long serialVersionUID = 1L;

        public Object(TitanGuid deleteTokenId, long timeToLive) {
            super(NodeObjectHandler.class, deleteTokenId, timeToLive);
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

    public NodeObjectHandler(TitanNode node) throws JMException {
        super(node, NodeObjectHandler.name, "Node Object");
    }

    public PublishUnpublishResponse publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassNotFoundException, ClassCastException,
        BeehiveObjectPool.Exception, BeehiveObjectStore.Exception {

        return null;
    }

    public PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws ClassNotFoundException, ClassCastException {

        return null;
    }

    public NodeObject.Object createObject() throws BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.ObjectExistenceException,
    BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, ClassNotFoundException, BeehiveObjectStore.DeleteTokenException {
        NodeObject.Object object = new NodeObjectHandler.Object(TitanGuidImpl.ZERO, TitanObject.INFINITE_TIME_TO_LIVE);
        object = (NodeObject.Object) BeehiveObjectStore.CreateSignatureVerifiedObject(object.getObjectId(), object);
        this.node.getObjectStore().lock(object.getObjectId());
        try {
            this.node.getObjectStore().store(object);
        } finally {
            this.node.getObjectStore().unlock(object.getObjectId());
        }
        return object;
    }
}
