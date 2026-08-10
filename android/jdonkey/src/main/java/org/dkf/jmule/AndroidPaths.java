/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package org.dkf.jmule;


import android.app.Application;
import android.content.Context;
import android.os.Environment;

import org.apache.commons.io.FilenameUtils;
import org.dkf.jmule.fragments.TransfersFragment;
import org.dkf.jmule.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 * JED2K stores all files to one place without sub dirs
 */
public final class AndroidPaths {
    // The slf4j imports were here before anything used them; data() logs now when it
    // declines a configured folder, and that needs an actual logger.
    private static final Logger LOG = LoggerFactory.getLogger(AndroidPaths.class);

    private static final boolean USE_EXTERNAL_STORAGE_DIR_ON_OR_AFTER_ANDROID_10 = true;
    private final Application app;

    private static final Map<Byte, String> fileTypeFolders = new HashMap<>();
    private static final Object fileTypeFoldersLock = new Object();

    public AndroidPaths(Application app) {
        this.app = app;
    }

    /**
     * Where downloads are written.
     * <p>
     * The folder the user picks in Settings is honoured here. It used to be stored, shown
     * in the wizard, shown in the settings summary, and used to report free space - and
     * then ignored at the one moment it mattered, because this method returned the public
     * download folder whatever it said. Picking an SD card did nothing.
     * <p>
     * The choice is only taken when the folder is actually usable, which is asked of the
     * platform file system rather than of File: under scoped storage a folder granted
     * through the document picker is writable through a descriptor while File.canWrite()
     * says no. An unusable choice falls back to the platform default with a line in the
     * log saying which and why, rather than failing every download silently.
     */
    public File data() {
        final File chosen = configured();
        if (chosen != null) {
            return chosen;
        }

        return platformDefault();
    }

    /**
     * @return the configured download folder when it is set and usable, else null
     */
    private File configured() {
        String path = null;

        try {
            path = ConfigurationManager.instance().getStoragePath();
        } catch (Throwable t) {
            // configuration not up yet - during very early startup
            return null;
        }

        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        final File dir = new File(path);

        if (dir.equals(platformDefault())) {
            return dir;     // nothing to check, it is the fallback anyway
        }

        try {
            final FileSystem fs = Platforms.fileSystem();

            if (!fs.exists(dir) && !fs.mkdirs(dir)) {
                LOG.warn("configured storage {} cannot be created, using the default", dir);
                return null;
            }

            if (!fs.isDirectory(dir)) {
                LOG.warn("configured storage {} is not a directory, using the default", dir);
                return null;
            }

            if (!fs.canWrite(dir)) {
                LOG.warn("configured storage {} is not writable, using the default", dir);
                return null;
            }

            return dir;
        } catch (Throwable t) {
            LOG.warn("configured storage {} unusable ({}), using the default", dir, t.toString());
            return null;
        }
    }

    private File platformDefault() {
        if (SystemUtils.hasAndroid10OrNewer()) {
            if (SystemUtils.hasAndroid10()) {
                return app.getExternalFilesDir(null);
            }

            // On Android 11 and up, they finally let us use File objects in the public download directory as long as we have permission from the user
            return android11AndUpStorage();
        }

        /* For Older versions of Android where we used to have access to write to external storage
         *  <externalStoragePath>/Download/FrostWire/
         */
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
    }

    public static byte getFileType(String filePath, boolean returnTorrentsAsDocument) {
        byte result = Constants.FILE_TYPE_UNKNOWN;

        MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(filePath));

        if (mt != null) {
            result = (byte) mt.getId();
        }

        if (returnTorrentsAsDocument && result == Constants.FILE_TYPE_TORRENTS) {
            result = Constants.FILE_TYPE_DOCUMENTS;
        }

        return result;
    }

    public static File android11AndUpStorage() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }


    /**
     * FILE_TYPE_AUDIO -> "Music"
     * FILE_TYPE_VIDEOS -> "Movies"
     * ...
     * Based on Android's Environment.DIRECTORY_XXX constants
     * We'll use these for MediaStore relative path prefixes concatenated to "/FrostWire" so the user
     * can easily find what's been downloaded with FrostWire in external folders.
     */
    private static String getFileTypeExternalRelativeFolderName(byte fileType) {
        synchronized (fileTypeFoldersLock) {
            // thread safe lazy load check
            if (fileTypeFolders.size() == 0) {
                fileTypeFolders.put(Constants.FILE_TYPE_AUDIO, Environment.DIRECTORY_MUSIC);
                fileTypeFolders.put(Constants.FILE_TYPE_VIDEOS, Environment.DIRECTORY_MOVIES);
                fileTypeFolders.put(Constants.FILE_TYPE_RINGTONES, Environment.DIRECTORY_RINGTONES);
                fileTypeFolders.put(Constants.FILE_TYPE_PICTURES, Environment.DIRECTORY_PICTURES);
                fileTypeFolders.put(Constants.FILE_TYPE_TORRENTS, Environment.DIRECTORY_DOWNLOADS);
                fileTypeFolders.put(Constants.FILE_TYPE_DOCUMENTS, Environment.DIRECTORY_DOCUMENTS);
            }
        }
        return fileTypeFolders.get(fileType);
    }

    public static String getRelativeFolderPath(File f) {
        byte fileType = AndroidPaths.getFileType(f.getAbsolutePath(), true);
        // "Music","Movies","Pictures","Download"
        String fileTypeSubfolder = AndroidPaths.getFileTypeExternalRelativeFolderName(fileType);
        String mediaStoreFolderPrefix = fileTypeSubfolder + "/JED2K";
        mediaStoreFolderPrefix = mediaStoreFolderPrefix.replace("//","/");
        return mediaStoreFolderPrefix;
    }
}
