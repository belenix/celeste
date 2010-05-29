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
package sunlabs.asdf.web.XML;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;


/**
 * This class is a collection of static helper functions to construct various common XHTML idioms.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public final class Xxhtml {
    /**
     * Create a control which will produce the input of an unbounded integer
     * (within the bounds of the java Integer class).
     * 
     * @param name  The name of the control
     * @param defaultValue The default value of the integer
     */
    public static XHTML.Input inputUnboundedInteger(String name, long defaultValue) {
        XHTML.Input input = new XHTML.Input(XHTML.Input.Type.TEXT,
                new XML.Attr("name", name),
                new XML.Attr("value", Long.toString(defaultValue)),
                new XML.Attr("size", Long.toString(10))
        );
        
        return input;        
    }
    
    /**
     * Produce an {@link XHTML.Select} element containing the XHTML Option elements for the Java logging {@link Level}.
     * 
     * @param name  The name of the Select control
     * @param selected the current logging {@link Level}
     */
    public static XHTML.Select selectJavaUtilLoggingLevel(String name, Level selected) {
        Level[] list = new Level[] { Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL };
    
        XHTML.Select select = new XHTML.Select(new XML.Attr("name", name));
        for (Level level : list) {
            XHTML.Option option = new XHTML.Option(new XML.Attr("label", level), new XML.Attr("value", level));
            option.addCDATA(level);
            if (selected.equals(level))
            	option.addAttribute(new XML.Attr("selected", "selected"));
            select.append(option);
        }
        return select;
    }

    public static String inputStreamToString(InputStream is, long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        int nread;

        if (offset > 0) {
            long byteCount = 0;
            do {
                byteCount += is.skip(offset);
            } while (byteCount < offset);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((nread = is.read(bytes)) > 0) {
            out.write(bytes, 0, nread);
        }
        
        return new String(out.toByteArray());
    }
    
    public static String inputStreamToString(InputStream is) throws IOException {
        byte[] bytes = new byte[1024];
        int nread;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        while ((nread = is.read(bytes)) > 0) {
            out.write(bytes, 0, nread);
        }
        
        return new String(out.toByteArray());
    }
    
    /*
     * -------------------------------------------------------------------------------------
     * | 0x0 0x1 0x2 0x3 0x4 0x5 0x6 0x7 0x8 0x9 0xA 0xB 0xC 0xD 0xE 0xF  0123456789ABCDEF
     * | 0x0 0x1 0x2 0x3 0x4 0x5 0x6 0x7 0x8 0x9 0xA 0xB 0xC 0xD 0xE 0xF  0123456789ABCDEF
     * | 0x0 0x1 0x2 0x3 0x4 0x5 0x6 0x7 0x8 0x9 0xA 0xB 0xC 0xD 0xE 0xF  0123456789ABCDEF
     * -------------------------------------------------------------------------------------
     */
    public static XHTML.Table DataFormat(ByteBuffer buffer) {
        if (buffer == null)
            return null;

        int cells = 32;

        XHTML.Table.Data emptyCell = new XHTML.Table.Data();

        buffer = buffer.duplicate();
        byte[] buf = new byte[cells];
        
        if (buffer.remaining() > 0) {
            XHTML.Table.Body tbody = new XHTML.Table.Body();       
            while (buffer.remaining() > 0) {
                int count = 0;
                while (count < cells && buffer.remaining() > 0) {
                    buf[count++] = buffer.get();
                }
                XHTML.Table.Row row = new XHTML.Table.Row();
                for (int i = 0; i < count; i++) {
                    row.append(new XHTML.Table.Data("%02x", (int) (buf[i] & 0xFF)));
                }
                for (int i = count; i < cells; i++){
                    row.append(emptyCell);
                }
                row.append(new XHTML.Table.Data((Object) XHTML.prettyPrint(buf, 0, count)));
                tbody.append(row);
            }
            XHTML.Table table = new XHTML.Table(tbody).setClass("formattedData");
            return table;
        }
        return null;
    }
    
    public static class Attr {
        public static XML.Attribute HRef(Object url) {
            return new XML.Attr("href", XHTML.SafeURL(url));
        }
        
        public static XML.Attribute HRef(String format, Object...params) {
            return new XML.Attr("href", XHTML.SafeURL(String.format(format, params)));
        }
    }
    
    /**
     * Produce a
     * &lt;meta http-equiv="refresh" content="600;url"&gt;
     * element
     * @param seconds
     * @param url
     */
    public static XHTML.Meta HttpEquivRefresh(int seconds, String url) {
        return new XHTML.Meta(String.format("%d;%s", seconds, url)).setHttpEquiv("refresh");
    }
    
    public static XHTML.Input InputText(XML.Attr... attrs) {
        XHTML.Input input = new XHTML.Input(XHTML.Input.Type.TEXT, attrs);
        return input;
    }
    
    public static XHTML.Input InputSubmit(XML.Attribute... attrs) {
        XHTML.Input input = new XHTML.Input(XHTML.Input.Type.SUBMIT, attrs);
        return input;
    }
    
    public static XHTML.Input InputHidden(XML.Attr... attrs) {
        XHTML.Input input = new XHTML.Input(XHTML.Input.Type.HIDDEN, attrs);
        return input;
    }
    
    /**
     * Produce an XHTML link element for a CSS stylesheet.
     * The arguments to this function produce the {@code href} attribute value.
     * @return An XHTML.Link element.
     */
    public static XHTML.Link Stylesheet(String format, Object... params) {
        XHTML.Link link = new XHTML.Link()
            .setType("text/css")
            .setRel("stylesheet")
            .setMedia("all")
            .setHref(format, params);
        return link;
    }
    
    /**
     * Return an {@link XHTML.EFlow} instance containing the Object
     * {@code o}'s String representation in mono-space font. 
     * 
     * @param o
     */
    public static XHTML.EFlow TextFormat(Object o) {
        String style = "border: 1px solid black; background-color: #8080F0; padding: 1em;";
        String text = String.valueOf(o);

        if (true) {
            XHTML.Div content = new XHTML.Div().setStyle(style);
            String lineStyle = "font-family: monospace";
            for (String line : text.split("\n")) {
                line = line.trim();
                content.add(new XHTML.Span((Object) line).setStyle(lineStyle), XHTML.CharacterEntity.crarr, new XHTML.Break());
            }
            return content;
        } else {
            XHTML.Preformatted result = new XHTML.Preformatted(o).setStyle(style);
            return result;
        }
    }
}
