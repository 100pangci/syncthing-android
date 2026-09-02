package com.nutomic.syncthingandroid.ui.nav

import android.app.Activity
import android.content.Intent
import com.nutomic.syncthingandroid.recentchanges.RecentChangesActivity
import com.nutomic.syncthingandroid.settings.SettingsActivity
import com.nutomic.syncthingandroid.webgui.WebGuiActivity

/**
 * Shared [AppNavigator] glue for activity based hosts. In-app routes are dispatched
 * through [navigateTo] while external activities (settings, web gui, recent changes)
 * are launched as standalone activities.
 */
abstract class IntentAppNavigator(protected val activity: Activity) : AppNavigator {

    override fun openDeviceEdit(deviceId: String?, isCreate: Boolean) =
        navigateTo(AppRoute.DeviceEdit(deviceId = deviceId, isCreate = isCreate))

    override fun openFolderEdit(folderId: String?, isCreate: Boolean) =
        navigateTo(AppRoute.FolderEdit(folderId = folderId, isCreate = isCreate))

    override fun openSyncConditions(objectPrefixAndId: String, objectReadableName: String) =
        navigateTo(AppRoute.SyncConditions(objectPrefixAndId, objectReadableName))

    override fun openFolderPicker(initialDirectory: String?, rootDirectory: String?) =
        navigateTo(AppRoute.FolderPicker(initialDirectory, rootDirectory))

    override fun openLog() = navigateTo(AppRoute.Log)

    override fun openWebView(url: String) = navigateTo(AppRoute.WebView(url))

    override fun openSettings(startDestination: String?) {
        val intent = Intent(activity, SettingsActivity::class.java)
        startDestination?.let {
            intent.putExtra(SettingsActivity.EXTRA_START_DESTINATION, it)
        }
        activity.startActivity(intent)
    }

    override fun openRecentChanges() {
        activity.startActivity(Intent(activity, RecentChangesActivity::class.java))
    }

    override fun openWebGui() {
        activity.startActivity(Intent(activity, WebGuiActivity::class.java))
    }
}
