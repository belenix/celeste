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
package sunlabs.celeste.node.services.api;

import java.io.Serializable;

import sunlabs.celeste.client.operation.LockFileOperation;
import sunlabs.celeste.node.services.object.AnchorObject;
import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.object.BeehiveObjectHandler;
import sunlabs.titan.node.object.InspectableObject;
import sunlabs.titan.node.object.MutableObject;

/**
 * The interface to the AnchorObject to VersionObject map.
 *
 * <p>
 * Every file is represented by an {@link AnchorObject} and a {@link VersionObject},
 * where the {@code AnchorObject} is the permanent representative for the Celeste file,
 * and the {@code VersionObject} represents a particular version of the file.
 * </p>
 * <p>
 * The {@code AnchorObject} to {@code VersionObject} map maintains the current file version such
 * that given the {@link TitanGuid} of the {@link AnchorObject}, the AObjectVersionMap
 * produces the {@link TitanGuid} of the current {@code VersionObject}.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public interface AObjectVersionMapAPI extends MutableObject.Handler<MutableObject.Handler.ObjectAPI>/*, InspectableObject.Handler<AObjectVersionMapAPI.Object>*/ {

    public interface ObjectAPI extends InspectableObject.Handler.Object, MutableObject.Handler.ObjectAPI, BeehiveObjectHandler.ObjectAPI {
    	  
    }
    
    public interface Lock extends Serializable {        
        /**
         * Return the {@link TitanGuid} locker-id in this value.
         */
        public TitanGuid getLockerObjectId();

        /**
         * Return the recursive lock count for this value.
         */
        public int getLockCount();
        
        /**
         * Get the client supplied lock token set with this lock.
         * <p>
         * A {@code null} value indicates that there is no token associated with this lock.
         * </p>
         */
        public String getToken();
        
        public Serializable getClientAnnotation();
        
        public LockFileOperation.Type getType();
    }
    
    /**
     *
     */
    abstract public class Value extends MutableObject.Value {
        private static final long serialVersionUID = 1L;

        /**
         * Return the {@link VersionObject.Object.Reference} in this value.
         */
        abstract public VersionObject.Object.Reference getReference();

        /**
         * Get the current {@link AObjectVersionMapAPI.Lock} instance, or {@code null} if there is no lock.
         */
        abstract public Lock getLock();
    }

    /**
     * Construct a new {@link AObjectVersionMapAPI.Value} from the given constituent parts.
     * 
     * @param value The {@link VersionObject.Object.Reference}
     * @param lock The {@link Lock} instance of a lock on this file.
     */
    public AObjectVersionMapAPI.Value newValue(VersionObject.Object.Reference value, Lock lock);
    
    /**
     * Construct a new {@link AObjectVersionMapAPI.Value} from the given constituent parts.
     * (See also {@link AObjectVersionMapAPI#newValue(VersionObject.Object.Reference, AObjectVersionMapAPI.Lock lock)})
     * @param value The {@link VersionObject.Object.Reference}
     */
    public AObjectVersionMapAPI.Value newValue(VersionObject.Object.Reference value);

    /**
     *
     */
    public interface Parameters extends MutableObject.Parameters {

    }

    public AObjectVersionMapAPI.Parameters createAObjectVersionMapParams(String parameterSpec);

    /**
     * Create a variable with the given {@code objectId}, applying parameters
     * that control the backing store mechanism in {@link AObjectVersionMapAPI.Parameters}
     * in the AObjectVersionMap.
     *
     * @param objectId the {@link MutableObject.ObjectId} of the variable to create
     * @param params the parameters controlling the backing store for the variable.
     *
     * @throws MutableObject.InsufficientResourcesException if there are
     *          insufficient resources in the system to create the variable.
     * @throws MutableObject.ExistenceException if the variable already exists.
     */
    public void createValue(TitanGuid objectId, TitanGuid deleteTokenId, AObjectVersionMapAPI.Parameters params, long timeToLive)
    throws MutableObject.InsufficientResourcesException, MutableObject.ExistenceException, MutableObject.ProtocolException;

    /**
     * Delete a variable with the given {@code objectId}, applying parameters
     * that control the backing store mechanism in {@link AObjectVersionMapAPI.Parameters}
     * in the AObjectVersionMap.
     *
     * @param objectId the {@link MutableObject.ObjectId} of the variable to create
     * @param params the parameters controlling the backing store for the variable.
     *
     * @throws MutableObject.InsufficientResourcesException if there are
     *          insufficient resources in the system to create the variable.
     * @throws MutableObject.ExistenceException if the variable already exists.
     */
    public void deleteValue(TitanGuid objectId, TitanGuid deleteToken, AObjectVersionMapAPI.Parameters params, long timeToLive)
    throws MutableObject.InsufficientResourcesException, MutableObject.ExistenceException, MutableObject.ProtocolException, MutableObject.NotFoundException, 
    MutableObject.PredicatedValueException, MutableObject.ObjectHistory.ValidationException, MutableObject.DeletedException;

    /**
     * Set the variable {@code objectId} to the value {@code value}.
     * If {@code predicatedValue} is non-null, the current value of the variable
     * <em>must</em> be equal to {@code predicatedValue}
     *
     * @param objectId the {@link TitanGuid} of the variable to set
     * @param predicatedValue the current required value of the variable, not {@code null}
     * @param value the new value to set
     * @param params the parameters controlling the backing store for the variable.

     * @throws MutableObject.PredicatedValueException the {@code predicatedValue}
     *          is non-null and the current value of the variable is not equal to {@code predicatedValue}
     * @throws MutableObject.InsufficientResourcesException  if there are
     *          insufficient resources in the system to store the variable.
     * @throws MutableObject.ObjectHistory.ValidationException if the current state
     *          of the variable is invalid and cannot (currently) be repaired.
     */
    public Value setValue(TitanGuid objectId, Value predicatedValue, Value value, AObjectVersionMapAPI.Parameters params)
    throws MutableObject.PredicatedValueException, MutableObject.InsufficientResourcesException,
    MutableObject.ObjectHistory.ValidationException, MutableObject.ProtocolException, MutableObject.DeletedException;


    /**
     * Get the current value of the variable {@code objectId}.
     *
     * @param objectId the {@link MutableObject.ObjectId} of the variable to get
     * @param params the parameters controlling the backing store for the variable.
     *
     * @throws MutableObject.InsufficientResourcesException if there are
     *          insufficient resources in the system to construct the authoritative value of {@code objectId}.
     * @throws MutableObject.NotFoundException if the mutable object was not found.
     */
    public AObjectVersionMapAPI.Value getValue(TitanGuid objectId, AObjectVersionMapAPI.Parameters params)
    throws MutableObject.InsufficientResourcesException, MutableObject.NotFoundException, MutableObject.ProtocolException;
}
