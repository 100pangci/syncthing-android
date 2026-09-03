package com.nutomic.syncthingandroid.model

class Connection {
    @JvmField
    var address: String = ""
    @JvmField
    var at: String = ""
    @JvmField
    var clientVersion: String = ""
    @JvmField
    var connected: Boolean = false
    @JvmField
    var inBytesTotal: Long = 0
    @JvmField
    var outBytesTotal: Long = 0
    @JvmField
    var paused: Boolean = false
    @JvmField
    var type: String = ""

    // These fields are not sent from Syncthing. They are populated by {@link #setTransferRate}.
    @JvmField
    var inBits: Long = 0
    @JvmField
    var outBits: Long = 0

    fun setTransferRate(previous: Connection, msElapsed: Long) {
        val secondsElapsed = msElapsed / 1000
        val inBytes = 8 * (inBytesTotal - previous.inBytesTotal) / secondsElapsed
        val outBytes = 8 * (outBytesTotal - previous.outBytesTotal) / secondsElapsed
        inBits = maxOf(0, inBytes)
        outBits = maxOf(0, outBytes)
    }
}
