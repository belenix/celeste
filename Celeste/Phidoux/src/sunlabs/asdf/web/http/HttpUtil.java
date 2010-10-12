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
package sunlabs.asdf.web.http;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A collection of utility functions for HTTP protocol processing.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public final class HttpUtil {
    /** Constant byte array for a carriage-return-new-line sequence */
    public final static byte[] CRNL = "\r\n".getBytes();
    public final static byte[] SPACE = " ".getBytes();
    public final static byte[] EQUAL = "=".getBytes();
    public final static byte[] QUOTE = "\"".getBytes();

    public static void transferTo(ReadableByteChannel inChannel, WritableByteChannel outChannel, int bufferSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        while (inChannel.read(buffer) > 0) {
            buffer.flip();
            outChannel.write(buffer);
            // Check if all bytes were written
            if (buffer.hasRemaining()) {
                // If not all bytes were written, move the unwritten bytes
                // to the beginning and set position just after the last
                // unwritten byte; also set limit to the capacity
                buffer.compact();
            } else {
                // Set the position to 0 and the limit to capacity
                buffer.clear();
            }
        }
    }

    /**
     * Copy byte from the InputStream to the OutputStream, using a buffer of {@code bufferSize} bytes, returning the number of bytes transfered.
     * 
     * @param in the InputStream to read data.
     * @param out the OutputStream to write data.
     * @param bufferSize the number of bytes for the intermediate buffer.
     * @return the number of bytes transfered.
     * @throws IOException if either the {@link InputStream} or {@link OutputStream} throw {@code IOException}.
     */
    public static long transferTo(InputStream in, OutputStream out, int bufferSize) throws IOException {

        byte[] buffer = new byte[bufferSize];
        int nread = 0;
        long count = 0;

        while (true) {
            nread = in.read(buffer);
            if (nread < 1)
                break;
            out.write(buffer, 0, nread);
            count += nread;
        }
        return count;
    }

    /**
     * Copy {@code limit} number of bytes from the {@link InputStream} to the
     * {@link OutputStream} using an intermediate buffer of {@code bufferLength} in
     * size. If {@code limit} is -1, then copy until the {@link InputStream} indicates
     * end-of-file.
     * 
     * @param in
     * @param out
     * @param bufferSize
     * @param limit
     * @throws IOException
     */
    public static long transferTo(InputStream in, OutputStream out, int bufferSize, long limit) throws IOException {
        if (limit == -1)
            return HttpUtil.transferTo(in, out, bufferSize);

        byte[] buffer = new byte[bufferSize];
        int nread = 0;
        long count = 0;
        int toRead = (int) Math.min(Math.min(limit, bufferSize), Integer.MAX_VALUE);
        //System.out.println("bufferSize=" + bufferSize + " limit=" + limit + " max=" + Integer.MAX_VALUE + " toRead=" + toRead);
        while ((nread = in.read(buffer, 0, toRead)) > 0) {
            out.write(buffer, 0, nread);
            count += nread;
            limit -= nread;

            toRead = (int) Math.min(Math.min(limit, bufferSize), Integer.MAX_VALUE);
            //System.out.println("bufferSize=" + bufferSize + " limit=" + limit + " max=" + Integer.MAX_VALUE + " toRead=" + toRead);
        }
        return count;
    }

    /**
     * Transfer bytes from the given {@link InputStream} {@code in} to the
     * {@link OutputStream} {@code output} looking for the byte {@code sequence}
     * to terminate the transfer.
     * The terminating sequence IS included in the transfer.
     * 
     * @throws IOException if an IOException (apart from EOF) is encountered while reading from {@code in}.
     * @throws EOFException if an EOF is encountered while reading from {@code in}.
     */
    public static long transferToUntilIncludedSequence(InputStream in, final byte[] sequence, OutputStream out) throws EOFException, IOException {
        byte[] b = new byte[1];
        int sequencePosition = 0;
        long count = 0;

        while (in.read(b) == 1) {
            out.write(b[0]);
            count++;
            if (b[0] == sequence[sequencePosition]) {
                sequencePosition++;
            } else {
                sequencePosition = 0;
            }
            if (sequencePosition >= sequence.length) {
                return count;
            }
        }
        throw new EOFException("EOF");
    }

    /**
     * Transfer bytes from the given {@link InputStream} {@code in} to the
     * {@link OutputStream} {@code output} looking for the byte {@code sequence}
     * to terminate the transfer.  The terminating sequence is NOT included in
     * the transfer.
     * 
     * @param in
     * @param sequence
     * @param output
     * @throws IOException
     */
    public static long transferToUntilExcludedSequence(InputStream in, final byte[] sequence, OutputStream output) throws EOFException, IOException {
        byte[] b = new byte[1];
        int sequencePosition = 0;
        long count = 0;

        while (in.read(b) == 1) {
            if (b[0] == sequence[sequencePosition]) {
                sequencePosition++;
            } else {
                output.write(sequence, 0, sequencePosition);
                count += sequencePosition;
                sequencePosition = 0; // reset this index to start the comparison over
                if (b[0] == sequence[sequencePosition]) {
                    sequencePosition++;
                } else {
                    output.write(b[0]);
                    count++;
                }
            }
            // If we have seen the entire length of the bytes to exclude, we are done.
            if (sequencePosition >= sequence.length) {
                return count;
            }
        }
        throw new EOFException("EOF");
    }

    public static String readLine(String prompt) throws IOException {	    
        InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        BufferedReader stdin = new BufferedReader(inputStreamReader);
        System.out.print(prompt + "> ");
        System.out.flush();
        return stdin.readLine();
    }

    public static String printByteArray(byte[] b) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            String s;
            int i;

            for (i = 0; i < b.length; i++) {
                s = Byte.toString(b[i]) + " ";
                os.write(s.getBytes());
                //if ((i+1) % 16 == 0)
                //    os.write("\n".getBytes());
            }

            //if ((i+1) % 16 != 0)
            //    os.write("\n".getBytes());

            return os.toString();
        } catch (IOException e) {
            return "printByteArray: error";
        }
    }

    public static String prettyPrint(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte caret = '^';

        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\n') {
                out.write(bytes[i]);
            } else if (bytes[i] < ' ') {
                out.write(caret);
                out.write(bytes[i] + 0x40);
            } else if (bytes[i] == '^') {
                out.write(caret);
                out.write(caret);
            } else {
                out.write(bytes[i]);
            }
        }
        return out.toString();
    }

    public static String prettyFormat(byte[] bytes, int width) {
        StringBuilder result = new StringBuilder();
        if (bytes != null) {
            for (int i = 0; i < bytes.length; i += width) {
                StringBuilder line = new StringBuilder();
                StringBuilder ascii = new StringBuilder();
                for (int j = i; j < (i + width) && j < bytes.length; j++) {
                    line.append(String.format("%02x ", bytes[j]));
                    ascii.append(String.format("%c", (bytes[j] >= ' ' && bytes[j] < 127) ? bytes[j] : '.'));    			
                }
                result.append(String.format("%-" + (width*3) + "s", line)).append(" ").append(ascii).append("\n");
            }
        } else {
            result.append("null");
        }
        return result.toString();
    }

    /**
     * Parse an URL/URI query string consisting of attribute=value pairs separated by ampersands, into a map of the attribute name mapped to their values.
     * The presence of an attribute with no value, maps to the empty string.
     * 
     * @throws UnsupportedEncodingException
     */
    public static Map<String,String> parseURLQuery(String query) throws UnsupportedEncodingException {
        if (query == null)
            return null;
        
        Map<String,String> result = new HashMap<String,String>();
        if (query != null) {
            query = URLDecoder.decode(query, "8859_1");
            String[] st = query.split("&");

            for (int i = 0; i < st.length; i++) {
                String[] av = st[i].split("=", 2);
                String key = av[0];
                String value = (av.length > 1) ? av[1] : "";
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * <p>
     *
     * Encapsulates path name semantics without incorporating behavior associated
     * with entities such as files or directories that a path name might
     * represent.
     *
     * </p><p>
     *
     * Each {@code PathName} object is immutable and holds a canonicalized
     * representation of a path through a hierarchical name space.  The successive
     * positions in the path are called <em>components</em>.  In the canonical
     * representation, path components are separated by {@code '/'} characters
     * (which implies that components themselves may not contain that character).
     * Canonicalization proceeds as follows:
     *
     * </p><ol>
     *
     * <li>
     *
     * Empty components are replaced with components named {@code "."}.
     *
     * </li><li>
     *
     * Occurrences of {@code "./"} are removed until none are left.
     *
     * </li><li>
     *
     * If the original path started with a {@code '/'} character (that is, it was
     * an <em>absolute</em> path rather than a <em>relative</em> path) any leading
     * sequence of {@code ".."}  components is removed.
     *
     * </li><li>
     *
     * Component pairs consisting of {@code ".."}  directly following a component
     * that is not {@code ".."}  are removed (along with their separating {@code
     * '/'} character) until there are no such pairs remaining.
     *
     * </li><li>
     *
     * If the original path was absolute, a {@code '/'} character is prepended to
     * the resulting path, so that absoluteness is preserved.  Otherwise, if the
     * resulting (relative) path is empty, it is replaced with {@code "."}.
     *
     * </li>
     *
     * </ol><p>
     *
     * Note that these rules imply that the canonical representation ends with a
     * {@code '/'} character only if it is absolute and has no components (that
     * is, if it refers to the <em>root</em> of the path hierarchy).
     *
     * </p>
     */
    //
    // XXX: The rules given above allow absolute paths containing ".." components
    //          at the beginning.  Should the rules be augmented to squash such
    //          components out?  (I.e., should they enforce ".." == "." at the root?)
    //
    public static class PathName1 implements Serializable, Comparable<PathName1>, Iterable<PathName1> {
        private static final long serialVersionUID = 0L;

        /**
         * <p>
         * Provides iterators that deliver a sequence of path names, one for each
         * component of the corresponding base {@code PathName} instance.
         * </p><p>
         * The iterators that this class provides are not thread safe; if multiple
         * threads are to share a given iterator, they must provide their own
         * synchronization to coordinate their use of that iterator.
         * </p>
         */
        public class PathIterator implements Iterator<PathName1> {
            private int index = 0;
            private String[] components = PathName1.this.getComponents();
            private StringBuilder accumulatingPath = new StringBuilder();

            /**
             * Creates an iterator for the containing {@code PathName} instance.
             */
            public PathIterator() {
            }

            public boolean hasNext() {
                return this.index < this.components.length;
            }

            public PathName1 next() {
                //
                // If the base path name is absolute avoid adding a separator
                // immediately after the root component, which is itself
                // represented by a separator.  Also, don't add a separator at the
                // front of the entire path.
                //
                if ((this.index == 1 && !PathName1.this.isAbsolute()) || index > 1)
                    this.accumulatingPath.append("/");
                this.accumulatingPath.append(this.components[index]);
                this.index += 1;
                return new PathName1(this.accumulatingPath.toString());
            }

            /**
             * {@code PathIterator} does not support the {@code remove()}
             * operation.
             *
             * @throws UnsupportedOperationException always
             */
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        private final String pathName;

        /**
         * An absolute path name referring to the base of the hierarchy of path
         * name components.
         */
        //        public final static PathName ROOT = new PathName("/");

        /**
         * Create a new path name, converting {@code rawPath} into canonical form.
         *
         * @param rawPath   a string representing a path name
         *
         * @throws IllegalArgumentException
         *      if {@code rawPath} is {@code null}
         */
        public PathName1(String rawPath) {
            if (rawPath == null)
                throw new IllegalArgumentException("rawPath must be non-null");
            this.pathName = PathName1.canonicalize(rawPath);
        }

        public PathName1(URI uri) {
            this.pathName = PathName1.canonicalize(uri.getPath());
        }

        /**
         * Return a path name whose component sequence consists of this path
         * name's sequence extended with {@code component}.
         *
         * @throws IllegalArgumentException
         *      if {@code component} is {@code null} or contains a separator
         *      character
         */
        public PathName1 appendComponent(String component) {
            if (component == null || component.indexOf("/") != -1)
                throw new IllegalArgumentException("component null or contains separator");
            return new PathName1(String.format("%s%s%s", this.pathName, "/", component));
        }

        /**
         * Return a path name that resolves this path name against {@code
         * basePath}, which must be absolute.  The resulting path is absolute.  If
         * this path is absolute, it is the resolved result.  Otherwise, {@code
         * basePath} becomes the starting point for the additional path components
         * contained in this path and the resolved result is the canonicalization
         * of the composite sequence of components.
         *
         * @param basePath  an absolute path to be used as the starting point for
         *                  resolution if this path name is not absolute
         *
         * @return  an absolute path leading to the same component that this
         *          path leads to
         *
         * @throws IllegalArgumentException
         *      if {@code basePath} is not absolute
         */
        public PathName1 resolve(PathName1 basePath) {
            if (!basePath.isAbsolute())
                throw new IllegalArgumentException(String.format("basePath \"%s\" must be absolute", basePath));
            if (this.isAbsolute())
                return this;
            StringBuilder sb = new StringBuilder(basePath.toString()).append("/").append(this.toString());
            return new PathName1(sb.toString());
        }

        /**
         * Provided that both this path and {@code descendant} are absolute
         * and that this path name is an ancestor of {@code descendant},
         * returns a relative path name that refers to the same location as {@code
         * descendant}.  That is, for a successful invocation, the following
         * relationship holds:
         *
         * <pre>
         *      this.resolve(this.relativize(descendant).equals(descendant)</pre>
         *
         * @param descendant    a relative {@code Pathname} such that {@code
         *                      this.isAncestor(descendant)} holds
         *
         * @return  a relative path name that, starting from this path name, leads
         *          to the same location as {@code descendant}
         *
         * @throws IllegalArgumentException
         *         if either of this path name or {@code descendant} is not an
         *         absolute path or if this path name is not and ancestor of
         *         {@code descendant}
         */
        public PathName1 relativize(PathName1 descendant) {
            if (descendant == null)
                throw new IllegalArgumentException("argument must be non-null and absolute");
            if (!this.isAbsolute() || !descendant.isAbsolute())
                throw new IllegalArgumentException("arguments must be and absolute");
            if (!this.isAncestor(descendant))
                throw new IllegalArgumentException("this path must be ancestor of descendant");

            int thisLength = this.getComponents().length;
            String[] descendantComponents = descendant.getComponents();
            StringBuilder sb = new StringBuilder(".");
            for (int i = thisLength; i < descendantComponents.length; i++) {
                sb.append("/").append(descendantComponents[i]);
            }
            return new PathName1(sb.toString());
        }

        /**
         * Return {@code true} if this path is absolute and {@code false}
         * otherwise.
         */
        public boolean isAbsolute() {
            return pathName.startsWith("/");
        }

        /**
         * Return the extension part of the final component of this path name (the
         * suffix following its final {@code '.'} character).  If the extension is
         * missing, return the empty string.
         *
         * @return  the final comnponent's extension.
         */
        public String getExtension() {
            String component = this.getLeafComponent();
            int dotLoc = component.lastIndexOf('.');
            if (dotLoc == -1 || dotLoc == component.length() - 1)
                return "";
            return component.substring(dotLoc + 1);
        }

        /**
         * Return this path name's components.   The components are returned in a
         * newly created array, with the final (leaf) component in the
         * highest-indexed position.  The leading (root) component of an absolute
         * path is represented by the string {@code "/"}.
         *
         * @return  an array of the path's components
         */
        //
        // XXX: Write some unit tests for this method.
        //
        public String[] getComponents() {
            //
            // Create a fresh copy (via String.split()), so that callers can't
            // corrupt each others' results.
            //
            String[] components = this.pathName.split(Pattern.quote("/"));
            if (this.isAbsolute()) {
                //
                // Ensure that the first component holds the root's
                // representation.
                //
                if (components.length == 0) {
                    //
                    // The path was "/".
                    //
                    components = new String[] { "/" };
                } else {
                    assert components[0].equals("");
                    //
                    // Replace the empty first component with the root's
                    // representation.
                    //
                    components[0] = "/";
                }
            }
            return components;
        }

        /**
         * Return the final (leaf) component of this path name.
         *
         * @return  this path name's leaf component
         */
        public String getLeafComponent() {
            String[] components = this.getComponents();
            int len = components.length;
            if (len == 0)
                return "/";
            return components[len - 1];
        }

        /**
         * Returns the last component of this path name.
         *
         * @return  this path name's last (leaf) component
         *
         * @see #getLeafComponent()
         */
        public String getBaseName() {
            return this.getLeafComponent();
        }

        /**
         * Returns a new {@code PathName} consisting of the parent directory of
         * this path name.
         *
         * @see #trimLeafComponent()
         */
        public PathName1 getDirName() {
            return this.trimLeafComponent();
        }

        /**
         * <p>
         *
         * Return a path name that leads up to, but does not include, this path
         * name's leaf component.
         *
         * <p><p>
         *
         * The result is formed by appending "/.." to this path name's canonical
         * string representation and then applying the canonicalization rules
         * described in the
         * {@link PathName class comment}.
         *
         * </p>
         *
         * @return  a path name resulting from trimming the final component from
         *          this path name
         */
        public PathName1 trimLeafComponent() {
            return new PathName1(String.format("%s%s%s", this.toString(), "/", ".."));
        }

        /**
         * Returns a set formed from members of {@code candidates} that are proper
         * descendants of this path name (that is, paths that extend this path
         * with one or more additional components).  If {@code directOnly} is set,
         * only direct descendants are included in the set (that is, ones that
         * extend this path name with a single component).
         *
         * @param candidates    the set from which members of the descendant set are drawn
         * @param directOnly    if {@code true}, restrict the members of the
         *                      returned set to direct descendants of this path
         *                      name
         *
         * @return a new set whose members are descendants of this path name
         *
         * @throws IllegalArgumentException
         *         if {@code candidates} is {@code null}
         */
        public SortedSet<PathName1> getDescendants(SortedSet<PathName1> candidates, boolean directOnly) {
            if (candidates == null)
                throw new IllegalArgumentException("candidates must be non-null");

            SortedSet<PathName1> descendants = new TreeSet<PathName1>();
            final int thisLength = this.getComponents().length;
            for (PathName1 path : candidates) {
                if (!this.isAncestor(path))
                    continue;
                if (directOnly && path.getComponents().length != thisLength + 1)
                    continue;
                descendants.add(path);
            }

            return descendants;
        }

        /**
         * Return the canonical string representation of this path name.
         *
         * @return this path name's canonical string representation
         */
        @Override
        public String toString() {
            return this.pathName;
        }

        /**
         * Two {@code PathName}s are equal if and only if their canonical {@code
         * String} representations are equal.
         */
        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof PathName1))
                return false;
            String otherPath = ((PathName1)other).pathName;
            return this.pathName.equals(otherPath);
        }

        @Override
        public int hashCode() {
            return this.pathName.hashCode();
        }

        /**
         * <p>
         *
         * Returns {@code true} if this path name is an ancestor of {@code other}.
         *
         * </p><p>
         *
         * One path is an ancestor of another if all of its components are equal to
         * the corresponding components of the other path.  (Thus, a path is an
         * ancestor of itself.)  Note that the comparison is strictly syntactic and
         * always returns {@code false} when one path is absolute and the other is
         * not.
         *
         * </p>
         *
         * @param other the path to be tested as a descendant of this path
         *
         * @return {@code true} if this path is an ancestor of {@code other}
         *
         * @throws IllegalArgumentExcaption
         *         if {@code other} is {@code null}
         */
        public boolean isAncestor(PathName1 other) {
            if (other == null)
                throw new IllegalArgumentException("argument must be non-null");

            //
            // Ensure we're comparing apples to apples.
            //
            if (this.isAbsolute() != other.isAbsolute())
                return false;

            final String[] thisComponents = this.getComponents();
            final String[] otherComponents = other.getComponents();
            int minComponents =
                Math.min(thisComponents.length, otherComponents.length);
            for (int i = 0; i < minComponents; i++) {
                if (!thisComponents[i].equals(otherComponents[i]))
                    return false;
            }
            return thisComponents.length <= otherComponents.length;
        }

        /**
         * Compares this {@code PathName} to {@code other}, performing the
         * comparison on a component by component basis, with absolute paths
         * always comparing less than non-absolute paths.
         *
         * @see #getComponents()
         */
        public int compareTo(PathName1 other) {
            //
            // Absolute path names compare less than relative path names.
            //
            if (this.isAbsolute() != other.isAbsolute()) {
                return this.isAbsolute() ? -1 : 1;
            }

            final String[] thisComponents = this.getComponents();
            final String[] otherComponents = other.getComponents();
            int minComponents =
                Math.min(thisComponents.length, otherComponents.length);
            for (int i = 0; i < minComponents; i++) {
                int compareValue = thisComponents[i].compareTo(otherComponents[i]);
                if (compareValue != 0)
                    return compareValue;
            }
            return thisComponents.length - otherComponents.length;
        }

        /**
         * Returns an iterator that returns a sequence of path names that start at
         * this {@code PathName}'s first component and successively proceed deeper
         * into the path until it finally returns all of this {@code PathName}.
         */
        public Iterator<PathName1> iterator() {
            return new PathIterator();
        }

        //
        // Here's the method that does the heavy lifting that implements the
        // canonicalization rules given in the class javadoc comment.
        //
        private static String canonicalize(String path) {
            boolean isAbsolute = path.startsWith("/");
            //
            // Arrays.asList produces a list that doesn't allow removals, so we
            // need to use it to initialize one that does.  Ick.  (We use lists
            // here to avoid having to write the removal code in line.)
            //
            List<String> components = new ArrayList<String>(Arrays.asList(
                    path.split(Pattern.quote("/"))));
            for (int i = 0; i < components.size(); ) {
                String component = components.get(i);

                //
                // Remove empty components and occurrences of ".".
                //
                if (component == null || component.equals("") ||
                        component.equals(".")) {
                    //
                    // Remove and examine the component that has slid into
                    // position i.  (That is, _don't_ increment i.)
                    //
                    components.remove(i);
                    continue;
                }

                //
                // Replace leading occurrences of "/.." with "/".  (That is,
                // ensure that ".." out of the root leads back to the root.)
                //
                if (i == 0 && isAbsolute && component.equals("..")) {
                    components.remove(i);
                    //
                    // Re-examine position 0.
                    //
                    continue;
                }

                //
                // Remove "<something>/.."
                //
                if (!component.equals("..") && i + 1 < components.size()) {
                    String nextComponent = components.get(i + 1);
                    if (nextComponent.equals("..")) {
                        //
                        // Remove both halves of the pair.
                        //
                        components.remove(i);
                        components.remove(i);
                        //
                        // We might have just removed the first ".." of a "../.."
                        // pair.  If so, we need to back up one component, so that
                        // the second ".." can be examined for stripping
                        // eligibility.
                        //
                        if (i > 0)
                            i--;
                        continue;
                    }
                }

                i++;
            }

            //
            // Turn a degenerate relative path back into ".".
            //
            if (!isAbsolute && components.size() == 0)
                components.add(".");

            StringBuilder sb = new StringBuilder();
            if (isAbsolute)
                sb.append("/");
            for (int i = 0; i < components.size(); i++) {
                sb.append(components.get(i));
                if (i + 1 < components.size())
                    sb.append("/");
            }
            return sb.toString();
        }
    }

    public static class PathName implements Iterable<String> {
        private String separator = "/";

        private Stack<String> path;

        public PathName() {
            this.path = new Stack<String>();
        }

        /**
         * Construct a {@code PathName} from the given String representation.
         * 
         * @param name  the String representation of the {@code PathName}.
         */
        public PathName(String name) {
            this();
            String tokens[] = name.split(this.separator);

            if (tokens.length == 0) {
                this.path.push("");
            } else {
                for (String t : tokens) {
                    if (t.equals("..")) {
                        if (this.path.isEmpty()) {
                            this.path.push(t);
                        } else if (this.path.peek().equals("..")) {
                            this.path.push(t);
                        } else if (this.path.peek().equals("")) { // Root
                            //
                        } else {
                            this.path.pop();
                        }
                    } else if (t.equals(".")) {
                        //
                    } else if (t.equals("")) {
                        if (this.path.isEmpty()) {
                            this.path.push(t);
//                        } else {
//                            if (!this.path.lastElement().equals(""))
//                                this.path.push(t);
                        }
                    } else {
                        this.path.push(t);
                    }
                }
            }
        }

        /**
         * Construct a {@code PathName} from the path component of the given {@link URI}.
         * @param uri the {@code URI} to extract the path component
         */
        public PathName(URI uri) {
            this(uri.getPath());
        }    

        /**
         * Return {@code true} if this path is an absolute path.
         * 
         * @return {@code true} if this path is an absolute path.
         */
        public boolean isAbsolute() {
            return this.path.get(0).equals("");
        }
        
        /**
         * Make this {@code PathName} absolute.
         * An absolute PathName begins with the path separator character.
         * <p>
         * For example:
         * <table>
         * <tr><td><tt>a/b</tt></td><td><tt>/a/b</tt></td></tr>
         * </table>
         * </dl>
         */
        public PathName makeAbsolute() {
            if (!this.path.get(0).equals(""))
                this.path.insertElementAt("", 0);
            return this;
        }

        /**
         * Get a component of a {@code PathName} indexed by {@code index}.
         * 
         * Note that if the PathName is absolute (ie. it starts with the separator character), the <i>0</i>th element is the separator character alone.
         * 
         * @param index
         */
        public String get(int index) {
            String result = this.path.elementAt(index);
            return result.equals("") ? this.separator : result;
        }

        /**
         * Return the tail of the path.
         * For example:
         * <ul>
         * <li>{@code tail("/a/b/c/d")} returns {@code "a/b/c/d"} (absolute paths contain the empty string as the first component).</li>
         * <li>{@code tail("a/b/c/d")} returns "b/c/d".</li>
         * </ul>
         */
        public PathName tail() {
            return this.tail(this.size()-1);
        }

        /**
         * Return the tail of this {@code PathName}, containing at most {@code length} components.
         * 
         * @param length the maximum number of components to leave in the path.
         * @return the tail of this {@code PathName}, containing at most {@code length} components.
         */
        @SuppressWarnings("unchecked")
        public PathName tail(int length) {
            PathName result = new PathName();
            result.path = (Stack<String>) this.path.clone();
            Stack<String> temporary = new Stack<String>();

            while (--length >= 0) {
                temporary.push(result.path.pop());
            }
            result.path = new Stack<String>();
            int l = temporary.size();
            for (int i = 0; i < l; i++) {
                result.path.push(temporary.pop());
            }
            return result;
        }

        /**
         * Return the parent portion of this path.
         * 
         * @return the parent portion of this path.
         */
        public PathName parent() {
            return this.head(this.size()-1);
        }

        /**
         * Truncate this PathName from right to left, returning a new PathName of at most {@code length} components.
         * @param length
         * @return a new PathName of at most {@code length} components.
         */
        @SuppressWarnings("unchecked")
        public PathName head(int length) {
            PathName result = new PathName();
            result.path = (Stack<String>) this.path.clone();
            while (result.path.size() > length) {
                if (result.path.peek().equals(""))
                    break;
                result.path.pop();
            }
            return result;
        }

        /**
         * Convenience method equivalent to {@link #parent()}.
         * @return the parent path of this path.
         */
        public PathName dirName() {
            return this.parent();
        }

        /**
         * Convenience method returning the last component of the {@code PathName}.
         * @return the last component of the {@code PathName}.
         */
        public String baseName() {
            return this.path.lastElement();
        }

        /**
         * Create a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         * 
         * @param name
         * @return a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         */
        public PathName append(String name) {
            PathName newPathName = new PathName(this.toString()).append(new PathName(name));
            return newPathName;
        }

        /**
         * Create a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         * 
         * @param name
         * @return a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         */
        public PathName append(PathName name) {
            PathName newPathName = new PathName(this.toString());
            for (String p : name.path) {
                if (!p.equals(""))
                    newPathName.path.push(p);
            }

            return newPathName;
        }

        /**
         * Get the total number of components in this path.
         * <p>
         * Absolute paths have an initial component consisting of the {@code null} String.
         * As a result the {@code #size()} of the absolute path <tt>/a/b</tt> is 3.
         * </p>
         * 
         * @return the total number of components in this path.
         */
        public int size() {
            return this.path.size();
        }

        public String toString() {
            if (this.path.size() == 0)
                return ".";

            StringBuilder result = new StringBuilder(this.path.get(0).equals("") ? this.separator : "");
            String slash = "";
            for (String p : this.path) {
                if (!p.equals("")) {
                    result.append(slash).append(p);
                    slash = this.separator;
                }
            }

            return result.toString();
        }

        public URI toURI() {
            return URI.create(this.toString());
        }

        public static boolean test(String path, String expectedValue) {
            PathName a = new PathName(path);
            if (a.toString().equals(expectedValue)) {
                System.out.printf("\"%s\" -> \"%s\" pass%n", path, expectedValue);
                return true;
            } else {
                System.out.printf("\"%s\" -> \"%s\" fail (expected \"%s\")%n", path, a.toString(), expectedValue);
                return false;
            }
        }
        public static void main(String[] args) {
            test("/", "/");
            test(".", ".");
            test("./", ".");
            test("./a", "a");
            test("./..", "..");
            test("./../..", "../..");
            test("./../../a/..", "../..");

            test("/a/b", "/a/b");
            test("/a/b/..", "/a");
            test("/a/../", "/");
            test("/a/../..", "/");
            test("/a/b/../../", "/");

            test("a/b//c", "a/b/c");
            test("/a/b//c/", "/a/b/c");


            //            System.out.printf("%s: head(2)='%s' tail(5)='%s' basename='%s' dirname='%s'%n", a, a.head(2), a.tail(5), a.baseName(), a.dirName());
            //            System.out.printf("head(5): %s%n", a.head(5));
            //            a.dump();
            //            a = new PathName("./");
            //            System.out.printf("%s%n", a.toString());
            //            a.dump();
            //            
            //            BinaryIterator bi = new BinaryIterator(32);
            //            int[] signum = {-1, 1, 1, 1, 1, 1};
            //            for (int i = 0; i < signum.length; i++) {
            //                System.out.printf("%d%n", bi.value());
            //                bi.signum(signum[i]);
            //            }
            //            System.out.printf("%d%n", bi.value());
            //            
            //            a = new PathName("a/b/c");
            //            System.out.printf("%s: head(2)='%s' tail(5)='%s' basename='%s' dirname='%s'%n", a, a.head(2), a.tail(5), a.baseName(), a.dirName());
            //            a.dump();


        }

        public Iterator<String> iterator() {
            return this.path.iterator();
        }
    }
}
