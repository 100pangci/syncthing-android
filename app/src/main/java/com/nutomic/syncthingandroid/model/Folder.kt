package com.nutomic.syncthingandroid.model

import android.text.TextUtils
import com.nutomic.syncthingandroid.service.Constants

/**
 * Sources:
 * - https://github.com/syncthing/syncthing/tree/master/lib/config
 * - https://github.com/syncthing/syncthing/blob/master/lib/config/folderconfiguration.go
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class Folder {
    // Folder Configuration
    @JvmField
    var group: String = ""
    @JvmField
    var id: String = ""
    @JvmField
    var label: String = ""
    @JvmField
    var filesystemType: String = "basic"
    @JvmField
    var path: String = ""
    @JvmField
    var type: String = Constants.FOLDER_TYPE_SEND_RECEIVE
    @JvmField
    var fsWatcherEnabled: Boolean = true
    @JvmField
    var fsWatcherDelayS: Float = 10f

    private var devices: MutableList<SharedWithDevice> = ArrayList()

    /**
     * Folder rescan interval defaults to 3600s as it is the default in
     * syncthing when the file watcher is enabled and a new folder is created.
     */
    @JvmField
    var rescanIntervalS: Int = 3600
    @JvmField
    var ignorePerms: Boolean = true
    @JvmField
    var autoNormalize: Boolean = true
    @JvmField
    var minDiskFree: MinDiskFree? = null
    @JvmField
    var versioning: Versioning? = null
    @JvmField
    var copiers: Int = 0
    @JvmField
    var pullerMaxPendingKiB: Int = 0
    @JvmField
    var hashers: Int = 0
    @JvmField
    var order: String = "random"
    @JvmField
    var ignoreDelete: Boolean = false
    @JvmField
    var scanProgressIntervalS: Int = 0
    @JvmField
    var pullerPauseS: Int = 0
    @JvmField
    var maxConflicts: Int = 10
    @JvmField
    var disableSparseFiles: Boolean = false
    @JvmField
    var paused: Boolean = false
    @JvmField
    var markerName: String = Constants.FILENAME_STFOLDER

    // Since v1.1.0
    @JvmField
    var copyOwnershipFromParent: Boolean? = false

    // Since v1.2.1, see PR #5852
    @JvmField
    var modTimeWindowS: Int = 0

    // Since v1.6.0
    // see PR #6587: "inorder", "random", "standard"
    @JvmField
    var blockPullOrder: String = "standard"
    // see PR #6588
    @JvmField
    var disableFsync: Boolean? = false
    // see PR #6573, #10200
    @JvmField
    var maxConcurrentWrites: Int = 0

    // Since v1.8.0
    // see PR #6746: "all", "copy_file_range", "duplicate_extents", "ioctl", "sendfile", "standard"
    @JvmField
    var copyRangeMethod: String = "standard"

    // Since v1.9.0
    @JvmField
    var caseSensitiveFS: Boolean? = false

    // Since v1.21.0
    @JvmField
    var syncOwnership: Boolean? = false
    @JvmField
    var sendOwnership: Boolean? = false

    // Since v1.22.0
    @JvmField
    var syncXattrs: Boolean? = false
    @JvmField
    var sendXattrs: Boolean? = false

    // Since v2.1.0
    @JvmField
    var blockIndexing: Boolean? = true

    // Folder Status
    @JvmField
    var invalid: String? = null

    class Versioning {
        @JvmField
        var type: String? = null
        @JvmField
        var cleanupIntervalS: Int = 0
        @JvmField
        var params: MutableMap<String, String> = HashMap()
        // Since v1.14.0
        @JvmField
        var fsPath: String? = null
        @JvmField
        var fsType: String? = null
    }

    class MinDiskFree {
        @JvmField
        var value: Float = 1f
        @JvmField
        var unit: String = "%"
    }

    fun addDevice(device: Device) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(device.deviceID)

        val d = SharedWithDevice()
        d.deviceID = device.deviceID
        d.introducedBy = device.introducedBy
        devices.add(d)
    }

    fun addDevice(sharedWithDevice: SharedWithDevice) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(sharedWithDevice.deviceID)

        val d = SharedWithDevice()
        d.deviceID = sharedWithDevice.deviceID
        d.encryptionPassword = sharedWithDevice.encryptionPassword
        d.introducedBy = sharedWithDevice.introducedBy
        devices.add(d)
    }

    fun getSharedWithDevices(): List<SharedWithDevice> {
        return devices
    }

    /**
     * Note: This is expected to return "1" if a folder is not shared with any
     * other device. Syncthing's config will list ourself as the only device
     * sub node which is associated with the folder. This will return >= 2
     * if the folder is shared with other devices.
     */
    fun getDeviceCount(): Int {
        if (devices == null) {
            return 1
        }
        return devices.size
    }

    fun getDevice(deviceId: String): SharedWithDevice? {
        for (d in devices) {
            if (d.deviceID == deviceId) {
                return d
            }
        }
        return null
    }

    fun removeDevice(deviceId: String) {
        val it = devices.iterator()
        while (it.hasNext()) {
            val currentId = it.next().deviceID
            if (currentId == deviceId) {
                it.remove()
            }
        }
    }

    override fun toString(): String {
        return if (TextUtils.isEmpty(label)) {
            if (TextUtils.isEmpty(id)) "" else id
        } else {
            label
        }
    }

    companion object {
        /**
         * Compares folders by labels, uses the folder ID as fallback if the label is empty.
         */
        @JvmField
        val LABEL_COMPARATOR: Comparator<Folder> = Comparator { lhs, rhs ->
            val lhsLabel = if (lhs.label != null && !lhs.label.isEmpty()) lhs.label else lhs.id ?: ""
            val rhsLabel = if (rhs.label != null && !rhs.label.isEmpty()) rhs.label else rhs.id ?: ""
            lhsLabel.compareTo(rhsLabel)
        }
    }
}
