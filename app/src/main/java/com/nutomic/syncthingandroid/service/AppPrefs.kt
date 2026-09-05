package com.nutomic.syncthingandroid.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import androidx.preference.PreferenceManager

/**
 * Provides preference getters and setters.
 */
object AppPrefs {
    private const val TAG = "AppPrefs"

    private const val PREF_VERBOSE_LOG_DEFAULT = false

    fun getPrefVerboseLog(context: Context?): Boolean {
        if (context == null) {
            Log.e(TAG, "getPrefVerboseLog: context == null")
            return PREF_VERBOSE_LOG_DEFAULT
        }
        return getPrefVerboseLog(PreferenceManager.getDefaultSharedPreferences(context))
    }

    fun getPrefVerboseLog(sharedPreferences: SharedPreferences?): Boolean {
        if (sharedPreferences == null) {
            Log.e(TAG, "getPrefVerboseLog: sharedPreferences == null")
            return PREF_VERBOSE_LOG_DEFAULT
        }
        return sharedPreferences.getBoolean(Constants.PREF_VERBOSE_LOG, PREF_VERBOSE_LOG_DEFAULT)
    }

    // BootReceiver.java (migrated in phase11) calls this statically.
    @JvmStatic
    fun getStartServiceOnBoot(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
    }
}
