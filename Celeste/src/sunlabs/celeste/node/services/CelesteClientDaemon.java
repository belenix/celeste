/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */
package sunlabs.celeste.node.services;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.node.BeehiveNode;
import sunlabs.beehive.node.services.BeehiveService;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.Profile_;
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
import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.util.CelesteIO;

/**
 * Network Socket based Celeste client interface.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class CelesteClientDaemon extends BeehiveService {
    private final static long serialVersionUID = 1L;
    private final static String name = BeehiveService.makeName(CelesteClientDaemon.class, CelesteClientDaemon.serialVersionUID);

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
    private CelesteNode celesteNode;

    public CelesteClientDaemon(final BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, CelesteClientDaemon.name, "Celeste Client Handler");
        node.configuration.add(CelesteClientDaemon.Port);
        node.configuration.add(CelesteClientDaemon.MaximumClients);
        node.configuration.add(CelesteClientDaemon.ClientBacklog);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.configuration.get(CelesteClientDaemon.Port));
            this.log.config("%s", node.configuration.get(CelesteClientDaemon.MaximumClients));
            this.log.config("%s", node.configuration.get(CelesteClientDaemon.ClientBacklog));
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
                this.setName(String.format("%s-%s", CelesteClientDaemon.this.node.getObjectId().toString(), socket.toString()));
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
                this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(CelesteClientDaemon.this.node.configuration.asInt(CelesteClientDaemon.MaximumClients),
                        new SimpleThreadFactory(CelesteClientDaemon.this.node.getObjectId() + ":"));
                //this.executor.prestartAllCoreThreads();
                try {
                    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

                    serverSocketChannel.configureBlocking(true);
                    serverSocketChannel.socket().bind(new InetSocketAddress(CelesteClientDaemon.this.node.configuration.asInt(CelesteClientDaemon.Port)),
                            CelesteClientDaemon.this.node.configuration.asInt(CelesteClientDaemon.ClientBacklog));
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

    public ResponseMessage performOperation(ProbeOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.AccessControlException, CelesteException.IllegalParameterException,
    CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.NotFoundException, CelesteException.NoSpaceException {

        return this.celesteNode.probe(operation);
    }

    public OrderedProperties performOperation(CreateFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.AccessControlException, CelesteException.IllegalParameterException,
    CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.NotFoundException, CelesteException.NoSpaceException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.createFile(operation, signature);
    }

    public boolean performOperation(DeleteFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.deleteFile(operation, signature);
    }

    public ResponseMessage performOperation(ExtensibleOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.VerificationException, CelesteException.AccessControlException, CelesteException.CredentialException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        Serializable object = (Serializable) ois.readObject();
        return this.celesteNode.runExtension(operation, signature, object);
    }

    public ResponseMessage performOperation(InspectFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException, CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException {

        return this.celesteNode.inspectFile(operation);
    }

    public ResponseMessage performOperation(InspectLockOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        return this.celesteNode.inspectLock(operation);
    }

    public BeehiveObject.Metadata performOperation(NewNameSpaceOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.RuntimeException, CelesteException.AlreadyExistsException, CelesteException.NoSpaceException,
    CelesteException.VerificationException, CelesteException.CredentialException {

        Profile_ profile = (Profile_) ois.readObject();
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.newNameSpace(operation, signature, profile);
    }

    public BeehiveObject.Metadata performOperation(NewCredentialOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.AccessControlException, CelesteException.IllegalParameterException, CelesteException.AlreadyExistsException,
    CelesteException.CredentialException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.VerificationException {

        Profile_ profile = (Profile_) ois.readObject();
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.newCredential(operation, signature, profile);
    }

    public ResponseMessage performOperation(ReadFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.DeletedException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException {

        Credential.Signature signature = (Credential.Signature) ois.readObject();

        return this.celesteNode.readFile(operation, signature);
    }

    public Credential performOperation(ReadProfileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException {

        return this.celesteNode.readCredential(operation);
    }

    public OrderedProperties performOperation(SetACLOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.setACL(operation, signature);
    }

    public OrderedProperties performOperation(WriteFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
        CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
        CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
        CelesteException.OutOfDateException, CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        int size = ois.readInt();
        byte[] data = new byte[size];
        ois.readFully(data);
        return this.celesteNode.writeFile(operation, signature, ByteBuffer.wrap(data));
    }

    public ResponseMessage performOperation(ScriptableOperation operation, ObjectInputStream ois) throws IOException, ClassNotFoundException {

        @SuppressWarnings("unused")
        Credential.Signature signature = (Credential.Signature) ois.readObject();
        @SuppressWarnings("unused")
        Serializable object = (Serializable) ois.readObject();
        return null;
        //        return this.celesteNode.runExtension((ScriptableOperation) operation, signature, object);
    }

    public OrderedProperties performOperation(SetFileLengthOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.NoSpaceException,
    CelesteException.VerificationException, CelesteException.OutOfDateException, CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.setFileLength(operation, signature);
    }

    public OrderedProperties performOperation(SetOwnerAndGroupOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.setOwnerAndGroup(operation, signature);
    }

    public ResponseMessage performOperation(LockFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.lockFile(operation, signature);
    }

    public ResponseMessage performOperation(UnlockFileOperation operation, ObjectInputStream ois)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileNotLocked, CelesteException.FileLocked {

        Credential.Signature signature = (Credential.Signature) ois.readObject();
        return this.celesteNode.unlockFile(operation, signature);
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

    /**
     * Set the {@link CelesteNode} instance that this daemon is to use to perform the Celeste functions.
     *
     *
     * @param celesteNode
     */
    public void setCelesteNode(CelesteNode celesteNode) {
        this.celesteNode = celesteNode;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
}
