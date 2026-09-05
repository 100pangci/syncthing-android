package com.nutomic.syncthingandroid.model

/**
 * REST API endpoint "/rest/events/disk"
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class DiskEvent {
    @JvmField
    var id: Long = 0
    @JvmField
    var globalID: Long = 0
    @JvmField
    var time: String = ""

    // type = {"LocalChangeDetected", "RemoteChangeDetected"}
    @JvmField
    var type: String = ""

    @JvmField
    var data: DiskEventData = DiskEventData()
}
