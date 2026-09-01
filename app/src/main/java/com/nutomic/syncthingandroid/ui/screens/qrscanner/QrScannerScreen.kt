package com.nutomic.syncthingandroid.ui.screens.qrscanner

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.nutomic.syncthingandroid.R

/**
 * QR code scanner screen: pure UI shell. The camera lifecycle (resume/pause/
 * decode) is owned by [com.nutomic.syncthingandroid.activities.QRScannerActivity].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBarcodeViewCreated: (DecoratedBarcodeView) -> Unit,
    onCancel: () -> Unit,
) {
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
            AndroidView(
                factory = { ctx ->
                    DecoratedBarcodeView(ctx).also { onBarcodeViewCreated(it) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
