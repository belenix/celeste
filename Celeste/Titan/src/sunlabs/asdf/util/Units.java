/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.asdf.util;

import java.util.HashMap;

/**
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class Units {
    public final static long KILOBYTE = 1024L;
    public final static long MEGABYTE = 1024L*1024L;
    public final static long GIGABYTE = 1024L*1024L*1024L;
    public final static long TERABYTE = 1024L*1024L*1024L*1024L;
    public final static long PETABYTE = 1024L*1024L*1024L*1024L*1024L;
    public final static long EXABYTE = 1024L*1024L*1024L*1024L*1024L*1024L;
    
    private final static HashMap<String,Long> suffix = new HashMap<String,Long>();
    static {
        Units.suffix.put("", 1L);
        Units.suffix.put("K", KILOBYTE);
        Units.suffix.put("M", MEGABYTE);
        Units.suffix.put("G", GIGABYTE);
        Units.suffix.put("T", TERABYTE);
        Units.suffix.put("P", PETABYTE);
        Units.suffix.put("E", EXABYTE);
        // The following are too big to express in a Java long.
//        Units.suffix.put("Z", 1024L*1024*1024*1024*1024L*1024L*1024L);
//        Units.suffix.put("Y", 1024L*1024*1024*1024*1024L*1024L*1024L*1024L);
    }
    
    /**
     * Give a String representation of a number which is of the form [-][0-9]+[KMGTPE]
     * return a long value representing the full numeric value.
     */
    public static long parseByte(String value) throws NumberFormatException {
        StringBuilder numeral = new StringBuilder("");
        StringBuilder units = new StringBuilder();

        int position = 0;
        // absorb all leading white space
        while (position < value.length()) {
            char c = value.charAt(position);
            if (!Character.isWhitespace(c)) {
                break;
            }
            position++;
        }

        // Capture a leading '-' sign (if present).
        if (value.charAt(position) == '-') {
            numeral.append(value.charAt(position));
            position++;
        }
        
        while (position < value.length()) {
            char c = value.charAt(position);
            if (Character.isDigit(c)) {
                numeral.append(c);
            } else {
                break;
            }
            position++;
        }

        while (position < value.length()) {
            char c = value.charAt(position);
            position++;
            if (Character.isWhitespace(c))
                break;
            units.append(c);
        }
        
        return Long.parseLong(numeral.toString()) * Units.suffix.get(units.toString());
    }
    
    /**
     * Produce a String representation of the value in base-2 magnitudes.
     * @param value
     */
    public static String longToCapacityString(long value) {
        if (value < KILOBYTE) {
            return Long.toString(value);
        }
        if (value < MEGABYTE) {
            return Long.toString((value + (KILOBYTE / 2)) / KILOBYTE) + "K";
        }
        if (value < GIGABYTE) {
            return Long.toString((value + (MEGABYTE / 2)) / MEGABYTE) + "M";
        }
        if (value < TERABYTE) {
            return Long.toString((value + (GIGABYTE / 2)) / GIGABYTE) + "G";
        }
        if (value < PETABYTE) {
            return Long.toString((value + (TERABYTE / 2)) / TERABYTE) + "T";
        }
        if (value < EXABYTE) {
            return Long.toString((value + (PETABYTE / 2)) / PETABYTE) + "P";
        }
        return Long.toString((value + (EXABYTE / 2)) / EXABYTE) + "E";
    }
    
    public static void main(String[] args) {
        System.out.printf("%d%n", parseByte("-1"));
        System.out.printf("%d%n", parseByte("11"));
        System.out.printf("%d%n", parseByte("104"));
        System.out.printf("%d%n", parseByte("1K"));
        System.out.printf("%d%n", parseByte("1G"));
        System.out.printf("%d%n", parseByte("100M"));
        System.out.printf("%d%n", parseByte("400T"));

        System.out.printf("%s%n", longToCapacityString(parseByte("-1")));
        System.out.printf("%s%n", longToCapacityString(parseByte("11")));
        System.out.printf("%s%n", longToCapacityString(parseByte("104")));
        System.out.printf("%s%n", longToCapacityString(parseByte("1K")));
        System.out.printf("%s%n", longToCapacityString(parseByte("1G")));
        System.out.printf("%s%n", longToCapacityString(parseByte("100M")));
        System.out.printf("%s%n", longToCapacityString(parseByte("400T")));
    }
}
