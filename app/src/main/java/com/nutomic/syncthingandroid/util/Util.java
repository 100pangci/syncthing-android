package com.nutomic.syncthingandroid.util;

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class Util {

    private static final String TAG = "Util";

    private Util() {
    }

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableFileSize(Context context, double bytes) {
        final String[] units = context.getResources().getStringArray(R.array.file_size_units);
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bytes per second
     * (eg 100 KiB/s).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableTransferRate(Context context, long bits) {
        final String[] units = context.getResources().getStringArray(R.array.transfer_rate_units);
        long bytes = bits / 8;
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Returns if the syncthing binary would be able to write a file into
     * the given folder given the configured access level.
     */
    public static boolean nativeBinaryCanWriteToPath(Context context, String absoluteFolderPath) {
        final String TOUCH_FILE_NAME = ".stwritetest";

        // Write permission test file.
        String touchFile = absoluteFolderPath + "/" + TOUCH_FILE_NAME;
        int exitCode = runShellCommand("echo \"\" > \"" + touchFile + "\"\n");
        if (exitCode != 0) {
            String error;
            switch (exitCode) {
                case 1:
                    error = "Permission denied";
                    break;
                default:
                    error = "Shell execution failed";
            }
            Log.i(TAG, "Failed to write test file '" + touchFile +
                "', " + error);
            return false;
        }

        // Detected we have write permission.
        Log.i(TAG, "Successfully wrote test file '" + touchFile + "'");

        // Remove test file.
        if (runShellCommand("rm \"" + touchFile + "\"\n") != 0) {
            // This is very unlikely to happen, so we have less error handling.
            Log.i(TAG, "Failed to remove test file");
        }
        return true;
    }

    /**
     * Look for running processes and return an array
     * containing the PIDs of found instances.
     */
    public static List<String> getProcessPIDs(final String processName) {
        List<String> processPIDs = new ArrayList<String>();
        String output = runShellCommandGetOutput("ps\n");
        if (TextUtils.isEmpty(output)) {
            Log.w(TAG, "getProcessPIDs: Failed to list processes. ps command returned empty.");
            return processPIDs;
        }

        String lines[] = output.split("\n");
        if (lines.length == 0) {
            Log.w(TAG, "getProcessPIDs: Failed to list processes. ps command returned no rows.");
            return processPIDs;
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains(processName)) {
                String processPID = line.trim().split("\\s+")[1];
                // Log.v(TAG, "getProcessPIDs: Found PID [" + processPID + "] for ["+ processName + "]");
                processPIDs.add(processPID);
            }
        }
        return processPIDs;
    }

    /**
     * Look for running processes and end them gracefully.
     */
    public static void killProcess(final String processName) {
        int exitCode;
        List<String> processPIDs = getProcessPIDs(processName);
        if (processPIDs.isEmpty()) {
            Log.v(TAG, "killProcess: Found no running instances of [" + processName + "]");
            return;
        }
        for (String processPID : processPIDs) {
            exitCode = runShellCommand("kill -SIGINT " + processPID + "\n");
            if (exitCode != 0) {
                Log.w(TAG, "killProcess: Failed to send kill SIGINT to process [" + processPID +
                        "] exit code " + Integer.toString(exitCode));
            }
        }

        /**
         * Wait for process to end.
         */
        while (!getProcessPIDs(processName).isEmpty()) {
            SystemClock.sleep(50);
        }
        Log.d(TAG, "killProcess: No more instances of [" + processName + "] running");
    }

    /**
     * Builds the web GUI URL from the given gui address (e.g. "127.0.0.1:8384").
     */
    public static URL buildWebGuiUrl(String guiAddress) {
        String urlProtocol = Constants.osSupportsTLS12() ? "https" : "http";
        try {
            return new URL(urlProtocol + "://" + guiAddress);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse web interface URL", e);
        }
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    public static <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    /**
     * Run command in a shell and return the exit code.
     */
    public static int runShellCommand(String cmd) {
        return runShellCommandInternal("runShellCommand", cmd, null);
    }

    /**
     * Run command in a shell and return the captured standard output.
     */
    public static String runShellCommandGetOutput(String cmd) {
        StringBuilder capturedStdOut = new StringBuilder();
        runShellCommandInternal("runShellCommandGetOutput", cmd, capturedStdOut);
        return capturedStdOut.toString();
    }

    /**
     * Run command in a shell, optionally capturing its standard output.
     */
    private static int runShellCommandInternal(String logTag, String cmd, @Nullable StringBuilder capturedStdOut) {
        // Assume "failure" exit code if an error is caught.
        // Note: redirectErrorStream(true); System.getProperty("line.separator");
        int exitCode = 255;
        Process shellProc = null;
        DataOutputStream shellOut = null;
        try {
            shellProc = Runtime.getRuntime().exec("sh");
            shellOut = new DataOutputStream(shellProc.getOutputStream());
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(shellOut));
            Log.d(TAG, logTag + ": " + cmd);
            bufferedWriter.write(cmd);
            bufferedWriter.flush();
            shellOut.close();
            shellOut = null;
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(shellProc.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (capturedStdOut == null) {
                        Log.v(TAG, logTag + ": " + line);
                    } else {
                        capturedStdOut.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, logTag + ": Failed to read output", e);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            exitCode = shellProc.waitFor();
            if (capturedStdOut != null && exitCode != 0) {
                Log.i(TAG, logTag + ": Exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, logTag + ": Exception", e);
        } finally {
            try {
                if (shellOut != null) {
                    shellOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, logTag + ": Failed to close stream", e);
            }
            if (shellProc != null) {
                shellProc.destroy();
            }
        }
        return exitCode;
    }

    /**
     * Check if a TCP is listening on the local device on a specific port.
     */
    public static Boolean isTcpPortListening(Integer port) {
        // t: tcp, l: listening, n: numeric
        String output = runShellCommandGetOutput("netstat -t -l -n");
        if (TextUtils.isEmpty(output)) {
            Log.w(TAG, "isTcpPortListening: Failed to run netstat. Returning false.");
            return false;
        }
        String[] results  = output.split("\n");
        for (String line : results) {
            if (TextUtils.isEmpty(output)) {
                continue;
            }
            String[] words = line.split("\\s+");
            if (words.length > 5) {
                String protocol = words[0];
                String localAddress = words[3];
                String connState = words[5];
                if (protocol.equals("tcp") || protocol.equals("tcp6")) {
                    if (localAddress.endsWith(":" + Integer.toString(port)) &&
                            connState.equalsIgnoreCase("LISTEN")) {
                        // Port is listening.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Format a path properly.
     *
     * @param path String containing the path that needs formatting.
     * @return formatted file path as a string.
     */
    public static String formatPath(String path) {
        return new File(path).toURI().normalize().getPath();
    }

    /**
     * Shorten a path using ellipsis to display it on UI
     * where we have little space to display it.
     */
    public static final String getPathEllipsis(final String fullFN) {
        final boolean FUNC_LOG_D = false;
        final boolean FUNC_LOG_V = false;
        final int MAX_CHARS_SUBDIR = 15;
        final int MAX_CHARS_FILENAME = MAX_CHARS_SUBDIR * 2;

        int index;
        String part;
        String workIn = fullFN;
        String workOut = "";
        while(true) {
            index = workIn.indexOf('/');
            if (index < 0) {
                // Last part is the filename.
                if (FUNC_LOG_V) {
                    Log.v(TAG, "getPathEllipsis: workIn [" + workIn + "] @ index <= 0");
                }
                if (workIn.length() > MAX_CHARS_FILENAME) {
                    int indexFileExt = workIn.lastIndexOf(".");
                    if (indexFileExt > 0) {
                        // Filename with extension.
                        String fileName = workIn.substring(0, indexFileExt);
                        if (fileName.length() > MAX_CHARS_FILENAME) {
                            fileName = fileName.substring(0, MAX_CHARS_FILENAME) + "\u22ef";
                        }
                        workIn = fileName + workIn.substring(indexFileExt);
                    } else {
                        // Filename without extension
                        workIn = workIn.substring(0, MAX_CHARS_FILENAME) + "\u22ef";
                    }
                }
                workOut += workIn;
                break;
            }
            // Handle one directory from the path.
            part = workIn.substring(0, index);
            if (FUNC_LOG_V) {
                Log.v(TAG, "getPathEllipsis: part [" + part + "]");
            }
            if (part.length() > MAX_CHARS_SUBDIR) {
                part = part.substring(0, MAX_CHARS_SUBDIR) + "\u22ef";
            }
            workOut += part + "/";
            workIn = workIn.substring(index + 1);
            if (FUNC_LOG_V) {
                Log.v(TAG, "getPathEllipsis: workIn [" + workIn + "], workOut [" + workOut + "]");
            }
        }
        if (FUNC_LOG_D) {
            Log.v(TAG, "getPathEllipsis: INP [" + fullFN + "]");
            Log.v(TAG, "getPathEllipsis: OUT [" + workOut + "]");
        }
        return workOut;
    }

    public static Boolean isRunningOnTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /**
     * Converts dateTime to readable localized string.
     */
    public static String formatDateTime(String dateTime) {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime;
        }

        ZonedDateTime parsedDateTime = ZonedDateTime.parse(dateTime);
        ZonedDateTime zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
        return formatter.format(zonedDateTime);
    }

    public static String formatTime(String dateTime) {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime;
        }

        ZonedDateTime parsedDateTime = ZonedDateTime.parse(dateTime);
        ZonedDateTime zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
        return formatter.format(zonedDateTime);
    }

    /**
     * Converts local time to ZonedDateTime.
     */
    public static String getLocalZonedDateTime() {
        // Legacy devices below API 26 don't support java.time; return a fixed
        // fallback timestamp as they cannot display the local time anyway.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "2021-02-11T22:11:29.356Z";
        }
        return ZonedDateTime.ofLocal(LocalDateTime.now(), ZoneId.of("UTC"), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    
    /**
     * Returns true if the given service class is currently running.
     * Note: getRunningServices() is deprecated and only returns a cached
     * snapshot on recent Android versions, which is fine for this check.
     */
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Quotes a string for safe use as a single shell argument.
     */
    public static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    public static void runScriptSet(final String absPath, final String[] scriptArgs) {
        File scriptFolder = new File(absPath);
        if (!scriptFolder.exists() || !scriptFolder.isDirectory()) {
            Log.w(TAG, "runScriptSet: Folder does not exist or is not of type folder: " + absPath);
            return;
        }

        // Find all script files within given folder path.
        File[] scriptFiles = scriptFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.ROOT).endsWith(".sh");
            }
        });
        if (scriptFiles == null || scriptFiles.length == 0) {
            Log.v(TAG, "runScriptSet: No script files found within folder: " + absPath);
            return;
        }
        for (File scriptFile : scriptFiles) {
            // Build arguments using shell escape.
            StringBuilder cmdBuilder = new StringBuilder();
            cmdBuilder.append("cd ").append(shellQuote(absPath + "/..")).append(";");
            cmdBuilder.append("sh ").append(shellQuote(scriptFile.getAbsolutePath()));
            if (scriptArgs != null) {
                for (String arg : scriptArgs) {
                    cmdBuilder.append(" ").append(shellQuote(arg));
                }
            }

            // Execute script.
            String command = cmdBuilder.toString();
            // Log.d(TAG, "runScriptSet: Exec [" + command + "]");
            Log.v(TAG, "runScriptSet: Exec result [" + runShellCommandGetOutput(command) + "]");
        }
    }
    
    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    public static String[] getSyncConflictFiles(final String absPath) {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("cd ").append(shellQuote(absPath + "/")).append(";");
        // Unescaped:
        //  find -type f -name "*\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*" -not -path "\.\/\.stversions\/*" -print | sed "s~\\.\/~~"
        cmdBuilder.append("find -type f -name \"*\\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*\" -not -path \"\\.\\/\\" + Constants.FOLDER_NAME_STVERSIONS + "\\/*\" -print | sed \"s~\\\\.\\/~~\"");
        String command = cmdBuilder.toString();
        // Log.v(TAG, "getSyncConflictFileCount: Exec [" + command + "]");
        String output = runShellCommandGetOutput(command);
        // Log.v(TAG, "getSyncConflictFileCount: Exec result [" + output + "]");
        if (output == null || output.isEmpty()) {
            return new String[]{};
        }
        return output.split("\\n");
    }

    /**
     * Cached {@link X509TrustManager} backed by the Android OS trust store ("AndroidCAStore"),
     * which aggregates both the system CAs and the CAs the user manually installed. Built lazily.
     */
    private static volatile X509TrustManager sOsTrustManager;
    private static volatile boolean sOsTrustManagerInitialized = false;

    /**
     * Returns an {@link X509TrustManager} that validates against the Android OS trust store,
     * including user-installed CAs. Unlike a {@code TrustManagerFactory.init((KeyStore) null)},
     * the "AndroidCAStore" keystore exposes user-added certificates on API 24+.
     *
     * Used as a fallback so the app can talk to a local Syncthing instance whose HTTPS certificate
     * was replaced with one signed by a CA the user trusts at the OS level (see
     * https://github.com/researchxxl/syncthing-android/issues/222).
     *
     * @return the OS-backed trust manager, or {@code null} if it could not be built.
     */
    public static X509TrustManager getOsTrustManager() {
        if (!sOsTrustManagerInitialized) {
            synchronized (Util.class) {
                if (!sOsTrustManagerInitialized) {
                    try {
                        KeyStore caStore = KeyStore.getInstance("AndroidCAStore");
                        caStore.load(null);
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init(caStore);
                        for (TrustManager tm : tmf.getTrustManagers()) {
                            if (tm instanceof X509TrustManager) {
                                sOsTrustManager = (X509TrustManager) tm;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "getOsTrustManager: Failed to build OS trust manager", e);
                    }
                    sOsTrustManagerInitialized = true;
                }
            }
        }
        return sOsTrustManager;
    }
}
