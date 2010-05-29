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
package sunlabs.asdf.web.http;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class Base64 {
    public static String lexicon = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    
    public static String encode(String string) {
        byte[] bytes = string.getBytes();
        int result_length = (((bytes.length * 4 / 3) + 3) / 4) * 4;
        byte[] result = new byte[result_length];

        int result_index = 0;
        int by3s = bytes.length / 3;
        for (int i = 0; i < by3s; i++) {
            byte[] bit24 = new byte[3];
            bit24[0] = bytes[i*3];
            bit24[1] = bytes[i*3+1];
            bit24[2] = bytes[i*3+2];
            int b1 = (bit24[0] & 0xFC) >> 2;
            int b2 = (bit24[0] & 0x3) << 4 | (bit24[1] & 0xF0) >> 4;
            int b3 = (bit24[1] & 0x0F) << 2 | (bit24[2] & 0xF0) >> 6;
            int b4 = (bit24[2] & 0x3F);
            result[result_index++] = (byte) lexicon.charAt(b1);
            result[result_index++] = (byte) lexicon.charAt(b2);
            result[result_index++] = (byte) lexicon.charAt(b3);
            result[result_index++] = (byte) lexicon.charAt(b4);
        }

        // compute the remainder
        int remainder = bytes.length - (bytes.length / 3) * 3;
        if (remainder == 1) {
            byte[] bit24 = new byte[3];
            bit24[0] = bytes[bytes.length-1];
            result[result_index++] = (byte) lexicon.charAt((bit24[0] & 0xFC) >> 2);
            result[result_index++] = (byte) lexicon.charAt((bit24[0] & 0x03) << 4);
            result[result_index++] = '=';
            result[result_index++] = '=';
        } else if (remainder == 2) {
            byte[] bit24 = new byte[3];
            bit24[0] = bytes[bytes.length-2];
            bit24[1] = bytes[bytes.length-1];
            bit24[2] = 0;
            result[result_index++] = (byte) lexicon.charAt((bit24[0] & 0xFC) >> 2);
            result[result_index++] = (byte) lexicon.charAt((bit24[0] & 0x3) << 4 | (bit24[1] & 0xF0) >> 4);
            result[result_index++] = (byte) lexicon.charAt((bit24[1] & 0x0F) << 2 | (bit24[2] & 0xF0) >> 6);
            result[result_index++] = '=';
        }
        return new String(result);
    }

    /**
     * Insert the base-64 {@code value} into the 3 octet {@code register} at position ({@code index % 4}).
     * <p>
     * Once the register is full, octets can be accessed as {@code register[0]}, {@code register[1]}, and {@code register[2]}.
     * </p>
     * <p>
     * From http://en.wikipedia.org/wiki/Base64
     * <style>
     * table.base64 { border: 1px solid black; border-collapse: collapse;}
     * table.base64 td { border: 1px solid black; }
     * </style>
     * <table class="base64">
     * <tbody>
     * <td>Octet Out</td>
     * <td colspan="8" align="center"><b>M</b></td>
     * <td colspan="8" align="center"><b>a</b></td>
     * <td colspan="8" align="center"><b>n</b></td>
     * </tr>
     * <tr>
     * <td>ASCII</td>
     * <td colspan="8" align="center">77</td>
     *
     * <td colspan="8" align="center">97</td>
     * <td colspan="8" align="center">110</td>
     * </tr>
     * <tr>
     * <td>Bit pattern</td>
     * <td>0</td>
     * <td>1</td>
     * <td>0</td>
     * <td>0</td>
     * <td>1</td>
     *
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>1</td>
     * <td>0</td>
     * <td>0</td>
     * <td>0</td>
     *
     * <td>0</td>
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>1</td>
     * <td>1</td>
     *
     * <td>0</td>
     * </tr>
     * <tr>
     * <td>Index</td>
     * <td colspan="6" align="center">19</td>
     * <td colspan="6" align="center">22</td>
     * <td colspan="6" align="center">5</td>
     * <td colspan="6" align="center">46</td>
     * </tr>
     * <tr>
     * <td>Base64-In</td>
     * <td colspan="6" align="center"><b>T</b></td>
     * <td colspan="6" align="center"><b>W</b></td>
     * <td colspan="6" align="center"><b>F</b></td>
     * <td colspan="6" align="center"><b>u</b></td>
     * </tr>
     * </tbody></table>
     * </p>
     *
     * @param register
     * @param index
     * @param value
     * @return {@code true} when the register is full, {@code false} otherwise.
     */
    private static boolean insert64(byte[] register, int index, byte value) {
        index = index % 4;
        if (index == 0) {
            register[0] = (byte) (value << 2);
            register[1] = 0;
            register[2] = 0;
        } else if (index == 1) {
            register[0] |= (byte) (value & 0x30) >> 4;
            register[1] |= (byte) (value & 0x0F) << 4;
        } else if (index == 2) {
            register[1] |= (byte) (value & 0xFC) >> 2;
            register[2] |= (byte) (value & 0x03) << 6;
        } else if (index == 3) {
            register [2] |= (value);
            return true;
        }
        return false;
    }

    public static String decode(String input) {
        byte register[] = new byte[3];

        byte[] result;
        int equalsSign = input.indexOf('=');
        if (equalsSign == -1) {
            result = new byte[input.length() * 3 / 4];
        } else {
            result = new byte[equalsSign * 3 / 4];
        }
        int result_index = 0;

        byte b = 0;
        int i = 0;
        while (i < input.length()) {
            b = (byte) input.charAt(i);
            if (b == '=') {
                break;
            }
            // insert into register
            if (insert64(register, i, (byte) lexicon.indexOf(b))) {
                result[result_index++] = register[0];
                result[result_index++] = register[1];
                result[result_index++] = register[2];
            }
            i++;
        }

        // If there is a remainder, extract the fractional parts from the register.
        if (b == '=') {
            if ((input.length() - i) == 2) {
                result[result_index++] = register[0];
            } else if ((input.length() - i) == 1) {
                result[result_index++] = register[0];
                result[result_index++] = register[1];
            }
        }

        return new String(result);
    }

    public static void main(String[] args) throws Exception {

        String authorisation = "Zm9vOmZvbw==";
        System.out.printf("authorisation %s%n", decode(authorisation));

        String input = "ManM";

        BufferedReader in = new BufferedReader(new FileReader("/tmp/x"));
        //BufferedReader in = new BufferedReader(new FileReader("/Users/glennscott/Development/OpenCeleste/trunk/build.xml"));
        long startTime = System.currentTimeMillis();
        long lineCount = 0;
        while (true) {
            String line = in.readLine();
            if (line == null)
                break;
            lineCount++;

            if (line.compareTo(decode(encode(line))) != 0) {
              System.out.printf("line:    '%s'%n", line);
              System.out.printf("encoded: %s%n", encode(line));
//              System.out.printf("encoded: %s%n", Codecs.base64Encode(line));
              System.out.printf("decoded: '%s'%n", decode(encode(line)));
            }
        }
        System.out.printf("%d %d%n", lineCount, (System.currentTimeMillis() - startTime));

        System.out.printf("%d%n", input.compareTo(decode(encode(input))));


        String output = encode(input);

        System.out.printf("%s -> %s%n", input, output);
//        System.out.printf("%s -> %s%n", input, Codecs.base64Encode(input));

        System.out.printf("decode '%s'%n", decode(output));
        input = "Man is distinguished, not only by his reason, but by this singular passion from other animals, which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge, exceeds the short vehemence of any carnal pleasure.";

        System.out.printf("%s -> %s%n", input, encode(input));
//        System.out.printf("%s -> %s%n", input, Codecs.base64Encode(input));

        System.out.printf("%d%n", input.compareTo(decode(encode(input))));
    }

}
