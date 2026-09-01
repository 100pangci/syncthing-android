package com.nutomic.syncthingandroid.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants

/**
 * Folder type selection dialog, ported from the legacy FolderTypeDialogActivity.
 */
@Composable
fun FolderTypeDialog(
    currentType: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val types = listOf(
        Constants.FOLDER_TYPE_SEND_RECEIVE to R.string.folder_type_sendreceive,
        Constants.FOLDER_TYPE_SEND_ONLY to R.string.folder_type_sendonly,
        Constants.FOLDER_TYPE_RECEIVE_ONLY to R.string.folder_type_receiveonly,
        Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED to R.string.folder_type_receive_encrypted,
    )
    var selected by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_type)) },
        text = {
            Column {
                types.forEach { (type, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = type }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == type,
                            onClick = { selected = type }
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * File pull order selection dialog, ported from the legacy PullOrderDialogActivity.
 */
@Composable
fun PullOrderDialog(
    currentOrder: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val orders = listOf(
        "random" to R.string.pull_order_type_random,
        "alphabetic" to R.string.pull_order_type_alphabetic,
        "smallestFirst" to R.string.pull_order_type_smallestFirst,
        "largestFirst" to R.string.pull_order_type_largestFirst,
        "oldestFirst" to R.string.pull_order_type_oldestFirst,
        "newestFirst" to R.string.pull_order_type_newestFirst,
    )
    var selected by remember { mutableStateOf(currentOrder ?: "random") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pull_order)) },
        text = {
            Column {
                orders.forEach { (order, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = order }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == order,
                            onClick = { selected = order }
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * File versioning dialog, ported from VersioningDialogActivity + the versioning fragments.
 */
@Composable
fun VersioningDialog(
    initialType: String,
    initialParams: Map<String, String>,
    onApply: (type: String, params: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Params(val keep: String, val cleanoutDays: String, val maxAge: String, val versionsPath: String, val command: String)

    var selectedType by remember { mutableStateOf(initialType.ifEmpty { "none" }) }
    var keep by remember { mutableStateOf(initialParams["keep"] ?: "5") }
    var cleanoutDays by remember { mutableStateOf(initialParams["cleanoutDays"] ?: "0") }
    // maxAge is stored in seconds (Syncthing expects seconds) but displayed in days.
    var maxAgeDays by remember {
        mutableStateOf(
            try {
                java.util.concurrent.TimeUnit.SECONDS
                    .toDays(initialParams["maxAge"]?.toLongOrNull() ?: 0L).toString()
            } catch (e: NumberFormatException) {
                "0"
            }
        )
    }
    var versionsPath by remember { mutableStateOf(initialParams["versionsPath"] ?: ".stversions") }
    var command by remember { mutableStateOf(initialParams["command"] ?: "") }

    val types = listOf("none", "trashcan", "simple", "staggered", "external")
    val typeLabels = mapOf(
        "none" to R.string.none,
        "trashcan" to R.string.type_trashcan,
        "simple" to R.string.type_simple,
        "staggered" to R.string.type_staggered,
        "external" to R.string.type_external,
    )

    fun clampNumber(value: String, min: Int, max: Int): String {
        val number = value.filter { it.isDigit() }.take(9).toLongOrNull() ?: min.toLong()
        return number.coerceIn(min.toLong(), max.toLong()).toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.file_versioning)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                types.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Text(
                            text = stringResource(typeLabels[type]!!),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                when (selectedType) {
                    "trashcan" -> {
                        NumberParamField(
                            label = stringResource(R.string.trashcan_versioning_info, cleanoutDays),
                            value = cleanoutDays,
                            onValueChange = { cleanoutDays = clampNumber(it, 0, 100) }
                        )
                    }
                    "simple" -> {
                        NumberParamField(
                            label = stringResource(R.string.keep_versions),
                            value = keep,
                            onValueChange = { keep = clampNumber(it, 1, 100000) }
                        )
                        NumberParamField(
                            label = stringResource(R.string.clean_out_after),
                            value = cleanoutDays,
                            onValueChange = { cleanoutDays = clampNumber(it, 0, 100) }
                        )
                    }
                    "staggered" -> {
                        NumberParamField(
                            label = stringResource(R.string.maximum_age),
                            value = maxAgeDays,
                            onValueChange = { maxAgeDays = clampNumber(it, 0, 100) }
                        )
                        OutlinedTextField(
                            value = versionsPath,
                            onValueChange = { versionsPath = it },
                            label = { Text(stringResource(R.string.versions_path)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                    "external" -> {
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            label = { Text(stringResource(R.string.command)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val params = mutableMapOf<String, String>()
                when (selectedType) {
                    "trashcan" -> params["cleanoutDays"] = cleanoutDays
                    "simple" -> {
                        params["keep"] = keep
                        params["cleanoutDays"] = cleanoutDays
                    }
                    "staggered" -> {
                        params["maxAge"] =
                            java.util.concurrent.TimeUnit.DAYS
                                .toSeconds(maxAgeDays.toLongOrNull() ?: 0L).toString()
                        params["versionsPath"] = versionsPath
                    }
                    "external" -> params["command"] = command
                }
                onApply(selectedType, params)
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun NumberParamField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}
