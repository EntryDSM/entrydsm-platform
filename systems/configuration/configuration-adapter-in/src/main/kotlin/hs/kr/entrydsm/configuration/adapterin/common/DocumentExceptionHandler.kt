package hs.kr.entrydsm.configuration.adapterin.common

import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.PresignFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUploadFailedException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class DocumentExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidFileFormatException::class)
    fun handleInvalidFileFormat(e: InvalidFileFormatException) =
        respond(ErrorCode.INVALID_FILE_FORMAT, e)

    @ExceptionHandler(FileTooLargeException::class, MaxUploadSizeExceededException::class)
    fun handleFileTooLarge(e: Exception) =
        respond(ErrorCode.FILE_TOO_LARGE, e)

    @ExceptionHandler(FileDocumentNotFoundException::class)
    fun handleFileNotFound(e: FileDocumentNotFoundException) =
        respond(ErrorCode.FILE_NOT_FOUND, e)

    @ExceptionHandler(
        InvalidFileNameException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentNotValidException::class,
        IllegalArgumentException::class,
    )
    fun handleInvalidRequestParam(e: Exception) =
        respond(ErrorCode.INVALID_REQUEST_PARAM, e)

    @ExceptionHandler(StorageUploadFailedException::class)
    fun handleStorageUploadFailed(e: StorageUploadFailedException) =
        respond(ErrorCode.STORAGE_UPLOAD_FAILED, e)

    @ExceptionHandler(PresignFailedException::class)
    fun handlePresignFailed(e: PresignFailedException) =
        respond(ErrorCode.PRESIGN_FAILED, e)

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
