package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.AccountStatus

data class UserSummaryResult(
    val userId: Long,
    val role: String,
    val status: AccountStatus,
)
