package sunlabs.titan.node.object.nodeobject;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.object.InspectableObject;

/**
 * Experimental object representing a node.
 *
 */
public interface NodeObject {
    public interface Handler extends InspectableObject.Handler<NodeObject.Object> {

        public NodeObject.Object createObject() throws TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException,
        TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.UnacceptableObjectException, ClassNotFoundException, TitanObjectStoreImpl.DeleteTokenException;
        
    }
    
    public interface Object extends InspectableObject.Handler.Object, TitanObject {
        
    }
}
