package com.nutomic.syncthingandroid.model

/**
 * Caches information frequently needed by the wrapper
 * to save expensive calls to Syncthing's REST API.
 * Vars in class do not correspond to JSON results.
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class CachedFolderStatus {
    /**
     * Calculated
     */
    @JvmField
    var completion: Double = 100.0

    /**
     * Accessed by setters
     */
    @JvmField
    var discoveredConflictFiles: Array<String> = emptyArray()
    @JvmField
    var lastItemFinishedAction: String = ""
    @JvmField
    var lastItemFinishedItem: String = ""
    @JvmField
    var lastItemFinishedTime: String = ""
    @JvmField
    var remoteIndexUpdated: Boolean = false
    @JvmField
    var paused: Boolean = false
}
