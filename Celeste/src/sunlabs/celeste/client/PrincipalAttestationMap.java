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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;

public class PrincipalAttestationMap extends
        HashMap<TitanGuid, PrincipalAttestation> {
    private final static long serialVersionUID = 1L;

    public PrincipalAttestationMap() {
        super();
    }

    /**
     * Reconstructs a {@code PrincipalAttestationMap} from its encoded form
     * (as produced by {@link #toString()}.
     *
     * @param encodedForm   the principal attestation map's encoded form
     */
    public PrincipalAttestationMap(String encodedForm) {
        //
        // The encoded form deliberately avoids using the ':' character, which
        // is known to be used as the separator for the encoded form of
        // PrincipalAttestation objects.
        //
        if (encodedForm == null || encodedForm.equals(""))
            return;
        String[] entries = encodedForm.split(";");
        for (String entry : entries) {
            String[] keyValue = entry.split("-");
            TitanGuid key = new TitanGuidImpl(keyValue[0]);
            PrincipalAttestation value =
                new PrincipalAttestation(keyValue[1]);
            this.put(key, value);
        }
    }

    /**
     * Renders this {@code PrincipalAttestationMap} into an encoded form
     * suitable for use with the {@link #PrincipalAttestationMap(String)
     * string constructor}.
     *
     * @return a string encoding this principal attestation map
     */
    //
    // XXX: Should toString() be burdened with an output format stability
    //      commitment?  Perhaps we should introduce a new encode() method for
    //      this purpose.
    //
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        //
        // Use an explicit iterator instead of a for loop so that the last
        // iteration can be detected, thereby avoiding adding a final trailing
        // ";".
        //
        Iterator<Map.Entry<TitanGuid, PrincipalAttestation>> it = this.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TitanGuid, PrincipalAttestation> entry = it.next();
            sb.append(String.format("%s-%s",
                entry.getKey().toString(),
                entry.getValue().toString()));
            if (it.hasNext())
                sb.append(";");
        }
        return sb.toString();
    }
}
