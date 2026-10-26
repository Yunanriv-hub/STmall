package com.fktm.app.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fktm.app.FktmApp
import com.fktm.app.ui.QrScannerScreen
import com.fktm.app.ui.theme.FktmTheme

/** QR scanner entry used by the embedded Life798 add-device flow. */
class DeviceScanActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RESULT = "com.fktm.app.scan.EXTRA_RESULT"

        fun intent(context: Context): Intent =
            Intent(context, DeviceScanActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FktmTheme(themeMode = (application as FktmApp).preferences.themeMode) {
                QrScannerScreen(
                    onDetected = { text ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_RESULT, text),
                        )
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }
}
