package com.nutomic.syncthingandroid.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.screens.qrscanner.QrScannerScreen

/**
 * QR code scanner activity. The camera is driven from the activity lifecycle
 * (mirroring the legacy implementation) so that permission grants, window
 * focus changes and composition order can never crash the scanner.
 */
class QRScannerActivity : ThemedAppCompatActivity(), BarcodeCallback {

    private var barcodeView: DecoratedBarcodeView? = null
    private var resultDelivered = false
    private var decodeRequested = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            finish()
        }
        // On grant: recomposition creates the view, which then starts the
        // camera via onBarcodeViewCreated -> startScannerIfResumed().
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ApplicationTheme {
                QrScannerScreen(
                    onBarcodeViewCreated = { view ->
                        barcodeView = view
                        startScannerIfResumed()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startScannerIfResumed()
    }

    override fun onPause() {
        try {
            barcodeView?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Pausing scanner failed", e)
        }
        decodeRequested = false
        super.onPause()
    }

    /**
     * Starts the scanner exactly like the legacy activity did: resume() then
     * decodeSingle(). Called when the view becomes available and on resume.
     * All camera access is wrapped so a transient camera failure can never
     * crash the app.
     */
    private fun startScannerIfResumed() {
        val view = barcodeView ?: return
        if (resultDelivered || decodeRequested) {
            return
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            try {
                view.post {
                    if (resultDelivered || isFinishing || isDestroyed) {
                        return@post
                    }
                    try {
                        view.resume()
                        view.decodeSingle(this)
                        decodeRequested = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Starting the scanner failed", e)
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scheduling the scanner failed", e)
                finish()
            }
        }
    }

    override fun barcodeResult(result: BarcodeResult) {
        if (resultDelivered) {
            return
        }
        resultDelivered = true
        try {
            barcodeView?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Pausing scanner failed", e)
        }
        setResult(RESULT_OK, Intent().putExtra(QR_RESULT_ARG, result.text))
        finish()
    }

    override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) {
        // Unused
    }

    companion object {
        private const val TAG = "QRScannerActivity"
        const val QR_RESULT_ARG = "QR_CODE"

        fun intent(context: Context): Intent = Intent(context, QRScannerActivity::class.java)
    }
}
