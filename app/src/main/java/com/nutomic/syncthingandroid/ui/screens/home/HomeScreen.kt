package com.nutomic.syncthingandroid.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.ui.LocalServiceState
import com.nutomic.syncthingandroid.ui.LocalSyncthingService
import com.nutomic.syncthingandroid.ui.components.EmptyListHint
import com.nutomic.syncthingandroid.ui.nav.LocalAppNavigator
import com.nutomic.syncthingandroid.ui.theme.AMOLED_CARD_BORDER_ALPHA
import com.nutomic.syncthingandroid.ui.theme.LocalAmoledTheme
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
// Devices/Status must use glyph pairs whose filled variant is visually solid;
// "Devices" and "DataUsage" are outline-style glyphs whose filled/outlined
// variants look identical, so the selected state would be invisible.
private val TAB_ICONS = listOf(
    Icons.Filled.Folder to Icons.Outlined.Folder,
    Icons.Filled.DeviceHub to Icons.Outlined.DeviceHub,
    Icons.Filled.PieChart to Icons.Outlined.PieChart,
)

/**
 * Home screen: folders / devices / status destinations on an MD3 bottom
 * navigation bar inside a drawer scaffold.
 * Ported from the legacy MainActivity + FolderListFragment + DeviceListFragment + StatusFragment.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onExitApp: () -> Unit,
) {
    val navigator = LocalAppNavigator.current
    val service = LocalSyncthingService.current
    val serviceState = LocalServiceState.current
    val api = service?.api
    val apiConfigLoaded = api?.isConfigLoaded ?: false

    // Folder/device lists are polled and owned by HomeDataHost (above the
    // NavDisplay), so they survive entry transitions; see HomeDataHost.
    val folders = LocalHomeFolderModels.current
    val devices = LocalHomeDeviceModels.current
    val isAmoled = LocalAmoledTheme.current

    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = TAB_FOLDERS, pageCount = { 3 })

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
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, stringResource(R.string.main_menu))
                        }
                    },
                    actions = {
                        if (pagerState.currentPage == TAB_FOLDERS) {
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
                        IconButton(onClick = { navigator.openSettings() }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                        }
                    }
                )
            },
            floatingActionButton = {
                // Add actions live on a bottom-right FAB (same spot as the folder
                // editor's save button), tab-aware: each list tab adds its own kind.
                when (pagerState.currentPage) {
                    TAB_FOLDERS -> {
                        FloatingActionButton(onClick = { navigator.openFolderEdit(null, true) }) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.add_folder))
                        }
                    }
                    TAB_DEVICES -> {
                        FloatingActionButton(onClick = { navigator.openDeviceEdit(null, true) }) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.add_device))
                        }
                    }
                    else -> {}
                }
            },
            bottomBar = {
                // Pure AMOLED: black bar, separated from the content only by a faint
                // hairline - no tinted surface, matching the outlined-card treatment.
                Column {
                    if (isAmoled) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                                .copy(alpha = AMOLED_CARD_BORDER_ALPHA)
                        )
                    }
                    NavigationBar(
                        containerColor = if (isAmoled) Color.Black
                            else MaterialTheme.colorScheme.surfaceContainer
                    ) {
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
                }
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Keep all three pages composed. Without this, every tab
                // switch had to rebuild the target page's whole UI on the
                // main thread mid-animation, which showed up as jank. Pages
                // now persist (including their scroll positions) and tab
                // switches only move the scroll offset.
                beyondViewportPageCount = 2
            ) { page ->
                when (page) {
                    TAB_FOLDERS -> FolderListPage(
                        folders = folders,
                    )
                    TAB_DEVICES -> DeviceListPage(
                        devices = devices,
                    )
                    else -> StatusPage(
                        serviceState = serviceState,
                        visible = pagerState.currentPage == TAB_STATUS
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    LazyColumn(
        state = rememberLazyListState(prefetchStrategy = NoLazyListPrefetch),
        modifier = Modifier.fillMaxSize(),
        // Keep the last row reachable above the bottom-right FAB.
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
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

@OptIn(ExperimentalFoundationApi::class)
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
    LazyColumn(
        state = rememberLazyListState(prefetchStrategy = NoLazyListPrefetch),
        modifier = Modifier.fillMaxSize(),
        // Keep the last row reachable above the bottom-right FAB.
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(devices, key = { it.id }, contentType = { "device" }) { model ->
            DeviceRow(
                model = model,
                onEdit = onEdit,
            )
        }
    }
}

/**
 * Prefetch strategy that never queues prefetch requests.
 *
 * Workaround for a Compose runtime 1.11 crash where resuming a prefetched
 * (paused) item composition throws
 * "IllegalArgumentException: Cannot disable reuse from root if it was caused
 * by other groups". Prefetching is a pure performance hint, so skipping it
 * only trades a small scroll-ahead cost for stability.
 */
@OptIn(ExperimentalFoundationApi::class)
internal object NoLazyListPrefetch : LazyListPrefetchStrategy {
    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) = Unit

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) = Unit

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}
