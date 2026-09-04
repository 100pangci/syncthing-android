package com.nutomic.syncthingandroid.ui.screens.folderpicker

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.ui.components.EmptyListHint
import com.nutomic.syncthingandroid.util.FileUtils
import com.nutomic.syncthingandroid.util.Util
import java.io.File
import java.util.TreeSet

/**
 * Built-in file system directory picker, ported from the legacy FolderPickerActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    initialDirectory: String?,
    rootDirectory: String?,
    onResult: (String?) -> Unit,
) {
    val context = LocalContext.current
    var roots by remember { mutableStateOf<Set<File>>(emptySet()) }
    var location by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rootDirectory) {
        roots = populateRoots(context, rootDirectory)
        if (!initialDirectory.isNullOrEmpty()) {
            location = File(initialDirectory)
        } else if (roots.size == 1) {
            location = roots.first()
        } else {
            location = null
        }
    }

    // Refresh the file list whenever the location changes.
    LaunchedEffect(location) {
        val loc = location
        files = if (loc == null) {
            emptyList()
        } else {
            val contents = loc.listFiles() ?: emptyArray()
            contents.sortedWith { f1, f2 ->
                when {
                    f1.isDirectory && f2.isFile -> -1
                    f1.isFile && f2.isDirectory -> 1
                    else -> f1.name.compareTo(f2.name, ignoreCase = true)
                }
            }
        }
    }

    val isRootView = location == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isRootView) stringResource(R.string.advanced_storage_path_overview)
                        else stringResource(R.string.current_path, location!!.absolutePath)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onResult(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel))
                    }
                },
                actions = {
                    if (!isRootView) {
                        val canGoUp = canGoUpToSubDir(location, roots) || canGoUpToRootDir(location, roots)
                        IconButton(
                            enabled = canGoUp,
                            onClick = {
                                if (canGoUpToSubDir(location, roots)) {
                                    location = location!!.parentFile
                                } else if (canGoUpToRootDir(location, roots)) {
                                    location = null
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.folder_go_up))
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.create_folder))
                        }
                        IconButton(onClick = {
                            location?.let { onResult(Util.formatPath(it.absolutePath)) }
                        }) {
                            Icon(Icons.Outlined.Done, stringResource(R.string.save_title))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isRootView) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(roots.toList()) { root ->
                        Text(
                            text = root.absolutePath,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { location = root }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            } else if (files.isEmpty()) {
                EmptyListHint(stringResource(R.string.folder_picker_title))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(files, key = { it.absolutePath }) { file ->
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (file.isFile) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        location = file
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_folder)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showCreateDialog = false
                    val loc = location
                    if (loc != null && name.isNotBlank()) {
                        val newFolder = File(loc, name.trim())
                        if (newFolder.mkdir()) {
                            location = newFolder
                        } else {
                            android.widget.Toast.makeText(
                                context, R.string.create_folder_failed, android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

private fun canGoUpToSubDir(location: File?, roots: Set<File>): Boolean =
    location != null && !roots.contains(location)

private fun canGoUpToRootDir(location: File?, roots: Set<File>): Boolean =
    roots.contains(location) && roots.size > 1

/**
 * Builds the list of storage roots, ported from FolderPickerActivity.populateRoots.
 */
private fun populateRoots(context: android.content.Context, rootDirectory: String?): Set<File> {
    val roots = ArrayList<File>()
    if (!rootDirectory.isNullOrEmpty()) {
        roots.add(File(rootDirectory))
    } else {
        roots.addAll(context.getExternalFilesDirs(null).filterNotNull())
        roots.remove(context.getExternalFilesDir(null))
        roots.add(Environment.getExternalStorageDirectory())
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))

        val mountedStoragePaths = FileUtils.getMountedStoragePathsAsFileArray()
        if (mountedStoragePaths != null) {
            roots.addAll(mountedStoragePaths.toList())
        }
        roots.add(File("/"))
    }
    // Remove any invalid directories and sort.
    val iterator = roots.iterator()
    while (iterator.hasNext()) {
        val f = iterator.next()
        if (f == null || !f.exists() || !f.isDirectory) {
            iterator.remove()
        }
    }
    return TreeSet(roots)
}
