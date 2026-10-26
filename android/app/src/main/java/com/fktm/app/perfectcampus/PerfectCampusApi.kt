package com.fktm.app.perfectcampus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class PerfectCampusRoomMeter(
    val used: String,
    val remaining: String,
    val businessType: String,
    val status: String,
)

data class PerfectCampusRoom(
    val name: String,
    val verify: String,
    val meters: List<PerfectCampusRoomMeter>,
)

data class PerfectCampusUsageDay(
    val date: String,
    val amount: String,
)

data class PerfectCampusRoomDevice(
    val name: String,
    val remaining: String,
    val totalBought: String,
    val totalUsed: String,
    val todayUsed: String,
    val weekUsage: List<PerfectCampusUsageDay>,
)

data class PerfectCampusRoomDetail(
    val roomName: String,
    val roomNumber: String,
    val devices: List<PerfectCampusRoomDevice>,
)

class PerfectCampusApiException(
    val code: Int,
    override val message: String,
) : Exception(message)

class PerfectCampusApi {

    private companion object {
        const val BASE_URL =
            "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36 Wanxiao/5.8.6"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun boundRooms(
        customerCode: String,
        account: String,
    ): List<PerfectCampusRoom> {
        val payload = JSONObject()
            .put("cmd", "getbindroom")
            .put("account", account)
        val body = request(
            account = account,
            customerCode = customerCode,
            method = "getbindroom",
            command = null,
            payload = payload,
        )
        return parsePerfectCampusRooms(body)
    }

    suspend fun roomDetail(
        customerCode: String,
        account: String,
        roomVerify: String,
    ): PerfectCampusRoomDetail {
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss000")
        )
        val payload = JSONObject()
            .put("cmd", "h5_getstuindexpage")
            .put("roomverify", roomVerify)
            .put("account", account)
            .put("timestamp", timestamp)
        val body = request(
            account = account,
            customerCode = customerCode,
            method = "h5_getstuindexpage",
            command = "JBSWaterElecService",
            payload = payload,
        )
        return parsePerfectCampusRoomDetail(body)
    }

    private suspend fun request(
        account: String,
        customerCode: String,
        method: String,
        command: String?,
        payload: JSONObject,
    ): JSONObject = withContext(Dispatchers.IO) {
        val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("param", payload.toString())
            .addQueryParameter("customercode", customerCode)
            .addQueryParameter("method", method)
        command?.let { urlBuilder.addQueryParameter("command", it) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", "https://xqh5.17wanxiao.com")
            .header("Referer", "https://xqh5.17wanxiao.com/userwaterelecmini/index.html")
            .header("X-Requested-With", "com.newcapec.mobile.ncp")
            .post(FormBody.Builder().build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PerfectCampusApiException(response.code, "服务返回 HTTP ${response.code}")
            }
            val envelope = JSONObject(response.body?.string().orEmpty())
            val code = envelope.optInt("code_", -1)
            if (code != 0 || !envelope.optBoolean("result_")) {
                throw PerfectCampusApiException(
                    code,
                    envelope.optString("message_").ifBlank { "查询失败" },
                )
            }
            val body = envelope.optString("body")
            if (body.isBlank()) {
                throw PerfectCampusApiException(-1, "服务没有返回房间数据")
            }
            val decoded = JSONObject(body)
            if (decoded.optString("result") != "0") {
                throw PerfectCampusApiException(
                    decoded.optInt("result", -1),
                    decoded.optString("message").ifBlank { "房间查询失败" },
                )
            }
            decoded
        }
    }

    private fun JSONObject.text(name: String): String =
        if (isNull(name)) "" else optString(name)
}

internal fun parsePerfectCampusRooms(body: JSONObject): List<PerfectCampusRoom> {
    val rooms = body.optJSONArray("roomlist") ?: JSONArray()
    return buildList {
        for (index in 0 until rooms.length()) {
            val room = rooms.optJSONObject(index) ?: continue
            val verify = room.textValue("roomverify")
            if (verify.isBlank()) continue
            val meters = room.optJSONArray("detaillist") ?: JSONArray()
            add(
                PerfectCampusRoom(
                    name = room.textValue("roomfullname").ifBlank { "已绑定房间" },
                    verify = verify,
                    meters = buildList {
                        for (meterIndex in 0 until meters.length()) {
                            val meter = meters.optJSONObject(meterIndex) ?: continue
                            add(
                                PerfectCampusRoomMeter(
                                    used = meter.textValue("use"),
                                    remaining = meter.textValue("odd"),
                                    businessType = meter.textValue("businesstype"),
                                    status = meter.textValue("status"),
                                )
                            )
                        }
                    },
                )
            )
        }
    }
}

internal fun parsePerfectCampusRoomDetail(body: JSONObject): PerfectCampusRoomDetail {
    val mods = body.optJSONArray("modlist") ?: JSONArray()
    return PerfectCampusRoomDetail(
        roomName = body.textValue("roomfullname").ifBlank { "房间水电" },
        roomNumber = body.textValue("roomnum"),
        devices = buildList {
            for (index in 0 until mods.length()) {
                val mod = mods.optJSONObject(index) ?: continue
                val week = mod.optJSONArray("weekuselist") ?: JSONArray()
                add(
                    PerfectCampusRoomDevice(
                        name = mod.textValue("devicename").ifBlank { "水电表" },
                        remaining = mod.textValue("odd"),
                        totalBought = mod.textValue("sumbuy"),
                        totalUsed = mod.textValue("sumsub"),
                        todayUsed = mod.textValue("todayuse"),
                        weekUsage = buildList {
                            for (dayIndex in 0 until week.length()) {
                                val day = week.optJSONObject(dayIndex) ?: continue
                                add(
                                    PerfectCampusUsageDay(
                                        date = day.textValue("weekday") + " " + day.textValue("date"),
                                        amount = day.textValue("dayuse"),
                                    )
                                )
                            }
                        },
                    )
                )
            }
        },
    )
}

private fun JSONObject.textValue(name: String): String =
    if (isNull(name)) "" else optString(name)
