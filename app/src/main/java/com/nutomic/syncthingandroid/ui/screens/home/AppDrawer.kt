package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.ui.theme.DISABLED_ALPHA
import com.nutomic.syncthingandroid.util.isTelevision

/**
 * Drawer content of the home screen, ported from the legacy DrawerFragment.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    stServiceRunning: Boolean,
    onShowDeviceId: () -> Unit,
    onRecentChanges: () -> Unit,
    onWebGui: () -> Unit,
    onBackup: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            DrawerHeader()

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            LazyColumn(
                state = rememberLazyListState(prefetchStrategy = NoLazyListPrefetch),
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f)
            ) {
                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.QrCode2, null) },
                        label = { Text(stringResource(R.string.show_device_id)) },
                        onClick = onShowDeviceId,
                    )
                }
                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.Restore, null) },
                        label = { Text(stringResource(R.string.recent_changes_title)) },
                        onClick = onRecentChanges,
                        enabled = stServiceRunning,
                    )
                }

                if (Constants.isDebuggable(context) || !config.isTelevision) {
                    item {
                        DrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Outlined.ViewQuilt, null) },
                            label = { Text(stringResource(R.string.web_gui_title)) },
                            onClick = onWebGui,
                            enabled = stServiceRunning,
                        )
                    }
                }

                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.ImportExport, null) },
                        label = { Text(stringResource(R.string.category_backup)) },
                        onClick = onBackup,
                    )
                }
                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.Autorenew, null) },
                        label = { Text(stringResource(R.string.restart)) },
                        onClick = onRestart,
                        enabled = stServiceRunning,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                DrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text(stringResource(R.string.settings_title)) },
                    onClick = onSettings,
                )
                var showAlert by remember { mutableStateOf(false) }
                DrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, null) },
                    label = { Text(stringResource(R.string.exit)) },
                    onClick = {
                        val isAutostartOn = context.appPreferences()
                            .getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
                        if (isAutostartOn) {
                            showAlert = true
                        } else {
                            onExit()
                        }
                    },
                )
                if (showAlert) {
                    AlertDialog(
                        onDismissRequest = { showAlert = false },
                        title = { Text(stringResource(R.string.dialog_exit_while_running_as_service_title)) },
                        text = { Text(stringResource(R.string.dialog_exit_while_running_as_service_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showAlert = false
                                onExit()
                            }) {
                                Text(stringResource(R.string.yes))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAlert = false }) {
                                Text(stringResource(R.string.no))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled) {
        NavigationDrawerItem(
            icon = icon,
            label = label,
            onClick = onClick,
            selected = false,
            modifier = modifier
        )
    } else {
        val color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        Surface(
            color = Color.Transparent,
            modifier = modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 24.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides color, content = icon)
                Spacer(Modifier.width(12.dp))
                CompositionLocalProvider(LocalContentColor provides color, content = label)
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 24.dp, bottom = 16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_monochrome_ui),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
