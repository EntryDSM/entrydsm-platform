package hs.kr.entrydsm.configuration.adapterin.common

import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleAccessDeniedException
import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleUnauthorizedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.domain.schedule.ScheduleNotFoundException
import org.slf4j.LoggerFactory
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

@RestControllerAdvice
class DocumentExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidFileFormatException::class)
    fun handleInvalidFileFormat(e: InvalidFileFormatException) =
        respond(ErrorCode.FILE_INVALID_FORMAT, e)

    @ExceptionHandler(FileTooLargeException::class, MaxUploadSizeExceededException::class)
    fun handleFileTooLarge(e: Exception) =
        respond(ErrorCode.FILE_TOO_LARGE, e)

    @ExceptionHandler(FileDocumentNotFoundException::class)
    fun handleFileNotFound(e: FileDocumentNotFoundException) =
        respond(ErrorCode.FILE_NOT_FOUND, e)

    @ExceptionHandler(ApplicantNotFoundException::class)
    fun handleApplicantNotFound(e: ApplicantNotFoundException) =
        respond(ErrorCode.APPLICANT_NOT_FOUND, e)

    @ExceptionHandler(ApplicantLookupFailedException::class)
    fun handleApplicantLookupFailed(e: ApplicantLookupFailedException) =
        respond(ErrorCode.APPLICATION_SERVICE_UNAVAILABLE, e)

    @ExceptionHandler(ScheduleNotFoundException::class)
    fun handleScheduleNotFound(e: ScheduleNotFoundException) =
        respond(ErrorCode.SCHEDULE_NOT_FOUND, e)

    @ExceptionHandler(ScheduleUnauthorizedException::class)
    fun handleUnauthorized(e: ScheduleUnauthorizedException) =
        respond(ErrorCode.AUTH_UNAUTHORIZED, e)

    @ExceptionHandler(ScheduleAccessDeniedException::class)
    fun handleAccessDenied(e: ScheduleAccessDeniedException) =
        respond(ErrorCode.ACCESS_DENIED, e)

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

    /** 없앤 API(예: 수험표 업로드 POST)를 부르는 클라이언트를 500 으로 셈하지 않도록 405 로 돌려준다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException) =
        respond(ErrorCode.METHOD_NOT_ALLOWED, e)

    @ExceptionHandler(StorageUnavailableException::class)
    fun handleStorageUnavailable(e: StorageUnavailableException) =
        respond(ErrorCode.FILE_STORAGE_UNAVAILABLE, e)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception", e)
        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR))
    }

    private fun respond(errorCode: ErrorCode, e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("{}: {}", errorCode.name, e.message)
        return ResponseEntity.status(errorCode.status).body(ApiResponse.failure(errorCode))
    }
}
