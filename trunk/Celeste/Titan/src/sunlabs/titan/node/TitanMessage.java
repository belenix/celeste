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
 * Please contact Oracle, 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.oracle.com if you need additional
 * information or have any questions.
 */
package sunlabs.titan.node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanNodeId;
import sunlabs.titan.api.XHTMLInspectable;
import sunlabs.titan.exception.BeehiveException;
import sunlabs.titan.node.util.DOLRLogFormatter;
import sunlabs.titan.util.DOLRStatus;

/**
 * The basic unit of {@link TitanNode} discourse is the {@code TitanMessage}.
 * <p>
 * {@code TitanMessage} instances are transmitted from {@code TitanNode} to {@code TitanNode}
 * via the underlying DHT routing mechanism of the system.  Each message contains several
 * fields used to control the routing of the message and a payload consisting of a {@link Serializable} Java object.
 * </p>
 * @see MessageService
 */
public class TitanMessage implements Serializable, XHTMLInspectable {
    private final static long serialVersionUID = 1L;

    /**
     * Create a new {@link TitanMessage} instance by reading it from an {@link InputStream}.
     * @param input
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static TitanMessage newInstance(InputStream input) throws IOException, ClassNotFoundException {
        DataInputStream din = new DataInputStream(input);
        int headerLength = din.readInt();
        int payloadLength = din.readInt();
        byte[] header = new byte[headerLength];
        din.readFully(header);
        byte[] payload = new byte[payloadLength];
        din.readFully(payload);
        return new TitanMessage(header, payload);
    }

    public static class RemoteException extends BeehiveException {
        private final static long serialVersionUID = 1L;

        public RemoteException(Throwable reason) {
            super(reason);
        }
    }

    public static final class Transmission {
        public static final boolean UNICAST = Boolean.FALSE;
        public static final boolean MULTICAST = Boolean.TRUE;
    }

    /**
     * TitanMessage routing control.
     * <p>
     * Messages can be routed exactly, which means the destination {@code TitanNodeId} must match the destination TitanNode's {@code TitanNodeId} exactly.
     * Otherwise, the message is routed loosely, which means the destination TitanNode's {@code TitanNodeId} is the closest (according to the NeighbourMap)
     * node to the destination {@code TitanNodeId}.
     * </p>
     */
    public static final class Route {
        public static final boolean LOOSELY = Boolean.FALSE;
        public static final boolean EXACTLY = Boolean.TRUE;
    }

    /**
     * This message type.  One of:
     * <ul>
     * <li>RouteToNode</li>
     * <li>RouteToObject</li>
     * <li>PublishObject</li>
     * <li>UnpublishObject</li>
     * </ul>
     */
    public enum Type {
        Reply(0), RouteToNode(1), RouteToObject(2), PublishObject(3), UnpublishObject(4);
        
        private byte value;
        
        Type(int v) {
            this.value = (byte) v;
        }
        
        public byte getValue() {
            return this.value;
        }
    }

    /** The version number of this message format. */
    private byte version = 2;

    /** This message's "type" */
    private Type type;

    /** {@code true} if this Message to be traced. */
    private boolean trace = false;

    /** The status this message is conveying */
    private DOLRStatus status;

    /** The unique message is for this message */
    private TitanGuid messageId;

    /** The {@link NodeAddress} of the {@link TitanNode} node generating this message. */
    private NodeAddress source;

    /** The destination {@link TitanGuidImpl}. */
    private TitanNodeId destinationNodeId;

    /**
     * The current hop count for this {@code TitanMessage}.
     * Each hop toward the root of the destination {@link TitanNodeId} increases the hop count.
     * If a message achieves a hop count equal to the number of routing tables,
     * the message is deemed "Undeliverable."
     *
     * TODO: The semantics here ought to be reversed in that the originator
     * should set the value to the maximum number of hops this message should
     * perform.  This would be more flexible in controlling the lifetime of
     * a message.
     */
    public short timeToLive;

    /** {@code True} if this message is to be processed by each node along the route. */
    private boolean isMulticast;

    /** {@code True} if this message is to be sent only by the specified destination. */
    private boolean isExactRouting;

    /** The originators timestamp */
    public long timestamp;

    /**
     * The object-id of the subject of this message.
     * <p>
     * This is typically the same as the destination, but in the cases of object location, where the destination of a message
     * is modified by intermediate node backpointers, this preserves the actual target {@link TitanGuidImpl} of the object.
     * </p>
     */
    public TitanGuid subjectId;

    /** The name of the target object-type for this Message. */
    private String subjectClass;

    /** The name of the target object-type method name for this Message */
    private String subjectClassMethod;

    //
    // These fields capture the data object that constitutes the message's
    // payload.  They are not part of the default serialized form because:
    //    - Messages may make several hops through intermediary nodes before
    //      reaching a node that needs to access the payload.
    //    - Serializing and deserializing the data object is potentially
    //      expensive and is best avoided if unneeded.
    // Thus, the fields get special treatment.
    //    - To create a fresh message, the constructor sets the dataObject
    //      field and leaves the serializedDataObject field null.
    //    - To transmit a message to another node, the serializedDataObject
    //      field is sent intact if non-null.  Otherwise, the transmitter
    //      serializes it from the dataObject field, sets the
    //      serializedDataObject field, and transmits it.
    //    - To receive a message from another node, the deserialization code
    //      sets the serializedDataObject field directly from the received
    //      byte stream, without attempting to deserialize it into dataObject
    //      form.
    //    - The get() method first checks whether the dataObject field is
    //      non-null.  If so, it deserializes the serializedDataObject field
    //      into the dataObject field.  It then casts the dataObject field to
    //      the requested type and returns the result.
    //
    // Note that these semantics imply that a Message contains a snapshot
    // of its dataObject that's taken at construction time.  Subsequent
    // changes to the dataObject are not reflected in the message's content.
    // Similarly, deserialization occurs at most once at a given node and the
    // same deserialized dataObject is returned for each call to get().
    //
    private transient Serializable dataObject = null;
    private transient byte[] payload = null;

    public TitanMessage(TitanMessage.Type type,
            NodeAddress source,
            TitanNodeId destination,
            TitanGuid subjectId,
            String subjectClass,
            String subjectClassMethod,
            boolean isMulticast,
            boolean isExactRouting,
            Serializable payLoad) {
        this.type = type;
        this.status = DOLRStatus.OK;
        this.timeToLive = TitanGuidImpl.n_digits;

        this.source = source;
        this.messageId = new TitanGuidImpl();

        this.destinationNodeId = destination;

        if (subjectClassMethod == null || subjectClassMethod.equals("")) {
            throw new RuntimeException("No subject method");
        }
        this.subjectId = subjectId;
        this.subjectClassMethod = subjectClassMethod;
        this.subjectClass = subjectClass;

        this.isMulticast = isMulticast;
        this.isExactRouting = isExactRouting;

        this.dataObject = payLoad;
        this.payload = null;
    }

    public TitanMessage(byte[] header, byte[] payload) throws IOException, ClassNotFoundException {

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(header));

        // See the order in the writeObject method.
        this.version = ois.readByte();
        byte t = ois.readByte();
        if (t == Type.Reply.value) this.type = Type.Reply;
        else if (t == Type.PublishObject.value) this.type = Type.PublishObject;
        else if (t == Type.UnpublishObject.value) this.type = Type.UnpublishObject;
        else if (t == Type.RouteToNode.value) this.type = Type.RouteToNode;
        else if (t == Type.RouteToObject.value) this.type = Type.RouteToObject;
        
        this.trace = ois.readBoolean();
        this.status = (DOLRStatus) ois.readObject();
        this.messageId = (TitanGuidImpl) ois.readObject();
        this.source = (NodeAddress) ois.readObject();
        this.destinationNodeId = (TitanNodeId) ois.readObject();
        this.timeToLive = ois.readShort();
        this.timestamp = ois.readLong();
        this.isMulticast = ois.readBoolean();
        this.isExactRouting = ois.readBoolean();
        this.subjectClass = (String) ois.readObject();
        this.subjectClassMethod = (String) ois.readObject();
        this.subjectId = (TitanGuidImpl) ois.readObject();        

        this.payload = payload;        
    }


    //    public void print(byte[] b) {
    //        for (int i = 0; i < b.length; i++){
    //            System.out.printf("0x%x ", b[i]);
    //        }
    //        System.out.println();
    //    }

    /**
     * Write this Message on the given {@link DataOutputStream}.
     */
    public void writeObject(DataOutputStream out) throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);

        oos.writeByte(this.version);
        oos.writeByte(this.type.value);
        oos.writeBoolean(this.trace);
        oos.writeObject(this.status);
        oos.writeObject(this.messageId);
        oos.writeObject(this.source);
        oos.writeObject(this.destinationNodeId);
        oos.writeShort(this.timeToLive);
        oos.writeLong(this.timestamp);
        oos.writeBoolean(this.isMulticast);
        oos.writeBoolean(this.isExactRouting);
        oos.writeObject(this.subjectClass);
        oos.writeObject(this.subjectClassMethod);
        oos.writeObject(this.subjectId);

        oos.close();
        bos.close();
        byte[] header = bos.toByteArray();

        if (this.payload == null) {
            this.payload = this.serialize(new TitanMessage.SerializedObject(this.dataObject));
        }

        out.writeInt(header.length);
        out.writeInt(this.payload.length);
        out.write(header);
        out.write(this.payload);
    }

    //
    // Serialization support
    //

    private byte[] serialize(Serializable object) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (IOException e) {
            // Not going to happen.
            return null;
        } finally {
            try {
                if (oos != null)
                    oos.close();
            } catch (IOException e) {}
            try {
                if (baos != null)
                    baos.close();
            } catch (IOException e) {}
        }
    }

    public String getSubjectClass() {
        return this.subjectClass;
    }

    public String getSubjectClassMethod() {
        return this.subjectClassMethod;
    }

    private StringBuilder printClassLoader(ClassLoader loader, StringBuilder result) {
        if (loader != null) {
            ClassLoader parent = loader.getParent();
            if (parent != null) {
                printClassLoader(parent, result);
            }
            result.append(String.format("-> %s%n", loader));
            return result;
        }
        return result.append("-> bootstrap\n");
    }

    /**
     * Get the Serialized payload from this message.
     *
     * <p>
     * The serialized payload is deserialized using the {@link ClassLoader} of the given {@link Class} {@code resultClass}.
     * Note that any message that contains a serialized object that this JVM cannot load using that class loader will
     * throw a {@link ClassNotFoundException}.
     * Note that when you are transmitting object instances that contain a serialized object with a class that is not known to every node's JVM,
     * send the object instance in a container which also contains a ClassLoader that knows how to load the alien Class.
     * </p>
     *
     * @param <C>         the static type to ascribe to the returned object
     * @param resultClass a runtime class representing {@code C} and from which the default {@link ClassLoader} is taken.
     * @param node        the local {@link TitanNode} instance
     *
     * @throws ClassCastException if the Serialized object cannot be cast to the class {@code klasse}
     * @throws ClassNotFoundException if the class {@code klasse} cannot be found by the class loader provided by {@code node}.
     * @throws TitanMessage.RemoteException if they payload contains an exception as the payload.
     */
    public <C> C getPayload(Class<? extends C> resultClass, TitanNode node) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException {
        try {
            if (this.status.equals(DOLRStatus.THROWABLE)) {
                this.loadPayloadObject(Exception.class.getClassLoader(), node);
                throw new TitanMessage.RemoteException(Exception.class.cast(this.dataObject));
            }
            this.loadPayloadObject(resultClass.getClassLoader(), node);
            if (this.dataObject == null) {
                System.err.printf("TitanMessage payload is null: %s%n", this.toString());
            }
            return resultClass.cast(this.dataObject);
        } catch (ClassCastException e) {
            node.getLogger().severe("ClassCast expected %s(%s), found %s(%s)",
                    resultClass.getName(), resultClass.getClassLoader(), this.dataObject.getClass().getName(), this.dataObject.getClass().getClassLoader());
            String r = printClassLoader(this.dataObject.getClass().getClassLoader(), new StringBuilder()).toString();
            node.getLogger().severe("%s", r);
            throw e;
        } catch (ClassNotFoundException e) {
            node.getLogger().severe("ClassNotFound %s loader=%s", resultClass.getName(), resultClass.getClassLoader());
            String r = printClassLoader(this.dataObject.getClass().getClassLoader(), new StringBuilder()).toString();
            node.getLogger().severe("%s", r);
            throw e;
        }
    }

    /**
     * Get the raw un-deserialized object in the Message.
     */
    public byte[] getRawPayLoad() {
        if (this.dataObject != null && this.payload == null) {
            this.payload = this.serialize(new TitanMessage.SerializedObject(this.dataObject));
            this.dataObject = null;
        }
        return this.payload;
    }

    public void setRawPayload(byte[] bytes) {
        this.payload = bytes;
        this.dataObject = null;
    }

    /**
     * Experimental ObjectInputStream that understands how to load classes using a specified ClassLoader
     *
     */
    public static class CustomObjectInputStream extends ObjectInputStream {
        private ClassLoader classLoader;

        public CustomObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, this.classLoader);
            } catch (ClassNotFoundException ex) {
                return super.resolveClass(desc);
            }
        }
    }

    /**
     * Represent a Serialized object that can be deserialized with a particular class loader.
     *
     */
    public static class SerializedObject implements Serializable {
        private final static long serialVersionUID = 1L;
        private byte[] bytes;

        public SerializedObject(Serializable object) {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bout);
                oos.writeObject(object);
                oos.close();
                this.bytes = bout.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public static Serializable deserialize(ClassLoader classLoader, SerializedObject serializedObject) throws IOException, ClassNotFoundException {
            CustomObjectInputStream ois = new CustomObjectInputStream(new ByteArrayInputStream(serializedObject.bytes), classLoader);
            return (Serializable) ois.readObject();
        }
    }

    private synchronized void loadPayloadObject(ClassLoader classLoader, TitanNode node) throws ClassCastException, ClassNotFoundException {
        if (this.dataObject == null) {
            //
            // Give the ApplicationFramework active on the Node hosting
            // this copy of the message a chance to substitute its own
            // class loaders to handle classes named in the serialized
            // form of the dataObject.
            //
            //ApplicationFramework fw = node.getApplicationFramework();
            ByteArrayInputStream bais = null;
            ObjectInputStream ois = null;
            try {
                bais = new ByteArrayInputStream(this.payload);
                ois = new ObjectInputStream(bais);
                this.dataObject = (Serializable) ois.readObject();
                if (this.dataObject instanceof SerializedObject) {
                    this.dataObject = SerializedObject.deserialize(classLoader, (SerializedObject) this.dataObject);
                } else {
                    System.err.printf("Message dataObject needs to be an instance of SerializedObject class%n");
                }
            } catch (IllegalStateException e) {
                try {
                    System.err.printf("vv ILLEGAL STATE EXCEPTION vv length=%d remaining data=%d %s%n", this.payload.length, ois.available(), this.subjectId);
                    FileOutputStream fout = new FileOutputStream("/tmp/serializedDataObject");
                    fout.write(this.payload);
                    fout.close();
                    DOLRLogFormatter.prettyPrint("  ", "%c ", this.payload, 160, System.err);
                    System.err.println();
                    try {
                        bais = new ByteArrayInputStream(this.payload);
                        ois = new ObjectInputStream(bais);
                        this.dataObject = (Serializable)ois.readObject();
                        System.err.printf("Successful retry%n");
                    } catch (ClassNotFoundException g) {
                        System.err.printf("ClassNotFoundException on retry%n");
                    } catch (IllegalStateException g) {
                        System.err.printf("IllegalStateException on retry%n");
                    }

                } catch (IOException f) {
                    System.err.printf("IOException when trying to pretty print the serialized object%n");
                }
                e.printStackTrace();
                System.err.printf("^^ ILLEGAL STATE EXCEPTION ^^%n");
                throw e;
            } catch (IOException e) {
                e.printStackTrace();
                //
                // Since there's no actual i/o, this exception should never
                // occur.
                //
                assert false : "a \"can't happen\" exception occurred";
            } finally {
                try {
                    if (ois != null)
                        ois.close();
                } catch (IOException e) {}
                try {
                    if (bais != null)
                        bais.close();
                } catch (IOException e) {}
            }
        } else {
            //            System.out.printf("Message.loadObject: %s(%s) already deserialised%n", this.dataObject.getClass(), this.dataObject.getClass().getClassLoader());
        }
    }

    /**
     * Compose a reply to the given Message with the specified data as the
     * payload.  It may (or may not) be necessary to override the
     * automatically set Message parameters in the reply with subsequent
     * calls to Message setter methods.
     */
    public TitanMessage composeReply(NodeAddress source, DOLRStatus replyStatus, Serializable payload) {
        if (payload == null)
            throw new NullPointerException("Payload cannot be null");
        TitanMessage message = new TitanMessage(TitanMessage.Type.Reply,
                source,
                this.source.getObjectId(),
                TitanGuidImpl.ANY,
                this.subjectClass,
                this.subjectClassMethod,
                this.isMulticast,
                this.isExactRouting,
                payload);
        message.setStatus(replyStatus);
        return message;
    }


    /**
     * Compose a reply to the given Message with the specified data as the payload.
     * <p>
     * It may (or may not) be necessary to override the default values set Message parameters in the reply with subsequent
     * calls to Message setter methods.
     * </p>
     */
    public TitanMessage composeReply(NodeAddress source, Serializable serializable) {
        TitanMessage message = new TitanMessage(TitanMessage.Type.Reply,
                source,
                this.source.getObjectId(),
                TitanGuidImpl.ANY,
                this.subjectClass,
                this.subjectClassMethod,
                this.isMulticast,
                this.isExactRouting,
                serializable);
        message.setStatus(DOLRStatus.OK);
        return message;
    }

    public TitanMessage composeReply(NodeAddress source, Throwable throwable) {
        return this.composeReply(source, DOLRStatus.THROWABLE, throwable);
    }

    /**
     * Get this message's message identifier.
     */
    public TitanGuid getMessageId() {
        return this.messageId;
    }

    /**
     * @return Returns the timestamp of this message
     */
    public long getTimestamp() {
        return this.timestamp;
    }

    /**
     * Get this Message's type.
     */
    public Type getType() {
        return this.type;
    }

    /**
     * Get the status encoded in this Message.
     */
    public DOLRStatus getStatus() {
        return this.status;
    }

    /**
     * Set the status of this DOLR message.
     */
    public void setStatus(DOLRStatus status) {
        this.status = status;
    }

    /**
     * Get the flag indicating that this Message should
     * be traced as it traverses the system.
     */
    public boolean isTraced() {
        return this.trace;
    }

    /**
     * Set the flag indicating that this {@link TitanMessage} should be traced
     * as it traverses the system.
     */
    public void setTraced(boolean v) {
        this.trace = v;
    }

    /**
     * Get the transmission mode of this message.
     */
    public boolean isMulticast() {
        return this.isMulticast;
    }

    /**
     * Set the routing behaviour for this message.
     */
    public void setExactRouting(boolean route) {
        this.isExactRouting = route;
    }

    /**
     * Get the routing behaviour for this message.
     * <p>
     * If {@code true}, then this message is destined for the node with the {@link TitanGuidImpl}
     * exactly equal to the value in this message's {@link #destinationNodeId}.
     * </p>
     * <p>
     * If <em>not</em> {@code true}, then this message is destined with the node that is the root
     * of the {@code TitanNodeId} in this message's {@code #destinationNodeId}.
     * </p>
     */
    public boolean isExactRouting() {
        return this.isExactRouting;
    }

    /**
     * @return Returns the {@link NodeAddress} of the node that send this message.
     */
    public NodeAddress getSource() {
        return this.source;
    }

    /**
     * Get the {@link TitanGuidImpl} of the destination node for this message.
     */
    public TitanNodeId getDestinationNodeId() {
        return this.destinationNodeId;
    }

    /**
     * Set the {@link TitanGuidImpl} of the destination node for this message.
     */
    public void setDestinationNodeId(TitanNodeId nodeId) {
        this.destinationNodeId = nodeId;
    }

    /**
     * Get the {@link TitanGuidImpl} of this message's "subject."
     */
    public TitanGuid getObjectId() {
        return this.subjectId;
    }

    /**
     * Produce a String displaying this {@code Message}'s message-id,
     * source {@link NodeAddress}, destination object-id, class and method.
     */
    public String traceReport() {
        String result = String.format("msg=%5.5s...: %s src=%5.5s... dst=%5.5s... subject=%5.5s... %s.%s",
                this.getMessageId(),
                this.type,
                this.getSource().format(),
                this.getDestinationNodeId(),
                this.subjectId,
                this.getSubjectClass(),
                this.getSubjectClassMethod());

        return result;
    }

    /**
     * Produce an XHTML table displaying this message.
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        XHTML.Table.Body tbody = new XHTML.Table.Body(
                new XHTML.Table.Row(new XHTML.Table.Data("version"), new XHTML.Table.Data(this.version)),
                new XHTML.Table.Row(new XHTML.Table.Data("status"), new XHTML.Table.Data(this.status + " (" + this.status.getName() + ")")),
                new XHTML.Table.Row(new XHTML.Table.Data("source"), new XHTML.Table.Data(this.source)),
                new XHTML.Table.Row(new XHTML.Table.Data("destination"), new XHTML.Table.Data(this.destinationNodeId)),
                new XHTML.Table.Row(new XHTML.Table.Data("objectId"), new XHTML.Table.Data(this.subjectId)),
                new XHTML.Table.Row(new XHTML.Table.Data("multicast?"), new XHTML.Table.Data(this.isMulticast)),
                new XHTML.Table.Row(new XHTML.Table.Data("exact routing?"), new XHTML.Table.Data(this.isExactRouting)),
                new XHTML.Table.Row(new XHTML.Table.Data("time to live"), new XHTML.Table.Data(this.timeToLive)),
                new XHTML.Table.Row(new XHTML.Table.Data("timestamp"), new XHTML.Table.Data(this.timestamp)),
                new XHTML.Table.Row(new XHTML.Table.Data("application"), new XHTML.Table.Data(this.subjectClass)),
                new XHTML.Table.Row(new XHTML.Table.Data("payload"), new XHTML.Table.Data(this.dataObject.getClass().getName()))
        );

        XHTML.Table table = new XHTML.Table(new XML.Attr("class", "Message"))
        .add(new XHTML.Table.Caption(this.type), tbody);

        return table;
    }

    /**
     * Encode this Message as a String
     */
    @Override
    public String toString() {
        String length = (this.payload == null) ?
                "no" : String.valueOf(this.payload.length);
        String payload = (this.dataObject == null) ?
                String.format("[%s bytes]", length) :
                    this.dataObject.getClass().getName();
                return String.format(
                        "id=%s %s status=%s source=%s destination=%s subjectId=%s " +
                        "isMulticast=%b isExactRouting=%b ttl=%d timestamp=%d " +
                        "class=%s method=%s payload=%s",
                        this.messageId,
                        this.type,
                        this.status,
                        this.source.format(),
                        this.destinationNodeId,
                        this.subjectId,
                        this.isMulticast,
                        this.isExactRouting,
                        this.timeToLive,
                        this.timestamp,
                        this.subjectClass,
                        this.subjectClassMethod,
                        payload
                );
    }
}
