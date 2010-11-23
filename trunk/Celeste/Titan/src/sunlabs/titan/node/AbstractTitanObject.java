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
package sunlabs.titan.node;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.DeleteableObject;
import sunlabs.titan.util.OrderedProperties;

/**
 * The abstract class implementation of the {@link TitanObject} interface.
 * Object implementors extend this class to take care of the basic behaviours of a {@code BeehiveObject}.
 * <p>
 * Most data objects available from the Beehive object pool are stored in the backing-store of some
 * of the nodes comprising the system. Other data objects available from the pool are synthetic and are not
 * located in a node's backing store but are nevertheless published by a node and are available from the node
 * as an ephemeral or dynamic object.
 *  * </p>
 * <p>
 * Provisional Comments
 * </p>
 * <p>
 * All objects have an {@link TitanGuid} object identifier that names the object (see {@link #getObjectId()})
 * An object's identifier is derived in one of three mutually exclusive ways:
 * </p>
 * <ol>
 * <li> Data attestation - The object's identifier is derived from the content of the object.
 *      This is suitable for the vast majority of objects.
 * <ul>
 *  <li>The object's meta-data contains a {@code ObjectStore.ObjectId} which is the hash of the
 *      {@code ObjectStore.DeleteTokenHash} and the
 *      {@code ObjectStore.DataHash} properties.</li>
 *  <li>The object's meta-data contains a {@code ObjectStore.DataHash}
 *      property which is the value returned from the object's {@link #getDataId()} method.</li>
 *  <li>The object's meta-data contains a {@code ObjectStore.DeleteTokenHash} property which is the hash
 *      of the (secret) Delete Token.</li>
 * </ul>
 * </li>
 * <li> Signature(Hash) attestation -The object's identifier is derived from a combination of hashes and signatures.
 * <ul>
 *   <li>The object's meta-data contains a {@code ObjectStore.ObjectId} which is explicitly specified.</li>
 *   <li>The object's meta-data contains a
 *       {@code ObjectStore.DeleteTokenHash} property which is the hash of the (secret) Delete Token.</li>
 *   <li>The object's meta-data contains a {@code ObjectStore.DataHash} property which is the hash of the first N bytes of the (original)
 *       object's data.</li>
 *   <li>The object's meta-data also contains a {@code ObjectStore.Voucher} which is a signature/hash of the
 *       {@code ObjectStore.DeleteTokenHash}, the object-id, and the {@code ObjectStore.DataHash} properties.</li>
 * </ul>
 * </li>
 * <li> Delete-Token attestation - The object's identifier is derived from a combination of hashes, one of which is the delete-token.
 * <ul>
 * <li>The object's meta-data contains an explicitly set {@code ObjectStore.ObjectId}.</li>
 * <li>The object's meta-data contains a {@code ObjectStore.DataHash} property which is the hash of the
 *     first N bytes of the (original) object's data.</li>
 * <li>The object's meta-data also contains an {@code ObjectStore.Voucher} which is a signature/hash of the
 *     {@code ObjectStore.DeleteTokenHash}, the object-id, and the {@code ObjectStore.DataHash} properties.</li>
 * <li>The object's meta-data contains the {@code ObjectStore.DeleteToken} as the exposed Delete Token</li>
 * </ul>
 * </li>
 * </ol>
 *
 */
public abstract class AbstractTitanObject implements TitanObject {
    private static final long serialVersionUID = 1L;

    public static final class Metadata extends OrderedProperties implements TitanObject.Metadata {
        private static final long serialVersionUID = 1L;

        public static final Metadata NONE = null;

        public Metadata() {
            super();
        }

        public Metadata(InputStream is) throws IOException {
            this();
            super.load(is);
        }

        public void store(OutputStream out) throws IOException {
            super.store(out, "");
        }

        @Override
        public Metadata setProperty(String name, Object value) {
            super.setProperty(name, value);
            return this;
        }

        public void putAll(TitanObject.Metadata meta) {
            if (meta != null && meta != AbstractTitanObject.Metadata.NONE) {
                for (Iterator<Object> i = meta.keySet().iterator(); i.hasNext(); /**/) {
                    String key = (String) i.next();
                    this.setProperty(key, meta.getProperty(key, null));
                }
            }
        }

        public String getProperty(String name, Object defaultValue) {
            return super.getProperty(name, defaultValue == null ? null : String.valueOf(defaultValue));
        }

        public XHTML.Table toXHTMLTable(URI uri, Map<String,HTTP.Message> props) {
        	XHTML.Table.Caption caption = new XHTML.Table.Caption("Beehive Object Metadata");
            XHTML.Table.Head thead = new XHTML.Table.Head(new XHTML.Table.Row(new XHTML.Table.Data("Attribute"), new XHTML.Table.Data("Value")));
            XHTML.Table.Body tbody = new XHTML.Table.Body();
            
            for (Object key : new TreeSet<Object>(this.keySet())) {
                tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(key),
                        new XHTML.Table.Data((Object) this.getProperty(key.toString()))));
            }
            return new XHTML.Table(caption, thead, tbody).setClass("MetaData");
        }

        public Metadata.XMLMetadataFactory.XMLMetadata toXML() {
            Metadata.XMLMetadataFactory xml = new XMLMetadataFactory();
            Metadata.XMLMetadataFactory.XMLMetadata metadata = xml.newMetadata();
            for (Object key : this.keySet()) {
                Metadata.XMLMetadataFactory.XMLProperty property = xml.newProperty(key.toString(), this.getProperty(key.toString()));
                metadata.add(property);
            }
            
            return metadata;
        }
        
        public static class XMLMetadataFactory extends XML.ElementFactoryImpl {
            public final static String XmlNameSpace = "http://labs.oracle.com/TitanObject/Metadata/Version1";

            public XMLMetadataFactory() {
                this(new XML.NameSpace("TitanObject.Metadata", XMLMetadataFactory.XmlNameSpace));
            }

            public XMLMetadataFactory(XML.NameSpace nameSpacePrefix) {
                super(nameSpacePrefix);
            }
            
            public XMLProperty newProperty(String name, String value) {
                return new XMLProperty(name, value);
            }
            
            public XMLMetadata newMetadata() {
                return new XMLMetadata();
            }
            
            public static class XMLMetadata extends XML.Node implements XML.Content {
                private static final long serialVersionUID = 1L;
                public static final String name = "metadata";
                public interface SubElement extends XML.Content {}
                
                public XMLMetadata() {
                    super(XMLMetadata.name, XML.Node.EndTagDisposition.REQUIRED);
                }

                public XMLMetadata add(XMLMetadata.SubElement... content) {
                    super.append(content);
                    return this;
                }
            }        
            
            public static class XMLProperty extends XML.Node implements XML.Content, XMLMetadata.SubElement {
                private static final long serialVersionUID = 1L;
                public static final String name = "property";
                public interface SubElement extends XML.Content {}
                
                public XMLProperty(String name, String value) {
                    super(XMLProperty.name, XML.Node.EndTagDisposition.REQUIRED);
                    this.addAttribute(new XML.Attr(name, value));
                }            
            }      
        }
    }


    protected TitanGuid objectId;

    private TitanObject.Metadata metaData;

    /**
     * 
     * @param handler the class extending this abstract class.
     * @param deleteTokenId The delete-token-id for this {@code TitanObject}.
     * @param timeToLive The time in seconds for this {@code TitanObject} to live (See also {@link TitanObject#INFINITE_TIME_TO_LIVE}.
     */
    public AbstractTitanObject(Class <? extends AbstractObjectHandler> handler, TitanGuid deleteTokenId, long timeToLive) {
        this.objectId = TitanGuidImpl.ANY;

        this.metaData = new AbstractTitanObject.Metadata();
        this.setProperty(TitanObjectStore.METADATA_CLASS, handler.getName());

        this.setDeleteTokenId(deleteTokenId);
        if (this.getDeleteTokenId() == null) {
            throw new IllegalArgumentException("Delete token-id cannot be null");
        }
        this.setTimeToLive(timeToLive);
    }

    public synchronized TitanGuid getObjectId() {
        return this.objectId;
    }

    public synchronized void setObjectId(TitanGuid id) {
        this.objectId = id;
    }

    abstract public TitanGuid getDataId();

    public void setTimeToLive(long timeToLive) {
        this.getMetadata().setProperty(TitanObjectStore.METADATA_SECONDSTOLIVE, timeToLive);
    }

    public long getCreationTime() {
        return this.getPropertyAsLong(TitanObjectStore.METADATA_CREATEDTIME, -1);
    }

    public long getTimeToLive() {
        return Long.valueOf(this.getMetadata().getProperty(TitanObjectStore.METADATA_SECONDSTOLIVE));
    }
    
    public long getRemainingSecondsToLive(long now) {
    	if (this.getTimeToLive() != TitanObject.INFINITE_TIME_TO_LIVE) {
    		return this.getCreationTime() + this.getTimeToLive() - now;
    	}
    	return TitanObject.INFINITE_TIME_TO_LIVE;
    }

    public void setDeleteTokenId(TitanGuid deleteTokenId) {
        if (deleteTokenId == null)
            throw new IllegalArgumentException("deleteTokenId is null");
        this.setProperty(TitanObjectStore.METADATA_DELETETOKENID, deleteTokenId);
    }

    public TitanGuid getDeleteTokenId() {
        return this.getPropertyAsObjectId(TitanObjectStore.METADATA_DELETETOKENID, null);
    }

    public TitanObject.Metadata getMetadata() {
        return this.metaData;
    }

    public TitanObject.Metadata setProperty(String name, Object value) {
        return this.getMetadata().setProperty(name, value);
    }

    public String getProperty(String name, Object defaultValue) {
        return this.metaData.getProperty(name, defaultValue == null ? null : defaultValue.toString());
    }

    public String getProperty(String name) {
        return this.metaData.getProperty(name);
    }

    public TitanGuid getPropertyAsObjectId(String name, TitanGuid defaultValue) {
        String value = this.getProperty(name, defaultValue);
        return (value == null) ? null : new TitanGuidImpl(value);
    }

    public long getPropertyAsLong(String name, long defaultValue) {
        String value = this.getProperty(name, String.valueOf(defaultValue));
        return Long.parseLong(value);
    }

    @Override
    public synchronized int hashCode() {
        return this.objectId.hashCode();
    }

    public boolean isDeleted() {
        return DeleteableObject.deleteTokenIsValid(this.getMetadata());
    }

    public String getObjectType() {
        String type = this.getProperty(TitanObjectStore.METADATA_CLASS);
        return type;
    }

    /**
     * Equality is based only on the equality of the {@link TitanGuid}s of the two objects.
     */
    @Override
    public synchronized boolean equals(Object other) {
        return this.objectId.equals(other);
    }

    @Override
    public String toString() {
        return "AbstractBeehiveObject[" + this.getObjectId() + "]";
    }

    /**
     * Create an XHTML {@code div} element to which subclasses can append additional XHTML elements.
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        XHTML.Div div = new XHTML.Div().setClass("section").addClass("BeehiveObject");
        div.add(new XHTML.Heading.H1(this.getObjectId()).setClass("ObjectId"));
        div.add(new XHTML.Span(this.getObjectType()));
        div.add(this.metaData.toXHTMLTable(uri, props));
        
        return div;
    }
}
