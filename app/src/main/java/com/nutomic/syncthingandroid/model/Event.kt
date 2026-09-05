package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class Event {
    @JvmField
    var id: Int = 0
    @JvmField
    var globalID: Int = 0
    @JvmField
    var type: String? = null
    @JvmField
    var time: String? = null
    @JvmField
    var data: Map<String, Any>? = null
}
