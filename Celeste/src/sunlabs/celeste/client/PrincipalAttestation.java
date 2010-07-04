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
package sunlabs.celeste.client;

import java.io.Serializable;

import sunlabs.beehive.BeehiveObjectId;
import sunlabs.beehive.api.Credential;
import sunlabs.celeste.node.ProfileCache;
import sunlabs.celeste.util.CelesteEncoderDecoder;

//
// Contains information recording an attestation of one principal by another.
// (Will be) used to attest that a given principal is a member of the group
// denoted by the attestor.
//
public class PrincipalAttestation implements Serializable {
    private final static long serialVersionUID = 1L;
    
    private final BeehiveObjectId attesteeId;
    private final BeehiveObjectId attestingId;
    //
    // XXX: Need method that returns a profile's current version.
    //
    //      Still not sure how to accomplish "get my version".  This is
    //      difficult, because the version rides with the file holding the
    //      encoded representation of a profile rather than with the decoded
    //      profile.  When unpacking a profile from its file representation,
    //      we could potentially tell it what its version is.  But when a
    //      profile is modified and before it's written out, we can't know
    //      what its version will be.  So perhaps profiles can't be used to
    //      attest until they've been written out.  (That probably collides
    //      head on with the locked/unlocked state information a profile
    //      maintains.)
    //
    private BeehiveObjectId attestingVersionId = null;
    private final Credential.Signature signature;

    //
    // Create a PrincipalAttestation object that asserts that the principal
    // denoted by attestingProfile attests to the principal denoted by
    // attesteeId.
    //
    public PrincipalAttestation(BeehiveObjectId attesteeId,
            Profile_ attestingProfile, char[] attestingPassword)
	throws Credential.Exception {

        this.attesteeId = attesteeId;
        this.attestingId = attestingProfile.getObjectId();

	this.signature =
	    attestingProfile.sign(attestingPassword, this.attesteeId);

        //
        // XXX: Still need to deal with recording the version of the attestor.
        //      Maybe it needs to be another argument to the constructor.
        //
    }

    //
    // Create a PrincipalAttestation object that asserts that the principal
    // denoted by attestingProfile (at version attestingVersionid) attests to
    // the principal denoted by attesteeId.
    //
    public PrincipalAttestation(BeehiveObjectId attesteeId,
				Profile_ attestingProfile,
				char[] attestingPassword,
				BeehiveObjectId attestingVersionId)
	throws Credential.Exception {

        this.attesteeId = attesteeId;
        this.attestingId = attestingProfile.getObjectId();
        this.attestingVersionId = attestingVersionId;
	this.signature =
	    attestingProfile.sign(attestingPassword, this.attesteeId);
    }

    /**
     * Reconstructs a {@code PrincipalAttestation} from its encoded form (as
     * produced by {@link #toString()}.
     *
     * @param encodedForm   the principal attestation's encoded form
     */
    public PrincipalAttestation(String encodedForm) {
        String[] encodedFields = encodedForm.split(":");
        if (encodedFields.length != 6)
            throw new IllegalArgumentException(
                "incorrectly encoded argument");
        this.attesteeId = new BeehiveObjectId(encodedFields[0]);
        this.attestingId = new BeehiveObjectId(encodedFields[1]);
        if (encodedFields[2].equals(""))
            this.attestingVersionId = null;
        else
            this.attestingVersionId = new BeehiveObjectId(encodedFields[2]);
        BeehiveObjectId signatureProfileId = new BeehiveObjectId(encodedFields[3]);
        String algorithm = encodedFields[4];
        byte[] signatureBytes =
            CelesteEncoderDecoder.fromHexString(encodedFields[5]);
        this.signature = new Credential.Signature(
            signatureProfileId, algorithm, signatureBytes);
    }

    /**
     * Return the object-id of the attestee.
     *
     * @return the attestee's object id
     */
    public BeehiveObjectId getAttesteeId() {
        return this.attesteeId;
    }

    /**
     * Return the object-id of the attestor.
     *
     * @return the attestor's object id
     */
    public BeehiveObjectId getAttestorId() {
        return this.attestingId;
    }

    //
    // The next two methods (will) work by fetching the profile named by
    // this.attestingId (or the version of it named by attestingVersionId)
    // and then using its verify() method to confirm or refute the claim held
    // in this.signature (or whatever I end up calling that member).
    //

    //
    // Return true if this token represents a valid historical attestation
    // (taking the specified version into account).
    //
    public boolean verifyVersion(ProfileCache cache, BeehiveObjectId versionId)
	throws Exception, Credential.Exception {

        Credential attestingProfile = cache.get(this.attestingId, this.attestingVersionId);
        return attestingProfile.verify(this.signature, this.attesteeId);
    }

    //
    // Return true if this token represents a valid attestation with respect
    // to the current version of the attesting entity.
    //
    public boolean verifyCurrent(ProfileCache cache) throws Exception, Credential.Exception {

        Credential attestingProfile = cache.get(this.attestingId);
        //
        // If attestingProfile's keying material has changed since the
        // signature was created, then verification of the signature against
        // the data used to create it will fail.
        //
        return attestingProfile.verify(this.signature, this.attesteeId);
    }

    /**
     * Renders this {@code PrincipalAttestation} into an encoded form suitable
     * for use with the {@link #PrincipalAttestation(String) string
     * constructor}.
     *
     * @return a string encoding this principal attestation object
     */
    //
    // XXX: Should toString() be burdened with an output format stability
    //      commitment?  Perhaps we should introduce a new encode() method for
    //      this purpose.
    //
    @Override
    public String toString() {
        String avid = (this.attestingVersionId == null) ?
            "" : this.attestingVersionId.toString();
        return String.format("%s:%s:%s:%s:%s:%s",
            this.attesteeId.toString(),
            this.attestingId.toString(),
            avid,
            //
            // XXX: Is the following field redundant with
            //      this.attestingId.toString() above?
            //
            this.signature.getId().toString(),
            this.signature.getAlgorithm(),
            CelesteEncoderDecoder.toHexString(this.signature.getSignature()));
    }
}
