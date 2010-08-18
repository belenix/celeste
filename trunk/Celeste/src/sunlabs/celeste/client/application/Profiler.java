/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.celeste.client.application;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import sunlabs.asdf.util.Time;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.client.operation.InspectFileOperation;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.NewNameSpaceOperation;
import sunlabs.celeste.client.operation.ProbeOperation;
import sunlabs.celeste.client.operation.ReadFileOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;
import sunlabs.celeste.client.operation.WriteFileOperation;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.Copyright;
import sunlabs.titan.Release;
import sunlabs.titan.api.Credential;
import sunlabs.titan.util.OrderedProperties;

/**
 * The Celeste Profiler is an application that simulates multiple simultaneous clients
 * performing multiple operations back-to-back and reports the elapsed time taken to
 * accomplish the operations.
 * <p>
 * The Profiler takes as command line arguments a set of parameters that control the
 * operations performed and writes on the standard output line containing the timing
 * information as comma-separated values.
 * </p>
 * <p>
 * The operations performed are:
 * <ul>
 * <li>Round-trip-time</li>
 * Measures the number of milliseconds to send a single round-trip message from the client to a Celeste node.
 * <br/>
 * <code>rtt:<i>initial</i>,<i>final</i>,<i>step</i>,<i>count</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>count: the number of messages to exchange.</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * <li>Bandwidth</li>
 * This test measures the number of bytes-per-second that can be copied, message by message, from the client to the Celeste node and back.
 * Be aware of the memory requirement for exchanging final*size messages.
 * If the resulting sizes are large, you likely will need adjustments to the
 * maximum memory sizes for the JVM running the Profiler.
 * <br/>
 * <code>bps:<i>initial</i>,<i>final</i>,<i>step</i>,<i>count</i>,<i>size</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>count: the number of messages to exchange.</li>
 * <li>size: the number of bytes in each message payload.</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * <li>Credentials</li>
 * This test creates a set of unique credentials.
 * <br/>
 * <code>creds:<i>count</i>,<i>output</i></li></code>
 * <ul>
 * <li>count: the number of credentials to create</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * <li>Create</li>
 * This test creates a set of unique files.
 * <br/>
 * <code>create:<i>initial</i>,<i>final</i>,<i>step</i>,<i>count</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>count: the number of files to create</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * <li>Write</li>
 * This test writes data to a file.
 * <br/>
 * <code>write:<i>initial</i>,<i>final</i>,<i>step</i>,<i>count</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>count: the writes to perform</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * <li>Read</li>
 * This test reads data from a file
 * <br/>
 * <code>read:<i>initial</i>,<i>final</i>,<i>step</i>,<i>count</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>count: the number of reads to perform</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The output consists of lines, each containing comma-separated values consisting of:
 * <ul>
 * <li>The number of simultaneous clients.</li>
 * <li>The minimum number of milliseconds to perform a single operation.</li>
 * <li>The mean number of milliseconds to perform a single operation.</li>
 * <li>The range of milliseconds to perform a single operation.</li>
 * <li>The median number of milliseconds to perform a single operation.</li>
 * <li>The maxium number of milliseconds to perform a single operation.</li>
 * </ul>
 * </p>
 * NB: This javadoc must be maintained in parallel with the Wiki page describing the Celeste Profiler.
 * @author Glenn Scott - Sun Microsystems Laboratories
 *
 */
public class Profiler {
    /**
     * Measure the time it takes to create a file.
     */
    public class CelesteCreateFile implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private String replicationParams;
        private int batch; // an integer differentiation different batches of files created.
        private int nOperations;

        public CelesteCreateFile(CountDownLatch countDown, Client client, int nOperations, int batch, String replicationParams) {
            this.client = client;
            this.countDown = countDown;
            this.batch = batch;
            this.nOperations = nOperations;
            this.replicationParams = replicationParams;
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException {

            CreateFileOperation createOperation = null;

            ClientMetaData clientMetaData = new ClientMetaData();
            long operationTime;
            long cummulativeTime = 0;
            long timeToLive = Time.minutesInSeconds(60);
            int blockObjectSize = 8*1024*1024;

            try {
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < this.nOperations; i++) {
                    BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(i).getBytes());
                    BeehiveObjectId deleteTokenId = this.client.credential.getObjectId().add(fileId);
                    FileIdentifier fileIdentifier = new FileIdentifier(this.client.nameSpaces.get(0), fileId);
                    createOperation = new CreateFileOperation(this.client.credential.getObjectId(),
                            fileIdentifier,
                            deleteTokenId,
                            timeToLive,
                            blockObjectSize,
                            this.replicationParams,
                            clientMetaData,
                            this.client.credential.getObjectId(),
                            CreateFileOperation.defaultGroupId,
                            CreateFileOperation.defaultAccessControl,
                            false);

                    Credential.Signature signature =
                        this.client.credential.sign("passphrase".toCharArray(), createOperation.getId(), clientMetaData.getId());

                    operationTime = System.currentTimeMillis();
                    try {
                        OrderedProperties reply = this.client.celeste.createFile(createOperation, signature);
                    } catch (CelesteException.VerificationException e) {
                        System.err.printf("signature %d: %s: %s %s%n", i, this.client.toString(), createOperation.getId(), clientMetaData.getId());
                        e.printStackTrace();
                        throw e;
                    }
                    if (false) {
                        ReadFileOperation readOperation = new ReadFileOperation(fileIdentifier, this.client.credential.getObjectId(), 0L, -1);

                        signature = this.client.credential.sign("passphrase".toCharArray(), readOperation.getId());

                        operationTime = System.currentTimeMillis();

                        ResponseMessage reply = this.client.celeste.readFile(readOperation, signature);
                    }

                    cummulativeTime += (System.currentTimeMillis() - operationTime);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the creation-time of a Credential.
     *
     */
    public class CelesteCredential implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private String replicationParams;

        public CelesteCredential(CountDownLatch countDown, Client client) {
            this.client = client;
            this.countDown = countDown;

            this.replicationParams = "Credential.Replication.Store=2";
        }

        /**
         * Perform {@code count} iterations of the Celeste
         * {@link NewCredentialOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException, CelesteException.NoSpaceException, CelesteException.VerificationException {
            try {
                long startTime = System.currentTimeMillis();
                BeehiveObjectId credentialId = new BeehiveObjectId(client.getName().getBytes());

                try {
                    ReadProfileOperation operation = new ReadProfileOperation(credentialId);

                    client.setCredential((Profile_) client.celeste.readCredential(operation));
                } catch (CelesteException.NotFoundException notFound) {
                    client.setCredential(new Profile_(client.getName(), client.credentialPassword.toCharArray()));
                    NewCredentialOperation operation = new NewCredentialOperation(client.getCredential().getObjectId(), BeehiveObjectId.ZERO, this.replicationParams);
                    Credential.Signature signature = client.getCredential().sign(client.credentialPassword.toCharArray(), operation.getId());
                    try {
                        client.celeste.newCredential(operation, signature, client.getCredential());
                    } catch (Exception e) {
                        System.err.println("failed to create credential for " + client.getName());
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return (System.currentTimeMillis() - startTime);
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the time it takes to inspect a file.
     */
    public class CelesteInspectFile implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private int batch;
        private int nOperations;

        public CelesteInspectFile(CountDownLatch countDown, Client client, int nOperations, int batch) {
            this.client = client;
            this.countDown = countDown;
            this.batch = batch;
            this.nOperations = nOperations;
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException {

            InspectFileOperation operation = null;

            long operationTime;
            long cummulativeTime = 0;

            BeehiveObjectId vObjectId = null;

            try {
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < this.nOperations; i++) {
                    BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(i).getBytes());

                    operation = new InspectFileOperation(new FileIdentifier(this.client.nameSpaces.get(0), fileId), vObjectId);
                    operationTime = System.currentTimeMillis();

                    ResponseMessage reply = this.client.celeste.inspectFile(operation);

                    cummulativeTime += (System.currentTimeMillis() - operationTime);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the creation-time of a NameSpace.
     *
     */
    public class CelesteNameSpace implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private String replicationParams;
        private boolean clientOnly;

        public CelesteNameSpace(CountDownLatch countDown, Client client) {
            this.client = client;
            this.countDown = countDown;
            this.clientOnly = false;

            this.replicationParams = "Credential.Replication.Store=2";
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException, CelesteException.NoSpaceException, CelesteException.VerificationException {
            try {
                long startTime = System.currentTimeMillis();

                try {
                    BeehiveObjectId nameSpaceId = new BeehiveObjectId((client.getName() + "ns").getBytes());
                    ReadProfileOperation operation = new ReadProfileOperation(nameSpaceId);

                    client.nameSpaces.add(client.celeste.readCredential(operation).getObjectId());
                } catch (CelesteException.NotFoundException notFound) {

                    Profile_ nameSpaceCredential = new Profile_(this.client.credential.getName() + "ns", client.credentialPassword.toCharArray());

                    NewNameSpaceOperation operation = new NewNameSpaceOperation(nameSpaceCredential.getObjectId(), BeehiveObjectId.ZERO, replicationParams);
                    Credential.Signature signature = client.getCredential().sign(client.credentialPassword.toCharArray(), operation.getId());

                    try {
                        if (this.clientOnly) {
                            client.celeste.newNameSpace(operation, signature, client.getCredential());
                        }
                        client.nameSpaces.add(nameSpaceCredential.getObjectId());
                    } catch (Exception e) {
                        System.err.println("failed to create credential for " + client.getName());
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return (System.currentTimeMillis() - startTime);
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the data transfer rate of the connection in bytes-per-second,
     * using the byte array {@code payload}.
     *
     * <p>
     * While the array {@code payload} is shared between all instances of the
     * Threads performing the measurement, the returned payload from the Celeste
     * node is not.  As a result the memory requirements for running this probe
     * is at least {@count} * {@code payload.length}.
     * </p>
     * <p>
     * For example, 200 clients transferring a 1Mb payload results in 200Mb of
     * data in memory.
     * </p>
     */
    public class CelesteProbeBandwidth implements Callable<Long> {
        private int count;
        private Client client;
        private ProbeOperation operation;
        private CountDownLatch countDown;

        public CelesteProbeBandwidth(CountDownLatch countDown, Client client, int count, byte[] payload) {
            this.count = count;
            this.client = client;
            this.operation = new ProbeOperation(client.credential, payload);
            this.countDown = countDown;
        }

        /**
         * Perform count iterations of the Celeste {@link ProbeOperation}
         * exchanging a payload.  Return the averaged number of milliseconds per exchange.
         */
        public Long call() throws IOException, ClassNotFoundException {
            try {
                long startTime = System.currentTimeMillis();
                for (int i = 0; i < this.count; i++) {
                    this.client.celeste.probe(operation);
                }
                return ((operation.getPayload().length * this.count) / (System.currentTimeMillis() - startTime)) * 1000L;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the round-trip-time as milliseconds per message between
     * the client and the Celeste node.
     *
     * <p>
     * Derive Transactions-per-second (TPS) as 1000/RTT.
     * </p>
     */
    public class CelesteProbeConnection implements Callable<Long> {
        private int nOperations;
        private Client client;
        private ProbeOperation operation;
        private CountDownLatch countDown;

        public CelesteProbeConnection(CountDownLatch countDown, Client client, int nOperations) {
            this.nOperations = nOperations;
            this.client = client;
            this.operation = new ProbeOperation();
            this.countDown = countDown;
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException {
            try {
                long startTime = System.currentTimeMillis();
                for (int i = 0; i < this.nOperations; i++) {
                    this.client.celeste.probe(operation);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the time it takes to write a file.
     */
    public class CelesteReadFile implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private int batch;
        private int nOperations;
        private int size;

        public CelesteReadFile(CountDownLatch countDown, Client client, int nOperations, int generation, int size) {
            this.client = client;
            this.countDown = countDown;
            this.batch = generation;
            this.nOperations = nOperations;
            this.size = size;
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.OutOfDateException, CelesteException.FileLocked {

            ReadFileOperation operation = null;

            long operationTime;
            long cummulativeTime = 0;

            BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(0).getBytes());
            FileIdentifier fileIdentifier = new FileIdentifier(this.client.nameSpaces.get(0), fileId);

            operationTime = System.currentTimeMillis();

            try {
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < this.nOperations; i++) {

                    operation = new ReadFileOperation(fileIdentifier, this.client.credential.getObjectId(), 0L, -1);

                    Credential.Signature signature =
                        this.client.credential.sign("passphrase".toCharArray(), operation.getId());

                    operationTime = System.currentTimeMillis();

                    ResponseMessage reply = this.client.celeste.readFile(operation, signature);

                    cummulativeTime += (System.currentTimeMillis() - operationTime);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    /**
     * Measure the time it takes to write a file.
     */
    public class CelesteWriteFile implements Callable<Long> {
        private Client client;
        private CountDownLatch countDown;
        private int batch;
        private int nOperations;
        private int size;

        public CelesteWriteFile(CountDownLatch countDown, Client client, int nOperations, int generation, int size) {
            this.client = client;
            this.countDown = countDown;
            this.batch = generation;
            this.nOperations = nOperations;
            this.size = size;
        }

        /**
         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
         * and return the average number of milliseconds per operation.
         */
        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.OutOfDateException, CelesteException.FileLocked {

            WriteFileOperation operation = null;
            ByteBuffer data = ByteBuffer.allocate(this.size);

            long operationTime;
            long cummulativeTime = 0;

            ClientMetaData clientMetaData = new ClientMetaData();

            BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(0).getBytes());

            BeehiveObjectId vObjectId = null;
            InspectFileOperation inspectOperation = new InspectFileOperation(new FileIdentifier(this.client.nameSpaces.get(0), fileId), client.credential.getObjectId());
            operationTime = System.currentTimeMillis();

            ResponseMessage reply = this.client.celeste.inspectFile(inspectOperation);

            vObjectId = reply.getMetadata().getPropertyAsObjectId(CelesteAPI.VOBJECTID_NAME, null);
            try {
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < this.nOperations; i++) {

                    operation = new WriteFileOperation(new FileIdentifier(this.client.nameSpaces.get(0), fileId),
                            this.client.credential.getObjectId(),
                            vObjectId,
                            clientMetaData,
                            0L, this.size);

                    Credential.Signature signature =
                        this.client.credential.sign("passphrase".toCharArray(), operation.getId(), clientMetaData.getId());

                    operationTime = System.currentTimeMillis();

                    OrderedProperties props = this.client.celeste.writeFile(operation, signature, data);
                    vObjectId = props.getPropertyAsObjectId(CelesteAPI.VOBJECTID_NAME, null);

                    cummulativeTime += (System.currentTimeMillis() - operationTime);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    public static class Client {
        public Profile_ credential;
        public String credentialPassword;
        public CelesteAPI celeste;
        public List<BeehiveObjectId> nameSpaces;
        public String name;

        public Client(String name, String password, CelesteAPI celeste) throws Credential.Exception {
            if (celeste == null)
                throw new IllegalArgumentException("celeste parameter is null");
            this.name = name;
            this.credentialPassword = password;
            this.celeste = celeste;
            this.nameSpaces = new LinkedList<BeehiveObjectId>();
        }

        public Profile_ getCredential() {
            return this.credential;
        }

        public String getName() {
            return this.name;
        }

        public void setCredential(Profile_ credential) {
            this.credential = credential;
        }

        @Override
        public String toString() {
            return String.format("Client %s: credential=%s celeste=%s nameSpace=%s", this.getName(), this.credential, this.celeste, this.nameSpaces);
        }
    }

    public static class Profile {
        private static final long serialVersionUID = 1L;

        public static void print(OutputStream out, List<Profile> list, String format, Object...args) throws IOException {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            out.write(String.format("# %s%n", Release.ThisRevision()).getBytes());
            out.write(String.format("# \"%s\" \"%s\" \"%s\"%n",  osBean.getName(), osBean.getVersion(), osBean.getArch()).getBytes());
            out.write(String.format(format, args).getBytes());
            out.write("\n".getBytes());
            out.write(Profile.reportHeaderCSV().getBytes());
            for (Profile p : list) {
                out.write(p.reportCSV().getBytes());
            }
        }

        public static String reportHeaderCSV() {
            return "Concurrent,Minimum,Mean,Range,Median,Maximum\n";
        }
        private List<Long> list;
        private long minimum;
        private long maximum;
        private long sum;

        private boolean alreadySorted = false;

        public Profile() {
            super();
            this.list = new LinkedList<Long>();
            this.sum = 0;
            this.maximum = Long.MIN_VALUE;
            this.minimum = Long.MAX_VALUE;
        }

        public boolean add(Long value) {
            this.sum += value;
            if (value > this.maximum)
                this.maximum = value;
            if (value < this.minimum)
                this.minimum = value;
            this.alreadySorted = false;
            boolean result = this.list.add(value);
            return result;
        }

        public Long maximum() {
            return this.maximum;
        }

        public Long mean() {
            return this.sum / this.list.size();
        }

        /**
         * Compute the median value in the sample set.
         * This destroys the order of the elements.
         */
        public Long median() {
            if (alreadySorted == false) {
                Collections.sort(this.list);
                this.alreadySorted = true;
            }
            return this.list.get(this.list.size()/2);
        }

        public Long minimum() {
            return this.minimum;
        }

        public Long range() {
            return this.maximum - this.minimum;
        }

        public String report() {
            if (this.list.size() == 0) {
                return String.format("%n=d %s", this.list.size(), "No data collected");
            } else {
                return String.format("n=%d minimum=%d mean=%d range=%d median=%d maximum=%d",
                        this.list.size(),
                        this.minimum(),
                        this.mean(),
                        this.range(),
                        this.median(),
                        this.maximum());
            }
        }

        /**
         * Produce a String of comma-separated values consisting of the
         * size of the sample set,
         * the minimum value,
         * the mean value,
         * the range of values in the sample set,
         * the median value in the sample set,
         * the maximum value in the sample set.
         */
        public String reportCSV() {
            if (this.list.size() == 0) {
                return "No data collected\n";
            } else {
                return String.format("%d,%d,%d,%d,%d,%d%n",
                        this.list.size(),
                        this.minimum(),
                        this.mean(),
                        this.range(),
                        this.median(),
                        this.maximum());
            }
        }

        public String toCSV() {
            StringBuilder result = new StringBuilder();
            if (this.list.size() > 0) {
                result.append(this.list.get(0).toString());
                for (int i = 1; i < this.list.size(); i++) {
                    result.append(",");
                    result.append(this.list.get(i));
                }
            }
            return result.toString();
        }

        @Override
        public String toString() {
            return this.list.toString();
        }
    }

    public static int MaximumClients = 200; // This needs to be less-than-or-equal to CelesteClientDaemon.MaxClients

    public static void main(String[] args) {
        String celesteAddress = "127.0.0.1:14000";
        boolean verbose = false;

        Stack<String> options = new Stack<String>();
        for (int i = args.length - 1; i >= 0; i--) {
            options.push(args[i]);
        }

        while (!options.empty()) {
            if (!options.peek().startsWith("--"))
                break;
            String option = options.pop();

            if (option.equals("--celeste-address")) {
                celesteAddress = options.pop();
            } else if (option.equals("--verbose")) {
            	verbose = true;
            }
        }

        System.out.printf("# %s%n", Release.ThisRevision());
        System.out.printf("# %s%n", Copyright.miniNotice);
        System.out.printf("# %Tc%n", System.currentTimeMillis());

        try {
            Profiler profiler = new Profiler(verbose, celesteAddress, Profiler.MaximumClients);
            try {
                if (options.empty()) {
                    for (;;) {
                        // read line
                        System.out.print("> ");
                        System.out.flush();
                        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                        String[] tokens = in.readLine().split("[ \t]+");
                        for (int i = tokens.length - 1; i >= 0; i--) {
                            options.push(tokens[i]);
                        }
                        try {
                            process(profiler, options);
                        } catch (EmptyStackException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        Profiler.process(profiler, options);
                    } catch (EmptyStackException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } finally {
                profiler.pool.shutdown();
            }
        } catch (Credential.Exception e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static InetSocketAddress makeAddress(String a) {
        String[] tokens = a.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }
    
    public static void process(Profiler profiler, Stack<String> options) throws Credential.Exception, InterruptedException, ExecutionException, IOException {
    	long celesteTimeOutMillis = 0;
    	
        while (!options.empty()) {
            String option = options.pop();
            if (option.startsWith("clients")) {
                int nClients = Integer.parseInt(options.pop());
                profiler.ensureSufficientClients(nClients, celesteTimeOutMillis);
            } else if (option.startsWith("rtt")) {
                String[] params = option.split("[:, ]+", 6);
                if (params.length != 6) {
                    System.err.printf("Usage: rtt:initial,max,step,nMessages,output%n");
                    continue;
                }

                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nMessages = Integer.parseInt(params[4]);

                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);

                Profile.print(output,
                        profiler.roundTripTime(initial, nClients, step, nMessages, celesteTimeOutMillis),
                        "# Round Trip Time: %d to %d by %d clients each performing %d operations", initial, nClients, step, nMessages);
            } else if (option.startsWith("bps")) {
                String[] params = option.split("[:, ]+", 7);
                if (params.length != 7) {
                    System.err.printf("Usage: bps:initial,max,step,nMessages,nBytes,output%n");
                    continue;
                }

                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nMessages = Integer.parseInt(params[4]);
                int nBytes = Integer.parseInt(params[5]);

                OutputStream output = params[6].equals("-") ? System.out : new FileOutputStream(params[6]);
                Profile.print(output,
                        profiler.bandwidth(initial, nClients, step, nMessages, nBytes, celesteTimeOutMillis),
                        "# Bandwidth BPS: %d to %d by %d clients each performing %d operations of %d bytes", initial, nClients, step, nMessages, nBytes);
            } else if (option.startsWith("creds")) {
                String[] params = option.split("[:, ]+", 3);
                if (params.length != 3) {
                    System.err.printf("Usage: creds:count,output%n");
                    continue;
                }
                int nClients = Integer.parseInt(params[1]);
                OutputStream output = params[2].equals("-") ? System.out : new FileOutputStream(params[2]);

                Profile.print(output,
                        profiler.credentials(nClients, celesteTimeOutMillis),
                        "# Credentials measuring %d operations", nClients);
            } else if (option.startsWith("namespaces")) {
                String[] params = option.split("[:, ]+", 3);
                if (params.length != 3) {
                    System.err.printf("Usage: namespaces:count,output%n");
                    continue;
                }
                int nClients = Integer.parseInt(params[1]);
                OutputStream output = params[2].equals("-") ? System.out : new FileOutputStream(params[2]);
                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.nameSpaces(nClients, celesteTimeOutMillis);
                Profile.print(output,
                		results,
                        "# Namespaces sending %d operations.%n# Elapsed time %s", nClients, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
            } else if (option.startsWith("create")) {
                String[] params = option.split("[:, ]+", 6);
                if (params.length != 6) {
                    System.err.printf("Usage: create:initial,max,step,files,output%n");
                    continue;
                }
                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nFiles = Integer.parseInt(params[4]);
                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);

                String replicationParams = "AObject.Replication.Store=2;VObject.Replication.Store=2;BObject.Replication.Store=2;AObjectVersionMap.Params=1,1";

                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.createFiles(initial, nClients, step, nFiles, replicationParams, celesteTimeOutMillis);
                Profile.print(output,
                		results,
                		"# Create files: %d to %d by %d clients each performing %d operations.%n# Elapsed time: %s",
                		initial, nClients, step, nFiles, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
            } else if (option.startsWith("inspect")) {
                String[] params = option.split("[:, ]+", 6);
                if (params.length != 6) {
                    System.err.printf("Usage: inspect:initial,max,step,files,output%n");
                    continue;
                }
                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nFiles = Integer.parseInt(params[4]);
                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);

                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.inspectFiles(initial, nClients, step, nFiles, celesteTimeOutMillis);
                Profile.print(output,
                        results,
                        "# Inspect: %d to %d by %d clients each performing %d operations.%n# Elapsed time: %s",
                        initial, nClients, step, nFiles, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
            } else if (option.startsWith("write")) {
                String[] params = option.split("[:, ]+", 6);
                if (params.length != 6) {
                    System.err.printf("Usage: write:initial,max,step,files,output%n");
                    continue;
                }
                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nFiles = Integer.parseInt(params[4]);
                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);

                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.writeFiles(initial, nClients, step, nFiles, celesteTimeOutMillis);
                Profile.print(output,
                        results,
                        "# Write: %d to %d by %d clients each performing %d operations.%n# Elapsed time: %s",
                        initial, nClients, step, nFiles, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
            } else if (option.startsWith("read")) {
                String[] params = option.split("[:, ]+", 6);
                if (params.length != 6) {
                    System.err.printf("Usage: read:initial,max,step,files%n");
                    continue;
                }
                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int nFiles = Integer.parseInt(params[4]);
                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);
                
                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.readFiles(initial, nClients, step, nFiles, celesteTimeOutMillis);
                Profile.print(output,
                        results,
                        "# Read: %d to %d by %d clients each performing %d operations.%n# Elapsed time: %s",
                        initial, nClients, step, nFiles, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
            } else if (option.equals("exit")) {
                return;
            } else {
                System.out.printf("Unknown option %s%n", option);
                System.out.printf("rtt:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.printf("bps:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.printf("creds:<concurrent>,<output>%n");
                System.out.printf("namespaces:<concurrent>,<output>%n");
                System.out.printf("create:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.printf("write:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.printf("read:<initial>,<final>,<step>%n");
                System.out.printf("inspect:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.flush();
            }
        }
    }

    InetSocketAddress address;
    boolean verbose;

    Map<Integer,Client> clients;

    ThreadPoolExecutor pool;

    public Profiler(boolean verbose, String celesteAddress, int maximumClientCount) throws IOException, Credential.Exception {
    	this.verbose = verbose;
        this.clients = new HashMap<Integer,Client>();
        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maximumClientCount);
        this.pool.prestartAllCoreThreads();

        this.address = Profiler.makeAddress(celesteAddress);
    }

    public List<Profile> bandwidth(int initial, int nClients, int step, int nMessages, int nBytes, long celesteTimeOutMillis) throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        this.ensureSufficientClients(nClients, celesteTimeOutMillis);
        byte[] payload = new byte[nBytes];

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                tasks.add(this.pool.submit(new CelesteProbeBandwidth(countDown, this.clients.get(clientId), nMessages, payload)));
            }

            countDown.await();
            Profile profile = new Profile();
            for (Future<Long> t : tasks) {
                profile.add(t.get());
            }
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
            result.add(profile);
        }
        return result;
    }

    public List<Profile> createFiles(int initial, int nClients, int step, int nOperations, String replicationParams, long celesteTimeOutMillis)
    throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        nameSpaces(nClients, celesteTimeOutMillis);

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                // Use concurrent as the "batch" argument to CelesteCreateFile()
                tasks.add(this.pool.submit(new CelesteCreateFile(countDown, this.clients.get(clientId), nOperations, concurrent, replicationParams)));
            }

            countDown.await();
            Profile profile = new Profile();
            

            for (Future<Long> t : tasks) {
                try {
                    profile.add(t.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
            result.add(profile);
        }

        return result;
    }

    public List<Profile> credentials(int nClients, long celesteTimeOutMillis) throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        this.ensureSufficientClients(nClients, celesteTimeOutMillis);

        CountDownLatch countDown = new CountDownLatch(nClients);
        List<Future<Long>> tasks = new LinkedList<Future<Long>>();

        for (Integer clientId : this.clients.keySet()) {
            tasks.add(this.pool.submit(new CelesteCredential(countDown, this.clients.get(clientId))));
        }

        countDown.await();
        List<Profile> result = new LinkedList<Profile>();
        Profile profile = new Profile();
        for (Future<Long> t : tasks) {
            profile.add(t.get());
        }
        result.add(profile);
        
        if (this.verbose) {
        	System.err.printf("%s%n", profile.report());
        }

        return result;
    }

    /**
     * Create {@code count} {@link Client} instances and connections to the
     * Celeste node identified by {@link #address}
     *
     * @param numberOfClients
     * @throws Credential.Exception
     * @throws IOException
     */
    public void ensureSufficientClients(int numberOfClients, long celesteTimeOutMillis) throws Credential.Exception, IOException {
        if (numberOfClients > Profiler.MaximumClients) {
            throw new IllegalArgumentException(String.format("maximum clients = %d", Profiler.MaximumClients));
        }
        if (this.clients.size() < numberOfClients) {
            for (int i = this.clients.size(); i < numberOfClients; i++) {
                this.clients.put(i, new Client(String.format("client-%s-%d", this.address, i),"passphrase", new CelesteProxy(this.address, celesteTimeOutMillis, TimeUnit.MILLISECONDS)));
            }
        }
    }

    public List<Profile> inspectFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis)
    throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        nameSpaces(nClients, celesteTimeOutMillis);
        List<Profile> result = new LinkedList<Profile>();
        // XXX Should the files be created or assumed to exist when this test is run?

//        System.out.printf("Inspect files: %d clients inspecting %d files%n", nClients, nOperations);
//        System.out.print(Profile.reportHeaderCSV());
        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                // Use concurrent as the generation argument to CelesteCreateFile()
                tasks.add(this.pool.submit(new CelesteInspectFile(countDown, this.clients.get(clientId), nOperations, concurrent)));
            }

            countDown.await();
            Profile profile = new Profile();
            for (Future<Long> t : tasks) {
                try {
                    profile.add(t.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            result.add(profile);
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
        }
        return result;
    }

    public List<Profile> nameSpaces(int nClients, long celesteTimeOutMillis) throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        //this.ensureSufficientCredentials(nClients);
        this.credentials(nClients, celesteTimeOutMillis);

//        System.out.printf("NameSpaces: %d namespaces%n", nClients);
//        System.out.print(Profile.reportHeaderCSV());

        CountDownLatch countDown = new CountDownLatch(nClients);
        List<Future<Long>> tasks = new LinkedList<Future<Long>>();

        for (Integer clientId : this.clients.keySet()) {
            tasks.add(this.pool.submit(new CelesteNameSpace(countDown, this.clients.get(clientId))));
        }

        countDown.await();
        List<Profile> result = new LinkedList<Profile>();
        Profile profile = new Profile();
        for (Future<Long> t : tasks) {
            profile.add(t.get());
        }
        result.add(profile);
        return result;
    }

    public List<Profile> readFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis) throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        nameSpaces(nClients, celesteTimeOutMillis);
        // XXX Should the files be created or assumed to exist when this test is run?

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                // Use concurrent as the generation argument to CelesteCreateFile()
                tasks.add(this.pool.submit(new CelesteReadFile(countDown, this.clients.get(clientId), nOperations, concurrent, 100)));
            }

            countDown.await();
            Profile profile = new Profile();
            for (Future<Long> t : tasks) {
                try {
                    profile.add(t.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            result.add(profile);
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
        }
        return result;
    }

    public List<Profile> roundTripTime(int initial, int nClients, int step, int nMessages, long celesteTimeOutMillis)
    throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        this.ensureSufficientClients(nClients, celesteTimeOutMillis);

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                tasks.add(this.pool.submit(new CelesteProbeConnection(countDown, this.clients.get(clientId), nMessages)));
            }

            countDown.await();
            Profile profile = new Profile();
            for (Future<Long> t : tasks) {
                profile.add(t.get());
            }

            result.add(profile);
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
        }

        return result;
    }

    public List<Profile> writeFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis)
    throws InterruptedException, ExecutionException, IOException, Credential.Exception {

        nameSpaces(nClients, celesteTimeOutMillis);
        // XXX Should the files be created or assumed to exist when this test is run?

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                // Use concurrent as the generation argument to CelesteCreateFile()
                tasks.add(this.pool.submit(new CelesteWriteFile(countDown, this.clients.get(clientId), nOperations, concurrent, 100)));
            }

            countDown.await();
            Profile profile = new Profile();
            for (Future<Long> t : tasks) {
                try {
                    profile.add(t.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            result.add(profile);
            if (this.verbose) {
            	System.err.printf("%s%n", profile.report());
            }
        }
        return result;
    }
}
