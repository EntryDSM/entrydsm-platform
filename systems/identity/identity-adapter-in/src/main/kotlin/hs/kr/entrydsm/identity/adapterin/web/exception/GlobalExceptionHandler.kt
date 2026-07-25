package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ErrorResponse> =
        response(exception.errorCode)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        MissingPathVariableException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleInvalidRequest(exception: Exception): ResponseEntity<ErrorResponse> =
        response(ErrorCode.INVALID_REQUEST_BODY)

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(exception: Exception): ResponseEntity<ErrorResponse> =
        response(ErrorCode.INTERNAL_SERVER_ERROR).also {
            logger.error(
                "Unhandled exception [correlationId={}]",
                MDC.get("correlationId") ?: "unknown",
                exception,
            )
        }

    private fun response(errorCode: ErrorCode): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse(
                    error = ErrorDetail.from(errorCode)
                ),
            )
}
