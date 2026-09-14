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

package org.dkf.jmule.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.dkf.jmule.AndroidPlatform;
import org.dkf.jmule.Engine;
import org.dkf.jmule.Folders;
import org.dkf.jmule.LollipopFileSystem;
import org.dkf.jmule.Platforms;
import org.dkf.jmule.R;
import org.dkf.jmule.StoragePicker;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.AbstractActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Shows the folders the app uses and lets the user add or remove their own.
 *
 * @see Folders for what these folders are, and what they are not
 */
public class FoldersActivity extends AbstractActivity {

    private static final Logger log = LoggerFactory.getLogger(FoldersActivity.class);

    private static final int PICK_FOLDER_REQUEST_CODE = 1267125;

    private LinearLayout list;

    public FoldersActivity() {
        super(R.layout.activity_folders);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setIcon(android.R.color.transparent);
        }

        list = findView(R.id.activity_folders_list);

        findView(R.id.activity_folders_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StoragePicker.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE);
            }
        });

        rebuild();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FOLDER_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        final File dir = resolve(resultCode, data);
        if (dir == null) {
            return;
        }

        Folders.add(dir.getAbsolutePath());
        rebuild();
        scan(dir);
    }

    /**
     * The picker hands back a document tree; the scan works in paths.
     */
    private File resolve(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return null;
        }

        try {
            final Uri tree = data.getData();
            getContentResolver().takePersistableUriPermission(tree
                    , data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

            if (AndroidPlatform.saf()) {
                final LollipopFileSystem fs = (LollipopFileSystem) Platforms.fileSystem();
                final String path = fs.getTreePath(tree);
                return (path != null) ? new File(path) : null;
            }
        } catch (Throwable t) {
            log.warn("unable to resolve the picked folder {}", t.toString());
        }

        UIUtils.showLongMessage(this, R.string.folders_pick_failed);
        return null;
    }

    private void scan(final File dir) {
        final int recovered = Engine.instance().rescanIncomplete(dir);

        if (recovered < 0) {
            UIUtils.showLongMessage(this, R.string.rescan_not_running);
        } else if (recovered == 0) {
            UIUtils.showLongMessage(this, R.string.rescan_none_short);
        } else {
            UIUtils.showLongMessage(this, getString(R.string.rescan_found, recovered));
        }
    }

    private void rebuild() {
        list.removeAllViews();

        final LayoutInflater inflater = LayoutInflater.from(this);
        final List<Folders.Entry> entries = Folders.all();

        for (final Folders.Entry entry : entries) {
            final View row = inflater.inflate(R.layout.view_folder_list_item, list, false);

            final TextView role = (TextView) row.findViewById(R.id.view_folder_list_item_role);
            final TextView path = (TextView) row.findViewById(R.id.view_folder_list_item_path);
            final Button scan = (Button) row.findViewById(R.id.view_folder_list_item_scan);
            final Button remove = (Button) row.findViewById(R.id.view_folder_list_item_remove);

            role.setText(labelFor(entry.role));
            path.setText(entry.dir.getAbsolutePath());

            scan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scan(entry.dir);
                }
            });

            if (entry.isRemovable()) {
                remove.setVisibility(View.VISIBLE);
                remove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Folders.remove(entry.dir.getAbsolutePath());
                        rebuild();
                    }
                });
            } else {
                remove.setVisibility(View.GONE);
            }

            list.addView(row);
        }
    }

    private int labelFor(final Folders.Role role) {
        switch (role) {
            case DOWNLOADS:
                return R.string.folders_role_downloads;
            case INCOMPLETE:
                return R.string.folders_role_incomplete;
            default:
                return R.string.folders_role_added;
        }
    }
}
