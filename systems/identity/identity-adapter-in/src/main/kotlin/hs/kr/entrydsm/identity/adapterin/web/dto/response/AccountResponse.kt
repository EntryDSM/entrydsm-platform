package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import java.time.Instant

data class AccountResponse(
    val userId: String,
    val role: String,
    val status: AccountStatus,
    val profile: ProfileResponse,
    val createdAt: Instant,
    val updatedAt: Instant,
)
