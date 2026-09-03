package com.nutomic.syncthingandroid.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;
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
}
