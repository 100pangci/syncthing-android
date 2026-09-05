package com.nutomic.syncthingandroid.model

/**
 * To avoid name confusion:
 * This is the exclude and include items list associated with every folder.
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class FolderIgnoreList {
    @JvmField
    var expanded: Array<String>? = null
    @JvmField
    var ignore: Array<String>? = null
}
