package com.nutomic.syncthingandroid.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.util.RootAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsExperimentalEntry() {
    entry<SettingsRoute.Experimental> {
        SettingsExperimentalScreen()
    }
}


@Composable
fun SettingsExperimentalScreen() {
    val context = LocalContext.current

    val useTor = rememberPreferenceState(Constants.PREF_USE_TOR, false)
    val socksProxy = rememberPreferenceState(Constants.PREF_SOCKS_PROXY_ADDRESS, "")
    val httpProxy = rememberPreferenceState(Constants.PREF_HTTP_PROXY_ADDRESS, "")
    val runAsRoot = rememberPreferenceState(Constants.PREF_RUN_AS_ROOT, false)
    val rootSwitchBusy = remember { mutableStateOf(false) }
    // Guard against re-processing the revert write when authorization was denied.
    val suppressNextRootChange = remember { mutableStateOf(false) }
    // Non-null while the root-mode warning dialog is up: true = enabling, false = disabling.
    val pendingRootWarning = remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    // React to root-mode toggles: turning it on probes su right away (this is what makes
    // the Magisk grant dialog appear immediately instead of at the next core start), then
    // BOTH directions show a plain warning dialog and only restart the core after the
    // user confirms. On confirm the root shell hands app storage back to the app UID when
    // needed (root-written config and key material carry explicit 0600 modes) — it must
    // run while su is still guaranteed to work. The revert write after a denied grant is
    // skipped via suppressNextRootChange.
    LaunchedEffect(Unit) {
        snapshotFlow { runAsRoot.value }
            .drop(1)
            .collect { enabled ->
                if (suppressNextRootChange.value) {
                    suppressNextRootChange.value = false
                    return@collect
                }
                rootSwitchBusy.value = true
                try {
                    if (enabled) {
                        val granted = withContext(Dispatchers.IO) { RootAccess.isSuAvailable() }
                        if (!granted) {
                            suppressNextRootChange.value = true
                            runAsRoot.value = false
                            Toast.makeText(
                                context, R.string.root_authorization_unavailable, Toast.LENGTH_LONG
                            ).show()
                            return@collect
                        }
                    }
                    // Keep the switch unchanged until the user decides in the dialog.
                    pendingRootWarning.value = enabled
                } finally {
                    rootSwitchBusy.value = false
                }
            }
    }

    pendingRootWarning.value?.let { enabling ->
        AlertDialog(
            onDismissRequest = { pendingRootWarning.value = null },
            title = { Text(stringResource(R.string.root_warning_title)) },
            text = {
                Text(
                    stringResource(
                        if (enabling) {
                            R.string.root_enable_warning_message
                        } else {
                            R.string.root_disable_warning_message
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRootWarning.value = null
                    scope.launch {
                        rootSwitchBusy.value = true
                        try {
                            suppressNextRootChange.value = true
                            runAsRoot.value = enabling
                            if (!enabling &&
                                withContext(Dispatchers.IO) { RootAccess.appStorageOwnedByRoot(context) }
                            ) {
                                withContext(Dispatchers.IO) { RootAccess.handBackStorage(context) }
                            }
                            context.startService(
                                Intent(context, SyncthingService::class.java)
                                    .setAction(SyncthingService.ACTION_RESTART)
                            )
                        } finally {
                            rootSwitchBusy.value = false
                        }
                    }
                }) {
                    Text(
                        stringResource(
                            if (enabling) {
                                R.string.root_enable_warning_continue
                            } else {
                                R.string.root_disable_warning_continue
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRootWarning.value = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.category_experimental),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.use_tor_title)) },
                summary = { Text(stringResource(R.string.use_tor_summary)) },
                state = useTor,
            )
        }
        item {
            val socksProxySummary = if (socksProxy.value.isBlank())
                "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.socks_proxy_address_example)}"
            else
                "${stringResource(R.string.use_proxy)} ${socksProxy.value}"
            TextFieldPreference(
                title = { Text(stringResource(R.string.socks_proxy_address_title)) },
                summary = { Text(socksProxySummary) },
                state = socksProxy,
                textToValue = {
                    validateProxy(
                        newValue = it,
                        regex = Regex("^socks5://.*:\\d{1,5}$"),
                        errorResId = R.string.toast_invalid_socks_proxy_address,
                        context = context,
                    )
                },
                enabled = !useTor.value,
            )
        }
        item {
            val httpProxySummary = if (httpProxy.value.isBlank())
                "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.http_proxy_address_example)}"
            else
                "${stringResource(R.string.use_proxy)} ${httpProxy.value}"
            TextFieldPreference(
                title = { Text(stringResource(R.string.http_proxy_address_title)) },
                summary = { Text(httpProxySummary) },
                state = httpProxy,
                textToValue = {
                    validateProxy(
                        newValue = it,
                        regex = Regex("^https?://.*:\\d{1,5}$"),
                        errorResId = R.string.toast_invalid_http_proxy_address,
                        context = context,
                    )
                },
                enabled = !useTor.value,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.run_as_root_title)) },
                summary = { Text(stringResource(R.string.run_as_root_summary)) },
                state = runAsRoot,
                enabled = !rootSwitchBusy.value,
            )
        }
    }
}

private fun validateProxy(
    newValue: String,
    regex: Regex,
    @StringRes errorResId: Int,
    context: Context
): String? {
    return when {
        newValue.isEmpty() -> newValue
        newValue.matches(regex) -> newValue
        else -> {
            Toast.makeText(context, errorResId, Toast.LENGTH_LONG).show()
            null
        }
    }
}
