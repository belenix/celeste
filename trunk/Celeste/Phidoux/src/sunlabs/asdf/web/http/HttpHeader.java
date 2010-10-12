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
package sunlabs.asdf.web.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import sunlabs.asdf.web.http.Base64;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.HTTP.ConflictException;
import sunlabs.asdf.web.http.HttpUtil.PathName;

/**
 * Instances of this class represent a single HTTP message header implementing the {@link HTTP.Message.Header} interface.
 * <p>
 * The static inner classes defined in this class represent each header defined in the
 * <a href="http://webdav.org/specs/rfc4918.html">WebDAV specification</a>.
 * See the static factory method {@link HttpHeader#getInstance(String)}.
 * </p>
 *
 * @author Glenn Scott, Sun Microsystems Laboratories
 */
public abstract class HttpHeader implements HTTP.Message.Header {
    private static final long serialVersionUID = 1L;

    public static class Parameter implements HTTP.Message.Header.Parameter {
        private String attribute;
        private String value;

        public Parameter(String attribute, String value) {
            this.attribute = attribute;
            this.value = value;
        }
        
        public String getAttribute() {
            return this.attribute;
        }

        public String getValue() {
            return this.value;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(this.attribute).append("=\"").append(this.value).append("\"");
            return result.toString();
        }
    }
    
 
    // Given a String containing the header name, map to the class that implements that header.
    protected static Map<String,Class<? extends HttpHeader>> nameToClassMap = new HashMap<String,Class<? extends HttpHeader>>();
    static {
        nameToClassMap.put(HttpHeader.ACCEPT.toLowerCase(), HttpHeader.Accept.class);
        nameToClassMap.put(HttpHeader.ACCEPTCHARSET.toLowerCase(), HttpHeader.AcceptCharset.class);
        nameToClassMap.put(HttpHeader.ACCEPTENCODING.toLowerCase(), HttpHeader.AcceptEncoding.class);
        nameToClassMap.put(HttpHeader.ACCEPTLANGUAGE.toLowerCase(), HttpHeader.AcceptLanguage.class);
        nameToClassMap.put(HttpHeader.ACCEPTRANGES.toLowerCase(), HttpHeader.AcceptRanges.class);
        nameToClassMap.put(HttpHeader.AGE.toLowerCase(), HttpHeader.Age.class);
        nameToClassMap.put(HttpHeader.ALLOW.toLowerCase(), HttpHeader.Allow.class);
        nameToClassMap.put(HttpHeader.AUTHORIZATION.toLowerCase(), HttpHeader.Authorization.class);
        nameToClassMap.put(HttpHeader.CACHECONTROL.toLowerCase(), HttpHeader.CacheControl.class);
        nameToClassMap.put(HttpHeader.CONNECTION.toLowerCase(), HttpHeader.Connection.class);
        nameToClassMap.put(HttpHeader.CONTENTDISPOSITION.toLowerCase(), HttpHeader.ContentDisposition.class);
//        map.put(HttpHeader.CONTENTENCODING.toLowerCase(), HttpHeader.ContentEncoding.class);
//        map.put(HttpHeader.CONTENTLANGUAGE.toLowerCase(), HttpHeader.ContentLanguage.class);
//        map.put(HttpHeader.CONTENTLOCATION.toLowerCase(), HttpHeader.ContentLocation.class);
//        map.put(HttpHeader.CONTENTMD5.toLowerCase(), HttpHeader.ContentMD5.class);
//        map.put(HttpHeader.CONTENTRANGE.toLowerCase(), HttpHeader.ContentRange.class);
        nameToClassMap.put(HttpHeader.CONTENTLENGTH.toLowerCase(), HttpHeader.ContentLength.class);
        nameToClassMap.put(HttpHeader.CONTENTTYPE.toLowerCase(), HttpHeader.ContentType.class);
        nameToClassMap.put(HttpHeader.DATE.toLowerCase(), HttpHeader.Date.class);
//        map.put(HttpHeader.ETAG.toLowerCase(), HttpHeader.ETag.class);
//        map.put(HttpHeader.EXPECT.toLowerCase(), HttpHeader.Expect.class);
//        map.put(HttpHeader.EXPIRES.toLowerCase(), HttpHeader.Expires.class);
//        map.put(HttpHeader.FROM.toLowerCase(), HttpHeader.From.class);
        nameToClassMap.put(HttpHeader.HOST.toLowerCase(), HttpHeader.Host.class);
//        map.put(HttpHeader.IFMATCH.toLowerCase(), HttpHeader.IfMatch.class);
//        map.put(HttpHeader.IFNONEMATCH.toLowerCase(), HttpHeader.IfNoneMatch.class);
//        map.put(HttpHeader.IFRANGE.toLowerCase(), HttpHeader.IfRange.class);
//        map.put(HttpHeader.IFUNMODIFIEDSINCE.toLowerCase(), HttpHeader.IfUnmodifiedSince.class);
        nameToClassMap.put(HttpHeader.KEEPALIVE.toLowerCase(), HttpHeader.KeepAlive.class);
//        map.put(HttpHeader.LASTMODIFIED.toLowerCase(), HttpHeader.LastModified.class);
//        map.put(HttpHeader.LOCATION.toLowerCase(), HttpHeader.Location.class);
//        map.put(HttpHeader.MAXFORWARDS.toLowerCase(), HttpHeader.MaxForwards.class);
//        map.put(HttpHeader.PRAGMA.toLowerCase(), HttpHeader.Pragma.class);
//        map.put(HttpHeader.PROXYAUTHENTICATION.toLowerCase(), HttpHeader.ProxyAuthenticate.class);
//        map.put(HttpHeader.PROXYAUTHORIZATION.toLowerCase(), HttpHeader.ProxyAuthorization.class);
        nameToClassMap.put(HttpHeader.RANGE.toLowerCase(), HttpHeader.Range.class);
        nameToClassMap.put(HttpHeader.REFERER.toLowerCase(), HttpHeader.Referer.class);
//        map.put(HttpHeader.RETRYAFTER.toLowerCase(), HttpHeader.RetryAfter.class);
        nameToClassMap.put(HttpHeader.SERVER.toLowerCase(), HttpHeader.Server.class);
        nameToClassMap.put(HttpHeader.TE.toLowerCase(), HttpHeader.Te.class);
//        map.put(HttpHeader.TRAILER.toLowerCase(), HttpHeader.Trailer.class);
        nameToClassMap.put(HttpHeader.TRANSFERENCODING.toLowerCase(), HttpHeader.TransferEncoding.class);
//        map.put(HttpHeader.UPGRADE.toLowerCase(), HttpHeader.Upgrade.class);
        nameToClassMap.put(HttpHeader.USERAGENT.toLowerCase(), HttpHeader.UserAgent.class);
//        map.put(HttpHeader.VARY.toLowerCase(), HttpHeader.Vary.class);
        nameToClassMap.put(HttpHeader.VIA.toLowerCase(), HttpHeader.Via.class);
        nameToClassMap.put(HttpHeader.WARNING.toLowerCase(), HttpHeader.Warning.class);
        nameToClassMap.put(HttpHeader.WWWAUTHENTICATE.toLowerCase(), HttpHeader.WWWAuthenticate.class);

        // WebDAV Headers
        nameToClassMap.put(HttpHeader.DAV.toLowerCase(), HttpHeader.DAV.class);
        nameToClassMap.put(HttpHeader.DEPTH.toLowerCase(), HttpHeader.Depth.class);
        nameToClassMap.put(HttpHeader.DESTINATION.toLowerCase(), HttpHeader.Destination.class);
        nameToClassMap.put(HttpHeader.IF.toLowerCase(), HttpHeader.If.class);
        nameToClassMap.put(HttpHeader.LOCKTOKEN.toLowerCase(), HttpHeader.LockToken.class);
        nameToClassMap.put(HttpHeader.OVERWRITE.toLowerCase(), HttpHeader.Overwrite.class);
        nameToClassMap.put(HttpHeader.TIMEOUT.toLowerCase(), HttpHeader.TimeOut.class);
        nameToClassMap.put(HttpHeader.TIMETYPE.toLowerCase(), HttpHeader.TimeType.class);
    }

    /**
     * Factory method to parse a header contained in the {@code String} {@code line} and return a corresponding subclass of {@link HttpHeader}.
     * <p>
     * The line may have embedded CRNL+SPACE and CRNL+TAB signifying a continuation line.
     * Clients or servers using continuation lines do so at their own peril.
     * Continuation lines are not uniformly supported.
     * </p>
     * <p>
     * Nota bene: CRNL+SPACE or CRNL+TAB appearing anywhere in the header will be
     * replaced and interpreted as a single space character.  Even if the
     * CRNL+SPACE or CRNL+TAB appear in s comment or quoted text.
     * </p>
     * @see #getInstance(String, String)
     * @param line
     * @return An object implementing the {@link sunlabs.asdf.web.http.HTTP.Message.Header HTTP.Message.Header} interface.
     */
    public static HTTP.Message.Header getInstance(String line) throws HTTP.BadRequestException {
        String header = line.replaceAll("(\\r\\n)*[ \\t]+", " ").replaceAll("[ \\t]+", " ");

        String[] parts = header.split(":", 2);
        return HttpHeader.getInstance(parts[0], parts[1]);
    }
    
    /**
     * Factory method to parse a header contained in the {@code String} {@code fieldName} and {@code fieldValue}
     * returning a corresponding subclass of {@link HttpHeader}.
     * <p>
     * If the {@code fieldName} (ie. the header name) not found in the static local Map {@link #nameToClassMap},
     * this method will return an instance of {@link HttpHeader.Generic}.
     * Otherwise, an instance specific to the header name is returned. 
     * </p>
     * @see #getInstance(String) 
     * @param fieldName The header name (no trailing colon character).
     * @param fieldValue The header value
     * @return An object implementing the {@link sunlabs.asdf.web.http.HTTP.Message.Header HTTP.Message.Header} interface.
     */
    public static HTTP.Message.Header getInstance(String fieldName, String fieldValue) throws HTTP.BadRequestException {
        fieldName = fieldName.trim();
        fieldValue = fieldValue.trim();

        try {
            Class<? extends HttpHeader> c = nameToClassMap.get(fieldName.toLowerCase());
            if (c == null) {
                return new HttpHeader.Generic(fieldName, fieldValue);
            }
            return c.getConstructor(String.class).newInstance(fieldValue);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof HTTP.BadRequestException) {
                throw new HTTP.BadRequestException(e.getCause());
            }
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Incompletely implemented
     *
     */
    public static class Accept extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        private List<InternetMediaType> types;

        public Accept() {
            super(HttpHeader.ACCEPT, MultipleHeaders.NOTALLOWED);
            this.types = new LinkedList<InternetMediaType>();
        }

        public Accept(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
            this.needsParsing = true;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            if (!this.needsParsing)
                return;

            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            for (HeaderToken t : tokens) {
                if (t.equals("*"))
                    System.out.printf("%s: %s%n", t.getClass(), t);
                this.types.add(InternetMediaType.getInstance(t.toString()));
            }

            this.needsParsing = false;
        }

        @Override
        public String format() {
            this.fieldValue = "";
            for (InternetMediaType type : this.types) {
                this.fieldValue += "; " + type.toString();
            }
            return this.fieldValue;
        }
        
    }

    /**
     * Incompletely implemented
     *
     */
    public static class AcceptCharset extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public AcceptCharset() {
            super(HttpHeader.ACCEPTCHARSET, MultipleHeaders.NOTALLOWED);
        }

        public AcceptCharset(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    /**
     * Incompletely implemented
     *
     */
    public static class AcceptEncoding extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public AcceptEncoding() {
            super(HttpHeader.ACCEPTENCODING, MultipleHeaders.NOTALLOWED);
        }

        public AcceptEncoding(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    /**
     * Incompletely implemented
     *
     */
    public static class AcceptLanguage extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public AcceptLanguage() {
            super(HttpHeader.ACCEPTLANGUAGE, MultipleHeaders.NOTALLOWED);
        }

        public AcceptLanguage(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    /**
     * Incompletely implemented
     *
     */
    public static class AcceptRanges extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public AcceptRanges() {
            super(HttpHeader.ACCEPTRANGES, MultipleHeaders.NOTALLOWED);
        }

        public AcceptRanges(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        public String getRanges() {
            return this.fieldValue;
        }
    }

    public static class Age extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public Age() {
            super(HttpHeader.AGE, MultipleHeaders.NOTALLOWED);
        }

        public Age(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    /**
     * Incompletely implemented
     *
     */
    public static class Allow extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        private Set<HTTP.Request.Method> methods;

        private Allow() {
            super(HttpHeader.ALLOW, MultipleHeaders.NOTALLOWED);
            this.methods = new HashSet<HTTP.Request.Method>();
        }

        public Allow(Collection<HTTP.Request.Method> methods) {
            this();

            for (HTTP.Request.Method method : methods) {
                this.methods.add(method);
            }
        }
        
        public Allow(HTTP.Request.Method...methods) {
            this();

            for (HTTP.Request.Method method : methods) {
                this.methods.add(method);
            }
        }
        
        public void parse() throws HTTP.NotImplementedException {
            throw new HTTP.NotImplementedException();            
        }
        
        public String format() {
            StringBuilder result = new StringBuilder();

            boolean comma = false;
            for (HTTP.Request.Method method : this.methods) {
                if (comma) {
                    result.append(", ");
                }
                result.append(method.toString());
                comma = true;
            }
            this.fieldValue = result.toString();
            return this.fieldValue;
        }

        public Set<HTTP.Request.Method> getMethods() {
            return this.methods;
        }
    }

    /**
     *
     */
    public static class Authorization extends HttpHeader implements HTTP.Message.Header.Authorization {
    	private final static long serialVersionUID = 1L;
    	
        private String scheme;
        private String parameters;

        private String basicUserName;
        private String basicPassword;

        public Authorization() {
            super(HttpHeader.AUTHORIZATION, MultipleHeaders.NOTALLOWED);
        }

        public Authorization(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
            this.needsParsing = true;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            if (!this.needsParsing)
                return;

            String[] tokens = this.fieldValue.split("[ ]+", 2);
            if (tokens.length != 2) {
                throw new HTTP.BadRequestException(HttpHeader.AUTHORIZATION + " " + this.fieldValue);
            }
            this.scheme = tokens[0];
            this.parameters = tokens[1];

            if (this.scheme.compareToIgnoreCase("Basic") == 0) {
                String[] params = Base64.decode(this.parameters).split(":");
                if (params.length != 2)
                    throw new HTTP.BadRequestException(String.format("%s: malformed basic credentials (%s).", this.parameters, Base64.decode(this.parameters)));

                this.basicUserName = params[0];
                this.basicPassword = params[1];
            } else if (this.scheme.compareTo("Digest") == 0) {
                throw new HTTP.BadRequestException("Only Basic authentication is implemented.");
            }
        }
        
        /**
         * Return {@code true} if this header's field value is valid.
         */
        public boolean valid() {
            try {
                this.parse();
                return true;
            } catch (HTTP.BadRequestException e) {
                return false;
            }
        }

        /**
         * Get the <em>scheme</em> field-value component of this {@code Authorization} header.
         * @throws InvalidFormatException if the field values are incorrectly formatted.
         */
        public String getScheme() throws HTTP.BadRequestException {
            this.parse();
            return this.scheme;
        }

        /**
         * Get the <em>parameter</em> field-value component of this Authorization header.
         * @throws InvalidFormatException if the field values are incorrectly formatted.
         */
        public String getParameters() throws HTTP.BadRequestException {
            this.parse();
            return this.parameters;
        }

        /**
         * Get the Basic Authentication parameters from this Authentication header.
         * @return A String array containing the colon separated parts of the Authentication parameters.
         * @throws InvalidFormatException if the Authorization scheme is not {@code Basic} or the field values are incorrectly formatted.
         */
        public String[] getBasicParameters() throws HTTP.BadRequestException {
            this.parse();
            if (this.getScheme().compareTo("Basic") != 0) {
                throw new HTTP.BadRequestException("Authorization must be Basic.");
            }

            String[] result = Base64.decode(this.parameters).split(":");
            return result;
        }

        public String getBasicUserName() throws HTTP.BadRequestException {
            this.parse();
            return this.basicUserName;
        }

        public String getBasicPassword() throws HTTP.BadRequestException {
            this.parse();
            return this.basicPassword;
        }
    }

    /**
     * Incompletely implemented
     *
     */
    public static class CacheControl extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public CacheControl() {
            super(HttpHeader.CACHECONTROL, MultipleHeaders.NOTALLOWED);
        }

        public CacheControl(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        public String getControl() {
            return this.fieldValue;
        }
    }

    /**
     * The HTTP {@code Connection:} header.
     */
    public static class Connection extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	/**
    	 * Convenience constant consisting of a {@code Connection: close} header.
    	 */
    	public final static Connection CLOSE = new Connection("close");
    	
        private String connection;
        private Set<String> tokens;

        private Connection() {
            super(HttpHeader.CONNECTION, MultipleHeaders.NOTALLOWED);
            this.connection = null;
            this.tokens = new HashSet<String>();
        }

        public Connection(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
            this.needsParsing = true;
        }
        
        public void parse() throws HTTP.BadRequestException {
            if (!this.needsParsing)
                return;

            List<HeaderToken> tokens = HttpHeader.parseFields(this.fieldValue, false);
            for (HeaderToken token : tokens) {
                if (!token.equals(",")) {
                    this.tokens.add(token.toString());
                }
            }
            if (tokens.size() > 0) {
                this.connection = tokens.get(0).toString();
            } else {
                throw new HTTP.BadRequestException("Missing Connection value");
            }
        }

        @Override
        public String format() {
            // Don't parse, because there is no method that adds tokens to the field value.
            // this.parse();
            
            StringBuilder result = new StringBuilder();
            boolean comma = false;
            for (String token : this.tokens) {
                if (comma)
                    result.append(", ");
                result.append(token);
                comma = true;                
            }
            
            this.fieldValue = result.toString();

            return this.fieldValue;
        }
        
        public Set<String> getTokens() throws HTTP.BadRequestException {
            this.parse();
            return this.tokens;
        }
        
        public boolean contains(String parameter) throws HTTP.BadRequestException {
            this.parse();
            return this.tokens.contains(parameter);
        }

        /**
         * This is a short-hand method which makes the (not valid, but not uncommon) assumption that the Connection header contains a single value.
         * @return the field value for the {@code Connection} header.
         * @throws BadRequestException
         */
        public String getConnection() throws HTTP.BadRequestException {
            this.parse();
            return this.connection;
        }
    }

    public static class ContentDisposition extends HttpHeader implements HTTP.Message.Header.ContentDisposition {
        private final static long serialVersionUID = 1L;
        
        String type;
        private Map<String,HTTP.Message.Header.Parameter> parameters;

        public ContentDisposition() {
            super(HttpHeader.CONTENTDISPOSITION, MultipleHeaders.NOTALLOWED);
            this.parameters = new HashMap<String,HTTP.Message.Header.Parameter>();
        }

        public ContentDisposition(String fieldValue) throws HTTP.BadRequestException {
            this();
            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            this.type = tokens.get(0).toString();

            for (int i = 1; i < tokens.size(); i++) {
                if (tokens.get(i).equals(";")) {
                    String attribute = tokens.get(++i).toString();
                    i++;
                    String value = tokens.get(++i).toString();
                    if (value.startsWith("\"")) {
                        value = value.substring(1, value.length()-1);
                    }
                    this.parameters.put(attribute, new HttpHeader.Parameter(attribute, value));
                }
            }
            this.fieldValue = fieldValue;
        }

        public ContentDisposition(String type, HTTP.Message.Header.Parameter...parameters) {
            this();

            this.type = type;
            for (HTTP.Message.Header.Parameter p : parameters) {
                this.parameters.put(p.getAttribute(), p);
            }

            // XXX Factor this.
            this.fieldValue = this.type.toString();
            for (Map.Entry<String,HTTP.Message.Header.Parameter> p : this.parameters.entrySet()) {
                this.fieldValue += "; " + p.getValue().toString();
            }
        }

        public String getType() {
            return this.type;
        }

        public Map<String,HTTP.Message.Header.Parameter> getParameters() {
            return this.parameters;
        }

        public HTTP.Message.Header.Parameter getParameter(String name) {
            return this.parameters.get(name);
        }
    }

    public static class ContentLength extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        private long length;

        private ContentLength() {
            super(HttpHeader.CONTENTLENGTH, MultipleHeaders.NOTALLOWED);
        }

        public ContentLength(String fieldValue) throws HTTP.BadRequestException {
            this();

            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            if (tokens.size() > 0) {
                this.length = Long.parseLong(tokens.get(0).toString());
            } else {
                throw new HTTP.BadRequestException("Missing ContentLength value");
            }
        }

        public ContentLength(long length) {
            this();
            this.length = length;
        }

        @Override
        public String format() {
            this.fieldValue = Long.toString(this.length);
            return this.fieldValue;
        }

        public long getLength() {
            return this.length;
        }
    }

    public static class ContentType extends HttpHeader implements HTTP.Message.Header.ContentType {
    	private final static long serialVersionUID = 1L;
    	
        private InternetMediaType mediaType;
        private Map<String,HTTP.Message.Header.Parameter> parameters;

        public ContentType() {
            super(HttpHeader.CONTENTTYPE, MultipleHeaders.NOTALLOWED);
            this.parameters = new HashMap<String,HTTP.Message.Header.Parameter>();
        }

        public ContentType(String fieldValue) throws HTTP.BadRequestException {
            this();

            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);

            this.mediaType = InternetMediaType.getInstance(tokens.get(0) + "/" + tokens.get(2));

            for (int i = 3; i < tokens.size(); i++) {
                if (tokens.get(i).equals(";")) {
                    String attribute = tokens.get(++i).toString();
                    i++;
                    this.parameters.put(attribute, new HttpHeader.Parameter(attribute, tokens.get(++i).toString()));
                }
            }
        }

        /**
         * Construct a {@code Content-Type:} {@link HTTP.Message.Header} instance specifying the given {@link InternetMediaType} as the content-type.
         */ 
        public ContentType(InternetMediaType type) {
            this();

            this.mediaType = type;
        }

        public ContentType(InternetMediaType type, HTTP.Message.Header.Parameter...parameters) {
            this();

            this.mediaType = type;
            for (HTTP.Message.Header.Parameter p : parameters) {
                this.parameters.put(p.getAttribute(), p);
            }
        }

        @Override
        protected String format() {
            this.fieldValue = this.mediaType.toString();
            for (Map.Entry<String,HTTP.Message.Header.Parameter> p : this.parameters.entrySet()) {
                this.fieldValue += "; " + p.getValue().toString();
            }
            return this.fieldValue;
        }

        public InternetMediaType getType() {
            return this.mediaType;
        }

        public Map<String,HTTP.Message.Header.Parameter> getParameters() {
            return this.parameters;
        }

        public HTTP.Message.Header.Parameter getParameter(String name) {
            return this.parameters.get(name);
        }
    }


    /**
     * RFC 822 + RFC 1123 formatted date.
     *
     * Sun, 06 Nov 1994 08:49:37 GMT  ; RFC 822, updated by RFC 1123
     */
    public static class Date extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        /**
         * Create an HttpHeader Date containing of the current date and time.
         */
        public Date() {
            super(HttpHeader.DATE, MultipleHeaders.NOTALLOWED);
        }

        public Date(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public String format() {
            this.fieldValue = String.format("%1$ta, %1$td %1$tb %1$tY %1$tT %1$tZ", Calendar.getInstance(TimeZone.getTimeZone("GMT")));
            return this.fieldValue;
        }

        public String getDate() {
            return this.fieldValue;
        }
    }

    public static class DAV extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public DAV() {
            super(HttpHeader.DAV, MultipleHeaders.ALLOWED);
        }

        public DAV(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
        
        public DAV(Collection<String> values) {
            this();
            StringBuilder result = new StringBuilder();
            String comma = "";
            for (String value : values) {
                result.append(comma).append(value);
                comma = ",";                
            }
            this.fieldValue = result.toString();            
        }
    }

    /**
     * See <a href="http://www.webdav.org/specs/rfc4918.html#HEADER_Depth">WebDAV Depth Header</a>
     *
     */
    public static class Depth extends HttpHeader implements HTTP.Message.Header.Depth { 
    	
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        private HTTP.Message.Header.Depth.Level depth;

        public Depth() {
            super(HttpHeader.DEPTH, MultipleHeaders.ALLOWED);
        }

        public Depth(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public String format() {
            this.fieldValue = this.depth.toString();
            return this.fieldValue;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            if (this.fieldValue.compareTo("0") == 0) {
                this.depth = Level.ONLY;
            } else if (this.fieldValue.compareTo("1") == 0) {
                this.depth = Level.ALL;
            } else {
                this.depth = Level.INFINITY;
            }
        }

        public HTTP.Message.Header.Depth.Level getLevel() throws HTTP.BadRequestException {
            this.parse();
            return this.depth;
        }
    }

    public static class Destination extends HttpHeader implements HTTP.Message.Header.Destination {
    	private final static long serialVersionUID = 1L;

    	private URI uri;
    	
        public Destination() {
            super(HttpHeader.DESTINATION, MultipleHeaders.ALLOWED);
        }

        public Destination(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
        
        @Override
        public String format() {
            if (this.fieldValue == null)
                this.fieldValue = this.uri.toString();
            return this.fieldValue;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            try {
                this.uri = new URI(this.fieldValue);
            } catch (URISyntaxException e) {
                throw new HTTP.BadRequestException("Malformed Destination header.", e);
            }
        }

        public URI getURI() throws HTTP.BadRequestException {
            this.parse();
            return this.uri;
        }
    }

    
    /**
     * A generic HttpHeader.
     * 
     */
    public static class Generic extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        public Generic(String fieldName, String fieldValue) {
            super(fieldName, MultipleHeaders.ALLOWED);
            this.fieldValue = fieldValue;
        }
    }    


    
    public static class ETag extends HttpHeader implements HTTP.Message.Header.ETag {
        private final static long serialVersionUID = 1L;
        
        private HTTP.EntityTag entityTag;

        public ETag(String fieldValue) throws HTTP.BadRequestException {
            super(HttpHeader.ETAG, MultipleHeaders.NOTALLOWED);
            this.fieldValue = fieldValue;
            this.entityTag = new HTTP.Resource.State.EntityTag(fieldValue);
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            this.entityTag = new HTTP.Resource.State.EntityTag(this.fieldValue);
        }

        public HTTP.EntityTag getEntityTag() {
            return this.entityTag;
        }
    }

    public static class Host extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        private String host;
        private String port;

        public Host(String fieldValue) throws HTTP.BadRequestException {
            super(HttpHeader.HOST, MultipleHeaders.NOTALLOWED);

            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            if (tokens.size() > 0) {
                this.host = tokens.get(0).toString();
                if (tokens.size() > 2) {
                    this.port = tokens.get(2).toString();
                }
            }
            this.fieldValue = this.host;
            if (this.port != null) {
                this.fieldValue += ":" + this.port;
            }
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            if (tokens.size() > 0) {
                this.host = tokens.get(0).toString();
                if (tokens.size() > 2) {
                    this.port = tokens.get(2).toString();
                }
            }
        }

        public String getHost() {
            return this.host;
        }

        public String getPort() {
            return this.port;
        }
    }

    /*
     * If = "If" ":" ( 1*No-tag-list | 1*Tagged-list ) 
     * No-tag-list = List
     * Tagged-list = Resource-Tag 1*List
     *   
     * Resource-Tag = "<" Simple-ref ">"
     * Simple-ref   = absolute-URI | ( path-absolute [ "?" query ] ) ; No LWS allowed in Resource-Tag
     * 
     * List = "(" 1*Condition ")"
     * Condition = ["Not"] (State-token | "[" entity-tag "]")
     *   ; entity-tag: see Section 3.11 of [RFC2616]
     *   ; No LWS allowed between "[", entity-tag and "]"
     *   
     * State-token = Coded-URL
     * Coded-URL   = "<" absolute-URI ">" 
     *   ; No linear whitespace (LWS) allowed in Coded-URL
     *   ; absolute-URI defined in RFC 3986, Section 4.3
     *   
     * From  Section 3.11 of [RFC2616]:
     * entity-tag = [ weak ] opaque-tag
     * weak       = "W/"
     * opaque-tag = quoted-string
     *
     * Section 8.3  
     *     
     * If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> ["I am an ETag"])
     *     (["I am another ETag"])
     *
     * If: (Not <urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> 
     *      <urn:uuid:58f202ac-22cf-11d1-b12d-002035b29092>)
     *
     * If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2>) 
     *     (Not <DAV:no-lock>)
     *
     * If: </resource1> 
     *      (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> [W/"A weak ETag"])
     *      (["strong ETag"])
     *
     * If: </specs/rfc2518.doc> (["4217"])
     */
    public static class If extends HttpHeader implements HTTP.Message.Header.If {
    	private final static long serialVersionUID = 1L;
    	private Set<HTTP.Resource.State.Token> submittedTokens;
    	private Conditional conditional;
    	
        public If() {
            super(HttpHeader.IF, MultipleHeaders.NOTALLOWED);
            this.conditional = new Conditional();
            this.submittedTokens = new HashSet<HTTP.Resource.State.Token>();
        }

        public If(String fieldValue) throws HTTP.BadRequestException {
            this();
            this.fieldValue = fieldValue;
        }
        
        public String format() {
            return this.fieldValue;
        }
        
        @Override
        public void parse() throws HTTP.BadRequestException {
            if (!this.needsParsing)
                return;

            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, true);
//            HttpHeader.print(tokens);

            try {
                URI resourceTag = null;
                for (HeaderToken t : tokens) {
                    if (t instanceof TokenComment) {  // These appear as header comments.
                        HTTP.Message.Header.If.Condition condition = new Condition(t.toString());
                        this.submittedTokens.addAll(condition.getStateTokens());
                        this.conditional.add(resourceTag, condition);
                    } else if (t instanceof HeaderToken.TokenURI) { // this must be a Resource-Tag of the form "<" URI ">"
                        String uri = t.toString();
                        resourceTag = new URI(uri.substring(1, uri.length() -1));
                    } else {
                        throw new HTTP.BadRequestException(String.format("Malformed If header: %s", this.fieldValue));
                    }
                }
            } catch (URISyntaxException e) {
                throw new HTTP.BadRequestException(String.format("Malformed If header: %s: %s", e.toString(), this.fieldValue));
            } finally {

            }

            this.needsParsing = false;
        }
        
        public boolean evaluate(HTTP.Resource...affectedResources)
        throws HTTP.BadRequestException, HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, ConflictException {
            this.parse();
            System.out.printf("IF.evaluate submitted state-tokens: %s%n", this.submittedTokens);
            
            // The If header evaluates the state tokens for each specified URI.
            // This means we need a way to fetch the state tokens for a resource.
            // But there is no "fetch state tokens" there is only lockdiscovery which is about locking not about general state.

            // Evaluate each ConditionSet in this If headers Conditional.
            // For a non-tagged ConditionSet (null resource URI) in the Conditional, substitute each of the defaultResources given as a parameter. 
            //
            // What I'd like to do here is fetch the lockdiscovery for each resource in the list.
            // Wherever that resource might be (cross-server).
            return this.conditional.evaluate(affectedResources);
        }
        
        public HTTP.Message.Header.If.Conditional getConditional() throws HTTP.BadRequestException {
            this.parse();
            return this.conditional;
        }
        
        public Collection<HTTP.Resource.State.Token> getSubmittedStateTokens() throws HTTP.BadRequestException {
            this.parse();
            return this.submittedTokens;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("If: ").append(this.fieldValue);

            return result.toString();
        }
        
        public static class Conditional implements HTTP.Message.Header.If.Conditional {            
            private Map<URI,HTTP.Message.Header.If.ConditionSet> conditionSets;

            public Conditional() {
                this.conditionSets = new HashMap<URI,HTTP.Message.Header.If.ConditionSet>();
            }

            public void add(URI resourceTag, HTTP.Message.Header.If.Condition toAdd) {
                HTTP.Message.Header.If.ConditionSet existingSet = this.conditionSets.get(resourceTag);
                if (existingSet == null) {
                    existingSet = new ConditionSet();
                }
                existingSet.add(toAdd);
                this.conditionSets.put(resourceTag, existingSet);
            }
            
            public HTTP.Message.Header.If.ConditionSet getConditionSet(URI resourceTag) {
                return this.conditionSets.get(resourceTag);                
            }

            
            public boolean evaluate(HTTP.Resource...defaultResources)
            throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
                // First evaluate the non-tagged ConditionSet for each of the defaultResources...
                HTTP.Message.Header.If.ConditionSet nonTagged = this.conditionSets.get(null);
                if (nonTagged != null) {
                    for (HTTP.Resource defaultResource : defaultResources) {
                        if (nonTagged.evaluate(defaultResource) == false) {
                            return false;
                        }
                    }
                }
                
                return true;
            }
            
            public boolean evaluate(URI requestURI, Map<URI,HTTP.Resource.State> resourceStates) {
                // Each URI in the resourceStates Map must have a corresponding match in this conditional.
                // A null key in this conditional matches the request URI.  (This is not very clean.)
                // So if a resourceURI cannot be looked up in this conditional, but the resourceURI matches the requestURI, then lookup the null key.
                System.out.printf("Conditional: %s%n", this.toString());
                
                for (URI resourceURI : resourceStates.keySet()) {
                    HTTP.Message.Header.If.ConditionSet conditionSet = this.conditionSets.get(resourceURI);
                    System.out.printf("Conditional: resourceURI=%s set=%s%n", resourceURI, conditionSet);
                    if (conditionSet == null) { // If the resourceURI is not found in this map.
                        if (!resourceURI.equals(requestURI)) { // and it is NOT equal to the requestURI, then fail.
                            System.out.printf("Conditional %s: false.  Not the default URI %s, therefore not present in the conditionSets%n", resourceURI, requestURI);
                            return false;
                        }
                        // Otherwise, use the "null" key to get the default CollectionSet
                        conditionSet = this.conditionSets.get(null);
                        if (conditionSet == null) {
                            System.out.printf("Conditional %s: false%n", resourceURI);
                            return false;
                        }
                    }
                    
                    if (!conditionSet.matches(resourceStates.get(resourceURI))) {
                        System.out.printf("Conditional %s: false%n", resourceURI);
                        return false;
                    }
                }
                System.out.printf("Conditional: true%n");
                return true;
            }

            public int size() {
                return this.conditionSets.size();
            }

            @Override
            public String toString() {
                StringBuilder result = new StringBuilder();

                for (URI resourceTag : this.conditionSets.keySet()) {
                    HTTP.Message.Header.If.ConditionSet conditions = this.conditionSets.get(resourceTag);
                    if (resourceTag != null) {
                        result.append("<").append(resourceTag.toASCIIString()).append("> ");
                    }
                    result.append(conditions.toString()).append(" ");
                }
                result.append(" ");
                return result.toString();
            }
        }

        /**
         * <p>
         * Each List production describes a series of conditions.
         * The whole list evaluates to true if and only if each condition evaluates to true (that is, the list represents a logical conjunction of Conditions).
         * </p>
         * <p>
         * Each No-tag-list and Tagged-list production may contain one or more Lists.
         * They evaluate to true if and only if any of the contained lists evaluates to true
         * (that is, if there's more than one List, that List sequence represents a logical disjunction of the Lists).
         * </p>
         * <p>
         * Finally, the whole If header evaluates to true if and only if at least one of the No-tag-list or Tagged-list productions evaluates to true.
         * If the header evaluates to false, the server MUST reject the request with a 412 (Precondition Failed) status.
         * Otherwise, execution of the request can proceed as if the header wasn't present.
         * </p>
         * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsytems, Inc.
         */
        // List = "(" 1*Condition ")"

        
        /*
         * ( condition_1 )
         * ( condition_2 )
         * ( condition_3 )
         * ...
         */
        public static class ConditionSet implements HTTP.Message.Header.If.ConditionSet {
            private Collection<HTTP.Message.Header.If.Condition> conditions;
            
            public ConditionSet() {
                this.conditions = new HashSet<HTTP.Message.Header.If.Condition>();
            }
            
            public ConditionSet(Collection<HTTP.Message.Header.If.Condition> conditions) {
                this.conditions = conditions;
            }

            public void add(HTTP.Message.Header.If.Condition condition) {
                this.conditions.add(condition);
            }
            
            public boolean matches(HTTP.Resource.State resourceState) {
                System.out.printf("  ConditionSet: %s%n", this.toString());
                for (HTTP.Message.Header.If.Condition condition : this.conditions) {
                    if (condition.matches(resourceState)) {
                        System.out.printf("  ConditionSet: %s true%n", this.toString());
                        return true;
                    }
                }
                System.out.printf("  ConditionSet: %s false%n", this.toString());
                return false;
            }

            public Collection<HTTP.Message.Header.If.Condition> getConditions() {
                return this.conditions;
            }
            
            public String toString() {
                StringBuilder result = new StringBuilder();
                String space = "";
                for (HTTP.Message.Header.If.Condition condition : this.conditions) {
                    result.append(space).append(condition.toString());
                    space = " ";                    
                }
                
                return result.toString();            
            }

            public boolean evaluate(HTTP.Resource resource)
            throws HTTP.InternalServerErrorException, HTTP.NotFoundException, HTTP.GoneException, HTTP.UnauthorizedException, HTTP.ConflictException {
                for (HTTP.Message.Header.If.Condition condition : this.conditions) {
                    if (condition.matches(resource.getResourceState()) == true) {
                        return true;
                    }
                }
                return false;
            }
        }
        
        public static class Condition implements HTTP.Message.Header.If.Condition {
            private Collection<HTTP.Message.Header.If.ConditionTerm> terms;
            private Collection<HTTP.Resource.State.Token> stateTokens;
            
            public Condition() {
                this.terms = new HashSet<HTTP.Message.Header.If.ConditionTerm>();
                this.stateTokens = new HashSet<HTTP.Resource.State.Token>();
            }
            
            /**
             * Compose a new Condition instance from the given String containing the representation of the Condition
             * as series of entity-tags and state-tokens syntactically encapsulated in "(" ")".
             * 
             * @param string the representation of the Condition as series of entity-tags and state-tokens syntactically encapsulated in "(" ")"
             * @throws HTTP.BadRequestException if the String cannot be parsed.
             */
            public Condition(String string) throws HTTP.BadRequestException {
                this();
                try {
                    List<HeaderToken> tt = HttpHeader.parseFields(string.substring(1, string.length()-1), true);

                    boolean negated = false;
                    for (HeaderToken u : tt) {
                        if (u instanceof HeaderToken.TokenURI) { // A State-token
                            HTTP.Resource.State.Token stateToken = new HTTP.Resource.State.CodedURL(((HeaderToken.TokenURI) u).getURI());
                            this.terms.add(new ConditionTerm(negated, stateToken));
                            this.stateTokens.add(stateToken);
                            negated = false;
                        } else if (u instanceof HeaderToken.EntityTag) { // An Entity-tag
                            this.terms.add(new ConditionTerm(negated, new HTTP.Resource.State.EntityTag(((HeaderToken.EntityTag) u).getEntityTag())));
                            negated = false;
                        } else if (u instanceof HeaderToken) { // Must be "Not"
                            negated = true; // assume so....                                
                        }                            
                    }
                } catch (URISyntaxException e) {
                    throw new HTTP.BadRequestException(String.format("%s: %s", e.toString(), string));
                } finally {

                }                
            }
            
            public void add(HTTP.Message.Header.If.ConditionTerm term) {
                this.terms.add(term);
            }
            
            public Collection<HTTP.Message.Header.If.ConditionTerm> getConditions() {
                // TODO Auto-generated method stub
                return null;
            }
            
            public Collection<HTTP.Resource.State.Token> getStateTokens() {
                return this.stateTokens;
            }
            
            public Collection<HTTP.Message.Header.If.ConditionTerm> getTerms() {
                return this.terms;
            }

            public boolean matches(HTTP.Resource.State resourceState) {
//                System.out.printf("    Condition %s:%n", this.toString());
                if (true) {
                    // The rules as I understand them (at the moment):

                    // Return TRUE *only* if:
                    // 1) Each entity-tag in this Condition must be in the resource state.
                    // 2) All lock-tokens in the resource, must be in the If header.

                    // For each term in this Condition:
                    //  If it is an entity-tag, it must be present in the resourceState

                    for (HTTP.Message.Header.If.ConditionTerm term : this.terms) {
                        Operand operand = term.getOperand();
                        if (operand instanceof HTTP.Resource.State.EntityTag) {
                            if (!operand.match(resourceState.getEntityTag())) {
                                System.out.printf("    Condition: false entity-tag %s is not in %s%n", term, resourceState);
                                return false;
                            } else {
                                System.out.printf("    Condition: true entity-tag %s is in %s%n", term, resourceState);
                                // true
                            }
                        }
                        
                        if (operand instanceof HTTP.Resource.State.Token) {
                            System.out.printf("state-tag: %s%n", operand.getClass());
                            
                            if (!term.matches(resourceState.getStateTokens())) {
                                System.out.printf("    Condition: false state-token %s is not in %s%n", term, resourceState);
                                return false;
                            } else {
                                System.out.printf("    Condition: true state-token %s is in %s%n", term, resourceState);
                                // true
                            }
                        }
                    }

//                    // For each term in the resourceState:
//                    //  If it is a state-token, it must be present in this Condition.
//                    for (HTTP.Resource.State.Token stateTag : resourceState.getStateTags()) {
//                        boolean result = this.terms.contains(stateTag);
//
//                        if (!this.terms.contains(stateTag)) {
//                            System.out.printf("    Condition: false %s is not in %s%n", stateTag, this.terms);
//                            return false;
//                        } else {
//                            System.out.printf("    Condition: true %s is in %s%n", stateTag, this.terms);
//                            // true
//                        }                       
//                    }
                }
                return true;
            }
            
            public String toString() {
                StringBuilder result = new StringBuilder();

                result.append("(");
                String space = "";
                for (HTTP.Message.Header.If.ConditionTerm term : this.terms) {
                    result.append(space);
                    result.append(term.toString());
                    space = " ";
                }
                result.append(")");
                return result.toString();                
            }
        }
        
        /**
         * Condition = ["Not"] (State-token | "[" entity-tag "]")
         */
        public static class ConditionTerm implements HTTP.Message.Header.If.ConditionTerm {
            private boolean negated;
            private HTTP.Message.Header.If.Operand operand;
            
            public ConditionTerm(boolean negated, HTTP.Message.Header.If.Operand operand) {
                this.negated = negated;
                this.operand = operand;
            }
            
            @Override
            public int hashCode() {
                return this.operand.hashCode();
            }
            
            @Override
            public boolean equals(Object o) {
                if (o == null)
                    return false;
                if (o == this)
                    return true;
                if (o instanceof ConditionTerm) {
                    ConditionTerm other = (ConditionTerm) o;
                    if (this.operand.equals(other.operand) && this.negated == other.negated)
                        return true;
                }
                return false;
            }
            
            public boolean isNegated() {
                return this.negated;
            }

            public HTTP.Message.Header.If.Operand getOperand() {
                return this.operand;
            }

            public boolean matches(Collection<HTTP.Resource.State.Token> terms) {
                boolean result = terms.contains(this.operand);
                if (this.negated) {
                    result = !result;
                }
                System.out.printf("      ConditionTerm %s: %b%n", this.toString(), result);
                return result;
            }

            @Override
            public String toString() {
                StringBuilder result = new StringBuilder();

                if (this.isNegated())
                    result.append("Not ");
                result.append(this.operand.toString());

                return result.toString();
            }
        }
    }

    public static class KeepAlive extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        private KeepAlive() {
            super(HttpHeader.KEEPALIVE, MultipleHeaders.NOTALLOWED);
        }

        public KeepAlive(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        public KeepAlive(long seconds) {
            this();
            this.fieldValue = Long.toString(seconds);
        }
    }

    public static class Location extends HttpHeader {
        private final static long serialVersionUID = 1L;
        private URI uri;
        
        public Location() {
            super(HttpHeader.LOCATION, MultipleHeaders.NOTALLOWED);
        }
        
        public Location(URI absoluteURI) {
            this();
            this.uri = absoluteURI;
        }

        public Location(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            try {
                this.uri = new URI(this.fieldValue);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } finally {

            }
        }

        public URI getLocation() throws HTTP.BadRequestException {
            this.parse();
            return this.uri;
        }

        @Override
        public String format() {
            this.fieldValue = this.uri.toString();
            return this.fieldValue;
        }
    }
    
    public static class LockToken extends HttpHeader implements HTTP.Message.Header.LockToken {
    	private final static long serialVersionUID = 1L;
    	private HTTP.Resource.State.CodedURL codedURL;
    	
        public LockToken() {
            super(HttpHeader.LOCKTOKEN, MultipleHeaders.NOTALLOWED);
        }
        
        public LockToken(URI absoluteURI) {
            this();
            this.codedURL = new HTTP.Resource.State.CodedURL(absoluteURI);
        }

        public LockToken(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            this.codedURL = new HTTP.Resource.State.CodedURL(this.fieldValue);            
        }
        
        public URI getCodedURL() throws HTTP.BadRequestException {
            this.parse();
            return this.codedURL.getCodedURL();
        }

        @Override
        public String format() {
            this.fieldValue = this.codedURL.toString();
            return this.fieldValue;
        }
    }

    public static class Overwrite extends HttpHeader implements HTTP.Message.Header.Overwrite {
    	private final static long serialVersionUID = 1L;
    	
        private boolean overwrite;

        public Overwrite() {
            super(HttpHeader.OVERWRITE, MultipleHeaders.NOTALLOWED);
            this.overwrite = true;
        }

        public Overwrite(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public void parse() {
            this.overwrite = (this.fieldValue.compareTo("T") == 0);
        }

        @Override
        public String format() {
            return this.overwrite ? "T" : "F";
        }

        public boolean getOverwrite() {
            this.parse();
            return this.overwrite;
        }
    }

    public static class Range extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	
        private long[][] ranges;

        private Range() {
            super(HttpHeader.RANGE, MultipleHeaders.NOTALLOWED);
        }

        public Range(String fieldValue) throws HTTP.BadRequestException {
            this();
            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, false);
            System.out.printf("Range(%s)%n", fieldValue);

            ArrayList<long[]> accumulator = new ArrayList<long[]>();

            // XXX catch the error of a null range.  Just the string: "-"
            if (tokens.get(0).equals("bytes")) {
                // tokens[1] should be '='
                for (int i = 2; i < tokens.size(); i++) {
                    HeaderToken t = tokens.get(i);
                    if (t.equals(","))
                        continue;

                    String range[] = t.toString().split("-");
                    if (range.length == 1) { // N-
                        accumulator.add(new long[] {Long.parseLong(range[0]), -1 });
                    } else if (range.length == 2) { // N-M, -M
                        if (range[0].compareTo("") == 0) { // -M
                            accumulator.add(new long[] {-1, Long.parseLong(range[1]) });
                        } else { // N-M
                            accumulator.add(new long[] {Long.parseLong(range[0]), Long.parseLong(range[1]) });
                        }
                    }
                }
                this.ranges = new long[accumulator.size()][2];
                accumulator.toArray(this.ranges);
            }

            this.fieldValue = fieldValue;
        }

        public Range(long[]...ranges) {
            this();

            this.ranges = ranges;

            StringBuilder accumulator = new StringBuilder();
            for (int i = 0; i < this.ranges.length; i++) {
                accumulator.append(this.ranges[i][0] == -1 ? "" : Long.toString(Math.abs(this.ranges[i][0])));
                accumulator.append("-");
                accumulator.append(this.ranges[i][1] == -1 ? "" : Long.toString(Math.abs(this.ranges[i][1])));

                if (i < (this.ranges.length - 1))
                    accumulator.append(",");
            }

            this.fieldValue = accumulator.toString();
        }

        public long[][] getRange() {
            return this.ranges;
        }
    }

    public static class Referer extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        private Referer() {
            super(HttpHeader.REFERER, MultipleHeaders.NOTALLOWED);
        }

        public Referer(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        public Referer(URI uri) {
            this();
            this.fieldValue = uri.toString();
        }
    }

    public static class Server extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        private List<Product> product;
        private List<String> comment;

        private Server() {
            super(HttpHeader.SERVER, MultipleHeaders.NOTALLOWED);
            this.product = new LinkedList<Product>();
            this.comment = new LinkedList<String>();
        }

        public Server(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
            this.needsParsing = true;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            if (!this.needsParsing)
                return;
            
            List<HeaderToken> tokens = HttpHeader.parseFields(this.fieldValue, true);
            try {
                for (int i = 0; i < tokens.size(); i++) {
                    HeaderToken t = tokens.get(i);

                    if (t instanceof TokenComment) {
                        this.comment.add(t.toString());
                    } else {
                        String product = t.toString();
                        i++;
                        // The rest of this Product definition is optional
                        if (i < tokens.size()) { // the rest of this is optional
                            t = tokens.get(i);
                            if (t.equals("/")) {
                                i++;
                                t = tokens.get(i);
                                this.product.add(new Product(product, t.toString()));

                            } else {
                                this.product.add(new Product(product, null));
                                if (t instanceof TokenComment) {
                                    this.comment.add(t.toString());
                                } else {
                                    throw new HTTP.BadRequestException("Server header syntax error: " + this.fieldValue);
                                }
                            }
                        }
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                throw new HTTP.BadRequestException("Server header syntax error: " + this.fieldValue);
            }
        }
        
        public String format() {
            return this.fieldValue;            
        }

        public List<Product> getProduct() throws HTTP.BadRequestException {
            this.parse();
            return this.product;
        }
    }


    public static class Te extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        public Te() {
            super(HttpHeader.TE, MultipleHeaders.NOTALLOWED);
        }

        public Te(String fieldValue)  {
            this();
            this.fieldValue = fieldValue;
        }
    }

    public static class TimeOut extends HttpHeader implements HTTP.Message.Header.Timeout {
    	private final static long serialVersionUID = 1L;

    	private int timeout;

        public TimeOut() {
            super(HttpHeader.TIMEOUT, MultipleHeaders.NOTALLOWED);
        }

        public TimeOut(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
        
        // TimeType = ("Second-" DAVTimeOutVal | "Infinite")    ; No LWS allowed within TimeType
        // DAVTimeOutVal = 1*DIGIT

        @Override
        public void parse() {
            if (this.fieldValue.compareToIgnoreCase("Infinite") == 0) {
                this.timeout = -1;
            } else {
                this.timeout = Integer.parseInt(this.fieldValue.substring(7));
            }            
        }
        
        public int getTimeOut() {
            this.parse();
            return this.timeout;
        }
    }

    public static class TimeType extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        public TimeType() {
            super(HttpHeader.TIMETYPE, MultipleHeaders.NOTALLOWED);
        }

        public TimeType(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }
    
    public static class TransferEncoding extends HttpHeader {
        private final static long serialVersionUID = 1L;
        
        private boolean chunked;

        public TransferEncoding() {
            super(HttpHeader.TRANSFERENCODING, MultipleHeaders.NOTALLOWED);
        }

        public TransferEncoding(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public void parse() {
            this.chunked = this.fieldValue.compareToIgnoreCase("chunked") == 0 ? true : false;
        }

        public String render() {
            return this.chunked ? "chunked" : "unsupportedF";
        }
    }

    public static class UserAgent extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        private List<Product> product;
        private List<String> comment;

        private UserAgent() {
            super(HttpHeader.USERAGENT, MultipleHeaders.NOTALLOWED);
            this.product = new LinkedList<Product>();
            this.comment = new LinkedList<String>();
        }

        public UserAgent(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }

        @Override
        public void parse() throws HTTP.BadRequestException {
            List<HeaderToken> tokens = HttpHeader.parseFields(fieldValue, true);
            try {
                for (int i = 0; i < tokens.size(); i++) {
                    HeaderToken t = tokens.get(i);

                    if (t instanceof TokenComment) {
                        this.comment.add(t.toString());
                    } else {
                        String product = t.toString();
                        i++;
                        // The rest of this Product definition is optional
                        if (i < tokens.size()) { // the rest of this is optional
                            t = tokens.get(i);
                            if (t.equals("/")) {
                                i++;
                                t = tokens.get(i);
                                this.product.add(new Product(product, t.toString()));

                            } else {
                                this.product.add(new Product(product, null));
                                if (t instanceof TokenComment) {
                                    this.comment.add(t.toString());
                                } else {
                                    throw new HTTP.BadRequestException("UserAgent header syntax error: " + fieldValue);
                                }
                            }
                        }
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                throw new HTTP.BadRequestException("UserAgent header syntax error: " + fieldValue);
            }
        }

        public List<Product> getProduct() throws HTTP.BadRequestException {
            parse();
            return this.product;
        }
    }

    public static class Via extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        public Via() {
            super(HttpHeader.VIA, MultipleHeaders.NOTALLOWED);
        }

        public Via(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    public static class Warning extends HttpHeader {
    	private final static long serialVersionUID = 1L;

        public Warning() {
            super(HttpHeader.WARNING, MultipleHeaders.NOTALLOWED);
        }

        public Warning(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
    }

    public static class WWWAuthenticate extends HttpHeader {
    	private final static long serialVersionUID = 1L;
    	private HTTP.Authenticate authenticate;

        private WWWAuthenticate() {
            super(HttpHeader.WWWAUTHENTICATE, MultipleHeaders.NOTALLOWED);
        }

        public WWWAuthenticate(HTTP.Authenticate challenge) {
            this();
            this.authenticate = challenge;
            this.fieldValue = authenticate.generateChallenge();
        }

        public WWWAuthenticate(String fieldValue) {
            this();
            this.fieldValue = fieldValue;
        }
        
        public String format() {
            return (this.authenticate == null) ? this.fieldValue : this.authenticate.generateChallenge();
        }
    }

    /**
     * The syntactic construct for section 3.8 Product Tokens
     *
     */
    public static class Product {
        private String product;
        private String version;

        public Product(String product, String version) {
            this.product = product;
            this.version = version;
        }

        public String getProduct() {
            return this.product;
        }

        public String getVersion() {
            return this.version;
        }
    }

    /**
     * A token container.
     *
     */
    // XXX What is needed is a Token type which is subclassed to the various kinds of tokens like quoted-string.
    protected static class HeaderToken {
        protected String token;

        public HeaderToken(String token) {
            this.token = token;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (other == this)
                return true;
            return this.toString().equals(other.toString());
        }
        
        @Override
        public int hashCode() {
            return this.token.hashCode();
        }

        @Override
        public String toString() {
            return this.token;
        }


        private static class EntityTag extends HeaderToken {
            public EntityTag(String value) {
                super(value);
            }

            public String getEntityTag() {
                return this.token.substring(1, this.token.length() -1);
            }
        }

        /**
         * A TokenURI is expressed as the string "<" URI ">"
         * 
         *
         */
        protected static class TokenURI extends HeaderToken {            
            public TokenURI(String value) throws HTTP.BadRequestException {
                super(value);
                
                try {
                    URI u = this.getURI();
                    String scheme = u.getScheme();
                    String schemeSpecificPart = u.getSchemeSpecificPart();

                    if (scheme != null) {
                        if (scheme.compareToIgnoreCase("opaquelocktoken") == 0) {
                            // Do this to just check the syntax.
                            @SuppressWarnings("unused")
                            UUID uuid = UUID.fromString(schemeSpecificPart);
                        }
                    }
                    
                } catch (URISyntaxException e) {
                    throw new HTTP.BadRequestException(e);
                } catch (IllegalArgumentException e) {
                    throw new HTTP.BadRequestException(e);
                } finally {
                    
                }
            }
            
            public URI getURI() throws URISyntaxException {
                return new URI(this.token.substring(1, this.token.length() -1));
            }
        }
    }
    

    private static class TokenComment extends HeaderToken {
        public TokenComment(String value) {
            super(value);
        }
    }


    private static class QuotedString extends HeaderToken {
        public QuotedString(String value) {
            super(value);
        }
    }

    private static String tokenSeparators = "()<>@,;:\\\"/[]?={} \t";

    /**
     * Starting at position {@code offset} where the initial "(" character starts the comment,
     * collect the characters of the comment, including nested comments finally returning.
     *
     * @param string The input field-value as a String
     * @param openParen The ordinal position of the opening '(' character.
     * @param token The {@link StringBuilder} instance used to build up the body of the comment.
     */
    private static int parseComment(String string, int openParen, StringBuilder token) {
        return parseRecursiveToken(string, ')', openParen, token);
    }
    
    /**
     * Starting at position {@code offset} where the initial "<" character starts the string,
     * collect the characters of the string, including nested products finally returning.
     *
     * @param string The input field-value as a String
     * @param openParen The ordinal position of the opening '<' character.
     * @param token The {@link StringBuilder} instance used to build up the body of the string.
     */
    private static int parseURI(String string, int openParen, StringBuilder token) {
        return parseRecursiveToken(string, '>', openParen, token);
    }
    
    /**
     * Starting at position {@code offset} where the initial "[" character starts the string,
     * collect the characters of the string, including nested products finally returning.
     *
     * @param string The input field-value as a String
     * @param openParen The ordinal position of the opening '<' character.
     * @param token The {@link StringBuilder} instance used to build up the body of the string.
     */
    private static int parseEntityTag(String string, int openParen, StringBuilder token) {
        return parseRecursiveToken(string, ']', openParen, token);
    }

    /**
     * Parse a token recursively in which the initial character of the input string is the starting character for this token,
     * and may appear again in the input String.
     * If it does appear again, it is processed recursively.
     * <p>
     * For example, the string <code>"(a (b c) d)"</code> would comprise one token. 
     * </p>
     * @param string
     * @param terminator
     * @param offset
     * @param token
     * @return
     */
    private static int parseRecursiveToken(String string, char terminator, int offset, StringBuilder token) {
        char initiator = string.charAt(offset);
        token.append(initiator);
        int depth = 1;

        for (int i = offset + 1; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == terminator) {
                token.append(c);
                depth--;
                if (depth < 1)
                    return i;
            } else if (c == initiator) {
                token.append(c);
                depth++;
            } else {
                token.append(c);
            }
        }
        return string.length();
    }

    private static int parseQuotedString(String string, int offset, StringBuilder token) {
        token.append("\"");
        for (int i = offset + 1; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '"') {
                token.append(c);
                return i;
            } else if (c == '\\') {
                token.append(c);
                token.append(string.charAt(i));
            } else {
                token.append(c);
            }
        }
        return string.length();
    }

    /**
     * Parse an input string conforming to the HTTP header syntax rules.
     * This also parses the RFC 4918 productions for {@code Coded-URL} and {@code Resource-Tag}
     * as tokens contained within matching {@code &lt;} and {@code &gt;}, and entity-tag tokens
     * contained in matching {@code [} and {@code ]} characters.
     * 
     * @param field the value of the header field
     * @param commentAllowed True if the header is allowed to contain comments.
     * @return a TToken instance which can be subclassed to the various kinds of tokens.
     * @throws HTTP.BadRequestException if the header field cannot be parsed correctly.
     */
    private static List<HeaderToken> parseFields(String field, boolean commentAllowed) throws HTTP.BadRequestException {
        ArrayList<HeaderToken> result = new ArrayList<HeaderToken>();
        char c;

        field = field.trim();

        StringBuilder token = new StringBuilder();
        for (int i = 0 ; i < field.length(); i++) {
            c = field.charAt(i);

            if (c == '"') { // quoted-string
                String t = token.toString();
                if (t.length() > 0) { // this shouldn't be reached, this should have been handled the separator clauses below...
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
                i = parseQuotedString(field, i, token);
                result.add(new QuotedString(token.toString()));
                token = new StringBuilder();
            } else if (c == '(') { // comment
                if (!commentAllowed) {
                    throw new HTTP.BadRequestException(String.format("Comment not allowed in header fields: %s", field));
                }
                String t = token.toString();
                if (t.length() > 0) { // this shouldn't be reached, this should have been handled the separator clauses below...
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
                i = parseComment(field, i, token);
                result.add(new TokenComment(token.toString()));
                token = new StringBuilder();
            } else if (c == ' ' || c == '\t') {
                String t = token.toString();
                if (t.length() > 0) {
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
            } else if (c == '<') { // RFC 4918 If: header Coded-URL and Resource-Tag
                String t = token.toString();
                if (t.length() > 0) { // this shouldn't be reached, this should have been handled the separator clauses below...
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
                i = parseURI(field, i, token);
                result.add(new HeaderToken.TokenURI(token.toString()));
                token = new StringBuilder();
            } else if (c == '[') { // RFC 4918 If: header entity-tag
                String t = token.toString();
                if (t.length() > 0) { // this shouldn't be reached, this should have been handled the separator clauses below...
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
                i = parseEntityTag(field, i, token);
                result.add(new HeaderToken.EntityTag(token.toString()));
                token = new StringBuilder();
            } else if (tokenSeparators.indexOf(c) != -1) {
                String t = token.toString();
                if (t.length() > 0) {
                    result.add(new HeaderToken(t));
                    token = new StringBuilder();
                }
                result.add(new HeaderToken(String.format("%c", c)));
            } else {
                token.append(c);
            }
        }
        String t = token.toString();
        if (t.length() > 0) {
            result.add(new HeaderToken(t));
            token = new StringBuilder();
        }
        return result;
    }

    /**
     * From <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">RFC-2616 Section 4</a>
     * <blockquote>
     * Multiple message-header fields with the same field-name MAY be present in a message
     * if and only if the entire field-value for that header field is defined as a comma-separated
     * list [i.e., #(values)]. It MUST be possible to combine the multiple header fields into
     * one "field-name: field-value" pair, without changing the semantics of the message,
     * by appending each subsequent field-value to the first, each separated by a comma.
     * The order in which header fields with the same field-name are received is
     * therefore significant to the interpretation of the combined field value,
     * and thus a proxy MUST NOT change the order of these field values when a message is forwarded.
     * </blockquote>
     */
    protected enum MultipleHeaders {
        /**
         * Multiple instances of this header <b>are</b> allowed in one HTTP message.
         */
        ALLOWED,
        /**
         * Multiple instances of this header <b>are not</b> allowed in one HTTP message.
         */
        NOTALLOWED
    };

    protected String name;
    protected String fieldValue;
    protected MultipleHeaders multipleHeaders;
    /**
     * If true, the {@code #fieldValue} needs header specific parsing.
     */
    protected boolean needsParsing;

    private HttpHeader(String name, MultipleHeaders multipleHeaders) {
        this.name = name;
        this.fieldValue = null;
        this.multipleHeaders = multipleHeaders;
        this.needsParsing = true;
    }

    private HttpHeader(String name, Object fieldValue) {
        this(name, MultipleHeaders.NOTALLOWED);
        this.fieldValue = String.valueOf(fieldValue);
        this.needsParsing = true;
    }

    /**
     * Override the behaviour of {@link #equals(Object)} such that instances are equal if
     * their names are equal (ignoring letter case) or are of the same Class.
     * 
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (this == other)
            return true;
        if (other instanceof String) {
            return this.getName().compareToIgnoreCase((String) other) == 0;                
        }
        if (other.getClass().equals(this.getClass()))
            return true;
        
        return false;
    }

    public String getName() {
        return this.name;
    }

    public String getFieldValue() {
        return format();
    }

    public void setFieldValue(String value) {
        this.fieldValue = value;
        this.needsParsing = true;
    }

    /**
     * Return true if this header is permitted to be specified multiple times in the {@link HTTP.Message}.
     * If {@code true}, then multiple adds of this header will be included in the message.
     * If {@code false}, then the last added instance of this header will replace the previous one.
     */
    public boolean multipleHeadersAllowed() {
        return this.multipleHeaders == MultipleHeaders.ALLOWED;
    }

    /**
     * Append additional field-value data to this header.
     *
     * @param additionalFieldValue
     */
    public void append(String additionalFieldValue) {
        this.fieldValue += ", " + additionalFieldValue;
        this.needsParsing = true;
    }

    /**
     * Parse the value of this {@link HTTP.Message.Header}'s field-value into its constituent parts.
     * <p>
     * Subclasses may delay parsing of the header's field-value until the parts are needed.
     * </p>
     *
     * @throws InvalidFormatException
     */
    public void parse() throws HTTP.BadRequestException, HTTP.NotImplementedException {
        if (!this.needsParsing)
            return;

        /* Does nothing by default */
    }

    /**
     * Recompute the value of this {@link HTTP.Message.Header}'s field-value.
     * <p>
     * Subclasses may modify the constituent parts of a header which then makes
     * it necessary to recompute the value of {@link #fieldValue} before it is output.
     * </p>
     * @return  This {@code HTTP.Message.Header}'s field-value.
     */
    protected String format() {
       return this.fieldValue;
    }

    /**
     * Return a string containing a formatted representation
     * of this header.
     *
     * @return  a formatted representation of this header
     */
    @Override
    public String toString() {
        return new String(this.toByteArray());
    }

    /**
     * Return a byte array representation of this header.
     *
     * @return  a byte array representation of this header
     */
    private byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            this.writeTo(out);
        } catch (IOException cantHappen) {
            throw new RuntimeException(cantHappen);
        }
        return out.toByteArray();
    }

    private static final byte[] colonSpace = ": ".getBytes();

    public long writeTo(OutputStream out) throws IOException {
        byte[] n = this.name.getBytes();
        out.write(n, 0, n.length);
        out.write(colonSpace, 0, colonSpace.length);
        long length = n.length + colonSpace.length;

        // If the fieldValue is null, call format() to generate it.
        if (this.fieldValue == null) {
            this.format();
        }
        if (this.fieldValue != null) {
            byte[] bytes = this.fieldValue.getBytes();
            out.write(bytes, 0, bytes.length);
            length += bytes.length;
        }

        return length;
    }

    public static void main(String[] args) throws Exception {
        // If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> 
        //      ["I am an ETag"])
        //      (["I am another ETag"])
        
        // If: (Not <urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> 
        //      <urn:uuid:58f202ac-22cf-11d1-b12d-002035b29092>)
        
        // If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2>) 
        //      (Not <DAV:no-lock>)
        
        // If: </resource1> 
        //      (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> 
        //      [W/"A weak ETag"]) (["strong ETag"])
        
        // If: </specs/rfc2518.doc> (["4217"])
        
        // (<opaquelocktoken:fd140919-b550-4c05-815d-b10082bd69b2>)
        String header;
       HttpHeader.If h;
        
        header = "If: (<urn:uuid:fe184f2e-6eec-41d0-c765-01adc56e6bb4>) (<urn:uuid:e454f3f3-acdc-452a-56c7-00a5c91e4b77>)";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n%s%n%s%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }

        header = "If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> [\"I am an ETag\"]) ([\"I am another ETag\"])";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }

        header = "If: (Not <urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> <urn:uuid:58f202ac-22cf-11d1-b12d-002035b29092>)";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }
        
        header = "If: (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2>) (Not <DAV:no-lock>)";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }

        header = "If: </resource1> (<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2> [W/\"A weak ETag\"]) ([\"strong ETag\"])";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }
        header = "If: </specs/rfc2518.doc> ([\"4217\"])";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }
        

        header = "If: </specs/resource-tag-1> ([\"4217\"]) </specs/resource-tag-2> ([\"abcd\"])";
        System.out.printf("%s%n", header);
        h = (HttpHeader.If) HttpHeader.getInstance(header);
        h.parse();
        if (!h.toString().equals(header)) {
            System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
        } else {
            System.out.printf("Equal:%n%s%n%n", h.toString());
        }


        Map<URI,HTTP.Resource.State> resourceStates = new HashMap<URI,HTTP.Resource.State>();
        HTTP.Resource.State resourceState = new HTTP.Resource.State(new URI("/foo"), new HTTP.Resource.State.EntityTag("foo"));
        resourceState.add(new HTTP.Resource.State.CodedURL("<opaquelocktoken:abe0386c-dcba-45db-b5ae-000000000000>"));
        
        resourceStates.put(resourceState.getURI(), resourceState);
        
        System.out.printf("%s%n", resourceStates);
        
//        resourceState = new HTTP.Resource.State(new HTTP.Resource.State.EntityTag("foobar"));
//        resourceState.add(new HTTP.Resource.State.CodedURL("<opaquelocktoken:abe0386c-dcba-45db-b5ae-111111111111>"));
//        resourceStates.put(new URI("/foo/bar"), resourceState);
        
        {
            header = "If: (<opaquelocktoken:abe0386c-dcba-45db-b5ae-ffa66d6485a4> [1257455332293032]) (Not <DAV:no-lock>)";
            System.out.printf("%s%n", header);
            h = (HttpHeader.If) HttpHeader.getInstance(header);

            h.parse();
       
            System.out.printf("evaluate: %b%n", h.getConditional().evaluate(new URI("/foo"), resourceStates));

            if (!h.toString().equals(header)) {
                System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
            } else {
                System.out.printf("Equal:%n%s%n%n", h.toString());
            }
        }
        
        resourceStates = new HashMap<URI,HTTP.Resource.State>();
        resourceState = new HTTP.Resource.State(new URI("/foo"), new HTTP.Resource.State.EntityTag("foo"));
        resourceState.add(new HTTP.Resource.State.CodedURL("<opaquelocktoken:abe0386c-dcba-45db-b5ae-000000000000>"));
        
        resourceStates.put(resourceState.getURI(), resourceState);
        
//        resourceState = new HTTP.Resource.State(new HTTP.Resource.State.EntityTag("foobar"));
//        resourceState.add(new HTTP.Resource.State.CodedURL("<opaquelocktoken:abe0386c-dcba-45db-b5ae-111111111111>"));
//        resourceStates.put(new URI("/foo/bar"), resourceState);
        
        {
            header = "If: (<opaquelocktoken:abe0386c-dcba-45db-b5ae-ffa66d6485a4> [1257455332293032]) (Not <DAV:no-lock> [foo])";
            System.out.printf("%s%n", header);
            h = (HttpHeader.If) HttpHeader.getInstance(header);

            h.parse();
       
            System.out.printf("evaluate: %b%n", h.getConditional().evaluate(new URI("/foo"), resourceStates));

            if (!h.toString().equals(header)) {
                System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
            } else {
                System.out.printf("Equal:%n%s%n%n", h.toString());
            }
        }

        {
            header = "If: <http://127.0.0.1:8080/foo> (<opaquelocktoken:abe0386c-dcba-45db-b5ae-ffa66d6485a4> [1257455332293032]) (<DAV:no-lock> [1257455332293032])" +
                                         " </foo/bar> (<opaquelocktoken:abe0386c-dcba-45db-b5ae-ffa66d6485a4> [1257455332293032]) (<DAV:no-lock> [1257455332293032])";
            System.out.printf("%s%n", header);
            h = (HttpHeader.If) HttpHeader.getInstance(header);

            h.parse();

            System.out.printf("evaluate: %b%n", h.getConditional().evaluate(new URI("/foo/bar"), resourceStates));

            if (!h.toString().equals(header)) {
                System.out.printf("Unequal:%n'%s'%n'%s'%n", header, h.toString());
            } else {
                System.out.printf("Equal:%n%s%n%n", h.toString());
            }
        }
        
        PathName n = new PathName("/a");
        System.out.printf("%s%n", n);
        System.out.printf("'%s'%n", n.dirName());
        System.out.printf("'%s'%n", n.dirName().dirName());
        System.out.printf("%s%n", n.append("e"));
    }
}
