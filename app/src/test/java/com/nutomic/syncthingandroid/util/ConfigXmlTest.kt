package com.nutomic.syncthingandroid.util

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider

import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets

/**
 * Unit tests for the config.xml DOM to model mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncthingApp::class)
class ConfigXmlTest {

    private fun newLoadedConfigXml(): ConfigXml {
        val configXml = ConfigXml(ApplicationProvider.getApplicationContext())
        configXml.loadConfig()
        return configXml
    }

    private fun writeConfig(content: String) {
        val configFile = Constants.getConfigFile(ApplicationProvider.getApplicationContext())
        FileWriter(configFile, StandardCharsets.UTF_8).use { writer -> writer.write(content) }
    }

    private fun configWithLocalDevice(): String {
        return TEST_CONFIG.replace(
            "  <device id=\"$REMOTE_DEVICE_ID\" name=\"Remote\"",
            "  <device id=\"$LOCAL_DEVICE_ID\" name=\"Local\"/>\n" +
                "  <device id=\"$REMOTE_DEVICE_ID\" name=\"Remote\""
        )
    }

    @Before
    fun setUp() {
        // Seed the cached local device id so ConfigXml never spawns the binary.
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext<Context>())
            .edit()
            .putString(Constants.PREF_LOCAL_DEVICE_ID, LOCAL_DEVICE_ID)
            .commit()
        writeConfig(TEST_CONFIG)
    }

    @Test
    fun loadConfig_parsesTestConfig() {
        newLoadedConfigXml()
    }

    @Test
    fun loadConfig_throwsOpenConfigExceptionOnMissingFile() {
        // Note: setUp wrote a config; remove it to hit the canRead guard.
        Constants.getConfigFile(ApplicationProvider.getApplicationContext<Context>()).delete()
        val configXml = ConfigXml(ApplicationProvider.getApplicationContext())
        try {
            configXml.loadConfig()
            fail("expected OpenConfigException")
        } catch (_: ConfigXml.OpenConfigException) {
        }
    }

    @Test
    fun getFolders_mapsFolderAttributes() {
        val folders = newLoadedConfigXml().folders
        assertEquals(2, folders.size)

        // Sorted by label: "Alpha" first, the unlabeled one falls back to its id.
        val folderA = folders[0]
        assertEquals("folder-a", folderA.id)
        assertEquals("Alpha", folderA.label)
        assertEquals("/data/folder-a", folderA.path)
        assertEquals(Constants.FOLDER_TYPE_SEND_RECEIVE, folderA.type)
        assertEquals(false, folderA.paused)

        // Shared-with device present, self excluded.
        assertEquals(1, if (folderA.getDevice(REMOTE_DEVICE_ID) != null) 1 else 0)

        val folderB = folders[1]
        assertEquals("folder-b", folderB.id)
        assertEquals("", folderB.label)
        assertEquals(Constants.FOLDER_TYPE_SEND_ONLY, folderB.type)
        assertEquals(true, folderB.paused)
    }

    @Test
    fun getFolders_mapsVersioning() {
        val folderA = newLoadedConfigXml().folders[0]
        assertEquals("trashcan", folderA.versioning!!.type)
        assertEquals("14", folderA.versioning!!.params["cleanoutDays"])
        assertEquals("MB", folderA.minDiskFree!!.unit)
        assertEquals(5.0, folderA.minDiskFree!!.value.toDouble(), 0.001)
    }

    @Test
    fun setFolderPause_persistsAcrossReload() {
        val configXml = newLoadedConfigXml()
        configXml.setFolderPause("folder-a", true)
        configXml.saveChanges()

        val folders = newLoadedConfigXml().folders
        assertEquals(true, folders[0].paused)
    }

    @Test
    fun updateFolder_roundTrip() {
        val configXml = newLoadedConfigXml()
        val folder = configXml.folders[0]
        folder.label = "Renamed"
        folder.path = "/data/renamed"
        configXml.updateFolder(folder)
        configXml.saveChanges()

        val reloaded = newLoadedConfigXml().folders[0]
        assertEquals("folder-a", reloaded.id)
        assertEquals("Renamed", reloaded.label)
        assertEquals("/data/renamed", reloaded.path)
    }

    @Test
    fun addFolder_replacesExistingFolderWithSameId() {
        // The deep-link bugfix made addFolder replace instead of duplicate
        // (double saves while Syncthing is not running must not create stale copies).
        val configXml = newLoadedConfigXml()
        val folder = Folder()
        folder.id = "folder-a"
        folder.label = "Replacement"
        folder.path = "/data/replacement"
        configXml.addFolder(folder)
        configXml.saveChanges()

        val folders = newLoadedConfigXml().folders
        assertEquals(2, folders.size)
        assertEquals(1, folders.count { it.id == "folder-a" })
        val replaced = folders.first { it.id == "folder-a" }
        assertEquals("Replacement", replaced.label)
        assertEquals("/data/replacement", replaced.path)
    }

    @Test
    fun removeFolder_removesAllDuplicateFolderNodes() {
        // Seed a stale duplicate of folder-a from an earlier double save.
        writeConfig(
            TEST_CONFIG.replace(
                "</folder>\n  <folder id=\"folder-b\"",
                "</folder>\n" +
                    "  <folder id=\"folder-a\" label=\"Stale\" path=\"/data/stale\" type=\"sendreceive\"/>\n" +
                    "  <folder id=\"folder-b\""
            )
        )
        assertEquals(2, newLoadedConfigXml().folders.count { it.id == "folder-a" })

        val configXml = newLoadedConfigXml()
        configXml.removeFolder("folder-a")
        configXml.saveChanges()

        val folders = newLoadedConfigXml().folders
        assertEquals(0, folders.count { it.id == "folder-a" })
        assertEquals(1, folders.size)
    }

    @Test
    fun guiAccessors_returnTestConfigValues() {
        val configXml = newLoadedConfigXml()
        assertEquals("test-api-key", configXml.apiKey)
        assertEquals(8384, configXml.webGuiBindPort)
        assertEquals("https://127.0.0.1:8384", configXml.webGuiUrl.toString())
    }

    @Test
    fun getWebGuiBindPort_fallsBackToDefaultWhenAddressLacksPort() {
        // The phase1 Gui.bindPort fix returns "" for addresses without a port segment;
        // webGuiBindPort must fall back to the default instead of crashing.
        writeConfig(TEST_CONFIG.replace("<address>127.0.0.1:8384</address>", "<address>127.0.0.1</address>"))
        val configXml = newLoadedConfigXml()
        assertEquals(Constants.DEFAULT_WEBGUI_TCP_PORT, configXml.webGuiBindPort)
    }

    @Test
    fun getDevices_excludesLocalDeviceAndSortsByName() {
        writeConfig(configWithLocalDevice())

        val withoutLocal = newLoadedConfigXml().getDevices(false)
        assertEquals(listOf("Remote"), withoutLocal.map { it.name })

        val withLocal = newLoadedConfigXml().getDevices(true)
        assertEquals(listOf("Local", "Remote"), withLocal.map { it.name })
        assertEquals(REMOTE_DEVICE_ID, withLocal[1].deviceID)
        assertEquals("metadata", withLocal[1].compression)
        assertEquals(listOf("tcp://192.168.1.2:22000"), withLocal[1].addresses)
    }

    @Test
    fun updateDevice_roundTrip() {
        val configXml = newLoadedConfigXml()
        val device = Device()
        device.deviceID = "NEW-DEVICE-ID"
        device.name = "New Device"
        device.compression = "always"
        device.addresses = arrayListOf("tcp://192.168.1.3:22000")
        device.allowedNetworks = arrayListOf("192.168.0.0/24")
        configXml.updateDevice(device)
        configXml.saveChanges()

        val reloaded = newLoadedConfigXml().getDevices(true)
        val newDevice = reloaded.first { it.deviceID == "NEW-DEVICE-ID" }
        assertEquals("New Device", newDevice.name)
        assertEquals("always", newDevice.compression)
        assertEquals(listOf("tcp://192.168.1.3:22000"), newDevice.addresses)
        assertEquals(listOf("192.168.0.0/24"), newDevice.allowedNetworks)
    }

    companion object {

        private const val LOCAL_DEVICE_ID = "LOCAL-DEVICE-ID"
        private const val REMOTE_DEVICE_ID = "REMOTE-DEVICE-ID"

        private val TEST_CONFIG = """
            <configuration version="37">
              <folder id="folder-a" label="Alpha" path="/data/folder-a" type="sendreceive">
                <device id="$REMOTE_DEVICE_ID"></device>
                <minDiskFree unit="MB">5</minDiskFree>
                <versioning type="trashcan"><param key="cleanoutDays" val="14"/></versioning>
              </folder>
              <folder id="folder-b" label="" path="/data/folder-b" type="sendonly">
                <paused>true</paused>
              </folder>
              <device id="$REMOTE_DEVICE_ID" name="Remote" compression="metadata">
                <address>tcp://192.168.1.2:22000</address>
              </device>
              <gui enabled="true" tls="false">
                <address>127.0.0.1:8384</address>
                <apikey>test-api-key</apikey>
                <user>syncthing</user>
              </gui>
              <options>
                <listenAddress>tcp://:22000</listenAddress>
              </options>
            </configuration>
        """.trimIndent()
    }
}
