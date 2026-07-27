package hs.kr.entrydsm.configuration.adapterin.common

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val timestamp: Instant? = null,
) {
    companion object {
        fun <T> success(data: T) = ApiResponse(success = true, data = data)

        fun failure(errorCode: ErrorCode, message: String? = null) = ApiResponse<Nothing>(
            success = false,
            error = ErrorResponse(
                code = errorCode.name,
                message = message ?: errorCode.message,
                status = errorCode.status.value(),
            ),
            timestamp = Instant.now(),
        )
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val status: Int,
)
