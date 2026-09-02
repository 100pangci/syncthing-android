package com.nutomic.syncthingandroid.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

/**
 * Generic single choice (radio list) dialog shared by the compression,
 * folder type and pull order dialogs.
 *
 * By default the selection is only reported when the OK button is pressed.
 * With [selectImmediately] every tap reports the selection right away and
 * only a cancel button is shown.
 */
@Composable
fun SingleChoiceDialog(
    title: String,
    entries: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    selectImmediately: Boolean = false,
) {
    var selected by remember(selectedIndex) { mutableStateOf(selectedIndex) }
    val currentSelection = if (selectImmediately) selectedIndex else selected
    val onPick: (Int) -> Unit = { index ->
        if (selectImmediately) onSelect(index) else selected = index
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = index == currentSelection,
                            onClick = { onPick(index) }
                        )
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectImmediately) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            } else {
                TextButton(onClick = { onSelect(selected) }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = if (selectImmediately) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}
