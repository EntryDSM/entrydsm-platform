package hs.kr.entrydsm.observability.domain.service

import hs.kr.entrydsm.observability.domain.enum.DeviceType

/** User-Agent 문자열에서 접근 기기 종류를 판별합니다. */
object DeviceTypeParser {
    fun parse(userAgent: String?): DeviceType {
        val ua = userAgent?.lowercase() ?: return DeviceType.ETC
        return when {
            "android" in ua -> DeviceType.ANDROID
            "iphone" in ua || "ipad" in ua || "ios" in ua -> DeviceType.IOS
            "windows" in ua -> DeviceType.WINDOWS
            else -> DeviceType.ETC
        }
    }
}
