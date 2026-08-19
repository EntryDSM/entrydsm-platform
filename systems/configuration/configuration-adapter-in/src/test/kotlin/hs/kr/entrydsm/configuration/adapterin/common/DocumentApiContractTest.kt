package hs.kr.entrydsm.configuration.adapterin.common

import hs.kr.entrydsm.configuration.adapterin.document.FileReferenceId
import hs.kr.entrydsm.configuration.adapterin.document.InvalidFileReferenceIdException
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.adapterin.document.requireExtension
import hs.kr.entrydsm.configuration.adapterin.document.toUploadCommand
import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.PresignFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUploadFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.multipart.MaxUploadSizeExceededException

class DocumentApiContractTest {

    private val handler = DocumentExceptionHandler()

    @Test
    fun `성공 응답은 data만 담고 error와 timestamp는 비운다`() {
        val response = ApiResponse.success("ok")

        assertTrue(response.success)
        assertEquals("ok", response.data)
        assertNull(response.error)
        assertNull(response.timestamp)
    }

    @Test
    fun `실패 응답은 코드 메시지 상태와 발생 시각을 담는다`() {
        val response = ApiResponse.failure(ErrorCode.FILE_NOT_FOUND)

        assertEquals(false, response.success)
        assertNull(response.data)
        assertEquals("FILE_NOT_FOUND", response.error?.code)
        assertEquals(ErrorCode.FILE_NOT_FOUND.message, response.error?.message)
        assertEquals(HttpStatus.NOT_FOUND.value(), response.error?.status)
        assertNotNull(response.timestamp)
    }

    @Test
    fun `실패 응답은 전달된 메시지로 기본 메시지를 대체한다`() {
        val response = ApiResponse.failure(ErrorCode.INVALID_REQUEST_PARAM, "receiptCode가 필요합니다.")

        assertEquals("receiptCode가 필요합니다.", response.error?.message)
    }

    @Test
    fun `오류 코드마다 HTTP 상태가 고정된다`() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST_PARAM.status)
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILE_FORMAT.status)
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND.status)
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.status)
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.STORAGE_UPLOAD_FAILED.status)
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.PRESIGN_FAILED.status)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.status)
    }

    @Test
    fun `도메인 예외를 정해진 상태와 오류 코드로 변환한다`() {
        assertMapped(
            ErrorCode.INVALID_FILE_FORMAT,
            handler.handleInvalidFileFormat(InvalidFileFormatException("a.exe", FileCategory.APPLICATION)),
        )
        assertMapped(
            ErrorCode.FILE_TOO_LARGE,
            handler.handleFileTooLarge(FileTooLargeException(20, 10)),
        )
        assertMapped(
            ErrorCode.FILE_TOO_LARGE,
            handler.handleFileTooLarge(MaxUploadSizeExceededException(10)),
        )
        assertMapped(
            ErrorCode.FILE_NOT_FOUND,
            handler.handleFileNotFound(FileDocumentNotFoundException("photo/none.jpg")),
        )
        assertMapped(
            ErrorCode.STORAGE_UPLOAD_FAILED,
            handler.handleStorageUploadFailed(StorageUploadFailedException("photo/a.jpg")),
        )
        assertMapped(
            ErrorCode.PRESIGN_FAILED,
            handler.handlePresignFailed(PresignFailedException("photo/a.jpg")),
        )
    }

    @Test
    fun `요청 오류 예외만 400으로 묶는다`() {
        assertMapped(
            ErrorCode.INVALID_REQUEST_PARAM,
            handler.handleInvalidRequestParam(InvalidFileNameException("../etc/passwd")),
        )
        assertMapped(
            ErrorCode.INVALID_REQUEST_PARAM,
            handler.handleInvalidRequestParam(InvalidFileReferenceIdException(FileCategory.ATTACHMENT, "123")),
        )
        assertMapped(
            ErrorCode.INVALID_REQUEST_PARAM,
            handler.handleInvalidRequestParam(MissingServletRequestParameterException("receiptCode", "String")),
        )
    }

    @Test
    fun `처리하지 못한 예외는 내부 메시지를 노출하지 않는다`() {
        val response = handler.handleUnexpected(IllegalStateException("jdbc://user:password@db"))

        assertMapped(ErrorCode.INTERNAL_SERVER_ERROR, response)
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.error?.message)
    }

    @Test
    fun `참조 ID는 카테고리 접두사를 붙이고 되돌린다`() {
        val referenceId = FileReferenceId.of(FileCategory.ATTACHMENT, 42)

        assertEquals("attachment_42", referenceId)
        assertEquals(42L, FileReferenceId.parse(FileCategory.ATTACHMENT, referenceId))
    }

    @Test(expected = InvalidFileReferenceIdException::class)
    fun `접두사가 없는 참조 ID는 거부한다`() {
        FileReferenceId.parse(FileCategory.ATTACHMENT, "42")
    }

    @Test(expected = InvalidFileReferenceIdException::class)
    fun `다른 카테고리 접두사의 참조 ID는 거부한다`() {
        FileReferenceId.parse(FileCategory.ATTACHMENT, "guideline_42")
    }

    @Test(expected = InvalidFileReferenceIdException::class)
    fun `숫자가 아닌 참조 ID는 거부한다`() {
        FileReferenceId.parse(FileCategory.ATTACHMENT, "attachment_abc")
    }

    @Test
    fun `업로드 파일의 확장자를 인식하고 커맨드로 변환한다`() {
        val file = MockMultipartFile("file", "증명사진.JPEG", null, ByteArray(3))

        assertEquals(FileExtension.JPG, file.requireExtension(FileCategory.PHOTO))

        val command = file.toUploadCommand(FileCategory.PHOTO, "photo_1.jpg")
        assertEquals(FileCategory.PHOTO, command.category)
        assertEquals("증명사진.JPEG", command.originalName)
        assertEquals("photo_1.jpg", command.fileName)
        assertEquals(3L, command.sizeBytes)
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `알 수 없는 확장자는 형식 오류로 거부한다`() {
        MockMultipartFile("file", "malware.exe", null, ByteArray(1))
            .requireExtension(FileCategory.ATTACHMENT)
    }

    @Test
    fun `응답 DTO는 도메인 값을 그대로 옮긴다`() {
        val fileDocument = FileDocument(
            id = 7,
            originalName = "지원서.pdf",
            objectKey = "application/application_1001.pdf",
            bucket = "entrydsm",
            contentType = "application/pdf",
            sizeBytes = 1024,
            checksum = "abc",
        )
        val uploaded = UploadFileResponse.from(fileDocument)

        assertEquals("application/application_1001.pdf", uploaded.key)
        assertEquals(fileDocument.fileName, uploaded.fileName)

        val downloadUrl = DownloadUrlResponse.from(
            DownloadUrl(fileName = "application_1001.pdf", downloadUrl = "https://s3/a", expiresIn = 300),
        )
        assertEquals("application_1001.pdf", downloadUrl.fileName)
        assertEquals("https://s3/a", downloadUrl.downloadUrl)
        assertEquals(300L, downloadUrl.expiresIn)
    }

    private fun assertMapped(
        expected: ErrorCode,
        response: org.springframework.http.ResponseEntity<ApiResponse<Nothing>>,
    ) {
        assertEquals(expected.status, response.statusCode)
        assertEquals(expected.name, response.body?.error?.code)
        assertEquals(expected.status.value(), response.body?.error?.status)
    }
}
