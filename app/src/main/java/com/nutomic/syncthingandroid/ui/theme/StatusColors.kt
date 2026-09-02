package com.nutomic.syncthingandroid.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Maps the legacy fixed status colors (text_red/text_blue/...) onto
 * Material 3 color scheme roles so status text follows the app theme.
 */
enum class StatusKind { ERROR, SYNCING, OK, WARNING, PAUSED }

/**
 * Fixed colors for check/validation results that have no MaterialTheme role
 * equivalent (e.g. the custom certificate check rows).
 */
val statusSuccess = Color(0xFF2E7D32)
val statusWarning = Color(0xFFF9A825)

/** Alpha of disabled content (icons, labels) not covered by an MD3 role. */
const val DISABLED_ALPHA = 0.38f

/** Container alpha of the [StatusBadge] pill. */
const val STATUS_BADGE_CONTAINER_ALPHA = 0.14f

@Composable
fun statusColor(kind: StatusKind): Color = when (kind) {
    StatusKind.ERROR -> MaterialTheme.colorScheme.error
    StatusKind.SYNCING -> MaterialTheme.colorScheme.primary
    StatusKind.OK -> MaterialTheme.colorScheme.tertiary
    StatusKind.WARNING -> MaterialTheme.colorScheme.secondary
    StatusKind.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Pill shaped status badge (MD3 tonal chip look): status color at low alpha
 * as container, status color as content color.
 */
@Composable
fun StatusBadge(
    text: String,
    kind: StatusKind,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(kind)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = STATUS_BADGE_CONTAINER_ALPHA),
        contentColor = color,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
