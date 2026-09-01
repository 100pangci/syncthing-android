package com.nutomic.syncthingandroid.ui.screens.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.ui.components.ClickRow
import com.nutomic.syncthingandroid.ui.components.FormCard
import com.nutomic.syncthingandroid.ui.components.ToggleRow

/**
 * Bottom part of the folder form: pull order / versioning rows, toggles,
 * device sharing and ignore patterns.
 */
@Composable
internal fun FolderEditBottomSection(
    holder: FolderEditStateHolder,
    folder: com.nutomic.syncthingandroid.model.Folder,
    isCreate: Boolean,
    prefExpertMode: Boolean,
    onMarkDirty: () -> Unit,
    onIgnoreListChanged: (String) -> Unit,
    onShowPullOrderDialog: () -> Unit,
    onShowVersioningDialog: () -> Unit,
    onOpenSyncConditions: () -> Unit,
    onOpenDeviceEdit: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        // ---- Devices sharing this folder ----
        FormCard(title = stringResource(R.string.devices)) {
        if (holder.deviceStates.isEmpty()) {
            Text(
                text = stringResource(R.string.devices_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDeviceEdit() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            holder.deviceStates.forEach { shareState ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = shareState.device.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val newShared = !shareState.shared
                                    if (newShared) {
                                        folder.addDevice(
                                            com.nutomic.syncthingandroid.model.SharedWithDevice().apply {
                                                deviceID = shareState.device.deviceID
                                                introducedBy = shareState.device.introducedBy
                                            }
                                        )
                                    } else {
                                        folder.removeDevice(shareState.device.deviceID)
                                    }
                                    holder.deviceStates = holder.deviceStates.map {
                                        if (it.device.deviceID == shareState.device.deviceID)
                                            it.copy(shared = newShared)
                                        else it
                                    }
                                    onMarkDirty()
                                }
                        )
                        Switch(
                            checked = shareState.shared,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    folder.addDevice(
                                        com.nutomic.syncthingandroid.model.SharedWithDevice().apply {
                                            deviceID = shareState.device.deviceID
                                            introducedBy = shareState.device.introducedBy
                                        }
                                    )
                                } else {
                                    folder.removeDevice(shareState.device.deviceID)
                                }
                                holder.deviceStates = holder.deviceStates.map {
                                    if (it.device.deviceID == shareState.device.deviceID)
                                        it.copy(shared = checked)
                                    else it
                                }
                                onMarkDirty()
                            }
                        )
                    }
                    if (shareState.shared) {
                        OutlinedTextField(
                            value = shareState.password,
                            onValueChange = { value ->
                                folder.getDevice(shareState.device.deviceID)?.let { it.encryptionPassword = value }
                                holder.deviceStates = holder.deviceStates.map {
                                    if (it.device.deviceID == shareState.device.deviceID)
                                        it.copy(password = value)
                                    else it
                                }
                                onMarkDirty()
                            },
                            label = { Text(stringResource(R.string.deviceEncryptionPasswordHint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        }
        FormCard {
        // ---- Toggles ----
        ToggleRow(
            title = stringResource(R.string.folder_fileWatcher),
            description = stringResource(R.string.folder_fileWatcherDescription),
            checked = folder.fsWatcherEnabled,
            onCheckedChange = { checked ->
                folder.fsWatcherEnabled = checked
                onMarkDirty()
            }
        )
        ToggleRow(
            title = stringResource(R.string.folder_pause),
            checked = folder.paused,
            onCheckedChange = { checked ->
                folder.paused = checked
                onMarkDirty()
            }
        )

        // ---- Custom sync conditions (edit mode only) ----
        if (!isCreate) {
            ToggleRow(
                title = stringResource(R.string.custom_sync_conditions_title),
                description = stringResource(R.string.custom_sync_conditions_description),
                checked = holder.customSyncConditions,
                onCheckedChange = { checked ->
                    holder.customSyncConditions = checked
                    onMarkDirty()
                }
            )
            if (holder.customSyncConditions) {
                ClickRow(
                    title = stringResource(R.string.custom_sync_conditions_dialog),
                    onClick = onOpenSyncConditions
                )
            }
        }

        // ---- Expert options ----
        if (prefExpertMode && folder.type != Constants.FOLDER_TYPE_SEND_ONLY) {
            val pullOrderLabel = pullOrderLabel(folder.order)
            val pullOrderDescription = pullOrderDescription(folder.order)
            ClickRow(
                title = stringResource(R.string.pull_order),
                value = pullOrderLabel,
                description = pullOrderDescription,
                onClick = onShowPullOrderDialog
            )
        }
        val versioningDescription = versioningDescription(folder)
        ClickRow(
            title = stringResource(R.string.file_versioning),
            value = versioningTypeLabel(folder),
            description = versioningDescription,
            onClick = onShowVersioningDialog
        )
        if (prefExpertMode) {
            ToggleRow(
                title = stringResource(R.string.folder_ignore_delete_caption),
                description = stringResource(R.string.folder_ignore_delete_description),
                checked = folder.ignoreDelete,
                onCheckedChange = { checked ->
                    folder.ignoreDelete = checked
                    onMarkDirty()
                }
            )
            ToggleRow(
                title = stringResource(R.string.folder_run_script_caption),
                description = stringResource(R.string.folder_run_script_description),
                checked = holder.runScript,
                onCheckedChange = { checked ->
                    holder.runScript = checked
                    onMarkDirty()
                }
            )
        }

        }
        FormCard(title = stringResource(R.string.ignore_patterns)) {
        if (!isCreate) {
            OutlinedTextField(
                value = holder.ignoreListText,
                onValueChange = onIgnoreListChanged,
                label = { Text(stringResource(R.string.ignore_patterns)) },
                enabled = folder.path.isNotEmpty() && holder.canWriteToPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        }
    }
}

@Composable
private fun pullOrderLabel(order: String?): String = when (order) {
    null, "" -> stringResource(R.string.pull_order_type_random)
    "random" -> stringResource(R.string.pull_order_type_random)
    "alphabetic" -> stringResource(R.string.pull_order_type_alphabetic)
    "smallestFirst" -> stringResource(R.string.pull_order_type_smallestFirst)
    "largestFirst" -> stringResource(R.string.pull_order_type_largestFirst)
    "oldestFirst" -> stringResource(R.string.pull_order_type_oldestFirst)
    "newestFirst" -> stringResource(R.string.pull_order_type_newestFirst)
    else -> order
}

@Composable
private fun pullOrderDescription(order: String?): String = when (order) {
    null, "" -> stringResource(R.string.pull_order_type_random_description)
    "random" -> stringResource(R.string.pull_order_type_random_description)
    "alphabetic" -> stringResource(R.string.pull_order_type_alphabetic_description)
    "smallestFirst" -> stringResource(R.string.pull_order_type_smallestFirst_description)
    "largestFirst" -> stringResource(R.string.pull_order_type_largestFirst_description)
    "oldestFirst" -> stringResource(R.string.pull_order_type_oldestFirst_description)
    "newestFirst" -> stringResource(R.string.pull_order_type_newestFirst_description)
    else -> ""
}

@Composable
private fun versioningTypeLabel(folder: com.nutomic.syncthingandroid.model.Folder): String {
    val type = folder.versioning?.type
    return when {
        type.isNullOrEmpty() -> stringResource(R.string.none)
        type == "simple" -> stringResource(R.string.type_simple)
        type == "trashcan" -> stringResource(R.string.type_trashcan)
        type == "staggered" -> stringResource(R.string.type_staggered)
        type == "external" -> stringResource(R.string.type_external)
        else -> type
    }
}

@Composable
private fun versioningDescription(folder: com.nutomic.syncthingandroid.model.Folder): String {
    val versioning = folder.versioning ?: return ""
    val type = versioning.type
    val params = versioning.params
    return when {
        type.isNullOrEmpty() -> ""
        type == "simple" -> stringResource(R.string.simple_versioning_info, params["keep"] ?: "")
        type == "trashcan" -> stringResource(R.string.trashcan_versioning_info, params["cleanoutDays"] ?: "")
        type == "staggered" -> {
            val maxAge = try {
                java.util.concurrent.TimeUnit.SECONDS
                    .toDays(params["maxAge"]?.toLong() ?: 0L).toInt()
            } catch (e: NumberFormatException) {
                0
            }
            stringResource(R.string.staggered_versioning_info, maxAge, params["versionsPath"] ?: "")
        }
        type == "external" -> stringResource(R.string.external_versioning_info, params["command"] ?: "")
        else -> ""
    }
}
