package hs.kr.entrydsm.identity.domain.model

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import java.time.LocalDate

class StudentProfile(
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
    applicantStatus: ApplicantStatus = ApplicantStatus.NONE,
    passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,
    submittedAt: Instant? = null,
    announcedAt: Instant? = null,
    updatedAt: Instant,
) {
    var applicantStatus: ApplicantStatus = applicantStatus
        private set

    var passStatus: PassStatus = passStatus
        private set

    var submittedAt: Instant? = submittedAt
        private set

    var announcedAt: Instant? = announcedAt
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun submit(now: Instant) {
        applicantStatus = ApplicantStatus.SUBMITTED
        submittedAt = now
        updatedAt = now
    }

    fun cancel(now: Instant) {
        if (applicantStatus != ApplicantStatus.SUBMITTED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
        }
        applicantStatus = ApplicantStatus.CANCELED
        updatedAt = now
    }

    fun announceResult(result: PassStatus, now: Instant) {
        check(result != PassStatus.NOT_ANNOUNCED) {
            "An announced result must be PASSED or FAILED"
        }
        passStatus = result
        announcedAt = now
        updatedAt = now
    }

    fun isResultAvailable(): Boolean = passStatus != PassStatus.NOT_ANNOUNCED
}
