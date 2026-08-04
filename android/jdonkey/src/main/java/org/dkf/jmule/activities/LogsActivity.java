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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.dkf.jmule.BuildConfig;
import org.dkf.jmule.ConfigurationManager;
import org.dkf.jmule.Constants;
import org.dkf.jmule.R;
import org.dkf.jmule.util.LogBuffer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.AbstractActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/**
 * Shows the application's own log.
 * <p>
 * The point is to make a misbehaving device self-diagnosable: reproduce the problem,
 * open this screen, and read - or share - what the app actually did, without a cable
 * and adb logcat. The verbose switch turns on DEBUG-level capture, which is where the
 * protocol-level detail lives.
 *
 * @see LogBuffer
 */
public class LogsActivity extends AbstractActivity {

    /**
     * Slow enough not to fight with the user's scrolling, fast enough that the screen
     * reads as live while a search or a download is running.
     */
    private static final long REFRESH_INTERVAL_MS = 2000;

    private final Handler handler = new Handler();

    private TextView logView;
    private TextView statusView;
    private ScrollView scroller;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public LogsActivity() {
        super(R.layout.activity_logs);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setIcon(android.R.color.transparent);
        }

        logView = findView(R.id.activity_logs_text);
        statusView = findView(R.id.activity_logs_status);
        scroller = findView(R.id.activity_logs_scroll);

        CheckBox verbose = findView(R.id.activity_logs_verbose);
        verbose.setChecked(LogBuffer.isVerbose());
        verbose.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LogBuffer.setVerbose(isChecked);
                ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_VERBOSE_LOG, isChecked);
                refresh();
            }
        });

        findView(R.id.activity_logs_button_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        findView(R.id.activity_logs_button_copy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard();
            }
        });

        findView(R.id.activity_logs_button_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });

        findView(R.id.activity_logs_button_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogBuffer.clear();
                refresh();
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        List<String> lines = LogBuffer.snapshot();

        // Only follow the tail when the user is already there. Scrolling back to read
        // something and being yanked to the bottom two seconds later would make the
        // screen unusable while anything is running.
        final boolean atBottom = isScrolledToBottom();

        StringBuilder sb = new StringBuilder(lines.size() * 80);
        for (String line : lines) {
            sb.append(line).append('\n');
        }

        logView.setText(sb.length() > 0 ? sb.toString() : getString(R.string.logs_empty));
        statusView.setText(getString(R.string.logs_line_count, lines.size(), LogBuffer.CAPACITY));

        if (atBottom) {
            scroller.post(new Runnable() {
                @Override
                public void run() {
                    scroller.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private boolean isScrolledToBottom() {
        View content = scroller.getChildAt(0);
        if (content == null) {
            return true;
        }
        int bottomEdge = content.getBottom() - scroller.getHeight() - scroller.getScrollY();
        // a few pixels of slack, the tail rarely lands exactly on the boundary
        return bottomEdge <= 16;
    }

    /**
     * Prepends what the log itself never says: which build and which device produced
     * it. Every bug report needs both.
     */
    private String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Mule on Android ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("package ").append(BuildConfig.APPLICATION_ID).append('\n');
        sb.append("android ").append(Build.VERSION.RELEASE)
                .append(" api ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("device ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("verbose ").append(LogBuffer.isVerbose()).append("\n\n");

        for (String line : LogBuffer.snapshot()) {
            sb.append(line).append('\n');
        }

        return sb.toString();
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("mule-log", report()));
        UIUtils.showLongMessage(this, R.string.logs_copied);
    }

    /**
     * Shared as a file rather than as intent text: a full buffer is well past the size
     * a Binder transaction will carry, and putting it in EXTRA_TEXT would crash the
     * share instead of sending it.
     */
    private void share() {
        Writer writer = null;
        try {
            File dir = new File(getCacheDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                UIUtils.showLongMessage(this, R.string.logs_share_failed);
                return;
            }

            File file = new File(dir, "mule-log.txt");
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(report());
            writer.flush();

            Uri uri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Mule on Android log");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, getString(R.string.logs_share)));
        } catch (Exception e) {
            UIUtils.showLongMessage(this, R.string.logs_share_failed);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
