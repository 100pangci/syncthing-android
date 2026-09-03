package com.nutomic.syncthingandroid.model

class Config {
    @JvmField
    var version: Int = 0
    @JvmField
    var devices: List<Device>? = null
    @JvmField
    var folders: List<Folder>? = null
    @JvmField
    var gui: Gui? = null
    @JvmField
    var options: Options? = null
    @JvmField
    var defaults: Defaults? = null
    @JvmField
    var remoteIgnoredDevices: List<RemoteIgnoredDevice>? = null
}
