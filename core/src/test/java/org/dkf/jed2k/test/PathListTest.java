package org.dkf.jed2k.test;

import org.dkf.jed2k.util.PathList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This is a list somebody curates by hand. Losing an entry, reordering it or growing a
 * second copy of the same folder are all silent failures, so each is pinned here.
 */
public class PathListTest {

    @Test
    public void testEmptyInput() {
        assertTrue(PathList.parse(null).isEmpty());
        assertTrue(PathList.parse("").isEmpty());
        assertEquals("", PathList.format(null));
        assertEquals("", PathList.format(Collections.<String>emptyList()));
    }

    @Test
    public void testOrderIsKept() {
        final List<String> paths = Arrays.asList("/b", "/a", "/c");
        assertEquals(paths, PathList.parse(PathList.format(paths)));
    }

    @Test
    public void testBlanksAreDropped() {
        assertEquals(Arrays.asList("/a", "/b"), PathList.parse("/a\n\n   \n/b\n"));
    }

    @Test
    public void testDuplicatesAreDropped() {
        assertEquals(Arrays.asList("/a", "/b"), PathList.parse("/a\n/b\n/a"));
    }

    @Test
    public void testSurroundingSpaceIsTrimmed() {
        assertEquals(Arrays.asList("/a", "/b"), PathList.parse("  /a  \n\t/b\t"));
    }

    /**
     * A path can contain spaces and almost every other character; only the newline is
     * off limits, which is why it is the separator.
     */
    @Test
    public void testPathsWithSpacesAndPunctuation() {
        final String path = "/storage/emulated/0/My Files (2)/déjà vu, ok!";
        assertEquals(Collections.singletonList(path), PathList.parse(PathList.format(
                Collections.singletonList(path))));
    }

    @Test
    public void testCarriageReturnsAreTolerated() {
        assertEquals(Arrays.asList("/a", "/b"), PathList.parse("/a\r\n/b"));
    }

    @Test
    public void testAdd() {
        assertEquals("/a", PathList.add("", "/a"));
        assertEquals("/a\n/b", PathList.add("/a", "/b"));
        assertEquals("/a", PathList.add(null, "/a"));
    }

    @Test
    public void testAddIsIdempotent() {
        assertEquals("/a\n/b", PathList.add("/a\n/b", "/b"));
        assertEquals("/a\n/b", PathList.add("/a\n/b", "  /b  "));
    }

    @Test
    public void testAddIgnoresBlanks() {
        assertEquals("/a", PathList.add("/a", ""));
        assertEquals("/a", PathList.add("/a", "   "));
        assertEquals("/a", PathList.add("/a", null));
    }

    @Test
    public void testRemove() {
        assertEquals("/a\n/c", PathList.remove("/a\n/b\n/c", "/b"));
        assertEquals("", PathList.remove("/a", "/a"));
    }

    @Test
    public void testRemoveWhatIsNotThere() {
        assertEquals("/a\n/b", PathList.remove("/a\n/b", "/zzz"));
        assertEquals("/a\n/b", PathList.remove("/a\n/b", null));
    }

    /**
     * A value that has picked up blanks or repeats from anywhere is cleaned by a
     * round trip rather than carried forward.
     */
    @Test
    public void testFormatNormalises() {
        assertEquals("/a\n/b", PathList.format(Arrays.asList("/a", "", "/b", "/a", "  ")));
    }
}
