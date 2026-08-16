package hs.kr.entrydsm.application.adapterin.web.exception

import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.application.application.exception.ApplicantAccessDeniedException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
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

    @ExceptionHandler(ApplicantNotFoundException::class)
    fun handleApplicantNotFound(exception: ApplicantNotFoundException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.NOT_FOUND,
            code = "APPLICANT_NOT_FOUND",
            message = exception.message ?: "applicant not found",
        )

    @ExceptionHandler(ApplicantAccessDeniedException::class)
    fun handleApplicantAccessDenied(exception: ApplicantAccessDeniedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.FORBIDDEN,
            code = "APPLICANT_ACCESS_DENIED",
            message = exception.message ?: "applicant access denied",
        )

    @ExceptionHandler(
        IllegalArgumentException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        MissingPathVariableException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleInvalidRequest(exception: Exception): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = exception.message ?: "invalid request",
        )

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(exception: Exception): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "internal server error",
        ).also {
            logger.error(
                "Unhandled exception [correlationId={}]",
                MDC.get("correlationId") ?: "unknown",
                exception,
            )
        }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(
                ErrorResponse(
                    error = ErrorDetail(
                        code = code,
                        message = message,
                        status = status.value(),
                    ),
                ),
            )
}

