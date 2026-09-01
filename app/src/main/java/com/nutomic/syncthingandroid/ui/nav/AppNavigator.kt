package com.nutomic.syncthingandroid.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * High level navigation callbacks provided to all screens. Implemented by MainActivity.
 */
interface AppNavigator {
    /** Push a route onto the navigation 3 back stack. */
    fun navigateTo(route: AppRoute)

    /** Pop the current route. */
    fun navigateBack()

    fun openDeviceEdit(deviceId: String?, isCreate: Boolean)
    fun openFolderEdit(folderId: String?, isCreate: Boolean)
    fun openSyncConditions(objectPrefixAndId: String, objectReadableName: String)
    fun openFolderPicker(initialDirectory: String?, rootDirectory: String?)
    fun openLog()
    fun openWebView(url: String)
    fun openSettings(startDestination: String? = null)
    fun openRecentChanges()
    fun openWebGui()
    fun showDeviceIdDialog()
    fun confirmRestart()
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> {
    error("AppNavigator not provided")
}
