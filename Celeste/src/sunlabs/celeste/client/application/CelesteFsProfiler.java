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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EmptyStackException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import sunlabs.asdf.util.Time;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.Release;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.OrderedProperties;

import sunlabs.celeste.CelesteException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.PathName;
import sunlabs.celeste.client.filesystem.simple.FileException;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.NewNameSpaceOperation;
import sunlabs.celeste.client.operation.ReadProfileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.util.ACL;

import static sunlabs.celeste.client.filesystem.FileAttributes.Names.*;

/**
 * <p>
 *
 * {@code CelesteFsProfiler} is an application that simulates multiple
 * simultaneous clients performing multiple operations back-to-back through
 * the Celeste file system and reports the elapsed time taken to accomplish
 * the operations.
 *
 * </p><p>
 *
 * {@code CelesteFsProfiler} takes as command line arguments a set of
 * parameters that control the operations performed and writes on the standard
 * output line containing the timing information as comma-separated values.
 *
 * </p><p>
 *
 * The operations performed are:
 *
 * </p><ul>
 *
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
 * </ul><p>
 *
 * <li>Create Trees</li>
 * This test creates complete directory trees with a given depth and fanout,
 * using a varying number of clients to cooperate in creating each tree.
 * <br>
 * <code>create-trees:<i>initial</i>,<i>final</i>,<i>step</i>,<i>depth</i>,<i>fanout</i>,<i>output</i></code>
 * <ul>
 * <li>initial: the initial number of concurrent clients</li>
 * <li>final: the final number of concurrent clients</li>
 * <li>step: the increment of the number of concurrent clients for each test</li>
 * <li>depth: the depth of the directory tree to create</li>
 * <li>fanout: the number of children each directory in the tree should have</li>
 * <li>output: the name of the output file, or '-' for standard output.</li>
 * </ul>
 *
 * </ul>
 *
 * N.B.:  This javadoc must be maintained in parallel with the Wiki page
 * describing {@code CelesteFsProfiler}.
 *
 * </p>
 */
public class CelesteFsProfiler {
    /**
     * Measure the time it takes to create a file.
     */
    public class CelesteCreateFile implements Callable<Long> {
        private final Client client;
        private final CountDownLatch countDown;
        private final String replicationParams;
        //
        // An integer differentiating different batches of files created.
        //
        // XXX: Not used for anything.
        //
        private final int batch;
        private final int nOperations;

        //
        // XXX: Need to figure out how the next two fields will be
        //      initialized.  Ideally, it should be possible to run this (and
        //      all the other) CelesteFsProfiler tests repeatedly, without
        //      having them conflict with each other.  That argues for having
        //      names include a random component.
        //
        //      Alternatively, perhaps fs should be given as an argument to
        //      the constructor.
        //
        private final String fileSystemName;
        private CelesteFileSystem fs = null;

        public CelesteCreateFile(CountDownLatch countDown, Client client, int nOperations, int batch, String replicationParams)
                throws IOException, FileException {
            this.client = client;
            this.countDown = countDown;
            this.batch = batch;
            this.nOperations = nOperations;
            this.replicationParams = replicationParams;

            //
            // Generate a random name for the name space we'll use to hold the
            // files this test creates (and thus for the file system itself.)
            //
            // (Making the name be random avoids collisions from multiple runs
            // of this test.)
            //
            this.fileSystemName = CelesteFsProfiler.this.randomFileName(CelesteFsProfiler.fileNameLength);

            //
            // Create the name space corresponding to this.fileSystemName.
            // Note that client.credentialPassword does double duty, serving
            // as the name space's password as well as the client credential's
            // password.
            //
            CelesteFsProfiler.this.ensureNameSpace(client,
                this.fileSystemName, client.credentialPassword,
                replicationParams);

            //
            // Create the file system that will hold the files.
            //
            this.fs = new CelesteFileSystem(
                this.client.getAddress(),
                this.client.getProxyCache(),
                this.fileSystemName,
                this.client.credential.getName(),
                this.client.credentialPassword);
            this.fs.newFileSystem();
        }

        /**
         * Perform {@code count} iterations of the {@code
         * CelesteFileSystem.createFile(Pathname)} operation and return the
         * average number of milliseconds per operation.
         */
        //
        // XXX: At the file system level, it's not possible to do a create
        //      without adding an entry for the new file to some directory.
        //      So this test of necessity measures directory performance as
        //      well as raw create performance.
        //
        // XXX: Need to stop catching FileException and add a throws clause
        //      with all the sub-exceptions that might occur.
        //
        // XXX: Need to check on how the parameters for this test map into
        //      instances of CelesteCreateFile and into calls to this method.
        //      To avoid degrading the results for tests with higher numbers
        //      of clients later in the run with overhead from leftovers
        //      from earlier in the run, it'd be wise to ensure that each
        //      client level gets a directory (or perhaps even a file system)
        //      of its own.
        //
        public Long call() {
            long operationTime;
            long cumulativeTime = 0;
            final long timeToLive = Time.minutesInSeconds(60);
            final long deleteTimeToLive = Time.minutesInSeconds(30);
            final int blockObjectSize = 8*1024*1024;

            //
            // Set up attributes to be shared by all files created in the
            // test.
            //
            // Mirror the attributes used in the base Celeste-level test as
            // closely as possible.  (But note that that test doesn't set
            // content type or delete time to live.)
            //
            OrderedProperties fileAttributes = new OrderedProperties();
            fileAttributes.put(BLOCK_SIZE_NAME, Integer.toString(blockObjectSize));
            fileAttributes.put(CONTENT_TYPE_NAME, "text/plain");
            fileAttributes.put(DELETION_TIME_TO_LIVE_NAME, Long.toString(deleteTimeToLive));
            fileAttributes.put(REPLICATION_PARAMETERS_NAME, this.replicationParams);
            fileAttributes.put(SIGN_MODIFICATIONS_NAME, Boolean.toString(false));
            fileAttributes.put(TIME_TO_LIVE_NAME, Long.toString(timeToLive));

            //
            // XXX: Need to ensure that there's a file system to hold the
            //      files that will be created below.  Should they be placed
            //      in the root directory, should a directory be created
            //      specifically to hold them, or should some other placement
            //      criterion be used?
            //
            //      Lacking a concrete reason to do otherwise, put the files
            //      in the root directory.
            //
            //      Eventually,
            //

            try {
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < this.nOperations; i++) {
                    //
                    // Create a random file name.  Note that the names need to
                    // be long enough that collisions are extremely unlikely,
                    // but short enough that manipulating them doesn't take
                    // excessively long.  (The Python version of this test
                    // uses a name length of 20, which has worked without
                    // fault for creating up to 1000 files.)
                    //
                    String fileName = CelesteFsProfiler.this.randomFileName(CelesteFsProfiler.fileNameLength);
                    PathName path = new PathName(String.format("/%s", fileName));

                    operationTime = System.currentTimeMillis();

                    //
                    // XXX: Exception handling:  catch them, let them
                    //      propagate?  For now, simply note and press on.
                    //
                    try {
                        fs.createFile(path);
                    } catch (FileException.Exists e) {
                        //
                        // There's been a name space collision.  Although each
                        // generated file name should be unique, there's
                        // always a chance that it isn't.  Note that fact.
                        //
                        System.err.printf(
                            "CelesteCreateFile: name collision at \"%s\"%n",
                            path);
                    } catch (FileException e) {
                        System.err.printf(
                            "CelesteCreateFile: %s%n", e.getMessage());
                        e.printStackTrace(System.err);
                    }

                    cumulativeTime += (System.currentTimeMillis() - operationTime);
                }
                return (System.currentTimeMillis() - startTime) / this.nOperations;
            } finally {
                this.countDown.countDown();
            }
        }
    }

    //
    // A class that does the work of constructing a complete m x n tree in the
    // file system name space.
    //
    // Clients cooperate to construct the tree by sharing a work list whose
    // entries describe pieces of the tree yet to be built.  As each client
    // works on an entry, it adds new entries for the next layer down from
    // where it is.
    //
    private class TreeBuilder {
        //
        // A given client's contribution to building the tree.
        //
        private final class TreeBuilderTask implements Callable<Long> {
            private final Client client;
            private final CountDownLatch latch;

            public TreeBuilderTask(Client client, CountDownLatch latch) {
                this.client = client;
                this.latch = latch;
            }

            //
            // XXX: Should catch all exceptions; otherwise the thread waiting
            //      for all tasks to complete will hang.
            //
            public Long call()  throws
                    FileException.BadVersion,
                    FileException.Exists,
                    FileException.CapacityExceeded,
                    FileException.CelesteFailed,
                    FileException.CelesteInaccessible,
                    FileException.CredentialProblem,
                    FileException.Deleted,
                    FileException.DirectoryCorrupted,
                    FileException.FileSystemNotFound,
                    FileException.IOException,
                    FileException.InvalidName,
                    FileException.Locked,
                    FileException.NotDirectory,
                    FileException.NotFound,
                    FileException.PermissionDenied,
                    FileException.RetriesExceeded,
                    FileException.Runtime,
                    FileException.ValidationFailed {
                //System.err.printf("TBT.call() %s entered%n",
                //    Thread.currentThread().getName());
                final TreeBuilder builder = TreeBuilder.this;
                final Deque<CreateItem> workList = builder.workList;
                long cumulativeTime = 0;
                try {
                    for (;;) {
                        CreateItem item = null;
                        synchronized(workList) {
                            if (builder.isFailed() || builder.leafClumpsClaimed ==
                                    builder.leafClumps) {
                                //
                                // There's no more work to do or a failure has
                                // occurred and the tree can't be completed.
                                // Return the time we spent working.
                                //
                                //System.err.printf("TBT: %s: no work remaining%n",
                                //    Thread.currentThread().getName());
                                return cumulativeTime;
                            }
                            //
                            // If there's work available grab it.  Otherwise,
                            // wait for some other worker to add some work.
                            //
                            if (workList.size() == 0) {
                                try {
                                    //System.err.printf("TBT: %s: empty workList%n",
                                    //    Thread.currentThread().getName());
                                    workList.wait();
                                    //System.err.printf("TBT: %s: awakened%n",
                                    //        Thread.currentThread().getName());
                                } catch (InterruptedException e) {
                                    //
                                    // Fall through.
                                    //
                                }
                                continue;
                            } else {
                                //
                                // XXX: Think about policy here.  Is there
                                //      value in randomly choosing between
                                //      removing from the head or the tail?
                                //
                                item = workList.removeLast();
                                //System.err.printf("TBT: %s: grabbed %s%n",
                                //    Thread.currentThread().getName(), item);
                                //
                                // If it's a leaf item, record our claim on
                                // it.
                                //
                                if (item.basePathDepth == builder.depth) {
                                    //System.err.printf(
                                    //    "claiming leaf work at %s%n",
                                    //    item.basePath);
                                    builder.leafClumpsClaimed++;
                                }
                            }
                        }
                        //
                        // Create the section of the tree that item describes.
                        //
                        long startTime = System.currentTimeMillis();
                        for (int i = 0; i < builder.fanout; i++) {
                            //
                            // Get a random name for the new file or directory
                            // about to be created.
                            //
                            String name = builder.generateRandomName();
                            PathName path = new PathName(String.format("%s/%s",
                                item.basePath, name));
                            try {
                                if (item.basePathDepth == builder.depth) {
                                    builder.createFile(this.client, path);
                                } else {
                                    builder.createDirectory(this.client, path);
                                    CreateItem newItem = new CreateItem(path,
                                        builder.fanout, item.basePathDepth + 1);
                                    synchronized(workList) {
                                        //System.err.printf("TBT: %s adding %s%n",
                                        //    Thread.currentThread().getName(),
                                        //    newItem);
                                        workList.addFirst(newItem);
                                        workList.notifyAll();
                                    }
                                }
                            } catch (Exception e) {
                                builder.setFailed(true);
                                //System.err.printf("TBT:%s create threw %s%n",
                                //    Thread.currentThread().getName(),
                                //    e.getMessage());
                                //e.printStackTrace(System.err);
                            }
                        }
                        cumulativeTime +=
                            System.currentTimeMillis() - startTime;
                    }
                } finally {
                    latch.countDown();
                }
            }
        }

        //
        // As workers create new directories in the interior of the tree, they
        // construct entries describing how those directories should be
        // populated and add them to workList.  When a worker finishes with
        // its current item, it grabs a new one from the list.
        //
        // Note that accesses to workList must be synchronized.
        //
        private Deque<CreateItem> workList = new ArrayDeque<CreateItem>();

        //
        // The name of the file system in which the tree is to be built.
        //
        private final String fsName;

        //
        // How deep the tree is to be beneath its root.  The root is level 0,
        // the final layer of directories appears at the indicated depth (so
        // that when the files in the lowest layer of directories are
        // considered as well, the tree has depth+1 layers).
        //
        private final int depth;
        //
        // How many subdirectories or files are to appear at as children of
        // each interior node.
        //
        private final int fanout;
        //
        // The number of directories at the deepest (directory) layer.  Since
        // a worker arriving at a directory is expected to fully populate that
        // directory with its direct children, this value determines how many
        // workers need to be waited for before creation is complete.
        //
        // This value is fanout ** depth.
        //
        private final int leafClumps;
        //
        // When leafClumpsClaimed reached leafClumps, all work has been
        // claimed, and workers should stop attempting to get more work.
        //
        private int leafClumpsClaimed = 0;

        //
        // Records whether or not any of the worker tasks has failed in
        // building its part of the tree.  Worker tasks check this value to
        // see whether they, in turn, should give up.
        //
        private boolean failed = false;

        public TreeBuilder(String fsName, int depth, int fanout) {
            this.fsName = fsName;

            if (depth <= 0 || fanout <= 0)
                throw new IllegalArgumentException(
                    "depth and fanout must be positive values");
            this.depth = depth;
            this.fanout = fanout;
            this.leafClumps = (int)Math.pow(fanout, depth);
            if (this.leafClumps <= 0)
                throw new IllegalArgumentException("tree size too large");
        }

        /**
         * Turns the given number of clients loose on building the tree.
         *
         * @return  a {@code Profile} containing the clients' work times
         *
         * @throws IllegalArgumentException
         *         if more clients are requested than have been created
         */
        public Profile build(int nClients) throws InterruptedException {
            final Map<Integer, Client> clients = CelesteFsProfiler.this.clients;
            if (nClients > clients.keySet().size()) {
                throw new IllegalArgumentException(
                    "not enough clients available");
            }

            //
            // Initialize the work list with a single item.
            //
            this.workList.add(new CreateItem(new PathName("/"), this.fanout, 0));

            //
            // Give each client a task to work on and let the overall thread
            // pool executor schedule them.
            //
            CountDownLatch latch = new CountDownLatch(nClients);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();
            for (int i = 0; i < nClients; i++) {
                Client client = clients.get(i);
                //System.err.printf("build: adding task for %s%n", client);
                TreeBuilderTask task = new TreeBuilderTask(client, latch);
                tasks.add(CelesteFsProfiler.this.pool.submit(task));
            }

            //
            // Wait for the tasks to complete and then gather their results.
            //
            //System.err.printf("build: waiting at latch%n");
            latch.await();
            //System.err.printf("build: past latch%n");
            Profile profile = new Profile();

            for (Future<Long> t : tasks) {
                try {
                    profile.add(t.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            if (CelesteFsProfiler.this.verbose) {
                System.err.printf("%s%n", profile.report());
            }

            return profile;
        }

        private void setFailed(boolean failed) {
            synchronized (this) {
                this.failed = failed;
            }
        }

        private boolean isFailed() {
            synchronized (this) {
                return this.failed;
            }
        }

        private String generateRandomName() {
            return CelesteFsProfiler.this.randomFileName(
                CelesteFsProfiler.fileNameLength);
        }

        private void createFile(Client client, PathName path) throws
                FileException.BadVersion,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.Exists,
                FileException.FileSystemNotFound,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotDirectory,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            //System.err.printf("creating file %s%n", path);
            final CelesteFileSystem fs = client.getFileSystem(this.fsName);
            fs.createFile(path, CelesteFsProfiler.this.attributes);
            synchronized (client.paths) {
                client.paths.add(path);
            }
        }

        private void createDirectory(Client client, PathName path) throws
                FileException.BadVersion,
                FileException.Exists,
                FileException.CapacityExceeded,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.FileSystemNotFound,
                FileException.IOException,
                FileException.InvalidName,
                FileException.Locked,
                FileException.NotDirectory,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.RetriesExceeded,
                FileException.Runtime,
                FileException.ValidationFailed {
            //System.err.printf("%s creating directory /%s%s%n",
            //    Thread.currentThread().getName(), this.fsName, path);
            final CelesteFileSystem fs = client.getFileSystem(this.fsName);
            if (fs == null)
                throw new IllegalStateException("no file system!");
            fs.createDirectory(path, CelesteFsProfiler.this.attributes, null);
            synchronized (client.paths) {
                client.paths.add(path);
            }
        }
    }

    //
    // Support class for maintaining a work list of files and directories to
    // create when building a complete directory subtree.
    //
    private final static class CreateItem {
        /**
         * The name of the directory in which to create the file or directory
         * this item describes.
         */
        public final PathName basePath;

        /**
         * The number of files or directories to create as direct children of
         * {@code basePath}.
         */
        public final int numItems;

        /**
         * The depth in the tree that {@code basePath} is to be considered to
         * have.
         */
        public final int basePathDepth;

        public CreateItem(PathName basePath, int numItems,
                int basePathDepth) {
            this.basePath = basePath;
            this.numItems = numItems;
            this.basePathDepth = basePathDepth;
        }

        @Override
        public String toString() {
            return String.format(
                "CreateItem[path=%s, numItems=%d, basePathDepth=%d]",
                basePath, numItems, basePathDepth);
        }
    }

    //
    // XXX: tree creation to do:
    //
    //      Write a method to call Client.ensureFileSystems() and then to
    //      create a TreeBuilder and call its build() method.
    //
    //      Add command line processing to call the method above.
    //

    /**
     * Measure the creation-time of a Credential.
     */
    //
    // XXX: This class isn't useful as a file-system level test, but it's
    //      still needed as part of creating a client.  It ought to be
    //      refactored to isolate the part that's actually needed to create a
    //      client.
    //
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

                CelesteAPI proxy = null;
                try {
                    proxy = this.client.getAndRemove(this.client.getAddress());
                    ReadProfileOperation operation = new ReadProfileOperation(credentialId);

                    ResponseMessage result = proxy.readCredential(operation);
                    client.setCredential(result.get(Profile_.class));
                } catch (CelesteException.NotFoundException notFound) {
                    client.setCredential(new Profile_(client.getName(), client.credentialPassword.toCharArray()));
                    NewCredentialOperation operation = new NewCredentialOperation(client.getCredential().getObjectId(), BeehiveObjectId.ZERO, this.replicationParams);
                    Credential.Signature signature = client.getCredential().sign(client.credentialPassword.toCharArray(), operation.getId());
                    try {
                        proxy.newCredential(operation, signature, client.getCredential());
                    } catch (Exception e) {
                        System.err.println("failed to create credential for " + client.getName());
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    this.client.addAndEvictOld(this.client.getAddress(), proxy);
                }
                return (System.currentTimeMillis() - startTime);
            } finally {
                this.countDown.countDown();
            }
        }
    }

//    /**
//     * Measure the time it takes to inspect a file.
//     */
//    //
//    // XXX: Ought to become a test to measure attribute fetching time.
//    //
//    public class CelesteInspectFile implements Callable<Long> {
//        private Client client;
//        private CountDownLatch countDown;
//        private int batch;
//        private int nOperations;
//
//        public CelesteInspectFile(CountDownLatch countDown, Client client, int nOperations, int batch) {
//            this.client = client;
//            this.countDown = countDown;
//            this.batch = batch;
//            this.nOperations = nOperations;
//        }
//
//        /**
//         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
//         * and return the average number of milliseconds per operation.
//         */
//        //
//        // XXX: Assumes that there's a pool of files with known names to
//        //      inspect.
//        //
//        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
//        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
//        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
//        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException {
//
//            InspectFileOperation operation = null;
//
//            long operationTime;
//            long cummulativeTime = 0;
//
//            BeehiveObjectId vObjectId = null;
//
//            CelesteAPI proxy = null;
//            try {
//                proxy = this.client.getAndRemove(this.client.getAddress());
//                long startTime = System.currentTimeMillis();
//
//                for (int i = 0; i < this.nOperations; i++) {
//                    BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(i).getBytes());
//
//                    operation = new InspectFileOperation(
//                        new FileIdentifier(this.client.nameSpaces.get(0), fileId), (vObjectId == null) ? BeehiveObjectId.ZERO : vObjectId);
//                    operationTime = System.currentTimeMillis();
//
//                    ResponseMessage reply = proxy.inspectFile(operation);
//
//                    cummulativeTime += (System.currentTimeMillis() - operationTime);
//                }
//                return (System.currentTimeMillis() - startTime) / this.nOperations;
//            } finally {
//                this.countDown.countDown();
//                this.client.addAndEvictOld(this.client.getAddress(), proxy);
//            }
//        }
//    }

    /**
     * Measure the creation-time of a NameSpace.
     */
    //
    // XXX: This class isn't useful as a file-system level test, but it's
    //      still needed as part of creating a client.  It ought to be
    //      refactored to isolate the part that's actually needed to create a
    //      client.
    //
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
         * Make sure that a name space exists for this client, returning the
         * time taken to create it (or to verify that it already exists).
         *
         * Note that the name space's name is a function of the client name.
         */
        public Long call() throws FileException.CredentialProblem {
            try {
                long startTime = System.currentTimeMillis();

                CelesteFsProfiler.this.ensureNameSpace(
                    this.client,
                    client.getName() + "ns",
                    client.credentialPassword,
                    this.replicationParams);

                return System.currentTimeMillis() - startTime;
            } finally {
                this.countDown.countDown();
            }
        }
    }

//    /**
//     * Measure the time it takes to read a file.
//     */
//    //
//    // XXX: Needs a pool of files to read from.  And those files need to hold
//    //      non-trivial (and known) data.
//    //
//    public class CelesteReadFile implements Callable<Long> {
//        private Client client;
//        private CountDownLatch countDown;
//        private int batch;
//        private int nOperations;
//        private int size;
//
//        public CelesteReadFile(CountDownLatch countDown, Client client, int nOperations, int generation, int size) {
//            this.client = client;
//            this.countDown = countDown;
//            this.batch = generation;
//            this.nOperations = nOperations;
//            this.size = size;
//        }
//
//        /**
//         * Perform {@code count} iterations of the Celeste {@link ProbeOperation}
//         * and return the average number of milliseconds per operation.
//         */
//        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
//        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
//        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
//        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.OutOfDateException, CelesteException.FileLocked {
//
//            ReadFileOperation operation = null;
//
//            long operationTime;
//            long cummulativeTime = 0;
//
//            BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(0).getBytes());
//            FileIdentifier fileIdentifier = new FileIdentifier(this.client.nameSpaces.get(0), fileId);
//
//            operationTime = System.currentTimeMillis();
//
//            CelesteAPI proxy = null;
//            try {
//                proxy = this.client.getAndRemove(this.client.getAddress());
//                long startTime = System.currentTimeMillis();
//
//                for (int i = 0; i < this.nOperations; i++) {
//
//                    operation = new ReadFileOperation(fileIdentifier, this.client.credential.getObjectId(), 0L, -1);
//
//                    Credential.Signature signature =
//                        this.client.credential.sign("passphrase".toCharArray(), operation.getId());
//
//                    operationTime = System.currentTimeMillis();
//
//                    ResponseMessage reply = proxy.readFile(operation, signature);
//
//                    cummulativeTime += (System.currentTimeMillis() - operationTime);
//                }
//                return (System.currentTimeMillis() - startTime) / this.nOperations;
//            } finally {
//                this.countDown.countDown();
//                this.client.addAndEvictOld(this.client.getAddress(), proxy);
//            }
//        }
//    }

//    /**
//     * Measure the time it takes to write a file.
//     */
//    // XXX: Needs a pool of files to write to.
//    //
//    public class CelesteWriteFile implements Callable<Long> {
//        private Client client;
//        private CountDownLatch countDown;
//        private int batch;
//        private int nOperations;
//        private int size;
//
//        public CelesteWriteFile(CountDownLatch countDown, Client client, int nOperations, int generation, int size) {
//            this.client = client;
//            this.countDown = countDown;
//            this.batch = generation;
//            this.nOperations = nOperations;
//            this.size = size;
//        }
//
//        /**
//         * Perform {@code count} iterations of the Celeste {@link WriteOperation}
//         * and return the average number of milliseconds per operation.
//         */
//        public Long call() throws IOException, ClassNotFoundException, Credential.Exception,
//        CelesteException.CredentialException, CelesteException.RuntimeException, CelesteException.AlreadyExistsException,
//        CelesteException.NoSpaceException, CelesteException.NotFoundException, CelesteException.VerificationException,
//        CelesteException.DeletedException, CelesteException.IllegalParameterException, CelesteException.AccessControlException, CelesteException.OutOfDateException, CelesteException.FileLocked {
//
//            WriteFileOperation operation = null;
//            ByteBuffer data = ByteBuffer.allocate(this.size);
//
//            long operationTime;
//            long cumulativeTime = 0;
//
//            ClientMetaData clientMetaData = new ClientMetaData();
//
//            BeehiveObjectId fileId = new BeehiveObjectId(Integer.toString(this.batch).getBytes()).add(Integer.toString(0).getBytes());
//
//            BeehiveObjectId vObjectId = null;
//            InspectFileOperation inspectOperation = new InspectFileOperation(
//                new FileIdentifier(this.client.nameSpaces.get(0), fileId), (vObjectId == null) ? BeehiveObjectId.ZERO : vObjectId);
//            operationTime = System.currentTimeMillis();
//
//            CelesteAPI proxy = null;
//            try {
//                proxy = this.client.getAndRemove(this.client.getAddress());
//                ResponseMessage reply = proxy.inspectFile(inspectOperation);
//
//                vObjectId = reply.getMetadata().getPropertyAsObjectId(CelesteAPI.VOBJECTID_NAME, null);
//
//                try {
//                    long startTime = System.currentTimeMillis();
//
//                    //
//                    // Do repeated writes to the same range of the same file,
//                    // so that existing data is replaced for each write after
//                    // the first.
//                    //
//                    for (int i = 0; i < this.nOperations; i++) {
//
//                        operation = new WriteFileOperation(new FileIdentifier(this.client.nameSpaces.get(0), fileId),
//                            this.client.credential.getObjectId(),
//                            vObjectId,
//                            clientMetaData,
//                            0L, this.size);
//
//                        Credential.Signature signature =
//                            this.client.credential.sign("passphrase".toCharArray(), operation.getId(), clientMetaData.getId());
//
//                        operationTime = System.currentTimeMillis();
//
//                        OrderedProperties props = proxy.writeFile(operation, signature, data);
//                        vObjectId = props.getPropertyAsObjectId(CelesteAPI.VOBJECTID_NAME, null);
//
//                        cumulativeTime += (System.currentTimeMillis() - operationTime);
//                    }
//                    return (System.currentTimeMillis() - startTime) / this.nOperations;
//                } finally {
//                    this.countDown.countDown();
//                }
//            } finally {
//                this.client.addAndEvictOld(this.client.getAddress(), proxy);
//            }
//        }
//    }

    /**
     * Represents the clients that perform profiling actions.  Each instance
     * holds the state for a particular client.
     */
    public static class Client {
        public Profile_ credential;
        public String credentialPassword;
        private final CelesteProxy.Cache proxyCache;
        private final InetSocketAddress address;

        public String name;
        //
        // Each client knows about a collection of name spaces and can
        // furthermore gain access to file systems based on those name spaces.
        //
        public final List<BeehiveObjectId> nameSpaces;
        public final Map<String, CelesteFileSystem> fileSystems =
            new HashMap<String, CelesteFileSystem>();
        //
        // A set of path names for the files and directories this client has
        // created.  (Set in the create-trees test; not necessarily available
        // if that test hasn't been run.)
        //
        public Set<PathName> paths = new HashSet<PathName>();

        public Client(String name, String password, InetSocketAddress address,
                CelesteProxy.Cache proxyCache) {
            if (proxyCache == null)
                throw new IllegalArgumentException(
                    "proxyCache parameter is null");
            this.name = name;
            this.credentialPassword = password;
            this.address = address;
            this.proxyCache = proxyCache;
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

        public InetSocketAddress getAddress() {
            return this.address;
        }

        public CelesteProxy.Cache getProxyCache() {
            return this.proxyCache;
        }

        public CelesteFileSystem getFileSystem(String fsName) {
            synchronized (this.fileSystems) {
                return this.fileSystems.get(fsName);
            }
        }

        /**
         * Ensure that this client has a file system handle for every name
         * space attached to it.  Assumes that the name spaces have already
         * been created.
         */
        //
        // XXX: Would doing this work by setting up future tasks and
        //      collecting timings provide interesting information?
        //
        public void ensureFileSystems() throws
                FileException.BadVersion,
                FileException.CelesteFailed,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.DirectoryCorrupted,
                FileException.IOException,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed {
            synchronized (this.fileSystems) {
                for (Map.Entry<String, CelesteFileSystem> entry : fileSystems.entrySet()) {
                    if (entry.getValue() != null) {
                        //
                        // We already have a handle for the file system
                        // associated with this name space.
                        //
                        continue;
                    }
                    String nameSpace = entry.getKey();
                    CelesteFileSystem fs = new CelesteFileSystem(this.address,
                        this.proxyCache, nameSpace, this.credential.getName(),
                        this.credentialPassword);
                    this.fileSystems.put(nameSpace, fs);
                }
            }
        }

        //
        // Impedance matching variants of the CelesteProxy.Cache methods.
        //

        public CelesteAPI getAndRemove(InetSocketAddress address) throws
                IOException {
            //
            // The only way CelesteProxy.Cache's getAndRemove() method should
            // throw an exception is when it needs to call its factory method
            // to create a new proxy.  But the factory (although it must be
            // specified to throw Exception) actually only throws IOException.
            //
            try {
                return this.proxyCache.getAndRemove(address);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                System.err.printf("unexpected Exception:%n");
                e.printStackTrace(System.err);
                throw new IOException(e);
            }
        }

        //
        // Present for symmetry with getAndRemove() above.
        //
        public void addAndEvictOld(InetSocketAddress address,
                CelesteAPI proxy) {
            this.proxyCache.addAndEvictOld(address, proxy);
        }

        @Override
        public String toString() {
            return String.format("Client %s: credential=%s address=%s nameSpace=%s",
                this.getName(), this.credential, this.address, this.nameSpaces);
        }
    }

    public static class Profile {
        private static final long serialVersionUID = 1L;

        /**
         * Emits the profiling results contained in {@code list} to {@code
         * out}, preceding the results proper with a header specified by
         * {@code format} and {@code args}.
         *
         * <p>
         *
         * Each entry in {@code list} is expected to hold the results for runs
         * with a given number of concurrent clients, with the number of
         * clients increasing from one entry to the next.
         *
         * </p>
         */
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

        /**
         * Adds a single result to this profile.
         */
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

        /**
         * Returns a string summarizing the results accumulated for this
         * profile instance.
         */
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

    //
    // Generate a random file name of the given length from fileNameAlphabet.
    // length and fileNameAlphabet are constrained to allow the name to be
    // used in common non-Celeste file systems (so that files can be copied
    // with their names retained).
    //
    private String randomFileName(int length) {
        if (length <= 0 || length > 255)
            throw new IllegalArgumentException("length out of range");
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            result.append(fileNameAlphabet.charAt(
                randVar.nextInt(fileNameAlphabet.length())));
        return result.toString();
    }

    private final static String fileNameAlphabet =
        "abcdefghijklmnopqrstuvwxyz_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    //
    // Using length 20 names seems excessively conservative.  Back off to 10.
    //
    private static int fileNameLength = 10;
    private final static Random randVar = new Random();

    // This value needs to be <= CelesteClientDaemon.MaxClients.
    public static int maximumClients = 200;

    //
    // Attributes to apply to files and directories created by as part of this
    // class's profiling runs.
    //
    private static final long timeToLive = Time.minutesInSeconds(60);
    private static final long deleteTimeToLive = Time.minutesInSeconds(30);
    private static final int blockObjectSize = 8*1024*1024;
    private static final String replicationParams =
        "AObject.Replication.Store=2;" +
        "VObject.Replication.Store=2;" +
        "BObject.Replication.Store=2;" +
        "AObjectVersionMap.Params=1,1;" +
        "Credential.Replication.Store=2";

    private final CelesteACL defaultACL = new CelesteACL(new CelesteACL.CelesteACE(
        new CelesteACL.AllMatcher(),
            EnumSet.of(
                CelesteACL.CelesteOps.deleteFile,
                CelesteACL.CelesteOps.inspectFile,
                CelesteACL.CelesteOps.lockFile,
                CelesteACL.CelesteOps.readFile,
                CelesteACL.CelesteOps.setUserAndGroup,
                CelesteACL.CelesteOps.setACL,
                CelesteACL.CelesteOps.setFileLength,
                CelesteACL.CelesteOps.writeFile),
            ACL.Disposition.grant
        ));
    private final OrderedProperties attributes;

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

        try {
            CelesteFsProfiler profiler =
                new CelesteFsProfiler(verbose, celesteAddress, CelesteFsProfiler.maximumClients);
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
                        } catch (FileException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        CelesteFsProfiler.process(profiler, options);
                    } catch (EmptyStackException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (FileException e) {
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

    public static void process(CelesteFsProfiler profiler, Stack<String> options) throws
            Credential.Exception, InterruptedException, ExecutionException,
            IOException, FileException {
        long celesteTimeOutMillis = 0;

        //
        // Run each test specified in the options stack.
        //
        while (!options.empty()) {
            String option = options.pop();
            if (option.startsWith("clients")) {
                int nClients = Integer.parseInt(options.pop());
                profiler.ensureSufficientClients(nClients);
            } else if (option.startsWith("create-trees")) {
                //
                // N.B.  Since this clause checks for a superstring of what
                // the next clause looks for, it must precede that one.
                //
                String[] params = option.split("[:, ]+", 7);
                if (params.length != 7) {
                    System.err.printf("Usage: create-trees:initial,max,step,depth,fanout,output%n");
                    continue;
                }
                int initial = Integer.parseInt(params[1]);
                int nClients = Integer.parseInt(params[2]);
                int step = Integer.parseInt(params[3]);
                int depth = Integer.parseInt(params[4]);
                int fanout = Integer.parseInt(params[5]);
                OutputStream output = params[6].equals("-") ? System.out : new FileOutputStream(params[6]);

                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.createTrees(initial, nClients, step, depth, fanout);
                Profile.print(output, results,
                    "# Create depth %d fanout %d trees: %d to %d by %d clients.%n# Elapsed time: %s",
                    depth, fanout, initial, nClients, step,
                    Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
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

                String replicationParams =
                    "AObject.Replication.Store=2;" +
                    "VObject.Replication.Store=2;" +
                    "BObject.Replication.Store=2;" +
                    "AObjectVersionMap.Params=1,1";

                long startTime = System.currentTimeMillis();
                List<Profile> results = profiler.createFiles(initial, nClients, step, nFiles, replicationParams, celesteTimeOutMillis);
                Profile.print(output,
                    results,
                    "# Create files: %d to %d by %d clients each performing %d operations.%n# Elapsed time: %s",
                    initial, nClients, step, nFiles, Time.formattedElapsedTime(System.currentTimeMillis() - startTime));
//            } else if (option.startsWith("inspect")) {
//                String[] params = option.split("[:, ]+", 6);
//                if (params.length != 6) {
//                    System.err.printf("Usage: inspect:initial,max,step,files,output%n");
//                    continue;
//                }
//                int initial = Integer.parseInt(params[1]);
//                int nClients = Integer.parseInt(params[2]);
//                int step = Integer.parseInt(params[3]);
//                int nFiles = Integer.parseInt(params[4]);
//                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);
//
//                Profile.print(output,
//                    profiler.inspectFiles(initial, nClients, step, nFiles, celesteTimeOutMillis),
//                    "# Inspect: %d to %d by %d clients each performing %d operations", initial, nClients, step, nFiles);
//            } else if (option.startsWith("write")) {
//                String[] params = option.split("[:, ]+", 6);
//                if (params.length != 6) {
//                    System.err.printf("Usage: write:initial,max,step,files,output%n");
//                    continue;
//                }
//                int initial = Integer.parseInt(params[1]);
//                int nClients = Integer.parseInt(params[2]);
//                int step = Integer.parseInt(params[3]);
//                int nFiles = Integer.parseInt(params[4]);
//                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);
//
//                Profile.print(output,
//                    profiler.writeFiles(initial, nClients, step, nFiles, celesteTimeOutMillis),
//                    "# Write: %d to %d by %d clients each performing %d operations", initial, nClients, step, nFiles);
//            } else if (option.startsWith("read")) {
//                String[] params = option.split("[:, ]+", 6);
//                if (params.length != 6) {
//                    System.err.printf("Usage: read:initial,max,step,files%n");
//                    continue;
//                }
//                int initial = Integer.parseInt(params[1]);
//                int nClients = Integer.parseInt(params[2]);
//                int step = Integer.parseInt(params[3]);
//                int nFiles = Integer.parseInt(params[4]);
//                OutputStream output = params[5].equals("-") ? System.out : new FileOutputStream(params[5]);
//
//                Profile.print(output,
//                    profiler.readFiles(initial, nClients, step, nFiles, celesteTimeOutMillis),
//                    "# Read: %d to %d by %d clients each performing %d operations", initial, nClients, step, nFiles);
            } else if (option.equals("exit")) {
                return;
            } else {
                System.out.printf("Unknown option %s%n", option);
                System.out.printf("create:<initial>,<final>,<step>,<nFiles>,<output>%n");
//                System.out.printf("write:<initial>,<final>,<step>,<nFiles>,<output>%n");
//                System.out.printf("read:<initial>,<final>,<step>%n");
//                System.out.printf("inspect:<initial>,<final>,<step>,<nFiles>,<output>%n");
                System.out.flush();
            }
        }
    }

    final InetSocketAddress address;
    public final CelesteProxy.Cache proxyCache;
    boolean verbose;

    Map<Integer, Client> clients;
    ThreadPoolExecutor pool;

    public CelesteFsProfiler(boolean verbose, String celesteAddress, int maximumClientCount) throws IOException, Credential.Exception {
        this.verbose = verbose;
        this.clients = new HashMap<Integer,Client>();
        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maximumClientCount);
        this.pool.prestartAllCoreThreads();

        this.address = CelesteFsProfiler.makeAddress(celesteAddress);
        //
        // XXX: Giving the cache sufficient capacity to accommodate the entire
        //      maximum client population at once is almost certainly
        //      overkill.
        //
        // XXX: Need to get the timeout value from our caller or turn it into
        //      a field of CelesteFsProfiler and get it from there.
        //
        this.proxyCache = new CelesteProxy.Cache(maximumClientCount, 0);

        //
        // Mirror the attributes used in the base Celeste-level test as
        // closely as possible.  (But note that that test doesn't set
        // content type or delete time to live.)
        //
        // XXX: We need to set an ACL that permits "other" write access to
        //      directories.  Otherwise, all Client instances will have to
        //      share the same identity.
        //
        this.attributes = new OrderedProperties();
        this.attributes.put(BLOCK_SIZE_NAME, Integer.toString(CelesteFsProfiler.blockObjectSize));
        this.attributes.put(CONTENT_TYPE_NAME, "text/plain");
        this.attributes.put(DELETION_TIME_TO_LIVE_NAME,
            Long.toString(CelesteFsProfiler.deleteTimeToLive));
        this.attributes.put(REPLICATION_PARAMETERS_NAME, CelesteFsProfiler.replicationParams);
        this.attributes.put(SIGN_MODIFICATIONS_NAME, Boolean.toString(false));
        this.attributes.put(TIME_TO_LIVE_NAME, Long.toString(CelesteFsProfiler.timeToLive));
        this.attributes.put(ACL_NAME,
            CelesteFsProfiler.this.defaultACL.toEncodedString());
    }

    public List<Profile> createTrees(int initial, int nClients, int step, int depth, int fanout)
        throws
            InterruptedException,
            ExecutionException,
            IOException,
            Credential.Exception,
            FileException {
        if (initial <= 0 || nClients < initial || step <= 0)
            throw new IllegalArgumentException(
                "client arguments must specify well-formed sequence");
        //
        // XXX: Clean this up!  nameSpaceNames and indexednameSpaceNames
        //      shouldn't both be necessary.
        //
        Set<String> nameSpaceNames = new HashSet<String>();
        Map<Integer, String> indexedNameSpaceNames = new HashMap<Integer, String>();

        //
        // We'll create a distinct file tree for each value in the sequence
        // given by the <initial, nClients, step> triple.  We choose to house
        // each of these trees in its own file system, and each of them needs
        // its own name space.
        //
        for (int i = initial; i <= nClients; i += step) {
            final String name = String.format("name-space-for-tree-%d", i);
            nameSpaceNames.add(name);
            indexedNameSpaceNames.put(i, name);
        }
        Map<String, BeehiveObjectId> nameSpaceMap = this.nameSpaces(nameSpaceNames);

        //
        // Create the clients and augment each of them with the name spaces
        // they'll need.
        //
        this.ensureSufficientClients(nClients);
        this.credentials(nClients);
        for (Client client : this.clients.values()) {
            for (Map.Entry<String, BeehiveObjectId> entry : nameSpaceMap.entrySet()) {
                //
                // XXX:  Shouldn't need both nameSpaces and fileSystems.  Clean
                //      this up!
                //
                client.nameSpaces.add(entry.getValue());
                client.fileSystems.put(entry.getKey(), null);
            }
            client.ensureFileSystems();
        }

        //
        // Now do the profiling runs themselves.
        //
        List<Profile> result = new LinkedList<Profile>();
        for (int i = initial; i <= nClients; i += step) {
            final String name = indexedNameSpaceNames.get(i);
            //
            // If the file system that's to hold this tree doesn't yet exist,
            // create it, using the first client to do so.
            //
            Client client0 = this.clients.get(0);
            CelesteFileSystem fs = client0.fileSystems.get(name);
            if (fs == null) {
                throw new IllegalStateException("missing file system handle");
            }
            if (!fs.exists()) {
                //
                // Make sure that the file system's root directory has
                // universal read access.  That means supplying the
                // defaultCreationAttributes argument to newFileSystem().
                //
                fs.newFileSystem(null, CelesteFsProfiler.this.attributes, null);
            }

            TreeBuilder treeBuilder = new TreeBuilder(name, depth, fanout);
            Profile profile = treeBuilder.build(i);
            result.add(profile);
        }

        return result;
    }

    public List<Profile> createFiles(int initial, int nClients, int step, int nOperations, String replicationParams, long celesteTimeOutMillis)
        throws
            InterruptedException,
            ExecutionException,
            IOException,
            Credential.Exception,
            FileException {

        //
        // XXX: The nameSpaces() method does too much, both creating name
        //      spaces and measuring the time taken to create them.  The
        //      creation code should be factored out so that it it can be used
        //      without timing clutter when timings aren't needed.
        //
        nameSpaces(nClients);

        List<Profile> result = new LinkedList<Profile>();

        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
            CountDownLatch countDown = new CountDownLatch(concurrent);
            List<Future<Long>> tasks = new LinkedList<Future<Long>>();

            for (int clientId = 0; clientId < concurrent; clientId++) {
                //
                // Use concurrent as the "batch" argument to
                // CelesteCreateFile().
                //
                // XXX: We may be creating too many file systems.  Each trip
                //      through this inner loop creates a fresh name
                //      space/file system pair to hold the nOperations files
                //      that CelesteCreateFile.call() will create.  Would it
                //      be more realistic to have one such pair for each trip
                //      through the outer loop?  (That would stress the code
                //      in DirectoryImpl harder, since directories would grow
                //      larger.  It would also confront that code with
                //      concurrent operations from parallel activity induced
                //      by the tasks created in this inner loop.)
                //
                //      The ultimate answer is likely to be "both", since both
                //      variations give useful information on different
                //      aspects of file system activity.
                //
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

    public List<Profile> credentials(int nClients) throws InterruptedException, ExecutionException, IOException,
            Credential.Exception {

        this.ensureSufficientClients(nClients);

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
     * Create {@code numberOfClients} {@link Client} instances and connections to the
     * Celeste node identified by {@link #address}
     *
     * @param numberOfClients
     *
     * @throws Credential.Exception
     * @throws IOException
     */
    public void ensureSufficientClients(int numberOfClients) throws Credential.Exception, IOException {
        if (numberOfClients > Profiler.MaximumClients) {
            throw new IllegalArgumentException(String.format("maximum clients = %d", Profiler.MaximumClients));
        }
        if (this.clients.size() < numberOfClients) {
            for (int i = this.clients.size(); i < numberOfClients; i++) {
                Client client = new Client(
                    String.format("client-%s-%d", this.address, i),
                    "passphrase",
                    this.address,
                    this.proxyCache);
                this.clients.put(i, client);
            }
        }
    }

//    public List<Profile> inspectFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis)
//    throws InterruptedException, ExecutionException, IOException, Credential.Exception {
//
//        nameSpaces(nClients, celesteTimeOutMillis);
//        List<Profile> result = new LinkedList<Profile>();
//        // XXX Should the files be created or assumed to exist when this test is run?
//
////        System.out.printf("Inspect files: %d clients inspecting %d files%n", nClients, nOperations);
////        System.out.print(Profile.reportHeaderCSV());
//        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
//            CountDownLatch countDown = new CountDownLatch(concurrent);
//            List<Future<Long>> tasks = new LinkedList<Future<Long>>();
//
//            for (int clientId = 0; clientId < concurrent; clientId++) {
//                // Use concurrent as the generation argument to CelesteCreateFile()
//                tasks.add(this.pool.submit(new CelesteInspectFile(countDown, this.clients.get(clientId), nOperations, concurrent)));
//            }
//
//            countDown.await();
//            Profile profile = new Profile();
//            for (Future<Long> t : tasks) {
//                try {
//                    profile.add(t.get());
//                } catch (ExecutionException e) {
//                    e.printStackTrace();
//                }
//            }
//            result.add(profile);
//            if (this.verbose) {
//                System.err.printf("%s%n", profile.report());
//            }
//        }
//        return result;
//    }

    //
    // Create name spaces for each of the names given in nameSpaceNames.
    //
    // XXX: Do this asynchronously using futures.
    //
    public Map<String, BeehiveObjectId> nameSpaces(Set<String> nameSpaceNames)
            throws FileException.CredentialProblem{
        Map<String, BeehiveObjectId> result = new HashMap<String, BeehiveObjectId>();
        for (String name : nameSpaceNames) {
            BeehiveObjectId id = this.ensureNameSpace(this.proxyCache,
                this.address, name, "passphrase", this.replicationParams);
            result.put(name, id);
        }
        return result;
    }

    public List<Profile> nameSpaces(int nClients) throws
            InterruptedException, ExecutionException, IOException, Credential.Exception {

        //this.ensureSufficientCredentials(nClients);
        this.credentials(nClients);

//        System.out.printf("NameSpaces: %d namespaces%n", nClients);
//        System.out.print(Profile.reportHeaderCSV());

        CountDownLatch countDown = new CountDownLatch(nClients);
        List<Future<Long>> tasks = new LinkedList<Future<Long>>();

        //
        // Asynchronously decorate each client with a corresponding name
        // space.
        //
        for (Integer clientId : this.clients.keySet()) {
            tasks.add(this.pool.submit(new CelesteNameSpace(countDown, this.clients.get(clientId))));
        }

        //
        // Accumulate the name space creation times into profile.
        //
        countDown.await();
        List<Profile> result = new LinkedList<Profile>();
        Profile profile = new Profile();
        for (Future<Long> t : tasks) {
            profile.add(t.get());
        }
        result.add(profile);
        return result;
    }

//    public List<Profile> readFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis) throws InterruptedException, ExecutionException, IOException, Credential.Exception {
//
//        nameSpaces(nClients, celesteTimeOutMillis);
//        // XXX Should the files be created or assumed to exist when this test is run?
//
//        List<Profile> result = new LinkedList<Profile>();
//
//        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
//            CountDownLatch countDown = new CountDownLatch(concurrent);
//            List<Future<Long>> tasks = new LinkedList<Future<Long>>();
//
//            for (int clientId = 0; clientId < concurrent; clientId++) {
//                // Use concurrent as the generation argument to CelesteCreateFile()
//                tasks.add(this.pool.submit(new CelesteReadFile(countDown, this.clients.get(clientId), nOperations, concurrent, 100)));
//            }
//
//            countDown.await();
//            Profile profile = new Profile();
//            for (Future<Long> t : tasks) {
//                try {
//                    profile.add(t.get());
//                } catch (ExecutionException e) {
//                    e.printStackTrace();
//                }
//            }
//            result.add(profile);
//            if (this.verbose) {
//                System.err.printf("%s%n", profile.report());
//            }
//        }
//        return result;
//    }

//    public List<Profile> writeFiles(int initial, int nClients, int step, int nOperations, long celesteTimeOutMillis)
//    throws InterruptedException, ExecutionException, IOException, Credential.Exception {
//
//        nameSpaces(nClients, celesteTimeOutMillis);
//        // XXX Should the files be created or assumed to exist when this test is run?
//
//        List<Profile> result = new LinkedList<Profile>();
//
//        for (int concurrent = initial; concurrent <= nClients; concurrent += step) {
//            CountDownLatch countDown = new CountDownLatch(concurrent);
//            List<Future<Long>> tasks = new LinkedList<Future<Long>>();
//
//            for (int clientId = 0; clientId < concurrent; clientId++) {
//                // Use concurrent as the generation argument to CelesteCreateFile()
//                tasks.add(this.pool.submit(new CelesteWriteFile(countDown, this.clients.get(clientId), nOperations, concurrent, 100)));
//            }
//
//            countDown.await();
//            Profile profile = new Profile();
//            for (Future<Long> t : tasks) {
//                try {
//                    profile.add(t.get());
//                } catch (ExecutionException e) {
//                    e.printStackTrace();
//                }
//            }
//            result.add(profile);
//            if (this.verbose) {
//                System.err.printf("%s%n", profile.report());
//            }
//        }
//        return result;
//    }

    private BeehiveObjectId ensureNameSpace(CelesteProxy.Cache cache,
            InetSocketAddress addr, String name, String password,
            String replicationParameters)
        throws
            FileException.CredentialProblem {
        CelesteAPI proxy = null;
        try {
            //
            // XXX: Need specializations of getAndRemove() and addAndEvictOld()
            //      that live at the CelesteFsProfiler level.  (Can probably
            //      use them to replace the ones I added to Client.)
            //
            proxy = cache.getAndRemove(addr);
            BeehiveObjectId nameSpaceId = new BeehiveObjectId(name.getBytes());
            ReadProfileOperation operation = new ReadProfileOperation(nameSpaceId);
            proxy.readCredential(operation);
            return nameSpaceId;
        } catch (CelesteException.NotFoundException notFound) {
            //
            // The name space doesn't yet exist and must be created.
            //
            try {
                Profile_ nameSpaceCredential = new Profile_(
                    name, password.toCharArray());
                NewNameSpaceOperation operation = new NewNameSpaceOperation(
                    nameSpaceCredential.getObjectId(), BeehiveObjectId.ZERO,
                    replicationParameters);
                Credential.Signature signature = nameSpaceCredential.sign(
                    password.toCharArray(), operation.getId());
                proxy.newNameSpace(operation, signature, nameSpaceCredential);
                return nameSpaceCredential.getObjectId();
            } catch (Exception e) {
                System.err.printf("failed to create name space for %s%n", name);
                e.printStackTrace(System.err);
                throw new FileException.CredentialProblem(e);
            }
        } catch (CelesteException.CredentialException e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.AccessControlException e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.RuntimeException e) {
            throw new FileException.CredentialProblem(e);
        } catch (IOException e) {
            throw new FileException.CredentialProblem(e);
        } catch (ClassNotFoundException e) {
            throw new FileException.CredentialProblem(e);
        } catch (Exception e) {
            //
            // XXX: This case should go away when addAndRemove() is suitably
            //      specialized.
            //
            throw new FileException.CredentialProblem(e);
        } finally {
            cache.addAndEvictOld(addr, proxy);
        }
    }

    //
    // Ensure that the given name space exists.
    //
    // XXX: Need to factor this method to remove client to extract one that
    //      isn't tied to a specific client.  (The name spaces for the
    //      create-tree file systems aren't tied to specific clients.)
    //
    private void ensureNameSpace(Client client, String name, String password,
            String replicationParameters) throws FileException.CredentialProblem {
        BeehiveObjectId id = this.ensureNameSpace(
            client.getProxyCache(), client.getAddress(), name, password,
            replicationParameters);
        synchronized (client.fileSystems) {
            //
            // XXX: Error checking to see whether or not these entries
            // have already been added?
            //
            client.nameSpaces.add(id);
            //
            // The target for this map entry will be filled in later by
            // means of a call to ensureFileSystems(client).
            //
            client.fileSystems.put(name, null);
        }
    }
}
