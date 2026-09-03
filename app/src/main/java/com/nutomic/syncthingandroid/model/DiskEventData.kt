package com.nutomic.syncthingandroid.model

/**
 * REST API endpoint "/rest/events/disk"
 */
class DiskEventData {
    // action = {"added", "deleted", "modified"}
    @JvmField
    var action: String = ""

    @JvmField
    var folder: String = ""
    @JvmField
    var folderID: String = ""
    @JvmField
    var label: String = ""
    @JvmField
    var modifiedBy: String = ""
    @JvmField
    var path: String = ""

    // type = {"file", "dir"}
    @JvmField
    var type: String = ""
}
