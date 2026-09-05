package com.nutomic.syncthingandroid.model

import android.text.TextUtils

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class SharedWithDevice {
    @JvmField
    var deviceID: String = ""
    @JvmField
    var introducedBy: String = ""

    /**
     * Since v1.12.0
     * See https://github.com/syncthing/syncthing/pull/7055
     */
    @JvmField
    var encryptionPassword: String = ""

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    val displayName: String
        get() {
            return if (TextUtils.isEmpty(deviceID)) "" else deviceID.substring(0, 7)
        }
}
