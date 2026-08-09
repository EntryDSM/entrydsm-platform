package hs.kr.entrydsm.observability.adapterin.web.dto.common

import hs.kr.entrydsm.observability.domain.enum.ErrorCode

data class ErrorDetail(
    val code: String,
    val message: String,
    val status: Int,
) {
    companion object {
        fun from(errorCode: ErrorCode) = ErrorDetail(
            errorCode.name,
            errorCode.message,
            errorCode.status,
        )
    }
}
