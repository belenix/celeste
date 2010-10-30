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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import sunlabs.asdf.web.http.InternetMediaType;

/**
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class XML {
    private static final String openQuestion = "<?";
    private static final String openAngle = "<";
    private static final byte[] openAngleBytes = openAngle.getBytes();
    private static final String openAngleSlash = "</";
    private static final byte[] openAngleSlashBytes = openAngleSlash.getBytes();
    private static final String closeAngle = ">";
    private static final byte[] closeAngleBytes = closeAngle.getBytes();
    private static final String slashCloseAngle = "/>";
    private static final String questionCloseAngle = "?>";
    private static final byte[] slashCloseAngleBytes = slashCloseAngle.getBytes();
    private static final String space = " ";
    private static final byte[] spaceBytes = " ".getBytes();

    public XML() {
        super();
    }

    /**
     * A class implementing {@code XML.ContentGenerator} provides methods to generate XML elements
     * with (or without) an XML name-space.
     * <p>
     * For example an XHTML class implementing XMLContentGenerator would be expected to provide
     * methods to generate XHTML elements, and ensure that the element content adhered to the XHTML
     * specification as well as ensuring the generated XHTML elements are correctly produced within
     * the XHTML XML name-space.
     * </p>
     * <pre>
     * public class XHTML implements XML.ContentGenerator {
     *     private XML.NameSpace nameSpace;
     *     
     *     public XHTML() {
     *         this.nameSpace = new XML.NameSpace("xhtml", "http://www.w3.org/1999/xhtml");    
     *     }
     *     
     *     public Html newHtml() {
     *       Html result = new XHTML.Html();
     *       result.setNameSpace(this.getNameSpace());    
     *       return result;
     *     }
     *     
     *     public static class Html extends XML.Node implements XML.Content {
     *       private static final long serialVersionUID = 1L;
     *       public static final String name = "html";
     *       public interface SubElement extends XML.Content {}
     *       
     *       public Html() {
     *           super(Html.name, XML.Node.EndTagDisposition.REQUIRED);
     *       }
     *       
     *       public Html add(Html.SubElement... content) {
     *           super.append(content);
     *           return this;
     *       }
     *       
     *       public Html add(String...content) {
     *           super.addCDATA((Object[]) content);
     *           return this;
     *       }
     *   }
     * }
     * </pre>
     * <p>
     * Using the ElementFactory generates elements for which the final operation must be an invocation of the {@link XML.Node#bindNameSpace()} method.
     * </p>
     * <p>
     * Guidelines on whether to encode values as elements or as attributes are roughly:
     * </p>
     * <ul>
     * <li>If the data could be itself marked up with elements, put it in an element.</li>
     * <li>If the data is suitable for attribute form, but could end up as multiple attributes of the same name on the same element, use child elements instead.</li>
     * <li>If the data is required to be in a standard DTD-like attribute type such as ID, IDREF, or ENTITY, use an attribute.</li>
     * <li>If the data should not be normalized for white space, use elements. (XML processors normalize attributes in ways that can change the raw text of the attribute value.)</li>
     * </ul>
     */
    public interface ElementFactory {

        /**
         * Get the {@link XML.NameSpace} that this generator is using.
         */
        public XML.NameSpace getNameSpace();        
    }
    
    /**
     * An extensible implementation of the {@link XML.ElementFactory} interface.
     *
     */
    public static class ElementFactoryImpl implements XML.ElementFactory {
        protected XML.NameSpace nameSpacePrefix;
        protected long nameSpaceReferenceCount;
        
        /**
         * Construct a new XML content factory using the given {@link XML.NameSpace} specification.
         * <p>
         * This will create elements within the given XML name-space.
         * </p>
         * @param nameSpacePrefix
         */
        public ElementFactoryImpl(XML.NameSpace nameSpacePrefix) {
            this.nameSpacePrefix = nameSpacePrefix;
            this.nameSpaceReferenceCount = 0;            
        }

        public NameSpace getNameSpace() {
            return this.nameSpacePrefix;
        }
    }
    
    /**
     * 
     *
     */
    public interface Content extends Serializable {
        /**
         * Set the attributes
         */
        public XML.Node setAttribute(XML.Attribute... attributes);
        
        /**
         * Get the attribute with the given name.
         * @param name
         */
        public XML.Attribute getAttribute(String name);
        /**
         * Remove the attribute with the given name.
         * @param name
         */
        public XML.Attribute removeAttribute(String name);

        /**
         * Set the XML name-space for this node.
         *
         * @param nameSpace
         */
        public void setNameSpace(XML.NameSpace nameSpace);

        /**
         * Get the children of this node.
         */
        public List<XML.Content> getChildren();
        
        public void setSubNodes(List<XML.Content> newList);
        
//        public Appendable toString(Appendable appendTo) throws IOException;
        
        /**
         * Write a representation of this object to the {@link DataOutputStream}.
         * 
         * @param out
         * @return The number of bytes sent.
         * @throws IOException
         */
        public long streamTo(DataOutputStream out) throws IOException;
                
        /**
         * The number of bytes streamTo() would produce if invoked.
         */
        public long streamLength();
    }

    /**
     * An XML attribute.
     * 
     */
    public interface Attribute extends Serializable {
        public String getName();
        public String getValue();
    }

    /**
     * An XML attribute.
     *
     */
    public static class Attr implements Attribute {
    	private final static long serialVersionUID = 1L;
    	
        protected String name;
        protected String value;

        public Attr(String name) {
            this.name = name;
            this.value = null;
        }

        public Attr(String name, String format, Object...objects) {
            this.name = name;
            this.value = String.format(format, objects);
        }

        public Attr(String name, Object value) {
            this.name = name;
            this.value = String.valueOf(value);
        }

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
        }

        private static final String equalsQuote = "=\"";
        private static final String quote = "\"";

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(this.name);
            if (this.value != null) {
                String escapedValue = this.value.replace("\"", "&quot");
                result.append(equalsQuote).append(escapedValue).append(quote);
            }
            return result.toString();            
        }
    }

    /**
     * Represent an entire XML document.
     */
    public static class Document {
        /**
         * If {@code true} invocations of {@link #toString()} or {@link #toString(Appendable)} will <b>not</b> contain any XML processing instructions.
         * 
         */
        protected boolean piInhibit = false;
        protected List<XML.ProcessingInstruction> piNodes;
        protected List<XML.Content> subNodes;
        
        protected Document() {
            this.piInhibit = false;
            this.subNodes = new LinkedList<XML.Content>();
            this.piNodes = new LinkedList<XML.ProcessingInstruction>();
            this.piNodes.add(XML.ProcessingInstruction.newXML("1.0", "utf-8"));
        }
        
        /**
         * Class constructor specifying the XML content and whether or not to output the ?xml processing directive.
         * 
         * @param xmlInhibit {@code true} if the output of this document will not contain the ?xml processing directive.
         * @param content the XML content for this document.
         */
        public Document(boolean piInhibit, XML.Content...content) {
            this();
            this.append(content);
            this.piInhibit = piInhibit;
        }
        
        /**
         * Class constructor specifying the XML content.
         * The constructor differentiates between instances of {@link XML.ProcessingInstruction}, segregating those to the list of processing instructions.
         * See {@link #setXMLInhibit(boolean)}.
         * 
         * @param content a variable list of {@link XML.Content} instances.
         */
        public Document(XML.Content...content) {
            this();
            for (XML.Content c : content) {
                if (c instanceof XML.ProcessingInstruction) {
                    this.append((XML.ProcessingInstruction) c);
                } else {
                    this.append(c);
                }
            }
        }
        
        /**
         * Do not output an initial &lt;?xml ?&gt; processing instruction.
         * This is useful when creating components of another XML document.
         * 
         * @param xmlInhibit
         */
        public void setXMLInhibit(boolean xmlInhibit) {
            this.piInhibit = xmlInhibit;
        }
        
        public void append(XML.Content...content) {
            for (XML.Content c : content) {
                this.subNodes.add(c);
            }
        }
        
        /**
         * Add the given XML processing instructions to the preamble of this XML Document.
         * See {@link #setXMLInhibit(boolean)}.
         * 
         * @param processingInstruction
         */
        public void append(XML.ProcessingInstruction...processingInstruction) {
            for (XML.ProcessingInstruction c : processingInstruction) {
                this.piNodes.add(c);
            }
        }


        @Override
        public String toString() {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                this.streamTo(new DataOutputStream(bout));
                return bout.toString();
            } catch (IOException cantHappen) {
                return cantHappen.getLocalizedMessage();
            }
        }

        public long streamTo(DataOutputStream out) throws IOException {
            long startingSize = out.size();
            if (this.subNodes != null) {
                if (!this.piInhibit) {
                    for (XML.ProcessingInstruction pi : this.piNodes) {
                        pi.streamTo(out);
                    }
//                    out.write("\n".getBytes());
                }
                for (XML.Content c : this.subNodes) {
                    c.streamTo(out);
                }
            }
            return out.size() - startingSize;
        }

        public long streamLength() {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            try {
                return this.streamTo(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * An XML Name Space serves to distinguish element names with the same spelling from each other.
     * 
     * See {@link XML.NameSpace#NameSpace(String)} and {@link XML.NameSpace#NameSpace(String, String)} 
     */
    public static class NameSpace extends Attr {
    	private final static long serialVersionUID = 1L;
    	
        private String prefix;
        // This is supposed to be a URI, but some namespace definitions use
        // URIs that are not parsable by the Java URI class.  For example,
        // WebDAV often defined xmlns:D="DAV:"  and the URI "DAV:" results in
        // an URISyntaxException thrown by the Java URI class constructor.

        /**
         * Create an {@code XML.NameSpace} specifying only the name-space URI as {@code uri} with no element name prefix.
         */
        public NameSpace(String uri) {
            super("xmlns", uri);
            this.prefix = null;
        }

        /**
         * Create an XML NameSpace specifying the given element name prefix {@code prefix} and the name-space URI as {@code uri}.
         * <p>
         * If {@code prefix} is {@code null} it is ignored and the result is equivalent to using {@link XML.NameSpace#NameSpace(String)}.
         * </p>
         * @param prefix the element prefix to use for this element.
         * @param uri the XML name-space URI.
         */
        public NameSpace(String prefix, String uri) {
            super("xmlns" + (prefix == null ? "" : (":" + prefix)), uri);
            this.prefix = prefix;
        }

        /**
         * Get the element prefix of this name-space.
         * @return the element prefix of this name-space.
         */
        public String getPrefix() {
            return this.prefix;
        }

        private static final String equalsQuote = "=\"";
        private static final String quote = "\"";

        @Override        
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (this.prefix == null) {
                result.append("xmlns");
                if (this.value != null) {
                    String escapedValue = this.value.replace("\"", "&quot");
                    result.append(equalsQuote).append(escapedValue).append(quote);
                }
            } else {
                result.append("xmlns:").append(this.prefix);
                if (this.value != null) {
                    String escapedValue = this.value.replace("\"", "&quot");
                    result.append(equalsQuote).append(escapedValue).append(quote);
                }
            }
            return result.toString();            
        }
    }

    /**
     * Processing Instructions are not part of the document's character data, but must be passed through to the application.
     * The PI begins with a target (PITarget) used to identify the application to which the instruction is directed.
     * The target names " XML ", " xml ", and so on are reserved for standardization in this or future versions of this specification.
     * The XML Notation mechanism may be used for formal declaration of PI targets.
     * Parameter entity references must not be recognized within processing instructions.
     */
    public static class ProcessingInstruction extends XML.Node {
        private static final long serialVersionUID = 1L;
        
        /**
         * Create an {@code xml} {@link XML.ProcessingInstruction}
         * 
         * @param version
         * @param encoding
         */
        public static XML.ProcessingInstruction newXML(String version, String encoding) {
            XML.ProcessingInstruction result = new XML.ProcessingInstruction("xml");
            // XXX We are leaving out the encoding attribute because although I think the order of attributes is not supposed to matter,
            // it does matter here because later, when formatting the XML, it complains if version is not the first attribute.
            result.addAttribute(/*new XML.Attr("encoding", encoding),*/ new XML.Attr("version", version));
            return result;
        }
        
        /**
         * Create an {@code xml-stylesheet} {@link XML.ProcessingInstruction}
         * 
         * @param url
         */
        public static XML.ProcessingInstruction newStyleSheet(String url) {
            XML.ProcessingInstruction result = new XML.ProcessingInstruction("xml-stylesheet");
            result.addAttribute(new XML.Attr("type", InternetMediaType.Application.XSLT), new XML.Attr("href", url.toString()));
            return result;
        }
        
        public ProcessingInstruction(String tag) {
            super(tag, XML.Node.EndTagDisposition.FORBIDDEN);
        }
        
        public long streamTo(DataOutputStream out) throws IOException {
            long startSize = out.size();
            // If this node is just text, append it.
            if (this.pcdata != null) {
                out.writeBytes(this.pcdata);
                return out.size() - startSize;
            }

            // This should encode the xmlns attribute if and only if this node has a namespace,
            // and the namespace is different from the parent namespace.
            // For this to happen, each node needs to have a link to its parent node.

            String elementName = this.getElementName();
            
            out.writeBytes(openQuestion);            
            out.writeBytes(elementName);

            if (this.attrs != null) {
                for (String key : this.attrs.keySet()) {
                    out.writeBytes(space);
                    out.writeBytes(this.attrs.get(key).toString());
                }
            }
            out.writeBytes(questionCloseAngle);            
            out.writeBytes("\n");
            return out.size() - startSize;
        }
        
        public Appendable toString(Appendable out) throws IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            this.streamTo(new DataOutputStream(bout));
            out.append(bout.toString());
            return out;
        }
    }
    
    /**
     * An single XML node.
     * <p>
     * A node contains either a String containing PCDATA or a ordered {@link List} of other {@link XML.Content} instances.
     * A node may optionally have an associated {@link XML.NameSpace} (see {@link #setNameSpace(XML.NameSpace)}).
     * </p>
     */
    public static class Node implements XML.Content {
    	private final static long serialVersionUID = 1L;
    	
        /**
         * A Document Type Definition will specify that an element may or may not require
         * an end-tag, or may prohibit an end-tag altogether.
         * For example an element may require an end-tag, such as <em>&lt;tag&gt;...&lt;/tag&gt;</em>
         * or not such as <em>&lt;tag /&gt;</em>.
         */
        public static enum EndTagDisposition {
            /**
             * The end-tag is required.
             * The element is of the form &lt;element&gt ... &lt;/element&gt;
             */
            REQUIRED,
            /**
             * The end-tag is forbidden.
             * The element is of the form &lt;element /&gt;
             */
            FORBIDDEN,
            /**
             * The end-tag is optional.
             * If the element contains no content, the end-tag may be omitted.
             * If the end tag is omitted, the element must terminate like this </em>&lt;element /&gt;</em>.
             */
            ABBREVIABLE
        }


        // A node contains either simply PCDATA:
        protected String pcdata;
        // or an element consisting of the following:
        protected List<XML.Content> subNodes;
        
        /**
         * This is the XML factory used to construct this element.
         */
        public XML.ElementFactory factory;
        
        /**
         * If this is non-null, this is the namespace for this element.
         */
        protected XML.NameSpace nameSpace;
        private String tag;
        protected SortedMap<String,XML.Attribute> attrs;
        protected EndTagDisposition endTag;
        
        /**
         * Construct a node containing PCDATA.
         */
        public Node(Object... pcdata) {
            StringBuilder sb = new StringBuilder();
            for (Object s : pcdata) {
                if (s instanceof String) {
                    sb.append((String) s);
                } else {
                    sb.append(String.valueOf(s));
                }
            }
            this.pcdata = sb.toString();

            this.tag = null;
            this.attrs = null;
            this.subNodes = null;
        }        

        /**
         * @param tag
         * @param endTag
         * @param factory
         */
        public Node(String tag, EndTagDisposition endTag, XML.ElementFactory factory) {
            this.factory = factory;
            if (factory != null) {
                this.nameSpace = factory.getNameSpace();
            }

            this.pcdata = null;

            this.tag = tag;
            this.attrs = new TreeMap<String,XML.Attribute>();
            this.endTag = endTag;
            this.subNodes = new LinkedList<XML.Content>();
        }

        /**
         * Construct a node with the given element name and {@link EndTagDisposition}.
         * 
         * @param tag a {@code String} containing the element name of this node. 
         * @param endTag the {@code EndTagDisposition} for this node.
         */
        public Node(String tag, EndTagDisposition endTag) {
            this(tag, endTag, null);
        }

        public Node addCDATA(Object... cdata) {
            for (Object o : cdata) {
                if (o instanceof String) {
                    this.append(new XML.Node((String) o));
                } else {
                    this.append(new XML.Node(String.valueOf(o)));
                }
            }
            return this;
        }

        public Node appendCDATA(String format, Object...objects) {
            return this.addCDATA(String.format(format, objects));
        }

        public Node append(XML.Content...nodes) {
            if (nodes == null)
                return this;
            if (this.endTag == EndTagDisposition.FORBIDDEN) {
                throw new IllegalArgumentException(String.format("Element '%s' cannot take child elements or contain content.", this.tag));
            }
            // If this Node's content is currently just PCDATA, convert it into an ELEMENT containing the PCDATA.
            // Encapsulate this Node's PCDATA into a new Node, create a new element Node to contain that PCDATA
            // and the new code we are adding here.
            // This is a violation of the XML rules which say PCDATA nodes cannot have children.
            if (this.pcdata != null) {
                this.subNodes = new LinkedList<XML.Content>();
                this.subNodes.add(this);
                this.pcdata = null;
            }
            if (this.subNodes == null) {
                this.subNodes = new LinkedList<XML.Content>();
            }
            for (XML.Content n : nodes) {
                if (n != null)
                    this.subNodes.add(n);
            }

            return this;
        }

        public List<XML.Content> getChildren() {
            return this.subNodes;
        }

        /**
         * Return the factory that originally created this node.
         * <p>
         * Typically the factory is used again to create children of this node.
         * Reusing the factory inherits any namespace definitions set in the factory.
         * </p>
         */
        public XML.ElementFactory getFactory() {
            return this.factory;
        }
        
        /**
         * Bind this Node's name-space using the prefix and XML name-space defined for this Node (see {@link #setNameSpace(NameSpace)}).
         * 
         * The effect of this operation is to set an attribute in the node specifying the {@code xmlns} value.
         * <p>
         * This method should be eliminated in favour of a mechanism that automatically binds all name-space definitions to elements and sub-elements in one recursive operation.
         * Elements that share the same XML name-space as their parents inherit the parent namespace (without emitting a, redundant, xmlns:ns="ns" attribute).
         * </p>
         * @see #setNameSpace(XML.NameSpace)
         */
        public Node bindNameSpace() {
            if (this.getNameSpace() != null)
                this.addAttribute(this.getNameSpace());
            return this;
        }
        
        /**
         * Set the XML name-space for this Node.
         */
        public void setNameSpace(XML.NameSpace nameSpace) {
            this.nameSpace = nameSpace;
        }
        
        /**
         * Get the XML name-space for this Node.
         * 
         * @return the XML name-space for this Node.
         * @see #bindNameSpace()
         * @see #setNameSpace(XML.NameSpace)
         */
        public XML.NameSpace getNameSpace() {
            return this.nameSpace;
        }

        public void setSubNodes(List<XML.Content> newList) {
            this.subNodes = newList;
        }

        public XML.Node addAttribute(XML.Attribute... attributes) {
            return this.setAttribute(attributes);
        }

        public XML.Node setAttribute(XML.Attribute... attributes) {
            if (this.attrs == null) {
                throw new IllegalArgumentException("TEXT node cannot accept attributes");
            }

            if (attributes != null) {
                for (XML.Attribute attr : attributes) {
                    this.attrs.put(attr.getName(), attr);
                }
            }
            return this;
        }

        public XML.Attribute getAttribute(String name) {
            return (this.attrs != null) ? this.attrs.get(name) : null;
        }

        public XML.Attribute removeAttribute(String name) {
            return this.attrs.remove(name);
        }

        public void removeAttribute(XML.Attribute... attributes) {
            for (XML.Attribute attr : attributes) {
                this.attrs.remove(attr.getName());
            }
        }

        /**
         * Compute this node's fully qualified element name.
         * <p>
         * The fully qualified element name includes the node's name-space prefix (if defined for this node).
         * See {@link #bindNameSpace()}.
         * </p>
         */
        protected String getElementName() {
            String elementName = "";
            if (this.nameSpace != null) {
                if (this.nameSpace.getPrefix() != null) {
                    elementName += this.nameSpace.getPrefix() + ":";
                }
            }
            elementName += this.tag;
            return elementName;
        }
        
        @Override
        public String toString() {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                this.streamTo(new DataOutputStream(bout));
                return bout.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        private long streamTo(DataOutputStream out, Object...objects) throws IOException {
            long startSize = out.size();
            for (Object o : objects) {
                if (o != null) {
                    if (o instanceof byte[]) {
                        out.write((byte[]) o);
                    } else if (o instanceof String) {
                        out.writeBytes(encodeXMLCharacters((String) o));
                        //out.writeBytes((String) o);
                    } else {
                        out.writeBytes(o.toString());
                    }
                }
            }            
            return out.size() - startSize;
        }
        
        public long streamTo(DataOutputStream out) throws IOException {
            if (this.pcdata != null) {
                return this.streamTo(out, this.pcdata);
            }
            long startSize = out.size();
            String elementName = this.getElementName();

            this.streamTo(out, openAngleBytes, elementName);

            if (this.attrs != null) {
                for (String key : this.attrs.keySet()) {
                    this.streamTo(out, spaceBytes, this.attrs.get(key));
                }
            }

            if (this.endTag == EndTagDisposition.ABBREVIABLE) {
                if (this.pcdata == null && this.getChildren().size() == 0) {
                    this.streamTo(out, slashCloseAngleBytes);
                } else {
                    this.streamTo(out, closeAngleBytes);
                    for (XML.Content n : this.getChildren()) {
                        n.streamTo(out);
                    }
                    this.streamTo(out, openAngleSlashBytes, elementName, closeAngleBytes);
                }
            } else if (this.endTag == EndTagDisposition.FORBIDDEN) {
                this.streamTo(out, slashCloseAngleBytes);
            } else {
                this.streamTo(out, closeAngleBytes);

                for (XML.Content n : this.getChildren()) {
                    n.streamTo(out);
                }
                this.streamTo(out, openAngleSlashBytes, elementName, closeAngleBytes);
            }
            return out.size() - startSize;
        }

        public long streamLength() {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            try {
                long length = this.streamTo(out);
                return length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Given an XML element name which may have an XML name-space prefix,
     * return just the name without the XML name-space prefix.

     * @param compositeElementName
     */
    public static String getXMLElementName(String compositeElementName) {
        String[] parts = compositeElementName.split(":", 2);
        if (parts.length > 1)
            return parts[1];
        return parts[0];
    }

    /**
     * Given an XML element name which may have an XML name-space prefix,
     * return XML name-space prefix.
     * <p>
     * If there is no XML name-space prefix, return null.
     * </p>
     * @param compositeElementName
     */
    public static String getXMLNameSpaceName(String compositeElementName) {
        String[] parts = compositeElementName.split(":", 2);
        if (parts.length > 1)
            return parts[0];
        return null;
    }

    public static int replaceElement(XML.Content node, Class<? extends XML.Content> klasse, XML.Attr attr, XML.Content newElement, OutputStream out, int indentation) {
        String indent = "              ".substring(0, indentation);
        System.out.println(indent + "replaceElement: " + node.getClass().toString() + " " + klasse + " " + attr.toString());

        int replacementCounter = 0;

        List<XML.Content> subNodes = node.getChildren();
        List<XML.Content> newList = new LinkedList<XML.Content>();

        if (subNodes != null) {
            for (XML.Content c : subNodes) {
                XML.Attribute a = c.getAttribute(attr.getName());
                if (c.getClass().equals(klasse) && a != null && attr.getValue().equals(a.getValue())) {
                    newList.add(newElement);
                    replacementCounter++;
                } else  {
                    newList.add(c);
                    replacementCounter = XML.replaceElement(c, klasse, attr, newElement, out, indentation + 2);
                }
            }
            node.setSubNodes(newList);
            return replacementCounter;
        }
        return replacementCounter;
    }

    public static String formatXMLElement(String input, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            Transformer transformer = TransformerFactory.newInstance().newTransformer(); 
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (TransformerConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (TransformerFactoryConfigurationError e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            
        }
    }
    
    /**
     * This method ensures that the output String has only valid XML unicode characters as specified by the
     * XML 1.0 standard. For reference, please see the
     * standard. This method will return an empty String if the input is null or empty.
     *
     * @author Donoiu Cristian, GPL
     * @param  The String whose non-valid characters we want to remove.
     * @return The in String, stripped of non-valid characters.
     */

    public static String removeInvalidXMLCharacters(String s) {

        StringBuilder out = new StringBuilder();                // Used to hold the output.

        int codePoint;                                          // Used to reference the current character.

        //String ss = "\ud801\udc00";                           // This is actualy one unicode character, represented by two code units!!!.

        //System.out.println(ss.codePointCount(0, ss.length()));// See: 1

        int i = 0;

        while (i < s.length()) {
            codePoint = s.codePointAt(i);                       // This is the unicode code of the character.

            if ((codePoint == 0x9) ||                           // Consider testing larger ranges first to improve speed. 
                    (codePoint == 0xA) ||
                    (codePoint == 0xD) ||
                    ((codePoint >= 0x20) && (codePoint <= 0xD7FF)) ||
                    ((codePoint >= 0xE000) && (codePoint <= 0xFFFD)) ||
                    ((codePoint >= 0x10000) && (codePoint <= 0x10FFFF))) {
                out.append(Character.toChars(codePoint));
            }               
            i+= Character.charCount(codePoint);                 // Increment with the number of code units(java chars) needed to represent a Unicode char.  
        }

        
        return out.toString();
    } 
    

    public static String encodeXMLCharacters(String s) {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            int codePoint = s.codePointAt(i);

            if ((codePoint < 0x20) || (codePoint >= 0x7f)) {
                if (codePoint != 0x0A) {
                    out.append("&#");
                    out.append(Integer.toString(codePoint));
                    out.append(";");
                } else {
                    out.append((char) codePoint);
                }
            } else {
                out.append((char) codePoint);
            }
        }

        return out.toString();
    } 
    
    public static String formatXMLDocument(String input, int indent) throws TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {
        
        String input2 = encodeXMLCharacters(input);
        
        Source xmlInput = new StreamSource(new StringReader(input2));
        StringWriter stringWriter = new StringWriter();
        StreamResult xmlOutput = new StreamResult(stringWriter);
        Transformer transformer = TransformerFactory.newInstance().newTransformer(); 
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
        transformer.transform(xmlInput, xmlOutput);
        
        return xmlOutput.getWriter().toString();
    }

    public static String formatXMLDocument(String input) throws TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {
        return formatXMLDocument(input, 2);
    }

    public final static byte[] Prolog = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes();

    public static void main(String...args) {
        

        XML.Node cell = new XML.Node("cell", XML.Node.EndTagDisposition.REQUIRED);
        Map<String, String> ensembles = new HashMap<String,String>();
        ensembles.put("k1", "v1");
        ensembles.put("k2", "v2");
        
        for (Map.Entry<String,String> e : ensembles.entrySet()) {
            cell.append(new XML.Node(e.getKey(), XML.Node.EndTagDisposition.REQUIRED).addCDATA(e.getValue().toString()));                
        }

        
        XML.ProcessingInstruction stylesheet = XML.ProcessingInstruction.newStyleSheet("/foo.xsl");
        XML.Document document = new XML.Document(stylesheet, cell);

        System.out.printf("%s%n", document);
    }
}
