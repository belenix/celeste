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
package sunlabs.titan.node.object;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import sunlabs.asdf.functional.AbstractMapFunction;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.XHTML.EFlow;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.XHTMLInspectable;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.services.CensusDaemon;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.services.api.Census;
import sunlabs.titan.node.util.DOLRLogger;
import sunlabs.titan.util.DOLRStatus;

/**
 * Helper functions to perform operations with Beehive MutableObjects.
 * <p>
 *
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class MutableObject {
    public interface Handler<T extends MutableObject.Handler.ObjectAPI> extends BeehiveObjectHandler {

        public static interface ObjectAPI extends BeehiveObjectHandler.ObjectAPI, Serializable {

        }

        /**
         * Set this node's object history for the specified object.
         */
        public BeehiveMessage setObjectHistory(BeehiveMessage message);

        /**
         * Get this node's object history for the specified object.
         *
         * @param message
         */
        public BeehiveMessage getObjectHistory(BeehiveMessage message);
    }

    /**
     * This is the value that is contained in a MutableObject.
     */
    abstract public static class Value implements Serializable, Cloneable {
        private static final long serialVersionUID = 1L;
        
        /**
         * This flag signifies that the value has been deleted.
         * To delete a value, set the value with the deleted flag set to true.
         * You cannot set the deleted flag to true without also supplying the deleteToken.
         * A deleted value hangs around in the system for the number of seconds specified in the timeToLive parameters.
         * XXX Fix this comment. 
         */
        private BeehiveObjectId deleteToken;
        private long deletedSecondsToLive;
        
        public Value() {
            this.deleteToken = null;
            this.deletedSecondsToLive = 0;
        }
        
        public BeehiveObjectId getDeleteToken() {
            return this.deleteToken;
        }
        
        public long getDeletedSecondsToLive() {
            return this.deletedSecondsToLive;
        }
        
        public Value setDeleteToken(BeehiveObjectId deleteToken, long deletedSecondsToLive) {
            this.deleteToken = deleteToken;
            this.deletedSecondsToLive = deletedSecondsToLive;
            return this;
        }
        
        abstract public Value clone();
        
        /**
         * Produce a printable String representation of this value;
         */
        abstract public String format();
        
        abstract public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
    }

    /**
     * This specifies the parameters for this {@link MutableObject} algorithm implementation.
     */
    public interface Parameters extends Serializable {
        /**
         * Get the number of expected faulty (unavailable) replicas.
         *
         * @return the number of faulty or unavailable nodes
         */
        public int getNFaulty();

        /**
         * Get the maximum number of expected <i>byzantine</i> replicas.
         * These are replicas that are attacking the system by faulty participation.
         *
         * @return the number of <i>byzantine</i> nodes
         */
        public int getNByzantine();

        public String format();
        
        /**
         * Return an {@link XHTML.Table} instance containing a formatted depiction.
         * @return
         */
        public XHTML.Table toXHTMLTable();
    }


    /**
     * Signals that a discrepancy between the expected value and the actual value of a {@link MutableObject}.
     */
    public static class PredicatedValueException extends Exception {
        private final static long serialVersionUID = 1L;

        public PredicatedValueException() {
            super();
        }

        public PredicatedValueException(String message) {
            super(message);
        }

        public PredicatedValueException(String message, Throwable cause) {
            super(message, cause);
        }

        public PredicatedValueException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Signals that an unrecoverable error occurred in the protocol maintaining the MutableObject.
     */
    public static class ProtocolException extends Exception {
        private final static long serialVersionUID = 1L;

        public ProtocolException() {
            super();
        }

        public ProtocolException(String format, Object...args) {
            super(String.format(format, args));
        }

        public ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }

        public ProtocolException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Signals that not enough replicas were found, or could be created, to manage a MutableObject.
     */
    public static class InsufficientResourcesException extends Exception {
        private final static long serialVersionUID = 1L;

        public InsufficientResourcesException() {
            super();
        }

        public InsufficientResourcesException(String message) {
            super(message);
        }

        public InsufficientResourcesException(String message, Throwable cause) {
            super(message, cause);
        }

        public InsufficientResourcesException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Signals that a MutableObject either exists when it shouldn't, or doesn't exist when it should.
     */
    public static class ExistenceException extends Exception {
        private final static long serialVersionUID = 1L;

        public ExistenceException() {
            super();
        }

        public ExistenceException(String message) {
            super(message);
        }

        public ExistenceException(String format, Object...args) {
            super(String.format(format, args));
        }

        public ExistenceException(String message, Throwable cause) {
            super(message, cause);
        }

        public ExistenceException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Signals that a specified MutableObject was not found in the object pool.
     */
    public static class NotFoundException extends Exception {
        private final static long serialVersionUID = 1L;

        public NotFoundException() {
            super();
        }

        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public NotFoundException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Signals the attempt to modify a MutableObject after it has been deleted,
     * but before it has been expired from the object pool.
     */
    public static class DeletedException extends Exception {
        private final static long serialVersionUID = 1L;

        public DeletedException() {
            super();
        }

        public DeletedException(String message) {
            super(message);
        }

        public DeletedException(String format, Object...args) {
            super(String.format(format, args));
        }

        public DeletedException(String message, Throwable cause) {
            super(message, cause);
        }

        public DeletedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * An object-id of a Mutable Object.
     *
     */
    public static class ObjectId extends BeehiveObjectId {
        private static final long serialVersionUID = 1L;

        /**
         * Construct a {@link MutableObject.ObjectId} from an existing {@link BeehiveObjectId}
         */
        public ObjectId(BeehiveObjectId objectId) {
            super(objectId);
        }
    }

    public static class Params implements MutableObject.Parameters {
        private final static long serialVersionUID = 1L;

        private int nFaulty;
        private int nByzantine;

        public Params(String parameterSpec) {
            String[] tokens = parameterSpec.split(",");
            if (tokens.length == 2) {
                this.nFaulty = Integer.valueOf(tokens[0]);
                this.nByzantine = Integer.valueOf(tokens[1]);
                if (this.nFaulty >= 0 && this.nByzantine >= 0) {
                    return;
                }
            }
            throw new IllegalArgumentException("Improper specification: " + parameterSpec);
        }

        public int getNFaulty() {
            return this.nFaulty;
        }

        public int getNByzantine() {
            return this.nByzantine;
        }

        public String format() {
            return Integer.toString(this.nFaulty) + " " + Integer.toString(this.nByzantine);
        }
        
        public XHTML.Table toXHTMLTable() {
        	XHTML.Table.Body tbody = new XHTML.Table.Body();
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Faulty"), new XHTML.Table.Data(this.nFaulty)));
        	tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("Byzantine"), new XHTML.Table.Data(this.nByzantine)));
        	
        	return new XHTML.Table(tbody);
        }
    }

    /**
     * <p>
     * A time-stamp in accordance with time-stamps defined in [1].
     * </p>
     * <p>
     * A time-stamp represents the change of the value of an object at a
     * particular time. A series of time-stamps is an object-history. A
     * time-stamp contains the object-id of the client node changing the value
     * and the {@link BeehiveObjectId} of the object-history-set that this timp-stamp is a
     * member of. A time-stamp contains the housekeeping values used for
     * maintenance of the time-stamp in a multi-server protocol.
     * </p>
     * <p>
     * Compact time-stamps can substitute operation and objectHistorySet with a
     * HASH(operation + objectHistorySetVerifier)
     * </p>
     * <p>
     * I include the value in the time-stamp, although I do not use it for
     * comparison. It makes getting the value of an object at a particular time
     * easier.
     * </p>
     */
    public static class TimeStamp implements Comparable<TimeStamp>, Serializable {
        private static final long serialVersionUID = 1L;

        private final long time;

        private final boolean isBarrier;

        private final BeehiveObjectId clientId;

        private final int operation;

        private final BeehiveObjectId objectHistorySetVerifier;

        private final MutableObject.Value value;

        private final BeehiveObjectId generation;

        private final SortedSet<BeehiveObjectId> replicas;

        /**
         * Construct a TimeStamp representing the initial state.
         */
        public TimeStamp() {
            this.time = 0;
            this.isBarrier = false;
            this.clientId = null;
            this.operation = 0;
            this.objectHistorySetVerifier = null;
            this.replicas = new TreeSet<BeehiveObjectId>();
            this.generation = null;
            this.value = null;
        }

        /**
         * The Barrier form of a {@code TimeStamp}.
         * <p>
         * This {@code TimeStamp} is only a barrier.
         * </p>
         *
         * @param time
         * @param clientId
         * @param objectHistorySet
         */
        public TimeStamp(long time, BeehiveObjectId clientId, ObjectHistorySet objectHistorySet) {
            this(time, true, 0, clientId, objectHistorySet.getObjectId(), objectHistorySet.getReplicaSet(), null);
        }

        /**
         * The value form of a {@code TimeStamp}.
         *
         * <p>
         * This {@code TimeStamp} is not a barrier and cannot be used as a barrier.  It contains only a value.
         * </p>
         */
        public TimeStamp(long time, int operation, BeehiveObjectId clientId, ObjectHistorySet objectHistorySet, MutableObject.Value value) {
            this(time, false, operation, clientId, objectHistorySet.getObjectId(), objectHistorySet.getReplicaSet(), value);
        }

        protected TimeStamp(long time, boolean barrierFlag, int operation, BeehiveObjectId clientId, BeehiveObjectId ohsId,
                SortedSet<BeehiveObjectId> serverSet, MutableObject.Value value) {
            this.time = time;
            this.isBarrier = barrierFlag;
            this.clientId = clientId;
            this.operation = operation;
            this.objectHistorySetVerifier = ohsId;
            this.replicas = serverSet;
            this.generation = null;
            this.value = value;
        }

        /**
         * Return {@code True} if this {@code TimeStamp} is the canonical initial {@code TimeStamp}.
         */
        public boolean isInitial() {
            return this.time == 0 && this.isBarrier == false
                    && this.clientId == null && this.operation == 0
                    && this.objectHistorySetVerifier == null
                    && this.value == null;
        }

        public long getTime() {
            return this.time;
        }

        /**
         * Return true if this {@code TimeStamp} is a barrier.
         */
        public boolean isBarrier() {
            return this.isBarrier;
        }

        /**
         * Return the {@link MutableObject.Value} instance in this {@code TimeStamp}.
         */
        public MutableObject.Value getValue() {
            return this.value;
        }

        /**
         * Return the {@link BeehiveObjectId} of the client establishing this @code TimeStamp}.
         */
        public BeehiveObjectId getClientId() {
            return this.clientId;
        }

        public void setServerSet(Set<BeehiveObjectId> servers) {
            this.replicas.addAll(servers);
        }

        public SortedSet<BeehiveObjectId> getServerSet() {
            return this.replicas;
        }

        /**
         * Compare two {@code TimeStamp} instances
         * <p>
         * Equality between two @code TimeStamp} instances is natural:  all elements
         * of the {@code TimeStamp}s must be identical.  Inequalities are measured as
         * the comparison (in order) of the time (numerical), the {@code barrierFlag}
         * (with false < true), clientId (lexicographic), operation
         * (lexicographic), and predicated objectHistorySet (lexicographic).
         * </p>
         * <p>
         * return 0 if they are equal, &gt;0 if this {@code TimeStamp} is greater than
         * the other, return &lt;0 if this {@code TimeStamp} is less than the other
         * </p>
         * <p>
         * See Definition 2.9 of [2]
         * </p>
         */
        public int compareTo(TimeStamp other) {
            // If the other is null, this version is clearly newer, so just
            // return 1.
            if (other == null)
                return 1;

            // If the time order is different, return the difference between
            // this time and the other.
            if (this.time != other.time)
                return (int) (this.time - other.time);

            // If this is a barrier and the other is NOT a barrier..
            if (this.isBarrier != other.isBarrier)
                return (this.isBarrier ? 1 : -3);

            if ((this.clientId == null) != (other.clientId == null))
                return this.clientId == null ? -2 : 1;

            int delta = (this.clientId == null && other.clientId == null) ? 0 : this.clientId.compareTo(other.clientId);
            if (delta != 0)
                return delta;

            if (this.operation != other.operation)
                return this.operation - other.operation;

            // If one has a verifier, but the other doesn't, then they are not
            // equal.
            if ((this.objectHistorySetVerifier == null) != (other.objectHistorySetVerifier == null))
                return this.objectHistorySetVerifier == null ? -1 : 1;

            if (this.objectHistorySetVerifier != null && this.objectHistorySetVerifier.compareTo(other.objectHistorySetVerifier) != 0) {
                return 1;
            }

//            if (false) {
//                if (this.replicas == null && other.getServerSet() != null) {
//                    return -1;
//                }
//
//                if (this.replicas != null && other.getServerSet() == null) {
//                    return 1;
//                }
//                // for (DOLRObjectId s : this.serverSet) {
//                // if (!other.getServerSet().contains(s))
//                // return 1;
//                // }
//            }

            return 0;
        }

        /**
         * Get the {@link BeehiveObjectId} of this {@code TimeStamp}.
         *
         */
        public BeehiveObjectId getObjectId() {
            BeehiveObjectId result = BeehiveObjectId.ZERO;
            result = result.add(this.clientId);
            result = result.add(this.generation);
            result = result.add(this.objectHistorySetVerifier);
            result = result.add(this.time);
            result = result.add(this.operation);
            result = result.add(this.isBarrier ? 1 : 0);
            for (BeehiveObjectId replica : this.replicas) {
                result = result.add(replica);
            }
            if (this.value != null)
                result = result.add(this.value.format().getBytes());
            return result;
        }

        @Override
        public String toString() {
            StringBuilder servers = new StringBuilder();
            boolean insertLeadingComma = false;
            if (this.replicas != null) {
                for (BeehiveObjectId s : this.replicas) {
                    servers.append((insertLeadingComma ? "," : "")).append(s == null ? "null" : String.format("%4.4s...", s));
                    insertLeadingComma = true;
                }
            } else {
                servers.append("no-servers");
            }

            String space = " ";

            // String v = (this.value == null)
            // ? "(null)"
            // : new String(this.value.array(), this.value.arrayOffset(),
            // Math.min(this.value.remaining(), 150));

            StringBuilder result = new StringBuilder("{")
                .append(this.isBarrier ? "B" : "V")
                .append(this.time)
            // .append(space).append(this.operation)
                .append(space)
                .append(this.clientId == null ? "null" : String.format("%4.4s...", this.clientId))
                .append(space)
                .append(this.objectHistorySetVerifier == null ? "null" : String.format("%4.4s...", this.objectHistorySetVerifier))
                // .append(space).append((this.generation == null) ? "null"
                // : String.format("%4.4s...", this.generation))
                // .append(space).append("[").append(servers).append("] ");
                .append(space).append(this.value == null ? "null" : this.value.toString())
                // .append(space).append(v)
               .append("}");
            ;

            return result.toString();
        }

        public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
            XHTML.Table.Body replicaSetBody = new XHTML.Table.Body();
            for (BeehiveObjectId replica : this.replicas) {
                replicaSetBody.add(new XHTML.Table.Row(new XHTML.Table.Data(WebDAVDaemon.inspectObjectXHTML(replica))));
            }
            XHTML.Table serverTable = new XHTML.Table(replicaSetBody);

            XHTML.Table.Data emptyCell = new XHTML.Table.Data("");

            XHTML.Table.Body tbody = new XHTML.Table.Body()
                    .add(new XHTML.Table.Row(
                    		new XHTML.Table.Data(this.time),
                    		new XHTML.Table.Data(this.isBarrier ? "Barrier" : "Value"),
                    		new XHTML.Table.Data("Operation=%s, GenerationId=%s", this.operation, this.generation),
                            emptyCell))
                    .add(new XHTML.Table.Row(
                    		emptyCell,
                    		new XHTML.Table.Data("ClientId"),
                    		new XHTML.Table.Data(this.clientId)))
                    .add(new XHTML.Table.Row(
                    		 emptyCell,
                             new XHTML.Table.Data("Object History Set Id"),
                             new XHTML.Table.Data(this.objectHistorySetVerifier)))
                    .add(new XHTML.Table.Row(
                    		 emptyCell,
                    		 new XHTML.Table.Data("Replica Set"),
                    		 new XHTML.Table.Data(new XML.Attr("colspan", "4")).add(serverTable)))
                    .add(new XHTML.Table.Row(
                    		 emptyCell,
                    		 new XHTML.Table.Data("Value"),
                    		 new XHTML.Table.Data(new XML.Attr("colspan", "10")).add(this.value == null ? "null" : this.value.toXHTML(uri, props))));

            return new XHTML.Table(tbody).setClass("timestamp");
        }
    }

    /**
     * <p>
     *
     * The single object history for a mutable object. An object-history is a list of
     * time-stamps in relative time order. The state of the object represented by
     * this object-history is always the last time-stamp.
     *
     * XXX The implementation of the authenticator is incomplete.
     *
     * Currently this just computes a {@link BeehiveObjectId} from the
     * object history (as a String), but doesn't include any verification that
     * this node produced it.
     *
     * </p>
     */
    public static class ObjectHistory implements XHTMLInspectable, Serializable, Iterable<TimeStamp>, Comparable<ObjectHistory> {
        private static final long serialVersionUID = 1L;

        public static class ValidationException extends Exception {
            private static final long serialVersionUID = 1L;

            public ValidationException() {
                super();
            }

            public ValidationException(String format, Object...args) {
                super(String.format(format, args));
            }

            public ValidationException(Throwable cause) {
                super(cause);
            }

            public ValidationException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        /**
         * Signifies that the {@link ObjectHistory} is out-of-date with respect
         * to another {@code ObjectHistory} object.
         */
        public static class OutOfDateException extends Exception {
            private static final long serialVersionUID = 1L;

            public OutOfDateException() {
                super();
            }

            public OutOfDateException(String message) {
                super(message);
            }

            public OutOfDateException(Throwable cause) {
                super(cause);
            }

            public OutOfDateException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        private final BeehiveObjectId objectId;

        private final BeehiveObjectId replicaId;

        private final List<MutableObject.TimeStamp> history = new LinkedList<TimeStamp>();

        private final BeehiveObjectId authenticator;

        private TimeStamp latestTimeStamp;
        
        public boolean readOnly = false;

        public ObjectHistory() {
            this.objectId = null;
            this.replicaId = null;
            this.authenticator = null;
            this.latestTimeStamp = null;
        }

        public ObjectHistory(BeehiveObjectId objectId, BeehiveObjectId server) {
            this.objectId = objectId;
            this.replicaId = server;
            this.authenticator = null;
            this.latestTimeStamp = null;
        }

        // Methods specific to the ObjectHistory

        /**
         * Produces a deep copy of this {@code ObjectHistory}.
         */
        public ObjectHistory dup() {
            try {
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bao);
                oos.writeObject(this);
                oos.close();
                ByteArrayInputStream bis = new ByteArrayInputStream(bao.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                return (ObjectHistory) ois.readObject();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }

        /**
         * Return {@code true} if this {@code ObjectHistory} latest {@link TimeStamp} is the initial {@code TimeStamp}.
         */
        public boolean isInitial() {
            return this.latestTimeStamp.isInitial();
        }

        /**
         * Add the given {@link TimeStamp} to this {@code ObjectHistory}.
         * <p>
         * If the new {@code TimeStamp} is newer than the previous value of
         * {@link #latestTimeStamp}, update {@code #latestTimeStamp} to the value
         * of the new {@code TimeStamp}.
         * </p>
         * @param timeStamp
         */
        public ObjectHistory add(TimeStamp timeStamp) {
//        	if (this.readOnly) {
//        		System.err.printf("modifying readonly ObjectHistory%n");
//        		new Throwable().printStackTrace();
//        	}
            this.history.add(timeStamp);
            if (this.latestTimeStamp == null || timeStamp.compareTo(this.latestTimeStamp) > 0) {
                this.latestTimeStamp = timeStamp;
            }
            return this;
        }

        /**
         * Get the value of this {@code ObjectHistory}'s {@code #latestTimeStamp}.
         */
        public TimeStamp getLatestTimeStamp() {
            return this.latestTimeStamp;
        }

        public BeehiveObjectId getReplicaId() {
            return this.replicaId;
        }

        public MutableObject.Value getValue() {
            return this.getLatestTimeStamp().getValue();
        }

        /**
         * Set the value of this {@link ObjectHistory}.
         *
         * Set the value of this ObjectHistory according to the next protocol
         * step dictated by the ObjectHistorySet protocol.
         *
         * @param logger
         * @param clientId
         * @param ohs
         * @param value
         * @throws ObjectHistory.OutOfDateException
         */
        public ObjectHistory setValue(DOLRLogger logger, BeehiveObjectId clientId, ObjectHistorySet ohs, MutableObject.Value value)
        throws ObjectHistory.OutOfDateException {
            //
            // Ensure that this ObjectHistory instance is in the given ObjectHistorySet.
            //
            MutableObject.ObjectHistory h = ohs.get(this.replicaId);
            if (h == null) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("The proffered OHS doesn't contain an entry for replica " + this.replicaId);
                }
                throw new ObjectHistory.OutOfDateException();
            }
            if (this.compareTo(h) > 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("The proffered OHS is out of date.");
                }
                throw new ObjectHistory.OutOfDateException("The proffered OHS is out of date.");
            }
            if (ohs.getState() == ObjectHistorySet.State.COMPLETE) {
                TimeStamp timeStamp = new TimeStamp(ohs.latestTimeStamp.getTime() + 1, 0, clientId, ohs, value);
                this.add(timeStamp);
            } else if (ohs.getState() == ObjectHistorySet.State.BARRIER) {
                TimeStamp barrier = new TimeStamp(ohs.latestTimeStamp.getTime() + 1, true, 0, clientId, ohs.getObjectId(), ohs.getReplicaSet(), null);
                this.add(barrier);
            } else if (ohs.getState() == ObjectHistorySet.State.REPAIRABLE_INLINE) {
                if (ohs.getLatestCandidate().compareTo(this.latestTimeStamp) > 0) {
                    // This instance is out of date, update it from the latest
                    // candidate from the object history set.
                    this.add(ohs.getLatestCandidate());
                } else {
                    TimeStamp timeStamp = this.getLatestTimeStamp();
                    for (BeehiveObjectId serverId : ohs.getReplicaSet()) {
                        if (!timeStamp.getServerSet().contains(serverId)) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("My ObjectHistory does not contain the same ServerSet");
                                logger.info("Old server set");
                                for (BeehiveObjectId i : timeStamp.getServerSet()) {
                                    logger.info(i.toString());
                                }
                                logger.info("New server set");
                                for (BeehiveObjectId i : ohs.getReplicaSet()) {
                                    logger.info(i.toString());
                                }
                            }
                        }
                    }
                }
                // Make sure the server set in the last time stamp is the server
                // set in the supplied ObjectHistorySet.
            } else if (ohs.getState() == ObjectHistorySet.State.COPY) {
                MutableObject.Value v = ohs.getLatestCandidate().getValue();

                TimeStamp newValue = new TimeStamp(ohs.getLatestCandidate().getTime() + 1, 0, clientId, ohs, v);
                this.add(newValue);
            }
            return this;
        }

        public Iterator<TimeStamp> iterator() {
            return this.history.iterator();
        }

        //
        // XXX: This method might just as well be equals(); the ordering seems
        //      arbitrary except for equality.  (Does that matter?  Yes: The
        //      sign on a.compareTo(b) must be anti-symmetric with that of
        //      b.compareTo(a), and this implementation violates that
        //      requirement.)
        //
        public int compareTo(ObjectHistory other) {
            if (this.history.size() != other.history.size())
                return (1);

            Iterator<TimeStamp> j = other.history.iterator();
            Iterator<TimeStamp> i = this.history.iterator();
            while (i.hasNext() && j.hasNext()) {
                TimeStamp x = i.next();
                TimeStamp y = j.next();
                if (x.compareTo(y) != 0) {
                    System.out.println("differ:\n    " + x);
                    System.out.println("    " + y);
                    return (1);
                }
            }
            return (i.hasNext() || j.hasNext()) ? 1 : 0;
        }

        /**
         * Produce an XHTML representation of this object history
         */
        public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
            
            XHTML.Table.Body tbody = new XHTML.Table.Body();
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("ObjectId"), new XHTML.Table.Data(this.objectId)));
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data("ReplicaId"), new XHTML.Table.Data(this.replicaId)));
            
            XHTML.Table.Body timeStampBody = new XHTML.Table.Body();

            for (TimeStamp timeStamp : this) {
                timeStampBody.add(new XHTML.Table.Row(new XHTML.Table.Data(timeStamp.toXHTML(uri, props))));
            }
            return new XHTML.Div(new XHTML.Table(tbody).setClass("MutableObject"), new XHTML.Table(timeStampBody).setClass("MutableObject"));
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(String.format("%7.7s... %7.7s... ", this.objectId, this.replicaId));

            for (TimeStamp timeStamp : this) {
                result.append(" ").append(timeStamp.toString());
            }
            return result.toString();
        }

        /**
         * Get the {@link BeehiveObjectId} of this {@code ObjectHistory}.
         *
         * <p>
         * Each {@code ObjectHistory} instance will have a unique {@code BeehiveObjectId}
         * distinguished by the {@code BeehiveObjectId} of the MutableObject it represents, the {@link TimeStamp}
         * history and the {@code replicaId}.
         * </p>
         */
        public BeehiveObjectId getObjectId() {
            BeehiveObjectId result = this.objectId;
            for (TimeStamp timeStamp : this) {
                result = result.add(timeStamp.getObjectId());
            }
            result = result.add(this.replicaId);

            return result;
        }
    }

    /**
     * This class represents an Object History Set (OHS) collected from a quorum
     * of servers.
     *
     * Once an Object History Set contains a quorum number of entries,
     * classification of the state of the object can be made. This
     * classification is used to determine the value of the object, or how it
     * must be repaired.
     */
    public static class ObjectHistorySet implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The {@link BeehiveObjectId} this {@code ObjectHistorySet} represents.
         */
        private MutableObject.ObjectId objectId;

        private MutableObject.Parameters params;

        /**
         * The {@link SortedMap} of {@link ObjectHistory} replica object-ids to
         * {@link ObjectHistory} instances for this ObjectHistorySet.
         */
        //
        // N.B.  This map must be sorted, to ensure that the hash computed in
        // getObjectId() has a deterministic value.
        //
        private final SortedMap<BeehiveObjectId, MutableObject.ObjectHistory> histories;

        /**
         * The State of an {@link ObjectHistorySet} is one of:
         * <ul>
         * <li>INCONCLUSIVE</li>
         * An insufficent number of replicas are available.
         * <li>COMPLETE</li>
         * A sufficient number of replicas are available and they are consistent.
         * <li>REPAIRABLE_INLINE</li>
         * A sufficient number of replicas are available, they are inconsistent and can be repaired.
         * <li>BARRIER</li>
         * A sufficient number of replicare are available, and they form a barrier.
         * <li>COPY</li>
         * </ul>
         */
        public enum State {
            INCONCLUSIVE, COMPLETE, REPAIRABLE_INLINE, BARRIER, COPY
        };

        /**
         * The {@link State} of this {@link ObjectHistorySet}.
         * <p>
         * The state of the object history set is continually updated as the set is updated.
         * </p>
         */
        private State state;

        /**
         * The most recent, repairable or better, Value in the Object History
         * Set
         */
        private TimeStamp latestValue;

        /**
         * The most recent, repairable or better, Barrier in the Object History
         * Set
         */
        private TimeStamp latestBarrier;

        /**
         * The most recent {@link TimeStamp} in all of the {@link ObjectHistory} instances in this set.
         */
        private TimeStamp latestTimeStamp;

        /**
         * The most recent of either latestBarrier or latestValue. If the State
         * of this object is COMPLETE, latestCandidate contains the
         * authoritative value of this MutableObject.
         */
        private TimeStamp latestCandidate;
        
        private boolean readOnly = false;

        public ObjectHistorySet(MutableObject.ObjectId objectId, MutableObject.Parameters params) {
            this.histories = new TreeMap<BeehiveObjectId, MutableObject.ObjectHistory>();
            this.objectId = objectId;
            this.params = params;

            this.state = State.INCONCLUSIVE;
            this.latestBarrier = null;
            this.latestValue = null;
            this.latestTimeStamp = null;
        }
        
//        public void setReadOnly(boolean value) {
//        	this.readOnly = value;
//        	for (ObjectHistory replica : this.histories.values()) {
//        		replica.readOnly = value;        		
//        	}
//        }

        public synchronized ObjectHistorySet dup() throws ObjectHistory.ValidationException {
            try {
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bao);
                oos.writeObject(this);
                oos.close();
                ByteArrayInputStream bis = new ByteArrayInputStream(bao.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                return (ObjectHistorySet) ois.readObject();
            } catch (IOException e) {
                throw new RuntimeException();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException();
            }
        }

        /**
         * Get the {@link BeehiveObjectId} of this {@code ObjectHistorySet}.
         * <p>
         * The BeehiveObjectId of this @code ObjectHistorySet} is computed as a hash over the entire {@code ObjectHistorySet}.
         * This is not the same as the {@code BeehiveObjectId} of the MutableObject that this {@code ObjectHistorySet} represents.
         * See {@link #getMutableObjectIdObjectId()}
         * </p>
         */
        public synchronized BeehiveObjectId getObjectId() {
            // It is of the utmost importance that the order in which these are computed is consistent.
            // Otherwise the computed hash will be different, even if the components are identical.
            BeehiveObjectId result = new BeehiveObjectId(this.objectId);
            result = result.add(this.params.format().getBytes());

            for (BeehiveObjectId replica : this.histories.keySet()) {
                result = result.add(replica);
                result = result.add(this.histories.get(replica).getObjectId());
                //result = result.add(this.histories.get(replica).toByteArray());
            }
            return result;
        }

        @Override
        public synchronized String toString() {
            StringBuilder s = new StringBuilder(this.objectId.toString())
                    .append(" f=").append(this.params.getNFaulty())
                    .append(" b=").append(this.params.getNByzantine())
                    .append(" State=").append(this.getState()).append("\n")
                    .append("T=").append(this.latestTimeStamp).append("\n")
                    .append("B=").append(this.latestBarrier).append("\n")
                    .append("V=").append(this.latestValue).append("\n")
                    .append("C=").append(this.latestCandidate).append("\n");

            for (BeehiveObjectId _server : this.histories.keySet()) {
                MutableObject.ObjectHistory objectHistory =  this.histories.get(_server);
                s.append(String.format("%s\n", objectHistory.toString()));
            }

            return s.toString();
        }

        /**
         * Get the {@link MutableObject.ObjectId} of the MutableObject represented by this ObjectHistorySet.
         *
         */
        public MutableObject.ObjectId getMutableObjectIdObjectId() {
            return this.objectId;
        }

        /**
         * Return the {@link MutableObject.ObjectHistory} instance of the given
         * {@code replicaId} from this {@code ObjectHistorySet}.
         * @param replicaId The {@link BeehiveObjectId} of the {@link ObjectHistory} replica.
         */
        public synchronized MutableObject.ObjectHistory get(BeehiveObjectId replicaId) {
            return this.histories.get(replicaId);
        }

        /**
         * Add the given {@link ObjectHistory} into this {@link ObjectHistorySet}
         * if the {@code ObjectHistorySet#state} is not already {@link State#COMPLETE}.
         * <p>
         * The state of the ObjectHistorySet is recomputed.
         * </p>
         * @param history this {@link ObjectHistory} to add.
         * @return {@code true} if the {@code ObjectHistory} was added to the {@code ObjectHistorySet}.
         */
        public synchronized boolean put(MutableObject.ObjectHistory history) {
        	// XXX
        	if (this.readOnly) {
        		System.err.printf("modifying a read-only ObjectHistorySet");
        		new Throwable().printStackTrace();
        	}
        	// XXX
            // If this ObjectHistorySet is already COMPLETE, don't bother recomputing it.
            if (this.state == State.COMPLETE) {
                return false;
            }
            // This can induce ConcurrentModificationException if this is modified while another thread is looking at this OHS.
            this.histories.put(history.getReplicaId(), history);

            // If we have not yet recorded a value for the latest TimeStamp in this ObjectHistorySet,
            // or the value we have previously recorded is older than the latest TimeStamp
            // in the given ObjectHistory, then update the value for the latest
            // TimeStamp in this ObjectHistorySet.
            if (this.latestTimeStamp == null || latestTimeStamp.compareTo(history.latestTimeStamp) < 0)
                this.latestTimeStamp = history.latestTimeStamp;

            // The state of this ObjectHistorySet is INCONCLUSIVE unless
            // proven otherwise.
            this.state = State.INCONCLUSIVE;

            //
            if (this.histories.size() >= this.quorumSize()) {
                this.latestBarrier = null;
                this.latestValue = null;

                // Determine the latest Barrier and Value TimeStamps from this
                // ObjectHistorySet.
                //
                // XXX This loop does too much.
                // This should take the longest history and iterate through
                // that, rather than go through each server as well.
                for (BeehiveObjectId _server : this.histories.keySet()) {
                    for (TimeStamp version : this.histories.get(_server)) {
                        if (this.order(version) >= this.repairableSize()) {
                            if (version.isBarrier() == true) {
                                if (version.compareTo(this.latestBarrier) > 0) {
                                    this.latestBarrier = version;
                                }
                            } else {
                                if (version.compareTo(this.latestValue) > 0) {
                                    this.latestValue = version;
                                }
                            }
                        }
                    }
                }

                // There are two kinds of repair:
                // The first is "inline repair" and can be done in one
                // iteration.
                // The second requires that a barrier be established in the
                // history set and then the last successful value is copied over
                // the barrier.
                //
                // The inline repair does not require a barrier or a copy as
                // it repairs a candidate in-place at its timestamp by
                // completing the operation that yielded the candidate.
                // Inline-repair operations can complete in a single round trip.
                // Inline repair operations can complete in a single round trip.
                //
                // Inline repair is ONLY possible if there is no contention for
                // the object. Contention for the object is indicated by the
                // presence of a TimeStamp in the ObjectHistorySet that is
                // greater than the TimeStamp of the repairable candidate.
                //
                // Inline repair can also be performed on barrier candidates.

                this.state = State.REPAIRABLE_INLINE;

                if (this.latestTimeStamp.compareTo(this.latestBarrier) > 0 && this.latestTimeStamp.compareTo(this.latestValue) > 0) {
                    this.state = State.BARRIER;
                }

                if (false) {
                    if (this.order(this.latestValue) >= this.quorumSize()) {
                        System.out.println(this.latestValue + " is sufficient for an inline super-quorum repair.");
                    }
                    if (this.order(this.latestBarrier) >= this.quorumSize()) {
                        System.out.println(this.latestBarrier + " is sufficient for an inline super-quorum repair.");
                    }
                }

                // If the latest TimeStamp is also the latest Barrier
                // then the next step in the protocol is to perform a COPY.
                if (this.latestTimeStamp.compareTo(this.latestBarrier) == 0 && this.order(this.latestBarrier) >= this.quorumSize()) {
                    this.state = State.COPY;
                    if (this.latestBarrier.isInitial())
                        this.latestBarrier = null;
                }

                // If the latest TimeStamp is also the latest Value,
                // then the protocol is COMPLETE.
                if (this.latestTimeStamp.compareTo(this.latestValue) == 0 && this.order(this.latestValue) >= this.quorumSize()) {
                    this.state = State.COMPLETE;
                    if (this.latestValue.isInitial())
                        this.latestValue = null;
                }

                if (this.latestValue != null) {
                    if (this.latestValue.compareTo(this.latestBarrier) > 0) {
                        this.latestCandidate = this.latestValue;
                    } else {
                        this.latestCandidate = this.latestBarrier;
                    }
                } else {
                    this.latestCandidate = this.latestBarrier;
                }
            }

            return true;
        }

        /**
         * Get the {@link State} of this object history set.
         * @return
         */
        public synchronized State getState() {
            return this.state;
        }

        public synchronized int size() {
            return this.histories.size();
        }

        /**
         * Return a {@link SortedSet} consisting of the {@link BeehiveObjectId}s of the replicas in this Object History Set.
         */
        public synchronized SortedSet<BeehiveObjectId> getReplicaSet() {
            SortedSet<BeehiveObjectId> result = new TreeSet<BeehiveObjectId>(this.histories.keySet());
            return result;
        }

        /**
         * Get the universe size (3b + 2t + 1) of this ObjectHistorySet.
         */
        public int getUniverseSize() {
            return 3 * this.params.getNFaulty() + 2 * this.params.getNByzantine() + 1;
        }

        /**
         * Get the quorum size (2t + 2b + 1) of this ObjectHistorySet.
         */
        public int quorumSize() {
            return 2 * this.params.getNFaulty() + 2 * this.params.getNByzantine() + 1;
        }

        /**
         * Get the repairable size (t + b + 1) of this ObjectHistorySet.
         */
        public int repairableSize() {
            return this.params.getNFaulty() + this.params.getNByzantine() + 1;
        }

        /**
         * Return an Iterator over all the keys to the histories in this
         * ObjectHistorySet.
         */
        public synchronized Iterator<BeehiveObjectId> iterator() {
            return this.histories.keySet().iterator();
        }

        /**
         * <p>
         * Determine the number of times the specified TimeStamp occurs in this
         * ObjectHistorySet.
         * </p>
         *
         * @param timeStamp
         */
        public synchronized int order(TimeStamp timeStamp) {
            int order = 0;
            if (timeStamp != null) {
                for (BeehiveObjectId replicaId : this.histories.keySet()) {
                    for (TimeStamp t : this.histories.get(replicaId)) {
                        if (t.compareTo(timeStamp) == 0) {
                            order++;
                            // This TimeStamp should be the only one in the
                            // server's history,
                            // so we should just be done looping here.
                            // break;
                        }
                    }
                }
            }

            return order;
        }

        /**
         * From all of the TimeStamps in all the object histories in this
         * ObjectHistorySet, return the latest.
         */
        public synchronized TimeStamp getLatestTimeStamp() {
            return this.latestTimeStamp;
        }

        /**
         * Get the most recent {@link TimeStamp} from this ObjectHistorySet consisting of either the latest Barrier or latest Value.
         * If the State of this object history set is COMPLETE, the latest candidate contains the authoritative value of this MutableObject.
         */
        public synchronized TimeStamp getLatestCandidate() {
            return this.latestCandidate;
        }
    }

    /**
     * Helper class containing nested classes that are sent as payload to
     * and from replicas of this Mutable Object.
     */
    public static class SetOperation {
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;

            private MutableObject.ObjectHistorySet objectHistorySet;
            public BeehiveObjectId objectHistoryId; // XXX debugging.  I get a bad object history on the receiver side because sometimes this node is the recevier and it modifies this objectHistorySet.
            private MutableObject.Value value;

            public Request(MutableObject.ObjectHistorySet objectHistorySet, MutableObject.Value value) {
                this.objectHistorySet = objectHistorySet;
                this.objectHistoryId = objectHistorySet.getObjectId();
                this.value = value;
            }

            public MutableObject.ObjectHistorySet getObjectHistorySet() {
                return this.objectHistorySet;
            }

            public MutableObject.Value getValue() {
                return this.value;
            }
            
            public String toString() {
            	StringBuilder result = new StringBuilder("SetOperation.Request objectHistoryId=").append(this.objectHistoryId);
            	if (!this.objectHistoryId.equals(this.objectHistorySet.getObjectId())) {
            		result.append(String.format("CONFUSED OBJECT HISTORY SET id=%s\n", this.objectHistorySet.getObjectId()));
            	}
//            	result.append(String.format(" %s vs %s", this.objectHistorySet.getObjectId(), this.objectHistoryId));
            	//result.append(this.getObjectHistorySet().toString());
            	
            	return result.toString();
            }
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;

            private MutableObject.ObjectHistory objectHistory;

            public Response(MutableObject.ObjectHistory objectHistory) {
                this.objectHistory = objectHistory;
            }

            public MutableObject.ObjectHistory getObjectHistory() {
                return this.objectHistory;
            }
        }
    }

    public static class SetObjectHistoryTask implements Callable<MutableObject.ObjectHistory> {
        private MutableObject.Handler<?> handler;
        protected BeehiveObjectId replicaId;
        private MutableObject.SetOperation.Request request;
        private CountDownLatch countDown;

        public SetObjectHistoryTask(CountDownLatch countDown, MutableObject.Handler<?> handler, BeehiveObjectId replicaId, MutableObject.SetOperation.Request request) {
            this.countDown = countDown;
            this.handler = handler;
            this.replicaId = replicaId;
            this.request = request;
        }

        public MutableObject.ObjectHistory call() throws ObjectHistory.ValidationException, MutableObject.ProtocolException {
            try {
                if (handler.getLogger().isLoggable(Level.FINE)) {
                    handler.getLogger().fine("replica %s: %s", this.replicaId, request.toString());
                }
                BeehiveMessage reply = handler.getNode().sendToObject(replicaId, handler.getName(), "setObjectHistory", request);

                if (reply != null && reply.getStatus().isSuccessful()) {
                    MutableObject.SetOperation.Response response = reply.getPayload(MutableObject.SetOperation.Response.class, handler.getNode());
                    return response.getObjectHistory();
                }
                throw new MutableObject.ProtocolException("replica %s failed to reply", this.replicaId);
            } catch (ClassNotFoundException e) {
                throw new ObjectHistory.ValidationException(e);
            } catch (ClassCastException e) {
                throw new ObjectHistory.ValidationException(e);
            } catch (RemoteException e) {
                throw new ObjectHistory.ValidationException(e);
            } finally {
                if (this.countDown != null)
                    this.countDown.countDown();
            }
        }
        
        public String toString() {
            return String.format("SetObjectHistoryTask: replica=%s", this.replicaId);
        }
    }

    /**
     * Set the current value of the object history set to the given value.
     *
     * @param handler the invoking {@link MutableObject.LockType} for this operation
     * @param objectHistorySet the current {@link ObjectHistorySet}
     * @param newValue the new value to set
     * @param params the {@link MutableObject.Params} instance governing the behaviour of the mutable object
     *
     * @return the resultant {@link ObjectHistorySet}
     *
     * @throws ObjectHistory.ValidationException if an unrecoverable error occurred in the resolution of the ObjectHistorySet
     */
    public static ObjectHistorySet setObjectHistorySet(MutableObject.Handler<?> handler,
            ObjectHistorySet objectHistorySet,
            MutableObject.Value newValue,
            MutableObject.Parameters params)
    throws ObjectHistory.ValidationException, MutableObject.ProtocolException {

        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine("updating %d replicas", objectHistorySet.histories.keySet().size());
        }

        MutableObject.ObjectHistorySet result = new MutableObject.ObjectHistorySet(objectHistorySet.getMutableObjectIdObjectId(), params);
        
        MutableObject.SetOperation.Request request = new MutableObject.SetOperation.Request(objectHistorySet, newValue);

        Map<FutureTask<MutableObject.ObjectHistory>, MutableObject.SetObjectHistoryTask> taskTracker =
            new HashMap<FutureTask<MutableObject.ObjectHistory>, MutableObject.SetObjectHistoryTask>();

        // Set the CountDownLatch to the total number of replicas to update.
        // As each replica is updated, the Thread performing the update will decrement the latch.
        CountDownLatch countDown = new CountDownLatch(objectHistorySet.histories.keySet().size());
        for (BeehiveObjectId replicaId : objectHistorySet.histories.keySet()) {
            MutableObject.SetObjectHistoryTask setter = new MutableObject.SetObjectHistoryTask(countDown, handler, replicaId, request);
            FutureTask<MutableObject.ObjectHistory> task = new FutureTask<MutableObject.ObjectHistory>(setter);
            taskTracker.put(task, setter);
            handler.getNode().execute(task);
        }
        
        boolean complainedAboutWaiting = false;
        for (;;) {
            try {
                if (countDown.await(10000, TimeUnit.MILLISECONDS))
                    break;
                for (FutureTask<MutableObject.ObjectHistory> task : taskTracker.keySet()) {
                    if (!task.isDone()) {
                        SetObjectHistoryTask t = taskTracker.get(task);
                        handler.getLogger().warning("(id=%d) waiting for set of %s.",  Thread.currentThread().getId(), t.replicaId);
                    }                        
                }

                complainedAboutWaiting = true;
            } catch (InterruptedException e) {
                throw new MutableObject.ProtocolException(e);
            }
        }

        if (complainedAboutWaiting) {
            handler.getLogger().warning("(id=%d) waiting done", Thread.currentThread().getId());
        }

        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine("collating");
        }

        for (FutureTask<MutableObject.ObjectHistory> task : taskTracker.keySet()) {
            try {
                MutableObject.ObjectHistory history = task.get();
                result.put(history);
                if (handler.getLogger().isLoggable(Level.FINEST)) {
                    handler.getLogger().finest("replica %s: %s", history.getReplicaId(), result.getState());
                }
            } catch (ExecutionException e) {
                e.printStackTrace();
                // restart the protocol
                return result;
            } catch (InterruptedException e) {
                e.printStackTrace();
                // restart the protocol
                return result;
            }                
        }

        return result;
    }

    public static class CreateOperation {
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;

            private MutableObject.ObjectId mutableObjectId;
            private BeehiveObjectId replicaId;
            private BeehiveObjectId deleteTokenId;
            private long timeToLive;

            public Request(MutableObject.ObjectId objectId, BeehiveObjectId replicaId, BeehiveObjectId deleteTokenId, long timeToLive) {
                this.mutableObjectId = objectId;
                this.replicaId = replicaId;
                this.deleteTokenId = deleteTokenId;
                this.timeToLive = timeToLive;
            }

            public MutableObject.ObjectId getObjectId() {
                return this.mutableObjectId;
            }

            public BeehiveObjectId getReplicaId() {
                return this.replicaId;
            }

            public BeehiveObjectId getDeleteTokenId() {
                return this.deleteTokenId;
            }
            
            public long getTimeToLive() {
                return this.timeToLive;
            }
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;

            private ObjectHistory objectHistory;

            public Response(ObjectHistory objectHistory) {
                this.objectHistory = objectHistory;
            }

            public ObjectHistory getObjectHistory() {
                return this.objectHistory;
            }
        }
    }

    /**
     * An {@link ObjectHistory} replica fetch operation.
     * <p>
     * Operations are paired in request/response messages.
     * </p>
     *
     */
    public static class GetOperation {
        public static class Request implements Serializable {
            private final static long serialVersionUID = 1L;

            private BeehiveObjectId replicaId;

            public Request(BeehiveObjectId replicaId) {
                this.replicaId = replicaId;
            }

            public BeehiveObjectId getReplicaId() {
                return this.replicaId;
            }
        }

        public static class Response implements Serializable {
            private final static long serialVersionUID = 1L;

            private MutableObject.ObjectHistory history;

            public Response(MutableObject.ObjectHistory history) {
                if (history == null) {
                    throw new NullPointerException("parameter may not be null");
                }
                this.history = history;
            }

            public MutableObject.ObjectHistory getObjectHistory() {
                return this.history;
            }
        }
    }

    /**
     * Retrieve a specified MutableObject ObjectHistory {@code replicaId} and add it to a cumulative {@link ObjectHistorySet}.
     * <p>
     * This class implements the {@link Callable} interface returning
     * a {@link MutableObject.ObjectHistory} replica.
     * </p>
     */
    protected static class GetObjectHistoryTask implements Callable<MutableObject.ObjectHistory> {
        private MutableObject.Handler<?> handler;
        private BeehiveObjectId replicaId;
        private CountDownLatch countDown;
        private MutableObject.ObjectHistorySet objectHistorySet;

        /**
         * Retrieve the specified MutableObject replica {@code replicaId},
         * insert that replica into the given {@link ObjectHistorySet} {@code result} (the ObjectHistorySet implementation must be thread-safe)
         * and if the subsequent state of the ObjectHistorySet is COMPLETE, or the replica
         * is the last replica of the universe of replicas, the {@link CountDownLatch#countDown()} method is invoked on {@code countDown}.
         *
         * @param countDown the {@link CountDownLatch} to be count-down upon a COMPLETE ObjectHistorySet, or the last replica is added to the ObjectHistorySet.
         * @param handler the {@link BeehiveObjectHandler} instance for the MutableObject.
         * @param replicaId {@link BeehiveObjectId} of the replica to retrieve.
         */
        public GetObjectHistoryTask(CountDownLatch countDown, MutableObject.Handler<?> handler, BeehiveObjectId replicaId, MutableObject.ObjectHistorySet result) {
            this.countDown = countDown;
            this.handler = handler;
            this.replicaId = replicaId;
            this.objectHistorySet = result;
        }

        public MutableObject.ObjectHistory call() throws ObjectHistory.ValidationException {
            try {
                BeehiveMessage reply = this.handler.getNode().sendToObject(replicaId, this.handler.getName(), "getObjectHistory", new MutableObject.GetOperation.Request(replicaId));

                if (reply != null) {
                    if (reply.getStatus().isSuccessful()) {
                        try {
                            MutableObject.GetOperation.Response response = reply.getPayload(MutableObject.GetOperation.Response.class, this.handler.getNode());
                            MutableObject.ObjectHistory history = response.getObjectHistory();

                            if (history != null) {
                                // If the state of the ObjectHistorySet we're working on is not yet COMPLETE,
                                // add the retrieved ObjectHistory to it.  Otherwise, if it is COMPLETE
                                // and there are still running tasks retrieving other instances of
                                // ObjectHistory, try to preempt them because they are now unnecessary.
                                // XXX It's reasonable to stop fetching replicas when the state of the ObjectHistorySet is COMPLETE or repairable.
                                if (objectHistorySet.getState() != ObjectHistorySet.State.COMPLETE) {
                                    if  (objectHistorySet.put(history)) {
                                        if (handler.getLogger().isLoggable(Level.FINEST)) {
                                            handler.getLogger().finest("replica %s: %s", history.getReplicaId(), objectHistorySet.getState());
                                        }
                                    }
                                }

                                if (objectHistorySet.getState() == ObjectHistorySet.State.COMPLETE && this.countDown.getCount() != 0) {
                                    // Since there are enough replicas to have a COMPLETE state,
                                    // this forces a preemptive end to fetching the rest of the replicas.
                                    for (int i = 0; i < objectHistorySet.getUniverseSize(); i++) {
                                        this.countDown.countDown();
                                    }
                                }
                            }

                            return history;
                        } catch (RemoteException e) {
                            throw new ObjectHistory.ValidationException(e);
                        }
                    }
                }
                return null;
            } catch (ClassNotFoundException e) {
                throw new ObjectHistory.ValidationException(e);
            } finally {
                if (this.countDown != null)
                    this.countDown.countDown();
            }
        }

        @Override
        public String toString() {
            return String.format("GetObjectHistoryTask: replica=%s", this.replicaId);
        }
    }

    protected static class CreateObjectHistoryTask implements Callable<MutableObject.ObjectHistory> {
        private MutableObject.Handler<?> handler;
        private BeehiveObjectId replicaId;
        private MutableObject.ObjectId objectId;
        private BeehiveObjectId destinationNodeId;
        private CountDownLatch countDown;
        private int replicaIndex;
        private BeehiveObjectId deleteTokenId;
        private ObjectHistory[] histories;
        private long timeToLive;

        /**
         * Create an initial object history set on the specified Beehive node.
         *<p>
         * The conditions this can encounter are:
         * </p>
         * <ul>
         * <li>
         * The object is created on the specified node.
         * The resulting {@link ObjectHistory} is returned.
         * </li>
         * <li>
         * The object already exists on the specified node and can be replaced.
         * The resulting {@link ObjectHistory} is returned.
         * </li>
         * <li>
         * The specified node cannot be found. NoSuchNodeException thrown.
         * </li>
         * <li>
         * The object already exists on the specified node and cannot be replaced.
         * AlreadyExistsException
         * </li>
         * <li>
         * The object already exists elsewhere in the system as a consequence of the
         * specified node's attempt to store the object the root of the object
         * signals that the object is NOT to be stored.
         * AlreadyExistsException
         * </li>
         * </ul>
         * <p>
         * Note that there is no guard against trying to create the object
         * history replica when one already exists in the system.  This should
         * be addressed by at least trying to fetch the proposed object history
         * to see if it exists before creating it on this node.
         * </p>
         * <p>
         * If a copy exists, but is currently offline, when it comes back online
         * there will be two instances of the same replica.  An algorithm for
         * choosing a winner or merging needs to be developed.
         * </p>
         * @param handler
         * @param destinationNodeId
         * @param objectId
         * @param replicaId
         */
        public CreateObjectHistoryTask(MutableObject.Handler<?> handler,
                CountDownLatch countDown,
                BeehiveObjectId destinationNodeId,
                MutableObject.ObjectId objectId,
                BeehiveObjectId replicaId,
                int replicaIndex,
                BeehiveObjectId deleteTokenId,
                ObjectHistory[] histories,
                long timeToLive) {
            this.countDown = countDown;
            this.handler = handler;
            this.replicaId = replicaId;
            this.objectId = objectId;
            this.destinationNodeId = destinationNodeId;
            this.replicaIndex = replicaIndex;
            this.deleteTokenId = deleteTokenId;
            this.histories = histories;
            this.timeToLive = timeToLive;
        }

        private static int debugLatch = 5;

        /**
         *
         * @throws ObjectHistory.ValidationException
         * @throws MutableObject.ExistenceException if the {@link ObjectHistory} already exists in the object pool.
         * @throws BeehiveNode.NoSuchNodeException
         */
        public MutableObject.ObjectHistory call() throws ObjectHistory.ValidationException, MutableObject.ExistenceException, BeehiveNode.NoSuchNodeException {

            if (handler.getLogger().isLoggable(Level.FINEST)) {
                handler.getLogger().finest("replica[%d] %s on node %s", this.replicaIndex, this.replicaId, this.destinationNodeId);
            }
            try {
//                if (this.replicaIndex == 3) {
//                    if (debugLatch > 0) {
//                        // induce a failure;
//                        debugLatch--;
//                        throw new BeehiveNode.NoSuchNodeException("debugging only");
//                    }
//                }

                BeehiveMessage reply = handler.getNode().sendToNodeExactly(this.destinationNodeId, handler.getName(), "createObjectHistory",
                    new CreateOperation.Request(this.objectId, this.replicaId, this.deleteTokenId, this.timeToLive));

                if (reply != null) {
                    if (reply.getStatus().isSuccessful()) {
                        try {
                            MutableObject.CreateOperation.Response response = reply.getPayload(MutableObject.CreateOperation.Response.class, handler.getNode());
                            this.histories[this.replicaIndex] = response.getObjectHistory();
                            return this.histories[this.replicaIndex];
                        } catch (RemoteException e) {
                            e.printStackTrace();
                            throw new MutableObject.ExistenceException(e);
                        }
                    }

                    if (reply.getStatus().equals(DOLRStatus.CONFLICT)) {
                        throw new MutableObject.ExistenceException(String.format("MutableObject history %s already exists on node %s", this.replicaId, this.destinationNodeId));
                    }
                    // The remote node had some problem creating the replica.
                    throw new ObjectHistory.ValidationException("Creating ObjectHistory node=%s replica=%s: %s", this.destinationNodeId, this.replicaId, reply.getStatus());
                }
                throw new ObjectHistory.ValidationException("Node %s replied with null.", this.destinationNodeId);
            } catch (ClassNotFoundException e) {
                throw new ObjectHistory.ValidationException(e);
            } finally {
                if (this.countDown != null)
                    this.countDown.countDown();
            }
        }
        
        public String toString() {
            return "CreateObjectHistoryTask " + this.replicaId.toString(); 
        }
    }

    /**
     * Create an {@link ObjectHistorySet} for the given {@code objectId}.
     * <p>
     * </p>
     *
     * @param handler
     * @param params the parameters for the query/update protocol used to
     *          maintain the authoritative value of the mutable object
     * @param objectId the global object-id for the mutable-object to create.
     * @throws MutableObject.ObjectHistory.ValidationException
     * @throws MutableObject.ProtocolException
     */

    private static MutableObject.ObjectHistorySet createObjectHistorySet(MutableObject.Handler<?> handler,
            MutableObject.Parameters params,
            MutableObject.ObjectId objectId,
            BeehiveObjectId deleteTokenId,
            long timeToLive)
    throws MutableObject.ObjectHistory.ValidationException, MutableObject.InsufficientResourcesException, MutableObject.ProtocolException {

        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine(objectId.toString());
        }

        // Create an ObjectHistorySet to store our replicas in.
        MutableObject.ObjectHistorySet initialObjectHistorySet = new MutableObject.ObjectHistorySet(objectId, params);

        // Create |U| ObjectHistory instances and store them.
        //
        Census census = handler.getNode().getService(CensusDaemon.class);

        int universeSize = initialObjectHistorySet.getUniverseSize();

        HashSet<BeehiveObjectId> usedNodes = new HashSet<BeehiveObjectId>();

        ObjectHistory[] histories = new ObjectHistory[universeSize];

        if (handler.getLogger().isLoggable(Level.FINEST)) {
            handler.getLogger().finest("universeSize=%d historySetSize=%d historyUniverseSize=%d",
                universeSize, initialObjectHistorySet.size(), initialObjectHistorySet.getUniverseSize());
        }

        while (true) {
            LinkedList<BeehiveObjectId> nodeIds = new LinkedList<BeehiveObjectId>(census.select(initialObjectHistorySet.getUniverseSize(), usedNodes, null).keySet());

            if (handler.getLogger().isLoggable(Level.FINEST)) {
                handler.getLogger().finest("Creating on %d nodes", nodeIds.size());
            }

            CountDownLatch countDown = new CountDownLatch(histories.length);

            Map<FutureTask<MutableObject.ObjectHistory>, MutableObject.CreateObjectHistoryTask> taskTracker =
                new HashMap<FutureTask<MutableObject.ObjectHistory>, MutableObject.CreateObjectHistoryTask>();

            // Loop through the histories and for each one that is not filled, submit a corresponding create operation.
            for (int i = 0; i < histories.length; i++) {
                if (handler.getLogger().isLoggable(Level.FINEST)) {
                    handler.getLogger().finest("replica[%d] %s", i, histories[i]);
                }
                if (histories[i] == null) {
                    BeehiveObjectId replicaId = objectId.add(i);
                    if (nodeIds.size() == 0)
                    	throw new MutableObject.InsufficientResourcesException();
                    // XXX Note that nodeIds may not contain histories.length elements and this will throw an exception.
                    BeehiveObjectId nodeId = nodeIds.remove();

                    usedNodes.add(nodeId);
                    MutableObject.CreateObjectHistoryTask t = new MutableObject.CreateObjectHistoryTask(handler, countDown, nodeId, objectId, replicaId, i, deleteTokenId, histories, timeToLive);
                    FutureTask<MutableObject.ObjectHistory> task = new FutureTask<MutableObject.ObjectHistory>(t);
                    taskTracker.put(task, t); // track these for debugging messages below.
                    handler.getNode().execute(task);
                } else {
                    countDown.countDown();
                }
            }

            if (handler.getLogger().isLoggable(Level.FINEST)) {
                handler.getLogger().finest("waiting for countDown=%d task size=%d", countDown.getCount(), taskTracker.size());
            }
            // If there were no tasks created, then all of the histories have been created. So stop.
            if (taskTracker.size() == 0)
                break;

            boolean complained = false;
            for (;;) {
                try {
                    if (countDown.await(10000, TimeUnit.MILLISECONDS))
                        break;
                    for (Map.Entry<FutureTask<MutableObject.ObjectHistory>, MutableObject.CreateObjectHistoryTask> entry : taskTracker.entrySet()) {
                        FutureTask<MutableObject.ObjectHistory> task = entry.getKey(); 
                        if (!task.isDone()) {
                            MutableObject.CreateObjectHistoryTask t = entry.getValue();
                            handler.getLogger().warning("(id=%d) waiting for replica %s to be stored on node %s.", Thread.currentThread().getId(), task.toString(), t.replicaId, t.destinationNodeId);
                        }                        
                    }

                    complained = true;
                } catch (InterruptedException e) {
                    throw new MutableObject.ProtocolException(e);
                }
            }

            if (complained) {
                handler.getLogger().warning("(id=%d) waiting done.", Thread.currentThread().getId());
            }

            for (FutureTask<MutableObject.ObjectHistory> task : taskTracker.keySet()) {
                try {
                    MutableObject.ObjectHistory history = task.get();
                    initialObjectHistorySet.put(history);
                    if (handler.getLogger().isLoggable(Level.FINEST)) {
                        handler.getLogger().finest("replica %s OHS size=%d %s", history.getObjectId(), initialObjectHistorySet.size(), initialObjectHistorySet.getState());
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }

        return initialObjectHistorySet;
    }

    /**
     * <p>
     * Get the {@link ObjectHistorySet} of the given {@code objectId} of the MutableObject.
     * </p>
     * <p>
     * Collect the set of {@link ObjectHistory} replicas for the {@link MutableObject}
     * {@code objectId} from the object pool.
     * </p>
     *
     * @param handler The ObjectType implementation that implements {@link MutableObject.LockType}.
     *
     * @param params The parameters specifying at least the number of faulty and byzantine replicas to tolerate.
     * @param objectId The {@link MutableObject.ObjectId} used as the key to the value stored in the replicas.
     *
     * @throws MutableObject.ProtocolException
     *
     * @return The {@link ObjectHistorySet} of responses.
     */
    public static MutableObject.ObjectHistorySet getObjectHistorySet(MutableObject.Handler<?> handler, MutableObject.Parameters params, MutableObject.ObjectId objectId)
    throws MutableObject.ProtocolException {
        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine(objectId.toString());
        }

        MutableObject.ObjectHistorySet result = new MutableObject.ObjectHistorySet(objectId, params);

        // Set countDown to the quorum's universe size.
        // Each GetObjectHistory instance decrements countDown whether or not it was successful.
        // If a GetObjectHistory instance causes the ObjectHistorySet to become complete,
        // it issues all of the count-downs in order to release the await() invocation.
        CountDownLatch countDown = new CountDownLatch(result.getUniverseSize());

        Map<FutureTask<MutableObject.ObjectHistory>, MutableObject.GetObjectHistoryTask> taskTracker =
            new HashMap<FutureTask<MutableObject.ObjectHistory>, MutableObject.GetObjectHistoryTask>();

        for (int r = 0; r < result.getUniverseSize() && result.getState() != ObjectHistorySet.State.COMPLETE; r++) {
            BeehiveObjectId replicaId = objectId.add(r);
            MutableObject.GetObjectHistoryTask getter = new MutableObject.GetObjectHistoryTask(countDown, handler, replicaId, result);
            FutureTask<MutableObject.ObjectHistory> task = new FutureTask<MutableObject.ObjectHistory>(getter);
            taskTracker.put(task, getter);
            handler.getNode().execute(task);
        }

        boolean complained = false;        
        for (;;) {
            try {
                if (countDown.await(10000, TimeUnit.MILLISECONDS))
                    break;
                for (FutureTask<MutableObject.ObjectHistory> task : taskTracker.keySet()) {
                    if (!task.isDone()) {
                        GetObjectHistoryTask t = taskTracker.get(task);
                        handler.getLogger().warning("(id=%d) waiting for retrieve of %s.",  Thread.currentThread().getId(), t.replicaId);
                    }                        
                }

                complained = true;
            } catch (InterruptedException e) {
                throw new MutableObject.ProtocolException(e);
            }
        }

        if (handler.getLogger().isLoggable(Level.WARNING)) {
            if (complained) {
                handler.getLogger().warning("(id=%d) waiting done.", Thread.currentThread().getId());
            }
        }

        return result;
    }


    public static class MappableGetObjectHistory extends AbstractMapFunction<BeehiveObjectId,MutableObject.ObjectHistory> {
        private CountDownLatch countDown;
        private MutableObject.Handler<?> handler;
        private MutableObject.ObjectHistorySet objectHistorySet;

        public MappableGetObjectHistory(MutableObject.Handler<?> handler, ExecutorService threads, MutableObject.ObjectHistorySet objectHistorySet) {
            super(threads);
            this.countDown = new CountDownLatch(objectHistorySet.getUniverseSize());
            this.handler = handler;
            this.objectHistorySet = objectHistorySet;
        }

        /**
         *
         */
        public class Function extends AbstractMapFunction<BeehiveObjectId,MutableObject.ObjectHistory>.Function {
            private MutableObject.Handler<?> handler;
            private MutableObject.ObjectHistorySet objectHistorySet;

            public Function(MutableObject.Handler<?> handler, MutableObject.ObjectHistorySet objectHistorySet, BeehiveObjectId item, CountDownLatch countDown) {
                super(item, countDown);
                this.handler = handler;
                this.objectHistorySet = objectHistorySet;
            }

            @Override
            public MutableObject.ObjectHistory function(BeehiveObjectId item) throws ObjectHistory.ValidationException/*, Exception*/ {
                try {
                    BeehiveMessage reply = this.handler.getNode().sendToObject(item, this.handler.getName(), "getObjectHistory", new MutableObject.GetOperation.Request(item));

                    if (reply != null) {
                        if (reply.getStatus().isSuccessful()) {
                            MutableObject.GetOperation.Response response = reply.getPayload(MutableObject.GetOperation.Response.class, this.handler.getNode());
                            MutableObject.ObjectHistory history = response.getObjectHistory();
                            // this.handler.getLogger().info("%s %s", history, replicaId);

                            if (this.objectHistorySet != null) {
                                if (history != null) {
                                    this.objectHistorySet.put(history);
                                    // handler.getLogger().info("new %s history=%s", result.state, history.toString());

                                    if (this.objectHistorySet.getState() == ObjectHistorySet.State.COMPLETE) {
                                        if (handler.getLogger().isLoggable(Level.FINE)) {
                                            handler.getLogger().fine("preemptive termination of quroum gets size=%d", this.objectHistorySet.size());
                                        }
                                        // If there are enough entries in the ObjectHistorySet, force completion by decrementing the countdown.
                                        // What needs to happen is the remaining running threads need to be shutdown AND decrement the CountDownLatch.
                                        for (int i = 0; i < this.objectHistorySet.getUniverseSize(); i++) {
                                            this.countDown.countDown();
                                        }
                                    }
                                }
                            }
                            return history;
                        }
                    }
                    return null;
                } catch (ClassNotFoundException e) {
                    throw new ObjectHistory.ValidationException(e);
                } catch (RemoteException e) {
                    throw new ObjectHistory.ValidationException(e);
                } finally {
//                    if (this.countDown != null)
//                        this.countDown.countDown();
                }
            }
        }

        @Override
        public AbstractMapFunction<BeehiveObjectId,MutableObject.ObjectHistory>.Function newFunction(BeehiveObjectId item) {
            return this.new Function(this.handler, this.objectHistorySet, item, this.countDown);
        }

    }

    /**
     * Create the specified MutableObject
     *
     * @param handler the {@link AbstractObjectHandler} handler that is creating this mutable-object.
     * @param objectId the {@link BeehiveObjectId} of this mutable-object.
     * @param params the parameters controlling the internal management of the variable.
     *
     * @throws MutableObject.InsufficientResourcesException if there are
     *          insufficient resources in the system to create the mutable-object.
     * @throws MutableObject.ExistenceException if the mutable-object already exists.
     * @throws MutableObject.ProtocolException
     */
    public static MutableObject.Value createValue(MutableObject.Handler<?> handler, MutableObject.ObjectId objectId, BeehiveObjectId deleteTokenId, MutableObject.Parameters params, long timeToLive)
    throws MutableObject.InsufficientResourcesException, MutableObject.ExistenceException, MutableObject.ProtocolException {
        //
        // Create the initial set of object replicas.
        //
        try {
            MutableObject.ObjectHistorySet objectHistorySet = MutableObject.createObjectHistorySet(handler, params, objectId, deleteTokenId, timeToLive);
            while (true) {
                if (handler.getLogger().isLoggable(Level.FINEST)) {
                    handler.getLogger().finest("state=%s", objectHistorySet.getState());
                }
                if (objectHistorySet.getState() == ObjectHistorySet.State.INCONCLUSIVE) {
                    throw new MutableObject.ExistenceException("MutableObject %s already exists.%n", objectId);
                }

                if (objectHistorySet.getState() == MutableObject.ObjectHistorySet.State.COMPLETE) {
                    TimeStamp latestTimeStamp = objectHistorySet.getLatestCandidate();
                    if (latestTimeStamp == null) {
                        return null;
                    }
                    // This code should actually signal failure because the
                    // newly created set must not contain a value.

                    if (handler.getLogger().isLoggable(Level.WARNING)) {
                        handler.getLogger().warning("%s already contains a value.", objectId);
                    }
                    MutableObject.Value value = latestTimeStamp.getValue();
                    return value;
                }
                TimeStamp latestTimeStamp = objectHistorySet.getLatestCandidate();
                // What we use for a value here doesn't matter. The servers will
                // ignore it.
                MutableObject.Value value = latestTimeStamp == null ? null : latestTimeStamp.getValue();
                objectHistorySet = MutableObject.setObjectHistorySet(handler, objectHistorySet, value, params);
            }
        } catch (MutableObject.ObjectHistory.ValidationException recoverable) {
            recoverable.printStackTrace();
            /* XXX this should be recoverable */
        }

        return null;
    }    
    
    /**
     * Helper method to delete an existing MutableObject.
     * 
     * @param handler
     * @param objectId
     * @param params
     * @param secondsToLive
     * @throws MutableObject.ObjectHistory.ValidationException 
     * @throws MutableObject.PredicatedValueException 
     * @throws MutableObject.DeletedException 
     */
    public static void deleteValue(MutableObject.Handler<?> handler, MutableObject.ObjectId objectId, BeehiveObjectId deleteToken, MutableObject.Parameters params, long secondsToLive)
    throws MutableObject.InsufficientResourcesException, MutableObject.NotFoundException, MutableObject.ProtocolException, MutableObject.PredicatedValueException,
    MutableObject.ObjectHistory.ValidationException, MutableObject.DeletedException {
        // Mark the object history as deleted (no more updates permitted).
        // Set the time-to-live value on each replica object to the given timeToLive;
        
        while (true) {
            // Get the current value
            // Set the new value to the current value plus marked as deleted.
            MutableObject.Value oldValue = MutableObject.getValue(handler, objectId, params);
            MutableObject.Value newValue = oldValue.clone().setDeleteToken(deleteToken, secondsToLive);
            MutableObject.setValue(handler, objectId, oldValue, newValue, params);

            break;
        }
    }

    /**
     * Helper method to get the value of an existing MutableObject.
     * 
     * @param handler 
     * @param objectId
     * @param params
     * @return
     * @throws MutableObject.InsufficientResourcesException
     * @throws MutableObject.NotFoundException
     * @throws MutableObject.ProtocolException
     */
    public static MutableObject.Value getValue(MutableObject.Handler<?> handler, MutableObject.ObjectId objectId, MutableObject.Parameters params)
    throws MutableObject.InsufficientResourcesException, MutableObject.NotFoundException, MutableObject.ProtocolException {

        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine(objectId.toString());
        }

        try {
            MutableObject.ObjectHistorySet objectHistorySet = MutableObject.getObjectHistorySet(handler, params, objectId);
            while (true) {
                if (handler.getLogger().isLoggable(Level.FINEST)) {
                    handler.getLogger().finest("state=%s", objectHistorySet.getState());
                }
                if (objectHistorySet.getState() == ObjectHistorySet.State.INCONCLUSIVE) {
                    throw new MutableObject.InsufficientResourcesException("Inconclusive object history");
                }

                if (objectHistorySet.getState() == MutableObject.ObjectHistorySet.State.COMPLETE) {
                    TimeStamp latestTimeStamp = objectHistorySet.getLatestCandidate();
                    if (latestTimeStamp == null) {
                        if (handler.getLogger().isLoggable(Level.FINEST)) {
                            handler.getLogger().finest("%s latestTimeStamp==null", objectId);
                        }
                        //throw new MutableObject.NotFoundException("%s not found.", objectId);
                        return null;
                    }
                    return latestTimeStamp.getValue();
                }

                TimeStamp latestTimeStamp = objectHistorySet.getLatestCandidate();
                // The function of setObjectHistory "ratchets the handle" on the protocol.
                // What we use for a value here doesn't matter because the servers will
                // ignore it because the ObjectHistorySet must come to agreement by repair
                // or barrier/copy, not by an update to the value.
                MutableObject.Value value = latestTimeStamp == null ? null : latestTimeStamp.getValue();
                objectHistorySet = MutableObject.setObjectHistorySet(handler, objectHistorySet, value, params);
            }
        } catch (MutableObject.ObjectHistory.ValidationException recoverable) {
            recoverable.printStackTrace();
            /* XXX this should be recoverable */
        }

        throw new MutableObject.NotFoundException("%s not found.", objectId);
    }

    /**
     * Set the value of a MutableObject.
     * 
     * @param handler 
     * @param objectId
     * @param predicatedValue
     * @param newValue
     * @param params
     * @return the new value of the MutableObject identified by {@code objectId}.
     * @throws MutableObject.PredicatedValueException
     * @throws MutableObject.ObjectHistory.ValidationException
     * @throws MutableObject.InsufficientResourcesException
     * @throws MutableObject.ProtocolException
     * @throws MutableObject.DeletedException 
     */
    public static MutableObject.Value setValue(MutableObject.Handler<?> handler, MutableObject.ObjectId objectId,
            MutableObject.Value predicatedValue, MutableObject.Value newValue, MutableObject.Parameters params)
    throws MutableObject.PredicatedValueException,
            MutableObject.ObjectHistory.ValidationException,
            MutableObject.InsufficientResourcesException,
            MutableObject.ProtocolException, MutableObject.DeletedException {

        if (handler.getLogger().isLoggable(Level.FINE)) {
            handler.getLogger().fine("%s", objectId);
        }

        MutableObject.ObjectHistorySet objectHistorySet = MutableObject.getObjectHistorySet(handler, params, objectId);

        while (true) {
            if (handler.getLogger().isLoggable(Level.FINEST)) {
                handler.getLogger().finest("state=%s", objectHistorySet.getState());
            }
            if (objectHistorySet.getState() == ObjectHistorySet.State.INCONCLUSIVE) {
                throw new MutableObject.InsufficientResourcesException();
            }

            if (objectHistorySet.getState() != MutableObject.ObjectHistorySet.State.COMPLETE) {
                // XXX Is this correct?  Check this out...
                objectHistorySet = MutableObject.setObjectHistorySet(handler, objectHistorySet, newValue, params);
            } else if (objectHistorySet.getState() == MutableObject.ObjectHistorySet.State.COMPLETE) {
                // The object history set is self-consistent and all servers
                // agree on the current value/state.
                // If the predicated value is not equal to the current value, fail.
                TimeStamp latestTimeStamp = objectHistorySet.getLatestCandidate();
                // If the latestTimeStamp is null, then the predicate MUST also be null.
                if (latestTimeStamp == null) {
                    if (predicatedValue != null) {
                        throw new MutableObject.PredicatedValueException(String.format("%s Latest TimeStamp is null, predicated value is not.", objectId));
                    }
                } else {
                    MutableObject.Value currentValue = latestTimeStamp.getValue();
                    if (currentValue.getDeleteToken() != null) {
                        if (handler.getLogger().isLoggable(Level.WARNING)) {
                            throw new MutableObject.DeletedException("Attempted to modify a deleted MutableObject: %s", objectId);
                        }
                    }
                    if (predicatedValue == null) {
                        throw new MutableObject.PredicatedValueException(String.format("%s Predicated value is null, latest TimeStamp is not.", objectId));
                    } else if (!currentValue.equals(predicatedValue)) {
                        throw new MutableObject.PredicatedValueException(String.format("%s Predicated value not equal to latest TimeStamp.", objectId));
                    }
                    // Everything is fine at this point....
                }

                // Compose the new value/state and transmit it to the servers
                objectHistorySet = MutableObject.setObjectHistorySet(handler, objectHistorySet, newValue, params);
                // If the resulting ObjectHistorySet is in the COMPLETE state, we are done.
                // Otherwise, repeat the process from the top...
                if (objectHistorySet.getState() == MutableObject.ObjectHistorySet.State.COMPLETE) {
                    return newValue;
                }
            }
        }
    }

    /*
     * Used in the main below.
     * and in debugging code elsewhere.
     */
    public static class GenericObjectValue extends MutableObject.Value {
        private static final long serialVersionUID = 1L;
        private Object v;

        public GenericObjectValue(Object v) {
            this.v = v;
        }

        public GenericObjectValue clone() {
            return new GenericObjectValue(this.v);
        }
        
        public String format() {
            return this.v.toString();
        }

		public EFlow toXHTML(URI uri, Map<String, HTTP.Message> props) {
			// TODO Auto-generated method stub
			return null;
		}

    }

    public static void main(String[] args) throws Exception {
        ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream("/tmp/deletethis"));
        ObjectHistory h = new ObjectHistory();
        o.writeObject(h);

        MutableObject.ObjectId objectId = (MutableObject.ObjectId) new BeehiveObjectId();
        BeehiveObjectId clientId = new BeehiveObjectId();

        // Be careful in using a single ObjectHistory and ObjectHistorySet
        // instances because some of these operations will change the
        // instance resulting in a new hash signature for the instance.
        // Since predicated values are based on previous hashes, what
        // appears to be a simple update will break the protocol.
        // Therefore there is liberal use of the special method "dup"
        // to compose separate instances of one object instance.

        // We simulate a set of 6 unique nodes here.
        // Each "node" has an ObjectHistory.
        ObjectHistory[] node = new ObjectHistory[6];

        // Initialise the object-history-sets
        for (int i = 0; i < node.length; i++) {
            node[i] = new ObjectHistory(objectId, new BeehiveObjectId());
            node[i].add(new TimeStamp());
        }

        // Create the overall object-history-set with the object-history for
        // each node.
        ObjectHistorySet ohs;

        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());
        System.out.println("Should be COMPLETE: " + ohs.getState());

        // Now start messing around with the object-history-set
        // Create a condition which looks like a partial update resulting in a
        // BARRIER.
        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        System.out.println("Update node: " + node[0].getReplicaId());

        ohs.put(node[0].dup());
        System.out.println("Should be BARRIER: " + ohs.getState());
        // System.out.print(ohs.toString());
        // System.out.println("-----------");

        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[1] = node[1].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[2] = node[2].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[3] = node[3].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[4] = node[4].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));

        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());

        // At this point the BARRIER should be established and the only next
        // thing to do is a copy.
        System.out.println("Should be COPY: " + ohs.getState()); // Should be COPY
        // System.out.print(ohs.toString());
        // System.out.println("-----------");

        // We are still trying to set the value, but this iteration performs the
        // COPY
        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[1] = node[1].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[2] = node[2].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[3] = node[3].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[4] = node[4].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());

        System.out.println("Should be COMPLETE " + ohs.getState());
        System.out.printf("Value=%s\n", (ohs.getLatestCandidate().getValue() == null) ? "null" : ohs.getLatestCandidate().getValue().toString());
        // System.out.println(ohs);
        // System.out.println("-----------");

        // We are still trying to set the value, but this iteration performs the
        // COPY
        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[1] = node[1].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[2] = node[2].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[3] = node[3].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        node[4] = node[4].setValue(null, clientId, ohs.dup(), new GenericObjectValue("a"));
        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());
        System.out.println("Should be COMPLETE " + ohs.getState());
        System.out.println("Value=" + ohs.getLatestCandidate().getValue());
        // System.out.println(ohs);
        // System.out.println("-----------");

        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[1] = node[1].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[2] = node[2].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());
        System.out.println("Should be REPAIRABLE " + ohs.getState());
        System.out.println(ohs.toString());
        System.out.println("-----------");

        node[0] = node[0].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[1] = node[1].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[2] = node[2].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[3] = node[3].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        node[4] = node[4].setValue(null, clientId, ohs.dup(), new GenericObjectValue("ab"));
        ohs = new ObjectHistorySet(objectId, new MutableObject.Params("1,1"));
        ohs.put(node[0].dup());
        ohs.put(node[1].dup());
        ohs.put(node[2].dup());
        ohs.put(node[3].dup());
        ohs.put(node[4].dup());
        System.out.println("Should be COMPLETE");
        System.out.println(ohs);
        System.out.println("-----------");

        // Establish a "super quorum"
        // ohs.put(oh[5]);
        // System.out.println(ohs);

        // System.out.println(ohs.classify());
    }
}
