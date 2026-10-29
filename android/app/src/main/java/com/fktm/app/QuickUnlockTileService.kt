package com.fktm.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * 下拉快捷磁贴：点一下直接开锁。
 *
 * 磁贴本身不跑 BLE（生命周期太短），而是把 MainActivity 以 `fktm://unlock` 拉起，
 * 由 MainActivity 侧走完整的连接 + 开锁流程（也顺带授权/报错可见）。
 */
class QuickUnlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { t ->
            t.state = Tile.STATE_INACTIVE
            t.label = "一键开锁"
            t.icon = Icon.createWithResource(this, R.drawable.ic_tile_unlock)
            t.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        val unlockIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("fktm://unlock")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        fun collapse(intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }

        fun launch() {
            try {
                collapse(unlockIntent)
            } catch (e: Throwable) {
                // 直接深链启动失败（例如应用被 ColorOS 冻结、数据暂不可读）时，
                // 退回系统标准启动入口；再失败就给出可读提示，而不是让系统弹一句
                // 「应用数据读取失败，无法启动应用」了事。
                val fallback = runCatching {
                    val launcher = packageManager.getLaunchIntentForPackage(packageName)
                        ?: throw IllegalStateException("no launcher intent")
                    collapse(launcher)
                }
                if (fallback.isFailure) {
                    Toast.makeText(
                        this,
                        "打开失败：应用可能被系统冻结，请先从桌面打开一次，再试磁贴",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 锁屏状态下点磁贴，系统可能直接拒绝启动并弹系统提示；
            // unlockAndRun 先解锁再执行，保证磁贴在任何锁屏状态都能把 App 拉起来。
            unlockAndRun { launch() }
        } else {
            launch()
        }
    }
}
