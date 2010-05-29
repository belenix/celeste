package sunlabs.celeste.client.operation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.services.CelesteClientDaemon;

public class ScriptableOperation extends AbstractCelesteOperation {
    private static final long serialVersionUID = 1L;

    public static final String name = "runScript";

    private final long offset;
    private final int length;

    /**
     * Creates a {@code ScriptableOperation} object encapsulating the fields
     * given as arguments.
     *
     * @param operationName a name identifying this kind of operation
     * @param fileIdentifier The {@link FileIdentifier} of the file.
     * @param readerId      the {@link BeehiveObjectId} of the {@link Credential} authorising the operation
     * @param vObjectId     the {@code BeehiveObjectId} of the version of the file this operation is to read, or {@code null} if
     *                      it is to read the latest version
     * @param offset        the starting offset within the file of the span to be read
     * @param length        the length of the span to be read (or {@code -1L}
     *                      to read to the end of file)
     */
    protected ScriptableOperation(FileIdentifier fileIdentifier, BeehiveObjectId readerId, BeehiveObjectId vObjectId, long offset, int length) {
        super(ScriptableOperation.name, fileIdentifier, readerId, vObjectId);
        this.offset = offset;
        this.length = length;
    }

    public ScriptableOperation(FileIdentifier fileIdentifier, BeehiveObjectId readerId, long offset, int length) {
        this(fileIdentifier, readerId, null, offset, length);
    }

    @Override
    public BeehiveObjectId getId() {
        BeehiveObjectId id = super.getId();
        return id;
    }

    @Override
    public CelesteACL.CelesteOps getRequiredPrivilege() {
        return CelesteACL.CelesteOps.readFile;
    }

    public String toString() {
        String result = super.toString()
        + " version=" + Long.toString(ScriptableOperation.serialVersionUID)
        + " offset=" + this.offset
        + " length=" + this.length;
        return result;
    }

    public long getOffset() {
        return this.offset;
    }

    public int getLength() {
        return this.length;
    }

    public Serializable dispatch(CelesteClientDaemon celeste, ObjectInputStream ois) throws IOException, ClassNotFoundException {
        return celeste.performOperation(this, ois);
    }
}
