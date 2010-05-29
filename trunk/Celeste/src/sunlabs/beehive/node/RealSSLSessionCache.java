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
package sunlabs.beehive.node;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import sunlabs.asdf.web.XML.XHTML;

/**
 * Keeping alive SSL Sessions
 *
 * This code supplements the SSL-internal session context, which caches
 * sessions only with soft references. Since it is important for us that
 * ssl sessions do *not* get garbage collected all the time, we keep hard
 * references around as well. The cache is limited in size, and periodically
 * discards invalidated or otherwise unreferenced sessions.
 * 
 * This 'RealSSLSessionCache' was done to cover up a deficiency in the normal 
 * SSL session cache. The normal cache uses weak pointers to remember the 
 * individual sessions, and thus the garbage collector can squeeze out the 
 * sessions at will. This is dangerous for the present application, since 
 * the establishment of SSL sessions is very expensive, and we can't really
 * afford to do them all the time. 
 * Thus this 'real' cache keeps strong pointers to sessions. When the weak ones
 * would go away, ours prevent the sessions from being collected, and thus make
 * the weak ones linger. This is good, and keeps the sessions visible to the
 * SSL engine, allowing them to be reused.
 * 
 * One must however note that the 'weak' caches also have a size limitation.
 * If you make them too small, then sessions will be pushed out, and by virtue
 * of this be invalidated (and not accessible to SSL in the future). This is
 * irrespective to the size of the 'strong' cache. in fact, the sessions will
 * be cleaned out from here eventually since they are not associated with a
 * context anymore (and additionally have isValid() == false).
 * So, keep the weak caches as large as you can!
 */
public class RealSSLSessionCache {
    private HashSet<SSLSession> allSessions;
    private int maxSetSize;
    private int counter;

    // --------------------------------------------------------------------------------------
    // some statistics data
    //
    // there are a few things we can look at:
    // 1) max number of sessions in this cache ever
    // 2) current number of sessions
    // 3) age of oldest and newest session
    // 4) number of sessions that have been accessed in the last N minutes
    // 5) detailed data about each session, including
    //    whether it can be found in the regular server or client 
    //    SSL context cache. (which only holds weak references
    // 6) whether there are any sessions in the weak caches that we are
    //    not covering in this cache

    private int maxSizeEver = 0; 
    
//    /**
//     * Returns the largest size this cache ever had.
//     */
//    public int getMaxSizeEver() {
//        return this.maxSizeEver;
//    }
//    
//    /**
//     * Resets the largest measured size back to zero.
//     */
//    public void resetMaxSizeEver() {
//        this.maxSizeEver = 0;
//    }
//
//    /**
//     * Returns the maximum size to which this cache is configured.
//     */
//    public int getMaxSetSize() {
//        return this.maxSetSize;
//    }
//    
//    /**
//     * Returns the internal counter for add() operations.
//     * This counter does not mean much, but shows you that the cache is
//     * alive, and adding (or re-adding) sessions.
//     */
//    public int getCounter() {
//        return this.counter;
//    }
    
    /**
     * Returns the current size of the session cache.
     */
    public int getSize() {
        return this.allSessions.size();
    }
    
    /**
     * Returns the oldest SSL session in the cache
     */
    public SSLSession getOldestSession() {
        return getOldestSession(this.allSessions);
    }
    
    private SSLSession getOldestSession(HashSet<SSLSession> sessions) {
    	long oldest = System.currentTimeMillis() + 1;
    	SSLSession res = null;
    	Iterator<SSLSession> iter = sessions.iterator();
        while (iter.hasNext()) {
            SSLSession sess = iter.next();
            if (sess.getCreationTime() < oldest) {
            	oldest = sess.getCreationTime();
            	res = sess;
            }
        }
        return res;
    }

    /**
     * Returns the newest SSL session in the cache
     */
    public SSLSession getNewestSession() {
        return getNewestSession(this.allSessions);
    }
    
    public SSLSession getNewestSession(HashSet<SSLSession> sessions) {
    	long newest = 0;
    	SSLSession res = null;
    	Iterator<SSLSession> iter = sessions.iterator();
        while (iter.hasNext()) {
            SSLSession sess = iter.next();
            if (sess.getCreationTime() > newest) {
            	newest = sess.getCreationTime();
            	res = sess;
            }
        }
        return res;
    }
    
    /**
     * Returns the number of SSL session that have been used to establish a
     * new SSL connection in the last N minutes.
     */
    public int getRecentSessionCount(int minutes) {
        return getRecentSessionCount(this.allSessions, minutes);
    }
    
    public int getRecentSessionCount(HashSet<SSLSession> sessions, int minutes) {
    	int count = 0;
    	long after = System.currentTimeMillis() - (long) minutes * 60L * 1000L;
    	Iterator<SSLSession> iter = sessions.iterator();
        while (iter.hasNext()) {
            SSLSession sess = iter.next();
            if (sess.getLastAccessedTime() > after) {
            	count++;
            }
        }
        return count;
    }
    
    /**
     * Returns a set of sessions (possibly empty) that contains those sessions
     * which can be found in the weak cache of the SSLSessionContext, but not in
     * our strong cache. If this occurs, there is an issue with coverage.
     */    
    HashSet<SSLSession> uncoveredWeakEntries(HashSet<SSLSession> strongSet, javax.net.ssl.SSLSessionContext cache) {
 
    	HashSet<SSLSession> s = new HashSet<SSLSession>();
       	// I'd love to use an iterator, but this is what you get...
        @SuppressWarnings(value="unchecked")
            Enumeration<byte[]> ids = cache.getIds();
    	while (ids.hasMoreElements()) {
    	    byte[] id = (byte[]) ids.nextElement();
    	    SSLSession sess = cache.getSession(id);
    	    if (sess != null && strongSet.contains(sess) == false) {
    	        s.add(sess);
    	    }
    	}
        return s;
    }
    
    /**
     * Returns a set of sessions (possibly empty) that contains sessions
     * found in the strong cache, but not in the weak cache. 
     */
    HashSet<SSLSession> uncoveredStrongEntries(HashSet<SSLSession> strongSet, javax.net.ssl.SSLSessionContext cache) {
        // There should be some, otherwise our additional caching has finally become
        // obsolete (e.g. the SSL implementation has changed).
    	HashSet<SSLSession> s = new HashSet<SSLSession>();
    	Iterator <SSLSession> iter = strongSet.iterator();
    	while (iter.hasNext()) {
    	    SSLSession sess = iter.next();
    	    byte[] id = sess.getId(); 
    	    if (cache.getSession(id) == null)
    	        s.add(sess);
    	}
    	return s;
    }
    
    public XHTML.Div toXHTML(javax.net.ssl.SSLSessionContext clientCache, javax.net.ssl.SSLSessionContext serverCache, String inspect) {
    	XHTML.Div body = new XHTML.Div();
    	body.add(new XHTML.Heading.H2("SSL Session Cache")); 	

    	SSLSession oldest = getOldestSession();
    	SSLSession newest = getNewestSession();
        DateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

        String oldest_time = (oldest == null) ? "never" : dateFormat.format(new Date(oldest.getCreationTime()));
        String newest_time = (newest == null) ? "never" : dateFormat.format(new Date(newest.getCreationTime()));
        
        int clientSize = 0;
        HashSet<SSLSession> clientSet = new HashSet<SSLSession>();
        @SuppressWarnings(value="unchecked")
            Enumeration<byte[]> i1 = serverCache.getIds();
        for ( ; i1.hasMoreElements(); clientSet.add(clientCache.getSession(i1.nextElement())))
            clientSize++;

        int serverSize = 0;
        HashSet<SSLSession> serverSet = new HashSet<SSLSession>();
        @SuppressWarnings(value="unchecked")
            Enumeration<byte[]> i2 = serverCache.getIds();
        for ( ; i2.hasMoreElements(); serverSet.add(serverCache.getSession(i2.nextElement())))
            serverSize++;
        
        
        XHTML.Table table1 = new XHTML.Table(
            new XHTML.Table.Caption("Strong Cache"),
            new XHTML.Table.Body(
                new XHTML.Table.Row(new XHTML.Table.Data("Liveness counter"), new XHTML.Table.Data("%d", this.counter)),
                new XHTML.Table.Row(new XHTML.Table.Data("Maximum configured size"), new XHTML.Table.Data("%d", this.maxSetSize)),
                new XHTML.Table.Row(new XHTML.Table.Data("Maximum size ever reached"), new XHTML.Table.Data("%d", this.maxSizeEver)),
                new XHTML.Table.Row(new XHTML.Table.Data("Current size"), new XHTML.Table.Data("%d", getSize())),
                new XHTML.Table.Row(new XHTML.Table.Data("Oldest entry"), new XHTML.Table.Data(oldest_time)),
                new XHTML.Table.Row(new XHTML.Table.Data("Newest entry"), new XHTML.Table.Data(newest_time)),
                new XHTML.Table.Row(new XHTML.Table.Data("Entries used last 10 min"), new XHTML.Table.Data(getRecentSessionCount(10))),
                new XHTML.Table.Row(new XHTML.Table.Data("Entries used last hour"), new XHTML.Table.Data(getRecentSessionCount(60))),
                new XHTML.Table.Row(new XHTML.Table.Data("Only in strong cache"), new XHTML.Table.Data(uncoveredStrongEntries(uncoveredStrongEntries(this.allSessions, serverCache), clientCache).size()))
            )).setClass("java");

    	oldest = getOldestSession(clientSet);
    	newest = getNewestSession(clientSet); 
    	oldest_time = (oldest == null) ? "never" : dateFormat.format(new Date(oldest.getCreationTime())); 
    	newest_time = (newest == null) ? "never" : dateFormat.format(new Date(newest.getCreationTime()));
        
        XHTML.Table table2 = new XHTML.Table(
        		new XHTML.Table.Caption("Weak Client Cache"),
        		new XHTML.Table.Body(
        				new XHTML.Table.Row(new XHTML.Table.Data("Max size"), new XHTML.Table.Data("%d", clientCache.getSessionCacheSize())),
        	       	    new XHTML.Table.Row(new XHTML.Table.Data("Current size"), new XHTML.Table.Data("%d", clientSize)),
        	       	    new XHTML.Table.Row(new XHTML.Table.Data("Session timeout"), new XHTML.Table.Data("%d", clientCache.getSessionTimeout())),
        	            new XHTML.Table.Row(new XHTML.Table.Data("Oldest entry"), new XHTML.Table.Data(oldest_time)),
        	       	    new XHTML.Table.Row(new XHTML.Table.Data("Newest entry"), new XHTML.Table.Data(newest_time)),
        	       	    new XHTML.Table.Row(new XHTML.Table.Data("Entries used last 10 min"), new XHTML.Table.Data("%d", getRecentSessionCount(clientSet, 10))),
        	       	    new XHTML.Table.Row(new XHTML.Table.Data("Entries used last hour"), new XHTML.Table.Data("%d", getRecentSessionCount(clientSet, 60))),
        	            new XHTML.Table.Row(new XHTML.Table.Data("Not in strong cache"), new XHTML.Table.Data("%d", uncoveredWeakEntries(this.allSessions, clientCache).size()))
        		)).setClass("java");
       	         
    	oldest = getOldestSession(serverSet);
    	newest = getNewestSession(serverSet); 
    	oldest_time = (oldest == null) ? "never" : dateFormat.format(new Date(oldest.getCreationTime())); 
       	newest_time = (newest == null) ? "never" : dateFormat.format(new Date(newest.getCreationTime()));
        
        XHTML.Table table3 = new XHTML.Table(
        		new XHTML.Table.Caption("Weak Server Cache"),
        		new XHTML.Table.Body(
        			new XHTML.Table.Row(new XHTML.Table.Data("Max size"), new XHTML.Table.Data("%d", serverCache.getSessionCacheSize())),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Current size"), new XHTML.Table.Data("%d", serverSize)),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Session timeout"), new XHTML.Table.Data("%d", serverCache.getSessionTimeout())),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Oldest entry"), new XHTML.Table.Data(oldest_time)),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Newest entry"), new XHTML.Table.Data(newest_time)),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Entries used last 10 min"), new XHTML.Table.Data("%d", getRecentSessionCount(serverSet, 10))),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Entries used last hour"), new XHTML.Table.Data("%d", getRecentSessionCount(serverSet, 60))),
                 	new XHTML.Table.Row(new XHTML.Table.Data("Not in strong cache"), new XHTML.Table.Data("%d", uncoveredWeakEntries(this.allSessions, serverCache).size()))
       			)).setClass("java");
        
        
        XHTML.Div statisticsTable = new XHTML.Div(new XHTML.Table(
        			new XHTML.Table.Caption("Statistics"),
        			new XHTML.Table.Body(
        				new XHTML.Table.Row(new XHTML.Table.Data(table1), new XHTML.Table.Data(table2), new XHTML.Table.Data(table3))
        			)
        		)
        	).setClass("section");
        
        body.add(statisticsTable);
    	
    	XHTML.Table.Body tbody = new XHTML.Table.Body();
    	tbody.add(new XHTML.Table.Row(
    			new XHTML.Table.Heading("Peer Host"),
    			new XHTML.Table.Heading("Peer Port"),
    			new XHTML.Table.Heading("Creation Time"),
    			new XHTML.Table.Heading("Last Access"),
    			new XHTML.Table.Heading("Is Valid"),
    			new XHTML.Table.Heading("Strong"),
    			new XHTML.Table.Heading("WeakC"),
    			new XHTML.Table.Heading("WeakS")));

        HashSet<SSLSession> superSet = new HashSet<SSLSession>();
        superSet.addAll(this.allSessions);
        superSet.addAll(clientSet);
        superSet.addAll(serverSet);
    	for (SSLSession sess : superSet) {
    	    String strong = this.allSessions.contains(sess) ? "X" : "";
    	    String weakC = clientSet.contains(sess) ? "X" : "";
    	    String weakS = serverSet.contains(sess) ? "X" : "";
    		
    	    tbody.add(
    	            new XHTML.Table.Row(
    	                    new XHTML.Table.Data(new XHTML.Anchor("%s", sess.getPeerHost()).setHref("?action=explore-ssl&inspect=" + toHex(sess.getId()))),
    	                    new XHTML.Table.Data(sess.getPeerPort()),
    	                    new XHTML.Table.Data(dateFormat.format(new Date(sess.getCreationTime()))),
    	                    new XHTML.Table.Data(dateFormat.format(new Date(sess.getLastAccessedTime()))),
    	                    new XHTML.Table.Data(sess.isValid()),
    	                    new XHTML.Table.Data(strong),
    	                    new XHTML.Table.Data(weakC),
    	                    new XHTML.Table.Data(weakS)
    	            ));
    	}
    	
    	body.add(new XHTML.Div(new XHTML.Table(new XHTML.Table.Caption("Data"), tbody)).setClass("section"));

    	if (inspect != null) {
    	    body.add(new XHTML.Heading.H2("Inspect"));
    	    byte[] id = fromHex(inspect);
    	    Iterator<SSLSession> i = superSet.iterator();
    	    SSLSession inspec_sess = null;
    	    while (i.hasNext()) {
    	        SSLSession sess = i.next();
    	        if (Arrays.equals(id, sess.getId()) == true) {
    	            inspec_sess = sess;
    	            break;
    	        }
    	    }

    	    if (inspec_sess == null) {
    	        body.add(new XHTML.Para("Could not find requested session."));
    	    } else {
    	        String peer_cert = "SSLPeerUnverifiedException";
    	        try {	
    	            peer_cert = inspec_sess.getPeerCertificates()[0].toString();
    	        } catch (SSLPeerUnverifiedException e) {
    	            /**/
    	        }

    	        XHTML.Table.Body dataTable = new XHTML.Table.Body(
    	                new XHTML.Table.Row(new XHTML.Table.Data("peerHost"), new XHTML.Table.Data("%s", inspec_sess.getPeerHost())),
    	                new XHTML.Table.Row(new XHTML.Table.Data("peerPort"), new XHTML.Table.Data("%d", inspec_sess.getPeerPort())),
    	                new XHTML.Table.Row(new XHTML.Table.Data("Protocol"), new XHTML.Table.Data("%s", inspec_sess.getProtocol())),
    	                new XHTML.Table.Row(new XHTML.Table.Data("CipherSuite"), new XHTML.Table.Data("%s", inspec_sess.getCipherSuite())),
    	                new XHTML.Table.Row(new XHTML.Table.Data("LocalCert"), new XHTML.Table.Data("%s", inspec_sess.getLocalCertificates()[0].toString())),  
    	                new XHTML.Table.Row(new XHTML.Table.Data("PeerCert"), new XHTML.Table.Data("%s", peer_cert))
    	        );

    	        body.add(new XHTML.Table(dataTable));
    	    }
    	}
    	
    	return body;
    }
    
    // end of the statistics part
    // --------------------------------------------------------------------------------------
    
    
    public RealSSLSessionCache(int size) {
        if (size > 0)
            this.maxSetSize = size;
        this.counter = 0;
        this.allSessions = new HashSet<SSLSession>(size);
    }
    
    synchronized public void add(SSLSession sess) {
        this.allSessions.add(sess);
        this.counter++;
        int size = this.allSessions.size();
        if (size > this.maxSizeEver)
            this.maxSizeEver = size;
        if (size > this.maxSetSize || (this.counter % 1000) == 0) {
            this.recycle();
        }
    }
    
    synchronized public void clear() {
        this.counter = 0;
        this.allSessions.clear();
    }
    
    protected static class SessionCompare implements Comparator<SSLSession>, Serializable {
        private final static long serialVersionUID = 0L;
        
        public int compare(SSLSession s1, SSLSession s2) throws ClassCastException {
            if (s1.getLastAccessedTime() < s2.getLastAccessedTime())
                return -1;
            if (s1.getLastAccessedTime() > s2.getLastAccessedTime())
                return 1;
            if (s1.equals(s2))
                return 0;
            // how do we order non-equal objects with equal time stamp?
            // lets just be 'consistent' in some fashion...
            return (s1.hashCode() - s2.hashCode());
        }
    }
    
    private void recycle() {
        // XXX This needs some kind of locking.

        this.counter = 0;
        TreeSet<SSLSession> sorter = new TreeSet<SSLSession>(new SessionCompare());
        Iterator<SSLSession> iter = this.allSessions.iterator();
        while (iter.hasNext()) {
            SSLSession sess = iter.next();
            // if there is no session, we drop it.
            // If the session is not associated to any context,
            // then it was somehow forcibly removed from the weak cache.
            // All that we prevent with our cache here is that the weak
            // references in that cache over there are collected by the GC.
            // Things can still be dropped if they get too old, or the weak
            // cache gets too full, etc.
            // Finally, if the session has been invalidated, it can not be used
            // to establish new connections either. Thus we can remove it from
            // our cache, even before it gets to be one of the oldest unused sessions.
            if (sess == null || sess.getSessionContext() == null || sess.isValid() == false)
                iter.remove();
            else
                sorter.add(sess);
        }

        int limit = this.maxSetSize * 3 / 4;
        if (limit == 0)
            limit = 1;

        iter = sorter.iterator();
        while (iter.hasNext() && limit < this.allSessions.size()) {
            SSLSession sess = iter.next();
            this.allSessions.remove(sess);
        }
    }

    // some usual utility functions -- do we have them centrally somewhere??
    
    private static String toHex(byte[] b) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            String res = ("0" + Integer.toHexString(b[i]));
            result.append(res.substring(res.length() - 2));
        }               
        return result.toString().toUpperCase(Locale.US);
    }

    public static byte[] fromHex(String h) {
        if (h == null)
            return null;
        h = h.toUpperCase(Locale.US);
        if (h.length()%2!=0)
            return null;
        if (h.matches(".*[^0-9A-F].*"))
            return null;
        
        byte[] result = new byte[h.length()/2];		
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (Integer.parseInt(h.substring(i*2, i*2+2), 16));
        }		
        return result;
    }
}
