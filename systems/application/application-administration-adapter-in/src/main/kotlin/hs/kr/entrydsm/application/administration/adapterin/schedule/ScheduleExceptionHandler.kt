package hs.kr.entrydsm.application.administration.adapterin.schedule

import hs.kr.entrydsm.application.administration.domain.schedule.ScheduleNotFoundException
import java.time.Instant
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [ScheduleController::class])
class ScheduleExceptionHandler {
    @ExceptionHandler(ScheduleNotFoundException::class)
    fun missing(error: ScheduleNotFoundException) = failure(404, "SCHEDULE_NOT_FOUND", "일정을 찾을 수 없습니다.")

    @ExceptionHandler(ScheduleUnauthorizedException::class)
    fun unauthorized(error: ScheduleUnauthorizedException) = failure(401, "AUTH_UNAUTHORIZED", "인증이 필요합니다.")

    @ExceptionHandler(ScheduleAccessDeniedException::class)
    fun denied(error: ScheduleAccessDeniedException) = failure(403, "ACCESS_DENIED", "접근 권한이 없습니다.")

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class, java.time.DateTimeException::class)
    fun invalid(error: Exception) = failure(400, "INVALID_REQUEST_PARAM", "요청 파라미터가 올바르지 않습니다.")

    private fun failure(status: Int, code: String, message: String) = ResponseEntity.status(status).body(
        mapOf("success" to false, "error" to mapOf("code" to code, "message" to message, "status" to status),
            "timestamp" to Instant.now()),
    )
}
