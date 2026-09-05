package com.nutomic.syncthingandroid.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trip smoke tests proving that the Kotlin-converted model classes keep
 * Gson field-name and default-value behaviour identical to the former Java beans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KotlinModelGsonTest {

    companion object {
        private const val FOLDER_JSON = "{" +
            "\"id\":\"camera\"," +
            "\"label\":\"Camera\"," +
            "\"path\":\"/storage/emulated/0/DCIM/Camera\"," +
            "\"type\":\"sendreceive\"," +
            "\"devices\":[{\"deviceID\":\"ABC123\"}]" +
            "}"

        private const val DEVICE_JSON = "{" +
            "\"deviceID\":\"ABCDEFG-1234567\"," +
            "\"name\":\"Pixel\"," +
            "\"addresses\":[\"dynamic\"]," +
            "\"maxRecvKbps\":2048" +
            "}"

        private const val CONFIG_JSON = "{" +
            "\"version\":31," +
            "\"devices\":[{\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"Pixel\"}]," +
            "\"folders\":[{\"id\":\"camera\",\"label\":\"Camera\"}]," +
            "\"gui\":{\"address\":\"127.0.0.1:8384\",\"user\":\"admin\"}," +
            "\"options\":{\"urAccepted\":1}" +
            "}"
    }

    @Test
    fun folder_deserializesAllFields() {
        val folder = Gson().fromJson(FOLDER_JSON, Folder::class.java)
        assertNotNull(folder)
        assertEquals("camera", folder.id)
        assertEquals("Camera", folder.label)
        assertEquals("/storage/emulated/0/DCIM/Camera", folder.path)
        assertEquals("sendreceive", folder.type)
        assertNotNull(folder.getSharedWithDevices())
        assertEquals(1, folder.getDeviceCount())
        assertEquals("ABC123", folder.getSharedWithDevices()[0].deviceID)
    }

    @Test
    fun folder_missingOptionalFields_keepsKotlinDefaults() {
        // Mirrors the former Java bean defaults.
        val folder = Gson().fromJson("{\"id\":\"x\"}", Folder::class.java)
        assertNotNull(folder)
        assertEquals("basic", folder.filesystemType)
        assertEquals(true, folder.fsWatcherEnabled)
        assertEquals(3600, folder.rescanIntervalS)
        assertEquals("random", folder.order)
        assertEquals(10, folder.maxConflicts)
        assertNull(folder.versioning)
        assertNull(folder.minDiskFree)
    }

    @Test
    fun folder_serializesWithSameFieldNames() {
        val folder = Folder()
        folder.id = "camera"
        folder.label = ""
        folder.type = "sendreceive"
        val json = Gson().toJson(folder)
        // Field names must match what Syncthing's REST API expects.
        assertNotNull(json)
        assertEquals(true, json.contains("\"id\":\"camera\""))
        assertEquals(true, json.contains("\"rescanIntervalS\":3600"))
        assertEquals(true, json.contains("\"fsWatcherDelayS\":10.0"))
    }

    @Test
    fun device_deserializesAllFields() {
        val device = Gson().fromJson(DEVICE_JSON, Device::class.java)
        assertNotNull(device)
        assertEquals("ABCDEFG-1234567", device.deviceID)
        assertEquals("Pixel", device.name)
        assertNotNull(device.addresses)
        assertEquals(1, device.addresses!!.size)
        assertEquals("dynamic", device.addresses!![0])
        assertEquals(2048, device.maxRecvKbps)
    }

    @Test
    fun device_missingOptionalFields_keepsKotlinDefaults() {
        val device = Gson().fromJson("{\"deviceID\":\"x\"}", Device::class.java)
        assertNotNull(device)
        assertEquals("metadata", device.compression)
        assertEquals(false, device.introducer)
        assertEquals(false, device.paused)
        assertEquals(false, device.autoAcceptFolders)
        assertEquals(0, device.maxRecvKbps)
        assertNull(device.addresses)
        assertNull(device.ignoredFolders)
    }

    @Test
    fun config_deserializesNestedModels() {
        // Fully qualified: clashes with org.robolectric.annotation.Config.
        val config = Gson().fromJson(CONFIG_JSON, com.nutomic.syncthingandroid.model.Config::class.java)
        assertNotNull(config)
        assertEquals(31, config.version)
        assertNotNull(config.devices)
        assertEquals(1, config.devices!!.size)
        assertEquals("Pixel", config.devices!![0].name)
        assertNotNull(config.folders)
        assertEquals("camera", config.folders!![0].id)
        assertNotNull(config.gui)
        assertEquals("127.0.0.1:8384", config.gui!!.address)
        assertEquals("admin", config.gui!!.user)
        assertNotNull(config.options)
        assertEquals(1, config.options!!.urAccepted)
        assertNull(config.defaults)
        assertNull(config.remoteIgnoredDevices)
    }

    @Test
    fun gui_addressNullFallback_keepsJavaBehaviour() {
        // The REST API may send "address": null, and webGuiUrl guards against it.
        val gui = Gson().fromJson("{\"address\":null}", Gui::class.java)
        assertNull(gui.address)
        assertEquals("", gui.bindAddress)
        assertEquals("", gui.bindPort)
    }

    @Test
    fun gui_addressParsing() {
        val gui = Gson().fromJson("{\"address\":\"0.0.0.0:8384\"}", Gui::class.java)
        assertEquals("0.0.0.0", gui.bindAddress)
        assertEquals("8384", gui.bindPort)
    }

    @Test
    fun connectionsAndStatus_deserialize() {
        val connections = Gson().fromJson(
            "{\"total\":{\"connected\":true,\"inBytesTotal\":100},\"connections\":{}}",
            Connections::class.java
        )
        assertNotNull(connections.total)
        assertEquals(true, connections.total!!.connected)
        assertEquals(100L, connections.total!!.inBytesTotal)

        val status = Gson().fromJson(
            "{\"myID\":\"ABC\",\"urVersionMax\":3,\"discoveryEnabled\":true}",
            SystemStatus::class.java
        )
        assertEquals("ABC", status.myID)
        assertEquals(3, status.urVersionMax)
        assertEquals(true, status.discoveryEnabled)
        assertEquals(0, status.goroutines)
    }

    @Test
    fun eventAndDiskEvent_deserialize() {
        val event = Gson().fromJson(
            "{\"id\":5,\"type\":\"DeviceConnected\",\"data\":{\"id\":\"XYZ\"}}",
            Event::class.java
        )
        assertEquals(5, event.id)
        assertEquals("DeviceConnected", event.type)
        assertNotNull(event.data)
        assertEquals("XYZ", event.data!!["id"])

        val diskEvent = Gson().fromJson(
            "{\"id\":1,\"type\":\"LocalChangeDetected\",\"data\":{\"action\":\"added\",\"path\":\"/a/b\"}}",
            DiskEvent::class.java
        )
        assertEquals(1L, diskEvent.id)
        assertEquals("LocalChangeDetected", diskEvent.type)
        assertNotNull(diskEvent.data)
        assertEquals("added", diskEvent.data!!.action)
    }

    @Test
    fun deepCopyRoundTrip_usedByLocalAndRemoteCompletion() {
        // Util.deepCopy relies on Gson toJson/fromJson round-trips.
        val folder = Folder()
        folder.id = "f1"
        folder.label = "F1"
        folder.addDevice(Device())
        val json = Gson().toJson(folder)
        val copy = Gson().fromJson(json, Folder::class.java)
        assertEquals("f1", copy.id)
        assertEquals("F1", copy.label)
        assertEquals(1, copy.getSharedWithDevices().size)
    }

    @Test
    fun folderStatus_deserializesAllFields() {
        val status = Gson().fromJson(
            "{\"globalBytes\":100,\"inSyncBytes\":50,\"state\":\"syncing\",\"pullErrors\":2}",
            FolderStatus::class.java
        )
        assertEquals(100L, status.globalBytes)
        assertEquals(50L, status.inSyncBytes)
        assertEquals("syncing", status.state)
        assertEquals(2, status.pullErrors)
    }

    @Test
    fun folderStatus_missingFields_keepDefaults() {
        val status = Gson().fromJson("{}", FolderStatus::class.java)
        assertEquals("idle", status.state)
        assertEquals("", status.error)
        assertEquals(0L, status.globalBytes)
        assertEquals(false, status.ignorePatterns)
    }

    @Test
    fun cachedFolderStatus_gsonRoundTrip() {
        // Util.deepCopy() round-trips this class via Gson (LocalCompletion.getFolderStatus).
        val cached = CachedFolderStatus()
        cached.completion = 42.0
        cached.paused = true
        cached.lastItemFinishedItem = "file.txt"
        cached.discoveredConflictFiles = arrayOf("a.sync-conflict-1.txt")
        val copy = Gson().fromJson(Gson().toJson(cached), CachedFolderStatus::class.java)
        assertEquals(42.0, copy.completion, 0.001)
        assertEquals(true, copy.paused)
        assertEquals("file.txt", copy.lastItemFinishedItem)
        assertEquals(1, copy.discoveredConflictFiles.size)
        assertEquals(100.0, CachedFolderStatus().completion, 0.001)
    }

    @Test
    fun completionInfo_deserializes() {
        val info = Gson().fromJson(
            "{\"completion\":55.5,\"globalBytes\":1000,\"needBytes\":450,\"remoteState\":\"idle\"}",
            CompletionInfo::class.java
        )
        assertEquals(55.5, info.completion, 0.001)
        assertEquals(1000.0, info.globalBytes, 0.001)
        assertEquals(450.0, info.needBytes, 0.001)
        assertEquals("idle", info.remoteState)
    }

    @Test
    fun completionInfo_missingFields_keepDefaults() {
        val info = Gson().fromJson("{}", CompletionInfo::class.java)
        assertEquals("unknown", info.remoteState)
        assertEquals(0.0, info.completion, 0.001)
        assertEquals(0L, info.sequence)
    }

    @Test
    fun defaults_deserializesNested() {
        val defaults = Gson().fromJson(
            "{\"device\":{\"deviceID\":\"ABC\"},\"folder\":{\"id\":\"f\"},\"ignores\":{\"line\":[\"!*.tmp\"]}}",
            Defaults::class.java
        )
        assertEquals("ABC", defaults.device!!.deviceID)
        assertEquals("f", defaults.folder!!.id)
        val ignores = defaults.ignores!!
        assertNotNull(ignores.line)
        assertEquals("!*.tmp", ignores.line!![0])
    }

    @Test
    fun deviceStat_deserializes() {
        val stat = Gson().fromJson(
            "{\"lastSeen\":\"2026-01-01T00:00:00Z\"}", DeviceStat::class.java
        )
        assertEquals("2026-01-01T00:00:00Z", stat.lastSeen)
        assertEquals("", DeviceStat().lastSeen)
    }

    @Test
    fun discoveredDevice_deserializes() {
        val device = Gson().fromJson(
            "{\"addresses\":[\"tcp4://192.168.178.10:40001\"]}", DiscoveredDevice::class.java
        )
        assertNotNull(device.addresses)
        assertEquals(1, device.addresses!!.size)
        assertEquals("tcp4://192.168.178.10:40001", device.addresses!![0])
        assertNull(DiscoveredDevice().addresses)
    }

    @Test
    fun folderIgnoreList_deserializes() {
        val list = Gson().fromJson(
            "{\"expanded\":[\"foo\"],\"ignore\":[\"!foo\",\"/bar\"]}", FolderIgnoreList::class.java
        )
        assertEquals(1, list.expanded!!.size)
        assertEquals("foo", list.expanded!![0])
        assertEquals(2, list.ignore!!.size)
        assertEquals("!foo", list.ignore!![0])
    }

    @Test
    fun ignoredFolder_deserializes() {
        val folder = Gson().fromJson(
            "{\"id\":\"f1\",\"label\":\"L\",\"time\":\"2026-01-01T00:00:00Z\"}", IgnoredFolder::class.java
        )
        assertEquals("f1", folder.id)
        assertEquals("L", folder.label)
        assertEquals("2026-01-01T00:00:00Z", folder.time)
    }

    @Test
    fun ignores_deserializes() {
        val ignores = Gson().fromJson("{\"line\":[\"//c\",\"!*.jpg\"]}", Ignores::class.java)
        assertNotNull(ignores.line)
        assertEquals(2, ignores.line!!.size)
        assertEquals("!*.jpg", ignores.line!![1])
        assertNull(Ignores().line)
    }

    @Test
    fun options_deserializesAllFields() {
        val options = Gson().fromJson(
            "{\"listenAddresses\":[\"default\"],\"localAnnouncePort\":21027," +
                "\"urAccepted\":-1,\"minHomeDiskFree\":{\"value\":2.0,\"unit\":\"GB\"}}",
            Options::class.java
        )
        assertNotNull(options.listenAddresses)
        assertEquals("default", options.listenAddresses!![0])
        assertEquals(21027, options.localAnnouncePort)
        assertEquals(-1, options.urAccepted)
        assertNotNull(options.minHomeDiskFree)
        assertEquals(2.0f, options.minHomeDiskFree!!.value, 0.001f)
        assertEquals("GB", options.minHomeDiskFree!!.unit)
    }

    @Test
    fun options_missingFields_keepDefaults() {
        val options = Gson().fromJson("{}", Options::class.java)
        assertEquals(true, options.globalAnnounceEnabled)
        assertEquals(60, options.reconnectionIntervalS)
        assertEquals("https://data.syncthing.net/newdata", options.urURL)
        assertEquals(1, options.maxFolderConcurrency)
        assertNull(options.listenAddresses)
        assertNull(options.minHomeDiskFree)
    }

    @Test
    fun options_usageReportingLogic() {
        val accepted = Gson().fromJson("{\"urAccepted\":3}", Options::class.java)
        assertEquals(true, accepted.isUsageReportingAccepted(3))
        assertEquals(true, accepted.isUsageReportingDecided(3))
        assertEquals(false, accepted.isUsageReportingAccepted(2))

        val denied = Gson().fromJson("{\"urAccepted\":-1}", Options::class.java)
        assertEquals(false, denied.isUsageReportingAccepted(3))
        assertEquals(true, denied.isUsageReportingDecided(3))

        val undecided = Gson().fromJson("{}", Options::class.java)
        assertEquals(false, undecided.isUsageReportingDecided(3))
    }

    @Test
    fun pendingDeviceAndFolder_deserialize() {
        val device = Gson().fromJson(
            "{\"time\":\"2026-01-01T00:00:00Z\",\"name\":\"Pixel\",\"address\":\"tcp://1.2.3.4:22000\"}",
            PendingDevice::class.java
        )
        assertEquals("Pixel", device.name)
        assertEquals("tcp://1.2.3.4:22000", device.address)

        val folder = Gson().fromJson(
            "{\"label\":\"Camera\",\"receiveEncrypted\":true,\"remoteEncrypted\":false}",
            PendingFolder::class.java
        )
        assertEquals("Camera", folder.label)
        assertEquals(true, folder.receiveEncrypted)
        assertEquals(false, folder.remoteEncrypted)
    }

    @Test
    fun remoteIgnoredDevice_deserializesWithDisplayNameFallback() {
        val device = Gson().fromJson(
            "{\"time\":\"2026-01-01T00:00:00Z\",\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"\"}",
            RemoteIgnoredDevice::class.java
        )
        assertEquals("", device.name)
        assertEquals("ABCDEFG", device.displayName)

        val named = Gson().fromJson(
            "{\"deviceID\":\"ABCDEFG-1234567\",\"name\":\"Pixel\"}", RemoteIgnoredDevice::class.java
        )
        assertEquals("Pixel", named.displayName)
    }

    @Test
    fun sharedWithDevice_displayNameAndEncryptionPassword() {
        val device = Gson().fromJson(
            "{\"deviceID\":\"ABCDEFG-1234567\",\"introducedBy\":\"XYZ\",\"encryptionPassword\":\"s3cret\"}",
            SharedWithDevice::class.java
        )
        assertEquals("ABCDEFG", device.displayName)
        assertEquals("XYZ", device.introducedBy)
        assertEquals("s3cret", device.encryptionPassword)

        assertEquals("", SharedWithDevice().displayName)
    }

    @Test
    fun systemStatus_nestedConnectionAndDialStatus() {
        val status = Gson().fromJson(
            "{\"connectionServiceStatus\":{" +
                "\"tcp://0.0.0.0:22000\":{\"error\":null," +
                "\"lanAddresses\":[\"tcp://0.0.0.0:22000\"]," +
                "\"wanAddresses\":[\"tcp://1.2.3.4:22000\"]}}," +
                "\"lastDialStatus\":{" +
                "\"tcp4://192.168.5.1\":{\"error\":\"dial timeout\"," +
                "\"when\":\"2019-09-21T09:10:35Z\"}}}",
            SystemStatus::class.java
        )
        assertNotNull(status.connectionServiceStatus)
        val svc = status.connectionServiceStatus!!["tcp://0.0.0.0:22000"]!!
        assertNull(svc.error)
        assertEquals(1, svc.lanAddresses!!.size)
        assertEquals("tcp://0.0.0.0:22000", svc.lanAddresses!![0])
        assertEquals("tcp://1.2.3.4:22000", svc.wanAddresses!![0])

        // The Kotlin keyword field "when" must round-trip with its JSON name.
        assertNotNull(status.lastDialStatus)
        val dial = status.lastDialStatus!!["tcp4://192.168.5.1"]!!
        assertEquals("dial timeout", dial.error)
        assertEquals("2019-09-21T09:10:35Z", dial.`when`)
    }

    @Test
    fun diskEventData_deserializesAllFields() {
        val data = Gson().fromJson(
            "{\"action\":\"modified\",\"folder\":\"camera\",\"folderID\":\"camera\",\"label\":\"Camera\"," +
                "\"modifiedBy\":\"ABCDEFG\",\"path\":\"/a/b.jpg\",\"type\":\"file\"}",
            DiskEventData::class.java
        )
        assertEquals("modified", data.action)
        assertEquals("camera", data.folder)
        assertEquals("camera", data.folderID)
        assertEquals("Camera", data.label)
        assertEquals("ABCDEFG", data.modifiedBy)
        assertEquals("/a/b.jpg", data.path)
        assertEquals("file", data.type)
    }

    @Test
    fun event_dataMapRoundTrip() {
        // Event.data round-trips through Gson as a reflective field (kotlin Map since phase12).
        val event = Event()
        event.id = 7
        event.type = "FolderSummary"
        val data = HashMap<String, Any>()
        data["folder"] = "camera"
        event.data = data
        val copy = Gson().fromJson(Gson().toJson(event), Event::class.java)
        assertEquals(7, copy.id)
        assertEquals("FolderSummary", copy.type)
        assertEquals("camera", copy.data!!["folder"])
    }
}
