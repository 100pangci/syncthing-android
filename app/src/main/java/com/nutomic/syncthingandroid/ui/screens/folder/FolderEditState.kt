package com.nutomic.syncthingandroid.ui.screens.folder

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.nutomic.syncthingandroid.model.Folder

/**
 * Gson backed saver so the editable Folder survives process death and rotation,
 * mirroring the legacy activity's onSaveInstanceState handling.
 */
internal val FolderSaver: Saver<Folder?, String> = Saver(
    save = { Gson().toJson(it) },
    restore = { Gson().fromJson(it, Folder::class.java) }
)

internal val UriSaver: Saver<Uri?, String> = Saver(
    save = { it?.toString() ?: "" },
    restore = { if (it.isNullOrEmpty()) null else Uri.parse(it) }
)

/**
 * Remembers the folder model, the SAF uri and the dirty flags.
 */
internal class FolderEditStateHolder {
    var folder by mutableStateOf<Folder?>(null)
    var folderUri by mutableStateOf<Uri?>(null)
    var needsUpdate by mutableStateOf(false)
    var ignoreListNeedsUpdate by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var canWriteToPath by mutableStateOf(false)
    var ignoreListText by mutableStateOf("")
    var deviceStates by mutableStateOf<List<DeviceShareState>>(emptyList())
    var customSyncConditions by mutableStateOf(false)
    var runScript by mutableStateOf(false)
}

@Composable
internal fun rememberFolderEditStateHolder(): FolderEditStateHolder {
    return androidx.compose.runtime.remember { FolderEditStateHolder() }
}
