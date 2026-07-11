package hs.kr.entrydsm.identity.adapterin.web.dto.common

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T?,
    val error: Any? = null,
)
