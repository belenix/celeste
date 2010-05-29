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

package sunlabs.celeste;

import java.io.Serializable;

import sunlabs.beehive.node.AbstractBeehiveObject;
import sunlabs.beehive.util.OrderedProperties;

/**
 * This class is used to communicate results from Celeste to the Celeste client.
 * 
 * <p>
 * Results are a pair of values consisting of serialized data and an {@link OrderedProperties} instance containing some meta-data.
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class ResponseMessage implements Serializable {
    private final static long serialVersionUID = 1L;
    
    private OrderedProperties metadata;
    private Serializable object;
    
    public ResponseMessage(OrderedProperties metadata, Serializable object) {
        this.object = object;
        this.metadata = metadata;
    }

    public ResponseMessage(OrderedProperties metadata) {
        this(metadata, new byte[0]);
    }
    
    public ResponseMessage(Serializable object) {
        this(new AbstractBeehiveObject.Metadata(), object);
    }
    
    public OrderedProperties getMetadata() {
        return this.metadata;
    }
    
    /**
     * 
     * @param <C> the class of the encapsulated return instance.
     * @param klasse the expected class of the encapsulated return object.
     * @throws ClassCastException if the encapsulated return object is not the the expected class
     * @throws Exception if the result contains an Exception thrown by the Celeste node.
     */
    public <C> C get(Class<? extends C> klasse) throws ClassCastException, Exception {
        try {
            // XXX This should throw a specific encapsulated Exception, not just Exception.
            if (this.object instanceof Exception) {
                throw (Exception) this.object;
            }
            return klasse.cast(this.object);
        } catch (ClassCastException e) {
            System.err.printf("ResponseMessage.get: %s: expected %s, got %s%n", e.toString(), klasse.getName(), this.object == null ? "null" : this.object.getClass().getName());
            throw e;
        }
    }
    
    public String toString() {
        return String.format("ResponseMessage: %s", this.object == null ? "null" : this.object.getClass().getName());
    }
}
