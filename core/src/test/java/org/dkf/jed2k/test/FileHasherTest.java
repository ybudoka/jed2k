package org.dkf.jed2k.test;

import org.dkf.jed2k.FileHasher;
import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.hash.MD4;
import org.dkf.jed2k.protocol.Hash;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class FileHasherTest {

    private static InputStream stream(byte[] data) {
        return new ByteArrayInputStream(data);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static Hash md4(byte[] data) {
        return Hash.fromBytes(new MD4().digest(data));
    }

    /**
     * Deterministic filler, so a failure is reproducible.
     */
    private static byte[] filler(int length, int seed) {
        final byte[] data = new byte[length];
        int x = seed | 1;
        for (int i = 0; i < length; i++) {
            x = x * 1103515245 + 12345;
            data[i] = (byte) (x >>> 16);
        }
        return data;
    }

    /**
     * A file smaller than one piece hashes to the MD4 of its content, checked against
     * the published vector rather than against this codebase's own MD4.
     */
    @Test
    public void testSinglePieceMatchesTheKnownMd4Vector() throws Exception {
        final Hash h = FileHasher.hash(stream(bytes("abc")), 3, 1000, null);
        assertEquals(Hash.fromString("A448017AAF21D8525FC10AE87AA6729D"), h);
    }

    /**
     * MD4 of nothing, which is also what Hash.TERMINAL is.
     */
    @Test
    public void testEmptyFile() throws Exception {
        assertEquals(Hash.TERMINAL, FileHasher.hash(stream(new byte[0]), 0, 1000, null));
    }

    /**
     * A piece boundary that does not divide the file: the last piece is the remainder,
     * not a padded piece.
     */
    @Test
    public void testPieceBoundaries() throws Exception {
        final Hash h = FileHasher.hash(stream(bytes("abcdef")), 6, 4, null);

        final List<Hash> expected = Arrays.asList(md4(bytes("abcd")), md4(bytes("ef")));
        assertEquals(Hash.fromHashSet(expected), h);
    }

    /**
     * The rule that catches people out: when the size divides exactly, a hash of zero
     * bytes is appended, so the file hash is not simply the single piece's hash.
     */
    @Test
    public void testExactMultipleAppendsTheTerminalHash() throws Exception {
        final Hash h = FileHasher.hash(stream(bytes("abcd")), 4, 4, null);

        final List<Hash> expected = Arrays.asList(md4(bytes("abcd")), Hash.TERMINAL);
        assertEquals(Hash.fromHashSet(expected), h);

        // and it is genuinely different from the piece hash on its own
        assertNotEquals(md4(bytes("abcd")), h);
    }

    /**
     * Reads span the internal chunk size, and a piece size that is not a multiple of it
     * is where an off-by-one in the read loop would show up.
     */
    @Test
    public void testPiecesLargerThanTheReadChunk() throws Exception {
        final int pieceSize = 256 * 1024 + 7;
        final byte[] data = filler(pieceSize * 2 + 1234, 7);

        final List<Hash> expected = new ArrayList<>();
        for (int off = 0; off < data.length; off += pieceSize) {
            final int end = Math.min(off + pieceSize, data.length);
            expected.add(md4(Arrays.copyOfRange(data, off, end)));
        }

        assertEquals(Hash.fromHashSet(expected)
                , FileHasher.hash(stream(data), data.length, pieceSize, null));
    }

    /**
     * A file shorter than it claims is a different fault from a hash mismatch and must
     * not be reported as one.
     */
    @Test
    public void testTruncatedStreamIsAnError() throws IOException {
        try {
            FileHasher.hash(stream(bytes("abc")), 10, 1000, null);
            fail("expected a truncated file to be reported");
        } catch (JED2KException expected) {
            // as intended
        }
    }

    @Test
    public void testCancellationReturnsNull() throws Exception {
        final Hash h = FileHasher.hash(stream(filler(100000, 3)), 100000, 1000, new FileHasher.Progress() {
            @Override
            public void onProgress(long done, long total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        });

        assertNull(h);
    }

    @Test
    public void testProgressReachesTheTotal() throws Exception {
        final long[] last = {0, 0};

        FileHasher.hash(stream(filler(50000, 11)), 50000, 4096, new FileHasher.Progress() {
            @Override
            public void onProgress(long done, long total) {
                last[0] = done;
                last[1] = total;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });

        assertEquals(50000, last[0]);
        assertEquals(50000, last[1]);
    }

    /**
     * One flipped byte has to change the answer - otherwise the check is decorative.
     */
    @Test
    public void testACorruptedByteChangesTheHash() throws Exception {
        final byte[] good = filler(30000, 5);
        final byte[] bad = good.clone();
        bad[17931] ^= 0x01;

        assertNotEquals(FileHasher.hash(stream(good), good.length, 4096, null)
                , FileHasher.hash(stream(bad), bad.length, 4096, null));
    }
}
