/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.celeste.node.services.object;

import java.io.Serializable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import sunlabs.asdf.functional.AbstractMapFunction;
import sunlabs.asdf.functional.MapFunction;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.node.object.DeleteableObject;
import sunlabs.beehive.node.object.ExtensibleObject;
import sunlabs.beehive.node.object.InspectableObject;
import sunlabs.beehive.node.object.ReplicatableObject;
import sunlabs.beehive.node.object.RetrievableObject;
import sunlabs.beehive.node.object.StorableObject;
import sunlabs.beehive.util.BufferableExtent;
import sunlabs.beehive.util.ExtentBufferMap;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.services.object.BlockObjectHandler.MappableBlockObjectReader;

/**
 * <p>
 * A BlockObject (BObject) is a BeehiveObject that contains file data.
 * </p>
 * <p>
 * @see VersionObject
 * @see AnchorObject
 * @author Glenn Scott - Sun Microsystems Laboratories
 * </p>
 */
public interface BlockObject extends
        StorableObject.Handler<BlockObject.Object>,
        RetrievableObject.Handler<BlockObject.Object>,
        InspectableObject.Handler<BlockObject.Object>,
        DeleteableObject.Handler<BlockObject.Object>,
        ReplicatableObject.Handler<BlockObject.Object>,
        ExtensibleObject.Handler<BlockObject.Object> {

    public static interface Object extends StorableObject.Handler.Object,
    RetrievableObject.Handler.Object,
    DeleteableObject.Handler.Object,
    ExtensibleObject.Handler.Object,
    InspectableObject.Handler.Object, 
    ReplicatableObject.Handler.Object {
        public final static String REPLICATIONPARAM_MIN_NAME = "BObject.Replication.Store";
        public final static String REPLICATIONPARAM_LOWWATER_NAME = "BObject.Replication.LowWater";

        public static interface Reference extends Serializable {
            public BeehiveObjectId getObjectId();

            /**
             * Get the byte offset in the original file where the data in this
             * {@code BlockObject} starts.  Equivalent to {@code
             * getBounds().getStartOffset()}.
             */
            public long getFileOffset();

            /**
             * Get the length of the data segment in the original file that
             * this {@code BlockObject} contains.  Equivalent to {@code
             * getBounds().getLength()}.
             */
            public int getLength();

            /**
             * Return an extent describing this {@code BlockObject}'s bounds.
             *
             * @return  this {@code BlockObject}'s bounds
             */
            public BufferableExtent getBounds();
        }

        public BlockObject.Object.Reference makeReference(long offset, BeehiveObjectId objectId);

        public BufferableExtent getBounds();

        public ExtentBufferMap getDataAsExtentBufferMap();

        /**
         * Return the minimum number of copies of this object that must
         * be stored when creating this object.
         */
        public int getReplicationMinimum();

        /**
         * Return the minimum number of cached copies of this object that
         * must be stored after this object is created.
         */
        public int getReplicationCache();
    }

    public BlockObject.Object create(BufferableExtent bounds,
            ExtentBufferMap data, BeehiveObject.Metadata metadata, BeehiveObjectId deleteTokenId, long timeToLive, ReplicationParameters replicationParams);

    /**
     * Construct a {@link FutureTask} instance that will fetch and return the {@code BlockObject}
     * specified in the given {@link BlockObject.Object.Reference}.
     * <p>
     * If {@code countDown} (a {@link CountDownLatch}) is non-null, its
     * {@code countDown} method is called once the BlockObject has been fetched.
     * </p>
     * @param bObjectRef the {@link BlockObject.Object.Reference} of the {@code BlockObject} to retrieve.
     * @param countDown
     * @param map
     * @param desiredSpan
     */
    public FutureTask<BlockObject.Object> retrieveTask(BlockObject.Object.Reference bObjectRef, CountDownLatch countDown, ExtentBufferMap map, BufferableExtent desiredSpan);

    /**
     * Construct and return a new instance of an object implementing the interface {@link MapFunction} which, when applied,
     * will fetch each {@link BlockObject} in the range specified by {@code desiredSpan}.
     */
    public MapFunction<BlockObject.Object.Reference,BlockObject.Object> newReader(ExecutorService executor, BlockObject handler, ExtentBufferMap result, BufferableExtent desiredSpan);
}
