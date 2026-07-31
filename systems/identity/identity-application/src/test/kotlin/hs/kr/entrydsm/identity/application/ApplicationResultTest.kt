package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationResultTest {
    @Test
    fun keepsApplicationResultContract() {
        val announcedAt = Instant.parse("2026-06-11T10:00:00Z")
        val result = ApplicationResultResult(PassStatus.PASSED, announcedAt)

        assertEquals(PassStatus.PASSED, result.passStatus)
        assertEquals(announcedAt, result.announcedAt)
    }
}
