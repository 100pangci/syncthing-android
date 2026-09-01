package com.nutomic.syncthingandroid.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.syncconditions.SyncConditionsScreen

/**
 * Custom sync conditions activity host, ported from the legacy SyncConditionsActivity.
 * The UI now lives in
 * [com.nutomic.syncthingandroid.ui.screens.syncconditions.SyncConditionsScreen].
 */
class SyncConditionsActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val objectPrefixAndId = intent.getStringExtra(EXTRA_OBJECT_PREFIX_AND_ID)
        val objectReadableName = intent.getStringExtra(EXTRA_OBJECT_READABLE_NAME) ?: ""
        if (objectPrefixAndId == null) {
            finish()
            return
        }

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    SyncConditionsScreen(
                        objectPrefixAndId = objectPrefixAndId,
                        objectReadableName = objectReadableName,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val DIRECTORY_REQUEST_CODE = 235
        private const val EXTRA_OBJECT_PREFIX_AND_ID = ".activities.SyncConditionsActivity.OBJECT_PREFIX_AND_ID"
        private const val EXTRA_OBJECT_READABLE_NAME = ".activities.SyncConditionsActivity.OBJECT_READABLE_NAME"

        fun createIntent(
            context: Context,
            objectPrefixAndId: String,
            objectReadableName: String,
        ): Intent {
            val intent = Intent(context, SyncConditionsActivity::class.java)
            intent.putExtra(EXTRA_OBJECT_PREFIX_AND_ID, objectPrefixAndId)
            intent.putExtra(EXTRA_OBJECT_READABLE_NAME, objectReadableName)
            return intent
        }
    }
}
