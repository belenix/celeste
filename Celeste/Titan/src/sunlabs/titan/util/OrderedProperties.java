/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan.util;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.XHTMLInspectable;

/**
 * These methods are mostly copied from java.util.Properties.java.  The only
 * purpose of this class is to make sure that {@code store()} always returns
 * exactly the same content, specifically that there is no timestamp and that
 * the order of properties listed is always the same.
 */
public class OrderedProperties extends java.util.Properties implements XHTMLInspectable {
    private static final long serialVersionUID = 1L; 
    // can we use the one of the super-class? we do have the same format, really
    
    public OrderedProperties() {
        super();
    }
    
    public OrderedProperties(String line, char separator) {
        this();

        int p1 = 0;
        char[] chars = line.trim().toCharArray();
        while (p1 < chars.length) {
            while (p1 < chars.length && (chars[p1] == ' ' || chars[p1] == '\t'))
                p1++;
            
            StringBuilder attribute = new StringBuilder();
            while (p1 < chars.length && chars[p1] != '=' && chars[p1] != ' ' && chars[p1] != '\t' && chars[p1] != separator) {
                attribute.append(chars[p1]);
                p1++;
            }

            while (p1 < chars.length && (chars[p1] == ' ' || chars[p1] == '\t'))
                p1++;

            StringBuilder value = new StringBuilder();
            if (p1 < chars.length) {
                if (chars[p1] == '=') {
                    p1++;
                    if (chars[p1] == '"') {
                        p1++;
                        while (p1 < chars.length && chars[p1] != '"') {
                            if (chars[p1] == '\\') {
                                p1++;
                            }
                            value.append(chars[p1]);
                            p1++;
                        }
                    } else {
                        while (p1 < chars.length && chars[p1] != ' ' && chars[p1] != '\t' && chars[p1] != separator) {
                            if (chars[p1] == '\\') {
                                p1++;
                            }
                            value.append(chars[p1]);
                            p1++;
                        }
                    }
                }
            }
            if (attribute.length() > 0) {
                this.setProperty(attribute.toString(), value.toString());
            }
            p1++;
        }
    }
    
    /**
     * Instantiate this {@code OrderedProperites} instance by reading from the given {@link URL}.
     * 
     * @throws IOException
     */
    public OrderedProperties(URL url) throws IOException {
        this((InputStream) url.openConnection().getContent());
    }

    /**
     * Instantiate this {@code OrderedProperites} instance by reading from the given {@link InputStream}.
     * 
     * @throws IOException
     */
    public OrderedProperties(InputStream in) throws IOException {
        super();
        this.load(in);
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            this.store(out, null);
        } catch (IOException io) {
            throw new RuntimeException(io);
        }
            
        return out.toByteArray();
    }
    
    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(this.toByteArray());
    }
    
    public Set<Object> keySet() {
        TreeSet<Object> sortedKeys = new TreeSet<Object>();
        sortedKeys.addAll(super.keySet());
        return sortedKeys;        
    }

    public void load(ByteBuffer buffer) {
        ByteArrayInputStream bin = new ByteArrayInputStream(buffer.array(), buffer.arrayOffset(), buffer.limit());
        try {
            this.load(bin);
        } catch (IOException io) {
            throw new RuntimeException(io);
        }
    }

    public void load(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            this.load(in);
        } finally {
            in.close();
        }
    }
    
    public void load(URL url) throws IOException {
        this.load((InputStream) url.openConnection().getContent());
    }
    
    // This is the biggest departure from java.util.Properties,
    // we must have the properties ordered in order to be able to
    // hash property lists to the same value.
    @Override
    public void store(OutputStream out, String comments) throws IOException {
        BufferedWriter awriter;
        awriter = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        if (comments != null)
            writeln(awriter, "#" + comments);
        writeln(awriter, "#");
        
        for (Object key : this.keySet()) {
            String val = null;
            try {
                val = (String) get(key);
            } catch (ClassCastException e) {
                String msg = String.format("key: %s: %s", key, e.getMessage());
                ClassCastException withKey = new ClassCastException(msg);
                withKey.initCause(e);
                throw withKey;
            }
            key = saveConvert((String) key, true);
            
            // No need to escape embedded and trailing spaces for value, hence
            // pass false to flag.
            val = saveConvert(val, false);
            writeln(awriter, key + "=" + val);
        }
        awriter.flush();
    }
    
    private static void writeln(BufferedWriter bw, String s) throws IOException {
        bw.write(s);
        bw.newLine();
    }
    
    private static char backSlash = '\\';
    
    private String saveConvert(String theString, boolean escapeSpace) {
        int len = theString.length();
        StringBuilder outBuffer = new StringBuilder();
        
        for(int x = 0; x < len; x++) {
            char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append(backSlash);
                    outBuffer.append(backSlash);
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch(aChar) {
            case ' ':
                if (x == 0 || escapeSpace) 
                    outBuffer.append(backSlash);
                outBuffer.append(' ');
                break;
            case '\t':
                outBuffer.append(backSlash).append('t');
                break;
            case '\n':
                outBuffer.append(backSlash).append('n');
                break;
            case '\r':
                outBuffer.append(backSlash).append('r');
                break;
            case '\f':
                outBuffer.append(backSlash).append('f');
                break;
            case '=': // Fall through
            case ':': // Fall through
            case '#': // Fall through
            case '!':
                outBuffer.append(backSlash).append(aChar);
                break;
            default:
                if ((aChar < 0x0020) || (aChar > 0x007e)) {
                    outBuffer.append(backSlash);
                    outBuffer.append('u');
                    outBuffer.append(toHex((aChar >> 12) & 0xF));
                    outBuffer.append(toHex((aChar >>  8) & 0xF));
                    outBuffer.append(toHex((aChar >>  4) & 0xF));
                    outBuffer.append(toHex( aChar        & 0xF));
                } else {
                    outBuffer.append(aChar);
                }
            }
        }
        return outBuffer.toString();
    }
    
    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }
    
    /** A table of hex digits */
    private static final char[] hexDigit = {
        '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
    };
    
    public synchronized OrderedProperties setProperty(String name, Object value) {
        super.setProperty(name, String.valueOf(value));
        return this;
    }

    public int getPropertyAsInt(String name) {
        String v = this.getProperty(name);
        return (v == null) ? null : Integer.parseInt(v);
    }
    
    public int getPropertyAsInt(String name, int defaultValue) {
        String v = this.getProperty(name);
        return (v == null) ? defaultValue : Integer.parseInt(v);
    }
    
    public long getPropertyAsLong(String name, long defaultValue) {
        String v = this.getProperty(name);
        return (v == null) ? defaultValue : Long.parseLong(v);
    }

    public TitanGuid getPropertyAsObjectId(String name, TitanGuid defaultValue) {
        String v = this.getProperty(name);
        return (v == null) ? defaultValue : new TitanGuidImpl(v);
    }
    
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        XHTML.Table.Body tbody = new XHTML.Table.Body();
        TreeMap<Object,String> sortedMap = new TreeMap<Object,String>();

        for (Object key : this.keySet()) {
            sortedMap.put(key, this.getProperty(key.toString()));
        }

        for (Object key : sortedMap.keySet()) {
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(key),
                    new XHTML.Table.Data((Object) this.getProperty(key.toString()))));
        }
        return new XHTML.Table(tbody).setClass("metadata");
    }

    public String toString() {
        return new String(this.toByteArray());
    }
}
