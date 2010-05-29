/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
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

/**
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class XML {
    private static final String openAngle = "<";
    private static final byte[] openAngleBytes = openAngle.getBytes();
    private static final String openAngleSlash = "</";
    private static final byte[] openAngleSlashBytes = openAngleSlash.getBytes();
    private static final String closeAngle = ">";
    private static final byte[] closeAngleBytes = closeAngle.getBytes();
    private static final String slashCloseAngle = "/>";
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
     *     private XML.NameSpace nameSpacePrefix;
     *     
     *     public XHTML() {
     *         this.nameSpace = new XML.NameSpace("xhtml", "http://www.w3.org/1999/xhtml");    
     *     }
     * }
     * </pre>
     * 
     */
    public interface ElementFactory {

        /**
         * Get the {@link XML.NameSpace} that this generator is using.
         */
        public XML.NameSpace getNameSpace();        
    }
    
    /**
     * 
     *
     */
    public interface Content extends Serializable {
        public XML.Node setAttribute(XML.Attribute... attributes);
        public XML.Attribute getAttribute(String name);
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
        public Appendable toString(Appendable appendTo) throws IOException;
        

        /**
         * Write a representation of this object to the {@link DataOutputStream}.
         * 
         * @param out
         * @return The number of bytes sent.
         * @throws IOException
         */
        public long streamTo(OutputStream out) throws IOException;
        
        // public static T streamFrom(DataInputStream in, Object... parameter) throws IOException;
        
        /**
         * The number of bytes streamTo() would produce if invoked.
         */
        public long streamLength();
    }

    /**
     * 
     * 
     */
    public interface Attribute extends Serializable {
        public String toString();
        public Appendable toString(Appendable appendTo) throws IOException;
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

        public Appendable toString(Appendable appendTo) throws IOException {
            appendTo.append(this.name);
            if (this.value != null) {
                String escapedValue = this.value.replace("\"", "&quot");
                appendTo.append(equalsQuote).append(escapedValue).append(quote);
            }
            return appendTo;
        }

        @Override
        public String toString() {
            try {
                return this.toString(new StringBuilder()).toString();
            } catch (IOException cantHappen) {
                cantHappen.printStackTrace();
                throw new RuntimeException(cantHappen);
            }
        }
    }

    public static class Document {
        private XML.Content content;

        public Document(XML.Content content) {
            this.content = content;
        }

        @Override
        public String toString() {
            try {
                return this.toString(new StringBuilder()).toString();
            } catch (IOException cantHappen) {
                cantHappen.printStackTrace();
                return cantHappen.getLocalizedMessage();
            }
        }

        public Appendable toString(Appendable appendTo) throws IOException {
            if (this.content != null) {
                appendTo.append(new String(XML.Prolog));
                this.content.toString(appendTo);
                appendTo.append("\n");
            }
            return appendTo;
        }
    }

    /**
     * An XML Name Space serves to distinguish element names
     * with the same spelling from each other.
     */
    public static class NameSpace extends Attr {
    	private final static long serialVersionUID = 1L;
    	
        private String prefix;
        // This is supposed to be a URI, but some namespace definitions use
        // URIs that are not parsable by the Java URI class.  For example,
        // WebDAV often defined xmlns:D="DAV:"  and the URI "DAV:" results in
        // an URISyntaxException thrown by the Java URI class constructor.

        /**
         * Create an XMLNameSpace specifying only the name-space URI as {@code uri} with no element name prefix.
         */
        public NameSpace(String uri) {
            super("xmlns", uri);
            this.prefix = null;
        }

        /**
         * Create an XML NameSpace specifying the given element name prefix {@code prefix} and the name-space URI as {@code uri}.
         * <p>
         * If {@code prefix} is {@code null} it is ignored and the result is equivalent to using {@link #NameSpace(String uri)}.
         * </p>
         * @param prefix the element prefix to use for this element.
         * @param uri the XML name-space URI.
         */
        public NameSpace(String prefix, String uri) {
            super("xmlns" + (prefix == null ? "" : (":" + prefix)), uri);
            this.prefix = prefix;
        }

        public String getPrefix() {
            return this.prefix;
        }

        private static final String equalsQuote = "=\"";
        private static final String quote = "\"";

        @Override
        public Appendable toString(Appendable appendTo) throws IOException {
            if (this.prefix == null) {
                appendTo.append("xmlns");
                if (this.value != null) {
                    String escapedValue = this.value.replace("\"", "&quot");
                    appendTo.append(equalsQuote).append(escapedValue).append(quote);
                }
            } else {
                appendTo.append("xmlns:").append(this.prefix);
                if (this.value != null) {
                    String escapedValue = this.value.replace("\"", "&quot");
                    appendTo.append(equalsQuote).append(escapedValue).append(quote);
                }
            }
            return appendTo;
        }

        @Override
        public String toString() {
            try {
                return this.toString(new StringBuilder()).toString();
            } catch (IOException cantHappen) {
                cantHappen.printStackTrace();
                throw new RuntimeException(cantHappen);
            }
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
         * According to a Document Type Definition an element may or may not require
         * an end-tag, or may prohibit an end-tag altogether.
         * For example an element may require an end-tag, such as <em>&lt;tag&gt;...&lt;/tag&gt;</em>
         * or not such as <em>&lt;tag /&gt;</em>.
         */
        public enum EndTagDisposition {
            /**
             * The end-tag is required.
             */
            REQUIRED,
            /**
             * The end-tag is forbidden.
             */
            FORBIDDEN,
            /**
             * The end-tag is optional.  If the element contains no content,
             * the end-tag may be omitted.  If the end tag is omitted, the
             * element must terminate like this </em>&lt;element /&gt;</em>.
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
        private SortedMap<String,XML.Attribute> attrs;
        protected EndTagDisposition endTag;
        
        /**
         * Constructor for PCDATA.
         */
        public Node(Object... pcdata) {
            StringBuilder sb = new StringBuilder();
            for (Object s : pcdata) {
                sb.append(String.valueOf(s));
            }
            this.pcdata = sb.toString();

            this.tag = null;
            this.attrs = null;
            this.subNodes = null;
        }        

        /**
         * @param tag
         * @param endTag
         * @param nameSpace
         */
        public Node(String tag, EndTagDisposition endTag, XML.ElementFactory factory) {
            this.factory = factory;
            this.nameSpace = factory.getNameSpace();

            this.pcdata = null;

            this.tag = tag;
            this.attrs = new TreeMap<String,XML.Attribute>();
            this.endTag = endTag;
            this.subNodes = new LinkedList<XML.Content>();
        }

        public Node(String tag, EndTagDisposition endTag) {
            //this(tag, endTag, null);
            this.factory = null;
            this.nameSpace = null;

            this.pcdata = null;

            this.tag = tag;
            this.attrs = new TreeMap<String,XML.Attribute>();
            this.endTag = endTag;
            this.subNodes = new LinkedList<XML.Content>();
        }

        public Node addCDATA(Object... cdata) {
            for (Object o : cdata) {
                this.append(new XML.Node(String.valueOf(o)));
            }
            return this;
        }

        protected Node appendCDATA(String format, Object...objects) {
            return this.addCDATA(String.format(format, objects));
        }

        public Node append(XML.Content...nodes) {
            if (nodes == null)
                return this;
            if (this.endTag == EndTagDisposition.FORBIDDEN) {
                throw new IllegalArgumentException(String.format("Element '%s' cannot take child elements or contain content.", this.tag));
            }
            // If this Node's content is currently just PCDATA, convert it into
            // an ELEMENT containing the PCDATA.
            // Encapsulate this Node's PCDATA into a new Node,
            // create a new element Node to contain that PCDATA
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
         * Bind this node's namespace using the prefix and XML name-space defined for this Node.
         * <p>
         * This method should be eliminated in favour of a mechanism that automatically binds all name-space definitions to elements and sub-elements in one recursive operation.
         * Elements that share the same XML name-space as their parents inherit the parent namespace (without emitting a, redundant, xmlns:ns="ns" attribute).
         * </p>
         * @see #setNameSpace(NameSpace)
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
         * @see #setNameSpace(NameSpace)
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
                throw new IllegalArgumentException("CDATA node cannot accept attributes");
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
         * The fully qualified element name includes the node's name-space prefix (if appropriate).
         * </p>
         */
        private String getElementName() {
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
            try {
                return this.toString(new StringBuilder()).toString();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public Appendable toString(Appendable out) throws IOException {
            // If this node is just text, append it.
            if (this.pcdata != null) {
                return out.append(this.pcdata);
            }

            // This should encode the xmlns attribute IFF this node has a namespace, and the namespace is different from the parent namespace.
            // For this to happen, each node needs to have a link to its parent node.
            

            String elementName = this.getElementName();            
            
            out.append(openAngle);

            out.append(elementName);
          

            if (this.attrs != null) {
                for (String key : this.attrs.keySet()) {
                    out.append(space).append(this.attrs.get(key).toString());
                }
            }

            if (this.endTag == EndTagDisposition.ABBREVIABLE) {
                if (this.pcdata == null && this.getChildren().size() == 0) {
                    out.append(slashCloseAngle);
                } else {
                    out.append(closeAngle);
                    for (XML.Content n : this.getChildren()) {
                        n.toString(out);
                    }
                    out.append(openAngleSlash);
                    out.append(elementName).append(closeAngle);
                }
            } else if (this.endTag == EndTagDisposition.FORBIDDEN) {
                out.append(slashCloseAngle);
            } else {
                out.append(closeAngle);
                for (XML.Content n : this.getChildren()) {
                    n.toString(out);
                }
                out.append(openAngleSlash);
                out.append(elementName).append(closeAngle);
            }
            return out;
        }

        private long streamTo(OutputStream out, Object...objects) throws IOException {
            long count = 0;
            for (Object o : objects) {
                if (o != null) {
                    byte[] bytes;
                    if (o instanceof byte[]) {
                        bytes = (byte[]) o;
                    } else {
                        bytes = o.toString().getBytes();
                    }
                    out.write(bytes);
                    count += bytes.length;
                }
            }
            return count;
        }

        public long streamTo(OutputStream out) throws IOException {
            if (this.pcdata != null) {
                return this.streamTo(out, this.pcdata);
            }
            long count = 0;
            String elementName = this.getElementName();
           
            count += this.streamTo(out, openAngleBytes, elementName);

            if (this.attrs != null) {
                for (String key : this.attrs.keySet()) {
                    count += this.streamTo(out, spaceBytes, this.attrs.get(key));
                }
            }

            if (this.endTag == EndTagDisposition.ABBREVIABLE) {
                if (this.pcdata == null && this.getChildren().size() == 0) {
                    count += this.streamTo(out, slashCloseAngleBytes);
                } else {
                    count += this.streamTo(out, closeAngleBytes);
                    for (XML.Content n : this.getChildren()) {
                        count += n.streamTo(out);
                    }
                    count += this.streamTo(out, openAngleSlashBytes, elementName, closeAngleBytes);
                }
            } else if (this.endTag == EndTagDisposition.FORBIDDEN) {
                count += this.streamTo(out, slashCloseAngleBytes);
            } else {
                count += this.streamTo(out, closeAngleBytes);

                for (XML.Content n : this.getChildren()) {
                    count += n.streamTo(out);
                }
                count += this.streamTo(out, openAngleSlashBytes, elementName, closeAngleBytes);
            }
            return count;
        }

        private long streamLength(Object...objects) {
            long count = 0;
            for (Object o : objects) {
                if (o != null) {
                    byte[] bytes;
                    if (o instanceof byte[]) {
                        bytes = (byte[]) o;
                    } else {
                        bytes = o.toString().getBytes();
                    }
                    count += bytes.length;
                }
            }
            return count;
        }

        public long streamLength() {
            if (this.pcdata != null) {
                return this.streamLength(this.pcdata);
            }

            String elementName = this.getElementName();
            
            long count = this.streamLength(openAngleBytes, elementName);

            if (this.attrs != null) {
                for (String key : this.attrs.keySet()) {
                    count += this.streamLength(spaceBytes, this.attrs.get(key));
                }
            }

            if (this.endTag == EndTagDisposition.ABBREVIABLE) {
                if (this.pcdata == null && this.getChildren().size() == 0) {
                    count += this.streamLength(slashCloseAngleBytes);
                } else {
                    count += this.streamLength(closeAngleBytes);
                    for (XML.Content n : this.getChildren()) {
                        count += n.streamLength();
                    }
                    count += this.streamLength(openAngleSlashBytes, elementName, closeAngleBytes);
                }
            } else if (this.endTag == EndTagDisposition.FORBIDDEN) {
                count += this.streamLength(slashCloseAngleBytes);
            } else {
                count += this.streamLength(closeAngleBytes);

                for (XML.Content n : this.getChildren()) {
                    count += n.streamLength();
                }
                count += this.streamLength(openAngleSlashBytes, elementName, closeAngleBytes);
            }
            
            if (count != this.toString().length()) {
                System.out.printf("node length discrepency (unicode?): actual %d reported %d '%s'%n", this.toString().length(), count, this.toString());
            }
            return count;
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
    
    public static String formatXMLDocument(String input, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            Transformer transformer = TransformerFactory.newInstance().newTransformer(); 
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

    public static String formatXMLDocument(String input) {
        return formatXMLDocument(input, 2);
    }
    

    public final static byte[] Prolog = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes();

    public static void main(String...args) {
        System.out.printf("prolog %d%n", Prolog.length);
        XML.Node xml = new XML.Node("tag", XML.Node.EndTagDisposition.ABBREVIABLE);
        System.out.printf("%s %d %d%n", xml, xml.streamLength(), xml.toString().length());

        xml.append(new XML.Node("inner", XML.Node.EndTagDisposition.FORBIDDEN));
        XML.Document document = new XML.Document(xml);

//        System.out.printf("%s %d %d%n", document, document.streamLength(), document.toString().length());
    }
}
