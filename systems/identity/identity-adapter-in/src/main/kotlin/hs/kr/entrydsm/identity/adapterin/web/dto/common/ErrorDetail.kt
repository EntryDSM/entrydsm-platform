package hs.kr.entrydsm.identity.adapterin.web.dto.common

data class ErrorDetail(
    val code: String,
    val message: String,
    val status: Int,
)
