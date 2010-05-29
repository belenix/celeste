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

package sunlabs.celeste.node;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.Hashtable;

import sunlabs.asdf.util.Time;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.operation.ReadProfileOperation;

/**
 * A {@code ProfileCache} maintains a cache of recently used {@code Profile_}
 * objects.  A given entry expires after it has been in the cache for a
 * timeout period determined by the argument given to the cache's constructor;
 * expired entries are lazily discarded and are replaced on demand by freshly
 * constructed profiles.
 */
//
// XXX: This class needs to evolve radically.  The notion of a cache with
//      timeouts is an invitation to undesirably sloppy semantics.  What we
//      want instead is a cache that understands successive profile versions
//      and that can fetch the file storing a given version of a given profile
//      and turn it into the profile itself.  (LRUCache comes close to what's
//      desired, except that it doesn't have a  notion of primary and
//      secondary key, which is what we need here.  But it doesn't look like
//      subclassing can easily fix this mismatch.)
//
public class ProfileCache {
    private static final long defaultCacheTimeout = 60L * 1000L; // 60 seconds

    private class CacheEntry {
        long time;
        Profile_ profile;

        CacheEntry(long time, Profile_ profile) {
            this.time = time;
            this.profile = profile;
        }
    }

    private final long cacheTimeout;
    private final InetSocketAddress address;
    private final CelesteProxy.Cache proxyCache;

    private Hashtable<BeehiveObjectId,CacheEntry> profileCache;

    /**
     * Create a new profile cache that communicates with Celeste via the given
     * proxy and whose entries time out after a default period (of one
     * minute).
     *
     * @param celesteProxy  the proxy to be used for communicating with
     *                      Celeste
     */
    public ProfileCache(CelesteAPI celesteProxy) {
        this(celesteProxy, ((int) ProfileCache.defaultCacheTimeout)/1000);
    }

    /**
     * Create a new profile cache that communicates with Celeste via the given
     * proxy and whose entries time out after {@code timeout} seconds.
     *
     * @param celesteProxy  the proxy to be used for communicating with
     *                      Celeste
     * @param timeout       the lifetime in seconds during which a cache entry
     *                      is valid
     */
    public ProfileCache(CelesteAPI celesteProxy, int timeout) {
        this(celesteProxy.getInetSocketAddress(), null, timeout);
    }

    /**
     * Create a new profile cache that communicates with the Celeste node with
     * the given {@code address} via the given proxy cache and whose entries
     * time out after {@code timeout} seconds.  If {@code proxyCache} is
     * @code null}, the profile cache will create one with default parameters.
     *
     * @param address       the address of the Celeste node that this {@code
     *                      ProfileCache} should contact for its interactions
     *                      with Celeste
     * @param proxyCache    a cache from which proxies for communicating with
     *                      Celeste are to be obtained
     * @param timeout       the lifetime in seconds during which a cache entry
     *                      is valid
     */
    public ProfileCache(InetSocketAddress address,
            CelesteProxy.Cache proxyCache, int timeout) {
        if (proxyCache == null) {
            //
            // Set up a cache with default parameters that might or might not
            // match actual requirements.
            //
            proxyCache =
                new CelesteProxy.Cache(4, Time.secondsInMilliseconds(300));
        }
        this.proxyCache = proxyCache;

        this.address = address;
        this.cacheTimeout = timeout * 1000L;
        this.profileCache = new Hashtable<BeehiveObjectId,CacheEntry>();
    }

    public void put(Profile_ p) {
        BeehiveObjectId guid = p.getObjectId();
        CacheEntry e = this.profileCache.get(guid);
        if (e == null) {
            e = new CacheEntry(System.currentTimeMillis(), p);
        } else {
            e.profile = p;
        }
        this.profileCache.put(guid, e);
    }

    public void remove(BeehiveObjectId profileGUID) {
        this.profileCache.remove(profileGUID);
    }

    public Profile_ get(String profileName) throws
            ClassNotFoundException,
            CelesteException.CredentialException {

        return this.get(new BeehiveObjectId(profileName.getBytes()));
    }

    /**
     * The cache will try to retrieve the profile from the celeste store, and
     * attempt to verify its integrity and authenticity.  Currently, there is
     * no other code on the client side that does this.
     *
     * An alternative place where this could go is StorageClient.java, since
     * that is the lowest-level interface to the celeste node on the client
     * side (ie, part of the trusted code base for the user/writer).
     *
     * @param profileId
     * @throws ClassNotFoundException
     * @throws CelesteException.CredentialException
     */
    public Profile_ get(BeehiveObjectId profileId) throws
            ClassNotFoundException,
            CelesteException.CredentialException {
        //
        // Use the cached entry if it exists and is current.
        //
        Profile_ p = getCachedOnly(profileId);
        if (p != null) {
            //System.err.println("Profile cache hit.: " + p.hashCode());
            return p;
        }

        //
        // Grab a fresh copy of the profile.
        //
        ResponseMessage msg = null;
        CelesteAPI proxy = null;
        try {
            proxy = this.proxyCache.getAndRemove(this.address);
            msg = proxy.readCredential(
                new ReadProfileOperation(profileId));
            try {
                p = msg.get(Profile_.class);
            } catch (Exception e) {
                throw new CelesteException.CredentialException(e);
            }
            //
            // Don't cache missing profiles.
            //
            if (p == null)
                return null;

            //
            // The profile validated successfully.  Cache and return it.
            //
            CacheEntry entry = new CacheEntry(System.currentTimeMillis(), p);
            this.profileCache.put(profileId, entry);

            return entry.profile;
        } catch (CelesteException.NotFoundException e) {
            return null;
        } catch (CelesteException.AccessControlException e) {
            return null;
        } catch (CelesteException.RuntimeException e) {
            return null;
        } catch (IOException e) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            this.proxyCache.addAndEvictOld(this.address, proxy);
        }
    }

    public Profile_ get(BeehiveObjectId profileId, BeehiveObjectId versionId)
            throws Exception, Credential.Exception {
        //
        // Profile_s are no longer versioned.  So just ignore the versionId
        // argument.
        //
        return this.get(profileId);
    }

    public boolean profileExists(String profileName) {
        try {
            return profileExists(new BeehiveObjectId(profileName.getBytes()));
        } catch (CelesteException e) {
            return false;
        }
    }

    /**
     *
     * @param profileId
     */
    public boolean profileExists(BeehiveObjectId profileId)
            throws CelesteException.RuntimeException {
        CelesteAPI proxy = null;
        try {
            proxy = this.proxyCache.getAndRemove(this.address);
            ResponseMessage msg = proxy.readCredential(
                new ReadProfileOperation(profileId));
            return msg.get(Profile_.class) != null;
        } catch (IOException ioe) {
            return false;
        } catch (CelesteException.NotFoundException e) {
            return false;
        } catch (CelesteException.CredentialException e) {
            return false;
        } catch (CelesteException.AccessControlException e) {
            return false;
        } catch (Exception e) {
            throw new CelesteException.RuntimeException(e);
        } finally {
            this.proxyCache.addAndEvictOld(this.address, proxy);
        }
    }

    /**
     * Provided that a current cache entry for the profile denoted by {@code
     * profileGUID} exists, return that profile or {@code null} otherwise.
     *
     * @param profileGUID   the profile's GUID
     *
     * @return  the profile or {@code null} if it is not current in the cache
     */
    public Profile_ getCachedOnly(BeehiveObjectId profileGUID) {
        CacheEntry entry = this.profileCache.get(profileGUID);

        if (entry != null && System.currentTimeMillis() - this.cacheTimeout > entry.time) {
            remove(profileGUID);
            entry = null;
        }
        if (entry != null)
            return entry.profile;

        return null;
    }
}
