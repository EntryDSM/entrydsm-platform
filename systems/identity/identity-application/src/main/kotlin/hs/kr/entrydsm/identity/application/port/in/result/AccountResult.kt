package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import java.time.Instant

data class AccountResult(
    val userId: Long,
    val role: Role,
    val status: AccountStatus,
    val profile: ProfileResult,
    val createdAt: Instant,
    val updatedAt: Instant,
)
