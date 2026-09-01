package com.nutomic.syncthingandroid.ui.screens.qrscanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.nutomic.syncthingandroid.R

/**
 * QR code scanner screen, ported from the legacy QRScannerActivity.
 * The zxing DecoratedBarcodeView is embedded via AndroidView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            onCancel()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qr_code_description)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, stringResource(android.R.string.cancel))
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
            if (hasCameraPermission) {
                // The view only gets created; camera start/stop is tied to the
                // host lifecycle below. Starting the camera directly from the
                // factory crashes when recomposition happens while the host is
                // not yet resumed (e.g. right after the permission dialog).
                AndroidView(
                    factory = { ctx ->
                        DecoratedBarcodeView(ctx).also { barcodeView = it }
                    },
                    update = { barcodeView = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Resume/pause the barcode scanner with the host lifecycle, mirroring the
    // legacy activity's onResume/onStop handling. Wrapped in try/catch so a
    // transient camera failure can never crash the app.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val bv = barcodeView
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try {
                        bv?.resume()
                        bv?.decodeSingle(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult) {
                                bv.pause()
                                onResult(result.text)
                            }

                            override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) {
                                // Unused
                            }
                        })
                    } catch (e: Exception) {
                        android.util.Log.w("QrScannerScreen", "Failed to start scanner", e)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try {
                        bv?.pause()
                    } catch (e: Exception) {
                        android.util.Log.w("QrScannerScreen", "Failed to pause scanner", e)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                barcodeView?.pause()
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }
}
