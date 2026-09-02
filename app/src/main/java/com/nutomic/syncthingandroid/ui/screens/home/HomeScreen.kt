package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.ui.appPreferences
import com.nutomic.syncthingandroid.ui.components.EmptyListHint
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.util.ConfigRouter
import com.nutomic.syncthingandroid.util.ConfigXml
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAB_FOLDERS = 0
private const val TAB_DEVICES = 1
private const val TAB_STATUS = 2

private val TAB_TITLES = intArrayOf(
    R.string.folders_fragment_title,
    R.string.devices_fragment_title,
    R.string.status_fragment_title
)

// MD3 bottom navigation: filled icon marks the selected destination, outlined
// icon the unselected ones (see "icon" guidance in the M3 NavigationBar spec).
private val TAB_ICONS = listOf(
    Icons.Filled.Folder to Icons.Outlined.Folder,
    Icons.Filled.Devices to Icons.Outlined.Devices,
    Icons.Filled.DataUsage to Icons.Outlined.DataUsage,
)

/**
 * Home screen: folders / devices / status destinations on an MD3 bottom
 * navigation bar inside a drawer scaffold.
 * Ported from the legacy MainActivity + FolderListFragment + DeviceListFragment + StatusFragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalAppNavigator.current
    val service = LocalSyncthingService.current
    val serviceState = LocalServiceState.current
    val api = service?.getApi()
    val apiConfigLoaded = api?.isConfigLoaded() ?: false

    val configRouter = remember { ConfigRouter(context) }
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = TAB_FOLDERS, pageCount = { 3 })

    var folders by remember { mutableStateOf<List<FolderUiModel>?>(null) }
    var devices by remember { mutableStateOf<List<DeviceUiModel>?>(null) }
    var sharedFoldersByDevice by remember { mutableStateOf<Map<String, List<Folder>>>(emptyMap()) }

    // Folders poll at the legacy GUI_UPDATE_INTERVAL cadence. The card data is
    // precomputed on the polling dispatcher; data class equality ensures rows
    // whose visible content did not change are skipped by composition.
    LaunchedEffect(serviceState, apiConfigLoaded) {
        while (isActive) {
            try {
                val newModels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    buildFolderUiModels(context, api, apiConfigLoaded, configRouter.getFolders(api))
                }
                if (newModels != folders) {
                    folders = newModels
                }
            } catch (e: ConfigXml.OpenConfigException) {
                folders = null
            }
            delay(Constants.GUI_UPDATE_INTERVAL)
        }
    }

    // Devices poll at the legacy REST_UPDATE_INTERVAL cadence (3s on O+),
    // including the forced remote status refresh. Shared folder lists are
    // derived here in memory; the legacy ConfigRouter.getSharedFolders would
    // re-parse config.xml per call.
    LaunchedEffect(serviceState, apiConfigLoaded) {
        while (isActive) {
            try {
                val (rawDevices, newSharedFolders) = kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {
                    if (serviceState == SyncthingService.State.ACTIVE && api != null && apiConfigLoaded) {
                        api.getRemoteDeviceStatus("")
                    }
                    val d = configRouter.getDevices(api, false)
                    // Derive sharing in memory; the legacy ConfigRouter helper
                    // re-parses config.xml on every call.
                    val map = HashMap<String, MutableList<Folder>>()
                    for (folder in configRouter.getFolders(api)) {
                        for (shared in folder.getSharedWithDevices()) {
                            map.getOrPut(shared.deviceID) { mutableListOf() }.add(folder)
                        }
                    }
                    d to map
                }
                val newModels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    buildDeviceUiModels(context, api, apiConfigLoaded, rawDevices, newSharedFolders)
                }
                if (newModels != devices) {
                    devices = newModels
                    sharedFoldersByDevice = newSharedFolders
                }
            } catch (e: ConfigXml.OpenConfigException) {
                devices = null
            }
            delay(Constants.REST_UPDATE_INTERVAL)
        }
    }



    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                stServiceRunning = serviceState == SyncthingService.State.ACTIVE,
                onShowDeviceId = { scope.launch { drawerState.close() }; navigator.showDeviceIdDialog() },
                onRecentChanges = { scope.launch { drawerState.close() }; navigator.openRecentChanges() },
                onWebGui = { scope.launch { drawerState.close() }; navigator.openWebGui() },
                onBackup = { scope.launch { drawerState.close() }; navigator.openSettings("ImportExport") },
                onRestart = { scope.launch { drawerState.close() }; navigator.confirmRestart() },
                onSettings = { scope.launch { drawerState.close() }; navigator.openSettings() },
                onExit = { scope.launch { drawerState.close() }; onExitApp() },
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(TAB_TITLES[pagerState.currentPage])) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, stringResource(R.string.main_menu))
                        }
                    },
                    actions = {
                        when (pagerState.currentPage) {
                            TAB_FOLDERS -> {
                                IconButton(onClick = { navigator.openFolderEdit(null, true) }) {
                                    Icon(Icons.Outlined.Add, stringResource(R.string.add_folder))
                                }
                                IconButton(onClick = {
                                    if (api != null && apiConfigLoaded) {
                                        api.rescanAll()
                                    }
                                }) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        stringResource(R.string.activity_main_bottom_navigation_rescan_all)
                                    )
                                }
                            }
                            TAB_DEVICES -> {
                                IconButton(onClick = { navigator.openDeviceEdit(null, true) }) {
                                    Icon(Icons.Outlined.Add, stringResource(R.string.add_device))
                                }
                            }
                            else -> {}
                        }
                        IconButton(onClick = { navigator.openSettings() }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    TAB_TITLES.forEachIndexed { index, titleRes ->
                        val selected = pagerState.currentPage == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = {
                                Icon(
                                    imageVector = if (selected) TAB_ICONS[index].first else TAB_ICONS[index].second,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(titleRes)) }
                        )
                    }
                }
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { page ->
                when (page) {
                    TAB_FOLDERS -> FolderListPage(
                        folders = folders,
                    )
                    TAB_DEVICES -> DeviceListPage(
                        devices = devices,
                    )
                    else -> StatusPage(serviceState = serviceState)
                }
            }
        }
    }
}

@Composable
private fun FolderListPage(
    folders: List<FolderUiModel>?,
) {
    val context = LocalContext.current
    val navigator = LocalAppNavigator.current
    if (folders.isNullOrEmpty()) {
        EmptyListHint(stringResource(R.string.folder_list_empty))
        return
    }
    // Stable callbacks: combined with the FolderUiModel data class equality,
    // rows whose content did not change are skipped while scrolling.
    val onEdit: (FolderUiModel) -> Unit = remember(navigator) {
        { model -> navigator.openFolderEdit(model.id, false) }
    }
    val onOverride: (FolderUiModel) -> Unit = remember(context) {
        { model ->
            context.startService(
                android.content.Intent(context, SyncthingService::class.java).apply {
                    putExtra(SyncthingService.EXTRA_FOLDER_ID, model.id)
                    action = SyncthingService.ACTION_OVERRIDE_CHANGES
                }
            )
        }
    }
    val onRevert: (FolderUiModel) -> Unit = remember(context) {
        { model ->
            context.startService(
                android.content.Intent(context, SyncthingService::class.java).apply {
                    putExtra(SyncthingService.EXTRA_FOLDER_ID, model.id)
                    action = SyncthingService.ACTION_REVERT_LOCAL_CHANGES
                }
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.id }, contentType = { "folder" }) { model ->
            FolderRow(
                model = model,
                onEdit = onEdit,
                onOverride = onOverride,
                onRevert = onRevert,
            )
        }
    }
}

@Composable
private fun DeviceListPage(
    devices: List<DeviceUiModel>?,
) {
    val navigator = LocalAppNavigator.current
    if (devices.isNullOrEmpty()) {
        EmptyListHint(stringResource(R.string.no_devices_configured))
        return
    }
    val onEdit: (DeviceUiModel) -> Unit = remember(navigator) {
        { model -> navigator.openDeviceEdit(model.id, false) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(devices, key = { it.id }, contentType = { "device" }) { model ->
            DeviceRow(
                model = model,
                onEdit = onEdit,
            )
        }
    }
}
