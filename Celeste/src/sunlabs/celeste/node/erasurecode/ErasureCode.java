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

package sunlabs.celeste.node.erasurecode;

import java.io.Serializable;

/**
 * Abstract class from which all Erasure Coders are derived.
 * 
 * Erasure Coders take as input original data and produce as output other data
 * from which the original data can be reconstructed.  The algorithms vary
 * from simple replication to sophisticated multipart reconstruction.
 */
public abstract class ErasureCode implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    public static class ErasureCodeException extends Exception {
        private static final long serialVersionUID = 1L;
        public ErasureCodeException(String text) {
            super(text);
        }

        public ErasureCodeException(Throwable reason) {
            super(reason);
        }
    }

    public static class UnsupportedAlgorithmException extends ErasureCodeException {
        private static final long serialVersionUID = 1L;
        public UnsupportedAlgorithmException(String text) {
            super(text);
        }
    }
    public static class InsufficientDataException extends ErasureCodeException {
        private static final long serialVersionUID = 1L;
        public InsufficientDataException(String text) {
            super(text);
        }
    }

    public static class NotRecoverableException extends ErasureCodeException {
        private static final long serialVersionUID = 1L;
        public NotRecoverableException(String text) {
            super(text);
        }

        public NotRecoverableException(Throwable reason) {
            super(reason);
        }
    }

    public static ErasureCode getInstance(String parameters)
    throws UnsupportedAlgorithmException {
        String[] tokens = parameters.split("/");
        try {
            if (tokens[0].equals(ErasureCodeIdentity.NAME)) return new ErasureCodeIdentity(parameters);
            if (tokens[0].equals(ErasureCodeReplica.NAME)) return new ErasureCodeReplica(parameters);
            throw new UnsupportedAlgorithmException("Unknown data replication: " + parameters);
        } catch (Exception e) {
            throw new UnsupportedAlgorithmException("Improperly specified data replication: " + parameters);
        }
    }

    public static ErasureCode getEncoder(String parameters, byte[] data)
    throws UnsupportedAlgorithmException {
        String[] tokens = parameters.split("/");
        try {
            if (tokens[0].equals(ErasureCodeIdentity.NAME)) return new ErasureCodeIdentity(parameters, data);
            if (tokens[0].equals(ErasureCodeReplica.NAME)) return new ErasureCodeReplica(parameters, data);
            throw new UnsupportedAlgorithmException("Unknown data replication: " + parameters);
        } catch (Exception e) {
            throw new UnsupportedAlgorithmException("Improperly specified data replication: " + parameters);
        }
    }

    public abstract String getName();

    /**
     * Given an array of byte arrays containing the erasure-coded parts of
     * an original byte array, reproduce the original byte array from the
     * fragment
     */
    public  abstract byte[] decodeData(byte[][] fragments)
    throws InsufficientDataException;

    public abstract byte[] getFragment(int index);

    /**
     * Return the number of fragments this erasure-coder produces.
     */
    public abstract int getFragmentCount();

    /**
     * Return the minimum number of fragments this erasure-coder must have to reconstruct the original data.
     */
    public abstract int getMinimumFragmentCount();
}
