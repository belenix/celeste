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
package sunlabs.titan.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;

import sunlabs.asdf.util.Time;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.node.object.TitanObjectHandler;

/**

 * Objects in the Beehive object pool have data (the state of the object) and
 * behaviour (operations that get, set, or modify the state of the object).
 * Every {@code BeehiveObject} is an instantiated Java object that implements
 * the {@code BeehiveObject} interface.
 * These Java objects may store the state in the object itself or act as a
 * proxy to some external object outside of the Beehive object store.
 * Each {@code BeehiveObject} implementation controls the access to its data
 * through explicit access methods devised by the designer of the class
 * implementing the {@code BeehiveObject} interface.
 * <p>
 * Most data objects stored in the Beehive object pool are stored in the
 * backing-store. Other data objects stored in the Beehive object pool are
 * ephemeral or dynamic and are not always ensured by the backing store,
 * but instead are typically computed or obtained from mechanisms outside of
 * the system when they are needed.
 * </p>
 * <p>
 * In all cases, this class presents the interface to all of these objects.
 * </p>
 * <p>
 * All objects and their object-ids are verifiable.
 * This verification comes in one of three forms:
 * </p>
 * <ol>
 * <li><b>Data attestation</b> &mdash The object-id of the object is derived from the
 * byte-wise content of the object and the mandatory {@code ObjectStore.DeleteTokenId} delete-token-id.
 * All of the following conditions must be true:
 * <ul>
 *  <li>The object's meta-data contains an {@code ObjectStore.DataHash}
 *      property which is the hash of the object's data/state (expressed as a
 *      {@link TitanGuid} value -- see {@link #getDataId()} .</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.DeleteTokenId}
 *      property which is the hash of the (secret) Delete Token (expressed as
 *      a {@link TitanGuid} value).</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.ObjectId}
 *      which is the hash of the {@code ObjectStore.DeleteTokenId} and
 *      the {@code ObjectStore.DataHash} properties (expressed as a
 *      {@link TitanGuid} value).</li>
 *  <li><p style='text-align: left; font-style: italic'>
 *      objectId = HASH(DeleteTokenHash || DataHash)</p></li>
 * </ul>
 * </li>
 * <li><b>Signature (Hash) attestation</b> &mdash The {@link TitanGuid} is
 *     derived from a combination of hashes and a signature. All of the
 *     following conditions must be true:
 * <ul>
 *  <li>The object's meta-data contains an {@code ObjectStore.DataHash}
 *      property which is the hash of the object's data/state -- see {@link #getDataId()}.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.DeleteTokenId}
 *      property which is the hash of the (secret) Delete Token.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.ObjectId}
 *      property which is explicitly specified.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.Voucher} which is
 *      a signature (hash) of the {@code ObjectStore.DeleteTokenId}, the
 *      {@code ObjectStore.ObjectId}, and the {@code ObjectStore.DataHash}
 *      properties.
 *  </li>
 *  <li><p style='text-align: left;'>
 *      <i>objectId</i> = <tt>ObjectStore.ObjectId</tt><br/>
 *      <rm>and</rm>
 *      <tt>ObjectStore.Voucher</tt> is equal to
 *       <i>HASH(deleteTokenId + objectId + dataHash)</i></br>
 *      </p>
 *  </li>
 * </ul>
 * </li>
 * <li><b>Delete-Token attestation</b> &mdash This is the form of anti-objects
 * (objects that are deleted). The {@link TitanGuid} is derived from a
 * combination of hashes and a signature and the Delete Token is exposed.
 * All of the following conditions must be true:
 * <ul>
 *  <li>The object's meta-data contains an {@code ObjectStore.DataHash}
 *      property which is the hash of the (original) object's data.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.DeleteTokenId}
 *      property which is the hash of the (secret) Delete Token.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.ObjectId}
 *      property which is explicitly specified.</li>
 *  <li>The object's meta-data contains an {@code ObjectStore.Voucher} which is
 *      a signature (hash) of the {@code ObjectStore.DeleteTokenId}, the
 *      {@code ObjectStore.ObjectId}, and the {@code ObjectStore.DataHash}
 *      properties.</li>
 *  <li>The object's meta-data contains the {@code ObjectStore.DeleteToken}
 *      as the exposed secret Delete Token</li>
 *  <li>The object has zero-length data. (currently not enforced in order
 *      to experiment with deletion)</li>
 * </ul>
 * </li>
 * </ol>
 * Signatures need to implemented and replace the simple hashing.
 * @see TitanObjectHandler
 */
public interface TitanObject extends /*XHTMLInspectable,*/ Serializable {
    /**
     * Signifies that this object never expires.
     */
    public static final long INFINITE_TIME_TO_LIVE = Long.MAX_VALUE;

    /**
     * The meta-data of a {@code TitanObject}.
     * <p>
     * The meta-data consists of name/value pairs containing system and application related information about this object.
     * </p>
     */
    public interface Metadata extends Serializable {
        public static final Metadata NONE = null;

        public String getProperty(String name, Object defaultValue);

        public String getProperty(String name);

        public TitanGuid getPropertyAsObjectId(String name, TitanGuid defaultValue);

        public long getPropertyAsLong(String name, long defaultValue);

        public Metadata setProperty(String name, Object value);

        public void putAll(TitanObject.Metadata metaData);

        public void store(OutputStream out) throws IOException;

        public void load(InputStream inStream) throws IOException;

//        public void loadFromXML(InputStream in) throws IOException;
//
//        public void storeToXML(OutputStream os) throws IOException;

        public Set<Object> keySet();

        public XHTML.EFlow toXHTMLTable(URI uri, Map<String,HTTP.Message> props);
    }

    /**
     * Produce the {@link TitanGuid} that names this {@code BeehiveObject}.
     * <p>
     * NB: The validity of the object-id of a {@code BeehiveObject} is governed by rules
     * specified in the preamble documentation for this class (See {@link TitanObject}).
     * </p>
     */
    public TitanGuid getObjectId();

    /**
     * <p>
     * Set the {@link TitanGuid} that names this {@code BeehiveObject}.
     * </p>
     * <p>
     * NB: The validity of the {@code BeehiveObjectId} of a {@code BeehiveObject}
     * is governed by rules specified in the preamble documentation for this class.
     * Setting an arbitrary value which is not valid is an error, but that error is not
     * checked here.
     * </p>
     */
    public void setObjectId(TitanGuid objectId);

    /**
     * Return the time this object was created on this node
     * in seconds from the system's epoch.
     *
     * @return  the creation time, expressed in seconds from the epoch
     */
    public long getCreationTime();

    /**
     * Set the number of remaining seconds for this object to live.
     */
    public void setTimeToLive(long seconds);

    /**
     * Get the number of seconds for this object to live since its creation time.
     *
     * The value {@link TitanObject#INFINITE_TIME_TO_LIVE} signifies an infinite time to live.
     */
    public long getTimeToLive();
    
    /**
     * Get the remaining number of seconds from {@code now} (expressed in seconds, see {@link Time#currentTimeInSeconds()}
     * and {@link System#currentTimeMillis()} that this object is scheduled to exist before it is expired.
     * If an object has an infinite expiration date, this method returns {@link TitanObject#INFINITE_TIME_TO_LIVE}.
     */
    public long getRemainingSecondsToLive(long now);

    /**
     * Set the {@link TitanGuid} of this object's deletion-token.
     * @param deleteTokenId
     */
    public void setDeleteTokenId(TitanGuid deleteTokenId);

    /**
     * Get the {@link TitanGuid} of this object's deletion-token.
     */
    public TitanGuid getDeleteTokenId();

    /**
     * Produce a {@link TitanGuid} representing the data of this object.
     * The data is whatever the implementation of the object needs to include in
     * what distinguishes this object from any other equal() object.
     */
    public TitanGuid getDataId();

    public TitanObject.Metadata getMetadata();

    /**
     * Return {@code true} if this {@code BeehiveObject} is in its anti-object form.
     */
    public boolean isDeleted();

    public String getObjectType();

    public TitanObject.Metadata setProperty(String name, Object value);

    public String getProperty(String name, Object defaultValue);

    public String getProperty(String name);

    public TitanGuid getPropertyAsObjectId(String name, TitanGuid defaultValue);

    public long getPropertyAsLong(String name, long defaultValue);
    
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props);
}