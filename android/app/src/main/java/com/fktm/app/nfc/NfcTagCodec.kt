package com.fktm.app.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.water.widget.DeviceIdParser
import java.net.URLEncoder

/**
 * 原版慧生活798 将设备贴纸写成 i.ilife798.com/nfc.html?q=<设备编号>。
 * 这里同时容忍 did/deviceId/device_id 等常见字段，便于读取旧标签。
 */
object NfcTagCodec {

    private val ilifeHosts = setOf(
        "i.ilife798.com",
        "i.hnkzy.com",
        "act.hnkzy.com",
        "act2.hnkzy.com",
        "prerelease.ilife798.com",
    )

    fun extractDeviceId(raw: String?): String? {
        return DeviceIdParser.normalizeOrNull(raw)
    }

    fun isIlifePayload(raw: String?): Boolean {
        val text = raw?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val uri = runCatching { Uri.parse(text) }.getOrNull()
        val host = uri?.host?.lowercase()
        return host in ilifeHosts ||
            text.contains("application/com.cloudora.android", ignoreCase = true) ||
            (host != null && host.endsWith(".ilife798.com") && extractDeviceId(text) != null)
    }

    fun tagUri(id: String): String =
        "https://i.ilife798.com/nfc.html?q=" +
            URLEncoder.encode(id.trim(), Charsets.UTF_8.name())

    fun messageForDevice(id: String): NdefMessage =
        NdefMessage(arrayOf(NdefRecord.createUri(tagUri(id))))

    fun extractText(message: NdefMessage?): String? {
        if (message == null) return null
        return sequence {
            for (record in message.records) {
                when {
                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        record.toUri()?.toString()?.let { yield(it) }
                    }

                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        readableText(record.payload)?.let { yield(it) }
                    }

                    record.tnf == NdefRecord.TNF_ABSOLUTE_URI ||
                        record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                        readableText(record.payload)?.let { yield(it) }
                    }
                }
            }
        }.firstOrNull { it.isNotBlank() }?.take(2048)
    }

    private fun readableText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val utf8 = payload.toString(Charsets.UTF_8).trim()
        if (utf8.isNotEmpty() && utf8.all { it.isLetterOrDigit() || it in "/:?&=._-#%" }) {
            return utf8
        }

        // NFC Forum Text Record: status byte + language code + UTF-8/UTF-16 text.
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        val textStart = 1 + languageLength
        if (textStart >= payload.size) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return payload.copyOfRange(textStart, payload.size).toString(charset).trim().takeIf(String::isNotEmpty)
    }
}
