package com.nutomic.syncthingandroid.model

/**
 * REST API endpoint "/rest/system/status"
 * Part of JSON answer in field {@link SystemStatus#lastDialStatus}
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class SystemStatusLastDialStatusElement {
    // Example: "dial tcp4 192.168.5.1:22000: i/o timeout"
    @JvmField
    var error: String? = null

    // Example: "2019-09-21T09:10:35Z"
    var `when`: String? = null
}
