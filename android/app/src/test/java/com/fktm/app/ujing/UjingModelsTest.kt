package com.fktm.app.ujing

import com.fktm.app.ujing.net.DeviceWashModel
import com.fktm.app.ujing.net.ModuleType
import com.fktm.app.ujing.net.OrderData
import com.fktm.app.ujing.net.PayChannel
import com.fktm.app.ujing.net.UjingJson
import com.fktm.app.ujing.net.UjingApi
import com.fktm.app.ujing.net.WasherStatus
import com.fktm.app.ujing.net.optionalNumericId
import com.fktm.app.ujing.net.parsePayArguments
import com.fktm.app.ujing.net.preferredAlipayChannel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UjingModelsTest {

    @Test
    fun recognizesSupportedBleModuleTypes() {
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_BLE_NORDIC))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_BLE_CYPRESS))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_2G_BLE))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_NB_BLE))
        assertFalse(ModuleType.supportsBle(ModuleType.TYPE_4G))
    }

    @Test
    fun runningOrderIncludesServerStartupStates() {
        listOf(
            WasherStatus.STARTING,
            WasherStatus.SELF_CLEANING_STARTING,
            WasherStatus.DETERGENT_DISPENSING,
            WasherStatus.SELF_CLEANING,
            WasherStatus.RUNNING,
        ).forEach { status ->
            assertTrue(OrderData(status = status).running)
        }
        assertFalse(OrderData(status = WasherStatus.COMPLETED).running)
    }

    @Test
    fun promotionPriceOnlyWinsWhenPositive() {
        assertEquals(
            250,
            DeviceWashModel(basePrice = 300, promotionPrice = 250).displayPriceCents,
        )
        assertEquals(
            300,
            DeviceWashModel(basePrice = 300, promotionPrice = 0).displayPriceCents,
        )
    }

    @Test
    fun acceptsOnlyKnownUjingQrHosts() {
        val api = UjingApi()
        assertTrue(api.isUjingQrCode("https://q.ujing.com.cn/ucqrc/index.html?cd=123456"))
        assertTrue(
            api.isUjingQrCode(
                "http://app.littleswan.com/u_download.html?type=Ujing&uuid=abc"
            )
        )
        assertFalse(api.isUjingQrCode("https://example.com/ucqrc/index.html?cd=123456"))
    }

    @Test
    fun parsesCanonicalPaymentArguments() {
        val data = UjingJson.parseToJsonElement(
            """{"payInfo":{"orderInfo":"app_id=202100&sign=abc"}}"""
        )
        val parsed = parsePayArguments(data)

        assertEquals("app_id=202100&sign=abc", parsed.orderStr)
        assertTrue(parsed.responseKeys.contains("payInfo.orderInfo"))
    }

    @Test
    fun parsesStringAndAliasPaymentArguments() {
        val asPayInfoString = parsePayArguments(
            UjingJson.parseToJsonElement("""{"payInfo":"app_id=1&sign=a"}""")
        )
        val asPayParams = parsePayArguments(
            UjingJson.parseToJsonElement("""{"payParams":{"orderStr":"app_id=2&sign=b"}}""")
        )
        val asTopLevel = parsePayArguments(
            UjingJson.parseToJsonElement("""{"orderInfo":"app_id=3&sign=c"}""")
        )
        val asEscapedJson = parsePayArguments(
            UjingJson.parseToJsonElement(
                """{"payInfo":"{\"orderInfo\":\"app_id=4&sign=d\"}"}"""
            )
        )

        assertEquals("app_id=1&sign=a", asPayInfoString.orderStr)
        assertEquals("app_id=2&sign=b", asPayParams.orderStr)
        assertEquals("app_id=3&sign=c", asTopLevel.orderStr)
        assertEquals("app_id=4&sign=d", asEscapedJson.orderStr)
    }

    @Test
    fun parsesNumericPaymentFlagsWithoutMistakingUnknownData() {
        val skipped = parsePayArguments(
            UjingJson.parseToJsonElement("""{"skipPay":1,"payFlag":"0"}""")
        )
        val paid = parsePayArguments(
            UjingJson.parseToJsonElement("""{"payFlag":"1"}""")
        )
        val unknown = parsePayArguments(
            UjingJson.parseToJsonElement("""{"newServerField":"not-a-payment-string"}""")
        )

        assertTrue(skipped.skipPay)
        assertTrue(paid.alreadyPaid)
        assertEquals("", unknown.orderStr)
        assertTrue(unknown.responseKeys.contains("newServerField"))
    }

    @Test
    fun prefersAlipayEvenWhenUnionPayIsServerDefault() {
        val selected = preferredAlipayChannel(
            listOf(
                PayChannel(channel = "unionPay", isDefault = true, weight4Display = 114),
                PayChannel(channel = "wechatPay", weight4Display = 20),
                PayChannel(channel = "alipay", weight4Display = 12),
            )
        )

        assertEquals("alipay", selected?.channel)
    }

    @Test
    fun omitsEmptyOrInvalidOptionalPaymentIds() {
        assertEquals(null, optionalNumericId(null))
        assertEquals(null, optionalNumericId(""))
        assertEquals(null, optionalNumericId("  "))
        assertEquals(null, optionalNumericId("red-packet"))
        assertEquals("123456", optionalNumericId(" 123456 "))
    }

    @Test
    fun recognizesDuplicatePaymentRequestError() {
        assertTrue(
            UjingApi.ApiException(6020208, "请勿重复请求").isDuplicatePaymentRequest
        )
        assertFalse(
            UjingApi.ApiException(500, "server error").isDuplicatePaymentRequest
        )
    }
}
