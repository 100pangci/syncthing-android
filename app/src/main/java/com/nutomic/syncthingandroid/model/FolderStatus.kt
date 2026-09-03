package com.nutomic.syncthingandroid.model

class FolderStatus {
    @JvmField
    var error: String = ""
    @JvmField
    var errors: Long = 0
    @JvmField
    var globalBytes: Long = 0
    @JvmField
    var globalDeleted: Long = 0
    @JvmField
    var globalDirectories: Long = 0
    @JvmField
    var globalFiles: Long = 0
    @JvmField
    var globalSymlinks: Long = 0
    @JvmField
    var globalTotalItems: Long = 0
    @JvmField
    var ignorePatterns: Boolean = false
    @JvmField
    var inSyncBytes: Long = 0
    @JvmField
    var inSyncFiles: Long = 0
    @JvmField
    var invalid: String = ""
    @JvmField
    var localBytes: Long = 0
    @JvmField
    var localDeleted: Long = 0
    @JvmField
    var localDirectories: Long = 0
    @JvmField
    var localFiles: Long = 0
    @JvmField
    var localSymlinks: Long = 0
    @JvmField
    var localTotalItems: Long = 0
    @JvmField
    var needBytes: Long = 0
    @JvmField
    var needDeletes: Long = 0
    @JvmField
    var needDirectories: Long = 0
    @JvmField
    var needFiles: Long = 0
    @JvmField
    var needSymlinks: Long = 0
    @JvmField
    var needTotalItems: Long = 0
    @JvmField
    var pullErrors: Long = 0
    @JvmField
    var receiveOnlyChangedBytes: Long = 0
    @JvmField
    var receiveOnlyChangedDeletes: Long = 0
    @JvmField
    var receiveOnlyChangedDirectories: Long = 0
    @JvmField
    var receiveOnlyChangedFiles: Long = 0
    @JvmField
    var receiveOnlyChangedSymlinks: Long = 0
    @JvmField
    var receiveOnlyTotalItems: Long = 0
    @JvmField
    var sequence: Long = 0
    @JvmField
    var state: String = "idle"
    @JvmField
    var stateChanged: String = ""
    @JvmField
    var version: Long = 0
    @JvmField
    var watchError: String = ""
}
