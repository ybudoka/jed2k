package org.dkf.jed2k.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of folder paths kept as one string, for settings that hold several of them.
 * <p>
 * A path may contain almost anything except a newline, which is why that is the
 * separator. Blank entries and duplicates are dropped and the order the user put them in
 * is kept: this is a list someone curates by hand, and having it silently reordered or
 * quietly grow a second copy of the same folder is worse than useless.
 */
public final class PathList {

    private static final String SEPARATOR = "\n";

    private PathList() {
    }

    /**
     * @param stored the value as persisted, possibly null or empty
     * @return the paths, in order, without blanks or repeats
     */
    public static List<String> parse(final String stored) {
        final List<String> result = new ArrayList<>();

        if (stored == null || stored.isEmpty()) {
            return result;
        }

        final Set<String> seen = new LinkedHashSet<>();

        // \r is tolerated so a value that has been through a text editor still loads
        for (final String raw : stored.split("[\\r\\n]+")) {
            final String path = raw.trim();
            if (!path.isEmpty() && seen.add(path)) {
                result.add(path);
            }
        }

        return result;
    }

    public static String format(final List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final String path : parse(join(paths))) {
            if (sb.length() > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(path);
        }

        return sb.toString();
    }

    /**
     * @return the stored value with {@code path} appended, or unchanged when it is blank
     * or already there
     */
    public static String add(final String stored, final String path) {
        if (path == null || path.trim().isEmpty()) {
            return (stored == null) ? "" : stored;
        }

        final List<String> paths = parse(stored);
        final String trimmed = path.trim();

        if (paths.contains(trimmed)) {
            return format(paths);
        }

        paths.add(trimmed);
        return format(paths);
    }

    public static String remove(final String stored, final String path) {
        if (path == null) {
            return format(parse(stored));
        }

        final List<String> paths = parse(stored);
        paths.remove(path.trim());
        return format(paths);
    }

    private static String join(final List<String> paths) {
        final StringBuilder sb = new StringBuilder();
        for (final String p : paths) {
            if (p == null) continue;
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(p);
        }
        return sb.toString();
    }
}
