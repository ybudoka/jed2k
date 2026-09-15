package org.dkf.jed2k.test;

import org.dkf.jed2k.AddTransferParams;
import org.dkf.jed2k.Time;
import org.dkf.jed2k.data.PieceBlock;
import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.protocol.Hash;
import org.dkf.jed2k.protocol.TransferResumeData;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The payload the app writes next to an unfinished download so it can be picked up again
 * after the private database is gone - a reinstall, a "clear data", or a second install
 * of the app sharing the same download folder.
 * <p>
 * A record is only worth writing if reading it back yields the same transfer, hash, size
 * and, above all, the same map of which pieces are already on disk. Without the piece
 * map a "recovered" download would start over from nothing, which is the opposite of
 * what it is for.
 */
public class AddTransferParamsRoundTripTest {

    private static AddTransferParams sample() throws JED2KException {
        final TransferResumeData trd = new TransferResumeData();
        trd.hashes.add(Hash.EMULE);
        trd.hashes.add(Hash.TERMINAL);
        trd.hashes.add(Hash.INVALID);

        trd.pieces.resize(3);
        trd.pieces.setBit(0);
        trd.pieces.setBit(2);

        trd.downloadedBlocks.add(new PieceBlock(1, 0));
        trd.downloadedBlocks.add(new PieceBlock(1, 17));
        trd.downloadedBlocks.add(new PieceBlock(1, 42));

        final AddTransferParams atp = new AddTransferParams(Hash.EMULE
                , Time.currentTimeMillis()
                , 1234567890L
                , new File("/storage/emulated/0/Download/Incomplete/debian.iso")
                , false);
        atp.resumeData.setData(trd);
        return atp;
    }

    private static AddTransferParams roundTrip(final AddTransferParams source) throws JED2KException {
        final ByteBuffer out = ByteBuffer.allocate(source.bytesCount());
        out.order(ByteOrder.LITTLE_ENDIAN);
        source.put(out);

        // exactly what gets written to the sidecar: the bytes actually produced
        final byte[] bytes = new byte[out.position()];
        System.arraycopy(out.array(), 0, bytes, 0, bytes.length);

        final ByteBuffer in = ByteBuffer.wrap(bytes);
        in.order(ByteOrder.LITTLE_ENDIAN);

        final AddTransferParams restored = new AddTransferParams();
        restored.get(in);
        return restored;
    }

    @Test
    public void testIdentityIsPreserved() throws JED2KException {
        final AddTransferParams source = sample();
        final AddTransferParams restored = roundTrip(source);

        assertEquals(source.getHash(), restored.getHash());
        assertEquals(source.getSize().longValue(), restored.getSize().longValue());
        assertEquals(source.getCreateTime().longValue(), restored.getCreateTime().longValue());
        assertEquals(source.getFilepath().asString(), restored.getFilepath().asString());
    }

    /**
     * The whole point of the record.
     */
    @Test
    public void testPieceMapSurvives() throws JED2KException {
        final AddTransferParams restored = roundTrip(sample());
        final TransferResumeData trd = restored.resumeData.getData();

        assertTrue("resume data missing after the round trip", trd != null);
        assertTrue(trd.pieces.getBit(0));
        assertTrue(!trd.pieces.getBit(1));
        assertTrue(trd.pieces.getBit(2));

        assertEquals(3, trd.hashes.size());
        assertEquals(Hash.EMULE, trd.hashes.get(0));
        assertEquals(Hash.TERMINAL, trd.hashes.get(1));

        assertEquals(3, trd.downloadedBlocks.size());
        assertEquals(new PieceBlock(1, 0), trd.downloadedBlocks.get(0));
        assertEquals(new PieceBlock(1, 17), trd.downloadedBlocks.get(1));
        assertEquals(new PieceBlock(1, 42), trd.downloadedBlocks.get(2));
    }

    /**
     * bytesCount() is what sizes the write buffer; if it under-reports, put() overflows
     * and the record is written truncated.
     */
    @Test
    public void testDeclaredSizeCoversWhatIsWritten() throws JED2KException {
        final AddTransferParams source = sample();
        final ByteBuffer out = ByteBuffer.allocate(source.bytesCount());
        out.order(ByteOrder.LITTLE_ENDIAN);
        source.put(out);

        assertTrue("put() wrote " + out.position() + " into a buffer of " + source.bytesCount(),
                out.position() <= source.bytesCount());
    }

    /**
     * A record travels with its file, so the path in it is whatever machine wrote it.
     * The scan retargets it to where the file actually is, and that has to stick.
     */
    @Test
    public void testFilepathCanBeRetargeted() throws JED2KException {
        final AddTransferParams restored = roundTrip(sample());
        restored.getFilepath().assignString("/storage/emulated/0/Download/Incomplete/debian (2).iso");

        final AddTransferParams again = roundTrip(restored);
        assertEquals("/storage/emulated/0/Download/Incomplete/debian (2).iso"
                , again.getFilepath().asString());
    }
}
