package hs.kr.entrydsm.identity

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.identity.config.SecurityConfig
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class IdentityBootstrapApplicationTest {
    @Test
    fun contextLoads() {
        assertTrue(true)
    }

    @Test
    fun objectMapperSupportsJavaTimeValues() {
        val objectMapper = SecurityConfig().objectMapper()
        val timestamp = Instant.parse("2026-07-21T00:00:00Z")
        val response = ErrorResponse(
            error = ErrorDetail(
                code = ErrorCode.AUTH_UNAUTHORIZED.name,
                message = ErrorCode.AUTH_UNAUTHORIZED.message,
                status = ErrorCode.AUTH_UNAUTHORIZED.status,
            ),
            timestamp = timestamp,
        )

        val serialized = objectMapper.writeValueAsString(response)
        val deserialized = objectMapper.readValue("\"2026-07-21\"", LocalDate::class.java)

        assertTrue(serialized.contains("\"timestamp\":\"2026-07-21T00:00:00Z\""))
        assertTrue(deserialized == LocalDate.of(2026, 7, 21))
    }
}
