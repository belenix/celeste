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

package sunlabs.celeste.client.filesystem;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

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
//      at the beginning.  Should the rules be augmented to squash such
//      components out?  (I.e., should they enforce ".." == "." at the root?)
//
public class PathName implements Serializable, Comparable<PathName>,
        Iterable<PathName> {
    private static final long serialVersionUID = 0L;

    /**
     * <p>
     *
     * Provides iterators that deliver a sequence of path names, one for each
     * component of the corresponding base {@code PathName} instance.
     *
     * </p><p>
     *
     * The iterators that this class provides are not thread safe; if multiple
     * threads are to share a given iterator, they must provide their own
     * synchronization to coordinate their use of that iterator.
     *
     * </p>
     */
    public class PathIterator implements Iterator<PathName> {
        private int index = 0;
        private String[] components = PathName.this.getComponents();
        private StringBuilder accumulatingPath = new StringBuilder();

        /**
         * Creates an iterator for the containing {@code PathName} instance.
         */
        public PathIterator() {
        }

        public boolean hasNext() {
            return this.index < this.components.length;
        }

        public PathName next() {
            //
            // If the base path name is absolute avoid adding a separator
            // immediately after the root component, which is itself
            // represented by a separator.  Also, don't add a separator at the
            // front of the entire path.
            //
            if ((this.index == 1 && !PathName.this.isAbsolute()) || index > 1)
                this.accumulatingPath.append(PathName.separator);
            this.accumulatingPath.append(this.components[index]);
            this.index += 1;
            return new PathName(this.accumulatingPath.toString());
        }

        /**
         * {@code PathIterator} does not support the {@code remove()}
         * operation.
         *
         * @throws UnsupportedOperationException
         *         always
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final static String separator = "/";

    private final String pathName;

    /**
     * An absolute path name referring to the base of the hierarchy of path
     * name components.
     */
    public final static PathName ROOT = new PathName(separator);

    /**
     * Create a new path name, converting {@code rawPath} into canonical form.
     *
     * @param rawPath   a string representing a path name
     *
     * @throws IllegalArgumentException
     *      if {@code rawPath} is {@code null}
     */
    public PathName(String rawPath) {
        if (rawPath == null)
            throw new IllegalArgumentException("rawPath must be non-null");
        this.pathName = PathName.canonicalize(rawPath);
    }

    /**
     * Return a path name whose component sequence consists of this path
     * name's sequence extended with {@code component}.
     *
     * @throws IllegalArgumentException
     *      if {@code component} is {@code null} or contains a separator
     *      character
     */
    public PathName appendComponent(String component) {
        if (component == null || component.indexOf(separator) != -1)
            throw new IllegalArgumentException(
                "component null or contains separator");
        return new PathName(
            String.format("%s%s%s", this.pathName, separator, component));
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
    public PathName resolve(PathName basePath) {
        if (!basePath.isAbsolute())
            throw new IllegalArgumentException(String.format(
                "basePath \"%s\" must be absolute", basePath));
        if (this.isAbsolute())
            return this;
        StringBuilder sb = new StringBuilder(
            basePath.toString()).append(separator).append(this.toString());
        return new PathName(sb.toString());
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
    public PathName relativize(PathName descendant) {
        if (descendant == null)
            throw new IllegalArgumentException(
                "argument must be non-null and absolute");
        if (!this.isAbsolute() || !descendant.isAbsolute())
            throw new IllegalArgumentException(
                "arguments must be and absolute");
        if (!this.isAncestor(descendant))
            throw new IllegalArgumentException(
                "this path must be ancestor of descendant");

        int thisLength = this.getComponents().length;
        String[] descendantComponents = descendant.getComponents();
        StringBuilder sb = new StringBuilder(".");
        for (int i = thisLength; i < descendantComponents.length; i++) {
            sb.append("/").append(descendantComponents[i]);
        }
        return new PathName(sb.toString());
    }

    /**
     * Return {@code true} if this path is absolute and {@code false}
     * otherwise.
     */
    public boolean isAbsolute() {
        return pathName.startsWith(separator);
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
        String[] components = this.pathName.split(Pattern.quote(separator));
        if (this.isAbsolute()) {
            //
            // Ensure that the first component holds the root's
            // representation.
            //
            if (components.length == 0) {
                //
                // The path was "/".
                //
                components = new String[] { separator };
            } else {
                assert components[0].equals("");
                //
                // Replace the empty first component with the root's
                // representation.
                //
                components[0] = separator;
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
            return separator;
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
    public PathName getDirName() {
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
     * {@link sunlabs.celeste.client.filesystem.PathName class comment}.
     *
     * </p>
     *
     * @return  a path name resulting from trimming the final component from
     *          this path name
     */
    public PathName trimLeafComponent() {
        return new PathName(String.format("%s%s%s",
            this.toString(), separator, ".."));
    }

    /**
     * Returns a set formed from members of {@code candidates} that are proper
     * descendants of this path name (that is, paths that extend this path
     * with one or more additional components).  If {@code directOnly} is set,
     * only direct descendants are included in the set (that is, ones that
     * extend this path name with a single component).
     *
     * @param candidates    the set from which members of the descendant set
     *                      are drawn
     * @param directOnly    if {@code true}, restrict the members of the
     *                      returned set to direct descendants of this path
     *                      name
     *
     * @return a new set whose members are descendants of this path name
     *
     * @throws IllegalArgumentException
     *         if {@code candidates} is {@code null}
     */
    public SortedSet<PathName> getDescendants(SortedSet<PathName> candidates,
            boolean directOnly) {
        if (candidates == null)
            throw new IllegalArgumentException("candidates must be non-null");

        SortedSet<PathName> descendants = new TreeSet<PathName>();
        final int thisLength = this.getComponents().length;
        for (PathName path : candidates) {
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
        if (other == null || !(other instanceof PathName))
            return false;
        String otherPath = ((PathName)other).pathName;
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
    public boolean isAncestor(PathName other) {
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
    public int compareTo(PathName other) {
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
    public Iterator<PathName> iterator() {
        return new PathIterator();
    }

    //
    // Here's the method that does the heavy lifting that implements the
    // canonicalization rules given in the class javadoc comment.
    //
    private static String canonicalize(String path) {
        boolean isAbsolute = path.startsWith(separator);
        //
        // Arrays.asList produces a list that doesn't allow removals, so we
        // need to use it to initialize one that does.  Ick.  (We use lists
        // here to avoid having to write the removal code in line.)
        //
        List<String> components = new ArrayList<String>(Arrays.asList(
            path.split(Pattern.quote(separator))));
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
            sb.append(separator);
        for (int i = 0; i < components.size(); i++) {
            sb.append(components.get(i));
            if (i + 1 < components.size())
                sb.append(separator);
        }
        return sb.toString();
    }
}
