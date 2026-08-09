package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.service.DeviceTypeParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeParserTest {
    @Test
    fun parsesAndroid() {
        assertEquals(DeviceType.ANDROID, DeviceTypeParser.parse("Mozilla/5.0 (Linux; Android 15)"))
    }

    @Test
    fun parsesIos() {
        assertEquals(DeviceType.IOS, DeviceTypeParser.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)"))
    }

    @Test
    fun parsesWindows() {
        assertEquals(DeviceType.WINDOWS, DeviceTypeParser.parse("Mozilla/5.0 (Windows NT 10.0)"))
    }

    @Test
    fun fallsBackToEtcForUnknownOrMissingUserAgent() {
        assertEquals(DeviceType.ETC, DeviceTypeParser.parse(null))
        assertEquals(DeviceType.ETC, DeviceTypeParser.parse("SomeBot/1.0"))
    }
}
