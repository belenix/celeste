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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.asdf.web.http.HTTP.Message;
import sunlabs.asdf.web.http.HttpHeader;
import sunlabs.asdf.web.http.HttpMessage;
import sunlabs.asdf.web.http.HttpUtil;
import sunlabs.asdf.web.http.InternetMediaType;
import sunlabs.asdf.web.http.HttpUtil.PathName1;

/**
 * This is an abstract class from which all HTTP message body content handlers are derived.
 *
 * <p>
 * A instance of this class produces output when the {@link #writeTo(DataOutputStream)} method is invoked.
 * </p>
 *
 * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
 */
public abstract class HttpContent implements HTTP.Message.Body {
    
    public static class Application {
        public static class OctetStream extends HttpContent {
        	private final static long serialVersionUID = 1L;

        	private ByteBuffer bytes;
            private int length;

            private InputStream inputStream;

            /**
             * Create an {@code Application.OctetStream} consisting of data in a byte array.
             * @see HttpContent.Application.OctetStream#OctetStream(byte[], int, int)
             * @param bytes The byte array.
             */
            public OctetStream(byte[] bytes) {
                this(bytes, 0, bytes.length);
            }

            /**
             * 
             * Create an {@code Application.OctetStream} consisting of data in a byte array,
             * starting at {@code offset} and continuing for {@code length} bytes.
             * @see HttpContent.Application.OctetStream#OctetStream(byte[])
             * 
             * @param bytes The byte array.
             * @param offset The starting offset in the byte array.
             * @param length The number of bytes.
             */
            public OctetStream(byte[] bytes, int offset, int length) {
                super(new HttpHeader.ContentType(InternetMediaType.Application.OctetStream));
                this.bytes = ByteBuffer.wrap(bytes, offset, length);
                this.length = this.bytes.remaining();
            }

            /**
             * Construct an {@code Application.OctetStream} from the given byte buffer.
             * The stream will incorporate the span of the buffer's bytes starting from its position for {@code buffer.remaining()} bytes.
             * The resulting stream may share storage with {@code buffer}, so that modifications to {@code buffer} may be reflected in the stream.
             *
             * @param buffer    the {@code ByteBuffer} backing the new octet stream
             */
            public OctetStream(ByteBuffer buffer) {
                super(new HttpHeader.ContentType(InternetMediaType.Application.OctetStream));

                this.bytes = buffer.duplicate();
                this.length = this.bytes.remaining();
            }

            /**
             * Construct an {@code Application.OctetStream} containing the serialized form of the given {@link Serializeable} object.
             * 
             * @param obj The object to serialize.
             */
            public OctetStream(Serializable obj) {
                super(new HttpHeader.ContentType(InternetMediaType.Application.OctetStream));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ObjectOutputStream out = new ObjectOutputStream(bos);
                    out.writeObject(obj);
                    out.close();
                } catch (IOException io) {
                    throw new RuntimeException(io);
                }
                this.bytes = ByteBuffer.wrap(bos.toByteArray(), 0, bos.size());
                this.length = this.bytes.remaining();
            }

//            /**
//             * Construct an {@code Application.OctetStream} consisting of {@code length} bytes from an {@link InputStream}.
//             * 
//             * @param obj The object to serialize.
//             */
//            public OctetStream(InputStream inputStream, int length) {
//                super(new HttpHeader.ContentType(InternetMediaType.Application.OctetStream));
//                this.inputStream = inputStream;
//                this.length = length;
//            }

            public long contentLength() {
                return this.length;
            }

            public long writeTo(OutputStream out) throws IOException {
                if (this.inputStream == null) {
                    long length = this.bytes.remaining(); 
                    if (this.bytes.hasArray()) {
                        out.write(this.bytes.array(), this.bytes.arrayOffset() + this.bytes.position(), this.bytes.remaining());
                    } else {
                        while (this.bytes.remaining() > 0) {
                            out.write(this.bytes.get());
                        }
                    }
                    return length;
                }

                return HttpUtil.transferTo(this.inputStream, out, 8192);
            }

            @Override
            public InputStream toInputStream() {
                if (this.inputStream != null)
                    return this.inputStream;
                
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                try {
                    this.writeTo(dos);
                    dos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return new ByteArrayInputStream(bos.toByteArray());
            }

            @Override
            public String toString() {
                if (this.inputStream == null) {
                    return new String(this.bytes.array(), this.bytes.arrayOffset() + this.bytes.position(), this.bytes.remaining());
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                try {
                    this.writeTo(dos);
                    dos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return bos.toString();
            }
        }
    }

    public static class Multipart extends HttpContent implements HTTP.Message.Body.MultiPart {

        public static String generateBoundaryString(String suffix) {
            return "BoUnDaRyStRiNg" + Long.toString(Math.abs(System.currentTimeMillis())) + suffix;
        }

        private byte[] bytes;
        private List<HTTP.Message> list;
        private String boundaryString;

        private Multipart(HTTP.Message.Header.ContentType contentType) {
            super(new HttpHeader.ContentType(InternetMediaType.Multipart.FormData));
            this.list = new LinkedList<HTTP.Message>();
            this.boundaryString = HttpContent.Multipart.generateBoundaryString("HtTpMuLtIpArT");
        }

        private Multipart(HTTP.Message.Header.ContentType contentType, String boundary, InputStream in) throws IOException, HTTP.BadRequestException {
            this(contentType);
            this.list = new ArrayList<HTTP.Message>();
            this.boundaryString = boundary;
            byte[] possibleBoundary = ("\r\n--" + boundary).getBytes();
            byte[] lastSuffix = ("--\r\n").getBytes();

            // Reuse this output stream for each collected message.
            // The HttpMessage() constructor leaves data in the buffer so we rely
            // on the toByteArray() method returning a new buffer each time.
            ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
            ByteArrayOutputStream suffixBuffer = new ByteArrayOutputStream(1024);

            // This is a MIME compliant client-side parser, so we ignore all that's before the initial boundary.
            HttpUtil.transferToUntilIncludedSequence(in, ("--" + boundary + "\r\n").getBytes(), os);
            os.reset();

            while (true) {
                if (HttpUtil.transferToUntilExcludedSequence(in, possibleBoundary, os) == 0) {
                    throw new IOException("Unexpected EOF");
                }

                suffixBuffer.reset();
                if (HttpUtil.transferToUntilIncludedSequence(in, HttpUtil.CRNL, suffixBuffer) == 0) {
                    throw new IOException("Unexpected EOF in boundary suffix");
                }
                byte[] suffix = suffixBuffer.toByteArray();

                if (Arrays.equals(suffix, HttpUtil.CRNL)) {
                    // Got the end of a normal boundary
                    this.add(new HttpMessage(os.toByteArray()));
                    os.reset();
                } else if (Arrays.equals(suffix, lastSuffix)) {
                    // Got the end of a terminal boundary
                    this.add(new HttpMessage(os.toByteArray()));
                    return;
                } else {
                    os.write(possibleBoundary);
                    os.write(suffix);
                }
            }
        }

        public boolean add(HTTP.Message o) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.add(o);
        }

        public void add(int index, HTTP.Message element) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            this.list.add(index, element);
        }

        public boolean addAll(Collection<? extends HTTP.Message> c) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.addAll(c);
        }

        public boolean addAll(int index, Collection<? extends HTTP.Message> c) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.addAll(index, c);
        }

        public void clear() {
            this.bytes = null; // this forces the recomputation of the output bytes.
            this.list.clear();
        }

        public boolean contains(Object o) {
            return this.list.contains(o);
        }

        public boolean containsAll(Collection<?> c) {
            return this.list.containsAll(c);
        }

        @Override
        public boolean equals(Object o) {
            return this.list.equals(o);
        }

        public HTTP.Message get(int index) {
            return this.list.get(index);
        }

        public String getBoundaryString() {
            return this.boundaryString;
        }

        @Override
        public int hashCode() {
            return this.list.hashCode();
        }

        public int indexOf(Object o) {
            return this.list.indexOf(o);
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        public Iterator<HTTP.Message> iterator() {
            return this.list.iterator();
        }

        public int lastIndexOf(Object o) {
            return this.list.lastIndexOf(o);
        }

        public ListIterator<HTTP.Message> listIterator() {
            return this.list.listIterator();
        }

        public ListIterator<HTTP.Message> listIterator(int index) {
            return this.list.listIterator(index);
        }

        public HTTP.Message remove(int index) {
            HTTP.Message result = this.list.get(index);
            this.list.remove(index);
            this.bytes = null; // this forces the recomputation of the output bytes.
            return result;
        }

        public HTTP.Message remove(Object o) {
            this.list.remove(o);
            this.bytes = null; // this forces the recomputation of the output bytes.
            return (HTTP.Message) o;
        }

        public boolean removeAll(Collection<?> c) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.removeAll(c);
        }

        public boolean retainAll(Collection<?> c) {

            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.retainAll(c);
        }

        public HTTP.Message set(int index, HTTP.Message element) {
            this.bytes = null; // this forces the recomputation of the output bytes.
            return this.list.set(index, element);
        }

        public int size() {
            return this.list.size();
        }

        public List<HTTP.Message> subList(int fromIndex, int toIndex) {
            return this.list.subList(fromIndex, toIndex);
        }

        public Object[] toArray() {
            return this.list.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return this.list.toArray(a);
        }

        /**
         * Produce a byte array encoding of the whole message body.
         * If the message body is multipart, separate the parts
         * by the boundary markers, inserting a body part inbetween each.
         */
        protected byte[] toByteArray() {
            if (this.bytes == null) {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                try {
                    this.writeTo(new DataOutputStream(result));
                } catch (IOException cantHappen) {
                    throw new RuntimeException(cantHappen);
                }
                this.bytes = result.toByteArray();
            }
            return this.bytes;
        }

        public ByteBuffer toByteBuffer() {
            return ByteBuffer.wrap(this.toByteArray());
        }

        public long writeTo(OutputStream out) throws IOException {
            long length = 0;
            if (this.size() > 0) {
                byte[] startBoundary = ("\r\n--" + this.getBoundaryString() + "\r\n").getBytes();
                byte[] lastBoundary = ("\r\n--" + this.getBoundaryString() + "--\r\n").getBytes();

                for (HTTP.Message message : this) {
                    out.write(startBoundary, 0, startBoundary.length);
                    length += startBoundary.length;
                    length += message.writeTo(out);
                }
                out.write(lastBoundary, 0, lastBoundary.length);
                length += lastBoundary.length;
            }
            return length;
        }

        public long contentLength() {
            return this.toByteArray().length;
        }

        public InputStream toInputStream() {
            return new ByteArrayInputStream(this.toByteArray());
        }
        
        public static class FormData extends HttpContent.Multipart implements Map<String,HTTP.Message>, HTTP.Message.Body.MultiPart.FormData {
            private static final long serialVersionUID = 1L;
            
            private Map<String,HTTP.Message> map;

            public FormData() {
                super(new HttpHeader.ContentType(InternetMediaType.Multipart.FormData));
                this.map = new HashMap<String,HTTP.Message>();
            }

            /**
             * Decode a String containing x-www-form-url-encoded data.
             *
             * @param encoded The String to decode.
             * @throws UnsupportedEncodingException
             */
            public FormData(String encoded) throws UnsupportedEncodingException {
                this();
                String query = encoded;
                if (query != null) {
                    query = URLDecoder.decode(query, "8859_1");
                    String[] st = query.split("&");

                    for (int i = 0; i < st.length; i++) {
                        String[] av = st[i].split("=", 2);
                        String key = av[0];
                        String value = (av.length > 1) ? av[1] : "";
                        HTTP.Message message = new HttpMessage(new HttpContent.Text.Plain(value));
                        this.put(key, message);
                    }
                }
            }

            /**
             * Construct a new {@link FormData} instance containing 
             * 
             * by parsing the given {@link InputStream}
             * according the format of multipart/form-data using the {@code String} {@code boundary} as the boundary string separating each part.
             * <p>
             * Each part of the input data is expected to have an accompanying {@code Content-Disposition:}
             * header containing the attribute {@code name} which is then used as the key in the resulting
             * Map of parts.
             * </p>
             * @param boundary
             * @param in
             * @throws IOException
             * @throws HTTP.Message.Header.InvalidFormatException
             */
            public FormData(String boundary, InputStream in) throws IOException, HTTP.BadRequestException {
                super(new HttpHeader.ContentType(InternetMediaType.Multipart.FormData), boundary, in);

                this.map = new HashMap<String,HTTP.Message>();
                for (HTTP.Message message : this) {
                    HttpHeader.ContentDisposition contentDisposition = (HttpHeader.ContentDisposition) message.getHeader(HTTP.Message.Header.CONTENTDISPOSITION);
                    if (contentDisposition == null) {
                        throw new HTTP.BadRequestException(HttpHeader.CONTENTDISPOSITION + " missing.");
                    }
                    //String name = contentDisposition.getField("name");
                    //System.out.printf("**************************************** name='%s'%n", name);
                    String name = contentDisposition.getParameter("name").getValue();
//                    System.out.printf("**************************************** name='%s'%n", name);
                    if (name == null)
                        throw new HTTP.BadRequestException(HttpHeader.CONTENTDISPOSITION + " contains no name field.");

                    this.map.put(name, message);
                }
            }

            /**
             * Given URL encoded data, encoded in an real URI, decode the data.
             *
             * @param url The URI to decode.
             * @throws UnsupportedEncodingException
             */
            public FormData(URI url) throws UnsupportedEncodingException {
                this();
                String query = url.getQuery();
                if (query != null) {
                    query = URLDecoder.decode(query, "8859_1");
                    String[] st = query.split("&");

                    for (int i = 0; i < st.length; i++) {
                        String[] av = st[i].split("=", 2);
                        String key = av[0];
                        String value = (av.length > 1) ? av[1] : "";
                        HTTP.Message message = new HttpMessage(new HttpContent.Text.Plain(value));
                        this.put(key, message);
                    }
                }
            }
            
            public Map<String, Message> getMap() {
                return this.map;
            }

            public void clear() {
                super.clear();
                this.map.clear();
            }

            public boolean containsKey(Object key) {
                return this.map.containsKey(key);
            }

            public boolean containsValue(Object value) {
                return this.map.containsValue(value);
            }

            public Set<Map.Entry<String,HTTP.Message>> entrySet() {
                return this.map.entrySet();
            }

            public HTTP.Message get(Object key) {
                return this.map.get(key);
            }

            public boolean isEmpty() {
                return this.map.isEmpty();
            }

            public Set<String> keySet() {
                return this.map.keySet();
            }

            public HTTP.Message put(String key, HTTP.Message message) {
                message.addHeader(new HttpHeader.ContentDisposition("form-data", new HttpHeader.Parameter("name", key)));
                this.add(message);
                return this.map.put(key, message);
            }

            public void putAll(Map<? extends String,? extends HTTP.Message> t) {
                this.map.putAll(t);
            }

            public HTTP.Message remove(Object key) {
                HTTP.Message message = this.get(key);
                super.remove(message);
                return this.map.remove(key);
            }

            public int size() {
                return this.map.size();
            }

            public Collection<HTTP.Message> values() {
                return this.map.values();
            }
        }
    }

    public static class RawByteArray extends HttpContent {
    	private final static long serialVersionUID = 1L;
    	private ByteBuffer bytes;
    	
        private int length;

        public RawByteArray(HTTP.Message.Header.ContentType type, byte[] bytes, int offset, int length) {
            super(type);
            this.bytes = ByteBuffer.wrap(bytes, offset, length);
            this.length = this.bytes.remaining();
        }

        public RawByteArray(HTTP.Message.Header.ContentType type, byte[] bytes) {
            this(type, bytes, 0, bytes.length);
        }

        public RawByteArray(HTTP.Message.Header.ContentType type, String string) {
            this(type, string.getBytes());
        }

        public long contentLength() {
            return this.length;
        }

        public long writeTo(OutputStream out) throws IOException {
            long length = this.bytes.remaining(); 
            ByteBuffer scratch = this.bytes.duplicate();
            if (scratch.hasArray()) {
                out.write(scratch.array(), scratch.arrayOffset() + scratch.position(), scratch.remaining());
            } else {
                while (scratch.remaining() > 0) {
                    out.write(scratch.get());
                }
            }
            return length;
        }

        @Override
        public InputStream toInputStream() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            OutputStream dos = new DataOutputStream(bos);
            try {
                this.writeTo(dos);
                dos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new ByteArrayInputStream(bos.toByteArray());
        }

        @Override
        public String toString() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            OutputStream dos = new DataOutputStream(bos);
            try {
                this.writeTo(dos);
                dos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return bos.toString();
        }
    }

    /**
     * Provide {@link HttpContent} access to an {@link InputStream}.
     */
    public static class RawInputStream extends HttpContent {
    	private final static long serialVersionUID = 1L;
    	
        private InputStream inputStream;
        private long contentLength;
        private byte[] content; // This is non-null when the input stream has read the content.
        private boolean closeInputStream;

        public RawInputStream(HttpHeader.ContentType type, InputStream in, long contentLength) {
            super(type);
            this.inputStream = in;
            this.contentLength = contentLength;            
        }
        
        public RawInputStream(HttpHeader.ContentType type, InputStream in) {
            super(type);
            this.inputStream = in;
            this.contentLength = -1;
        }

        /**
         * Open a file and use it as an {@link InputStream}, stipulating the content-type.
         * <p>
         * If the application that is using this class has been started from a Java jar file,
         * then open the named file via the class loader for the jar.  This
         * means that all accessible "files" are stored in the jar file.
         * Otherwise open the file as a {@link FileInputStream}.
         * </p>
         *
         * @param type
         * @param localRoot
         * @param localPath
         * @throws IOException
         */
        public RawInputStream(HttpHeader.ContentType type, String localRoot, String localPath) throws IOException {
            super(type);

            URL root = this.getClass().getResource("/");

            String absolutePath = "/" + new PathName1(localRoot + new PathName1(localPath).toString()).toString();
//            System.out.printf("%s%n", absolutePath);
//            System.out.printf("%s%n", "/" + localRoot + "/" + localPath);
//            System.out.printf("%s%n", new HttpUtil.PathName(localRoot).add(new HttpUtil.PathName(localPath)));
            
//            String absolutePath = "/" + localRoot + "/" + localPath;

            if (root == null) { // We are inside of a .jar file.
//                System.out.printf("HttpContent.RawInputStream: inside jar file. path='%s' absolutePath='%s'%n", localPath, absolutePath);
                this.inputStream = this.getClass().getResourceAsStream(absolutePath);
                if (this.inputStream == null) {
                    throw new FileNotFoundException(absolutePath);
                }
                this.contentLength = this.inputStream.available();
            } else {
                File file = new File(new File(localRoot).getAbsolutePath(), new PathName1(localPath).toString());
                this.inputStream = new FileInputStream(file);
                this.contentLength = file.length();
            }
        }

        /**
         * Open a file and use it as an InputStream.
         * <p>
         * If the application that is using this class has been started from a Java jar file,
         * then open the named file via the class loader for the jar.  This
         * means that all accessible "files" are stored in the jar file.
         * Otherwise open the file as a {@link FileInputStream}.
         * </p>
         *
         * @param localRoot The full pathname of the directory of {@code localFile}
         * @param localPath The file name of the file to use.
         * @throws IOException
         */
        public RawInputStream(String localRoot, String localPath) throws IOException {
            this(new HttpHeader.ContentType(InternetMediaType.getByFileExtension(InternetMediaType.getExtension(localPath))), localRoot, localPath);
        }

        public long contentLength() {
            return this.contentLength;
        }

        /**
         * If set to {@code true} the {@link InputStream} is closed after it has reached EOF or {@link #contentLength} bytes have been read.
         * @param b
         */
        public void setCloseInputStream(boolean b) {
            this.closeInputStream = b;
        }

        @Override
        public void setContentLength(long length) {
            this.contentLength = length;
        }

        /**
         * Copy the data from the {@link InputStream} to the given {@link DataOutputStream}.
         * <p>
         * This method does not store the data read from the {@link InputStream}.
         * If, however, the data has been read from the {@link InputStream} as the result
         * of an earlier invocation of {@link #toByteArray()} or {@link #toByteBuffer()},
         * the previously allocated byte array is written to the {@link DataOutputStream}.
         * </p>
         * <p>
         * After copying {@code #contentLength()} bytes, the {@link InputStream} is closed.
         * </p>
         */
        public long writeTo(OutputStream out) throws IOException {
            if (this.content == null) {
                long length = HttpUtil.transferTo(this.toInputStream(), out, HttpContent.BUFFERSIZE, this.contentLength());
                if (this.closeInputStream) {
                    System.out.printf("HttpContent.RawInputStream: closing input stream upon finishing output.  This is wasteful.%n");
                    this.inputStream.close();
                }
                return length;
            }
            out.write(this.content);
            return this.content.length;
        }

        /**
         * Return the body as a byte array.
         * <p>
         * {@code contentLength} number of bytes is read from {@link InputStream} and are stored in a byte array.
         * {@link InputStream} is closed once the data is read.
         * </p>
         * <p>
         * Subsequent calls to this method will return the previously stored byte array.
         * </p>
         */
        private byte[] toByteArray() {
        	try {
        		ByteArrayOutputStream os = new ByteArrayOutputStream();
        		this.writeTo(os);
        		this.content = os.toByteArray();
        	} catch (IOException e) {
        		throw new RuntimeException(e);
        	}

        	return this.content;
        }

        @Override
        public InputStream toInputStream() {
            if (this.content == null) {
                return this.inputStream;
            }
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public String toString() {
            return new String(this.toByteArray());
        }
    }
    
    public static class TransferEncodedInputStream extends HttpContent {
        private static final long serialVersionUID = 1L;

        private long contentLength;
        private HttpHeader.TransferEncoding transferEncoding;
        private InputStream input;

        public TransferEncodedInputStream(HttpHeader.ContentType type, InputStream in, HttpHeader.TransferEncoding transferEncoding) {
            super(type);
            System.out.printf("TransferEncodedInputStream%n");
            this.transferEncoding = transferEncoding;
            this.input = in;
            this.contentLength = -1;
        }

        public long contentLength() {
            return this.contentLength;
        }

        @Override
        public InputStream toInputStream() {
            return null;
        }

        public long writeTo(OutputStream out) throws IOException {
            return 0;
        }
    }

    /**
     * Construct an HTTP message body of the type {@code text/plain}.
     */
    public static class Text {
        public static class HTML extends HttpContent {
        	private final static long serialVersionUID = 1L;
        	
            private XHTML.Document xhtml;

            public HTML(XHTML.Document html) {
                super(new HttpHeader.ContentType(InternetMediaType.Text.HTML));
                this.xhtml = html;
            }

            public long contentLength() {
                return this.xhtml.streamLength();
            }

            public long writeTo(OutputStream out) throws IOException {
                return this.xhtml.streamTo(out);
            }

            @Override
            public InputStream toInputStream() {
                return new ByteArrayInputStream(this.xhtml.toString().getBytes());
            }

            @Override
            public String toString() {
                return this.xhtml.toString();
            }
        }

        public static class Plain extends HttpContent {
        	private final static long serialVersionUID = 1L;
        	
            private String plain;

            /**
             * Construct a new Plain instance containing the value of the given {@link Object} {@code o} as produced by {@link String.valueOf(Object o)}.
             * <p>
             * If the Object o is null, the value is the empty String {@code ""}, not the String {@code "null"}
             * </p>
             * @param o
             */
            public Plain(Object o) {
                super(new HttpHeader.ContentType(InternetMediaType.Text.Plain));
                this.plain = o == null ? "" : String.valueOf(o);
            }

            public Plain(String format, Object... o) {
                super(new HttpHeader.ContentType(InternetMediaType.Text.Plain));
                this.plain = String.format(format, o);
            }

            public long contentLength() {
                return this.plain.getBytes().length;
            }

            public long writeTo(OutputStream out) throws IOException {
                byte[] bytes = this.plain.getBytes();
                out.write(bytes);
                return bytes.length;
            }

            @Override
            public InputStream toInputStream() {
                return new ByteArrayInputStream(this.plain.getBytes());
            }

            @Override
            public String toString() {
                return this.plain;
            }
        }

        public static class XML extends HttpContent {
        	private final static long serialVersionUID = 1L;

        	private String xml;

            public XML(sunlabs.asdf.web.XML.XML.Document document) {
                super(new HttpHeader.ContentType(InternetMediaType.Text.XML));
                this.xml = sunlabs.asdf.web.XML.XML.formatXMLDocument(document.toString());
            }
            
            public XML(String xmlString) {
                super(new HttpHeader.ContentType(InternetMediaType.Text.XML));
                this.xml = xmlString;
            }

            public long contentLength() {
                return this.xml.length();
            }

            public long writeTo(OutputStream out) throws IOException {
                out.write(this.xml.getBytes());
                return this.xml.length();
            }

            @Override
            public InputStream toInputStream() {
                return new ByteArrayInputStream(this.xml.getBytes());
            }

            @Override
            public String toString() {
                return this.xml;
            }
        }
    }

    /**
     * The size of the buffer used to copy the contents to the {@link OutputStream}.
     */
    public static int BUFFERSIZE = 64 * 1024;

    protected HTTP.Message.Header.ContentType contentType;

    protected HttpContent(HTTP.Message.Header.ContentType contentType) {
        this.contentType = contentType;
    }
    
    /**
     * Write this content to the {@link OutputStream} {@code out}.
     */
    abstract public long writeTo(OutputStream out) throws IOException;

    public HTTP.Message.Header.ContentType getContentType() {
        return this.contentType;
    }
    
    public void setContentType(HTTP.Message.Header.ContentType type) {
        this.contentType = type;
    }

    /**
     * Specify the Content-Length of the body.
     * <p>
     * </p>
     *
     * @param length
     */
    @Deprecated
    public void setContentLength(long length) {
        throw new RuntimeException("Cannot set contentLength");
    }

    /**
     * Produce an {@link InputStream} instance from which the HTTP content may be read.
     */
    abstract public InputStream toInputStream();
}
