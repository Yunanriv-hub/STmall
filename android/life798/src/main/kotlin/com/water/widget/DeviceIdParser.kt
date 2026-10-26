package com.water.widget

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** 将扫码结果或手动输入收敛为可存储的设备编号。 */
object DeviceIdParser {
    private val deviceKeyPattern = Regex(
        """(?:[?&#;,\s{]|^)[\"']?(?:q|did|deviceId|device_id|deviceCode|code|id)[\"']?\s*(?:=|:)\s*[\"']?([^&#/,}\s\"']+)""",
        RegexOption.IGNORE_CASE
    )
    private val explicitPathMarkers = setOf("device", "devices", "did", "bind", "binding", "q", "qr")
    private val strongDeviceIdPattern = Regex("""\d{8,32}""")
    private val flexibleDeviceIdPattern = Regex("""[A-Za-z0-9][A-Za-z0-9_-]{5,63}""")
    private val explicitDeviceIdPattern = Regex("""[A-Za-z0-9][A-Za-z0-9_-]{2,63}""")

    fun normalize(raw: String): String {
        val input = raw.trim()
        if (input.isBlank()) return ""
        return normalizeOrNull(input) ?: input
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .take(160)
    }

    /** 只返回可识别的设备号；二维码、NFC 和手动输入共用。 */
    fun normalizeOrNull(raw: String?): String? {
        var current = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        repeat(4) {
            val decoded = decode(current).trim()
            val candidates = deviceKeyPattern.findAll(decoded)
                .map { it.groupValues[1] }
                .toList()
                .asReversed()
            for (rawCandidate in candidates) {
                val candidate = clean(rawCandidate)
                if (isExplicitDeviceId(candidate)) return candidate.take(160)
                if (candidate.startsWith("http", ignoreCase = true) && candidate != current) {
                    return normalizeOrNull(candidate)
                }
            }
            extractFromUrlPath(decoded)?.let { return it.take(160) }
            val candidate = clean(decoded)
            if (isDeviceId(candidate)) return candidate.take(160)
            if (candidate == current || candidate.length > 2048) return null
            current = candidate
        }
        return null
    }

    private fun extractFromUrlPath(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https", "ilife")) return null

        val segments = uri.path
            ?.split('/')
            ?.map(::decode)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (segments.isEmpty()) return null

        val host = uri.host?.lowercase().orEmpty()
        if (host == "hnkzy.com" || host.endsWith(".hnkzy.com")) {
            segments.asReversed().firstOrNull(::isStrongDeviceId)?.let { return it }
        }

        val markerIndex = segments.indexOfLast { it.lowercase() in explicitPathMarkers }
        if (markerIndex >= 0) {
            segments.drop(markerIndex + 1).asReversed()
                .firstOrNull(::isFlexibleDeviceId)
                ?.let { return it }
        }

        return segments.lastOrNull()?.takeIf(::isStrongDeviceId)
    }

    private fun isStrongDeviceId(value: String): Boolean = strongDeviceIdPattern.matches(value)

    private fun isFlexibleDeviceId(value: String): Boolean =
        flexibleDeviceIdPattern.matches(value) && value.any(Char::isDigit)

    private fun isExplicitDeviceId(value: String): Boolean =
        explicitDeviceIdPattern.matches(value) && value.any(Char::isDigit)

    private fun isDeviceId(value: String): Boolean =
        isStrongDeviceId(value) || isFlexibleDeviceId(value)

    private fun clean(value: String): String = value
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .removePrefix("did:")
        .removePrefix("DID:")

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
