package hs.kr.entrydsm.application.adapterin.web.dto.common

data class ErrorDetail(
    val code: String,
    val message: String,
    val status: Int,
)

