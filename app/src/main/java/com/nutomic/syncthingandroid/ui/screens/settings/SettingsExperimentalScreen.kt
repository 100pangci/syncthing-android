package com.nutomic.syncthingandroid.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.util.RootAccess
import kotlinx.coroutines.Dispatchers
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
    // Non-null while the root-mode warning dialog is up: true = enabling, false = disabling.
    // The switch never writes the preference directly — a tap only opens this dialog, and
    // the preference is written (and the core restarted) only after the user confirms.
    val pendingRootWarning = remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

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
                            if (enabling) {
                                // Request the su grant here — this is where the Magisk
                                // dialog appears. Abort without writing the preference
                                // when su is unavailable or denied.
                                val granted = withContext(Dispatchers.IO) { RootAccess.isSuAvailable() }
                                if (!granted) {
                                    Toast.makeText(
                                        context, R.string.root_authorization_unavailable, Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }
                                runAsRoot.value = true
                            } else {
                                // While su is still guaranteed to work, hand root-session
                                // files back to the app UID: the root-uid core writes
                                // config.xml and key material with explicit 0600 modes,
                                // which the unprivileged app could no longer read.
                                if (withContext(Dispatchers.IO) { RootAccess.appStorageOwnedByRoot(context) }) {
                                    withContext(Dispatchers.IO) { RootAccess.handBackStorage(context) }
                                }
                                runAsRoot.value = false
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
                value = runAsRoot.value,
                onValueChange = { requested -> pendingRootWarning.value = requested },
                title = { Text(stringResource(R.string.run_as_root_title)) },
                summary = { Text(stringResource(R.string.run_as_root_summary)) },
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
