package com.fktm.app.ujing

import com.fktm.app.ujing.net.ApiResponseDiagnostics
import com.fktm.app.ujing.net.DeviceWashModel
import com.fktm.app.ujing.net.Headers
import com.fktm.app.ujing.net.ModuleType
import com.fktm.app.ujing.net.OrderData
import com.fktm.app.ujing.net.PayChannel
import com.fktm.app.ujing.net.PaymentOrderDisposition
import com.fktm.app.ujing.net.PaymentPayloadKind
import com.fktm.app.ujing.net.PaymentRequestGate
import com.fktm.app.ujing.net.PaymentStartDecision
import com.fktm.app.ujing.net.UjingJson
import com.fktm.app.ujing.net.UjingApi
import com.fktm.app.ujing.net.WasherStatus
import com.fktm.app.ujing.net.canRebuildAfterMissingPayment
import com.fktm.app.ujing.net.isTrustedAlipayDeepLink
import com.fktm.app.ujing.net.optionalNumericId
import com.fktm.app.ujing.net.paymentChooseSign
import com.fktm.app.ujing.net.paymentOrderDisposition
import com.fktm.app.ujing.net.parsePayArguments
import com.fktm.app.ujing.net.preferredAlipayChannel
import com.fktm.app.ujing.net.validateAlipaySdkOrderString
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
            """{"payInfo":{"orderInfo":"${validOrderInfo()}"},"payFlag":0}"""
        )
        val parsed = parsePayArguments(data)

        assertEquals(validOrderInfo(), parsed.orderStr)
        assertEquals(PaymentPayloadKind.ALIPAY_SDK, parsed.payloadKind)
        assertEquals(validOrderInfo().length, parsed.payloadLength)
        assertEquals("payInfo.orderInfo", parsed.sourcePath)
        assertTrue(parsed.responseKeys.contains("payInfo.orderInfo"))
    }

    @Test
    fun parsesOfficialAlipayF2FDeepLinkFromTopLevelUrl() {
        val link = "alipays://platformapi/startapp?appId=2021000000000000&url=encoded"
        val parsed = parsePayArguments(
            UjingJson.parseToJsonElement("""{"url":"$link"}""")
        )

        assertEquals(link, parsed.rawPaymentValue)
        assertEquals(PaymentPayloadKind.APP_SCHEME, parsed.payloadKind)
        assertEquals("url", parsed.sourcePath)
        assertEquals("alipays://platformapi/startapp", parsed.safeValueHint)
        assertTrue(isTrustedAlipayDeepLink(link))
    }

    @Test
    fun rejectsUntrustedAlipayLikeSchemes() {
        assertTrue(isTrustedAlipayDeepLink("alipays://platformapi/startapp?x=1"))
        assertTrue(isTrustedAlipayDeepLink("alipay://platformapi/startApp?x=1"))
        assertTrue(isTrustedAlipayDeepLink("alipayqr://platformapi/startapp?saId=10000007"))
        assertFalse(isTrustedAlipayDeepLink("alipays://evil.example/startapp?x=1"))
        assertFalse(isTrustedAlipayDeepLink("alipays://platformapi/other?x=1"))
        assertFalse(isTrustedAlipayDeepLink("other://platformapi/startapp?x=1"))
        assertFalse(isTrustedAlipayDeepLink("https://platformapi/startapp?x=1"))
    }

    @Test
    fun acceptsOnlyContractPaymentContainersAndJsonStrings() {
        val asPayInfoString = parsePayArguments(
            UjingJson.parseToJsonElement("""{"payInfo":"${validOrderInfo()}"}""")
        )
        val asTopLevel = parsePayArguments(
            UjingJson.parseToJsonElement("""{"orderInfo":"${validOrderInfo()}"}""")
        )
        val asEscapedJson = parsePayArguments(
            UjingJson.parseToJsonElement(
                """{"payInfo":"{\"orderInfo\":\"${validOrderInfo()}\"}"}"""
            )
        )
        val unrelatedContainer = parsePayArguments(
            UjingJson.parseToJsonElement("""{"payParams":{"orderStr":"${validOrderInfo()}"}}""")
        )
        val emptyPayInfoFallback = parsePayArguments(
            UjingJson.parseToJsonElement(
                """{"payInfo":"","orderInfo":"${validOrderInfo()}"}"""
            )
        )

        assertEquals(validOrderInfo(), asPayInfoString.orderStr)
        assertEquals(validOrderInfo(), asTopLevel.orderStr)
        assertEquals(validOrderInfo(), asEscapedJson.orderStr)
        assertEquals(validOrderInfo(), emptyPayInfoFallback.orderStr)
        assertEquals("", unrelatedContainer.orderStr)
        assertEquals(PaymentPayloadKind.NONE, unrelatedContainer.payloadKind)
    }

    @Test
    fun doesNotScanUnknownWrappersOrArraysForPaymentData() {
        val parsed = parsePayArguments(
            UjingJson.parseToJsonElement(
                """{"newWrapper":[{"serverPayload":"${validOrderInfo()}"}]}"""
            )
        )

        assertEquals("", parsed.orderStr)
        assertEquals(PaymentPayloadKind.NONE, parsed.payloadKind)
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
            UjingJson.parseToJsonElement(
                """{"newServerField":"not-a-payment-string","orderNo":"123456"}"""
            )
        )

        assertTrue(skipped.skipPay)
        assertTrue(paid.alreadyPaid)
        assertEquals("", unknown.orderStr)
        assertTrue(unknown.responseKeys.contains("newServerField"))
        assertTrue(unknown.responseKeys.contains("orderNo"))
    }

    @Test
    fun readsControlFlagsOnlyFromResponseDataTopLevel() {
        val parsed = parsePayArguments(
            UjingJson.parseToJsonElement(
                """{"payInfo":{"skipPay":1,"jumpToQuery":1,"orderInfo":"${validOrderInfo()}"}}"""
            )
        )

        assertFalse(parsed.skipPay)
        assertFalse(parsed.jumpToQuery)
        assertEquals(validOrderInfo(), parsed.orderStr)
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
    fun usesAlipayF2FOnlyAsDeepLinkFallback() {
        val selected = preferredAlipayChannel(
            listOf(
                PayChannel(channel = "alipayF2F", isDefault = true),
                PayChannel(channel = "wechatPay"),
            )
        )

        assertEquals("alipayF2F", selected?.channel)
        assertFalse(selected?.alipaySdkCompatible == true)
        assertTrue(selected?.alipayDeepLinkCompatible == true)
    }

    @Test
    fun omitsEmptyOrInvalidOptionalPaymentIds() {
        assertEquals(null, optionalNumericId(null))
        assertEquals(null, optionalNumericId(""))
        assertEquals(null, optionalNumericId("  "))
        assertEquals(null, optionalNumericId("red-packet"))
        assertEquals("0", optionalNumericId("0"))
        assertEquals("123456", optionalNumericId(" 123456 "))
    }

    @Test
    fun recognizesDuplicatePaymentRequestError() {
        val diagnostics = ApiResponseDiagnostics(
            httpStatus = 500,
            businessCode = 6020208,
            message = "请勿重复请求",
            reason = null,
            fieldTypes = mapOf("$.data.payInfo" to "object"),
            bodyBytes = 80,
        )
        val error = UjingApi.ApiException(
            6020208,
            "请勿重复请求",
            httpStatus = 500,
            diagnostics = diagnostics,
        )

        assertTrue(error.isDuplicatePaymentRequest)
        assertEquals(500, error.httpStatus)
        assertEquals(6020208, error.diagnostics?.businessCode)
        assertEquals("object", error.diagnostics?.fieldTypes?.get("$.data.payInfo"))
        assertFalse(
            UjingApi.ApiException(500, "server error").isDuplicatePaymentRequest
        )
    }

    @Test
    fun omitsChooseSignUntilPaymentIsSigned() {
        assertEquals(null, paymentChooseSign(PayChannel(channel = "alipay")))
        assertEquals(
            1,
            paymentChooseSign(
                PayChannel(channel = "alipay", isPaymentSignAgreen = true)
            )
        )
    }

    @Test
    fun usesCurrentAndroidHeadersForPaymentRequests() {
        val headers = Headers.androidPayment("token")

        assertEquals("BA", headers["x-app-code"])
        assertEquals("2.4.18", headers["x-app-version"])
        assertEquals("1.1.79", headers["weex-version"])
        assertEquals("Bearer token", headers["Authorization"])
    }

    @Test
    fun rejectsUrlsJsonQrAndFakeSignaturesAsSdkPayloads() {
        assertEquals(
            PaymentPayloadKind.URL,
            validateAlipaySdkOrderString("https://example.com/pay").kind,
        )
        assertEquals(
            PaymentPayloadKind.JSON_OBJECT,
            validateAlipaySdkOrderString(
                """{"app_id":"1","biz_content":"x","sign":"abcdefghijklmnop"}"""
            ).kind,
        )
        assertEquals(
            PaymentPayloadKind.QR_OR_TEXT,
            validateAlipaySdkOrderString("ALIPAY_QR:123456").kind,
        )
        assertEquals(
            PaymentPayloadKind.INVALID_ALIPAY,
            validateAlipaySdkOrderString(
                "app_id=1&biz_content=x&sign_type=RSA2"
            ).kind,
        )
        assertEquals(
            PaymentPayloadKind.INVALID_ALIPAY,
            validateAlipaySdkOrderString(
                "app_id=1&biz_content=x&sign="
            ).kind,
        )
    }

    @Test
    fun preservesValidSignedOrderStringExactly() {
        val raw = validOrderInfo()
        val validation = validateAlipaySdkOrderString(raw)

        assertTrue(validation.valid)
        assertEquals(PaymentPayloadKind.ALIPAY_SDK, validation.kind)
        assertEquals(raw.length, validation.length)
        assertEquals(raw, parsePayArguments(UjingJson.parseToJsonElement("\"$raw\"")).orderStr)
    }

    @Test
    fun distinguishesEmptyPaidProcessingAndTerminalOrders() {
        val empty = parsePayArguments(null)
        assertEquals(PaymentPayloadKind.NONE, empty.payloadKind)
        assertTrue(empty.payloadReason.contains("为空"))

        assertEquals(
            PaymentOrderDisposition.ALREADY_PAID,
            paymentOrderDisposition(OrderData(status = WasherStatus.PAID)),
        )
        assertEquals(
            PaymentOrderDisposition.PROCESSING,
            paymentOrderDisposition(OrderData(status = WasherStatus.PAYING)),
        )
        assertEquals(
            PaymentOrderDisposition.PAYABLE,
            paymentOrderDisposition(OrderData(status = WasherStatus.RESERVED)),
        )
        assertEquals(
            PaymentOrderDisposition.REBUILDABLE,
            paymentOrderDisposition(OrderData(status = WasherStatus.PAYMENT_TIMEOUT)),
        )
        assertEquals(
            PaymentOrderDisposition.TERMINAL,
            paymentOrderDisposition(OrderData(status = WasherStatus.CANCELED)),
        )
        assertEquals(
            PaymentOrderDisposition.UNKNOWN,
            paymentOrderDisposition(null),
        )
    }

    @Test
    fun allowsRebuildOnlyAfterMissingArgumentsForReservedOrExpiredOrder() {
        assertTrue(canRebuildAfterMissingPayment(WasherStatus.RESERVED))
        assertTrue(canRebuildAfterMissingPayment(WasherStatus.PAYMENT_TIMEOUT))
        assertFalse(canRebuildAfterMissingPayment(WasherStatus.PAYING))
        assertFalse(canRebuildAfterMissingPayment(WasherStatus.PAID))
        assertFalse(canRebuildAfterMissingPayment(WasherStatus.RUNNING))
    }

    @Test
    fun paymentGateBlocksRepeatedClicksAndEnforcesDuplicateCooldown() {
        var now = 1_000L
        val gate = PaymentRequestGate { now }

        assertEquals(PaymentStartDecision.Allowed, gate.tryStart())
        assertEquals(PaymentStartDecision.InFlight, gate.tryStart())
        gate.finish()
        gate.blockDuplicate(10)
        assertEquals(PaymentStartDecision.Cooling(10), gate.tryStart())

        now += 10_000L
        assertEquals(PaymentStartDecision.Allowed, gate.tryStart())
        gate.finish()
    }

    private fun validOrderInfo(): String =
        "app_id=2021000000000000" +
            "&biz_content=%7B%22out_trade_no%22%3A%22123%22%7D" +
            "&charset=utf-8&format=JSON&method=alipay.trade.app.pay" +
            "&sign_type=RSA2&sign=abcdefghijklmnopqrstuvwxyz0123456789"
}
