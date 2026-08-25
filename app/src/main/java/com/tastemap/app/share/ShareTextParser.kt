package com.tastemap.app.share

/**
 * F15 v1：解析外部分享进来的文本（大众点评/美团/小红书/高德等"复制链接/分享文案"）。
 * 只做本地正则解析（零网络）：提取店名候选与经纬度；短链接的网络展开刻意不做（硬约束 2）。
 */
object ShareTextParser {

    data class Parsed(val name: String?, val lat: Double?, val lng: Double?)

    fun parse(text: String): Parsed {
        val lat = Regex("(?:dlat=|lat=)(-?\\d{1,2}\\.\\d{3,})").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("(?<![\\d.])([1-8]?\\d\\.\\d{4,})[,，]\\s*")  // "30.6xxx," 前半段
                .find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val lng = Regex("(?:dlon=|lon=|lng=)(-?\\d{1,3}\\.\\d{3,})").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("[,，]\\s*(-?\\d{2,3}\\.\\d{4,})(?![\\d])")   // ",114.3xxx" 后半段
                .find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        val name = Regex("[【『“\"']([^】』”\"']{2,30})[】』”\"']").find(text)?.groupValues?.get(1)?.trim()
            ?: text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.length in 2..30 && !it.startsWith("http", true) && !it.matches(Regex(".*\\d{2}\\.\\d{3,}.*")) }
                ?.trimEnd('！', '!', '，', ',', '。', '.')

        return Parsed(name, lat, lng)
    }
}
