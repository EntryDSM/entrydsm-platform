package hs.kr.entrydsm.application.adapterin.web.exception

import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.application.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.application.application.exception.ApplicantAlreadyExistsException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import hs.kr.entrydsm.application.application.exception.SensitiveConsentRequiredException
import hs.kr.entrydsm.application.application.exception.ApplicationAccessDeniedException
import hs.kr.entrydsm.application.application.exception.ApplicationErrorCode
import hs.kr.entrydsm.application.application.exception.AccountPhoneLookupFailedException
import hs.kr.entrydsm.application.application.exception.ApplicationValidationException
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import jakarta.servlet.http.HttpServletRequest
import tools.jackson.core.JsonToken
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException
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

    @ExceptionHandler(EvaluationValidationException::class)
    fun handleEvaluationValidation(exception: EvaluationValidationException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.message ?: "평가에 필요한 성적을 확인해주세요")

    @ExceptionHandler(AccountPhoneLookupFailedException::class)
    fun handleAccountPhoneLookupFailed(exception: AccountPhoneLookupFailedException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_SERVICE_UNAVAILABLE", "가입 전화번호를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.")
            .also {
                logger.warn("Account phone lookup failed [correlationId={}, causeType={}]", MDC.get("correlationId") ?: "unknown", exception.cause?.javaClass?.name)
            }

    @ExceptionHandler(ApplicantAlreadyExistsException::class)
    fun handleApplicantAlreadyExists(exception: ApplicantAlreadyExistsException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.CONFLICT,
            code = "APPLICANT_ALREADY_EXISTS",
            message = exception.message ?: "applicant already exists",
        )

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

    @ExceptionHandler(ApplicationPeriodClosedException::class)
    fun handleApplicationPeriodClosed(exception: ApplicationPeriodClosedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.FORBIDDEN,
            code = "APPLICATION_PERIOD_CLOSED",
            message = exception.message ?: "application period is closed",
        )

    // 기간을 확인하지 못하면 원서를 받지 않는다. configuration 장애라 서버 쪽 오류로 남긴다.
    @ExceptionHandler(ApplicationPeriodLookupFailedException::class)
    fun handleApplicationPeriodLookupFailed(exception: ApplicationPeriodLookupFailedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            code = "SCHEDULE_SERVICE_UNAVAILABLE",
            message = "schedule service is unavailable",
        ).also {
            logger.warn(
                "Application period lookup failed [correlationId={}]",
                MDC.get("correlationId") ?: "unknown",
                exception,
            )
        }

    @ExceptionHandler(ApplicationAccessDeniedException::class)
    fun handleApplicationAccessDenied(exception: ApplicationAccessDeniedException): ResponseEntity<ErrorResponse> =
        response(
            status = HttpStatus.FORBIDDEN,
            code = "ACCESS_DENIED",
            message = exception.message ?: "student role is required",
        )

    @ExceptionHandler(ApplicationValidationException::class)
    fun handleApplicationValidation(exception: ApplicationValidationException): ResponseEntity<ErrorResponse> =
        inputError(exception.errorCode)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val error = exception.bindingResult.fieldErrors
            .sortedWith(compareBy({ it.field }, { it.code ?: "" }))
            .firstOrNull()
        val reason = when (error?.code) {
            "NotBlank", "NotNull" -> "REQUIRED"
            "Size" -> "TOO_LONG"
            "Pattern" -> "INVALID_FORMAT"
            else -> null
        }
        val code = if (error != null && reason != null) ApplicationErrorCode.find(error.field, reason) else null
        return code?.let(::inputError) ?: handleInvalidRequest(exception, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(exception: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val cause = generateSequence(exception.cause) { it.cause }
            .filterIsInstance<MismatchedInputException>().firstOrNull()
        val field = cause?.path?.mapNotNull { it.propertyName }?.joinToString(".")
        val reason = when {
            cause is InvalidNullException || cause?.currentToken == JsonToken.VALUE_NULL -> "REQUIRED"
            cause?.targetType?.isEnum == true -> "INVALID_VALUE"
            else -> "INVALID_TYPE"
        }
        val code = field?.let { ApplicationErrorCode.find(it, reason) }
        return code?.let(::inputError) ?: handleInvalidRequest(exception, request)
    }

    private fun inputError(code: ApplicationErrorCode): ResponseEntity<ErrorResponse> =
        response(HttpStatus.BAD_REQUEST, code.name, code.message)

    @ExceptionHandler(
        IllegalArgumentException::class,
        MissingPathVariableException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleInvalidRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn(
            "Unclassified bad request [correlationId={}, method={}, path={}, exceptionType={}, causeType={}]",
            MDC.get("correlationId") ?: "unknown",
            request.method,
            request.requestURI,
            exception.javaClass.name,
            exception.cause?.javaClass?.name ?: "none",
        )
        return response(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "요청 형식을 확인해주세요.",
        )
    }

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

