package sunlabs.celeste.client.operation;

import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL.CelesteOps;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.api.Credential;

public class ProbeOperation extends AbstractCelesteOperation {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String name = "Probe";
    
    private byte[] payload;
    
    public ProbeOperation() {
        super(ProbeOperation.name, new FileIdentifier(null, null), null, null);
    }
    
    public ProbeOperation(Credential credential, byte[] payload) {
        this();
        this.payload = payload;
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws Exception {
        return celeste.performOperation(this, ois);
    }

    @Override
    public CelesteOps getRequiredPrivilege() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public byte[] getPayload() {
        return this.payload;
    }
    
    @Override
    public String toString() {
        if (this.payload == null)
            return super.toString();
        return String.format("%s payload=%d", super.toString(), this.payload.length);
                
    }
}
