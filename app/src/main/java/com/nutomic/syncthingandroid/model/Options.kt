package com.nutomic.syncthingandroid.model

/**
 * Sources:
 * - https://github.com/syncthing/syncthing/tree/main/lib/config
 * - https://github.com/syncthing/syncthing/blob/main/lib/config/optionsconfiguration.go
 * Public fields on purpose: Gson reflective binding + direct field access from Java tests.
 */
class Options {
    @JvmField
    var listenAddresses: Array<String>? = null
    @JvmField
    var globalAnnounceServers: Array<String>? = null
    @JvmField
    var globalAnnounceEnabled: Boolean = true
    @JvmField
    var localAnnounceEnabled: Boolean = true
    @JvmField
    var localAnnouncePort: Int = 21027
    @JvmField
    var localAnnounceMCAddr: String? = null
    @JvmField
    var maxSendKbps: Int = 0
    @JvmField
    var maxRecvKbps: Int = 0
    @JvmField
    var reconnectionIntervalS: Int = 60
    @JvmField
    var relaysEnabled: Boolean = true
    @JvmField
    var relayReconnectIntervalM: Int = 10
    @JvmField
    var startBrowser: Boolean = false
    @JvmField
    var natEnabled: Boolean = true
    @JvmField
    var natLeaseMinutes: Int = 60
    @JvmField
    var natRenewalMinutes: Int = 30
    @JvmField
    var natTimeoutSeconds: Int = 10
    @JvmField
    var urAccepted: Int = 0
    @JvmField
    var urUniqueId: String? = null
    @JvmField
    var urURL: String = "https://data.syncthing.net/newdata"
    @JvmField
    var urPostInsecurely: Boolean = false
    @JvmField
    var urInitialDelayS: Int = 1800
    @JvmField
    var autoUpgradeIntervalH: Int = 0
    @JvmField
    var upgradeToPreReleases: Boolean = false
    @JvmField
    var keepTemporariesH: Int = 24
    @JvmField
    var cacheIgnoredFiles: Boolean = false
    @JvmField
    var progressUpdateIntervalS: Int = 5
    @JvmField
    var limitBandwidthInLan: Boolean = false
    @JvmField
    var releasesURL: String = "https://upgrades.syncthing.net/meta.json"
    @JvmField
    var alwaysLocalNets: Array<String>? = null
    @JvmField
    var overwriteRemoteDeviceNamesOnConnect: Boolean = false
    @JvmField
    var tempIndexMinBlocks: Int = 10
    @JvmField
    var setLowPriority: Boolean = true

    // Since v0.14.28
    @JvmField
    var minHomeDiskFree: MinHomeDiskFree? = null

    // Since v1.2.0
    @JvmField
    var crURL: String = "https://crash.syncthing.net/newcrash"
    @JvmField
    var crashReportingEnabled: Boolean = true
    @JvmField
    var stunKeepaliveStartS: Int = 180
    @JvmField
    var stunKeepaliveMinS: Int = 20
    @JvmField
    var stunServer: String = "default"

    // Since v1.4.0
    @JvmField
    var maxFolderConcurrency: Int = 1
    @JvmField
    var maxConcurrentIncomingRequestKiB: Int = 0

    // Since v1.10.0
    @JvmField
    var announceLANAddresses: Boolean = true

    // Since v1.11.0
    @JvmField
    var sendFullIndexOnUpgrade: Boolean = false

    // Since v1.12.0
    @JvmField
    var featureFlag: String = ""

    // Since v1.13.0
    @JvmField
    var connectionLimitEnough: Int = 0
    @JvmField
    var connectionLimitMax: Int = 0

    @JvmField
    var unackedNotificationID: String = ""

    class MinHomeDiskFree {
        @JvmField
        var value: Float = 1f
        @JvmField
        var unit: String = "%"
    }

    fun isUsageReportingAccepted(urVersionMax: Int): Boolean {
        return urAccepted == urVersionMax
    }

    fun isUsageReportingDecided(urVersionMax: Int): Boolean {
        return isUsageReportingAccepted(urVersionMax) || urAccepted == USAGE_REPORTING_DENIED
    }

    companion object {
        const val USAGE_REPORTING_UNDECIDED = 0
        const val USAGE_REPORTING_DENIED = -1
    }
}
