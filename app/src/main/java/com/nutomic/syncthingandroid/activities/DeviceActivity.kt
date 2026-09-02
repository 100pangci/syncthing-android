package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.ui.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.device.DeviceEditScreen

/**
 * Deep link host for the "device wants to connect" notification.
 * Ported from the legacy View based DeviceActivity; the UI now lives in
 * [com.nutomic.syncthingandroid.ui.screens.device.DeviceEditScreen].
 */
class DeviceActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val isCreate = intent.getBooleanExtra(EXTRA_IS_CREATE, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    DeviceEditScreen(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        isCreate = isCreate,
                        notificationId = notificationId,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = ".activities.DeviceActivity.NOTIFICATION_ID"
        const val EXTRA_DEVICE_ID = ".activities.DeviceActivity.DEVICE_ID"
        const val EXTRA_DEVICE_NAME = ".activities.DeviceActivity.DEVICE_NAME"
        const val EXTRA_IS_CREATE = ".activities.DeviceActivity.IS_CREATE"
    }
}
