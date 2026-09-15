package org.dkf.jed2k.disk.test;

import org.dkf.jed2k.Constants;
import org.dkf.jed2k.disk.DesktopFileHandler;
import org.dkf.jed2k.disk.PieceManager;
import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.hash.MD4;
import org.dkf.jed2k.protocol.BitField;
import org.dkf.jed2k.protocol.Hash;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PieceManagerVerifyTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // two full pieces and a partial third one
    private static final long FILE_SIZE = 2 * Constants.PIECE_SIZE + 3 * Constants.BLOCK_SIZE + 1234;
    private static final int PIECES = 3;

    private byte[] content;
    private List<Hash> hashes;

    private File writeFile(String name) throws IOException {
        content = new byte[(int) FILE_SIZE];
        new Random(42).nextBytes(content);
        hashes = new ArrayList<>();
        for (int p = 0; p < PIECES; ++p) {
            int begin = (int) (p * Constants.PIECE_SIZE);
            int end = (int) Math.min(begin + Constants.PIECE_SIZE, FILE_SIZE);
            MD4 md4 = new MD4();
            md4.update(content, begin, end - begin);
            hashes.add(Hash.fromBytes(md4.digest()));
        }

        File f = folder.newFile(name);
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.write(content);
        }
        return f;
    }

    private PieceManager manager(File f) {
        int blocksInLastPiece = (int) ((FILE_SIZE % Constants.PIECE_SIZE + Constants.BLOCK_SIZE - 1) / Constants.BLOCK_SIZE);
        return new PieceManager(new DesktopFileHandler(f), PIECES, blocksInLastPiece);
    }

    @Test
    public void intactFilePassesEveryPiece() throws IOException, JED2KException {
        PieceManager pm = manager(writeFile("ok.dat"));
        BitField good = pm.verifyPieces(hashes, FILE_SIZE);
        assertEquals(PIECES, good.count());
        pm.releaseFile(false);
    }

    @Test
    public void corruptedPieceIsReported() throws IOException, JED2KException {
        File f = writeFile("corrupt.dat");
        // flip one byte in the middle of piece 1 - what a truncate-and-rewrite leaves behind
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(Constants.PIECE_SIZE + 100);
            raf.write(content[(int) Constants.PIECE_SIZE + 100] ^ 0xFF);
        }

        PieceManager pm = manager(f);
        BitField good = pm.verifyPieces(hashes, FILE_SIZE);
        assertTrue(good.getBit(0));
        assertFalse(good.getBit(1));
        assertTrue(good.getBit(2));
        pm.releaseFile(false);
    }

    @Test
    public void zeroedPieceIsReported() throws IOException, JED2KException {
        File f = writeFile("zeroed.dat");
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(0);
            raf.write(new byte[(int) Constants.PIECE_SIZE]);
        }

        PieceManager pm = manager(f);
        BitField good = pm.verifyPieces(hashes, FILE_SIZE);
        assertFalse(good.getBit(0));
        assertTrue(good.getBit(1));
        assertTrue(good.getBit(2));
        pm.releaseFile(false);
    }

    @Test
    public void shortFileFailsMissingPiecesOnly() throws IOException, JED2KException {
        File f = writeFile("short.dat");
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.setLength(2 * Constants.PIECE_SIZE + 10);
        }

        PieceManager pm = manager(f);
        BitField good = pm.verifyPieces(hashes, FILE_SIZE);
        assertTrue(good.getBit(0));
        assertTrue(good.getBit(1));
        assertFalse(good.getBit(2));
        pm.releaseFile(false);
    }
}
