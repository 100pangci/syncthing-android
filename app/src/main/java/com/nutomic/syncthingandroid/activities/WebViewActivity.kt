package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.ui.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.webview.WebViewScreen

/**
 * Generic web view activity host, ported from the legacy WebViewActivity.
 * The UI now lives in [com.nutomic.syncthingandroid.ui.screens.webview.WebViewScreen].
 */
class WebViewActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val webPageUrl = intent.getStringExtra(EXTRA_WEB_URL)
        if (webPageUrl == null) {
            finish()
            return
        }

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    WebViewScreen(
                        webPageUrl = webPageUrl,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_WEB_URL = ".activities.WebViewActivity.WEB_URL"
    }
}
