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
package sunlabs.celeste.node.object;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.celeste.client.ReplicationParameters;
import sunlabs.celeste.node.CelesteNode;
import sunlabs.celeste.node.erasurecode.ErasureCode;
import sunlabs.celeste.node.erasurecode.ErasureCodeIdentity;
import sunlabs.celeste.node.services.object.FObjectType;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.api.XHTMLInspectable;
import sunlabs.titan.node.BeehiveObjectStore.Exception;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanNodeImpl;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.BeehiveObjectStore.InvalidObjectException;
import sunlabs.titan.node.BeehiveObjectStore.UnacceptableObjectException;
import sunlabs.titan.node.TitanNodeIdImpl;
import sunlabs.titan.node.object.BeehiveObjectHandler;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.util.DOLRStatus;

public final class StorableFragmentedObject {

    public interface Handler<T extends StorableFragmentedObject.Handler.Object> extends BeehiveObjectHandler {
        public static final String ERASURECODER = "StorableFragmentedObject.ErasureCoder";

        public interface Object extends BeehiveObjectHandler.ObjectAPI {

        }

        public interface FragmentMap {

        }

        /**
         * <p>
         * Client side store of an erasure-coded {@link TitanObject} on a specific {@link TitanNode}.
         * </p>
         * <p>
         * Store a given {@code TitanObject} on the specified destination.
         * The {@code TitanObject} is to be replicated via the replication algorithm named by erasureCodeName.
         * The destination may be explicitly specified in which case the node with the "closest" object-id will be selected,
         * or it may be {@link TitanGuid#ANY} in which case a random node will be selected.
         * </p>
         * @param destination
         * @param erasureCodeName
         * @param object
         * @return The {@link TitanMessage} response from the destination selected to store the object.
         * @throws BeehiveObjectStore.NoSpaceException
         * @throws TitanNodeImpl.NoSuchNodeException
         * @throws ErasureCode.UnsupportedAlgorithmException
         */
        public FragmentMap store(TitanGuid destination, ErasureCode erasureCodeName, T object)
        throws BeehiveObjectStore.NoSpaceException, TitanNodeImpl.NoSuchNodeException, ErasureCode.UnsupportedAlgorithmException;

        /**
         * <p>
         * Client side store of an erasure-coded {@link TitanObject} on a random {@link TitanNode}.
         * </p>
         * <p>
         * Store a given {@code TitanObject} on a random {@code TitanNode}.
         * This method is equivalent to:
         * </p>
         * <pre>
         * StorableFragmentedObject.Handler.store(TitanGuid.ANY, erasureCodeName, object);
         * </pre>
         * @param erasureCodeName
         * @param object
         * @return The {@link TitanMessage} response from the destination selected to store the object.
         * @throws BeehiveObjectStore.NoSpaceException<
         * @throws TitanNodeImpl.NoSuchNodeException
         * @throws ErasureCode.UnsupportedAlgorithmException
         */
        public FragmentMap store(ErasureCode erasureCodeName, T object)
        throws BeehiveObjectStore.NoSpaceException, TitanNodeImpl.NoSuchNodeException, ErasureCode.UnsupportedAlgorithmException;

        /**
         * <p>
         * Server size store.
         * </p>
         * <p>
         * The return message contains the FragmentMap of the resulting stored object and its fragments.
         * </p>
         * @param message
         * @return The {@link TitanMessage} to use as the response.
         */
        public TitanMessage storeLocalObject(TitanMessage message)
        throws BeehiveObjectStore.NoSpaceException;
    }

    /**
     * A FragmentMap contains the objectId of a stored object,
     * the objectIds of the erasure-coded fragments of that object,
     * and the erasure-code specification that is used to reassemble
     * the first object from the fragments.
     * <style>
     * table.FragmentMap {
     *  border: 1px solid black;
     *  padding: 0;
     * }
     * table.FragmentMap td {
     *  border-bottom: 1px solid gray;
     *  margin: 0;
     *  padding: 0;
     * }
     * </style>
     * Each FragmentMap is a single mapping from one DOLRObject to a set of
     * fragments of that object (FObject) generated by an information dispersal function,
     * wherein the original DOLRObject can be reconstructed from some subset of the fragments.
     * <table class="FragmentMap">
     * <tr><td>GUID</td><td>Erasure Coder</td><td >fragment objectId</td></tr>
     * <tr><td></td><td></td><td>fragment objectId</td></tr>
     * <tr><td></td><td></td><td>fragment objectId</td></tr>
     * <tr><td></td><td></td><td>fragment objectId</td></tr>
     * <tr><td></td><td></td><td>fragment objectId</td></tr>
     * <tr><td></td><td></td><td>fragment objectId</td></tr>
     * <tr><td></td><td></td><td>...</td></tr>
     * </table>
     */
    public static class FragmentMap implements XHTMLInspectable, Serializable {
        private final static long serialVersionUID = 1L;
        private final static String comma = ",";
        private final static String colon = ":";

        private TitanGuid objectId;
        private ErasureCode erasureCoder;
        private TitanGuid[] fragmentId;

        public FragmentMap(TitanGuid objectId, ErasureCode erasureCoder, TitanGuid[] fragmentIds) {
            this.objectId = objectId;
            if (erasureCoder != null) {
                this.erasureCoder = erasureCoder;
                this.fragmentId = fragmentIds;
            }
        }

        /**
         * Get the Reference to the stored object this fragment map represents.
         */
        public TitanGuid getObjectId() {
            return this.objectId;
        }

        /**
         * Get the array of References to the stored objects that make up the set
         * of fragments of the stored object this fragment map represents.
         */
        public TitanGuid[] getFragments() {
            return this.fragmentId;
        }

        /**
         * Return the erasure-coder specification used by this FragmentMap.
         */
        public ErasureCode getErasureCoder() {
            return this.erasureCoder;
        }

        /**
         * Return a formatted String representing this FragmentMap.
         */
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(this.objectId.toString());
            if (this.erasureCoder != null) {
                result.append(colon).append(this.erasureCoder.toString()).append(colon);
                for (int i = 0; i < this.fragmentId.length; i++) {
                    result.append(this.fragmentId[i].toString());
                    if (i < (this.fragmentId.length - 1))
                        result.append(comma);
                }
            }
            return result.toString();
        }

        public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
            XHTML.Table.Data emptyData = new XHTML.Table.Data();
            XHTML.Table.Data indent = new XHTML.Table.Data(new XML.Attr("style", "padding: 0 0 0 3em"));

            XHTML.Table.Body tbody = new XHTML.Table.Body();
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(new XML.Attr("colspan", "3"))
                .add(WebDAVDaemon.inspectObjectXHTML(this.objectId))));
            if (this.erasureCoder != null) {
                tbody.add(new XHTML.Table.Row(indent, new XHTML.Table.Data(this.erasureCoder.toString())));
                for (int i = 0; i < this.fragmentId.length; i++) {
                    tbody.add(new XHTML.Table.Row(indent, emptyData, new XHTML.Table.Data(WebDAVDaemon.inspectObjectXHTML(this.fragmentId[i]))));
                }
            }

            return new XHTML.Table(new XML.Attr("class", "FragmentMap")).add(tbody);
        }

        /**
         * String is {length, bytes}
         * String objectId
         * String erasureCoder
         * int n-fragments
         * String fragments...
         * @return A ByteBuffer containing this encoded FragmentMap
         */
        public ByteBuffer toByteBuffer() {
            ByteBuffer result;
            byte[] oid = this.objectId.toString().getBytes();

            if (this.erasureCoder != null) {
                byte[] ecoder = this.erasureCoder.toString().getBytes();

                int length = (4 + oid.length) + (4 + ecoder.length) + 4 + this.fragmentId.length * (4 + oid.length);

                result = ByteBuffer.allocate(length);
                result.putInt(oid.length);
                result.put(oid);
                result.putInt(ecoder.length);
                result.put(ecoder);
                result.putInt(this.fragmentId.length);

                for (int i = 0; i < this.fragmentId.length; i++) {
                    oid = this.fragmentId[i].toString().getBytes();
                    result.putInt(oid.length);
                    result.put(oid);
                }
            } else {
                int length = (4 + oid.length) + (4);

                result = ByteBuffer.allocate(length);
                result.putInt(oid.length);
                result.put(oid);
                result.putInt(0);
            }
            result.rewind();
            return result;
        }
    }

    /**
     * See also {@link StorableFragmentedObject#storeObjectLocally(Handler, TitanObject, TitanMessage)}.
     * 
     * @param objectType
     * @param destination
     * @param erasureCode
     * @param object
     * @param maxAttempts
     * @return
     * @throws BeehiveObjectStore.NoSpaceException
     * @throws TitanNodeImpl.NoSuchNodeException
     * @throws ErasureCode.UnsupportedAlgorithmException
     */
    public static FragmentMap storeObjectRemotely(BeehiveObjectHandler objectType, TitanNodeId destination, ErasureCode erasureCode,
            StorableFragmentedObject.Handler.Object object,
            int maxAttempts)
    throws BeehiveObjectStore.NoSpaceException, TitanNodeImpl.NoSuchNodeException, ErasureCode.UnsupportedAlgorithmException {
        object.setProperty(StorableFragmentedObject.Handler.ERASURECODER, erasureCode).setProperty(ObjectStore.METADATA_TYPE, objectType.getName());

        if (destination == TitanGuidImpl.ANY) {
            return StorableFragmentedObject.storeObjectRemotely(objectType, erasureCode, object, maxAttempts);
        }

        try {
            TitanMessage reply = objectType.getNode().sendToNodeExactly(destination, objectType.getName(), "storeLocalObject", object);

            object.setObjectId(reply.subjectId);
            return reply.getPayload(StorableFragmentedObject.FragmentMap.class, objectType.getNode());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (ClassCastException e) {
            e.printStackTrace();
            return null;
        } catch (TitanMessage.RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static FragmentMap storeObjectRemotely(
            BeehiveObjectHandler objectType,
            ErasureCode erasureCode,
            StorableFragmentedObject.Handler.Object object,
            int maxAttempts)
    throws BeehiveObjectStore.NoSpaceException, ErasureCode.UnsupportedAlgorithmException {

        object.setProperty(StorableFragmentedObject.Handler.ERASURECODER, erasureCode).setProperty(ObjectStore.METADATA_TYPE, objectType.getName());

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            TitanMessage reply = objectType.getNode().sendToNode(new TitanNodeIdImpl(), objectType.getName(), "storeLocalObject", object);
            if (reply.getStatus().isSuccessful()) {
                object.setObjectId(reply.subjectId);
                try {
                    return reply.getPayload(StorableFragmentedObject.FragmentMap.class, objectType.getNode());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    return null;
                } catch (ClassCastException e) {
                    e.printStackTrace();
                    return null;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        throw new BeehiveObjectStore.NoSpaceException();
    }

    /**
     * <p>
     * Server side store.
     * </p>
     * <p>
     * The return message contains the FragmentMap of the resulting stored
     * object and its fragments.
     * </p>
     * @param message
     * @return The {@link TitanMessage} to use as the response.
     */
    public static TitanMessage storeObjectLocally(StorableFragmentedObject.Handler<StorableFragmentedObject.Handler.Object> objectType, TitanObject object, TitanMessage message)
    throws BeehiveObjectStore.NoSpaceException, BeehiveObjectStore.DeleteTokenException {
        TitanNode node = objectType.getNode();

        if (message.isTraced()) {
            objectType.getLogger().info("recv(%5.5s...)",
                 message.getMessageId());
        }

        // Ensure that that the object's TYPE is set in the metadata.
        object.setProperty(ObjectStore.METADATA_TYPE, objectType.getName());

        try {
            node.getObjectStore().store(object);
        } catch (InvalidObjectException e) {
            // Could not store the Object.  Inform the sender so they can adapt.
            TitanMessage reply = message.composeReply(node.getNodeAddress(), e);
            return reply;
        } catch (UnacceptableObjectException e) {
            // Could not store the Object.  Inform the sender so they can adapt.
            TitanMessage reply = message.composeReply(node.getNodeAddress(), e);
            return reply;        
        }
        
        try {
            node.getObjectStore().lock(BeehiveObjectStore.ObjectId(object));
            try {
                node.getObjectStore().store(object);
                if (message.isTraced()) {
                    objectType.getLogger().info("recv(%5.5s...) stored %s", message.getMessageId(), object.getObjectId());
                }
            } finally {
                try {
                    node.getObjectStore().unlock(object);
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (sunlabs.titan.node.BeehiveObjectPool.Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (BeehiveObjectStore.UnacceptableObjectException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (BeehiveObjectStore.DeleteTokenException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (BeehiveObjectStore.InvalidObjectIdException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (BeehiveObjectStore.NoSpaceException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (BeehiveObjectStore.InvalidObjectException e) {
            return message.composeReply(node.getNodeAddress(), e);
        }


        TitanGuid objectId = object.getObjectId();

        TitanService a = node.getService(CelesteNode.OBJECT_PKG + ".FObjectType");
        if (a instanceof BeehiveObjectHandler && a instanceof FObjectType) {
            FObjectType fObjectApplication = (FObjectType) a;
            TitanObject.Metadata fObjectMetaData = object.getMetadata();

            String erasureCodeName = fObjectMetaData.getProperty(StorableFragmentedObject.Handler.ERASURECODER, ErasureCodeIdentity.NAME + "/1");
            ReplicationParameters replicationParams = new ReplicationParameters("FObject.Replication.Store=2;FObjectReplication.LowWater=2;");

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(object);
                oos.close();

                ErasureCode erasureCoder = ErasureCode.getEncoder(erasureCodeName, bos.toByteArray());
                TitanGuid[] fObjectId = new TitanGuid[erasureCoder.getFragmentCount()];

                for (int i = 0; i < fObjectId.length; i++) {
                    byte[] fragment = erasureCoder.getFragment(i);
                    FObjectType.FObject fObject =
                        fObjectApplication.storeObject(
                                fObjectApplication.create(object.getDeleteTokenId(), object.getTimeToLive(), replicationParams, fObjectMetaData, fragment));
                    fObjectId[i] = fObject.getObjectId();
                }
                StorableFragmentedObject.FragmentMap map = new StorableFragmentedObject.FragmentMap(objectId, erasureCoder, fObjectId);

                TitanMessage reply = message.composeReply(node.getNodeAddress(), map);

                return reply;
            } catch (IOException e) {
                node.getLogger().warning(e.toString());
            } catch (ErasureCode.UnsupportedAlgorithmException e) {
                node.getLogger().severe(e.toString());
            } catch (UnacceptableObjectException e) {
                node.getLogger().severe(e.toString());
            } catch (BeehiveObjectPool.Exception e) {
                node.getLogger().severe(e.toString());
            }

            TitanMessage reply = message.composeReply(node.getNodeAddress(), DOLRStatus.NOT_ACCEPTABLE);
            return reply;
        } else {
            TitanMessage reply = message.composeReply(node.getNodeAddress(), DOLRStatus.INTERNAL_SERVER_ERROR);
            return reply;
        }
    }
}
