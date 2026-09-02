package com.nutomic.syncthingandroid.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

val LocalResultBus = staticCompositionLocalOf<ResultBus> {
    error("ResultBus not provided")
}

/**
 * Lightweight bus to hand results from a pushed route (e.g. [AppRoute.FolderPicker])
 * back to the route that opened it.
 */
class ResultBus {

    /** Absolute path picked by the folder picker, null when cancelled. */
    val folderPickerResult = MutableStateFlow<String?>(null)

    /** Non-null device id while the device id QR dialog should be shown. */
    val showDeviceIdDialog = MutableStateFlow<String?>(null)

    /** Non-null usage report text while the usage reporting dialog should be shown. */
    val usageReport = MutableStateFlow<String?>(null)

    /** Set to true while the restart Syncthing confirmation dialog should be shown. */
    val restartRequested = MutableStateFlow(false)

    fun reset() {
        folderPickerResult.value = null
        showDeviceIdDialog.value = null
        usageReport.value = null
        restartRequested.value = false
    }
}
