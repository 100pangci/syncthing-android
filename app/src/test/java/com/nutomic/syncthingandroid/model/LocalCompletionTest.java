package com.nutomic.syncthingandroid.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Behavioural tests for the Kotlin-converted LocalCompletion cache model:
 * completion calculation, paused/finished filtering and cache maintenance.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LocalCompletionTest {

    private FolderStatus folderStatus(long globalBytes, long inSyncBytes, String state) {
        FolderStatus status = new FolderStatus();
        status.globalBytes = globalBytes;
        status.inSyncBytes = inSyncBytes;
        status.state = state;
        return status;
    }

    @Test
    public void setFolderStatus_calculatesCompletion() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(1000, 250, "syncing"));
        Map.Entry<FolderStatus, CachedFolderStatus> entry = completion.getFolderStatus("f1");
        assertEquals(25.0, entry.getValue().completion, 0.001);
        assertEquals(1000L, entry.getKey().globalBytes);
    }

    @Test
    public void setFolderStatus_zeroGlobalBytes_meansComplete() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(0, 0, "syncing"));
        assertEquals(100.0, completion.getFolderStatus("f1").getValue().completion, 0.001);
    }

    @Test
    public void setFolderStatus_inSyncAboveGlobal_isClampedToComplete() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(100, 200, "syncing"));
        assertEquals(100.0, completion.getFolderStatus("f1").getValue().completion, 0.001);
    }

    @Test
    public void setFolderStatus_idleState_meansComplete() {
        LocalCompletion completion = new LocalCompletion(false);
        // 50% by byte count, but the "idle" state overrides completion to 100%.
        completion.setFolderStatus("f1", false, folderStatus(1000, 500, "idle"));
        assertEquals(100.0, completion.getFolderStatus("f1").getValue().completion, 0.001);
    }

    @Test
    public void setFolderStatus_preservesPausedFlag() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", true, folderStatus(1000, 0, "idle"));
        completion.setFolderStatus("f1", folderStatus(1000, 500, "syncing"));
        Map.Entry<FolderStatus, CachedFolderStatus> entry = completion.getFolderStatus("f1");
        assertEquals(true, entry.getValue().paused);
        assertEquals(50.0, entry.getValue().completion, 0.001);
    }

    @Test
    public void getTotalFolderCompletion_excludesPausedAndFinishedFolders() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("a", false, folderStatus(1000, 500, "syncing"));
        completion.setFolderStatus("b", true, folderStatus(1000, 0, "syncing"));
        completion.setFolderStatus("c", false, folderStatus(1000, 1000, "idle"));
        assertEquals(50, completion.getTotalFolderCompletion());
    }

    @Test
    public void getTotalFolderCompletion_emptyModel_returns100() {
        assertEquals(100, new LocalCompletion(false).getTotalFolderCompletion());
    }

    @Test
    public void updateFromConfig_addsAndRemovesFolders() {
        LocalCompletion completion = new LocalCompletion(false);
        Folder keep = new Folder();
        keep.id = "keep";
        Folder drop = new Folder();
        drop.id = "drop";
        Folder added = new Folder();
        added.id = "added";
        completion.updateFromConfig(new ArrayList<>(Arrays.asList(keep, drop)));
        completion.setFolderStatus("drop", false, folderStatus(1000, 500, "syncing"));
        completion.updateFromConfig(new ArrayList<>(Arrays.asList(keep, added)));

        assertTrue(completion.getFolderStatus("keep").getValue().completion >= 0.0);
        // "drop" was removed from the cache and re-created empty.
        assertEquals(100.0, completion.getFolderStatus("drop").getValue().completion, 0.001);
        assertEquals(100.0, completion.getFolderStatus("added").getValue().completion, 0.001);
    }

    @Test
    public void getFolderStatus_unknownFolder_returnsFreshDefaults() {
        Map.Entry<FolderStatus, CachedFolderStatus> entry =
                new LocalCompletion(false).getFolderStatus("nope");
        assertEquals("idle", entry.getKey().state);
        assertEquals(100.0, entry.getValue().completion, 0.001);
    }

    @Test
    public void getFolderStatus_returnsDeepCopy_mutationsDoNotLeak() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(1000, 500, "syncing"));
        Map.Entry<FolderStatus, CachedFolderStatus> entry = completion.getFolderStatus("f1");
        entry.getKey().globalBytes = 42L;
        entry.getValue().completion = 1.0;
        assertEquals(1000L, completion.getFolderStatus("f1").getKey().globalBytes);
        assertEquals(50.0, completion.getFolderStatus("f1").getValue().completion, 0.001);
    }

    @Test
    public void setLastItemFinished_storesDetails() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(1000, 1000, "idle"));
        completion.setLastItemFinished("f1", "update", "photo.jpg", "2026-01-01T00:00:00Z");
        CachedFolderStatus cached = completion.getFolderStatus("f1").getValue();
        assertEquals("update", cached.lastItemFinishedAction);
        assertEquals("photo.jpg", cached.lastItemFinishedItem);
        assertEquals("2026-01-01T00:00:00Z", cached.lastItemFinishedTime);
    }

    @Test
    public void setRemoteIndexUpdatedAndDiscoveredConflictFiles_stored() {
        LocalCompletion completion = new LocalCompletion(false);
        completion.setFolderStatus("f1", false, folderStatus(1000, 0, "syncing"));
        completion.setRemoteIndexUpdated("f1", true);
        completion.setDiscoveredConflictFiles("f1", new String[]{"a.conflict"});
        CachedFolderStatus cached = completion.getFolderStatus("f1").getValue();
        assertTrue(cached.remoteIndexUpdated);
        assertEquals(1, cached.discoveredConflictFiles.length);
        assertEquals("a.conflict", cached.discoveredConflictFiles[0]);
    }
}
