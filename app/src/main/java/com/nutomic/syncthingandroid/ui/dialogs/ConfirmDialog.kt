package com.nutomic.syncthingandroid.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R

/**
 * Simple confirmation dialog with OK/No buttons (mirrors the legacy AlertDialog usages).
 */
@Composable
fun ConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (title != null) ({ Text(title) }) else null,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
 * Compression selection dialog (single choice), ported from DeviceActivity's dialog.
 */
@Composable
fun CompressionDialog(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.compression),
        entries = stringArrayResource(R.array.compress_entries).toList(),
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        onDismiss = onDismiss,
        selectImmediately = true,
    )
}
