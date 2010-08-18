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
package sunlabs.titan.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.Hashtable;
import java.util.Map;
/**
 *
 * Global communicator of "status".
 */
public class DOLRStatus implements Serializable {
    private final static long serialVersionUID = 1L;
        
    private static Map<String,DOLRStatus> byName = new Hashtable<String,DOLRStatus>(30);
    private static Map<Integer,DOLRStatus> byValue = new Hashtable<Integer,DOLRStatus>(30);

    public static DOLRStatus getInstance(ObjectInputStream in) throws IOException, ClassNotFoundException {
        return (DOLRStatus) in.readObject();
    }
    
    public static DOLRStatus fromEncoding(int value) {
        return DOLRStatus.byValue.get(Integer.valueOf(value));
    }

    private String name;
    private int value;
    
//    private Exception exception;
    
//    private Class<?> klasse;
//    
//    public DOLRStatus(Class<?> klasse, String name) {
//        this.klasse = klasse;
//        this.name = name;
//    }
    
    public DOLRStatus(String mnemonic, int value) {
//        this(DOLRStatus.class, mnemonic);
        this.name = mnemonic;

        this.value = value;
        DOLRStatus status;
        
        if ((status = DOLRStatus.byName.get(mnemonic)) != null || (status = DOLRStatus.fromEncoding(value)) != null) {
            System.err.println("Warning: DOLRStatus: name '" + mnemonic + "' already used: " + status.getName() + "=" + status.value);
        }

        DOLRStatus.byName.put(mnemonic, this);
        DOLRStatus.byValue.put(Integer.valueOf(value), this);
    }
    
    public boolean equals(Object other) {
        if (other instanceof DOLRStatus) {
            DOLRStatus otherStatus = (DOLRStatus) other;
            if (this.value == otherStatus.value) {
                return true;
            }
        }
        return false;
    }

    public String getName() {
        return this.name;
    }

    public String toString() {
        return String.format("%d %s", this.value, this.name);
    }

    public int toEncoding() {
        return this.value;
    }
    
    /**
     * Return {@code true} if this {@link DOLRStatus} signifies a successful status.
     */
    public boolean isSuccessful() {
        if (this.value < 0)
            return false;
        
        return (this.value / 100) == DOLRStatus.SUCCESSFUL;
    }
    
//     public void serializeToOutputStream(DataOutputStream out) throws IOException {
// XXX verify output stream is buffered....
//         new ObjectOutputStream(out).writeObject(this);
//     }

    public static final long INFORMATIONAL = 1;
    public static final long SUCCESSFUL = 2;

    public static final DOLRStatus THROWABLE = new DOLRStatus("Throwable", -1);
    
    public static final DOLRStatus OK = new DOLRStatus("OK", 200);

    public static final DOLRStatus BAD_REQUEST = new DOLRStatus("Bad Request", 400);
    public static final DOLRStatus INTERNAL_SERVER_ERROR = new DOLRStatus("Internal Server Error", 500);
    public static final DOLRStatus NOT_FOUND = new DOLRStatus("Not Found", 404);
    public static final DOLRStatus PRECONDITION_FAILED = new DOLRStatus("Precondition Failed", 412);
    public static final DOLRStatus SERVICE_UNAVAILABLE = new DOLRStatus("Service Unavailable", 503);
    public static final DOLRStatus BAD_GATEWAY = new DOLRStatus("Bad Gateway",  502);
    public static final DOLRStatus EXPECTATION_FAILED = new DOLRStatus("Expectation Failed",  417);
    public static final DOLRStatus NOT_IMPLEMENTED = new DOLRStatus("Not Implemented",  501);
    public static final DOLRStatus CONFLICT  = new DOLRStatus("Conflict", 409);
    public static final DOLRStatus FORBIDDEN  = new DOLRStatus("Forbidden", 403);
    public static final DOLRStatus GONE  = new DOLRStatus("Gone", 410);
    public static final DOLRStatus METHOD_NOT_ALLOWED  = new DOLRStatus("Method Not Allowed", 405);
    public static final DOLRStatus NOT_ACCEPTABLE  = new DOLRStatus("Not Acceptable", 406);
    public static final DOLRStatus REQUESTED_RANGE_NOT_SATISFIABLE = new DOLRStatus("Requested Range Not Satisfiable", 416);
    public static final DOLRStatus UNAUTHORIZED = new DOLRStatus("Unauthorized", 401);
    public static final DOLRStatus CREATED  = new DOLRStatus("Created", 201);
    public static final DOLRStatus ACCEPTED  = new DOLRStatus("Accepted", 202);
    public static final DOLRStatus NOT_MODIFIED = new DOLRStatus("Not Modified", 304);
    public static final DOLRStatus TEMPORARY_REDIRECT = new DOLRStatus("Temporary Redirect", 307);
    

    public static final DOLRStatus NOSUCHNODE = new DOLRStatus("No Such Node", 901);
}
