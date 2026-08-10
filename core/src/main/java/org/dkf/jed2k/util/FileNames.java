package org.dkf.jed2k.util;

/**
 * Picks a file name that is not already in use.
 * <p>
 * ed2k identifies a file by its hash, and two different hashes routinely carry the same
 * name - the same release repacked, or just "video.mp4". Downloading both wrote them to
 * one path, so the two transfers interleaved their writes into a single file and
 * produced two corrupt results out of two good sources. A name that already exists on
 * disk from an earlier download has the same effect.
 * <p>
 * The new download is renamed rather than the old one: whatever is already there may be
 * open, indexed, or referenced by something else, and moving it under those feet is the
 * one outcome nobody asks for.
 */
public final class FileNames {

    /**
     * Bound on the search. Reaching it means ten thousand files differing only by their
     * suffix, which is a broken caller rather than a real library.
     */
    public static final int MAX_ATTEMPTS = 10000;

    /**
     * Whether a name is already spoken for. The caller decides what that means - a file
     * on disk, a transfer already heading for it, or both.
     */
    public interface Taken {
        boolean contains(final String name);
    }

    private FileNames() {
    }

    /**
     * @param name  the name the file would like to have
     * @param taken tells whether a candidate is unavailable
     * @return {@code name} when it is free, otherwise the first of
     * {@code name (2)}, {@code name (3)} ... that is not, with the suffix placed before
     * the extension. Returns {@code name} unchanged when it is null or empty.
     */
    public static String uniqueName(final String name, final Taken taken) {
        if (name == null || name.isEmpty() || taken == null) {
            return name;
        }

        if (!taken.contains(name)) {
            return name;
        }

        String candidate = null;
        for (int i = 2; i < MAX_ATTEMPTS; i++) {
            candidate = withSuffix(name, i);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }

        // Nothing sensible left to try. Handing back the last candidate keeps the
        // download going; the alternative is refusing to download at all.
        return candidate;
    }

    /**
     * {@code report.txt, 2 -> report (2).txt}
     * <p>
     * The extension is whatever follows the last dot, which is what browsers and file
     * managers do. A leading dot is a hidden file rather than an extension, so
     * {@code .gitignore} becomes {@code .gitignore (2)} and not {@code  (2).gitignore}.
     */
    public static String withSuffix(final String name, final int index) {
        final int dot = name.lastIndexOf('.');
        final String suffix = " (" + index + ")";

        if (dot <= 0) {
            return name + suffix;
        }

        return name.substring(0, dot) + suffix + name.substring(dot);
    }
}
