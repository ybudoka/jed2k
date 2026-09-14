package org.dkf.jed2k.alert;

import org.dkf.jed2k.exception.BaseErrorCode;
import org.dkf.jed2k.exception.ErrorCode;
import org.dkf.jed2k.protocol.Hash;

/**
 * result of a verify and repair pass over a transfer's file on disk
 * when ec is NO_ERROR piecesOk of piecesTotal pieces matched their hash; the rest were
 * returned to the download queue. Any other code means the file could not be checked.
 */
public class TransferVerifiedAlert extends TransferAlert {
    public final int piecesOk;
    public final int piecesTotal;
    public final BaseErrorCode ec;

    public TransferVerifiedAlert(final Hash h, int piecesOk, int piecesTotal, final BaseErrorCode ec) {
        super(h);
        this.piecesOk = piecesOk;
        this.piecesTotal = piecesTotal;
        this.ec = ec;
    }

    public boolean isOk() {
        return ec == ErrorCode.NO_ERROR;
    }

    public boolean isRepairNeeded() {
        return isOk() && piecesOk != piecesTotal;
    }

    @Override
    public String toString() {
        return "transfer verified " + super.toString() + " " + piecesOk + "/" + piecesTotal + " " + ec.getDescription();
    }
}
