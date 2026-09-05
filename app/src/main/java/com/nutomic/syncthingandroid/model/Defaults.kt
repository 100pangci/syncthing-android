package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class Defaults {
    @JvmField
    var device: Device? = null
    @JvmField
    var folder: Folder? = null
    @JvmField
    var ignores: Ignores? = null
}
