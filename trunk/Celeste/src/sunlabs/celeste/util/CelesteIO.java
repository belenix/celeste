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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Stack;

public final class CelesteIO {
    private final static byte nl = 10; // US-ASCII newline
    /**
     * 
     * @param b the byte array to pretty print.
     * @return A pretty-printed string of the input byte array.
     */
    public static String printByteArray(byte[] b) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String s;        
        for (int i = 0; i < b.length; i++) {
            s = Byte.toString(b[i]) + " ";
            os.write(s.getBytes());
        }

        return os.toString();
    }
    
    public static byte[] readUntilByte(InputStream is, byte[] terminators)
    throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        byte[] b = new byte[1];
        
        while (is.read(b) == 1) {
            for (int i = 0; i < terminators.length; i++) {
                if (b[0] == terminators[i]) {
                    System.out.printf("'%s' terminated with %d%n", new String(bos.toByteArray()), b[0]);
                    return bos.toByteArray();
                }
            }
            bos.write(b);
        }
        return bos.toByteArray();
    }
    
    /**
     * Read a line's worth of data from an InputStream.
     * Return either the ByteArrayOutputStream containing the line
     * (including the newline character), null if there is no more
     * data, or throw and IOException if something bad has happened.
     * @param is InputStream of data
     * @return A ByteArrayOutputStream containing the single input line.
     * @throws IOException
     */
    public static byte[] readLineAsByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        byte[] b = new byte[1];
        
        int count = 0;
        
        do {
            if (is.read(b) != 1) 
                break;
            line.write(b[0]);
            count++;
        } while (b[0] != CelesteIO.nl); // US-ASCII newline
        
        if (count == 0) {
            return null;
        }

        return line.toByteArray();
    }
    
    /**
     * Write a String to the given OutputStream.
     * A newline character is always appended.
     * @param out
     * @param line
     * @throws IOException
     */
    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write(line.getBytes());
        out.write('\n');
    }
    
    private static byte[] readQuotedString(InputStream in) throws IOException {
        ByteArrayOutputStream token = new ByteArrayOutputStream();
        byte[] b = new byte[1];
        
        while (in.read(b) == 1) {
            if (b[0] == '"') {
                break;
            }
            if (b[0] == '\\') {
                if (in.read(b) != 1) {
                    throw new IOException("Quoted string syntax error");
                }
            }
            token.write(b);            
        }
        
        return token.toByteArray();        
    }
    
    public static Stack<String> parseInputLine(InputStream in) throws IOException {
        System.out.println("parseInputLine");
        ByteArrayOutputStream token = new ByteArrayOutputStream();
        Stack<String> list = new Stack<String>();
        byte[] b = new byte[1];

        while (in.read(b) == 1) {
            System.out.printf("a: %03o%n", b[0]);
            if (b[0] != ' ' && b[0] != '\t' && b[0] != '\n' && b[0] != '\r') { // if not white space
                if (b[0] == '"') {
                    byte[] c = readQuotedString(in);
                    token.write(c);
                    list.insertElementAt(token.toString(), 0);
                    token.reset();
                } else if (b[0] == '\\') {
                    if (in.read(b) != 1) {
                        throw new IOException("Unexpected EOF");
                    }
                    System.out.printf("b: %03o%n", b[0]);
                    token.write(b);
                } else {
                    token.write(b);
                    while (in.read(b) == 1) {
                        if (b[0] != ' ' && b[0] != '\t' && b[0] != '\n' && b[0] != '\r') { // if not white space
                            if (b[0] == '\\') {
                                if (in.read(b) != 1) {
                                    throw new IOException("Unexpected EOF");
                                }
                                System.out.printf("d: %03o%n", b[0]);
                                token.write(b);
                            } else {
                                System.out.printf("e: %03o%n", b[0]);
                                token.write(b);
                            }
                        } else if (b[0] == '\n') {
                            list.insertElementAt(token.toString(), 0);
                            return list;
                        } else {
                            break;
                        }

                    }
                    list.insertElementAt(token.toString(), 0);
                    token.reset();
                }
            } else if (b[0] == '\n') {
                break;
            }
        }
        
        return list;
    }
        
    public static void main(String[] args) throws Exception {
        String s = " hello \"world\" this is a \\ test\r\ncrap";
        System.out.printf("%s", s);
        ByteArrayInputStream bos = new ByteArrayInputStream(s.getBytes());
        
        Stack<String> list = CelesteIO.parseInputLine(bos);
        System.out.println();
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.get(i));
        }
    }
}
