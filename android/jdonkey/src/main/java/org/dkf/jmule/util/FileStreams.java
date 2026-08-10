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

package org.dkf.jmule.util;

import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import org.dkf.jmule.LollipopFileSystem;
import org.dkf.jmule.Platforms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Opens a downloaded file for reading, whichever storage regime the device is under.
 * <p>
 * The FileSystem abstraction can copy, write and delete but cannot hand back a stream,
 * and under scoped storage a download is not reachable as a plain File at all - it is a
 * document behind a descriptor. Anything that wants to read a file end to end needs both
 * paths, so they live here rather than being repeated.
 */
public final class FileStreams {

    private FileStreams() {
    }

    /**
     * @return a stream over the file's content; the caller closes it
     * @throws IOException when the file cannot be opened
     */
    public static InputStream open(final File file) throws IOException {
        if (file == null) {
            throw new IOException("no file");
        }

        if (Platforms.get().saf()) {
            final LollipopFileSystem fs = (LollipopFileSystem) Platforms.fileSystem();
            final android.util.Pair<ParcelFileDescriptor, DocumentFile> fd = fs.openFD(file, "r");

            if (fd == null || fd.first == null) {
                throw new IOException("unable to open " + file.getName());
            }

            // closing the stream closes the descriptor with it
            return new ParcelFileDescriptor.AutoCloseInputStream(fd.first);
        }

        return new FileInputStream(file);
    }
}
