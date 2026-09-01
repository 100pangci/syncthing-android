package com.nutomic.syncthingandroid.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.ui.screens.qrscanner.QrScannerScreen

/**
 * QR code scanner activity host, ported from the legacy QRScannerActivity.
 * The UI now lives in [com.nutomic.syncthingandroid.ui.screens.qrscanner.QrScannerScreen].
 */
class QRScannerActivity : ThemedAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApplicationTheme {
                QrScannerScreen(
                    onResult = { code ->
                        val intent = Intent().putExtra(QR_RESULT_ARG, code)
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val QR_RESULT_ARG = "QR_CODE"

        fun intent(context: Context): Intent = Intent(context, QRScannerActivity::class.java)
    }
}
