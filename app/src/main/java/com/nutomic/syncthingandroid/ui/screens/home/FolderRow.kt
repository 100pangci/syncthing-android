package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.colorResource
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
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.util.FileUtils
import com.nutomic.syncthingandroid.util.Util

/**
 * One folder list item, ported from the legacy FoldersAdapter (View based).
 */
@Composable
fun FolderRow(
    folder: Folder,
    restApi: RestApi?,
    apiConfigLoaded: Boolean,
    onOverride: (Folder) -> Unit,
    onRevert: (Folder) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderEntry = if (restApi != null && apiConfigLoaded) {
        restApi.getFolderStatus(folder.id)
    } else {
        null
    }
    val folderStatus: FolderStatus? = folderEntry?.key
    val cachedFolderStatus: CachedFolderStatus? = folderEntry?.value

    var showOverrideConfirm by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                FileUtils.openFolder(context, folder.path)
            }
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
                Text(
                    text = getShortPathForUI(context, folder.path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { FileUtils.openFolder(context, folder.path) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.open_file_manager),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        val statusTextColor: androidx.compose.ui.graphics.Color
        var statusText = ""
        var showProgressBar = false
        var progress = 0f

        if (folderStatus == null) {
            statusTextColor = MaterialTheme.colorScheme.onSurface
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
                    statusTextColor = colorResource(R.color.text_red)
                }
                failedItems -> {
                    statusText = stringResource(R.string.state_failed_items, folderStatus.errors)
                    statusTextColor = colorResource(R.color.text_red)
                }
                folder.paused -> {
                    statusText = stringResource(R.string.state_paused)
                    statusTextColor = colorResource(R.color.text_purple)
                }
                else -> when (folderStatus.state) {
                    "clean-waiting" -> {
                        statusText = stringResource(R.string.state_clean_waiting)
                        statusTextColor = colorResource(R.color.text_orange)
                    }
                    "cleaning" -> {
                        statusText = stringResource(R.string.state_cleaning)
                        statusTextColor = colorResource(R.color.text_blue)
                    }
                    "idle" -> {
                        if (folder.getDeviceCount() <= 1) {
                            statusText = stringResource(R.string.state_unshared)
                            statusTextColor = colorResource(R.color.text_orange)
                        } else if (revertButtonVisible) {
                            statusText = stringResource(R.string.state_local_additions)
                            statusTextColor = colorResource(R.color.text_green)
                        } else {
                            statusText = stringResource(R.string.state_up_to_date)
                            statusTextColor = colorResource(R.color.text_green)
                        }
                    }
                    "scan-waiting" -> {
                        statusText = stringResource(R.string.state_scan_waiting)
                        statusTextColor = colorResource(R.color.text_orange)
                    }
                    "scanning" -> {
                        statusText = stringResource(R.string.state_scanning)
                        statusTextColor = colorResource(R.color.text_blue)
                    }
                    "sync-waiting" -> {
                        statusText = stringResource(R.string.state_sync_waiting)
                        statusTextColor = colorResource(R.color.text_orange)
                    }
                    "syncing" -> {
                        progress = (cachedFolderStatus?.completion ?: 100.0).toFloat() / 100f
                        statusText = stringResource(
                            R.string.state_syncing,
                            cachedFolderStatus?.completion?.toInt() ?: 100
                        )
                        statusTextColor = colorResource(R.color.text_blue)
                    }
                    "sync-preparing" -> {
                        statusText = stringResource(R.string.state_sync_preparing)
                        statusTextColor = colorResource(R.color.text_blue)
                    }
                    "error" -> {
                        statusText =
                            if (folderStatus.error.isNullOrEmpty())
                                stringResource(R.string.state_error)
                            else
                                stringResource(R.string.state_error_message, folderStatus.error)
                        statusTextColor = colorResource(R.color.text_red)
                    }
                    "unknown" -> {
                        statusText = stringResource(R.string.state_unknown)
                        statusTextColor = colorResource(R.color.text_red)
                    }
                    else -> {
                        statusText = folderStatus.state
                        statusTextColor = colorResource(R.color.text_red)
                    }
                }
            }

            if (overrideButtonVisible) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showOverrideConfirm = true }) {
                    Text(stringResource(R.string.override_changes))
                }
            }
            if (revertButtonVisible) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showRevertConfirm = true }) {
                    Text(revertLabel)
                }
            }

            showConflictsUI(cachedFolderStatus?.discoveredConflictFiles ?: emptyArray())
            showLastItemFinishedUI(cachedFolderStatus)

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
                    color = colorResource(R.color.text_red)
                )
            }
        }

        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusTextColor
            )
        }
        if (showProgressBar) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
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
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun showConflictsUI(discoveredConflictFiles: Array<String>) {
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
        color = colorResource(R.color.text_orange)
    )
}

@Composable
private fun showLastItemFinishedUI(cachedFolderStatus: CachedFolderStatus?) {
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
    var shortenedPath = path.replaceFirst("/storage/emulated/0", "[int]")
    shortenedPath = shortenedPath.replaceFirst("/storage/[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}".toRegex(), "[ext]")
    shortenedPath = shortenedPath.replaceFirst("/" + context.packageName, "/[app]")
    return "\u2756 " + Util.getPathEllipsis(shortenedPath)
}
