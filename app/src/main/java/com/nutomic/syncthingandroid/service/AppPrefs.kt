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

    private const val PREF_RUN_AS_ROOT_DEFAULT = false

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

    fun getStartServiceOnBoot(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
    }

    fun getRunAsRoot(sharedPreferences: SharedPreferences?): Boolean {
        if (sharedPreferences == null) {
            Log.e(TAG, "getRunAsRoot: sharedPreferences == null")
            return PREF_RUN_AS_ROOT_DEFAULT
        }
        return sharedPreferences.getBoolean(Constants.PREF_RUN_AS_ROOT, PREF_RUN_AS_ROOT_DEFAULT)
    }

    fun getLastCoreRunAsRoot(sharedPreferences: SharedPreferences?): Boolean {
        if (sharedPreferences == null) {
            Log.e(TAG, "getLastCoreRunAsRoot: sharedPreferences == null")
            return PREF_RUN_AS_ROOT_DEFAULT
        }
        return sharedPreferences.getBoolean(Constants.PREF_LAST_CORE_RUN_AS_ROOT, PREF_RUN_AS_ROOT_DEFAULT)
    }

    fun setLastCoreRunAsRoot(sharedPreferences: SharedPreferences, value: Boolean) {
        sharedPreferences.edit().putBoolean(Constants.PREF_LAST_CORE_RUN_AS_ROOT, value).apply()
    }
}
