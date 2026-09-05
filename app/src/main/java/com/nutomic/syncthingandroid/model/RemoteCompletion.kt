package com.nutomic.syncthingandroid.model

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.nutomic.syncthingandroid.util.Util
import java.util.AbstractMap

/**
 * This class caches remote folder and device synchronization
 * completion indicators defined in {@link RemoteCompletionInfo}
 * according to syncthing's REST "/completion" JSON result schema.
 * Completion model of syncthing's web UI is completion[deviceId][folderId]
 */
class RemoteCompletion(enableVerboseLog: Boolean) {

    private var ENABLE_DEBUG_LOG = false
    private val ENABLE_VERBOSE_LOG = enableVerboseLog

    private val deviceFolderMap:
        MutableMap<String, Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>>> = HashMap()

    /**
     * Object that must be locked upon accessing deviceFolderMap.
     */
    private val deviceFolderMapLock = Any()

    /**
     * Removes a folder from the cache model.
     */
    private fun removeFolder(folderId: String) {
        synchronized(deviceFolderMapLock) {
            for (folderMapEntry in deviceFolderMap.values) {
                val folderMap = folderMapEntry.value
                if (folderMap.containsKey(folderId)) {
                    folderMap.remove(folderId)
                    break
                }
            }
        }
    }

    /**
     * Updates device and folder information in the cache model
     * after a config update.
     */
    fun updateFromConfig(newDevices: List<Device>, newFolders: List<Folder>) {
        synchronized(deviceFolderMapLock) {
            // Handle devices that were removed from the config.
            val removedDevices = ArrayList<String>()
            for (deviceId in deviceFolderMap.keys) {
                var deviceFound = false
                for (device in newDevices) {
                    if (device.deviceID == deviceId) {
                        deviceFound = true
                        break
                    }
                }
                if (!deviceFound) {
                    removedDevices.add(deviceId)
                }
            }
            for (deviceId in removedDevices) {
                logV("updateFromConfig: Remove device '${getShortenedDeviceId(deviceId)}' from cache model")
                deviceFolderMap.remove(deviceId)
            }

            // Handle devices that were added to the config.
            for (device in newDevices) {
                if (!deviceFolderMap.containsKey(device.deviceID)) {
                    logV("updateFromConfig: Add device '${getShortenedDeviceId(device.deviceID)}' to cache model")
                    deviceFolderMap[device.deviceID] = AbstractMap.SimpleEntry(
                        Connection(),
                        HashMap()
                    )
                }
            }

            // Handle folders that were removed from the config.
            val removedFolders = ArrayList<String>()
            for (device in deviceFolderMap.values) {
                for (folderId in device.value.keys) {
                    var folderFound = false
                    for (folder in newFolders) {
                        if (folder.id == folderId) {
                            folderFound = true
                            break
                        }
                    }
                    if (!folderFound) {
                        removedFolders.add(folderId)
                    }
                }
            }
            for (folderId in removedFolders) {
                logV("updateFromConfig: Remove folder '$folderId' from cache model")
                removeFolder(folderId)
            }

            // Handle folders that were added to the config.
            for (folder in newFolders) {
                for (device in newDevices) {
                    if (folder.getDevice(device.deviceID) != null) {
                        // folder is shared with device.
                        val folderMap = deviceFolderMap[device.deviceID]!!.value
                        if (!folderMap.containsKey(folder.id)) {
                            logV("updateFromConfig: Add folder '${folder.id}'" +
                                " shared with device '${getShortenedDeviceId(device.deviceID)}' to cache model.")
                            folderMap[folder.id] = RemoteCompletionInfo()
                        }
                    }
                }
            }
        }
    }

    /**
     * Accumulates per-folder completion values and calculates the average percentage
     * clamped to 0-100. Takes into account that Syncthing's WebUI considers remote
     * folders with 0% and 100% completion as up-to-date.
     */
    private class CompletionAccumulator {
        private var folderCount = 0
        private var sumCompletion = 0.0

        fun accumulate(completionInfos: Iterable<RemoteCompletionInfo>) {
            for (completionInfo in completionInfos) {
                var folderCompletion = completionInfo.completion
                if (folderCompletion < 0) {
                    folderCompletion = 0.0
                } else if (folderCompletion > 100) {
                    folderCompletion = 100.0
                }
                if (folderCompletion != 0.0 && folderCompletion != 100.0) {
                    sumCompletion += folderCompletion
                    folderCount++
                }
            }
        }

        fun calculatePercentage(): Int {
            if (folderCount == 0) {
                return 100
            }
            var completion = Math.floor(sumCompletion / folderCount).toInt()
            if (completion < 0) {
                completion = 0
            } else if (completion > 100) {
                completion = 100
            }
            return completion
        }
    }

    /**
     * Calculates remote device sync completion percentage across all connected devices.
     * Returns "-1" if sync completion is not applicable.
     */
    fun getTotalDeviceCompletion(): Int {
        synchronized(deviceFolderMapLock) {
            var connectedDeviceCount = 0
            for (device in deviceFolderMap.values) {
                if (device.key.connected) {
                    connectedDeviceCount++
                }
            }
            if (connectedDeviceCount == 0) {
                return -1
            }
            val accumulator = CompletionAccumulator()
            for (device in deviceFolderMap.values) {
                if (!device.key.connected) {
                    continue
                }
                accumulator.accumulate(device.value.values)
            }
            return accumulator.calculatePercentage()
        }
    }

    /**
     * Calculates remote device sync completion percentage across all folders
     * shared with the device.
     */
    fun getDeviceCompletion(deviceId: String): Int {
        synchronized(deviceFolderMapLock) {
            if (!deviceFolderMap.containsKey(deviceId)) {
                logV("getDeviceCompletion: Cache miss for deviceId=[$deviceId]")
                return 100
            }

            val folderMap = deviceFolderMap[deviceId]!!.value
            if (folderMap != null) {
                val accumulator = CompletionAccumulator()
                accumulator.accumulate(folderMap.values)
                return accumulator.calculatePercentage()
            }
            return 100
        }
    }

    fun getDeviceNeedBytes(deviceId: String): Double {
        synchronized(deviceFolderMapLock) {
            if (!deviceFolderMap.containsKey(deviceId)) {
                logV("getDeviceNeedBytes: Cache miss for deviceId=[$deviceId]")
                return 0.0
            }

            var sumNeedBytes = 0.0
            val folderMap = deviceFolderMap[deviceId]!!.value
            if (folderMap != null) {
                for (folder in folderMap.values) {
                    sumNeedBytes += folder.needBytes
                }
            }
            return sumNeedBytes
        }
    }

    /**
     * Set completionInfo within the completion[deviceId][folderId] model.
     */
    fun setCompletionInfo(deviceId: String, folderId: String, completionInfo: RemoteCompletionInfo) {
        synchronized(deviceFolderMapLock) {
            // Add device parent node if it does not exist.
            if (!deviceFolderMap.containsKey(deviceId)) {
                deviceFolderMap[deviceId] = AbstractMap.SimpleEntry(
                    Connection(),
                    HashMap()
                )
            }
            logV("setCompletionInfo: Storing ${completionInfo.completion}% for folder \"" +
                "$folderId\" at device \"" +
                "${getShortenedDeviceId(deviceId)}\".")
            // Add folder or update existing folder entry.
            deviceFolderMap[deviceId]!!.value[folderId] = completionInfo
        }
    }

    /**
     * Returns remote device status.
     */
    fun getDeviceStatus(deviceId: String): Connection {
        synchronized(deviceFolderMapLock) {
            if (!deviceFolderMap.containsKey(deviceId)) {
                return Connection()
            }
            val connection = deviceFolderMap[deviceId]!!.key
            return Util.deepCopy(connection, object : TypeToken<Connection>() {}.type)
        }
    }

    fun getOnlineDeviceCount(): Int {
        synchronized(deviceFolderMapLock) {
            var onlineDeviceCount = 0
            for (device in deviceFolderMap.values) {
                if (device.key.connected) {
                    onlineDeviceCount++
                }
            }
            return onlineDeviceCount
        }
    }

    /**
     * Store remote device status for later when we need info for the UI.
     */
    fun setDeviceStatus(deviceId: String, connection: Connection) {
        synchronized(deviceFolderMapLock) {
            // Add device parent node if it does not exist.
            if (!deviceFolderMap.containsKey(deviceId)) {
                deviceFolderMap[deviceId] = AbstractMap.SimpleEntry(
                    Connection(),
                    HashMap()
                )
            }

            if (ENABLE_DEBUG_LOG) {
                Log.d(TAG, "setDeviceStatus: deviceId=\"$deviceId\"" +
                    ", connected=${connection.connected}" +
                    ", paused=${connection.paused}")
            }

            // Update device status information.
            val updatedEntry: Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>> =
                AbstractMap.SimpleEntry(
                    Util.deepCopy(connection, object : TypeToken<Connection>() {}.type),
                    Util.deepCopy(
                        deviceFolderMap[deviceId]!!.value,
                        object : TypeToken<HashMap<String, RemoteCompletionInfo>>() {}.type
                    )
                )
            deviceFolderMap[deviceId] = updatedEntry
        }
    }

    /**
     * Returns the first characters of the device ID for logging purposes.
     */
    fun getShortenedDeviceId(deviceId: String): String {
        return if (deviceId.isEmpty()) "" else deviceId.substring(0, 7)
    }

    private fun logV(logMessage: String) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage)
        }
    }

    companion object {
        private const val TAG = "RemoteCompletion"
    }
}
