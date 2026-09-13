package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.service.UserAgentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserAgentParserTest {
    @Test
    fun parsesChromeOnAndroid() {
        val ua = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"

        assertEquals("Chrome 138", UserAgentParser.browser(ua))
        assertEquals("Android 15", UserAgentParser.os(ua))
    }

    @Test
    fun parsesSafariOnMac() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

        assertEquals("Safari 17", UserAgentParser.browser(ua))
        assertEquals("macOS 10.15.7", UserAgentParser.os(ua))
    }

    @Test
    fun returnsNullForMissingUserAgent() {
        assertNull(UserAgentParser.browser(null))
        assertNull(UserAgentParser.os(null))
    }
}
