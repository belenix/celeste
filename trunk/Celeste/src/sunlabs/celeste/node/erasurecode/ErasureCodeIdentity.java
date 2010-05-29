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

import java.nio.ByteBuffer;

public class ErasureCodeIdentity extends ErasureCode {
    private final static long serialVersionUID = 1L;

    public final static String NAME = "ErasureCodeIdentity";
    private int fragmentCount;
    private ByteBuffer data;

    public ErasureCodeIdentity(String parameters) {
        super();
        String[] tokens = parameters.split("/");
        this.fragmentCount = Integer.parseInt(tokens[1]);
    }

    public ErasureCodeIdentity(String parameters, byte[] data) {
        super();
        String[] tokens = parameters.split("/");
        this.fragmentCount = Integer.parseInt(tokens[1]);
        this.data = ByteBuffer.wrap(data);
    }

    @Override
    public String getName() {
        return ErasureCodeIdentity.NAME + "/" + this.fragmentCount;
    }

    @Override
    public byte[] getFragment(int index) {
        byte key = 10;

        this.data.rewind();
        byte[] fragment = new byte[this.data.remaining()];
        for (int j = 0; data.remaining() > 0; j++) {
            fragment[j] = ((byte) (this.data.get() ^ key));
        }

        return fragment;
    }

    @Override
    public byte[] decodeData(byte[][] fragments) {
        byte key = 10;

        //ByteBuffer result = ByteBuffer.allocate(fragments[0].length);
        byte[] result = new byte[fragments[0].length];

        for (int i = 0; i < fragments[0].length; i++) {
            byte b = fragments[0][i];
            result[i] = ((byte) (b ^  key));
        }

        return result;
    }

    public int getFragmentCount() {
        return this.fragmentCount;
    }

    @Override
    public int getMinimumFragmentCount() {
        return 1;
    }
    
    @Override
    public String toString() {
        return ErasureCodeIdentity.NAME + "/" + this.fragmentCount;
    }
}
