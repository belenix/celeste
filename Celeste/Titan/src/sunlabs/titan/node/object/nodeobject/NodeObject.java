package sunlabs.titan.node.object.nodeobject;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveObjectStore.DeleteTokenException;
import sunlabs.titan.node.object.InspectableObject;

public interface NodeObject {
    public interface Handler extends InspectableObject.Handler<NodeObject.Object> {

        public void createObject() throws BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.ObjectExistenceException,
        BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, ClassNotFoundException, BeehiveObjectStore.DeleteTokenException;
        
    }
    
    public interface Object extends InspectableObject.Handler.Object, TitanObject {
        
    }
}
