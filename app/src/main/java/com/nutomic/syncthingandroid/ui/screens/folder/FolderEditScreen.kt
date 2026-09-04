package com.nutomic.syncthingandroid.ui.screens.folder

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.ui.dialogs.ConfirmDialog
import com.nutomic.syncthingandroid.ui.nav.AppNavigator
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.ui.nav.LocalResultBus
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.util.ConfigRouter
import com.nutomic.syncthingandroid.util.FileUtils

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
    // Draft state is store-backed (NOT remember): Nav3 disposes this entry while the
    // sync conditions / folder picker routes are on top, and the draft has to survive
    // that. See FolderEditStateStore for the eviction lifecycle.
    val holder = LocalFolderEditStateStore.current.holderFor(folderEditStateKey(folderId, isCreate))
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showFolderTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showPullOrderDialog by rememberSaveable { mutableStateOf(false) }
    var showVersioningDialog by rememberSaveable { mutableStateOf(false) }

    // Init the model once.
    LaunchedEffect(Unit) {
        initFolderEditState(
            context, holder, isCreate, folderId, folderLabel, receiveEncrypted,
            deviceId, notificationId, configRouter, navigator, preferences,
        )
    }
    // Cancel the consent notification once the service is connected. On a
    // cold start from the notification tap the service is not yet bound while
    // the init effect runs, so cancellation has to react to the service
    // becoming available instead.
    LaunchedEffect(service) {
        service?.getNotificationHandler()?.cancelConsentNotification(notificationId)
    }
    // Refresh device share states when the api becomes available or folder changes.
    LaunchedEffect(apiConfigLoaded, holder.folder?.id) {
        refreshDeviceShareStates(configRouter, api, holder)
    }
    // Collect folder picker result pushed by the FolderPicker route.
    LaunchedEffect(Unit) {
        collectFolderPickerResults(resultBus, context, holder)
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
    fun saveFolder() {
        val folder = holder.folder ?: return
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
    }
    Scaffold(
        topBar = {
            FolderEditTopBar(
                holder = holder, isCreate = isCreate,
                onDiscardChanges = { showDiscardDialog = true },
                onDelete = { showDeleteDialog = true },
            )
        },
        floatingActionButton = {
            if (f != null) {
                FloatingActionButton(
                    onClick = { saveFolder() },
                    modifier = Modifier.imePadding()
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        stringResource(if (isCreate) R.string.create else R.string.save_title)
                    )
                }
            }
        }
    ) { innerPadding ->
        FolderEditBody(
            holder = holder, folder = f, isCreate = isCreate,
            prefExpertMode = prefExpertMode, innerPadding = innerPadding,
            launchSafPicker = { safLauncher.launch(it) },
            onFolderTypeDialogApproved = { showFolderTypeDialog = true },
            onShowPullOrderDialog = { showPullOrderDialog = true },
            onShowVersioningDialog = { showVersioningDialog = true },
            onMarkDirty = { holder.needsUpdate = true },
        )
    }
    // ---- Dialogs ----
    FolderEditConfigDialogs(
        folder = f, isCreate = isCreate, holder = holder,
        showFolderTypeDialog = showFolderTypeDialog,
        showPullOrderDialog = showPullOrderDialog,
        showVersioningDialog = showVersioningDialog,
        onDismissFolderTypeDialog = { showFolderTypeDialog = false },
        onDismissPullOrderDialog = { showPullOrderDialog = false },
        onDismissVersioningDialog = { showVersioningDialog = false },
    )
    FolderEditConfirmDialogs(
        folder = f,
        showDeleteDialog = showDeleteDialog,
        showDiscardDialog = showDiscardDialog,
        configRouter = configRouter, api = api, preferences = preferences,
        onDismissDeleteDialog = { showDeleteDialog = false },
        onDismissDiscardDialog = { showDiscardDialog = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderEditTopBar(
    holder: FolderEditStateHolder,
    isCreate: Boolean,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit,
) {
    val navigator = LocalAppNavigator.current
    TopAppBar(
        title = {
            Text(stringResource(if (isCreate) R.string.create_folder else R.string.edit_folder))
        },
        navigationIcon = {
            IconButton(onClick = {
                if (holder.isSaving) return@IconButton
                if (holder.needsUpdate) onDiscardChanges()
                else navigator.navigateBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel))
            }
        },
        actions = {
            if (!isCreate) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.delete_folder))
                }
            }
        }
    )
}

@Composable
private fun FolderEditBody(
    holder: FolderEditStateHolder,
    folder: Folder?,
    isCreate: Boolean,
    prefExpertMode: Boolean,
    innerPadding: PaddingValues,
    launchSafPicker: (Uri?) -> Unit,
    onFolderTypeDialogApproved: () -> Unit,
    onShowPullOrderDialog: () -> Unit,
    onShowVersioningDialog: () -> Unit,
    onMarkDirty: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalAppNavigator.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (folder != null) {
            FolderEditContent(
                holder = holder,
                folder = folder,
                isCreate = isCreate,
                prefExpertMode = prefExpertMode,
                onMarkDirty = onMarkDirty,
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
                        launchSafPicker(initialUri)
                    } catch (e: android.content.ActivityNotFoundException) {
                        navigator.openFolderPicker(holder.folder?.path, null)
                    }
                },
                onPickAdvancedPath = { navigator.openFolderPicker(holder.folder?.path, null) },
                onShowFolderTypeDialog = {
                    onFolderTypeDialogRequest(context, holder) { ok ->
                        if (ok) onFolderTypeDialogApproved()
                    }
                },
                onShowPullOrderDialog = onShowPullOrderDialog,
                onShowVersioningDialog = onShowVersioningDialog,
                onOpenSyncConditions = {
                    navigator.navigateTo(
                        com.nutomic.syncthingandroid.ui.nav.AppRoute.SyncConditions(
                            objectPrefixAndId = Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id,
                            objectReadableName = folder.label
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

@Composable
private fun FolderEditConfigDialogs(
    folder: Folder?,
    isCreate: Boolean,
    holder: FolderEditStateHolder,
    showFolderTypeDialog: Boolean,
    showPullOrderDialog: Boolean,
    showVersioningDialog: Boolean,
    onDismissFolderTypeDialog: () -> Unit,
    onDismissPullOrderDialog: () -> Unit,
    onDismissVersioningDialog: () -> Unit,
) {
    val context = LocalContext.current
    if (showFolderTypeDialog && folder != null) {
        com.nutomic.syncthingandroid.ui.dialogs.FolderTypeDialog(
            currentType = folder.type,
            onSelect = { newType ->
                onDismissFolderTypeDialog()
                if (!isCreate && newType == Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED) {
                    Toast.makeText(
                        context,
                        R.string.folder_type_switch_to_receive_encrypted_not_allowed,
                        Toast.LENGTH_LONG
                    ).show()
                    return@FolderTypeDialog
                }
                folder.type = newType
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = onDismissFolderTypeDialog
        )
    }
    if (showPullOrderDialog && folder != null) {
        com.nutomic.syncthingandroid.ui.dialogs.PullOrderDialog(
            currentOrder = folder.order,
            onSelect = { order ->
                onDismissPullOrderDialog()
                folder.order = order
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = onDismissPullOrderDialog
        )
    }
    if (showVersioningDialog && folder != null) {
        com.nutomic.syncthingandroid.ui.dialogs.VersioningDialog(
            initialType = folder.versioning?.type ?: "none",
            initialParams = folder.versioning?.params ?: emptyMap(),
            onApply = { type, params ->
                onDismissVersioningDialog()
                applyVersioning(folder, type, params)
                holder.configVersion++
                holder.needsUpdate = true
            },
            onDismiss = onDismissVersioningDialog
        )
    }
}

@Composable
private fun FolderEditConfirmDialogs(
    folder: Folder?,
    showDeleteDialog: Boolean,
    showDiscardDialog: Boolean,
    configRouter: ConfigRouter,
    api: RestApi?,
    preferences: SharedPreferences,
    onDismissDeleteDialog: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
) {
    val navigator = LocalAppNavigator.current
    if (showDeleteDialog && folder != null) {
        ConfirmDialog(
            message = stringResource(R.string.remove_folder_confirm),
            onConfirm = {
                onDismissDeleteDialog()
                FolderEditActions.delete(configRouter, api, preferences, folder.id, navigator)
            },
            onDismiss = onDismissDeleteDialog
        )
    }
    if (showDiscardDialog) {
        ConfirmDialog(
            message = stringResource(R.string.dialog_discard_changes),
            onConfirm = {
                onDismissDiscardDialog()
                navigator.navigateBack()
            },
            onDismiss = onDismissDiscardDialog
        )
    }
}

private suspend fun initFolderEditState(
    context: Context,
    holder: FolderEditStateHolder,
    isCreate: Boolean,
    folderId: String?,
    folderLabel: String?,
    receiveEncrypted: Boolean,
    deviceId: String?,
    notificationId: Int,
    configRouter: ConfigRouter,
    navigator: AppNavigator,
    preferences: SharedPreferences,
) {
    if (holder.folder != null) return
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
            return
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
}

private suspend fun refreshDeviceShareStates(
    configRouter: ConfigRouter,
    api: RestApi?,
    holder: FolderEditStateHolder,
) {
    val f = holder.folder ?: return
    val devices = configRouter.getDevices(api, false)
    holder.deviceStates = devices.map { device ->
        val shared = f.getDevice(device.deviceID) != null
        val password = f.getDevice(device.deviceID)?.encryptionPassword ?: ""
        DeviceShareState(device, shared, password)
    }
}

private suspend fun collectFolderPickerResults(
    resultBus: ResultBus,
    context: Context,
    holder: FolderEditStateHolder,
) {
    resultBus.folderPickerResult.collect { path ->
        if (path != null) {
            onPickedPath(context, holder, path)
        }
        resultBus.folderPickerResult.value = null
    }
}
