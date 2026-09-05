package com.nutomic.syncthingandroid.util

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

import androidx.appcompat.app.AppCompatActivity

import com.google.gson.Gson
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants

import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object Util {

    private const val TAG = "Util"

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     *
     * Based on http://stackoverflow.com/a/5599842
     */
    fun readableFileSize(context: Context, bytes: Double): String {
        val units = context.resources.getStringArray(R.array.file_size_units)
        if (bytes <= 0) return "0 " + units[0]
        val digitGroups = (Math.log10(bytes) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#")
            .format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bytes per second
     * (eg 100 KiB/s).
     *
     * Based on http://stackoverflow.com/a/5599842
     */
    fun readableTransferRate(context: Context, bits: Long): String {
        val units = context.resources.getStringArray(R.array.transfer_rate_units)
        val bytes = bits / 8
        if (bytes <= 0) return "0 " + units[0]
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#")
            .format(bytes.toDouble() / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * Returns if the syncthing binary would be able to write a file into
     * the given folder given the configured access level.
     *
     * With [asRoot] the probe runs through the root shell, which is what the syncthing
     * core's effective access looks like when the "run as root" mode is enabled: the app
     * UID's own EACCES would otherwise wrongly reject root-only folders.
     */
    fun nativeBinaryCanWriteToPath(
        context: Context,
        absoluteFolderPath: String,
        asRoot: Boolean = false,
    ): Boolean {
        val touchFileName = ".stwritetest"

        // Write permission test file.
        val touchFile = "$absoluteFolderPath/$touchFileName"
        val exitCode = if (asRoot) {
            RootAccess.code("touch '$touchFile'")
        } else {
            runShellCommand("echo \"\" > \"$touchFile\"\n")
        }
        if (exitCode != 0) {
            val error = when (exitCode) {
                1 -> "Permission denied"
                else -> "Shell execution failed"
            }
            Log.i(TAG, "Failed to write test file '$touchFile', $error")
            return false
        }

        // Detected we have write permission.
        Log.i(TAG, "Successfully wrote test file '$touchFile'")

        // Remove test file.
        val rmExitCode = if (asRoot) {
            RootAccess.code("rm '$touchFile'")
        } else {
            runShellCommand("rm \"$touchFile\"\n")
        }
        if (rmExitCode != 0) {
            // This is very unlikely to happen, so we have less error handling.
            Log.i(TAG, "Failed to remove test file")
        }
        return true
    }

    /**
     * Look for running processes and return a list
     * containing the PIDs of found instances.
     *
     * With [asRoot] the listing runs through the root shell: the root-uid syncthing core
     * is invisible to the app's own `ps` (Android procfs only exposes same-UID processes).
     */
    fun getProcessPIDs(processName: String, asRoot: Boolean = false): List<String> {
        val processPIDs = mutableListOf<String>()
        val output = if (asRoot) {
            RootAccess.out("ps").joinToString("\n")
        } else {
            runShellCommandGetOutput("ps\n")
        }
        if (output.isEmpty()) {
            Log.w(TAG, "getProcessPIDs: Failed to list processes. ps command returned empty.")
            return processPIDs
        }

        val lines = output.split("\n".toRegex())
        if (lines.isEmpty()) {
            Log.w(TAG, "getProcessPIDs: Failed to list processes. ps command returned no rows.")
            return processPIDs
        }

        for (line in lines) {
            if (line.contains(processName)) {
                val processPID = line.trim().split("\\s+".toRegex())[1]
                processPIDs.add(processPID)
            }
        }
        return processPIDs
    }

    /**
     * Look for running processes and end them gracefully.
     *
     * With [asRoot] both the lookup and the SIGINT go through the root shell: an app-UID
     * `kill` cannot signal the root-uid syncthing core (EPERM), and `ps` cannot see it.
     */
    fun killProcess(processName: String, asRoot: Boolean = false) {
        val processPIDs = getProcessPIDs(processName, asRoot)
        if (processPIDs.isEmpty()) {
            Log.v(TAG, "killProcess: Found no running instances of [$processName]")
            return
        }
        for (processPID in processPIDs) {
            val exitCode = if (asRoot) {
                RootAccess.code("kill -SIGINT $processPID")
            } else {
                runShellCommand("kill -SIGINT $processPID\n")
            }
            if (exitCode != 0) {
                Log.w(TAG, "killProcess: Failed to send kill SIGINT to process [$processPID" +
                        "] exit code $exitCode")
            }
        }

        /**
         * Wait for process to end.
         */
        while (getProcessPIDs(processName, asRoot).isNotEmpty()) {
            SystemClock.sleep(50)
        }
        Log.d(TAG, "killProcess: No more instances of [$processName] running")
    }

    /**
     * Builds the web GUI URL from the given gui address (e.g. "127.0.0.1:8384").
     */
    fun buildWebGuiUrl(guiAddress: String): URL {
        val urlProtocol = if (Constants.osSupportsTLS12()) "https" else "http"
        try {
            return URL("$urlProtocol://$guiAddress")
        } catch (e: MalformedURLException) {
            throw RuntimeException("Failed to parse web interface URL", e)
        }
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    fun <T> deepCopy(obj: T, type: Type): T {
        val gson = Gson()
        return gson.fromJson(gson.toJson(obj, type), type)
    }

    /**
     * Run command in a shell and return the exit code.
     */
    fun runShellCommand(cmd: String): Int {
        return runShellCommandInternal("runShellCommand", cmd, null)
    }

    /**
     * Run command in a shell and return the captured standard output.
     */
    fun runShellCommandGetOutput(cmd: String): String {
        val capturedStdOut = StringBuilder()
        runShellCommandInternal("runShellCommandGetOutput", cmd, capturedStdOut)
        return capturedStdOut.toString()
    }

    /**
     * Run command in a shell, optionally capturing its standard output.
     */
    private fun runShellCommandInternal(logTag: String, cmd: String, capturedStdOut: StringBuilder?): Int {
        // Assume "failure" exit code if an error is caught.
        // Note: redirectErrorStream(true); System.getProperty("line.separator");
        var exitCode = 255
        var shellProc: Process? = null
        var shellOut: DataOutputStream? = null
        try {
            shellProc = Runtime.getRuntime().exec("sh")
            shellOut = DataOutputStream(shellProc.getOutputStream())
            val bufferedWriter = BufferedWriter(OutputStreamWriter(shellOut))
            Log.d(TAG, "$logTag: $cmd")
            bufferedWriter.write(cmd)
            bufferedWriter.flush()
            shellOut.close()
            shellOut = null
            try {
                BufferedReader(InputStreamReader(shellProc.inputStream, StandardCharsets.UTF_8)).use { bufferedReader ->
                    while (true) {
                        val line = bufferedReader.readLine() ?: break
                        if (capturedStdOut == null) {
                            Log.v(TAG, "$logTag: $line")
                        } else {
                            capturedStdOut.append(line).append("\n")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "$logTag: Failed to read output", e)
            }
            exitCode = shellProc.waitFor()
            if (capturedStdOut != null && exitCode != 0) {
                Log.i(TAG, "$logTag: Exited with code $exitCode")
            }
        } catch (e: IOException) {
            Log.w(TAG, "$logTag: Exception", e)
        } catch (e: InterruptedException) {
            Log.w(TAG, "$logTag: Exception", e)
        } finally {
            try {
                shellOut?.close()
            } catch (e: IOException) {
                Log.w(TAG, "$logTag: Failed to close stream", e)
            }
            shellProc?.destroy()
        }
        return exitCode
    }

    /**
     * Check if a TCP is listening on the local device on a specific port.
     */
    fun isTcpPortListening(port: Int): Boolean {
        // t: tcp, l: listening, n: numeric
        val output = runShellCommandGetOutput("netstat -t -l -n")
        if (output.isEmpty()) {
            Log.w(TAG, "isTcpPortListening: Failed to run netstat. Returning false.")
            return false
        }
        for (line in output.split("\n".toRegex())) {
            val words = line.split("\\s+".toRegex())
            if (words.size > 5) {
                val protocol = words[0]
                val localAddress = words[3]
                val connState = words[5]
                if (protocol == "tcp" || protocol == "tcp6") {
                    if (localAddress.endsWith(":" + port.toString()) &&
                        connState.equals("LISTEN", ignoreCase = true)
                    ) {
                        // Port is listening.
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Format a path properly.
     *
     * @param path String containing the path that needs formatting.
     * @return formatted file path as a string.
     */
    fun formatPath(path: String): String {
        return File(path).toURI().normalize().path
    }

    /**
     * Shorten a path using ellipsis to display it on UI
     * where we have little space to display it.
     */
    fun getPathEllipsis(fullFN: String): String {
        val MAX_CHARS_SUBDIR = 15
        val MAX_CHARS_FILENAME = MAX_CHARS_SUBDIR * 2

        var workIn = fullFN
        var workOut = ""
        while (true) {
            val index = workIn.indexOf('/')
            if (index < 0) {
                // Last part is the filename.
                if (workIn.length > MAX_CHARS_FILENAME) {
                    val indexFileExt = workIn.lastIndexOf(".")
                    if (indexFileExt > 0) {
                        // Filename with extension.
                        var fileName = workIn.substring(0, indexFileExt)
                        if (fileName.length > MAX_CHARS_FILENAME) {
                            fileName = fileName.substring(0, MAX_CHARS_FILENAME) + "\u22ef"
                        }
                        workIn = fileName + workIn.substring(indexFileExt)
                    } else {
                        // Filename without extension
                        workIn = workIn.substring(0, MAX_CHARS_FILENAME) + "\u22ef"
                    }
                }
                workOut += workIn
                break
            }
            // Handle one directory from the path.
            var part = workIn.substring(0, index)
            if (part.length > MAX_CHARS_SUBDIR) {
                part = part.substring(0, MAX_CHARS_SUBDIR) + "\u22ef"
            }
            workOut += "$part/"
            workIn = workIn.substring(index + 1)
        }
        return workOut
    }

    fun isRunningOnTV(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Converts dateTime to readable localized string.
     */
    fun formatDateTime(dateTime: String): String {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime
        }

        val parsedDateTime = ZonedDateTime.parse(dateTime)
        val zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        return formatter.format(zonedDateTime)
    }

    fun formatTime(dateTime: String): String {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime
        }

        val parsedDateTime = ZonedDateTime.parse(dateTime)
        val zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        return formatter.format(zonedDateTime)
    }

    /**
     * Converts local time to ZonedDateTime.
     */
    fun getLocalZonedDateTime(): String {
        // Legacy devices below API 26 don't support java.time; return a fixed
        // fallback timestamp as they cannot display the local time anyway.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "2021-02-11T22:11:29.356Z"
        }
        return ZonedDateTime.ofLocal(LocalDateTime.now(), ZoneId.of("UTC"), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * Returns true if the given service class is currently running.
     * Note: getRunningServices() is deprecated and only returns a cached
     * snapshot on recent Android versions, which is fine for this check.
     */
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in am.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * Quotes a string for safe use as a single shell argument.
     */
    fun shellQuote(s: String): String {
        return "'" + s.replace("'", "'\\''") + "'"
    }

    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    fun runScriptSet(absPath: String, scriptArgs: Array<String>?) {
        val scriptFolder = File(absPath)
        if (!scriptFolder.exists() || !scriptFolder.isDirectory) {
            Log.w(TAG, "runScriptSet: Folder does not exist or is not of type folder: $absPath")
            return
        }

        // Find all script files within given folder path.
        val scriptFiles = scriptFolder.listFiles { _, name ->
            name.lowercase(Locale.ROOT).endsWith(".sh")
        }
        if (scriptFiles == null || scriptFiles.isEmpty()) {
            Log.v(TAG, "runScriptSet: No script files found within folder: $absPath")
            return
        }
        for (scriptFile in scriptFiles) {
            // Build arguments using shell escape.
            val cmdBuilder = StringBuilder()
            cmdBuilder.append("cd ").append(shellQuote("$absPath/..")).append(";")
            cmdBuilder.append("sh ").append(shellQuote(scriptFile.absolutePath))
            if (scriptArgs != null) {
                for (arg in scriptArgs) {
                    cmdBuilder.append(" ").append(shellQuote(arg))
                }
            }

            // Execute script.
            val command = cmdBuilder.toString()
            Log.v(TAG, "runScriptSet: Exec result [" + runShellCommandGetOutput(command) + "]")
        }
    }

    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    fun getSyncConflictFiles(absPath: String): Array<String> {
        val cmdBuilder = StringBuilder()
        cmdBuilder.append("cd ").append(shellQuote("$absPath/")).append(";")
        // Unescaped:
        //  find -type f -name "*\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*" -not -path "\.\/\.stversions\/*" -print | sed "s~\.\/~~"
        cmdBuilder.append("find -type f -name \"*\\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*\" -not -path \"\\.\\/\\" + Constants.FOLDER_NAME_STVERSIONS + "\\/*\" -print | sed \"s~\\\\.\\/~~\"")
        val command = cmdBuilder.toString()
        val output = runShellCommandGetOutput(command)
        if (output.isEmpty()) {
            return arrayOf()
        }
        return output.split("\n".toRegex()).toTypedArray()
    }

    /**
     * Cached [X509TrustManager] backed by the Android OS trust store ("AndroidCAStore"),
     * which aggregates both the system CAs and the CAs the user manually installed. Built lazily.
     */
    @Volatile
    private var osTrustManager: X509TrustManager? = null

    @Volatile
    private var osTrustManagerInitialized = false

    /**
     * Returns an [X509TrustManager] that validates against the Android OS trust store,
     * including user-installed CAs. Unlike a `TrustManagerFactory.init((KeyStore) null)`,
     * the "AndroidCAStore" keystore exposes user-added certificates on API 24+.
     *
     * Used as a fallback so the app can talk to a local Syncthing instance whose HTTPS certificate
     * was replaced with one signed by a CA the user trusts at the OS level (see
     * https://github.com/researchxxl/syncthing-android/issues/222).
     *
     * @return the OS-backed trust manager, or `null` if it could not be built.
     */
    fun getOsTrustManager(): X509TrustManager? {
        if (!osTrustManagerInitialized) {
            synchronized(this) {
                if (!osTrustManagerInitialized) {
                    try {
                        val caStore = KeyStore.getInstance("AndroidCAStore")
                        caStore.load(null)
                        val tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm()
                        )
                        tmf.init(caStore)
                        for (tm in tmf.trustManagers) {
                            if (tm is X509TrustManager) {
                                osTrustManager = tm
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getOsTrustManager: Failed to build OS trust manager", e)
                    }
                    osTrustManagerInitialized = true
                }
            }
        }
        return osTrustManager
    }
}
