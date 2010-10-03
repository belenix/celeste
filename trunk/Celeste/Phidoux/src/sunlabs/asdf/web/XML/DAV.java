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
package sunlabs.asdf.web.XML;

import sunlabs.asdf.web.http.HTTP;

/**
 * From rfc2518
 * 
 * <!DOCTYPE webdav-1.0 [
 *
 * <!--============ XML Elements from Section 12 ==================-->
 *
 * <!ELEMENT activelock (lockscope, locktype, depth, owner?, timeout?, locktoken?) >
 *
 *  <!ELEMENT lockentry (lockscope, locktype) >
 *  <!ELEMENT lockinfo (lockscope, locktype, owner?) >
 *
 *   <!ELEMENT locktype (write) >
 *   <!ELEMENT write EMPTY >
 *
 *   <!ELEMENT lockscope (exclusive | shared) >
 *   <!ELEMENT exclusive EMPTY >
 *   <!ELEMENT shared EMPTY >
 *
 *   <!ELEMENT depth (#PCDATA) >
 *
 *   <!ELEMENT owner ANY >
 *
 *   <!ELEMENT timeout (#PCDATA) >
 *
 *   <!ELEMENT locktoken (href+) >
 *
 *   <!ELEMENT href (#PCDATA) >
 *
 *   <!ELEMENT link (src+, dst+) >
 *   <!ELEMENT dst (#PCDATA) >
 *   <!ELEMENT src (#PCDATA) >
 *
 *   <!ELEMENT multistatus (response+, responsedescription?) >
 *
 *   <!ELEMENT response (href, ((href*, status)|(propstat+)), responsedescription?) >
 *   <!ELEMENT status (#PCDATA) >
 *   <!ELEMENT propstat (prop, status, responsedescription?) >
 *   <!ELEMENT responsedescription (#PCDATA) >
 *
 *   <!ELEMENT prop ANY >
 *  
   <!ELEMENT propertybehavior (omit | keepalive) >
   <!ELEMENT omit EMPTY >

   <!ELEMENT keepalive (#PCDATA | href+) >

   <!ELEMENT propertyupdate (remove | set)+ >
   <!ELEMENT remove (prop) >
   <!ELEMENT set (prop) >

   <!ELEMENT propfind (allprop | propname | prop) >
   <!ELEMENT allprop EMPTY >
   <!ELEMENT propname EMPTY >

   <!ELEMENT collection EMPTY >

   <!--=========== Property Elements from Section 13 ===============-->
   <!ELEMENT creationdate (#PCDATA) >
   <!ELEMENT displayname (#PCDATA) >
   <!ELEMENT getcontentlanguage (#PCDATA) >
   <!ELEMENT getcontentlength (#PCDATA) >
   <!ELEMENT getcontenttype (#PCDATA) >
   <!ELEMENT getetag (#PCDATA) >
   <!ELEMENT getlastmodified (#PCDATA) >
   <!ELEMENT lockdiscovery (activelock)* >
   <!ELEMENT resourcetype ANY >
   <!ELEMENT source (link)* >
   <!ELEMENT supportedlock (lockentry)* >
   ]>

  For the moment the namespace is part of the element names below.

 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class DAV implements XML.ElementFactory {
    /**
     * <ul>
     * <li>15.1  creationdate</li>
     * <li>15.2  displayname</li>
     * <li>15.3  getcontentlanguage</li>
     * <li>15.4  getcontentlength</li>
     * <li>15.5  getcontenttype</li>
     * <li>15.6  getetag</li>
     * <li>15.7  getlastmodified</li>
     * <li>15.8  lockdiscovery</li>
     * <li>15.9  resourcetype</li>
     * <li>15.10 supportedlock</li>
     * </ul>
     */
    public static final class Property {
        public final static String CREATIONDATE = "creationdate";
        public final static String DISPLAYNAME = "displayname";
        public final static String GETCONTENTLANGUAGE = "getcontentlanguage";
        public final static String GETCONTENTTYPE = "getcontenttype";
        public final static String GETETAG = "getetag";
        public final static String GETLASTMODIFIED = "getlastmodified";
        public final static String LOCKDISCOVERY = "lockdiscovery";
        public final static String RESOURCETYPE = "resourcetype";
        public final static String SUPPORTEDLOCK = "supportedlock";
    }
    
    private XML.NameSpace nameSpacePrefix;
    private long nameSpaceReferenceCount;
    
    /**
     * Construct a new DAV XML factory using the default {@link XML.NameSpace} specification {@code xmlns:dav="DAV:"}
     */
    public DAV() {
        this(new XML.NameSpace("dav", "DAV:"));
    }
    
    /**
     * Construct a new DAV content factory using the given {@link XML.NameSpace} specification.
     * <p>
     * This will create elements within the given XML name-space.
     * </p>
     * @param nameSpacePrefix
     */
    public DAV(XML.NameSpace nameSpacePrefix) {
        this.nameSpacePrefix = nameSpacePrefix;
        this.nameSpaceReferenceCount = 0;
    }
    
    /**
     * Return a count of the number of times this content generator created an element.
     * 
     */
    public long getReferenceCount() {
        return this.nameSpaceReferenceCount;
    }
    
    /**
     * Set the count of the number of time this content generator created an element.
     * @param value the new reference count for this generator.
     */
    public void setReferenceCount(long value) {
        this.nameSpaceReferenceCount = value;
    }
    
    public XML.NameSpace getNameSpace() {
        return this.nameSpacePrefix;
    }
    
    /**
     * Increment the reference count for this XML generator's name-space,
     * and return the value of the name-space.
     * @return this {@link XML.NameSpace}.
     */
    public XML.NameSpace incrementNameSpaceReference() {
        this.nameSpaceReferenceCount++;
        return this.nameSpacePrefix;
    }
    
    /**
     * Create a new DAV {@code activelock} element.
     * 
     * All parameters that are <em>not</em> an instance of {@link XML.Attribute} or {@link ActiveLock.SubElement} are ignored.
     */
    public DAV.ActiveLock newActiveLock(Object...objects) {
        DAV.ActiveLock result = new DAV.ActiveLock();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof ActiveLock.SubElement) {
                result.append((ActiveLock.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Allprop newAllprop(XML.Attribute... attributes) {
        DAV.Allprop result = new DAV.Allprop(attributes);
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.AppleDoubleHeader newAppleDoubleHeader(Object...objects) {
        DAV.AppleDoubleHeader result = new DAV.AppleDoubleHeader();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Collection newCollection(XML.Attribute... attributes) {
        DAV.Collection result = new DAV.Collection(attributes);
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Depth newDepth(Object...objects) {
        DAV.Depth result = new DAV.Depth();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof Depth.Level) {
                result.add((Depth.Level) o);
            } else if (o instanceof HTTP.Message.Header.Depth.Level) {
                HTTP.Message.Header.Depth.Level level = (HTTP.Message.Header.Depth.Level) o; 
                if (level == HTTP.Message.Header.Depth.Level.ALL) {
                    result.add(Depth.Level.ALL);
                } else  if (level == HTTP.Message.Header.Depth.Level.ONLY) {
                    result.add(Depth.Level.ONLY);
                } else  if (level == HTTP.Message.Header.Depth.Level.INFINITY) {
                    result.add(Depth.Level.INFINITY);
                }
            } else {
                throw new IllegalArgumentException(String.format("mismatched argument %s", o.getClass()));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Href newHref(Object...objects) {
        DAV.Href result = new DAV.Href();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else {
                result.add(String.valueOf(o).replaceAll("/+", "/"));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.LockDiscovery newLockDiscovery(Object...objects) {
        DAV.LockDiscovery result = new DAV.LockDiscovery();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof LockDiscovery.SubElement) {
                result.append((LockDiscovery.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.LockInfo newLockInfo(Object...objects) {
        DAV.LockInfo result = new DAV.LockInfo();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof LockInfo.SubElement) {
                result.append((LockInfo.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.LockRoot newLockRoot(Object...objects) {
        DAV.LockRoot result = new DAV.LockRoot();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof LockToken.SubElement) {
                result.append((LockToken.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.LockScope newLockScope(LockScope.SubElement o) {
        DAV.LockScope result = new DAV.LockScope(o);
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.LockToken newLockToken(Object...objects) {
        DAV.LockToken result = new DAV.LockToken();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof LockToken.SubElement) {
                result.append((LockToken.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.LockType newLockType(Object...objects) {
        DAV.LockType result = new DAV.LockType();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof LockType.SubElement) {
                result.append((LockType.SubElement) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.GetContentLength newGetContentLength(Object...objects) {
        DAV.GetContentLength result = new DAV.GetContentLength();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.GetContentType newGetContentType(Object...objects) {
        DAV.GetContentType result = new DAV.GetContentType();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.GetLastModified newGetLastModified(Object...objects) {
        DAV.GetLastModified result = new DAV.GetLastModified();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    /**
     * Create a {@link DAV.GetLastModified} property.
     * <p>
     * 
     * rfc1123-date (defined in Section 3.3.1 of [RFC2616]) : Sun, 06 Nov 1994 08:49:37 GMT
     * </p>
     * @param time
     */
    public DAV.GetLastModified newGetLastModified(long time) {
        DAV.GetLastModified result = new DAV.GetLastModified();
        result.factory = this;
        result.add(String.format("%1$ta, %1$td %1$tb %1$tY %1$tT GMT", time));
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.GetCreationTime newGetCreationTime(long time) {
        DAV.GetCreationTime result = new DAV.GetCreationTime();
        result.factory = this;
        result.add(String.format("%1$ta, %1$td %1$tb %1$tY %1$tT GMT", time));
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public XML.Node newElement(String name) {
        XML.Node result = new XML.Node(name, XML.Node.EndTagDisposition.ABBREVIABLE);
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    /**
     * Construct a new {@link DAV.Exclusive} element. 
     */
    public DAV.Exclusive newExclusive() {
        DAV.Exclusive result = new DAV.Exclusive();
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Include newInclude(Object...objects) {
        DAV.Include result = new DAV.Include();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }    
    
    /**
     * Factory method to create a new {@link DAV.MultiStatus} instance.
     * <p>
     * Interpret the variable number of parameters as either instances of
     * {@link XML.Attribute} or of {@link DAV.MultiStatus.SubElement}
     * </p>
     */
    public DAV.MultiStatus newMultiStatus(Object...objects) {
        DAV.MultiStatus result = new DAV.MultiStatus();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.MultiStatus.SubElement) {
                result.add((DAV.MultiStatus.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.getClass().toString() + " " + o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    

    public DAV.Owner newOwner(Object...objects) {
        DAV.Owner result = new DAV.Owner();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else {
                result.add(String.valueOf(o).replaceAll("/+", "/"));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Prop newProp(Object...objects) {
        DAV.Prop result = new DAV.Prop();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.PropertyUpdate newPropertyUpdate(Object...objects) {
        DAV.PropertyUpdate result = new DAV.PropertyUpdate();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Propfind.SubElement) {
                result.add((DAV.PropertyUpdate.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Propfind newPropfind(Object...objects) {
        DAV.Propfind result = new DAV.Propfind();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Propfind.SubElement) {
                result.add((DAV.Propfind.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Propstat newPropstat(Object...objects) {
        DAV.Propstat result = new DAV.Propstat();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Propstat.SubElement) {
                result.add((DAV.Propstat.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Quota newQuota(Object...objects) {
        DAV.Quota result = new DAV.Quota();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.QuotaAvailableBytes newQuotaAvailableBytes(Object...objects) {
        DAV.QuotaAvailableBytes result = new DAV.QuotaAvailableBytes();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.QuotaUsed newQuotaUsed(Object...objects) {
        DAV.QuotaUsed result = new DAV.QuotaUsed();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.QuotaUsedBytes newQuotaUsedBytes(Object...objects) {
        DAV.QuotaUsedBytes result = new DAV.QuotaUsedBytes();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.Remove newRemove(Object...objects) {
        DAV.Remove result = new DAV.Remove();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Propstat.SubElement) {
                result.add((DAV.Remove.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.ResourceType newResourceType(Object...objects) {
        DAV.ResourceType result = new DAV.ResourceType();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof XML.Content) {
                result.add((XML.Content) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Response newResponse(Object...objects) {
        DAV.Response result = new DAV.Response();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Response.SubElement) {
                result.add((DAV.Response.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.getClass().toString() + " " + o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Set newSet(Object...objects) {
        DAV.Set result = new DAV.Set();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else if (o instanceof DAV.Propstat.SubElement) {
                result.add((DAV.Set.SubElement) o);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    /**
     * Construct a new {@link DAV.Shared} element. 
     */
    public DAV.Shared newShared() {
        DAV.Shared result = new DAV.Shared();
        result.factory = this;
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public DAV.Status newStatus(Object...objects) {
        DAV.Status result = new DAV.Status();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.Timeout newTimeout(Object...objects) {
        DAV.Timeout result = new DAV.Timeout();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            } else {
                result.add(String.valueOf(o));
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }

    public DAV.Write newWrite(Object...objects) {
        DAV.Write result = new DAV.Write();
        result.factory = this;
        for (Object o : objects) {
            if (o instanceof XML.Attribute) {
                result.addAttribute((XML.Attribute) o);
            }
        }
        result.setNameSpace(this.incrementNameSpaceReference());
        return result;
    }
    
    public static class AppleDoubleHeader extends XML.Node  {
        private static final long serialVersionUID = 1L;

        public static final String name = "appledoubleheader";
        
        public AppleDoubleHeader() {
            super(AppleDoubleHeader.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public AppleDoubleHeader(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public AppleDoubleHeader(XML.Content... content) {
            this();
            this.add(content);
        }
        
        public AppleDoubleHeader(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public AppleDoubleHeader add(XML.Content... content) {
            super.append(content);
            return this;
        }

        public AppleDoubleHeader add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class Collection extends XML.Node {
        private static final long serialVersionUID = 1L;
        
        public static final String name = "collection";
        
        public Collection() {
            super(Collection.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Collection(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }

    public static class Depth extends XML.Node implements ActiveLock.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "depth";
        
        public interface SubElement extends XML.Content {}
        
        public static enum Level {
            /**
             * Corresponds to the WebDAV Depth header of 0 which signals that the
             * operation applies only to the named resource.
             */
            ONLY {
                public String toString() {
                    return "0";
                }
            },
            /**
             * Corresponds to the WebDAV Depth header of 1 which signals that the
             * operation applies to the named resource and its internal members.
             */
            ALL {
                public String toString() {
                    return "1";
                }
            },
            /**
             * Corresponds to the WebDAV Depth header of 'infinity' which signals
             * that the operation applies to the named resource and all of its members.
             */
            INFINITY {
                public String toString() {
                    return "infinity";
                }
            }
        };
        
        private Depth() {
            super(Depth.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public Depth(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Depth(Depth.Level level) {
            this();
            this.add(level.toString());
        }

        private Depth add(Object...content) {
            this.addCDATA(content);
            return this;
        }     
    }

    public static class ActiveLock extends XML.Node implements LockDiscovery.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "activelock";
        
        public ActiveLock(XML.ElementFactory factory) {
            this.factory = factory;            
        }
        
        public ActiveLock() {
            super(ActiveLock.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public ActiveLock(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public ActiveLock(XML.Content...content) {
            this();
            this.add(content);
        }

        public ActiveLock add(XML.Content...content) {
            super.append(content);
            return this;
        }     
    }
    
    public static class Allprop extends XML.Node implements Propfind.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "allprop";
        
        public Allprop() {
            super(Allprop.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }
        
        public Allprop(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }    

    public static class MultiStatus extends XML.Node implements XML.Content {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "multistatus";
        
        public MultiStatus() {
            super(MultiStatus.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public MultiStatus(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public MultiStatus add(MultiStatus.SubElement... content) {
            super.append(content);
            return this;
        }
    }    

    public static class Owner extends XML.Node implements ActiveLock.SubElement, LockInfo.SubElement {
        private static final long serialVersionUID = 1L;
        
        public static final String name = "owner";
        
        public Owner() {
            super(Owner.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public Owner(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Owner(Object...content) {
            this();
            this.add(content);
        }

        public Owner add(Object...cdata) {
            super.addCDATA(cdata);
            return this;
        }     
    }
    
    public static class ResourceType extends XML.Node implements Response.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "resourcetype";
        
        public ResourceType() {
            super(ResourceType.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public ResourceType(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public ResourceType(XML.Content... content) {
            this();
            this.add(content);
        }
        
        public ResourceType(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public ResourceType add(XML.Content... content) {
            super.append(content);
            return this;
        }

        public ResourceType add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class Remove extends XML.Node implements PropertyUpdate.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "remove";
        
        public Remove() {
            super(Remove.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Remove(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Remove(Remove.SubElement... content) {
            this();
            this.add(content);
        }
        
        public Remove add(Remove.SubElement... content) {
            super.append(content);
            return this;
        }
    }

    /**
     * &lt;!ELEMENT response (href, ((href*, status)|(propstat+)), error?, responsedescription? , location?) &gt;
     *
     *
     */
    public static class Response extends XML.Node implements MultiStatus.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "response";
        
        public Response() {
            super(Response.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Response(Response.SubElement...content){
            this();
            this.add(content);
        }
        
        public Response(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Response add(Response.SubElement...content) {
            super.append(content);
            return this;
        }
    }
    
    /**
     * See <a href="http://www.webdav.org/specs/rfc4918.html#ELEMENT_responsedescription">responsedescription</a>
     */
    public static class ResponseDescription extends XML.Node implements MultiStatus.SubElement, Response.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "responsedescription";

        public ResponseDescription() {
            super(ResponseDescription.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public ResponseDescription(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public ResponseDescription(Object... cdata) {
            this();
            this.add(cdata);
        }

        public ResponseDescription add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }

    public static class Href extends XML.Node implements Response.SubElement, LockToken.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "href";
        
        public Href() {
            super(Href.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Href(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Href(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Href add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }

    /**
     * From RFC 4918 <cite><a href="http://webdav.org/specs/rfc4918.html#ELEMENT_exclusive">14.6 exclusive XML Element</a></cite>
     * <p>
     * Specifies an exclusive lock.
     * </p>
     */
    public static class Exclusive extends XML.Node implements LockScope.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "exclusive";
        
        public Exclusive() {
            super(Exclusive.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Exclusive(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }
    
    public static class GetContentLength extends XML.Node implements Response.SubElement {
        private static final long serialVersionUID = 1L;
        
        public static final String name = "getcontentlength";
        
        public GetContentLength() {
            super(GetContentLength.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public GetContentLength(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public GetContentLength(XML.Content...content) {
            this();
            this.add(content);
        }
        
        public GetContentLength(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public GetContentLength add(XML.Content...content) {
            super.append(content);
            return this;
        }

        public GetContentLength add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class GetContentType extends XML.Node implements Response.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "getcontenttype";

        public GetContentType() {
            super(GetContentType.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }

        public GetContentType(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }

        public GetContentType(XML.Content...content) {
            this();
            this.add(content);
        }

        public GetContentType(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public GetContentType add(XML.Content...content) {
            super.append(content);
            return this;
        }

        public GetContentType add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }

   public static class GetCreationTime extends XML.Node implements Response.SubElement {
       private static final long serialVersionUID = 1L;
       public static final String name = Property.CREATIONDATE;

       public GetCreationTime() {
           super(GetCreationTime.name, XML.Node.EndTagDisposition.ABBREVIABLE);
       }

       public GetCreationTime(XML.Attribute... attributes) {
           this();
           this.addAttribute(attributes);
       }

       public GetCreationTime(XML.Content... content) {
           this();
           this.add(content);
       }

       public GetCreationTime(Object... cdata) {
           this();
           this.addCDATA(cdata);
       }

       public GetCreationTime add(XML.Content... content) {
           super.append(content);
           return this;
       }

       public GetCreationTime add(Object... cdata) {
           super.addCDATA(cdata);
           return this;
       }
   }
    
    public static class GetLastModified extends XML.Node implements Response.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = Property.GETLASTMODIFIED;
        
        public GetLastModified() {
            super(GetLastModified.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public GetLastModified(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public GetLastModified(XML.Content... content) {
            this();
            this.add(content);
        }
        
        public GetLastModified(Object... cdata) {
            this();
            this.addCDATA(cdata);
        }

        public GetLastModified add(XML.Content... content) {
            super.append(content);
            return this;
        }

        public GetLastModified add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
//    public static class Include extends XML.Node implements Propfind.SubElement {
//        private static final long serialVersionUID = 1L;
//        
//        public static final String name = "include";
//        
//        public Include() {
//            super(Include.name, XML.Node.EndTagDisposition.ABBREVIABLE);
//        }
//        
//        public Include(XML.Attribute...attributes) {
//            this();
//            this.addAttribute(attributes);
//        }
//        
//        public Include(XML.Content...content) {
//            this();
//            this.add(content);
//        }
//
//        public Include add(XML.Content...content) {
//            super.append(content);
//            return this;
//        }
//    }

    public static class Include extends XML.Node implements Propfind.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "include";
        
        public Include() {
            super(Include.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Include(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Include(XML.Content... content) {
            this();
            this.add(content);
        }
        
        public Include add(XML.Content... content) {
            super.append(content);
            return this;
        }
    }
    
    public static class LockDiscovery extends XML.Node implements Prop.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "lockdiscovery";
        
        public LockDiscovery() {
            super(LockDiscovery.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockDiscovery(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockDiscovery(LockDiscovery.SubElement...content) {
            this();
            this.add(content);
        }

        public LockDiscovery add(LockDiscovery.SubElement...content) {
            super.append(content);
            return this;
        }     
    }

    public static class LockInfo extends XML.Node {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "lockinfo";
        
        public LockInfo() {
            super(LockInfo.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockInfo(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockInfo(LockInfo.SubElement...content) {
            this();
            this.append(content);
        }

        public LockInfo append(LockInfo.SubElement...content) {
            super.append(content);
            return this;
        }     
    }

    public static class LockRoot extends XML.Node implements ActiveLock.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "lockroot";
        
        public LockRoot() {
            super(LockRoot.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockRoot(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockRoot(LockRoot.SubElement...content) {
            this();
            this.add(content);
        }

        public LockRoot add(LockRoot.SubElement...content) {
            super.append(content);
            return this;
        }     
    }
    
    public static class LockScope extends XML.Node implements ActiveLock.SubElement, LockInfo.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "lockscope";
        
        private LockScope() {
            super(LockScope.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockScope(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockScope(LockScope.SubElement...content) {
            this();
            this.add(content);
        }

        public LockScope add(LockScope.SubElement...content) {
            super.append(content);
            return this;
        }     
    }

    public static class LockToken extends XML.Node implements ActiveLock.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "locktoken";
        
        public LockToken() {
            super(LockToken.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockToken(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockToken(LockToken.SubElement...content) {
            this();
            this.add(content);
        }

        public LockToken add(LockToken.SubElement...content) {
            super.append(content);
            return this;
        }     
    }
    
    public static class LockType extends XML.Node implements ActiveLock.SubElement, LockInfo.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "locktype";
        
        public LockType() {
            super(LockType.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public LockType(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public LockType(LockType.SubElement...content) {
            this();
            this.add(content);
        }

        public LockType add(LockType.SubElement...content) {
            super.append(content);
            return this;
        }     
    }

    public static class Propfind extends XML.Node implements Propstat.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "propfind";
        
        public Propfind() {
            super(Propfind.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Propfind(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Propfind(Propfind.SubElement... content) {
            this();
            this.add(content);
        }
        
        public Propfind add(Propfind.SubElement... content) {
            super.append(content);
            return this;
        }
    }
    
    public static class Propstat extends XML.Node implements Response.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "propstat";
        
        public Propstat() {
            super(Propstat.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Propstat(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Propstat(Propstat.SubElement... content) {
            this();
            this.add(content);
        }
        
        public Propstat add(Propstat.SubElement... content) {
            super.append(content);
            return this;
        }
    }
    
    public static class Prop extends XML.Node implements Propstat.SubElement, Propfind.SubElement, Set.SubElement, Remove.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "prop";
        
        public Prop() {
            super(Prop.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Prop(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Prop(XML.Content... content) {
            this();
            this.add(content);
        }
        
        public Prop add(XML.Content... content) {
            super.append(content);
            return this;
        }
    }

    public static class PropertyUpdate extends XML.Node {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "propertyupdate";
        
        public PropertyUpdate() {
            super(PropertyUpdate.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public PropertyUpdate(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public PropertyUpdate(PropertyUpdate.SubElement... content) {
            this();
            this.add(content);
        }
        
        public PropertyUpdate add(PropertyUpdate.SubElement... content) {
            super.append(content);
            return this;
        }
    }
    
    public static class Propname extends XML.Node {
        private static final long serialVersionUID = 1L;

        public static final String name = "propname";
        
        public Propname() {
            super(Propname.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Propname(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }
    
    /*
    <D:/>
    <D:quota/>
    <D:quotaused/>
    */
    public static class Quota extends XML.Node implements Prop.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "quota";
        
        public Quota() {
            super(Quota.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public Quota(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Quota(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Quota add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class QuotaAvailableBytes extends XML.Node implements Prop.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "quota-available-bytes";
        
        public QuotaAvailableBytes() {
            super(QuotaAvailableBytes.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public QuotaAvailableBytes(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public QuotaAvailableBytes(Object... cdata) {
            this();
            this.add(cdata);
        }

        public QuotaAvailableBytes add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class QuotaUsed extends XML.Node implements Prop.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "quotaused";
        
        public QuotaUsed() {
            super(QuotaUsed.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public QuotaUsed(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public QuotaUsed(Object... cdata) {
            this();
            this.add(cdata);
        }

        public QuotaUsed add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class QuotaUsedBytes extends XML.Node implements Prop.SubElement {
        private static final long serialVersionUID = 1L;
        
        public static final String name = "quota-used-bytes";
        
        public QuotaUsedBytes() {
            super(QuotaUsedBytes.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public QuotaUsedBytes(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public QuotaUsedBytes(Object... cdata) {
            this();
            this.add(cdata);
        }

        public QuotaUsedBytes add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class Set extends XML.Node implements PropertyUpdate.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "set";
        
        public Set() {
            super(Set.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Set(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Set(Set.SubElement... content) {
            this();
            this.add(content);
        }
        
        public Set add(Set.SubElement... content) {
            super.append(content);
            return this;
        }
    }
    

    public static class Shared extends XML.Node implements DAV.LockScope.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "shared";
        
        public Shared() {
            super(Shared.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Shared(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }
    
    public static class Status extends XML.Node implements Propstat.SubElement, Response.SubElement {
        private static final long serialVersionUID = 1L;
        
        public static final String name = "status";
        
        public Status() {
            super(Status.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public Status(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Status(Object... cdata) {
            this();
            this.add(cdata);
        }

        public Status add(Object... cdata) {
            super.addCDATA(cdata);
            return this;
        }
    }
    
    public static class Timeout extends XML.Node implements ActiveLock.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}
        
        public static final String name = "timeout";
        
        public Timeout() {
            super(Timeout.name, XML.Node.EndTagDisposition.ABBREVIABLE);
        }
        
        public Timeout(XML.Attribute...attributes) {
            this();
            this.addAttribute(attributes);
        }
        
        public Timeout(Object...content) {
            this();
            this.addCDATA(content);
        }

        public Timeout add(Object...content) {
            super.addCDATA(content);
            return this;
        }     
    }
    
    public static class Write extends XML.Node implements LockType.SubElement {
        private static final long serialVersionUID = 1L;

        public static final String name = "write";
        
        public Write() {
            super(Write.name, XML.Node.EndTagDisposition.FORBIDDEN);
        }

        public Write(XML.Attribute... attributes) {
            this();
            this.addAttribute(attributes);
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        
        DAV x = new DAV(new XML.NameSpace(""));
        XML.Node node = x.newElement("nonamespace");
        node.addCDATA("randomvalue");
//        node.removeAttribute(node.getNameSpace());
        System.out.printf("nonamespace= %s%n", node.toString());
        
        
//        DAV x = new DAV(new XML.NameSpace("http://example.com/neon/litmus/"));
//        XML.Node node = x.newElement("foo");
//        node.setAttribute(node.getNameSpace());
//        System.out.printf("%s%n", node.toString());
    }
}
