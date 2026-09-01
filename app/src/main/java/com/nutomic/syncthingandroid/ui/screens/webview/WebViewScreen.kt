package com.nutomic.syncthingandroid.ui.screens.webview

import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.Util

/**
 * Generic WebView screen, ported from the legacy WebViewActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    webPageUrl: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isRunningOnTV = remember { Util.isRunningOnTV(context) }
    var sslNoticeUserDecision by remember { mutableStateOf(false) }
    var showSslDialog by remember { mutableStateOf(false) }
    var pendingSslHandler by remember { mutableStateOf<SslErrorHandler?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_issue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel))
                    }
                },
                actions = {
                    if (!isRunningOnTV) {
                        IconButton(onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webPageUrl)))
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_in_browser))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        clearCache(true)
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: android.net.http.SslError
                            ) {
                                if (sslNoticeUserDecision) {
                                    handler.proceed()
                                    return
                                }
                                pendingSslHandler = handler
                                showSslDialog = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                            }
                        }
                        loadUrl(webPageUrl)
                    }
                },
                update = { webView = it },
                modifier = Modifier.fillMaxSize()
            )
            if (isLoading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.web_page_loading, webPageUrl),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    CircularProgressIndicator(Modifier.padding(top = 32.dp))
                }
            }
        }
    }

    if (showSslDialog) {
        AlertDialog(
            onDismissRequest = { showSslDialog = false },
            title = { Text(stringResource(R.string.security_notice)) },
            text = { Text(stringResource(R.string.ssl_cert_invalid_text, webPageUrl)) },
            confirmButton = {
                TextButton(onClick = {
                    showSslDialog = false
                    sslNoticeUserDecision = true
                    pendingSslHandler?.proceed()
                    pendingSslHandler = null
                }) { Text(stringResource(R.string.cont)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSslDialog = false
                    pendingSslHandler?.cancel()
                    pendingSslHandler = null
                    if (isRunningOnTV) {
                        onBack()
                    }
                }) { Text(stringResource(R.string.cancel_title)) }
            }
        )
    }
}

