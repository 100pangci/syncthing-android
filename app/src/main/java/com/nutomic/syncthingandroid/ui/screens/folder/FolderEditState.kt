package com.nutomic.syncthingandroid.ui.screens.folder

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Holds the unsaved draft state of [FolderEditScreen] OUTSIDE the Navigation 3
 * entry composition, keyed by [folderEditStateKey].
 *
 * Why this exists: Navigation 3 (1.1.3) only composes the top entry and
 * disposes entries covered by another route. The draft used to live in plain
 * `remember {}` inside the entry, so pushing the SyncConditions route (or the
 * folder picker) destroyed every unsaved edit - the paused toggle, the custom
 * sync conditions switch, shared devices, the ignore list - and the screen was
 * rebuilt from the saved config on return. That made per-folder custom sync
 * conditions impossible to enable: the switch flipped back before the user
 * could ever press save, and RunConditionMonitor only honours the conditions
 * when that persisted flag is set.
 *
 * Lifecycle: a holder lives exactly as long as its FolderEdit route is on the
 * back stack. The host activity (MainActivity) watches the stack and calls
 * [retainAll], so saving, discarding or deleting the folder always evicts the
 * draft and the next open starts fresh - without the stale-draft resurrection
 * that a plain rememberSaveable would risk (NavDisplay never clears its
 * SaveableStateHolder on pop).
 */
internal class FolderEditStateStore {
    private val holders = mutableMapOf<String, FolderEditStateHolder>()

    fun holderFor(key: String): FolderEditStateHolder =
        holders.getOrPut(key) { FolderEditStateHolder() }

    /** Evicts every holder whose route is no longer on the back stack. */
    fun retainAll(keys: Set<String>) {
        holders.keys.retainAll(keys)
    }
}

/**
 * Stable identity of a folder edit session. Deliberately independent of the
 * share/notification extras: re-entering the same folder's editor (from home,
 * or via a new share notification) continues the same draft while the route is
 * still on the stack, and starts fresh once it has been evicted.
 */
internal fun folderEditStateKey(folderId: String?, isCreate: Boolean): String =
    if (isCreate || folderId == null) "create" else "edit:$folderId"

internal val LocalFolderEditStateStore = staticCompositionLocalOf<FolderEditStateStore> {
    error("FolderEditStateStore not provided")
}
