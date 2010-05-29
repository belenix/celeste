package sunlabs.celeste.client.operation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL.CelesteOps;
import sunlabs.celeste.node.services.CelesteClientDaemon;

public class InspectLockOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;
    public static final String name = "inspectLock";

    /**
     * @param fileIdentifier The {@link FileIdentifier} of the file lock to inspect.
     * @param clientId The {@link BeehiveObjectId} of the client's credential.
     * @param vObjectId The {@link BeehiveObjectId} predicate specifying the expected recent version of the file.
     */
    public InspectLockOperation(FileIdentifier fileIdentifier, BeehiveObjectId clientId) {
        super(InspectLockOperation.name, fileIdentifier, clientId, null);
    }

    @Override
    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {
        return celeste.performOperation(this, ois);
    }

    @Override
    public CelesteOps getRequiredPrivilege() {
        // TODO Auto-generated method stub
        return null;
    }

}
