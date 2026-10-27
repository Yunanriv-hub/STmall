package com.fktm.app.ujing.net

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 脱敏接口诊断。字段值不会进入该结构，字符串只保留长度。
 */
data class ApiResponseDiagnostics(
    val httpStatus: Int,
    val businessCode: Int?,
    val message: String?,
    val reason: String?,
    val fieldTypes: Map<String, String>,
    val bodyBytes: Int,
)

data class PaymentArgumentsResult(
    val envelope: Envelope<JsonElement>,
    val diagnostics: ApiResponseDiagnostics,
)

/**
 * U净云端 API 客户端（OkHttp 手写，不引 Retrofit）。
 *
 * 设计要点：
 *  - token 由外部（TokenStore）注入，401 统一抛 [ApiException]。
 *  - 响应兼容两种形态：带 `{code,data}` 包裹，或裸对象/数组（BLE 接口如此）。
 */
class UjingApi {

    companion object {
        private const val CAPTCHA_HMAC_KEY =
            "T3pAWrBqKzS2GC7LKQbIDN2xkWEYzTS/nrHdYfbTkHU="
    }

    var token: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * `payment/arguments` 会创建服务端支付会话，属于非幂等 GET。
     * OkHttp 默认可能自动重发 GET；这里禁用网络层重试，避免服务端返回 6020208。
     */
    private val nonRetryClient = client.newBuilder()
        .retryOnConnectionFailure(false)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    class ApiException(
        val code: Int,
        message: String,
        val httpStatus: Int? = null,
        val diagnostics: ApiResponseDiagnostics? = null,
    ) : IOException(message) {
        /** U净支付参数重复请求。 */
        val isDuplicatePaymentRequest: Boolean get() = code == 6020208
    }

    private data class HttpResponsePayload(
        val status: Int,
        val body: String,
    )

    // -----------------------------------------------------------------
    // 基础请求
    // -----------------------------------------------------------------

    private fun urlOf(path: String, query: Map<String, String?>): HttpUrl {
        val b = HttpUrl.Builder()
            .scheme("https")
            .host("phoenix.ujing.online")
            .addPathSegments(path.trimStart('/'))
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null
    ): HttpResponsePayload = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(urlOf(path, query))
        headers.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((bodyJson ?: "{}").toRequestBody(jsonMedia))
            "DELETE" -> builder.delete()
            else -> error("unsupported method $method")
        }
        val httpClient = if (path == "/api/v1/payment/arguments") nonRetryClient else client
        httpClient.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            HttpResponsePayload(resp.code, text)
        }
    }

    private suspend fun raw(
        method: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
    ): String {
        val payload = executeRequest(method, path, headers, query, bodyJson)
        val text = payload.body
        if (text.isBlank()) {
            if (payload.status in 200..299) return "{}"
            throw ApiException(payload.status, "HTTP ${payload.status}", payload.status)
        }
        val root = runCatching { UjingJson.parseToJsonElement(text) }.getOrNull()
        val obj = root as? JsonObject
        val code = obj?.get("code")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
        val message = obj?.firstTextValue("message", "msg")
        val reason = obj?.firstTextValue("reason")
        val diagnostics = responseDiagnostics(payload.status, root, code, message, reason, text)
        if (code != null && code != 0 && code != 200) {
            throw ApiException(code, message ?: "code=$code", payload.status, diagnostics)
        }
        if (payload.status !in 200..299) {
            throw ApiException(
                code ?: payload.status,
                message ?: "HTTP ${payload.status}",
                payload.status,
                diagnostics,
            )
        }
        // `{}` 是服务端的「静默拒绝」形态，由调用方按业务语义判断。
        if (root == null) {
            throw ApiException(payload.status, "响应不是 JSON", payload.status, diagnostics)
        }
        return text
    }

    /**
     * 解析响应：自动识别是否带 `data` 包裹。
     * 无包裹时把整段 JSON 当作 data（BLE 接口的裸数组/裸对象）。
     *
     * 特殊情况：`{}`（空对象、无 `code` 也无 `data`）是服务端的静默拒绝形态，
     * 直接返回 `Envelope(200, null)`，不尝试反序列化成 T——否则会因字段缺失
     * 而落进默认值分支，让"拒绝"看起来像"成功但内容为空"。
     */
    private fun <T> decode(flexible: String, d: KSerializer<T>): Envelope<T> {
        val root: JsonElement = UjingJson.parseToJsonElement(flexible)
        val obj = root as? JsonObject
        if (obj != null && obj.isEmpty()) {
            return Envelope(code = 200, data = null)
        }
        return if (obj != null && obj.containsKey("data")) {
            UjingJson.decodeFromJsonElement(Envelope.serializer(d), root)
        } else {
            Envelope(code = 200, data = UjingJson.decodeFromJsonElement(d, root))
        }
    }

    private fun responseDiagnostics(
        httpStatus: Int,
        root: JsonElement?,
        businessCode: Int?,
        message: String?,
        reason: String?,
        body: String,
    ): ApiResponseDiagnostics {
        val fieldTypes = linkedMapOf<String, String>()
        root?.let { collectFieldTypes(it, "$", fieldTypes, 0) }
        return ApiResponseDiagnostics(
            httpStatus = httpStatus,
            businessCode = businessCode,
            message = message?.take(160),
            reason = reason?.take(80),
            fieldTypes = fieldTypes,
            bodyBytes = body.toByteArray(Charsets.UTF_8).size,
        )
    }

    private fun JsonObject.firstTextValue(vararg names: String): String? {
        val expected = names.map { it.lowercase() }.toSet()
        return entries.firstOrNull { it.key.lowercase() in expected }
            ?.value
            ?.let { it as? JsonPrimitive }
            ?.content
    }

    private fun collectFieldTypes(
        element: JsonElement,
        path: String,
        output: MutableMap<String, String>,
        depth: Int,
    ) {
        if (depth > 6 || output.size >= 80) return
        when (element) {
            is JsonObject -> {
                output[path] = "object"
                element.entries.take(30).forEach { (key, value) ->
                    collectFieldTypes(value, "$path.$key", output, depth + 1)
                }
            }
            is JsonArray -> {
                output[path] = "array(${element.size})"
                element.take(3).forEachIndexed { index, value ->
                    collectFieldTypes(value, "$path[$index]", output, depth + 1)
                }
            }
            is JsonPrimitive -> {
                val type = when {
                    element.content == "null" -> "null"
                    element.isString -> "string(len=${element.content.length})"
                    element.content == "true" || element.content == "false" -> "boolean"
                    element.content.toDoubleOrNull() != null -> "number"
                    else -> "primitive"
                }
                output[path] = type
            }
        }
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        d: KSerializer<T>
    ): Envelope<T> = decode(raw(method, path, headers, query, bodyJson), d)

    private fun biz(): Map<String, String> =
        Headers.bi(token ?: throw ApiException(401, "未登录：token 为空"))

    private fun androidPaymentBiz(): Map<String, String> =
        Headers.androidPayment(token ?: throw ApiException(401, "未登录：token 为空"))

    // -----------------------------------------------------------------
    // 鉴权
    // -----------------------------------------------------------------

    /** 发短信验证码。 */
    suspend fun sendCaptcha(mobile: String): Envelope<JsonElement> {
        val nonce = ByteArray(16).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
        val timestamp = System.currentTimeMillis() / 1000
        val signature = hmacSha256Base64(CAPTCHA_HMAC_KEY, nonce + timestamp)
        return runCatching {
            call(
                method = "GET",
                path = "/api/v1/wechat/captcha/create",
                headers = mapOf(
                    "x-app-code" to "BO",
                    "x-app-version" to "1.1.0",
                    "User-Agent" to "U jing/2.4.3 (iPhone; iOS 17.3; Scale/3.00)",
                    "x-mobile-brand" to "apple",
                    "x-mobile-model" to "iPhone14,5",
                    "x-user-geo" to "-180.000000,-180.000000",
                ),
                query = mapOf(
                    "mobile" to mobile,
                    "type" to "1",
                    "nonce" to nonce,
                    "timestamp" to timestamp.toString(),
                    "signature" to signature,
                ),
                d = JsonElement.serializer(),
            )
        }.getOrElse {
            call(
                method = "GET",
                path = "/api/v1/captcha",
                headers = Headers.ziCode(),
                query = mapOf(
                    "mobile" to mobile,
                    "sessionId" to "AFS_SWITCH_OFF",
                    "sig" to "AFS_SWITCH_OFF",
                    "token" to "AFS_SWITCH_OFF",
                    "type" to "1",
                ),
                d = JsonElement.serializer(),
            )
        }
    }

    /** 登录并写入 [token]。 */
    suspend fun login(mobile: String, captcha: String): Envelope<LoginData> {
        val body = UjingJson.encodeToString(LoginRequest.serializer(), LoginRequest(captcha, mobile))
        val env = call("POST", "/api/v1/login", Headers.ziLogin(), bodyJson = body, d = LoginData.serializer())
        env.data?.token?.takeIf { it.isNotBlank() }?.let { token = it }
        return env
    }

    // -----------------------------------------------------------------
    // 设备 / 门店
    // -----------------------------------------------------------------

    /** 扫洗衣机二维码换取设备信息。 */
    suspend fun scanWasherCode(qrCode: String): Envelope<ScanResultWrap> {
        val body = UjingJson.encodeToString(ScanRequest.serializer(), ScanRequest(qrCode))
        return call("POST", "/api/v1/devices/scanWasherCode", biz(), bodyJson = body, d = ScanResultWrap.serializer())
    }

    /** 设备详情（含洗衣模式列表）。 */
    suspend fun programInfo(deviceId: String): Envelope<ProgramInfo> = call(
        "GET", "/api/v1/app/washer/devices/program/info", biz(),
        query = mapOf("deviceId" to deviceId),
        d = ProgramInfo.serializer()
    )

    /** 按下单门店 + 机型拉模式（无 deviceId 时）。 */
    suspend fun programInfoByType(storeId: String, deviceTypeId: Int): Envelope<ProgramInfo> = call(
        "GET", "/api/v1/app/washer/devices/program/info", biz(),
        query = mapOf("storeId" to storeId, "deviceTypeId" to deviceTypeId.toString()),
        d = ProgramInfo.serializer()
    )

    // -----------------------------------------------------------------
    // 订单
    // -----------------------------------------------------------------

    suspend fun createOrder(req: CreateOrderRequest): Envelope<OrderData> {
        val body = UjingJson.encodeToString(CreateOrderRequest.serializer(), req)
        return call("POST", "/api/v1/orders/create", biz(), bodyJson = body, d = OrderData.serializer())
    }

    suspend fun orderDetail(orderId: String, additional: String = "price"): Envelope<OrderData> = call(
        "GET", "/api/v1/orders/$orderId/detail", biz(),
        query = mapOf("additional" to additional),
        d = OrderData.serializer()
    )

    /** 当前账号进行中的订单；服务端可能直接返回数组或包在 orders 字段内。 */
    suspend fun runningOrders(): List<OrderData> {
        val root = UjingJson.parseToJsonElement(
            raw("GET", "/api/v1/orders/running", biz())
        )
        val data = (root as? JsonObject)?.get("data") ?: root
        val array = when (data) {
            is JsonArray -> data
            is JsonObject -> data["orders"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return UjingJson.decodeFromJsonElement(ListSerializer(OrderData.serializer()), array)
    }

    suspend fun cancelOrder(orderId: String): Envelope<JsonElement> {
        val body = """{"orderId":"$orderId"}"""
        return call("POST", "/api/v1/orders/$orderId/cancel", biz(), bodyJson = body, d = JsonElement.serializer())
    }

    // -----------------------------------------------------------------
    // BLE
    // -----------------------------------------------------------------

    /** Cypress：上报设备回传的随机数/数据包。 */
    suspend fun bleDataReport(macAddress: String, reportData: List<String>): Envelope<JsonElement> {
        val body = UjingJson.encodeToString(
            BleDataReport.serializer(),
            BleDataReport(macAddress, reportData.map { it.uppercase() })
        )
        return call("POST", "/api/v1/ble/data/report", biz(), bodyJson = body, d = JsonElement.serializer())
    }

    /** Cypress：取云端签好名的指令包。 */
    suspend fun bleCommandAcquire(macAddress: String, command: String): List<BleCommandPackage> {
        val body = UjingJson.encodeToString(
            BleCommandAcquireRequest.serializer(),
            BleCommandAcquireRequest(macAddress, command)
        )
        val env = call(
            "POST", "/api/v1/ble/command/acquire", biz(),
            bodyJson = body,
            d = ListSerializer(BleCommandPackage.serializer())
        )
        return env.data.orEmpty()
    }

    /**
     * Nordic：一次性上报读取状态并取回明文指令。
     *
     * 响应兼容带包裹与裸对象两种形态。
     */
    suspend fun nordicReport(report: NordicReport): NordicCommandResponse {
        val body = UjingJson.encodeToString(NordicReport.serializer(), report)
        val env = call(
            "POST", "/api/v1/ble/data/report/nordic", biz(),
            bodyJson = body,
            d = NordicCommandResponse.serializer()
        )
        // `{}` 形态（静默拒绝）会让 env.data 为 null。此时给一个 errorCode=0 的空响应，
        // 由 NordicWasher 按 reportType 判断"该回指令却没回指令"，而不是在这里凭空报错。
        return env.data ?: NordicCommandResponse(errorCode = 0)
    }

    /** 暴露给调试页：原始 JSON 文本。 */
    suspend fun norticReportRaw(report: NordicReport): String {
        val body = UjingJson.encodeToString(NordicReport.serializer(), report)
        return raw("POST", "/api/v1/ble/data/report/nordic", biz(), bodyJson = body)
    }

    // -----------------------------------------------------------------
    // 支付
    // -----------------------------------------------------------------

    /** 可用支付渠道（收银台列表）。[include] 为本机已安装支付 APP 的 channel 逗号串。 */
    suspend fun payMethods(
        orderId: String,
        include: String? = null,
        serviceSubjectId: String? = null,
        deviceId: String? = null,
        creativeNumber: String? = null
    ): Envelope<PayMethodsData> = call(
        "GET", "/api/v1/payment/methods", androidPaymentBiz(),
        query = mapOf(
            "orderId" to orderId,
            "include" to include,
            "serviceSubjectId" to serviceSubjectId,
            "deviceId" to deviceId,
            "creativeNumber" to creativeNumber
        ),
        d = PayMethodsData.serializer()
    )

    /**
     * 取支付参数。
     *
     * 注意是 **GET** 而非 POST：`cashierNew.js` 的 `getPayArgumentsReq` 只在
     * `rechargeTypes = ["shower","water"]` 时用 POST，washer 走 GET。
     *
     * 实测（用已完成订单）：已支付订单返回 `100001 订单已支付或已过期，无法支付`，
     * 对已完成订单**无副作用**。
     */
    suspend fun payArguments(
        channel: String,
        orderId: String,
        payVersion: Int = 1,
        chooseSign: Int? = null,
        branchType: Int? = null,
        jumpToAliMini: Boolean? = null,
        alipayF2FNoAds: Boolean = false,
        couponId: String = "",
        redPacketId: String? = null,
        isUseRedPacket: Boolean = false,
    ): PaymentArgumentsResult {
        val payload = executeRequest(
            method = "GET",
            path = "/api/v1/payment/arguments",
            headers = androidPaymentBiz(),
            query = mapOf(
            "channel" to channel,
            "orderId" to orderId,
            "payVersion" to payVersion.toString(),
            "chooseSign" to chooseSign?.toString(),
            "branchType" to branchType?.toString(),
            "jumpToAliMini" to jumpToAliMini?.toString(),
            "alipayF2FNoAds" to alipayF2FNoAds.toString(),
            // 这两个字段在服务端是可选整数。未使用优惠时发送空字符串会被
            // strconv.ParseInt 解析为 500；必须让 urlOf 完全省略该参数。
            // 官方无优惠券时仍发送空 couponId；只有 redPacketId 是严格整数。
            "couponId" to couponId,
            "redPacketId" to optionalNumericId(redPacketId),
            "isUseRedPacket" to isUseRedPacket.toString(),
            ),
        )
        val root = runCatching { UjingJson.parseToJsonElement(payload.body) }.getOrNull()
        val obj = root as? JsonObject
        val code = obj?.get("code")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
        val message = obj?.firstTextValue("message", "msg")
        val reason = obj?.firstTextValue("reason")
        val diagnostics = responseDiagnostics(
            payload.status,
            root,
            code,
            message,
            reason,
            payload.body,
        )
        if (payload.body.isBlank()) {
            throw ApiException(
                code ?: payload.status,
                message ?: "支付参数响应为空",
                payload.status,
                diagnostics,
            )
        }
        if (root == null) {
            throw ApiException(payload.status, "支付参数响应不是 JSON", payload.status, diagnostics)
        }
        if (code != null && code != 0 && code != 200) {
            throw ApiException(code, message ?: "code=$code", payload.status, diagnostics)
        }
        if (payload.status !in 200..299) {
            throw ApiException(
                code ?: payload.status,
                message ?: "HTTP ${payload.status}",
                payload.status,
                diagnostics,
            )
        }
        val data = when {
            obj == null -> root
            obj.containsKey("data") -> obj["data"]?.takeUnless {
                it is JsonPrimitive && it.content == "null"
            }
            else -> root
        }
        return PaymentArgumentsResult(
            envelope = Envelope(
                code = code ?: 200,
                reason = reason,
                message = message,
                data = data,
            ),
            diagnostics = diagnostics,
        )
    }

    /** 支付成功后向服务端回执。 */
    suspend fun payStatusCommon(orderId: String, channel: String): Envelope<JsonElement> = call(
        "GET", "/api/v1/payment/$orderId/payStatus", androidPaymentBiz(),
        query = mapOf("channel" to channel),
        d = JsonElement.serializer()
    )

    /** 轮询支付结果（`status == "3"` 即成功）。 */
    suspend fun lastPayStatus(orderId: String): Envelope<PayStatusData> = call(
        "GET", "/api/v1/app/payment/$orderId/lastPayStatus", androidPaymentBiz(),
        d = PayStatusData.serializer()
    )

    fun isUjingQrCode(value: String): Boolean {
        val text = value.trim()
        return Regex(
            """^https?://q\.ujing\.com\.cn/ucqrc/index\.html\?cd=\d+""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(text) || Regex(
            """^https?://app\.littleswan\.com/u_download\.html\?""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(text)
    }

    private fun hmacSha256Base64(key: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
    }
}

/**
 * U净优惠字段是可选整数。空串或非数字都不能进入 query：
 * 服务端会直接对字段做 strconv.ParseInt，空串会返回 HTTP 500。
 */
internal fun optionalNumericId(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && it.toLongOrNull() != null }

