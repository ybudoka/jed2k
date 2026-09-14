package org.dkf.jed2k.disk.test;

import org.dkf.jed2k.disk.DesktopFileHandler;
import org.dkf.jed2k.disk.FileHandler;
import org.dkf.jed2k.exception.JED2KException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Created by apavlov on 01.06.17.
 */
public class FileHandlerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void trivialFileHandlerTestUsageAfterClose() throws IOException, JED2KException {
        FileHandler fh = new DesktopFileHandler(folder.newFile("test.dat"));
        FileChannel fc = fh.getWriteChannel();
        ByteBuffer buff = ByteBuffer.allocate(10);
        buff.putInt(1).putInt(2).putShort((short)2);
        buff.flip();
        assertTrue(buff.hasRemaining());
        fc.write(buff);
        assertFalse(buff.hasRemaining());
        fh.close();
        assertFalse(fc.isOpen());
        fc = fh.getWriteChannel();
        buff.flip();
        assertTrue(buff.hasRemaining());
        fc.write(buff);
        assertFalse(buff.hasRemaining());

        FileChannel read = fh.getReadChannel();
        assertTrue(read.isOpen());
        buff.flip();
        read.read(buff);
        buff.flip();
        assertEquals(1, buff.getInt());
        assertEquals(2, buff.getInt());
        assertEquals((short)2, buff.getShort());
        assertTrue(fc.isOpen());
        assertTrue(read.isOpen());
        fh.close();
        assertFalse(fc.isOpen());
        assertFalse(read.isOpen());
    }

    /**
     * A resumed transfer already has verified data on disk. Re-opening the write channel
     * (first write after restart, or after closeChannels() on an i/o error) must keep it.
     */
    @Test
    public void reopeningWriteChannelKeepsExistingContent() throws IOException, JED2KException {
        File f = folder.newFile("resume.dat");
        byte[] existing = new byte[4096];
        for (int i = 0; i < existing.length; ++i) existing[i] = (byte) (i * 31 + 7);
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(existing);
        }

        FileHandler fh = new DesktopFileHandler(f);

        // restore path reads first, exactly like PieceManager.restoreBlock()
        ByteBuffer restored = ByteBuffer.allocate(1024);
        fh.getReadChannel().position(1024);
        while (restored.hasRemaining()) fh.getReadChannel().read(restored);
        restored.flip();
        assertEquals(ByteBuffer.wrap(existing, 1024, 1024), restored);

        // then the first new block is written somewhere in the middle
        byte[] fresh = new byte[512];
        Arrays.fill(fresh, (byte) 0x5A);
        FileChannel wc = fh.getWriteChannel();
        wc.position(2048);
        wc.write(ByteBuffer.wrap(fresh));

        // and again after channels were dropped and re-opened
        fh.closeChannels();
        wc = fh.getWriteChannel();
        wc.position(3072);
        wc.write(ByteBuffer.wrap(fresh));
        fh.close();

        byte[] expected = existing.clone();
        System.arraycopy(fresh, 0, expected, 2048, fresh.length);
        System.arraycopy(fresh, 0, expected, 3072, fresh.length);
        assertEquals(expected.length, f.length());
        assertTrue(Arrays.equals(expected, Files.readAllBytes(f.toPath())));
    }

    @Test
    public void writeChannelCreatesMissingFile() throws IOException, JED2KException {
        File f = new File(folder.getRoot(), "fresh.dat");
        assertFalse(f.exists());
        FileHandler fh = new DesktopFileHandler(f);
        FileChannel wc = fh.getWriteChannel();
        wc.position(100);
        wc.write(ByteBuffer.wrap(new byte[]{1, 2, 3}));
        fh.close();
        assertTrue(f.exists());
        assertEquals(103, f.length());
    }
}
