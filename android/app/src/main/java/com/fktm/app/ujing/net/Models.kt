package com.fktm.app.ujing.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/**
 * 兼容「布尔字段收到数字」的服务端。
 *
 * U净 服务端的 `deviceWashModel[]` 把 `isPromotion` / `isDefault` / `hide`
 * 以 **数字 0/1** 返回，而非 JSON 布尔；kotlinx.serialization 严格模式会抛
 * `Failed to parse literal '0' as a boolean value`，导致整个 ProgramInfo 解析失败、
 * 洗衣模式列表为空。
 */
object FlexibleBoolSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val input = decoder as? JsonDecoder ?: return false
        val prim = input.decodeJsonElement() as? JsonPrimitive ?: return false
        val raw = prim.content.trim()
        return when (raw.lowercase()) {
            "1", "true" -> true
            "0", "0.0", "", "false", "null" -> false
            else -> raw.toDoubleOrNull()?.let { it != 0.0 } ?: false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

/**
 * 数字字段的宽容反序列化器。
 *
 * 服务端同一字段在不同接口/版本下可能是 JSON 数字（`40`）、字符串（`"40"`）
 * 或浮点（`40.0`）。`isLenient` 只解决"字符串里是合法 JSON"的场景，
 * 不解决 `Int` 字段收到 `"40"`，因此需要它。
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return 0
        val prim = input.decodeJsonElement() as? JsonPrimitive ?: return 0
        val raw = prim.content.trim()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return 0
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * 云端响应模型。
 *
 * 统一包裹：`{ code, reason, message, data, metadata }`，code ∈ {0, 200} 视为成功。
 * 字段全部可空 + 默认值，兼容服务端灰度期字段增删。
 */
val UjingJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val reason: String? = null,
    val message: String? = null,
    val data: T? = null
) {
    val isOk: Boolean get() = code == 0 || code == 200
}

// ---------------------------------------------------------------------------
// 鉴权
// ---------------------------------------------------------------------------

@Serializable
data class LoginRequest(
    val captcha: String,
    val mobile: String
)

@Serializable
data class LoginData(
    val token: String = ""
)

// ---------------------------------------------------------------------------
// 扫码 / 设备
// ---------------------------------------------------------------------------

@Serializable
data class ScanRequest(
    val qrCode: String
)

@Serializable
data class ScanResultWrap(
    val result: ScanResult? = null
)

@Serializable
data class ScanResult(
    val orderId: Long = 0,
    val deviceId: String = "",
    val macAddress: String = "",
    val isSlotMachine: Boolean = false,
    /** 见 ModuleType：1=NORDIC 5=CYPRESS … */
    val moduleType: Int = -1,
    val status: Int = 0,
    val mobile: String = "",
    /** true = 当前空闲可下单 */
    val createOrderEnabled: Boolean = false,
    val reason: String? = null,
    val needSync: Boolean = false,
    val needSyncBLE: Boolean = false,
    val deviceTypeId: Int = 0
)

@Serializable
data class ProgramInfo(
    val customDeviceType: Int = 0,
    val deviceId: String = "",
    val deviceNo: String = "",
    val deviceTypeId: Int = 0,
    val deviceTypeName: String = "",
    val deviceWashModel: List<DeviceWashModel> = emptyList(),
    val isOpenDoubleExposure: Boolean = false,
    val isWashTemperatureEnable: Boolean = false,
    val macAddress: String = "",
    val moduleType: Int = -1,
    val serviceSubjectId: Long = 0,
    val serviceSubjectName: String = "",
    @Serializable(with = FlexibleBoolSerializer::class)
    val showMore: Boolean = false,
    val storeId: String = "",
    val storeMobile: String = "",
    val storeName: String = "",
    val type: Int = 0,
    val warmTip: String = ""
)

@Serializable
data class DeviceWashModel(
    val workModelId: Int = 0,
    val workModelName: String = "",
    /** 单位：分 */
    val basePrice: Int = 0,
    val promotionPrice: Int? = null,
    @Serializable(with = FlexibleBoolSerializer::class)
    val isPromotion: Boolean = false,
    /** 默认选中项 */
    @Serializable(with = FlexibleBoolSerializer::class)
    val isDefault: Boolean = false,
    @Serializable(with = FlexibleBoolSerializer::class)
    val hide: Boolean = false,
    /** 单位：分钟 */
    val time: Int = 0,
    val description: String = "",
    val tags: List<String> = emptyList()
) {
    /** 展示价（分）：服务端无促销时 promotionPrice 显式返回 0（不是 null），须判 >0 才采信。 */
    val displayPriceCents: Int get() = (promotionPrice ?: 0).takeIf { it > 0 } ?: basePrice
}

// ---------------------------------------------------------------------------
// 下单
// ---------------------------------------------------------------------------

@Serializable
data class CreateOrderRequest(
    val type: Int = 1,
    val deviceTypeId: Int,
    val deviceId: String,
    val deviceWashModelId: Int,
    val storeId: String,
    val wp_detergentGearId: Int? = null,
    val wp_disinfectantGearId: Int? = null,
    val washTemperatureId: Int? = null,
    val dryTime: Int? = null,
    @SerialName("additionParams") val additionParams: Map<String, String> = emptyMap()
)

/**
 * 订单数据。
 *
 * 注意：`orderId` / `status` 服务端有时返回数字、有时返回字符串。
 * `orderId` 声明为 String 是因为数字/字符串都能被 kotlinx 的 lenient 模式读入 String；
 * `status` 声明为 Int 则在收到 `"40"` 这类字符串时会失败，因此用 [FlexibleIntSerializer]。
 */
@Serializable
data class OrderData(
    val orderId: String = "",
    val deviceId: String = "",
    val macAddress: String = "",
    val orderNo: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val status: Int = 0,
    val statusRemark: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val deviceWashModelId: Int = 0,
    val deviceWashModel: DeviceWashModel? = null,
    val workTime: Int? = null,
    val remainTime: Int? = null,
    val cycle: String? = null,
    val createAt: String? = null,
    val userClickWashStartTime: String? = null,
    val washStartTime: String? = null,
    val payFlag: Int? = null,
    val payPrice: Double? = null,
    val deviceNo: String? = null,
    val storeName: String? = null,
    val storeMobile: String? = null
) {
    /** 订单是否处于「已启动但未结束」——此时不可重复启动。 */
    val running: Boolean get() = status in WasherStatus.IN_PROGRESS

    /**
     * 服务端给的剩余秒数，负数按 0 处理。
     *
     * UI 侧拿它做本地倒计时（每秒递减），避免只靠轮询导致计时几分钟才跳一次。
     */
    val remainSeconds: Int get() = (remainTime ?: 0).coerceAtLeast(0)

    /** 剩余时间文本，如「剩余 26 分钟」。 */
    val remainText: String
        get() {
            val sec = remainSeconds
            if (sec <= 0) return ""
            val min = sec / 60
            return if (min <= 0) "剩余不足 1 分钟" else "剩余约 $min 分钟"
        }

    /**
     * 订单真正的开始时刻（epoch 毫秒），0 表示服务端还没给。
     *
     * 服务端在「启动中」(status=21) 阶段 `remainTime` 还是 0，光靠它无从倒计时；
     * 用这个时刻 + [totalSeconds] 自己推，重开 App 也能接着走，不会每次从总时长从头开始。
     * 取值的优先级：`washStartTime`（实际开始洗）→ `userClickWashStartTime`（用户点启动）→ `createAt`（下单）。
     * 三者都是 UTC ISO-8601（形如 `2026-09-22T10:07:06Z`）。
     */
    val startMs: Long
        get() {
            val raw = (washStartTime ?: userClickWashStartTime ?: createAt)?.trim().orEmpty()
            if (raw.isBlank()) return 0
            return runCatching { java.time.Instant.parse(raw).toEpochMilli() }
                .recoverCatching {
                    java.time.LocalDateTime.parse(raw)
                        .toInstant(java.time.ZoneOffset.UTC)
                        .toEpochMilli()
                }
                .getOrDefault(0)
        }

    /** 该模式的整套时长（秒）。服务端 `workTime` 单位是分钟。 */
    val totalSeconds: Int get() = (workTime ?: 0) * 60
}

// ---------------------------------------------------------------------------
// BLE 专用
// ---------------------------------------------------------------------------

/** `POST /ble/data/report` 请求体。 */
@Serializable
data class BleDataReport(
    val macAddress: String,
    val reportData: List<String>
)

/** `POST /ble/command/acquire` 请求体。 */
@Serializable
data class BleCommandAcquireRequest(
    val macAddress: String,
    val command: String
)

/** acquire 响应：数组，每项含若干 hex 包。 */
@Serializable
data class BleCommandPackage(
    val packages: List<String> = emptyList()
)

/**
 * `POST /ble/data/report/nordic` 上报体。
 *
 * 对齐官方：
 * ```
 * {type, deviceId, command, commandType, originStr, deviceInfoStr,
 *  bleVersionStr, random, orderNoStr, orderId?}
 * ```
 */
@Serializable
data class NordicReport(
    val type: Int,
    val deviceId: String,
    val command: String = "",
    val commandType: String = "",
    val originStr: String = "",
    val deviceInfoStr: String = "",
    val bleVersionStr: String = "",
    val random: String = "",
    val orderNoStr: String = "",
    val orderId: String? = null
)

/**
 * Nordic 上报响应：云端回传的明文指令。
 *
 * 实测响应形态（探针实验变体 C/E，type=2 无 orderId 或 type=3）：
 * ```json
 * {"code":0,"data":{"deviceStatus":"1","errorCode":0,"orderStatus":"40","status":""}}
 * ```
 * 带 orderId + type=2 且订单已在运行时，服务端回 `{}` → 解析后全部字段为 null。
 */
@Serializable
data class NordicCommandResponse(
    val orderId: String? = null,
    val orderNo: String? = null,
    /** 明文指令，直接写目标特征 */
    val command: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val errorCode: Int = 0,
    /** 设备模块状态："1" = WORK。 */
    val deviceStatus: String? = null,
    /** 订单状态："40" = 运行中。 */
    val orderStatus: String? = null,
    val status: String? = null
)

// ---------------------------------------------------------------------------
// 枚举
// ---------------------------------------------------------------------------

object ModuleType {
    const val TYPE_2G = 0
    const val TYPE_BLE_NORDIC = 1
    const val TYPE_NB = 2
    const val TYPE_2G_BLE = 3
    const val TYPE_NB_BLE = 4
    const val TYPE_BLE_CYPRESS = 5
    const val TYPE_WIFI_MESH = 6
    const val TYPE_4G = 7

    /** 支持 App 直连蓝牙的模块类型。 */
    fun supportsBle(m: Int): Boolean = m in setOf(TYPE_BLE_NORDIC, TYPE_2G_BLE, TYPE_NB_BLE, TYPE_BLE_CYPRESS)

    fun bleTypeOf(m: Int): String? = when (m) {
        TYPE_BLE_NORDIC -> "bleNordic51822"
        TYPE_BLE_CYPRESS -> "bleCypress20736"
        else -> null
    }
}

object WasherDeviceType {
    const val PULSATOR = 1
    const val DRUM = 2
    const val DRYER = 3
    const val SHOE_WASHER = 4
    const val DRYER_LARGE = 6
    const val PULSATOR_OTT = 8
    const val DRUM_10KG = 9
    const val DRYER_9KG = 10
    const val PULSATOR_65KG = 11
    const val DRUM_10KG_NEW = 12
    const val DRYER_10KG = 13

    val DRYER_TYPES = setOf(DRYER, DRYER_LARGE, DRYER_9KG, DRYER_10KG)

    fun displayName(id: Int): String = when (id) {
        PULSATOR -> "波轮机"
        DRUM -> "滚筒机"
        DRYER -> "烘干机"
        SHOE_WASHER -> "洗鞋机"
        DRYER_LARGE -> "大型烘干机"
        PULSATOR_OTT -> "波轮机(OTT)"
        DRUM_10KG -> "10KG滚筒"
        DRYER_9KG -> "9KG烘干机"
        PULSATOR_65KG -> "6.5KG波轮"
        DRUM_10KG_NEW -> "10KG滚筒(新)"
        DRYER_10KG -> "10KG烘干机"
        else -> "设备#$id"
    }
}

object WasherStatus {
    const val RESERVED = 10
    const val PAYING = 17
    const val PAID = 20
    const val STARTING = 21
    const val SELF_CLEANING_STARTING = 22
    const val DETERGENT_DISPENSING = 24
    const val ORDER_PROTECTION = 29
    const val SELF_CLEANING = 30
    const val SELF_CLEANING_COMPLETED = 35
    const val RUNNING = 40
    const val COMPLETED = 50
    const val PAYMENT_TIMEOUT = 51
    const val START_FAILED = 52
    const val CANCELED = 53

    val BLE_STARTABLE = setOf(SELF_CLEANING, RUNNING)

    /**
     * 「已启动但没结束」的全部中间态。
     *
     * 启动后服务端可能先给 21（启动中）/ 22 / 24，过一会儿才落到 40（运行中）。
     * 这几个状态都要当成「运行中」处理，否则界面会又把「启动洗衣机」按钮亮出来。
     * 排除：29 订单保护（异常态，需人工介入）、50 完成、51 支付超时、52 启动失败、53 取消。
     */
    val IN_PROGRESS = setOf(
        STARTING,
        SELF_CLEANING_STARTING,
        DETERGENT_DISPENSING,
        SELF_CLEANING,
        RUNNING
    )

    fun displayName(s: Int): String = when (s) {
        RESERVED -> "已预约"
        PAYING -> "支付中"
        PAID -> "已支付"
        STARTING -> "启动中"
        SELF_CLEANING_STARTING -> "筒自洁启动中"
        DETERGENT_DISPENSING -> "投放洗涤剂"
        ORDER_PROTECTION -> "订单保护中"
        SELF_CLEANING -> "筒自洁中"
        SELF_CLEANING_COMPLETED -> "筒自洁完成"
        RUNNING -> "运行中"
        COMPLETED -> "已完成"
        PAYMENT_TIMEOUT -> "支付超时"
        START_FAILED -> "启动失败"
        CANCELED -> "已取消"
        else -> "状态#$s"
    }
}

/** 洗涤剂/消毒剂档位。 */
object Gear {
    const val DETERGENT_STANDARD = 1
    const val DETERGENT_HEAVY = 3
    const val DISINFECTANT_STANDARD = 4
    const val DISINFECTANT_HEAVY = 6
}

/** 水温档位。 */
object WashTemperature {
    const val NORMAL = 1
    const val C30 = 2
    const val C40 = 3
    const val C60 = 4
}

// ---------------------------------------------------------------------------
// 支付
// ---------------------------------------------------------------------------

/**
 * `GET /payment/methods` 的渠道项。
 *
 * 实测返回 60 余字段，这里只声明用得到的；其余由 `ignoreUnknownKeys` 丢弃。
 * 渠道 channel 取值：`alipay` / `wechatPay` / `unionPay` / `abcPay` / `cmbPayH5` /
 * `ccbPay` / `bocPay` / `mobilePay` / `ticketPay` / `alipayF2F` / `dyPay`。
 */
@Serializable
data class PayChannel(
    val channel: String = "",
    val name: String = "",
    @Serializable(with = FlexibleBoolSerializer::class) val isDefault: Boolean = false,
    @Serializable(with = FlexibleBoolSerializer::class) val isRecommended: Boolean = false,
    @Serializable(with = FlexibleBoolSerializer::class) val needUpgrade: Boolean = false,
    val upgradeNotes: String? = null,
    val upgradeVersion: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val branchType: Int = 0,
    /** 银联委托支付是否已签约。 */
    @Serializable(with = FlexibleBoolSerializer::class) val paymentSigned: Boolean = false,
    /** 免密协议是否已同意（支付宝侧同名语义）。 */
    @Serializable(with = FlexibleBoolSerializer::class) val isPaymentSignAgreen: Boolean = false,
    @Serializable(with = FlexibleBoolSerializer::class) val jumpToAliMini: Boolean = false,
    @Serializable(with = FlexibleBoolSerializer::class) val needOAuth: Boolean = false,
    val description: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val weight4Display: Int = 0
) {
    /** 该渠道是否已具备免密签约（true 时服务端可能直接返 skipPay）。 */
    val signCapable: Boolean get() = paymentSigned || isPaymentSignAgreen

    val displayName: String
        get() = name.ifBlank {
            when (channel) {
                "alipay", "alipayF2F" -> "支付宝"
                "wechatPay", "wechatPayMidea" -> "微信支付"
                "unionPay" -> "云闪付"
                "cmbPayH5" -> "招行一网通"
                else -> channel
            }
        }

    /** 只有官方 `alipay` 渠道返回 PayTask 可消费的 orderInfo。 */
    val alipaySdkCompatible: Boolean
        get() = channel.equals("alipay", ignoreCase = true)
}

@Serializable
data class PayMethodsData(
    val channels: List<PayChannel> = emptyList(),
    val unionPayDirectLogo: String? = null
)

/**
 * 归一化后的 `GET /payment/arguments` 响应。
 *
 * 原始响应先以 [JsonElement] 接收，再按官方收银台契约在
 * `payInfo.orderInfo`、`payInfo` 字符串或顶层 `orderInfo` 中取值。
 *
 * - [skipPay] = true → 服务端已免密扣款完成，客户端**不要**再唤起收银台；
 * - [jumpToQuery] = true → 跳支付状态页轮询。
 */
enum class PaymentPayloadKind {
    NONE,
    ALIPAY_SDK,
    URL,
    JSON_OBJECT,
    QR_OR_TEXT,
    INVALID_ALIPAY,
}

data class PaymentStringValidation(
    val kind: PaymentPayloadKind,
    val length: Int,
    val reason: String,
) {
    val valid: Boolean get() = kind == PaymentPayloadKind.ALIPAY_SDK
}

data class PayArgumentsData(
    /** 已验证的支付宝 SDK 字符串；无效内容不会放入该字段。 */
    val orderStr: String = "",
    val skipPay: Boolean = false,
    val jumpToQuery: Boolean = false,
    val payFlag: Int? = null,
    val isPaid: Boolean = false,
    val payloadKind: PaymentPayloadKind = PaymentPayloadKind.NONE,
    val payloadLength: Int = 0,
    val payloadReason: String = "",
    val sourcePath: String = "",
    /** 只用于诊断的字段路径，不包含字段值。 */
    val responseKeys: List<String> = emptyList(),
) {
    val alreadyPaid: Boolean get() = payFlag == 1 || isPaid
}

enum class PaymentOrderDisposition {
    PAYABLE,
    ALREADY_PAID,
    PROCESSING,
    REBUILDABLE,
    TERMINAL,
    UNKNOWN,
}

fun paymentOrderDisposition(order: OrderData?): PaymentOrderDisposition {
    if (order == null) return PaymentOrderDisposition.UNKNOWN
    if (order.payFlag == 1 || order.status in setOf(
            WasherStatus.PAID,
            WasherStatus.STARTING,
            WasherStatus.SELF_CLEANING_STARTING,
            WasherStatus.DETERGENT_DISPENSING,
            WasherStatus.ORDER_PROTECTION,
            WasherStatus.SELF_CLEANING,
            WasherStatus.SELF_CLEANING_COMPLETED,
            WasherStatus.RUNNING,
            WasherStatus.COMPLETED,
        )
    ) {
        return PaymentOrderDisposition.ALREADY_PAID
    }
    return when (order.status) {
        WasherStatus.RESERVED -> PaymentOrderDisposition.PAYABLE
        WasherStatus.PAYING -> PaymentOrderDisposition.PROCESSING
        WasherStatus.PAYMENT_TIMEOUT -> PaymentOrderDisposition.REBUILDABLE
        WasherStatus.START_FAILED,
        WasherStatus.CANCELED,
        -> PaymentOrderDisposition.TERMINAL
        else -> PaymentOrderDisposition.UNKNOWN
    }
}

sealed interface PaymentStartDecision {
    data object Allowed : PaymentStartDecision
    data object InFlight : PaymentStartDecision
    data class Cooling(val seconds: Int) : PaymentStartDecision
}

/**
 * 支付按钮互斥与 6020208 冷却状态机。
 *
 * 时钟可注入，便于在没有 Android 环境时测试重复点击和冷却行为。
 */
class PaymentRequestGate(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var inFlight = false
    private var blockedUntilMs = 0L

    @Synchronized
    fun tryStart(): PaymentStartDecision {
        if (inFlight) return PaymentStartDecision.InFlight
        val remaining = blockedUntilMs - clock()
        if (remaining > 0) {
            return PaymentStartDecision.Cooling(((remaining + 999) / 1_000).toInt())
        }
        inFlight = true
        return PaymentStartDecision.Allowed
    }

    @Synchronized
    fun finish() {
        inFlight = false
    }

    @Synchronized
    fun blockDuplicate(seconds: Int = 10) {
        blockedUntilMs = maxOf(blockedUntilMs, clock() + seconds * 1_000L)
    }

    @Synchronized
    fun clearCooldown() {
        blockedUntilMs = 0L
    }

    @Synchronized
    fun reset() {
        inFlight = false
        blockedUntilMs = 0L
    }
}

/**
 * 安全选择本 App 能消费的支付宝渠道。
 *
 * 只有官方 `alipay` 渠道会返回 PayTask 可消费的 `payInfo.orderInfo`。
 * `alipayF2F` 在官方 Android 客户端走收银台 H5，不是当前 App 已实现的 SDK 支付。
 */
fun preferredAlipayChannel(channels: List<PayChannel>): PayChannel? {
    return channels.firstOrNull {
        !it.needUpgrade && it.channel.equals("alipay", ignoreCase = true)
    }
}

/** 官方收银台只在已签约时发送 `chooseSign=1`；未签约时必须省略。 */
fun paymentChooseSign(channel: PayChannel): Int? =
    if (channel.signCapable) 1 else null

/** 支付参数明确缺失后，待支付或已超时的旧订单可重建一次。 */
fun canRebuildAfterMissingPayment(status: Int): Boolean =
    status == WasherStatus.RESERVED || status == WasherStatus.PAYMENT_TIMEOUT

/**
 * 按 U净官方收银台契约解析支付参数：
 *
 * - `data.payInfo.orderInfo`
 * - `data.payInfo` 为字符串
 * - `data.orderInfo`（官方 `payInfo || response` 回退）
 *
 * 不扫描无关字段。JSON 字符串只允许表示上述对象，不会把 URL、二维码或普通 JSON
 * 当作 SDK 支付串。
 */
fun parsePayArguments(element: JsonElement?): PayArgumentsData {
    if (element == null) return PayArgumentsData(payloadReason = "响应 data 为空")
    if (element is JsonPrimitive) {
        val raw = element.content
        val nestedObject = parseObjectJsonString(raw)
        if (nestedObject != null) return parsePayArgumentsObject(nestedObject)
        return directPaymentPayload(raw, "data")
    }
    val obj = element as? JsonObject
        ?: return PayArgumentsData(payloadReason = "响应 data 不是对象")
    return parsePayArgumentsObject(obj)
}

private fun normalizePaymentKey(key: String): String =
    key.lowercase().filter(Char::isLetterOrDigit)

private fun parsePayArgumentsObject(obj: JsonObject): PayArgumentsData {
    val payInfo = obj.valueForPaymentKey("payInfo")
    val topLevelOrderInfo = {
        obj.valueForPaymentKey("orderInfo")
            ?.asPaymentString()
            ?.let { "orderInfo" to it }
    }
    val candidate: Pair<String, String>? = when (payInfo) {
        is JsonObject -> payInfo
            .valueForPaymentKey("orderInfo")
            ?.asPaymentString()
            ?.let { "payInfo.orderInfo" to it }
        is JsonPrimitive -> {
            val nested = parseObjectJsonString(payInfo.content)
            if (nested != null) {
                nested.valueForPaymentKey("orderInfo")
                    ?.asPaymentString()
                    ?.let { "payInfo.orderInfo" to it }
            } else {
                val primitive = payInfo.content.takeIf { it.isNotBlank() && it != "null" }
                if (primitive != null) "payInfo" to primitive else topLevelOrderInfo()
            }
        }
        null -> topLevelOrderInfo()
        else -> null
    }
    val parsed = if (candidate == null) {
        PayArgumentsData(payloadReason = "未找到 payInfo.orderInfo 或 orderInfo")
    } else {
        directPaymentPayload(candidate.second, candidate.first)
    }
    return parsed.copy(
        skipPay = obj.flexibleBool("skipPay", "skip_pay"),
        jumpToQuery = obj.flexibleBool("jumpToQuery", "jump_to_query"),
        payFlag = obj.flexibleInt("payFlag", "pay_flag"),
        isPaid = obj.flexibleBool("isPaid", "paid"),
        responseKeys = paymentResponseKeys(obj),
    )
}

private fun directPaymentPayload(raw: String, sourcePath: String): PayArgumentsData {
    val validation = validateAlipaySdkOrderString(raw)
    return PayArgumentsData(
        orderStr = raw.takeIf { validation.valid }.orEmpty(),
        payloadKind = validation.kind,
        payloadLength = validation.length,
        payloadReason = validation.reason,
        sourcePath = sourcePath,
        responseKeys = listOf(sourcePath),
    )
}

internal fun validateAlipaySdkOrderString(raw: String): PaymentStringValidation {
    if (raw.isEmpty()) return PaymentStringValidation(PaymentPayloadKind.NONE, 0, "支付串为空")
    val text = raw.trim()
    if (text.isEmpty()) return PaymentStringValidation(PaymentPayloadKind.NONE, raw.length, "支付串仅含空白")
    if (text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true)
    ) {
        return PaymentStringValidation(PaymentPayloadKind.URL, raw.length, "返回的是网页地址")
    }
    if (text.startsWith("{")) {
        return PaymentStringValidation(PaymentPayloadKind.JSON_OBJECT, raw.length, "返回的是 JSON 对象")
    }
    if (text.startsWith("[")) {
        return PaymentStringValidation(PaymentPayloadKind.JSON_OBJECT, raw.length, "返回的是 JSON 数组")
    }
    if (text != raw || text.any { it == '\n' || it == '\r' || it == '\u0000' }) {
        return PaymentStringValidation(PaymentPayloadKind.QR_OR_TEXT, raw.length, "支付串含非法控制字符")
    }

    val pairs = text.split('&').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) return@mapNotNull null
        val key = runCatching {
            URLDecoder.decode(part.substring(0, index), Charsets.UTF_8.name())
        }.getOrElse { return@mapNotNull null }
        key to part.substring(index + 1)
    }
    val keyCounts = pairs.groupingBy { it.first }.eachCount()
    val required = setOf("app_id", "biz_content", "sign")
    val presentRequired = required.count { keyCounts[it] == 1 }
    if (presentRequired == 0) {
        return PaymentStringValidation(PaymentPayloadKind.QR_OR_TEXT, raw.length, "不是支付宝参数串")
    }
    if (presentRequired != required.size) {
        return PaymentStringValidation(
            PaymentPayloadKind.INVALID_ALIPAY,
            raw.length,
            "支付宝参数串缺少必要字段或字段重复",
        )
    }

    val values = pairs.toMap()
    val sign = values["sign"].orEmpty()
    if (values["app_id"].isNullOrBlank() ||
        values["biz_content"].isNullOrBlank() ||
        sign.length < 16 ||
        sign.equals("null", ignoreCase = true)
    ) {
        return PaymentStringValidation(
            PaymentPayloadKind.INVALID_ALIPAY,
            raw.length,
            "必要字段为空或签名为空",
        )
    }
    return PaymentStringValidation(PaymentPayloadKind.ALIPAY_SDK, raw.length, "支付宝 SDK 参数串")
}

private fun parseObjectJsonString(raw: String): JsonObject? {
    val text = raw.trim()
    if (!text.startsWith("{")) return null
    return runCatching { UjingJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
}

private fun JsonObject.valueForPaymentKey(key: String): JsonElement? {
    val normalized = normalizePaymentKey(key)
    return entries.firstOrNull { normalizePaymentKey(it.key) == normalized }?.value
}

private fun JsonElement.asPaymentString(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it != "null" }

private fun paymentResponseKeys(obj: JsonObject): List<String> {
    val keys = obj.keys.take(30).toMutableList()
    val payInfo = obj.valueForPaymentKey("payInfo") as? JsonObject
    payInfo?.keys?.take(20)?.forEach { keys += "payInfo.$it" }
    return keys.distinct()
}

private fun JsonObject.flexibleBool(vararg keys: String): Boolean {
    val normalized = keys.map(::normalizePaymentKey).toSet()
    val raw = entries.firstOrNull { normalizePaymentKey(it.key) in normalized }?.value
        ?: return false
    val text = (raw as? JsonPrimitive)?.content?.trim()?.lowercase() ?: return false
    return when (text) {
        "1", "true", "yes" -> true
        "0", "false", "no", "null", "" -> false
        else -> text.toDoubleOrNull()?.let { it != 0.0 } ?: false
    }
}

private fun JsonObject.flexibleInt(vararg keys: String): Int? {
    val normalized = keys.map(::normalizePaymentKey).toSet()
    val raw = entries.firstOrNull { normalizePaymentKey(it.key) in normalized }
        ?.value as? JsonPrimitive
        ?: return null
    val text = raw.content.trim()
    return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt()
}

/** `GET /app/payment/{id}/lastPayStatus` 响应。status == "3" 为支付成功。 */
@Serializable
data class PayStatusData(
    val status: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val payFlag: Int = 0
) {
    val success: Boolean get() = status == "3"
}

