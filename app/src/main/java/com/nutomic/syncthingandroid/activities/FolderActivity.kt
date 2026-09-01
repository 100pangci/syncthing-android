package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.CompositionLocalsHost
import com.nutomic.syncthingandroid.ui.nav.ResultBus
import com.nutomic.syncthingandroid.ui.screens.folder.FolderEditScreen

/**
 * Deep link host for the "folder shared by device" notification.
 * Ported from the legacy View based FolderActivity; the UI now lives in
 * [com.nutomic.syncthingandroid.ui.screens.folder.FolderEditScreen].
 */
class FolderActivity : SyncthingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val folderId = intent.getStringExtra(EXTRA_FOLDER_ID)
        val folderLabel = intent.getStringExtra(EXTRA_FOLDER_LABEL)
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val isCreate = intent.getBooleanExtra(EXTRA_IS_CREATE, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val receiveEncrypted = intent.getBooleanExtra(EXTRA_RECEIVE_ENCRYPTED, false)

        val resultBus = ResultBus()
        setContent {
            ApplicationTheme {
                CompositionLocalsHost(activity = this, resultBus = resultBus) {
                    FolderEditScreen(
                        folderId = folderId,
                        folderLabel = folderLabel,
                        isCreate = isCreate,
                        deviceId = deviceId,
                        receiveEncrypted = receiveEncrypted,
                        notificationId = notificationId,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = ".activities.FolderActivity.DEVICE_ID"
        const val EXTRA_FOLDER_ID = ".activities.FolderActivity.FOLDER_ID"
        const val EXTRA_FOLDER_LABEL = ".activities.FolderActivity.FOLDER_LABEL"
        const val EXTRA_IS_CREATE = ".activities.FolderActivity.IS_CREATE"
        const val EXTRA_NOTIFICATION_ID = ".activities.FolderActivity.NOTIFICATION_ID"
        const val EXTRA_RECEIVE_ENCRYPTED = ".activities.FolderActivity.RECEIVE_ENCRYPTED"
        const val EXTRA_REMOTE_ENCRYPTED = ".activities.FolderActivity.REMOTE_ENCRYPTED"
        const val FOLDER_ADD_CODE = 402
    }
}
