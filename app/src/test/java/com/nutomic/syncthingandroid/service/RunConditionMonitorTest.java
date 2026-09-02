package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.RunConditionMonitor.OnShouldRunChangedListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for the run condition decision logic.
 * The force start/stop pref short-circuits all network checks, which makes
 * these tests deterministic without simulated networks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = SyncthingApp.class, shadows = ShadowContentResolverWithSyncObserver.class)
public class RunConditionMonitorTest {

    private RunConditionMonitor mMonitor;

    private final AtomicInteger mShouldRunDecisions = new AtomicInteger(0);
    private volatile boolean mLastShouldRun = false;

    private final OnShouldRunChangedListener mShouldRunListener =
            shouldRun -> {
                mLastShouldRun = shouldRun;
                mShouldRunDecisions.incrementAndGet();
            };

    private final RunConditionMonitor.OnSyncPreconditionChangedListener mPreconditionListener =
            runConditionMonitor -> { };

    @Before
    public void setUp() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit().clear().commit();
        mShouldRunDecisions.set(0);
        mLastShouldRun = false;
        mMonitor = new RunConditionMonitor(
                ApplicationProvider.getApplicationContext(),
                mShouldRunListener,
                mPreconditionListener);
    }

    @After
    public void tearDown() {
        mMonitor.shutdown();
    }

    @Test
    public void forceStart_prefOverrunsNetworkConditions() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit()
                .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_START)
                .commit();

        mMonitor.updateShouldRunDecision();

        assertTrue(mLastShouldRun);
    }

    @Test
    public void forceStop_prefPreventsRunning() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit()
                .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_STOP)
                .commit();

        mMonitor.updateShouldRunDecision();

        assertFalse(mLastShouldRun);
    }

    @Test
    public void noConditionsMet_doesNotRun() {
        // No forced state, no network => all conditions unmet.
        mMonitor.updateShouldRunDecision();

        assertFalse(mLastShouldRun);
    }

    @Test
    public void customSyncConditionsPause_nullWhenDisabled() {
        assertNull(mMonitor.getCustomSyncConditionsPause("folder-x"));
    }

    @Test
    public void customSyncConditionsPause_pausesWhenConditionsUnmet() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit()
                .putBoolean(Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS("folder-x"), true)
                .commit();

        // Without any network and default power source conditions, the object must pause.
        Boolean paused = mMonitor.getCustomSyncConditionsPause("folder-x");
        if (paused == null) {
            fail("getCustomSyncConditionsPause returned null although custom conditions are enabled");
        }
        assertTrue(paused);
    }
}
