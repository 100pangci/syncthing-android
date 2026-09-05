package com.nutomic.syncthingandroid.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent

import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.nutomic.syncthingandroid.util.JobUtils

/**
 * SyncTriggerJobService to be scheduled by the JobScheduler.
 * See [JobUtils.scheduleSyncTriggerServiceJob] for more details.
 */
class SyncTriggerJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        val intent = Intent(RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED)

        // if Syncthing should start, forward this information to SyncTriggerReceiver
        // otherwise Syncthing will stop
        if (params.extras?.getInt(RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW, 0) == 1) {
            intent.putExtra(RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true)
        }
        localBroadcastManager.sendBroadcast(intent)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }
}
