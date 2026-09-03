package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.DocumentExceptionHandler
import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DocumentControllerTest {

    private val upload = RecordingUploadFileUseCase()
    private val issue = StubIssueDownloadUrlUseCase()
    private val read = StubReadFileUseCase()

    private fun mockMvc(controller: Any): MockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(DocumentExceptionHandler())
            .build()

    private fun pdf(name: String = "지원서.pdf") =
        MockMultipartFile("file", name, null, "content".toByteArray())

    @Test
    fun `지원서 업로드는 수험번호 기반 파일명으로 저장한다`() {
        mockMvc(applicationController())
            .perform(multipart("/api/document/v11/application").file(pdf()).param("receiptCode", "1001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.pdf"))
            .andExpect(jsonPath("$.data.key").value("dsm_Entry/Backend/application/application_1001.pdf"))

        assertEquals(FileCategory.APPLICATION, upload.lastCommand?.category)
        assertEquals("application_1001.pdf", upload.lastCommand?.fileName)
        assertEquals("지원서.pdf", upload.lastCommand?.originalName)
    }

    @Test
    fun `지원서 업로드는 허용하지 않는 형식을 400으로 거부한다`() {
        mockMvc(applicationController())
            .perform(
                multipart("/api/document/v11/application")
                    .file(MockMultipartFile("file", "지원서.jpg", null, ByteArray(1)))
                    .param("receiptCode", "1001"),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_FILE_FORMAT"))
    }

    @Test
    fun `지원서 조회는 같은 수험번호의 여러 형식 중 최근 업로드본을 돌려준다`() {
        read.put(stored("dsm_Entry/Backend/application/application_1001.pdf", Instant.parse("2026-01-01T00:00:00Z")))
        read.put(stored("dsm_Entry/Backend/application/application_1001.hwp", Instant.parse("2026-02-01T00:00:00Z")))

        mockMvc(applicationController())
            .perform(get("/api/document/v11/application").param("receiptCode", "1001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.exists").value(true))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.hwp"))
    }

    @Test
    fun `지원서가 없으면 pdf 기본 파일명과 미존재 표시를 돌려준다`() {
        mockMvc(applicationController())
            .perform(get("/api/document/v11/application").param("receiptCode", "1001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.exists").value(false))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.pdf"))
            .andExpect(jsonPath("$.data.key").value("dsm_Entry/Backend/application/application_1001.pdf"))
    }

    @Test
    fun `지원서 다운로드는 요청한 형식의 파일로 URL을 발급한다`() {
        mockMvc(applicationController())
            .perform(
                get("/api/document/v11/application/download")
                    .param("receiptCode", "1001")
                    .param("format", "hwp"),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("application_1001.hwp"))
            .andExpect(jsonPath("$.data.expiresIn").value(300))

        assertEquals("application_1001.hwp", issue.lastCommand?.fileName)
    }

    @Test
    fun `지원하지 않는 다운로드 형식은 400으로 거부한다`() {
        mockMvc(applicationController())
            .perform(
                get("/api/document/v11/application/download")
                    .param("receiptCode", "1001")
                    .param("format", "jpg"),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_FILE_FORMAT"))
    }

    @Test
    fun `수험표 다운로드는 수험번호 기반 파일명을 사용한다`() {
        mockMvc(AdmissionTicketController(upload, issue))
            .perform(get("/api/document/v11/admission-ticket/download").param("receiptCode", "1001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("admission_ticket_1001.pdf"))

        assertEquals(FileCategory.ADMISSION_TICKET, issue.lastCommand?.category)
    }

    @Test
    fun `수험번호에 경로 문자가 들어오면 400으로 거부한다`() {
        mockMvc(AdmissionTicketController(upload, issue))
            .perform(get("/api/document/v11/admission-ticket/download").param("receiptCode", "../1001"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `지원자 명단은 파일명을 주지 않으면 오늘 날짜로 저장한다`() {
        val expected = "applicants_${DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())}.xlsx"

        mockMvc(ApplicantListController(upload, issue))
            .perform(multipart("/api/document/v11/applicant-list").file(xlsx()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value(expected))
    }

    @Test
    fun `지원자 명단은 지정한 xlsx 파일명을 그대로 쓴다`() {
        mockMvc(ApplicantListController(upload, issue))
            .perform(
                multipart("/api/document/v11/applicant-list")
                    .file(xlsx())
                    .param("fileName", "applicants_20260726.xlsx"),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("applicants_20260726.xlsx"))
    }

    @Test
    fun `지원자 명단은 xlsx가 아닌 파일명을 400으로 거부한다`() {
        mockMvc(ApplicantListController(upload, issue))
            .perform(
                multipart("/api/document/v11/applicant-list")
                    .file(xlsx())
                    .param("fileName", "applicants.csv"),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_FILE_FORMAT"))
    }

    @Test
    fun `첨부파일 업로드는 카테고리 접두사가 붙은 ID를 돌려준다`() {
        mockMvc(AttachmentController(upload, issue))
            .perform(multipart("/api/document/v11/attachment").file(pdf("첨부.pdf")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.attachmentId").value("attachment_7"))
            .andExpect(jsonPath("$.data.fileName").value("첨부.pdf"))
            .andExpect(jsonPath("$.data.size").value(7))
    }

    @Test
    fun `첨부파일 다운로드는 접두사가 붙은 ID만 받는다`() {
        val mvc = mockMvc(AttachmentController(upload, issue))

        mvc.perform(get("/api/document/v11/attachment/download").param("attachmentId", "attachment_7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/id/7"))

        mvc.perform(get("/api/document/v11/attachment/download").param("attachmentId", "7"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `입학요강 다운로드는 guideline 접두사 ID를 파싱한다`() {
        mockMvc(GuidelineController(issue))
            .perform(get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/id/3"))
    }

    @Test
    fun `증명사진 업로드는 저장 직후 다운로드 URL을 함께 돌려준다`() {
        mockMvc(PhotoController(upload, issue))
            .perform(multipart("/api/document/v11/photo").file(MockMultipartFile("file", "사진.png", null, ByteArray(2))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").value("https://s3/photo"))

        assertEquals(FileCategory.PHOTO, upload.lastCommand?.category)
        assert(upload.lastCommand!!.fileName.matches(Regex("photo_[0-9a-f]{32}\\.png")))
    }

    @Test
    fun `없는 파일을 조회하면 404를 돌려준다`() {
        issue.notFound = true

        mockMvc(GuidelineController(issue))
            .perform(get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
    }

    private fun applicationController() = ApplicationFileController(upload, issue, read)

    private fun xlsx() = MockMultipartFile("file", "명단.xlsx", null, ByteArray(1))

    private fun stored(objectKey: String, createdAt: Instant) = FileDocument(
        id = 7,
        originalName = "지원서",
        objectKey = objectKey,
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 7,
        checksum = "abc",
        createdAt = createdAt,
    )

    private class RecordingUploadFileUseCase : UploadFileUseCase {
        var lastCommand: UploadFileCommand? = null

        override fun upload(command: UploadFileCommand, content: InputStream): FileDocument {
            lastCommand = command
            return FileDocument(
                id = 7,
                originalName = command.originalName,
                objectKey = command.category.objectKeyOf(command.fileName),
                bucket = "entrydsm",
                contentType = "application/octet-stream",
                sizeBytes = command.sizeBytes,
                checksum = "abc",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        }
    }

    private class StubIssueDownloadUrlUseCase : IssueDownloadUrlUseCase {
        var lastCommand: IssueDownloadUrlCommand? = null
        var notFound = false

        override fun issueByCommand(command: IssueDownloadUrlCommand): DownloadUrl {
            lastCommand = command
            if (notFound) throw FileDocumentNotFoundException(command.fileName)
            return DownloadUrl(command.fileName, "https://s3/photo", 300)
        }

        override fun issueById(id: Long): DownloadUrl {
            if (notFound) throw FileDocumentNotFoundException("id=$id")
            return DownloadUrl("file_$id.pdf", "https://s3/id/$id", 300)
        }
    }

    private class StubReadFileUseCase : ReadFileUseCase {
        private val byObjectKey = mutableMapOf<String, FileDocument>()

        fun put(fileDocument: FileDocument) {
            byObjectKey[fileDocument.objectKey] = fileDocument
        }

        override fun findById(id: Long): FileDocument = throw FileDocumentNotFoundException("id=$id")

        override fun findByFileName(category: FileCategory, fileName: String): FileDocument? =
            byObjectKey[category.objectKeyOf(fileName)]

        override fun existsById(id: Long): Boolean = false
    }
}
