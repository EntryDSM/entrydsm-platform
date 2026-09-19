package hs.kr.entrydsm.application.adapterin.web.exception

import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import hs.kr.entrydsm.application.application.exception.SensitiveConsentRequiredException
import hs.kr.entrydsm.application.application.exception.ApplicationAccessDeniedException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.CONFLICT,
            code = "DATA_INTEGRITY_VIOLATION",
            message = "data integrity conflict",
        )

    @ExceptionHandler(ApplicantNotFoundException::class)
    fun handleApplicantNotFound(exception: ApplicantNotFoundException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.NOT_FOUND,
            code = "APPLICANT_NOT_FOUND",
            message = exception.message ?: "applicant not found",
        )

    @ExceptionHandler(AuthenticationRequiredException::class)
    fun handleAuthenticationRequired(exception: AuthenticationRequiredException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.UNAUTHORIZED,
            code = "AUTHENTICATION_REQUIRED",
            message = exception.message ?: "authentication is required",
        )

    @ExceptionHandler(SensitiveConsentRequiredException::class)
    fun handleSensitiveConsentRequired(exception: SensitiveConsentRequiredException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.FORBIDDEN,
            code = "SENSITIVE_CONSENT_REQUIRED",
            message = exception.message ?: "sensitive information consent is required",
        )

    @ExceptionHandler(ApplicationAccessDeniedException::class)
    fun handleApplicationAccessDenied(exception: ApplicationAccessDeniedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.FORBIDDEN,
            code = "ACCESS_DENIED",
            message = exception.message ?: "student role is required",
        )

    @ExceptionHandler(
        IllegalArgumentException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        MissingPathVariableException::class,
        MissingRequestHeaderException::class,
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

    // 없는 경로·메서드 요청이 아래 Exception 처리로 빠지면 500과 ERROR 로그가 남고, gateway 서킷 브레이커가 실패로 센다.
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleApiNotFound(exception: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.NOT_FOUND,
            code = "API_NOT_FOUND",
            message = "api not found",
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(exception: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            code = "METHOD_NOT_ALLOWED",
            message = "method not allowed",
            headers = exception.headers,
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
        headers: HttpHeaders = HttpHeaders.EMPTY,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .headers(headers)
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

