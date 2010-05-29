/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.celeste.extension;

import java.io.Serializable;

import java.nio.ByteBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import sunlabs.asdf.functional.AbstractMapFunction;
import sunlabs.asdf.functional.AbstractReduceFunction;
import sunlabs.asdf.functional.MapFunction;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.node.BeehiveMessage;
import sunlabs.beehive.node.BeehiveNode;
import sunlabs.beehive.node.BeehiveObjectStore;
import sunlabs.beehive.node.object.BeehiveObjectHandler;
import sunlabs.beehive.node.object.ExtensibleObject;
import sunlabs.beehive.node.object.MutableObject;
import sunlabs.beehive.node.services.api.BeehiveExtension;
import sunlabs.beehive.util.ExtentBuffer;
import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.node.CelesteACL.CelesteOps;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.celeste.node.services.object.AnchorObject;
import sunlabs.celeste.node.services.object.BlockObject;
import sunlabs.celeste.node.services.object.BlockObjectHandler;
import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.celeste.node.services.object.VersionObjectHandler;
import sunlabs.celeste.node.services.object.VersionObject.BadManifestException;

/**
 * An example Word Count Beehive extension.
 * <p>
 * This class implements a simple count of white-space separated words of a
 * Celeste file and is an example of a Beehive extension - a programme supplied
 * by a client to be distributed and executed within the Beehive system.
 * The result is a list of the words found and the count of their occurrence.
 * </p>
 * <p>
 * <strike>In all cases, a Beehive extension class implements {@link BeehiveExtension}
 * interface, where {@code T} denotes the Java type of the value returned to
 * the client. Note that the return type {@code T} must implement the
 * {@link Serializable} interface.</strike>
 * </p>
 * <p>
 * The algorithm of actually counting words is currently flawed as it makes
 * no provisions for handling words that span {@link BlockObject} boundaries.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class WordCount implements ExtensibleObject.Extension<HashMap<String,Long>> {
    private final static long serialVersionUID = 1L;

    private CelesteNode node;
    private ExtensibleOperation operation;
    private ExtensibleObject.JarClassLoader classLoader;

    /**
     * This is the main WordCount driver class.
     * <p>
     * An instance of this class is created and invoked on the Celeste node that has received the run-extension operation from a client.
     * The client supplies an array of URLs to Java Jar files from which the extension will be loaded.
     * This class is in the first Jar file in the array.
     * </p>
     *
     * <p>
     * Once an object has been instantiated from this class, this instance's {@link #call} method is invoked to perform the whole operation.
     * The return value from the {@link #call} method is the return value of this extension.
     * </p>
     */
    public WordCount(ExtensibleObject.JarClassLoader classLoader, CelesteNode node, ExtensibleOperation operation) {
        this.classLoader = classLoader;
        this.node = node;
        this.operation = operation;
    }

    @SuppressWarnings("unchecked")
    public HashMap<String,Long> call()
    throws BeehiveObjectStore.DeletedObjectException, ClassNotFoundException, BeehiveObjectStore.NotFoundException, BeehiveObjectStore.ObjectExistenceException,
           BeehiveObjectStore.InvalidObjectException, MutableObject.InsufficientResourcesException, MutableObject.NotFoundException,
           MutableObject.ProtocolException, BeehiveMessage.RemoteException {

        BeehiveObjectId vObjectId = this.operation.getVObjectId();
        
        // An arbitrary array of Strings is in the ExtensionOperation instance.
        // This array is created by the original creator of the ExtensionOperation and can be used in here to do different things. 
        for (String arg : this.operation.getArgs()) {
        	System.out.printf("%s%n", arg);
        }

        AnchorObject anchorObjectHandler = (AnchorObject) this.node.getService("sunlabs.celeste.node.services.object.AnchorObjectHandler");
        VersionObject versionObjectHandler = this.node.getService(VersionObject.class, "sunlabs.celeste.node.services.object.VersionObjectHandler");
        AnchorObject.Object aObject = anchorObjectHandler.retrieve(operation.getFileIdentifier());

        // If the object-id of the VersionObject is not supplied (signaled by being null or all zeros),
        // we must fetch the current VersionObject object-id.
        if (vObjectId == null || vObjectId.equals(BeehiveObjectId.ZERO)) {
            AObjectVersionMapAPI aObjectVersionMap = (AObjectVersionMapAPI) this.node.getService("sunlabs.celeste.node.services.AObjectVersionService");
            VersionObject.Object.Reference vObjectReference =
                aObjectVersionMap.getValue(aObject.getObjectId(), aObject.getAObjectVersionMapParams()).getReference();
            vObjectId = vObjectReference.getObjectId();
        }

        ExtensibleObject.Operation.Request request = new ExtensibleObject.Operation.Request(WordCount.VObjectExtension.class, this.classLoader, this.operation);
        return versionObjectHandler.extension(HashMap.class, vObjectId, request);
    }

    /**
     * This class will be instantiated and invoked on a Beehive Node that is hosting a copy of the target {@link VersionObject}.
     * 
     */
    public static class VObjectExtension implements ExtensibleObject.Extension<HashMap<String,Long>> {
        private final static long serialVersionUID = 1L;

        private ExtensibleObject.JarClassLoader classLoader;
        private VersionObjectHandler handler;
        private BeehiveObjectId objectId;
        private ExtensibleOperation operation;
        private ExecutorService threadPool;

        public VObjectExtension(ExtensibleObject.JarClassLoader classLoader, ExtensibleOperation operation, BeehiveObjectId objectId, VersionObjectHandler handler) {
            this.classLoader = classLoader;
            this.handler = handler;
            this.objectId = objectId;
            this.operation = operation;
            // XXX We create the thread pool here, but really this should be
            // handed in as a parameter to the constructor from the object handler.
            this.threadPool = Executors.newFixedThreadPool(3);
        }

        public HashMap<String,Long> call() throws BeehiveObjectStore.ObjectExistenceException, BeehiveObjectStore.NotFoundException, ClassCastException {
            // Get the required VersionObject then iterate through the BlockObjects
            // asking each one for its word count.
            
            // Get the VersionObject locally.
            VersionObject.Object vObject = this.handler.getNode().getObjectStore().get(VersionObject.Object.class, this.objectId);

            // Perform access control check.
            boolean accessPermitted = vObject.checkAccess(this.operation.getClientId(), CelesteOps.readFile);
            if (!accessPermitted) {
                // Ignore this for now.
                // throw new BeehiveException.AccessDenied();
            }
            
            // Get all of the BlockObject.Object.References from the current VersionObject.
            // These are pointers to the BlockObjects that make up this file.
            SortedMap<Long,BlockObject.Object.Reference> bObjectMap;
            try {
                VersionObject.Manifest manifest = vObject.getManifest(0, vObject.getFileSize());
                bObjectMap = manifest.getManifest();
            } catch (BadManifestException e) {
                e.printStackTrace();
                throw new BeehiveObjectStore.NotFoundException(e);
            }

            BlockObject blockObjectHandler = (BlockObject) this.handler.getNode().getService(CelesteNode.OBJECT_PKG + ".BlockObjectHandler");

            try {
                MapFunction<BlockObject.Object.Reference,BObjectExtension.BObjectResult> applyToAll =
                    new WordCountMapFunction(this.classLoader, this.threadPool, this.handler.getNode(), blockObjectHandler, operation);
                try {
                    List<MapFunction.Result<BObjectExtension.BObjectResult>> a = applyToAll.map(bObjectMap.values());

                    WordCountReduceFunction reduce = new WordCountReduceFunction();

                    return reduce.reduce(a, new HashMap<String,Long>());
                } catch (Exception e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            } finally {
                this.threadPool.shutdown();
            }

            return null;
        }

        /**
         * Extend the {@link AbstractReduceFunction} class to simply tally the accumulated
         * results from each of the BlockObjects replying with their {@link BObjectExtension.BObjectResult} instances.
         * The result of this is a {@link HashMap} indexed by the words as Strings.
         */
        public static class WordCountReduceFunction extends AbstractReduceFunction<MapFunction.Result<BObjectExtension.BObjectResult>,HashMap<String,Long>> {
            @Override
            public HashMap<String,Long> function(HashMap<String,Long> accumulator, MapFunction.Result<BObjectExtension.BObjectResult> item) throws ExecutionException, InterruptedException {
                for (String word : item.get().wordCount.keySet()) {
                    Long count = accumulator.get(word);
                    if (count == null) {
                        count = item.get().wordCount.get(word);
                    } else {
                        count += item.get().wordCount.get(word);
                    }
                    accumulator.put(word, count);
                }
                return accumulator;
            }
        }

        /**
         * Extend the general {@link AbstractMapFunction} class to dispatch a
         * message to each {@link BlockObject} in the file to compute each {@code BlockObject}'s word-count.
         *
         * @see AbstractMapFunction
         * @see MapFunction
         */
        public static class WordCountMapFunction extends AbstractMapFunction<BlockObject.Object.Reference,BObjectExtension.BObjectResult> {
            private BeehiveNode node;
            private ExtensibleOperation operation;
            private BlockObject handler;
            private ExtensibleObject.JarClassLoader classLoader;

            public WordCountMapFunction(ExtensibleObject.JarClassLoader classLoader, ExecutorService executor, BeehiveNode node, BlockObject blockObjectHandler, ExtensibleOperation operation) {
                super(executor);
                this.node = node;
                this.handler = blockObjectHandler;
                this.operation = operation;
                this.classLoader = classLoader;
            }

            @Override
            public AbstractMapFunction<BlockObject.Object.Reference,BObjectExtension.BObjectResult>.Function newFunction(BlockObject.Object.Reference item) {
                return this.new Function(this.classLoader, this.handler, item);
            }

            /**
             *
             * @see AbstractMapFunction.Function
             */
            private class Function extends AbstractMapFunction<BlockObject.Object.Reference,BObjectExtension.BObjectResult>.Function {
                private BlockObject handler;
                private ExtensibleObject.JarClassLoader classLoader;

                public Function(ExtensibleObject.JarClassLoader classLoader, BlockObject blockObjectHandler, BlockObject.Object.Reference item) {
                    super(item);
                    this.handler = blockObjectHandler;
                    this.classLoader = classLoader;
                }

                @Override
                public BObjectExtension.BObjectResult function(BlockObject.Object.Reference bObjectReference) throws Exception {
                    BeehiveObjectId objectId = bObjectReference.getObjectId();
                    ExtensibleObject.Operation.Request request = new ExtensibleObject.Operation.Request(WordCount.BObjectExtension.class, this.classLoader, operation);
                    return this.handler.extension(BObjectExtension.BObjectResult.class, objectId, request);
                }
            }
        }
    }

    /**
     * Instances of this class process on a local {@link BlockObject}.
     */
    public static class BObjectExtension implements ExtensibleObject.Extension<BObjectExtension.BObjectResult> {
        private static final long serialVersionUID = 1L;

        /**
         * This is the result returned from each {@link BlockObject}'s evaluation of itself.
         * The result ultimately contains a {@link Map} of words (as String instances) to a
         * Java Long value of the number of occurrences of the word.
         */
        private static class BObjectResult implements Serializable {
            private static final long serialVersionUID = 1L;
            public boolean wordInitial;
            public long words;
            public boolean wordFinal;
            public Map<String,Long> wordCount;

            public BObjectResult(boolean wordInitial, long words, boolean wordFinal, Map<String,Long> wordCount) {
                this.wordInitial = wordInitial;
                this.words = words;
                this.wordFinal = wordFinal;
                this.wordCount = wordCount;
            }
        }

        private ExtensibleObject.JarClassLoader classLoader;
        private BeehiveObjectId objectId;
        private BeehiveObjectHandler handler;
        @SuppressWarnings("unused")
        private ExtensibleOperation operation;

        public BObjectExtension(ExtensibleObject.JarClassLoader classLoader, ExtensibleOperation operation, BeehiveObjectId objectId, BlockObjectHandler handler) {
            this.classLoader = classLoader;
            this.operation = operation;
            this.objectId = objectId;
            this.handler = handler;
        }

        public HashMap<String,Long> counter(HashMap<String,Long> wordMap, ByteBuffer eb) {
            byte b = 0;
            boolean inWhiteSpace = true;

            int start = 0;
            for (int i = 0; eb.remaining() > 0; i++) {
                b = eb.get();
                if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                    if (inWhiteSpace) {
                        // skip over white space
                    } else {
                        // transition from non-white space to white space (end of a word).
                        String word = new String(eb.array(), eb.arrayOffset() + start, i-start);
                        Long wordCount = wordMap.get(word);
                        if (wordCount == null) {
                            wordCount = new Long(1);
                        } else {
                            wordCount += 1;
                        }
                        wordMap.put(word, wordCount);
                        start = i;
                    }
                    inWhiteSpace = true;
                } else {
                    if (inWhiteSpace) {
                        // transition from white space to non-white space (start of a word)
                        start = i;
                    } else {
                        // skip over the word
                    }
                    inWhiteSpace = false;
                }
            }

            if (inWhiteSpace) {

            } else {
                String word = new String(eb.array(), eb.arrayOffset() + start, eb.limit() - start);
                Long wordCount = wordMap.get(word);
                if (wordCount == null) {
                    wordCount = new Long(1);
                } else {
                    wordCount += 1;
                }
                wordMap.put(word, wordCount);
            }

            return wordMap;
        }

        public BObjectExtension.BObjectResult call() throws BeehiveObjectStore.ObjectExistenceException, BeehiveObjectStore.NotFoundException {
            long startTime = System.currentTimeMillis();
            try {
                BlockObject.Object bObject = this.handler.getNode().getObjectStore().get(BlockObject.Object.class, this.objectId);
                if (bObject != null) {
                    HashMap<String,Long> wordMap = new HashMap<String,Long>();
                    for (ExtentBuffer eb : bObject.getDataAsExtentBufferMap().values()) {
                        this.counter(wordMap, eb.getByteBuffer());
                    }
                    return new BObjectResult(false, 0, false, wordMap);
                }
                throw new BeehiveObjectStore.ObjectExistenceException("BlockObject %s not found.", this.objectId);
            } finally {
                long elapsedTime = System.currentTimeMillis() - startTime;
                this.handler.getLogger().info("[%d ms] %s", elapsedTime, this.objectId.toString());
            }
        }
    }
}
