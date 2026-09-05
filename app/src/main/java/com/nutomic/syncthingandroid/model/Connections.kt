package com.nutomic.syncthingandroid.model

/** Public fields on purpose: Gson reflective binding + direct field access from Java tests. */
class Connections {
    @JvmField
    var total: Connection? = null
    @JvmField
    var connections: Map<String, Connection>? = null
}
