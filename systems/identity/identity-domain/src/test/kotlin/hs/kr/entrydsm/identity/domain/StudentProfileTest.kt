package hs.kr.entrydsm.identity.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentProfileTest {
    @Test
    fun cancelsSubmittedApplication() {
        val now = Instant.parse("2026-06-11T11:00:00Z")
        val profile = StudentProfile(
            name = "홍길동",
            phone = "01012345678",
            birthdate = LocalDate.parse("2009-03-15"),
            signupType = SignupType.SELF,
            applicantStatus = ApplicantStatus.SUBMITTED,
            updatedAt = Instant.parse("2026-06-11T10:00:00Z"),
        )

        profile.cancel(now)

        assertEquals(ApplicantStatus.CANCELED, profile.applicantStatus)
        assertEquals(now, profile.updatedAt)
    }
}
