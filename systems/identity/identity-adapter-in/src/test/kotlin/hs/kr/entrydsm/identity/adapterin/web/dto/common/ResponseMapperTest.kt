package hs.kr.entrydsm.identity.adapterin.web.dto.common

import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseMapperTest {
    private val timestamp = Instant.parse("2026-06-11T10:00:00Z")
    private val profile = ProfileResult(
        name = "홍길동",
        phone = "01012345678",
        birthdate = LocalDate.parse("2009-03-15"),
        signupType = SignupType.SELF,
        applicantStatus = ApplicantStatus.SUBMITTED,
    )

    @Test
    fun mapsUserIdToExternalFormat() {
        val response = UserSummaryResult(123L, Role.USER, AccountStatus.ACTIVE).toResponse()

        assertEquals("user_123", response.userId)
        assertEquals("USER", response.role)
    }

    @Test
    fun mapsAccountAndNestedProfile() {
        val response = AccountResult(
            userId = 123L,
            role = Role.USER,
            status = AccountStatus.ACTIVE,
            profile = profile,
            createdAt = timestamp,
            updatedAt = timestamp,
        ).toResponse()

        assertEquals("user_123", response.userId)
        assertEquals("USER", response.role)
        assertEquals(profile.name, response.profile.name)
        assertEquals(profile.applicantStatus, response.profile.applicantStatus)
        assertEquals(timestamp, response.updatedAt)
    }

    @Test
    fun mapsBasicInfoAndProfileFields() {
        val response = BasicInfoResult(
            userId = 123L,
            role = Role.USER,
            status = AccountStatus.ACTIVE,
            name = profile.name,
            phone = profile.phone,
            birthdate = profile.birthdate,
            signupType = profile.signupType,
            applicantStatus = profile.applicantStatus,
            createdAt = timestamp,
            updatedAt = timestamp,
        ).toResponse()

        assertEquals("user_123", response.userId)
        assertEquals("USER", response.role)
        assertEquals(profile.birthdate, response.birthdate)
        assertEquals(profile.signupType, response.signupType)
    }

    @Test
    fun preservesNullableApplicationTimestamps() {
        val statusResponse = ApplicationStatusResult(
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            updatedAt = timestamp,
        ).toResponse()
        val resultResponse = ApplicationResultResult(
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        ).toResponse()

        assertEquals(null, statusResponse.submittedAt)
        assertEquals(null, resultResponse.announcedAt)
        assertEquals(ApplicantStatus.NONE, statusResponse.applicantStatus)
        assertEquals(PassStatus.NOT_ANNOUNCED, resultResponse.passStatus)
    }
}
