package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile

/** Account data required before the database assigns the account ID. */
data class AccountRegistration(
    val loginId: String,
    val passwordHash: PasswordHash,
    val role: Role,
    val status: AccountStatus,
    val profile: StudentProfile,
)
