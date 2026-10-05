package hs.kr.entrydsm.application.administration.adapterin.common

import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = ["hs.kr.entrydsm.application.administration.adapterin.document"])
class DocumentExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException) = respond(ErrorCode.AUTH_UNAUTHORIZED, e)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException) = respond(ErrorCode.ACCESS_DENIED, e)

    @ExceptionHandler(hs.kr.entrydsm.application.administration.domain.document.exception.ApplicantNotFoundException::class)
    fun handleApplicantNotFound(e: hs.kr.entrydsm.application.administration.domain.document.exception.ApplicantNotFoundException) =
        respond(ErrorCode.APPLICANT_NOT_FOUND, e)

    @ExceptionHandler(hs.kr.entrydsm.application.administration.domain.document.exception.ApplicantLookupFailedException::class)
    fun handleApplicantLookupFailed(e: hs.kr.entrydsm.application.administration.domain.document.exception.ApplicantLookupFailedException) =
        respond(when (e.cause) {
            is hs.kr.entrydsm.application.application.exception.EvaluationValidationException -> ErrorCode.APPLICATION_SCORE_INVALID
            is IllegalArgumentException -> ErrorCode.APPLICATION_FORM_INVALID
            else -> ErrorCode.APPLICATION_SERVICE_UNAVAILABLE
        }, e)

    @ExceptionHandler(InvalidFileFormatException::class)
    fun handleInvalidFileFormat(e: InvalidFileFormatException) =
        respond(ErrorCode.FILE_INVALID_FORMAT, e)

    @ExceptionHandler(FileTooLargeException::class, MaxUploadSizeExceededException::class)
    fun handleFileTooLarge(e: Exception) =
        respond(ErrorCode.FILE_TOO_LARGE, e)

    @ExceptionHandler(FileDocumentNotFoundException::class)
    fun handleFileNotFound(e: FileDocumentNotFoundException) =
        respond(ErrorCode.FILE_NOT_FOUND, e)

    @ExceptionHandler(DocumentAccessDeniedException::class)
    fun handleDocumentAccessDenied(e: DocumentAccessDeniedException) =
        respond(ErrorCode.FILE_ACCESS_DENIED, e)

    /** 멀티파트가 아닌 요청이나 파일 파트 누락도 여기서 400 이다. 용량 초과는 더 구체적인 처리기가 413 으로 받는다. */
    @ExceptionHandler(
        InvalidFileNameException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MultipartException::class,
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class,
    )
    fun handleInvalidRequestParam(e: Exception) =
        respond(ErrorCode.INVALID_REQUEST_PARAM, e)

    @ExceptionHandler(StorageUnavailableException::class)
    fun handleStorageUnavailable(e: StorageUnavailableException) =
        respond(ErrorCode.FILE_STORAGE_UNAVAILABLE, e)

    // 없는 경로·메서드 요청이 아래 Exception 처리로 빠지면 500과 ERROR 로그가 남고, gateway 서킷 브레이커가 실패로 센다.
    // 없앤 API 를 계속 부르는 클라이언트(옛 `POST /photo` 는 404, `POST /admission-tickets/{id}` 는 405)도 여기서 걸린다.
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleApiNotFound(e: NoResourceFoundException) =
        respond(ErrorCode.API_NOT_FOUND, e)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException) =
        respond(ErrorCode.METHOD_NOT_ALLOWED, e, e.headers)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Document request failed [exception={}, stack={}]", e.javaClass.name, e.stackTrace.joinToString("\n"))
        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR))
    }

    private fun respond(
        errorCode: ErrorCode,
        e: Exception,
        headers: HttpHeaders = HttpHeaders.EMPTY,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Document request failed [code={}, exception={}]", errorCode.name, e.javaClass.name)
        return ResponseEntity.status(errorCode.status).headers(headers).body(ApiResponse.failure(errorCode))
    }
}
