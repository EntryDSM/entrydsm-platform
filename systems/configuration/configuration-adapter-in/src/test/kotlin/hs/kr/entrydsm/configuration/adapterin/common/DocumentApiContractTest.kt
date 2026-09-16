package hs.kr.entrydsm.configuration.adapterin.common

import hs.kr.entrydsm.configuration.adapterin.document.dto.ApplicationFileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.PageResponse
import hs.kr.entrydsm.configuration.adapterin.document.toUploadCommand
import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    fun `공통 코드가 아닌 오류 코드는 도메인 접두사로 시작한다`() {
        val common = setOf("INVALID_REQUEST_PARAM", "AUTH_UNAUTHORIZED", "ACCESS_DENIED", "METHOD_NOT_ALLOWED", "INTERNAL_SERVER_ERROR")
        val domains = listOf("FILE_", "APPLICANT_", "APPLICATION_", "SCHEDULE_")

        ErrorCode.entries.filterNot { it.name in common }.forEach { code ->
            assertTrue(code.name, domains.any(code.name::startsWith))
        }
    }

    @Test
    fun `오류 코드마다 HTTP 상태가 고정된다`() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST_PARAM.status)
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.FILE_INVALID_FORMAT.status)
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FILE_ACCESS_DENIED.status)
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND.status)
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.status)
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.FILE_STORAGE_UNAVAILABLE.status)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.APPLICATION_SERVICE_UNAVAILABLE.status)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.status)
    }

    @Test
    fun `도메인 예외를 정해진 상태와 오류 코드로 변환한다`() {
        assertMapped(ErrorCode.FILE_INVALID_FORMAT, handler.handleInvalidFileFormat(InvalidFileFormatException("a.exe", FileCategory.PHOTO)))
        assertMapped(ErrorCode.FILE_TOO_LARGE, handler.handleFileTooLarge(FileTooLargeException(20, 10)))
        assertMapped(ErrorCode.FILE_TOO_LARGE, handler.handleFileTooLarge(MaxUploadSizeExceededException(10)))
        assertMapped(ErrorCode.FILE_NOT_FOUND, handler.handleFileNotFound(FileDocumentNotFoundException("photo_1")))
        assertMapped(ErrorCode.FILE_ACCESS_DENIED, handler.handleDocumentAccessDenied(DocumentAccessDeniedException()))
        assertMapped(ErrorCode.FILE_STORAGE_UNAVAILABLE, handler.handleStorageUnavailable(StorageUnavailableException("presign", "photo/a.jpg")))
        assertMapped(ErrorCode.APPLICANT_NOT_FOUND, handler.handleApplicantNotFound(ApplicantNotFoundException(12)))
        assertMapped(ErrorCode.APPLICATION_SERVICE_UNAVAILABLE, handler.handleApplicantLookupFailed(ApplicantLookupFailedException(12)))
    }

    @Test
    fun `요청 오류 예외만 400으로 묶는다`() {
        assertMapped(ErrorCode.INVALID_REQUEST_PARAM, handler.handleInvalidRequestParam(InvalidFileNameException("../etc/passwd")))
        assertMapped(ErrorCode.INVALID_REQUEST_PARAM, handler.handleInvalidRequestParam(MissingServletRequestParameterException("page", "int")))
        assertMapped(ErrorCode.INVALID_REQUEST_PARAM, handler.handleInvalidRequestParam(IllegalArgumentException("size")))
    }

    @Test
    fun `처리하지 못한 예외는 내부 메시지를 노출하지 않는다`() {
        val response = handler.handleUnexpected(IllegalStateException("jdbc://user:password@db"))

        assertMapped(ErrorCode.INTERNAL_SERVER_ERROR, response)
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.error?.message)
    }

    @Test
    fun `업로드 파일을 올린 이름과 크기로 커맨드로 바꾼다`() {
        val requester = Requester(10, Requester.Role.STUDENT)
        val command = MockMultipartFile("file", "증명사진.JPEG", null, ByteArray(3)).toUploadCommand(FileCategory.PHOTO, requester)

        assertEquals(FileCategory.PHOTO, command.category)
        assertEquals("증명사진.JPEG", command.originalName)
        assertEquals(3L, command.sizeBytes)
        assertEquals(requester, command.requester)
    }

    @Test
    fun `파일 응답은 공개 ID 파일이면 ID와 올린 이름을, 지원자 파일이면 저장 이름을 담고 키는 담지 않는다`() {
        val file = DownloadableFile(document(), downloadUrl = "https://s3/a", expiresIn = 300)

        assertEquals(FileResponse("attachment_3f2c", "공지.pdf", 1024, "https://s3/a", 300), FileResponse.of(file))
        assertEquals(FileResponse(null, "3f2c_notice.pdf", 1024, "https://s3/a", 300), FileResponse.ofApplicant(file))
        assertEquals(ApplicationFileResponse(exists = false), ApplicationFileResponse.of(null))
        assertEquals(ApplicationFileResponse(true, "3f2c_notice.pdf", 1024, "https://s3/a", 300), ApplicationFileResponse.of(file))
    }

    @Test
    fun `목록 응답은 전체 개수로 전체 페이지 수를 올림해 센다`() {
        assertEquals(3, PageResponse.of(listOf("a"), page = 1, size = 10, totalElements = 21).totalPages)
        assertEquals(2, PageResponse.of(listOf("a"), page = 1, size = 10, totalElements = 20).totalPages)
        assertEquals(0, PageResponse.of(emptyList<String>(), page = 1, size = 10, totalElements = 0).totalPages)
    }

    private fun document() = FileDocument(
        id = 7,
        publicId = "attachment_3f2c",
        originalName = "공지.pdf",
        objectKey = "dsm_Entry/Backend/attachment/3f2c_notice.pdf",
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 1024,
        checksum = "abc",
    )

    private fun assertMapped(expected: ErrorCode, response: ResponseEntity<ApiResponse<Nothing>>) {
        assertEquals(expected.status, response.statusCode)
        assertEquals(expected.name, response.body?.error?.code)
        assertEquals(expected.status.value(), response.body?.error?.status)
    }
}
