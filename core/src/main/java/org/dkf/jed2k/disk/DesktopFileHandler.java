package org.dkf.jed2k.disk;

import org.dkf.jed2k.exception.ErrorCode;
import org.dkf.jed2k.exception.JED2KException;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by inkpot on 30.01.2017.
 */
public class DesktopFileHandler extends FileHandler {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(DesktopFileHandler.class);

    /**
     * Backs the write channel. Kept so it can be closed together with the channels.
     */
    private RandomAccessFile writeFile;

    public DesktopFileHandler(final File file) {
        super(file);
    }

    /**
     * The write channel is opened lazily, on the first block written, and re-opened
     * after every closeChannels(). This used to be
     * <p>
     *     new FileOutputStream(file)
     * <p>
     * which truncates the file to zero length on open. For a brand new download that is
     * harmless, but a resumed transfer already holds verified pieces on disk: they are
     * marked "have" from resume data and never re-read, so the first new block written
     * after a restart silently wiped every one of them. The transfer then finished with
     * a file that was zeros everywhere except the blocks downloaded since the restart.
     * <p>
     * Opening through RandomAccessFile("rw") creates the file when missing and leaves
     * existing content untouched; PieceManager positions the channel before every write.
     */
    @Override
    protected FileOutputStream allocateOutputStream() throws JED2KException {
        try {
            assert writeFile == null;
            writeFile = new RandomAccessFile(file, "rw");
            return new FileOutputStream(writeFile.getFD());
        } catch(IOException e) {
            closeWriteFile();
            throw new JED2KException(ErrorCode.IO_EXCEPTION);
        }
    }

    @Override
    protected FileInputStream allocateInputStream() throws JED2KException {
        try {
            return new FileInputStream(file);
        } catch(IOException e) {
            throw new JED2KException(ErrorCode.IO_EXCEPTION);
        }
    }

    @Override
    public void closeChannels() {
        super.closeChannels();
        closeWriteFile();
    }

    private void closeWriteFile() {
        if (writeFile == null) return;
        try {
            writeFile.close();
        } catch(IOException e) {
            log.error("unable to close write file {}", e.toString());
        } finally {
            writeFile = null;
        }
    }

    @Override
    protected void deleteFile() throws JED2KException {
        close();
        if (!file.delete()) throw new JED2KException(ErrorCode.UNABLE_TO_DELETE_FILE);
    }
}
