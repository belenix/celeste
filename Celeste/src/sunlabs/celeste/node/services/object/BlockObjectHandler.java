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

package sunlabs.celeste.node.services.object;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.functional.AbstractMapFunction;
import sunlabs.asdf.functional.MapFunction;
import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.object.ExtensibleObject;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractBeehiveObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.node.object.ReplicatableObject;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.PublishDaemon;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.util.BufferableExtent;
import sunlabs.titan.util.BufferableExtentImpl;
import sunlabs.titan.util.DOLRStatus;
import sunlabs.titan.util.ExtentBuffer;
import sunlabs.titan.util.ExtentBufferMap;
import sunlabs.titan.util.ExtentImpl;

/**
 * This class embodies all operations involving BlockObjects.
 */
public final class BlockObjectHandler extends AbstractObjectHandler implements BlockObject {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(BlockObjectHandler.class, BlockObjectHandler.serialVersionUID);

    private final static int replicationStore = 3;
    private final static int replicationCache = 3;

    /**
     * A BObject is a segment (Block) of data from a Celeste file.
     */
    public static class BObject extends AbstractBeehiveObject implements BlockObject.Object {
        private static final long serialVersionUID = 2L;

        /**
         * A {@code Reference} has an objectId, an offset, and a length.  The
         * objectId names a {@code BObject}, which in turn encodes file data
         * for the span denoted by that offset and length.  The offset and
         * length are packaged together as a {@code BufferableExtent}, but are
         * available individually through convenience methods.
         */
        public static class Reference implements BlockObject.Object.Reference {
            private final static long serialVersionUID = 1L;

            private final BufferableExtent extent;
            private final TitanGuid objectId;

            private Reference(BlockObject.Object bObject, long offset, TitanGuid objectId) {
                this.extent = new BufferableExtentImpl(offset, bObject.getBounds().getLength());
                this.objectId = objectId;
            }

            /**
             * Create a {@code BObject.Reference} from a formatted string
             * produced by {@code BObject.Reference.toString()}.
             *
             * @param reference the formatted representation to be
             *                  reconstituted
             */
            public Reference(String reference) {
                String[] tokens = reference.split("/");
                this.extent = new BufferableExtentImpl(Long.parseLong(tokens[0]), Integer.parseInt(tokens[1]));
                this.objectId = new TitanGuidImpl(tokens[2]);
            }

            //
            // XXX: Shouldn't the comparison (and hashCode) take BObject
            //      identity into account?
            //

            /**
             * Return true if the offset of this reference is equal to the
             * offset of the given reference.
             * XXX Who actually calls this method?
             */
            @Override
            public boolean equals(java.lang.Object other) {
                if (other == null)
                    return false;
                if (other == this)
                    return true;
                if (other instanceof BObject.Reference) {
                    BObject.Reference otherReference = (BObject.Reference) other;
                    if (this.extent.getStartOffset() == otherReference.extent.getStartOffset()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int hashCode() {
                return this.toString().hashCode();
            }

            public TitanGuid getObjectId() {
                return this.objectId;
            }

            /**
             * Get the span in the original file that the data in this {@code
             * BObject} covers.
             *
             * @return  this {@code BObject}'s bounds
             */
            public BufferableExtent getBounds() {
                return this.extent;
            }

            //
            // XXX: The next two methods are now expressible in terms of the
            //      one above; that is, they've been reduced to convenience
            //      methods.  Deprecate them?
            //

            /**
             * Get the byte offset in the original file where the data in this BObject starts.
             */
            public long getFileOffset() {
                return this.extent.getStartOffset();
            }

            /**
             * Get the length of the data segment in the original file that this BObject contains.
             */
            public int getLength() {
                return this.extent.getLength();
            }

            private static String slash = "/";

            @Override
            public String toString() {
                StringBuilder result = new StringBuilder()
                    .append(this.getFileOffset())
                    .append(slash)
                    .append(this.getLength())
                    .append(slash)
                    .append(this.objectId);
                return result.toString();
            }
        }

        //
        // The data associated with a given BObject, encapsulated as a
        // serializable object so that the BObject constructor can call the
        // superclass constructor that expects a serializable object.
        //
        private static class BObjectContents implements Serializable {
            private final static long serialVersionUID = 1L;

            //
            // The range within its file that this BObject covers.
            //
            public final BufferableExtent bounds;
            //
            // A collection of ExtentBuffers holding the BObject's data.
            //
            public final ExtentBufferMap data;

            public BObjectContents(BufferableExtent bounds,
                    ExtentBufferMap data) {
                if (bounds == null)
                    throw new IllegalArgumentException("bad bounds");
                if (data == null)
                    throw new IllegalArgumentException("bad data");
                //
                // Verify that data's overall extent does not extend beyond
                // that of bounds.
                //
                if (!data.isVacuous() && !bounds.contains(data)) {
                    //
                    // Use an ExtentImpl as a formatting aid to view the
                    // ExtentBufferMap simply as an Extent.
                    //
                    String msg = String.format(
                        "data's extent %s does not fit within bounds %s",
                        new ExtentImpl(data), bounds);
                    throw new IllegalArgumentException(msg);
                }

                this.bounds = bounds;
                this.data = data;
            }
        }

        private int replicationMinimum;

        private int replicationCache;

        private BObjectContents contents;

        //
        // Package visibility, so that unit tests can access it.
        //
        BObject(BufferableExtent bounds, ExtentBufferMap data, TitanObject.Metadata metadata, TitanGuid deleteTokenId, long timeToLive, ReplicationParameters replicationParams) {
            this(new BObject.BObjectContents(bounds, data), metadata, deleteTokenId, timeToLive);
            this.replicationMinimum = replicationParams.getAsInteger(BlockObject.Object.REPLICATIONPARAM_MIN_NAME, BlockObjectHandler.replicationStore);
            this.replicationCache = replicationParams.getAsInteger(BlockObject.Object.REPLICATIONPARAM_LOWWATER_NAME, BlockObjectHandler.replicationCache);
            this.setProperty(ObjectStore.METADATA_REPLICATION_STORE, this.replicationMinimum);
            this.setProperty(ObjectStore.METADATA_REPLICATION_LOWWATER, this.replicationCache);
        }

        private BObject(BObjectContents contents, TitanObject.Metadata metadata, TitanGuid deleteTokenId, long timeToLive) {
            super(BlockObjectHandler.class, deleteTokenId, timeToLive);
            this.contents = contents;
        }

        public BlockObject.Object.Reference makeReference(long offset, TitanGuid objectId) {
            return new BlockObjectHandler.BObject.Reference(this, offset, objectId);
        }

        public BufferableExtent getBounds() {
            return this.contents.bounds;
        }

        public ExtentBufferMap getDataAsExtentBufferMap() {
            return this.contents.data;
        }

        @Override
        public TitanGuid getDataId() {
            //
            // To avoid confusing two block objects for the same file that
            // have identical contents, but that appear in different places
            // within the file, the id generated here must include the
            // starting offset.
            //
            TitanGuid id = new TitanGuidImpl("".getBytes());
            id = id.add(this.getBounds().getStartOffset());
            for (ByteBuffer c : this.contents.data.getBuffers()) {
                id = id.add(c);
            }
            return id;
        }

        public int getReplicationMinimum() {
            return this.replicationMinimum;
        }

        public int getReplicationCache() {
            return this.replicationCache;
        }
        
        public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props) {
        	XHTML.Table.Body tbody = new XHTML.Table.Body();
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Data Start"), new XHTML.Table.Data(this.getBounds().getStartOffset())));
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Data Length"), new XHTML.Table.Data(this.getBounds().getLength())));
        	XHTML.Table table = new XHTML.Table(tbody);

            XHTML.Div result = (XHTML.Div) super.toXHTML(uri, props);
            result.add(new XHTML.Div(table).setClass("section").addClass("BlockObject"));

            return result;
        }

        public void delete(TitanGuid profferedDeleteToken, long timeToLive) throws BeehiveObjectStore.DeleteTokenException {
            this.contents = new BObject.BObjectContents(new BufferableExtentImpl(0L, 0), new ExtentBufferMap());

            DeleteableObject.ObjectDeleteHelper(this, profferedDeleteToken, timeToLive);

            BeehiveObjectStore.CreateSignatureVerifiedObject(this.getObjectId(), this);
        }
    }

    // This is a lock signaling that a published object is undergoing the delete process.
    private final ObjectLock<TitanGuid> publishObjectDeleteLocks;

    // This is a lock signaling that the deleteLocalObject() method is already deleting the specified object.
    private final ObjectLock<TitanGuid> deleteLocalObjectLocks;

    public BlockObjectHandler(TitanNode node) throws JMException {
        super(node, BlockObjectHandler.name, "Celeste Block Object Handler");
        this.publishObjectDeleteLocks = new ObjectLock<TitanGuid>();
        this.deleteLocalObjectLocks = new ObjectLock<TitanGuid>();
    }

    public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message)  throws ClassNotFoundException, ClassCastException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException,
    BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception, BeehiveObjectStore.InvalidObjectIdException, BeehiveObjectStore.InvalidObjectException, BeehiveObjectStore.Exception {
        try {
            BlockObject.Object bObject = message.getPayload(BlockObject.Object.class, this.node);
            Publish.PublishUnpublishResponse reply = StorableObject.storeLocalObject(this, bObject, message);
            return reply;
        } catch (TitanMessage.RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    public Publish.PublishUnpublishResponse publishObject(TitanMessage message) throws ClassNotFoundException, ClassCastException {
        try {
            Publish.PublishUnpublishRequest publishRequest = message.getPayload(Publish.PublishUnpublishRequest.class, this.node);

            //
            // Handle deleted objects.
            //
            DeleteableObject.publishObjectHelper(this, publishRequest);
            AbstractObjectHandler.publishObjectBackup(this, publishRequest);

            return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress(), new HashSet<TitanGuid>(publishRequest.getObjects().keySet()));
        } catch (TitanMessage.RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message) throws ClassCastException, ClassNotFoundException {
        try {
            Publish.PublishUnpublishRequest request = message.getPayload(Publish.PublishUnpublishRequest.class, this.getNode());
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("%s", request);
            }
            ReplicatableObject.unpublishObjectRootHelper(this, request);

            return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
        } catch (TitanMessage.RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        String action = HttpMessage.asString(props.get("action"), null);

        if (action != null) {
            if (action.equals("set-config")) {
                this.log.config("Set run-time parameters: loggerLevel=" + this.log.getEffectiveLevel().toString());
            }
        }

        XML.Attribute nameAction = new XML.Attr("name", "action");

        XHTML.Button fetch = new XHTML.Button("text").add("Fetch");
        fetch.setAttribute(nameAction,
                new XML.Attr("value", "set-config"),
                new XML.Attr("title", "Fetch"));

        XHTML.Input objectIdField = new XHTML.Input(XHTML.Input.Type.TEXT).setName("objectId").setSize(64);

        XHTML.Form controls = new XHTML.Form("").setMethod("get").setEncodingType("application/x-www-url-encoded")
        .add(new XHTML.Table(new XML.Attr("class", "controls"))
            .add(new XHTML.Table.Body(
                    new XHTML.Table.Row(new XHTML.Table.Data("Run Application"), new XHTML.Table.Data("")),
                    new XHTML.Table.Row(new XHTML.Table.Data(fetch), new XHTML.Table.Data(objectIdField))
        )));

        String logdata = null;
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(this.log.getLogfile(0));
            logdata = Xxhtml.inputStreamToString(fin);
        } catch (IOException e) {
            logdata = e.toString();
        } finally {
            if (fin != null) try { fin.close(); } catch (IOException ignore) { }
        }

        XHTML.Table logfile = new XHTML.Table(new XHTML.Table.Caption("Logfile"),
                new XHTML.Table.Body(new XHTML.Table.Row(
                        new XHTML.Table.Data(new XHTML.Preformatted(logdata).setClass("logfile")))
                ));

        XHTML.Div body = new XHTML.Div(
                new XHTML.Heading.H1(BlockObjectHandler.name + " " + this.node.getNodeId()),
                new XHTML.Div(controls).setClass("section"),
                new XHTML.Div(logfile).setClass("section"));

        return body;
    }

    public BlockObject.Object storeObject(BlockObject.Object bObject) throws IOException, BeehiveObjectStore.NoSpaceException,
      BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {
        StorableObject.storeObject(this, bObject);
        return bObject;
    }

    public TitanObject retrieveLocalObject(TitanMessage message) throws BeehiveObjectStore.NotFoundException {
        long startTime = System.currentTimeMillis();
        try {
            return this.node.getObjectStore().get(TitanObject.class, message.subjectId);
        } finally {
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("[%d ms] %s", System.currentTimeMillis() - startTime, message.subjectId);
            }
        }
    }

    public BlockObject.Object create(BufferableExtent bounds,
            ExtentBufferMap data, TitanObject.Metadata metadata, TitanGuid deleteTokenId, long timeToLive, ReplicationParameters replicationParams) {
        return new BObject(bounds, data, metadata, deleteTokenId, timeToLive, replicationParams);
    }

    public FutureTask<BlockObject.Object> retrieveTask(BlockObject.Object.Reference bObjectRef, CountDownLatch countDown, ExtentBufferMap map, BufferableExtent desiredSpan) {
        return new FutureTask<BlockObject.Object>(new RetrieveFutureExecution(bObjectRef, countDown, map, desiredSpan));
    }

    public class MappableBlockObjectReader extends AbstractMapFunction<BlockObject.Object.Reference,BlockObject.Object> {
        private BlockObject handler;
        private ExtentBufferMap collection;
        private BufferableExtent desiredSpan;

        public MappableBlockObjectReader(ExecutorService executor, BlockObject handler, ExtentBufferMap collection, BufferableExtent desiredSpan) {
            super(executor);
            this.handler = handler;
            this.collection = collection;
            this.desiredSpan = desiredSpan;
        }

        /**
         * Fetch the {@link BlockObject} referenced by {@code item}.
         *
         */
        public class Function extends AbstractMapFunction<BlockObject.Object.Reference,BlockObject.Object>.Function {
            public Function(BlockObject.Object.Reference item) {
                super(item);
            }

            @Override
            public BlockObject.Object function(BlockObject.Object.Reference item) throws ClassNotFoundException, ClassCastException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException {
                BlockObject.Object bObject = MappableBlockObjectReader.this.handler.retrieve(item.getObjectId());
                if (MappableBlockObjectReader.this.collection != null) {
                    //
                    // Get this BlockObject's data map and trim it down to include
                    // only the portion contained within the span that's to be
                    // read.
                    //
                    ExtentBufferMap data = bObject.getDataAsExtentBufferMap();
                    ExtentBufferMap trimmedData = data.intersect(desiredSpan);

                    //
                    // Add the ExtentBuffers from the trimmed view of this BlockObject
                    // into the map that's accumulating the overall result.
                    // (Since each BlockObject ExtentBufferMap contains
                    // ExtentBuffers whose spans are disjoint from those from
                    // other BlockObject, it's not necessary to use the
                    // replaceExtents() method; the put() method suffices.)
                    //
                    for (ExtentBuffer extentBuffer : trimmedData.values()) {
                        synchronized (MappableBlockObjectReader.this.collection) {
                            MappableBlockObjectReader.this.collection.put(extentBuffer);
                        }
                    }
                }
                return bObject;
            }
        }

        @Override
        public AbstractMapFunction<BlockObject.Object.Reference,BlockObject.Object>.Function newFunction(BlockObject.Object.Reference objectId) {
            return this.new Function(objectId);
        }
    }

    public MapFunction<BlockObject.Object.Reference,BlockObject.Object> newReader(ExecutorService executor, BlockObject handler, ExtentBufferMap collection, BufferableExtent desiredSpan) {
        MappableBlockObjectReader reader = new MappableBlockObjectReader(executor, handler, collection, desiredSpan);
        return reader;
    }

    private class RetrieveFutureExecution implements Runnable, Callable<BlockObject.Object> {
        private BlockObject.Object.Reference reference;
        private CountDownLatch countDown;
        private ExtentBufferMap map;
        private BufferableExtent desiredSpan;

        public RetrieveFutureExecution(BlockObject.Object.Reference bObjectRef, CountDownLatch countDown, ExtentBufferMap map, BufferableExtent desiredSpan) {
            this.reference = bObjectRef;
            this.countDown = countDown;
            this.map = map;
            this.desiredSpan = desiredSpan;
        }

        public BlockObjectHandler.BObject call() throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NotFoundException {
            long startTime = System.currentTimeMillis();
            try {
                BlockObjectHandler.BObject bObject = RetrievableObject.retrieve(BlockObjectHandler.this, BlockObjectHandler.BObject.class, this.reference.getObjectId());

                if (this.map != null) {
                    //
                    // Get this BObject's data map and trim it down to include
                    // only the portion contained within the span that's to be
                    // read.
                    //
                    ExtentBufferMap data = bObject.getDataAsExtentBufferMap();
                    ExtentBufferMap trimmedData = data.intersect(desiredSpan);
                    // BlockObjectHandler.this.log.info("%s %s {%d+%d}", this.reference.getObjectId().toString(), data.asString(false), data.getStartOffset(), data.getRemaining());
                    //this.log.info("trimmedData bounds: %s, size: %d",
                    //    (trimmedData.size() != 0) ? (new BufferableExtentImpl(trimmedData)).toString() : "empty",
                    //    trimmedData.size());

                    //
                    // Add the ExtentBuffers from the trimmed view of this BObject
                    // into the map that's accumulating the overall result.
                    // (Since each BObject's ExtentBufferMap contains
                    // ExtentBuffers whose spans are disjoint from those from
                    // other BObjects, it's not necessary to use the
                    // replaceExtents() method; the put() method suffices.)
                    //
                    for (ExtentBuffer extentBuffer : trimmedData.values()) {
                        synchronized (this.map) {
                            this.map.put(extentBuffer);
                        }
                    }
                }
                return bObject;
            } catch (IllegalStateException e) {
                BlockObjectHandler.this.log.severe("ILLEGAL STATE EXCEPTION %s: ", this.reference.getObjectId());
                throw e;
            } finally {
                if (BlockObjectHandler.this.log.isLoggable(Level.FINE)) {
                    BlockObjectHandler.this.log.fine("[%d ms] %s",
                        (System.currentTimeMillis() - startTime),
                        this.reference.getObjectId().toString());
                }
                if (this.countDown != null)
                    this.countDown.countDown();
            }
        }

        public void run() {
            try {
                this.call();
            } catch (BeehiveObjectStore.DeletedObjectException e) {
                e.printStackTrace();
            } catch (BeehiveObjectStore.NotFoundException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    

    public ReplicatableObject.Replicate.Response replicateObject(TitanMessage message) throws ClassNotFoundException, ClassCastException,
    BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {
        try {
            ReplicatableObject.Replicate.Request request = message.getPayload(ReplicatableObject.Replicate.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("replicate %s", request.getObjectId());
            }
            BlockObject.Object aObject = this.retrieve(request.getObjectId());

            // XXX It would be good to have the Request contain a Map and not a Set, and then just use keySet().
            Set<TitanNodeId> excludeNodes = new HashSet<TitanNodeId>();
            for (TitanNodeId publisher : request.getExcludedNodes()) {
                excludeNodes.add(publisher);                
            }
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("excluding %s", excludeNodes);
            }

            aObject.setProperty(ObjectStore.METADATA_SECONDSTOLIVE, aObject.getRemainingSecondsToLive(Time.currentTimeInSeconds()));

            StorableObject.storeObject(this, aObject, 1, excludeNodes, null);

            return new ReplicatableObject.Replicate.Response();
        } catch (TitanMessage.RemoteException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    public BlockObject.Object retrieve(TitanGuid objectId) throws ClassCastException, ClassNotFoundException, BeehiveObjectStore.NotFoundException, BeehiveObjectStore.DeletedObjectException {

        return RetrievableObject.retrieve(this, BlockObject.Object .class, objectId);
    }

    public TitanMessage deleteLocalObject(TitanMessage message) throws ClassNotFoundException, TitanMessage.RemoteException, BeehiveObjectStore.DeleteTokenException {
        // If the object is locked here, then we are already in the process of deleting it, so just return.
        if (this.deleteLocalObjectLocks.trylock(message.subjectId)) {
            try {
            	if (this.log.isLoggable(Level.FINE)) {
            		this.log.fine("%s", message.subjectId);
            	}
                return DeleteableObject.deleteLocalObject(this, message.getPayload(DeleteableObject.Request.class, this.node), message);
            } finally {
                this.deleteLocalObjectLocks.unlock(message.subjectId);
            }
        }

        return message.composeReply(this.node.getNodeAddress(), new DeleteableObject.Response());
    }

    public ObjectLock<TitanGuid> getPublishObjectDeleteLocks() {
        return publishObjectDeleteLocks;
    }

    public DOLRStatus deleteObject(TitanGuid objectId, TitanGuid profferedDeletionToken, long timeToLive) throws BeehiveObjectStore.NoSpaceException {
        DeleteableObject.Request request = new DeleteableObject.Request(objectId, profferedDeletionToken, timeToLive);
        TitanMessage reply = this.node.sendToObject(objectId, this.getName(), "deleteLocalObject", request);
        return reply.getStatus();
    }

    /**
     * @param object The given BObject for which an anti-object is to be
     *        constructed and returned.
     * @param profferedDeleteToken  The delete-token to use for the anti-object.
     * @return The anti-object form of the given BeehiveObject.
     * @throws IOException
     * @throws BeehiveObjectStore.NoSpaceException
     * @throws BeehiveObjectStore.DeleteTokenException
     */
    public TitanObject createAntiObject(DeleteableObject.Handler.Object object, TitanGuid profferedDeleteToken, long timeToLive)
    throws IOException, BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {
        BlockObject.Object bObject = BlockObject.Object.class.cast(object);
        bObject.delete(profferedDeleteToken, timeToLive);
        return object;
    }

    public Serializable extensibleOperation(TitanMessage message) throws ClassCastException, TitanMessage.RemoteException, SecurityException,
        IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return ExtensibleObject.extensibleOperation(this, message);
    }

    public <C> C extension(Class<? extends C> klasse, TitanGuid objectId, ExtensibleObject.Operation.Request op) throws ClassCastException, ClassNotFoundException, RemoteException {
        return ExtensibleObject.extension(this, klasse, objectId, op);
    }
}
