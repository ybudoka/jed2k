package org.dkf.jed2k.test;

import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.protocol.BitField;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for two sign bugs in BitField.
 */
public class BitFieldTest {

    /**
     * count() indexes a lookup table with the high nibble of each byte. Java bytes are
     * signed, so for any byte >= 0x80 the unmasked shift sign-extends to a negative
     * index and throws ArrayIndexOutOfBoundsException.
     */
    @Test
    public void testCountWithHighBitsSet() {
        BitField bf = new BitField(16);
        bf.setAll();
        assertEquals(16, bf.count());

        BitField single = new BitField(8);
        single.setBit(0); // 0x80 - high bit, the byte is negative
        assertEquals(1, single.count());

        BitField mixed = new BitField(24);
        mixed.setBit(0);
        mixed.setBit(7);
        mixed.setBit(9);
        mixed.setBit(23);
        assertEquals(4, mixed.count());
    }

    @Test
    public void testCountEmptyAndPartial() {
        BitField bf = new BitField(20);
        assertEquals(0, bf.count());
        bf.setBit(19);
        assertEquals(1, bf.count());
    }

    /**
     * The size is written as an unsigned 16 bit value but was read back with a signed
     * getShort(). Anything above 32767 came back negative, and bitsToBytes() of a
     * negative count reaches new byte[negative].
     * <p>
     * A bitfield this large means a piece count this large; at the ed2k piece size that
     * is a file in the hundreds of gigabytes, so this is a robustness fix rather than an
     * everyday path.
     */
    @Test
    public void testRoundTripAboveSignedShortRange() throws JED2KException {
        final int bits = 40000;
        BitField bf = new BitField(bits);
        bf.setBit(0);
        bf.setBit(39999);
        bf.setBit(20000);

        ByteBuffer dst = ByteBuffer.allocate(bf.bytesCount()).order(ByteOrder.LITTLE_ENDIAN);
        bf.put(dst);
        dst.flip();

        BitField restored = new BitField();
        restored.get(dst);

        assertEquals(bits, restored.size());
        assertTrue(restored.getBit(0));
        assertTrue(restored.getBit(20000));
        assertTrue(restored.getBit(39999));
        assertEquals(3, restored.count());
        assertEquals(bf, restored);
    }

    @Test
    public void testRoundTripOrdinarySize() throws JED2KException {
        BitField bf = new BitField(1000);
        for (int i = 0; i < 1000; i += 3) bf.setBit(i);

        ByteBuffer dst = ByteBuffer.allocate(bf.bytesCount()).order(ByteOrder.LITTLE_ENDIAN);
        bf.put(dst);
        dst.flip();

        BitField restored = new BitField();
        restored.get(dst);

        assertEquals(bf, restored);
        assertEquals(bf.count(), restored.count());
    }
}
