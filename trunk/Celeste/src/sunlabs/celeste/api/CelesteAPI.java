/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.celeste.api;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.client.operation.DeleteFileOperation;
import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.celeste.client.operation.InspectFileOperation;
import sunlabs.celeste.client.operation.InspectLockOperation;
import sunlabs.celeste.client.operation.LockFileOperation;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.NewNameSpaceOperation;
import sunlabs.celeste.client.operation.ProbeOperation;
import sunlabs.celeste.client.operation.ReadFileOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;
import sunlabs.celeste.client.operation.SetACLOperation;
import sunlabs.celeste.client.operation.SetFileLengthOperation;
import sunlabs.celeste.client.operation.SetOwnerAndGroupOperation;
import sunlabs.celeste.client.operation.UnlockFileOperation;
import sunlabs.celeste.client.operation.WriteFileOperation;
import sunlabs.celeste.node.services.object.AnchorObject;
import sunlabs.celeste.node.services.object.BlockObject;
import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.util.OrderedProperties;

/**
 * The basic Celeste file store interface.
 * <p>
 * CAUTION: This interface will change as improvements are made.
 * </p>
 */
public interface CelesteAPI extends Closeable {
    /** The API uses serialized Java object representations of operations */
    public static final String CLIENT_PROTOCOL_OBJECT = "object";
    
    /** The API uses text representations of operations */
    public static final String CLIENT_PROTOCOL_TEXT = "text";
    
    /** The {@link TitanGuid} file's {@link AnchorObject} */
    public final static String AOBJECTID_NAME = "Celeste.AObjectId";
    
    /** The {@link TitanGuid} of the file's {@link VersionObject} */
    public final static String VOBJECTID_NAME = "Celeste.VObjectId";
    
    /** The {@link TitanGuid} of the file's lock */
    public final static String LOCKID_NAME = "Celeste.Lock.Id";

    /** The reference count of the file's lock */
    public final static String LOCKCOUNT_NAME = "Celeste.Lock.Count";

    /** The lock's token (if any) */
    public final static String LOCKTOKEN_NAME = "Celeste.Lock.Token";
    
    /** The reference count of the file's type */
    public final static String LOCKTYPE_NAME = "Celeste.Lock.Type";
    
    /** The {@link TitanGuid} of a file's Delete Token. */
    public final static String DELETETOKENID_NAME = "Celeste.DeleteTokenId";
    
    /** The String representing the file's replication information. */
    public final static String DEFAULTREPLICATIONPARAMETERS_NAME = "Celeste.ReplicationParameters";
    
    /** The maximum number bytes in size of an underlying {@link BlockObject} that comprises a file */
    public final static String DEFAULTBOBJECTSIZE_NAME = "Celeste.BObjectSize";
    
    /** The {@link TitanGuid} of the file's owner {@link Credential} */
    public final static String VOBJECT_OWNER_NAME = "Celeste.OwnerId";
    
    /** The {@link TitanGuid} of the file's group {@link Credential} */
    public final static String VOBJECT_GROUP_NAME = "Celeste.GroupId";
    public final static String VOBJECT_ACL_NAME = "Celeste.ACL";
    public final static String FILE_SIZE_NAME = "Celeste.FileSize";
    public final static String WRITE_SIGNATURE_INCLUDES_DATA = "Celeste.SignData";

    public final static String VERSION_NAME = "Celeste.Version";
    
    public final static String WRITERS_SIGNATURE_NAME = "Celeste.WritersSignature";
    public final static String CLIENTMETADATA_NAME = "Celeste.ClientMetaData";
    
    
    /**
     * Create a Celeste file.
     * <p>
     * Every Celeste file has a name, an owner,
     * and set of access control parameters which govern subsequent
     * operations on the file.
     * </p>
     * <p>
     * To create a file, you must supply a {@link CreateFileOperation}
     * object instance and the signature of that object.
     * @param operation A CreateFileOperation object instance. 
     * @param signature The signature of the creator.
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.IllegalParameterException 
     * @throws CelesteException.AlreadyExistsException 
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.RuntimeException 
     * @throws CelesteException.NotFoundException 
     */    
    public OrderedProperties createFile(CreateFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.AccessControlException, CelesteException.IllegalParameterException,
    CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.NotFoundException, CelesteException.NoSpaceException;

    public ResponseMessage probe(ProbeOperation operation)
    throws IOException, ClassNotFoundException;

    /**
     * The the globally unique identifier of this particular Celeste system.
     * 
     * @return  this Celeste confederation's globally unique identifier
     */
    public TitanGuid getNetworkObjectId();
    
    /**
     * Write to a Celeste file.
     *
     * @param data     A byte buffer containing the data to write to the file.
     * @param signature   The signature of the writer which is used to
     *                          authenticate permission to write this Celeste
     *                          file (currently this must be set to null).
     * @param writeFileOperation  
     *
     * @return a {@link ResponseMessage} encapsulating the results of the write
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.IllegalParameterException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.NotFoundException
     * @throws CelesteException.NoSpaceException
     * @throws CelesteException.RuntimeException
     * @throws CelesteException.VerificationException
     * @throws CelesteException.DeletedException
     * @throws CelesteException.OutOfDateException
     * @throws CelesteException.FileLocked
     */
    public OrderedProperties writeFile(WriteFileOperation writeFileOperation, Credential.Signature signature, ByteBuffer data)
    throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
        CelesteException.OutOfDateException, CelesteException.FileLocked;

    /**
     * Set the length of a Celeste file, extending or truncating as needed.
     * 
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.NotFoundException
     * @throws CelesteException.DeletedException 
     */
    public OrderedProperties setFileLength(SetFileLengthOperation setFileLengthOperation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.NoSpaceException,
    CelesteException.VerificationException, CelesteException.OutOfDateException, CelesteException.FileLocked;

    /**
     * Delete a Celeste file.
     * 
     * Deletion requires supplying the delete-token as a parameter in the {@link DeleteFileOperation}.
     * The delete-token is the original, secret, {@link TitanGuid} such that the {@code TitanGuid}
     * of that original delete token (ie. <i>F(F(delete-token))</i>) is equal to value given to the
     * {@link CreateFileOperation} constructor when the file was created.
     * 
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.NotFoundException
     * @throws CelesteException.DeletedException 
     * @throws CelesteException.RuntimeException 
     * @throws CelesteException.VerificationException 
     */
    public Boolean deleteFile(DeleteFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException;

    /**
     * Read a Celeste file.
     * 
     * @param operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.DeletedException 
     * @throws CelesteException.NotFoundException
     */
    public ResponseMessage readFile(ReadFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.DeletedException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException;
    
    /**
     * Return the version information of the specified Celeste file.
     * The information is encoded in a
     * {@link OrderedProperties} instance
     * in the {@link ResponseMessage ResponseMessage}
     * (see {@link ResponseMessage#getMetadata() ResponseMessage.getMetadata()})

     * <table>
     *   <tbody>
     *    <tr><td>{@link CelesteAPI#AOBJECTID_NAME AOBJECTID_NAME}</td><td>The AnchorObject TitanGuid.</td></tr>
     *    <tr><td>{@link CelesteAPI#DEFAULTBOBJECTSIZE_NAME DEFAULTBOBJECTSIZE_NAME}</td></td><td>The default BlockObject size</td></tr>
     *    <tr><td>{@link CelesteAPI#DEFAULTREPLICATIONPARAMETERS_NAME DEFAULTREPLICATIONPARAMETERS_NAME}</td></td>The replication parameters.</td></tr>
     *    <tr><td>{@link CelesteAPI#DELETETOKENID_NAME DELETETOKENID_NAME}</td></td><td>The Delete Token TitanGuid</td></tr>
     *    <tr><td>{@link CelesteAPI#FILE_SIZE_NAME FILE_SIZE_NAME}</td></td><td>The file size</td></tr>
     *    <tr><td>{@link CelesteAPI#VOBJECTID_NAME VOBJECTID_NAME}</td></td><td>The VersionObject TitanGuid</td></tr>
     *    <tr><td>{@link CelesteAPI#VOBJECT_GROUP_NAME VOBJECT_GROUP_NAME}</td></td><td>The TitanGuid of the group credential</td></tr>
     *    <tr><td>{@link CelesteAPI#VOBJECT_OWNER_NAME VOBJECT_OWNER_NAME}</td></td><td>The TitanGuid of the owner credential</td></tr>
     *    <tr><td>{@link CelesteAPI#WRITE_SIGNATURE_INCLUDES_DATA WRITES_MUST_BE_SIGNED_NAME}</td></td><td>"True" if writes are signed</td></tr>
     *    <tr><td>{@link CelesteAPI#VOBJECT_ACL_NAME VOBJECT_ACL_NAME}</td><td>The encoded AccessControlList</td></tr>
     *    <tr><td>{@link CelesteAPI#WRITERS_SIGNATURE_NAME WRITERS_SIGNATURE_NAME}</td><td>The encoded signature of the last updater</td></tr>
     *    <tr><td>{@link CelesteAPI#CLIENTMETADATA_NAME CLIENTMETADATA_NAME}</td><td>The encoded client metadata</td></tr>
     *  </tbody>
     * </table>
     * </p>
     * <p>
     * A {@code VersionObject} object-id may be supplied in the given {@link InspectFileOperation} to specify a particular
     * version of the file.  If the {@code VersionObject} object-id is {@code null},
     * then the absolute latest version is used.
     * </p>
     * @throws CelesteException.NotFoundException 
     * @throws CelesteException.RuntimeException
     */
    public ResponseMessage inspectFile(InspectFileOperation operation)
    throws IOException, ClassNotFoundException,
           CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException ;
    

    public ResponseMessage inspectLock(InspectLockOperation operation)
    throws IOException, ClassNotFoundException,
           CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException ;

    /**
     * Create a new credential.
     * 
     * The credential must not already exist.
     * 
     * @param operation
     * @param signature
     * @param credential
     *
     * @return the newly created credential's metadata
     *
     * @throws CelesteException.RuntimeException 
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AlreadyExistsException 
     * @throws CelesteException.NoSpaceException
     * @throws CelesteException.VerificationException
     */
    public TitanObject.Metadata newCredential(NewCredentialOperation operation, Credential.Signature signature, Credential credential)
    throws IOException, ClassNotFoundException,
           CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
           CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException;

    /**
     * Create a new file name-space.
     * 
     * The name-space must not already exist.
     * 
     * @param operation a {@link NewNameSpaceOperation} instance governing the name-space.
     * @param signature a {@link Credential.Signature} of the creator.
     * @param credential 
     *
     * @return the newly created name space's metadata
     *
     * @throws CelesteException.RuntimeException 
     * @throws CelesteException.AlreadyExistsException
     * @throws CelesteException.NoSpaceException
     * @throws CelesteException.VerificationException
     * @throws CelesteException.CredentialException
     */
    public TitanObject.Metadata newNameSpace(NewNameSpaceOperation operation, Credential.Signature signature, Credential credential)
    throws IOException, ClassNotFoundException,
           CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
           CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException;
    
    /**
     * Retrieves a credential from Celeste, encoded in the {@link ResponseMessage} in
     * serialized form.
     * <p>
     * The signature on the stored credential, and the binding
     * of the profile to the credential name is validated. However, upper layers
     * should not rely on this validation taking place correctly, if they do not
     * fully trust the Celeste node they are interacting with.
     * </p>
     * 
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.RuntimeException 
     * @throws CelesteException.NotFoundException 
     */
     public Credential readCredential(ReadProfileOperation operation)
     throws IOException, ClassNotFoundException, CelesteException.CredentialException, CelesteException.NotFoundException, CelesteException.RuntimeException;

    /**
     * Changes the owner and group of a Celeste file.
     *
     * @param operation a {@code SetUserAndGroupOperation} object
     *                  encapsulating the operation's parameters, including
     *                  the desired new owner and group
     *                  
     * @param signature a signature validating that the client issuing the
     *                  operation actually did so
     *
     * @return a {@link ResponseMessage} recording the results of the operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.DeletedException 
     * @throws CelesteException.NoSpaceException 
     * @throws CelesteException.NotFoundException 
     * @throws CelesteException.AlreadyExistsException 
     */
    public OrderedProperties setOwnerAndGroup(SetOwnerAndGroupOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked;

    /**
     * Changes the access control list of a Celeste file.
     *
     * @param operation a {@code SetACLOperation} object encapsulating the
     *                  operation's parameters, including the desired new
     *                  access control list
     *                  
     * @param signature a signature validating that the client issuing the
     *                  operation actually did so
     *
     * @return a {@link ResponseMessage} recording the results of the operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.AccessControlException 
     * @throws CelesteException.NotFoundException
     * @throws CelesteException.RuntimeException
     * @throws CelesteException.NoSpaceException
     * @throws CelesteException.DeletedException
     */
    public OrderedProperties setACL(SetACLOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked;

    /**
     * Runs a plug-in on a Celeste file.
     *
     * @param operation a {@code ScriptableOperation} object encapsulating the
     *                  operation's parameters.
     *                  
     * @param signature a signature validating that the client issuing the
     *                  operation actually did so
     *
     * @return a {@link ResponseMessage} recording the results of the operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.NotFoundException 
     */
    public ResponseMessage runExtension(ExtensibleOperation operation, Credential.Signature signature, Serializable ois)
    throws IOException,
           CelesteException.VerificationException, CelesteException.AccessControlException, CelesteException.CredentialException, CelesteException.NotFoundException,
           CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException, CelesteException.DeletedException,
           CelesteException.FileLocked;
    
    /**
     * Lock a Celeste file.
     *
     * @param operation a {@code LockOperation} object encapsulating the
     *                  operation's parameters.
     *                  
     * @param signature a signature validating that the client issuing the
     *                  operation actually did so
     *
     * @return a {@link ResponseMessage} recording the results of the operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.NotFoundException 
     */
    public ResponseMessage lockFile(LockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked;

    /**
     * Unlock a Celeste file.
     *
     * @param operation a {@code UnlockOperation} object encapsulating the
     *                  operation's parameters.
     *                  
     * @param signature a signature validating that the client issuing the
     *                  operation actually did so
     *
     * @return a {@link ResponseMessage} recording the results of the operation
     * @throws CelesteException.CredentialException 
     * @throws CelesteException.NotFoundException 
     */
    public ResponseMessage unlockFile(UnlockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileNotLocked, CelesteException.FileLocked;    

    /**
     * Returns the address and port of the CelesteNode providing this
     * {@code CelesteAPI} implementation.
     *
     * @return the {@code InetSocketAddress} of the underlying Celeste node
     */
    public InetSocketAddress getInetSocketAddress();
}
