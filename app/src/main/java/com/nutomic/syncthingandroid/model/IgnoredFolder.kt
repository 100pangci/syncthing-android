package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class IgnoredFolder {
    @JvmField
    var id: String = ""
    @JvmField
    var label: String = ""
    @JvmField
    var time: String = ""
}
