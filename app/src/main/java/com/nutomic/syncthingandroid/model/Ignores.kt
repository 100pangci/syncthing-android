package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class Ignores {
    @JvmField
    var line: List<String>? = null
}
