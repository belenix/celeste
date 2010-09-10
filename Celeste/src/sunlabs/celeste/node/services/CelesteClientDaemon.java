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
package sunlabs.celeste.node.services;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.functional.MapFunction;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.TimeProfiler;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.CelesteException.AccessControlException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.client.operation.AbstractCelesteOperation;
import sunlabs.celeste.client.operation.CelesteOperation;
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
import sunlabs.celeste.client.operation.ScriptableOperation;
import sunlabs.celeste.client.operation.SetACLOperation;
import sunlabs.celeste.client.operation.SetFileLengthOperation;
import sunlabs.celeste.client.operation.SetOwnerAndGroupOperation;
import sunlabs.celeste.client.operation.UnlockFileOperation;
import sunlabs.celeste.client.operation.WriteFileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.ProfileCache;
import sunlabs.celeste.node.object.ExtensibleObject.JarClassLoader;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.celeste.node.services.object.AnchorObject;
import sunlabs.celeste.node.services.object.AnchorObjectHandler;
import sunlabs.celeste.node.services.object.BlockObject;
import sunlabs.celeste.node.services.object.BlockObjectHandler;
import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.celeste.node.services.object.VersionObjectHandler;
import sunlabs.celeste.util.ACL;
import sunlabs.celeste.util.CelesteIO;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractBeehiveObject;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveObjectStore.DeleteTokenException;
import sunlabs.titan.node.BeehiveObjectStore.UnacceptableObjectException;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.MutableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.object.CredentialObject;
import sunlabs.titan.node.services.object.CredentialObjectHandler;
import sunlabs.titan.util.BufferableExtent;
import sunlabs.titan.util.BufferableExtentImpl;
import sunlabs.titan.util.DOLRStatus;
import sunlabs.titan.util.ExtentBuffer;
import sunlabs.titan.util.ExtentBufferMap;
import sunlabs.titan.util.ExtentBufferStreamer;
import sunlabs.titan.util.OrderedProperties;

/**
 * Network Socket based Celeste client interface.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class CelesteClientDaemon extends AbstractTitanService {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(CelesteClientDaemon.class, CelesteClientDaemon.serialVersionUID);

    public final static Attributes.Prototype Port = new Attributes.Prototype(CelesteClientDaemon.class, "Port",
            14000,
            "The TCP port that this node listens on for incoming client connections");

    /**
     * The default number of simultaneous clients threads.
     */
    public final static Attributes.Prototype MaximumClients = new Attributes.Prototype(CelesteClientDaemon.class,
            "MaximumClients",
            32,
            "The maximum number of concurrent client connections that this node will accomodate.");

    /**
     * The number of queued, yet-to-be-accepted client connection requests.
     */
    public final static Attributes.Prototype ClientBacklog = new Attributes.Prototype(CelesteClientDaemon.class,
            "ClientBacklog",
            0,
            "The depth of the queue of yet-to-be-accepted client connection.");

    private interface ClientInterface {
        public void process();
    }

    private Thread clientDaemon;

    public CelesteClientDaemon(final TitanNode node) throws JMException {
        super(node, CelesteClientDaemon.name, "Celeste Client Handler");
        node.getConfiguration().add(CelesteClientDaemon.Port);
        node.getConfiguration().add(CelesteClientDaemon.MaximumClients);
        node.getConfiguration().add(CelesteClientDaemon.ClientBacklog);

        this.credentialCache = new ProfileCache(node);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.getConfiguration().get(CelesteClientDaemon.Port));
            this.log.config("%s", node.getConfiguration().get(CelesteClientDaemon.MaximumClients));
            this.log.config("%s", node.getConfiguration().get(CelesteClientDaemon.ClientBacklog));
        }
    }

    /**
     * An instance of this class listens on a given TCP port for incoming connections
     * and dispatches each connection to a new instance of {@link ClientServer}.
     */
    private class ClientListener extends Thread {
        private class SimpleThreadFactory implements ThreadFactory {
            private String name;
            private long counter;

            public SimpleThreadFactory(String name) {
                this.name = name;
                this.counter = 0;
            }

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(String.format("%s-client-%d", this.name, this.counter));
                this.counter++;
                return thread;
            }
        }

        private class ClientServer extends Thread implements Runnable {
            private SocketChannel socket;
            private OutputStream outputStream;

            public ClientServer(SocketChannel socket) {
                super(ClientListener.this.getThreadGroup(), socket.toString());
                this.socket = socket;
                this.setName(String.format("%s-%s", CelesteClientDaemon.this.node.getNodeId().toString(), socket.toString()));
            }

            @Override
            public void run() {
                //
                // Any exception thrown will terminate this connection.
                // Exceptions thrown internal to the CelesteNode must return
                // them through the client connection.
                // Exceptions here indicate something catastrophic.
                try {
                    this.outputStream = new BufferedOutputStream(this.socket.socket().getOutputStream());
                    this.outputStream.write(CelesteClientDaemon.this.node.getNetworkObjectId().toString().getBytes());
                    this.outputStream.write('\n');
                    this.outputStream.flush();

                    // An idea here is to read the first n bytes of input from
                    // the client and make a determination of which protocol
                    // the client is using.  Some notable clients, for example
                    // WebDAV/HTTP, will not announce what protocol it is.
                    // It will just start sending requests.
                    while (true) {
                        byte[] bytes = CelesteIO.readLineAsByteArray(this.socket.socket().getInputStream());
                        String line = new String(bytes).trim();
                        if (line.compareToIgnoreCase(CelesteAPI.CLIENT_PROTOCOL_OBJECT) == 0) {
                            ClientInterface client = new SerializedOperations(line, this.socket.socket(), this.outputStream);
                            client.process();
                            break;
                        } else {
                            if (CelesteClientDaemon.this.getLogger().isLoggable(Level.WARNING)) {
                                CelesteClientDaemon.this.getLogger().warning("Client requested unimplemented protocol '%s'%n", line);
                            }
                            CelesteIO.writeLine(this.socket.socket().getOutputStream(), "Unrecognised protocol");
                        }
                    }
                } catch (Exception e) {
                    System.err.printf("(Information only (not a problem)%n");
                    e.printStackTrace();
                }
                try {
                    this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private ThreadPoolExecutor executor;

        protected ClientListener() {
            super(new ThreadGroup(CelesteClientDaemon.this.node.getThreadGroup(),
                    CelesteClientDaemon.this.getName()),
                    CelesteClientDaemon.this.node.getThreadGroup().getName() + ":" + CelesteClientDaemon.this.getName() + ".Server");
            this.setPriority(Thread.NORM_PRIORITY);
        }

        @Override
        public void run() {
            try {
                this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(CelesteClientDaemon.this.node.getConfiguration().asInt(CelesteClientDaemon.MaximumClients),
                        new SimpleThreadFactory(CelesteClientDaemon.this.node.getNodeId() + ":"));
                //this.executor.prestartAllCoreThreads();
                try {
                    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

                    serverSocketChannel.configureBlocking(true);
                    serverSocketChannel.socket().bind(new InetSocketAddress(CelesteClientDaemon.this.node.getConfiguration().asInt(CelesteClientDaemon.Port)),
                            CelesteClientDaemon.this.node.getConfiguration().asInt(CelesteClientDaemon.ClientBacklog));
                    serverSocketChannel.socket().setReuseAddress(true);

                    while (!interrupted()) {
                        try {
                            ClientServer server = new ClientServer(serverSocketChannel.accept());
                            //                    server.start();
                            // If the pool is full and busy, new connections will be
                            // enqueued but will wait until an existing connection terminates.
                            this.executor.submit(server);
                        } catch (IOException e) {
                            CelesteClientDaemon.this.log.info("Client connection: %s", e.toString());
                        }
                    }
                } catch (java.net.BindException e) {
                    System.err.printf("%s: Check configuration assignment of %s%n", e.getLocalizedMessage(), CelesteClientDaemon.Port);
                } catch (Exception e) {
                    CelesteClientDaemon.this.log.severe("Cannot start ClientListener: %s", e.toString());
                    throw new RuntimeException(e);
                }
            } finally {
                this.executor.shutdownNow();
            }

            return;
        }

        private class SerializedOperations implements ClientInterface {
            private Socket socket;
            private ObjectOutputStream oos;
            private ObjectInputStream ois;
            private OutputStream outputStream;

            public SerializedOperations(String protocol, Socket socket, OutputStream outputStream) {
                this.socket = socket;
                this.outputStream = outputStream;
            }

            /**
             * Read and process serialized {@link CelesteOperation} instances from the socket.
             */
            public void process() {
                // Any exception thrown will terminate this connection.
                // Errors internal to the Celeste code should return a
                // StorageMessage indicating an error.  Exceptions here
                // indicate something catastrophic.
                try {
                    this.oos = new ObjectOutputStream(outputStream);
                    oos.flush(); // Write the stream header to make it readable by the client now.
                    this.ois = new ObjectInputStream(this.socket.getInputStream());
                    //
                    // Begin by reading a CelesteOperation from the ObjectInputStream.
                    // We don't know what kind of operation to expect but we can use
                    // a double-dispatch technique to discriminate between each kind.
                    // That will cause the sub-classed CelesteOperation's dispatch()
                    // method to be invoked, which in turn will call back to
                    // this class's performOperation() method with the actual
                    // operation.
                    //
                    while (!this.socket.isClosed()) {
                        CelesteOperation operation = (CelesteOperation) ois.readObject();
                        if (CelesteClientDaemon.this.log.isLoggable(Level.FINE)) {
                            CelesteClientDaemon.this.log.fine("@@ %s", operation.toString());
                        }
                        try {
                            Serializable reply = operation.dispatch(CelesteClientDaemon.this, ois);
                            if (CelesteClientDaemon.this.log.isLoggable(Level.FINE)) {
                                CelesteClientDaemon.this.log.fine("$$ %s: result=%s", operation.getOperationName(), reply.toString());
                            }
                            oos.writeObject(reply);
                            oos.flush();
                            oos.reset();
                        } catch (Exception e) {
                            e.printStackTrace();
                            // The CelesteExceptions are just sent back to the client.
                            if (!(e instanceof CelesteException))
                                e.printStackTrace();
                            try {
                                oos.writeObject(e);
                                oos.flush();
                                oos.reset();
                                if (CelesteClientDaemon.this.log.isLoggable(Level.FINE)) {
                                    CelesteClientDaemon.this.log.fine("$$ %s", e.toString());
                                }
                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            }
                        }
                    }
                } catch (EOFException e) {
//                    System.err.printf("Client closed connection %s%n", this.socket.toString());
                    // do nothing...
                } catch (IOException e) {
                    CelesteClientDaemon.this.log.fine("%s when communicating with client", e.toString());
                    //e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    CelesteClientDaemon.this.log.fine("%s when communicating with client", e.toString());
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    ResponseMessage reply = new ResponseMessage(e);
                    try {
                        oos.writeObject(reply);
                        oos.flush();
                        oos.reset();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        }
    }

    public ResponseMessage performOperation(ProbeOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.AccessControlException, CelesteException.IllegalParameterException, CelesteException.AlreadyExistsException,
        CelesteException.VerificationException, CelesteException.DeletedException, CelesteException.CredentialException, CelesteException.RuntimeException,
        CelesteException.NotFoundException, CelesteException.NoSpaceException {

        return this.probe(operation);
    }

    public OrderedProperties performOperation(CreateFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.AccessControlException, CelesteException.IllegalParameterException, CelesteException.AlreadyExistsException,
        CelesteException.VerificationException, CelesteException.DeletedException, CelesteException.CredentialException, CelesteException.RuntimeException,
        CelesteException.NotFoundException, CelesteException.NoSpaceException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.createFile(operation, signature);
    }

    public boolean performOperation(DeleteFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException,
        CelesteException.IllegalParameterException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.deleteFile(operation, signature);
    }

    public ResponseMessage performOperation(ExtensibleOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.VerificationException, CelesteException.AccessControlException, CelesteException.CredentialException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        Serializable object = (Serializable) ois.readObject();
        return this.runExtension(operation, signature, object);
    }

    public ResponseMessage performOperation(InspectFileOperation operation, ObjectInputStream ois)  throws IOException, ClassNotFoundException,
        CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException {
        return this.inspectFile(operation);
    }

    public ResponseMessage performOperation(InspectLockOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
        CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException,
        CelesteException.OutOfDateException, CelesteException.FileLocked {

        return this.inspectLock(operation);
    }

    public TitanObject.Metadata performOperation(NewNameSpaceOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.RuntimeException, CelesteException.AlreadyExistsException, CelesteException.NoSpaceException, CelesteException.VerificationException,
        CelesteException.CredentialException {

        Profile_ profile = (Profile_) ois.readObject();
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.newNameSpace(operation, signature, profile);
    }

    public TitanObject.Metadata performOperation(NewCredentialOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.AccessControlException, CelesteException.IllegalParameterException, CelesteException.AlreadyExistsException,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.VerificationException {

        Profile_ profile = (Profile_) ois.readObject();
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.newCredential(operation, signature, profile);
    }

    public ResponseMessage performOperation(ReadFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
        CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();

        return this.readFile(operation, signature);
    }

    public Credential performOperation(ReadProfileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException {

        return this.readCredential(operation);
    }

    public OrderedProperties performOperation(SetACLOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
        CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
        CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.setACL(operation, signature);
    }

    public OrderedProperties performOperation(WriteFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
        CelesteException.OutOfDateException, CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        int size = ois.readInt();
        byte[] data = new byte[size];
        ois.readFully(data);
        return this.writeFile(operation, signature, ByteBuffer.wrap(data));
    }

    public ResponseMessage performOperation(ScriptableOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException {

        @SuppressWarnings("unused")
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        @SuppressWarnings("unused")
        Serializable object = (Serializable) ois.readObject();
        return null;
        //        return this.celesteNode.runExtension((ScriptableOperation) operation, signature, object);
    }

    public OrderedProperties performOperation(SetFileLengthOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
        CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
        CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.setFileLength(operation, signature);
    }

    public OrderedProperties performOperation(SetOwnerAndGroupOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.setOwnerAndGroup(operation, signature);
    }

    public ResponseMessage performOperation(LockFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.lockFile(operation, signature);
    }

    public ResponseMessage performOperation(UnlockFileOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileNotLocked, CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.unlockFile(operation, signature);
    }

    @Override
    public synchronized void start() throws Exception {
        if (this.clientDaemon == null) {
            this.setStatus("start");
            this.clientDaemon = new ClientListener();
            this.clientDaemon.start();
        }
    }

    @Override
    public void stop() {
//        this.setStatus("stopped");
//        if (this.clientDaemon != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.clientDaemon);
//            }
//            this.clientDaemon.interrupt(); // Logged
//            this.clientDaemon = null;
//        }
    }

//    /**
//     * Set the {@link CelesteNode} instance that this daemon is to use to perform the Celeste functions.
//     *
//     *
//     * @param celesteNode
//     */
//    public void setCelesteNode(CelesteNode celesteNode) {
//        this.celesteNode = celesteNode;
//    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }


    public ResponseMessage probe(ProbeOperation operation)
    throws IOException, ClassNotFoundException {
        return new ResponseMessage(operation);
    }

    //
    // Fill in common metadata that's to be returned in response to any
    // Celeste interface method invocation.
    //
    public OrderedProperties fillMetadata(AnchorObject.Object aObject, VersionObject.Object vObject, AObjectVersionMapAPI.Lock lock) {
        OrderedProperties metadata = new AbstractBeehiveObject.Metadata();
        if (aObject != null) {
            metadata
            .setProperty(CelesteAPI.AOBJECTID_NAME, aObject.getObjectId())
            .setProperty(CelesteAPI.DEFAULTBOBJECTSIZE_NAME, aObject.getBObjectSize())
            .setProperty(CelesteAPI.DEFAULTREPLICATIONPARAMETERS_NAME, aObject.getReplicationParameters())
            .setProperty(CelesteAPI.DELETETOKENID_NAME, aObject.getDeleteTokenId())
            .setProperty(CelesteAPI.WRITE_SIGNATURE_INCLUDES_DATA, aObject.getSignWrites());
        }
        if (vObject != null) {
            metadata
            .setProperty(CelesteAPI.FILE_SIZE_NAME, vObject.getFileSize())
            .setProperty(CelesteAPI.VOBJECTID_NAME, vObject.getObjectId())
            .setProperty(CelesteAPI.VOBJECT_GROUP_NAME, vObject.getGroup())
            .setProperty(CelesteAPI.VOBJECT_OWNER_NAME, vObject.getOwner())
            .setProperty(CelesteAPI.VERSION_NAME, vObject.getVersion())
            ;
            CelesteACL acl = vObject.getACL();
            if (acl != null) {
                metadata.setProperty(CelesteAPI.VOBJECT_ACL_NAME, acl.toEncodedString());
            }

            Credential.Signature writerSignature = vObject.getSignature();
            if (writerSignature != null)
                metadata.setProperty(CelesteAPI.WRITERS_SIGNATURE_NAME, writerSignature);

            ClientMetaData clientData = vObject.getClientMetaData();
            if (clientData != null)
                metadata.setProperty(CelesteAPI.CLIENTMETADATA_NAME, clientData.getEncodedContext());
        }
        if (lock != null) {
            metadata.setProperty(CelesteAPI.LOCKTYPE_NAME, lock.getType());
            metadata.setProperty(CelesteAPI.LOCKID_NAME, lock.getLockerObjectId());
            metadata.setProperty(CelesteAPI.LOCKCOUNT_NAME, lock.getLockCount());
            if (lock.getToken() != null)
                metadata.setProperty(CelesteAPI.LOCKTOKEN_NAME, lock.getToken());
        }

        return metadata;
    }
    


    /**
     * Check access control throwing an {@link CelesteException.AccessControlException}
     * containing current file meta-data if the check doesn't succeed.
     */
    public void checkACL(CelesteOperation op, AnchorObject.Object aObject, VersionObject.Object vObject)
    throws CelesteException.AccessControlException {
        CelesteACL acl = vObject.getACL();
        if (acl != null) {
            CelesteACL.CelesteOps privilege = op.getRequiredPrivilege();
            CelesteACL.CelesteFileAttributeAccessor accessor = new CelesteACL.CelesteFileAttributeAccessor(vObject);
            try {
                acl.check(privilege, accessor, op.getClientId());
            } catch (ACL.ACLException e) {
                throw new CelesteException.AccessControlException(this.fillMetadata(aObject, vObject, null), e);
            }
        }
    }

    /**
     * Return {@code true} if the given {@link CelesteOperation} is permitted by the CelesteACL in the given {@link VersionObject.Object}
     * @param op
     * @param aObject
     * @param vObject
     * @return
     */
    private boolean inAccessControList(CelesteOperation operation, VersionObject.Object vObject) {
        CelesteACL acl = vObject.getACL();
        if (acl != null) {
            CelesteACL.CelesteFileAttributeAccessor accessor = new CelesteACL.CelesteFileAttributeAccessor(vObject);
            try {
                acl.check(operation.getRequiredPrivilege(), accessor, operation.getClientId());
            } catch (ACL.ACLException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check to see if the current version of the file is the expected version
     * of the file as specified in the {@link CelesteOperation#getVObjectId()}
     * <p>
     * Note that this test is advisory only in an attempt to fail before having to process the full file update.
     * It is possible that subsequent to this test but before this operation is performed the file may be updated and this operation fail.
     * </p>
     * @param operation the {@link CelesteOperation} being performed
     * @param aObject the file's {@link AnchorObject.Object} instance.
     * @param vObject the file's {@link VersionObject.Object} instance.
     * @throws CelesteException.OutOfDateException if the file's {@code VersionObject.Object} instance is not the most recent version of the file.
     */
    public void checkUpToDate(CelesteOperation operation, AnchorObject.Object aObject, VersionObject.Object vObject) throws CelesteException.OutOfDateException {
        if (operation.getVObjectId() != null && vObject.getObjectId().equals(operation.getVObjectId()) == false) {
            String message = String.format("Current VersionObject is %s, expected %s", vObject.getObjectId(), operation.getVObjectId());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine(message);
            }
            throw new CelesteException.OutOfDateException(this.fillMetadata(aObject, vObject, null), message);
        }
    }

    /**
     * Check if there is a lock on this file.
     * <p>
     * If no lock is set, return {@code false}.
     * <p>
     * Otherwise a lock is already set.  If that lock is not owned by the client specified in the {@link CelesteOperation}
     * (See {@link CelesteOperation#getClass()}), then throw {@link CelesteException.FileLocked} containing current file
     * meta-data, including the lock information.
     * </p>
     * <p>
     * Otherwise, the lock is a nested lock, return {@code true}.
     * </p>
     * @param operation the {@link CelesteOperation} being performed
     * @param currentValue the current file version value.
     * @param aObject the file's {@link AnchorObject.Object} instance.
     * @param vObject the file's {@link VersionObject.Object} instance.
     * @throws CelesteException.FileLocked if the file is locked.
     */
    public boolean checkFileLock(CelesteOperation operation, AObjectVersionMapAPI.Value currentValue, AnchorObject.Object aObject, VersionObject.Object vObject)
    throws CelesteException.FileLocked {
        AObjectVersionMapAPI.Lock lock = currentValue.getLock();
        if (lock != null) {
            if (lock.getLockerObjectId() != null) {
                if (lock.getLockerObjectId().equals(operation.getClientId()) == false) {
                    OrderedProperties properties = this.fillMetadata(aObject, vObject, lock);
                    throw new CelesteException.FileLocked(properties, "File is locked.");
                }
                return true;
            }
        }
        return false;
    }



    /**
     * Create a Celeste file.
     *
     * Note that the object-id of the writing node is not currently being tracked
     * -- this might be a good thing in a future version, to make celeste
     * nodes accountable for faulty commits.
     *
     * @throws AccessControlException
     * @throws CelesteException.IllegalParameterException if the file identifier is the reserved value of all zeros.
     * @throws CelesteException.CredentialException
     * @throws CelesteException.NotFoundException
     * @throws ClassNotFoundException
     * @throws ClassCastException
     *
     */
    public OrderedProperties createFile(CreateFileOperation operation, Credential.Signature signature)
    throws IOException, CelesteException.AccessControlException, CelesteException.IllegalParameterException,
           CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
           CelesteException.CredentialException, CelesteException.RuntimeException,
           CelesteException.NotFoundException, CelesteException.NoSpaceException, ClassCastException, ClassNotFoundException {
        // Prevent client applications from passing in zero fileNameId fields.
        // Doing such a thing would intrude upon the credential name space.
        // Use newCredential to create credentials.
        if (operation.getFileIdentifier().getFileId().equals(TitanGuidImpl.ZERO)) {
            throw new CelesteException.IllegalParameterException("File Identifier cannot be zero");
        }

        this.checkOperationSignature(signature, operation, operation.getClientMetaData().getId());

        return this._createFile(signature, operation);
    }


    private  static String AObjectVersionMapParamsDefault = "1,1";

    /*
     *
     */
    public OrderedProperties _createFile(Credential.Signature signature, CreateFileOperation operation)
    throws IOException,
    CelesteException.AlreadyExistsException, CelesteException.DeletedException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.IllegalParameterException, ClassCastException, ClassNotFoundException {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());

        try {
            VersionObjectHandler versionObjectHandler =  this.node.getService(VersionObjectHandler.class);
            AnchorObjectHandler anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);
            String lineariserName = operation.getReplicationParams().getAsString("Linearizer", AObjectVersionService.class.getName());
            AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(lineariserName);


            // Try to induce an early failure to see if the AObject already exists.
            // If it does, but it is deleted, retrieve() will throw DeletedObjectException.
            try {
                anchorObjectHandler.retrieve(operation.getFileIdentifier());
                throw new CelesteException.AlreadyExistsException("File exists %s", operation.getFileIdentifier());
            } catch (BeehiveObjectStore.NotFoundException e) {
                // The object is NOT supposed to exist at this point.
                //this.log.info("AObject %s does not already exist (good).", anchorObjectHandler.makeObjectId(vObject.getCreatorId(), vObject.getFileId()));
            }

            AnchorObject.Object aObject = anchorObjectHandler.create(operation.getFileIdentifier(),
                    operation.getReplicationParams(),
                    operation.getDeleteTokenId(),
                    operation.getTimeToLive(),
                    operation.getBObjectSize(),
                    operation.getSignWrites());

            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("AObjectVersionMap parameters: %s", operation.getReplicationParams().getAsString("AObjectVersionMap.Params", AObjectVersionMapParamsDefault));
            }
            AObjectVersionMapAPI.Parameters params = lineariser.createAObjectVersionMapParams(operation.getReplicationParams().getAsString("AObjectVersionMap.Params", AObjectVersionMapParamsDefault));
            aObject.setAObjectVersionMapParams(params);

            timingProfiler.stamp("init");
            lineariser.createValue(aObject.getObjectId(), operation.getDeleteTokenId(), aObject.getAObjectVersionMapParams(), aObject.getTimeToLive());

            timingProfiler.stamp("createValue");
            aObject = anchorObjectHandler.storeObject(aObject);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("Stored %s", aObject);
            }
            timingProfiler.stamp("AObject");

            AnchorObject.Object.Version nextVersion = anchorObjectHandler.makeVersion(new TitanGuidImpl(), 0);

            // Construct a provisional VObject
            VersionObject.Object vObject = versionObjectHandler.create(
                    aObject.getObjectId(),
                    operation.getReplicationParams(),
                    operation,
                    operation.getClientMetaData(),
                    signature);
            vObject.setVersion(nextVersion);

            versionObjectHandler.storeObject(vObject);
            VersionObject.Object.Reference newVObjectReference = vObject.makeReference();
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("Stored %s", vObject);
            }
            timingProfiler.stamp("VObject");

            lineariser.setValue(aObject.getObjectId(),
                    null,
                    lineariser.newValue(newVObjectReference),
                    aObject.getAObjectVersionMapParams());
            timingProfiler.stamp("storeValue");
            OrderedProperties metaData = this.fillMetadata(aObject, vObject, null);

            return metaData;
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ExistenceException e) {
            throw new CelesteException.AlreadyExistsException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.NoSpaceException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.IllegalParameterException(e);
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } catch (UnacceptableObjectException e) {
            throw new CelesteException.AlreadyExistsException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.AlreadyExistsException(e); // XXX Needs a better exception.
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }
    
    public Boolean deleteFile(DeleteFileOperation operation, Credential.Signature signature)
    throws IOException,
        CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            //
            // XXX: Additional checking before performing the operation?
            //      Consistency with other operations would dictate verification
            //      of proper predication and that the predicated version's ACL
            //      permits the operation?
            //

            AnchorObject anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);

            DOLRStatus status = anchorObjectHandler.delete(operation.getFileIdentifier(), operation.getDeleteToken(), operation.getTimeToLive());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", status.toString());
            }
            return Boolean.TRUE;
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.NoSpaceException(e);
        } catch (ClassCastException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (RemoteException e) {
            throw new CelesteException.RuntimeException(e.getCause());
        } catch (DeleteTokenException e) {
            throw new CelesteException.IllegalParameterException(e);
        } catch (ClassNotFoundException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }
    



    public ResponseMessage inspectLock(InspectLockOperation operation) throws IOException, ClassNotFoundException,
    CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
//            this.checkOperationSignature(signature, operation);

            AnchorObject anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = this.node.getService(VersionObjectHandler.class);

            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());
            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());

            VersionObject.Object.Reference vObjectReference = currentValue.getReference();

            final VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectReference.getObjectId());

            this.checkUpToDate(operation, aObject, vObject);

//            this.checkACL(operation, aObject, vObject);

            AObjectVersionMapAPI.Lock currentLock = currentValue.getLock();

            ResponseMessage reply = new ResponseMessage(this.fillMetadata(aObject, vObject, currentLock), currentLock);

            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("OK");
            }
            return reply;

        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (CelesteException.OutOfDateException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timing.print(System.out);
        }
    }

    public ResponseMessage inspectFile(InspectFileOperation operation)
    throws IOException, ClassNotFoundException,
    CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());
        try {
            AnchorObject anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);
            VersionObject versionObjectHandler = this.node.getService(VersionObjectHandler.class);

            timingProfiler.stamp("init");
            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", aObject);
            }
            timingProfiler.stamp("retrieveAObject");

            AObjectVersionMapAPI.Lock lock = null;

            // Resolve what version object we need to be working with here.
            // If the version object objectId is not specified, then we use the latest one.
            TitanGuid vObjectId = operation.getVObjectId();
            if (vObjectId == null || vObjectId.equals(TitanGuidImpl.ZERO)) {
                AObjectVersionMapAPI lineariser = this.node.getService(AObjectVersionService.class);
                AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());
                lock = currentValue.getLock();

                vObjectId = currentValue.getReference().getObjectId();
            }
            timingProfiler.stamp("getValue");

            VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectId);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", vObject);
            }
            timingProfiler.stamp("retrieveVObject");

            //
            // Make sure that this version's ACL permits the invoker to
            // inspect the file.
            //
            // XXX: It turns out that our permission checking infrastructure
            //      isn't robust enough to sustain this check yet.  (Clients
            //      such as FileImpl use inspectFile() as part of implementing
            //      other operations.  Permission to invoke those other
            //      operations shouldn't depend on permission to invoke
            //      inspectFile().)  Disable the check until this problem is
            //      resolved.
            //
            //this.checkACL(operation, aObject, vObject);

            OrderedProperties metaData = this.fillMetadata(aObject, vObject, lock);
            ResponseMessage reply = new ResponseMessage(metaData, vObject.getClientMetaData());

            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("OK");
            }
            return reply;
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException("Cannot determine current version for File %s", operation.getFileIdentifier());
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }

    public ResponseMessage lockFile(LockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            AnchorObject anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = this.node.getService(VersionObjectHandler.class);

            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());
            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());

            VersionObject.Object.Reference vObjectReference = currentValue.getReference();

            final VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectReference.getObjectId());

            this.checkUpToDate(operation, aObject, vObject);

            this.checkACL(operation, aObject, vObject);

            AObjectVersionMapAPI.Lock currentLock = currentValue.getLock();

            AObjectVersionMapAPI.Lock newLock;
            if (this.checkFileLock(operation, currentValue, aObject, vObject)) {
                if (!currentLock.getType().equals(operation.getType())) {
                    throw new CelesteException.FileLocked(this.fillMetadata(aObject, vObject, currentLock), "Mismatched lock types.");
                }

                // The file is already locked by this clientId.
                // A nested (repeated) lock *must* have the same token.
                if (currentLock.getToken() != null) {
                    if (!currentLock.getToken().equals(operation.getToken())) {
                        throw new CelesteException.FileLocked(this.fillMetadata(aObject, vObject, currentLock), "Mismatched lock tokens.");
                    }
                }

                // increment the reference counter and set it.

                newLock = new AObjectVersionService.Lock(operation.getType(),
                        currentValue.getLock().getLockerObjectId(),
                        currentValue.getLock().getLockCount()+1,
                        currentValue.getLock().getToken(),
                        currentValue.getLock().getClientAnnotation());
            } else {
                newLock = new AObjectVersionService.Lock(operation.getType(), operation.getClientId(), 1, operation.getToken(), operation.getClientAnnotation());
            }

            lineariser.setValue(aObject.getObjectId(),
                    currentValue,
                    lineariser.newValue(vObjectReference, newLock),
                    aObject.getAObjectVersionMapParams());

            ResponseMessage result = new ResponseMessage(this.fillMetadata(aObject, vObject, currentLock), newLock);
            return result;
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } finally {
            timing.print(System.out);
        }
    }
    public OrderedProperties writeFile(WriteFileOperation operation, Credential.Signature signature, ByteBuffer buffer)
    throws IOException,
        CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
        CelesteException.OutOfDateException, CelesteException.FileLocked {

        //
        // Because Credentials always have a fileId component equal to ZERO,
        // ensure that they cannot be written directly by other applications.
        //
        if (operation.getFileIdentifier().getFileId().equals(TitanGuidImpl.ZERO)) {
            throw new CelesteException.IllegalParameterException("File Identifier cannot be zero");
        }
        Credential clientCredential = this.getProfile(operation.getClientId());

        return this._writeFile(clientCredential, operation, signature, buffer);
    }

    private OrderedProperties _writeFile(Credential requestorCredential, WriteFileOperation operation, Credential.Signature signature, ByteBuffer buffer)
    throws IOException,
        CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
        CelesteException.OutOfDateException, CelesteException.FileLocked {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());
        try {
            if (operation.getLength() != buffer.remaining()) {
                throw new CelesteException.VerificationException("Data lengths do not match.  %d vs %d", operation.getLength(), buffer.remaining());
            }

            AnchorObject anchorObjectHandler = this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = this.node.getService(VersionObjectHandler.class);
            BlockObject blockObjectHandler = this.node.getService(BlockObjectHandler.class);

            // Fetch the existing AObject for this file. Use the contents of
            // the existing AObject to set some initial values in the VersionObject
            // as well as to ensure that when the "commit" of this file update
            // happens, we haven't been out-dated by another simultaneous update.

            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", aObject);
            }

            if (aObject.getSignWrites()) {
                if (this.log.isLoggable(Level.FINEST)) {
                    this.log.finest("Performing data signature check");
                }
                TitanGuid dataId = new TitanGuidImpl(buffer);
                this.checkOperationSignature(signature, operation, dataId);
            } else {
                if (this.log.isLoggable(Level.FINEST)) {
                    this.log.finest("Skipping data signature check");
                }
            }

            //
            // Ensure that currentVObjectId is the latest -- otherwise the
            // writer signature will be invalid.  A Celeste node letting
            // through invalid writes could be considered evil, and excluded
            // from the system.  This is necessary so that there is a proof
            // of liveliness, ie old content, can not be re-written with its old
            // signature at a future time.
            //
            // Stated differently, the fact that the writer's signed metadata
            // includes the object-id of the latest version's VObject
            // prevents rogue implementations of CelesteNode from replaying the
            // write at a later time.
            //
            // To help recovery for a writer who has lost a race with another
            // writer, the failure message includes details about the current
            // version.
            //
            // XXX: All that's really needed in the lost race case is the latest
            // VObject object-id.  This may be true of the successful write
            // case as well. If so, we ought to send only the VObject
            // object-id, since it's much less bulky.

            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());

            VersionObject.Object.Reference vObjectReference = currentValue.getReference();

            VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectReference.getObjectId());

            this.checkUpToDate(operation, aObject, vObject);
            //
            // Although we passed the test above, it is still possible for
            // this write to fail due to a VersionObject-id mismatch.  If
            // there is another writer updating the AObject and that writer is
            // faster than we are, the atomic-update of the AObject will also
            // fail due to mismatched VObject-id.  This will be discovered and
            // properly handled by the lineariser.
            //

            this.checkACL(operation, aObject, vObject);

            this.checkFileLock(operation, currentValue, aObject, vObject);

            //
            // Set up the metadata properties for the yet-to-be-built BlockObjects.
            //
            TitanObject.Metadata bObjectMetaData = new AbstractBeehiveObject.Metadata();
            TitanGuid deleteTokenHash = aObject.getDeleteTokenId();

            final long start = operation.getFileOffset();
            final int bObjectSize = aObject.getBObjectSize();

            long timeToLive = aObject.getTimeToLive();

            //
            // Get an ExtentBuffer holding the data to be written, establishing
            // the invariant that source.position() == 0.  The main processing
            // loop below maintains this invariant at the point where it's ready
            // for the next pass, doing so by reassigning source (to strip off the
            // portion most recently processed).
            //
            ExtentBuffer source = new ExtentBuffer(operation.getFileOffset(), buffer.slice());
            final int dataLength = source.remaining();
            //this.log.info("overall source bounds: %s, dataLength %d",
            //    source.asString(false), dataLength);

            //
            // Break the write up into a sequence of BObject updates/creates.
            // At each iteration, get the starting offset of the as yet
            // unprocessed suffix of the overall data sequence and calculate
            // the BObject offset that should hold that offset.  Then look up
            // that BObject and prepare to either update or create it.  Merge
            // the part of the data that overlaps it into the BObject, write
            // the BObject, and repeat until done.
            //
            // This code seems really complicated compared to what it's
            // actually doing.
            //
            while (source.remaining() > 0) {
                assert source.position() == 0 : "source should have position 0 but is at " + source.position();
                long writeOffset = source.getStartOffset();
                //
                // Get the starting offset of the BObject that will hold the
                // write from this loop iteration and use that offset to look
                // up the BObject (which may not exist).
                //
                long bObjectOffset = (writeOffset / bObjectSize) * bObjectSize;
                BlockObject.Object.Reference bObjectReference = vObject.getBObjectReference(bObjectOffset);
                //
                // Find the portion of the source buffer that falls within
                // the confines of the BObject we'll write.
                //
                BufferableExtent maximalBounds = new BufferableExtentImpl(bObjectOffset, bObjectSize);
                ExtentBuffer sourcePortion = source.intersect(maximalBounds);
                //
                // Now set the new BObject's bounds to cover both existing
                // data and the new data to be written.  sourcePortion's
                // length is relative to its starting offset, so it has to be
                // converted to be relative to the BObject's starting offset
                // instead.
                //
                int existingBobjectLength = (bObjectReference == null) ? 0 : bObjectReference.getBounds().getLength();
                int convertedSourceLength = sourcePortion.getLength() + ((int) (writeOffset - bObjectOffset));
                BufferableExtent bounds = new BufferableExtentImpl(bObjectOffset, Math.max(existingBobjectLength, convertedSourceLength));
                //this.log.info(
                //    "BObject bounds: %s, source bounds: %s, intersection bounds %s",
                //        bounds, source, sourcePortion);

                //
                // Get existing data for this portion of the file.  That is,
                // fetch the BObject and get its data (in the form of an
                // ExtentBufferMap) if it exists, or create an empty map if it
                // doesn't.
                //
                ExtentBufferMap data = null;
                if (bObjectReference != null) {
                    BlockObject.Object bObject = blockObjectHandler.
                    retrieve(bObjectReference.getObjectId());
                    assert bObject != null;
                    data = bObject.getDataAsExtentBufferMap();
                    //BufferableExtent boBounds = bObject.getBounds();
                    //assert boBounds.contains(data) : String.format(
                    //    "boBounds: %s, data: %s", boBounds, data.asString());
                    //assert bounds.intersects(boBounds) : String.format(
                    //    "bounds: %s, boBounds: %s", bounds, boBounds);
                } else {
                    //this.log.info("no pre-existent extent");
                    data = new ExtentBufferMap();
                }

                //
                // Drop the data from the write into place.
                //
                data.replaceExtents(sourcePortion);

                //
                // Create and record the new BlockObject.
                //
                BlockObject.Object newBObject = blockObjectHandler.create(bounds, data, bObjectMetaData, deleteTokenHash, timeToLive, aObject.getReplicationParameters());
                newBObject = blockObjectHandler.storeObject(newBObject);
                assert newBObject.getBounds().contains(data) : String.format("newBboBounds: %s, data: %s", newBObject.getBounds(), data.asString(false));
                //
                // Since the new BObject contains data from this write, its
                // bounds should encompass those of the data that this write
                // contributed.
                //
                assert newBObject.getBounds().contains(bounds);
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("BObject{%d+%d} %s", bObjectOffset, newBObject.getBounds().getLength(), newBObject.getObjectId().toString());
                }
                vObject.addBObject(newBObject.makeReference(bObjectOffset, newBObject.getObjectId()));

                //
                // Advance source for the next iteration.
                //
                source = source.position(sourcePortion.capacity()).slice();
            }

            vObject.setFileSize(Math.max(vObject.getFileSize(), (start + dataLength)));
            vObject.setSignature(signature);
            vObject.setCelesteOperation(operation);
            vObject.setClientMetaData(operation.getClientMetaData());
            vObject.setTimeToLive(timeToLive);

            AnchorObject.Object.Version aObjectVersion = vObjectReference.getVersion();
            AnchorObject.Object.Version nextVersion = anchorObjectHandler.makeVersion(aObjectVersion.getGeneration(), aObjectVersion.getSerialNumber() + 1);

            vObject.setDeleteTokenId(aObject.getDeleteTokenId());
            vObject.setPreviousVObjectReference(vObjectReference);
            vObject.setVersion(nextVersion);

            vObject = versionObjectHandler.storeObject(vObject);
            if (this.log.isLoggable(Level.FINEST)) {
                this.log.finest("Stored %s", vObject);
            }
            VersionObject.Object.Reference newVObjectReference = vObject.makeReference();

            lineariser.setValue(aObject.getObjectId(),
                    currentValue,
                    lineariser.newValue(newVObjectReference, currentValue.getLock()), aObject.getAObjectVersionMapParams());

            OrderedProperties result = this.fillMetadata(aObject, vObject, currentValue.getLock());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("OK new VObject.Reference %s", newVObjectReference);
            }
            return result;
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.NoSpaceException(e);
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (sunlabs.titan.node.object.MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassCastException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }

    public ResponseMessage readFile(ReadFileOperation operation, Credential.Signature signature)
    throws IOException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.DeletedException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException {

        TimeProfiler timeProfiler = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            //
            // XXX: Ought to use a BufferableExtent in place of these two
            //      fields.
            //
            long offset = operation.getOffset();
            int length = operation.getLength();

            AnchorObject.Object aObject = null;

            AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService(AnchorObjectHandler.class);
            VersionObject versionObjectHandler = (VersionObject) this.node.getService(VersionObjectHandler.class);
            final BlockObject blockObjectHandler = (BlockObject) this.node.getService(BlockObjectHandler.class);
            timeProfiler.stamp("init");

            //
            // Find the file version from which to do the read.
            //
            TitanGuid vObjectId = operation.getVObjectId();
            AObjectVersionMapAPI.Value currentVersion = null;
            try {
                aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());

                // If the object-id of the VersionObject is not supplied,
                // we must fetch the current VObject object-id.
                if (vObjectId == null || vObjectId.equals(TitanGuidImpl.ZERO)) {
                    AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(AObjectVersionService.class);
                    currentVersion = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());
                    VersionObject.Object.Reference vObjectReference = currentVersion.getReference();
                    vObjectId = vObjectReference.getObjectId();
                }
            } catch (BeehiveObjectStore.DeletedObjectException e) {
                if (this.log.isLoggable(Level.FINEST)) {
                    this.log.finest("AObject %s deleted", operation.getFileIdentifier());
                }
                throw new CelesteException.DeletedException(e);
            } catch (BeehiveObjectStore.NotFoundException e) {
                throw new CelesteException.NotFoundException(e);
            } catch (MutableObject.InsufficientResourcesException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.NotFoundException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.ProtocolException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (ClassCastException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new CelesteException.RuntimeException(e);
            }
            timeProfiler.stamp("VObjectId");

            if (this.log.isLoggable(Level.FINER)) {
                this.log.finer("AnchorObject %s VersionObject %s", aObject.getObjectId(), vObjectId);
            }

            final VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectId);

            timeProfiler.stamp("VObjectFetch");

            this.checkACL(operation, aObject, vObject);

            long fileSize = vObject.getFileSize();

            //
            // XXX: I'd like to get rid of this.  I think it is unnecessary.
            //
            //      It can't go away completely.  At a minimum, an offset
            //      beyond EOF should be transformed into the offset of EOF.
            //
            if (offset > fileSize) {
                throw new CelesteException.IllegalParameterException("Starting byte index exceeds file size.");
            }

            if (length == -1 || length > fileSize) {
                length = (int) (fileSize - offset);
            }
            BufferableExtent desiredSpan = new BufferableExtentImpl(offset, length);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("desiredSpan: " + desiredSpan);
            }

            //
            // Beware of overflow here.  (Hence the check immediately
            // following.)
            //
            // XXX: This is one reason why ReadFileOperation should use a
            //      BufferableExtent to specify the span to read.  The Extent
            //      classes have overflow checking built into them.
            //
            long fileStop = offset + length;
            int readLength = (int) (fileStop - offset);

            if (readLength < 0) {
                String msg = String.format("Read size must be 0 <= x <= %d; got %d", Integer.MAX_VALUE, readLength);
                this.log.info(msg);
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine(msg);
                }
                throw new CelesteException.IllegalParameterException(msg);
            }

            SortedMap<Long, BlockObject.Object.Reference> map = vObject.getExtent(offset, fileStop);
            if (map == null) {
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("Version Object has no mapping for range[%d-%d]", offset, fileStop);
                }
                //
                // Must return all zeros for this whole extent; using an empty
                // map causes that to happen.
                //
                map = new TreeMap<Long, BlockObject.Object.Reference>();
            }
            //
            // Accumulate the (possibly sparse) data satisfying the read into
            // its own map.
            //

            timeProfiler.stamp("PreRead");
            ExtentBufferMap newCollection = new ExtentBufferMap();

            ExecutorService executor = Executors.newFixedThreadPool(3);
            MapFunction<BlockObject.Object.Reference,BlockObject.Object> reader = blockObjectHandler.newReader(executor, blockObjectHandler, newCollection, desiredSpan);
            reader.setStopOnException(true);
            try {
                List<MapFunction.Result<BlockObject.Object>> list = reader.map(map.values());
            } catch (ExecutionException e) {
                throw new CelesteException.RuntimeException(e);
            } finally {
                // Shutdown the ExecutorService otherwise it won't get garbage collected.
                executor.shutdown();
            }
            timeProfiler.stamp("PostRead");

            ExtentBufferStreamer e = new ExtentBufferStreamer(desiredSpan, newCollection);

            // XXX Fix this to include the lock held (if any).
            OrderedProperties metadata = this.fillMetadata(aObject, vObject, currentVersion != null ? currentVersion.getLock() : null);
            ResponseMessage reply = new ResponseMessage(metadata, e);

            return reply;
        } catch (BeehiveObjectStore.DeletedObjectException deleted) {
            deleted.printStackTrace();
            return new ResponseMessage(deleted);
        } catch (BeehiveObjectStore.NotFoundException e) {
            return new ResponseMessage(e);
        } catch (ClassCastException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timeProfiler.stamp("remainder");
            timeProfiler.printCSV(System.out);
            timeProfiler.print(System.out);
        }
    }
    

    public ResponseMessage runExtension(ExtensibleOperation operation, Credential.Signature signature, Serializable parameters)
    throws IOException,
           CelesteException.VerificationException, CelesteException.AccessControlException, CelesteException.CredentialException, CelesteException.NotFoundException,
           CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            URL[] jarFileURLs = operation.getJarFileURLs();
            if (jarFileURLs != null) {
                if (jarFileURLs.length > 0) {
                    JarClassLoader classLoader = new JarClassLoader(jarFileURLs);
                    try {
                        String mainClassName = classLoader.getMainClassName();

                        if (mainClassName == null) {
                            throw new CelesteException.IllegalParameterException("Jar file %s does not specifiy a 'Main-Class' manifest attribute.", jarFileURLs[0]);
                        }
                        Callable<Serializable> extension = classLoader.construct(mainClassName, (TitanNodeImpl) this.node, operation);
                        try {
                            return new ResponseMessage(extension.call());
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new CelesteException.RuntimeException(e);
                        } finally {
                            classLoader.disconnect();
                        }
                    } catch (ClassNotFoundException e) {
                        throw new CelesteException.RuntimeException(classLoader.getMainClassName() + ": not found.");
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(classLoader.getMainClassName() + ": no constructor: public static Serializable C(JarClassLoader, CelesteNode, ExtensibleOperation)");
                    } catch (InvocationTargetException e) {
                        e.getTargetException().printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                        throw new CelesteException.RuntimeException(e);
                    }
                }

                throw new CelesteException.RuntimeException("No Jar file URLs");
            } else {
                throw new CelesteException.RuntimeException("Missing Jar file URL");
            }
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }

    public OrderedProperties setFileLength(SetFileLengthOperation operation, Credential.Signature signature)
    throws IOException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.NoSpaceException,
           CelesteException.VerificationException, CelesteException.OutOfDateException, CelesteException.FileLocked {

        TimeProfiler timingProfiler = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            long stop = operation.getLength();

            AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService(AnchorObjectHandler.class);
            VersionObject versionObjectHandler = (VersionObject) this.node.getService(VersionObjectHandler.class);
            AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(AObjectVersionService.class);
            BlockObject blockObjectHandler = (BlockObject) this.node.getService(BlockObjectHandler.class);

            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());

            // Ensure that predicated VObjectId is the latest -- otherwise the writer signature will be invalid.
            // A Celeste node letting through invalid writes should be considered evil, and excluded from the system.
            // This is necessary so that there is a proof of liveliness: old content, can not be re-written with
            // its old signature at a future time.
            //
            // However, the AObjectVersionMap will enforce whether or not the VObjectId is the most current.
            // This is really just to produce the correct signature.
            //
            // Actually, it is a policy decision how much slack you want to cut the application. One could say that
            // e.g. the referenced version needs to be among the last 10 versions.
            //
            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());

            VersionObject.Object.Reference vObjectReference = currentValue.getReference();

            VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectReference.getObjectId());

            this.checkACL(operation, aObject, vObject);

            this.checkUpToDate(operation, aObject, vObject);

            this.checkFileLock(operation, currentValue, aObject, vObject);

            if (stop <= vObject.getFileSize()) {
                TitanGuid deleteTokenId = aObject.getDeleteTokenId();

                //
                // The operation here is:  given the end of the file,
                // determine which BObject will be the last BObject in the new
                // version of the file.  Delete all BObjects after the last
                // BObject in the new version of the file.  If the last
                // BObject has some residual data from the previous version,
                // allocate a new BObject and copy the residual data in to it.
                // Store the new BObject and add it into the VObject.
                //
                // N.B.  In the ExtentBufferMap world, "copy residual"
                // translates to "get the original BObject's map and intersect
                // it with the new bounds".
                //
                BlockObject.Object.Reference lastBObjectReference = vObject.getBObjectReference(stop);

                long deleteAllBObjectsAfterOffset = stop;
                if (lastBObjectReference != null) {
                    deleteAllBObjectsAfterOffset = lastBObjectReference.getFileOffset() + lastBObjectReference.getLength();
                    vObject.truncate(Long.valueOf(deleteAllBObjectsAfterOffset));

                    int numberOfBytesToKeepFromLastBObject = (int) (stop - lastBObjectReference.getFileOffset());
                    //                  System.out.println("              .setFileLength: The last BObject containing stop position " + stop + " has offset=" + lastBObjectReference.getFileOffset() + " length=" + lastBObjectReference.getLength());
                    //                  System.out.println("              .setFileLength: deleteAllBObjectsAfterOffset: " + deleteAllBObjectsAfterOffset + " numberOfBytesToKeepFromLastBObject " + numberOfBytesToKeepFromLastBObject);

                    if (numberOfBytesToKeepFromLastBObject > 0) {
                        BlockObject.Object lastBObject = blockObjectHandler.retrieve(lastBObjectReference.getObjectId());

                        TitanObject.Metadata bObjectMetaData = lastBObject.getMetadata();

                        BufferableExtent newBounds = new BufferableExtentImpl(lastBObjectReference.getFileOffset(), numberOfBytesToKeepFromLastBObject);
                        ExtentBufferMap data = lastBObject.getDataAsExtentBufferMap();
                        ExtentBufferMap newData = data.intersect(newBounds);

                        BlockObject.Object newBObject =
                            blockObjectHandler.create(newBounds, newData, bObjectMetaData, deleteTokenId, aObject.getTimeToLive(), aObject.getReplicationParameters());
                        newBObject = blockObjectHandler.storeObject(newBObject);

                        vObject.addBObject(newBObject.makeReference(lastBObjectReference.getFileOffset(), newBObject.getObjectId()));
                    } else if (numberOfBytesToKeepFromLastBObject == 0) {
                        vObject.truncate(lastBObjectReference.getFileOffset());
                    }
                }
            }

            vObject.setFileSize(stop);
            vObject.setSignature(signature);
            vObject.setCelesteOperation(operation);
            vObject.setClientMetaData(operation.getClientMetaData());
            vObject.setTimeToLive(aObject.getTimeToLive());

            AnchorObject.Object.Version aObjectVersion = vObjectReference.getVersion();
            AnchorObject.Object.Version nextVersion =
                anchorObjectHandler.makeVersion(aObjectVersion.getGeneration(), aObjectVersion.getSerialNumber() + 1);

            vObject.setDeleteTokenId(aObject.getDeleteTokenId());
            vObject.setVersion(nextVersion);

            versionObjectHandler.storeObject(vObject);
            VersionObject.Object.Reference newVObjectReference = vObject.makeReference();

            lineariser.setValue(aObject.getObjectId(),
                    currentValue,
                    lineariser.newValue(newVObjectReference, currentValue.getLock()),
                    aObject.getAObjectVersionMapParams());

            // XXX Fix this to include the lock held (if any).
            OrderedProperties metaData = this.fillMetadata(aObject, vObject, null);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("OK");
            }
            return metaData;
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.NoSpaceException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.OutOfDateException(e);
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException("Cannot determine latest version of File %s",  operation.getFileIdentifier());
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassCastException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timingProfiler.stamp("remainder");
            timingProfiler.printCSV(System.out);
            timingProfiler.print(System.out);
        }
    }


    public TitanObject.Metadata newCredential(NewCredentialOperation operation, Credential.Signature signature, Credential credential)
    throws IOException,
            CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
            CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException {

        // XXX It must be an error to create a credential more than once because a second credential would have the same name but a different key pair.
        //
        
        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
            //
            // Check that the credential can sign things and that it properly
            // verifies its own creation.
            //
            if (credential.isLimited())
                throw new CelesteException.CredentialException("credential must support sign() operation");
            if (credential.verify(signature, operation.getId())) {
                //
                // Dig replication information out of the operation and use it
                // to set corresponding properties in credential, for
                // storeObject() use when storing and publishing it.
                //
                ReplicationParameters replicationParams = operation.getReplicationParameters();
                int copies = replicationParams.getAsInteger(CredentialObject.Object.REPLICATIONPARAM_STORE_NAME, 3);
                credential.setProperty(ObjectStore.METADATA_REPLICATION_STORE, copies);
                int lowWater = replicationParams.getAsInteger(CredentialObject.Object.REPLICATIONPARAM_LOWWATER_NAME, 3);
                credential.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER, lowWater);

                //
                // If we were to follow the pattern defined by AObjects, we
                // would call a create() operation that
                // CredentialOperationHandler defines.  But that would just
                // return a Credential and we already have one in our hands.
                //
                // All that's needed beyond that is to store credential.
                //
                CredentialObject handler = (CredentialObject) this.node.getService(CredentialObjectHandler.class);
                assert handler != null;

                handler.storeObject(credential);
                return credential.getMetadata();
            } else {
                throw new CelesteException.VerificationException("Signature failed.");
            }
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.CredentialException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.CredentialException(e);
        } catch (Credential.Exception e) {
            throw new CelesteException.CredentialException(e);
        } catch (UnacceptableObjectException e) {
            throw new CelesteException.AlreadyExistsException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.AlreadyExistsException(e);
        } finally {
            timing.print(System.out);
        }
    }

    public TitanObject.Metadata newNameSpace(NewNameSpaceOperation operation, Credential.Signature signature, Credential credential)
    throws IOException,
            CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
            CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
            //
            // Check that the credential can sign things and that it properly
            // verifies its own creation in the guise of a name space.
            //
            // XXX: Perhaps credentials used as name spaces shouldn't be
            //      required to be able to sign things.
            //
            // XXX What does this check actually mean?  In this use case,
            // the profile only needs to verify a signature, not generate one.
            // Yet this is test to ensure that the Credential can generate one.
            if (credential.isLimited())
                throw new CelesteException.CredentialException("credential must support sign() operation");
            if (credential.verify(signature, operation.getId())) {
                //
                // Dig replication information out of the operation and use it
                // to set corresponding properties in credential, for
                // storeObject() use when storing and publishing it.
                //
                ReplicationParameters replicationParams = operation.getReplicationParameters();
                int copies = replicationParams.getAsInteger(CredentialObject.Object.REPLICATIONPARAM_STORE_NAME, 3);
                credential.setProperty(ObjectStore.METADATA_REPLICATION_STORE, copies);
                int lowWater = replicationParams.getAsInteger(CredentialObject.Object.REPLICATIONPARAM_LOWWATER_NAME, 3);
                credential.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER, lowWater);

                //
                // If we were to follow the pattern defined by AObjects, we
                // would call a create() operation that
                // CredentialOperationHandler defines.  But that would just
                // return a Credential and we already have one in our hands.
                //
                // All that's needed beyond that is to store credential.
                //
                CredentialObject handler = (CredentialObject)this.node.getService(CredentialObjectHandler.class);
                assert handler != null;
                handler.storeObject(credential);
                return credential.getMetadata();
            } else {
                throw new CelesteException.VerificationException("Signature failed.");
            }
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.CredentialException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.CredentialException(e);
        } catch (Credential.Exception e) {
            throw new CelesteException.CredentialException(e);
        } catch (NumberFormatException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            throw new CelesteException.AlreadyExistsException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.AlreadyExistsException(e);
        } finally {
            timing.print(System.out);
        }
    }

    // XXX Return the value, not a ResponseMessage.  ResponseMessage occludes exceptions and makes the client unwrap it, instead of the client-side API.
    public Credential readCredential(ReadProfileOperation operation) throws IOException, CelesteException.NotFoundException, CelesteException.RuntimeException {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());

        final TitanGuid credentialId = operation.getCredentialId();

        try {
            final CredentialObject handler = (CredentialObject) this.node.getService(CredentialObjectHandler.class);
            assert handler != null;
            Credential credential = handler.retrieve(credentialId);
            if (credential == null)
                throw new CelesteException.NotFoundException();
            //
            // XXX: What about metadata in the reply?  For now I'm punting by
            //      returning an empty property map, but is this right?
            //
            return credential;
            //return new ResponseMessage(new OrderedProperties(), credential);
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (ClassCastException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timing.print(System.out);
        }
    }

    public OrderedProperties setOwnerAndGroup(SetOwnerAndGroupOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
            this.checkOperationSignature(signature, operation);

            AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = (VersionObject)  this.node.getService(VersionObjectHandler.class);

            VersionObject.Object vObject = null;
            AnchorObject.Object aObject = null;

            try {
                aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());
            } catch (BeehiveObjectStore.DeletedObjectException e) {
                throw new CelesteException.DeletedException(operation.getFileIdentifier().toString());
            } catch (BeehiveObjectStore.NotFoundException e) {
                throw new CelesteException.NotFoundException(e);
            }

            //
            // Make sure the operation is properly predicated.
            //
            VersionObject.Object.Reference vObjectReference;
            AObjectVersionMapAPI.Value currentValue;
            try {
                currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());

                vObjectReference = currentValue.getReference();
            } catch (MutableObject.InsufficientResourcesException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.ProtocolException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (sunlabs.titan.node.object.MutableObject.NotFoundException e) {
                throw new CelesteException.NotFoundException("Cannot determine current version for %s", operation.getFileIdentifier());
            }

            try {
                vObject = versionObjectHandler.retrieve(operation.getVObjectId());
            } catch (BeehiveObjectStore.DeletedObjectException e) {
                throw new CelesteException.DeletedException(e);
            } catch (BeehiveObjectStore.NotFoundException e) {
                throw new CelesteException.NotFoundException("Cannot locate Version object %s File %s", operation.getVObjectId(), operation.getFileIdentifier());
            }

            this.checkACL(operation, aObject, vObject);

            this.checkUpToDate(operation, aObject, vObject);

            this.checkFileLock(operation, currentValue, aObject, vObject);

            //
            // Alter the predicated vObject into the one that should result from
            // the operation.
            //
            vObject.setOwner(operation.getOwnerId());
            vObject.setGroup(operation.getGroupId());
            vObject.setSignature(signature);
            vObject.setCelesteOperation(operation);
            vObject.setClientMetaData(operation.getClientMetaData());

            try {
                AnchorObject.Object.Version aObjectVersion = vObjectReference.getVersion();
                AnchorObject.Object.Version nextVersion =
                    anchorObjectHandler.makeVersion(aObjectVersion.getGeneration(), aObjectVersion.getSerialNumber() + 1);

                vObject.setDeleteTokenId(aObject.getDeleteTokenId());
                vObject.setVersion(nextVersion);
                vObject.setTimeToLive(aObject.getTimeToLive());

                versionObjectHandler.storeObject(vObject);
                VersionObject.Object.Reference newVObjectReference = vObject.makeReference();

                lineariser.setValue(aObject.getObjectId(),
                        currentValue,
                        lineariser.newValue(newVObjectReference, currentValue.getLock()),
                        aObject.getAObjectVersionMapParams());

                OrderedProperties metaData = this.fillMetadata(aObject, vObject, currentValue.getLock());
                if (this.log.isLoggable(Level.FINE)) {
                    this.log.fine("OK");
                }
                return metaData;
            } catch (MutableObject.InsufficientResourcesException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (BeehiveObjectStore.NoSpaceException e) {
                throw new CelesteException.NoSpaceException(e);
            } catch (BeehiveObjectStore.DeleteTokenException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.PredicatedValueException e) {
                throw new CelesteException.OutOfDateException(e);
            } catch (MutableObject.ProtocolException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.ObjectHistory.ValidationException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (MutableObject.DeletedException e) {
                throw new CelesteException.DeletedException(e);
            } catch (BeehiveObjectStore.UnacceptableObjectException e) {
                throw new CelesteException.RuntimeException(e);
            } catch (BeehiveObjectPool.Exception e) {
                throw new CelesteException.RuntimeException(e);
            }
        } finally {
            timing.print(System.out);
        }
    }

    public ResponseMessage unlockFile(UnlockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
           CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException,
           CelesteException.OutOfDateException, CelesteException.FileNotLocked, CelesteException.FileLocked {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());

        AnchorObject.Object aObject = null;
        try {
            this.checkOperationSignature(signature, operation);

            AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = (VersionObject) this.node.getService(VersionObjectHandler.class);

            aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());

            TitanGuid vObjectId = operation.getVObjectId();

            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());
            VersionObject.Object.Reference vObjectReference = currentValue.getReference();
            vObjectId = vObjectReference.getObjectId();

            VersionObject.Object vObject = versionObjectHandler.retrieve(vObjectId);

            this.checkUpToDate(operation, aObject, vObject);

            AObjectVersionMapAPI.Lock currentLock = currentValue.getLock();

            // Don't expose the fact that a file is not locked (or locked) to a requestor that does not have permission to perform the unlock.

            // 1) If the requester presents a lock token and the current lock has that token, then unlock the file and return success.
            // 2) If the requester is not permitted by the access control list, throw AccessControlException
            // 3) If the requester presents a lock token and the current lock is not that token, then throw FileLocked.
            // 4) Unlock the file and return success.

            // If the requestor is presenting the correct lock token, it doesn't matter that they might not be in the access control list.
            // 1)
            if (operation.getToken() != null) {
                if (currentLock == null) {
                    // The requestor supplied a lock token, but there wasn't even a lock.
                    throw new CelesteException.AccessControlException("Mismatched lock tokens.");
                }
                if (operation.getToken().equals(currentLock.getToken())) {
                    AObjectVersionMapAPI.Lock newLock = null;

                    if (currentLock.getLockCount() == 1) {
                        // Remove the lock entirely.
                        lineariser.setValue(aObject.getObjectId(),
                                currentValue,
                                lineariser.newValue(currentValue.getReference()),
                                aObject.getAObjectVersionMapParams());
                    } else {
                       newLock = new AObjectVersionService.Lock(currentLock.getType(),
                                   currentLock.getLockerObjectId(),
                                   currentLock.getLockCount()-1,
                                   currentLock.getToken(),
                                   currentLock.getClientAnnotation());
                        lineariser.setValue(aObject.getObjectId(),
                                currentValue,
                                lineariser.newValue(currentValue.getReference(), newLock),
                                aObject.getAObjectVersionMapParams());
                    }

                    ResponseMessage result = new ResponseMessage(this.fillMetadata(aObject, vObject, newLock), newLock);
                    return result;
                }
            }

            // 2)
            this.checkACL(operation, aObject, vObject);

            if (currentLock == null) {
                throw new CelesteException.FileNotLocked();
            }

            // 3)
            if (operation.getToken() != null) {
                if (!operation.getToken().equals(currentLock.getToken())) {
                    throw new CelesteException.FileLocked(this.fillMetadata(aObject, vObject, currentLock), "Mismatched lock tokens.");
                }
            }

            // 4)
            AObjectVersionMapAPI.Lock newLock = null;

            if (currentLock.getLockCount() == 1) {
                // Remove the lock entirely.
                lineariser.setValue(aObject.getObjectId(),
                        currentValue,
                        lineariser.newValue(currentValue.getReference()),
                        aObject.getAObjectVersionMapParams());
            } else {
               newLock = new AObjectVersionService.Lock(currentLock.getType(),
                           currentLock.getLockerObjectId(),
                           currentLock.getLockCount()-1,
                           currentLock.getToken(),
                           currentLock.getClientAnnotation());
                lineariser.setValue(aObject.getObjectId(),
                        currentValue,
                        lineariser.newValue(currentValue.getReference(), newLock),
                        aObject.getAObjectVersionMapParams());
            }


            ResponseMessage result = new ResponseMessage(this.fillMetadata(aObject, vObject, newLock), newLock);
            return result;
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException("Cannot determine current version for Anchor %s", aObject.getObjectId());
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.OutOfDateException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } finally {
            timing.print(System.out);
        }
    }

    public OrderedProperties setACL(SetACLOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        TimeProfiler timing = new TimeProfiler(operation.getOperationName());
        try {
            //
            // Validate the provided signature.
            //
            this.checkOperationSignature(signature, operation);

            AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService(AnchorObjectHandler.class);
            AObjectVersionMapAPI lineariser = (AObjectVersionMapAPI) this.node.getService(AObjectVersionService.class);
            VersionObject versionObjectHandler = (VersionObject) this.node.getService(VersionObjectHandler.class);

            AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());

            //
            // Make sure the operation is correctly predicated.
            // Force an early failure if not.
            //
            AObjectVersionMapAPI.Value currentValue = lineariser.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams());
            VersionObject.Object.Reference vObjectReference = currentValue.getReference();

            VersionObject.Object vObject = versionObjectHandler.retrieve(operation.getVObjectId());

            this.checkUpToDate(operation, aObject, vObject);

            this.checkACL(operation, aObject, vObject);

            this.checkFileLock(operation, currentValue, aObject, vObject);

            //
            // Alter the predicated vObject into the one that should result from
            // the operation.
            //
            vObject.setACL(operation.getACL());
            vObject.setSignature(signature);
            vObject.setCelesteOperation(operation);
            vObject.setClientMetaData(operation.getClientMetaData());

            AnchorObject.Object.Version aObjectVersion = vObjectReference.getVersion();
            AnchorObject.Object.Version nextVersion =
                anchorObjectHandler.makeVersion(aObjectVersion.getGeneration(), aObjectVersion.getSerialNumber() + 1);

            vObject.setDeleteTokenId(aObject.getDeleteTokenId());
            vObject.setVersion(nextVersion);
            vObject.setTimeToLive(aObject.getTimeToLive());

            versionObjectHandler.storeObject(vObject);
            VersionObject.Object.Reference newVObjectReference = vObject.makeReference();

            lineariser.setValue(aObject.getObjectId(),
                    currentValue,
                    lineariser.newValue(newVObjectReference, currentValue.getLock()),
                    aObject.getAObjectVersionMapParams());

            OrderedProperties metaData = this.fillMetadata(aObject, vObject, currentValue.getLock());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("OK");
            }
            return metaData;
        } catch (MutableObject.InsufficientResourcesException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.NotFoundException e) {
            throw new CelesteException.NotFoundException("Cannot determine current version for %s", operation.getFileIdentifier());
        } catch (BeehiveObjectStore.NoSpaceException e) {
            throw new CelesteException.NoSpaceException(e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.PredicatedValueException e) {
            throw new CelesteException.OutOfDateException(e);
        } catch (MutableObject.ObjectHistory.ValidationException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (MutableObject.ProtocolException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectStore.DeletedObjectException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.NotFoundException e) {
            throw new CelesteException.NotFoundException(e);
        } catch (MutableObject.DeletedException e) {
            throw new CelesteException.DeletedException(e);
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            throw new CelesteException.RuntimeException(e);
        } catch (BeehiveObjectPool.Exception e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            timing.print(System.out);
        }
    }


    // A cache for credentials, lifetime one minute...
    //
    // Credentials are mainly used by the composer to authenticate writer
    // operations when the data is written.  Giving the client interface this
    // cache speeds up client-side profile retrievals as well as writing
    // operations (which would also require a profile retrieval.  Since
    // profile retrievals include high cost operations, minimizing them is
    // important.

    private ProfileCache credentialCache;

    public Credential getProfile(TitanGuid credentialId)
        throws IOException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException {

        // not using get() in the cache but instead manually putting it into
        // it avoids doubly performing verification that is present both in get()
        // and in readProfile(). It does exist in get() because that is mostly
        // relevant on the client side.
//        Credential credential = this.credentialCache.getCachedOnly(credentialId);
//        if (credential == null) {
            ReadProfileOperation readCredential = new ReadProfileOperation(credentialId);
            Credential credential = this.readCredential(readCredential);
//            if (credential != null)
//                this.credentialCache.put(credential);
//        }
        return credential;
    }

    /**
     * Verify the signature on the given operation.
     * <p>
     * Throws an {@link CelesteException.VerificationException} if the signature does not validate successfully.
     * </p>
     * @param operation the {@link AbstractCelesteOperation} from which the signing credential is taken.
     * @param signature the {@link Credential.Signature} on the operation and any additional {@link TitanGuid} instances as parameters.
     * @param objectIds an array of {@link TitanGuid} instances which are also used as data to verify with the signature.
     *
     * @throws CelesteException.VerificationException if the signature does not verify
     * @throws CelesteException.CredentialException
     * @throws CelesteException.RuntimeException
     * @throws CelesteException.AccessControlException
     * @throws CelesteException.NotFoundException
     * @throws IOException
     */
    public void checkOperationSignature(Credential.Signature signature, AbstractCelesteOperation operation, TitanGuid...objectIds)
    throws CelesteException.VerificationException, CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.AccessControlException, CelesteException.NotFoundException, IOException {

        TimeProfiler time = new TimeProfiler("verifyOperationSignature");
        try {
            if (signature != null) {
                try {
                    if (operation.getClientId() == null) {
                        throw new CelesteException.CredentialException("Credential object-id is null.");
                    }
                    Credential clientCredential = this.getProfile(operation.getClientId());
                    TitanGuid[] ids = new TitanGuid[objectIds.length + 1];
                    ids[0] = operation.getId();
                    for (int i = 0; i < objectIds.length; i++) {
                        ids[i+1] = objectIds[i];
                    }
                    if (clientCredential.verify(signature, ids) == false) {
                        StringBuilder s = new StringBuilder("Bad signature: clientId=")
                        .append(clientCredential.getObjectId())
                        .append(" signatureId=")
                        .append(signature.getObjectId());
                        for (int i = 0; i < ids.length; i++) {
                            s.append(" ").append(ids[i]);
                        }
                        throw new CelesteException.VerificationException(s.toString());
                    }
                } catch(Credential.Exception e) {
                    throw new CelesteException.CredentialException(e);
                }
            }
        } finally {
            time.print(System.out);
        }
    }
}
