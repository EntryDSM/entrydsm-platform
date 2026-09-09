package hs.kr.entrydsm.observability.adapterin.web.dto.common

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T?,
    val error: ErrorDetail? = null,
)
