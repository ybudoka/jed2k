package org.dkf.jmule.adapters.menu;

import android.content.Context;

import org.dkf.jmule.R;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.MenuAction;

/**
 * verify and repair: re-hash the file on disk against the ed2k hash set and download
 * again every piece that does not match
 */
public final class VerifyTransferMenuAction extends MenuAction {
    private final Transfer download;

    public VerifyTransferMenuAction(Context context, Transfer download) {
        super(context, R.drawable.ic_verify_black_24dp, R.string.verify_transfer_menu_action);
        this.download = download;
    }

    @Override
    protected void onClick(Context context) {
        if (download.isVerifying()) {
            UIUtils.showShortMessage(context, R.string.transfer_verify_already_running);
            return;
        }

        download.verify();
        UIUtils.showShortMessage(context, R.string.transfer_verify_started);
    }
}
