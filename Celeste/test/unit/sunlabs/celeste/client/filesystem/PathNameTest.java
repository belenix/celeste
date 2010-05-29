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
package sunlabs.celeste.client.filesystem;

import java.util.Arrays;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static java.io.File.separator;

import static org.junit.Assert.*;

//
// N.B. The tests below are written using "/" as the file separator within
//      pathname strings.  But since the PathName class uses File.separator as
//      its internal separator, the raw "/"-bearing strings need to be
//      converted to use File.separator instead.  That's what all the
//      replaceAll() invocations are for.
//
//      Not also the static import of File.separator above, so that the
//      replaceAll invocations can be as succinct as possible.
//
public class PathNameTest {
    //
    // Verify that "/" is its own canonical form.
    //
    @Test
    public final void testCanonSlash() {
        PathName path = new PathName(separator);
        assertEquals("\"/\" is its own canonical form",
            separator, path.toString());
    }

    //
    // Verify that "." is its own canonical form.
    //
    @Test
    public final void testCanonDot() {
        PathName path = new PathName(".");
        assertEquals("\".\" is its own canonical form",
            ".", path.toString());
    }

    //
    // Verify that ".." is its own canonical form.
    //
    @Test
    public final void testCanonDotDot() {
        PathName path = new PathName("..");
        assertEquals("\"..\" is its own canonical form",
            "..", path.toString());
    }

    //
    // Verify that an empty path is equivalent to ".".
    //
    @Test
    public final void testCanonEmpty() {
        PathName path = new PathName("");
        assertEquals("\"\" is equivalent to \".\"",
            ".", path.toString());
    }

    //
    // Verify that a single component relative path has the proper canonical
    // form.
    //
    @Test
    public final void testRelativeSingle() {
        PathName path = new PathName("single");
        assertEquals("\"single\" has proper canonical form",
            "single", path.toString());
    }

    //
    // Verify that a single component absolute path has the proper canonical
    // form.
    //
    @Test
    public final void testAbsoluteSingle() {
        PathName path = new PathName(separator + "single");
        assertEquals("\"/single\" has proper canonical form",
            separator + "single", path.toString());
    }

    //
    // Verify that a three component relative path has the proper canonical
    // form.
    //
    @Test
    public final void testRelativeTriple() {
        String rawPath = "ab/23/def".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"ab/23/def\" has proper canonical form",
            rawPath, path.toString());
    }

    //
    // Verify that a three component absolute path has the proper canonical
    // form.
    //
    @Test
    public final void testAbsoluteTriple() {
        String rawPath = "/ab/23/def".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"/ab/23/def\" has proper canonical form",
            rawPath, path.toString());
    }

    //
    // Verify that empty components and "." components are properly stripped.
    //
    @Test
    public final void testStripEmptyAndDot0() {
        String rawPath = "ab//c/.//e/f/.".replaceAll("/", separator);
        String canonicalPath = "ab/c/e/f".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"ab//c/.//e/f/.\" has proper canonical form",
            canonicalPath, path.toString());
    }

    //
    // Verify that a trailing '/' is properly stripped.
    //
    @Test
    public final void testStripEmptyAndDot1() {
        String rawPath = "/pink/".replaceAll("/", separator);
        String canonicalPath = "/pink".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"/pink/\" has proper canonical form",
            canonicalPath, path.toString());
    }

    //
    // Verify that a single strippable occurrence of ".." is properly
    // stripped.
    //
    @Test
    public final void testStripDotDotSingle() {
        String rawPath = "/blurfl/gronk/../plap".replaceAll("/", separator);
        String canonicalPath = "/blurfl/plap".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"/blurfl/gronk/../plap\" has proper canonical form",
            canonicalPath, path.toString());
    }

    //
    // Verify that a strippable "../.."  pair is properly stripped.
    //
    @Test
    public final void testStripDotDotDouble() {
        String rawPath =
            "/blurfl/gronk/zorch/../../plap".replaceAll("/", separator);
        String canonicalPath = "/blurfl/plap".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals(
            "\"/blurfl/gronk/zorch/../../plap\" has proper canonical form",
            canonicalPath, path.toString());
    }

    //
    // Verify that leading ".."s are stripped from absolute paths.
    //
    @Test
    public final void testLeadingAbsoluteDotDot() {
        String rawPath = "/../apple/pear".replaceAll("/", separator);
        String canonicalPath = "/apple/pear".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"/../apple/pear\" has proper canonical form",
            canonicalPath, path.toString());
    }

    //
    // Verify that leading ".."s aren't stripped from relative paths.
    //
    @Test
    public final void testLeadingRelativeDotDot() {
        String rawPath = "../apple/pear".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals("\"../apple/pear\" has proper canonical form",
            rawPath, path.toString());
    }

    //
    // XXX: Need to test pathName.equals().  Otherwise the tests below could
    //      yield false positives from broken equals().
    //

    //
    // Verify that resolving an absolute path yields an equivalent path.
    //
    @Test
    public final void testResolveAbsolute() {
        String rawPath = "/abc/def".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String rawBase = "/gray".replaceAll("/", separator);
        PathName base = new PathName(rawBase);
        assertEquals("resolving an absolute path yields that path",
            path, path.resolve(base));
    }

    //
    // Verify that resolving a relative path without a leading ".." works
    // properly.
    //
    @Test
    public final void testResolveNoLeadingDotDot() {
        String rawPath = "abc/def".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String rawBase = "/gray".replaceAll("/", separator);
        PathName base = new PathName(rawBase);
        String resolved = "/gray/abc/def".replaceAll("/", separator);
        assertEquals(
            "resolving a relative path with no leading .. concatenates them",
            resolved, path.resolve(base).toString());
    }

    //
    // Verify that resolving a relative path with a leading ".." works
    // properly.
    //
    @Test
    public final void testResolveLeadingDotDot() {
        String rawPath = "../abc/def".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String rawBase = "/gray/blue".replaceAll("/", separator);
        PathName base = new PathName(rawBase);
        String resolved = "/gray/abc/def".replaceAll("/", separator);
        assertEquals(
            "resolving a relative path with a leading .. backs into the base",
            resolved, path.resolve(base).toString());
    }

    //
    // getLeafComponent() tests
    //

    //
    // Verify that the last component of "/" is correct.
    //
    @Test
    public final void testLeafSlash() {
        String rawPath = "/";
        PathName path = new PathName(rawPath);
        assertEquals("getLeafComponent(\"/\") returns \"/\"",
            path.toString(), path.getLeafComponent());
    }

    //
    // Verify that the last component of "." is correct.
    //
    @Test
    public final void testLeafDot() {
        String rawPath = ".";
        PathName path = new PathName(rawPath);
        assertEquals("getLeafComponent(\".\") returns \".\"",
            path.toString(), path.getLeafComponent());
    }

    //
    // Verify that the last component of a single component absolute path is
    // correct.
    //
    @Test
    public final void testLeafSingleAbsolute() {
        String rawPath = "/gronk";
        PathName path = new PathName(rawPath);
        assertEquals("getLeafComponent(\"/gronk\") returns \"gronk\"",
            "gronk", path.getLeafComponent());
    }

    //
    // Verify that the last component of a single component absolute path is
    // correct.
    //
    @Test
    public final void testLeafSingleRelative() {
        String rawPath = "gronk";
        PathName path = new PathName(rawPath);
        assertEquals("getLeafComponent(\"gronk\") returns \"gronk\"",
            "gronk", path.getLeafComponent());
    }

    //
    // Verify that the last component of a multiple component absolute path is
    // correct.
    //
    @Test
    public final void testLeafMultipleAbsolute() {
        String rawPath = "/gronk/thud/plop";
        PathName path = new PathName(rawPath);
        assertEquals(
            "getLeafComponent(\"/gronk/thud/plop\") returns \"plop\"",
            "plop", path.getLeafComponent());
    }

    //
    // Verify that the last component of a multiple component absolute path is
    // correct.
    //
    @Test
    public final void testLeafMultipleRelative() {
        String rawPath = "gronk/thud/plop".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals(
            "getLeafComponent(\"gronk/thud/plop\") returns \"plop\"",
            "plop", path.getLeafComponent());
    }

    //
    // trimLeafComponent() tests
    //

    //
    // Verify that a multi-component absolute path is trimmed correctly.
    //
    @Test
    public final void testTrimLeafAbsoluteMulti() {
        String rawPath = "/gronk/thud/plop".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String trimmedPath = "/gronk/thud".replaceAll("/", separator);
        assertEquals(
            "trimLeafComponent(\"/gronk/thud/plop\" returns \"/gronk/thud\"",
            trimmedPath, path.trimLeafComponent().toString());
    }

    //
    // Verify that a multi-component relative path is trimmed correctly.
    //
    @Test
    public final void testTrimLeafRelativeMulti() {
        String rawPath = "gronk/thud/plop".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String trimmedPath = "gronk/thud".replaceAll("/", separator);
        assertEquals(
            "trimLeafComponent(\"gronk/thud/plop\" returns \"gronk/thud\"",
            trimmedPath, path.trimLeafComponent().toString());
    }

    //
    // Verify that a single-component absolute path is trimmed correctly.
    //
    @Test
    public final void testTrimLeafAbsoluteSingle() {
        String rawPath = "/gronk".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String trimmedPath = "/".replaceAll("/", separator);
        assertEquals(
            "trimLeafComponent(\"/gronk\" returns \"/\"",
            trimmedPath, path.trimLeafComponent().toString());
    }

    //
    // Verify that a single-component relative path is trimmed correctly.
    //
    @Test
    public final void testTrimLeafRelativeSingle() {
        String rawPath = "gronk".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        assertEquals(
            "trimLeafComponent(\"gronk\" returns \".\"",
            ".", path.trimLeafComponent().toString());
    }

    //
    // Verify that ".." is trimmed correctly.
    //
    @Test
    public final void testTrimLeafRelativeDotDot() {
        String rawPath = "..".replaceAll("/", separator);
        PathName path = new PathName(rawPath);
        String trimmedPath = "../..".replaceAll("/", separator);
        assertEquals(
            "trimLeafComponent(\"..\" returns \"../..\"",
            trimmedPath, path.trimLeafComponent().toString());
    }

    //
    // getExtension() tests
    //

    //
    // Verify that a path containing no '.' yields the expected extension.
    //
    @Test
    public final void testExtensionNone() {
        String rawPath = "gronk";
        PathName path = new PathName(rawPath);
        assertEquals(
            "getExtension(\"gronk\") returns \"\"",
            "", path.getExtension());
    }

    //
    // Verify that a path containing a single embedded '.' yields the expected
    // extension.
    //
    @Test
    public final void testExtensionSingle() {
        String rawPath = "gronk.jpeg";
        PathName path = new PathName(rawPath);
        assertEquals(
            "getExtension(\"gronk.jpeg\") returns \"jpeg\"",
            "jpeg", path.getExtension());
    }

    //
    // Verify that a path containing a single '.' at the end yields the
    // expected extension.
    //
    @Test
    public final void testExtensionSingleEnd() {
        String rawPath = "gronk.";
        PathName path = new PathName(rawPath);
        assertEquals(
            "getExtension(\"gronk.\") returns \"\"",
            "", path.getExtension());
    }

    //
    // Verify that a path containing multiple embedded '.'s yields the
    // expected extension.
    //
    @Test
    public final void testExtensionMultiple() {
        String rawPath = "gronk.jpeg.bak";
        PathName path = new PathName(rawPath);
        assertEquals(
            "getExtension(\"gronk.jpeg.bak\") returns \"bak\"",
            "bak", path.getExtension());
    }

    //
    // compareTo tests
    //
    // XXX: Could do with many more.
    //

    //
    // Verify that two paths with an equal number of components compare
    // properly.
    //

    @Test
    public final void testCompareToEq0() {
        PathName p1 = new PathName("/a/bd/de");
        PathName p2 = new PathName("/a/bd/de");
        assertEquals(
            "compareTo on identical paths yields 0",
            p1.compareTo(p2), 0);
    }

    @Test
    public final void testCompareToNeq0() {
        PathName p1 = new PathName("/a/bd/de");
        PathName p2 = new PathName("/a/bd/df");
        assertTrue(
            "new PathName(\"/a/bd/de\").compareTo(new PathName(\"/a/bd/df\")) < 0",
            p1.compareTo(p2) < 0);
    }

    @Test
    public final void testCompareToNeq1() {
        PathName p1 = new PathName("/a/bd/de");
        PathName p2 = new PathName("/a/bdd/e");
        assertTrue(
            "compareTo on paths identical except for '/' placement != 0",
            p1.compareTo(p2) != 0);
    }

    //
    // Verify that a path that's an ancestor of another compares less than the
    // other.
    //
    @Test
    public final void testCompareToAncestor() {
        PathName p1 = new PathName("/a/bc/d");
        PathName p2 = new PathName("/a/bc/d/ef");
        assertTrue(
            "an ancestor of another path compares less than that path",
            p1.compareTo(p2) < 0);
    }

    //
    // isAncestor tests
    //

    //
    // Verify that the root path is an ancestor of some arbitrary other path.
    //
    @Test
    public final void testIsAncestorRoot() {
        PathName p1 = new PathName("/");
        PathName p2 = new PathName("/apple/orange");
        assertTrue(
            "the root is the ancestor of an arbitrary other path",
            p1.isAncestor(p2));
    }

    //
    // Verify that isAncestor correctly handles two equal non-root paths
    // in an ancestry relationship.
    //
    @Test
    public final void testIsAncestorNonRootEq() {
        PathName p1 = new PathName("/apple/orange/pear");
        PathName p2 = new PathName("/apple/orange/pear");
        assertTrue(
            "isAncestor on two non-root, equal paths",
            p1.isAncestor(p2));
    }

    //
    // Verify that isAncestor correctly handles two non-equal non-root paths
    // in an ancestry relationship.
    //
    @Test
    public final void testIsAncestorNonRootNeq0() {
        PathName p1 = new PathName("/apple");
        PathName p2 = new PathName("/apple/orange/pear");
        assertTrue(
            "isAncestor on two non-root, non-equal paths with one a subpath",
            p1.isAncestor(p2));
    }

    //
    // Verify that isAncestor correctly handles two non-equal non-root paths
    // in a non-ancestry relationship.
    //
    @Test
    public final void testIsAncestorNonRootNeq1() {
        PathName p1 = new PathName("/apple/potato");
        PathName p2 = new PathName("/apple/orange/pear");
        assertFalse(
            "isAncestor on two non-root, non-equal unrelated paths",
            p1.isAncestor(p2));
    }

    //
    // Tests of the ordering produced by compareTo when PathNames are used in
    // SortedSets
    //

    //
    // Verify that an iteration starting at pathSet.tailSet(p) and continuing
    // until !p.isAncestor(pathFromTailSet) covers exactly the subtree rooted
    // at p.
    //
    @Test
    public final void testSubtreeIteration() {
        PathName aePath = new PathName("/ae");
        PathName[] paths = {
            new PathName("/"),
            new PathName("/aa"),
            new PathName("/ae"),
            new PathName("/ae/b"),
            new PathName("/ae/b/c"),
            new PathName("/ae/b/d"),
            new PathName("/ax"),
        };
        PathName[] aePaths = {
            new PathName("/ae"),
            new PathName("/ae/b"),
            new PathName("/ae/b/c"),
            new PathName("/ae/b/d"),
        };
        SortedSet<PathName> allPathsSet =
            new TreeSet<PathName>(Arrays.asList(paths));
        SortedSet<PathName> aeSubtree =
            new TreeSet<PathName>(Arrays.asList(aePaths));
        SortedSet<PathName> aeTail = allPathsSet.tailSet(aePath);
        //
        // aeTail should contain the subtree rooted at "/ea" as well as set
        // elements following the subtree in the ordering (in this case,
        // "/ax").  Select the subtree itself by iterating until the
        // isAncestor relation fails.
        //
        SortedSet<PathName> computedAeSubtree = new TreeSet<PathName>();
        for (PathName path : aeTail) {
            if (!aePath.isAncestor(path))
                break;
            computedAeSubtree.add(path);
        }
        assertEquals(
            "iteration from tail set should produce all subtree paths",
            aeSubtree, computedAeSubtree);
    }

    //
    // relativize() tests
    //

    //
    // Verify that a straighforward relativization produces the proper result.
    //
    @Test
    public final void testRelativize0() {
        PathName base = new PathName("/a/b/c");
        PathName descendant = new PathName("/a/b/c/d/e");
        PathName relative = new PathName("d/e");
        assertEquals(
            "\"/a/b/c\".relativize(\"/a/b/c/d/e\") == \"d/e\"",
            relative, base.relativize(descendant));
    }

    //
    // Verify that relativizing a path to itself yields ".".
    //
    @Test
    public final void testRelativizeSelf() {
        PathName base = new PathName("/a/b/c");
        PathName dot = new PathName (".");
        assertEquals(
            "relativizing a path to itself yields \".\"",
            dot, base.relativize(base));
    }

    //
    // PathName.iterator() tests
    //

    //
    // Verify that a multi-component path name's iterator produces the proper
    // sequence of partial path names.
    //
    @Test
    public final void testIteratorMultiComponentAbsolute() {
        PathName[] paths = {
            new PathName("/"),
            new PathName("/a"),
            new PathName("/a/b"),
            new PathName("/a/b/c"),
        };
        Iterator<PathName> it = paths[paths.length - 1].iterator();

        int i = 0;
        while (it.hasNext() && i < paths.length) {
            PathName path = it.next();
            assertEquals(String.format(
                "iteration %d of pathname iterator yields proper partial path",
                i),
                paths[i], path);
            i += 1;;
        }
        assertTrue(
            "iterator is exhausted and has produces all partial paths",
            !it.hasNext() && i == paths.length);
    }
}
