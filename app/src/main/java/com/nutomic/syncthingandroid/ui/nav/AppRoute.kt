package com.nutomic.syncthingandroid.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Routes of the single-activity Compose app, rendered with Navigation 3.
 */
@Serializable
sealed interface AppRoute : NavKey {

    /** Home: folders / devices / status tabs with the navigation drawer. */
    @Serializable
    data object Home : AppRoute

    @Serializable
    data class DeviceEdit(
        val deviceId: String? = null,
        val deviceName: String? = null,
        val isCreate: Boolean = false,
        val notificationId: Int = 0,
    ) : AppRoute

    @Serializable
    data class FolderEdit(
        val folderId: String? = null,
        val folderLabel: String? = null,
        val isCreate: Boolean = false,
        val deviceId: String? = null,
        val receiveEncrypted: Boolean = false,
        val notificationId: Int = 0,
    ) : AppRoute

    /** Built-in file system directory picker. Result is delivered via [LocalResultBus]. */
    @Serializable
    data class FolderPicker(
        val initialDirectory: String? = null,
        val rootDirectory: String? = null,
    ) : AppRoute

    @Serializable
    data class SyncConditions(
        val objectPrefixAndId: String,
        val objectReadableName: String,
    ) : AppRoute

    @Serializable
    data object Log : AppRoute

    @Serializable
    data class WebView(val url: String) : AppRoute
}
