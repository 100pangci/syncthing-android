package com.nutomic.syncthingandroid.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nutomic.syncthingandroid.activities.DeviceActivity
import com.nutomic.syncthingandroid.activities.FolderActivity
import com.nutomic.syncthingandroid.activities.FolderPickerActivity
import com.nutomic.syncthingandroid.activities.LogActivity
import com.nutomic.syncthingandroid.activities.SyncthingActivity
import com.nutomic.syncthingandroid.activities.SyncConditionsActivity
import com.nutomic.syncthingandroid.activities.WebViewActivity
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.nav.AppRoute
import com.nutomic.syncthingandroid.ui.nav.IntentAppNavigator
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.ui.nav.LocalResultBus
import com.nutomic.syncthingandroid.ui.nav.ResultBus

/**
 * Provides the composition locals (service, service state, navigator, result bus)
 * for screens hosted inside a standalone activity (deep link entry points).
 */
@Composable
fun CompositionLocalsHost(
    activity: SyncthingActivity,
    resultBus: ResultBus,
    content: @Composable () -> Unit,
) {
    var serviceState by remember {
        mutableStateOf(activity.getService()?.getCurrentState() ?: SyncthingService.State.INIT)
    }

    DisposableEffect(activity) {
        val listener = SyncthingService.OnServiceStateChangeListener { currentState ->
            serviceState = currentState ?: SyncthingService.State.INIT
        }
        activity.getService()?.registerOnServiceStateChangeListener(listener)
        onDispose {
            activity.getService()?.unregisterOnServiceStateChangeListener(listener)
        }
    }

    val navigator = remember(activity) {
        object : IntentAppNavigator(activity) {
            override fun navigateTo(route: AppRoute) {
                when (route) {
                    is AppRoute.DeviceEdit -> activity.startActivity(
                        Intent(activity, DeviceActivity::class.java).apply {
                            putExtra(DeviceActivity.EXTRA_IS_CREATE, route.isCreate)
                            putExtra(DeviceActivity.EXTRA_DEVICE_ID, route.deviceId)
                            putExtra(DeviceActivity.EXTRA_DEVICE_NAME, route.deviceName)
                            putExtra(DeviceActivity.EXTRA_NOTIFICATION_ID, route.notificationId)
                        }
                    )
                    is AppRoute.FolderEdit -> activity.startActivity(
                        Intent(activity, FolderActivity::class.java).apply {
                            putExtra(FolderActivity.EXTRA_IS_CREATE, route.isCreate)
                            putExtra(FolderActivity.EXTRA_FOLDER_ID, route.folderId)
                            putExtra(FolderActivity.EXTRA_FOLDER_LABEL, route.folderLabel)
                            putExtra(FolderActivity.EXTRA_DEVICE_ID, route.deviceId)
                            putExtra(FolderActivity.EXTRA_RECEIVE_ENCRYPTED, route.receiveEncrypted)
                            putExtra(FolderActivity.EXTRA_NOTIFICATION_ID, route.notificationId)
                        }
                    )
                    is AppRoute.FolderPicker -> activity.startActivity(
                        FolderPickerActivity.createIntent(activity, route.initialDirectory, route.rootDirectory)
                    )
                    is AppRoute.SyncConditions -> activity.startActivity(
                        SyncConditionsActivity.createIntent(activity, route.objectPrefixAndId, route.objectReadableName)
                    )
                    is AppRoute.Log -> activity.startActivity(Intent(activity, LogActivity::class.java))
                    is AppRoute.WebView -> activity.startActivity(
                        Intent(activity, WebViewActivity::class.java).apply {
                            putExtra(WebViewActivity.EXTRA_WEB_URL, route.url)
                        }
                    )
                    else -> {}
                }
            }

            override fun navigateBack() {
                activity.finish()
            }

            override fun showDeviceIdDialog() {
                // Not reachable in standalone activity hosts: only the home screen,
                // which is hosted inside MainActivity, can trigger this.
            }

            override fun confirmRestart() {
                // Not reachable in standalone activity hosts.
            }
        }
    }

    CompositionLocalProvider(
        LocalSyncthingService provides activity.getService(),
        LocalServiceState provides serviceState,
        LocalAppNavigator provides navigator,
        LocalResultBus provides resultBus,
        content = content,
    )
}
