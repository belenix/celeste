package sunlabs.titan.howto.exampleobject;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.object.InspectableObject;

public interface ExampleObject {
    public interface Handler extends InspectableObject.Handler<ExampleObject.Object> {

        public void createObject() throws TitanObjectStoreImpl.InvalidObjectException, TitanObjectStoreImpl.ObjectExistenceException,
        TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.UnacceptableObjectException, ClassNotFoundException, TitanObjectStoreImpl.DeleteTokenException;
        
    }
    
    public interface Object extends InspectableObject.Handler.Object, TitanObject {
        
    }
}
