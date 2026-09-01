package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Connection
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.util.ConfigRouter
import com.nutomic.syncthingandroid.util.Util
import com.nutomic.syncthingandroid.ui.appPreferences

private const val ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD = 50L * 1024 * 8
private const val TIMESTAMP_NEVER_SEEN = "1970-01-01T00:00:00Z"

/**
 * One device list item, ported from the legacy DevicesAdapter (View based).
 */
@Composable
fun DeviceRow(
    device: Device,
    configRouter: ConfigRouter,
    restApi: RestApi?,
    apiConfigLoaded: Boolean,
) {
    val context = LocalContext.current
    val preferences = context.appPreferences()

    val deviceLastSeen = preferences.getString(
        Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + device.deviceID, ""
    ) ?: ""
    val lastSeenText = stringResource(
        R.string.device_last_seen,
        if (deviceLastSeen.isEmpty() || deviceLastSeen == TIMESTAMP_NEVER_SEEN)
            stringResource(R.string.device_last_seen_never)
        else
            Util.formatDateTime(deviceLastSeen)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = lastSeenText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val sharedFolders = configRouter.getSharedFolders(device.deviceID)
        if (sharedFolders.isEmpty()) {
            Text(
                text = stringResource(R.string.device_state_unused),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.shared_folders_title_colon),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "\u2022 " + sharedFolders.joinToString("\n\u2022 ") { it.toString() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var statusText = ""
        var statusColor = MaterialTheme.colorScheme.onSurface
        var showProgressBar = false
        var progress = 1f
        var showRateInOut = false

        if (device.paused) {
            statusText = stringResource(R.string.device_paused)
            statusColor = colorResource(R.color.text_purple)
        } else if (restApi == null || !apiConfigLoaded) {
            statusText = stringResource(R.string.device_disconnected)
            statusColor = colorResource(R.color.text_red)
        } else {
            val conn: Connection = restApi.getRemoteDeviceStatus(device.deviceID)
            val completion = restApi.getRemoteDeviceCompletion(device.deviceID)
            val needBytes = restApi.getRemoteDeviceNeedBytes(device.deviceID)

            if (conn.connected) {
                val bandwidthUpDownText = buildString {
                    append("\u21f5 ")
                    append(stringResource(R.string.download_title))
                    append(" \u02c5 ")
                    append(Util.readableTransferRate(context, conn.inBits))
                    append(" \u2022 ")
                    append(stringResource(R.string.upload_title))
                    append(" \u02c4 ")
                    append(Util.readableTransferRate(context, conn.outBits))
                }
                showRateInOut = true
                val syncingState = completion != 100
                showProgressBar = syncingState
                if (!syncingState) {
                    if ((conn.inBits + conn.outBits) >= ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD) {
                        statusText = stringResource(R.string.state_syncing_general)
                        statusColor = colorResource(R.color.text_blue)
                    } else {
                        statusText = stringResource(R.string.device_up_to_date)
                        statusColor = colorResource(R.color.text_green)
                    }
                } else {
                    progress = completion / 100f
                    statusText = stringResource(
                        R.string.device_syncing_percent_bytes,
                        completion,
                        Util.readableFileSize(context, needBytes)
                    )
                    statusColor = colorResource(R.color.text_blue)
                }
                Text(
                    text = bandwidthUpDownText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                statusText =
                    if (needBytes == 0.0)
                        stringResource(R.string.device_disconnected)
                    else
                        stringResource(
                            R.string.device_disconnected_not_synced,
                            Util.readableFileSize(context, needBytes)
                        )
                statusColor = colorResource(R.color.text_red)
            }
        }

        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor
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
}
