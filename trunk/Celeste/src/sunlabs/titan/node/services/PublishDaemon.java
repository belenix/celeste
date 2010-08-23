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
package sunlabs.titan.node.services;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.UnpublishObjectMessage;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.services.api.Publish;

/**
 * The Beehive Node Publish Object Daemon
 *
 * <p>
 * Perform two independent functions:
 * <ol>
 * <li>
 * Iterate through the local object store backpointer list and for each
 * backpointer and subtract the value of the current publish rate from the
 * time-to-live field.  If the result is less than or equal to the negative
 * value of the refresh rate (ie.  one refresh cycle has passed since the
 * time-to-live reached zero), then delete the backpointer.  Otherwise, update
 * the time-to-live value in the publisher's record.
 * </li>
 * <li>
 * Continuously iterate through through the local object store publishing each
 * object, pausing for {@link PublishDaemon#PublishObjectInterstitialSleepMillis} milliseconds between each publish message.
 * Every object has an associated object-type specified in the stored object's metadata.
 * </li>
 * </ol>
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public final class PublishDaemon extends BeehiveService implements Publish, PublishDaemonMBean {
    private final static long serialVersionUID = 1L;
    private final static String name = BeehiveService.makeName(PublishDaemon.class, PublishDaemon.serialVersionUID);

    /**
     * The number of seconds between each iteration of the object publishing task.
     * Setting this value to less than 1 causes the publisher to never run.
     * This can be useful when debugging and running publishing task manually through the node inspector.
     */
    public final static Attributes.Prototype PublishPeriodSeconds = new Attributes.Prototype(PublishDaemon.class,
            "PublishPeriodSeconds",
            Time.minutesInSeconds(10),
            "The number of seconds to delay after completing an object publish cycle, to starting the next one.");
    public final static Attributes.Prototype PublishObjectInterstitialSleepMillis = new Attributes.Prototype(PublishDaemon.class,
            "PublishObjectInterstitialSleepMillis",
            0,
            "The number of milliseconds to delay between each publish message from this node.");

    /**
     * The number of milliseconds between each iteration of the object back-pointer expiration task.
     * Setting this value to less than 1 causes the expiration to never run.
     * This can be useful when debugging and running expiration task manually through the node inspector.
     */
    private final static Attributes.Prototype ExpirePeriodSeconds = new Attributes.Prototype(PublishDaemon.class,
            "ExpirePeriodSeconds",
            Time.minutesInSeconds(10),
            "The number of seconds to delay after completing an publish record expiration cycle, to starting the next one.");

    transient private PublishLocalObjectDaemon publishDaemon;
    transient private ScheduledFuture<?> publishDaemonFuture;
    transient private ExpireBackpointerDaemon expireDaemon;
    transient private ScheduledFuture<?> expireDaemonFuture;
    transient private ScheduledThreadPoolExecutor threadPool;

    private class SimpleThreadFactory implements ThreadFactory {
    	private String name;
    	private long counter;
    	private ThreadGroup threadGroup;

    	public SimpleThreadFactory(ThreadGroup group, String name) {
    		this.threadGroup = group;
    		this.name = name;
    		this.counter = 0;
    	}

    	public Thread newThread(Runnable r) {
    		Thread thread = new Thread(this.threadGroup, r);
    		thread.setName(String.format("%s-pool-%d", this.name, this.counter));
    		this.counter++;
    		return thread;
    	}
    }

    public PublishDaemon(final BeehiveNode node)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(node, PublishDaemon.name, "Publish Objects in the Object Store");

        node.configuration.add(PublishDaemon.PublishObjectInterstitialSleepMillis);
        node.configuration.add(PublishDaemon.PublishPeriodSeconds);
        node.configuration.add(PublishDaemon.ExpirePeriodSeconds);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.configuration.get(PublishDaemon.PublishObjectInterstitialSleepMillis));
            this.log.config("%s", node.configuration.get(PublishDaemon.PublishPeriodSeconds));
            this.log.config("%s", node.configuration.get(PublishDaemon.ExpirePeriodSeconds));
        }

        this.expireDaemon = new ExpireBackpointerDaemon();
        this.publishDaemon = new PublishLocalObjectDaemon();

        this.threadPool = new ScheduledThreadPoolExecutor(2, new SimpleThreadFactory(PublishDaemon.this.node.getThreadGroup(), PublishDaemon.this.node.getObjectId() + "." + PublishDaemon.this.getName()));
    }
    
    /**
     * This implementation is a generic unpublish of an object, typically used where an
     * object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     */
    public BeehiveMessage unpublishObject(BeehiveMessage message) {
        try {
            PublishDaemon.UnpublishObject.Request request = message.getPayload(PublishDaemon.UnpublishObject.Request.class, this.getNode());
            if (this.log.isLoggable(Level.FINEST)) {                
                this.log.finest("%s", request.getObjectIds());
            }

            BeehiveMessage response = message.composeReply(this.node.getNodeAddress());
            return response;
        } catch (ClassCastException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return message.composeReply(this.node.getNodeAddress(), e);
        }
    }
    

    private class ExpireBackpointerDaemon implements Runnable {
        /** The duration of time (in milliseconds) the last full publish of the object store consumed. */
        protected long elapsedMillis;
        /** The duration of time (in milliseconds) the last full iteration consumed */
        protected long startTime;
        /** The total number of object publisher records examined. */
        protected long objectCount;
        
        private Boolean busy = false;

        ExpireBackpointerDaemon() {
        }

        public void run() {
        	try {
        		synchronized (this.busy) {
        			if (this.busy) {
        				if (PublishDaemon.this.log.isLoggable(Level.WARNING)) {
        					PublishDaemon.this.log.warning("already busy");
        					System.err.println("already busy");
        				}
        				return;                         
        			}
        			this.busy = true;
        		}
        		if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        			PublishDaemon.this.log.finest("start");
        		}
        		
        		this.startTime = System.currentTimeMillis();

        		// For each object-id in the ObjectPublisher back-pointer list, examine
        		// the list of publishers, decrementing the time-to-live for each by
        		// the frequency in seconds of this repeating process.  If the
        		// time-to-live is zero or negative, remove that publisher from the list of
        		// publishers.
        		long count = 0;
        		for (BeehiveObjectId objectId : PublishDaemon.this.node.getObjectPublishers().keySet()) {
        			Set<Publishers.PublishRecord> publishers = PublishDaemon.this.node.getObjectPublishers().getPublishersAndLock(objectId);
        			try {
                        if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
                            PublishDaemon.this.log.finest("begin %s %d publishers", objectId, publishers.size());
                        }
        				boolean modified = false;
        				Set<Publishers.PublishRecord> newPublishers = new HashSet<Publishers.PublishRecord>(publishers);
        				for (Publishers.PublishRecord record : publishers) {
        					long secondsUntilExpired = record.getExpireTimeSeconds() - Time.currentTimeInSeconds();
        					if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        						PublishDaemon.this.log.finest("%s %s expireTime=%d dt=%+d", objectId, record.getNodeAddress().getObjectId(), record.getExpireTimeSeconds(), secondsUntilExpired);
        					}
        					if (secondsUntilExpired <= 0) {
        						newPublishers.remove(record);
        						modified = true;
        					}
        				}
        				if (modified) {
        					if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        						PublishDaemon.this.log.finest("end %s %d publishers", objectId, newPublishers.size());
        					}
        					PublishDaemon.this.node.getObjectPublishers().put(objectId, newPublishers);
        				}
        			} finally {
        				PublishDaemon.this.node.getObjectPublishers().unlock(objectId);
        			}
        		}

        		this.objectCount = count;
        		this.elapsedMillis = (System.currentTimeMillis() - startTime);
        		
        		if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        			PublishDaemon.this.log.finest("stop %dms", this.elapsedMillis);
        		}

        		synchronized (this.busy) {
        			this.busy = false;
        		}
        		return;
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
    }

    public interface PublishLocalObjectDaemonMBean extends ThreadMBean {

    }

    /**
     * This class iterates through the local object store and for each object it computes the absolute
     * expiration time of the object based on the object's creation time and the object's stated time-to-live.
     * If the object is out of time-to-live, it is removed from the object store.
     * Finally, if the object has been removed, this node will emit a corresponding
     * {@link UnpublishObjectMessage} otherwise, a {@link PublishObjectMessage} is sent.
     */
    private class PublishLocalObjectDaemon implements Runnable {
        /** The duration of time (in milliseconds) the last full publish of the object store consumed. */
        protected long elapsedMillis;

        protected long objectCount;
        //        private ObjectName jmxObjectName;
        public Integer publishIteration = 0;
        private Boolean busy = false;
        
        PublishLocalObjectDaemon() {
        }

        public void run() {
        	synchronized (this.busy) {
        		if (this.busy) {
        			if (PublishDaemon.this.log.isLoggable(Level.WARNING)) {
        				PublishDaemon.this.log.warning("already busy");
        			}
        			return;                         
        		}
        		this.busy = true;
        	}
        	// During the first iteration through the objects, go as fast possible.
        	long publishObjectInterstitialSleepTime = 0;

        	ObjectStore objectStore = PublishDaemon.this.node.getObjectStore();


        	long count = 0;
        	long startTime = System.currentTimeMillis();

        	if (PublishDaemon.this.log.isLoggable(Level.FINE)) {
        		PublishDaemon.this.log.fine("publishing w/inter-object sleep %dms", publishObjectInterstitialSleepTime);
        	}
        	for (BeehiveObjectId objectId : objectStore) {
        		if (objectId == null) {
        			PublishDaemon.this.log.warning("Got a null object-id from ObjectStore.iterator()");
        			continue;
        		}

        		try {
        			// The unlockAndPublish method in BeehiveObjectStore produces
        			// the Publish or Unpublish messages.  The act of unlocking
        			// a locked object induces a PublishObjectMessage or UnpublishObjectMessage
        			// depending on whether the object exists in the object store or does not.

        			if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        				PublishDaemon.this.log.finest("%s", objectId.toString());
        			}
        			//
        			// Try to get a lock on each object. If that fails, just skip it because whatever Thread has it
        			// locked will eventually unlock it and that will result in a publish or unpublish of that object.
        			//
        			BeehiveObject object = objectStore.tryGetAndLock(BeehiveObject.class, objectId);
        			if (object != null) {
        				try {
        					if (object.getRemainingSecondsToLive(Time.currentTimeInSeconds()) < 1) {
        						if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        							PublishDaemon.this.log.finest("expiring %s", objectId.toString());
        						}
        						objectStore.remove(object);
        					}
        				} finally {
        					objectStore.unlock(object);
        				}
        			} else {
        				if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        					PublishDaemon.this.log.finest("already locked %s", objectId.toString());
        				}
        			}
        		} catch (BeehiveObjectStore.NotFoundException e) {
        			// skip this object...
        		}

        		count++;
        		if (publishObjectInterstitialSleepTime > 0) {
        			// Induces some time delay between each message.
        			synchronized (this) {
        				try {
        					this.wait(publishObjectInterstitialSleepTime);
        				} catch (InterruptedException e) {
        					e.printStackTrace();
        				}
        			}
        		}
        	}

        	this.objectCount = count;

        	this.elapsedMillis = (System.currentTimeMillis() - startTime);

        	publishObjectInterstitialSleepTime = PublishDaemon.this.node.configuration.asLong(PublishDaemon.PublishObjectInterstitialSleepMillis);

        	this.publishIteration++;
        	if (PublishDaemon.this.log.isLoggable(Level.FINE)) {
        		PublishDaemon.this.log.fine("iteration# %d, %d objects, elapsed-time=%dms", this.publishIteration, this.objectCount, this.elapsedMillis);
        	}
        	
        	synchronized (this.busy) {
        		this.busy = false;
        	}
        }
    }

    /**
     * Get the number of seconds that a publish record for an object on this node should be kept
     * before it is to be expired.
     * This node is responsible for refreshing the publish records before they are expired.
     */
    private long getPublishRecordSecondsToLive() {
    	// If the PublishPeriodSeconds less than 1, then the publish task will not run automatically.
    	// Since this is typically for debugging, I arbitrarily claim that records should live for 5 minutes.
    	if (PublishDaemon.this.node.configuration.asLong(PublishDaemon.PublishPeriodSeconds) < 1) {
    		return Time.minutesInSeconds(5);
    	}
    	return PublishDaemon.this.node.configuration.asLong(PublishDaemon.PublishPeriodSeconds) * 2;
    }
    
    @Override
    public synchronized void start() {
    	this.setStatus("initializing");

    	if (this.node.configuration.asInt(PublishDaemon.ExpirePeriodSeconds) > 0) {
    		this.expireDaemonFuture = this.threadPool.scheduleWithFixedDelay(
    				this.expireDaemon,
    				node.configuration.asInt(PublishDaemon.ExpirePeriodSeconds),
    				node.configuration.asInt(PublishDaemon.ExpirePeriodSeconds),
    				TimeUnit.SECONDS
    		);
    	} else {
    		this.log.warning("%s is %d, expiration must be done manually.",
    				PublishDaemon.ExpirePeriodSeconds,
    				this.node.configuration.asInt(PublishDaemon.ExpirePeriodSeconds));
    	}

        if (this.node.configuration.asInt(PublishDaemon.PublishPeriodSeconds) > 0) {
                this.publishDaemonFuture = this.threadPool.scheduleWithFixedDelay(
                                this.publishDaemon,
                                node.configuration.asInt(PublishDaemon.PublishPeriodSeconds),
                                node.configuration.asInt(PublishDaemon.PublishPeriodSeconds),
                                TimeUnit.SECONDS
                );
        } else {
                this.log.warning("%s is %d, publishing must be done manually.",
                                PublishDaemon.PublishPeriodSeconds,
                                this.node.configuration.asInt(PublishDaemon.PublishPeriodSeconds));             
        }

        // There was an idea here to not return from this method until all of the objects in this nodes object store were published.
        // But that would mean that other nodes looking for the objects wouldn't be able to connect to this node to get them.
        this.setStatus("running");
    }

    @Override
    public void stop() {
//        this.setStatus("stopping");
//        if (this.publishDaemon != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.publishDaemon);
//            }
//            this.publishDaemon.interrupt(); // Logged
//            this.publishDaemon = null;
//        }
//        if (this.expireDaemon != null) {
//            if (this.log.isLoggable(Level.INFO)) {
//                this.log.info("Interrupting Thread %s%n", this.expireDaemon);
//            }
//            this.expireDaemon.interrupt(); // Logged
//            this.expireDaemon = null;
//        }
//        this.setStatus("stopped");
    }

    public void jmxSetPublishObjectInterstitialSleepTime(long time) {
        // It is best if this the period is less than the amount of time
        // that a socket to out neighbour(s) remains valid.  If it is
        // longer, then the socket may be closed and new sockets will
        // have to be created to handle the publishing.
        this.node.configuration.set(PublishDaemon.PublishObjectInterstitialSleepMillis, time);
//
//        if (PublishDaemon.this.jmxObjectNameRoot != null) {
//            this.sendJMXNotification("publishPeriod", oldValue, time);
//        }
    }

    public long jmxGetPublishObjectInterstitialSleepTime() {
        return this.node.configuration.asLong(PublishDaemon.PublishObjectInterstitialSleepMillis);
    }

    public synchronized void jmxSetExpirePeriod(long periodMillis) {
        this.node.configuration.set(PublishDaemon.PublishObjectInterstitialSleepMillis, periodMillis);
        if (PublishDaemon.this.jmxObjectNameRoot != null) {
//            this.sendJMXNotification("expirePeriod", oldValue, period);
        }
    }

    public long jmxGetExpirePeriodSeconds() {
        return this.node.configuration.asLong(PublishDaemon.ExpirePeriodSeconds);
    }

    public void jmxPublishNow() {
        this.threadPool.execute(this.publishDaemon);
    }

    public void jmxExpireNow() {
        this.threadPool.execute(this.expireDaemon);
    }

    /**
     * Publish the availability of the given {@link BeehiveObject}.
     * <p>
     * A {@link PublishObjectMessage} is composed and routed through the local node to the node that is the root of the object's identifier.
     * The reply {@link BeehiveMessage} is returned.
     * </p>
     */
    public BeehiveMessage publish(BeehiveObject object) {
    	long publishRecordSecondsToLive = Math.min(this.getPublishRecordSecondsToLive(), object.getRemainingSecondsToLive(Time.currentTimeInSeconds()));

    	if (this.log.isLoggable(Level.FINEST)) {
    		this.log.finest("%s objectTTL=%ds recordTTL=%ds", object.getObjectId(), object.getRemainingSecondsToLive(Time.currentTimeInSeconds()), publishRecordSecondsToLive);
    	}
   
    	PublishDaemon.PublishObject.Request publishRequest = new PublishDaemon.PublishObject.Request(this.node.getNodeAddress(), publishRecordSecondsToLive, object);

        // this.node.sendPublishObjectMessage(BeehiveObject object);
        // this.node.sendPublishObjectMessage(object.getObjectId(), object.getProperty(BeehiveObjectStore.METADATA_TYPE),
        //      "publishObject",
        //      new AbstractBeehiveObject(BeehiveObjectId.ANY, metaData));
        PublishObjectMessage message = new PublishObjectMessage(this.node.getNodeAddress(), object.getObjectId(), object.getObjectType(), "publishObject", publishRequest);

        BeehiveMessage reply = this.node.receive(message);

        return reply;
    }
    
    public BeehiveMessage unpublish(BeehiveObjectId objectId, UnpublishObject.Type type) {
    	PublishDaemon.UnpublishObject.Request request = new PublishDaemon.UnpublishObject.Request(objectId, type);
    	if (this.log.isLoggable(Level.FINEST)) {
    		this.log.finest("%s", objectId);
    	}
    	
        BeehiveMessage message = new UnpublishObjectMessage(this.node.getNodeAddress(), objectId, PublishDaemon.name, "unpublishObject", request);
        message.setTraced(true);

        return this.node.receive(message);
    }

    public BeehiveMessage unpublish(BeehiveObject object, UnpublishObject.Type type) {
        PublishDaemon.UnpublishObject.Request request = new PublishDaemon.UnpublishObject.Request(object.getObjectId(), type);
        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("%s %s", object.getObjectId(), object.getObjectType());
        }
        
        BeehiveMessage message = new UnpublishObjectMessage(this.node.getNodeAddress(), object.getObjectId(), object.getObjectType(), "unpublishObject", request);
        message.setTraced(true);

        return this.node.receive(message);
    }    

    public static class GetPublishers implements Serializable {
		private static final long serialVersionUID = 1L;
		
		public static class Request /*extends AbstractBeehiveObjectHandler.Request*/ implements Serializable {
			private static final long serialVersionUID = 1L;

			private BeehiveObjectId objectId;
			
			public Request(BeehiveObjectId objectId) {
				this.objectId = objectId;
			}

			public BeehiveObjectId getObjectId() {
				return objectId;
			}
    	}
		
    	public static class Response implements Serializable {
    		private static final long serialVersionUID = 1L;
    		private Set<Publishers.PublishRecord> publishers;
    		
    		public Response(Set<Publishers.PublishRecord> publishers) {
    			this.publishers = publishers;
    		}
    		
    		public Set<Publishers.PublishRecord> getPublishers() {
    			return this.publishers;
    		}
    	}
    }

    public BeehiveMessage getPublishers(BeehiveMessage message) throws ClassCastException, ClassNotFoundException, RemoteException {
        GetPublishers.Request request = message.getPayload(GetPublishers.Request.class, this.node);

        Set<Publishers.PublishRecord> publishers = PublishDaemon.this.node.getObjectPublishers().getPublishers(request.getObjectId());

        return message.composeReply(this.node.getNodeAddress(), new GetPublishers.Response(publishers)); 
    }
	
    public Set<Publishers.PublishRecord> getPublishers(BeehiveObjectId objectId) throws ClassNotFoundException, ClassCastException {
		GetPublishers.Request request = new GetPublishers.Request(objectId);

        BeehiveMessage reply = PublishDaemon.this.node.sendToNode(objectId, PublishDaemon.this.getName(), "getPublishers", request);

        GetPublishers.Response response;
		try {
			response = reply.getPayload(GetPublishers.Response.class, this.node);
		} catch (RemoteException e) {
			Throwable cause = e.getCause();
			if (cause instanceof ClassCastException) {
				throw (ClassCastException) cause;
			} else {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
        return response.getPublishers();
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
    	String action = HttpMessage.asString(props.get("action"), null);
    	if (action != null) {
    		if (action.equals("stop")) {
    			this.stop();
    		} else if (action.equals("start")) {
    			this.start();
    		} else if (action.equals("set-config")) {
    			this.log.config("Set run-time parameters: loggerLevel=" + this.log.getEffectiveLevel().toString());
    		} else if (action.equals("publishNow")) {
    			this.jmxPublishNow();
    		} else if (action.equals("expireNow")) {
    			this.jmxExpireNow();
    		}
    	}
    	XHTML.Button expireNow = new XHTML.Button("Expire Now").setType(XHTML.Button.Type.SUBMIT)
    	.setName("action").setValue("expireNow").setTitle("Remove expired publish records now");

    	XHTML.Button publishNow = new XHTML.Button("Publish Now").setType(XHTML.Button.Type.SUBMIT)
    	.setName("action").setValue("publishNow").setTitle("Publish local object-store now");

    	XHTML.Table.Body tbody = new XHTML.Table.Body();
    	for (String name : this.node.configuration.keySet()) {
    		if (name.startsWith(PublishDaemon.class.getCanonicalName())) {
    			tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(name), new XHTML.Table.Data(String.valueOf(this.node.configuration.get(name).getValue()))));
    		}
    	}
    	XHTML.Table configurationTable = new XHTML.Table(new XHTML.Table.Caption("Configuration Values"), tbody).addClass("striped");

    	XHTML.Table publish = new XHTML.Table(new XHTML.Table.Caption("Publish Thread"),
    			new XHTML.Table.Body(
    					new XHTML.Table.Row(new XHTML.Table.Data("Next Publish Time"),
    							new XHTML.Table.Data(this.publishDaemonFuture == null
    									? "manual"
    									: WebDAVDaemon.formatTime(this.publishDaemonFuture.getDelay(TimeUnit.MILLISECONDS) + System.currentTimeMillis()))),
						new XHTML.Table.Row(new XHTML.Table.Data("&Delta; Time"),
								new XHTML.Table.Data(this.publishDaemonFuture == null
										? ""
    									: Time.formattedElapsedTime(this.publishDaemonFuture.getDelay(TimeUnit.MILLISECONDS)))),
    					new XHTML.Table.Row(new XHTML.Table.Data("Elapsed Time"),
    							new XHTML.Table.Data(Time.formattedElapsedTime(this.publishDaemon.elapsedMillis))),
    					new XHTML.Table.Row(new XHTML.Table.Data("# Objects"),
    							new XHTML.Table.Data(this.publishDaemon.objectCount)),
    							new XHTML.Table.Row(new XHTML.Table.Data(""),
    					new XHTML.Table.Data(publishNow))
    			)
    	);

    	XHTML.Table expire = new XHTML.Table(new XHTML.Table.Caption("Expire Thread"),
    			new XHTML.Table.Body(
    					new XHTML.Table.Row(new XHTML.Table.Data("Next Expire Time"),
    							new XHTML.Table.Data(this.expireDaemonFuture == null
    									? "manual"
    									: WebDAVDaemon.formatTime(this.expireDaemonFuture.getDelay(TimeUnit.MILLISECONDS) + System.currentTimeMillis())
    							)),
    					new XHTML.Table.Row(new XHTML.Table.Data("&Delta; Time"),
    							new XHTML.Table.Data(this.expireDaemonFuture == null
    									? ""
    									: Time.formattedElapsedTime(this.expireDaemonFuture.getDelay(TimeUnit.MILLISECONDS))
    							)),
    					new XHTML.Table.Row(new XHTML.Table.Data("Elapsed Time"),
    							new XHTML.Table.Data(Time.formattedElapsedTime(this.expireDaemon.elapsedMillis))),
    					new XHTML.Table.Row(new XHTML.Table.Data("# Objects"),
    							new XHTML.Table.Data(this.expireDaemon.objectCount)),
    					new XHTML.Table.Row(new XHTML.Table.Data(""),
    							new XHTML.Table.Data(expireNow))
    			)
    	);

    	XHTML.Table t = new XHTML.Table(
    			new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(publish), new XHTML.Table.Data(expire), new XHTML.Table.Data(configurationTable))
    			)).setClass("controls");

    	XHTML.Form form = new XHTML.Form("").setMethod("get").setEncodingType("application/x-www-url-encoded");
    	form.add(t);

    	XHTML.Div div = (XHTML.Div) super.toXHTML(uri, props);
    	div.add(new XHTML.Div(form).setClass("section"));
    	div.add(new XHTML.Div(this.node.getObjectPublishers().toXHTML(uri, props)).setClass("section"));

    	return div;
    }
    
    /**
     * Messaging concerning object publishing.
     */
    public static class PublishObject {
    	/**
    	 * A message from a {@link BeehiveNode} publishing the availability of one or more {@link BeehiveObject} instances.
    	 */
        public static class Request implements sunlabs.titan.node.services.api.Publish.Request {
            private static final long serialVersionUID = 1L;
            
            private Map<BeehiveObjectId,BeehiveObject.Metadata> objects;
            private NodeAddress publisher;
            private long secondsToLive;

            /**
             * If {@code true} this Publish is a backup for the root of the object's
             * {@link BeehiveObjectId} and signals the helper method
             * {@link AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Request)}
             * to not continue making backup back-pointers.
             */
            private boolean backup;

            /**
             * Construct a Request containing the {@code NodeAddress} of the {@code BeehiveNode} publishing the given objects.
             *  
             * @param publisher The {@code NodeAddress} of the {@link BeehiveNode} publishing the objects.
             * @param secondsToLive The number of seconds each publish record should exist.
             * @param object One or more {@link BeehiveObject} instances to publish.
             */
            public Request(NodeAddress publisher, long secondsToLive, BeehiveObject...object) {
            	this.objects = new HashMap<BeehiveObjectId,BeehiveObject.Metadata>();
            	for (BeehiveObject o : object) {
            		this.objects.put(o.getObjectId(), o.getMetadata());
            	}
            	
            	this.publisher = publisher;
            	this.secondsToLive = secondsToLive;            	
            }

            /**
             * If {@code true} this Publish is a backup for the root of the object's
             * {@link BeehiveObjectId} and signals the helper method
             * {@link AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Request)}
             * to <b>not</b> continue making backup back-pointers.
             */
            public boolean isBackup() {
                return this.backup;
            }
            
            /**
             * Set the backup flag to {@code value}.
             * See {@link #isBackup()}
             */
            public void setBackup(boolean value) {
                this.backup = value;
            }
            
            /**
             * Get the {@link NodeAddress} of the publisher of this Request.
             */
            public NodeAddress getPublisherAddress() {
            	return this.publisher;
            }

            /**
             * Get the map of {@link BeehiveObject}s in this Request.
             */
            public Map<BeehiveObjectId,BeehiveObject.Metadata> getObjectsToPublish() {
            	return this.objects;
            }

            /**
             * Get the number of seconds a publish record created from this Request should exist.
             */
            public long getSecondsToLive() {
                return this.secondsToLive;
            }
        }

        public static class Response implements sunlabs.titan.node.services.api.Publish.Response, Serializable {
            private static final long serialVersionUID = 1L;
            private Set<BeehiveObjectId> objectIds;

            public Response() {
                this.objectIds = new HashSet<BeehiveObjectId>();
            }

            public Response(Set<BeehiveObjectId> objectIds) {
                this.objectIds = objectIds;
            }
            
            public Set<BeehiveObjectId> getObjectIds() {
                return this.objectIds;
            }
        }
    }
    
    /**
     * 
     *
     */
    public static class UnpublishObject {
        public enum  Type { OPTIONAL, REQUIRED };
        /**
         * A message from a node unpublishing the availability of a {@link BeehiveObject}.
         */
    	public static class Request implements Serializable {
    		private final static long serialVersionUID = 1L;

    		private UnpublishObject.Type type;
    		private Collection<BeehiveObjectId> objectIds;
    		
    		public Request(BeehiveObjectId objectId, UnpublishObject.Type type) {
    		    this.type = type;
    			this.objectIds = new LinkedList<BeehiveObjectId>();
    			this.objectIds.add(objectId);
    		}
    		
    		public UnpublishObject.Type getType() {
    		    return this.type;
    		}
    		
    		public Collection<BeehiveObjectId> getObjectIds() {
    			return this.objectIds;
    		}
    	}
    	
    	public static class Response implements Serializable {
    		private final static long serialVersionUID = 1L;
    		
    		public Response() {
    			
    		}
    	}
    }
}
