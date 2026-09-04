package com.nutomic.syncthingandroid.service

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the event polling loop and the syncthing event to local action mapping.
 *
 * The polling loop runs on an [UnconfinedTestDispatcher]: `delay` is driven by virtual time
 * ([TestScheduler.advanceTimeBy]) and the mocked [RestApi] answers synchronously, so whole
 * poll cycles run deterministically on the test thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncthingApp::class)
class EventPollerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
    private val restApi: RestApi = mock(RestApi::class.java)
    private val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext<Context>())
    private val notificationManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var poller: EventPoller

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        poller = EventPoller(ApplicationProvider.getApplicationContext(), restApi, scope)
    }

    @After
    fun tearDown() {
        poller.stop()
        scope.cancel()
    }

    private fun event(type: String, data: HashMap<String, Any>): Event {
        val event = Event()
        event.id = 1
        event.type = type
        event.data = data as java.util.Map<String, Any>
        return event
    }

    /**
     * Stubs the id-rollback probe `getEvents(0, 1)` so it answers with [lastId].
     */
    private fun stubProbe(lastId: Long) {
        doAnswer { invocation ->
            invocation.getArgument<RestApi.OnReceiveEventListener>(2).onDone(lastId)
            null
        }.`when`(restApi).getEvents(eq(0L), eq(1L), any())
    }

    /**
     * Stubs the main fetch `getEvents(<since>, 0)`: emits the given events, then completes
     * with [lastId].
     */
    private fun stubFetch(lastId: Long, vararg events: Event) {
        doAnswer { invocation ->
            val listener = invocation.getArgument<RestApi.OnReceiveEventListener>(2)
            events.forEach { listener.onEvent(it, JsonObject()) }
            listener.onDone(lastId)
            null
        }.`when`(restApi).getEvents(anyLong(), eq(0L), any())
    }

    /**
     * Runs one full poll cycle: the 5 s delay, the probe, the main fetch and the scheduling
     * of the next cycle.
     */
    private fun runPollCycle() {
        testScheduler.runCurrent()   // let a freshly started loop reach its delay
        testScheduler.advanceTimeBy(EventPoller.EVENT_UPDATE_INTERVAL)
        testScheduler.runCurrent()
    }

    private fun persistLastId(id: Long) {
        prefs.edit()
                .putLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, id)
                .commit()
    }

    // region Polling loop

    @Test
    fun start_delaysFirstPollByInterval() {
        poller.start()

        // The first poll must not fire immediately, only after EVENT_UPDATE_INTERVAL.
        verify(restApi, never()).getEvents(anyLong(), anyLong(), any())

        stubProbe(lastId = 0)
        stubFetch(lastId = 0)
        runPollCycle()

        verify(restApi).getEvents(eq(0L), eq(1L), any())
    }

    @Test
    fun pollCycle_probesThenFetches_andPersistsLastId() {
        // Probe id must stay >= the fetch's lastId, or the rollback check would
        // (correctly) restart polling from zero.
        stubProbe(lastId = 200)
        stubFetch(
                lastId = 102,
                event("StateChanged", hashMapOf("folder" to "f", "to" to "idle")),
                event("StateChanged", hashMapOf("folder" to "f", "to" to "syncing")),
        )
        poller.start()

        runPollCycle()

        // Fresh start (empty prefs): probe first (0, 1), then fetch everything from zero.
        verify(restApi).getEvents(eq(0L), eq(1L), any())
        verify(restApi).getEvents(eq(0L), eq(0L), any())
        // The probe id only serves the rollback check, it is not a fetch starting point.
        assertEquals(
                102L,
                prefs.getLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, 0)
        )

        // The next cycle starts over with the probe and resumes fetching from 102.
        runPollCycle()
        verify(restApi, times(2)).getEvents(eq(0L), eq(1L), any())
        verify(restApi).getEvents(eq(102L), eq(0L), any())
    }

    @Test
    fun pollCycle_restoresLastIdFromPrefs() {
        persistLastId(100)
        stubProbe(lastId = 200)
        stubFetch(lastId = 200)
        poller.start()

        runPollCycle()

        verify(restApi).getEvents(eq(100L), eq(0L), any())
    }

    @Test
    fun pollCycle_idRanBackwards_restartsFromZero() {
        persistLastId(100)
        stubProbe(lastId = 5)
        stubFetch(lastId = 5)
        poller.start()

        runPollCycle()

        // Syncthing was restarted, so polling resumes from the beginning.
        verify(restApi).getEvents(eq(0L), eq(0L), any())
    }

    @Test
    fun pollCycle_fetchReturnsLowerId_lastIdNotRegressed() {
        persistLastId(100)
        stubProbe(lastId = 200)
        stubFetch(lastId = 50)
        poller.start()

        runPollCycle()

        // onDone ids lower than the current one must neither overwrite the field nor persist.
        verify(restApi).getEvents(eq(100L), eq(0L), any())
        assertEquals(
                100L,
                prefs.getLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, 0)
        )

        // And the next cycle still polls from 100.
        runPollCycle()
        verify(restApi, times(2)).getEvents(eq(100L), eq(0L), any())
    }

    @Test
    fun onError_retriesAfterInterval() {
        doAnswer { invocation ->
            invocation.getArgument<RestApi.OnReceiveEventListener>(2).onError()
            null
        }.`when`(restApi).getEvents(eq(0L), eq(1L), any())
        poller.start()

        runPollCycle()

        verify(restApi, times(1)).getEvents(eq(0L), eq(1L), any())
        runPollCycle()

        verify(restApi, times(2)).getEvents(eq(0L), eq(1L), any())
    }

    @Test
    fun stop_stopsPolling() {
        stubProbe(lastId = 0)
        stubFetch(lastId = 0)
        poller.start()

        runPollCycle()
        verify(restApi, times(1)).getEvents(eq(0L), eq(1L), any())

        poller.stop()
        runPollCycle()
        runPollCycle()
        verify(restApi, times(1)).getEvents(eq(0L), eq(1L), any())
    }

    @Test
    fun startTwice_onlyOneLoopRuns() {
        stubProbe(lastId = 0)
        stubFetch(lastId = 0)
        poller.start()
        poller.start()

        runPollCycle()

        verify(restApi, times(1)).getEvents(eq(0L), eq(1L), any())
    }

    // endregion

    // region Event to action mapping

    @Test
    fun configSaved_reloadsConfig() {
        poller.onEvent(event("ConfigSaved", hashMapOf()), JsonObject())

        verify(restApi).reloadConfig()
    }

    @Test
    fun deviceConnectivityEvents_areForwarded() {
        poller.onEvent(
                event("DeviceConnected", hashMapOf("id" to "DEVICE-1")),
                JsonObject()
        )
        poller.onEvent(
                event("DeviceDisconnected", hashMapOf("id" to "DEVICE-1")),
                JsonObject()
        )

        verify(restApi).updateRemoteDeviceConnected("DEVICE-1", true)
        verify(restApi).updateRemoteDeviceConnected("DEVICE-1", false)
    }

    @Test
    fun ignoredEvents_areNotForwardedToRestApi() {
        val ignoredTypes = arrayOf(
                "DeviceDiscovered",
                "DownloadProgress",
                "FolderScanProgress",
                "FolderWatchStateChanged",
                "ItemStarted",
                "ListenAddressesChanged",
                "LoginAttempt",
                "RemoteDownloadProgress",
        )
        for (type in ignoredTypes) {
            // Include a payload that would trigger follow-up actions if the event
            // accidentally fell through to the RemoteIndexUpdated handler.
            poller.onEvent(
                    event(
                            type,
                            hashMapOf(
                                    "device" to "DEVICE-1",
                                    "folder" to "folder-a",
                                    "items" to 5.0,
                            )
                    ),
                    JsonObject()
            )
        }

        verify(restApi, never()).setRemoteIndexUpdated(anyString(), anyString(), anyBoolean())
    }

    @Test
    fun remoteIndexUpdated_withItems_isForwarded() {
        poller.onEvent(
                event(
                        "RemoteIndexUpdated",
                        hashMapOf(
                                "device" to "DEVICE-1",
                                "folder" to "folder-a",
                                "items" to 5.0,
                        )
                ),
                JsonObject()
        )

        verify(restApi).setRemoteIndexUpdated("DEVICE-1", "folder-a", true)
    }

    @Test
    fun remoteIndexUpdated_withZeroItems_isNotForwarded() {
        poller.onEvent(
                event(
                        "RemoteIndexUpdated",
                        hashMapOf(
                                "device" to "DEVICE-1",
                                "folder" to "folder-a",
                                "items" to 0.0,
                        )
                ),
                JsonObject()
        )

        verify(restApi, never()).setRemoteIndexUpdated(anyString(), anyString(), anyBoolean())
    }

    @Test
    fun pendingFoldersChanged_withoutDeviceId_doesNotCrashAndNotifies() {
        val pendingFolder = hashMapOf<String, Any>("folderID" to "folder-a")
        poller.onEvent(
                event("PendingFoldersChanged", hashMapOf("added" to arrayListOf(pendingFolder))),
                JsonObject()
        )
        // Must not throw (regression: null dereference before the null check).
    }

    @Test
    fun folderErrors_insufficientSpace_postsCrashNotification() {
        val json = JsonParser.parseString(
                "{\"data\": {\"errors\": [{\"error\": \"insufficient space in basic folder\", " +
                        "\"path\": \"/storage/emulated/0/Sync/file.txt\"}]}}")
                .getAsJsonObject()

        poller.onEvent(event("FolderErrors", hashMapOf()), json)

        assertTrue(notificationManager.activeNotifications.size >= 1)
    }

    // endregion
}
