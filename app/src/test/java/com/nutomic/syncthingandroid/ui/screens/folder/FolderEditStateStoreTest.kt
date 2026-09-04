package com.nutomic.syncthingandroid.ui.screens.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the draft store lifecycle: one holder per route key, eviction strictly
 * when the key leaves the back stack (see FolderEditStateStore KDoc for why
 * this exists).
 */
class FolderEditStateStoreTest {

    @Test
    fun holderFor_returnsSameInstanceForSameKey() {
        val store = FolderEditStateStore()
        val a = store.holderFor("edit:f1")
        a.needsUpdate = true
        assertSame(a, store.holderFor("edit:f1"))
        assertTrue(store.holderFor("edit:f1").needsUpdate)
    }

    @Test
    fun holderFor_returnsDistinctInstancesForDistinctKeys() {
        val store = FolderEditStateStore()
        assertNotSame(store.holderFor("edit:f1"), store.holderFor("edit:f2"))
        assertNotSame(store.holderFor("edit:f1"), store.holderFor("create"))
    }

    @Test
    fun retainAll_evictsKeysMissingFromBackStack_andKeepsLiveOnes() {
        val store = FolderEditStateStore()
        val keep = store.holderFor("edit:f1")
        store.holderFor("edit:f2")
        store.holderFor("create")

        store.retainAll(setOf("edit:f1"))

        assertSame(keep, store.holderFor("edit:f1"))
        val recreated = store.holderFor("edit:f2")
        assertTrue(!recreated.needsUpdate) // fresh holder, not the evicted draft
        assertEquals(false, recreated.customSyncConditions)
    }

    @Test
    fun folderEditStateKey_stablePerFolderId() {
        assertEquals("edit:f1", folderEditStateKey("f1", isCreate = false))
        assertEquals("create", folderEditStateKey(null, isCreate = true))
        assertEquals("create", folderEditStateKey("f1", isCreate = true))
        assertEquals("create", folderEditStateKey(null, isCreate = false))
    }
}
