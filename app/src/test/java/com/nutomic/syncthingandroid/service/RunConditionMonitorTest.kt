package com.nutomic.syncthingandroid.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.BatteryManager
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock

/**
 * Unit tests for the run condition decision logic (phase4 Kotlin port).
 *
 * The force start/stop pref short-circuits all network checks, which makes
 * those tests deterministic without simulated networks. The network-dependent
 * paths are simulated through Robolectric's ShadowConnectivityManager:
 * connecting the default network to WIFI capabilities + NET_CAPABILITY_INTERNET
 * makes isWifiOrEthernetConnection() return true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SyncthingApp::class, shadows = [ShadowContentResolverWithSyncObserver::class])
class RunConditionMonitorTest {

    private lateinit var monitor: RunConditionMonitor

    private val shouldRunDecisions = AtomicInteger(0)

    @Volatile
    private var lastShouldRun = false

    private val shouldRunListener = object : RunConditionMonitor.OnShouldRunChangedListener {
        override fun onShouldRunDecisionChanged(shouldRun: Boolean) {
            lastShouldRun = shouldRun
            shouldRunDecisions.incrementAndGet()
        }
    }

    private val preconditionListener = object : RunConditionMonitor.OnSyncPreconditionChangedListener {
        override fun onSyncPreconditionChanged(runConditionMonitor: RunConditionMonitor) = Unit
    }

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        shouldRunDecisions.set(0)
        lastShouldRun = false
    }

    @After
    fun tearDown() {
        if (this::monitor.isInitialized) {
            monitor.shutdown()
        }
    }

    private fun prefs(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(
            ApplicationProvider.getApplicationContext<Context>()
        )

    private fun app(): Context = ApplicationProvider.getApplicationContext()

    private fun createMonitor() {
        monitor = RunConditionMonitor(app(), shouldRunListener, preconditionListener)
    }

    /**
     * Simulates a (non-)connected default WIFI network. The wifi capabilities
     * satisfy isWifiOrEthernetConnection() on the API 24+ code path.
     */
    private fun setWifiConnected(connected: Boolean) {
        val cm = app().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(cm)
        shadow.setDefaultNetworkActive(connected)
        if (connected) {
            shadow.setActiveNetworkInfo(
                ShadowNetworkInfo.newInstance(
                    NetworkInfo.DetailedState.CONNECTED,
                    ConnectivityManager.TYPE_WIFI,
                    0,
                    true,
                    true
                )
            )
            val network = cm.activeNetwork
            assertNotNull(network)
            val caps = ShadowNetworkCapabilities.newInstance()
            shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadow.setNetworkCapabilities(network!!, caps)
        }
    }

    /**
     * Registers a sticky battery-changed broadcast, like the OS does, so
     * isCharging() reads BATTERY_PLUGGED_AC.
     */
    private fun setCharging(charging: Boolean) {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_PLUGGED, if (charging) {
                BatteryManager.BATTERY_PLUGGED_AC
            } else {
                0
            })
        app().sendStickyBroadcast(intent)
    }

    private fun setForceStartStop(state: Int) {
        prefs().edit()
            .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, state)
            .commit()
    }

    @Test
    fun forceStart_prefOverrunsNetworkConditions() {
        setForceStartStop(Constants.BTNSTATE_FORCE_START)
        createMonitor()

        monitor.updateShouldRunDecision()

        assertTrue(lastShouldRun)
        assertEquals(app().getString(R.string.reason_force_start), monitor.getRunDecisionExplanation())
    }

    @Test
    fun forceStop_prefPreventsRunning() {
        setForceStartStop(Constants.BTNSTATE_FORCE_STOP)
        setWifiConnected(true)
        createMonitor()

        monitor.updateShouldRunDecision()

        assertFalse(lastShouldRun)
        assertEquals(app().getString(R.string.reason_force_stop), monitor.getRunDecisionExplanation())
    }

    @Test
    fun noConditionsMet_doesNotRun() {
        // No forced state, no network => all conditions unmet.
        createMonitor()

        monitor.updateShouldRunDecision()

        assertFalse(lastShouldRun)
    }

    @Test
    fun wifiConnected_runsOnConstruction() {
        setWifiConnected(true)
        createMonitor()

        assertTrue(lastShouldRun)
        assertTrue(monitor.getRunDecisionExplanation().contains(app().getString(R.string.reason_on_wifi)))
        assertEquals(1, shouldRunDecisions.get())
    }

    @Test
    fun wifiDisconnect_stopsAndRestore_resumes() {
        // Core acceptance path: WiFi disconnect => sync stops, WiFi restore => sync resumes.
        setWifiConnected(true)
        createMonitor()
        assertTrue(lastShouldRun)

        // WiFi disconnects.
        setWifiConnected(false)
        monitor.updateShouldRunDecision()
        assertFalse(lastShouldRun)
        assertEquals(2, shouldRunDecisions.get())

        // WiFi reconnects.
        setWifiConnected(true)
        monitor.updateShouldRunDecision()
        assertTrue(lastShouldRun)
        assertEquals(3, shouldRunDecisions.get())
    }

    @Test
    fun powerSaving_blocksRunWhileRespected() {
        setWifiConnected(true)
        shadowOf(
            app().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        ).setIsPowerSaveMode(true)
        createMonitor()

        assertFalse(lastShouldRun)
        assertEquals(
            app().getString(R.string.reason_not_while_power_saving),
            monitor.getRunDecisionExplanation()
        )
    }

    @Test
    fun powerSaving_ignoredWhenNotRespected() {
        prefs().edit().putBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, false).commit()
        setWifiConnected(true)
        shadowOf(
            app().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        ).setIsPowerSaveMode(true)
        createMonitor()

        assertTrue(lastShouldRun)
    }

    @Test
    fun timeSchedule_withinSleepInterval_blocksAndExpiresAfterInterval() {
        prefs().edit()
            .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
            .putLong(Constants.PREF_LAST_RUN_TIME, SystemClock.elapsedRealtime())
            .commit()
        setWifiConnected(true)
        createMonitor()

        // The last sync is recent, so we are outside the "should run" time window.
        assertFalse(lastShouldRun)
        assertEquals(0, shouldRunDecisions.get())
        assertEquals(
            String.format(
                app().getString(R.string.reason_not_within_time_frame_2),
                app().getString(R.string.reason_not_within_time_frame_0_min)
            ),
            monitor.getRunDecisionExplanation()
        )

        // Once the sleep interval has passed, the monitor must let syncthing run again.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(61))
        monitor.updateShouldRunDecision()

        assertTrue(lastShouldRun)
        assertEquals(1, shouldRunDecisions.get())
    }

    @Test
    fun powerSourceCharger_notCharging_blocksRun() {
        prefs().edit()
            .putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER)
            .commit()
        setWifiConnected(true)
        createMonitor()

        assertFalse(lastShouldRun)
        assertEquals(
            app().getString(R.string.reason_not_charging),
            monitor.getRunDecisionExplanation()
        )
    }

    @Test
    fun powerSourceCharger_chargingStickyBroadcast_letsRun() {
        prefs().edit()
            .putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER)
            .commit()
        setCharging(true)
        setWifiConnected(true)
        createMonitor()

        assertTrue(lastShouldRun)
    }

    @Test
    fun customSyncConditionsPause_nullWhenDisabled() {
        createMonitor()

        assertNull(monitor.getCustomSyncConditionsPause("folder-x"))
    }

    @Test
    fun customSyncConditionsPause_pausesWhenConditionsUnmet() {
        prefs().edit()
            .putBoolean(Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS("folder-x"), true)
            .commit()
        createMonitor()

        // Without any network and default power source conditions, the object must pause.
        val paused = monitor.getCustomSyncConditionsPause("folder-x")
        assertNotNull(paused)
        assertTrue(paused!!)
    }

    @Test
    fun customSyncConditionsPause_runsWhenWifiConnected() {
        prefs().edit()
            .putBoolean(Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS("folder-x"), true)
            .commit()
        setWifiConnected(true)
        createMonitor()

        val paused = monitor.getCustomSyncConditionsPause("folder-x")
        assertNotNull(paused)
        assertFalse(paused!!)
    }
}
