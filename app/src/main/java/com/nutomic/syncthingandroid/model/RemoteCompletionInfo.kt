package com.nutomic.syncthingandroid.model

/**
 * Caches information frequently needed by the wrapper
 * to save expensive calls to Syncthing's REST API.
 * Vars in class do not correspond to JSON results.
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class RemoteCompletionInfo {
    @JvmField
    var completion: Double = 100.0
    @JvmField
    var needBytes: Double = 0.0
}
