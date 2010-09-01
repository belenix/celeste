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
 * Please contact Oracle, 16 Network Circle, MenloPark, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.titan.node.services.xml;

import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.XML.NameSpace;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;

/**
 * 
 * @author Glenn Scott, Sun Labs, Oracle
 *
 */

public class TitanXML implements XML.ElementFactory  {

    public final static String XmlNameSpace = "http://labs.oracle.com/Titan/Version1";
    
    private XML.NameSpace nameSpacePrefix;
    private long nameSpaceReferenceCount;

    /**
     * Construct a new XML content factory using the given {@link XML.NameSpace} specification.
     * <p>
     * This will create elements within the given XML name-space.
     * </p>
     * @param nameSpacePrefix
     */
    public TitanXML(XML.NameSpace nameSpacePrefix) {
        this.nameSpacePrefix = nameSpacePrefix;
        this.nameSpaceReferenceCount = 0;
    }
    
    public TitanXML() {
        this(new XML.NameSpace("titan", TitanXML.XmlNameSpace));
    }

    public NameSpace getNameSpace() {
        return this.nameSpacePrefix;
    }
    
    public XMLRoutingTable newXMLRoutingTable(TitanGuid objectId, int version, int depth) {
        XMLRoutingTable result = new XMLRoutingTable(objectId, version, depth);
        
        result.setNameSpace(this.getNameSpace());        
        return result;
    }

    public XMLService newXMLService(String name, String description, String message) {
        XMLService result = new XMLService(name, description, message);
        
        result.setNameSpace(this.getNameSpace());        
        return result;
    }
    
    public XMLServices newXMLServices() {
        XMLServices result = new XMLServices();
        
        result.setNameSpace(this.getNameSpace());        
        return result;
    }
    
    public XMLNode newXMLNode(TitanGuid objectId, String string) {
        XMLNode result = new XMLNode(objectId, string);
        result.setNameSpace(this.getNameSpace());    
        return result;
    }

    public XMLObject newXMLObject(TitanGuid objectId, String objectType, boolean deleted, long size, String remainingTimeToLive, String nStore, String lowWater, String highWater) {
        XMLObject result = new XMLObject(objectId, objectType, deleted, size, remainingTimeToLive, nStore, lowWater, highWater);
        result.setNameSpace(this.getNameSpace());
        return result;
    }
    
    public XMLObjectStore newXMLObjectStore() {
        XMLObjectStore result = new XMLObjectStore();
        result.setNameSpace(this.getNameSpace());
        return result;
    }
    
    public XMLRoute newXMLRoute(int row, int column) {
        XMLRoute result = new XMLRoute(row, column);
        result.setNameSpace(this.getNameSpace());    
        return result;
    }
    
    public XMLRouteNode newXMLRouteNode(TitanGuid objectId, String ipAddress, int port, int httpPort) {
        XMLRouteNode result = new XMLRouteNode(objectId, ipAddress, port, httpPort);
        result.setNameSpace(this.getNameSpace());    
        return result;
    }
    
    public static class XMLNode extends XML.Node implements XML.Content {
        private static final long serialVersionUID = 1L;
        public static final String name = "node";
        public interface SubElement extends XML.Content {}

        public XMLNode(TitanGuid objectId, String string) {
            super(XMLNode.name, XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(
                    new XML.Attr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"),
                    new XML.Attr("xsi:schemaLocation", "xsd/node.xsd"),
                    new XML.Attr("uptime", string));
        }

        public XMLNode add(XMLNode.SubElement... content) {
            super.append(content);
            return this;
        }
    }
    
    public static class XMLObject extends XML.Node implements XML.Content, XMLObjectStore.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "object";
        public interface SubElement extends XML.Content {}
        
        public XMLObject(TitanGuid objectId, String objectType, boolean deleted, long size, String remainingTimeToLive, String lowWater, String nStore, String highWater) {
            super(XMLObject.name, XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(
                    //new XML.Attr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"),
                    //new XML.Attr("xsi:schemaLocation", "xsd/object.xsd"),
                    new XML.Attr("objectId", objectId),
                    new XML.Attr("objectType", objectType),
                    new XML.Attr("deleted", deleted ? "true" : "false"),
                    new XML.Attr("size", size),
                    new XML.Attr("lowWater", lowWater),
                    new XML.Attr("highWater", highWater),
                    new XML.Attr("nStore", nStore),
                    new XML.Attr("ttl", remainingTimeToLive)                    
            );
        }
        
        public XMLObject add(XMLObject.SubElement...content) {
            super.append(content);
            return this;
        }
    }
    public static class XMLObjectStore extends XML.Node implements XML.Content, XMLNode.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "object-store";
        public interface SubElement extends XML.Content {}
        
        public XMLObjectStore() {
            super(XMLObjectStore.name, XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(
                    new XML.Attr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"),
                    new XML.Attr("xsi:schemaLocation", "xsd/object-store.xsd")
            );
        }
        
        public XMLObjectStore add(XMLObjectStore.SubElement...content) {
            super.append(content);
            return this;
        }
    }
    
    public static class XMLRoutingTable extends XML.Node implements XML.Content, XMLNode.SubElement {
        private static final long serialVersionUID = 1L;
        public interface SubElement extends XML.Content {}

        public static final String name = "routing-table";

        private TitanGuid objectId;

        public XMLRoutingTable(TitanGuid objectId, int version, int depth) {
            super(XMLRoutingTable.name, XML.Node.EndTagDisposition.REQUIRED);
            this.objectId = objectId;

            this.addAttribute(
                    new XML.Attr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"),
                    new XML.Attr("xsi:schemaLocation", "xsd/routing-table.xsd"),
                    new XML.Attr("objectId", this.objectId),
                    new XML.Attr("version", version),
                    new XML.Attr("depth", depth)
            );
        }

        public XMLRoutingTable add(XMLRoutingTable.SubElement... content) {
            super.append(content);
            return this;
        }        
    }
    
    public static class XMLService extends XML.Node implements XML.Content, XMLServices.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "service";
        public interface SubElement extends XML.Content {}
        
        public XMLService(String serviceName, String description, String message) {
            super(XMLService.name, XML.Node.EndTagDisposition.REQUIRED);
            this.addAttribute(
                    new XML.Attr("name", serviceName),
                    new XML.Attr("description", description));
            this.add(message);
        }
        
        public XMLService add(XMLService.SubElement... content) {
            super.append(content);
            return this;
        }
        
        public XMLService add(String...content) {
            super.addCDATA((Object[]) content);
            return this;
        }
    }    
    
    public static class XMLServices extends XML.Node implements XML.Content, XMLNode.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "services";
        public interface SubElement extends XML.Content {}
        
        public XMLServices() {
            super(XMLServices.name, XML.Node.EndTagDisposition.REQUIRED);
        }
        
        public XMLServices add(XMLServices.SubElement... content) {
            super.append(content);
            return this;
        }    
    }    
    
    public static class XMLRoute extends XML.Node implements XML.Content, XMLRoutingTable.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "route";
        public interface SubElement extends XML.Content {}
        
        public XMLRoute(int row, int col) {
            super(XMLRoute.name, XML.Node.EndTagDisposition.REQUIRED);

            this.addAttribute(
                    new XML.Attr("row", row),
                    new XML.Attr("col", col));
        }
        
        public XMLRoute add(XMLRoute.SubElement... content) {
            super.append(content);
            return this;
        }    
    }    

    public static class XMLRouteNode extends XML.Node implements XML.Content, XMLRoute.SubElement {
        private static final long serialVersionUID = 1L;
        public static final String name = "route-node";
        
        public XMLRouteNode(TitanGuid objectId, String ipAddress, int port, int httpPort) {
            super(XMLRouteNode.name, XML.Node.EndTagDisposition.REQUIRED);

            this.addAttribute(
                    new XML.Attr("objectId", objectId),
                    new XML.Attr("ipAddress", ipAddress),
                    new XML.Attr("port", port),
                    new XML.Attr("http", httpPort));
        }
    }
    
    public static void main(String[] args) {
        TitanXML xml = new TitanXML();

        XMLRoutingTable table = xml.newXMLRoutingTable(new TitanGuidImpl(), 0, 4);
        table.bindNameSpace();
        XMLRoute route = xml.newXMLRoute(0, 0);
        route.add(xml.newXMLRouteNode(new TitanGuidImpl(), "127.0.0.1", 12000, 12001));
        table.add(route);
        System.out.printf("%s%n", XML.formatXMLDocument(table.toString()));
    }
}
