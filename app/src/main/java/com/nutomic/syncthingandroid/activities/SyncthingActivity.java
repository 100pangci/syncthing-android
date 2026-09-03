package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends ThemedAppCompatActivity implements ServiceConnection {

    private static final String TAG = "SyncthingActivity";

    /**
     * Notified whenever the service connection is established or dropped, so
     * UI code can observe the service as state instead of polling getService().
     */
    public interface OnServiceConnectionChangedListener {
        void onServiceConnectionChanged(@Nullable SyncthingService service);
    }

    private SyncthingService mSyncthingService;
    private final List<OnServiceConnectionChangedListener> mServiceConnectionListeners =
            new CopyOnWriteArrayList<>();

    public final void addOnServiceConnectionChangedListener(OnServiceConnectionChangedListener listener) {
        mServiceConnectionListeners.add(listener);
    }

    public final void removeOnServiceConnectionChangedListener(OnServiceConnectionChangedListener listener) {
        mServiceConnectionListeners.remove(listener);
    }

    private void notifyServiceConnectionChanged() {
        for (OnServiceConnectionChangedListener listener : mServiceConnectionListeners) {
            listener.onServiceConnectionChanged(mSyncthingService);
        }
    }

    @Override
    protected void onPause() {
        unbindService(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure the service is started, not only bound: deep links (e.g. the
        // "device/folder shared" notification accept actions) can launch
        // activities directly without MainActivity ever running. A bound-only
        // service never receives onStartCommand, so RunConditionMonitor would
        // never be created, Syncthing would never be launched by this process
        // and RestApi would stay null - making every config save silently fall
        // back to writing config.xml, which a running Syncthing instance never
        // re-reads.
        Intent serviceIntent = new Intent(this, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(new Intent(this, SyncthingService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = syncthingServiceBinder.getService();
        notifyServiceConnectionChanged();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
        notifyServiceConnectionChanged();
    }

    /**
     * Returns service object (or null if not bound).
     */
    public SyncthingService getService() {
        return mSyncthingService;
    }

    /**
     * Returns RestApi instance, or null if SyncthingService is not yet connected.
     */
    public RestApi getApi() {
        return (getService() != null)
                ? getService().getApi()
                : null;
    }
}
