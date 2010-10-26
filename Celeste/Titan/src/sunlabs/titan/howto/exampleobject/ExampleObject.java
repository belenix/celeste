package sunlabs.titan.howto.exampleobject;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.object.InspectableObject;

public interface ExampleObject {
    public interface Handler extends InspectableObject.Handler<ExampleObject.Object> {

        public void createObject() throws BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.ObjectExistenceException,
        BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, ClassNotFoundException, BeehiveObjectStore.DeleteTokenException;
        
    }
    
    public interface Object extends InspectableObject.Handler.Object, TitanObject {
        
    }
}
