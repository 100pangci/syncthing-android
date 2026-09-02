package com.nutomic.syncthingandroid.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the remote completion cache model, especially the
 * 0-100 clamping and up-to-date exclusion behaviour of the completion
 * percentage calculation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RemoteCompletionTest {

    private static final String DEVICE_1 = "DEVICE-1";
    private static final String DEVICE_2 = "DEVICE-2";

    private RemoteCompletionInfo completionInfo(double completion, double needBytes) {
        RemoteCompletionInfo info = new RemoteCompletionInfo();
        info.completion = completion;
        info.needBytes = needBytes;
        return info;
    }

    @Test
    public void emptyModel_returnsSaneDefaults() {
        RemoteCompletion completion = new RemoteCompletion(false);
        assertEquals(100, completion.getDeviceCompletion(DEVICE_1));
        assertEquals(-1, completion.getTotalDeviceCompletion());
        assertEquals(0, completion.getOnlineDeviceCount());
        assertEquals(0.0, completion.getDeviceNeedBytes(DEVICE_1), 0.001);
    }

    @Test
    public void completion_isAveragedOverPartiallySyncedFolders() {
        RemoteCompletion completion = new RemoteCompletion(false);
        completion.setDeviceStatus(DEVICE_1, new Connection());
        completion.setDeviceStatus(DEVICE_1, connectedConnection());
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(40, 600));
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(60, 400));

        assertEquals(50, completion.getDeviceCompletion(DEVICE_1));
        assertEquals(50, completion.getTotalDeviceCompletion());
        assertEquals(1, completion.getOnlineDeviceCount());
        assertEquals(1000.0, completion.getDeviceNeedBytes(DEVICE_1), 0.001);
    }

    @Test
    public void completion_excludesUpToDateFolders() {
        RemoteCompletion completion = new RemoteCompletion(false);
        completion.setDeviceStatus(DEVICE_1, connectedConnection());
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(100, 0));
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(0, 0));
        completion.setCompletionInfo(DEVICE_1, "folder-c", completionInfo(30, 500));

        // Only folder-c counts (0% and 100% are considered up-to-date).
        assertEquals(30, completion.getDeviceCompletion(DEVICE_1));
        assertEquals(30, completion.getTotalDeviceCompletion());
    }

    @Test
    public void completion_clampsOutOfRangeValues() {
        RemoteCompletion completion = new RemoteCompletion(false);
        completion.setDeviceStatus(DEVICE_1, connectedConnection());
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(-5, 0));
        completion.setCompletionInfo(DEVICE_1, "folder-b", completionInfo(150, 0));

        // Both clamp into the up-to-date range (0/100) and are therefore excluded.
        assertEquals(100, completion.getDeviceCompletion(DEVICE_1));
    }

    @Test
    public void totalCompletion_ignoresDisconnectedDevices() {
        RemoteCompletion completion = new RemoteCompletion(false);
        completion.setDeviceStatus(DEVICE_1, connectedConnection());
        completion.setCompletionInfo(DEVICE_1, "folder-a", completionInfo(50, 0));

        Connection disconnected = new Connection();
        disconnected.connected = false;
        completion.setDeviceStatus(DEVICE_2, disconnected);
        completion.setCompletionInfo(DEVICE_2, "folder-b", completionInfo(10, 0));

        assertEquals(50, completion.getTotalDeviceCompletion());
        assertEquals(1, completion.getOnlineDeviceCount());
    }

    private Connection connectedConnection() {
        Connection connection = new Connection();
        connection.connected = true;
        return connection;
    }
}
