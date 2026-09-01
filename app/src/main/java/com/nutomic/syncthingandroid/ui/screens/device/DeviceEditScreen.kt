package com.nutomic.syncthingandroid.ui.screens.device

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.DiscoveredDevice
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.activities.QRScannerActivity
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.ui.dialogs.CompressionDialog
import com.nutomic.syncthingandroid.ui.dialogs.ConfirmDialog
import com.nutomic.syncthingandroid.ui.dialogs.DeviceIdQrDialog
import com.nutomic.syncthingandroid.ui.nav.AppRoute
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.util.Compression
import com.nutomic.syncthingandroid.util.ConfigRouter

internal data class FolderShareState(
    val folder: Folder,
    val shared: Boolean,
    val password: String,
)

internal data class DeviceEditUiState(
    val device: Device,
    val needsUpdate: Boolean,
    val folderStates: List<FolderShareState>,
    val customSyncConditions: Boolean,
    val compressionIndex: Int,
)

private val UiStateSaver: Saver<DeviceEditUiState?, String> = Saver(
    save = { Gson().toJson(it) },
    restore = { Gson().fromJson(it, DeviceEditUiState::class.java) }
)

/**
 * Device add/edit screen, ported from the legacy DeviceActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditScreen(
    deviceId: String?,
    deviceName: String?,
    isCreate: Boolean,
    notificationId: Int,
) {
    val context = LocalContext.current
    val navigator = LocalAppNavigator.current
    val service = LocalSyncthingService.current
    val serviceState = LocalServiceState.current
    val api = service?.getApi()
    val apiConfigLoaded = api?.isConfigLoaded() ?: false
    val configRouter = remember { ConfigRouter(context) }
    val preferences = context.appPreferences()
    val prefExpertMode = preferences.getBoolean(Constants.PREF_EXPERT_MODE, false)

    var ui by rememberSaveable(stateSaver = UiStateSaver) { mutableStateOf<DeviceEditUiState?>(null) }

    // Init the model once.
    LaunchedEffect(Unit) {
        if (ui != null) return@LaunchedEffect
        val d = if (isCreate) {
            Device().apply {
                name = deviceName ?: ""
                deviceID = deviceId ?: ""
                addresses = listOf("dynamic")
                compression = Compression.METADATA.getValue(context)
            }
        } else {
            var found: Device? = null
            for (current in configRouter.getDevices(null, false)) {
                if (current.deviceID == (deviceId ?: "")) {
                    found = current
                    break
                }
            }
            if (found == null) {
                // Device not found, maybe it was deleted.
                navigator.navigateBack()
                return@LaunchedEffect
            }
            found
        }
        ui = DeviceEditUiState(
            device = d,
            needsUpdate = isCreate,
            folderStates = emptyList(),
            customSyncConditions = if (isCreate) false else preferences.getBoolean(
                Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(
                    Constants.PREF_OBJECT_PREFIX_DEVICE + d.deviceID
                ), false
            ),
            compressionIndex = Compression.fromValue(context, d.compression).getIndex(),
        )
    }

    var discoveredDevices by rememberSaveable { mutableStateOf<Map<String, DiscoveredDevice>?>(null) }
    val folders = remember(apiConfigLoaded) { configRouter.getFolders(api) }

    // Refresh folder share states whenever folders list changes.
    LaunchedEffect(folders, ui?.device?.deviceID) {
        val d = ui?.device ?: return@LaunchedEffect
        ui = ui?.copy(
            folderStates = folders.map { folder ->
                val shared = folder.getDevice(d.deviceID) != null
                val password = folder.getDevice(d.deviceID)?.encryptionPassword ?: ""
                FolderShareState(folder, shared, password)
            }
        )
    }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    var showCompressionDialog by rememberSaveable { mutableStateOf(false) }
    var discoveryRefresh by rememberSaveable { mutableStateOf(0) }

    val qrScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanned = result.data?.getStringExtra(QRScannerActivity.QR_RESULT_ARG)
        if (scanned != null) {
            val d = ui?.device ?: return@rememberLauncherForActivityResult
            d.deviceID = scanned
            ui = ui!!.copy(needsUpdate = true)
        }
    }

    LaunchedEffect(apiConfigLoaded, notificationId) {
        service?.getNotificationHandler()?.cancelConsentNotification(notificationId)
    }

    // Query discovered devices in create mode.
    LaunchedEffect(isCreate, apiConfigLoaded, ui?.device?.deviceID?.isEmpty(), discoveryRefresh) {
        if (isCreate && apiConfigLoaded && ui?.device?.deviceID?.isEmpty() == true) {
            api?.getDiscoveredDevices { result ->
                discoveredDevices = result
            }
        }
    }

    BackHandler(enabled = true) {
        if (ui?.needsUpdate == true) {
            showDiscardDialog = true
        } else {
            navigator.navigateBack()
        }
    }

    fun updateDevice(mutate: (Device) -> Unit) {
        val current = ui ?: return
        mutate(current.device)
        ui = current.copy(needsUpdate = true)
    }

    fun save() {
        val current = ui ?: return
        val d = current.device
        if (d.deviceID.isNullOrEmpty()) {
            Toast.makeText(context, R.string.device_id_required, Toast.LENGTH_LONG).show()
            return
        }
        if (!d.checkDeviceID()) {
            Toast.makeText(context, R.string.device_id_invalid, Toast.LENGTH_LONG).show()
            return
        }
        if (!d.checkDeviceAddresses()) {
            Toast.makeText(context, R.string.device_addresses_invalid, Toast.LENGTH_LONG).show()
            return
        }
        // Apply folder sharing + encryption passwords.
        for (state in current.folderStates) {
            val folder = state.folder
            if (state.shared) {
                folder.addDevice(d)
                folder.getDevice(d.deviceID)?.let { it.encryptionPassword = state.password }
            } else {
                folder.removeDevice(d.deviceID)
            }
            configRouter.updateFolder(api, folder)
        }
        if (isCreate) {
            configRouter.updateDevice(api, d)
            navigator.navigateBack()
            return
        }
        if (!current.needsUpdate) {
            navigator.navigateBack()
            return
        }
        preferences.edit().putBoolean(
            Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(
                Constants.PREF_OBJECT_PREFIX_DEVICE + d.deviceID
            ),
            current.customSyncConditions
        ).apply()
        configRouter.updateDevice(api, d)
        navigator.navigateBack()
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (isCreate) R.string.add_device else R.string.edit_device))
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (ui?.needsUpdate == true) showDiscardDialog = true
                            else navigator.navigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { save() }) {
                            Icon(
                                Icons.Outlined.Save,
                                stringResource(if (isCreate) R.string.create else R.string.save_title)
                            )
                        }
                        if (!isCreate) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.delete_device))
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val current = ui
                if (current != null) {
                    DeviceEditContent(
                        state = current,
                        isCreate = isCreate,
                        prefExpertMode = prefExpertMode,
                        discoveredDevices = discoveredDevices,
                        onDeviceMutate = ::updateDevice,
                        onFolderShareChange = { folderId, shared ->
                            ui = current.copy(
                                needsUpdate = true,
                                folderStates = current.folderStates.map {
                                    if (it.folder.id == folderId) it.copy(shared = shared) else it
                                }
                            )
                        },
                        onFolderPasswordChange = { folderId, password ->
                            ui = current.copy(
                                needsUpdate = true,
                                folderStates = current.folderStates.map {
                                    if (it.folder.id == folderId) it.copy(password = password) else it
                                }
                            )
                        },
                        onCustomSyncConditionsChange = { checked ->
                            ui = current.copy(needsUpdate = true, customSyncConditions = checked)
                        },
                        onCompressionClick = { showCompressionDialog = true },
                        onScanQr = { qrScanLauncher.launch(QRScannerActivity.intent(context)) },
                        onShowQr = { showQrDialog = true },
                        onOpenSyncConditions = {
                            navigator.navigateTo(
                                AppRoute.SyncConditions(
                                    objectPrefixAndId = Constants.PREF_OBJECT_PREFIX_DEVICE + current.device.deviceID,
                                    objectReadableName = current.device.displayName
                                )
                            )
                        },
                        onOpenFolderEdit = { navigator.openFolderEdit(null, true) },
                        onPickDiscoveredDevice = { pickedId ->
                            current.device.deviceID = pickedId
                            ui = current.copy(needsUpdate = true)
                        },
                        onRefreshDiscovery = { discoveryRefresh++ },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.syncthing_starting),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (showQrDialog) {
        val current = ui
        if (current != null) {
            DeviceIdQrDialog(
                deviceName = current.device.displayName.trim(),
                deviceId = current.device.deviceID,
                isCurrentDevice = false,
                onDismiss = { showQrDialog = false }
            )
        } else {
            showQrDialog = false
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            message = stringResource(R.string.remove_device_confirm),
            onConfirm = {
                showDeleteDialog = false
                configRouter.removeDevice(api, ui?.device?.deviceID)
                navigator.navigateBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            message = stringResource(R.string.dialog_discard_changes),
            onConfirm = {
                showDiscardDialog = false
                navigator.navigateBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    if (showCompressionDialog) {
        val current = ui
        if (current != null) {
            CompressionDialog(
                selectedIndex = current.compressionIndex,
                onSelect = { index ->
                    val compression = Compression.fromIndex(index)
                    showCompressionDialog = false
                    if (compression.getIndex() != current.compressionIndex) {
                        updateDevice { it.compression = compression.getValue(context) }
                        ui = ui?.copy(compressionIndex = index)
                    }
                },
                onDismiss = { showCompressionDialog = false }
            )
        } else {
            showCompressionDialog = false
        }
    }
}
