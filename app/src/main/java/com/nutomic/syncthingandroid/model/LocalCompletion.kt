package com.nutomic.syncthingandroid.model

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.nutomic.syncthingandroid.util.Util
import java.util.AbstractMap

/**
 * This class caches local folder synchronization
 * completion indicators defined in {@link CachedFolderStatus}
 * according to Syncthing's "FolderSummary" event JSON result schema.
 * Completion model of Syncthing's web UI is completion[folderId]
 */
class LocalCompletion(enableVerboseLog: Boolean) {

    private val folderMap: MutableMap<String, Map.Entry<FolderStatus, CachedFolderStatus>> = HashMap()

    /**
     * Object that must be locked upon accessing folderMap.
     */
    private val folderMapLock = Any()

    private val ENABLE_VERBOSE_LOG = enableVerboseLog

    /**
     * Updates folder information in the cache model
     * after a config update.
     */
    fun updateFromConfig(newFolders: List<Folder>) {
        synchronized(folderMapLock) {
            // Handle folders that were removed from the config.
            val removedFolders = ArrayList<String>()
            for (folderId in folderMap.keys) {
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
            for (folderId in removedFolders) {
                logV("updateFromConfig: Remove folder '$folderId' from cache model")
                folderMap.remove(folderId)
            }

            // Handle folders that were added to the config.
            for (folder in newFolders) {
                if (!folderMap.containsKey(folder.id)) {
                    logV("updateFromConfig: Add folder '${folder.id}' to cache model.")
                    folderMap[folder.id] = AbstractMap.SimpleEntry(FolderStatus(), CachedFolderStatus())
                }
            }
        }
    }

    /**
     * Calculates local folder sync completion percentage across all folders.
     */
    fun getTotalFolderCompletion(): Int {
        synchronized(folderMapLock) {
            var folderCount = 0
            var sumCompletion = 0.0
            for (folder in folderMap.values) {
                val cachedFolderStatus = folder.value

                // Filter invalid percentage values we may have got from the REST API.
                if (cachedFolderStatus.completion < 0) {
                    cachedFolderStatus.completion = 0.0
                } else if (cachedFolderStatus.completion > 100) {
                    cachedFolderStatus.completion = 100.0
                }

                if (!cachedFolderStatus.paused &&
                    cachedFolderStatus.completion != 100.0
                ) {
                    sumCompletion += cachedFolderStatus.completion
                    folderCount++
                }
            }
            if (folderCount == 0) {
                return 100
            }
            var totalFolderCompletion = Math.floor(sumCompletion / folderCount).toInt()
            if (totalFolderCompletion < 0) {
                totalFolderCompletion = 0
            } else if (totalFolderCompletion > 100) {
                totalFolderCompletion = 100
            }
            return totalFolderCompletion
        }
    }

    /**
     * Returns local folder status including completion info.
     */
    fun getFolderStatus(folderId: String): Map.Entry<FolderStatus, CachedFolderStatus> {
        synchronized(folderMapLock) {
            if (!folderMap.containsKey(folderId)) {
                return AbstractMap.SimpleEntry(FolderStatus(), CachedFolderStatus())
            }
            val folderEntry = folderMap[folderId]!!
            return AbstractMap.SimpleEntry(
                Util.deepCopy(folderEntry.key, object : TypeToken<FolderStatus>() {}.type),
                Util.deepCopy(folderEntry.value, object : TypeToken<CachedFolderStatus>() {}.type)
            )
        }
    }

    /**
     * Store folderStatus for later when we need info for the UI.
     * Calculate cachedFolderStatus within the completion[folderId] model.
     */
    fun setFolderStatus(folderId: String, folderPaused: Boolean, folderStatus: FolderStatus) {
        synchronized(folderMapLock) {
            val cacheEntry = getFolderStatus(folderId)
            val cachedFolderStatus = cacheEntry.value
            cachedFolderStatus.paused = folderPaused
            if (folderStatus.globalBytes == 0L ||
                (folderStatus.inSyncBytes > folderStatus.globalBytes)
            ) {
                cachedFolderStatus.completion = 100.0
            } else {
                cachedFolderStatus.completion = Math.floor(folderStatus.inSyncBytes.toDouble() / folderStatus.globalBytes * 100).toDouble()
            }
            if (folderStatus.state == "idle") {
                cachedFolderStatus.completion = 100.0
            }
            if (ENABLE_VERBOSE_LOG) {
                logV("setFolderStatus: folderId=\"$folderId\"" +
                    ", state=\"${folderStatus.state}\"" +
                    ", paused=${cachedFolderStatus.paused}" +
                    ", completion=${cachedFolderStatus.completion.toInt()}%")
            }

            // Add folder or update existing folder entry.
            folderMap[folderId] = AbstractMap.SimpleEntry(folderStatus, cachedFolderStatus)
        }
    }

    fun setFolderStatus(folderId: String, folderStatus: FolderStatus) {
        synchronized(folderMapLock) {
            // Persist cachedFolderStatus.paused from the previous entry.
            val cacheEntry = getFolderStatus(folderId)
            setFolderStatus(folderId, cacheEntry.value.paused, folderStatus)
        }
    }

    /**
     * Setters of additionally stored information
     * e.g. "ItemFinished" event details arriving through {@link EventPoller} > {@link RestApi}
     */
    fun setLastItemFinished(
        folderId: String,
        lastItemFinishedAction: String,
        lastItemFinishedItem: String,
        lastItemFinishedTime: String
    ) {
        synchronized(folderMapLock) {
            val cacheEntry = getFolderStatus(folderId)
            val cachedFolderStatus = cacheEntry.value
            cachedFolderStatus.lastItemFinishedAction = lastItemFinishedAction
            cachedFolderStatus.lastItemFinishedItem = lastItemFinishedItem
            cachedFolderStatus.lastItemFinishedTime = lastItemFinishedTime

            // Add folder or update existing folder entry.
            folderMap[folderId] = AbstractMap.SimpleEntry(cacheEntry.key, cachedFolderStatus)
        }
    }

    fun setRemoteIndexUpdated(folderId: String, remoteIndexUpdated: Boolean) {
        synchronized(folderMapLock) {
            val cacheEntry = getFolderStatus(folderId)
            val cachedFolderStatus = cacheEntry.value
            cachedFolderStatus.remoteIndexUpdated = remoteIndexUpdated

            // Add folder or update existing folder entry.
            folderMap[folderId] = AbstractMap.SimpleEntry(cacheEntry.key, cachedFolderStatus)
        }
    }

    fun setDiscoveredConflictFiles(folderId: String, discoveredConflictFiles: Array<String>) {
        synchronized(folderMapLock) {
            val cacheEntry = getFolderStatus(folderId)
            val cachedFolderStatus = cacheEntry.value
            cachedFolderStatus.discoveredConflictFiles = discoveredConflictFiles

            // Add folder or update existing folder entry.
            folderMap[folderId] = AbstractMap.SimpleEntry(cacheEntry.key, cachedFolderStatus)
        }
    }

    private fun logV(logMessage: String) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage)
        }
    }

    companion object {
        private const val TAG = "LocalCompletion"
    }
}
