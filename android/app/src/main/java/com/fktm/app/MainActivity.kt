package com.fktm.app

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.fktm.app.ble.BleLockClient
import com.fktm.app.ui.CampusLiteApp
import com.fktm.app.ui.theme.FktmTheme
import com.fktm.app.ui.theme.resolveDarkTheme
import com.fktm.app.util.NotificationPermission
import com.fktm.app.vm.UnlockViewModel
import com.fktm.app.vm.WaterViewModel

/**
 * New independent shell for the two primary campus actions.
 *
 * The official Tmall Campus app stays signed and functional. This app only
 * implements local BLE/QR services and imports an existing account session with
 * explicit one-time authorization.
 */
class MainActivity : ComponentActivity() {

    private val lockVm: UnlockViewModel by viewModels()
    private val waterVm: WaterViewModel by viewModels()
    private var pendingAutoUnlock = false
    private var backgroundImageUri by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                if (!pendingAutoUnlock) return@registerForActivityResult
                pendingAutoUnlock = false
                autoUnlockPrimaryLock()
            } else {
                // Do not retain a stale request and unexpectedly unlock later.
                pendingAutoUnlock = false
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    private val backgroundImagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val saved = uri.toString()
            (application as FktmApp).preferences.backgroundImageUri = saved
            backgroundImageUri = saved
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FktmApp
        backgroundImageUri = app.preferences.backgroundImageUri

        setContent {
            var themeMode by remember { mutableStateOf(app.preferences.themeMode) }
            var glassEnabled by remember { mutableStateOf(app.preferences.liquidGlassEnabled) }
            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())

            SideEffect {
                val barColor = if (darkTheme) AndroidColor.BLACK else AndroidColor.rgb(245, 247, 245)
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            FktmTheme(themeMode = themeMode) {
                CampusLiteApp(
                    lockVm = lockVm,
                    waterVm = waterVm,
                    onRequestBlePermissions = ::ensureBluetoothPermissions,
                    onEnsureNotificationPermission = ::ensureNotificationPermission,
                    onQuickUnlock = ::requestQuickUnlock,
                    themeMode = themeMode,
                    darkTheme = darkTheme,
                    glassEnabled = glassEnabled,
                    onThemeModeChange = { mode ->
                        app.preferences.themeMode = mode
                        themeMode = mode
                    },
                    onGlassEnabledChange = { enabled ->
                        app.preferences.liquidGlassEnabled = enabled
                        glassEnabled = enabled
                    },
                    backgroundImageUri = backgroundImageUri,
                    onPickBackgroundImage = {
                        backgroundImagePicker.launch(arrayOf("image/*"))
                    },
                    onClearBackgroundImage = {
                        backgroundImageUri?.let { saved ->
                            runCatching {
                                contentResolver.releasePersistableUriPermission(
                                    Uri.parse(saved),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                        }
                        app.preferences.backgroundImageUri = null
                        backgroundImageUri = null
                    },
                )
            }
        }

        ensureNotificationPermission()
        if (isUnlockIntent(intent)) requestAutoUnlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isUnlockIntent(intent)) requestAutoUnlock()
    }

    override fun onStart() {
        super.onStart()
        ble().setForeground(true)
        lockVm.prewarm()
    }

    override fun onStop() {
        super.onStop()
        ble().setForeground(false)
    }

    private fun ble(): BleLockClient = (application as FktmApp).bleClient

    private fun isUnlockIntent(intent: Intent?): Boolean =
        intent?.data?.toString()?.contains("unlock") == true

    private fun requestAutoUnlock() {
        if (hasBluetoothPermissions()) autoUnlockPrimaryLock()
        else {
            pendingAutoUnlock = true
            ensureBluetoothPermissions()
        }
    }

    private fun requestQuickUnlock() {
        // The card routes to credential setup when no lock has been saved.
        if (lockVm.locks.isEmpty()) return
        requestAutoUnlock()
    }

    private fun autoUnlockPrimaryLock() {
        val credential = (application as FktmApp).credentialStore.primary() ?: return
        lockVm.unlock(credential)
    }

    private fun ensureBluetoothPermissions() {
        if (hasBluetoothPermissions()) return
        permissionLauncher.launch(BleLockClient(this).requiredPermissions())
    }

    private fun ensureNotificationPermission() {
        if (NotificationPermission.isGranted(this)) return
        val permission = NotificationPermission.permission
        if (permission.isNotEmpty()) {
            notificationPermissionLauncher.launch(permission)
        }
    }

    private fun hasBluetoothPermissions(): Boolean =
        BleLockClient(this).hasPermissions()
}
