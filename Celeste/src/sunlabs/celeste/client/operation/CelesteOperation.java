package sunlabs.celeste.client.operation;

import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;
import sunlabs.titan.BeehiveObjectId;

public interface CelesteOperation extends Serializable {

    public String getOperationName();

    public BeehiveObjectId getClientId();

    /**
     * Get the {@link BeehiveObjectId} of the {@link VersionObject} of the file that this operation applies to.
     * <p>
     * By convention if this value is {@code null} or all zeros (deprecated) (See {@link BeehiveObjectId#ZERO}),
     * then this operation is to apply to the current version of the file.
     * </p>
     * @return
     */
    public BeehiveObjectId getVObjectId();
    
    public BeehiveObjectId getId();

    /**
     * Return the access control privilege required to invoke this operation.
     *
     * </p><p>
     *
     * Each concrete subclass must implement this method by mapping it onto
     * the corresponding privilege defined by the {@code
     * CelesteACL.CelesteOps} enumeration.
     *
     * </p>
     *
     * @return  the privilege required to invoke this operation
     */
    public CelesteACL.CelesteOps getRequiredPrivilege();


    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws Exception ;
}
