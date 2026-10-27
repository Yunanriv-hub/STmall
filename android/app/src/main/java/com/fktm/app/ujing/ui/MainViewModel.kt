package com.fktm.app.ujing.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.fktm.app.ujing.ble.BleError
import com.fktm.app.ujing.ble.CypressWasher
import com.fktm.app.ujing.ble.NordicCommand
import com.fktm.app.ujing.ble.NordicWasher
import com.fktm.app.ujing.data.SavedDevice
import com.fktm.app.ujing.data.TokenStore
import com.fktm.app.ujing.data.UjingDeviceSnapshot
import com.fktm.app.ujing.net.CreateOrderRequest
import com.fktm.app.ujing.net.DeviceWashModel
import com.fktm.app.ujing.net.ModuleType
import com.fktm.app.ujing.net.OrderData
import com.fktm.app.ujing.net.PayArgumentsData
import com.fktm.app.ujing.net.PayChannel
import com.fktm.app.ujing.net.PaymentArgumentsResult
import com.fktm.app.ujing.net.PaymentOrderDisposition
import com.fktm.app.ujing.net.PaymentPayloadKind
import com.fktm.app.ujing.net.PaymentRequestGate
import com.fktm.app.ujing.net.PaymentStartDecision
import com.fktm.app.ujing.net.ProgramInfo
import com.fktm.app.ujing.net.ScanResult
import com.fktm.app.ujing.net.UjingApi
import com.fktm.app.ujing.net.WasherStatus
import com.fktm.app.ujing.net.canRebuildAfterMissingPayment
import com.fktm.app.ujing.net.isTrustedAlipayDeepLink
import com.fktm.app.ujing.net.paymentOrderDisposition
import com.fktm.app.ujing.net.paymentChooseSign
import com.fktm.app.ujing.net.parsePayArguments
import com.fktm.app.ujing.net.preferredAlipayChannel

private data class PayChannelCacheKey(
    val orderId: String,
    val deviceId: String,
    val serviceSubjectId: String,
)

private data class AlipayChannelResolution(
    val channel: PayChannel?,
    val refreshed: Boolean,
)

private sealed interface PaymentOrderLookup {
    data class Found(val order: OrderData) : PaymentOrderLookup
    data class Failed(val error: Throwable) : PaymentOrderLookup
}

/**
 * 全流程编排：登录 → 扫码 → 选模式 → 蓝牙启动。
 *
 * 刻意保持单 ViewModel + 纯 StateFlow，不引 DI 框架。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "UjingMini"
    private val store = TokenStore(app)
    val api = UjingApi()
    val savedDevices = store.savedDevicesFlow

    private val _mobile = MutableStateFlow("")
    val mobile = _mobile.asStateFlow()

    private val _codeSent = MutableStateFlow(false)
    val codeSent = _codeSent.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn = _loggedIn.asStateFlow()

    private val _device = MutableStateFlow(SavedDevice())
    val device = _device.asStateFlow()

    private val _deviceSnapshots = MutableStateFlow<Map<String, UjingDeviceSnapshot>>(emptyMap())
    val deviceSnapshots = _deviceSnapshots.asStateFlow()

    private val _myOrders = MutableStateFlow<List<OrderData>>(emptyList())
    val myOrders = _myOrders.asStateFlow()

    private val _scan = MutableStateFlow<ScanResult?>(null)
    val scan = _scan.asStateFlow()

    /** 当前待启动的订单号。来源：扫洗衣机码返回的 orderId（已在官方渠道下单支付的订单）。 */
    private val _orderId = MutableStateFlow("")
    val orderId = _orderId.asStateFlow()

    /**
     * 订单详情。扫到 orderId 后拉一次，用 `status` 判断订单是否已在运行。
     *
     * 背景：服务端对已 RUNNING 的订单会**静默拒绝**重复启动
     * （`ble/data/report/nordic` 返回 `{}`），所以必须在点「蓝牙启动」之前
     * 就知道订单状态，否则用户白点一次、还以为是 App 坏了。
     */
    private val _order = MutableStateFlow<OrderData?>(null)
    val order = _order.asStateFlow()

    /**
     * 收银台渠道列表（`GET /payment/methods`）。下单成功后自动拉取。
     *
     * 实测本账号 5 个渠道：unionPay(默认) / abcPay / alipay / wechatPay / cmbPayH5。
     */
    private val _payChannels = MutableStateFlow<List<PayChannel>>(emptyList())
    val payChannels = _payChannels.asStateFlow()

    /** 当前选中的支付渠道 channel，默认 alipay。 */
    private val _selectedChannel = MutableStateFlow("alipay")
    val selectedChannel = _selectedChannel.asStateFlow()

    /**
     * 待唤起的支付宝订单串。
     *
     * 为什么要绕一圈：`PayTask` 构造需要 `Activity`，而 ViewModel 拿不到 Activity。
     * 因此 ViewModel 只负责从服务端取 `orderInfo` 放到这里，
     * 由 UI 层 collect 到之后调 `AlipayLauncher.pay(activity, ...)`，结果再回传 [onPayResult]。
     */
    private val _pendingOrderInfo = MutableStateFlow<String?>(null)
    val pendingOrderInfo = _pendingOrderInfo.asStateFlow()

    /** `alipayF2F` 返回的官方 App 跳转；只保存在内存中，不写日志或磁盘。 */
    private val _pendingAlipayDeepLink = MutableStateFlow<String?>(null)
    val pendingAlipayDeepLink = _pendingAlipayDeepLink.asStateFlow()

    private val _awaitingExternalPayment = MutableStateFlow(false)

    /**
     * 订单已付款、但还没启动洗衣机。
     *
     * 付款成功后置 true，UI 据此把主按钮切成「启动洗衣机」；
     * 启动成功或订单结束置 false。重启 App 时由 [refreshOrder] 按 `payFlag` 还原。
     */
    private val _paid = MutableStateFlow(false)
    val paid = _paid.asStateFlow()

    /**
     * 启动指令已下发、但服务端还没把订单推进到「运行中」。
     *
     * 点「启动洗衣机」时立即置 true，UI 主按钮切成「启动中…」并禁用（防重复点击）；
     * 等 orderDetail 的 status 变成 40 才清掉，让按钮接上「运行中 · 剩余 N 分钟」。
     */
    private val _starting = MutableStateFlow(false)
    val starting = _starting.asStateFlow()

    /** 每秒心跳。服务端的 remainTime 不保证持续回写，界面靠它做本地倒计时。 */
    private val _tick = MutableStateFlow(0L)
    val tick = _tick.asStateFlow()

    /** 最近一次成功拉到订单状态的时刻，配合 [tick] 推算剩余时间。 */
    private val _orderFetchedAt = MutableStateFlow(0L)
    val orderFetchedAt = _orderFetchedAt.asStateFlow()

    /**
     * 本地「刚下发启动指令」的时刻，0 表示没有。
     *
     * 服务端把订单从「已付款」推到「运行中」有延迟（实测几秒到十几秒），
     * 这段时间界面不能退回「启动洗衣机」，用这个时间戳兜一段；
     * 服务端确认 running（或订单结束）后清零。
     */
    private val _startedAt = MutableStateFlow(0L)
    val startedAt = _startedAt.asStateFlow()

    /**
     * 直接从洗衣机机身读到的剩余时间（秒），null = 还没读到过。
     *
     * STATUS 特征（`4A320009`）第 13 字节就是机身显示屏上那个分钟数，
     * 比服务端的 `remainTime` 权威 —— 服务端在「启动中」阶段根本不回写。
     * 蓝牙连接时（约 10 米内）读一次，记下时刻，由界面每秒递减。
     */
    private val _deviceRemain = MutableStateFlow<Int?>(null)
    val deviceRemain = _deviceRemain.asStateFlow()

    /** 读到 [deviceRemain] 的时刻，界面用它推算已经过去多少秒。 */
    private val _deviceRemainAt = MutableStateFlow(0L)
    val deviceRemainAt = _deviceRemainAt.asStateFlow()

    private val _program = MutableStateFlow<ProgramInfo?>(null)
    val program = _program.asStateFlow()

    private val _selectedModelId = MutableStateFlow(0)
    val selectedModelId = _selectedModelId.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    /** 支付参数请求进行中。UI 用它禁用按钮，ViewModel 用它阻止连点。 */
    private val _paying = MutableStateFlow(false)
    val paying = _paying.asStateFlow()

    private val paymentRequestGate = PaymentRequestGate()
    private var payChannelCacheKey: PayChannelCacheKey? = null
    private var payChannelsRefreshedAt = 0L
    private var rebuiltFromOrderId: String? = null
    private var autoRebuildPending = false
    private var createOrderInFlight = false

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.currentDevice()
            if (saved.valid) _device.value = saved
            val token = store.currentToken()
            var authenticated = false
            if (!token.isNullOrBlank()) {
                api.token = token
                _loggedIn.value = true
                authenticated = true
                _mobile.value = store.mobileFlow.first().orEmpty()
                if (saved.valid) {
                    refreshProgram(silent = true)
                    scheduleIdlePoll()  // 启动设备占用状态轮询
                }
            }
            val last = store.lastWorkModelFlow.first()
            if (last != 0) _selectedModelId.value = last
            // 订单号必须跨重启存活：蓝牙启动时服务端靠它签发指令，丢了就静默空转
            val savedOrderId = store.currentOrderId()
            if (savedOrderId.isNotBlank()) {
                _orderId.value = savedOrderId
                log("恢复待启动订单 orderId=$savedOrderId")
                // 顺带把订单状态拉回来：折叠卡的角标与主按钮都靠它判断
                refreshOrder(savedOrderId)
                // 渠道列表不跨进程保存；续付前必须重新拉，否则 branchType/签约参数会丢失
                if (authenticated) loadPayMethods()
            }
        }
    }

    fun log(line: String) {
        // vivo 只放行 E 级日志，Log.d 会被系统静默丢弃（实测 adb logcat 为空）
        Log.e(TAG, line)
        _logs.value = (_logs.value + line).takeLast(200)
    }

    fun consumeToast() { _toast.value = null }

    private fun error(e: Throwable) {
        val msg = when (e) {
            is UjingApi.ApiException -> "接口错误 ${e.code}：${e.message}"
            else -> e.message ?: "未知错误"
        }
        _toast.value = msg
        log("ERROR ${safeDiagnosticText(msg, 200)}")
    }

    // -----------------------------------------------------------------
    // 登录
    // -----------------------------------------------------------------

    fun onMobileChange(v: String) { _mobile.value = v.filter { it.isDigit() }.take(11) }

    fun sendCode() {
        val m = _mobile.value
        if (m.length != 11) { _toast.value = "手机号需 11 位"; return }
        viewModelScope.launch {
            _busy.value = "正在发送验证码"
            runCatching { api.sendCaptcha(m) }
                .onSuccess { _codeSent.value = true; _toast.value = "验证码已发送" }
                .onFailure { error(it) }
            _busy.value = null
        }
    }

    fun login(captcha: String) {
        val m = _mobile.value
        if (captcha.isBlank()) { _toast.value = "请输入验证码"; return }
        viewModelScope.launch {
            _busy.value = "正在登录"
            runCatching { api.login(m, captcha) }
                .onSuccess { env ->
                    val t = env.data?.token.orEmpty()
                    if (t.isBlank()) { _toast.value = "登录返回 token 为空"; return@onSuccess }
                    store.saveSession(t, m)
                    _loggedIn.value = true
                    _toast.value = "登录成功"
                    val d = _device.value
                    if (d.valid) {
                        refreshProgram(silent = true)
                        scheduleIdlePoll()
                    }
                    if (_orderId.value.isNotBlank()) loadPayMethods()
                }
                .onFailure { error(it) }
            _busy.value = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            store.clearSession()
            store.saveDeviceList(emptyList())
            api.token = null
            _loggedIn.value = false
            _codeSent.value = false
            _program.value = null
            cancelOrderPoll()
            cancelIdlePoll()
            stopTick()
            _scan.value = null
            _orderId.value = ""
            _order.value = null
            _paid.value = false
            _starting.value = false
            _startedAt.value = 0
            _deviceRemain.value = null
            _deviceRemainAt.value = 0
            _orderFetchedAt.value = 0
            _device.value = SavedDevice()
            _deviceSnapshots.value = emptyMap()
            _myOrders.value = emptyList()
            _pendingOrderInfo.value = null  // 防止退出后残留支付串触发支付宝
            _pendingAlipayDeepLink.value = null
            _awaitingExternalPayment.value = false
            _paying.value = false
            invalidatePayChannelCache()
            paymentRequestGate.reset()
            autoRebuildPending = false
            rebuiltFromOrderId = null
            store.saveOrderId("")
        }
    }

    // -----------------------------------------------------------------
    // 扫码 / 设备
    // -----------------------------------------------------------------

    fun onScan(raw: String) {
        if (raw.isBlank()) return
        if (!api.isUjingQrCode(raw)) {
            _toast.value = "这不是 U净洗衣机二维码"
            return
        }
        viewModelScope.launch {
            _busy.value = "正在识别设备"
            runCatching { api.scanWasherCode(raw) }
                .onSuccess { env ->
                    val r = env.data?.result
                    if (r == null || r.deviceId.isBlank()) {
                        _toast.value = "二维码无效"
                    } else {
                        _scan.value = r
                        store.saveQrCode(raw)  // 存扫码原文，后续重放 scanWasherCode 检查设备占用
                        // 换设备时清理旧设备的全部订单/倒计时残留，防止旧状态污染新设备
                        _paid.value = false
                        _startedAt.value = 0
                        _deviceRemain.value = null
                        _deviceRemainAt.value = 0
                        _orderFetchedAt.value = 0
                        paymentRequestGate.clearCooldown()
                        invalidatePayChannelCache()
                        autoRebuildPending = false
                        if (r.orderId > 0) {
                            _orderId.value = r.orderId.toString()
                            store.saveOrderId(r.orderId.toString())
                            log("扫到未完成订单 orderId=${r.orderId}")
                        } else {
                            _orderId.value = ""
                            _order.value = null
                            store.saveOrderId("")
                            log("该设备上没有属于你的未完成订单")
                        }
                        val d = SavedDevice(
                            qrCode = raw,
                            deviceId = r.deviceId,
                            macAddress = r.macAddress,
                            deviceTypeId = r.deviceTypeId,
                            moduleType = r.moduleType,
                            storeName = ""
                        )
                        _device.value = d
                        store.saveDevice(d)
                        if (!r.createOrderEnabled) {
                            _toast.value = r.reason ?: "该设备当前不可下单"
                        }
                        refreshProgram(silent = false)
                        // 有关联订单就顺手把它的状态拉回来：运行中 / 待支付 / 待启动
                        if (r.orderId > 0) refreshOrder(r.orderId.toString())
                    }
                }
                .onFailure { error(it) }
            _busy.value = null
        }
    }

    fun selectSavedDevice(saved: SavedDevice) {
        if (!saved.valid) return
        viewModelScope.launch {
            _device.value = saved
            store.saveDevice(saved)
            if (saved.qrCode.isNotBlank()) store.saveQrCode(saved.qrCode)
            _scan.value = null
            _program.value = null
            _order.value = null
            _paid.value = false
            _starting.value = false
            _startedAt.value = 0
            _deviceRemain.value = null
            _deviceRemainAt.value = 0
            _orderFetchedAt.value = 0
            val snapshot = _deviceSnapshots.value[saved.deviceId]
            _orderId.value = snapshot?.orderId.orEmpty()
            store.saveOrderId(_orderId.value)
            refreshProgram(silent = false)
            if (_orderId.value.isNotBlank()) refreshOrder()
            refreshDeviceSnapshot(saved)
        }
    }

    fun renameSavedDevice(saved: SavedDevice, name: String) {
        store.renameDevice(saved.deviceId, name)
        if (_device.value.deviceId == saved.deviceId) {
            _device.value = _device.value.copy(customName = name.trim())
        }
    }

    fun removeSavedDevice(saved: SavedDevice) {
        store.removeDevice(saved.deviceId)
        _deviceSnapshots.value = _deviceSnapshots.value - saved.deviceId
        if (_device.value.deviceId == saved.deviceId) {
            val next = store.savedDevicesFlow.value.firstOrNull()
            if (next != null) selectSavedDevice(next) else {
                _device.value = SavedDevice()
                _program.value = null
                _scan.value = null
                _order.value = null
            }
        }
    }

    private suspend fun refreshDeviceSnapshot(saved: SavedDevice) {
        runCatching { api.scanWasherCode(saved.qrCode) }
            .onSuccess { env ->
                val result = env.data?.result ?: return@onSuccess
                val remaining = result.orderId.takeIf { it > 0 }?.let { orderId ->
                    runCatching {
                        api.orderDetail(orderId.toString()).data?.remainSeconds
                    }.getOrNull()
                }
                _deviceSnapshots.value = _deviceSnapshots.value + (
                    saved.deviceId to UjingDeviceSnapshot(
                        label = if (result.createOrderEnabled) "空闲" else "使用中",
                        detail = result.reason.orEmpty(),
                        createOrderEnabled = result.createOrderEnabled,
                        orderId = result.orderId.takeIf { it > 0 }?.toString().orEmpty(),
                        remainingSeconds = remaining,
                        checkedAt = System.currentTimeMillis(),
                    )
                    )
            }
            .onFailure {
                _deviceSnapshots.value = _deviceSnapshots.value + (
                    saved.deviceId to UjingDeviceSnapshot(
                        label = "查询失败",
                        detail = it.message.orEmpty(),
                        checkedAt = System.currentTimeMillis(),
                    )
                    )
            }
    }

    fun refreshSavedDevices() {
        val token = api.token ?: return
        viewModelScope.launch {
            if (_busy.value == null) _busy.value = "正在刷新 U净设备"
            store.savedDevicesFlow.value.forEach { saved ->
                if (token.isBlank()) return@forEach
                refreshDeviceSnapshot(saved)
                if (_device.value.deviceId == saved.deviceId) {
                    val snapshot = _deviceSnapshots.value[saved.deviceId]
                    if (snapshot?.orderId?.isNotBlank() == true) {
                        _orderId.value = snapshot.orderId
                        store.saveOrderId(snapshot.orderId)
                    }
                }
            }
            _busy.value = null
        }
    }

    fun refreshMyOrders() {
        if (api.token.isNullOrBlank()) {
            _myOrders.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching { api.runningOrders() }
                .onSuccess { _myOrders.value = it }
                .onFailure {
                    if (_myOrders.value.isEmpty()) _myOrders.value = emptyList()
                }
        }
    }

    fun exportDevicesJson(): String {
        val array = JSONArray()
        store.savedDevicesFlow.value.forEach { saved ->
            array.put(
                JSONObject()
                    .put("qrCode", saved.qrCode)
                    .put("deviceId", saved.deviceId)
                    .put("macAddress", saved.macAddress)
                    .put("storeId", saved.storeId)
                    .put("deviceTypeId", saved.deviceTypeId)
                    .put("deviceNo", saved.deviceNo)
                    .put("storeName", saved.storeName)
                    .put("moduleType", saved.moduleType)
                    .put("customName", saved.customName)
            )
        }
        return JSONObject()
            .put("schema", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("devices", array)
            .toString(2)
    }

    fun importDevicesJson(raw: String): Result<Int> = runCatching {
        val root = runCatching { JSONObject(raw) }.getOrNull()
        val array = root?.optJSONArray("devices") ?: JSONArray(raw)
        val merged = store.savedDevicesFlow.value.associateBy { it.deviceId }.toMutableMap()
        for (index in 0 until array.length()) {
            val value = array.getJSONObject(index)
            val device = SavedDevice(
                qrCode = value.optString("qrCode"),
                deviceId = value.optString("deviceId"),
                macAddress = value.optString("macAddress"),
                storeId = value.optString("storeId"),
                deviceTypeId = value.optInt("deviceTypeId"),
                deviceNo = value.optString("deviceNo"),
                storeName = value.optString("storeName"),
                moduleType = value.optInt("moduleType", -1),
                customName = value.optString("customName"),
            )
            if (device.valid && device.qrCode.isNotBlank()) merged[device.deviceId] = device
        }
        store.saveDeviceList(merged.values.toList())
        merged.size
    }

    /**
     * 拉订单详情，刷新 [order] 状态。
     *
     * 关键用途：判断订单是否已在运行。服务端对已 RUNNING 的订单会静默拒绝
     * 重复启动（`ble/data/report/nordic` 返回 `{}`），所以启动前必须先看状态。
     */
    fun refreshOrder(id: String = _orderId.value) {
        if (id.isBlank()) { _order.value = null; return }
        viewModelScope.launch {
            runCatching { api.orderDetail(id) }
                .onSuccess { env ->
                    val o = env.data
                    if (o == null) {
                        log("订单 $id 详情为空（订单不存在或已结束）")
                        _order.value = null
                        return@onSuccess
                    }
                    // 订单已结束：本地收尾，回到「可以开新的一单」的状态。
                    // 刻意不让「已完成」常驻在界面上，只在结束的那一刻提示一次。
                    if (o.status == WasherStatus.COMPLETED) {
                        log("订单 $id 已完成，清除本地订单号")
                        _orderId.value = ""
                        _order.value = null
                        _paid.value = false
                        _startedAt.value = 0
                        _deviceRemain.value = null
                        store.saveOrderId("")
                        cancelOrderPoll()
                        stopTick()
                        _toast.value = "上一单已完成"
                        refreshProgram(silent = true)
                        scheduleIdlePoll()  // 订单结束，回到 idle 轮询
                        return@onSuccess
                    }
                    _order.value = o
                    // 记下这次拉取的时刻：界面用 remainTime 减掉「已过去的秒数」做本地倒计时
                    _orderFetchedAt.value = System.currentTimeMillis()
                    // 已付款、未运行 → 处于「等待启动」态。
                    // 重启 App 后 _paid 内存态会丢，这里按服务端 payFlag 还原。
                    // 单向赋值：服务端 payFlag 回写有延迟，已确认付过款就别再打回未付，
                    // 否则用户刚付完会看到按钮又变回「支付」（实测踩过）。
                    if (o.payFlag == 1 && !o.running) _paid.value = true
                    log(
                        "订单 $id 状态=${o.status} ${o.statusRemark.orEmpty()} " +
                            "模式=${o.deviceWashModelId} ${o.remainText} paid=${_paid.value}"
                    )
                    if (o.running) {
                        // 服务端已确认在跑，本地兜底标记可以撤了
                        _startedAt.value = 0
                        startTick()
                        cancelIdlePoll()
                        scheduleOrderPoll(id)
                    } else {
                        cancelOrderPoll()
                        scheduleIdlePoll()
                    }
                }
                .onFailure { error(it) }
        }
    }

    private var orderPollJob: Job? = null

    /**
     * 订单运行中时的定时刷新（15 秒一次）。
     *
     * 洗衣机完成后服务端不会主动推送，只能轮询 `orders/{id}/detail`，
     * 靠 `status` 从 40 变 50 来切「已完成」。订单一旦不再运行就自动停掉。
     * 秒级倒计时不靠它：界面用 [tick] 本地走秒，这里只负责校准与收尾。
     */
    private fun scheduleOrderPoll(id: String) {
        if (orderPollJob?.isActive == true) return
        orderPollJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                if (_orderId.value != id) return@launch
                refreshOrder(id)
                val o = _order.value
                if (o == null || !o.running) return@launch
            }
        }
    }

    private fun cancelOrderPoll() {
        orderPollJob?.cancel()
        orderPollJob = null
    }

    // -----------------------------------------------------------------
    // 设备占用状态轮询（Bug 1+2：别人下单后本 App 感知不到）
    // -----------------------------------------------------------------

    private var idlePollJob: Job? = null

    /**
     * 重放 scanWasherCode 检查设备是否可下单。
     *
     * 别人占用洗衣机时，服务端 scanWasherCode 会返回 createOrderEnabled=false + reason，
     * 这是唯一能感知「机器被别人用了」的 API。30 秒轮一次，idle 时自动运行。
     */
    fun checkDeviceStatus() {
        viewModelScope.launch { checkDeviceStatusSuspend() }
    }

    /** [checkDeviceStatus] 的 suspend 版本，供轮询协程和 forceSync 等待完成。 */
    private suspend fun checkDeviceStatusSuspend() {
        val qr = store.currentQrCode()
        if (qr.isBlank()) return
        runCatching { api.scanWasherCode(qr) }
            .onSuccess { env ->
                val r = env.data?.result ?: return@onSuccess
                _scan.value = r
                log("设备状态检查：createOrderEnabled=${r.createOrderEnabled} reason=${r.reason ?: "-"} orderId=${r.orderId}")
                if (r.orderId > 0) {
                    // 服务端发现了未完成订单（自己的或别处下的），同步过来
                    if (r.orderId.toString() != _orderId.value) {
                        _orderId.value = r.orderId.toString()
                        store.saveOrderId(r.orderId.toString())
                        refreshOrder(r.orderId.toString())
                    }
                } else if (_orderId.value.isNotBlank() && _order.value?.running != true) {
                    // 服务端说没有未完成订单，但本地还挂着一个旧的（且不在运行中）→ 清掉
                    log("服务端无未完成订单，清理本地残留 orderId=${_orderId.value}")
                    _orderId.value = ""
                    _order.value = null
                    _paid.value = false
                    _startedAt.value = 0
                    _deviceRemain.value = null
                    store.saveOrderId("")
                }
            }
            .onFailure { log("设备状态检查失败：${it.message}") }
    }

    /**
     * idle 时的定时设备状态检查（30 秒一次）。
     * 订单运行中时自动退出（由 scheduleOrderPoll 接管），订单结束后重启。
     */
    private fun scheduleIdlePoll() {
        if (idlePollJob?.isActive == true) return
        idlePollJob = viewModelScope.launch {
            while (isActive) {
                checkDeviceStatusSuspend()
                delay(30_000)
                if (_order.value?.running == true) return@launch
            }
        }
    }

    private fun cancelIdlePoll() {
        idlePollJob?.cancel()
        idlePollJob = null
    }

    private var tickJob: Job? = null

    /**
     * 每秒推一次 [tick]。
     *
     * 服务端的 `remainTime` 只在轮询时才回写（15 秒一次），直接显示会一顿一顿的；
     * 界面拿 `tick - orderFetchedAt` 算出已经过去多少秒，从服务端给的剩余时间里减掉，
     * 看起来就是每秒都在走。订单不再运行后由 [stopTick] 停掉，不空转。
     */
    private fun startTick() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                _tick.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    fun refreshProgram(silent: Boolean = false) {
        val d = _device.value
        if (!d.valid) { if (!silent) _toast.value = "尚未绑定设备"; return }
        viewModelScope.launch {
            if (!silent) _busy.value = "正在加载洗衣模式"
            runCatching { api.programInfo(d.deviceId) }
                .onSuccess { env ->
                    val p = env.data
                    if (p == null) { if (!silent) _toast.value = "未取到设备信息"; return@onSuccess }
                    _program.value = p
                    val merged = d.copy(
                        storeId = p.storeId.ifBlank { d.storeId },
                        macAddress = p.macAddress.ifBlank { d.macAddress },
                        deviceTypeId = if (p.deviceTypeId != 0) p.deviceTypeId else d.deviceTypeId,
                        deviceNo = p.deviceNo,
                        storeName = p.storeName,
                        moduleType = if (p.moduleType >= 0) p.moduleType else d.moduleType
                    )
                    _device.value = merged
                    store.saveDevice(merged)
                    val current = _selectedModelId.value
                    val exists = p.deviceWashModel.any { it.workModelId == current }
                    if (!exists) {
                        _selectedModelId.value =
                            p.deviceWashModel.firstOrNull { it.isDefault }?.workModelId
                                ?: p.deviceWashModel.firstOrNull()?.workModelId
                                ?: 0
                    }
                }
                .onFailure { if (!silent) error(it) }
            if (!silent) _busy.value = null
        }
    }

    /**
     * 右上角刷新：拉设备信息 + 拉订单状态；订单在跑时顺带连一次蓝牙，
     * 把机身显示屏上的剩余时间读回来（这是唯一能拿到真实剩余时间的途径）。
     */
    fun refreshAll() {
        refreshProgram(silent = true)
        refreshOrder()
        if (_order.value?.running == true) syncDevice()
    }

    /**
     * 连一次洗衣机，读回机身侧剩余时间。
     *
     * 只在订单运行中才有意义；失败不打扰用户（机器不在身边时必然失败）。
     */
    fun syncDevice() {
        val d = _device.value
        if (!d.bleReady || !ModuleType.supportsBle(d.moduleType)) return
        viewModelScope.launch {
            val outcome = runCatching {
                when (d.moduleType) {
                    ModuleType.TYPE_BLE_NORDIC -> {
                        val w = NordicWasher(getApplication(), api) { log(it) }
                        w.sync(d.macAddress, d.deviceId) as? NordicWasher.Outcome.Started
                    }

                    ModuleType.TYPE_BLE_CYPRESS -> {
                        val w = CypressWasher(getApplication(), api) { log(it) }
                        w.sync(d.macAddress, d.deviceId)
                        null // Cypress 是透传通道，没有可读的机身状态特征
                    }

                    else -> null
                }
            }.getOrElse { log("读机身状态失败：${it.message}"); null }

            val minutes = outcome?.remainMinutes
            val statusTime = outcome?.statusTimeMs ?: 0L

            if (minutes == null) return@launch

            // 机身显示 0 分钟 = 洗程已结束，推动服务端结算
            if (minutes == 0) {
                if (_order.value?.running == true) {
                    log("机身显示 0 分钟，洗程已结束，推动服务端结算")
                    refreshOrder()
                }
                return@launch
            }

            // 时间戳比对：机身时间戳早于订单开始 → 待机值（上次洗程总时长），不作倒计时基准
            val orderStart = _order.value?.startMs ?: 0L
            if (statusTime > 0 && orderStart > 0 && statusTime < orderStart - 60_000) {
                log("机身时间戳早于订单开始（待机值），不作为倒计时基准")
                return@launch
            }

            // 只有当前订单确实在跑才记录
            if (_order.value?.running != true) {
                log("机身显示 $minutes 分钟，但订单已不在运行，不作为倒计时基准")
                return@launch
            }
            _deviceRemain.value = minutes * 60
            _deviceRemainAt.value = System.currentTimeMillis()
            log("机身剩余 $minutes 分钟（已与显示屏对齐）")
        }
    }

    fun selectModel(id: Int) {
        _selectedModelId.value = id
        viewModelScope.launch { store.saveLastWorkModel(id) }
    }

    fun selectedModel(): DeviceWashModel? =
        _program.value?.deviceWashModel?.firstOrNull { it.workModelId == _selectedModelId.value }

    // -----------------------------------------------------------------
    // 下单
    // -----------------------------------------------------------------

    /**
     * 下单。
     *
     * @param thenPay 下单成功后是否立刻接着发起支付 —— 「一键开洗」走这条路。
     */
    fun createOrder(thenPay: Boolean = false) {
        val d = _device.value
        val p = _program.value
        val m = selectedModel()
        if (!d.valid || p == null || m == null) { _toast.value = "请先扫码并选择模式"; return }
        if (createOrderInFlight) {
            log("忽略重复下单请求：订单正在创建")
            return
        }
        createOrderInFlight = true
        viewModelScope.launch {
            _busy.value = "正在下单"
            try {
                val order = api.createOrder(
                    CreateOrderRequest(
                        deviceTypeId = d.deviceTypeId,
                        deviceId = d.deviceId,
                        deviceWashModelId = m.workModelId,
                        storeId = d.storeId
                    )
                ).data
                if (order == null || order.orderId.isBlank()) {
                    _toast.value = "下单失败：无订单号"
                } else {
                    _orderId.value = order.orderId
                    paymentRequestGate.clearCooldown()
                    invalidatePayChannelCache()
                    store.saveOrderId(order.orderId)
                    _toast.value = "下单成功（待支付）"
                    val merged = d.copy(macAddress = order.macAddress.ifBlank { d.macAddress })
                    _device.value = merged
                    store.saveDevice(merged)
                    log("下单成功：status=${order.status} orderIdLength=${order.orderId.length}")
                    loadPayMethods(thenPay)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logPaymentFailure("orders/create", e)
                error(e)
            } finally {
                createOrderInFlight = false
                _busy.value = null
            }
        }
    }

    /**
     * 取消当前订单。
     *
     * 主要用途：清掉「卡在服务端」的旧订单。未支付的订单会立即释放设备；
     * 已支付的订单按服务端退款规则处理。成功后本机不再持有待启动订单号。
     */
    fun cancelOrder() {
        val oid = _orderId.value
        if (oid.isBlank()) { _toast.value = "没有可取消的订单"; return }
        viewModelScope.launch {
            _busy.value = "正在取消订单"
            runCatching { api.cancelOrder(oid) }
                .onSuccess {
                    log("订单已取消：orderIdLength=${oid.length}")
                    _toast.value = "订单已取消"
                    _orderId.value = ""
                    _order.value = null
                    _paid.value = false
                    _startedAt.value = 0
                    paymentRequestGate.clearCooldown()
                    invalidatePayChannelCache()
                    autoRebuildPending = false
                    _deviceRemain.value = null
                    store.saveOrderId("")
                    cancelOrderPoll()
                    stopTick()
                    refreshProgram(silent = true)
                }
                .onFailure { e ->
                    log("取消失败：${safeDiagnosticText(e.message, 160)}")
                    _toast.value = "取消失败：${e.message?.take(80)}"
                }
            _busy.value = null
        }
    }

    // -----------------------------------------------------------------
    // 支付（APP 内唤起支付宝）
    //
    // 链路对齐 cashierNew.js：
    //   orders/create → orderId
    //   GET payment/methods          → 渠道列表
    //   GET payment/arguments        → payInfo.orderInfo（U净 商户签名串）
    //   PayTask.pay(orderInfo)       → 支付宝收银台（用户指纹/面容确认）
    //   GET payment/{id}/payStatus   → 回执
    //   GET app/payment/{id}/lastPayStatus → 轮询（status == "3" 成功）
    //
    // 我们不经手任何资金：orderInfo 由 U净 服务端签发，钱进 U净 商户账户。
    // -----------------------------------------------------------------

    fun selectChannel(channel: String) {
        if (channel.equals("alipay", ignoreCase = true)) {
            _selectedChannel.value = "alipay"
        }
    }

    private fun currentPayChannelCacheKey(orderId: String): PayChannelCacheKey =
        PayChannelCacheKey(
            orderId = orderId,
            deviceId = _device.value.deviceId,
            serviceSubjectId = _program.value?.serviceSubjectId?.toString().orEmpty(),
        )

    private fun invalidatePayChannelCache() {
        _payChannels.value = emptyList()
        _selectedChannel.value = ""
        payChannelCacheKey = null
        payChannelsRefreshedAt = 0L
    }

    /**
     * 拉收银台渠道列表。
     *
     * @param thenPay 本次拉取成功后是否立刻发起支付。
     */
    fun loadPayMethods(thenPay: Boolean = false) {
        val oid = _orderId.value
        if (oid.isBlank()) return
        viewModelScope.launch {
            try {
                val resolution = refreshPayChannels(oid)
                if (thenPay) {
                    if (resolution.channel != null) {
                        startPayInternal(forceChannelRefresh = false)
                    } else {
                        _toast.value = channelUnsupportedMessage()
                    }
                }
            } catch (e: Throwable) {
                logPaymentFailure("payment/methods", e)
                if (thenPay) error(e)
            }
        }
    }

    private suspend fun fetchPayChannels(orderId: String): List<PayChannel> =
        api.payMethods(
            orderId = orderId,
            serviceSubjectId = _program.value?.serviceSubjectId?.toString(),
            deviceId = _device.value.deviceId,
        ).data?.channels.orEmpty()

    private fun applyPayChannels(
        channels: List<PayChannel>,
        cacheKey: PayChannelCacheKey,
    ): PayChannel? {
        val list = channels
            .filter { !it.needUpgrade }
            .sortedByDescending { it.weight4Display }
        _payChannels.value = list
        payChannelCacheKey = cacheKey
        payChannelsRefreshedAt = System.currentTimeMillis()
        val selected = preferredAlipayChannel(list)
        _selectedChannel.value = selected?.channel.orEmpty()
        log(
            "支付渠道已刷新：count=${list.size} " +
                "channels=${list.joinToString(",") { it.channel }}"
        )
        selected?.let {
            val mode = when {
                it.alipaySdkCompatible -> "PayTask"
                it.alipayDeepLinkCompatible -> "AppDeepLink"
                else -> "unsupported"
            }
            log("选择支付宝渠道：channel=${it.channel} mode=$mode branchType=${it.branchType}")
        }
        return selected
    }

    private suspend fun refreshPayChannels(orderId: String): AlipayChannelResolution {
        val key = currentPayChannelCacheKey(orderId)
        val channels = fetchPayChannels(orderId)
        return AlipayChannelResolution(
            channel = applyPayChannels(channels, key),
            refreshed = true,
        )
    }

    private suspend fun ensureAlipayChannel(
        orderId: String,
        forceRefresh: Boolean,
    ): AlipayChannelResolution {
        val key = currentPayChannelCacheKey(orderId)
        val cacheFresh = System.currentTimeMillis() - payChannelsRefreshedAt <= 30_000L
        if (!forceRefresh && payChannelCacheKey == key && cacheFresh) {
            return AlipayChannelResolution(
                channel = preferredAlipayChannel(_payChannels.value),
                refreshed = false,
            )
        }
        return refreshPayChannels(orderId)
    }

    /** 一键开洗：没有订单就先下单，有订单就直接付款。 */
    fun payAndWash() {
        if (_paying.value) {
            log("忽略一键开洗连点：支付流程正在进行")
            return
        }
        if (_orderId.value.isNotBlank()) startPay() else createOrder(thenPay = true)
    }

    /**
     * 用户主动支付入口：强制刷新与当前订单绑定的渠道，再进入统一状态机。
     */
    fun startPay() {
        startPayInternal(forceChannelRefresh = true)
    }

    private fun startPayInternal(forceChannelRefresh: Boolean) {
        val oid = _orderId.value
        if (oid.isBlank()) {
            _toast.value = "请先下单"
            return
        }
        when (val decision = paymentRequestGate.tryStart()) {
            PaymentStartDecision.InFlight -> {
                log("忽略支付按钮连点：已有支付请求正在处理")
                return
            }
            is PaymentStartDecision.Cooling -> {
                _toast.value = "支付请求正在处理，请等待 ${decision.seconds} 秒后再试"
                return
            }
            PaymentStartDecision.Allowed -> Unit
        }
        _paying.value = true
        viewModelScope.launch {
            _busy.value = "正在准备支付"
            var rebuildStaleOrder = false
            val allowAutoRebuild = !autoRebuildPending
            try {
                val firstLookup = queryPaymentOrder(oid)
                if (firstLookup is PaymentOrderLookup.Failed) {
                    logOrderQueryFailure("支付前", firstLookup.error)
                    _toast.value = "暂时无法确认订单状态，请稍后重试"
                    return@launch
                }
                val order = (firstLookup as PaymentOrderLookup.Found).order
                _order.value = order
                val firstDisposition = paymentOrderDisposition(order)
                logPaymentOrderState("支付前", order, firstDisposition)
                when (firstDisposition) {
                    PaymentOrderDisposition.ALREADY_PAID -> {
                        _paid.value = true
                        _toast.value = "订单已支付，可以启动洗衣机"
                        return@launch
                    }
                    PaymentOrderDisposition.PROCESSING -> {
                        _toast.value = "订单正在支付处理中，正在确认结果"
                        pollPayStatus(oid)
                        return@launch
                    }
                    PaymentOrderDisposition.REBUILDABLE -> {
                        if (allowAutoRebuild) {
                            rebuildStaleOrder = prepareStaleOrderRebuild(oid, order)
                        } else {
                            _toast.value = "订单支付已超时，请取消订单后重新下单"
                        }
                        return@launch
                    }
                    PaymentOrderDisposition.TERMINAL -> {
                        _toast.value = "当前订单已结束，不能继续支付，请重新下单"
                        return@launch
                    }
                    PaymentOrderDisposition.PAYABLE,
                    PaymentOrderDisposition.UNKNOWN,
                    -> Unit
                }

                val resolution = ensureAlipayChannel(
                    orderId = oid,
                    forceRefresh = forceChannelRefresh,
                )
                val channel = resolution.channel
                if (channel == null) {
                    log("支付渠道不支持 PayTask：channels=${_payChannels.value.joinToString(",") { it.channel }}")
                    _toast.value = channelUnsupportedMessage()
                    return@launch
                }

                val result = requestPayArguments(oid, channel)
                val data = parsePayArguments(result.envelope.data)
                logPaymentArguments(channel, data, result, resolution.refreshed)
                if (consumePaymentArguments(oid, channel, data)) return@launch

                val latestLookup = queryPaymentOrder(oid)
                if (latestLookup is PaymentOrderLookup.Failed) {
                    logOrderQueryFailure("支付参数缺失后", latestLookup.error)
                    _toast.value = "暂时无法确认订单状态，请稍后重试"
                    return@launch
                }
                val latest = (latestLookup as PaymentOrderLookup.Found).order
                _order.value = latest
                val latestDisposition = paymentOrderDisposition(latest)
                logPaymentOrderState("支付参数缺失后", latest, latestDisposition)
                when (latestDisposition) {
                    PaymentOrderDisposition.ALREADY_PAID -> {
                        _paid.value = true
                        _toast.value = "订单已支付，可以启动洗衣机"
                        return@launch
                    }
                    PaymentOrderDisposition.PROCESSING -> {
                        _toast.value = "订单正在支付处理中，正在确认结果"
                        pollPayStatus(oid)
                        return@launch
                    }
                    PaymentOrderDisposition.TERMINAL -> {
                        _toast.value = "当前订单已结束，请重新下单"
                        return@launch
                    }
                    PaymentOrderDisposition.PAYABLE,
                    PaymentOrderDisposition.REBUILDABLE,
                    -> {
                        if (
                            latest.orderId == oid &&
                            data.payloadKind == PaymentPayloadKind.NONE &&
                            canRebuildAfterMissingPayment(latest.status) &&
                            allowAutoRebuild &&
                            rebuiltFromOrderId != oid
                        ) {
                            rebuildStaleOrder = prepareStaleOrderRebuild(oid, latest)
                            return@launch
                        }
                    }
                    PaymentOrderDisposition.UNKNOWN -> Unit
                }
                _toast.value = missingPaymentMessage(data, resolution.refreshed)
            } catch (e: UjingApi.ApiException) {
                logPaymentFailure("payment/arguments", e)
                if (e.isDuplicatePaymentRequest) {
                    paymentRequestGate.blockDuplicate(10)
                    log("支付参数重复请求 6020208，进入 10 秒冷却")
                    _toast.value = "支付请求正在处理，请等待 10 秒后再试"
                    refreshOrder(oid)
                } else {
                    error(e)
                }
            } catch (e: IOException) {
                logPaymentFailure("network", e)
                _toast.value = "网络请求失败，暂时无法确认支付状态，请稍后重试"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logPaymentFailure("unexpected", e)
                error(e)
            } finally {
                paymentRequestGate.finish()
                _paying.value = false
                _busy.value = null
                if (rebuildStaleOrder) {
                    autoRebuildPending = true
                    _toast.value = "旧订单支付参数已失效，正在重新下单"
                    viewModelScope.launch {
                        delay(1_200)
                        createOrder(thenPay = true)
                    }
                } else {
                    autoRebuildPending = false
                }
            }
        }
    }

    private suspend fun queryPaymentOrder(orderId: String): PaymentOrderLookup =
        try {
            val order = api.orderDetail(orderId).data
            if (order == null) {
                PaymentOrderLookup.Failed(IllegalStateException("订单详情 data 为空"))
            } else {
                PaymentOrderLookup.Found(order)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            PaymentOrderLookup.Failed(e)
        }

    private suspend fun prepareStaleOrderRebuild(
        orderId: String,
        order: OrderData,
    ): Boolean {
        if (rebuiltFromOrderId == orderId) {
            _toast.value = "该订单无法签发支付参数，请取消订单后重新下单"
            return false
        }
        val cancellation = if (order.status == WasherStatus.RESERVED) {
            runCatching { api.cancelOrder(orderId) }
        } else {
            Result.success(Unit)
        }
        return cancellation.fold(
            onSuccess = {
                rebuiltFromOrderId = orderId
                _orderId.value = ""
                _order.value = null
                _paid.value = false
                _starting.value = false
                _startedAt.value = 0
                _deviceRemain.value = null
                _deviceRemainAt.value = 0
                _orderFetchedAt.value = 0
                invalidatePayChannelCache()
                store.saveOrderId("")
                cancelOrderPoll()
                stopTick()
                refreshProgram(silent = true)
                log("旧订单 status=${order.status} 无法签发支付参数，已准备重建")
                true
            },
            onFailure = {
                logPaymentFailure("cancel/stale-order", it)
                _toast.value = "旧订单支付参数已失效，请手动取消后重新下单"
                false
            },
        )
    }

    private suspend fun requestPayArguments(
        orderId: String,
        channel: PayChannel,
    ): PaymentArgumentsResult = api.payArguments(
        channel = channel.channel,
        orderId = orderId,
        payVersion = 1,
        chooseSign = paymentChooseSign(channel),
        branchType = channel.branchType,
        jumpToAliMini = channel.jumpToAliMini,
        couponId = "",
        redPacketId = "0",
        isUseRedPacket = false,
        alipayF2FNoAds = false,
    )

    private suspend fun consumePaymentArguments(
        orderId: String,
        channel: PayChannel,
        data: PayArgumentsData,
    ): Boolean {
        when {
            data.alreadyPaid -> {
                _paid.value = true
                _toast.value = "订单已支付，可以启动洗衣机"
                refreshOrder(orderId)
                return true
            }
            data.skipPay -> {
                log("服务端免密直付成功（skipPay=true）")
                _toast.value = "已免密扣款，正在刷新订单"
                refreshOrder(orderId)
                return true
            }
            data.jumpToQuery -> {
                _toast.value = "正在确认支付结果"
                pollPayStatus(orderId)
                return true
            }
            data.orderStr.isNotBlank() -> {
                log("取到有效支付宝 SDK 支付串：length=${data.orderStr.length}")
                _pendingOrderInfo.value = data.orderStr
                return true
            }
            channel.alipayDeepLinkCompatible && data.rawPaymentValue.isNotBlank() -> {
                log(
                    "取到支付宝 App 跳转参数：kind=${data.payloadKind} " +
                        "length=${data.payloadLength} hint=${data.safeValueHint}"
                )
                if (!isTrustedAlipayDeepLink(data.rawPaymentValue)) {
                    log("拒绝非支付宝官方的自定义跳转")
                    _toast.value = "服务端返回的支付宝跳转无法验证，已停止打开"
                    return true
                }
                _pendingAlipayDeepLink.value = data.rawPaymentValue
                _awaitingExternalPayment.value = true
                _toast.value = "正在打开支付宝收银台"
                return true
            }
        }
        return false
    }

    private fun channelUnsupportedMessage(): String {
        val f2f = _payChannels.value.any {
            it.channel.equals("alipayF2F", ignoreCase = true)
        }
        return if (f2f) {
            "当前订单的支付宝渠道暂不可用，请稍后刷新重试"
        } else {
            "当前订单未提供可用的支付宝支付方式"
        }
    }

    private fun missingPaymentMessage(
        data: PayArgumentsData,
        channelsRefreshed: Boolean,
    ): String {
        val prefix = if (channelsRefreshed) "支付渠道已刷新；" else ""
        val detail = when (data.payloadKind) {
            PaymentPayloadKind.NONE ->
                data.payloadReason.ifBlank { "服务端未返回支付参数" }
            PaymentPayloadKind.URL ->
                "服务端返回了其他网页支付地址，当前订单不能安全打开"
            PaymentPayloadKind.APP_SCHEME ->
                "服务端返回了未支持的应用跳转，当前订单不能安全打开"
            PaymentPayloadKind.JSON_OBJECT ->
                "服务端返回 JSON，但未提供官方 payInfo.orderInfo SDK 支付串"
            PaymentPayloadKind.QR_OR_TEXT ->
                "服务端返回二维码或普通文本，未提供支付宝 SDK 支付串"
            PaymentPayloadKind.INVALID_ALIPAY ->
                "支付宝参数缺少必要字段或签名为空，不能交给 SDK"
            PaymentPayloadKind.ALIPAY_SDK ->
                "支付宝支付串状态异常，请稍后重试"
        }
        return prefix + detail
    }

    private fun logPaymentArguments(
        channel: PayChannel,
        data: PayArgumentsData,
        result: PaymentArgumentsResult,
        channelsRefreshed: Boolean,
    ) {
        val diagnostics = result.diagnostics
        val fields = diagnostics.fieldTypes.entries
            .take(24)
            .joinToString(",") { "${it.key}:${it.value}" }
        log(
            "支付参数：http=${diagnostics.httpStatus} business=${result.envelope.code} " +
                "bytes=${diagnostics.bodyBytes} " +
                "channel=${channel.channel} branchType=${channel.branchType} " +
                "payVersion=1 jumpToAliMini=${channel.jumpToAliMini} " +
                "chooseSign=${paymentChooseSign(channel) ?: "omitted"} " +
                "refreshed=$channelsRefreshed source=${data.sourcePath.ifBlank { "-" }} " +
                "kind=${data.payloadKind} payLength=${data.payloadLength} " +
                "safeValue=${data.safeValueHint.ifBlank { "-" }} " +
                "skipPay=${data.skipPay} jumpToQuery=${data.jumpToQuery} " +
                "payFlag=${data.payFlag} isPaid=${data.isPaid} fields=$fields"
        )
    }

    private fun logOrderQueryFailure(stage: String, error: Throwable) {
        logPaymentFailure("order/detail/$stage", error)
    }

    private fun logPaymentOrderState(
        stage: String,
        order: OrderData,
        disposition: PaymentOrderDisposition,
    ) {
        log(
            "订单查询[$stage]：result=success status=${order.status} " +
                "payFlag=${order.payFlag ?: -1} disposition=$disposition"
        )
    }

    private fun logPaymentFailure(stage: String, error: Throwable) {
        if (error is UjingApi.ApiException) {
            val fields = error.diagnostics?.fieldTypes
                ?.entries
                ?.take(24)
                ?.joinToString(",") { "${it.key}:${it.value}" }
                .orEmpty()
            log(
                "支付诊断[$stage]：type=business http=${error.httpStatus ?: -1} " +
                    "code=${error.code} message=${safeDiagnosticText(error.message, 120)} fields=$fields"
            )
        } else {
            log(
                "支付诊断[$stage]：type=${error.javaClass.simpleName} " +
                    "message=${safeDiagnosticText(error.message, 160)}"
            )
        }
    }

    private fun safeDiagnosticText(value: String?, limit: Int): String =
        value.orEmpty()
            .replace(Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+"""), "[url]")
            .take(limit)

    /** UI 层唤起支付宝后的结果回传。 */
    fun onPayResult(resultStatus: String, memo: String) {
        val oid = _orderId.value
        log("支付宝返回 resultStatus=$resultStatus memo=${safeDiagnosticText(memo, 120)}")
        viewModelScope.launch {
            when (resultStatus) {
                "9000" -> {
                    _toast.value = "支付成功"
                    // 付款成功 → 立刻切「启动洗衣机」，不等 payStatusCommon 网络回执（可能耗时数秒）。
                    // refreshOrder 里的 _paid 是单向赋值，不会把这里打回未付。
                    _paid.value = true
                    runCatching { api.payStatusCommon(oid, _selectedChannel.value) }
                        .onFailure { log("支付回执失败：${it.message}") }
                    refreshOrder(oid)
                }
                "8000" -> {
                    _toast.value = "支付处理中，正在确认结果"
                    pollPayStatus(oid)
                }
                "6001" -> _toast.value = "已取消支付"
                else -> _toast.value = "支付未完成（$resultStatus ${memo.ifBlank { "" }}）"
            }
        }
    }

    fun consumePendingOrderInfo() { _pendingOrderInfo.value = null }

    fun consumePendingAlipayDeepLink() { _pendingAlipayDeepLink.value = null }

    fun onAlipayDeepLinkLaunchFailed(message: String) {
        _pendingAlipayDeepLink.value = null
        _awaitingExternalPayment.value = false
        _paying.value = false
        _busy.value = null
        log("打开支付宝失败：${safeDiagnosticText(message, 160)}")
        _toast.value = "无法打开支付宝：${message.take(80)}"
    }

    /** 从支付宝返回时不能把 resultCode 当成付款结果，必须向 U净查询订单状态。 */
    fun onExternalPaymentReturned() {
        if (!_awaitingExternalPayment.compareAndSet(expect = true, update = false)) return
        val oid = _orderId.value
        log("从支付宝返回，开始确认订单支付状态")
        if (oid.isBlank()) {
            _toast.value = "暂无待确认订单"
            return
        }
        paymentRequestGate.finish()
        _paying.value = true
        _toast.value = "已返回 Cumpus，正在确认支付结果"
        viewModelScope.launch {
            try {
                pollPayStatus(oid)
            } finally {
                _paying.value = false
                _busy.value = null
            }
        }
    }

    /** 轮询支付结果，最多 5 次 × 1.5s。 */
    private suspend fun pollPayStatus(orderId: String) {
        var lastError: Throwable? = null
        repeat(5) { i ->
            val ok = runCatching { api.lastPayStatus(orderId) }
                .onFailure {
                    lastError = it
                    log("查支付状态失败：${safeDiagnosticText(it.message, 160)}")
                }
                .getOrNull()?.data?.success == true
            if (ok) {
                log("支付状态确认为成功（第 ${i + 1} 次查询）")
                _toast.value = "支付成功"
                _paid.value = true
                refreshOrder(orderId)
                return
            }
            kotlinx.coroutines.delay(1500)
        }
        refreshOrder(orderId)
        _toast.value = if (lastError != null) {
            "暂时无法确认支付结果，请稍后刷新订单"
        } else {
            "支付结果尚未确认，请稍后刷新订单"
        }
    }

    // -----------------------------------------------------------------
    // 蓝牙启动
    // -----------------------------------------------------------------

    fun startWash(command: String = NordicCommand.START_WASH) {
        val d = _device.value
        if (!d.valid) { _toast.value = "尚未绑定设备"; return }
        if (!d.bleReady) {
            _toast.value = "设备蓝牙地址尚未同步，请点击右上角刷新后再试"
            log("拒绝启动：设备 ${d.deviceId} 缺少 MAC，先刷新设备详情")
            return
        }
        if (!ModuleType.supportsBle(d.moduleType)) {
            _toast.value = "该设备模块(${d.moduleType})不支持蓝牙直连"
            return
        }
        if (_orderId.value.isBlank()) {
            // 没有订单号就别连蓝牙了：服务端不会签发指令，连了也是白跑一趟（实测会静默空转）
            _toast.value = "没有待启动的订单。请先用支付宝小程序/U净扫码下单并支付，回到本页重新扫码"
            log("拒绝启动：当前无待启动订单（orderId 为空）")
            return
        }
        val o = _order.value
        if (o != null && o.running) {
            // 订单已经在跑。服务端对这种订单会静默拒绝再次签发指令（返回 `{}`），
            // 再连一次蓝牙也只会拿到空指令，纯属浪费用户时间。
            val tail = o.remainText.ifBlank { o.statusRemark.orEmpty() }
            _toast.value = "订单已在运行中${if (tail.isBlank()) "" else "（$tail）"}"
            log("拒绝启动：订单 ${o.orderId} 已在运行（status=${o.status} $tail）")
            return
        }
        viewModelScope.launch {
            // 「启动中…」过渡态：蓝牙连接 + 上报 + 写指令通常要几秒，
            // 这段时间按钮必须锁住，否则用户会连点、重复上报（服务端会静默拒绝）。
            _starting.value = true
            _busy.value = "蓝牙连接中"
            try {
                when (d.moduleType) {
                    ModuleType.TYPE_BLE_NORDIC -> runNordicStart(d, command)
                    ModuleType.TYPE_BLE_CYPRESS -> runCypressStart(d, command)
                    else -> {
                        _toast.value = "模块 ${d.moduleType} 暂不支持蓝牙直连"
                        log("不支持的模块类型 ${d.moduleType}")
                    }
                }
            } finally {
                _starting.value = false
                _busy.value = null
            }
        }
    }

    private suspend fun runNordicStart(d: SavedDevice, command: String) {
        val washer = NordicWasher(getApplication(), api) { log(it) }
        val result = runCatching {
            washer.run(
                macAddress = d.macAddress,
                deviceId = d.deviceId,
                orderId = _orderId.value.ifBlank { null },
                command = command,
                reportType = 2
            )
        }.getOrElse { NordicWasher.Outcome.Failed(BleError.CONNECT_FAILED, it.message ?: "异常") }
        when (result) {
            is NordicWasher.Outcome.Started -> {
                _toast.value = "已启动，洗衣机开始工作"
                // 指令已下发。这里刻意不清 _paid：服务端可能还停在下单时的状态，
                // 由 refreshOrder 按真实 status 接管（21/40 会走 running 分支）。
                // 在服务端反应过来之前，用本地时间戳先兜一段「运行中」。
                _orderFetchedAt.value = System.currentTimeMillis()
                _startedAt.value = System.currentTimeMillis()
                // 顺手记下机身侧剩余时间：这是显示屏上的数，后面倒计时以它为准
                val statusTime = result.statusTimeMs
                result.remainMinutes?.takeIf { it > 0 }?.let { m ->
                    // 时间戳比对：机身时间戳早于订单开始 → 待机值，不作倒计时基准
                    val orderStart = _order.value?.startMs ?: 0L
                    if (statusTime > 0 && orderStart > 0 && statusTime < orderStart - 60_000) {
                        log("机身时间戳早于订单开始（待机值），不作为倒计时基准")
                    } else {
                        _deviceRemain.value = m * 60
                        _deviceRemainAt.value = System.currentTimeMillis()
                        log("机身剩余 $m 分钟（读自 STATUS 特征）")
                    }
                }
                if (result.remainMinutes == 0) {
                    log("机身显示 0 分钟（刚启动或待机值），不作为倒计时基准")
                }
                log("Nordic 启动成功")
                // 给服务端一点时间推进状态，然后拉一次校准
                delay(3_000)
                refreshOrder()
            }
            is NordicWasher.Outcome.Pending -> {
                _toast.value = "设备已有订单：${result.orderId}"
                log("已有订单 ${result.orderId}")
            }
            is NordicWasher.Outcome.Failed -> {
                _toast.value = "启动失败[${result.code}] ${result.message}"
                log("启动失败 ${result.code} ${result.message}")
            }
        }
    }

    private suspend fun runCypressStart(d: SavedDevice, command: String) {
        val washer = CypressWasher(getApplication(), api) { log(it) }
        val result = runCatching {
            washer.run(
                macAddress = d.macAddress,
                deviceId = d.deviceId,
                command = command,
                operation = CypressWasher.OP_CONTROL_DEVICE
            )
        }.getOrElse { CypressWasher.Outcome.Failed(BleError.CONNECT_FAILED, it.message ?: "异常") }
        when (result) {
            is CypressWasher.Outcome.Started -> {
                _toast.value = "已启动，洗衣机开始工作"
                // 同 Nordic：不清 _paid，交给 refreshOrder 按真实 status 接管
                _orderFetchedAt.value = System.currentTimeMillis()
                _startedAt.value = System.currentTimeMillis()
                log("Cypress 启动成功，写入 ${result.packageCount} 包")
                delay(3_000)
                refreshOrder()
            }
            is CypressWasher.Outcome.Failed -> {
                _toast.value = "启动失败[${result.code}] ${result.message}"
                log("启动失败 ${result.code} ${result.message}")
            }
        }
    }

    fun syncStatus() {
        viewModelScope.launch { syncStatusSuspend() }
    }

    /** [syncStatus] 的 suspend 版本，供 forceSync 等待 BLE 上报完成。 */
    private suspend fun syncStatusSuspend() {
        val d = _device.value
        if (!d.valid) return
        if (!ModuleType.supportsBle(d.moduleType)) {
            _toast.value = "该设备模块(${d.moduleType})不支持蓝牙直连"
            return
        }
        _busy.value = "同步设备状态"
        runCatching {
            when (d.moduleType) {
                ModuleType.TYPE_BLE_NORDIC ->
                    NordicWasher(getApplication(), api) { log(it) }.sync(d.macAddress, d.deviceId)
                ModuleType.TYPE_BLE_CYPRESS ->
                    CypressWasher(getApplication(), api) { log(it) }.sync(d.macAddress, d.deviceId)
                else -> {
                    _toast.value = "模块 ${d.moduleType} 暂不支持蓝牙直连"
                    log("不支持的模块类型 ${d.moduleType}")
                }
            }
        }.onFailure { error(it) }
        refreshProgram(silent = true)
        // 同步顺带把订单状态刷一遍：type=3 上报会让服务端回写最新 orderStatus
        if (_orderId.value.isNotBlank()) refreshOrder()
        _busy.value = null
    }

    /**
     * 强制同步（Bug 4：服务端状态卡死导致误报「洗衣机在使用中」）。
     *
     * 连一次蓝牙上报（reportType=3）可推动服务端结算卡住的订单，
     * 然后重新检查设备占用状态。室友首次扫码遇到「使用中」但实际空闲时点这个。
     */
    fun forceSync() {
        viewModelScope.launch {
            _busy.value = "正在强制同步"
            syncStatusSuspend()   // 等 BLE 连接+上报完成（约 3-5 秒）
            delay(3_000)          // 等服务端结算
            checkDeviceStatusSuspend()  // 等状态检查完成
            _busy.value = null
        }
    }
}

