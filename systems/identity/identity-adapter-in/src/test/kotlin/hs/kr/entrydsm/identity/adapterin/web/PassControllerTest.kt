package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.request.PassPopupRequest
import hs.kr.entrydsm.identity.application.port.`in`.PassPort
import hs.kr.entrydsm.identity.application.port.`in`.PassVerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

class PassControllerTest {
    @Test
    fun popupDelegatesRedirectUrlAndReturnsHtml() {
        val passPort = RecordingPassPort()
        val response = PassController(passPort).popup(PassPopupRequest("https://auth.entrydsm.kr/callback"))

        assertEquals("https://auth.entrydsm.kr/callback", passPort.redirectUrl)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("text/html", response.headers.contentType?.toString())
        assertEquals("<form></form>", response.body)
    }

    @Test
    fun infoPreventsCachingAndReferrerLeakage() {
        val response = PassController(RecordingPassPort()).info("model-token")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("01012345678", response.body?.data?.phoneNumber)
        assertEquals("홍길동", response.body?.data?.name)
        assertEquals("no-store, no-cache, must-revalidate", response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
        assertEquals("no-cache", response.headers.getFirst(HttpHeaders.PRAGMA))
        assertEquals("no-referrer", response.headers.getFirst("Referrer-Policy"))
    }

    @Test
    fun infoPassesTheModelTokenToTheApplicationPort() {
        val passPort = RecordingPassPort()

        PassController(passPort).info("model-token")

        assertEquals("model-token", passPort.modelToken)
        assertTrue(passPort.verifyCalled)
    }

    private class RecordingPassPort : PassPort {
        var redirectUrl: String? = null
        var modelToken: String? = null
        var verifyCalled = false

        override fun generatePopup(redirectUrl: String): String {
            this.redirectUrl = redirectUrl
            return "<form></form>"
        }

        override fun verify(token: String): PassVerificationResult {
            modelToken = token
            verifyCalled = true
            return PassVerificationResult("01012345678", "홍길동")
        }
    }
}
