package hs.kr.entrydsm.observability.adapterin.web.exception

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.observability.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MonitorException::class)
    fun handleMonitorException(exception: MonitorException): ResponseEntity<ErrorResponse> =
        response(exception.errorCode)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        BindException::class,
        ConstraintViolationException::class,
        MethodArgumentNotValidException::class,
        MissingPathVariableException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleInvalidRequest(exception: Exception): ResponseEntity<ErrorResponse> =
        response(ErrorCode.INVALID_PAYLOAD)

    // SSE 구독자가 끊겼거나(다음 전송에서 드러난다) 구독 시간이 끝난 정상 종료다.
    // 응답은 이미 text/event-stream 으로 나가 오류 본문을 쓸 수 없으니 아무것도 하지 않는다.
    @ExceptionHandler(AsyncRequestNotUsableException::class, AsyncRequestTimeoutException::class)
    fun handleClosedStream() {
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(exception: Exception): ResponseEntity<ErrorResponse> =
        response(ErrorCode.INTERNAL_SERVER_ERROR).also {
            logger.error(
                "Unhandled exception [X-trace-Id={}]",
                MDC.get("X-trace-Id") ?: "unknown",
                exception,
            )
        }

    private fun response(errorCode: ErrorCode): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(errorCode.status)
            // SSE 구독 요청은 Accept 가 text/event-stream 뿐이라 협상에 맡기면 JSON 을 못 골라 401·403·429 가 500 이 된다.
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ErrorResponse(
                    error = ErrorDetail.from(errorCode)
                ),
            )
}
