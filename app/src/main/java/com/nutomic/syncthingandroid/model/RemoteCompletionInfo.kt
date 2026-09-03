package com.nutomic.syncthingandroid.model

/**
 * Caches information frequently needed by the wrapper
 * to save expensive calls to Syncthing's REST API.
 * Vars in class do not correspond to JSON results.
 */
class RemoteCompletionInfo {
    @JvmField
    var completion: Double = 100.0
    @JvmField
    var needBytes: Double = 0.0
}
