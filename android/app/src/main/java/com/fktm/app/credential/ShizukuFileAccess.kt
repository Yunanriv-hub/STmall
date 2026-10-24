package com.fktm.app.credential

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.fktm.app.ILogcatGrab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Reads the official app's WebView cookie database through the already
 * installed Shizuku UserService.
 *
 * The privileged service exposes only [ILogcatGrab.readCookieDb], not a generic
 * path-based file API. This keeps the elevated surface narrow.
 */
object ShizukuFileAccess {

    const val PERMISSION_REQUEST_CODE = 4208
    private const val BIND_TIMEOUT_MS = 15_000L

    sealed interface Result {
        data class Success(val bytes: ByteArray) : Result
        data object NotRunning : Result
        data object PermissionDenied : Result
        data class Failed(val reason: String) : Result
    }

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    suspend fun readCookieDb(context: Context): Result {
        if (!isRunning()) return Result.NotRunning
        if (!hasPermission()) {
            requestPermission()
            return Result.PermissionDenied
        }

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine<Result> { continuation ->
                    val args = serviceArgs(context.applicationContext)
                    var connection: ServiceConnection? = null
                    var unbound = false

                    fun unbind() {
                        if (unbound) return
                        unbound = true
                        connection?.let {
                            runCatching { Shizuku.unbindUserService(args, it, true) }
                        }
                    }

                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            val result = runCatching {
                                ILogcatGrab.Stub.asInterface(binder).readCookieDb()
                            }.fold(
                                onSuccess = { bytes ->
                                    if (bytes == null || bytes.isEmpty()) {
                                        Result.Failed("Shizuku 已连接，但读不到官方应用 Cookie 数据库")
                                    } else {
                                        Result.Success(bytes)
                                    }
                                },
                                onFailure = {
                                    Result.Failed("读取登录态失败：${it.message ?: it.javaClass.simpleName}")
                                },
                            )
                            unbind()
                            if (continuation.isActive) continuation.resume(result)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            unbind()
                            if (continuation.isActive) {
                                continuation.resume(Result.Failed("Shizuku 服务已断开"))
                            }
                        }
                    }

                    connection = conn
                    continuation.invokeOnCancellation { unbind() }
                    runCatching {
                        Shizuku.bindUserService(args, conn)
                    }.onFailure {
                        unbind()
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.Failed("连接 Shizuku 服务失败：${it.message ?: it.javaClass.simpleName}")
                            )
                        }
                    }
                }
            } ?: Result.Failed("连接 Shizuku 超时")
        }
    }

    private fun serviceArgs(context: Context): Shizuku.UserServiceArgs {
        val installedAt = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(1L)
        return Shizuku.UserServiceArgs(
            ComponentName(context.packageName, LogcatGrabService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("grab")
            .debuggable(false)
            .version((installedAt / 1000L).toInt())
    }
}
