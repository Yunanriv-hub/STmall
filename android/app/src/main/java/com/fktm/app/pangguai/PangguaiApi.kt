package com.fktm.app.pangguai

import com.fktm.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class PangguaiUser(
    val phone: String,
    val balance: String,
    val hasVip: Boolean,
)

data class PangguaiRecentDevice(
    val goodsId: Int,
    val goodsName: String,
    val orgName: String,
    val workStatus: Int,
)

data class PangguaiDevice(
    val goodsId: Int,
    val name: String,
    val orgName: String,
    val brandName: String,
    val soldState: Int,
    val serviceTelephone: String,
)

data class PangguaiSku(
    val skuId: Int,
    val name: String,
    val feature: String,
    val price: String,
    val stockState: Int,
)

data class PangguaiOrder(
    val id: String,
    val orderNo: String,
    val status: Int,
    val createTime: String,
    val payPrice: String,
    val machineName: String,
    val shopName: String,
    val functionName: String,
    val minutes: Int,
)

class PangguaiApiException(
    val code: Int,
    override val message: String,
) : Exception(message)

internal fun pangguaiSignature(
    appSecret: String,
    channel: String,
    timestamp: String,
    token: String,
    version: String,
    path: String,
): String = MessageDigest.getInstance("SHA-256")
    .digest(
        "appSecret=$appSecret&channel=$channel&timestamp=$timestamp&token=$token&version=$version&$path"
            .toByteArray(Charsets.UTF_8)
    )
    .joinToString("") { "%02x".format(it) }

class PangguaiApi(
    private val appSecret: String = BuildConfig.PANGGUAI_APP_SECRET,
    private val version: String = BuildConfig.PANGGUAI_API_VERSION,
) {

    companion object {
        const val BASE_URL = "https://userapi.qiekj.com"
        const val CHANNEL = "android_app"

        fun extractNqt(raw: String?): String? {
            val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val query = runCatching { URI(value).rawQuery }.getOrNull() ?: return null
            return query.split('&').firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=', "")
                if (!key.equals("NQT", ignoreCase = true)) return@firstNotNullOfOrNull null
                val encoded = pair.substringAfter('=', "").takeIf(String::isNotBlank)
                    ?: return@firstNotNullOfOrNull null
                URLDecoder.decode(encoded, Charsets.UTF_8.name()).takeIf(String::isNotBlank)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendSmsCode(phone: String) {
        val response = post(
            path = "/common/sms/sendCode",
            token = "",
            fields = mapOf(
                "phone" to phone,
                "template" to "reg",
            ),
        )
        ensureSuccess(response)
    }

    suspend fun login(phone: String, verify: String): String {
        val response = post(
            path = "/user/reg",
            token = "",
            fields = mapOf(
                "phone" to phone,
                "verify" to verify,
                "channel" to CHANNEL,
            ),
        )
        return ensureSuccess(response)
            .optJSONObject("data")
            ?.text("token")
            ?.takeIf(String::isNotBlank)
            ?: throw PangguaiApiException(-1, "登录响应缺少 Token")
    }

    suspend fun userInfo(token: String): PangguaiUser {
        val data = ensureSuccess(
            post("/user/info", token, mapOf("token" to token)),
        ).optJSONObject("data")
            ?: throw PangguaiApiException(-1, "账户信息为空")
        return PangguaiUser(
            phone = data.text("phone"),
            balance = data.text("balance"),
            hasVip = data.optBoolean("hasVip"),
        )
    }

    suspend fun recentDevices(token: String): List<PangguaiRecentDevice> {
        val response = post(
            path = "/goods/latestUsed",
            token = token,
            fields = mapOf(
                "categoryCode" to "wash",
                "token" to token,
            ),
        )
        val array = ensureSuccess(response).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { item ->
                    add(
                        PangguaiRecentDevice(
                            goodsId = item.optInt("goodsId"),
                            goodsName = item.text("goodsName"),
                            orgName = item.text("orgName"),
                            workStatus = item.optInt("workStatus"),
                        )
                    )
                }
            }
        }.filter { it.goodsId > 0 }
    }

    suspend fun scanDevice(nqt: String, token: String): Int {
        val response = post(
            path = "/goods/scan/v2",
            token = token,
            fields = mapOf(
                "NQT" to nqt,
                "token" to token,
            ),
        )
        val rawId = ensureSuccess(response)
            .optJSONObject("data")
            ?.text("id")
            .orEmpty()
        return rawId.toIntOrNull()
            ?: throw PangguaiApiException(-1, "扫码结果不是有效设备")
    }

    suspend fun deviceDetail(goodsId: Int, token: String): PangguaiDevice {
        val data = ensureSuccess(
            post(
                path = "/goods/normal/details",
                token = token,
                fields = mapOf(
                    "goodsId" to goodsId.toString(),
                    "token" to token,
                ),
            ),
        ).optJSONObject("data")
            ?: throw PangguaiApiException(-1, "设备详情为空")
        return PangguaiDevice(
            goodsId = data.optInt("goodsId", goodsId),
            name = data.text("name"),
            orgName = data.text("orgName"),
            brandName = data.text("brandName"),
            soldState = data.optInt("soldState"),
            serviceTelephone = data.text("serviceTelephone"),
        )
    }

    suspend fun deviceSkus(goodsId: Int, token: String): List<PangguaiSku> {
        val response = post(
            path = "/goods/normal/skus",
            token = token,
            fields = mapOf(
                "goodsId" to goodsId.toString(),
                "token" to token,
            ),
        )
        val array = ensureSuccess(response).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { item ->
                    add(
                        PangguaiSku(
                            skuId = item.optInt("skuId"),
                            name = item.text("name"),
                            feature = item.text("feature"),
                            price = item.text("price"),
                            stockState = item.optInt("stockState"),
                        )
                    )
                }
            }
        }.filter { it.skuId > 0 }
    }

    suspend fun orders(token: String, limit: Int = 30): List<PangguaiOrder> {
        val response = post(
            path = "/order/list",
            token = token,
            fields = mapOf(
                "page" to "1",
                "pageSize" to limit.coerceIn(1, 50).toString(),
                "orderStatus" to "",
                "token" to token,
            ),
        )
        val items = ensureSuccess(response)
            .optJSONObject("data")
            ?.optJSONArray("items")
            ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let { item ->
                    add(
                        PangguaiOrder(
                            id = item.text("id"),
                            orderNo = item.text("orderNo"),
                            status = item.optInt("orderStatus"),
                            createTime = item.text("createTime"),
                            payPrice = item.text("payPrice"),
                            machineName = item.text("machineName"),
                            shopName = item.text("shopName"),
                            functionName = item.text("machineFunctionName"),
                            minutes = item.optInt("markMinutes"),
                        )
                    )
                }
            }
        }
    }

    private suspend fun post(
        path: String,
        token: String,
        fields: Map<String, String>,
    ): JSONObject = withContext(Dispatchers.IO) {
        require(appSecret.isNotBlank()) { "胖乖生活本机协议配置缺失" }
        val timestamp = System.currentTimeMillis().toString()
        val sign = pangguaiSignature(
            appSecret = appSecret,
            channel = CHANNEL,
            timestamp = timestamp,
            token = token,
            version = version,
            path = path,
        )
        val bodyBuilder = FormBody.Builder()
        fields.forEach(bodyBuilder::add)
        val request = Request.Builder()
            .url(BASE_URL + path)
            .header("Authorization", token)
            .header("Version", version)
            .header("channel", CHANNEL)
            .header("timestamp", timestamp)
            .header("sign", sign)
            .post(bodyBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PangguaiApiException(response.code, "服务返回 HTTP ${response.code}")
            }
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun ensureSuccess(response: JSONObject): JSONObject {
        val code = response.optInt("code", -1)
        if (code != 0) {
            throw PangguaiApiException(code, response.optString("msg").ifBlank { "操作失败" })
        }
        return response
    }

    private fun JSONObject.text(name: String): String =
        if (isNull(name)) "" else optString(name)

}
