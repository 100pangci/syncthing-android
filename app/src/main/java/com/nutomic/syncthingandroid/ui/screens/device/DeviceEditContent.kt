package com.nutomic.syncthingandroid.ui.screens.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.DiscoveredDevice
import com.nutomic.syncthingandroid.ui.components.ClickRow
import com.nutomic.syncthingandroid.ui.components.ToggleRow

/**
 * Form content of the device add/edit screen.
 */
@Composable
internal fun DeviceEditContent(
    state: DeviceEditUiState,
    isCreate: Boolean,
    prefExpertMode: Boolean,
    discoveredDevices: Map<String, DiscoveredDevice>?,
    onDeviceMutate: ((Device) -> Unit) -> Unit,
    onFolderShareChange: (String, Boolean) -> Unit,
    onFolderPasswordChange: (String, String) -> Unit,
    onCustomSyncConditionsChange: (Boolean) -> Unit,
    onCompressionClick: () -> Unit,
    onScanQr: () -> Unit,
    onShowQr: () -> Unit,
    onOpenSyncConditions: () -> Unit,
    onOpenFolderEdit: () -> Unit,
    onPickDiscoveredDevice: (String) -> Unit,
    onRefreshDiscovery: () -> Unit,
) {
    val device = state.device

    var deviceIdText by remember(device.deviceID) { mutableStateOf(device.deviceID ?: "") }
    var nameText by remember(device) { mutableStateOf(device.name ?: "") }
    var addressesText by remember(device) { mutableStateOf(displayableAddresses(device)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // ---- Device identity ----
        if (isCreate) {
            OutlinedTextField(
                value = deviceIdText,
                onValueChange = { value ->
                    deviceIdText = value
                    onDeviceMutate { it.deviceID = value }
                },
                label = { Text(stringResource(R.string.device_id)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                TextButton(onClick = onScanQr) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_qr_code_description))
                }
            }
        } else {
            ClickRow(
                title = stringResource(R.string.device_id),
                value = device.deviceID,
                onClick = onShowQr
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                IconButton(onClick = onShowQr) {
                    Icon(Icons.Outlined.QrCode2, contentDescription = stringResource(R.string.show_device_id))
                }
                Text(
                    text = stringResource(R.string.show_device_id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Discovered devices (create mode only) ----
        if (isCreate && deviceIdText.isEmpty() && discoveredDevices != null) {
            if (discoveredDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.local_discovery_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.discovered_devices_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onRefreshDiscovery() }
                )
                discoveredDevices.forEach { (id, discoveredDevice) ->
                    val readableAddresses = discoveredDevice.addresses?.joinToString(", ") ?: ""
                    val caption = id + if (readableAddresses.isEmpty()) "" else " ($readableAddresses)"
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickDiscoveredDevice(id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // ---- Name ----
        OutlinedTextField(
            value = nameText,
            onValueChange = { value ->
                nameText = value
                onDeviceMutate { it.name = value }
            },
            label = { Text(stringResource(R.string.device_name)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ---- Addresses ----
        OutlinedTextField(
            value = addressesText,
            onValueChange = { value ->
                addressesText = value
                onDeviceMutate { it.addresses = persistableAddresses(value) }
            },
            label = { Text(stringResource(R.string.addresses)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ---- Compression (expert mode only) ----
        if (prefExpertMode) {
            ClickRow(
                title = stringResource(R.string.compression),
                value = compressionTitle(state.compressionIndex),
                onClick = onCompressionClick
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ---- Toggles ----
        ToggleRow(
            title = stringResource(R.string.introducer),
            checked = device.introducer,
            onCheckedChange = { checked -> onDeviceMutate { it.introducer = checked } }
        )
        ToggleRow(
            title = stringResource(R.string.autoAcceptFolders),
            checked = device.autoAcceptFolders,
            onCheckedChange = { checked -> onDeviceMutate { it.autoAcceptFolders = checked } }
        )
        ToggleRow(
            title = stringResource(R.string.pause_device),
            checked = device.paused,
            onCheckedChange = { checked -> onDeviceMutate { it.paused = checked } }
        )
        ToggleRow(
            title = stringResource(R.string.untrusted_device),
            checked = device.untrusted,
            onCheckedChange = { checked -> onDeviceMutate { it.untrusted = checked } }
        )

        // ---- Custom sync conditions (edit mode only) ----
        if (!isCreate) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ToggleRow(
                title = stringResource(R.string.custom_sync_conditions_title),
                description = stringResource(R.string.custom_sync_conditions_dialog),
                checked = state.customSyncConditions,
                onCheckedChange = onCustomSyncConditionsChange
            )
            if (state.customSyncConditions) {
                ClickRow(
                    title = stringResource(R.string.custom_sync_conditions_dialog),
                    value = stringResource(R.string.custom_sync_conditions_description),
                    onClick = onOpenSyncConditions
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ---- Folders shared with this device ----
        Text(
            text = stringResource(R.string.folders),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (state.folderStates.isEmpty()) {
            Text(
                text = stringResource(R.string.folders_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFolderEdit() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            state.folderStates.forEach { shareState ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = shareState.folder.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFolderShareChange(shareState.folder.id, !shareState.shared) }
                        )
                        androidx.compose.material3.Switch(
                            checked = shareState.shared,
                            onCheckedChange = { checked ->
                                onFolderShareChange(shareState.folder.id, checked)
                            }
                        )
                    }
                    if (shareState.shared) {
                        OutlinedTextField(
                            value = shareState.password,
                            onValueChange = { value ->
                                onFolderPasswordChange(shareState.folder.id, value)
                            },
                            label = { Text(stringResource(R.string.deviceEncryptionPasswordHint)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun displayableAddresses(device: com.nutomic.syncthingandroid.model.Device): String {
    val addresses = device.addresses ?: return ""
    return addresses.joinToString(", ")
}

/**
 * Fault tolerant address list parsing, ported from DeviceActivity.persistableAddresses.
 */
private fun persistableAddresses(userInput: String): List<String> {
    if (userInput.isEmpty()) {
        return listOf("dynamic")
    }
    var input = userInput.replace(",", " ")
    input = input.replace(";", " ")
    input = input.replace("\\s+".toRegex(), ", ")
    return input.split(", ")
}

@Composable
private fun compressionTitle(index: Int): String {
    val entries = androidx.compose.ui.res.stringArrayResource(R.array.compress_entries)
    return entries.getOrElse(index) { "" }
}
