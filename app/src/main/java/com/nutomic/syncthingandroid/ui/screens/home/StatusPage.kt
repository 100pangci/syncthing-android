package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.common.base.Optional
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Connection
import com.nutomic.syncthingandroid.model.SystemStatus
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.util.Util
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Status tab: why syncthing is (not) running plus system statistics.
 * Ported from the legacy StatusFragment and SegmentedButton.
 */
@Composable
fun StatusPage(
    serviceState: SyncthingService.State,
) {
    val context = LocalContext.current
    val service = LocalSyncthingService.current
    val api = service?.getApi()
    val preferences = context.appPreferences()

    var forceStartStopState by remember {
        mutableStateOf(
            preferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP)
        )
    }

    var ramUsage by remember { mutableStateOf("") }
    var download by remember { mutableStateOf("") }
    var upload by remember { mutableStateOf("") }
    var announceServer by remember { mutableStateOf("") }
    var uptime by remember { mutableStateOf("") }
    var totalSyncCompletion by remember { mutableStateOf(-1) }

    LaunchedEffect(serviceState) {
        while (isActive) {
            if (serviceState == SyncthingService.State.ACTIVE && api != null && api.isConfigLoaded()) {
                api.getRemoteDeviceStatus("")
                totalSyncCompletion = api.getTotalSyncCompletion()
                api.getSystemStatus { systemStatus ->
                    val announceTotal = systemStatus.discoveryMethods
                    val announceConnected =
                        announceTotal - Optional.fromNullable(systemStatus.discoveryErrors).transform { it.size }.or(0)
                    ramUsage = Util.readableFileSize(context, systemStatus.sys.toDouble())
                    announceServer =
                        if (announceTotal == 0) ""
                        else String.format(Locale.getDefault(), "%1\$d/%2\$d", announceConnected, announceTotal)

                    val uptimeDays = TimeUnit.SECONDS.toDays(systemStatus.uptime)
                    val uptimeHours = TimeUnit.SECONDS.toHours(systemStatus.uptime) - TimeUnit.DAYS.toHours(uptimeDays)
                    val uptimeMinutes = TimeUnit.SECONDS.toMinutes(systemStatus.uptime) -
                            TimeUnit.HOURS.toMinutes(uptimeHours) - TimeUnit.DAYS.toMinutes(uptimeDays)
                    uptime = when {
                        uptimeDays > 0 -> String.format(Locale.getDefault(), "%dd %02dh %02dm", uptimeDays, uptimeHours, uptimeMinutes)
                        uptimeHours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", uptimeHours, uptimeMinutes)
                        else -> String.format(Locale.getDefault(), "%dm", uptimeMinutes)
                    }

                    val total: Connection =
                        if (api.isConfigLoaded()) api.getTotalConnectionStatistic() else Connection()
                    // "Hide" rates below 1 KiB/s to avoid bothering the user with idle traffic.
                    download = (if (total.inBits / 8 < 1024) "0 B/s" else Util.readableTransferRate(context, total.inBits)) +
                            " (" + Util.readableFileSize(context, total.inBytesTotal.toDouble()) + ")"
                    upload = (if (total.outBits / 8 < 1024) "0 B/s" else Util.readableTransferRate(context, total.outBits)) +
                            " (" + Util.readableFileSize(context, total.outBytesTotal.toDouble()) + ")"
                }
            }
            delay(Constants.REST_UPDATE_INTERVAL)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overall sync progress card (moved here from the legacy top bar strip).
        if (serviceState == SyncthingService.State.ACTIVE && totalSyncCompletion != -1) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.state_syncing, totalSyncCompletion),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { totalSyncCompletion / 100f },
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        val statusItems = buildList {
            when (serviceState) {
                SyncthingService.State.INIT, SyncthingService.State.STARTING ->
                    add(stringResource(R.string.syncthing_starting))
                SyncthingService.State.ACTIVE ->
                    add(stringResource(R.string.syncthing_running))
                SyncthingService.State.DISABLED ->
                    add(stringResource(R.string.syncthing_not_running))
                SyncthingService.State.ERROR ->
                    add(stringResource(R.string.syncthing_has_crashed))
            }
            if (serviceState == SyncthingService.State.ACTIVE || serviceState == SyncthingService.State.DISABLED) {
                val explanation = service?.runDecisionExplanation?.trim()?.replace("\n", "\n- ") ?: ""
                add(stringResource(R.string.reason) + "\n- " + explanation)
            }
            if (serviceState == SyncthingService.State.ACTIVE) {
                if (uptime.isNotEmpty()) add(stringResource(R.string.uptime) + ": " + uptime)
                if (ramUsage.isNotEmpty()) add(stringResource(R.string.ram_usage) + ": " + ramUsage)
                if (download.isNotEmpty()) add(stringResource(R.string.download_title) + ": " + download)
                if (upload.isNotEmpty()) add(stringResource(R.string.upload_title) + ": " + upload)
                if (announceServer.isNotEmpty()) add(stringResource(R.string.announce_server) + ": " + announceServer)
            }
        }

        statusItems.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Run decision picker: segmented buttons, equally weighted so the three
        // segments stay aligned on every screen width and locale.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val labels = listOf(
                stringResource(R.string.button_follow_run_conditions),
                stringResource(R.string.button_force_start),
                stringResource(R.string.button_force_stop)
            )
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = forceStartStopState == index,
                    onClick = {
                        forceStartStopState = index
                        preferences.edit()
                            .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, index)
                            .apply()
                        // Notify RunConditionMonitor that the decision changed.
                        androidx.localbroadcastmanager.content.LocalBroadcastManager
                            .getInstance(context)
                            .sendBroadcast(
                                android.content.Intent(
                                    com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION
                                )
                            )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
