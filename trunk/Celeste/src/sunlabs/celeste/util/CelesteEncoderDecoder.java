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
package sunlabs.celeste.util;

import java.io.UnsupportedEncodingException;

import java.net.URLDecoder;
import java.net.URLEncoder;

import java.util.Locale;

/**
 * Miscellaneous functions for encoding and decoding data.
 */
public final class CelesteEncoderDecoder {
    /**
     * 
     */
    public static String stringEncoder(String s) {
	try {
	    return URLEncoder.encode(s, "UTF-8");
	} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
	}
    }

    /**
     * 
     */
    public static String stringDecoder(String s) {
	try {
	    return URLDecoder.decode(s, "UTF-8");
	} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
	}
    }

    /**
     * 
     */
    public static String stringDecoder(byte[] bytes) {
        return CelesteEncoderDecoder.stringDecoder(new String(bytes));
    }

    /**
     * 
     */
    public static String stringEncoder(byte[] bytes) {
	return CelesteEncoderDecoder.stringEncoder(new String(bytes));
    }
    
    public static String toHexString(byte[] b) {
        if (b == null)
            return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            String res = ("0" + Integer.toHexString(b[i]));
            result.append(res.substring(res.length() - 2));
        }               
        return result.toString().toUpperCase(Locale.US);
        
    }
    
    public static byte[] fromHexString(String h) {
        if (h == null)
            return null;        
        if (h.length() % 2 != 0)
            return null;
        h = h.toUpperCase(Locale.US);
        if (h.matches(".*[^0-9A-F].*"))
            return null;
        
        byte[] result = new byte[h.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (Integer.parseInt(h.substring(i*2, i*2 + 2), 16));
        }
        return result;
        
    }
}
