package com.nutomic.syncthingandroid.model

/**
 * /rest/db/completion?device=deviceId&folder=folderId
 */
class CompletionInfo {
    @JvmField
    var completion: Double = 0.0
    @JvmField
    var globalBytes: Double = 0.0
    @JvmField
    var globalItems: Double = 0.0
    @JvmField
    var needBytes: Double = 0.0
    @JvmField
    var needDeletes: Double = 0.0
    @JvmField
    var needItems: Double = 0.0
    @JvmField
    var remoteState: String = "unknown"
    @JvmField
    var sequence: Long = 0
}
