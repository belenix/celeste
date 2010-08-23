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

import java.util.Properties;
import java.util.TreeMap;

public class Attributes extends TreeMap<String,Attributes.Attribute> {
	private static final long serialVersionUID = 1L;
	
	private static char backSlash = '\\';

    private static String saveConvert(String theString, boolean escapeSpace) {
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

    public static class Attribute {
        protected String fullName;
        protected Object value;
        protected String description;

        @Deprecated
        public Attribute(String name, Object value) {
            this(name, value, null);
        }
        
        public Attribute(String name, Object value, String description) {
            this.fullName = name;
            this.value = value;
            this.description = description;
        }

        public Object getValue() {
            return this.value;
        }

        public String[] asStringArray(String split) {
            String s = this.asString();
            return s == null ? null : s.split(split);
        }

        public String asString() {
            return this.value == null ? null : this.value.toString();
        }

        public Integer asInt() {
            return Integer.parseInt(this.asString());
        }

        public Long asLong() {
            return Long.parseLong(this.asString());
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getName() {
            return fullName;
        }

        public String format() {
            StringBuilder result = new StringBuilder();
            if (this.description != null) {
                result.append("#\" ").append(this.description);
            }
            result.append(this.toString());
            return result.toString();
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(this.fullName).append("=").append(this.value);
            return result.toString();
        }
    }

    public Attributes() {
    	super();
    }

    /**
     * Override any {@link Attribute} already stored.
     *
     * @param props
     */
    public void update(Properties props) {
        for (Object key : props.keySet()) {
            String fullName = String.valueOf(key);
            this.put(fullName, new Attribute(key.toString(), props.get(key)));
        }
    }

    /**
     * Add or replace the {@link Attribute} named by the given attribute.
     *
     * @param attr
     * @return the full-name of the {@code Attribute}.
     */
    public String update(Attribute attr) {
        this.put(attr.fullName, attr);
        return attr.fullName;
    }

    /**
     * Create a new instance of {@link Attribute} from the given {@link Prototype} and add it <b>if it does not already exist</b>.
     *
     * @see Prototype#newAttribute()
     */
    public String add(Prototype proto) {
        return this.add(proto.newAttribute());
    }

    /**
     * Add the given {@link Attribute} if it does not already exist.
     *
     * @param attr
     * @return the full-name of the {@code Attribute}.
     */
    public String add(Attribute attr) {
        return this.addFullName(attr.getName(), attr);
    }

    /**
     * Add the given {@link Attribute} if it does not already exist.
     *
     * @param fullName
     * @param attr
     * @return the full-name of the {@code Attribute}.
     */
    private String addFullName(String fullName, Attribute attr) {
        if (!this.containsKey(fullName)) {
            this.put(fullName, attr);
        }
        return fullName;
    }

    public String set(Prototype proto, Object value) {
        Attribute a = this.get(proto.fullName);
        if (a != null) {
            a.value = value;
        } else {
            this.add(proto.newAttribute(value));
        }
        return proto.fullName;
    }

    public Attribute get(Prototype proto) {
        return this.get(proto.fullName);
    }

    /**
     * Return {@code true} if the {@link Attribute} named by {@code proto} is set to the {@code null} value.
     */
    public boolean isUnset(Prototype proto) {
        Attribute a = this.get(proto.fullName);
        if (a != null) {
            if (a.value == null)
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (String key : this.keySet()) {
            Attribute a = this.get(key);
            if (a != null) {
                if (a.value != null) {
                    s.append(Attributes.saveConvert(key, true)).append("=").append(Attributes.saveConvert(String.valueOf(this.get(key).value), false)).append("\n");
                }
            }
        }
        return s.toString();
    }

    public static class Prototype {
        private String fullName;
        private Object defaultValue;
        protected String description;

        public Prototype(String fullName, Object defaultValue, String description) {
            this.fullName = fullName;
            this.defaultValue = defaultValue;
            this.description = description;
        }

        /**
         * Construct an instance of {@code Prototype} concatenating the canonical class name of {@code klasse}
         * and the given {@code name} as the full-name of the {@code Prototype}.
         * <p>
         * The default value of new instance is not set.
         * </p>
         * @param klasse
         * @param name
         */
        @Deprecated
        public Prototype(Class<?> klasse, String name) {
            this(klasse.getCanonicalName() + "." + name, null, null);
        }

        /**
         * Construct an instance of {@code Prototype} concatenating the canonical class name of {@code klasse}
         * and the given {@code name} as the full-name of the {@code Prototype}.
         * <p>
         * The default value of new instance is {@code defaultValue}.
         * </p>
         * @param name
         * @param defaultValue
         */
        @Deprecated
        public Prototype(Class<?> klasse, String name, Object defaultValue) {
            this(klasse.getCanonicalName() + "." + name, defaultValue, null);
        }
        

        /**
         * Construct an instance of {@code Prototype} concatenating the canonical class name of {@code klasse}
         * and the given {@code name} as the full-name of the {@code Prototype}, and {@code description} as the
         * description of the prototype and its value.
         * <p>
         * The default value of new instance is {@code defaultValue}.
         * </p>
         * @param name
         * @param defaultValue
         */
        public Prototype(Class<?> klasse, String name, Object defaultValue, String description) {
            this(klasse.getCanonicalName() + "." + name, defaultValue, description);
        }

        /**
         * Generate a new {@link Attribute} instance from this {@code Prototype}.
         * <p>
         * The value of new attribute is set to {@code value}.
         * </p>
         *
         * @param value
         */
        public Attribute newAttribute(Object value) {
            return new Attribute(this.fullName, value == null ? this.defaultValue : value);
        }

        /**
         * Generate a new {@link Attribute} instance from this {@code Prototype}.
         * <p>
         * The value of new attribute is set to the default value of this {@link Prototype}.
         * </p>
         */
        public Attribute newAttribute() {
            return this.newAttribute(this.defaultValue);
        }

        public String getName() {
            return this.fullName;
        }

        public Object getDefaultValue() {
            return this.defaultValue;
        }

        @Override
        public String toString() {
            return Attributes.saveConvert(this.fullName, true) + "=" + Attributes.saveConvert(this.defaultValue.toString(), false);
        }
    }

    public String asString(Attributes.Prototype prototype) {
        if (this.containsKey(prototype.fullName)) {
            return String.valueOf(this.get(prototype.fullName).value);
        }
        throw new IllegalArgumentException(String.format("Attribute \"%s\" not set", prototype.fullName));
    }

    public Integer asInt(Attributes.Prototype prototype) {
        String value = this.asString(prototype);
        return Integer.parseInt(value);
    }

    public Integer asLong(Attributes.Prototype prototype) {
        String value = this.asString(prototype);
        return Integer.parseInt(value);
    }

    public static void main(String[] args) {
        Attributes attr = new Attributes();

        Attributes.Prototype Foo = new Attributes.Prototype(Attributes.class, "Foo", "bar");
        Attributes.Prototype Bar = new Attributes.Prototype(Attributes.class, "Bar", "a");

        attr.add(Foo.newAttribute("fubar"));
        attr.add(Bar.newAttribute(null));
        System.out.println(attr.toString());

        System.out.printf("%s%n", attr.asString(Bar));
    }
}
