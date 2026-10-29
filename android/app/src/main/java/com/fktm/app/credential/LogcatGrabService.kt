package com.fktm.app.credential

import com.fktm.app.ILogcatGrab
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 跑在 **Shizuku UserService 进程**里的抓取器（root 或 shell 身份）。
 *
 * 这个进程不是普通的 Android 应用进程 —— 它是 Shizuku 用 `app_process` 拉起来的，
 * 因此这里**不能**用 Context / ContentProvider 等 Android 组件（它们会失效）。
 * 也**不要**用 `android.util.Log`：`app_process` 下 native lib 路径可能不可用，
 * 会抛 UnsatisfiedLinkError。诊断信息统一攒进 [diagText]，由客户端取走。
 *
 * 但正因如此，它可以自由地用 [ProcessBuilder] 起 `logcat`：shell/root 身份本来就有权读日志。
 * 与 `fktm/p0_capture.bat` 做的事完全一致，只是从电脑搬到了手机里。
 *
 * ## 为什么要「命中即落盘」
 *
 * 抓取全程用户都在天猫校园里（fkTM 在后台），而 ColorOS 会把后台进程**冻结** ——
 * 实测连前台服务（dataSync 类型）也挡不住，App 会被 `do_freezer_trap` 冻住上百秒。
 * 冻结期间 binder 回复送不回来，App 里那个 `await` 一直不醒。
 * 所以：**只要命中，立刻把结果写进调用方给的文件**，由 App 在恢复前台时自取。
 * 这样结果绝不依赖「此刻 App 是否被调度」。
 */
class LogcatGrabService : ILogcatGrab.Stub() {

    /** 逐步更新的诊断（构造时先放哨兵值，便于判断「方法压根没被调到」）。 */
    @Volatile
    private var diagText: String = "grab() never called"

    @Volatile
    private var lastHit: String? = null

    private val notes = StringBuilder()

    /** TLog 兜底扫描的文件计数（每次 grab 重置，防单轮扫描失控）。 */
    private val tlogFilesScanned = AtomicInteger()

    override fun diag(): String = diagText

    override fun readCookieDb(): ByteArray {
        for (path in COOKIE_PATHS) {
            val bytes = runCatching { File(path).readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                stage("cookie-db=$path bytes=${bytes.size}")
                return bytes
            }
        }

        // Some vendor WebViews do not expose a readable Cookies database. The
        // no-root setup helper can place the same cookie map at this fixed path
        // through adb; values are never logged by this service.
        val imported = runCatching { File(COOKIE_IMPORT_PATH).readBytes() }.getOrNull()
        if (imported != null && imported.isNotEmpty()) {
            stage("cookie-import=$COOKIE_IMPORT_PATH bytes=${imported.size}")
            return imported
        }

        stage("cookie-db not readable")
        return ByteArray(0)
    }

    /** 记录一步进度，并同步到远程可读的镜像文件（排障用）。 */
    @Synchronized
    private fun stage(text: String) {
        notes.append(text).append("; ")
        val snapshot = notes.toString()
        diagText = snapshot
        runCatching {
            File(MIRROR).writeText(snapshot)
            File(MIRROR).setReadable(true, false)
        }
    }

    override fun consumeHit(): String {
        val hit = lastHit ?: runCatching { File(HIT_MIRROR).readText() }.getOrNull()
        lastHit = null
        runCatching { File(HIT_MIRROR).delete() }
        return hit.orEmpty()
    }

    override fun grab(timeoutMs: Int, resultPath: String): String {
        val t0 = System.currentTimeMillis()
        synchronized(this) { notes.setLength(0) }
        lastHit = null
        tlogFilesScanned.set(0)
        runCatching { File(HIT_MIRROR).delete() }

        stage("t0")

        reapStaleSiblings()

        // 关键：UserService 进程的 PATH 可能不完整，`logcat` 未必能按名字找到。
        // 优先用绝对路径，失败再退回裸名字。
        val binary = resolveLogcat()
        if (binary == null) {
            stage("no logcat binary in PATH or /system/bin")
            return ""
        }
        stage("logcat=$binary")

        // ★ 不再 `logcat -c`。
        //
        // 成功率低的主要根因之一：官方 App 经常在**进入门锁页/刷新设备时**就把
        // activationKey 打出来，而不是等到按开锁那一刻。用户的操作顺序并不固定 ——
        // 「先打开智能宿舍 → 再回来点开始抓取」时，`-c` 会把已经下发的钥匙整条抹掉，
        // 这一轮 100% 抓空。
        //
        // 现在的做法：用 `-T` 回看 3 分钟 + 进程内按 epoch 时间戳过滤，
        // 既接得住「先开页面再点抓取」，也不会把几天前的旧钥匙误当成本次命中。
        val startEpochSec = System.currentTimeMillis() / 1000L
        val lookbackSec = LOOKBACK_SEC
        val args = buildList {
            add("-v"); add("epoch")
            add("-T"); add((startEpochSec - lookbackSec).toString())
            add("-b"); add("main,system,crash")
            TAGS.forEach { add("$it:V") }
            add("*:S")
        }.toTypedArray()

        val proc = try {
            ProcessBuilder(binary, *args)
                .redirectErrorStream(true)
                .start()
        } catch (e: Throwable) {
            stage("exec failed: ${e.javaClass.simpleName}: ${e.message}")
            return ""
        }
        stage("logcat started lookback=${lookbackSec}s")

        val buffer = ArrayDeque<String>()
        val done = CountDownLatch(1)
        val lineCount = AtomicInteger()
        val matchCount = AtomicInteger()
        val oldSkipped = AtomicInteger()
        val firstLines = AtomicReference<String?>(null)

        // logcat 是持续输出的阻塞流，丢给独立线程泵出；
        // 主线程只负责「等 latch 或被超时唤醒」。
        val reader = thread(name = "fktm-logcat", isDaemon = true) {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    val n = lineCount.incrementAndGet()
                    firstLines.compareAndSet(null, line)
                    if (n == 1) stage("first line arrived")
                    // 进程内再做一次时间过滤：太老的记录不是这次抓取想要的
                    val ts = epochSeconds(line)
                    if (ts != null && ts < startEpochSec - lookbackSec) {
                        oldSkipped.incrementAndGet()
                        return@forEachLine
                    }
                    synchronized(buffer) {
                        buffer.addLast(line)
                        while (buffer.size > MAX_BUFFER_LINES) buffer.removeFirst()
                    }
                    if (LogcatParser.parse(line) != null) {
                        matchCount.incrementAndGet()
                        // Keep a binder-visible copy as well as the preferred
                        // app-private file. Non-root Shizuku runs as shell and
                        // cannot write the latter.
                        lastHit = line
                        stage("MATCH, ${writeHit(resultPath, line)}")
                        done.countDown()
                    }
                }
            } catch (_: Throwable) {
                // destroy() 之后 readLine 抛异常，属正常退出路径
            } finally {
                done.countDown()
            }
        }

        try {
            done.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 读干净 stdout，否则强杀前那点错误输出会丢
        runCatching { proc.inputStream.close() }
        val exit = runCatching { proc.exitValue() }.getOrNull()
        runCatching { proc.destroy() }
        reader.interrupt()

        // ★ 第二层兜底：logcat 没抓到（官方 release 版可能不打 logcat），
        // 直接去官方 App 自己的日志文件里找（TLog 等）。root 设备可读，
        // shell 身份读不了会自动跳过。
        if (lastHit == null) {
            val tlogLine = tlogFallback(startEpochSec - lookbackSec)
            if (tlogLine != null) {
                matchCount.incrementAndGet()
                stage("MATCH(tlog), ${writeHit(resultPath, tlogLine)}")
            }
        }

        val elapsed = System.currentTimeMillis() - t0
        stage(
            "lines=${lineCount.get()} matched=${matchCount.get()} oldSkipped=${oldSkipped.get()}" +
                " exit=${exit ?: "still-running"} elapsed=${elapsed}ms"
        )
        firstLines.get()?.let { stage("first=${mask(it)}") }

        return synchronized(buffer) { buffer.joinToString("\n") }
    }

    /**
     * 从 `-v epoch` 的行首提取秒级时间戳（`1759532200.123 ...`）。
     * 拿不到就返回 null —— 由调用方决定是否放行（不匹配的行不因格式问题被误杀）。
     */
    private fun epochSeconds(line: String): Long? =
        EPOCH_PREFIX.find(line)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * 兜底：扫描官方天猫校园自己落盘的日志文件（TLog / *.log）。
     *
     * 只有 Shizuku 以 root 身份运行时才可能读到 `/data/user/0/` 下的内容；
     * shell 身份会 EACCES，全部静默跳过（stage 记录尝试结果，不影响主链路）。
     *
     * 扫描约束：目录深度 ≤ 3、最多 200 个文件、只读 10 分钟内修改过、
     * 单文件 ≤ 10MB（只读前 2MB）。命中即返回该文件里的原始行。
     */
    private fun tlogFallback(fromEpochSec: Long): String? {
        var scanned = 0
        try {
            for (root in TLOG_ROOTS) {
                val hit = scanLogDir(File(root), 0, fromEpochSec) { scanned++ }
                if (hit != null) {
                    stage("tlog scanned=$scanned")
                    return hit
                }
            }
        } catch (_: Throwable) {
            // 权限不足 / 目录不存在：正常（shell 身份必然如此）
        }
        stage("tlog scanned=$scanned none")
        return null
    }

    private fun scanLogDir(dir: File, depth: Int, fromEpochSec: Long, onFile: () -> Unit): String? {
        if (depth > TLOG_MAX_DEPTH || tlogFilesScanned.get() >= TLOG_MAX_FILES) return null
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return null
        for (f in children.orEmpty()) {
            if (tlogFilesScanned.get() >= TLOG_MAX_FILES) return null
            if (f.isDirectory) {
                scanLogDir(f, depth + 1, fromEpochSec, onFile)?.let { return it }
                continue
            }
            if (!f.isFile) continue
            if (f.length() < 16 || f.length() > TLOG_MAX_FILE_BYTES) continue
            if (f.lastModified() / 1000 < fromEpochSec - 300) continue
            val name = f.name.lowercase()
            if (!name.contains("log") && !name.endsWith(".txt")) continue
            onFile()
            tlogFilesScanned.incrementAndGet()
            val hit = runCatching {
                val head = if (f.length() > TLOG_READ_BYTES) {
                    f.inputStream().buffered().use { it.readNBytes(TLOG_READ_BYTES.toInt()) }
                } else {
                    f.readBytes()
                }
                LogcatParser.parse(String(head, Charsets.ISO_8859_1))
            }.getOrNull() ?: continue
            // 只认“新近修改过的文件”里的命中，避免翻出上次抓取的旧钥匙
            if (f.lastModified() / 1000 < fromEpochSec) continue
            return hit.sourceLine
        }
        return null
    }

    /**
     * 把命中的那一行**原样**写进 [resultPath]。
     *
     * 刻意不换成 JSON：写回的就是 logcat 的原始行，
     * 调用方可以直接复用同一个 [LogcatParser]（正则已单测覆盖），
     * 少一层格式转换就少一处会出错的地方。
     *
     * 先写临时文件再 rename，避免 App 读到写了一半的内容。
     */
    private fun writeHit(resultPath: String, line: String): String {
        fun writeAtomically(path: String): Boolean {
            val f = File(path)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(line)
            tmp.setReadable(true, false)
            val ok = if (!tmp.renameTo(f)) {
                f.writeText(line)
                f.setReadable(true, false)
                runCatching { tmp.delete() }
                true
            } else {
                true
            }
            f.setReadable(true, false)
            return ok
        }

        val local = runCatching { writeAtomically(resultPath) }.getOrDefault(false)
        val remote = runCatching { writeAtomically(HIT_MIRROR) }.getOrDefault(false)
        return "hit local=$local remote=$remote"
    }

    /** `/system/bin/logcat` 优先；没有则退回 PATH 上的 `logcat`。 */
    private fun resolveLogcat(): String? {
        if (File(ABS_LOGCAT).canExecute()) return ABS_LOGCAT
        return "logcat"
    }

    /**
     * 回收**同名僵尸 UserService 进程**。
     *
     * 背景：Shizuku 的 UserService 进程不会随 `unbindUserService` 一起消亡 ——
     * 它会被 reparent 到 init（ppid=1）并一直挂着。
     * 换 APK 后新旧进程并存、日积月累；更糟的是 Shizuku 会按
     * `(包名, 后缀, version)` 复用旧进程，导致「改了代码没生效」。
     *
     * 这里趁我们已经起来，顺手把**cmdline 与本进程完全相同**的兄弟进程干掉。
     * 判据极窄（整段 cmdline 精确相等 + 排除自身），不会误伤别的进程。
     */
    private fun reapStaleSiblings() {
        val self = selfPid() ?: return
        val marker = selfCmdline()
        if (marker.isNullOrBlank()) return

        var reaped = 0
        runCatching {
            File(PROC).listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == self) return@forEach
                val cmd = runCatching {
                    File(dir, "cmdline").readBytes()
                        .toString(Charsets.ISO_8859_1)
                        .substringBefore('\u0000')
                }.getOrNull() ?: return@forEach
                if (cmd != marker) return@forEach
                runCatching {
                    ProcessBuilder(KILL, "-9", pid.toString())
                        .redirectErrorStream(true).start().waitFor()
                }
                reaped++
            }
        }
        if (reaped > 0) stage("reaped=$reaped")
    }

    /** `/proc/self` 是指向本进程 pid 目录的符号链接 → 解析出自身 pid。 */
    private fun selfPid(): Int? = runCatching {
        File("/proc/self").canonicalPath.substringAfterLast('/').toInt()
    }.getOrNull()

    /** 本进程的 cmdline（NUL 结尾，取第一段）。 */
    private fun selfCmdline(): String? = runCatching {
        File("/proc/self/cmdline").readBytes()
            .toString(Charsets.ISO_8859_1)
            .substringBefore('\u0000')
    }.getOrNull()

    /** 诊断信息里绝不能带出 activationKey —— 遮掉 hex 长串。 */
    private fun mask(line: String): String =
        line.replace(Regex("""[0-9a-fA-F]{16,}"""), "***")

    companion object {
        private const val ABS_LOGCAT = "/system/bin/logcat"
        private const val KILL = "/system/bin/kill"
        private const val PROC = "/proc"
        private const val COOKIE_IMPORT_PATH =
            "/data/local/tmp/campus_lite_cookies.json"
        private const val HIT_MIRROR = "/data/local/tmp/fktm_grab_hit.txt"

        /**
         * 诊断镜像文件。写在 `/data/local/tmp`（shell 可读）——
         * 这样 App 被冻结时，我们用 `adb shell cat` 也能看到服务端走到哪一步了。
         */
        private const val MIRROR = "/data/local/tmp/fktm_grab_diag.txt"

        /** logcat `-T` 回看时长（秒）：覆盖「先打开门锁页再点开始抓取」的操作顺序。 */
        private const val LOOKBACK_SEC = 180L

        /** 进程内环形缓冲上限：只影响 binder 返回值兜底，命中不依赖它。 */
        private const val MAX_BUFFER_LINES = 6000

        /** `-v epoch` 行首：`1759532200.123 D/Tag(pid): ...`。 */
        private val EPOCH_PREFIX = Regex("""^(\d{9,12})\.(\d{3,9})""")

        private val COOKIE_PATHS = listOf(
            "/data/user/0/com.tmall.campus.and/app_webview/Default/Cookies",
            "/data/data/com.tmall.campus.and/app_webview/Default/Cookies",
            "/data/user/0/com.tmall.campus.and/app_webview/Cookies",
            "/data/data/com.tmall.campus.and/app_webview/Cookies",
        )

        // ---- TLog 兜底扫描约束 ----
        private val TLOG_ROOTS = listOf(
            "/data/user/0/com.tmall.campus.and/files",
            "/data/data/com.tmall.campus.and/files",
        )
        private const val TLOG_MAX_DEPTH = 3
        private const val TLOG_MAX_FILES = 200
        private const val TLOG_MAX_FILE_BYTES = 10L * 1024 * 1024
        private const val TLOG_READ_BYTES = 2L * 1024 * 1024

        /** 与 `p0_capture.bat` 一致；`VocLockHelper` 匹配不到 `VocLockHelperV2`，两个都要列。 */
        private val TAGS = listOf(
            "VocLockHelperV2", "VocLockHelper", "VocLockJsBridge", "VocUnlockBridge",
            "BluetoothHelper", "VocLockManager", "VocLockSession", "VocLockBridge",
            "ZMUnlockBridge", "VocStatistics", "VocLogger", "Logger", "TLog",
        )
    }
}
