package org.dkf.jed2k.disk;

import org.dkf.jed2k.Transfer;
import org.dkf.jed2k.exception.BaseErrorCode;
import org.dkf.jed2k.protocol.BitField;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * outcome of AsyncVerify, delivered to the transfer on the session thread
 */
public class AsyncVerifyResult implements AsyncOperationResult {
    private final Transfer transfer;
    private final BitField goodPieces;
    private final List<ByteBuffer> buffers;
    private final BaseErrorCode code;

    public AsyncVerifyResult(final Transfer t, final BitField goodPieces, final List<ByteBuffer> buffers, final BaseErrorCode ec) {
        transfer = t;
        this.goodPieces = goodPieces;
        this.buffers = buffers;
        code = ec;
    }

    @Override
    public void onCompleted() {
        transfer.onVerifyCompleted(goodPieces, buffers, code);
    }

    @Override
    public BaseErrorCode getCode() {
        return code;
    }
}
