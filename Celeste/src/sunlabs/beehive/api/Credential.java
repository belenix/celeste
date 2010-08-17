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
package sunlabs.beehive.api;

import java.math.BigInteger;

import java.io.Serializable;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.node.object.InspectableObject;
import sunlabs.beehive.node.object.RetrievableObject;
import sunlabs.beehive.node.object.StorableObject;

/**
 * A {@code Credential} represents an identity of an external entity such as a user, organisation, application, etc.
 * Credentials provide the ability to create and verify a signature of a list of {@link BeehiveObjectId} instances.
 */
public interface Credential extends Serializable, StorableObject.Handler.Object, RetrievableObject.Handler.Object, InspectableObject.Handler.Object {

    /**
     * Signal that a {@code Credential} cannot be used to accomplish a particular task (such as signing and
     * verification). This might be due to insufficient information
     * (e.g. missing private key) or a cryptographic problem (e.g. a
     * configuration problem or corrupted data).
     */
    // XXX This should be merged with CelesteException.CredentialException
    public static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs an Exception with no detail message.
         */
        public Exception() {
            super();
        }

        /**
         * Constructs an Exception with the specified detail message.
         *
         * @param message a detail message
         */
        public Exception(String message) {
            super(message);
        }

        /**
         * Constructs an Exception with the specified detail message and cause.
         *
         * @param message a detail message
         * @param reason a cause for the exception
         */
        public Exception(String message, Throwable reason) {
            super(message, reason);
        }

        /**
         * Constructs an Exception with no detail message and a cause.
         *
         * @param reason a cause for the exception
         */
        public Exception(Throwable reason) {
            super(reason);
        }
    }

    /**
     * A credential signature consists of the object-id of the signing
     * {@code Credential}, the signature algorithm used, and the signature
     * data as a simple array of bytes.
     *
     */
    public static class Signature implements Serializable {
        private final static long serialVersionUID = 1L;

        private final BeehiveObjectId id;
        private final String algorithm;
        private final byte[] signature;

        /**
         * Create a Signature object associated with a credential, with a
         * given digital signature, and the name of the algorithm used to
         * create (and verify) that signature.
         *
         * @param id        the identifier of the {@code Credential} used to
         *                  create the digital signature
         * @param algorithm the algorithm used to create the digital signature
         * @param signature the digital signature
         */
        public Signature(BeehiveObjectId id, String algorithm,
            byte[] signature) {

            this.id = id;
            this.algorithm = algorithm;
            this.signature = signature;
        }

        /**
         * Return the object identifier of the {@code Credential} that
         * created this {@code Signature}.
         *
         * @return the id of the {@code Credential} used to create this
         * {@code Signature}
         */
        public BeehiveObjectId getId() {
            return this.id;
        }

        /**
         * Return the name of the algorithm used to create (and verify) the
         * digital signature contained in this object.
         *
         * @return the name of the signing algorithm
         */
        public String getAlgorithm() {
            return this.algorithm;
        }

        /**
         * Return the digital signature contained in this object.
         *
         * @return the digital signature
         */
        public byte[] getSignature() {
            return this.signature;
        }

        /**
         * Return the value of this signature as a {@link BeehiveObjectId}.
         */
        public BeehiveObjectId getObjectId() {
            return new BeehiveObjectId(this.signature);
        }

        @Override
        public String toString() {
            return this.id + " " + this.algorithm + " "
                + new BigInteger(this.signature).toString();
        }
    }

    /**
     * Return the object-id of this {@code Credential}.
     *
     * Subclasses implement this method according to their particular
     * mechanism for identifying {@code Credential} instances.
     *
     * Examples are to produce a object-id based upon a distinguished name,
     * or upon a public key.  Both of these examples must permit the
     * object-id of the {@code Credential} to be verified by publicly
     * available information.
     *
     * @return an object-id for this {@code Credential}
     */
    public BeehiveObjectId getObjectId();

    /**
     *
     * Returns {@code true} if this {@code Credential} is limited only to
     * verifying signatures created by its corresponding unlimited version.
     *
     * @return {@code true} if this credential is verify-only.
     */
    public boolean isLimited();
    
    /**
     * Sign the collection of {@link BeehiveObjectId} instances.
     *
     * @param password a password needed by the implementing class to sign
     * the collection
     *
     * @param ids the list of object ids to sign
     *
     * @return a {@code Signature} object containing the digital signature
     *
     * @throws Credential.Exception
     */
    public Credential.Signature sign(char[] password, BeehiveObjectId... ids) throws Credential.Exception;

    /**
     * Verify that a given {@code Signature} was signed by this {@code Credential}.
     *
     * @param signature the signature object to verify
     * @param           ids the array of object ids which the signature is
     *                  purported to have signed
     *
     * @return true if this {@code Credential} can verify that the {@code
     *              Signature} object's digital signature does correctly sign
     *              the object ids listed
     *
     * @throws Credential.Exception
     */
    public boolean verify(Credential.Signature signature, BeehiveObjectId... ids) throws Credential.Exception;

    public String getName();

    public byte[] decrypt(char[] invokerPassword, byte[] encryptedDeleteToken) throws Credential.Exception;

    public byte[] encrypt(byte[] bytes) throws Credential.Exception;
}
