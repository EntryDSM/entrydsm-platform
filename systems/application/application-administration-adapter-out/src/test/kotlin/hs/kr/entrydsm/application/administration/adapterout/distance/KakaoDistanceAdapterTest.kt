package hs.kr.entrydsm.application.administration.adapterout.distance

import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import org.junit.Assert.*
import org.junit.Test
import tools.jackson.databind.ObjectMapper

class KakaoDistanceAdapterTest {
    @Test
    fun kakaoDistanceReadsCoordinatesAndMeterValue() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "key", "https://local.test", "https://directions.test", "학교", 1000)

        val distance = adapter.parseDistance(
            """{"routes":[{"result_code":0,"summary":{"distance":1234}}]}""",
        )
        val coordinates = adapter.parseCoordinates("""{"documents":[{"x":"127.1","y":"36.3"}]}""")

        assertEquals(1234L, distance)
        assertEquals(KakaoDistanceAdapter.Coordinates(127.1, 36.3), coordinates)
    }

    @Test
    fun kakaoFailureDoesNotExposeApiKey() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "super-secret", "::", "::", "학교", 1)

        val exception = runCatching { adapter.distanceFromSchool("집") }.exceptionOrNull()

        assertTrue(exception is AdminDomainException)
        assertFalse(exception?.message.orEmpty().contains("super-secret"))
    }

}
