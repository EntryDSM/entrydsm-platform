package hs.kr.entrydsm.admin.adapterin.web.dto.common

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T?,
    val error: ErrorDetail? = null,
)
