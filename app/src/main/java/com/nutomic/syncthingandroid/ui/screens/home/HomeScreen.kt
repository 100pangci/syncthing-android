package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.nutomic.syncthingandroid.ui.nav.AppRoute
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

/**
 * Home screen: folders / devices / status tabs inside a drawer scaffold.
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

    var folders by remember { mutableStateOf<List<Folder>?>(null) }
    var devices by remember { mutableStateOf<List<Device>?>(null) }
    var totalSyncCompletion by remember { mutableIntStateOf(-1) }
    var showExitFab by remember {
        mutableStateOf(!context.appPreferences().getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false))
    }

    // Refresh the visible data once per GUI update interval, as the legacy screens did.
    LaunchedEffect(serviceState, apiConfigLoaded) {
        while (isActive) {
            if (serviceState == SyncthingService.State.ACTIVE && api != null && apiConfigLoaded) {
                api.getRemoteDeviceStatus("")
            }
            try {
                folders = configRouter.getFolders(api)
                devices = configRouter.getDevices(api, false)
            } catch (e: ConfigXml.OpenConfigException) {
                folders = null
                devices = null
            }
            totalSyncCompletion =
                if (serviceState == SyncthingService.State.ACTIVE && apiConfigLoaded)
                    api?.getTotalSyncCompletion() ?: -1
                else
                    -1
            delay(Constants.GUI_UPDATE_INTERVAL)
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
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
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
                    if (serviceState == SyncthingService.State.ACTIVE && totalSyncCompletion != -1) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { totalSyncCompletion / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "$totalSyncCompletion%",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showExitFab) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.exit)) },
                        icon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                        onClick = onExitApp,
                    )
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding)) {
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    TAB_TITLES.forEachIndexed { index, titleRes ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(titleRes)) }
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        TAB_FOLDERS -> FolderListPage(
                            folders = folders,
                            configLoaded = apiConfigLoaded,
                        )
                        TAB_DEVICES -> DeviceListPage(
                            devices = devices,
                            configRouter = configRouter,
                            api = api,
                            configLoaded = apiConfigLoaded,
                        )
                        else -> StatusPage(serviceState = serviceState)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderListPage(
    folders: List<Folder>?,
    configLoaded: Boolean,
) {
    val service = LocalSyncthingService.current
    val context = LocalContext.current
    if (folders.isNullOrEmpty()) {
        EmptyListHint(stringResource(R.string.folder_list_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.id }) { folder ->
            FolderRow(
                folder = folder,
                restApi = service?.getApi(),
                apiConfigLoaded = configLoaded,
                onOverride = { f ->
                    context.startService(
                        android.content.Intent(context, SyncthingService::class.java).apply {
                            putExtra(SyncthingService.EXTRA_FOLDER_ID, f.id)
                            action = SyncthingService.ACTION_OVERRIDE_CHANGES
                        }
                    )
                },
                onRevert = { f ->
                    context.startService(
                        android.content.Intent(context, SyncthingService::class.java).apply {
                            putExtra(SyncthingService.EXTRA_FOLDER_ID, f.id)
                            action = SyncthingService.ACTION_REVERT_LOCAL_CHANGES
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun DeviceListPage(
    devices: List<Device>?,
    configRouter: ConfigRouter,
    api: RestApi?,
    configLoaded: Boolean,
) {
    if (devices.isNullOrEmpty()) {
        EmptyListHint(stringResource(R.string.no_devices_configured))
        return
    }
    val sorted = remember(devices) {
        devices.sortedWith(compareBy { if (it.name.isNullOrEmpty()) it.deviceID else it.name })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(sorted, key = { it.deviceID }) { device ->
            DeviceRow(
                device = device,
                configRouter = configRouter,
                restApi = api,
                apiConfigLoaded = configLoaded,
            )
        }
    }
}
