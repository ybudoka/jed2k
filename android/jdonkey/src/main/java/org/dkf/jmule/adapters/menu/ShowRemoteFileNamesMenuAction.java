/*
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;

import org.dkf.jmule.R;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.MenuAction;

import java.util.List;

/**
 * Lists the names the sources advertise for a transfer.
 * <p>
 * ed2k identifies a file by its hash, not its name, so every source reports whatever
 * name it happens to have stored. They usually agree, and when they do not that is
 * worth seeing: a file whose sources call it three unrelated things is the classic
 * signature of a mislabelled or faked release.
 * <p>
 * Tapping a name copies it, which is the other half of why the list is useful.
 */
public class ShowRemoteFileNamesMenuAction extends MenuAction {

    private final Transfer transfer;

    public ShowRemoteFileNamesMenuAction(Context context, Transfer transfer) {
        super(context, R.drawable.ic_format_list_bulleted_black_24dp, R.string.remote_file_names);
        this.transfer = transfer;
    }

    @Override
    protected void onClick(Context context) {
        if (context == null || transfer == null) {
            return;
        }

        final List<String> names = transfer.getRemoteFileNames();

        if (names == null || names.isEmpty()) {
            // Names only arrive once a source has answered our file request, so an empty
            // list is the normal state for a transfer that has not connected to anyone
            // yet rather than an error.
            UIUtils.showLongMessage(context, R.string.remote_file_names_empty);
            return;
        }

        final String[] items = names.toArray(new String[0]);

        new AlertDialog.Builder(context)
                .setTitle(R.string.remote_file_names)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copy(context, items[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private static void copy(Context context, String name) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null && name != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("data", name));
            UIUtils.showLongMessage(context, R.string.transfers_context_menu_copy_name_copied);
        }
    }
}
