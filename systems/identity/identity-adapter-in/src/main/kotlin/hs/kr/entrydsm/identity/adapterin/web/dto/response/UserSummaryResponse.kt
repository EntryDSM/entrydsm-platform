package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.AccountStatus

data class UserSummaryResponse(
    val userId: String,
    val role: String,
    val status: AccountStatus,
)
