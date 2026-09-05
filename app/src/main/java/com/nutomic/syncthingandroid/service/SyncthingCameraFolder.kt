package com.nutomic.syncthingandroid.service

import android.content.Context
import android.os.Environment

import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.util.FileUtils

/**
 * Builds the model for the app-specific "Syncthing Camera" folder, shared by the
 * config.xml path (service startup) and the RestApi path (live enablement).
 * Returns null if the storage directory cannot be determined.
 */
internal fun buildSyncthingCameraFolder(context: Context): Folder? {
    val storageDir = FileUtils.getExternalFilesDir(
        context,
        FileUtils.ExternalStorageDirType.INT_MEDIA,
        Environment.DIRECTORY_PICTURES
    ) ?: return null

    val folder = Folder()
    folder.minDiskFree = Folder.MinDiskFree()
    folder.id = Constants.syncthingCameraFolderId
    folder.label = context.getString(R.string.default_syncthing_camera_folder_label)
    folder.path = storageDir.absolutePath

    val versioning = Folder.Versioning()
    versioning.type = "trashcan"
    versioning.params["cleanoutDays"] = "14"
    versioning.cleanupIntervalS = 3600
    versioning.fsPath = ""
    versioning.fsType = "basic"
    folder.versioning = versioning
    return folder
}
