package sunlabs.titan.node.services.objectstore;

import java.io.Serializable;
import java.util.Set;

import sunlabs.asdf.web.XML.XML;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.Publishers;

public class GetPublishers implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public static class Request /*extends AbstractBeehiveObjectHandler.Request*/ implements Serializable {
        private static final long serialVersionUID = 1L;

        private TitanGuid objectId;
        
        public Request(TitanGuid objectId) {
            this.objectId = objectId;
        }

        public TitanGuid getObjectId() {
            return objectId;
        }
    }
    
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private Set<Publishers.PublishRecord> publishers;
        
        public Response(Set<Publishers.PublishRecord> publishers) {
            this.publishers = publishers;
        }
        
        public Set<Publishers.PublishRecord> getPublishers() {
            return this.publishers;
        }
        
        /**
         * <publishers xmlns="">
         *   <publisher objectId="" nodeId="">
         *     <metadata name="" value=""/>
         *   </publisher>
         * </GetPublishers>
         * @return
         */
        public XML.Content toXML() {
            ResponseXML factory = new ResponseXML();

            ResponseXML.PublishersXML result = factory.newPublishersXML();
            for (Publishers.PublishRecord record : this.publishers) {
                result.add(record.toXML());
            }                

            result.bindNameSpace();
            System.out.println(result.toString());
            return result;
        }
    }

    public static class ResponseXML extends XML.ElementFactoryImpl {
        public final static String XmlNameSpace = "http://labs.oracle.com/" + ResponseXML.class.getCanonicalName();

        public PublishersXML newPublishersXML() {
            return new PublishersXML(this);
        }
        
        public PublisherXML newPublisherXML() {
            return new PublisherXML(this);
        }
        
        public PropertyXML newPropertyXML() {
            return null;
        }
        
        public static class PublishersXML extends XML.Node implements XML.Content {
            private static final long serialVersionUID = 1L;
            public static final String name = "publishers";
            public interface SubElement extends XML.Content {}
            
            public PublishersXML(XML.ElementFactory factory) {
                super(PublishersXML.name, XML.Node.EndTagDisposition.REQUIRED, factory);
            }
            
            public void add(PublishersXML.SubElement element) {
                this.append(element);                
            }
        }
        
        public static class PublisherXML extends XML.Node implements XML.Content {
            private static final long serialVersionUID = 1L;
            public static final String name = "publisher";
            public interface SubElement extends XML.Content {}
            
            public PublisherXML(XML.ElementFactory factory) {
                super(PublisherXML.name, XML.Node.EndTagDisposition.REQUIRED, factory);
            }
            
        }
        
        public static class PropertyXML extends XML.Node implements XML.Content {
            private static final long serialVersionUID = 1L;
            public static final String name = "property";
            public interface SubElement extends XML.Content {}
            
        }
        
        public ResponseXML() {
            this(new XML.NameSpace("publishers", ResponseXML.XmlNameSpace));
        }
        
        public ResponseXML(XML.NameSpace nameSpacePrefix) {
            super(nameSpacePrefix);
        }
        
    }
    

}
