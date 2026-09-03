package hs.kr.entrydsm.admin.adapterin.web.dto.common

import java.time.Instant

data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail,
    val timestamp: Instant = Instant.now(),
)
