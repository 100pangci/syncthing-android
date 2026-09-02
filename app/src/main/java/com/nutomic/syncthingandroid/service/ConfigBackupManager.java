package com.nutomic.syncthingandroid.service;

import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.service.SyncthingService.State;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.model.enums.AesKeyStrength;

/**
 * Owns the configuration backup/restore logic: exporting config, keys, index
 * database and shared preferences into an (optionally encrypted) ZIP archive
 * under {@link Constants#EXPORT_PATH}, and importing them back.
 */
public class ConfigBackupManager {

    private static final String TAG = "ConfigBackupManager";

    /**
     * Default relative path of the backup zip below the external storage root.
     */
    private static final String DEFAULT_BACKUP_REL_PATH = "backups/syncthing/config.zip";

    private final SyncthingService mService;
    private final SharedPreferences mPreferences;
    private final boolean ENABLE_VERBOSE_LOG;

    public ConfigBackupManager(SyncthingService service,
                               SharedPreferences preferences,
                               boolean enableVerboseLog) {
        mService = service;
        mPreferences = preferences;
        ENABLE_VERBOSE_LOG = enableVerboseLog;
    }

    /**
     * Exports the local config and keys to {@link Constants#EXPORT_PATH}.
     */
    public boolean exportConfig() {
        boolean failSuccess = true;
        Log.d(TAG, "exportConfig BEGIN");

        if (mService.getCurrentState() != State.DISABLED) {
            // Shutdown synchronously.
            mService.shutdownToState(State.DISABLED);
        }

        // Create export dir if non-existant.
        File targetZip = getBackupZipFile();
        targetZip.getParentFile().mkdirs();

        // Export SharedPreferences.
        File sharedPreferencesFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            sharedPreferencesFile = Constants.getSharedPrefsFile(mService);
            fileOutputStream = new FileOutputStream(sharedPreferencesFile);
            if (!sharedPreferencesFile.exists()) {
                sharedPreferencesFile.createNewFile();
            }
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(mPreferences.getAll());
            objectOutputStream.flush();
            fileOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "exportConfig: Failed to export SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectOutputStream != null) {
                    objectOutputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "exportConfig: Failed to export SharedPreferences #2", e);
            }
        }

        // Make a list of files to backup.
        List<File> includePaths = Arrays.asList(
            Constants.getConfigFile(mService),

            Constants.getPrivateKeyFile(mService),
            Constants.getPublicKeyFile(mService),

            Constants.getHttpsCertFile(mService),
            Constants.getHttpsKeyFile(mService),

            Constants.getSharedPrefsFile(mService),

            Constants.getIndexDbFolder(mService)
        );

        // If user set one, apply a password and encrypt the zip file.
        String zipEncryptionPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");

        // Compress files to zip file.
        try {
            // Delete existing ZIP file to ensure we create a fresh archive instead of appending
            if (targetZip.exists()) {
                targetZip.delete();
            }

            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
            parameters.setCompressionLevel(CompressionLevel.NORMAL);

            ZipFile zipFile;
            if (zipEncryptionPassword.isEmpty()) {
                zipFile = new ZipFile(targetZip);
                parameters.setEncryptFiles(false);
            } else {
                zipFile = new ZipFile(targetZip, zipEncryptionPassword.toCharArray());
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
                parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }

            // Add files.
            for (File includePath : includePaths) {
                if (includePath.exists()) {
                    if (includePath.isFile()) {
                        zipFile.addFile(includePath, parameters);
                    } else if (includePath.isDirectory()) {
                        zipFile.addFolder(includePath, parameters);
                    }
                }
            }

            if (sharedPreferencesFile != null && sharedPreferencesFile.exists()) {
                sharedPreferencesFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "exportConfig: Failed to export config", e);
            failSuccess = false;
        }
        Log.d(TAG, "exportConfig END");

        // Start syncthing after export if run conditions apply.
        restartIfRunConditionsApply();
        return failSuccess;
    }

    /**
     * Imports config and keys from {@link Constants#EXPORT_PATH}.
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        ZipFile zipFile = null;
        Log.d(TAG, "importConfig PRECHECK");

        // Check if ZIP exists.
        File zipFilePath = getBackupZipFile();
        if (!zipFilePath.exists()) {
            Log.e(TAG, "importConfig: ZIP file is missing. Please check if it is present at '" + zipFilePath.getAbsolutePath() + "' as specified in the settings screen.");
            return false;
        }

        // Open ZIP file.
        try {
            // If user set one, get password to decrypt the zip file.
            String zipEncryptionPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");
            if (zipEncryptionPassword.isEmpty()) {
                zipFile = new ZipFile(zipFilePath);
            } else {
                zipFile = new ZipFile(zipFilePath, zipEncryptionPassword.toCharArray());
                if (!zipFile.isEncrypted()) {
                    Log.e(TAG, "importConfig: ZIP file is not encrypted, but password was specified in settings screen. Try to specify an empty password temporarily.");
                    return false;
                }
            }

            // Check if ZIP archive contains required files.
            List<String> checkFiles = Arrays.asList(
                Constants.CONFIG_FILE,

                Constants.PRIVATE_KEY_FILE,
                Constants.PUBLIC_KEY_FILE
            );
            for (final String checkFile : checkFiles) {
                if (zipFile.getFileHeader(checkFile) == null) {
                    Log.e(TAG, "importConfig: Required file not found inside zip [" + checkFile + "]");
                    return false;
                }
            }

            // Test if supplied encryption password is correct.
            String cacheDir = mService.getCacheDir().getAbsolutePath();
            zipFile.extractFile(Constants.PUBLIC_KEY_FILE, cacheDir);
            new File(cacheDir, Constants.PUBLIC_KEY_FILE).delete();
        } catch (ZipException e) {
            Log.e(TAG, "importConfig: Failed to open zip, " + e.getMessage());
            return false;
        }

        // Shutdown SyncthingNative.
        boolean failSuccess = true;
        Log.d(TAG, "importConfig BEGIN");
        if (mService.getCurrentState() != State.DISABLED) {
            // Shutdown synchronously.
            mService.shutdownToState(State.DISABLED);
        }

        // Remove database folder if it exists.
        File databasePath = Constants.getIndexDbFolder(mService);
        if (databasePath.exists()) {
            Log.d(TAG, "importConfig: Clearing index database");
            try {
                FileUtils.deleteDirectoryRecursively(databasePath);
            } catch (IOException e) {
                Log.e(TAG, "Failed to delete directory '" + databasePath.getAbsolutePath() + "'" + e);
            }
        }

        // Decompress zip file.
        try {
            zipFile.extractAll(mService.getFilesDir().getAbsolutePath());
        } catch (ZipException e) {
            Log.e(TAG, "importConfig: Failed to extract zip, " + e.getMessage());
            failSuccess = false;
        }

        // Check if necessary files are present after extraction.
        List<File> checkPaths = Arrays.asList(
            Constants.getConfigFile(mService),

            Constants.getPrivateKeyFile(mService),
            Constants.getPublicKeyFile(mService),

            Constants.getHttpsCertFile(mService),
            Constants.getHttpsKeyFile(mService),

            Constants.getSharedPrefsFile(mService)
        );
        for (final File checkPath : checkPaths) {
            if (!checkPath.exists()) {
                Log.e(TAG, "importConfig: Missing file after extraction [" + checkPath.getName() + "]");
                failSuccess = false;
            }
        }

        // Import shared preferences.
        File sharedPreferencesFile = Constants.getSharedPrefsFile(mService);
        if (sharedPreferencesFile.exists()) {
            Log.d(TAG, "importConfig: Importing shared preferences");
            failSuccess = failSuccess && importConfigSharedPrefs(sharedPreferencesFile);
            sharedPreferencesFile.delete();
        }

        try {
            cleanupImportedFolderDatabases();
        } catch (Exception e) {
            Log.e(TAG, "importConfig: Failed to cleanup invalid folder databases", e);
        }

        // Start syncthing after import if run conditions apply.
        restartIfRunConditionsApply();
        return failSuccess;
    }

    /**
     * Get backup zip file.
     * Default: /storage/emulated/0/backups/syncthing/config.zip
     */
    private File getBackupZipFile() {
        String relPathToZip = mPreferences.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, DEFAULT_BACKUP_REL_PATH);
        // NOTE: somehow we get empty string from the prefs, which crashes the app, use default when that happens
        // TODO: figure out where the empty string is coming from and fix that
        if (relPathToZip.isEmpty()) {
            relPathToZip = DEFAULT_BACKUP_REL_PATH;
        }
        return new File(Environment.getExternalStorageDirectory(), relPathToZip);
    }

    private void restartIfRunConditionsApply() {
        if (mService.shouldRunAfterRestart()) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            mainLooper.post(() -> mService.launchStartupTask(SyncthingRunnable.Command.main));
        }
    }

    private void cleanupImportedFolderDatabases() {
        ConfigXml configXml = new ConfigXml(mService);
        try {
            configXml.loadConfig();
        } catch (ConfigXml.OpenConfigException e) {
            Log.w(TAG, "importConfig: Unable to parse imported config for DB cleanup");
            return;
        }

        final List<com.nutomic.syncthingandroid.model.Folder> folders = configXml.getFolders();
        if (folders == null || folders.isEmpty()) {
            return;
        }

        for (com.nutomic.syncthingandroid.model.Folder folder : folders) {
            if (folder == null || folder.id == null || folder.id.isEmpty()) {
                continue;
            }

            final String folderPathValue = folder.path;
            final File folderPath = (folderPathValue == null || folderPathValue.isEmpty())
                    ? null
                    : new File(folderPathValue);
            final boolean folderPathMissing = folderPath == null || !folderPath.isDirectory();

            String markerName = folder.markerName;
            if (markerName == null || markerName.isEmpty()) {
                markerName = Constants.FILENAME_STFOLDER;
            }
            final boolean markerMissing = folderPathMissing || !new File(folderPath, markerName).exists();

            if (folderPathMissing || markerMissing) {
                Log.i(TAG, "importConfig: Folder path or marker missing for folder id \"" + folder.id + "\". Resetting Syncthing database.");
                new SyncthingRunnable(mService, SyncthingRunnable.Command.resetdatabase).run();
                break;
            }
        }
    }

    private boolean importConfigSharedPrefs(final File file) {
        boolean failSuccess = true;
        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        Map<?, ?> sharedPrefsMap = null;
        try {

            // Read, deserialize shared preferences.
            fileInputStream = new FileInputStream(file);
            objectInputStream = new ObjectInputStream(fileInputStream);
            Object objectFromInputStream = objectInputStream.readObject();
            if (objectFromInputStream instanceof Map) {
                sharedPrefsMap = (Map<?, ?>) objectFromInputStream;

                // Store backup folder to restore it back later in the process.
                String relPathToZip = mPreferences.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, "");
                String backupPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");

                // Prepare a SharedPreferences commit.
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.clear();
                for (Map.Entry<?, ?> e : sharedPrefsMap.entrySet()) {
                    String prefKey = (String) e.getKey();
                    switch (prefKey) {
                        // Preferences that are no longer used and left-overs from previous versions of the app.
                        case "first_start":
                        case "advanced_folder_picker":
                        case "backup_folder_name":
                        case "bind_network":
                        case "log_to_file":
                        case "notification_type":
                        case "notify_crashes":
                        case "suggest_new_folder_root":
                        case "use_legacy_hashing":
                        case "pref_current_language":
                        case "restartOnWakeup":
                        case "wakelock_while_binary_running":
                        case "use_root":
                        case "important_news_shown_version":
                            LogV("importConfig: Ignoring deprecated pref \"" + prefKey + "\".");
                            break;
                        // Cached information which is not available on SettingsActivity.
                        case Constants.PREF_APP_START_COUNTER:
                        case Constants.PREF_BTNSTATE_FORCE_START_STOP:
                        case Constants.PREF_DEBUG_FACILITIES_AVAILABLE:
                        case Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID:
                        case Constants.PREF_LAST_BINARY_VERSION:
                        case Constants.PREF_LOCAL_DEVICE_ID:
                        case Constants.PREF_LAST_RUN_TIME:
                            LogV("importConfig: Ignoring cache pref \"" + prefKey + "\".");
                            break;
                        default:
                            Log.i(TAG, "importConfig: Adding pref \"" + prefKey + "\" to commit ...");

                            // The editor only provides typed setters.
                            if (e.getValue() instanceof Boolean) {
                                editor.putBoolean(prefKey, (Boolean) e.getValue());
                            } else if (e.getValue() instanceof String) {
                                editor.putString(prefKey, (String) e.getValue());
                            } else if (e.getValue() instanceof Integer) {
                                editor.putInt(prefKey, (Integer) e.getValue());
                            } else if (e.getValue() instanceof Float) {
                                editor.putFloat(prefKey, (Float) e.getValue());
                            } else if (e.getValue() instanceof Long) {
                                editor.putLong(prefKey, (Long) e.getValue());
                            } else if (e.getValue() instanceof Set) {
                                editor.putStringSet(prefKey, asSet((Set<?>) e.getValue(), String.class));
                            } else {
                                Log.w(TAG, "importConfig: SharedPref type " + e.getValue().getClass().getName() + " is unknown");
                            }
                            break;
                    }
                }
                editor.putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relPathToZip);
                editor.putString(Constants.PREF_BACKUP_PASSWORD, backupPassword);

                /**
                 * If all shared preferences have been added to the commit successfully,
                 * apply the commit.
                 */
                failSuccess = failSuccess && editor.commit();
            } else {
                Log.e(TAG, "importConfig: Invalid object stream");
            }
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "importConfig: Failed to import SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectInputStream != null) {
                    objectInputStream.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "importConfig: Failed to import SharedPreferences #2", e);
            }
        }
        return failSuccess;
    }

    private static <T> Set<T> asSet(Set<?> c, Class<? extends T> type) {
        if (c == null) {
            return null;
        }
        Set<T> set = new HashSet<T>();
        for (Object o : c) {
            set.add(type.cast(o));
        }
        return set;
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
