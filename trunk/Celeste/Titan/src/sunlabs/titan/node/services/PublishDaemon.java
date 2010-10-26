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
package sunlabs.titan.node.services;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.jmx.ThreadMBean;
import sunlabs.asdf.util.Attributes;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.titan.api.ObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.PublishObjectMessage;
import sunlabs.titan.node.Publishers;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.TitanMessage.RemoteException;
import sunlabs.titan.node.TitanNodeIdImpl;
import sunlabs.titan.node.UnpublishObjectMessage;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.TitanObjectHandler;
import sunlabs.titan.node.services.api.Publish;

/**
 * The Titan Node Publish Object Daemon
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
public final class PublishDaemon extends AbstractTitanService implements Publish, PublishDaemonMBean {
    private final static long serialVersionUID = 1L;
    private final static String name = AbstractTitanService.makeName(PublishDaemon.class, PublishDaemon.serialVersionUID);

    /**
     * The number of seconds between each iteration of the object publishing task.
     * Setting this value to less than 1 causes the publisher to never run.
     * This can be useful when debugging and running publishing task manually through the node inspector.
     */
    public final static Attributes.Prototype PublishPeriodSeconds = new Attributes.Prototype(PublishDaemon.class,
            "PublishPeriodSeconds",
            Time.minutesInSeconds(60),
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

    public PublishDaemon(final TitanNode node) throws JMException {
        super(node, PublishDaemon.name, "Publish Objects in the Object Store");

        node.getConfiguration().add(PublishDaemon.PublishObjectInterstitialSleepMillis);
        node.getConfiguration().add(PublishDaemon.PublishPeriodSeconds);
        node.getConfiguration().add(PublishDaemon.ExpirePeriodSeconds);

        if (this.log.isLoggable(Level.CONFIG)) {
            this.log.config("%s", node.getConfiguration().get(PublishDaemon.PublishObjectInterstitialSleepMillis));
            this.log.config("%s", node.getConfiguration().get(PublishDaemon.PublishPeriodSeconds));
            this.log.config("%s", node.getConfiguration().get(PublishDaemon.ExpirePeriodSeconds));
        }

        this.expireDaemon = new ExpireBackpointerDaemon();
        this.publishDaemon = new PublishLocalObjectDaemon();

        this.threadPool = new ScheduledThreadPoolExecutor(2, new SimpleThreadFactory(PublishDaemon.this.node.getThreadGroup(), PublishDaemon.this.node.getNodeId() + "." + PublishDaemon.this.getName()));
    }
    
    /**
     * This implementation is a generic unpublish of an object, typically used where an
     * object was not found and the node transmits a remedial unpublish message to the rest
     * of the system to remove any spurious back-pointers for the object that point to this node.
     */
    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException {
        Publish.PublishUnpublishRequest request = message.getPayload(Publish.PublishUnpublishRequest.class, this.getNode());
        if (this.log.isLoggable(Level.FINEST)) {                
            this.log.finest("%s", request);
        }
        
        // There is nothing to do, the backpointers will be removed in TitanNode.receive() method(s).

        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
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
        	    // We don't permit this method to be run more than once at a time.
        		synchronized (this.busy) {
        			if (this.busy) {
        				if (PublishDaemon.this.log.isLoggable(Level.WARNING)) {
        					PublishDaemon.this.log.warning("already busy");
        				}
        				return;                         
        			}
        			this.busy = true;
        		}
        		if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        			PublishDaemon.this.log.finest("start");
        		}
        		
        		this.startTime = System.currentTimeMillis();
        		
        		this.objectCount = PublishDaemon.this.node.getObjectPublishers().expire(PublishDaemon.this.log);

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
        	for (TitanGuid objectId : objectStore) {
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
        			TitanObject object = objectStore.tryGetAndLock(TitanObject.class, objectId);
        			if (object != null) {
        				try {
        					if (object.getRemainingSecondsToLive(Time.currentTimeInSeconds()) < 1) {
        						if (PublishDaemon.this.log.isLoggable(Level.FINEST)) {
        							PublishDaemon.this.log.finest("expiring %s", objectId.toString());
        						}
        						objectStore.remove(object);
        					}
        				} finally {
        					try {
                                objectStore.unlock(object);
                            } catch (ClassNotFoundException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (BeehiveObjectStore.Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (BeehiveObjectPool.Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
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

        	publishObjectInterstitialSleepTime = PublishDaemon.this.node.getConfiguration().asLong(PublishDaemon.PublishObjectInterstitialSleepMillis);

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
    	if (PublishDaemon.this.node.getConfiguration().asLong(PublishDaemon.PublishPeriodSeconds) < 1) {
    		return Time.minutesInSeconds(5);
    	}
    	return PublishDaemon.this.node.getConfiguration().asLong(PublishDaemon.PublishPeriodSeconds) * 2;
    }
    
    @Override
    public synchronized void start() {
        if (this.isStarted()) {
            return;
        }
        super.start();

    	if (this.node.getConfiguration().asInt(PublishDaemon.ExpirePeriodSeconds) > 0) {
    		this.expireDaemonFuture = this.threadPool.scheduleWithFixedDelay(
    				this.expireDaemon,
    				node.getConfiguration().asInt(PublishDaemon.ExpirePeriodSeconds),
    				node.getConfiguration().asInt(PublishDaemon.ExpirePeriodSeconds),
    				TimeUnit.SECONDS
    		);
    	} else {
    		this.log.warning("%s is %d, expiration must be done manually.",
    				PublishDaemon.ExpirePeriodSeconds,
    				this.node.getConfiguration().asInt(PublishDaemon.ExpirePeriodSeconds));
    	}

        if (this.node.getConfiguration().asInt(PublishDaemon.PublishPeriodSeconds) > 0) {
                this.publishDaemonFuture = this.threadPool.scheduleWithFixedDelay(
                                this.publishDaemon,
                                node.getConfiguration().asInt(PublishDaemon.PublishPeriodSeconds),
                                node.getConfiguration().asInt(PublishDaemon.PublishPeriodSeconds),
                                TimeUnit.SECONDS
                );
        } else {
                this.log.warning("%s is %d, publishing must be done manually.",
                                PublishDaemon.PublishPeriodSeconds,
                                this.node.getConfiguration().asInt(PublishDaemon.PublishPeriodSeconds));             
        }

        // There was an idea here to not return from this method until all of the objects in this nodes object store were published.
        // But that would mean that other nodes looking for the objects wouldn't be able to connect to this node to get them.
        this.setStatus("running");
    }

    @Override
    public void stop() {
        super.stop();
    }

    public void jmxSetPublishObjectInterstitialSleepTime(long time) {
        // It is best if this the period is less than the amount of time
        // that a socket to out neighbour(s) remains valid.  If it is
        // longer, then the socket may be closed and new sockets will
        // have to be created to handle the publishing.
        this.node.getConfiguration().set(PublishDaemon.PublishObjectInterstitialSleepMillis, time);
//
//        if (PublishDaemon.this.jmxObjectNameRoot != null) {
//            this.sendJMXNotification("publishPeriod", oldValue, time);
//        }
    }

    public long jmxGetPublishObjectInterstitialSleepTime() {
        return this.node.getConfiguration().asLong(PublishDaemon.PublishObjectInterstitialSleepMillis);
    }

    public synchronized void jmxSetExpirePeriod(long periodMillis) {
        this.node.getConfiguration().set(PublishDaemon.PublishObjectInterstitialSleepMillis, periodMillis);
        if (PublishDaemon.this.jmxObjectNameRoot != null) {
//            this.sendJMXNotification("expirePeriod", oldValue, period);
        }
    }

    public long jmxGetExpirePeriodSeconds() {
        return this.node.getConfiguration().asLong(PublishDaemon.ExpirePeriodSeconds);
    }

    public void jmxPublishNow() {
        this.threadPool.execute(this.publishDaemon);
    }

    public void jmxExpireNow() {
        this.threadPool.execute(this.expireDaemon);
    }

    /**
     * Publish the availability of the given {@link TitanObject}.
     * <p>
     * A {@link PublishObjectMessage} is composed and routed through the local node to the node that is the root of the object's identifier.
     * The {@link TitanObjectHandler#publishObject(TitanMessage)} method in the {@code BeehiveObjectHandler} corresponding to the given {@link TitanObject}'s
     * class is invoked and its reply returned.
     * The reply {@link TitanMessage} is returned.
     * </p>
     */
    public PublishDaemon.PublishObject.PublishUnpublishResponseImpl publish(TitanObject object) throws ClassCastException, ClassNotFoundException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception {
        long publishRecordSecondsToLive = Math.min(this.getPublishRecordSecondsToLive(), object.getRemainingSecondsToLive(Time.currentTimeInSeconds()));

        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("%s objectTTL=%ds recordTTL=%ds", object.getObjectId(), object.getRemainingSecondsToLive(Time.currentTimeInSeconds()), publishRecordSecondsToLive);
        }

        PublishDaemon.PublishObject.PublishUnpublishRequestImpl publishRequest = new PublishDaemon.PublishObject.PublishUnpublishRequestImpl(this.node.getNodeAddress(), publishRecordSecondsToLive, object);

        PublishObjectMessage message = new PublishObjectMessage(this.node.getNodeAddress(), object.getObjectId(), object.getObjectType(), "publishObject", publishRequest);

        TitanMessage reply = this.node.receive(message);

        try {
            PublishDaemon.PublishObject.PublishUnpublishResponseImpl response = reply.getPayload(PublishDaemon.PublishObject.PublishUnpublishResponseImpl.class, this.node);
            return response;
        } catch (RemoteException e) {
            // We know that the "publishObject" method is defined to throw ClassNotFoundException, ClassCastException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception
            if (e.getCause() instanceof BeehiveObjectPool.Exception)
                throw (BeehiveObjectPool.Exception) e.getCause();
            if (e.getCause() instanceof BeehiveObjectStore.Exception)
                throw (BeehiveObjectStore.Exception) e.getCause();
            throw new IllegalArgumentException(e);
        }
    }
    
    public TitanMessage unpublish(TitanGuid objectId) {
    	Publish.PublishUnpublishRequest request = new PublishDaemon.PublishObject.PublishUnpublishRequestImpl(this.node.getNodeAddress(), objectId);
    	if (this.log.isLoggable(Level.FINEST)) {
    		this.log.finest("%s", objectId);
    	}
    	
        TitanMessage message = new UnpublishObjectMessage(this.node.getNodeAddress(), objectId, PublishDaemon.name, "unpublishObject", request);

        return this.node.receive(message);
    }

    public Publish.PublishUnpublishResponse unpublish(TitanObject object) throws ClassCastException, ClassNotFoundException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception {
        Publish.PublishUnpublishRequest request = new PublishDaemon.PublishObject.PublishUnpublishRequestImpl(this.node.getNodeAddress(), 0L, object);
        if (this.log.isLoggable(Level.FINEST)) {
            this.log.finest("%s %s", object.getObjectId(), object.getObjectType());
        }

        TitanMessage message = new UnpublishObjectMessage(this.node.getNodeAddress(), object.getObjectId(), object.getObjectType(), "unpublishObject", request);
        message.setTraced(true);

        TitanMessage reply = this.node.receive(message);
        try {
            Publish.PublishUnpublishResponse result = reply.getPayload(Publish.PublishUnpublishResponse.class, this.node);
            return result;
        } catch (TitanMessage.RemoteException e) {
            // We know that the "unpublishObject" method is defined to throw ClassNotFoundException, ClassCastException, BeehiveObjectPool.Exception, BeehiveObjectStore.Exception
            if (e.getCause() instanceof BeehiveObjectPool.Exception)
                throw (BeehiveObjectPool.Exception) e.getCause();
            if (e.getCause() instanceof BeehiveObjectStore.Exception)
                throw (BeehiveObjectStore.Exception) e.getCause();
            throw new IllegalArgumentException(e);
        }
    }    

    public static class GetPublishers implements Serializable {
		private static final long serialVersionUID = 1L;
		
		public static class Request /*extends AbstractBeehiveObjectHandler.Request*/ implements Serializable {
			private static final long serialVersionUID = 1L;

			private TitanGuid objectId;
			
			public Request(TitanGuid objectId) {
				this.objectId = objectId;
			}

			public TitanGuid getObjectId() {
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

    public TitanMessage getPublishers(TitanMessage message, GetPublishers.Request request) throws ClassCastException, ClassNotFoundException {
        Set<Publishers.PublishRecord> publishers = PublishDaemon.this.node.getObjectPublishers().getPublishers(request.getObjectId());

        return message.composeReply(this.node.getNodeAddress(), new GetPublishers.Response(publishers)); 
    }
	
    public Set<Publishers.PublishRecord> getPublishers(TitanGuid objectId) throws ClassNotFoundException, ClassCastException {
		GetPublishers.Request request = new GetPublishers.Request(objectId);

		// Send a message to the root node -- the node closest to the given object id.
        TitanMessage reply = PublishDaemon.this.node.sendToNode(new TitanNodeIdImpl(objectId), PublishDaemon.this.getName(), "getPublishers", request);

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
    	for (String name : this.node.getConfiguration().keySet()) {
    		if (name.startsWith(PublishDaemon.class.getCanonicalName())) {
    			tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(name),
    			        new XHTML.Table.Data(String.valueOf(this.node.getConfiguration().get(name).getValue()))));
    		}
    	}
    	XHTML.Table configurationTable = new XHTML.Table(new XHTML.Table.Caption("Configuration Values"), tbody).addClass("striped");

    	XHTML.Table publish = new XHTML.Table(new XHTML.Table.Caption("Publish Thread"),
    			new XHTML.Table.Body(
    					new XHTML.Table.Row(new XHTML.Table.Data("Next Publish Time"),
    							new XHTML.Table.Data(this.publishDaemonFuture == null
    									? "manual"
    									: Time.ISO8601(this.publishDaemonFuture.getDelay(TimeUnit.MILLISECONDS) + System.currentTimeMillis()))),
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
    									: Time.ISO8601(this.expireDaemonFuture.getDelay(TimeUnit.MILLISECONDS) + System.currentTimeMillis()))),
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
    	 * A message from a {@link TitanNode} publishing the availability of one or more {@link TitanObject} instances.
    	 */
        public static class PublishUnpublishRequestImpl implements sunlabs.titan.node.services.api.Publish.PublishUnpublishRequest {
            private static final long serialVersionUID = 1L;
            
            private Map<TitanGuid,TitanObject.Metadata> objects;
            private NodeAddress publisher;
            private long secondsToLive;

            /**
             * If {@code true} this Publish is a backup for the root of the object's
             * {@link TitanGuid} and signals the helper method
             * {@link AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Publish.PublishUnpublishRequest)}
             * to not continue making backup back-pointers.
             */
            private boolean backup;

            /**
             * Construct a {@link sunlabs.titan.node.services.api.Publish.PublishUnpublishRequest PublishUnpublishRequest}
             * containing the {@link NodeAddress} of the {@code TitanNode} publishing the given objects.
             * 
             * @param publisher The {@link NodeAddress} of the {@link TitanNode} publishing the objects.
             * @param secondsToLive The number of seconds each publish record should exist.
             * @param object One or more {@link TitanObject} instances to publish.
             */
            public PublishUnpublishRequestImpl(NodeAddress publisher, long secondsToLive, TitanObject...object) {
            	this.objects = new HashMap<TitanGuid,TitanObject.Metadata>();
            	for (TitanObject o : object) {
            		this.objects.put(o.getObjectId(), o.getMetadata());
            	}
            	
            	this.publisher = publisher;
            	this.secondsToLive = secondsToLive;            	
            }
            
            /**
             * Construct a {@link sunlabs.titan.node.services.api.Publish.PublishUnpublishRequest PublishUnpublishRequest}
             * containing the {@link NodeAddress} of the {@code TitanNode} publishing the given objects.
             * This is the anonymous form of publish or unpublish where there is no accompanying metaadata about the objects.
             *  
             * @param publisher The {@code NodeAddress} of the {@link TitanNode} publishing the objects.
             * @param objectIds One or more {@link TitanGuid} instances to publish.
             */
            public PublishUnpublishRequestImpl(NodeAddress publisher, TitanGuid...objectIds) {
                this.objects = new HashMap<TitanGuid,TitanObject.Metadata>();
                for (TitanGuid objectId : objectIds) {
                    this.objects.put(objectId, null);
                }
                
                this.publisher = publisher;
                this.secondsToLive = 0;
            }

            /**
             * If {@code true} this {@link  Publish.PublishUnpublishRequest} is a backup for the root of the object's
             * {@link TitanGuid} and signals the helper method
             * {@link AbstractObjectHandler#publishObjectBackup(AbstractObjectHandler, Publish.PublishUnpublishRequest)}
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
             * Get the map of {@link TitanGuid}s to {@link sunlabs.titan.api.TitanObject.Metadata} in this Request.
             */
            public Map<TitanGuid,TitanObject.Metadata> getObjects() {
            	return this.objects;
            }

            /**
             * Get the number of seconds a publish record created from this Request should exist.
             */
            public long getSecondsToLive() {
                return this.secondsToLive;
            }
        }

        public static class PublishUnpublishResponseImpl implements Publish.PublishUnpublishResponse, Serializable {
            private static final long serialVersionUID = 1L;
            private Set<TitanGuid> objectIds;
            private NodeAddress rootNodeAddress;

            public PublishUnpublishResponseImpl(NodeAddress rootNodeAddress) {
                this.objectIds = new HashSet<TitanGuid>();
                this.rootNodeAddress = rootNodeAddress;
            }

            public PublishUnpublishResponseImpl(NodeAddress rootNodeAddress, Set<TitanGuid> objectIds) {
                this(rootNodeAddress);
                this.objectIds = objectIds;
            }

            public Set<TitanGuid> getObjectIds() {
                return this.objectIds;
            }
            
            public NodeAddress getRootNodeAddress() {
                return this.rootNodeAddress;
            }
        }
    }
}
