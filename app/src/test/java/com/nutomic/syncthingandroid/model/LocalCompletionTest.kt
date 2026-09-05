package com.nutomic.syncthingandroid.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioural tests for the Kotlin-converted LocalCompletion cache model:
 * completion calculation, paused/finished filtering and cache maintenance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalCompletionTest {

    private fun folderStatus(globalBytes: Long, inSyncBytes: Long, state: String): FolderStatus {
        val status = FolderStatus()
        status.globalBytes = globalBytes
        status.inSyncBytes = inSyncBytes
        status.state = state
        return status
    }

    @Test
    fun setFolderStatus_calculatesCompletion() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(1000, 250, "syncing"))
        val entry = completion.getFolderStatus("f1")
        assertEquals(25.0, entry.value.completion, 0.001)
        assertEquals(1000L, entry.key.globalBytes)
    }

    @Test
    fun setFolderStatus_zeroGlobalBytes_meansComplete() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(0, 0, "syncing"))
        assertEquals(100.0, completion.getFolderStatus("f1").value.completion, 0.001)
    }

    @Test
    fun setFolderStatus_inSyncAboveGlobal_isClampedToComplete() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(100, 200, "syncing"))
        assertEquals(100.0, completion.getFolderStatus("f1").value.completion, 0.001)
    }

    @Test
    fun setFolderStatus_idleState_meansComplete() {
        val completion = LocalCompletion(false)
        // 50% by byte count, but the "idle" state overrides completion to 100%.
        completion.setFolderStatus("f1", false, folderStatus(1000, 500, "idle"))
        assertEquals(100.0, completion.getFolderStatus("f1").value.completion, 0.001)
    }

    @Test
    fun setFolderStatus_preservesPausedFlag() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", true, folderStatus(1000, 0, "idle"))
        completion.setFolderStatus("f1", folderStatus(1000, 500, "syncing"))
        val entry = completion.getFolderStatus("f1")
        assertEquals(true, entry.value.paused)
        assertEquals(50.0, entry.value.completion, 0.001)
    }

    @Test
    fun getTotalFolderCompletion_excludesPausedAndFinishedFolders() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("a", false, folderStatus(1000, 500, "syncing"))
        completion.setFolderStatus("b", true, folderStatus(1000, 0, "syncing"))
        completion.setFolderStatus("c", false, folderStatus(1000, 1000, "idle"))
        assertEquals(50, completion.getTotalFolderCompletion())
    }

    @Test
    fun getTotalFolderCompletion_emptyModel_returns100() {
        assertEquals(100, LocalCompletion(false).getTotalFolderCompletion())
    }

    @Test
    fun updateFromConfig_addsAndRemovesFolders() {
        val completion = LocalCompletion(false)
        val keep = Folder()
        keep.id = "keep"
        val drop = Folder()
        drop.id = "drop"
        val added = Folder()
        added.id = "added"
        completion.updateFromConfig(arrayListOf(keep, drop))
        completion.setFolderStatus("drop", false, folderStatus(1000, 500, "syncing"))
        completion.updateFromConfig(arrayListOf(keep, added))

        assertTrue(completion.getFolderStatus("keep").value.completion >= 0.0)
        // "drop" was removed from the cache and re-created empty.
        assertEquals(100.0, completion.getFolderStatus("drop").value.completion, 0.001)
        assertEquals(100.0, completion.getFolderStatus("added").value.completion, 0.001)
    }

    @Test
    fun getFolderStatus_unknownFolder_returnsFreshDefaults() {
        val entry = LocalCompletion(false).getFolderStatus("nope")
        assertEquals("idle", entry.key.state)
        assertEquals(100.0, entry.value.completion, 0.001)
    }

    @Test
    fun getFolderStatus_returnsDeepCopy_mutationsDoNotLeak() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(1000, 500, "syncing"))
        val entry = completion.getFolderStatus("f1")
        entry.key.globalBytes = 42L
        entry.value.completion = 1.0
        assertEquals(1000L, completion.getFolderStatus("f1").key.globalBytes)
        assertEquals(50.0, completion.getFolderStatus("f1").value.completion, 0.001)
    }

    @Test
    fun setLastItemFinished_storesDetails() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(1000, 1000, "idle"))
        completion.setLastItemFinished("f1", "update", "photo.jpg", "2026-01-01T00:00:00Z")
        val cached = completion.getFolderStatus("f1").value
        assertEquals("update", cached.lastItemFinishedAction)
        assertEquals("photo.jpg", cached.lastItemFinishedItem)
        assertEquals("2026-01-01T00:00:00Z", cached.lastItemFinishedTime)
    }

    @Test
    fun setRemoteIndexUpdatedAndDiscoveredConflictFiles_stored() {
        val completion = LocalCompletion(false)
        completion.setFolderStatus("f1", false, folderStatus(1000, 0, "syncing"))
        completion.setRemoteIndexUpdated("f1", true)
        completion.setDiscoveredConflictFiles("f1", arrayOf("a.conflict"))
        val cached = completion.getFolderStatus("f1").value
        assertTrue(cached.remoteIndexUpdated)
        assertEquals(1, cached.discoveredConflictFiles.size)
        assertEquals("a.conflict", cached.discoveredConflictFiles[0])
    }
}
