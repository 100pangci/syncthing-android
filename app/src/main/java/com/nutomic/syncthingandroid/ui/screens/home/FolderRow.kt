package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.CachedFolderStatus
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.model.FolderStatus
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.ui.theme.StatusBadge
import com.nutomic.syncthingandroid.ui.theme.StatusKind
import com.nutomic.syncthingandroid.util.FileUtils
import com.nutomic.syncthingandroid.util.Util

/**
 * One folder list item (MD3 card), ported from the legacy FoldersAdapter.
 * Tapping the card opens the folder settings; the trailing icon opens the
 * folder in the system file manager.
 */
@Composable
fun FolderRow(
    folder: Folder,
    restApi: RestApi?,
    apiConfigLoaded: Boolean,
    onEdit: () -> Unit,
    onOverride: (Folder) -> Unit,
    onRevert: (Folder) -> Unit,
) {
    val context = LocalContext.current
    val folderEntry = if (restApi != null && apiConfigLoaded) {
        restApi.getFolderStatus(folder.id)
    } else {
        null
    }
    val folderStatus: FolderStatus? = folderEntry?.key
    val cachedFolderStatus: CachedFolderStatus? = folderEntry?.value

    var showOverrideConfirm by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (folder.type) {
                    Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED -> Icons.Outlined.Lock
                    Constants.FOLDER_TYPE_RECEIVE_ONLY -> Icons.Outlined.Download
                    Constants.FOLDER_TYPE_SEND_ONLY -> Icons.Outlined.Upload
                    else -> Icons.Outlined.Folder
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (folder.label.isNullOrEmpty()) folder.id else folder.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val shortPath = remember(folder.path) {
                        getShortPathForUI(context, folder.path)
                    }
                    Text(
                        text = shortPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { FileUtils.openFolder(context, folder.path) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.open_file_manager),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val statusKind: StatusKind
            var statusText = ""
            var showProgressBar = false
            var progress = 0f

            if (folderStatus == null) {
                statusKind = StatusKind.PAUSED
            } else {
                val failedItems = folderStatus.errors > 0
                val neededItems = folderStatus.needFiles + folderStatus.needDirectories +
                        folderStatus.needSymlinks + folderStatus.needDeletes
                val outOfSync = folderStatus.state == "idle" && neededItems > 0
                val overrideButtonVisible = folder.type == Constants.FOLDER_TYPE_SEND_ONLY && outOfSync

                showProgressBar = folderStatus.state == "syncing"

                var revertButtonVisible = false
                if (folder.type == Constants.FOLDER_TYPE_RECEIVE_ONLY) {
                    revertButtonVisible = folderStatus.receiveOnlyTotalItems > 0
                } else if (folder.type == Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED) {
                    revertButtonVisible =
                        (folderStatus.receiveOnlyTotalItems - folderStatus.receiveOnlyChangedDeletes) > 0
                }
                val revertLabel =
                    if (folder.type == Constants.FOLDER_TYPE_RECEIVE_ONLY)
                        stringResource(R.string.revert_local_changes)
                    else
                        stringResource(R.string.delete_unexpected_items)

                when {
                    outOfSync -> {
                        statusText = stringResource(R.string.status_outofsync)
                        statusKind = StatusKind.ERROR
                    }
                    failedItems -> {
                        statusText = stringResource(R.string.state_failed_items, folderStatus.errors)
                        statusKind = StatusKind.ERROR
                    }
                    folder.paused -> {
                        statusText = stringResource(R.string.state_paused)
                        statusKind = StatusKind.PAUSED
                    }
                    else -> when (folderStatus.state) {
                        "clean-waiting" -> {
                            statusText = stringResource(R.string.state_clean_waiting)
                            statusKind = StatusKind.WARNING
                        }
                        "cleaning" -> {
                            statusText = stringResource(R.string.state_cleaning)
                            statusKind = StatusKind.SYNCING
                        }
                        "idle" -> {
                            if (folder.getDeviceCount() <= 1) {
                                statusText = stringResource(R.string.state_unshared)
                                statusKind = StatusKind.WARNING
                            } else if (revertButtonVisible) {
                                statusText = stringResource(R.string.state_local_additions)
                                statusKind = StatusKind.OK
                            } else {
                                statusText = stringResource(R.string.state_up_to_date)
                                statusKind = StatusKind.OK
                            }
                        }
                        "scan-waiting" -> {
                            statusText = stringResource(R.string.state_scan_waiting)
                            statusKind = StatusKind.WARNING
                        }
                        "scanning" -> {
                            statusText = stringResource(R.string.state_scanning)
                            statusKind = StatusKind.SYNCING
                        }
                        "sync-waiting" -> {
                            statusText = stringResource(R.string.state_sync_waiting)
                            statusKind = StatusKind.WARNING
                        }
                        "syncing" -> {
                            progress = (cachedFolderStatus?.completion ?: 100.0).toFloat() / 100f
                            statusText = stringResource(
                                R.string.state_syncing,
                                cachedFolderStatus?.completion?.toInt() ?: 100
                            )
                            statusKind = StatusKind.SYNCING
                        }
                        "sync-preparing" -> {
                            statusText = stringResource(R.string.state_sync_preparing)
                            statusKind = StatusKind.SYNCING
                        }
                        "error" -> {
                            statusText =
                                if (folderStatus.error.isNullOrEmpty())
                                    stringResource(R.string.state_error)
                                else
                                    stringResource(R.string.state_error_message, folderStatus.error)
                            statusKind = StatusKind.ERROR
                        }
                        "unknown" -> {
                            statusText = stringResource(R.string.state_unknown)
                            statusKind = StatusKind.ERROR
                        }
                        else -> {
                            statusText = folderStatus.state
                            statusKind = StatusKind.ERROR
                        }
                    }
                }

                if (overrideButtonVisible) {
                    TextButton(onClick = { showOverrideConfirm = true }) {
                        Text(stringResource(R.string.override_changes))
                    }
                }
                if (revertButtonVisible) {
                    TextButton(onClick = { showRevertConfirm = true }) {
                        Text(revertLabel)
                    }
                }

                ConflictsSection(cachedFolderStatus?.discoveredConflictFiles ?: emptyArray())
                LastItemFinishedSection(cachedFolderStatus)

                if (!folder.paused) {
                    val itemsAndSize = buildString {
                        append("\u2211 ")
                        append(
                            pluralStringResource(
                                R.plurals.files,
                                folderStatus.inSyncFiles.toInt(),
                                folderStatus.inSyncFiles,
                                folderStatus.globalFiles
                            )
                        )
                        append(" \u2022 ")
                        append(
                            stringResource(
                                R.string.folder_size_format,
                                Util.readableFileSize(context, folderStatus.inSyncBytes.toDouble()),
                                Util.readableFileSize(context, folderStatus.globalBytes.toDouble())
                            )
                        )
                    }
                    Text(
                        text = itemsAndSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!folderStatus.invalid.isNullOrEmpty()) {
                    Text(
                        text = folderStatus.invalid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                StatusBadge(text = statusText, kind = statusKind)
            }
            if (showProgressBar) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showOverrideConfirm) {
        AlertDialog(
            onDismissRequest = { showOverrideConfirm = false },
            title = { Text(stringResource(R.string.override_changes)) },
            text = { Text(stringResource(R.string.override_changes_question)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverrideConfirm = false
                    onOverride(folder)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverrideConfirm = false }) {
                    Text(stringResource(android.R.string.no))
                }
            }
        )
    }
    if (showRevertConfirm) {
        AlertDialog(
            onDismissRequest = { showRevertConfirm = false },
            title = { Text(stringResource(R.string.revert_local_changes)) },
            text = { Text(stringResource(R.string.revert_local_changes_question)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevertConfirm = false
                    onRevert(folder)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirm = false }) {
                    Text(stringResource(android.R.string.no))
                }
            }
        )
    }
}

@Composable
private fun ConflictsSection(discoveredConflictFiles: Array<String>) {
    val conflictFileCount = discoveredConflictFiles.size
    if (conflictFileCount == 0) {
        return
    }
    val itemCountAndFirst = buildString {
        append("\u26a0 ")
        append(pluralStringResource(R.plurals.conflicts, conflictFileCount, conflictFileCount))
        append("\n\u292e ")
        append(discoveredConflictFiles[0])
        if (conflictFileCount > 1) {
            append("\n\u2026")
        }
    }
    Text(
        text = itemCountAndFirst,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun LastItemFinishedSection(cachedFolderStatus: CachedFolderStatus?) {
    if (cachedFolderStatus == null) return
    if (cachedFolderStatus.lastItemFinishedAction.isNullOrEmpty() ||
        cachedFolderStatus.lastItemFinishedItem.isNullOrEmpty() ||
        cachedFolderStatus.lastItemFinishedTime.isNullOrEmpty()
    ) {
        return
    }
    val finishedItemText = buildString {
        append("\u21cc")
        when (cachedFolderStatus.lastItemFinishedAction) {
            "delete" -> append(" \u2297")
            "update" -> append(" \u229b")
            else -> append(" \u2049")
        }
        append(" ")
        append(Util.getPathEllipsis(cachedFolderStatus.lastItemFinishedItem))
    }
    val finishedTimeText = "\u21cc\u231a" + Util.formatTime(cachedFolderStatus.lastItemFinishedTime)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = finishedItemText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = finishedTimeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getShortPathForUI(context: android.content.Context, path: String): String {
    var shortenedPath = path
        .replaceFirst("/storage/emulated/0", "[int]")
        .replaceFirst("/storage/", "[ext]/")
    shortenedPath = if (shortenedPath.startsWith("/" + context.packageName)) {
        "/[app]" + shortenedPath.removePrefix("/" + context.packageName)
    } else {
        shortenedPath
    }
    return "\u2756 " + Util.getPathEllipsis(shortenedPath)
}
