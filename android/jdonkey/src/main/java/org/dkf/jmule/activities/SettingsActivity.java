/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Emil Suleymanov (sssemil)
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

package org.dkf.jmule.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import android.net.Uri;

import org.dkf.jmule.AndroidPlatform;
import org.dkf.jmule.ConfigurationManager;
import org.dkf.jmule.LollipopFileSystem;
import org.dkf.jmule.Platforms;
import org.dkf.jmule.Constants;
import org.dkf.jmule.ED2KService;
import org.dkf.jmule.Engine;
import org.dkf.jmule.R;
import org.dkf.jmule.StoragePicker;
import org.dkf.jmule.util.NightMode;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.preference.NumberPickerPreference;
import org.dkf.jmule.views.preference.StoragePreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * See {@link ConfigurationManager}
 *
 * @author gubatron
 * @author aldenml
 * @author sssemil
 */
public class SettingsActivity extends PreferenceActivity {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsActivity.class);
    private static final boolean INTERNAL_BUILD = true;
    private static String currentPreferenceKey = null;
    private boolean finishOnBack = false;

    @Override
    protected void onResume() {
        super.onResume();
        setupComponents();
        initializePreferenceScreen(getPreferenceScreen());
        if (currentPreferenceKey != null) {
            onPreferenceTreeClick(getPreferenceScreen(), getPreferenceManager().findPreference(currentPreferenceKey));
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // applies the light/dark preference before any resource is resolved
        super.attachBaseContext(NightMode.wrap(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.application_preferences);

        hideActionBarIcon(getActionBar());

        setupComponents();

        String action = getIntent().getAction();
        if (action != null) {
            getIntent().setAction(null);
            if (action.equals(Constants.ACTION_SETTINGS_SELECT_STORAGE)) {
                StoragePreference.invokeStoragePreference(this);
            } else if (action.equals(Constants.ACTION_SETTINGS_OPEN_TORRENT_SETTINGS)) {
                finishOnBack = true;
                openPreference("jmule.prefs.transfer.preference_category");
                return;
            }
        }

        updateConnectSwitch();
    }

    private void hideActionBarIcon(ActionBar bar) {
        if (bar != null) {
            LOG.info("set home button enabled");
            bar.setHomeButtonEnabled(true);
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            bar.setDisplayShowTitleEnabled(true);
            bar.setIcon(android.R.color.transparent);
        }
    }

    private void setupComponents() {
        setupConnectSwitch();
        useDhtCheckbox();
        setupNickname();
        setupListenPort();
        setupServerReconnect();
        setupPingServer();
        setupStorageOption();
        setupOtherOptions();
        setupTransferOptions();
        useWordsFilterCheckbox();
        shareMediaDownloadsCheckbox();
        setupThemeOption();
        setupRescan();
        setupLogs();
        setupAbout();
    }

    /**
     * Distinct from StoragePicker's own code so onActivityResult can tell "the user
     * picked a download folder" from "the user picked a folder to scan".
     */
    private static final int SCAN_FOLDER_REQUEST_CODE = 1267124;

    /**
     * Two ways to pick up unfinished downloads the app has lost track of.
     * <p>
     * The app's private database is what normally remembers them, and it does not
     * survive a reinstall, a "clear data", or a second install of the app sharing the
     * same download folder. The resume record written next to each unfinished file does,
     * so a folder can be re-read at any time - the app's own Incomplete folder, or one
     * the user points at.
     */
    private void setupRescan() {
        Preference folders = findPreference("jmule.prefs.show_folders");
        if (folders != null) {
            folders.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(SettingsActivity.this, FoldersActivity.class));
                    return true;
                }
            });
        }

        Preference rescan = findPreference("jmule.prefs.rescan_incomplete");
        if (rescan != null) {
            rescan.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    runRescan(null);
                    return true;
                }
            });
        }

        Preference pick = findPreference("jmule.prefs.rescan_folder");
        if (pick != null) {
            pick.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(StoragePicker.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                    startActivityForResult(intent, SCAN_FOLDER_REQUEST_CODE);
                    return true;
                }
            });
        }
    }

    /**
     * @param dir folder to look in, or null for the app's Incomplete folder
     */
    private void runRescan(final File dir) {
        final int recovered = Engine.instance().rescanIncomplete(dir);

        if (recovered < 0) {
            UIUtils.showLongMessage(this, R.string.rescan_not_running);
            return;
        }

        if (recovered == 0) {
            UIUtils.showInformationDialog(this, R.string.rescan_none
                    , R.string.rescan_incomplete, false, null);
            return;
        }

        UIUtils.showLongMessage(this, getString(R.string.rescan_found, recovered));
    }

    /**
     * Opens the log screen. Same explicit-Intent treatment as the About entry.
     */
    private void setupLogs() {
        Preference logs = findPreference("jmule.prefs.show_logs");
        if (logs == null) {
            return;
        }

        logs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(SettingsActivity.this, LogsActivity.class));
                return true;
            }
        });
    }

    /**
     * Opens the About screen. Previously an <intent> element in
     * application_preferences.xml with a hardcoded android:targetPackage, which breaks
     * as soon as the applicationId carries a suffix. An explicit Intent built from the
     * class resolves against whichever package is running.
     */
    private void setupAbout() {
        Preference about = findPreference("jmule.prefs.show_about");
        if (about == null) {
            return;
        }

        about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
                return true;
            }
        });
    }

    /**
     * Light / dark / follow-the-system. The choice is read in
     * {@link org.dkf.jmule.util.NightMode#wrap(Context)} when an activity attaches its
     * base context, so every open activity has to be rebuilt for it to take effect.
     */
    private void setupThemeOption() {
        final ListPreference themePreference = (ListPreference) findPreference(Constants.PREF_KEY_GUI_THEME);
        if (themePreference == null) {
            return;
        }

        if (themePreference.getValue() == null) {
            themePreference.setValue(Constants.THEME_SYSTEM);
        }
        themePreference.setSummary(themePreference.getEntry());

        themePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String value = (newValue != null) ? newValue.toString() : Constants.THEME_SYSTEM;
                ConfigurationManager.instance().setString(Constants.PREF_KEY_GUI_THEME, value);

                int index = themePreference.findIndexOfValue(value);
                if (index >= 0) {
                    themePreference.setSummary(themePreference.getEntries()[index]);
                }

                // recreate() re-runs attachBaseContext(); MainActivity picks the new
                // theme up the same way when it is resumed and recreated behind us
                recreate();
                return true;
            }
        });
    }

    private void setupTransferOptions() {
        setupMaxTotalConnections();
    }

    private void setupNickname() {
        EditTextPreference nickPreference = (EditTextPreference)findPreference(Constants.PREF_KEY_NICKNAME);
        if (nickPreference != null) {
            nickPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LOG.info("new nick {}", (String)newValue);
                    Engine.instance().setNickname((String)newValue);
                    Engine.instance().configureServices();
                    return true;
                }
            });
        }
    }

    private void setupMaxTotalConnections() {
        NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TRANSFER_MAX_TOTAL_CONNECTIONS);
        if (pickerPreference != null) {
            pickerPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LOG.info("explicit setup total connections to {}", (int)newValue);
                    Engine.instance().setMaxConnections((int)newValue);
                    Engine.instance().configureServices();
                    return true;
                }
            });
        }
    }

    private void setupListenPort() {
        NumberPickerPreference pickerPref = (NumberPickerPreference) findPreference(Constants.PREF_KEY_LISTEN_PORT);
        if (pickerPref != null) {
            pickerPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LOG.info("explicit set listen port {}", (int)newValue);
                    Engine.instance().setListenPort((int)newValue);
                    Engine.instance().configureServices();
                    return true;
                }
            });
        }
    }

    private void setupServerReconnect() {
        final CheckBoxPreference serverReconnect = (CheckBoxPreference) findPreference(Constants.PREF_KEY_RECONNECT_TO_SERVER);
        if (serverReconnect != null) {
            serverReconnect.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().setReconnectToServer((boolean) newValue);
                    Engine.instance().configureServices();
                    return true;
                }
            });
        } else {
            LOG.error("Unable to find check box {}", Constants.PREF_KEY_RECONNECT_TO_SERVER);
        }
    }

    private void setupPingServer() {
        final CheckBoxPreference serverPing = (CheckBoxPreference) findPreference(Constants.PREF_KEY_PING_SERVER);
        if (serverPing != null) {
            serverPing.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().setServerPing((boolean) newValue);
                    Engine.instance().configureServices();
                    return true;
                }
            });
        } else {
            LOG.error("Unable to find check box {}", Constants.PREF_KEY_PING_SERVER);
        }
    }

    private void setupOtherOptions() {
        setupPermanentStatusNotificationOption();
        setupVibrateOnDownloadCompletedOption();
        setupForwardPortsOption();
    }

    private void setupPermanentStatusNotificationOption() {
        final CheckBoxPreference enablePermanentStatusNotification = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
        if (enablePermanentStatusNotification != null) {
            enablePermanentStatusNotification.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean notificationEnabled = (boolean) newValue;
                    Engine.instance().setPermanentNotification(notificationEnabled);
                    if (!notificationEnabled) {
                        NotificationManager notificationService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        if (notificationService != null) {
                            notificationService.cancel(ED2KService.ED2K_STATUS_NOTIFICATION);
                        }
                    }
                    return true;
                }
            });
        }
    }


    private void setupVibrateOnDownloadCompletedOption() {
        final CheckBoxPreference vibrateOnDownloadCompleted = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD);
        if (vibrateOnDownloadCompleted != null) {
            vibrateOnDownloadCompleted.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().setVibrateOnDownloadCompleted((boolean) newValue);
                    return true;
                }
            });
        }
    }

    private void setupForwardPortsOption() {
        final CheckBoxPreference vibrateOnDownloadCompleted = (CheckBoxPreference) findPreference(Constants.PREF_KEY_FORWARD_PORTS);
        if (vibrateOnDownloadCompleted != null) {
            vibrateOnDownloadCompleted.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().forwardPorts((boolean) newValue);
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateConnectSwitch() {
        SwitchPreference preference = (SwitchPreference) findPreference("jmule.prefs.internal.connect_disconnect");
        if (preference != null) {
            final OnPreferenceChangeListener onPreferenceChangeListener = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);

            preference.setSummary(R.string.ed2k_network_summary);
            preference.setEnabled(true);

            if (Engine.instance().isStarted()) {
                preference.setChecked(true);
            } else if (Engine.instance().isStarting() || Engine.instance().isStopping()) {
                connectSwitchImOnIt(preference);
            } else if (Engine.instance().isStopped()) {
                preference.setChecked(false);
            }

            preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }
    }

    private void connectSwitchImOnIt(SwitchPreference preference) {
        final OnPreferenceChangeListener onPreferenceChangeListener = preference.getOnPreferenceChangeListener();
        preference.setOnPreferenceChangeListener(null);
        preference.setEnabled(false);
        preference.setSummary(R.string.im_on_it);
        preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
    }

    private void setupConnectSwitch() {
        SwitchPreference preference = (SwitchPreference) findPreference("jmule.prefs.internal.connect_disconnect");
        if (preference != null) {
            preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean newStatus = ((Boolean) newValue).booleanValue();
                    LOG.info("setup connect switch listener {}", newStatus?"ON":"OFF");
                    if (Engine.instance().isStarted() && !newStatus) {
                        disconnect();
                    } else if (newStatus && (Engine.instance().isStopped())) {
                        connect();
                    }
                    return true;
                }
            });
        }
    }

    private void useDhtCheckbox() {
        final CheckBoxPreference useDht = (CheckBoxPreference) findPreference(Constants.PREF_KEY_CONNECT_DHT);
        if (useDht != null) {
            useDht.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().useDht((boolean) newValue);
                    return true;
                }
            });
        }
    }

    private void useWordsFilterCheckbox() {
        final CheckBoxPreference showAll = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_SAFE_MODE);
        if (showAll != null) {
            showAll.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().setSafeMode((boolean) newValue);
                    return true;
                }
            });
        }
    }

    private void shareMediaDownloadsCheckbox() {
        final CheckBoxPreference shareMediaDownloads = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_SHARE_MEDIA_DOWNLOADS);
        if (shareMediaDownloads != null) {
            shareMediaDownloads.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Engine.instance().publishDownloadedFile((boolean) newValue);
                    return true;
                }
            });
        }
    }

    private void setupStorageOption() {
        // intentional repetition of preference value here
        String kitkatKey = "jmule.prefs.storage.path";
        String lollipopKey = "jmule.prefs.storage.path_asf";

        PreferenceCategory category = (PreferenceCategory) findPreference("jmule.prefs.general");

        if (AndroidPlatform.saf()) {
            // make sure this won't be saved for kitkat
            Preference p = findPreference(kitkatKey);
            if (p != null) {
                category.removePreference(p);
            }
            p = findPreference(lollipopKey);
            if (p != null) {
                p.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        LOG.info("listener setup storage path {}", newValue.toString());
                        StoragePreference.updateStorageOptionSummary(SettingsActivity.this, newValue.toString());
                        return true;
                    }
                });
                LOG.info("update storage option {}", ConfigurationManager.instance().getStoragePath());
                StoragePreference.updateStorageOptionSummary(this, ConfigurationManager.instance().getStoragePath());
            }
        } else {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                category.removePreference(p);
            }
        }
    }

    @Override
    public void startActivity(Intent intent) {
        if (intent != null && StoragePicker.ACTION_OPEN_DOCUMENT_TREE.equals(intent.getAction())) {
            StoragePicker.show(this);
        } else {
            super.startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_FOLDER_REQUEST_CODE) {
            runRescan(folderFromTree(resultCode, data));
        }
        else if (requestCode == StoragePicker.SELECT_FOLDER_REQUEST_CODE) {
            StoragePreference.onDocumentTreeActivityResult(this, requestCode, resultCode, data);
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Turns the document tree the picker returns back into a path the scan can read.
     * Returns null when the user backed out or the tree cannot be resolved, in which
     * case the scan falls back to the Incomplete folder.
     */
    private File folderFromTree(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return null;
        }

        try {
            final Uri tree = data.getData();
            getContentResolver().takePersistableUriPermission(tree
                    , data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

            if (AndroidPlatform.saf()) {
                LollipopFileSystem fs = (LollipopFileSystem) Platforms.fileSystem();
                String path = fs.getTreePath(tree);
                return (path != null) ? new File(path) : null;
            }
        } catch (Throwable t) {
            LOG.warn("unable to resolve the picked folder {}", t.toString());
        }

        return null;
    }

    private void connect() {
        final Activity context = this;
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                LOG.info("listen port {}", ConfigurationManager.instance().getLong(Constants.PREF_KEY_LISTEN_PORT));
                Engine.instance().setListenPort((int)ConfigurationManager.instance().getLong(Constants.PREF_KEY_LISTEN_PORT));
                Engine.instance().setMaxConnections((int)ConfigurationManager.instance().getLong(Constants.PREF_KEY_TRANSFER_MAX_TOTAL_CONNECTIONS));
                Engine.instance().setNickname(ConfigurationManager.instance().getString(Constants.PREF_KEY_NICKNAME));
                Engine.instance().setReconnectToServer(ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_RECONNECT_TO_SERVER));
                Engine.instance().setServerPing(ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_PING_SERVER));

                LOG.info("configuration {} max conn {} port {} reconnect to server {}"
                        , ConfigurationManager.instance().getString(Constants.PREF_KEY_NICKNAME)
                        , (int)ConfigurationManager.instance().getLong(Constants.PREF_KEY_TRANSFER_MAX_TOTAL_CONNECTIONS)
                        , (int)ConfigurationManager.instance().getLong(Constants.PREF_KEY_LISTEN_PORT)
                        , ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_RECONNECT_TO_SERVER));
            }

            @Override
            protected Void doInBackground(Void... params) {
                Engine.instance().startServices();

                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SwitchPreference preference = (SwitchPreference) findPreference("jmule.prefs.internal.connect_disconnect");
                        connectSwitchImOnIt(preference);
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                UIUtils.showShortMessage(context, R.string.toast_on_connect);
                updateConnectSwitch();
            }
        };

        task.execute();
    }

    private void disconnect() {
        final Context context = this;
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Engine.instance().stopServices(true);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                UIUtils.showShortMessage(context, R.string.toast_on_disconnect);
                updateConnectSwitch();
            }
        };

        task.execute();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean r = super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof PreferenceScreen) {
            initializePreferenceScreen((PreferenceScreen) preference);
            currentPreferenceKey = preference.getKey();
        }
        return r;
    }

    /**
     * HOW TO HIDE A NESTED PreferenceScreen ActionBar icon.
     * The nested PreferenceScreens are basically Dialog instances,
     * if we want to hide the icon on those, we need to get their dialog.getActionBar()
     * instance, hide the icon, and then we need to set the click listeners for the
     * dialog's laid out views. Here we do all that.
     *
     * @param preferenceScreen
     */
    private void initializePreferenceScreen(PreferenceScreen preferenceScreen) {
        if (preferenceScreen == null) {
            return;
        }

        final Dialog dialog = preferenceScreen.getDialog();
        if (dialog != null) {

            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    LOG.info("on click cancel");
                    dialog.dismiss();
                    if (finishOnBack) {
                        finish();
                    }
                }
            });

            hideActionBarIcon(dialog.getActionBar());
            View homeButton = dialog.findViewById(android.R.id.home);
            LOG.info("home btn: {}", homeButton!=null?"yes":"no");

            if (homeButton != null) {
                OnClickListener dismissDialogClickListener = new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LOG.info("on click back");
                        if (finishOnBack) {
                            finish();
                            return;
                        }
                        dialog.dismiss();
                    }
                };

                ViewParent homeBtnContainer = homeButton.getParent();
                if (homeBtnContainer instanceof FrameLayout) {
                    ViewGroup containerParent = (ViewGroup) homeBtnContainer.getParent();

                    if (containerParent instanceof LinearLayout) {
                        LOG.info("set to linear parent");
                        containerParent.setOnClickListener(dismissDialogClickListener);
                    } else {
                        LOG.info("set to frame parent");
                        ((FrameLayout) homeBtnContainer).setOnClickListener(dismissDialogClickListener);
                    }
                } else {
                    LOG.info("set to btn");
                    homeButton.setOnClickListener(dismissDialogClickListener);
                }

                homeButton.setOnClickListener(dismissDialogClickListener);
            }
        }
    }

    private void openPreference(String key) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final ListAdapter listAdapter = preferenceScreen.getRootAdapter();

        final int itemsCount = listAdapter.getCount();
        int itemNumber;
        for (itemNumber = 0; itemNumber < itemsCount; ++itemNumber) {
            if (listAdapter.getItem(itemNumber).equals(findPreference(key))) {
                preferenceScreen.onItemClick(null, null, itemNumber, 0);
                break;
            }
        }
    }
}
