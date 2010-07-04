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

package sunlabs.celeste.client;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.LRUCache;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
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
import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.util.CelesteIO;

/**
 * The {@code CelesteProxy} class is the primary client interface to Celeste and is a proxy for client invocations of methods in
 * the {@code Celeste} interface.
 * It relays such invocations to a remote {@code Celeste} node and retrieves and returns their results to the original caller.
 * The remote node is identified by its {@code URL}.
 */
public class CelesteProxy implements CelesteAPI {
    /**
     * <p>
     * A cache for {@code CelesteProxy} instances.
     * </p><p>
     * The class provides two cache variants:  one for communicating with
     * Celeste nodes running on remote hosts, and another for communicating
     * with a Celeste node running in the caller's address space.
     * </p><p>
     * Callers should obtain and release entries from the cache according to
     * the design pattern described in
     * {@link sunlabs.beehive.util.LRUCache}.
     * </p>
     */
    //
    // XXX: I haven't been able to find a way to make links to the two
    //      constructors work in the javadoc comment above.  In particular,
    //          {@link #Cache(int) one}
    //      and
    //          {@link #Cache(CelesteNode) another}
    //      don't work.
    //
    public static class Cache extends LRUCache<InetSocketAddress, CelesteAPI> {
        private final static long serialVersionUID = 1L;

        /**
         * Creates a cache for handling {@code CelesteAPI} proxy connections to a remote Celeste node.
         * The cache treats each proxy instance as exclusive use and specifies that each instance should use {@code timeOutMillis} as its {@link Socket}
         * timeout value for communicating to a remote node.
         * See {@link Socket#setSoTimeout(int)}.
         *
         * @param capacity      the maximum number of entries the cache should contain
         * @param timeOutMillis the timeout value to use when communicating through a proxy to a remote Celeste node
         */
        public Cache(int capacity, long timeOutMillis) {
            this(capacity, true, new ProxyFactory(timeOutMillis, TimeUnit.MILLISECONDS));
        }

        /**
         * Creates a cache for handling {@code CelesteAPI} connections to a Celeste node that's part of the caller's address space.
         *
         * @param celesteNode   the Celeste node to which the cache's proxy will make calls
         */
        public Cache(CelesteNode celesteNode) {
            this(1, false, new DirectCelesteReferenceFactory(celesteNode));
        }

        //
        // Private constructor that does the real work.
        //
        private Cache(int capacity, boolean exclusiveItemUse, LRUCache.Factory<InetSocketAddress, CelesteAPI> factory) {
            super(capacity, factory, exclusiveItemUse, null);
        }

        /**
         * This method specializes the superclass implementation by closing
         * the proxy when it is no longer either in use or in the cache.
         *
         * @param addr  the address of the proxy's Celeste node
         * @param proxy the proxy itself
         */
        @Override
        protected void disposeItem(InetSocketAddress addr, CelesteAPI proxy) {
            try {
                super.disposeItem(addr, proxy);
                proxy.close();
            } catch (IOException ignore) {
                //
                // XXX: Perhaps some sort of log message should be issued
                //      here.  There's little else that can be done.
                //
            }
        }
    }

    //
    // The proxy factory to be used when connecting to remote Celeste nodes.
    //
    private static class ProxyFactory implements LRUCache.Factory<InetSocketAddress, CelesteAPI> {
        private final long timeOut;
        private final TimeUnit timeUnit;

        public ProxyFactory(long timeOut, TimeUnit timeUnit) {
            this.timeOut = timeOut;
            this.timeUnit = timeUnit;
        }

        public CelesteAPI newInstance(InetSocketAddress addr) throws IOException {
            return new CelesteProxy(addr, this.timeOut, this.timeUnit);
        }
    }

    //
    // This factory variant accepts a direct reference to a CelesteNode in its
    // constructor and hands that reference back every time its corresponding
    // ProxyCache needs a new instance.  It's intended for use in situations
    // where the client is linked in directly with the Celeste implementation.
    //
    private static class DirectCelesteReferenceFactory implements
            LRUCache.Factory<InetSocketAddress, CelesteAPI> {
        private final CelesteNode celesteNode;

        public DirectCelesteReferenceFactory(CelesteNode celesteNode) {
            this.celesteNode = celesteNode;
        }

        public CelesteAPI newInstance(InetSocketAddress addr) throws Exception {
            //System.err.printf("DirectProxyCache miss: %s%n", addr);
            return this.celesteNode;
        }
    }

    private final Socket newSocket;
    private final InetSocketAddress address;
    private final ObjectOutputStream objectOutputStream;
    private final ObjectInputStream objectInputStream;
    private BeehiveObjectId networkObjectId;

    /**
     * Create a Celeste Client node.
     * <p>
     * See {@link Socket#setSoTimeout(int)}
     * </p>
     *
     * @param address   the {@link InetSocketAddress} of the Celeste node this
     *                  client is to communicate with
     * @param timeOut   the specified timeout, in units given by {@code
     *                  timeUnit}
     * @param timeUnit  the unit in which {@code timeOut} is given
     */
    public CelesteProxy(InetSocketAddress address, long timeOut, TimeUnit timeUnit) throws IOException {
        this.address = address;
        this.newSocket = new Socket(address.getAddress(), address.getPort());
        this.newSocket.setSoTimeout((int) timeUnit.toMillis(timeOut));

        //
        // The proxy handshakes with its peer Celeste node by first reading a
        // line from its socket connection and obtaining the node's Beehive
        // object id from the line's contents.  It then turns around and
        // writes an object describing the protocol it expects to speak.
        //
        InputStream in = this.newSocket.getInputStream();
        byte[] bytes = CelesteIO.readLineAsByteArray(in);
        String line = new String(bytes).trim();
        this.networkObjectId = new BeehiveObjectId(line);

        BufferedOutputStream bos = new BufferedOutputStream(this.newSocket.getOutputStream());
        CelesteIO.writeLine(bos, CelesteAPI.CLIENT_PROTOCOL_OBJECT);
        bos.flush();

        this.objectInputStream = new ObjectInputStream(this.newSocket.getInputStream());
        this.objectOutputStream = new ObjectOutputStream(bos);
    }

    public void close() {
        try {
            this.objectInputStream.close();
        } catch (IOException ignore) {

        }

        try {
            this.objectOutputStream.close();
        } catch (IOException ignore) {

        }
        try {
            this.newSocket.close();
        } catch (IOException ignore) {

        }
    }

    public BeehiveObjectId getNetworkObjectId() {
        return this.networkObjectId;
    }

    /**
     * Return the {@code InetSocketAddress} of the Celeste node with which
     * this Celeste proxy communicates.
     */
    public InetSocketAddress getInetSocketAddress() {
        return this.address;
    }

    public OrderedProperties createFile(CreateFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.AccessControlException, CelesteException.IllegalParameterException,
    CelesteException.AlreadyExistsException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.NotFoundException, CelesteException.NoSpaceException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.IllegalParameterException)
                throw (CelesteException.IllegalParameterException) reason;
            if (reason instanceof CelesteException.AlreadyExistsException)
                throw (CelesteException.AlreadyExistsException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;

            System.err.printf("CelesteProxy.createFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }

        return (OrderedProperties) reply;
    }

    public ResponseMessage probe(ProbeOperation operation)
    throws IOException, ClassNotFoundException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            //
            return null;
        }
        return (ResponseMessage) reply;
    }

    public OrderedProperties writeFile(WriteFileOperation operation, Credential.Signature signature,  ByteBuffer buffer)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.NoSpaceException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.DeletedException,
    CelesteException.OutOfDateException, CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.writeInt(buffer.remaining());
        this.objectOutputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.IllegalParameterException)
                throw (CelesteException.IllegalParameterException) reason;
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.OutOfDateException)
                throw (CelesteException.OutOfDateException) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.writeFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }

        return (OrderedProperties) reply;
    }

    public ResponseMessage readFile(ReadFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.DeletedException, CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.IllegalParameterException)
                throw (CelesteException.IllegalParameterException) reason;

            System.err.printf("CelesteProxy.readFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (ResponseMessage) reply;
    }

    public OrderedProperties setFileLength(SetFileLengthOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
    CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.NoSpaceException,
    CelesteException.VerificationException, CelesteException.OutOfDateException, CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.setFileLength: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();
            return null;
        }
        return (OrderedProperties) reply;
    }

    public Boolean deleteFile(DeleteFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
           CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException,
           CelesteException.RuntimeException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.NoSpaceException {

            this.objectOutputStream.reset();
            this.objectOutputStream.writeObject(operation);
            this.objectOutputStream.writeObject(signature);
            this.objectOutputStream.flush();
            Serializable reply = (Serializable) this.objectInputStream.readObject();
            if (reply instanceof Exception) {
                Exception reason = (Exception) reply;
                // XXX There must be a better way.
                if (reason instanceof CelesteException.CredentialException)
                    throw (CelesteException.CredentialException) reason;
                if (reason instanceof CelesteException.AccessControlException)
                    throw (CelesteException.AccessControlException) reason;
                if (reason instanceof CelesteException.NotFoundException)
                    throw (CelesteException.NotFoundException) reason;
                if (reason instanceof CelesteException.RuntimeException)
                    throw (CelesteException.RuntimeException) reason;
                if (reason instanceof CelesteException.DeletedException)
                    throw (CelesteException.DeletedException) reason;
                if (reason instanceof CelesteException.VerificationException)
                    throw (CelesteException.VerificationException) reason;

                System.err.printf("CelesteProxy.deleteFile: Uncaught exception (developers take note): %s%n", reason.toString());
                reason.printStackTrace();
                return null;
            }
            return (Boolean) reply;
    }

    public ResponseMessage inspectFile(InspectFileOperation operation)
    throws IOException, ClassNotFoundException,
           CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException  {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            System.err.printf("inspectFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();
            return null;
        }
        return (ResponseMessage) reply;
    }

    public ResponseMessage inspectLock(InspectLockOperation operation)
    throws IOException, ClassNotFoundException,
           CelesteException.NotFoundException, CelesteException.RuntimeException, CelesteException.DeletedException  {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            System.err.printf("inspectLock: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (ResponseMessage) reply;
    }

    public ResponseMessage lockFile(LockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.IllegalParameterException)
                throw (CelesteException.IllegalParameterException) reason;
            if (reason instanceof CelesteException.OutOfDateException)
                throw (CelesteException.OutOfDateException) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.lockFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (ResponseMessage) reply;
    }

    public Credential readCredential(ReadProfileOperation operation)
    throws IOException, ClassNotFoundException, CelesteException.CredentialException, CelesteException.NotFoundException, CelesteException.RuntimeException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            System.err.printf("CelesteProxy.readCredential: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (Credential) reply;
    }

    public BeehiveObject.Metadata newCredential(NewCredentialOperation operation, Credential.Signature signature, Credential profile)
    throws IOException, ClassNotFoundException,
        CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(profile);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.AlreadyExistsException)
                throw (CelesteException.AlreadyExistsException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;

            System.err.printf("CelesteProxy.newCredential: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }

        return (BeehiveObject.Metadata) reply;
    }

    public BeehiveObject.Metadata newNameSpace(NewNameSpaceOperation operation, Credential.Signature signature, Credential profile)
    throws IOException, ClassNotFoundException,
        CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.CredentialException {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(profile);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.AlreadyExistsException)
                throw (CelesteException.AlreadyExistsException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;

            System.err.printf("CelesteProxy.newNameSpace: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();
            return null;
        }

        return (BeehiveObject.Metadata) reply;
    }

    public OrderedProperties setOwnerAndGroup(SetOwnerAndGroupOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.DeletedException, CelesteException.NoSpaceException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.NoSpaceException)
                throw (CelesteException.NoSpaceException) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.setOwnerAndGroup: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (OrderedProperties) reply;
    }

    public OrderedProperties setACL(SetACLOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.RuntimeException,
    CelesteException.NoSpaceException, CelesteException.DeletedException, CelesteException.VerificationException, CelesteException.OutOfDateException,
    CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.setACL: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (OrderedProperties) reply;
    }

    public ResponseMessage unlockFile(UnlockFileOperation operation, Credential.Signature signature)
    throws IOException, ClassNotFoundException,
    CelesteException.CredentialException, CelesteException.AccessControlException, CelesteException.NotFoundException, CelesteException.DeletedException,
    CelesteException.RuntimeException, CelesteException.VerificationException, CelesteException.IllegalParameterException, CelesteException.OutOfDateException,
    CelesteException.FileNotLocked, CelesteException.FileLocked {

        this.objectOutputStream.reset();
        this.objectOutputStream.writeObject(operation);
        this.objectOutputStream.writeObject(signature);
        this.objectOutputStream.flush();
        Serializable reply = (Serializable) this.objectInputStream.readObject();
        if (reply instanceof Exception) {
            Exception reason = (Exception) reply;
            // XXX There must be a better way.
            if (reason instanceof CelesteException.CredentialException)
                throw (CelesteException.CredentialException) reason;
            if (reason instanceof CelesteException.AccessControlException)
                throw (CelesteException.AccessControlException) reason;
            if (reason instanceof CelesteException.NotFoundException)
                throw (CelesteException.NotFoundException) reason;
            if (reason instanceof CelesteException.DeletedException)
                throw (CelesteException.DeletedException) reason;
            if (reason instanceof CelesteException.RuntimeException)
                throw (CelesteException.RuntimeException) reason;
            if (reason instanceof CelesteException.VerificationException)
                throw (CelesteException.VerificationException) reason;
            if (reason instanceof CelesteException.IllegalParameterException)
                throw (CelesteException.IllegalParameterException) reason;
            if (reason instanceof CelesteException.OutOfDateException)
                throw (CelesteException.OutOfDateException) reason;
            if (reason instanceof CelesteException.FileNotLocked)
                throw (CelesteException.FileNotLocked) reason;
            if (reason instanceof CelesteException.FileLocked)
                throw (CelesteException.FileLocked) reason;

            System.err.printf("CelesteProxy.lockFile: Uncaught exception (developers take note): %s%n", reason.toString());
            reason.printStackTrace();

            return null;
        }
        return (ResponseMessage) reply;
    }

    public ResponseMessage runExtension(ExtensibleOperation operation, Credential.Signature signature, Serializable object)
    throws IOException,
        CelesteException.AccessControlException, CelesteException.VerificationException, CelesteException.CredentialException, CelesteException.NotFoundException,
        CelesteException.RuntimeException, CelesteException.NoSpaceException, CelesteException.IllegalParameterException {
        try {
            this.objectOutputStream.reset();
            this.objectOutputStream.writeObject(operation);
            this.objectOutputStream.writeObject(signature);
            this.objectOutputStream.writeObject(object);
            this.objectOutputStream.flush();
            Serializable reply = (Serializable) this.objectInputStream.readObject();
            if (reply instanceof Exception) {
                Exception reason = (Exception) reply;
                // XXX There must be a better way.
                if (reason instanceof CelesteException.AccessControlException)
                    throw (CelesteException.AccessControlException) reason;
                if (reason instanceof CelesteException.VerificationException)
                    throw (CelesteException.VerificationException) reason;
                if (reason instanceof CelesteException.CredentialException)
                    throw (CelesteException.CredentialException) reason;
                if (reason instanceof CelesteException.NotFoundException)
                    throw (CelesteException.NotFoundException) reason;
                if (reason instanceof CelesteException.RuntimeException)
                    throw (CelesteException.RuntimeException) reason;
                if (reason instanceof CelesteException.NoSpaceException)
                    throw (CelesteException.NoSpaceException) reason;
                if (reason instanceof CelesteException.IllegalParameterException)
                    throw (CelesteException.IllegalParameterException) reason;

                System.err.printf("CelesteProxy.runExtension: Uncaught exception (developers take note): %s %s%n", reason.getClass(), reason.toString());
                reason.printStackTrace();
                return null;
            }
            return (ResponseMessage) reply;
        } catch (ClassNotFoundException e) {
            return new ResponseMessage(e);
        } catch (IllegalStateException e) {
            System.err.printf("ILLEGAL STATE EXCEPTION: remaining data %d%n", this.objectInputStream.available());
            for (int i = 0; i < this.objectInputStream.available(); i++) {
                System.err.printf("%c ", this.objectInputStream.read());
                System.err.flush();
            }
            throw e;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("CelesteProxy");
        result.append(" ").append(this.address.toString());

        return result.toString();
    }
}
