/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dkf.jmule;

import org.dkf.jed2k.util.PathList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The folders the app uses, and the ones the user has added to them.
 * <p>
 * Two of them are fixed and follow the storage setting: downloads land in one and
 * unfinished ones in the other. The rest are folders the user points at so their
 * unfinished downloads can be picked up from there - an SD card, or wherever an earlier
 * version wrote.
 * <p>
 * None of them is offered to other peers. This client does not upload: a peer asking for
 * a file by name is disconnected, and one asking whether we have a file is told we do
 * not. Adding a folder here makes its unfinished downloads recoverable, and nothing else.
 */
public final class Folders {

    /** What a folder is for, which is also what the user can do with it. */
    public enum Role {
        /** Finished downloads. Follows the storage setting. */
        DOWNLOADS,
        /** Unfinished downloads. Always a subfolder of DOWNLOADS. */
        INCOMPLETE,
        /** Added by the user, and removable. */
        ADDED
    }

    public static final class Entry {
        public final File dir;
        public final Role role;

        Entry(final File dir, final Role role) {
            this.dir = dir;
            this.role = role;
        }

        public boolean isRemovable() {
            return role == Role.ADDED;
        }
    }

    private Folders() {
    }

    private static String stored() {
        try {
            return ConfigurationManager.instance().getString(Constants.PREF_KEY_EXTRA_FOLDERS);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void store(final String value) {
        ConfigurationManager.instance().setString(Constants.PREF_KEY_EXTRA_FOLDERS, value);
    }

    /**
     * @return the fixed folders first, then the added ones in the order they were added
     */
    public static List<Entry> all() {
        final List<Entry> entries = new ArrayList<>();

        final File downloads = Platforms.data();
        if (downloads != null) {
            entries.add(new Entry(downloads, Role.DOWNLOADS));
        }

        final File incomplete = IncompleteFiles.folder();
        if (incomplete != null) {
            entries.add(new Entry(incomplete, Role.INCOMPLETE));
        }

        for (final String path : PathList.parse(stored())) {
            entries.add(new Entry(new File(path), Role.ADDED));
        }

        return entries;
    }

    public static void add(final String path) {
        store(PathList.add(stored(), path));
    }

    public static void remove(final String path) {
        store(PathList.remove(stored(), path));
    }

    /**
     * @return the added folders only - the fixed two are scanned anyway
     */
    public static List<String> added() {
        return PathList.parse(stored());
    }
}
