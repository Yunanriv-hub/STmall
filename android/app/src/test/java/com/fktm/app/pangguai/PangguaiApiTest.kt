package com.fktm.app.pangguai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PangguaiApiTest {

    @Test
    fun extractsUppercaseNqtFromEncodedUrl() {
        assertEquals(
            "A1 B2",
            PangguaiApi.extractNqt(
                "https://example.test/device?foo=1&NQT=A1%20B2"
            ),
        )
    }

    @Test
    fun extractsLowercaseNqtWithPlusEncoding() {
        assertEquals(
            "A1 B2",
            PangguaiApi.extractNqt(
                "https://example.test/device?nqt=A1+B2"
            ),
        )
    }

    @Test
    fun rejectsPayloadWithoutNqt() {
        assertNull(PangguaiApi.extractNqt("https://example.test/device?foo=1"))
        assertNull(PangguaiApi.extractNqt("not a url"))
    }

    @Test
    fun extractsOfficialDeviceCodeKeysUsedByWasherAndHairDryer() {
        assertEquals(
            PangguaiScanCode("IMEI", "860000000000001"),
            PangguaiApi.extractDeviceCode("https://example.test/d?IMEI=860000000000001"),
        )
        assertEquals(
            PangguaiScanCode("SN", "ABCD1234"),
            PangguaiApi.extractDeviceCode("source=scan&sn=ABCD1234&from=app"),
        )
        assertNull(PangguaiApi.extractDeviceCode("https://example.test/d?foo=1"))
    }

    @Test
    fun acceptsOnlyOfficialAlipayStartAppTargetsFromChargeEndpoint() {
        assertTrue(
            PangguaiApi.isSupportedAlipayTarget(
                "alipays://platformapi/startapp?appId=2018072460764274"
            )
        )
        assertTrue(
            PangguaiApi.isSupportedAlipayTarget(
                "alipayqr://platformapi/startapp?saId=10000007"
            )
        )
        assertFalse(
            PangguaiApi.isSupportedAlipayTarget(
                "alipays://evil.example/startapp?appId=1"
            )
        )
        assertFalse(
            PangguaiApi.isSupportedAlipayTarget(
                "https://h5.qiekj.com/device"
            )
        )
    }

    @Test
    fun buildsOriginalAlipayMiniAppTargetForHairDryerScan() {
        val target = PangguaiScanResult(
            id = "",
            jumpToAlipayFlag2 = true,
            jumpToDevicePageFlag = true,
            deviceParams = "NQT=DRYER-01",
        ).alipayTarget()

        assertEquals(
            "alipays://platformapi/startapp?appId=2018072460764274" +
                "&page=pages/skipNew/skipNew" +
                "&query=NQT%3DDRYER-01%26fromApp%3D1%26%26keepUsing%3D1",
            target,
        )
    }

    @Test
    fun fallsBackToAlipayScannerForFlagTwoAndKeepsNativeDevicesNative() {
        assertEquals(
            "alipayqr://platformapi/startapp?saId=10000007",
            PangguaiScanResult(
                id = "",
                jumpToAlipayFlag2 = true,
                jumpToDevicePageFlag = false,
                deviceParams = "NQT=1",
            ).alipayTarget(),
        )
        assertNull(
            PangguaiScanResult(
                id = "123",
                jumpToAlipayFlag2 = false,
                jumpToDevicePageFlag = false,
                deviceParams = "NQT=1",
            ).alipayTarget()
        )
    }

    @Test
    fun signatureMatchesKnownSha256Vector() {
        assertEquals(
            "c5079206b3a93a59f72667388eb113e50bb2b7b4dfaa6cf5289496a0574938d6",
            pangguaiSignature(
                appSecret = "secret",
                channel = "android_app",
                timestamp = "1700000000000",
                token = "tok",
                version = "1.60.3",
                path = "/user/info",
            ),
        )
    }
}
