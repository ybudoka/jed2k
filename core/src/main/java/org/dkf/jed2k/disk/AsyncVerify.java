package org.dkf.jed2k.disk;

import org.dkf.jed2k.Transfer;
import org.dkf.jed2k.exception.ErrorCode;
import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.protocol.BitField;
import org.dkf.jed2k.protocol.Hash;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * re-hash every piece of the transfer's file straight from disk and report which ones
 * match the transfer's hash set
 * <p>
 * Runs on the disk thread after every write already queued for the transfer, so it sees
 * the file exactly as the download left it. Partial block managers are dropped first:
 * whatever they held is either on disk (and will be re-hashed here) or was never
 * completed (and will be downloaded again), and their buffers go back to the pool.
 */
public class AsyncVerify extends TransferCallable<AsyncOperationResult> {
    private final List<Hash> hashes;
    private final long fileSize;

    public AsyncVerify(final Transfer t, final List<Hash> hashes, long fileSize) {
        super(t);
        this.hashes = hashes;
        this.fileSize = fileSize;
    }

    @Override
    public AsyncOperationResult call() throws Exception {
        PieceManager pm = getTransfer().getPieceManager();
        List<ByteBuffer> buffers = pm.abort();
        try {
            BitField good = pm.verifyPieces(hashes, fileSize, new PieceManager.VerifyProgress() {
                @Override
                public void onPieceChecked(int done, int total) {
                    getTransfer().setVerifyProgress(done, total);
                }
            });
            return new AsyncVerifyResult(getTransfer(), good, buffers, ErrorCode.NO_ERROR);
        } catch(JED2KException e) {
            return new AsyncVerifyResult(getTransfer(), new BitField(hashes.size()), buffers, e.getErrorCode());
        }
    }
}
