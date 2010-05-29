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
package sunlabs.celeste.client;

import sunlabs.beehive.util.OrderedProperties;

/**
 * Contains information about the replication policy for a Celeste file.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class ReplicationParameters extends OrderedProperties {
    private final static long serialVersionUID = 1L;
    
    public ReplicationParameters(String specification) {
        super(specification, ';');
    }
    
    public int getAsInteger(Object... args) {
        if (!this.containsKey(args[0])) {
            try {
                return Integer.parseInt(args[1].toString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return Integer.parseInt(this.getProperty((String) args[0]));
    }
    
    public long getAsLong(Object... args) {
        if (!this.containsKey(args[0])) {
            try {
                return Long.parseLong(args[1].toString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return Long.parseLong(this.getProperty((String) args[0]));
    }
    
    public String getAsString(Object... args) {
        if (!this.containsKey(args[0])) {
            return args[1].toString();
        }
        return this.getProperty((String) args[0]);
    }
}
