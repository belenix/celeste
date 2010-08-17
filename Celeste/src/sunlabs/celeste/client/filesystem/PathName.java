package sunlabs.celeste.client.filesystem;

import java.net.URI;
import java.util.Iterator;
import java.util.Stack;


public class PathName implements Iterable<HierarchicalFileSystem.FileName>, HierarchicalFileSystem.FileName {
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
            this.path.push(this.separator);
        } else {
            for (String t : tokens) {
                if (t.equals("..")) {
                    if (this.path.isEmpty()) {
                        this.path.push(t);
                    } else if (this.path.peek().equals("..")) {
                        this.path.push(t);
                    } else if (this.path.peek().equals(this.separator)) { // Don't ascend farther than the root
                        //
                    } else {
                        this.path.pop();
                    }
                } else if (t.equals(".")) {
                    //
                } else if (t.equals("")) {
                    if (this.path.isEmpty()) {
                        this.path.push(this.separator);
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
        return this.path.get(0).equals(this.separator);
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
        if (!this.path.get(0).equals(this.separator))
            this.path.insertElementAt(this.separator, 0);
        return this;
    }

    /**
     * Get a component of a {@code PathName} indexed by {@code index}.
     * 
     * Note that if the PathName is absolute (ie. it starts with the {@link #separator} string), the <i>0</i>th element is the separator string  alone.
     * 
     * @param index
     * @return
     */
    public String get(int index) {
        String result = this.path.elementAt(index);
        return result.equals(this.separator) ? this.separator : result;
    }

    /**
     * Return the tail of the path.
     * For example:
     * <ul>
     * <li>{@code tail("/a/b/c/d")} returns {@code "a/b/c/d"} (absolute paths contain the {@link #separator} string as the first component).</li>
     * <li>{@code tail("a/b/c/d")} returns "b/c/d".</li>
     * </ul>
     * @return
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
            if (result.path.peek().equals(this.separator))
                break;
            result.path.pop();
        }
        return result;
    }

    /**
     * Convenience method equivalent to {@link #parent()}.
     * @return the parent path of this path.
     */
    public PathName getDirName() {
        return this.parent();
    }
    
    public String[] getComponents() {
        return this.path.toArray(new String[this.path.size()]);
    }

    /**
     * Convenience method returning the last component of the {@code PathName}.
     * @return the last component of the {@code PathName}.
     */
    public String getBaseName() {
        return this.path.lastElement();
    }
    
    public String getNameExtension() {
        String component = this.getBaseName();
        int dot = component.lastIndexOf('.');
        if (dot == -1 || dot == component.length() - 1)
            return "";
        return component.substring(dot + 1);        
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
     * Create a new {@code PathName} instance composed from this {@code PathName} and appending the given {@code name}.
     * 
     * @param name
     * @return a new {@code PathName} instance composed from this {@code PathName} and appending the given {@code name}.
     */
    public PathName append(HierarchicalFileSystem.FileName name) {
        PathName newPathName = new PathName(this.toString());
        for (String p : name.getComponents()) {
            if (!p.equals(this.separator))
                newPathName.path.push(p);
        }

        return newPathName;
    }

    /**
     * Get the total number of components in this path.
     * <p>
     * Absolute paths have an initial component consisting of the {@link separator}String.
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

        StringBuilder result = new StringBuilder(this.path.get(0).equals(this.separator) ? this.separator : "");
        String slash = "";
        for (String p : this.path) {
            if (!p.equals(this.separator)) {
                result.append(slash).append(p);
                slash = this.separator;
            }
        }

        return result.toString();
    }

    public URI toURI() {
        return URI.create(this.toString());
    }
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
    public class PathIterator implements Iterator<HierarchicalFileSystem.FileName> {
        private int index = 0;
        private String[] components = PathName.this.path.toArray(new String[0]);
        private StringBuilder accumulatingPath = new StringBuilder();

        /**
         * Creates an iterator for the containing {@code PathName} instance.
         */
        public PathIterator() {
        }

        public boolean hasNext() {
            return this.index < this.components.length;
        }

        public HierarchicalFileSystem.FileName next() {
            //
            // If the base path name is absolute avoid adding a separator
            // immediately after the root component, which is itself
            // represented by a separator.  Also, don't add a separator at the
            // front of the entire path.
            //
            if ((this.index == 1 && !PathName.this.isAbsolute()) || index > 1)
                this.accumulatingPath.append(PathName.this.separator);
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

    public Iterator<HierarchicalFileSystem.FileName> iterator() {
        return new PathIterator();
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
}
