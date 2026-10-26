package com.fktm.app.pangguai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
