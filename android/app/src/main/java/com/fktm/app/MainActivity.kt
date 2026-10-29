package com.fktm.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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
    private var showBatteryPrompt by mutableStateOf(false)

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

        // 桌面「一键打开宿舍门」快捷方式在每次启动时重新推送一遍：
        // 覆盖安装 / 改名（STmall → Cumpus）后，桌面缓存的那份可能已经失效，
        // ColorOS 点旧快捷方式会提示「应用数据读取失败，无法启动应用」。
        // 动态推送同 id 会原地刷新（已固定的快捷方式同样按 id 更新）。
        refreshUnlockShortcut()

        // ColorOS 会把不常驻后台的应用「速冻」，被冻住后快捷方式/磁贴可能拉起失败。
        // 安装后第一次启动时询问一次是否加入电池优化白名单（系统弹窗，用户决定）。
        maybePromptBatteryWhitelist(app)

        setContent {
            var themeMode by remember { mutableStateOf(app.preferences.themeMode) }
            var glassEnabled by remember { mutableStateOf(app.preferences.liquidGlassEnabled) }
            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
            val systemBarColor by animateColorAsState(
                targetValue = if (darkTheme) Color.Black else Color(0xFFF5F7F5),
                animationSpec = tween(360),
                label = "system-bar-color",
            )

            SideEffect {
                val barColor = systemBarColor.toArgb()
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            FktmTheme(themeMode = themeMode) {
                if (showBatteryPrompt) {
                    AlertDialog(
                        onDismissRequest = { showBatteryPrompt = false },
                        title = { Text("允许后台运行") },
                        text = {
                            Text(
                                "ColorOS 会把不常用的应用「速冻」，被冻结后「一键打开宿舍门」" +
                                    "快捷方式和下拉磁贴可能无法启动应用。\n\n" +
                                    "建议在接下来的系统页面里把 Cumpus 设为「允许」，" +
                                    "保证快捷开锁随时可用。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryPrompt = false
                                openBatteryWhitelist()
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryPrompt = false }) {
                                Text("以后再说")
                            }
                        },
                    )
                }
                CampusLiteApp(
                    lockVm = lockVm,
                    waterVm = waterVm,
                    onRequestBlePermissions = ::ensureBluetoothPermissions,
                    onEnsureNotificationPermission = ::ensureNotificationPermission,
                    onQuickUnlock = ::requestQuickUnlock,
                    onOpenBatterySettings = ::openBatteryWhitelist,
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

    /**
     * 每次启动重新推送「一键打开宿舍门」快捷方式。
     *
     * 覆盖安装、应用改名后，桌面缓存的那份静态快捷方式可能指向旧状态；
     * 动态推送同 id 会原地刷新（固定的快捷方式同样按 id 更新），
     * 避免 ColorOS 点旧快捷方式时提示「应用数据读取失败，无法启动应用」。
     */
    private fun refreshUnlockShortcut() {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(this, "unlock")
                .setShortLabel(getString(R.string.shortcut_unlock_short))
                .setLongLabel(getString(R.string.shortcut_unlock_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_tile_unlock))
                .setIntent(
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("fktm://unlock")
                    }
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        }
    }

    /**
     * 安装后只弹一次的「允许后台运行」引导（ColorOS 速冻是快捷方式/磁贴
     * 启动失败的头号原因；加入电池白名单后系统不再冻结本应用）。
     */
    private fun maybePromptBatteryWhitelist(app: FktmApp) {
        val pm = getSystemService(PowerManager::class.java)
        val ignoring = runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
        if (ignoring) return
        if (app.preferences.batteryPromptShown) return
        app.preferences.batteryPromptShown = true
        showBatteryPrompt = true
    }

    /** 打开系统「电池优化白名单」页面（用户手动确认允许）。 */
    private fun openBatteryWhitelist() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }
}
