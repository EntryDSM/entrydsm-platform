package hs.kr.entrydsm.identity.domain

import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import java.time.LocalDate

class StudentProfile(
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
    var applicantStatus: ApplicantStatus = ApplicantStatus.NONE,
    var passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,
    var submittedAt: Instant? = null,
    var announcedAt: Instant? = null,
    var updatedAt: Instant,
) {
    fun cancel(now: Instant) {
        if (applicantStatus != ApplicantStatus.SUBMITTED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
        }
        applicantStatus = ApplicantStatus.CANCELED
        updatedAt = now
    }

    fun isResultAvailable(): Boolean = passStatus != PassStatus.NOT_ANNOUNCED
}
