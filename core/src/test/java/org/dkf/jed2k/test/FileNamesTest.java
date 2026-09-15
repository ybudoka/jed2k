package org.dkf.jed2k.test;

import org.dkf.jed2k.util.FileNames;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FileNamesTest {

    /**
     * @param names names that are already in use
     */
    private static FileNames.Taken taken(String... names) {
        final Set<String> set = new HashSet<>(Arrays.asList(names));
        return new FileNames.Taken() {
            @Override
            public boolean contains(String name) {
                return set.contains(name);
            }
        };
    }

    @Test
    public void testFreeNameIsUsedAsIs() {
        assertEquals("debian.iso", FileNames.uniqueName("debian.iso", taken()));
        assertEquals("debian.iso", FileNames.uniqueName("debian.iso", taken("ubuntu.iso")));
    }

    @Test
    public void testSuffixGoesBeforeTheExtension() {
        assertEquals("debian (2).iso", FileNames.uniqueName("debian.iso", taken("debian.iso")));
    }

    @Test
    public void testCountsUpPastEveryTakenName() {
        assertEquals("debian (4).iso", FileNames.uniqueName("debian.iso",
                taken("debian.iso", "debian (2).iso", "debian (3).iso")));
    }

    /**
     * The gap is not filled in order to keep numbering monotonic - it is filled because
     * the first free candidate wins, and that is the behaviour to pin down.
     */
    @Test
    public void testTakesTheFirstFreeSlot() {
        assertEquals("debian (3).iso", FileNames.uniqueName("debian.iso",
                taken("debian.iso", "debian (2).iso", "debian (4).iso")));
    }

    @Test
    public void testNameWithoutExtension() {
        assertEquals("README (2)", FileNames.uniqueName("README", taken("README")));
    }

    /**
     * A leading dot is a hidden file, not an extension.
     */
    @Test
    public void testDotFile() {
        assertEquals(".gitignore (2)", FileNames.uniqueName(".gitignore", taken(".gitignore")));
    }

    /**
     * Only the last dot counts, which is what browsers and file managers do.
     */
    @Test
    public void testMultipleDots() {
        assertEquals("archive.tar (2).gz", FileNames.uniqueName("archive.tar.gz", taken("archive.tar.gz")));
    }

    @Test
    public void testTrailingDot() {
        assertEquals("weird (2).", FileNames.uniqueName("weird.", taken("weird.")));
    }

    /**
     * A name that already carries a suffix is not parsed, it is suffixed again. Ugly but
     * unambiguous, and it cannot collide with the file it was derived from.
     */
    @Test
    public void testAlreadySuffixedName() {
        assertEquals("debian (2) (2).iso", FileNames.uniqueName("debian (2).iso", taken("debian (2).iso")));
    }

    @Test
    public void testEmptyAndNullAreLeftAlone() {
        assertNull(FileNames.uniqueName(null, taken("x")));
        assertEquals("", FileNames.uniqueName("", taken("x")));
        assertEquals("a.iso", FileNames.uniqueName("a.iso", null));
    }

    /**
     * Everything taken: the download still has to get a name rather than fail.
     */
    @Test
    public void testGivesUpWithAName() {
        String result = FileNames.uniqueName("x.iso", new FileNames.Taken() {
            @Override
            public boolean contains(String name) {
                return true;
            }
        });

        assertEquals("x (" + (FileNames.MAX_ATTEMPTS - 1) + ").iso", result);
    }

    @Test
    public void testWithSuffix() {
        assertEquals("a (2).b", FileNames.withSuffix("a.b", 2));
        assertEquals("a (17)", FileNames.withSuffix("a", 17));
    }
}
