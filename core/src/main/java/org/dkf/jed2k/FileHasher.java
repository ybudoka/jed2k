package org.dkf.jed2k;

import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.hash.MD4;
import org.dkf.jed2k.protocol.Hash;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Recomputes a file's ed2k hash from its content, so a finished download can be checked
 * against the hash it was requested by.
 * <p>
 * The hash is what ed2k identifies a file by, and it is the only thing that can say
 * whether what landed on disk is what was asked for. Pieces are verified as they arrive
 * during a download, but that only covers the path from the network to the disk - it
 * says nothing about a file that was resumed from a bad hash set, written by an older
 * build, or touched afterwards.
 * <p>
 * The convention is eMule's, and is the one {@link Hash#fromHashSet} expects on the
 * other side: the file is cut into pieces, each piece is MD4'd, and the file hash is the
 * MD4 over those hashes concatenated - except for a single-piece file, which is its own
 * piece hash. When the size is an exact multiple of the piece size a hash of zero bytes
 * is appended, which is why {@link Hash#TERMINAL} is MD4 of nothing.
 */
public final class FileHasher {

    /**
     * Read granularity. Small enough not to matter on a phone, large enough that a
     * multi-gigabyte file is not read a page at a time.
     */
    private static final int CHUNK = 256 * 1024;

    /**
     * Lets a caller show progress and stop early - these files run to gigabytes.
     */
    public interface Progress {
        void onProgress(final long done, final long total);

        boolean isCancelled();
    }

    private FileHasher() {
    }

    /**
     * @param in        the file's content, read once from the beginning
     * @param size      its length in bytes
     * @param pieceSize {@link Constants#PIECE_SIZE} in production; a parameter so the
     *                  piece boundaries can be exercised without nine-megabyte fixtures
     * @param progress  may be null
     * @return the ed2k hash of the content, or null if the caller cancelled
     * @throws JED2KException when the stream is shorter than {@code size} says
     */
    public static Hash hash(final InputStream in
            , final long size
            , final long pieceSize
            , final Progress progress) throws IOException, JED2KException {

        if (in == null || size < 0 || pieceSize <= 0) {
            throw new JED2KException(org.dkf.jed2k.exception.ErrorCode.ILLEGAL_ARGUMENT);
        }

        final List<Hash> pieces = new ArrayList<>();
        final byte[] buffer = new byte[CHUNK];

        long done = 0;

        while (done < size) {
            final long pieceEnd = Math.min(done + pieceSize, size);
            final MD4 md4 = new MD4();

            while (done < pieceEnd) {
                if (progress != null && progress.isCancelled()) {
                    return null;
                }

                final int want = (int) Math.min(CHUNK, pieceEnd - done);
                final int read = in.read(buffer, 0, want);

                if (read < 0) {
                    // The file is shorter than it claims: not a hash mismatch but a
                    // different fault, and worth saying so rather than reporting a
                    // wrong hash computed over what was there.
                    throw new JED2KException(org.dkf.jed2k.exception.ErrorCode.IO_EXCEPTION);
                }

                md4.update(buffer, 0, read);
                done += read;

                if (progress != null) {
                    progress.onProgress(done, size);
                }
            }

            pieces.add(Hash.fromBytes(md4.digest()));
        }

        // eMule appends the hash of a zero-length piece when the size divides exactly,
        // and Hash.TERMINAL is precisely MD4 of no bytes.
        if (size > 0 && size % pieceSize == 0) {
            pieces.add(new Hash(Hash.TERMINAL));
        }

        if (pieces.isEmpty()) {
            return new Hash(Hash.TERMINAL);
        }

        return Hash.fromHashSet(pieces);
    }

    public static Hash hash(final InputStream in, final long size, final Progress progress)
            throws IOException, JED2KException {
        return hash(in, size, Constants.PIECE_SIZE, progress);
    }
}
