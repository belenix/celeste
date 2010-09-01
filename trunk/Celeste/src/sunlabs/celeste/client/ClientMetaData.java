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

import java.io.Serializable;

import java.nio.ByteBuffer;

import java.util.Arrays;

import sunlabs.celeste.util.CelesteEncoderDecoder;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;

/**
 * <p>
 *
 * An instance of {@code ClientMetaData} encodes information about that
 * portion of the metadata pertinent to a modification to a given Celeste file
 * that is of no relevance to the Celeste layer itself, but that must be
 * signed so that it cannot be repudiated or forged.  Such metadata typically
 * includes items such as the file's content type, its creation and modify
 * times, and so on.
 *
 * </p><p>
 *
 * Instances of this class are immutable, and its {@code encodedContext} byte
 * arrays are copied as needed to prevent client code from altering them.
 *
 * </p>
 */
//
// XXX: Instances of this class are immutable, but at the price of copying the
//      opaqueContext members upon construction and upon invocation of
//      getContext().  Can we afford to pay this cost?  The primary use of
//      this context is to encode a small set of properties, so it's probably
//      ok.
//
//      In fact, it appears that the _only_ use of this field is to encode a
//      FileProperties object, so we could avoid the extra copies by taking a
//      FileProperties object as an argument to the constructor and replacing
//      getContext with a method to deliver a reconstituted FileProperties
//      object.  (But that's just use in the existing system.  One could
//      envision other subsystems built on top of the Celeste layer that use
//      the opaqueContext for other purposes, so the newly proposed
//      constructor and method should probably sit alongside the existing
//      ones.)
//
//      Although we may wish to revisit this issue later, the resolution is to
//      accept the cost of copying, without attempting to mitigate it as
//      proposed above.
//
public class ClientMetaData implements Serializable {
    private final static long serialVersionUID = 1L;

    private static final int version = 1;

    public static class ContextException extends Exception {
        private static final long serialVersionUID = 0;
        public ContextException(String message) {
            super(message);
        }
    }

    private final byte [] opaqueContext;

    public ClientMetaData() {
        this.opaqueContext = null;
    }

    public ClientMetaData(ByteBuffer opaqueContext) {
        //
        // Copy the supplied opaqueContext so that this instance is guaranteed
        // to be immutable.
        //
        //opaqueContext.rewind();
        this.opaqueContext = new byte[opaqueContext.remaining()];
        opaqueContext.duplicate().get(this.opaqueContext);
    }

    public ClientMetaData(String hexEncodedContext) throws ClientMetaData.ContextException {
        String[] args = hexEncodedContext.split(":");
        int v = Integer.parseInt(args[0]);
        if (v != ClientMetaData.version) {
            throw new ContextException("Bad version");
        }
        if (args.length > 1 && args[1].length() > 0)
            this.opaqueContext = CelesteEncoderDecoder.fromHexString(args[1]);
        else
            this.opaqueContext = null;
    }

    /**
     * Fetch the raw (byte array) form of this {@code ClientMetaData}
     * instance.
     *
     * @return  this client metadata instance, in {@code byte[]} form
     */
    public byte[] getContext() {
        if (this.opaqueContext == null)
            return null;
        int length = this.opaqueContext.length;
        byte[] opaqueContext = new byte[length];
        System.arraycopy(this.opaqueContext, 0, opaqueContext, 0, length);
        return opaqueContext;
    }

    public TitanGuid getId() {
        if (this.opaqueContext == null)
            return new TitanGuidImpl(new byte[0]);
        return new TitanGuidImpl(this.opaqueContext);
    }

    /**
     * Return a string representation of this {@code ClientMetaData} instance.
     * The representation includes the instance's encoding version as well as
     * the encoded representation itself.
     *
     * @return  this client metadata instance, in string form
     */
    public String getEncodedContext() {
        String encodedContext = (this.opaqueContext == null) ? "" :
            CelesteEncoderDecoder.toHexString(this.opaqueContext);
        return String.format("%d:%s",
                ClientMetaData.version,
                encodedContext);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ClientMetaData))
            return false;
        ClientMetaData cmd = (ClientMetaData)other;
        if (this.opaqueContext == null && cmd.getContext() == null)
            return true;
        if (this.opaqueContext == null)
            return false;
        return Arrays.equals(this.opaqueContext, cmd.opaqueContext);
    }

    @Override
    public int hashCode() {
        if (this.opaqueContext == null)
            return 0;
        return Arrays.hashCode(this.opaqueContext);
    }
}
