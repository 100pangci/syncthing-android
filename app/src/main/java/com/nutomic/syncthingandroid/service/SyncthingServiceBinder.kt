package com.nutomic.syncthingandroid.service

import android.os.Binder

class SyncthingServiceBinder(val service: SyncthingService) : Binder()
