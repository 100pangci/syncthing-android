package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class DeviceStat {
    @JvmField
    var lastSeen: String = ""
}
