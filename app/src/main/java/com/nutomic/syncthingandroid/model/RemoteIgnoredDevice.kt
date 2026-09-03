package com.nutomic.syncthingandroid.model

import android.text.TextUtils

class RemoteIgnoredDevice {
    @JvmField
    var time: String = ""
    @JvmField
    var deviceID: String = ""
    @JvmField
    var name: String = ""
    @JvmField
    var address: String = ""

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    val displayName: String
        get() {
            return if (TextUtils.isEmpty(name)) deviceID.substring(0, 7) else name
        }
}
