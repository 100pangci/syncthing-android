package com.nutomic.syncthingandroid.model

/**
 * Sources:
 * - https://github.com/syncthing/syncthing/tree/master/lib/config
 * - https://github.com/syncthing/syncthing/blob/master/lib/config/guiconfiguration.go
 */
class Gui {
    @JvmField
    var enabled: Boolean = true

    @JvmField
    var useTLS: Boolean = false

    @JvmField
    var address: String? = "127.0.0.1:8384"
    @JvmField
    var user: String? = null
    @JvmField
    var password: String? = null
    @JvmField
    var apiKey: String? = null

    /**
     * Available: default, dark, black
     */
    @JvmField
    var theme: String = "default"

    @JvmField
    var insecureAdminAccess: Boolean = false
    @JvmField
    var insecureAllowFrameLoading: Boolean = false
    @JvmField
    var insecureSkipHostCheck: Boolean = false

    /**
     * The bind host part of {@link #address}, or "" if unset or malformed.
     */
    val bindAddress: String
        get() {
            val address = address
            if (address == null) return ""
            val split = address.split(":")
            return split.firstOrNull() ?: ""
        }

    /**
     * The bind port part of {@link #address}, or "" if unset or malformed.
     */
    val bindPort: String
        get() {
            val address = address
            if (address == null) return ""
            val split = address.split(":")
            return if (split.size < 2) "" else split[1]
        }
}
