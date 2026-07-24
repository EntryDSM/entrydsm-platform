package hs.kr.entrydsm.identity.adapterin.web.dto.common

import hs.kr.entrydsm.identity.domain.enum.ErrorCode

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
