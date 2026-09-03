package com.nutomic.syncthingandroid.ui.screens.folder

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nutomic.syncthingandroid.model.Folder

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

    /**
     * Bumped whenever the folder model is changed from a dialog (type, pull
     * order, versioning). Sections reading the Java model directly are keyed
     * on this so they recompose reliably.
     */
    var configVersion by mutableStateOf(0)
}

@Composable
internal fun rememberFolderEditStateHolder(): FolderEditStateHolder {
    return androidx.compose.runtime.remember { FolderEditStateHolder() }
}
