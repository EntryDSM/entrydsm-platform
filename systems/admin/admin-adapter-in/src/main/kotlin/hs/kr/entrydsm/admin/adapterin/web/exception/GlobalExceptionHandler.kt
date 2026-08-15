package hs.kr.entrydsm.admin.adapterin.web.exception

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.admin.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private const val SERVER_ERROR_STATUS = 500

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 클라이언트 잘못이 아닌 5xx는 원인을 남깁니다. 저장소 장애처럼 서버가 고쳐야 할 문제가
     * 코드만 남고 사라지면 추적할 수 없기 때문입니다.
     */
    @ExceptionHandler(AdminException::class)
    fun handleAdminException(exception: AdminException): ResponseEntity<ErrorResponse> =
        response(exception.errorCode).also {
            if (exception.errorCode.status >= SERVER_ERROR_STATUS) {
                logger.error("Admin failure [code={}]", exception.errorCode.name, exception)
            }
        }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        BindException::class,
        ConstraintViolationException::class,
        MethodArgumentNotValidException::class,
        MissingPathVariableException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class,
    )
    fun handleInvalidRequest(exception: Exception): ResponseEntity<ErrorResponse> =
        response(ErrorCode.INVALID_REQUEST_BODY)

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
            .body(ErrorResponse(error = ErrorDetail.from(errorCode)))
}
