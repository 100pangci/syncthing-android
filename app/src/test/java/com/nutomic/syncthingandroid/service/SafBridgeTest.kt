package com.nutomic.syncthingandroid.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider

import com.nutomic.syncthingandroid.SyncthingApp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for bridge registration, fresh-install detection ("needs authorization")
 * and path-stable re-authorization of [SafBridge].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncthingApp::class)
class SafBridgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fcitxUri: Uri =
        Uri.parse("content://org.fcitx.fcitx5.android.provider/tree/sync")

    @Before
    fun takePersistableGrant() {
        context.contentResolver.takePersistableUriPermission(
            fcitxUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    @Test
    fun register_returnsForwardedPathUnderBridgeRoot() {
        val safBridge = SafBridge(context)
        val path = safBridge.register(fcitxUri)
        assertTrue(safBridge.isForwardedPath(path))
        assertTrue(safBridge.isForwarded(path))
        assertFalse(safBridge.needsAuthorization(path))
        safBridge.unregister(path)
    }

    @Test
    fun register_isDeterministicForSameUri() {
        val safBridge = SafBridge(context)
        val first = safBridge.register(fcitxUri)
        val second = safBridge.register(fcitxUri)
        assertEquals(first, second)
        safBridge.unregister(first)
    }

    @Test
    fun freshInstall_needsAuthorization_andReauthorizeKeepsPath() {
        val safBridge = SafBridge(context)
        val path = safBridge.register(fcitxUri)

        // Simulate a fresh install + config import: prefs wiped, new instance, the
        // restored config still points at [path] but the mapping is gone.
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val reinstalled = SafBridge(context)

        assertFalse(reinstalled.isForwarded(path))
        assertTrue(reinstalled.needsAuthorization(path))
        // Normal storage paths are never "needs authorization".
        assertFalse(reinstalled.needsAuthorization("/storage/emulated/0/docs"))

        // Re-authorizing must keep the configured path EXACTLY as-is so the
        // imported config keeps working without a rewrite.
        reinstalled.reauthorize(path, fcitxUri)
        assertTrue(reinstalled.isForwarded(path))
        assertFalse(reinstalled.needsAuthorization(path))

        reinstalled.unregister(path)
    }

    @Test
    fun reauthorize_allowsDifferentUriForSameFolder() {
        val safBridge = SafBridge(context)
        val path = safBridge.register(fcitxUri)
        val otherUri = Uri.parse("content://org.fcitx.fcitx5.android.provider/tree/other")
        safBridge.reauthorize(path, otherUri)
        assertTrue(safBridge.isForwarded(path))
        safBridge.unregister(path)
        assertFalse(safBridge.isForwarded(path))
    }

    @Test
    fun restoredMappingWithLostGrant_needsAuthorization() {
        // The clear-data/reinstall case: the config import restored the mapping,
        // but the persisted grant was revoked by the system.
        val safBridge = SafBridge(context)
        val path = safBridge.register(fcitxUri)
        assertFalse(safBridge.needsAuthorization(path))

        context.contentResolver.releasePersistableUriPermission(
            fcitxUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        assertTrue(safBridge.needsAuthorization(path))

        // Re-picking the folder re-takes the grant and clears the condition.
        context.contentResolver.takePersistableUriPermission(
            fcitxUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        assertFalse(safBridge.needsAuthorization(path))
        safBridge.unregister(path)
    }

    @Test
    fun unregister_removesForwardingState() {
        val safBridge = SafBridge(context)
        val path = safBridge.register(fcitxUri)
        safBridge.unregister(path)
        assertFalse(safBridge.isForwarded(path))
        assertFalse(safBridge.isForwardedPath(path) && safBridge.isForwarded(path))
    }
}
