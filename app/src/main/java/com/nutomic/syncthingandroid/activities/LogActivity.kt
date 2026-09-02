package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.ui.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.log.LogScreen

/**
 * Log viewer activity host, ported from the legacy LogActivity.
 * The UI now lives in [com.nutomic.syncthingandroid.ui.screens.log.LogScreen].
 */
class LogActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    LogScreen(onBack = { finish() })
                }
            }
        }
    }
}
