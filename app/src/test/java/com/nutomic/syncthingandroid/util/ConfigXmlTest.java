package com.nutomic.syncthingandroid.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Unit tests for the config.xml DOM to model mapping.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = SyncthingApp.class)
public class ConfigXmlTest {

    private static final String LOCAL_DEVICE_ID = "LOCAL-DEVICE-ID";
    private static final String REMOTE_DEVICE_ID = "REMOTE-DEVICE-ID";

    private static final String TEST_CONFIG =
            "<configuration version=\"37\">\n" +
            "  <folder id=\"folder-a\" label=\"Alpha\" path=\"/data/folder-a\" type=\"sendreceive\">\n" +
            "    <device id=\"" + REMOTE_DEVICE_ID + "\"></device>\n" +
            "    <minDiskFree unit=\"MB\">5</minDiskFree>\n" +
            "    <versioning type=\"trashcan\"><param key=\"cleanoutDays\" val=\"14\"/></versioning>\n" +
            "  </folder>\n" +
            "  <folder id=\"folder-b\" label=\"\" path=\"/data/folder-b\" type=\"sendonly\">\n" +
            "    <paused>true</paused>\n" +
            "  </folder>\n" +
            "  <device id=\"" + REMOTE_DEVICE_ID + "\" name=\"Remote\" compression=\"metadata\">\n" +
            "    <address>tcp://192.168.1.2:22000</address>\n" +
            "  </device>\n" +
            "  <gui enabled=\"true\" tls=\"false\">\n" +
            "    <address>127.0.0.1:8384</address>\n" +
            "    <apikey>test-api-key</apikey>\n" +
            "    <user>syncthing</user>\n" +
            "  </gui>\n" +
            "  <options>\n" +
            "    <listenAddress>tcp://:22000</listenAddress>\n" +
            "  </options>\n" +
            "</configuration>\n";

    @Before
    public void setUp() throws Exception {
        // Seed the cached local device id so ConfigXml never spawns the binary.
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit()
                .putString(Constants.PREF_LOCAL_DEVICE_ID, LOCAL_DEVICE_ID)
                .commit();
        File configFile = Constants.getConfigFile(ApplicationProvider.getApplicationContext());
        FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8);
        writer.write(TEST_CONFIG);
        writer.close();
    }

    private ConfigXml newLoadedConfigXml() throws Exception {
        ConfigXml configXml = new ConfigXml(ApplicationProvider.getApplicationContext());
        configXml.loadConfig();
        return configXml;
    }

    @Test
    public void loadConfig_parsesTestConfig() throws Exception {
        newLoadedConfigXml();
    }

    @Test
    public void getFolders_mapsFolderAttributes() throws Exception {
        List<Folder> folders = newLoadedConfigXml().getFolders();
        assertEquals(2, folders.size());

        // Sorted by label: "Alpha" first, the unlabeled one falls back to its id.
        Folder folderA = folders.get(0);
        assertEquals("folder-a", folderA.id);
        assertEquals("Alpha", folderA.label);
        assertEquals("/data/folder-a", folderA.path);
        assertEquals(Constants.FOLDER_TYPE_SEND_RECEIVE, folderA.type);
        assertFalse(folderA.paused);

        // Shared-with device present, self excluded.
        assertEquals(1, folderA.getDevice(REMOTE_DEVICE_ID) != null ? 1 : 0);

        Folder folderB = folders.get(1);
        assertEquals("folder-b", folderB.id);
        assertEquals("", folderB.label);
        assertEquals(Constants.FOLDER_TYPE_SEND_ONLY, folderB.type);
        assertTrue(folderB.paused);
    }

    @Test
    public void getFolders_mapsVersioning() throws Exception {
        List<Folder> folders = newLoadedConfigXml().getFolders();
        Folder folderA = folders.get(0);
        assertEquals("trashcan", folderA.versioning.type);
        assertEquals("14", folderA.versioning.params.get("cleanoutDays"));
        assertEquals("MB", folderA.minDiskFree.unit);
        assertEquals(5.0, folderA.minDiskFree.value, 0.001);
    }

    @Test
    public void setFolderPause_persistsAcrossReload() throws Exception {
        ConfigXml configXml = newLoadedConfigXml();
        configXml.setFolderPause("folder-a", true);
        configXml.saveChanges();

        List<Folder> folders = newLoadedConfigXml().getFolders();
        assertTrue(folders.get(0).paused);
    }

    @Test
    public void updateFolder_roundTrip() throws Exception {
        ConfigXml configXml = newLoadedConfigXml();
        Folder folder = configXml.getFolders().get(0);
        folder.label = "Renamed";
        folder.path = "/data/renamed";
        configXml.updateFolder(folder);
        configXml.saveChanges();

        Folder reloaded = newLoadedConfigXml().getFolders().get(0);
        assertEquals("folder-a", reloaded.id);
        assertEquals("Renamed", reloaded.label);
        assertEquals("/data/renamed", reloaded.path);
    }

    @Test
    public void guiAccessors_returnTestConfigValues() throws Exception {
        ConfigXml configXml = newLoadedConfigXml();
        assertEquals("test-api-key", configXml.getApiKey());
        assertEquals(Integer.valueOf(8384), configXml.getWebGuiBindPort());
        assertEquals("https://127.0.0.1:8384", configXml.getWebGuiUrl().toString());
    }
}
