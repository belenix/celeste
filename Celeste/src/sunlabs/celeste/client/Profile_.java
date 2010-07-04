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

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.BeehiveObject;
import sunlabs.beehive.api.Credential;
import sunlabs.beehive.node.AbstractBeehiveObject;
import sunlabs.beehive.node.services.object.CredentialObjectHandler;

/**
 * A Profile represents a responsible entity to and for Celeste.
 *
 * Profiles are versioned Celeste objects with a few special properties:
 * <ol>
 *
 * <li> The object id is derived only from the name. This should be changed
 *      so that we don't have multiple simple String->ObjectId mappings
 *      potentially causing objects of the wrong type to be overwritten.
 *
 * <li> Profiles can currently not be deleted -- this would undermine their
 *      use as anchors in the authentication framework within Celeste.</li>
 *
 * <li> The creator and all writers of this object are currently supposed to
 *      have the same object-id.</li>
 *
 * <li> Each profile contains
 *
 *    <ul>
 *    <li>a profile name</li>
 *    <li>a public key, bound to the profile name</li>
 *    <li>an optional password protected private key</li>
 *    </ul>
 *    </li>
 *
 * <li> The signature in the celeste permission object must always match the
 *      public key in the profile.</li>
 *
 * </ol>
 * </p><p>
 *
 * {@code Profile_} objects are immutable, though they can be replaced in
 * the object store. Only {@code Profile_} objects that contain a private
 * key can be used to create {@code Signature} objects and encrypt data.
 *
 * </p><p>
 */
public class Profile_ extends AbstractBeehiveObject implements Credential {
    private static final long serialVersionUID = 1L;
    private final static String KEY_TYPE = "RSA";
    private final static String DIGITAL_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private final static String SIGNATURE_CIPHER = "RSA/ECB/PKCS1Padding";
    private final static String KEY_CIPHER = "PBEWithMD5AndDES";
    private final static int KEY_SIZE = 1024;
    private final static byte[] salt = "celesteb".getBytes();
    private final static PBEParameterSpec paramSpec = new PBEParameterSpec(salt, 20);
    private static final BeehiveObjectId credentialBase = new BeehiveObjectId("credential".getBytes());

    private final String name;
    private final byte[] encryptedPrivateKey;
    private final PublicKey publicKey;
    private final boolean limited;
    private transient BeehiveObjectId cachedId;
    private BeehiveObjectId dataId;

    /**
     * Create a new Profile_ based on a newly generated key pair, with the
     * new private key encrypted with the password.
     *
     * @param name name assigned to this profile object
     * @param password password that encrypts the private key
     *
     * @throws Credential.Exception
     */
    public Profile_(String name, char[] password) throws Credential.Exception {
        //
        // N.B.  Credentials are never deleted, so the delete token created
        // here will never be used (and thus there's no need to record its
        // value).
        //
        super(CredentialObjectHandler.class, new BeehiveObjectId(), BeehiveObject.INFINITE_TIME_TO_LIVE);
        this.name = name;
        this.limited = false;

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(Profile_.KEY_TYPE);
            generator.initialize(Profile_.KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = pair.getPublic();

            Cipher cipher = getCipher(password, Cipher.WRAP_MODE);
            this.encryptedPrivateKey = cipher.wrap(pair.getPrivate());
        } catch (GeneralSecurityException ex) {
            throw new Credential.Exception(ex);
        }
    }

    /**
     * Create a new Profile_ with an existing public key. This variant of a
     * Credential can only be used to verify Signature objects. The
     * Signature signer must remember the private key (or the original
     * Profile_ separately.
     */
    private Profile_(String name, PublicKey publicKey) {
        //
        // N.B.  Credentials are never deleted, so the delete token created
        // here will never be used (and thus there's no need to record its
        // value).
        //
        super(CredentialObjectHandler.class, new BeehiveObjectId(), BeehiveObject.INFINITE_TIME_TO_LIVE);
        this.name = name;
        this.limited = true;
        this.publicKey = publicKey;
        this.encryptedPrivateKey = null;
    }

    /**
     * Create a new Profile_ from the name and existing public key from
     * another Profile_ object. The resulting Credential can only be used
     * to verify Signature objects or decrypt data.
     *
     * @return a new limited {@code Profile_} object
     */
    public Profile_ limit() {
        return new Profile_(this.name, this.publicKey);
    }

    /**
     *
     * Returns {@code true} if this {@code Profile_} is limited only to
     * verifying signatures created by its corresponding unrestricted version.
     *
     * @return {@code true} if this profile is verify-only.
     */
    public boolean isLimited() {
        return this.limited;
    }

    /**
     * Create an object id based on the name.
     */
    @Override
    public BeehiveObjectId getObjectId() {
        if (cachedId == null) {
            cachedId = new BeehiveObjectId(this.getName().getBytes());
        }
        return cachedId;
    }

    /**
     * Return the name bound to this profile.
     *
     * @return the name associated with this profile
     */
    public String getName() {
        return name;
    }

    /**
     * Return an initialized cipher object for the given password.
     */
    private Cipher getCipher(char[] password, int mode)
        throws GeneralSecurityException {

        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKeyFactory keyFac =
            SecretKeyFactory.getInstance(Profile_.KEY_CIPHER);
        SecretKey pbeKey = keyFac.generateSecret(pbeKeySpec);
        Cipher cipher = Cipher.getInstance(Profile_.KEY_CIPHER);
        cipher.init(mode, pbeKey, paramSpec);
        return cipher;
    }

    /**
     * Decrypt the private key with the provided password and return it.
     * 
     * @throws Credential.Exception
     */
    public PrivateKey getPrivateKey(char[] password) throws Credential.Exception {

        if (this.encryptedPrivateKey == null) {
            throw new Credential.Exception("no private key provided");
        }
        try {
            Cipher cipher = getCipher(password, Cipher.UNWRAP_MODE);
            return (PrivateKey) cipher.unwrap(this.encryptedPrivateKey, Profile_.KEY_TYPE, Cipher.PRIVATE_KEY);
        } catch (GeneralSecurityException ex) {
            throw new Credential.Exception(ex);
        }
    }

    //
    // Methods from AbstractBeehiveObject
    //

    @Override
    public BeehiveObjectId getDataId() {
        if (this.dataId == null) {
            //
            // Hash together everything that distinguishes this profile from any
            // other object stored in Beehive.  (Since the cachedId field is
            // derived from the name field, it's not included.)
            //
            BeehiveObjectId id = new BeehiveObjectId(this.name.getBytes());
            id = id.add(this.encryptedPrivateKey);
            this.dataId = id.add(this.publicKey.getEncoded());
        }
        return this.dataId;
    }

    /**
     * Sign the collection of {@link BeehiveObjectId} instances using this
     * profile's private key.
     *
     * @param password the password needed access the encrypted private key
     * @param ids the list of object ids to sign
     *
     * @return a {@code Signature} object containing the digital signature
     *
     * @throws Credential.Exception encapsulating a {@link GeneralSecurityException}
     * instance thrown by the underlying {@link java.security.Signature} system.
     */
    public Credential.Signature sign(char[] password, BeehiveObjectId... ids) throws Credential.Exception {

        try {
            String algorithm = Profile_.DIGITAL_SIGNATURE_ALGORITHM;
            java.security.Signature sign =
                java.security.Signature.getInstance(algorithm);

            sign.initSign(this.getPrivateKey(password));
            for (BeehiveObjectId id : ids) {
                if (id != null)
                    sign.update(id.getBytes());
            }
            return new Credential.Signature(this.getObjectId(),
                sign.getAlgorithm(), sign.sign());
        } catch (GeneralSecurityException e) {
            throw new Credential.Exception(e);
        }
    }

    /**
     * Verify that a given {@code Signature} was signed by this
     * {@code Credential}.
     *
     * @param signature the signature object to verify
     * @param ids the array of object ids which the signature is purported
     * to have signed
     *
     * @return true if this {@code Credential} can verify that the {@code
     * Signature} object's digital signature does correctly sign the object
     * ids listed
     *
     * @throws Credential.Exception
     */
    public boolean verify(Credential.Signature signature,
        BeehiveObjectId... ids)
        throws Credential.Exception {

        try {
            java.security.Signature verifier =
                java.security.Signature.getInstance(signature.getAlgorithm());
            verifier.initVerify(this.publicKey);
            for (BeehiveObjectId id : ids) {
                if (id != null)
                    verifier.update(id.getBytes());
            }
            return verifier.verify(signature.getSignature());
        } catch (GeneralSecurityException e) {
            throw new Credential.Exception(e);
        }
    }

    /**
     * Encrypt the supplied byte array with this Profile's public key.
     *
     * @param data to be encrypted.
     *
     * @return The encrypted result.
     *
     * @throws Credential.Exception
     */
    public byte[] encrypt(byte[] data)
        throws Credential.Exception {

        try {
            Cipher cipher =
                Cipher.getInstance(Profile_.SIGNATURE_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, this.publicKey);
            byte[] capsule = cipher.doFinal(data);
            return capsule;
        } catch (GeneralSecurityException e) {
            throw new Credential.Exception(e);
        }
    }

    /**
     * Decrypt the supplied byte array with this Profile's private key.
     *
     * @param password  the password needed to access the encrypted private
     *                  key
     * @param data      the data to be decrypted
     *
     * @return the unencrypted data
     *
     * @throws Credential.Exception
     */
    public byte[] decrypt(char[] password, byte[] data)
        throws Credential.Exception {

        try {
            Cipher cipher =
                Cipher.getInstance(Profile_.SIGNATURE_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, this.getPrivateKey(password));
            byte[] capsule = cipher.doFinal(data);
            return capsule;
        } catch (GeneralSecurityException e) {
            throw new Credential.Exception(e);
        }
    }

    public XHTML.EFlow inspectAsXHTML(URI uri, Map<String,HTTP.Message> props) {
    	XHTML.Div result = super.toXHTML(uri, props);
    	
    	XHTML.Table table = new XHTML.Table(
    			new XHTML.Table.Caption("Credential"),
    			new XHTML.Table.Body(
    					new XHTML.Table.Row(new XHTML.Table.Data("Credential Name"), new XHTML.Table.Data(this.name)),
    					new XHTML.Table.Row(new XHTML.Table.Data("Public&nbsp;Key&nbsp;Algorithm"), new XHTML.Table.Data(this.publicKey.getAlgorithm())),
    					new XHTML.Table.Row(new XHTML.Table.Data("Public&nbsp;Key&nbsp;Format"), new XHTML.Table.Data(this.publicKey.getFormat()))));
    	
    	result.append(table);
    	return result;
    }
}
