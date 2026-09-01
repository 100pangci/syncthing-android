package com.nutomic.syncthingandroid.ui.screens.home

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.ui.theme.StatusBadge
import com.nutomic.syncthingandroid.util.FileUtils
import com.nutomic.syncthingandroid.util.Util

/**
 * One folder list card (pure renderer; all data is precomputed in
 * [FolderUiModel]). Tapping the card opens the folder settings, the trailing
 * icon opens the folder in the system file manager.
 */
@Composable
fun FolderRow(
    model: FolderUiModel,
    onEdit: (FolderUiModel) -> Unit,
    onOverride: (FolderUiModel) -> Unit,
    onRevert: (FolderUiModel) -> Unit,
) {
    val context = LocalContext.current
    var showOverrideConfirm by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = { onEdit(model) },
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
                val icon = when (model.typeTag) {
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
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.pathShort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { FileUtils.openFolder(context, model.path) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.open_file_manager),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (model.overrideVisible) {
                TextButton(onClick = { showOverrideConfirm = true }) {
                    Text(stringResource(R.string.override_changes))
                }
            }
            if (model.revertVisible) {
                TextButton(onClick = { showRevertConfirm = true }) {
                    Text(stringResource(model.revertLabelRes))
                }
            }

            model.conflictText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (model.lastItemText != null && model.lastItemTimeText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = model.lastItemText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = model.lastItemTimeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            model.itemsAndSize?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            model.invalidText?.let {
                if (it.isNotEmpty()) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            model.statusText?.let {
                Spacer(Modifier.height(4.dp))
                StatusBadge(text = it, kind = model.statusKind)
            }
            if (model.isSyncing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { model.completion / 100f },
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
                    onOverride(model)
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
                    onRevert(model)
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
