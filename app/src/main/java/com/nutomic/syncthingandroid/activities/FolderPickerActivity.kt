package com.nutomic.syncthingandroid.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.ui.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.folderpicker.FolderPickerScreen

/**
 * Directory picker activity host, ported from the legacy FolderPickerActivity.
 * Used with the activity result API; the UI now lives in
 * [com.nutomic.syncthingandroid.ui.screens.folderpicker.FolderPickerScreen].
 */
class FolderPickerActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialDirectory = intent.getStringExtra(EXTRA_INITIAL_DIRECTORY)
        val rootDirectory = intent.getStringExtra(EXTRA_ROOT_DIRECTORY)

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    FolderPickerScreen(
                        initialDirectory = initialDirectory,
                        rootDirectory = rootDirectory,
                        onResult = { path ->
                            if (path != null) {
                                val intent = Intent().putExtra(EXTRA_RESULT_DIRECTORY, path)
                                setResult(RESULT_OK, intent)
                            } else {
                                setResult(RESULT_CANCELED)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_DIRECTORY = ".activities.FolderPickerActivity.RESULT_DIRECTORY"
        private const val EXTRA_INITIAL_DIRECTORY = ".activities.FolderPickerActivity.INITIAL_DIRECTORY"
        private const val EXTRA_ROOT_DIRECTORY = ".activities.FolderPickerActivity.ROOT_DIRECTORY"

        fun createIntent(
            context: Context,
            initialDirectory: String?,
            rootDirectory: String?,
        ): Intent {
            val intent = Intent(context, FolderPickerActivity::class.java)
            if (!initialDirectory.isNullOrEmpty()) {
                intent.putExtra(EXTRA_INITIAL_DIRECTORY, initialDirectory)
            }
            if (!rootDirectory.isNullOrEmpty()) {
                intent.putExtra(EXTRA_ROOT_DIRECTORY, rootDirectory)
            }
            return intent
        }
    }
}
