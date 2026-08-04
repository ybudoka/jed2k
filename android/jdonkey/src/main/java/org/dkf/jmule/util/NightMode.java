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

package org.dkf.jmule.util;

import android.content.Context;
import android.content.res.Configuration;

import org.dkf.jmule.ConfigurationManager;
import org.dkf.jmule.Constants;

/**
 * Applies the user's light / dark theme choice.
 * <p>
 * The activities here extend {@code android.app.Activity} and
 * {@code android.preference.PreferenceActivity}, not {@code AppCompatActivity}, so
 * {@code AppCompatDelegate.setDefaultNightMode()} has nothing to hook into. Instead
 * each activity wraps its base context in one whose {@link Configuration#uiMode}
 * carries the night bit we want, which is the same signal the resource loader uses to
 * pick {@code values-night/} over {@code values/}.
 * <p>
 * On {@link Constants#THEME_SYSTEM} the base context is handed back untouched, so the
 * app simply follows the platform setting (Android 10 and up; on older releases the
 * system never reports night, and the explicit Dark option is the way in).
 */
public final class NightMode {

    private NightMode() {
    }

    /**
     * @return the stored preference, defaulting to {@link Constants#THEME_SYSTEM}.
     * Never throws: ConfigurationManager.instance() raises if the store has not been
     * created yet, and this is called from attachBaseContext, early in the lifecycle.
     */
    public static String currentSetting() {
        try {
            String value = ConfigurationManager.instance().getString(Constants.PREF_KEY_GUI_THEME);
            if (Constants.THEME_LIGHT.equals(value) || Constants.THEME_DARK.equals(value)) {
                return value;
            }
        } catch (Throwable ignored) {
            // ConfigurationManager.create() runs from MainApplication.onCreate()
        }
        return Constants.THEME_SYSTEM;
    }

    /**
     * Wraps {@code base} so resources resolve against the chosen theme.
     * <p>
     * Call from {@code Activity.attachBaseContext(Context)}:
     * {@code super.attachBaseContext(NightMode.wrap(base));}
     *
     * @return a configuration-overridden context, or {@code base} itself when the
     * choice is "follow the system" or the preference store is not up yet
     */
    public static Context wrap(final Context base) {
        if (base == null) {
            return null;
        }

        final String setting = currentSetting();
        if (Constants.THEME_SYSTEM.equals(setting)) {
            return base;
        }

        final int night = Constants.THEME_DARK.equals(setting)
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;

        final Configuration configuration = new Configuration(base.getResources().getConfiguration());
        if ((configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == night) {
            return base; // already what we want, avoid an extra context
        }

        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(configuration);
    }
}
