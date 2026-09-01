package com.nutomic.syncthingandroid.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.share.ShareFilesHelper
import com.nutomic.syncthingandroid.ui.screens.share.ShareScreen

/**
 * Share-into-folder activity host, ported from the legacy ShareActivity.
 * The UI now lives in [com.nutomic.syncthingandroid.ui.screens.share.ShareScreen].
 */
class ShareActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val extrasToCopy = ArrayList<Uri>()
        val action = intent?.action
        if (action != null) {
            if (action == Intent.ACTION_SEND) {
                try {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        extrasToCopy.add(uri)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Intent.ACTION_SEND: Ignored malformed intent.")
                }
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                try {
                    @Suppress("DEPRECATION")
                    val extras = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (extras != null) {
                        extrasToCopy.addAll(extras)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Intent.ACTION_SEND_MULTIPLE: Ignored malformed intent.")
                }
            }
        }

        if (extrasToCopy.isEmpty()) {
            android.widget.Toast.makeText(
                this, getString(R.string.nothing_share), android.widget.Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val files = LinkedHashMap<Uri, String>()
        for (sourceUri in extrasToCopy) {
            val displayName = ShareFilesHelper.getDisplayNameForUri(this, sourceUri)
                ?: ShareFilesHelper.generateDisplayName(this)
            files[sourceUri] = displayName
        }

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    ShareScreen(
                        files = files,
                        onDone = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShareActivity"

        /** Prefix for per-folder saved sub directory preferences. Kept public for RestApi. */
        const val PREF_FOLDER_SAVED_SUBDIRECTORY = "saved_sub_directory_"
    }
}
