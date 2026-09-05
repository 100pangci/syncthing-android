package com.nutomic.syncthingandroid.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the remote completion cache model, especially the
 * 0-100 clamping and up-to-date exclusion behaviour of the completion
 * percentage calculation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteCompletionTest {

    companion object {
        private const val DEVICE_1 = "DEVICE-1"
        private const val DEVICE_2 = "DEVICE-2"
    }

    private fun completionInfo(completion: Double, needBytes: Double): RemoteCompletionInfo {
        val info = RemoteCompletionInfo()
        info.completion = completion
        info.needBytes = needBytes
        return info
    }

    private fun connectedConnection(): Connection {
        val connection = Connection()
        connection.connected = true
        return connection
    }

    @Test
    fun emptyModel_returnsSaneDefaults() {
        val completion = RemoteCompletion(false)
        assertEquals(100, completion.getDeviceCompletion(DEVICE_1))
        assertEquals(-1, completion.getTotalDeviceCompletion())
        assertEquals(0, completion.getOnlineDeviceCount())
        assertEquals(0.0, completion.getDeviceNeedBytes(DEVICE_1), 0.001)
    }

    @Test
    fun completion_isAveragedOverPartiallySyncedFolders() {
        val completion = RemoteCompletion(false)
        completion.setDeviceStatus(DEVICE_1, Connection())
        completion.setDeviceStatus(DEVICE_1, connectedConnection())
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(40.0, 600.0))
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(60.0, 400.0))

        assertEquals(50, completion.getDeviceCompletion(DEVICE_1))
        assertEquals(50, completion.getTotalDeviceCompletion())
        assertEquals(1, completion.getOnlineDeviceCount())
        assertEquals(1000.0, completion.getDeviceNeedBytes(DEVICE_1), 0.001)
    }

    @Test
    fun completion_excludesUpToDateFolders() {
        val completion = RemoteCompletion(false)
        completion.setDeviceStatus(DEVICE_1, connectedConnection())
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(100.0, 0.0))
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(0.0, 0.0))
        completion.setCompletionInfo(DEVICE_1, "folder-c", completionInfo(30.0, 500.0))

        // Only folder-c counts (0% and 100% are considered up-to-date).
        assertEquals(30, completion.getDeviceCompletion(DEVICE_1))
        assertEquals(30, completion.getTotalDeviceCompletion())
    }

    @Test
    fun completion_clampsOutOfRangeValues() {
        val completion = RemoteCompletion(false)
        completion.setDeviceStatus(DEVICE_1, connectedConnection())
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(-5.0, 0.0))
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(150.0, 0.0))

        // Both clamp into the up-to-date range (0/100) and are therefore excluded.
        assertEquals(100, completion.getDeviceCompletion(DEVICE_1))
    }

    @Test
    fun totalCompletion_ignoresDisconnectedDevices() {
        val completion = RemoteCompletion(false)
        completion.setDeviceStatus(DEVICE_1, connectedConnection())
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(50.0, 0.0))

        val disconnected = Connection()
        disconnected.connected = false
        completion.setDeviceStatus(DEVICE_2, disconnected)
        completion.setCompletionInfo(DEVICE_2, "folder-b", completionInfo(10.0, 0.0))

        assertEquals(50, completion.getTotalDeviceCompletion())
        assertEquals(1, completion.getOnlineDeviceCount())
    }
}
