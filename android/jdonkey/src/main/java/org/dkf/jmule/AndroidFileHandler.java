package org.dkf.jmule;

import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import org.dkf.jed2k.disk.FileHandler;
import org.dkf.jed2k.exception.ErrorCode;
import org.dkf.jed2k.exception.JED2KException;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by inkpot on 31.01.2017.
 */
public class AndroidFileHandler extends FileHandler {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AndroidFileHandler.class);
    private DocumentFile doc;
    private ParcelFileDescriptor descriptor;
    /**
     * set once close() released the descriptor; the next channel request re-opens the
     * document, which is what verify and repair needs on a finished transfer
     */
    private boolean released = false;

    public AndroidFileHandler(final File file, final DocumentFile doc, final ParcelFileDescriptor descriptor) {
        super(file);
        this.doc = doc;
        this.descriptor = descriptor;
    }

    private ParcelFileDescriptor descriptor() throws JED2KException {
        if (released) {
            if (!(Platforms.fileSystem() instanceof LollipopFileSystem)) {
                throw new JED2KException(ErrorCode.IO_EXCEPTION);
            }

            LollipopFileSystem fs = (LollipopFileSystem) Platforms.fileSystem();
            android.util.Pair<ParcelFileDescriptor, DocumentFile> reopened = fs.openFD(file, "rw");
            if (reopened == null || reopened.first == null || reopened.second == null) {
                log.error("unable to re-open {}", file);
                throw new JED2KException(ErrorCode.IO_EXCEPTION);
            }

            log.info("re-opened {}", file);
            descriptor = reopened.first;
            doc = reopened.second;
            released = false;
        }

        return descriptor;
    }

    @Override
    protected FileOutputStream allocateOutputStream() throws JED2KException {
        return new FileOutputStream(descriptor().getFileDescriptor());
    }

    @Override
    protected FileInputStream allocateInputStream() throws JED2KException {
        return new FileInputStream(descriptor().getFileDescriptor());
    }

    @Override
    protected void deleteFile() throws JED2KException {
        if (!doc.delete()) {
            throw new JED2KException(ErrorCode.UNABLE_TO_DELETE_FILE);
        }
    }

    @Override
    public void close() {
        super.close();
        if (released) return;
        try {
            descriptor.close();
        } catch(IOException e) {
            log.error("unable to close file descriptor {}", e.toString());
        } finally {
            released = true;
        }
    }
}
