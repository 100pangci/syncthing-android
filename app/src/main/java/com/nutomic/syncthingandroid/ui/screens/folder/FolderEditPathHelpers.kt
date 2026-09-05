package com.nutomic.syncthingandroid.ui.screens.folder

import android.content.Context
import android.widget.Toast
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.AppPrefs
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.util.Util

/**
 * Path resolution + write test, ported from FolderActivity.checkWriteAndUpdateUI.
 */
internal fun checkWriteAndUpdateUI(context: Context, holder: FolderEditStateHolder) {
    val folder = holder.folder ?: return
    if (folder.path.isNullOrEmpty()) {
        return
    }
    // In root mode the core accesses paths with root privileges, so the write probe must
    // run through the root shell too: the app UID's own EACCES would wrongly reject
    // root-only folders and force the folder to "sendonly".
    val asRoot = AppPrefs.getRunAsRoot(
        (context.applicationContext as SyncthingApp).preferences
    )
    holder.canWriteToPath = Util.nativeBinaryCanWriteToPath(context, folder.path, asRoot)
    if (!holder.canWriteToPath) {
        // Force "sendonly" folder.
        folder.type = Constants.FOLDER_TYPE_SEND_ONLY
    }
}

/**
 * Handles a picked absolute path (from SAF or the built-in folder picker).
 */
internal fun onPickedPath(context: Context, holder: FolderEditStateHolder, rawPath: String?) {
    val folder = holder.folder ?: return
    var targetPath = rawPath?.let { Util.formatPath(it) }
    if (targetPath.isNullOrEmpty() || targetPath == java.io.File.separator) {
        folder.path = ""
        holder.folderUri = null
        checkWriteAndUpdateUI(context, holder)
        Toast.makeText(context, R.string.toast_invalid_folder_selected, Toast.LENGTH_LONG).show()
        return
    }
    folder.path = com.nutomic.syncthingandroid.util.FileUtils.cutTrailingSlash(targetPath) ?: ""
    checkWriteAndUpdateUI(context, holder)
    holder.needsUpdate = true
}

/**
 * Validates whether the folder type dialog may be opened.
 */
internal fun onFolderTypeDialogRequest(
    context: Context,
    holder: FolderEditStateHolder,
    onAllowed: (Boolean) -> Unit,
) {
    val folder = holder.folder ?: return
    if (folder.path.isNullOrEmpty()) {
        Toast.makeText(context, R.string.folder_path_required, Toast.LENGTH_LONG).show()
        return
    }
    if (!holder.canWriteToPath) {
        // Readonly path: only "sendonly" is allowed and the UI already explains this.
        Toast.makeText(context, R.string.folder_path_readonly, Toast.LENGTH_LONG).show()
        return
    }
    onAllowed(true)
}

/**
 * Applies the versioning dialog result, ported from FolderActivity.updateVersioning.
 */
internal fun applyVersioning(folder: Folder, type: String, params: Map<String, String>) {
    if (folder.versioning == null) {
        folder.versioning = Folder.Versioning()
    }
    if (type == "none") {
        val newVersioning = Folder.Versioning()
        newVersioning.type = ""
        folder.versioning = newVersioning
    } else {
        val versioning = folder.versioning ?: Folder.Versioning().also { folder.versioning = it }
        versioning.params.clear()
        versioning.params.putAll(params)
        versioning.type = type
    }
}
