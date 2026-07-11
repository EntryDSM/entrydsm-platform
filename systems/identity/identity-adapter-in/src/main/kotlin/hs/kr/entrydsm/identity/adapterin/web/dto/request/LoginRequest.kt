package hs.kr.entrydsm.identity.adapterin.web.dto.request

data class LoginRequest(
    val loginId: String,
    val password: String,
)
