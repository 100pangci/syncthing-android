package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class PendingDevice {
    @JvmField
    var time: String = ""
    @JvmField
    var name: String = ""
    @JvmField
    var address: String = ""
}
