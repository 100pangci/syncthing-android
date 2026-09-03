package com.nutomic.syncthingandroid.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Round-trip smoke tests proving that the Kotlin-converted model classes keep
 * Gson field-name and default-value behaviour identical to the former Java beans.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KotlinModelGsonTest {

    private static final String FOLDER_JSON = "{" +
            "\"id\":\"camera\"," +
            "\"label\":\"Camera\"," +
            "\"path\":\"/storage/emulated/0/DCIM/Camera\"," +
            "\"type\":\"sendreceive\"," +
            "\"devices\":[{\"deviceID\":\"ABC123\"}]" +
            "}";

    private static final String DEVICE_JSON = "{" +
            "\"deviceID\":\"ABCDEFG-1234567\"," +
            "\"name\":\"Pixel\"," +
            "\"addresses\":[\"dynamic\"]," +
            "\"maxRecvKbps\":2048" +
            "}";

    private static final String CONFIG_JSON = "{" +
            "\"version\":31," +
            "\"devices\":[{\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"Pixel\"}]," +
            "\"folders\":[{\"id\":\"camera\",\"label\":\"Camera\"}]," +
            "\"gui\":{\"address\":\"127.0.0.1:8384\",\"user\":\"admin\"}," +
            "\"options\":{\"urAccepted\":1}" +
            "}";

    @Test
    public void folder_deserializesAllFields() {
        Folder folder = new Gson().fromJson(FOLDER_JSON, Folder.class);
        assertNotNull(folder);
        assertEquals("camera", folder.id);
        assertEquals("Camera", folder.label);
        assertEquals("/storage/emulated/0/DCIM/Camera", folder.path);
        assertEquals("sendreceive", folder.type);
        assertNotNull(folder.getSharedWithDevices());
        assertEquals(1, folder.getDeviceCount());
        assertEquals("ABC123", folder.getSharedWithDevices().get(0).deviceID);
    }

    @Test
    public void folder_missingOptionalFields_keepsKotlinDefaults() {
        // Mirrors the former Java bean defaults.
        Folder folder = new Gson().fromJson("{\"id\":\"x\"}", Folder.class);
        assertNotNull(folder);
        assertEquals("basic", folder.filesystemType);
        assertEquals(true, folder.fsWatcherEnabled);
        assertEquals(3600, folder.rescanIntervalS);
        assertEquals("random", folder.order);
        assertEquals(10, folder.maxConflicts);
        assertNull(folder.versioning);
        assertNull(folder.minDiskFree);
    }

    @Test
    public void folder_serializesWithSameFieldNames() {
        Folder folder = new Folder();
        folder.id = "camera";
        folder.label = "";
        folder.type = "sendreceive";
        String json = new Gson().toJson(folder);
        // Field names must match what Syncthing's REST API expects.
        assertNotNull(json);
        assertEquals(true, json.contains("\"id\":\"camera\""));
        assertEquals(true, json.contains("\"rescanIntervalS\":3600"));
        assertEquals(true, json.contains("\"fsWatcherDelayS\":10.0"));
    }

    @Test
    public void device_deserializesAllFields() {
        Device device = new Gson().fromJson(DEVICE_JSON, Device.class);
        assertNotNull(device);
        assertEquals("ABCDEFG-1234567", device.deviceID);
        assertEquals("Pixel", device.name);
        assertNotNull(device.addresses);
        assertEquals(1, device.addresses.size());
        assertEquals("dynamic", device.addresses.get(0));
        assertEquals(Integer.valueOf(2048), device.maxRecvKbps);
    }

    @Test
    public void device_missingOptionalFields_keepsKotlinDefaults() {
        Device device = new Gson().fromJson("{\"deviceID\":\"x\"}", Device.class);
        assertNotNull(device);
        assertEquals("metadata", device.compression);
        assertEquals(false, device.introducer);
        assertEquals(false, device.paused);
        assertEquals(false, device.autoAcceptFolders);
        assertEquals(Integer.valueOf(0), device.maxRecvKbps);
        assertNull(device.addresses);
        assertNull(device.ignoredFolders);
    }

    @Test
    public void config_deserializesNestedModels() {
        com.nutomic.syncthingandroid.model.Config config =
                new Gson().fromJson(CONFIG_JSON, com.nutomic.syncthingandroid.model.Config.class);
        assertNotNull(config);
        assertEquals(31, config.version);
        assertNotNull(config.devices);
        assertEquals(1, config.devices.size());
        assertEquals("Pixel", config.devices.get(0).name);
        assertNotNull(config.folders);
        assertEquals("camera", config.folders.get(0).id);
        assertNotNull(config.gui);
        assertEquals("127.0.0.1:8384", config.gui.address);
        assertEquals("admin", config.gui.user);
        assertNotNull(config.options);
        assertEquals(1, config.options.urAccepted);
        assertNull(config.defaults);
        assertNull(config.remoteIgnoredDevices);
    }

    @Test
    public void gui_addressNullFallback_keepsJavaBehaviour() {
        // The REST API may send "address": null, and getWebGuiUrl() guards against it.
        Gui gui = new Gson().fromJson("{\"address\":null}", Gui.class);
        assertNull(gui.address);
        assertEquals("", gui.getBindAddress());
        assertEquals("", gui.getBindPort());
    }

    @Test
    public void gui_addressParsing() {
        Gui gui = new Gson().fromJson("{\"address\":\"0.0.0.0:8384\"}", Gui.class);
        assertEquals("0.0.0.0", gui.getBindAddress());
        assertEquals("8384", gui.getBindPort());
    }

    @Test
    public void connectionsAndStatus_deserialize() {
        Connections connections = new Gson().fromJson(
                "{\"total\":{\"connected\":true,\"inBytesTotal\":100},\"connections\":{}}",
                Connections.class);
        assertNotNull(connections.total);
        assertEquals(true, connections.total.connected);
        assertEquals(100L, connections.total.inBytesTotal);

        SystemStatus status = new Gson().fromJson(
                "{\"myID\":\"ABC\",\"urVersionMax\":3,\"discoveryEnabled\":true}",
                SystemStatus.class);
        assertEquals("ABC", status.myID);
        assertEquals(3, status.urVersionMax);
        assertEquals(true, status.discoveryEnabled);
        assertEquals(0, status.goroutines);
    }

    @Test
    public void eventAndDiskEvent_deserialize() {
        Event event = new Gson().fromJson(
                "{\"id\":5,\"type\":\"DeviceConnected\",\"data\":{\"id\":\"XYZ\"}}",
                Event.class);
        assertEquals(5, event.id);
        assertEquals("DeviceConnected", event.type);
        assertNotNull(event.data);
        assertEquals("XYZ", event.data.get("id"));

        DiskEvent diskEvent = new Gson().fromJson(
                "{\"id\":1,\"type\":\"LocalChangeDetected\",\"data\":{\"action\":\"added\",\"path\":\"/a/b\"}}",
                DiskEvent.class);
        assertEquals(1L, diskEvent.id);
        assertEquals("LocalChangeDetected", diskEvent.type);
        assertNotNull(diskEvent.data);
        assertEquals("added", diskEvent.data.action);
    }

    @Test
    public void deepCopyRoundTrip_usedByLocalAndRemoteCompletion() {
        // Util.deepCopy relies on Gson toJson/fromJson round-trips.
        Folder folder = new Folder();
        folder.id = "f1";
        folder.label = "F1";
        folder.addDevice(new Device());
        String json = new Gson().toJson(folder);
        Folder copy = new Gson().fromJson(json, Folder.class);
        assertEquals("f1", copy.id);
        assertEquals("F1", copy.label);
        assertEquals(1, copy.getSharedWithDevices().size());
    }

    @Test
    public void folderStatus_deserializesAllFields() {
        FolderStatus status = new Gson().fromJson(
                "{\"globalBytes\":100,\"inSyncBytes\":50,\"state\":\"syncing\",\"pullErrors\":2}",
                FolderStatus.class);
        assertEquals(100L, status.globalBytes);
        assertEquals(50L, status.inSyncBytes);
        assertEquals("syncing", status.state);
        assertEquals(2, status.pullErrors);
    }

    @Test
    public void folderStatus_missingFields_keepDefaults() {
        FolderStatus status = new Gson().fromJson("{}", FolderStatus.class);
        assertEquals("idle", status.state);
        assertEquals("", status.error);
        assertEquals(0L, status.globalBytes);
        assertEquals(false, status.ignorePatterns);
    }

    @Test
    public void cachedFolderStatus_gsonRoundTrip() {
        // Util.deepCopy() round-trips this class via Gson (LocalCompletion.getFolderStatus).
        CachedFolderStatus cached = new CachedFolderStatus();
        cached.completion = 42.0;
        cached.paused = true;
        cached.lastItemFinishedItem = "file.txt";
        cached.discoveredConflictFiles = new String[]{"a.sync-conflict-1.txt"};
        CachedFolderStatus copy = new Gson().fromJson(new Gson().toJson(cached), CachedFolderStatus.class);
        assertEquals(42.0, copy.completion, 0.001);
        assertEquals(true, copy.paused);
        assertEquals("file.txt", copy.lastItemFinishedItem);
        assertEquals(1, copy.discoveredConflictFiles.length);
        assertEquals(100.0, new CachedFolderStatus().completion, 0.001);
    }

    @Test
    public void completionInfo_deserializes() {
        CompletionInfo info = new Gson().fromJson(
                "{\"completion\":55.5,\"globalBytes\":1000,\"needBytes\":450,\"remoteState\":\"idle\"}",
                CompletionInfo.class);
        assertEquals(55.5, info.completion, 0.001);
        assertEquals(1000.0, info.globalBytes, 0.001);
        assertEquals(450.0, info.needBytes, 0.001);
        assertEquals("idle", info.remoteState);
    }

    @Test
    public void completionInfo_missingFields_keepDefaults() {
        CompletionInfo info = new Gson().fromJson("{}", CompletionInfo.class);
        assertEquals("unknown", info.remoteState);
        assertEquals(0.0, info.completion, 0.001);
        assertEquals(0L, info.sequence);
    }

    @Test
    public void defaults_deserializesNested() {
        Defaults defaults = new Gson().fromJson(
                "{\"device\":{\"deviceID\":\"ABC\"},\"folder\":{\"id\":\"f\"},\"ignores\":{\"line\":[\"!*.tmp\"]}}",
                Defaults.class);
        assertEquals("ABC", defaults.device.deviceID);
        assertEquals("f", defaults.folder.id);
        assertNotNull(defaults.ignores.line);
        assertEquals("!*.tmp", defaults.ignores.line.get(0));
    }

    @Test
    public void deviceStat_deserializes() {
        DeviceStat stat = new Gson().fromJson(
                "{\"lastSeen\":\"2026-01-01T00:00:00Z\"}", DeviceStat.class);
        assertEquals("2026-01-01T00:00:00Z", stat.lastSeen);
        assertEquals("", new DeviceStat().lastSeen);
    }

    @Test
    public void discoveredDevice_deserializes() {
        DiscoveredDevice device = new Gson().fromJson(
                "{\"addresses\":[\"tcp4://192.168.178.10:40001\"]}", DiscoveredDevice.class);
        assertNotNull(device.addresses);
        assertEquals(1, device.addresses.length);
        assertEquals("tcp4://192.168.178.10:40001", device.addresses[0]);
        assertNull(new DiscoveredDevice().addresses);
    }

    @Test
    public void folderIgnoreList_deserializes() {
        FolderIgnoreList list = new Gson().fromJson(
                "{\"expanded\":[\"foo\"],\"ignore\":[\"!foo\",\"/bar\"]}", FolderIgnoreList.class);
        assertEquals(1, list.expanded.length);
        assertEquals("foo", list.expanded[0]);
        assertEquals(2, list.ignore.length);
        assertEquals("!foo", list.ignore[0]);
    }

    @Test
    public void ignoredFolder_deserializes() {
        IgnoredFolder folder = new Gson().fromJson(
                "{\"id\":\"f1\",\"label\":\"L\",\"time\":\"2026-01-01T00:00:00Z\"}", IgnoredFolder.class);
        assertEquals("f1", folder.id);
        assertEquals("L", folder.label);
        assertEquals("2026-01-01T00:00:00Z", folder.time);
    }

    @Test
    public void ignores_deserializes() {
        Ignores ignores = new Gson().fromJson("{\"line\":[\"//c\",\"!*.jpg\"]}", Ignores.class);
        assertNotNull(ignores.line);
        assertEquals(2, ignores.line.size());
        assertEquals("!*.jpg", ignores.line.get(1));
        assertNull(new Ignores().line);
    }

    @Test
    public void options_deserializesAllFields() {
        Options options = new Gson().fromJson(
                "{\"listenAddresses\":[\"default\"],\"localAnnouncePort\":21027," +
                        "\"urAccepted\":-1,\"minHomeDiskFree\":{\"value\":2.0,\"unit\":\"GB\"}}",
                Options.class);
        assertNotNull(options.listenAddresses);
        assertEquals("default", options.listenAddresses[0]);
        assertEquals(21027, options.localAnnouncePort);
        assertEquals(-1, options.urAccepted);
        assertNotNull(options.minHomeDiskFree);
        assertEquals(2.0f, options.minHomeDiskFree.value, 0.001f);
        assertEquals("GB", options.minHomeDiskFree.unit);
    }

    @Test
    public void options_missingFields_keepDefaults() {
        Options options = new Gson().fromJson("{}", Options.class);
        assertEquals(true, options.globalAnnounceEnabled);
        assertEquals(60, options.reconnectionIntervalS);
        assertEquals("https://data.syncthing.net/newdata", options.urURL);
        assertEquals(1, options.maxFolderConcurrency);
        assertNull(options.listenAddresses);
        assertNull(options.minHomeDiskFree);
    }

    @Test
    public void options_usageReportingLogic() {
        Options accepted = new Gson().fromJson("{\"urAccepted\":3}", Options.class);
        assertEquals(true, accepted.isUsageReportingAccepted(3));
        assertEquals(true, accepted.isUsageReportingDecided(3));
        assertEquals(false, accepted.isUsageReportingAccepted(2));

        Options denied = new Gson().fromJson("{\"urAccepted\":-1}", Options.class);
        assertEquals(false, denied.isUsageReportingAccepted(3));
        assertEquals(true, denied.isUsageReportingDecided(3));

        Options undecided = new Gson().fromJson("{}", Options.class);
        assertEquals(false, undecided.isUsageReportingDecided(3));
    }

    @Test
    public void pendingDeviceAndFolder_deserialize() {
        PendingDevice device = new Gson().fromJson(
                "{\"time\":\"2026-01-01T00:00:00Z\",\"name\":\"Pixel\",\"address\":\"tcp://1.2.3.4:22000\"}",
                PendingDevice.class);
        assertEquals("Pixel", device.name);
        assertEquals("tcp://1.2.3.4:22000", device.address);

        PendingFolder folder = new Gson().fromJson(
                "{\"label\":\"Camera\",\"receiveEncrypted\":true,\"remoteEncrypted\":false}",
                PendingFolder.class);
        assertEquals("Camera", folder.label);
        assertEquals(Boolean.TRUE, folder.receiveEncrypted);
        assertEquals(Boolean.FALSE, folder.remoteEncrypted);
    }

    @Test
    public void remoteIgnoredDevice_deserializesWithDisplayNameFallback() {
        RemoteIgnoredDevice device = new Gson().fromJson(
                "{\"time\":\"2026-01-01T00:00:00Z\",\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"\"}",
                RemoteIgnoredDevice.class);
        assertEquals("", device.name);
        assertEquals("ABCDEFG", device.getDisplayName());

        RemoteIgnoredDevice named = new Gson().fromJson(
                "{\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"Pixel\"}", RemoteIgnoredDevice.class);
        assertEquals("Pixel", named.getDisplayName());
    }

    @Test
    public void sharedWithDevice_displayNameAndEncryptionPassword() {
        SharedWithDevice device = new Gson().fromJson(
                "{\"deviceID\":\"ABCDEFG-1234567\",\"introducedBy\":\"XYZ\",\"encryptionPassword\":\"s3cret\"}",
                SharedWithDevice.class);
        assertEquals("ABCDEFG", device.getDisplayName());
        assertEquals("XYZ", device.introducedBy);
        assertEquals("s3cret", device.encryptionPassword);

        assertEquals("", new SharedWithDevice().getDisplayName());
    }

    @Test
    public void systemStatus_nestedConnectionAndDialStatus() {
        SystemStatus status = new Gson().fromJson(
                "{\"connectionServiceStatus\":{" +
                        "\"tcp://0.0.0.0:22000\":{\"error\":null," +
                        "\"lanAddresses\":[\"tcp://0.0.0.0:22000\"]," +
                        "\"wanAddresses\":[\"tcp://1.2.3.4:22000\"]}}," +
                "\"lastDialStatus\":{" +
                        "\"tcp4://192.168.5.1\":{\"error\":\"dial timeout\"," +
                        "\"when\":\"2019-09-21T09:10:35Z\"}}}",
                SystemStatus.class);
        assertNotNull(status.connectionServiceStatus);
        SystemStatusConnectionServiceStatusElement svc =
                status.connectionServiceStatus.get("tcp://0.0.0.0:22000");
        assertNull(svc.error);
        assertEquals(1, svc.lanAddresses.size());
        assertEquals("tcp://0.0.0.0:22000", svc.lanAddresses.get(0));
        assertEquals("tcp://1.2.3.4:22000", svc.wanAddresses.get(0));

        // The Kotlin keyword field "when" must round-trip with its JSON name.
        assertNotNull(status.lastDialStatus);
        SystemStatusLastDialStatusElement dial = status.lastDialStatus.get("tcp4://192.168.5.1");
        assertEquals("dial timeout", dial.error);
        assertEquals("2019-09-21T09:10:35Z", dial.getWhen());
    }

    @Test
    public void diskEventData_deserializesAllFields() {
        DiskEventData data = new Gson().fromJson(
                "{\"action\":\"modified\",\"folder\":\"camera\",\"folderID\":\"camera\",\"label\":\"Camera\"," +
                        "\"modifiedBy\":\"ABCDEFG\",\"path\":\"/a/b.jpg\",\"type\":\"file\"}",
                DiskEventData.class);
        assertEquals("modified", data.action);
        assertEquals("camera", data.folder);
        assertEquals("camera", data.folderID);
        assertEquals("Camera", data.label);
        assertEquals("ABCDEFG", data.modifiedBy);
        assertEquals("/a/b.jpg", data.path);
        assertEquals("file", data.type);
    }

    @Test
    public void event_javaUtilMapRoundTrip() {
        // Event.data must stay java.util.Map: Kotlin's Map would leak wildcards to Java callers.
        Event event = new Event();
        event.id = 7;
        event.type = "FolderSummary";
        Map<String, Object> data = new HashMap<>();
        data.put("folder", "camera");
        event.data = data;
        Event copy = new Gson().fromJson(new Gson().toJson(event), Event.class);
        assertEquals(7, copy.id);
        assertEquals("FolderSummary", copy.type);
        assertEquals("camera", copy.data.get("folder"));
    }
}
