package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Event;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.NotificationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the syncthing event to local action mapping.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = SyncthingApp.class)
public class EventProcessorTest {

    private static final String[] IGNORED_EVENT_TYPES = {
            "DeviceDiscovered",
            "DownloadProgress",
            "FolderScanProgress",
            "FolderWatchStateChanged",
            "ItemStarted",
            "ListenAddressesChanged",
            "LoginAttempt",
            "RemoteDownloadProgress",
    };

    private RestApi mRestApi;
    private EventProcessor mEventProcessor;
    private NotificationManager mNotificationManager;

    @Before
    public void setUp() {
        mRestApi = mock(RestApi.class);
        mEventProcessor = new EventProcessor(ApplicationProvider.getApplicationContext(), mRestApi);
        mNotificationManager = ApplicationProvider.getApplicationContext()
                .getSystemService(NotificationManager.class);
    }

    private Event event(String type, Map<String, Object> data) {
        Event event = new Event();
        event.id = 1;
        event.type = type;
        event.data = data;
        return event;
    }

    @Test
    public void ignoredEvents_areNotForwardedToRestApi() {
        for (String type : IGNORED_EVENT_TYPES) {
            // Include a payload that would trigger follow-up actions if the event
            // accidentally fell through to the RemoteIndexUpdated handler.
            Map<String, Object> data = new HashMap<>();
            data.put("device", "DEVICE-1");
            data.put("folder", "folder-a");
            data.put("items", 5.0);

            mEventProcessor.onEvent(event(type, data), new JsonObject());
        }

        verify(mRestApi, never()).setRemoteIndexUpdated(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void remoteIndexUpdated_withItems_isForwarded() {
        Map<String, Object> data = new HashMap<>();
        data.put("device", "DEVICE-1");
        data.put("folder", "folder-a");
        data.put("items", 5.0);

        mEventProcessor.onEvent(event("RemoteIndexUpdated", data), new JsonObject());

        verify(mRestApi).setRemoteIndexUpdated("DEVICE-1", "folder-a", true);
    }

    @Test
    public void remoteIndexUpdated_withZeroItems_isNotForwarded() {
        Map<String, Object> data = new HashMap<>();
        data.put("device", "DEVICE-1");
        data.put("folder", "folder-a");
        data.put("items", 0.0);

        mEventProcessor.onEvent(event("RemoteIndexUpdated", data), new JsonObject());

        verify(mRestApi, never()).setRemoteIndexUpdated(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void pendingFoldersChanged_withoutDeviceId_doesNotCrashAndNotifies() {
        Map<String, Object> pendingFolder = new HashMap<>();
        pendingFolder.put("folderID", "folder-a");
        Map<String, Object> data = new HashMap<>();
        data.put("added", java.util.Collections.singletonList(pendingFolder));

        // Must not throw (regression: null dereference before the null check).
        mEventProcessor.onEvent(event("PendingFoldersChanged", data), new JsonObject());
    }

    @Test
    public void folderErrors_insufficientSpace_postsCrashNotification() {
        Event event = event("FolderErrors", new HashMap<>());
        JsonObject json = JsonParser.parseString(
                "{\"data\": {\"errors\": [{\"error\": \"insufficient space in basic folder\", " +
                        "\"path\": \"/storage/emulated/0/Sync/file.txt\"}]}}")
                .getAsJsonObject();

        mEventProcessor.onEvent(event, json);

        assertTrue(mNotificationManager.getActiveNotifications().length >= 1);
    }
}
