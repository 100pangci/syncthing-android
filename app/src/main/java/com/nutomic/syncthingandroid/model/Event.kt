package com.nutomic.syncthingandroid.model

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
    var data: java.util.Map<String, Any>? = null
}
