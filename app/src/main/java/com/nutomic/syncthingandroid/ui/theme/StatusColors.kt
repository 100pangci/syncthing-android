package com.nutomic.syncthingandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Maps the legacy fixed status colors (text_red/text_blue/...) onto
 * Material 3 color scheme roles so status text follows the app theme.
 */
enum class StatusKind { ERROR, SYNCING, OK, WARNING, PAUSED }

@Composable
fun statusColor(kind: StatusKind): Color = when (kind) {
    StatusKind.ERROR -> MaterialTheme.colorScheme.error
    StatusKind.SYNCING -> MaterialTheme.colorScheme.primary
    StatusKind.OK -> MaterialTheme.colorScheme.tertiary
    StatusKind.WARNING -> MaterialTheme.colorScheme.secondary
    StatusKind.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
}
