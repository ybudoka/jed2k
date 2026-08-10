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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.dkf.jed2k.FileHasher;
import org.dkf.jed2k.protocol.Hash;
import org.dkf.jmule.R;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.FileStreams;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recomputes a finished download's ed2k hash from the file on disk and says whether it
 * matches the hash the file was requested by.
 * <p>
 * Pieces are checked as they arrive, so a download that completes normally is already
 * verified - but that only covers the path from the network to the disk. It says nothing
 * about a transfer resumed from a hash set that was wrong, a file written by an older
 * build, or one that something else has touched since. This is the answer to "is what I
 * have actually the file I asked for", and the hash is the only thing that can give it.
 */
public class VerifyTransferMenuAction extends MenuAction {

    private static final Logger log = LoggerFactory.getLogger(VerifyTransferMenuAction.class);

    /**
     * Redrawing a progress bar for every 256 KB read would spend more time on the UI
     * thread than on hashing.
     */
    private static final int PROGRESS_STEPS = 100;

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

        final File file = transfer.getFile();
        if (file == null) {
            UIUtils.showLongMessage(context, R.string.verify_unreadable);
            return;
        }

        final Hash expected;
        try {
            expected = Hash.fromString(transfer.getHash());
        } catch (Throwable t) {
            log.warn("unable to parse the transfer hash {}", t.toString());
            UIUtils.showLongMessage(context, R.string.verify_unreadable);
            return;
        }

        final long size = transfer.getSize();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final Handler ui = new Handler(Looper.getMainLooper());

        final ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(PROGRESS_STEPS);
        bar.setIndeterminate(false);
        final int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);

        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle(R.string.verify_running)
                .setMessage(file.getName())
                .setView(bar)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelled.set(true);
                    }
                })
                .create();

        progress.show();

        // A gigabyte takes a while and must not run on the UI thread. The dialog is the
        // only thing holding a reference to this work, and cancelling it stops the read.
        new Thread(new Runnable() {
            @Override
            public void run() {
                Hash actual = null;
                boolean failed = false;
                InputStream in = null;

                try {
                    in = FileStreams.open(file);
                    actual = FileHasher.hash(in, size, new FileHasher.Progress() {
                        private int lastStep = -1;

                        @Override
                        public void onProgress(final long done, final long total) {
                            if (total <= 0) return;
                            final int step = (int) (done * PROGRESS_STEPS / total);
                            if (step == lastStep) return;
                            lastStep = step;
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    bar.setProgress(step);
                                }
                            });
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled.get();
                        }
                    });
                } catch (Throwable t) {
                    log.warn("verification of {} failed: {}", file.getName(), t.toString());
                    failed = true;
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }

                final boolean unreadable = failed;
                final Hash result = actual;

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            progress.dismiss();
                        } catch (Throwable ignored) {
                        }

                        if (cancelled.get()) {
                            return;
                        }

                        if (unreadable || result == null) {
                            UIUtils.showInformationDialog(context, R.string.verify_unreadable
                                    , R.string.verify_failed_title, false, null);
                            return;
                        }

                        final boolean ok = expected.equals(result);
                        log.info("verification of {}: {}", file.getName(), ok ? "match" : "MISMATCH");

                        UIUtils.showInformationDialog(context
                                , ok ? R.string.verify_ok : R.string.verify_mismatch
                                , ok ? R.string.verify_ok_title : R.string.verify_failed_title
                                , false, null);
                    }
                });
            }
        }, "verify-" + file.getName()).start();
    }
}
