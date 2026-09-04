package com.nutomic.syncthingandroid.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.http.ApiClient
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.util.ConfigRouter
import com.nutomic.syncthingandroid.util.ConfigXml
import com.nutomic.syncthingandroid.util.PermissionUtil
import com.nutomic.syncthingandroid.util.Util
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.HashSet
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SyncthingService"

/**
 * Interval in ms, at which connections to the web gui are performed on first start
 * to find out if it's online.
 */
private const val WEB_GUI_POLL_INTERVAL = 150L

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
class SyncthingService : Service() {

    companion object {
        /**
         * Delay before retrying a deferred shutdown or re-applying certificate changes.
         */
        private const val SHUTDOWN_RETRY_DELAY_MS = 1000L

        /** Intent action to perform a Syncthing restart. */
        @JvmField
        val ACTION_RESTART = ".SyncthingService.RESTART"

        /** Intent action to perform a Syncthing stop. */
        @JvmField
        val ACTION_STOP = ".SyncthingService.STOP"

        /** Intent action to reset Syncthing's database. */
        @JvmField
        val ACTION_RESET_DATABASE = ".SyncthingService.RESET_DATABASE"

        /** Intent action to reset Syncthing's delta indexes. */
        @JvmField
        val ACTION_RESET_DELTAS = ".SyncthingService.RESET_DELTAS"

        @JvmField
        val ACTION_REFRESH_NETWORK_INFO = ".SyncthingService.REFRESH_NETWORK_INFO"

        /** Intent action to permanently ignore a device connection request. */
        @JvmField
        val ACTION_IGNORE_DEVICE = ".SyncthingService.IGNORE_DEVICE"

        /** Intent action to permanently ignore a folder share request. */
        @JvmField
        val ACTION_IGNORE_FOLDER = ".SyncthingService.IGNORE_FOLDER"

        /** Intent action to override folder changes. */
        @JvmField
        val ACTION_OVERRIDE_CHANGES = ".SyncthingService.OVERRIDE_CHANGES"

        /** Intent action to revert local folder changes. */
        @JvmField
        val ACTION_REVERT_LOCAL_CHANGES = ".SyncthingService.REVERT_LOCAL_CHANGES"

        /** Extra used together with ACTION_IGNORE_DEVICE, ACTION_IGNORE_FOLDER. */
        @JvmField
        val EXTRA_NOTIFICATION_ID = ".SyncthingService.EXTRA_NOTIFICATION_ID"

        /** Extra used together with ACTION_IGNORE_DEVICE. */
        @JvmField
        val EXTRA_DEVICE_ID = ".SyncthingService.EXTRA_DEVICE_ID"

        /** Extra used together with ACTION_IGNORE_DEVICE. */
        @JvmField
        val EXTRA_DEVICE_ADDRESS = ".SyncthingService.EXTRA_DEVICE_ADDRESS"

        /** Extra used together with ACTION_IGNORE_DEVICE. */
        @JvmField
        val EXTRA_DEVICE_NAME = ".SyncthingService.EXTRA_DEVICE_NAME"

        /** Extra used together with ACTION_IGNORE_FOLDER. */
        @JvmField
        val EXTRA_FOLDER_ID = ".SyncthingService.EXTRA_FOLDER_ID"

        /** Extra used together with ACTION_IGNORE_FOLDER. */
        @JvmField
        val EXTRA_FOLDER_LABEL = ".SyncthingService.EXTRA_FOLDER_LABEL"

        /** Extra used together with ACTION_STOP. */
        @JvmField
        val EXTRA_STOP_AFTER_CRASHED_NATIVE = ".SyncthingService.EXTRA_STOP_AFTER_CRASHED_NATIVE"
    }

    /**
     * Outcome of [replaceHttpsCertificate] / [resetHttpsCertificate].
     */
    enum class HttpsCertReplaceResult {
        /** The new certificate was applied and Syncthing came back online with it. */
        SUCCESS,
        /** The files were written but Syncthing is not currently meant to run; applies on next start. */
        SUCCESS_PENDING_START,
        /** The change failed and the previous certificate was restored. */
        FAILED,
    }

    fun interface OnHttpsCertReplaceResultListener {
        fun onResult(result: HttpsCertReplaceResult, errorDetail: String?)
    }

    fun interface OnServiceStateChangeListener {
        fun onServiceStateChange(currentState: State)
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    enum class State {
        /**
         * Service is initializing, Syncthing was not started yet.
         */
        INIT,
        /**
         * Syncthing binary is starting.
         */
        STARTING,
        /**
         * Syncthing binary is running,
         * Rest API is available,
         * RestApi class read the config and is fully initialized.
         */
        ACTIVE,
        /**
         * Syncthing binary is shutting down.
         */
        DISABLED,
        /**
         * There is some problem that prevents Syncthing from running.
         */
        ERROR,
    }

    private var enableVerboseLog = false

    /**
     * Initialize the service with State.DISABLED as [RunConditionMonitor] will
     * send an update if we should run the binary after it got instantiated in
     * [onStartCommand].
     */
    var currentState: State = State.DISABLED
        private set

    private lateinit var configRouter: ConfigRouter
    private var config: ConfigXml? = null
    private var syncthingRunnableThread: Thread? = null
    private lateinit var handler: Handler

    private val onServiceStateChangeListeners = HashSet<OnServiceStateChangeListener>()
    private val binder = SyncthingServiceBinder(this)

    /**
     * Scope for the web gui availability poll. Callbacks are delivered on the
     * main thread like the old Volley/PollWebGuiAvailableTask path did.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var webGuiPollJob: Job? = null

    var api: RestApi? = null
        private set

    private var eventPoller: EventPoller? = null

    private var runConditionMonitor: RunConditionMonitor? = null

    private var syncthingRunnable: SyncthingRunnable? = null

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var preferences: SharedPreferences

    /**
     * Object that must be locked upon accessing [currentState]
     */
    private val stateLock = Any()

    /**
     * Stores the result of the last should run decision received by OnShouldRunChangedListener.
     */
    private var lastDeterminedShouldRun = false

    /**
     * True if the user granted the storage permission.
     */
    private var storagePermissionGranted = false

    private lateinit var configBackupManager: ConfigBackupManager

    private lateinit var httpsCertManager: HttpsCertManager

    /**
     * True if the last run condition decision told the service to (re)start the binary.
     */
    fun shouldRunAfterRestart(): Boolean {
        return lastDeterminedShouldRun
    }

    /**
     * Starts the native binary.
     */
    override fun onCreate() {
        super.onCreate()
        (application as SyncthingApp).component().inject(this)
        enableVerboseLog = AppPrefs.getPrefVerboseLog(preferences)
        LogV("onCreate")
        configRouter = ConfigRouter(this)
        handler = Handler()
        configBackupManager = ConfigBackupManager(this, preferences, enableVerboseLog)
        httpsCertManager = HttpsCertManager(this, handler)

        // If runtime permissions are revoked, android kills and restarts the service.
        // We need to recheck if we still have the storage permission.
        storagePermissionGranted = PermissionUtil.haveStoragePermission(this)

        notificationHandler.setAppShutdownInProgress(false)
    }

    /**
     * Handles intent actions, e.g. [ACTION_RESTART]
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (!storagePermissionGranted) {
            Log.e(TAG, "User revoked storage permission. Stopping service.")
            notificationHandler.showStoragePermissionRevokedNotification()
            stopSelf()
            return START_NOT_STICKY
        }

        // Run condition monitor is enabled. Instantiate it on first onStartCommand
        // and enable callback on run condition change affecting the final decision
        // to run/terminate syncthing. After initial run conditions are collected
        // the first decision is sent to [onShouldRunDecisionChanged].
        if (runConditionMonitor == null) {
            // Kotlin cannot SAM-convert method references to plain interfaces;
            // use object expressions (phase4 decision).
            runConditionMonitor = RunConditionMonitor(
                this,
                object : RunConditionMonitor.OnShouldRunChangedListener {
                    override fun onShouldRunDecisionChanged(shouldRun: Boolean) {
                        this@SyncthingService.onShouldRunDecisionChanged(shouldRun)
                    }
                },
                object : RunConditionMonitor.OnSyncPreconditionChangedListener {
                    override fun onSyncPreconditionChanged(runConditionMonitor: RunConditionMonitor) {
                        this@SyncthingService.applyCustomRunConditions(runConditionMonitor)
                    }
                }
            )
        }
        notificationHandler.updatePersistentNotification(this)

        if (intent == null) {
            return START_STICKY
        }

        if (ACTION_RESTART == intent.action && currentState == State.ACTIVE) {
            shutdownToState(State.INIT)
            launchStartupTask(SyncthingRunnable.Command.main)
        } else if (ACTION_STOP == intent.action) {
            if (intent.getBooleanExtra(EXTRA_STOP_AFTER_CRASHED_NATIVE, false)) {
                // We were requested to stop the service because the syncthing native
                // binary crashed. Changing currentState prevents the "defer until
                // syncthing is started" routine we normally use for clean shutdown
                // to take place. Instead, we will immediately shutdown the crashed
                // instance forcefully.
                currentState = State.ERROR
                shutdownToState(State.DISABLED)
            } else {
                // Graceful shutdown.
                if (currentState == State.STARTING ||
                    currentState == State.ACTIVE
                ) {
                    shutdownToState(State.DISABLED)
                }
            }
        } else if (ACTION_RESET_DATABASE == intent.action) {
            // 1. Stop syncthing native if it's running.
            // 2. Reset the database, syncthing native will exit after performing the reset.
            // 3. Relaunch syncthing native if it was previously running.
            Log.i(TAG, "Invoking reset of database")
            if (currentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdownToState(State.DISABLED)
            }
            SyncthingRunnable(this, SyncthingRunnable.Command.resetdatabase).run()
            if (lastDeterminedShouldRun) {
                launchStartupTask(SyncthingRunnable.Command.main)
            }
        } else if (ACTION_RESET_DELTAS == intent.action) {
            // 1. Stop syncthing native if it's running.
            // 2. Reset delta index, syncthing native will NOT exit after performing the reset.
            // 3. If syncthing was previously NOT running: schedule a shutdown of the native
            //    binary after it left State.STARTING (to State.ACTIVE) - the moment when the
            //    reset delta index work was completed and Web UI came up. The shutdown gets
            //    deferred until State.ACTIVE was reached, then syncthing native will be
            //    shutdown synchronously.
            Log.i(TAG, "Invoking reset of delta indexes")
            if (currentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdownToState(State.DISABLED)
            }
            launchStartupTask(SyncthingRunnable.Command.resetdeltas)
            if (!lastDeterminedShouldRun) {
                // Shutdown if syncthing was not running before the UI action was raised.
                shutdownToState(State.DISABLED)
            }
        } else if (ACTION_REFRESH_NETWORK_INFO == intent.action) {
            runConditionMonitor?.updateShouldRunDecision()
        } else if (ACTION_IGNORE_DEVICE == intent.action) {
            configRouter.ignoreDevice(
                api,
                intent.getStringExtra(EXTRA_DEVICE_ID),
                intent.getStringExtra(EXTRA_DEVICE_NAME),
                intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
            )
            notificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        } else if (ACTION_IGNORE_FOLDER == intent.action) {
            configRouter.ignoreFolder(
                api,
                intent.getStringExtra(EXTRA_DEVICE_ID),
                intent.getStringExtra(EXTRA_FOLDER_ID),
                intent.getStringExtra(EXTRA_FOLDER_LABEL)
            )
            notificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        } else if (ACTION_OVERRIDE_CHANGES == intent.action && currentState == State.ACTIVE) {
            intent.getStringExtra(EXTRA_FOLDER_ID)?.let { api?.overrideChanges(it) }
        } else if (ACTION_REVERT_LOCAL_CHANGES == intent.action && currentState == State.ACTIVE) {
            intent.getStringExtra(EXTRA_FOLDER_ID)?.let { api?.revertLocalChanges(it) }
        } else {
            afterFreshServiceInstanceStart()
        }
        return START_STICKY
    }

    /**
     * Event handler to catch a fresh service startup right after the run condition
     * evaluation took place and SyncthingNative may be starting in the background
     * meanwhilst or non-present.
     */
    private fun afterFreshServiceInstanceStart() {
        LogV("afterFreshServiceInstanceStart: Service started from scratch, SyncthingNative is going to STATE_$currentState meanwhilst ...")
        if (currentState == State.DISABLED) {
            // Read and parse the config from disk.
            val configXml = ConfigXml(this)
            try {
                configXml.loadConfig()
            } catch (e: ConfigXml.OpenConfigException) {
                notificationHandler.showCrashedNotification(R.string.config_read_failed, "afterFreshServiceInstanceStart:OpenConfigException")
                synchronized(stateLock) {
                    onServiceStateChange(State.ERROR)
                }
                stopSelf()
                return
            }
        }
    }

    /**
     * After run conditions monitored by [RunConditionMonitor] changed and
     * it had an influence on the decision to run/terminate syncthing, this
     * function is called to notify this class to run/terminate the syncthing binary.
     * [onServiceStateChange] is called while applying the decision change.
     */
    private fun onShouldRunDecisionChanged(newShouldRunDecision: Boolean) {
        if (newShouldRunDecision != lastDeterminedShouldRun) {
            Log.i(TAG, "shouldRun decision changed to $newShouldRunDecision according to configured run conditions.")
            lastDeterminedShouldRun = newShouldRunDecision

            // React to the shouldRun condition change.
            if (newShouldRunDecision) {
                // Start syncthing.
                when (currentState) {
                    State.DISABLED, State.INIT ->
                        launchStartupTask(SyncthingRunnable.Command.main)
                    State.STARTING, State.ACTIVE, State.ERROR -> {}
                }
            } else {
                // Stop syncthing.
                if (currentState == State.DISABLED) {
                    return
                }
                shutdownToState(State.DISABLED)
            }
        }
    }

    /**
     * After sync preconditions changed, we need to inform [RestApi] to pause or
     * unpause devices and folders as defined in per-object sync preferences.
     */
    private fun applyCustomRunConditions(runConditionMonitor: RunConditionMonitor) {
        synchronized(stateLock) {
            if (api != null && currentState == State.ACTIVE) {
                // Forward event because syncthing is running.
                api?.applyCustomRunConditions(runConditionMonitor)
                return
            }
        }

        var configChanged = false

        // Read and parse the config from disk.
        val configXml = ConfigXml(this)
        try {
            configXml.loadConfig()
        } catch (e: ConfigXml.OpenConfigException) {
            notificationHandler.showCrashedNotification(R.string.config_read_failed, "applyCustomRunConditions:OpenConfigException")
            synchronized(stateLock) {
                onServiceStateChange(State.ERROR)
            }
            stopSelf()
            return
        }

        // Check if the folders are available from config.
        val folders: List<Folder>? = configXml.folders
        if (folders != null) {
            for (folder in folders) {
                val folderPrefixAndId = Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id
                val shouldPause = runConditionMonitor.getCustomSyncConditionsPause(folderPrefixAndId)
                if (shouldPause == null) {
                    continue
                }
                LogV("applyCustomRunConditions: f(${folder.label})=${if (!shouldPause) "1" else "0"}")
                if (folder.paused != shouldPause) {
                    configXml.setFolderPause(folder.id, shouldPause)
                    Log.d(TAG, "applyCustomRunConditions: f(${folder.label})=${if (!shouldPause) ">1" else ">0"}")
                    configChanged = true
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: folders == null")
            return
        }

        // Check if the devices are available from config.
        val devices: List<Device>? = configXml.getDevices(false)
        if (devices != null) {
            for (device in devices) {
                val devicePrefixAndId = Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID
                val shouldPause = runConditionMonitor.getCustomSyncConditionsPause(devicePrefixAndId)
                if (shouldPause == null) {
                    continue
                }
                LogV("applyCustomRunConditions: d(${device.name})=${if (!shouldPause) "1" else "0"}")
                if (device.paused != shouldPause) {
                    configXml.setDevicePause(device.deviceID, shouldPause)
                    Log.d(TAG, "applyCustomRunConditions: d(${device.name})=${if (!shouldPause) ">1" else ">0"}")
                    configChanged = true
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: devices == null")
            return
        }

        if (configChanged) {
            LogV("applyCustomRunConditions: Saving changed config ...")
            configXml.saveChanges()
        } else {
            LogV("applyCustomRunConditions: No action was necessary.")
        }
    }

    /**
     * Prepares to launch the syncthing binary.
     */
    fun launchStartupTask(srCommand: SyncthingRunnable.Command) {
        synchronized(stateLock) {
            if (currentState != State.DISABLED && currentState != State.INIT) {
                Log.e(TAG, "launchStartupTask: Wrong state $currentState detected. Cancelling.")
                return
            }
        }

        val config = ConfigXml(this)
        try {
            config.loadConfig()
        } catch (e: ConfigXml.OpenConfigException) {
            notificationHandler.showCrashedNotification(R.string.config_read_failed, "launchStartupTask:OpenConfigException")
            synchronized(stateLock) {
                onServiceStateChange(State.ERROR)
            }
            stopSelf()
            return
        }
        this.config = config

        // Check if the SyncthingNative's configured webgui port is allocated by another app or process.
        val webGuiTcpPort = config.webGuiBindPort
        val isWebUIPortListening = Util.isTcpPortListening(webGuiTcpPort)
        if (isWebUIPortListening) {
            // We shouldn't start SyncthingNative as we would wait forever for life signs on the configured port. (ANR)
            Log.e(TAG, "launchStartupTask: WebUI tcp port $webGuiTcpPort unavailable. Second instance?")
            notificationHandler.showCrashedNotification(R.string.webui_tcp_port_unavailable, webGuiTcpPort.toString())
            return
        }

        onServiceStateChange(State.STARTING)

        if (api == null) {
            api = RestApi(
                this, config.webGuiUrl, config.apiKey,
                { onApiAvailable() },
                { onServiceStateChange(currentState) }
            )
            Log.i(TAG, "Web GUI will be available at ${config.webGuiUrl}")
        }

        // Check mSyncthingRunnable lifecycle and create singleton.
        if (syncthingRunnable != null || syncthingRunnableThread != null) {
            Log.e(TAG, "onStartupTaskCompleteListener: Syncthing binary lifecycle violated")
            return
        }
        syncthingRunnable = SyncthingRunnable(this, srCommand)

        // Check if an old syncthing instance is still running.
        // This happens after an in-place app upgrade. If so, end it.
        Util.killProcess(Constants.FILENAME_SYNCTHING_BINARY)

        // Start the syncthing binary in a separate thread.
        val syncthingRunnableThread = Thread(syncthingRunnable)
        syncthingRunnableThread.setUncaughtExceptionHandler { _, _ ->
            Log.e(TAG, "mSyncthingRunnableThread: Uncaught exception [ExecutableNotFoundException]")
            notificationHandler.showCrashedNotification(R.string.executable_not_found, Constants.FILENAME_SYNCTHING_BINARY)
        }
        this.syncthingRunnableThread = syncthingRunnableThread
        syncthingRunnableThread.start()

        // Wait for the web-gui of the native syncthing binary to come online.
        //
        // In case the binary is to be stopped, also be aware that another thread could request
        // to stop the binary in the time while waiting for the GUI to become active. See the
        // comment for [onDestroy] for details.
        if (webGuiPollJob == null) {
            // Poll for the web-gui of the native syncthing binary to come online.
            //
            // In case the binary is to be stopped, also be aware that another thread could request
            // to stop the binary in the time while waiting for the GUI to become active. See the
            // comment for [onDestroy] for details.
            val pollClient = ApiClient(
                httpsCertFile = Constants.getHttpsCertFile(this),
                url = config.webGuiUrl,
                apiKey = config.apiKey,
                logFailures = false,
            )
            webGuiPollJob = serviceScope.launch {
                pollWebGuiUntilAvailable(
                    fetch = { pollClient.get("") },
                    webGuiUrl = config.webGuiUrl.toString(),
                    onAvailable = { api?.readConfigFromRestApi() },
                )
            }
        }
    }

    /**
     * Called when [RestApi.checkReadConfigFromRestApiCompleted] detects
     * the RestApi class has been fully initialized.
     * UI stressing results in api getting null on simultaneous shutdown, so
     * we check it for safety.
     */
    private fun onApiAvailable() {
        val restApi = api
        if (restApi == null) {
            Log.e(TAG, "onApiAvailable: Did we stop the binary during startup? api == null")
            return
        }
        synchronized(stateLock) {
            if (currentState != State.STARTING) {
                Log.e(TAG, "onApiAvailable: Wrong state $currentState detected. Cancelling callback.")
                return
            }
            onServiceStateChange(State.ACTIVE)
        }

        if (eventPoller == null) {
            eventPoller = EventPoller(this, restApi).also { it.start() }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Stops the native binary.
     * Shuts down RunConditionMonitor instance.
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (runConditionMonitor != null) {
            // Shut down the OnShouldRunChangedListener so we won't get interrupted by run
            // condition events that occur during shutdown.
            runConditionMonitor?.shutdown()
        }
        notificationHandler.setAppShutdownInProgress(true)
        if (!storagePermissionGranted) {
            // If the storage permission got revoked, we did not start the binary and
            // are in State.INIT requiring an immediate shutdown of this service class.
            Log.i(TAG, "Shutting down syncthing binary due to missing storage permission.")
        }
        shutdownToState(State.DISABLED)
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Stop SyncthingNative and all helpers like event processor and api handler.
     * Sets [currentState] to newState.
     * Performs a synchronous shutdown of the native binary.
     */
    fun shutdownToState(newState: State) {
        if (currentState == State.STARTING) {
            Log.w(TAG, "Deferring shutdown until State.STARTING was left")
            handler.postDelayed({ shutdownToState(newState) }, SHUTDOWN_RETRY_DELAY_MS)
            return
        }

        synchronized(stateLock) {
            onServiceStateChange(newState)
        }

        webGuiPollJob?.cancel()
        webGuiPollJob = null

        eventPoller?.let {
            it.stop()
            eventPoller = null
        }

        notificationHandler.cancelRestartNotification()

        api?.let {
            if (syncthingRunnable != null) {
                it.shutdown()
            }
            api = null
        }

        syncthingRunnable?.let {
            Util.killProcess(Constants.FILENAME_SYNCTHING_BINARY)
            syncthingRunnableThread?.let { thread ->
                LogV("Waiting for mSyncthingRunnableThread to finish after killProcess(Syncthing) ...")
                try {
                    thread.join()
                } catch (e: InterruptedException) {
                    Log.w(TAG, "mSyncthingRunnableThread InterruptedException")
                }
                Log.d(TAG, "Finished mSyncthingRunnableThread.")
                syncthingRunnableThread = null
            }
            syncthingRunnable = null
        }
    }

    /**
     * Force re-evaluating run conditions immediately e.g. after
     * preferences were modified by SettingsActivity#onStop.
     */
    fun evaluateRunConditions() {
        if (runConditionMonitor == null) {
            return
        }
        Log.d(TAG, "Forced re-evaluating run conditions ...")
        runConditionMonitor?.updateShouldRunDecision()
    }

    /**
     * Register a listener for the syncthing API state changing.
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see unregisterOnServiceStateChangeListener
     */
    fun registerOnServiceStateChangeListener(listener: OnServiceStateChangeListener) {
        // Initially send the current state to the new subscriber to make sure it doesn't stay
        // in undefined state forever until the state next change occurs.
        listener.onServiceStateChange(currentState)
        onServiceStateChangeListeners.add(listener)
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @see registerOnServiceStateChangeListener
     */
    fun unregisterOnServiceStateChangeListener(listener: OnServiceStateChangeListener) {
        onServiceStateChangeListeners.remove(listener)
    }

    /**
     * Called to notify listeners of an API change.
     */
    private fun onServiceStateChange(newState: State) {
        if (newState == currentState) {
            Log.d(TAG, "onServiceStateChange: Called with unchanged state $newState")
            return
        }
        Log.i(TAG, "onServiceStateChange: from $currentState to $newState")
        currentState = newState
        handler.post {
            notificationHandler.updatePersistentNotification(this)
            val iterator = onServiceStateChangeListeners.iterator()
            while (iterator.hasNext()) {
                iterator.next().onServiceStateChange(currentState)
            }
        }
    }

    val runDecisionExplanation: String
        get() {
            val monitor = runConditionMonitor
                ?: return resources.getString(R.string.reason_run_condition_monitor_not_instantiated)
            return monitor.getRunDecisionExplanation()
        }

    /**
     * Exports the local config and keys to [Constants.EXPORT_PATH].
     */
    fun exportConfig(): Boolean {
        return configBackupManager.exportConfig()
    }

    /**
     * Imports config and keys from [Constants.EXPORT_PATH].
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    fun importConfig(): Boolean {
        return configBackupManager.importConfig()
    }

    fun replaceHttpsCertificate(
        certPem: ByteArray,
        keyPem: ByteArray,
        listener: OnHttpsCertReplaceResultListener
    ) {
        httpsCertManager.replaceHttpsCertificate(certPem, keyPem, listener)
    }

    fun resetHttpsCertificate(listener: OnHttpsCertReplaceResultListener) {
        httpsCertManager.resetHttpsCertificate(listener)
    }

    private fun LogV(logMessage: String) {
        if (enableVerboseLog) {
            Log.v(TAG, logMessage)
        }
    }
}

/**
 * Polls [fetch] every WEB_GUI_POLL_INTERVAL ms until one
 * attempt succeeds, then invokes [onAvailable] exactly once and returns.
 *
 * This replaces the former `PollWebGuiAvailableTask` (deleted in phase7): the poll loop is
 * inlined into the service, which is now Kotlin, but kept as a top-level function taking a
 * `fetch` lambda so the retry/cancel semantics stay unit-testable without a real service.
 *
 * Behaviour parity with the deleted implementation:
 *  - Transport failures (syncthing not up yet) retry silently: connection failures and
 *    timeouts log at most once every 10 attempts, everything else warns.
 *  - Cancelling the calling coroutine stops the poll and the in-flight request (ApiClient
 *    awaits the OkHttp call with coroutine-aware cancellation), like the old
 *    `cancelRequestsAndCallback` did.
 */
internal suspend fun pollWebGuiUntilAvailable(
    fetch: suspend () -> String,
    webGuiUrl: String,
    onAvailable: () -> Unit,
) {
    Log.i(TAG, "Starting to poll for web gui availability")
    var logIncidence = 0
    while (true) {
        try {
            fetch()
            Log.i(TAG, "Web GUI has come online at $webGuiUrl")
            onAvailable()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (e is ConnectException || e is SocketTimeoutException) {
                logIncidence++
                if (logIncidence == 1 || logIncidence % 10 == 0) {
                    Log.v(TAG, "Polling web gui ... ($logIncidence)")
                }
            } else {
                Log.w(TAG, "Unexpected error while polling web gui", e)
            }
            delay(WEB_GUI_POLL_INTERVAL)
        }
    }
}
