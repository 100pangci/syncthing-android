package com.nutomic.syncthingandroid.ui.screens.folder

import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.ui.dialogs.ConfirmDialog
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.ui.nav.LocalResultBus
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.util.ConfigRouter
import com.nutomic.syncthingandroid.util.FileUtils
import com.nutomic.syncthingandroid.util.Util

/**
 * Folder add/edit screen, ported from the legacy FolderActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditScreen(
    folderId: String?,
    folderLabel: String?,
    isCreate: Boolean,
    deviceId: String?,
    receiveEncrypted: Boolean,
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
    val scope = rememberCoroutineScope()
    val resultBus = LocalResultBus.current

    val prefExpertMode = preferences.getBoolean(Constants.PREF_EXPERT_MODE, false)
    val holder = rememberFolderEditStateHolder()

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showFolderTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showPullOrderDialog by rememberSaveable { mutableStateOf(false) }
    var showVersioningDialog by rememberSaveable { mutableStateOf(false) }

    fun markDirty() {
        holder.needsUpdate = true
    }

    // Init the model once.
    LaunchedEffect(Unit) {
        if (holder.folder != null) return@LaunchedEffect
        if (isCreate) {
            holder.folder = initNewFolder(folderId, folderLabel, receiveEncrypted)
            holder.needsUpdate = true
        } else {
            var found: Folder? = null
            for (current in configRouter.getFolders(null)) {
                if (current.id == (folderId ?: "")) {
                    found = current
                    break
                }
            }
            if (found == null) {
                navigator.navigateBack()
                return@LaunchedEffect
            }
            holder.folder = found
            configRouter.getFolderIgnoreList(null, found) { list ->
                holder.ignoreListText = list.ignore?.joinToString("\n") ?: ""
            }
        }
        holder.customSyncConditions = if (isCreate) false else preferences.getBoolean(
            Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(
                Constants.PREF_OBJECT_PREFIX_FOLDER + holder.folder!!.id
            ), false
        )
        holder.runScript = preferences.getBoolean(
            Constants.DYN_PREF_OBJECT_FOLDER_RUN_SCRIPT(holder.folder!!.id), false
        )
        // Evaluate the write access of the folder path so "folder type" and the
        // ignore patterns are enabled for folders that are only being edited.
        checkWriteAndUpdateUI(context, holder)
        // Automatically share with the given device (e.g. when accepting a folder share).
        deviceId?.let { devId ->
            holder.folder?.addDevice(com.nutomic.syncthingandroid.model.SharedWithDevice().apply {
                deviceID = devId
            })
            holder.needsUpdate = true
        }
        service?.getNotificationHandler()?.cancelConsentNotification(notificationId)
    }

    // Refresh device share states when the api becomes available or folder changes.
    LaunchedEffect(apiConfigLoaded, holder.folder?.id) {
        val f = holder.folder ?: return@LaunchedEffect
        val devices = configRouter.getDevices(api, false)
        holder.deviceStates = devices.map { device ->
            val shared = f.getDevice(device.deviceID) != null
            val password = f.getDevice(device.deviceID)?.encryptionPassword ?: ""
            DeviceShareState(device, shared, password)
        }
    }

    // Collect folder picker result pushed by the FolderPicker route.
    LaunchedEffect(Unit) {
        resultBus.folderPickerResult.collect { path ->
            if (path != null) {
                onPickedPath(context, holder, path)
            }
            resultBus.folderPickerResult.value = null
        }
    }

    BackHandler(enabled = !holder.isSaving) {
        if (holder.needsUpdate) {
            showDiscardDialog = true
        } else {
            navigator.navigateBack()
        }
    }

    // SAF directory picker used when the system picker is available.
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            holder.folderUri = uri
            val targetPath = FileUtils.getAbsolutePathFromSAFUri(context, uri)
            onPickedPath(context, holder, targetPath)
        }
    }

    val f = holder.folder
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isCreate) R.string.create_folder else R.string.edit_folder))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (holder.isSaving) return@IconButton
                        if (holder.needsUpdate) showDiscardDialog = true
                        else navigator.navigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val folder = holder.folder ?: return@IconButton
                        FolderEditActions.save(
                            scope = scope,
                            context = context,
                            navigator = navigator,
                            configRouter = configRouter,
                            api = api,
                            preferences = preferences,
                            folder = folder,
                            folderUri = holder.folderUri,
                            needsUpdate = holder.needsUpdate,
                            ignoreListNeedsUpdate = holder.ignoreListNeedsUpdate,
                            ignoreListText = holder.ignoreListText,
                            deviceStates = holder.deviceStates,
                            customSyncConditions = holder.customSyncConditions,
                            runScript = holder.runScript,
                            isCreate = isCreate,
                            isSaving = holder.isSaving,
                            setSaving = { holder.isSaving = it },
                            onValidationError = { res ->
                                Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                            },
                        )
                    }) {
                        Icon(
                            Icons.Outlined.Save,
                            stringResource(if (isCreate) R.string.create else R.string.save_title)
                        )
                    }
                    if (!isCreate) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete_folder))
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
            if (f != null) {
                FolderEditContent(
                    holder = holder,
                    folder = f,
                    isCreate = isCreate,
                    prefExpertMode = prefExpertMode,
                    onMarkDirty = ::markDirty,
                    onIgnoreListChanged = {
                        holder.ignoreListText = it
                        holder.ignoreListNeedsUpdate = true
                        holder.needsUpdate = true
                    },
                    onPickPath = {
                        // Prefer the storage access framework picker with a sensible initial uri.
                        var initialUri: Uri? = null
                        val externalFilesDirUri =
                            FileUtils.getExternalFilesDirUri(context, FileUtils.ExternalStorageDirType.INT_MEDIA)
                        if (FileUtils.directoryUriExists(context, externalFilesDirUri)) {
                            initialUri = externalFilesDirUri
                        } else {
                            val internalFilesDirUri = FileUtils.getInternalStorageRootUri()
                            if (FileUtils.directoryUriExists(context, internalFilesDirUri)) {
                                initialUri = internalFilesDirUri
                            }
                        }
                        try {
                            safLauncher.launch(initialUri)
                        } catch (e: android.content.ActivityNotFoundException) {
                            navigator.openFolderPicker(holder.folder?.path, null)
                        }
                    },
                    onPickAdvancedPath = { navigator.openFolderPicker(holder.folder?.path, null) },
                    onShowFolderTypeDialog = {
                        onFolderTypeDialogRequest(context, holder) { ok ->
                            if (ok) showFolderTypeDialog = true
                        }
                    },
                    onShowPullOrderDialog = { showPullOrderDialog = true },
                    onShowVersioningDialog = { showVersioningDialog = true },
                    onOpenSyncConditions = {
                        navigator.navigateTo(
                            com.nutomic.syncthingandroid.ui.nav.AppRoute.SyncConditions(
                                objectPrefixAndId = Constants.PREF_OBJECT_PREFIX_FOLDER + f.id,
                                objectReadableName = f.label
                            )
                        )
                    },
                    onOpenDeviceEdit = { navigator.openDeviceEdit(null, true) },
                )
            }
            if (holder.isSaving) {
                SavingOverlay()
            }
        }
    }

    // ---- Dialogs ----
    if (showFolderTypeDialog && f != null) {
        com.nutomic.syncthingandroid.ui.dialogs.FolderTypeDialog(
            currentType = f.type,
            onSelect = { newType ->
                showFolderTypeDialog = false
                if (!isCreate && newType == Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED) {
                    Toast.makeText(
                        context,
                        R.string.folder_type_switch_to_receive_encrypted_not_allowed,
                        Toast.LENGTH_LONG
                    ).show()
                    return@FolderTypeDialog
                }
                f.type = newType
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = { showFolderTypeDialog = false }
        )
    }
    if (showPullOrderDialog && f != null) {
        com.nutomic.syncthingandroid.ui.dialogs.PullOrderDialog(
            currentOrder = f.order,
            onSelect = { order ->
                showPullOrderDialog = false
                f.order = order
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = { showPullOrderDialog = false }
        )
    }
    if (showVersioningDialog && f != null) {
        com.nutomic.syncthingandroid.ui.dialogs.VersioningDialog(
            initialType = f.versioning?.type ?: "none",
            initialParams = f.versioning?.params ?: emptyMap(),
            onApply = { type, params ->
                showVersioningDialog = false
                applyVersioning(f, type, params)
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = { showVersioningDialog = false }
        )
    }
    if (showDeleteDialog && f != null) {
        ConfirmDialog(
            message = stringResource(R.string.remove_folder_confirm),
            onConfirm = {
                showDeleteDialog = false
                FolderEditActions.delete(configRouter, api, preferences, f.id, navigator)
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
}
