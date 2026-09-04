package com.nutomic.syncthingandroid.ui.screens

import com.nutomic.syncthingandroid.ui.nav.EditStateStore
import com.nutomic.syncthingandroid.ui.screens.device.deviceEditStateKey
import com.nutomic.syncthingandroid.ui.screens.folder.folderEditStateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the draft store lifecycle shared by the folder and device editors: one
 * state object per route key, eviction strictly when the key leaves the back
 * stack (see EditStateStore for why plain remember/rememberSaveable are not
 * sufficient under Navigation 3).
 */
class EditStateStoreTest {

    private class Draft(var dirty: Boolean = false)

    @Test
    fun stateFor_returnsSameInstanceForSameKey() {
        val store = EditStateStore { Draft() }
        val a = store.stateFor("edit:f1")
        a.dirty = true
        assertSame(a, store.stateFor("edit:f1"))
        assertTrue(store.stateFor("edit:f1").dirty)
    }

    @Test
    fun stateFor_returnsDistinctInstancesForDistinctKeys() {
        val store = EditStateStore { Draft() }
        assertNotSame(store.stateFor("edit:f1"), store.stateFor("edit:f2"))
        assertNotSame(store.stateFor("edit:f1"), store.stateFor("create"))
    }

    @Test
    fun retainAll_evictsKeysMissingFromBackStack_andKeepsLiveOnes() {
        val store = EditStateStore { Draft() }
        val keep = store.stateFor("edit:f1")
        store.stateFor("edit:f2")
        store.stateFor("create")

        store.retainAll(setOf("edit:f1"))

        assertSame(keep, store.stateFor("edit:f1"))
        assertTrue(!store.stateFor("edit:f2").dirty) // fresh state, not the evicted draft
    }

    @Test
    fun folderEditStateKey_stablePerFolderId() {
        assertEquals("edit:f1", folderEditStateKey("f1", isCreate = false))
        assertEquals("create", folderEditStateKey(null, isCreate = true))
        assertEquals("create", folderEditStateKey("f1", isCreate = true))
        assertEquals("create", folderEditStateKey(null, isCreate = false))
    }

    @Test
    fun deviceEditStateKey_stablePerDeviceId() {
        assertEquals("edit:d1", deviceEditStateKey("d1", isCreate = false))
        assertEquals("create", deviceEditStateKey(null, isCreate = true))
        assertEquals("create", deviceEditStateKey("d1", isCreate = true))
        assertEquals("create", deviceEditStateKey(null, isCreate = false))
    }
}
