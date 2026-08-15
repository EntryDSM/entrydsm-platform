package hs.kr.entrydsm.admin.adapterin.web.dto.common

import hs.kr.entrydsm.admin.domain.enum.ErrorCode

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
