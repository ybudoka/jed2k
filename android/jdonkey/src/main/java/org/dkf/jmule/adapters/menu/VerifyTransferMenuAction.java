/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dkf.jmule.adapters.menu;

import android.content.Context;
import android.content.DialogInterface;

import org.dkf.jmule.R;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verify and repair: the session re-hashes the file on disk piece by piece against the
 * ed2k hash set, keeps what matches and downloads again what does not.
 * <p>
 * Pieces are checked as they arrive, so a download that ran normally is already
 * verified - but that only covers the path from the network to the disk. It says nothing
 * about a file written by an older build, truncated on resume, or touched by something
 * else since. This used to answer "is what I have the file I asked for" with a yes or a
 * no computed on the UI side; now the answer comes with the fix, and works on unfinished
 * transfers too. The work runs on the session's disk thread, the transfer shows
 * "Verifying" with its progress in the list, and the outcome arrives as a notification.
 */
public class VerifyTransferMenuAction extends MenuAction {
    private static final Logger log = LoggerFactory.getLogger(VerifyTransferMenuAction.class);

    private final Transfer transfer;

    public VerifyTransferMenuAction(Context context, Transfer transfer) {
        super(context, R.drawable.ic_verify_black_24dp, R.string.transfers_context_menu_verify);
        this.transfer = transfer;
    }

    @Override
    protected void onClick(final Context context) {
        if (context == null || transfer == null) {
            return;
        }

        if (transfer.isVerifying()) {
            UIUtils.showShortMessage(context, R.string.transfer_verify_already_running);
            return;
        }

        // Peers are dropped for the duration and a multi-gigabyte file takes a while to
        // read, so this is worth a confirmation rather than a mis-tap.
        UIUtils.showYesNoDialog(context
                , R.string.verify_confirm_message
                , R.string.verify_confirm_title
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        log.info("verify and repair requested for {}", transfer.getDisplayName());
                        transfer.verify();
                        UIUtils.showShortMessage(context, R.string.transfer_verify_started);
                    }
                });
    }
}
