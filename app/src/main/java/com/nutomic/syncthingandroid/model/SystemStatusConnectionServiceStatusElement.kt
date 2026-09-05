package com.nutomic.syncthingandroid.model

/**
 * REST API endpoint "/rest/system/status"
 * Part of JSON answer in field {@link SystemStatus#connectionServiceStatus}
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class SystemStatusConnectionServiceStatusElement {
    @JvmField
    var error: String? = null
    @JvmField
    var lanAddresses: List<String>? = null
    @JvmField
    var wanAddresses: List<String>? = null
}
