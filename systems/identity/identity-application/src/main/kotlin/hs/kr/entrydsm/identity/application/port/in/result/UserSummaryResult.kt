package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.AccountStatus

data class UserSummaryResult(
    val userId: String,
    val role: String,
    val status: AccountStatus,
)
