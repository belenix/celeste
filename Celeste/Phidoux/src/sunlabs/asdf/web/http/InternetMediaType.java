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

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Map;

/**
 * This class defines a set of common representations of RFC 2046 Internet Media Types as instances of this class.
 * See also, <a href="http://www.iana.org/assignments/media-types">The IANA registry of MIME Media Types</a>
 * <p>
 * Create additional instances via the {@link #getInstance(String)}
 * constructor which automatically adds the new instance to the global Map {@link InternetMediaType#byTypeAndSubType}
 * for subsequent reuse via the {@link #getInstance(String)} class method. 
 * </p>
 * <p>
 * Associate {@code InternetMediaType} instances with file suffix extensions via the {@link InternetMediaType#addFileExtension(InternetMediaType, String[])}
 * for later lookup via that {@link InternetMediaType#getByFileExtension(String)}
 * </p>
 * <p>
 * See {@link InternetMediaType#getInstance(String)}
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public final class InternetMediaType implements Serializable {
	private final static long serialVersionUID = 1L;
	
	/**
	 * Application Media Types
	 */
    public static final class Application {
        public static final InternetMediaType XWWWFormURLEncoded = InternetMediaType.getInstance("application/x-www-form-urlencoded");
        public static final InternetMediaType OctetStream = InternetMediaType.getInstance("application/octet-stream");
        public static final InternetMediaType Directory = InternetMediaType.getInstance("application/directory");
        public static final InternetMediaType Ogg = InternetMediaType.getInstance("application/ogg", ".ogg");
        public static final InternetMediaType Xhtml = InternetMediaType.getInstance("application/xhtml+xml", ".xhtml");
        public static final InternetMediaType Javascript = InternetMediaType.getInstance("application/javascript", ".js");
        public static final InternetMediaType XSLT = InternetMediaType.getInstance("application/xml", ".xslt");;
    }
    
    /**
     * Audio Media Types
     */
    public static final class Audio {
        public static final InternetMediaType Mpeg = InternetMediaType.getInstance("audio/mpeg", ".mp3");
        public static final InternetMediaType XMpegUrl = InternetMediaType.getInstance("audio/x-mpegurl", ".m3u");
        public static final InternetMediaType XMsWma = InternetMediaType.getInstance("audio/x-ms-wma", ".wma");
        public static final InternetMediaType XMsWax = InternetMediaType.getInstance("audio/x-ms-wax", ".wax");
    }
    
    /**
     * Example Media Types
     */
    public static final class Example {

    }

    /**
     * HTTP Media Types
     */
    public static final class Httpd {
        public static final InternetMediaType Directory = InternetMediaType.getInstance("httpd/directory");
    }

    /**
     * Image Media Types
     */
    public static final class Image {
        public static final InternetMediaType TIFF = InternetMediaType.getInstance("image/tiff", ".tiff");
        public static final InternetMediaType PNG = InternetMediaType.getInstance("image/png", ".png");
        public static final InternetMediaType Gif = InternetMediaType.getInstance("image/gif", ".gif");
        public static final InternetMediaType Jpeg = InternetMediaType.getInstance("image/jpeg", ".jpg", ".jpeg", "jpe");
        public static final InternetMediaType Icon = InternetMediaType.getInstance("image/x-ico", ".ico");
    }

    public static final class Message {
        public static final InternetMediaType CPIM = InternetMediaType.getInstance("message/plain"); // [RFC3862]
        public static final InternetMediaType DeliveryStatus = InternetMediaType.getInstance("message/delivery-status"); // [RFC1894]
        public static final InternetMediaType DispositionNotification = InternetMediaType.getInstance("message/disposition-notification"); //       [RFC2298]
        public static final InternetMediaType Example = InternetMediaType.getInstance("message/example"); // [RFC4735]
        public static final InternetMediaType ExternalBody = InternetMediaType.getInstance("message/external-body"); // [RFC2045,RFC2046]
        public static final InternetMediaType Http = InternetMediaType.getInstance("message/http"); // [RFC2616]
        public static final InternetMediaType News = InternetMediaType.getInstance("message/news"); // [RFC 1036, H.Spencer]
        public static final InternetMediaType Partial = InternetMediaType.getInstance("message/partial"); // [RFC2045,RFC2046]
        public static final InternetMediaType RFC822 = InternetMediaType.getInstance("message/rfc822"); // [RFC2045,RFC2046]
        public static final InternetMediaType SHttp = InternetMediaType.getInstance("message/s-http"); // [RFC2660]
        public static final InternetMediaType SIP = InternetMediaType.getInstance("message/sip"); // [RFC3261]
        public static final InternetMediaType SIPFrag = InternetMediaType.getInstance("message/sipfrag"); // [RFC3420]
        public static final InternetMediaType TrackingStatus = InternetMediaType.getInstance("message/tracking-status");
    }

    public static final class Model {
        public static final InternetMediaType Example = InternetMediaType.getInstance("message/example"); // [RFC4735]
        public static final InternetMediaType IGES = InternetMediaType.getInstance("message/iges"); // [Parks]
        public static final InternetMediaType Mesh = InternetMediaType.getInstance("message/mesh"); // [RFC2077]
        public static final InternetMediaType VRML = InternetMediaType.getInstance("message/vrml"); // [RFC2077]
    }

    public static final class Multipart {
        /** multipart-form-data */
        public static final InternetMediaType FormData = InternetMediaType.getInstance("multipart/form-data");
        public static final InternetMediaType Mixed = InternetMediaType.getInstance("multipart/mixed");
        public static final InternetMediaType Digest = InternetMediaType.getInstance("multipart/digest");
        public static final InternetMediaType Alternative = InternetMediaType.getInstance("multipart/alternative");
        public static final InternetMediaType Related = InternetMediaType.getInstance("multipart/related");
        public static final InternetMediaType Report = InternetMediaType.getInstance("multipart/report");
        public static final InternetMediaType Signed = InternetMediaType.getInstance("multipart/signed");
        public static final InternetMediaType Encrypted = InternetMediaType.getInstance("multipart/encrypted");
    }

    public static final class Text {
        public static final InternetMediaType Plain = InternetMediaType.getInstance("text/plain", ".txt", ".text");
        public static final InternetMediaType HTML = InternetMediaType.getInstance("text/html", ".html", ".htm");
        public static final InternetMediaType XML = InternetMediaType.getInstance("application/xml", ".xml");
        public static final InternetMediaType CSS = InternetMediaType.getInstance("text/css", ".css");
        public static final InternetMediaType CSV = InternetMediaType.getInstance("text/csv", ".csv");
        public static final InternetMediaType XSL = InternetMediaType.getInstance("application/xml", ".xsl");
    }

    public static final class Video {
        public static final InternetMediaType XMsAsf = InternetMediaType.getInstance("video/x-ms-asf", ".asf");
        public static final InternetMediaType XMsAsx = InternetMediaType.getInstance("video/x-ms-asx", ".asx");
        public static final InternetMediaType XMsWmv = InternetMediaType.getInstance("video/x-ms-wmv", ".wmv");
        public static final InternetMediaType XMsWvx = InternetMediaType.getInstance("video/x-ms-wvx", ".wvx");
        public static final InternetMediaType XMsWmx = InternetMediaType.getInstance("video/x-ms-wmx", ".wmx");
        public static final InternetMediaType Mp4 = InternetMediaType.getInstance("video/mp4", ".mp4");
        public static final InternetMediaType Mpeg = InternetMediaType.getInstance("video/mpeg");
        public static final InternetMediaType AVI = InternetMediaType.getInstance("video/x-msvideo");
        public static final InternetMediaType Quicktime = InternetMediaType.getInstance("video/quicktime", ".mov");
    }

    public static final class Wildcard {
        public static final InternetMediaType Plain = InternetMediaType.getInstance("*/*");
    }

    // The purpose of these is to simply cause the static values to be instantiated so they can be looked up.
    public static InternetMediaType init;

    /**
     * A Map of InternetMediaTypes keyed by the file extension commonly associated with the media type.
     */
    private static Map<String,InternetMediaType> byExtension;

    /**
     * A Map of InternetMediaTypes keyed by the String composite of their type and subtype.
     */
    private static Map<String,InternetMediaType> byTypeAndSubType;

    static {
        InternetMediaType.byExtension = new Hashtable<String,InternetMediaType>();
        InternetMediaType.byTypeAndSubType = new Hashtable<String,InternetMediaType>();
        // Establish the top-level application types.
        new InternetMediaType.Application();
        new InternetMediaType.Audio();
        new InternetMediaType.Example();
        new InternetMediaType.Httpd();
        new InternetMediaType.Image();
        new InternetMediaType.Message();
        new InternetMediaType.Model();
        new InternetMediaType.Multipart();
        new InternetMediaType.Text();
        new InternetMediaType.Video();
        new InternetMediaType.Wildcard();
    }

    /**
     * Get the {@link InternetMediaType} defined for the specified file name extension.
     *
     * @param fileExtension
     */
    public static InternetMediaType getByFileExtension(String fileExtension) {
        return InternetMediaType.byExtension.get(fileExtension);
    }

    /**
     * Get the file name <em>extension</em> from the given filename.
     * <p>
     * The extension is the String of characters after the last '.' character in the given name.
     * </p>
     */
    public static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1)
            return "";
        return fileName.substring(dot);
    }

    /**
     * Add an association of the Strings in {@code strings} to the {@link InternetMediaType} {@code type}.
     * <p>
     * </p>
     * @param type An {@link InternetMediaType}.
     * @param strings An array of strings, each element to be a key in subsequent lookup returning {@code type}.
     */
    public static InternetMediaType addFileExtension(InternetMediaType type, String[] strings) {
        if (strings != null) {
            for (String extension : strings) {
                InternetMediaType.byExtension.put(extension, type);
            }
        }
        return type;
    }

    public static void main(String[] args) {
        InternetMediaType type = InternetMediaType.getByFileExtension(".txt");
        System.out.println("type: " + type);
    }

    /**
     * Get the {@link InternetMediaType} instance specified by the given <em>type</em> and <em>sub-type</em>.
     * <p>
     * If there is no corresponding instance, create it, add it to the global set of InternetMediaTypes for later use, and return it.
     * </p>
     *
     * @param typeSubType
     * 
     * @see InternetMediaType#byTypeAndSubType
     * @see InternetMediaType#addFileExtension(InternetMediaType, String[])
     */
    public static InternetMediaType getInstance(String typeSubType) {
        if (typeSubType == null) {
            throw new NullPointerException("typeSubType are null");
        }
        InternetMediaType result = InternetMediaType.byTypeAndSubType.get(typeSubType);
        if (result == null) {
            result = new InternetMediaType(typeSubType);
            InternetMediaType.byTypeAndSubType.put(typeSubType, result);
        }
        return result;
    }

    public static InternetMediaType getInstance(String typeSubType, String...fileExtensions) {
        InternetMediaType result = InternetMediaType.getInstance(typeSubType);
        InternetMediaType.addFileExtension(result, fileExtensions);
        return result;
    }

    private String type;

    private InternetMediaType(String typeSubType) {
        this.type = typeSubType;
    }

    /**
     * Compare equality of another object.
     *
     * It is only necessary that the String representations of this and the other object be equal.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
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
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return this.type;
    }
}
