package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role

data class UserSummaryResult(
    val userId: Long,
    val role: Role,
    val status: AccountStatus,
)
