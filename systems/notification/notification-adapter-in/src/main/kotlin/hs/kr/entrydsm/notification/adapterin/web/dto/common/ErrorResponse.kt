package hs.kr.entrydsm.notification.adapterin.web.dto.common

data class ErrorResponse(
    val status: Int,
    val message: String,
    val code: String,
)
