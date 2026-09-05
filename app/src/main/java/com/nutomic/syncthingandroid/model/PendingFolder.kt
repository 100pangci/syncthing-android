package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class PendingFolder {
    @JvmField
    var label: String = ""
    @JvmField
    var time: String = ""
    @JvmField
    var receiveEncrypted: Boolean = false
    @JvmField
    var remoteEncrypted: Boolean = false
}
