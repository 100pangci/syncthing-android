package com.nutomic.syncthingandroid.model

class PendingFolder {
    @JvmField
    var label: String = ""
    @JvmField
    var time: String = ""
    @JvmField
    var receiveEncrypted: Boolean? = false
    @JvmField
    var remoteEncrypted: Boolean? = false
}
