package com.nutomic.syncthingandroid.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService

/**
 * Composition locals shared by all screens of the Compose app shell.
 */
val LocalSyncthingService = staticCompositionLocalOf<SyncthingService?> { null }

val LocalServiceState = staticCompositionLocalOf { SyncthingService.State.INIT }

/**
 * Incremented whenever the service state changes; forces recomposition of collectors.
 */
val LocalServiceTick = staticCompositionLocalOf { 0 }

fun serviceApi(service: SyncthingService?): RestApi? = service?.getApi()

fun Context.appPreferences(): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(this)
