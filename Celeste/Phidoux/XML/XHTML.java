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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Construct valid XHTML documents.
 * 
 * <p>
 * Programmatically compose XHTML documents conforming to specification of XHTML 1.0 Strict.
 * The intent is to make it difficult to compose invalid XHTML document content.
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories - Sun Microsystems, Inc.
 */
public class XHTML extends XML.Node {
	private final static long serialVersionUID = 1L;

    public enum TextAlign { LEFT, CENTER, RIGHT, JUSTIFY };

    public enum Shape { DEFAULT, RECT, CIRCLE, POLY };

    public interface Attributes<T> extends CoreAttributes<T>, InternationalizationAttributes<T>, EventsAttributes<T> {
        /**/
    }

    public interface CoreAttributes<T> {
        public T setStyle(Object styleSheet);
        public T setTitle(Object text);
        public T setId(Object id);
        public T setClass(Object cdata);
        public T addClass(Object cdata);
    }    

    protected static <X extends XHTML> X setAttribute(X element, Object name, Object value) {
        element.addAttribute(new XML.Attr(name.toString(), value.toString()));
        return element;
    }
    
    /**
     * <p>
     * Add a class name to a CSS class attribute.
     * </p>
     * <p>
     * The given CSS class name is appended to the list of names that specify the CSS class attribute.
     * </p>
     * @param className
     */
    protected static <X extends XHTML> X addClass(X element, Object className) {
        XML.Attribute currentClass = element.getAttribute("class");
        String name = CSS.safeClass(className);
        if (currentClass != null) {
            className = currentClass.getValue() + " " + name;
        }
        return XHTML.setAttribute(element, "class", className);
    }

    protected static <X extends XHTML> X setClass(X element, Object className) {
        return XHTML.setAttribute(element, "class", className);
    }

    protected static <X extends XHTML> X setStyle(X element, Object styleSheet) {
        return XHTML.setAttribute(element, "style", styleSheet);
    }

    protected static <X extends XHTML> X setId(X element, Object styleSheet) {
        return XHTML.setAttribute(element, "id", styleSheet);
    }

    protected static <X extends XHTML> X setTitle(X element, Object text) {
        return XHTML.setAttribute(element, "title", text);
    }

    public interface InternationalizationAttributes<T> {
        public T setLang(Object languageCode);
        /** A language code, as directed by RFC3066 */
        public T setXMLLang(Object languageCode);
        public T setDirection(boolean leftToRight);
    }

    protected static <X extends XHTML> X setLang(X element, Object langSpec) {
        return XHTML.setAttribute(element, "lang", langSpec);
    }

    protected static <X extends XHTML> X setXMLLang(X element, Object langSpec) {
        return XHTML.setAttribute(element, "xml:lang", langSpec);
    }

    protected static <X extends XHTML> X setDirection(X element, boolean ltr) {
        return XHTML.setAttribute(element, "dir", ltr ? "ltr" : "rtl");
    }

    public interface EventsAttributes<T> {
        public T setOnClick(Object script);
        public T setOnDblClick(Object script);
        public T setOnMouseDown(Object script);
        public T setOnMouseUp(Object script);
        public T setOnMouseOver(Object script);
        public T setOnMouseMove(Object script);
        public T setOnMouseOut(Object script);
        public T setOnKeyPress(Object script);
        public T setOnKeyDown(Object script);
        public T setOnKeyUp(Object script);
    }

    protected static <X extends XHTML> X setOnClick(X element, Object script) {
        return XHTML.setAttribute(element, "onclick", script);
    }

    protected static <X extends XHTML> X setOnDblClick(X element, Object script) {
        return XHTML.setAttribute(element, "ondblclick", script);
    }

    protected static <X extends XHTML> X setOnMouseDown(X element, Object script) {
        return XHTML.setAttribute(element, "onmousedown", script);
    }

    protected static <X extends XHTML> X setOnMouseUp(X element, Object script) {
        return XHTML.setAttribute(element, "onmouseup", script);
    }

    protected static <X extends XHTML> X setOnMouseOver(X element, Object script) {
        return XHTML.setAttribute(element, "onmouseover", script);
    }

    protected static <X extends XHTML> X setOnMouseMove(X element, Object script) {
        return XHTML.setAttribute(element, "onmousemove", script);
    }

    protected static <X extends XHTML> X setOnMouseOut(X element, Object script) {
        return XHTML.setAttribute(element, "onmouseout", script);
    }

    protected static <X extends XHTML> X setOnKeyPress(X element, Object script) {
        return XHTML.setAttribute(element, "onkeypress", script);
    }

    protected static <X extends XHTML> X setOnKeyDown(X element, Object script) {
        return XHTML.setAttribute(element, "onkeydown", script);
    }

    protected static <X extends XHTML> X setOnKeyUp(X element, Object script) {
        return XHTML.setAttribute(element, "onkeyup", script);
    }

    public interface Focus<T> {
        public T setAccesskey(String character);
        public T setTabIndex(int number);
        public T setOnfocus(Object script);
        public T setOnblur(Object script);
    }

    protected static <X extends XHTML> X setAccesskey(X element, Object character) {
        return XHTML.setAttribute(element, "accesskey", character);
    }

    protected static <X extends XHTML> X setTabIndex(X element, Object number) {
        return XHTML.setAttribute(element, "tabindex", number);
    }

    protected static <X extends XHTML> X setOnfocus(X element, Object script) {
        return XHTML.setAttribute(element, "onfocus", script);
    }

    protected static <X extends XHTML> X setOnblur(X element, Object script) {
        return XHTML.setAttribute(element, "onblur", script);
    }

    /**
     * Given an array of bytes produce a pretty-printed String representing those bytes.
     *
     * @param bytes the byte array to pretty-print
     *
     * @return  the pretty-printed string
     */    
    public static String prettyPrint(byte[] bytes, int offset, int count) {
        StringBuilder out = new StringBuilder();

        for (int i = offset; i < count; i++) {
            if (bytes[i] == '\n') {
                out.append(bytes[i]);
            } else if (bytes[i] == '&') {
                out.append("&amp;");
            } else if (bytes[i] < ' ' || bytes[i] == '\\') {
                out.append(String.format("\\x%02X", bytes[i]));
            } else {
                out.append(String.format("%c", bytes[i]));
            }
        }
        return out.toString();
    }

    public static String escape(String content) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            switch (c) {
            case '"':  s.append("\\\"");    break;
            case '\'':  s.append("\\\'");   break;
            default: s.append(c);           break;
            }
        }
        return s.toString();
    }

    public static String SafeURL(Object o) {
        String url = String.valueOf(o);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            switch (c) {
            case '&': s.append("&amp;"); break;
            default:
                s.append(c); break;
            }
        }
        return s.toString();
    }

    public static String SafeURL(String format, Object... o) {
        return XHTML.SafeURL(String.format(format, o));        
    }
    
    
    public static class CDATA /*implements Streamable*/ {
        // Note that when the CDATA section includes javascript comments which
        // inhibits the javascript interpreter from parsing the CDATA
        // declaration as javascript producing an error.
        private static final String prefix = "/*<![CDATA[*/\n";
        private static final String suffix = "\n/*]]>*/";
        private String cdata;

        public CDATA(Object cdata) {
            this.cdata = cdata.toString();
        }

        public String toString() {
            StringBuilder s = new StringBuilder().append(prefix).append(cdata).append(suffix);
            return s.toString(); 
        }

        public long streamLength() {
            return prefix.getBytes().length + this.cdata.getBytes().length + suffix.getBytes().length; 
        }

        public long streamTo(DataOutputStream out) throws IOException {
            byte[] p = CDATA.prefix.getBytes();
            byte[] c = this.cdata.getBytes();
            byte[] s = CDATA.suffix.getBytes();
            
            out.write(p);
            out.write(c);
            out.write(s);
            return p.length + c.length + s.length; 
        }
    }

    public static class CharacterEntity {
        /**
         * Given a string containing unescaped character entities,
         * return a new String consisting of the given String with entities
         * &quot; &amp; &gt; &lt; escaped.
         * @param content
         */
        public static String escape(String content) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                switch (c) {
                case '"':  s.append("&quot;");  break;
                case '&':  s.append("&amp;");  break;
                case '<':  s.append("&lt;");   break;
                case '>':  s.append("&gt;");   break;
                default: s.append(c);          break;
                }
            }
            return s.toString();            
        }

        public static final XHTML.PCData nbsp = new XHTML.PCData("&#160;");     /* no-break space = non-breaking space, U+00A0 ISOnum */
        public static final XHTML.PCData iexcl = new XHTML.PCData("&#161;");    /* inverted exclamation mark, U+00A1 ISOnum */
        public static final XHTML.PCData cent = new XHTML.PCData("&#162;");     /* cent sign, U+00A2 ISOnum */
        public static final XHTML.PCData pound = new XHTML.PCData("&#163;");    /* pound sign, U+00A3 ISOnum */
        public static final XHTML.PCData curren = new XHTML.PCData("&#164;");   /* currency sign, U+00A4 ISOnum */
        public static final XHTML.PCData yen = new XHTML.PCData("&#165;");      /* yen sign = yuan sign, U+00A5 ISOnum */
        public static final XHTML.PCData brvbar = new XHTML.PCData("&#166;");   /* broken bar = broken vertical bar, U+00A6 ISOnum */
        public static final XHTML.PCData sect = new XHTML.PCData("&#167;");     /* section sign, U+00A7 ISOnum */
        public static final XHTML.PCData uml = new XHTML.PCData("&#168;");      /* diaeresis = spacing diaeresis, U+00A8 ISOdia */
        public static final XHTML.PCData copy = new XHTML.PCData("&#169;");     /* copyright sign, U+00A9 ISOnum */
        public static final XHTML.PCData ordf = new XHTML.PCData("&#170;");     /* feminine ordinal indicator, U+00AA ISOnum */
        public static final XHTML.PCData laquo = new XHTML.PCData("&#171;");    /* left-pointing double angle quotation mark = left pointing guillemet, U+00AB ISOnum */
        public static final XHTML.PCData not = new XHTML.PCData("&#172;");      /* not sign = angled dash, U+00AC ISOnum */
        public static final XHTML.PCData shy = new XHTML.PCData("&#173;");      /* soft hyphen = discretionary hyphen, U+00AD ISOnum */
        public static final XHTML.PCData reg = new XHTML.PCData("&#174;");      /* registered sign = registered trade mark sign, U+00AE ISOnum */
        public static final XHTML.PCData macr = new XHTML.PCData("&#175;");     /* macron = spacing macron = overline = APL overbar, U+00AF ISOdia */
        public static final XHTML.PCData deg = new XHTML.PCData("&#176;");      /* degree sign, U+00B0 ISOnum */
        public static final XHTML.PCData plusmn = new XHTML.PCData("&#177;");   /* plus-minus sign = plus-or-minus sign, U+00B1 ISOnum */
        public static final XHTML.PCData sup2 = new XHTML.PCData("&#178;");     /* superscript two = superscript digit two = squared, U+00B2 ISOnum */
        public static final XHTML.PCData sup3 = new XHTML.PCData("&#179;");     /* superscript three = superscript digit three = cubed, U+00B3 ISOnum */
        public static final XHTML.PCData acute = new XHTML.PCData("&#180;");    /* acute accent = spacing acute, U+00B4 ISOdia */
        public static final XHTML.PCData micro = new XHTML.PCData("&#181;");    /* micro sign, U+00B5 ISOnum */
        public static final XHTML.PCData para = new XHTML.PCData("&#182;");     /* pilcrow sign = paragraph sign, U+00B6 ISOnum */
        public static final XHTML.PCData middot = new XHTML.PCData("&#183;");   /* middle dot = Georgian comma = Greek middle dot, U+00B7 ISOnum */
        public static final XHTML.PCData cedil = new XHTML.PCData("&#184;");    /* cedilla = spacing cedilla, U+00B8 ISOdia */
        public static final XHTML.PCData sup1 = new XHTML.PCData("&#185;");     /* superscript one = superscript digit one, U+00B9 ISOnum */
        public static final XHTML.PCData ordm = new XHTML.PCData("&#186;");     /* masculine ordinal indicator, U+00BA ISOnum */
        public static final XHTML.PCData raquo = new XHTML.PCData("&#187;");    /* right-pointing double angle quotation mark = right pointing guillemet, U+00BB ISOnum */
        public static final XHTML.PCData frac14 = new XHTML.PCData("&#188;");   /* vulgar fraction one quarter = fraction one quarter, U+00BC ISOnum */
        public static final XHTML.PCData frac12 = new XHTML.PCData("&#189;");   /* vulgar fraction one half = fraction one half, U+00BD ISOnum */
        public static final XHTML.PCData frac34 = new XHTML.PCData("&#190;");   /* vulgar fraction three quarters = fraction three quarters, U+00BE ISOnum */
        public static final XHTML.PCData iquest = new XHTML.PCData("&#191;");   /* inverted question mark = turned question mark, U+00BF ISOnum */
        public static final XHTML.PCData Agrave = new XHTML.PCData("&#192;");   /* latin capital letter A with grave = latin capital letter A grave, U+00C0 ISOlat1 */
        public static final XHTML.PCData Aacute = new XHTML.PCData("&#193;");   /* latin capital letter A with acute, U+00C1 ISOlat1 */
        public static final XHTML.PCData Acirc = new XHTML.PCData("&#194;");    /* latin capital letter A with circumflex, U+00C2 ISOlat1 */
        public static final XHTML.PCData Atilde = new XHTML.PCData("&#195;");   /* latin capital letter A with tilde, U+00C3 ISOlat1 */
        public static final XHTML.PCData Auml = new XHTML.PCData("&#196;");     /* latin capital letter A with diaeresis, U+00C4 ISOlat1 */
        public static final XHTML.PCData Aring = new XHTML.PCData("&#197;");    /* latin capital letter A with ring above = latin capital letter A ring, U+00C5 ISOlat1 */
        public static final XHTML.PCData AElig = new XHTML.PCData("&#198;");    /* latin capital letter AE = latin capital ligature AE, U+00C6 ISOlat1 */
        public static final XHTML.PCData Ccedil = new XHTML.PCData("&#199;");   /* latin capital letter C with cedilla, U+00C7 ISOlat1 */
        public static final XHTML.PCData Egrave = new XHTML.PCData("&#200;");   /* latin capital letter E with grave, U+00C8 ISOlat1 */
        public static final XHTML.PCData Eacute = new XHTML.PCData("&#201;");   /* latin capital letter E with acute, U+00C9 ISOlat1 */
        public static final XHTML.PCData Ecirc = new XHTML.PCData("&#202;");    /* latin capital letter E with circumflex, U+00CA ISOlat1 */
        public static final XHTML.PCData Euml = new XHTML.PCData("&#203;");     /* latin capital letter E with diaeresis, U+00CB ISOlat1 */
        public static final XHTML.PCData Igrave = new XHTML.PCData("&#204;");   /* latin capital letter I with grave, U+00CC ISOlat1 */
        public static final XHTML.PCData Iacute = new XHTML.PCData("&#205;");   /* latin capital letter I with acute, U+00CD ISOlat1 */
        public static final XHTML.PCData Icirc = new XHTML.PCData("&#206;");    /* latin capital letter I with circumflex, U+00CE ISOlat1 */
        public static final XHTML.PCData Iuml = new XHTML.PCData("&#207;");     /* latin capital letter I with diaeresis, U+00CF ISOlat1 */
        public static final XHTML.PCData ETH = new XHTML.PCData("&#208;");      /* latin capital letter ETH, U+00D0 ISOlat1 */
        public static final XHTML.PCData Ntilde = new XHTML.PCData("&#209;");   /* latin capital letter N with tilde, U+00D1 ISOlat1 */
        public static final XHTML.PCData Ograve = new XHTML.PCData("&#210;");   /* latin capital letter O with grave, U+00D2 ISOlat1 */
        public static final XHTML.PCData Oacute = new XHTML.PCData("&#211;");   /* latin capital letter O with acute, U+00D3 ISOlat1 */
        public static final XHTML.PCData Ocirc = new XHTML.PCData("&#212;");    /* latin capital letter O with circumflex, U+00D4 ISOlat1 */
        public static final XHTML.PCData Otilde = new XHTML.PCData("&#213;");   /* latin capital letter O with tilde, U+00D5 ISOlat1 */
        public static final XHTML.PCData Ouml = new XHTML.PCData("&#214;");     /* latin capital letter O with diaeresis, U+00D6 ISOlat1 */
        public static final XHTML.PCData times = new XHTML.PCData("&#215;");    /* multiplication sign, U+00D7 ISOnum */
        public static final XHTML.PCData Oslash = new XHTML.PCData("&#216;");   /* latin capital letter O with stroke = latin capital letter O slash, U+00D8 ISOlat1 */
        public static final XHTML.PCData Ugrave = new XHTML.PCData("&#217;");   /* latin capital letter U with grave, U+00D9 ISOlat1 */
        public static final XHTML.PCData Uacute = new XHTML.PCData("&#218;");   /* latin capital letter U with acute, U+00DA ISOlat1 */
        public static final XHTML.PCData Ucirc = new XHTML.PCData("&#219;");    /* latin capital letter U with circumflex, U+00DB ISOlat1 */
        public static final XHTML.PCData Uuml = new XHTML.PCData("&#220;");     /* latin capital letter U with diaeresis, U+00DC ISOlat1 */
        public static final XHTML.PCData Yacute = new XHTML.PCData("&#221;");   /* latin capital letter Y with acute, U+00DD ISOlat1 */
        public static final XHTML.PCData THORN = new XHTML.PCData("&#222;");    /* latin capital letter THORN, U+00DE ISOlat1 */
        public static final XHTML.PCData szlig = new XHTML.PCData("&#223;");    /* latin small letter sharp s = ess-zed, U+00DF ISOlat1 */
        public static final XHTML.PCData agrave = new XHTML.PCData("&#224;");   /* latin small letter a with grave = latin small letter a grave, U+00E0 ISOlat1 */
        public static final XHTML.PCData aacute = new XHTML.PCData("&#225;");   /* latin small letter a with acute, U+00E1 ISOlat1 */
        public static final XHTML.PCData acirc = new XHTML.PCData("&#226;");    /* latin small letter a with circumflex, U+00E2 ISOlat1 */
        public static final XHTML.PCData atilde = new XHTML.PCData("&#227;");   /* latin small letter a with tilde, U+00E3 ISOlat1 */
        public static final XHTML.PCData auml = new XHTML.PCData("&#228;");     /* latin small letter a with diaeresis, U+00E4 ISOlat1 */
        public static final XHTML.PCData aring = new XHTML.PCData("&#229;");    /* latin small letter a with ring above = latin small letter a ring, U+00E5 ISOlat1 */
        public static final XHTML.PCData aelig = new XHTML.PCData("&#230;");    /* latin small letter ae = latin small ligature ae, U+00E6 ISOlat1 */
        public static final XHTML.PCData ccedil = new XHTML.PCData("&#231;");   /* latin small letter c with cedilla, U+00E7 ISOlat1 */
        public static final XHTML.PCData egrave = new XHTML.PCData("&#232;");   /* latin small letter e with grave, U+00E8 ISOlat1 */
        public static final XHTML.PCData eacute = new XHTML.PCData("&#233;");   /* latin small letter e with acute, U+00E9 ISOlat1 */
        public static final XHTML.PCData ecirc = new XHTML.PCData("&#234;");    /* latin small letter e with circumflex, U+00EA ISOlat1 */
        public static final XHTML.PCData euml = new XHTML.PCData("&#235;");     /* latin small letter e with diaeresis, U+00EB ISOlat1 */
        public static final XHTML.PCData igrave = new XHTML.PCData("&#236;");   /* latin small letter i with grave, U+00EC ISOlat1 */
        public static final XHTML.PCData iacute = new XHTML.PCData("&#237;");   /* latin small letter i with acute, U+00ED ISOlat1 */
        public static final XHTML.PCData icirc = new XHTML.PCData("&#238;");    /* latin small letter i with circumflex, U+00EE ISOlat1 */
        public static final XHTML.PCData iuml = new XHTML.PCData("&#239;");     /* latin small letter i with diaeresis, U+00EF ISOlat1 */
        public static final XHTML.PCData eth = new XHTML.PCData("&#240;");      /* latin small letter eth, U+00F0 ISOlat1 */
        public static final XHTML.PCData ntilde = new XHTML.PCData("&#241;");   /* latin small letter n with tilde, U+00F1 ISOlat1 */
        public static final XHTML.PCData ograve = new XHTML.PCData("&#242;");   /* latin small letter o with grave, U+00F2 ISOlat1 */
        public static final XHTML.PCData oacute = new XHTML.PCData("&#243;");   /* latin small letter o with acute, U+00F3 ISOlat1 */
        public static final XHTML.PCData ocirc = new XHTML.PCData("&#244;");    /* latin small letter o with circumflex, U+00F4 ISOlat1 */
        public static final XHTML.PCData otilde = new XHTML.PCData("&#245;");   /* latin small letter o with tilde, U+00F5 ISOlat1 */
        public static final XHTML.PCData ouml = new XHTML.PCData("&#246;");     /* latin small letter o with diaeresis, U+00F6 ISOlat1 */
        public static final XHTML.PCData divide = new XHTML.PCData("&#247;");   /* division sign, U+00F7 ISOnum */
        public static final XHTML.PCData oslash = new XHTML.PCData("&#248;");   /* latin small letter o with stroke, = latin small letter o slash, U+00F8 ISOlat1 */
        public static final XHTML.PCData ugrave = new XHTML.PCData("&#249;");   /* latin small letter u with grave, U+00F9 ISOlat1 */
        public static final XHTML.PCData uacute = new XHTML.PCData("&#250;");   /* latin small letter u with acute, U+00FA ISOlat1 */
        public static final XHTML.PCData ucirc = new XHTML.PCData("&#251;");    /* latin small letter u with circumflex, U+00FB ISOlat1 */
        public static final XHTML.PCData uuml = new XHTML.PCData("&#252;");     /* latin small letter u with diaeresis, U+00FC ISOlat1 */
        public static final XHTML.PCData yacute = new XHTML.PCData("&#253;");   /* latin small letter y with acute, U+00FD ISOlat1 */
        public static final XHTML.PCData thorn = new XHTML.PCData("&#254;");    /* latin small letter thorn, U+00FE ISOlat1 */
        public static final XHTML.PCData yuml = new XHTML.PCData("&#255;");     /* latin small letter y with diaeresis, U+00FF ISOlat1 */
        
        public static final XHTML.PCData crarr = new XHTML.PCData("&#8629");
    }

    public XHTML(String tag, XML.Node.EndTagDisposition disposition) {
        super(tag, disposition);
    }
    
    public <C> C setClass(Class<? extends C> type, String className) {
        XHTML.setAttribute(this, "class", className);
        return type.cast(this);
    }

    public static class Document {	
        private final static byte[] DocumentType = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n".getBytes();

        private XHTML.Html html;

        private Document() {
            /**/
        }

        public Document(XHTML.Html content) {
            this();
            this.html = content;
        }

        public String toString() {
            try {
                return this.toString(new StringBuilder()).toString();
            } catch (IOException cantHappen) {
                cantHappen.printStackTrace();
                return cantHappen.getLocalizedMessage();
            }
        }

        public Appendable toString(Appendable appendTo) throws IOException {
            if (this.html != null) {
                appendTo.append(new String(XML.Prolog));
                appendTo.append(new String(XHTML.Document.DocumentType));
                this.html.toString(appendTo);
            }
            return appendTo;
        }

        public long streamTo(OutputStream out) throws IOException {
            long count = 0;
            if (this.html != null) {
                count += XML.Prolog.length + XHTML.Document.DocumentType.length;
                out.write(XML.Prolog);
                out.write(XHTML.Document.DocumentType);
                count += this.html.streamTo(out);
            }
            return count;
        }
        
        public long streamLength() {
            long count = 0;
            if (this.html != null) {
                count += XML.Prolog.length + XHTML.Document.DocumentType.length;
                count += this.html.streamLength();
            }
            return count;
        }

        public Document add(XHTML.Html... c) {
            this.html.append(c);
            return this;
        }
    }

    public interface Empty extends XML.Content {}

    /*
     * <!ENTITY % fontstyle
     *     "TT | I | B | BIG | SMALL">
     *
     * <!ENTITY % phrase "EM | STRONG | DFN | CODE |
     *                      SAMP | KBD | VAR | CITE | ABBR | ACRONYM" >
     *
     * <!ENTITY % special
     *   "A | IMG | OBJECT | BR | SCRIPT | MAP | Q | SUB | SUP | SPAN | BDO">
     *
     * <!ENTITY % formctrl "INPUT | SELECT | TEXTAREA | LABEL | BUTTON">
     *
     * <!-- %inline; covers inline or "text-level" elements -->
     * <!ENTITY % inline "#PCDATA | %fontstyle; | %phrase; | %special; | %formctrl;">
     */

    public interface EPCData extends XML.Content,
    AddressSubElement,
    AnchorContent,
    ButtonSubElement,
    EFlow,
    EInline,
    FieldSetSubElement,
    ObjSubElement,
    OptionSubElement,
    ParaSubElement,
    PreContent,
    ScriptSubElement,
    StyleSubElement,
    TextAreaSubElement,
    TitleSubElement
    {}

    /**
     * The XHTML DTD internal entity EFlow
     */
    public interface EFlow extends XML.Content,
    BlockQuoteSubElement,
    BodySubElement,
    DefinitionListDefinitionSubElement,
    DeleteSubElement,
    DivSubElement,
    IFrameSubElement,
    InsertSubElement,
    ListItemSubElement,
    NoFramesSubElement,
    NoScriptSubElement,
    TableDataSubElement
    {}
    
    public interface EInline extends EFlow,
    AbbrevationSubElement,
    AcronymSubElement,
    BiDirectionalOverrideSubElement,
    BigSubElement,
    BoldSubElement,
    CitationSubElement,
    CodeSubElement,
    DefineSubElement,
    DefinitionListTermSubElement,
    DeleteSubElement,
    EmphasisSubElement,
    HeadingSubElement,
    InsertSubElement,
    ItalicSubElement,
    KeyboardSubElement,
    LabelSubElement,
    ParaSubElement,
    QuotationSubElement,
    SampleSubElement,
    SmallSubElement,
    SpanSubElement,
    StrongSubElement,
    SubscriptSubElement,
    SuperscriptSubElement,
    TableCaptionSubElement,
    VariableSubElement
    {}
    public interface E_inline extends EInline,
    AddressSubElement,
    FieldSetSubElement
    {}
    public interface ESpecial extends E_inline,
    ObjSubElement,
    ButtonSubElement
    {}

    public interface ESpecialPre extends ESpecial,
    AnchorContent
    {}
    public interface EInlineForms extends E_inline,
    AnchorContent,
    PreContent
    {}

    public interface EPhrase extends E_inline,
    AnchorContent,
    ButtonSubElement
    {}

    public interface EFontStyle extends E_inline,
    AnchorContent,
    ButtonSubElement
    {}

    public interface EMiscInline extends EMisc, EInline,
    AnchorContent,
    AddressSubElement,
    PreContent
    {}


    /**
     * The XHTML Specification for
     * <!ENTITY % Block "(%block; | form | %misc;)*">
     * 
     * @author Glenn Scott - Sun Microsystems Laboratories - Sun Microsystems, Inc.
     */
    public interface EBlock extends EFlow
    {}
    public interface EMisc extends EBlock,
    ButtonSubElement,
    FormContent,
    FieldSetSubElement
    {}
    public interface E_block extends EBlock,
    ObjSubElement,
    FormContent,
    FieldSetSubElement
    {}
    public interface EHeading extends E_block,
    ButtonSubElement
    {}
    public interface ELists extends E_block,
    ButtonSubElement
    {}
    public interface EBlockText extends E_block,
    ButtonSubElement
    {}

    public interface EHeadContent extends XML.Content
    {}

    public interface EHeadMisc extends XML.Content,
    HeadSubElement
    {}



    public static class PCData extends XML.Node implements EPCData {
    	private final static long serialVersionUID = 1L;
    	
        public PCData(String charSequence) {
            super(charSequence);			
        }
    }

    public interface AbbrevationSubElement extends XML.Content {}
    public static class Abbreviation extends XHTML implements Attributes<Abbreviation>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Abbreviation() {
            super("abbr", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Abbreviation(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Abbreviation(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public Abbreviation(EInline... content) {
            this();
            this.add(content);
        }

        public Abbreviation add(EInline... content) {
            super.append(content);
            return this;
        }

        public Abbreviation setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Abbreviation setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Abbreviation setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Abbreviation setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Abbreviation addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Abbreviation setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Abbreviation setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Abbreviation setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Abbreviation setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Abbreviation setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Abbreviation setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Abbreviation setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Abbreviation setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Abbreviation setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Abbreviation setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Abbreviation setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Abbreviation setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Abbreviation setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface AcronymSubElement extends XML.Content {}
    public static class Acronym extends XHTML implements Attributes<Acronym>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Acronym() {
            super("acronym", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Acronym(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Acronym(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public Acronym(EInline... content) {
            this();
            this.add(content);
        }

        public Acronym add(EInline... content) {
            super.append(content);
            return this;
        }

        public Acronym setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Acronym setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Acronym setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Acronym setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Acronym addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Acronym setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Acronym setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Acronym setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Acronym setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Acronym setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Acronym setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Acronym setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Acronym setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Acronym setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Acronym setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Acronym setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Acronym setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Acronym setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface AddressSubElement extends XML.Content {}
    public static class Address extends XHTML/*<AddressSubElement>*/ implements EBlockText {
    	private final static long serialVersionUID = 1L;
    	
        public Address() {
            super("address", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Address(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Address(AddressSubElement... inline) {
            this();
            this.add(inline);
        }

        public Address add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Address add(AddressSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface AnchorContent extends XML.Content {}
    public static class Anchor extends XHTML implements E_inline, PreContent, Attributes<Anchor>, Focus<Anchor> {
    	private final static long serialVersionUID = 1L;
    	
        public Anchor() {
            super("a", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Anchor(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Anchor(Object... cdata) {
            this();
            this.add(cdata);
        }
        
        public Anchor(String format, Object... cdata) {
            this();
            this.appendCDATA(format, cdata);
        }

        public Anchor(E_inline... content) {
            this();
            this.append(content);
        }

        public Anchor add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Anchor add(AnchorContent... content) {
            super.append(content);
            return this;
        }

        /*
         * charset     %Charset;      #IMPLIED
         * type        %ContentType;  #IMPLIED
         * name        NMTOKEN        #IMPLIED
         * href        %URI;          #IMPLIED
         * hreflang    %LanguageCode; #IMPLIED
         * rel         %LinkTypes;    #IMPLIED
         * rev         %LinkTypes;    #IMPLIED
         * shape       %Shape;        "rect"
         * coords      %Coords;       #IMPLIED
         * target      %FrameTarget;  #IMPLIED
         */
        public Anchor setType(Object contentType) {
            return XHTML.setAttribute(this, "type", contentType);
        }

        public Anchor setName(Object nmtoken) {
            return XHTML.setAttribute(this, "name", nmtoken);
        }

        public Anchor setHref(Object uri) {
            return XHTML.setAttribute(this, "href", uri);
        }
        
        public Anchor setHref(String format, Object...args) {
            return XHTML.setAttribute(this, "href", String.format(format, args));
        }

        public Anchor setHrefLang(Object langSpec) {
            return XHTML.setAttribute(this, "hreflang", langSpec);
        }

        public Anchor setRel(Object linkType) {
            return XHTML.setAttribute(this, "rel", linkType);
        }

        public Anchor setRev(Object linkType) {
            return XHTML.setAttribute(this, "rev", linkType);
        }

        public Anchor setShape(Shape shape) {
            return XHTML.setAttribute(this, "shape", shape.toString().toLowerCase());
        }

        public Anchor setCoords(Object cdata) {
            return XHTML.setAttribute(this, "coords", cdata);
        }

        public Anchor setTarget(Object frameTarget) {
            return XHTML.setAttribute(this, "target", frameTarget);
        }

        public Anchor setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Anchor setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Anchor setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Anchor setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Anchor addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Anchor setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Anchor setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Anchor setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Anchor setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Anchor setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Anchor setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Anchor setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Anchor setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Anchor setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Anchor setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Anchor setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Anchor setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Anchor setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public Anchor setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public Anchor setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public Anchor setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public Anchor setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }
    }


    public static class Area extends XHTML/*<Empty>*/ implements MapSubElement, Attributes<Area>, Focus<Area> {
    	private final static long serialVersionUID = 1L;
    	
        public Area(String alt) {
            super("area", XML.Node.EndTagDisposition.FORBIDDEN);
            XHTML.setAttribute(this, "alt", alt);
        }

        public Area(String alt, XML.Attribute... attributes) {
            this(alt);
            this.addAttribute(attributes);
        }

        public Area setShape(Shape shape) {
            return XHTML.setAttribute(this, "shape", shape.toString().toLowerCase());
        }

        public Area setCoords(Object cdata) {
            return XHTML.setAttribute(this, "coords", cdata);
        }

        public Area setHref(Object href) {
            this.removeAttribute("nohref");
            return XHTML.setAttribute(this, "href", href);
        }

        public Area setNoHref() {
            this.removeAttribute("href");
            return XHTML.setAttribute(this, "nohref", "nohref");
        }

        public Area setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Area setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Area setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Area setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Area addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Area setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Area setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Area setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Area setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Area setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Area setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Area setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Area setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Area setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Area setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Area setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Area setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Area setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public Area setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public Area setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public Area setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public Area setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }
    }

    public static class Base extends XHTML/*<Empty>*/ implements HeadSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Base(String uri) {
            super("base", XML.Node.EndTagDisposition.FORBIDDEN);
            this.addAttribute(new XML.Attr("href", uri));
        }

        public Base(String uri, XML.Attribute... attributes) {
            this(uri);
            this.addAttribute(attributes);
        }
    }

    public interface BiDirectionalOverrideSubElement extends XML.Content {}
    public static class BiDirectionalOverride extends XHTML/*<BiDirectionalOverrideSubElement>*/ implements ESpecialPre, CoreAttributes<BiDirectionalOverride> {
    	private final static long serialVersionUID = 1L;
    	
        public BiDirectionalOverride(boolean rtl) {
            super("bdo", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("dir", rtl ? "rtl" : "ltr"));
        }

        public BiDirectionalOverride(boolean rtl, XML.Attribute... attributes) {
            this(rtl);
            this.addAttribute(attributes);
        }

        public BiDirectionalOverride(boolean rtl, Object... cdata) {
            this(rtl);
            this.add(cdata);
        }

        public BiDirectionalOverride(boolean rtl, EInline... content) {
            this(rtl);
            this.add(content);
        }

        public BiDirectionalOverride add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public BiDirectionalOverride add(EInline... content) {
            super.append(content);
            return this;
        }

        public BiDirectionalOverride setTitle(Object cdata) {
            this.addAttribute(new XML.Attr("title", cdata));
            return this;
        }

        public BiDirectionalOverride setId(Object text) {
            this.addAttribute(new XML.Attr("id", text));
            return this;
        }

        public BiDirectionalOverride setStyle(Object styleSheet) {
            this.addAttribute(new XML.Attr("style", styleSheet));
            return this;
        }

        public BiDirectionalOverride setClass(Object cdata) {
            return XHTML.setClass(this, cdata);
        }

        public BiDirectionalOverride addClass(Object cdata) {
            return XHTML.addClass(this, cdata);
        }

        public BiDirectionalOverride setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public BiDirectionalOverride setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }
    }

    public interface BigSubElement extends XML.Content {}
    public static class Big extends XHTML/*<BigSubElement>*/ implements EFontStyle, Attributes<Big> {
    	private final static long serialVersionUID = 1L;
    	
        public Big() {
            super("big", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Big(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Big(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Big(BigSubElement... content) {
            this();
            this.add(content);
        }

        public Big add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Big add(BigSubElement... content) {
            super.append(content);
            return this;
        }

        public Big setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Big setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Big setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Big setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Big addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Big setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Big setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Big setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Big setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Big setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Big setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Big setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Big setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Big setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Big setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Big setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Big setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Big setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface BlockQuoteSubElement extends XML.Content {}
    public static class BlockQuote extends XHTML/*<BlockQuoteSubElement>*/ implements EBlockText {
    	private final static long serialVersionUID = 1L;
    	
        public BlockQuote() {
            super("blockquote", XML.Node.EndTagDisposition.REQUIRED);
        }

        public BlockQuote(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public BlockQuote(Object... cdata) {
            this();
            this.add(cdata);
        }

        public BlockQuote(BlockQuoteSubElement... content) {
            this();
            this.add(content);
        }

        public BlockQuote add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public BlockQuote add(BlockQuoteSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface BodySubElement extends XML.Content {}
    public static class Body extends XHTML implements HtmlSubElement, Attributes<Body> {
    	private final static long serialVersionUID = 1L;
    	
        public Body() {
            super("body", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Body(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Body(BodySubElement... content) {
            this();
            this.add(content);
        }

        public Body add(BodySubElement... content) {
            super.append(content);
            return this;
        }

        public Body setOnLoad(Object script) {
            return XHTML.setAttribute(this, "onload", script);
        }

        public Body setOnUnload(Object script) {
            return XHTML.setAttribute(this, "onunload", script);
        }

        public Body setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Body setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Body setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Body setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Body addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Body setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Body setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Body setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Body setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Body setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Body setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Body setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Body setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Body setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Body setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Body setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Body setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Body setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface BoldSubElement extends XML.Content {}
    public static class Bold extends XHTML/*<BoldSubElement>*/ implements EFontStyle {
    	private final static long serialVersionUID = 1L;
    	
        public Bold() {
            super("b", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Bold(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Bold(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Bold(BoldSubElement... content) {
            this();
            this.add(content);
        }

        public Bold add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Bold add(BoldSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public static class Break extends XHTML implements ESpecialPre, CoreAttributes<Break> {
    	private final static long serialVersionUID = 1L;
    	
        private final static String br = "br";
        
        public Break() {
            super(XHTML.Break.br, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Break(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public enum Clear { LEFT, All, RIGHT, NONE };

        public Break setClear(Break.Clear clear) {
            this.addAttribute(new XML.Attr("clear", clear.toString().toLowerCase()));
            return this;
        }

        public Break setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Break setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Break setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Break setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Break addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Break setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Break setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Break setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Break setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Break setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Break setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Break setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Break setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Break setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Break setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Break setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Break setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Break setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    /**
     * <!ENTITY % button.content "(#PCDATA | p | %heading; | div | %lists; | %blocktext; | table | %special; | %fontstyle; | %phrase; | %misc;)*">
     */
    public interface ButtonSubElement extends XML.Content {}
    public static class Button extends XHTML implements EInlineForms, Attributes<Button>, Focus<Button> {
    	private final static long serialVersionUID = 1L;
    	
        public enum Type { BUTTON, SUBMIT, RESET };

        public Button() {
            super("button", XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Button(Button.Type type) {
            this();
            this.setType(type);
        }

        public Button(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Button(ButtonSubElement... content) {
            this();
            this.add(content);
        }

        public Button(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Button add(ButtonSubElement... content) {
            super.append(content);
            return this;
        }

        public Button add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
        /*
         * name        CDATA          #IMPLIED
         * value       CDATA          #IMPLIED
         * type        (button|submit|reset) "submit"
         * disabled    (disabled)     #IMPLIED(non-Javadoc)
         */
        public Button setName(Object cdata) {
            return XHTML.setAttribute(this, "name", cdata);
        }

        public Button setValue(Object cdata) {
            return XHTML.setAttribute(this, "value", cdata);
        }

        public Button setType(Button.Type type) {
            return XHTML.setAttribute(this, "type", type.toString().toLowerCase());
        }

        public Button setDisabled(boolean disabled) {
            if (disabled) {
                return XHTML.setAttribute(this, "disabled", "disabled");
            }
            this.removeAttribute("disabled");
            return this;
        }

        public Button setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Button setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Button setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Button setClass(Object cdata) {
            return XHTML.setClass(this, cdata);
        }

        public Button addClass(Object cdata) {
            return XHTML.addClass(this, cdata);
        }

        public Button setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Button setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Button setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Button setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Button setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Button setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Button setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Button setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Button setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Button setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Button setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Button setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public Button setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public Button setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public Button setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }

        public Button setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Button setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface CitationSubElement extends XML.Content {}
    public static class Citation extends XHTML implements Attributes<Citation>, EFontStyle {
    	private final static long serialVersionUID = 1L;
    	
        public Citation() {
            super("cite", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Citation(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Citation(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Citation(EInline... content) {
            this();
            this.add(content);
        }

        public Citation add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Citation add(EInline... content) {
            super.append(content);
            return this;
        }

        public Citation setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Citation setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Citation setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Citation setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Citation addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Citation setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Citation setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Citation setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Citation setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Citation setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Citation setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Citation setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Citation setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Citation setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Citation setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Citation setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Citation setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Citation setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface CodeSubElement extends XML.Content {}
    public static class Code extends XHTML implements Attributes<Code>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Code() {
            super("code", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Code(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Code(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Code(EInline... content) {
            this();
            this.add(content);
        }

        public Code add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Code add(EInline... content) {
            super.append(content);
            return this;
        }

        public Code setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Code setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Code setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Code setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Code addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Code setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Code setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Code setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Code setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Code setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Code setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Code setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Code setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Code setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Code setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Code setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Code setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Code setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface DefineSubElement extends XML.Content {}
    public static class Define extends XHTML implements Attributes<Define>, EPhrase {
    	private final static long serialVersionUID = 1L;
    
        public Define() {
            super("dfn", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Define(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Define(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public Define(EInline... content) {
            this();
            this.add(content);
        }

        public Define add(EInline... content) {
            super.append(content);
            return this;
        }

        public Define setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Define setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Define setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Define setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Define addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Define setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Define setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Define setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Define setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Define setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Define setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Define setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Define setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Define setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Define setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Define setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Define setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Define setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface DeleteSubElement extends XML.Content {}
    public static class Delete extends XHTML/*<DeleteSubElement>*/ implements EMiscInline {
    	private final static long serialVersionUID = 1L;
    	
        public Delete() {
            super("del", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Delete(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Delete(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Delete(DeleteSubElement... content) {
            this();
            this.add(content);
        }

        public Delete add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Delete add(DeleteSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface DivSubElement extends XML.Content {}
    public static class Div extends XHTML implements E_block, ButtonSubElement, Attributes<Div> {
    	private final static long serialVersionUID = 1L;
    	
        public Div() {
            super("div", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Div(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Div(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Div(EFlow... content) {
            this();
            this.add(content);
        }

        public Div add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Div add(EFlow... content) {
            super.append(content);
            return this;
        }

        public Div setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Div setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Div setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Div setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Div addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Div setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Div setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Div setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Div setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Div setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Div setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Div setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Div setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Div setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Div setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Div setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Div setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Div setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface EmphasisSubElement extends XML.Content {}
    public static class Emphasis extends XHTML/*<EmphasisSubElement>*/ implements EPhrase, Attributes<Emphasis> {
    	private final static long serialVersionUID = 1L;
    	
        public Emphasis() {
            super("em", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Emphasis(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Emphasis(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Emphasis(EInline... content) {
            this();
            this.add(content);
        }

        public Emphasis add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Emphasis add(EInline... content) {
            super.append(content);
            return this;
        }

        public Emphasis setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Emphasis setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Emphasis setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Emphasis setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Emphasis addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Emphasis setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Emphasis setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Emphasis setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Emphasis setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Emphasis setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Emphasis setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Emphasis setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Emphasis setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Emphasis setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Emphasis setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Emphasis setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Emphasis setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Emphasis setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface FormContent extends XML.Content {}
    public static class Form extends XHTML implements EBlock, EFlow, FieldSetSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Form(String action) {
            super("form", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("action", action));
        }

        public Form(String action, XML.Attribute...attributes) {
            this(action);
            this.addAttribute(attributes);
        }

        public Form add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Form add(FormContent... content) {
            super.append(content);
            return this;
        }

        public Form setMethod(String method) {
            this.addAttribute(new XML.Attr("method", method));
            return this;
        }

        public Form setEncodingType(String contentType) {
            this.addAttribute(new XML.Attr("enctype", contentType));
            return this;
        }

        public Form setOnSubmit(String script) {
            this.addAttribute(new XML.Attr("onsubmit", script));
            return this;
        }

        public Form setOnReset(String script) {
            // %Script;       #IMPLIED
            this.addAttribute(new XML.Attr("onreset", script));
            return this;
        }

        public Form setAccept(String contentType) {
            // %ContentTypes; #IMPLIED
            this.addAttribute(new XML.Attr("accept", contentType));
            return this;
        }

        public Form setAcceptCharSet(String charsets) {
            // %Charsets;  #IMPLIED
            this.addAttribute(new XML.Attr("accept-charset", charsets));
            return this;
        }
    }

    public interface FieldSetSubElement extends XML.Content {}
    public static class FieldSet extends XHTML/*<FieldSetSubElement>*/ implements E_block {
    	private final static long serialVersionUID = 1L;
    	
        public FieldSet() {
            super("fieldset", XML.Node.EndTagDisposition.REQUIRED);
        }

        public FieldSet(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public FieldSet(FieldSetSubElement... content) {
            this();
            this.append(content);
        }
    }

    public interface HeadSubElement extends XML.Content {}
    public static class Head extends XHTML implements HtmlSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Head() {
            super("head", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Head(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Head(HeadSubElement... content) {
            this();
            this.add(content);
        }

        public Head add(HeadSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface HeadingSubElement extends XML.Content {}
    public static class Heading extends XHTML/*<HeadingSubElement>*/ implements EHeading {
    	private final static long serialVersionUID = 1L;
    	
        public Heading(String tag) {
            super(tag, XML.Node.EndTagDisposition.REQUIRED);
        }

        public Heading(String tag, XML.Attribute... attributes) {
            this(tag);
            this.addAttribute(attributes);
        }

        public static class H1 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H1() {
                super("h1");
            }

            public H1(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H1(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H1(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public H1(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H1 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }
            
            public H1 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H1 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }
            
            public H1 setTitle(Object cdata) {
                return XHTML.setTitle(this, cdata);
            }

            public H1 setId(Object text) {
                return XHTML.setId(this, text);
            }

            public H1 setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public H1 setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public H1 addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public H1 setLang(Object langSpec) {
                return XHTML.setLang(this, langSpec);
            }

            public H1 setXMLLang(Object langSpec) {
                return XHTML.setXMLLang(this, langSpec);
            }

            public H1 setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public H1 setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public H1 setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public H1 setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public H1 setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public H1 setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public H1 setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public H1 setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public H1 setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public H1 setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public H1 setOnKeyUp(Object script)  {
                return XHTML.setOnKeyUp(this, script);
            }
        }

        public static class H2 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H2() {
                super("h2");
            }

            public H2(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H2(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H2(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public H2(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H2 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }

            public H2 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H2 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }

            public H2 setTitle(Object cdata) {
                return XHTML.setTitle(this, cdata);
            }

            public H2 setId(Object text) {
                return XHTML.setId(this, text);
            }

            public H2 setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public H2 setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public H2 addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public H2 setLang(Object langSpec) {
                return XHTML.setLang(this, langSpec);
            }

            public H2 setXMLLang(Object langSpec) {
                return XHTML.setXMLLang(this, langSpec);
            }

            public H2 setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public H2 setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public H2 setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public H2 setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public H2 setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public H2 setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public H2 setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public H2 setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public H2 setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public H2 setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public H2 setOnKeyUp(Object script)  {
                return XHTML.setOnKeyUp(this, script);
            }
        }

        public static class H3 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H3() {
                super("h3");
            }

            public H3(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H3(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H3(Object... cdata) {
                this();
                this.add(cdata);
            }

            public H3(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H3 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }
            
            public H3 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H3 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }
            
            public H3 setTitle(Object cdata) {
                return XHTML.setTitle(this, cdata);
            }

            public H3 setId(Object text) {
                return XHTML.setId(this, text);
            }

            public H3 setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public H3 setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public H3 addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public H3 setLang(Object langSpec) {
                return XHTML.setLang(this, langSpec);
            }

            public H3 setXMLLang(Object langSpec) {
                return XHTML.setXMLLang(this, langSpec);
            }

            public H3 setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public H3 setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public H3 setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public H3 setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public H3 setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public H3 setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public H3 setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public H3 setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public H3 setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public H3 setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public H3 setOnKeyUp(Object script)  {
                return XHTML.setOnKeyUp(this, script);
            }
        }

        public static class H4 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H4() {
                super("h4");
            }

            public H4(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H4(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H4(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public H4(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H4 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }

            public H4 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H4 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }
            
            public H4 setTitle(Object cdata) {
                return XHTML.setTitle(this, cdata);
            }

            public H4 setId(Object text) {
                return XHTML.setId(this, text);
            }

            public H4 setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public H4 setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public H4 addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public H4 setLang(Object langSpec) {
                return XHTML.setLang(this, langSpec);
            }

            public H4 setXMLLang(Object langSpec) {
                return XHTML.setXMLLang(this, langSpec);
            }

            public H4 setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public H4 setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public H4 setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public H4 setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public H4 setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public H4 setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public H4 setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public H4 setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public H4 setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public H4 setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public H4 setOnKeyUp(Object script)  {
                return XHTML.setOnKeyUp(this, script);
            }
        }

        public static class H5 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H5() {
                super("h5");
            }

            public H5(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H5(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H5(Object... cdata) {
                this();
                this.add(cdata);
            }            
            
            public H5(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H5 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }

            public H5 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H5 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }
        }

        public static class H6 extends Heading {
        	private final static long serialVersionUID = 1L;
        	
            public H6() {
                super("h6");
            }

            public H6(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public H6(HeadingSubElement... content) {
                this();
                this.add(content);
            }

            public H6(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public H6(String format, Object... cdata) {
                this();
                super.appendCDATA(format, cdata);
            }
            
            public H6 appendCDATA(String format, Object... cdata) {
                super.appendCDATA(format, cdata);
                return this;
            }

            public H6 add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public H6 add(HeadingSubElement... content) {
                super.append(content);
                return this;
            }
        }
    }

    public static class HorizontalRule extends XHTML/*<Empty>*/ implements EBlockText {
    	private final static long serialVersionUID = 1L;
    	
        public HorizontalRule() {
            super("hr", XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public HorizontalRule(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }

    public interface HtmlSubElement extends XML.Content {}
    public static class Html extends XHTML {
    	private final static long serialVersionUID = 1L;
    	
        public Html() {
            super("html", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("lang", "en"));
            this.addAttribute(new XML.Attr("xml:lang", "en"));
            this.addAttribute(new XML.Attr("xmlns", "http://www.w3.org/1999/xhtml"));
        }

        public Html(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Html(HtmlSubElement... content) {
            this();
            this.add(content);
        }

        public Html add(HtmlSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public static class Input extends XHTML implements EInlineForms, Attributes<Input>, Focus<Input> {
    	private final static long serialVersionUID = 1L;
    	
        public enum Type { TEXT, PASSWORD, CHECKBOX, RADIO, SUBMIT, RESET, FILE, HIDDEN, IMAGE, BUTTON };
        public enum Align { TOP, MIDDLE, BOTTOM, LEFT, RIGHT }

        public Input() {
            super("input", XML.Node.EndTagDisposition.FORBIDDEN);
        }
        
        public Input(Input.Type type, XML.Attribute...attributes) {
            this();
            this.setType(type);
            this.addAttribute(attributes);
        }

        /*
         * type        %InputType;    "text"
         * name        CDATA          #IMPLIED
         * value       CDATA          #IMPLIED
         * checked     (checked)      #IMPLIED
         * disabled    (disabled)     #IMPLIED
         * readonly    (readonly)     #IMPLIED
         * size        CDATA          #IMPLIED
         * maxlength   %Number;       #IMPLIED
         * src         %URI;          #IMPLIED
         * alt         CDATA          #IMPLIED
         * usemap      %URI;          #IMPLIED
         * onselect    %Script;       #IMPLIED
         * onchange    %Script;       #IMPLIED
         * accept      %ContentTypes; #IMPLIED
         */
        public Input setType(Input.Type type) {
            return XHTML.setAttribute(this, "type", type.toString().toLowerCase());
        }

        public Input setName(Object cdata) {
            return XHTML.setAttribute(this, "name", cdata);
        }

        public Input setValue(Object cdata) {
            return XHTML.setAttribute(this, "value", cdata);
        }

        public Input setChecked(boolean checked) {
            if (checked) {
                return XHTML.setAttribute(this, "checked", "checked");
            }
            this.removeAttribute("checked");
            return this;
        }

        public Input setDisabled(boolean disabled) {
            if (disabled) {
                return XHTML.setAttribute(this, "disabled", "disabled");
            }
            this.removeAttribute("disabled");
            return this;
        }

        public Input setReadOnly(boolean readOnly) {
            if (readOnly) {
                return XHTML.setAttribute(this, "readonly", "readonly");
            }
            this.removeAttribute("readonly");
            return this;
        }

        public Input setSize(Object cdata) {
            return XHTML.setAttribute(this, "size", cdata);
        }

        public Input setMaxLength(int number) {
            return XHTML.setAttribute(this, "maxlength", number);
        }

        public Input setSrc(Object uri) {
            return XHTML.setAttribute(this, "src", uri);
        }

        public Input setAlt(Object cdata) {
            return XHTML.setAttribute(this, "alt", cdata);
        }

        public Input setUseMap(Object uri) {
            return XHTML.setAttribute(this, "usemap", uri);
        }

        public Input setOnSelect(Object script) {
            return XHTML.setAttribute(this, "onselect", script);
        }

        public Input setOnChange(Object script) {
            return XHTML.setAttribute(this, "onchange", script);
        }

        public Input setAccept(Object contentType) {
            return XHTML.setAttribute(this, "accept", contentType);
        }

        public Input setAlign(Input.Align align) {
            return XHTML.setAttribute(this, "align", align.toString().toLowerCase());
        }

        public Input setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Input setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Input setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Input setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Input addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Input setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Input setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Input setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Input setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Input setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Input setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Input setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Input setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Input setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Input setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Input setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Input setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Input setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public Input setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public Input setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public Input setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public Input setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }
    }

    public interface IFrameSubElement extends XML.Content {}
    public static class IFrame extends XHTML/*<IFrameSubElement>*/ implements EBlock {
    	private final static long serialVersionUID = 1L;
    	
        public IFrame() {
            super("iframe", XML.Node.EndTagDisposition.REQUIRED);
        }

        public IFrame(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public IFrame(Object... cdata) {
            this();
            this.add(cdata);
        }

        public IFrame(IFrameSubElement... content) {
            this();
            this.add(content);
        }

        public IFrame add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public IFrame add(IFrameSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface InsertSubElement extends XML.Content {}
    public static class Insert extends XHTML/*<InsertSubElement>*/ implements EMiscInline {
    	private final static long serialVersionUID = 1L;
    	
        public Insert() {
            super("ins", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Insert(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Insert(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Insert(InsertSubElement... content) {
            this();
            this.add(content);
        }

        public Insert add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Insert add(InsertSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface KeyboardSubElement extends XML.Content {}
    public static class Keyboard extends XHTML/*<KeyboardSubElement>*/ implements Attributes<Keyboard>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Keyboard() {
            super("kbd", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Keyboard(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Keyboard(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Keyboard(EInline... content) {
            this();
            this.add(content);
        }

        public Keyboard add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Keyboard add(EInline... content) {
            super.append(content);
            return this;
        }

        public Keyboard setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Keyboard setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Keyboard setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Keyboard setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Keyboard addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Keyboard setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Keyboard setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Keyboard setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Keyboard setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Keyboard setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Keyboard setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Keyboard setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Keyboard setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Keyboard setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Keyboard setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Keyboard setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Keyboard setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Keyboard setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface LabelSubElement extends XML.Content {}
    public static class Label extends XHTML/*<LabelSubElement>*/ implements EInlineForms {
    	private final static long serialVersionUID = 1L;
    	
        public Label() {
            super("label", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Label(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Label(LabelSubElement... content) {
            this();
            this.add(content);
        }

        public Label(Object... cdata) {
            this();
            this.add(cdata);	
        }

        public Label add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Label add(LabelSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface LegendSubElement extends XML.Content {}
    public static class Legend extends XHTML/*<LegendSubElement>*/ implements FieldSetSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Legend() {
            super("legend", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Legend(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Legend add(LegendSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface DefinitionListTermSubElement extends XML.Content {}
    public interface DefinitionListDefinitionSubElement extends XML.Content {}
    public interface DefinitionListSubElement extends XML.Content {}

    public static class Definition extends XHTML/*<DefinitionListSubElement>*/ {
    	private final static long serialVersionUID = 1L;
    	
        public Definition() {
            super("dl", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Definition(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Definition add(XHTML.List.Definition.Description content) {
            super.append(content);
            return this;
        }

        public Definition add(XHTML.List.Definition.Term content) {
            super.append(content);
            return this;
        }

        public static class Term extends XHTML/*<DefinitionListTermSubElement>*/ implements DefinitionListSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Term() {
                super("dt", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Term(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Term add(DefinitionListTermSubElement... content) {
                super.append(content);
                return this;
            }

            public Term add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }
        }

        public static class Description extends XHTML implements DefinitionListSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Description() {
                super("dd", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Description(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Description add(DefinitionListDefinitionSubElement... content) {
                super.append(content);
                return this;
            }

            public Description add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }
        }
    }

    public interface ListItemSubElement extends XML.Content {}
    public static class List extends XHTML/*<Empty>*/ implements ELists {
    	private final static long serialVersionUID = 1L;

        public List(String tag) {
            super(tag, XML.Node.EndTagDisposition.REQUIRED);
        }

        public static class Unordered extends List {
        	private final static long serialVersionUID = 1L;
        	
            public Unordered() {
                super("ul");
            }

            public Unordered(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Unordered add(List.Item... item) {
                super.append(item);
                return this;
            }
        }

        public static class Ordered extends List {
        	private final static long serialVersionUID = 1L;
        	
            public Ordered() {
                super("ol");
            }

            public Ordered(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Ordered add(List.Item... item) {
                super.append(item);
                return this;
            }
        }

        public static class Item extends XHTML/*<ListItemSubElement>*/ {
        	private final static long serialVersionUID = 1L;
        	
            public Item() {
                super("li", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Item(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Item add(EFlow... item) {
                super.append(item);
                return this;
            }

            public Item add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }
        }
    }

    public interface NoFramesSubElement extends XML.Content {}
    public static class NoFrames extends XHTML/*<NoFramesSubElement>*/ implements EBlock {
    	private final static long serialVersionUID = 1L;
    	
        public NoFrames() {
            super("noframes", XML.Node.EndTagDisposition.REQUIRED);
        }

        public NoFrames(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public NoFrames(Object... cdata) {
            this();
            this.add(cdata);
        }

        public NoFrames(NoFramesSubElement... content) {
            this();
            this.add(content);
        }

        public NoFrames add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public NoFrames add(NoFramesSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface ObjSubElement extends XML.Content {}
    public static class Obj extends XHTML/*<ObjSubElement>*/ implements EHeadMisc, ESpecial {
    	private final static long serialVersionUID = 1L;
    	
        public Obj() {
            super("object", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Obj(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);			
        }

        public Obj(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Obj(ObjSubElement... content) {
            this();
            this.append(content);
        }

        public Obj add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Obj add(ObjSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface OptionSubElement extends XML.Content {}
    public static class Option extends XHTML implements OptionGroupSubElement, SelectSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Option() {
            super("option", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Option(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Option(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Option(OptionSubElement... content) {
            this();
            this.add(content);
        }

        public Option add(OptionSubElement... content) {
            super.append(content);
            return this;
        }

        public Option add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }

    public interface OptionGroupSubElement extends XML.Content {}
    public static class OptionGroup extends XHTML implements SelectSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public OptionGroup(String label) {
            super("optgroup", XML.Node.EndTagDisposition.REQUIRED);
            XHTML.setAttribute(this, "label", label);
        }

        public OptionGroup(String label, XML.Attribute... attributes) {
            this(label);
            this.addAttribute(attributes);
        }

        public OptionGroup(String label, Object... cdata) {
            this(label);
            this.add(cdata);
        }

        public OptionGroup(String label, OptionGroupSubElement... content) {
            this(label);
            this.add(content);
        }

        public OptionGroup add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public OptionGroup add(OptionGroupSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface ParamInterface extends XML.Content, ObjSubElement {}
    public static class Param extends XHTML implements ParamInterface {
    	private final static long serialVersionUID = 1L;
    	
        public Param() {
            super("param", XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Param(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }

    /**
     * <!ENTITY % pre.content "(#PCDATA | a | %fontstyle; | %phrase; | %special.pre; | %misc.inline; | %inline.forms;)*">
     */
    public interface PreContent extends XML.Content {}
    public static class Preformatted extends XHTML implements EBlockText {
    	private final static long serialVersionUID = 1L;
    	
        public Preformatted() {
            super("pre", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Preformatted(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Preformatted(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Preformatted add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Preformatted add(PreContent... content) {
            super.append(content);
            return this;
        }

        public Preformatted setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Preformatted setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Preformatted setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Preformatted setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Preformatted addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Preformatted setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Preformatted setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Preformatted setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Preformatted setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Preformatted setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Preformatted setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Preformatted setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Preformatted setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Preformatted setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Preformatted setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Preformatted setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Preformatted setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Preformatted setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }        
    }

    public interface QuotationSubElement extends XML.Content {}
    public static class Quotation extends XHTML implements Attributes<Quotation>, ESpecial {
    	private final static long serialVersionUID = 1L;
    	
        public Quotation() {
            super("q", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Quotation(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Quotation(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Quotation(EInline... content) {
            this();
            this.add(content);
        }

        public Quotation add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Quotation add(EInline... content) {
            super.append(content);
            return this;
        }

        public Quotation setCite(Object cdata) {
            return XHTML.setAttribute(this, "cite", cdata);
        }

        public Quotation setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Quotation setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Quotation setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Quotation setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Quotation addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Quotation setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Quotation setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Quotation setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Quotation setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Quotation setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Quotation setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Quotation setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Quotation setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Quotation setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Quotation setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Quotation setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Quotation setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Quotation setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface TableSubElement extends XML.Content {}
    public interface TableCaptionSubElement extends XML.Content {}
    public interface TableRowSubElement extends XML.Content {}
    public interface TableDataSubElement extends XML.Content {}
    public interface TableSubSectionElement extends XML.Content {}

    public static class Table extends XHTML/*<TableSubElement>*/ implements E_block, ButtonSubElement, Attributes<Table> {
    	private final static long serialVersionUID = 1L;
    	
        public enum Scope { ROW, COL, ROWGROUP, COLGROUP };
        public enum CellHAlign { LEFT, CENTER, RIGHT, JUSTIFY, CHAR };
        public enum CellVAlign { TOP, MIDDLE, BOTTOM, BASELINE };

        public Table() {
            super("table", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Table(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Table(TableSubElement... content) {
            this();
            this.add(content);
        }

        public Table add(TableSubElement... content) {
            super.append(content);
            return this;
        }        

        public Table setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Table setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Table setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Table setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Table addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Table setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Table setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Table setDirection(boolean leftToRight) {
            return XHTML.setDirection(this, leftToRight);
        }

        public Table setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Table setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Table setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Table setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Table setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Table setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Table setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Table setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Table setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Table setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public static class Caption extends XHTML/*<XHTML.TableCaptionSubElement>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Caption() {
                super("caption", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Caption(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Caption(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public Caption(String format, Object...cdata) {
                this();
                this.appendCDATA(format, cdata);
            }

            public Caption add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            public Caption add(TableCaptionSubElement... content) {
                super.append(content);
                return this;
            }
        }

        public static class ColumnGroup extends XHTML/*<XHTML.Table.Column>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public ColumnGroup() {
                super("colgroup", XML.Node.EndTagDisposition.REQUIRED);
            }

            public ColumnGroup(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public ColumnGroup add(XHTML.Table.Column... content) {
                super.append(content);
                return this;
            }
        }

        public static class Column extends XHTML/*<Empty>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Column() {
                super("column", XML.Node.EndTagDisposition.FORBIDDEN);
            }

            public Column(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }
        }

        public static class Body extends XHTML/*<XHTML.Table.Row>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Body() {
                super("tbody", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Body(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Body(XHTML.Table.Row... content) {
                this();
                this.add(content);
            }

            public XHTML.Table.Body add(XHTML.Table.Row... content) {
                super.append(content);
                return this;
            }
        }

        public static class Head extends XHTML/*<XHTML.Table.Row>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Head() {
                super("thead", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Head(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Head(XHTML.Table.Row... content) {
                this();
                this.add(content);
            }

            public XHTML.Table.Head add(XHTML.Table.Row... content) {
                super.append(content);
                return this;
            }
        }

        public static class Foot extends XHTML/*<XHTML.Table.Row>*/ implements TableSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Foot() {
                super("tfoot", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Foot(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public XHTML.Table.Foot add(XHTML.Table.Row... content) {
                super.append(content);
                return this;
            }
        }


        public static class Row extends XHTML/*<XHTML.TableRowSubElement>*/ implements TableSubSectionElement, CoreAttributes<Row> {
        	private final static long serialVersionUID = 1L;
        	
            public Row() {
                super("tr", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Row(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Row(XHTML.TableRowSubElement... content) {
                this();
                this.add(content);
            }

            public Row add(XHTML.TableRowSubElement... content) {
                super.append(content);
                return this;
            }

            public Row setTitle(Object cdata) {
                this.addAttribute(new XML.Attr("title", cdata));
                return this;
            }

            public Row setId(Object text) {
                this.addAttribute(new XML.Attr("id", text));
                return this;
            }

            public Row setStyle(Object styleSheet) {
                this.addAttribute(new XML.Attr("style", styleSheet));
                return this;
            }

            public Row setClass(Object cdata) {
                return XHTML.setClass(this, cdata);
            }

            public Row addClass(Object cdata) {
                return XHTML.addClass(this, cdata);
            }
        }

        public static class Data extends XHTML implements XHTML.TableRowSubElement, Attributes<Data> {		
        	private final static long serialVersionUID = 1L;
        		
            public Data() {
                super("td", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Data(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Data(XHTML.TableDataSubElement... flow) {
                this();
                this.add(flow);
            }

            public Data(Object... cdata) {
                this();
                this.add(cdata);
            }
            
            public Data(String format, Object... cdata) {
                this();
                this.appendCDATA(format, cdata);
            }

            public Data add(XHTML.TableDataSubElement... flow) {
                super.append(flow);
                return this;
            }

            public Data add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }

            /*
             * abbr        %Text;         #IMPLIED
             * axis        CDATA          #IMPLIED
             * headers     IDREFS         #IMPLIED
             * scope       %Scope;        #IMPLIED
             * rowspan     %Number;       "1"
             * colspan     %Number;       "1"
             * align      (left|center|right|justify|char) #IMPLIED
             * char       %Character;    #IMPLIED
             * charoff    %Length;       #IMPLIED"
             * "valign     (top|middle|bottom|baseline) #IMPLIED"
             */

            public Data setAbbr(Object text) {
                return XHTML.setAttribute(this, "abbr", text);
            }

            public Data setAxis(Object cdata) {
                return XHTML.setAttribute(this, "axis", cdata);
            }

            public Data setHeaders(Object idrefs) {
                return XHTML.setAttribute(this, "headers", idrefs);
            }

            public Data setScope(XHTML.Table.Scope scope) {
                return XHTML.setAttribute(this, "scope", scope.toString().toLowerCase());
            }

            public Data setRowSpan(int number) {
                return XHTML.setAttribute(this, "rowspan", number);
            }

            public Data setColumnSpan(int number) {
                return XHTML.setAttribute(this, "colspan", number);
            }

            public Data setAlign(Table.CellHAlign align) {
                return XHTML.setAttribute(this, "align", align.toString().toLowerCase());
            }

            public Data setChar(Object character) {
                return XHTML.setAttribute(this, "char", character);
            }

            public Data setCharOff(Object length) {
                return XHTML.setAttribute(this, "charof", length);
            }

            public Data setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public Data setTitle(Object title) {
                return XHTML.setTitle(this, title);
            }

            public Data setId(Object id) {
                return XHTML.setId(this, id);
            }

            public Data setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public Data addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public Data setLang(Object languageCode) {
                return XHTML.setLang(this, languageCode);
            }

            public Data setXMLLang(Object languageCode) {
                return XHTML.setXMLLang(this, languageCode);
            }

            public Data setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public Data setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public Data setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public Data setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public Data setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public Data setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public Data setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public Data setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public Data setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public Data setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public Data setOnKeyUp(Object script) {
                return XHTML.setOnKeyUp(this, script);
            }
        }

        public static class Heading extends XHTML/*<XHTML.TableDataSubElement>*/ implements Attributes<Heading>, XHTML.TableRowSubElement {
        	private final static long serialVersionUID = 1L;
        	
            public Heading() {
                super("th", XML.Node.EndTagDisposition.REQUIRED);
            }

            public Heading(XML.Attribute... attributes) {
                this();
                this.addAttribute(attributes);
            }

            public Heading(Object... cdata) {
                this();
                this.add(cdata);
            }

            public Heading(EFlow... content) {
                this();
                this.append(content);
            }

            public Table.Heading add(Object... cdata) {
                super.addCDATA(cdata);
                return this;
            }
            public Table.Heading setAbbr(Object text) {
                return XHTML.setAttribute(this, "abbr", text);
            }

            public Table.Heading setAxis(Object cdata) {
                return XHTML.setAttribute(this, "axis", cdata);
            }

            public Table.Heading setHeaders(Object idrefs) {
                return XHTML.setAttribute(this, "headers", idrefs);
            }

            public Table.Heading setScope(XHTML.Table.Scope scope) {
                return XHTML.setAttribute(this, "scope", scope.toString().toLowerCase());
            }

            public Table.Heading setRowSpan(int number) {
                return XHTML.setAttribute(this, "rowspan", number);
            }

            public Table.Heading setColumnSpan(int number) {
                return XHTML.setAttribute(this, "colspan", number);
            }

            public Table.Heading setAlign(Table.CellHAlign align) {
                return XHTML.setAttribute(this, "align", align.toString().toLowerCase());
            }

            public Table.Heading setChar(Object character) {
                return XHTML.setAttribute(this, "char", character);
            }

            public Table.Heading setCharOff(Object length) {
                return XHTML.setAttribute(this, "charof", length);
            }

            public Table.Heading setStyle(Object styleSheet) {
                return XHTML.setStyle(this, styleSheet);
            }

            public Table.Heading setTitle(Object title) {
                return XHTML.setTitle(this, title);
            }

            public Table.Heading setId(Object id) {
                return XHTML.setId(this, id);
            }

            public Table.Heading setClass(Object className) {
                return XHTML.setClass(this, className);
            }

            public Table.Heading addClass(Object className) {
                return XHTML.addClass(this, className);
            }

            public Table.Heading setLang(Object languageCode) {
                return XHTML.setLang(this, languageCode);
            }

            public Table.Heading setXMLLang(Object languageCode) {
                return XHTML.setXMLLang(this, languageCode);
            }

            public Table.Heading setDirection(boolean ltr) {
                return XHTML.setDirection(this, ltr);
            }

            public Table.Heading setOnClick(Object script) {
                return XHTML.setOnClick(this, script);
            }

            public Table.Heading setOnDblClick(Object script) {
                return XHTML.setOnDblClick(this, script);
            }

            public Table.Heading setOnMouseDown(Object script) {
                return XHTML.setOnMouseDown(this, script);
            }

            public Table.Heading setOnMouseUp(Object script) {
                return XHTML.setOnMouseUp(this, script);
            }

            public Table.Heading setOnMouseOver(Object script) {
                return XHTML.setOnMouseOver(this, script);
            }

            public Table.Heading setOnMouseMove(Object script) {
                return XHTML.setOnMouseMove(this, script);
            }

            public Table.Heading setOnMouseOut(Object script) {
                return XHTML.setOnMouseOut(this, script);
            }

            public Table.Heading setOnKeyPress(Object script) {
                return XHTML.setOnKeyPress(this, script);
            }

            public Table.Heading setOnKeyDown(Object script) {
                return XHTML.setOnKeyDown(this, script);
            }

            public Table.Heading setOnKeyUp(Object script) {
                return XHTML.setOnKeyUp(this, script);
            }
        }
    }

    public interface TextAreaSubElement extends XML.Content {}
    public static class TextArea extends XHTML/*<TextAreaSubElement>*/ implements EInlineForms, Attributes<TextArea>, Focus<TextArea> {
    	private final static long serialVersionUID = 1L;
    	
        public TextArea(int rows, int cols) {
            super("textarea", XML.Node.EndTagDisposition.REQUIRED);
            XHTML.setAttribute(this, "rows", rows);
            XHTML.setAttribute(this, "cols", cols);
        }

        public TextArea(int rows, int cols, XML.Attribute... attributes) {
            this(rows, cols);
            this.addAttribute(attributes);
        }

        public TextArea(int rows, int cols, Object... cdata) {
            this(rows, cols);
            this.add(cdata);
        }

        public TextArea add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        /*
         * name        CDATA          #IMPLIED
         * rows        %Number;       #REQUIRED
         * cols        %Number;       #REQUIRED
         * disabled    (disabled)     #IMPLIED
         * readonly    (readonly)     #IMPLIED
         * onselect    %Script;       #IMPLIED
         * onchange    %Script;       #IMPLIED
         */
        public TextArea setName(Object cdata) {
            return XHTML.setAttribute(this, "name", cdata);
        }

        public TextArea setDisabled(Object cdata) {
            return XHTML.setAttribute(this, "disabled", cdata);
        }

        public TextArea seReadOnly(Object cdata) {
            return XHTML.setAttribute(this, "readonly", cdata);
        }

        public TextArea setOnSelect(Object cdata) {
            return XHTML.setAttribute(this, "onselect", cdata);
        }

        public TextArea setOnChange(Object cdata) {
            return XHTML.setAttribute(this, "onchange", cdata);
        }

        public TextArea setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public TextArea setId(Object text) {
            return XHTML.setId(this, text);
        }

        public TextArea setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public TextArea setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public TextArea addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public TextArea setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public TextArea setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public TextArea setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public TextArea setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public TextArea setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public TextArea setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public TextArea setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public TextArea setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public TextArea setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public TextArea setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public TextArea setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public TextArea setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public TextArea setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public TextArea setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public TextArea setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public TextArea setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public TextArea setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }
    }

    public interface TitleSubElement extends XML.Content {}
    public static class Title extends XHTML implements HeadSubElement, InternationalizationAttributes<Title> {
    	private final static long serialVersionUID = 1L;
    	
        public Title() {
            super("title",  XML.Node.EndTagDisposition.REQUIRED);
        }

        public Title(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Title(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public Title add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Title setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Title setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Title setDirection(boolean leftToRight) {
            return XHTML.setDirection(this, leftToRight);
        }

    }

    public interface MapSubElement extends XML.Content {}
    public static class Map extends XHTML/*<MapSubElement>*/ implements ESpecialPre {
    	private final static long serialVersionUID = 1L;
    	
        /*
         *   id          ID             #REQUIRED
  class       CDATA          #IMPLIED
  style       %StyleSheet;   #IMPLIED
  title       %Text;         #IMPLIED
  name        NMTOKEN        #IMPLIED
         */
        public Map(String id) {
            super("map",  XML.Node.EndTagDisposition.REQUIRED);
            XHTML.setAttribute(this, "id", id);
        }

        public Map(String id, XML.Attribute... attributes) {
            this(id);
            this.addAttribute(attributes);
        }

        public Map(String id, /*Block | Form | %misc*/ MapSubElement... content) {
            this(id);
            this.add(content);
        }

        public Map add(MapSubElement... content) {
            super.append(content);
            return this;
        }

        public Map setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Map setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Map setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Map addClass(Object cdata) {
            return XHTML.addClass(this, cdata);
        }
    }

    public static class Meta extends XHTML implements EHeadMisc, InternationalizationAttributes<Meta> {
    	private final static long serialVersionUID = 1L;
    	
        public Meta(String content) {
            super("meta", XML.Node.EndTagDisposition.FORBIDDEN);
            this.addAttribute(new XML.Attr("content", content));
        }

        public Meta(String content, XML.Attribute... attributes) {
            this(content);
            this.addAttribute(attributes);
        }

        public Meta setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Meta setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Meta setDirection(boolean leftToRight) {
            return XHTML.setDirection(this, leftToRight);
        }

        public Meta setHttpEquiv(String cdata) {
            this.addAttribute(new XML.Attr("http-equiv", cdata));
            return this;
        }

        public Meta setName(String cdata) {
            this.addAttribute(new XML.Attr("name", cdata));
            return this;
        }

        public Meta setScheme(String cdata) {
            this.addAttribute(new XML.Attr("scheme", cdata));
            return this;
        }
    }

    public static class Image extends XHTML implements ESpecial, Attributes<Image> {
    	private final static long serialVersionUID = 1L;
    	
        public  Image(String src, String alt) {
            super("img", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("src", src));
            this.addAttribute(new XML.Attr("alt", alt));
        }

        public Image(String src, String alt, XML.Attribute... attributes) {
            this(src, alt);
            this.addAttribute(attributes);
        }

        public Image setLongDesc(String uri) {
            return XHTML.setAttribute(this, "longdesc", uri);
        }

        public Image setHeight(int height) {
            return XHTML.setAttribute(this, "height", height);
        }

        public Image setWidth(int width) {
            return XHTML.setAttribute(this, "width", width);
        }

        public Image setUseMap(String uri) {
            return XHTML.setAttribute(this, "usemap", uri);
        }

        public Image setIsMap(boolean isMap) {
            if (isMap) {
                return XHTML.setAttribute(this, "ismap", "ismap");
            }
            this.removeAttribute("ismap");
            return this;
        }

        public Image setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Image setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Image setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Image setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Image addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Image setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Image setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Image setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Image setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Image setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Image setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Image setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Image setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Image setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Image setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Image setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Image setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Image setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public static class Link extends XHTML implements EHeadMisc, Attributes<Link> {
    	private final static long serialVersionUID = 1L;
    	
        public  Link() {
            super("link", XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Link(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        /*
         * charset     %Charset;      #IMPLIED
         * href        %URI;          #IMPLIED
         * hreflang    %LanguageCode; #IMPLIED
         * type        %ContentType;  #IMPLIED
         * rel         %LinkTypes;    #IMPLIED
         * rev         %LinkTypes;    #IMPLIED
         * media       %MediaDesc;    #IMPLIED
         * target      %FrameTarget;  #IMPLIED
         */

        public Link setCharset(Object charSet) {
            this.addAttribute(new XML.Attr("charset", charSet));
            return this;
        }
        public Link setHref(Object uri) {
            this.addAttribute(new XML.Attr("href", uri));
            return this;
        }
        
        public Link setHref(String format, Object... args) {
            this.addAttribute(new XML.Attr("href", String.format(format, args)));
            return this;
        }

        public Link setHrefLang(Object languageCode) {
            this.addAttribute(new XML.Attr("hreflang", languageCode));
            return this;
        }

        public Link setType(Object contentType) {
            this.addAttribute(new XML.Attr("type", contentType));
            return this;
        }

        public Link setRel(Object cdata) {
            this.addAttribute(new XML.Attr("rel", cdata));
            return this;
        }

        public Link setRev(Object cdata) {
            this.addAttribute(new XML.Attr("rev", cdata));
            return this;
        }

        public Link setMedia(Object cdata) {
            this.addAttribute(new XML.Attr("media", cdata));
            return this;
        }

        public Link setTarget(Object frameTarget) {
            this.addAttribute(new XML.Attr("target", frameTarget));
            return this;
        }

        public Link setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Link setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Link setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Link setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Link addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Link setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Link setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Link setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Link setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Link setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Link setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Link setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Link setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Link setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Link setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Link setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Link setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Link setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }

        public Link setAccesskey(String character) {
            return XHTML.setAccesskey(this, character);
        }

        public Link setTabIndex(int number) {
            return XHTML.setTabIndex(this, number);
        }

        public Link setOnfocus(Object script) {
            return XHTML.setOnfocus(this, script);
        }

        public Link setOnblur(Object script) {
            return XHTML.setOnblur(this, script);
        }
    }

    public interface NoScriptSubElement extends XML.Content {}
    public static class NoScript extends XHTML/*<NoScriptSubElement>*/ implements EHeadMisc, EMisc {
    	private final static long serialVersionUID = 1L;
    	
        public NoScript() {
            super("noscript", XML.Node.EndTagDisposition.REQUIRED);
        }

        public NoScript(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public NoScript(Object... cdata) {
            this();
            this.add(cdata);
        }

        public NoScript(NoScriptSubElement... content) {
            this();
            this.add(content);
        }

        public NoScript add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public NoScript add(NoScriptSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface SampleSubElement extends XML.Content {}
    public static class Sample extends XHTML/*<SampleSubElement>*/ implements Attributes<Sample>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Sample() {
            super("samp", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Sample(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Sample(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Sample(EInline... content) {
            this();
            this.add(content);
        }

        public Sample add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Sample add(EInline... content) {
            super.append(content);
            return this;
        }

        public Sample setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Sample setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Sample setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Sample setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Sample addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Sample setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Sample setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Sample setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Sample setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Sample setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Sample setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Sample setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Sample setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Sample setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Sample setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Sample setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Sample setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Sample setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface ScriptSubElement extends XML.Content {}
    public static class Script extends XHTML implements EHeadMisc, EMiscInline {
    	private final static long serialVersionUID = 1L;
    	
        public Script(String contentType) {
            super("script", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("type", contentType));
//            this.newLine = true;
        }

        public Script(String contentType, XML.Attribute... attributes) {
            this(contentType);
            this.addAttribute(attributes);
        }

        public Script(String contentType, XHTML.CDATA... cdata) {
            this(contentType);
            this.add(cdata);
        }

        public Script add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
        
        public Script add(XHTML.CDATA... cdata) {
            super.addCDATA((Object[]) cdata);
            return this;
        }

        public Script setCharset(String charSet) {
            this.addAttribute(new XML.Attr("charset", String.valueOf(charSet)));
            return this;
        }

        public Script setSource(String uri) {
            this.addAttribute(new XML.Attr("src", String.valueOf(uri)));
            return this;
        }

        public Script setDefer(boolean trueFalse) {
            if (trueFalse) {
                this.addAttribute(new XML.Attr("defer", "defer"));
            } else {
                this.removeAttribute("defer");
            }
            return this;
        }
    }

    public interface SelectSubElement extends XML.Content {}
    public static class Select extends XHTML/*<SelectSubElement>*/ implements EInlineForms {
    	private final static long serialVersionUID = 1L;
    	
        public Select() {
            super("select", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Select(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Select(SelectSubElement... content) {
            this();
            this.add(content);
        }

        public Select add(SelectSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public static interface SpanSubElement extends XML.Content {}
    public static class Span extends XHTML implements ESpecialPre, Attributes<Span> {
    	private final static long serialVersionUID = 1L;
    	
        public Span() {
            super("span", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Span(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Span(Object... content) {
            this();
            this.add(content);
        }
        
        public Span(String format, Object... content) {
            this();
            this.appendCDATA(format, content);
        }

        public Span(EInline... content) {
            this();
            this.add(content);
        }

        public Span add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Span add(EInline... content) {
            super.append(content);
            return this;
        }


        public Span setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Span setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Span setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Span setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Span addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Span setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Span setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Span setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Span setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Span setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Span setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Span setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Span setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Span setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Span setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Span setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Span setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Span setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface StrongSubElement extends XML.Content {}
    public static class Strong extends XHTML/*<StrongSubElement>*/ implements EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Strong() {
            super("strong", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Strong(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Strong(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Strong(EInline... content) {
            this();
            this.add(content);
        }

        public Strong add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Strong add(EInline... content) {
            super.append(content);
            return this;
        }
    }

    public interface SubscriptSubElement extends XML.Content {}
    public static class Subscript extends XHTML/*<SubscriptSubElement>*/ implements Attributes<Subscript>, ESpecial {
    	private final static long serialVersionUID = 1L;
    	
        public Subscript() {
            super("sub", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Subscript(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Subscript(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Subscript(EInline... content) {
            this();
            this.add(content);
        }

        public Subscript add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Subscript add(EInline... content) {
            super.append(content);
            return this;
        }

        public Subscript setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Subscript setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Subscript setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Subscript setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Subscript addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Subscript setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Subscript setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Subscript setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Subscript setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Subscript setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Subscript setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Subscript setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Subscript setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Subscript setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Subscript setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Subscript setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Subscript setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Subscript setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface SuperscriptSubElement extends XML.Content {}
    public static class Superscript extends XHTML/*<SuperscriptSubElement>*/ implements Attributes<Superscript>, ESpecial {
    	private final static long serialVersionUID = 1L;
    	
        public Superscript() {
            super("sup", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Superscript(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Superscript(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Superscript(XHTML.EInline... content) {
            this();
            this.add(content);
        }

        public Superscript add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Superscript add(EInline... content) {
            super.append(content);
            return this;
        }

        public Superscript setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Superscript setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Superscript setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Superscript setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Superscript addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Superscript setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Superscript setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Superscript setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Superscript setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Superscript setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Superscript setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Superscript setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Superscript setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Superscript setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Superscript setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Superscript setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Superscript setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Superscript setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface StyleSubElement extends XML.Content {}
    public static class Style extends XHTML implements HeadSubElement {
    	private final static long serialVersionUID = 1L;
    	
        public Style(String contentType) {
            super("style", XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(new XML.Attr("type", contentType));
        }

        public Style(String contentType, XML.Attribute... attributes) {
            this(contentType);
            this.addAttribute(attributes);
        }

        public Style(String contentType, Object... cdata) {
            this(contentType);
            this.add(cdata);
        }

        public Style add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
        
        public Style add(String format, Object... cdata) {
            super.appendCDATA(format, cdata);
            return this;
        }

        public Style setMedia(Object mediaDesc) {
            this.addAttribute(new XML.Attr("media", mediaDesc));
            return this;
        }

        public Style setTitle(Object text) {
            this.addAttribute(new XML.Attr("title", text));
            return this;
        }
    }

    public interface ItalicSubElement extends XML.Content {}
    public static class Italic extends XHTML/*<ItalicSubElement>*/ implements EFontStyle {
    	private final static long serialVersionUID = 1L;
    	
        public Italic() {
            super("i", XML.Node.EndTagDisposition.REQUIRED);			
        }

        public Italic(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Italic(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Italic(ItalicSubElement... content) {
            this();
            this.add(content);
        }

        public Italic add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Italic add(ItalicSubElement... content) {
            super.append(content);
            return this;
        }
    }

    public interface SmallSubElement extends XML.Content {}
    public static class Small extends XHTML/*<SmallSubElement>*/ implements EFontStyle, Attributes<Small> {
    	private final static long serialVersionUID = 1L;
    	
        public Small() {
            super("small", XML.Node.EndTagDisposition.REQUIRED);			
        }

        public Small(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Small(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Small(SmallSubElement... content) {
            this();
            this.add(content);
        }

        public Small add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Small add(SmallSubElement... content) {
            super.append(content);
            return this;
        }

        public Small setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Small setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Small setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Small setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Small addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Small setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Small setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Small setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Small setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Small setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Small setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Small setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Small setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Small setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Small setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Small setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Small setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Small setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }

    public interface ParaSubElement extends XML.Content {}
    public static class Para extends XHTML implements E_block, ButtonSubElement, Attributes<Para> {
    	private final static long serialVersionUID = 1L;
    	
        public Para() {
            super("p", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Para(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Para(String format, Object... content) {
            this();
            super.appendCDATA(format, content);
        }
        
        public Para(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public Para(ParaSubElement... subElements) {
            this();
            this.add(subElements);
        }

        public Para add(ParaSubElement... subElements) {
            super.append(subElements);
            return this;
        }

        public Para add(EInline... cdata) {
            this.addCDATA((Object[]) cdata);
            return this;
        }

        public Para add(EMiscInline... cdata) {
            this.addCDATA((Object[]) cdata);
            return this;
        }

        public Para add(Object... cdata) {
            this.addCDATA(cdata);
            return this;
        }

        public Para setAlign(TextAlign align){
            this.addAttribute(new XML.Attr("align", align.toString().toLowerCase()));
            return this;
        }

        public Para setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Para setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Para setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Para setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Para addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Para setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Para setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Para setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Para setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Para setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Para setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Para setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Para setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Para setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Para setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Para setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Para setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Para setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }
    
    
    public static class Tty extends XHTML implements EFontStyle, Attributes<Tty> {
    	private final static long serialVersionUID = 1L;
    	
        public Tty() {
            super("tt", XML.Node.EndTagDisposition.REQUIRED);                        
        }

        public Tty(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Tty(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Tty(SmallSubElement... content) {
            this();
            this.add(content);
        }

        public Tty add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Tty add(SmallSubElement... content) {
            super.append(content);
            return this;
        }

        public Tty setTitle(Object cdata) {
            return XHTML.setTitle(this, cdata);
        }

        public Tty setId(Object text) {
            return XHTML.setId(this, text);
        }

        public Tty setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Tty setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Tty addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Tty setLang(Object langSpec) {
            return XHTML.setLang(this, langSpec);
        }

        public Tty setXMLLang(Object langSpec) {
            return XHTML.setXMLLang(this, langSpec);
        }

        public Tty setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Tty setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Tty setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Tty setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Tty setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Tty setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Tty setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Tty setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Tty setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Tty setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Tty setOnKeyUp(Object script)  {
            return XHTML.setOnKeyUp(this, script);
        }
    }
    
    public interface VariableSubElement extends XML.Content {}
    public static class Variable extends XHTML implements Attributes<Variable>, EPhrase {
    	private final static long serialVersionUID = 1L;
    	
        public Variable() {
            super("var", XML.Node.EndTagDisposition.REQUIRED);
        }

        public Variable(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }

        public Variable(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Variable(EInline... content) {
            this();
            this.add(content);
        }

        public Variable add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }

        public Variable add(EInline... content) {
            super.append(content);
            return this;
        }

        public Variable setStyle(Object styleSheet) {
            return XHTML.setStyle(this, styleSheet);
        }

        public Variable setTitle(Object title) {
            return XHTML.setTitle(this, title);
        }

        public Variable setId(Object id) {
            return XHTML.setId(this, id);
        }

        public Variable setClass(Object className) {
            return XHTML.setClass(this, className);
        }

        public Variable addClass(Object className) {
            return XHTML.addClass(this, className);
        }

        public Variable setLang(Object languageCode) {
            return XHTML.setLang(this, languageCode);
        }

        public Variable setXMLLang(Object languageCode) {
            return XHTML.setXMLLang(this, languageCode);
        }

        public Variable setDirection(boolean ltr) {
            return XHTML.setDirection(this, ltr);
        }

        public Variable setOnClick(Object script) {
            return XHTML.setOnClick(this, script);
        }

        public Variable setOnDblClick(Object script) {
            return XHTML.setOnDblClick(this, script);
        }

        public Variable setOnMouseDown(Object script) {
            return XHTML.setOnMouseDown(this, script);
        }

        public Variable setOnMouseUp(Object script) {
            return XHTML.setOnMouseUp(this, script);
        }

        public Variable setOnMouseOver(Object script) {
            return XHTML.setOnMouseOver(this, script);
        }

        public Variable setOnMouseMove(Object script) {
            return XHTML.setOnMouseMove(this, script);
        }

        public Variable setOnMouseOut(Object script) {
            return XHTML.setOnMouseOut(this, script);
        }

        public Variable setOnKeyPress(Object script) {
            return XHTML.setOnKeyPress(this, script);
        }

        public Variable setOnKeyDown(Object script) {
            return XHTML.setOnKeyDown(this, script);
        }

        public Variable setOnKeyUp(Object script) {
            return XHTML.setOnKeyUp(this, script);
        }
    }


    /*
     * function for printing code.
     * 
     */
    public static void coreAttributes(String name) {
        System.out.printf("public %s setStyle(Object styleSheet) {\n", name);
        System.out.println("  return XHTML.setStyle(this, styleSheet);");
        System.out.println("}\n");
        System.out.printf("public %s setTitle(Object title) {\n", name);
        System.out.println("  return XHTML.setTitle(this, title);");
        System.out.println("}\n");
        System.out.printf("public %s setId(Object id) {\n", name);
        System.out.println("  return XHTML.setId(this, id);");
        System.out.println("}\n");
        System.out.printf("public %s setClass(Object className) {\n", name);
        System.out.println("  return XHTML.setClass(this, className);");
        System.out.println("}\n");
        System.out.printf("public %s addClass(Object className) {\n", name);
        System.out.println("  return XHTML.addClass(this, className);");
        System.out.println("}\n");
    }

    public static void i18nAttributes(String name) {
        System.out.printf("public %s setLang(Object languageCode) {\n", name);
        System.out.println("  return XHTML.setLang(this, languageCode);");
        System.out.println("}\n");
        System.out.printf("public %s setXMLLang(Object languageCode) {\n", name);
        System.out.println("  return XHTML.setXMLLang(this, languageCode);");
        System.out.println("}\n");
//      System.out.printf("public %s setId(Object id) {\n", name);
//      System.out.println("  return XHTML.setId(this, id);");
//      System.out.println("}\n");
        System.out.printf("public %s setDirection(boolean ltr) {\n", name);
        System.out.println("  return XHTML.setDirection(this, ltr);");
        System.out.println("}\n");
    }

    public static void eventsAttributes(String name) {

        System.out.printf("public %s setOnClick(Object script) {\n", name);
        System.out.println("  return XHTML.setOnClick(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnDblClick(Object script) {\n", name);
        System.out.println("  return XHTML.setOnDblClick(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnMouseDown(Object script) {\n", name);
        System.out.println("  return XHTML.setOnMouseDown(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnMouseUp(Object script) {\n", name);
        System.out.println("  return XHTML.setOnMouseUp(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnMouseOver(Object script) {\n", name);
        System.out.println("  return XHTML.setOnMouseOver(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnMouseMove(Object script) {\n", name);
        System.out.println("  return XHTML.setOnMouseMove(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnMouseOut(Object script) {\n", name);
        System.out.println("  return XHTML.setOnMouseOut(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnKeyPress(Object script) {\n", name);
        System.out.println("  return XHTML.setOnKeyPress(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnKeyDown(Object script) {\n", name);
        System.out.println("  return XHTML.setOnKeyDown(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnKeyUp(Object script) {\n", name);
        System.out.println("  return XHTML.setOnKeyUp(this, script);");
        System.out.println("}\n");
    }

    public static void focusAttributes(String name) {

        System.out.printf("public %s setAccesskey(Object character) {\n", name);
        System.out.println("  return XHTML.setAccesskey(this, character);");
        System.out.println("}\n");
        System.out.printf("public %s setTabIndex(int number) {\n", name);
        System.out.println("  return XHTML.setTabIndex(this, number);");
        System.out.println("}\n");
        System.out.printf("public %s setOnfocus(Object script) {\n", name);
        System.out.println("  return XHTML.setOnfocus(this, script);");
        System.out.println("}\n");
        System.out.printf("public %s setOnblur(Object script) {\n", name);
        System.out.println("  return XHTML.setOnblur(this, script);");
        System.out.println("}\n");
    }

    public static void attributes(String name) {
        coreAttributes(name);
        i18nAttributes(name);
        eventsAttributes(name);
    }


    
    public static void main(String[] args) throws IOException {
        XHTML.Body body = new XHTML.Body(new XHTML.Div(new XHTML.Para("Hello World")),
                (XHTML.Div) new XHTML.Div(new XHTML.Para("Goodbye World 1")).addAttribute(new XML.Attr("id", "key")),
                (XHTML.Div) new XHTML.Div(new XHTML.Para("Goodbye World 2")).addAttribute(new XML.Attr("id", "key"))
                                         );
        System.out.println(body.toString());

        int count = XML.replaceElement(body, XHTML.Div.class, new XML.Attr("id", "key"), new XHTML.Span("replacement"), System.err, 0);
        System.out.printf("replaced %d%n", count);
        
        System.out.println(body.toString());
    }
}
