package hs.kr.entrydsm.notification.adapterin.web.dto.common

data class ApiResponse<T>(
    val status: Int,
    val message: String,
    val data: T?,
)

