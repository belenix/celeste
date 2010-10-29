package sunlabs.titan.node.services.objectstore;

import sunlabs.asdf.web.XML.XML;

public class SetXMLFactory extends XML.ElementFactoryImpl {
    public final static String XmlNameSpace = "http://labs.oracle.com/Set/Version1";

    public SetXMLFactory() {
        this(new XML.NameSpace("set", SetXMLFactory.XmlNameSpace));
    }

    public SetXMLFactory(XML.NameSpace nameSpacePrefix) {
        super(nameSpacePrefix);
    }
    
    public XMLSet newSet() {
        return new XMLSet();
    }
        
    public static class XMLSet extends XML.Node implements XML.Content {
        private static final long serialVersionUID = 1L;
        public static final String name = "metadata";
        public interface SubElement extends XML.Content {}
        
        public XMLSet() {
            super(XMLSet.name, XML.Node.EndTagDisposition.REQUIRED);
        }

        public XMLSet add(XMLSet.SubElement... content) {
            super.append(content);
            return this;
        }
        
        public XMLSet add(XML.Content... content) {
            super.append(content);
            return this;
        }
    }
}
