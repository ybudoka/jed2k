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

import org.dkf.jed2k.AddTransferParams;
import org.dkf.jed2k.exception.JED2KException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * The folder unfinished downloads live in, and the resume record kept beside each one.
 * <p>
 * Two things follow from putting them somewhere of their own. A half-downloaded file no
 * longer sits in the download folder looking like a finished one - it is not playable,
 * not complete, and indistinguishable from the real thing by name alone. And the folder
 * becomes a place that can be scanned: a resume record written next to each file makes a
 * download recoverable from the file itself rather than only from the app's private
 * database, which is lost on a reinstall, on "clear data", and whenever the same folder
 * is shared with a second install of the app.
 * <p>
 * The record is the same {@link AddTransferParams} the database stores - hash, size,
 * name, and the map of which pieces are already on disk - so a recovered transfer picks
 * up where it stopped rather than starting again.
 */
public final class IncompleteFiles {

    private static final Logger log = LoggerFactory.getLogger(IncompleteFiles.class);

    /** Subfolder of the download directory. */
    public static final String FOLDER = "Incomplete";

    /** Appended to the data file's name, so the two sort together. */
    public static final String RESUME_SUFFIX = ".jed2k";

    /**
     * A resume record is small - a hash set and a bitmap - but a corrupt or foreign file
     * could claim any size, and this is read at startup before anything is validated.
     */
    private static final int MAX_RESUME_BYTES = 4 * 1024 * 1024;

    /**
     * LollipopFileSystem.listFiles() only applies the filter on its SAF path, and a null
     * filter there short-circuits to an empty array. Passing one that accepts everything
     * is the difference between scanning the folder and silently finding nothing.
     */
    private static final FileFilter ACCEPT_ALL = new FileFilter() {
        @Override
        public boolean accept(final File file) {
            return true;
        }

        @Override
        public void file(final File file) {
        }
    };

    private IncompleteFiles() {
    }

    /**
     * @return the folder unfinished downloads go in, created if it was missing, or null
     * when it could not be created - in which case the caller should fall back to the
     * download folder rather than fail the download
     */
    public static File folder() {
        final File data = Platforms.data();
        if (data == null) {
            return null;
        }

        final File dir = new File(data, FOLDER);
        final FileSystem fs = Platforms.fileSystem();

        if (!fs.exists(dir) && !fs.mkdirs(dir)) {
            log.warn("[incomplete] unable to create {}", dir);
            return null;
        }

        return dir;
    }

    public static File resumeFileFor(final File dataFile) {
        return new File(dataFile.getParentFile(), dataFile.getName() + RESUME_SUFFIX);
    }

    public static boolean isResumeFile(final String name) {
        return name != null && name.endsWith(RESUME_SUFFIX);
    }

    /**
     * Writes the resume record next to its file. Best effort: the database copy is the
     * primary one, this is the copy that survives the database.
     */
    public static void writeResume(final File dataFile, final AddTransferParams atp) {
        if (dataFile == null || atp == null) {
            return;
        }

        try {
            final ByteBuffer buffer = ByteBuffer.allocate(atp.bytesCount());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            atp.put(buffer);

            final byte[] bytes = new byte[buffer.position()];
            System.arraycopy(buffer.array(), 0, bytes, 0, bytes.length);

            if (!Platforms.fileSystem().write(resumeFileFor(dataFile), bytes)) {
                log.warn("[incomplete] unable to write resume record for {}", dataFile.getName());
            }
        } catch (JED2KException e) {
            log.warn("[incomplete] unable to serialize resume record for {}: {}", dataFile.getName(), e.getMessage());
        } catch (Throwable t) {
            log.warn("[incomplete] resume record for {} failed: {}", dataFile.getName(), t.toString());
        }
    }

    public static void deleteResume(final File dataFile) {
        if (dataFile == null) {
            return;
        }

        final File resume = resumeFileFor(dataFile);
        if (Platforms.fileSystem().exists(resume) && !Platforms.fileSystem().delete(resume)) {
            log.warn("[incomplete] unable to remove resume record {}", resume.getName());
        }
    }

    /**
     * Reads every resume record in the incomplete folder whose data file is still there.
     * <p>
     * A record without its file is stale - the file was deleted from outside the app -
     * and is removed. A file without a record cannot be recovered at all: an unfinished
     * file cannot be hashed back to the ed2k file it belongs to, since the hash covers
     * content that has not been downloaded yet. Those are counted and reported rather
     * than silently ignored.
     *
     * @return the recoverable transfers, each already pointing at its file on disk
     */
    public static List<AddTransferParams> scan(final File scratchDir) {
        return scan(folder(), scratchDir);
    }

    /**
     * Looks for an unfinished download of this exact hash.
     * <p>
     * This is what makes starting the same file twice continue it instead of beginning
     * again: the resume record beside the partial file carries the hash, so a request
     * for that hash can be pointed at the bytes already on disk.
     *
     * @return its resume record, already pointing at the file, or null
     */
    public static AddTransferParams findByHash(final org.dkf.jed2k.protocol.Hash hash
            , final File scratchDir) {

        if (hash == null) {
            return null;
        }

        for (final AddTransferParams atp : scan(scratchDir)) {
            if (atp != null && hash.equals(atp.getHash())) {
                return atp;
            }
        }

        return null;
    }

    /**
     * The same, over any folder. Unfinished downloads made before this app version, or
     * by another install writing somewhere else, are found by pointing this at wherever
     * they are.
     */
    public static List<AddTransferParams> scan(final File dir, final File scratchDir) {
        final List<AddTransferParams> found = new ArrayList<>();

        if (dir == null) {
            return found;
        }

        log.info("[incomplete] scanning {}", dir);

        final FileSystem fs = Platforms.fileSystem();
        final File[] entries = fs.listFiles(dir, ACCEPT_ALL);

        if (entries == null) {
            return found;
        }

        final List<File> orphans = new LinkedList<>();

        for (final File entry : entries) {
            if (entry == null || fs.isDirectory(entry)) {
                continue;
            }

            if (!isResumeFile(entry.getName())) {
                if (!fs.exists(resumeFileFor(entry))) {
                    orphans.add(entry);
                }
                continue;
            }

            final String dataName = entry.getName()
                    .substring(0, entry.getName().length() - RESUME_SUFFIX.length());
            final File dataFile = new File(dir, dataName);

            if (!fs.exists(dataFile)) {
                log.info("[incomplete] resume record {} has no file, removing it", entry.getName());
                fs.delete(entry);
                continue;
            }

            final AddTransferParams atp = read(entry, fs, scratchDir);
            if (atp == null) {
                continue;
            }

            // The record travels with the file, so the path inside it is whatever the
            // machine that wrote it used. What matters is where the file is now.
            try {
                atp.getFilepath().assignString(dataFile.getAbsolutePath());
                found.add(atp);
            } catch (Throwable t) {
                log.warn("[incomplete] unable to retarget {}: {}", entry.getName(), t.toString());
            }
        }

        if (!orphans.isEmpty()) {
            log.warn("[incomplete] {} unfinished file(s) have no resume record and cannot be continued"
                    + " - an unfinished file cannot be identified by its content", orphans.size());
            for (final File o : orphans) {
                log.info("[incomplete] no resume record for {}", o.getName());
            }
        }

        return found;
    }

    private static AddTransferParams read(final File file, final FileSystem fs, final File scratchDir) {
        final long size = fs.length(file);

        if (size <= 0 || size > MAX_RESUME_BYTES) {
            log.warn("[incomplete] resume record {} has implausible size {}, skipping", file.getName(), size);
            return null;
        }

        try {
            final byte[] bytes = readAll(file, (int) size, fs, scratchDir);
            if (bytes == null) {
                return null;
            }

            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            final AddTransferParams atp = new AddTransferParams();
            atp.get(buffer);
            return atp;
        } catch (JED2KException e) {
            log.warn("[incomplete] resume record {} is not readable: {}", file.getName(), e.getMessage());
        } catch (Throwable t) {
            log.warn("[incomplete] resume record {} failed: {}", file.getName(), t.toString());
        }

        return null;
    }

    /**
     * FileSystem has no way to open a stream, and under scoped storage the record may
     * not be reachable as a plain File at all - so it is copied into the app's own
     * scratch directory, which always is, and read from there.
     */
    private static byte[] readAll(final File file, final int size, final FileSystem fs, final File scratchDir) {
        final File scratch = new File(scratchDir, "resume-scan.tmp");
        java.io.InputStream in = null;

        try {
            if (!fs.copy(file, scratch)) {
                log.warn("[incomplete] unable to read {}", file.getName());
                return null;
            }

            in = new java.io.FileInputStream(scratch);

            final byte[] bytes = new byte[size];
            int read = 0;
            while (read < size) {
                final int n = in.read(bytes, read, size - read);
                if (n < 0) break;
                read += n;
            }

            return (read == size) ? bytes : null;
        } catch (Throwable t) {
            log.warn("[incomplete] unable to read {}: {}", file.getName(), t.toString());
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {
                }
            }
            scratch.delete();
        }
    }
}
