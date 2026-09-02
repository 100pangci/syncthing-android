package com.nutomic.syncthingandroid.ui.screens.folder

import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.Folder
import java.util.Random

internal data class DeviceShareState(
    val device: Device,
    val shared: Boolean,
    val password: String,
)

internal fun generateRandomFolderId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    val sb = StringBuilder()
    val random = Random()
    for (i in 0 until 10) {
        if (i == 5) {
            sb.append("-")
        }
        sb.append(chars[random.nextInt(chars.size)])
    }
    return sb.toString()
}

internal fun initNewFolder(folderId: String?, folderLabel: String?, receiveEncrypted: Boolean): Folder =
    Folder().apply {
        id = folderId ?: generateRandomFolderId()
        label = folderLabel?.trim() ?: ""
        paused = false
        type = if (receiveEncrypted)
            Constants_FOLDER_TYPE_RECEIVE_ENCRYPTED
        else
            Constants_FOLDER_TYPE_SEND_RECEIVE
        minDiskFree = Folder.MinDiskFree()
        versioning = Folder.Versioning()
        versioning.type = "trashcan"
        versioning.params["cleanoutDays"] = "14"
        versioning.cleanupIntervalS = 0
        versioning.fsPath = ""
        versioning.fsType = "basic"
    }

internal const val Constants_FOLDER_TYPE_RECEIVE_ENCRYPTED =
    com.nutomic.syncthingandroid.service.Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED
internal const val Constants_FOLDER_TYPE_SEND_RECEIVE =
    com.nutomic.syncthingandroid.service.Constants.FOLDER_TYPE_SEND_RECEIVE
internal const val Constants_FOLDER_TYPE_SEND_ONLY =
    com.nutomic.syncthingandroid.service.Constants.FOLDER_TYPE_SEND_ONLY
