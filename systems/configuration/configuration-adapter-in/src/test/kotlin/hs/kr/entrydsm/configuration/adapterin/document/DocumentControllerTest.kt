package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.DocumentExceptionHandler
import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.GenerateAdmissionTicketUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.RequestPostProcessor
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
    private var generated: Pair<String, Requester>? = null
    private val generate = GenerateAdmissionTicketUseCase { receiptCode, requester ->
        generated = receiptCode to requester
        stored(FileCategory.ADMISSION_TICKET.objectKeyOf("admission_ticket_$receiptCode.pdf"))
    }

    private val mvc: MockMvc =
        MockMvcBuilders.standaloneSetup(DocumentUploadController(upload, issue, generate), DocumentDownloadController(issue, read))
            .addInterceptors(ConfigurationAuthorizationInterceptor())
            .setControllerAdvice(DocumentExceptionHandler())
            .build()

    @Test
    fun `지원서 업로드는 수험번호 기반 파일명으로 적재하고 요청자와 수험번호를 넘긴다`() {
        mvc.perform(multipart("/api/document/v11/application").file(pdf()).param("receiptCode", "1001").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.pdf"))
            .andExpect(jsonPath("$.data.key").value("dsm_Entry/Backend/application/application_1001.pdf"))

        val command = upload.lastCommand!!
        assertEquals(FileCategory.APPLICATION, command.category)
        assertEquals("지원서.pdf", command.originalName)
        assertEquals(Requester(10, Requester.Role.STUDENT), command.requester)
        assertEquals("1001", command.receiptCode)
    }

    @Test
    fun `지원서 업로드는 허용하지 않는 형식을 400으로 거부한다`() {
        mvc.perform(
            multipart("/api/document/v11/application")
                .file(MockMultipartFile("file", "지원서.jpg", null, ByteArray(1)))
                .param("receiptCode", "1001")
                .with(admin()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_FILE_FORMAT"))
    }

    @Test
    fun `게이트웨이 헤더가 없거나 id가 숫자가 아니면 401, 모르는 역할이면 403으로 거부한다`() {
        mvc.perform(get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))

        mvc.perform(
            get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3")
                .header("X-User-Id", "user_1").header("X-User-Role", "STUDENT"),
        ).andExpect(status().isUnauthorized)

        mvc.perform(
            get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3")
                .header("X-User-Id", "9").header("X-User-Role", "GUEST"),
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `적재·다운로드 권한이 없다는 판정은 403으로 돌려준다`() {
        issue.denied = true

        mvc.perform(get("/api/document/v11/applicant-list/download").param("fileName", "applicants_20260726.xlsx").with(student(10)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `지원서 조회는 서비스가 찾은 최근 적재본을 돌려준다`() {
        read.application = stored("dsm_Entry/Backend/application/application_1001.hwp")

        mvc.perform(get("/api/document/v11/application").param("receiptCode", "1001").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.exists").value(true))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.hwp"))

        assertEquals(Requester(10, Requester.Role.STUDENT), read.lastRequester)
    }

    @Test
    fun `지원서가 없으면 pdf 기본 파일명과 미존재 표시를 돌려준다`() {
        mvc.perform(get("/api/document/v11/application").param("receiptCode", "1001").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.exists").value(false))
            .andExpect(jsonPath("$.data.fileName").value("application_1001.pdf"))
            .andExpect(jsonPath("$.data.key").value("dsm_Entry/Backend/application/application_1001.pdf"))
    }

    @Test
    fun `지원서 다운로드는 요청한 형식의 파일명과 수험번호로 URL을 발급한다`() {
        mvc.perform(
            get("/api/document/v11/application/download")
                .param("receiptCode", "1001")
                .param("format", "hwp")
                .with(student(10)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("application_1001.hwp"))
            .andExpect(jsonPath("$.data.expiresIn").value(300))

        assertEquals(
            IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.hwp", Requester(10, Requester.Role.STUDENT), "1001"),
            issue.lastCommand,
        )
    }

    @Test
    fun `지원하지 않는 다운로드 형식은 400으로 거부한다`() {
        mvc.perform(
            get("/api/document/v11/application/download")
                .param("receiptCode", "1001")
                .param("format", "jpg")
                .with(admin()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `수험표 생성은 수험번호와 요청자를 넘기고 적재된 키를 돌려준다`() {
        mvc.perform(get("/api/document/v11/admission-ticket").param("receiptCode", "1001").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.key").value("dsm_Entry/Backend/admission-ticket/admission_ticket_1001.pdf"))
            .andExpect(jsonPath("$.data.fileName").value("admission_ticket_1001.pdf"))

        assertEquals("1001" to Requester(10, Requester.Role.STUDENT), generated)

        mvc.perform(multipart("/api/document/v11/admission-ticket").file(pdf()).param("receiptCode", "1001").with(admin()))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `수험표 다운로드는 수험번호 기반 파일명을 사용한다`() {
        mvc.perform(get("/api/document/v11/admission-ticket/download").param("receiptCode", "1001").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("admission_ticket_1001.pdf"))

        assertEquals(FileCategory.ADMISSION_TICKET, issue.lastCommand?.category)
        assertEquals("1001", issue.lastCommand?.receiptCode)
    }

    @Test
    fun `수험번호에 경로 문자가 들어오면 400으로 거부한다`() {
        mvc.perform(get("/api/document/v11/admission-ticket/download").param("receiptCode", "../1001").with(admin()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `지원자 명단은 파일명을 주지 않으면 오늘 날짜로 적재한다`() {
        val expected = "applicants_${DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())}.xlsx"

        mvc.perform(multipart("/api/document/v11/applicant-list").file(xlsx()).with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value(expected))
    }

    @Test
    fun `지원자 명단은 지정한 xlsx 파일명을 그대로 쓴다`() {
        mvc.perform(
            multipart("/api/document/v11/applicant-list")
                .file(xlsx())
                .param("fileName", "applicants_20260726.xlsx")
                .with(admin()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("applicants_20260726.xlsx"))
    }

    @Test
    fun `지원자 명단은 xlsx가 아닌 파일명을 400으로 거부한다`() {
        mvc.perform(
            multipart("/api/document/v11/applicant-list")
                .file(xlsx())
                .param("fileName", "applicants.csv")
                .with(admin()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_FILE_FORMAT"))
    }

    @Test
    fun `첨부파일 업로드는 카테고리 접두사가 붙은 ID를 돌려준다`() {
        mvc.perform(multipart("/api/document/v11/attachment").file(pdf("첨부.pdf")).with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.attachmentId").value("attachment_7"))
            .andExpect(jsonPath("$.data.fileName").value("첨부.pdf"))
            .andExpect(jsonPath("$.data.size").value(7))
    }

    @Test
    fun `첨부파일 다운로드는 접두사가 붙은 ID만 받는다`() {
        mvc.perform(get("/api/document/v11/attachment/download").param("attachmentId", "attachment_7").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/ATTACHMENT/7"))

        mvc.perform(get("/api/document/v11/attachment/download").param("attachmentId", "7").with(student(10)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `입학요강 적재는 guideline 접두사가 붙은 ID를 돌려준다`() {
        mvc.perform(multipart("/api/document/v11/guideline").file(pdf("2027_요강.pdf")).with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.guidelineId").value("guideline_7"))
            .andExpect(jsonPath("$.data.fileName").value("2027_요강.pdf"))

        assertEquals(FileCategory.GUIDELINE, upload.lastCommand?.category)
        assert(upload.lastCommand!!.fileName.matches(Regex("[0-9a-f]{32}_2027___\\.pdf")))
    }

    @Test
    fun `입학요강 다운로드는 guideline 접두사 ID를 요강 종류로 조회한다`() {
        mvc.perform(get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/GUIDELINE/3"))
    }

    @Test
    fun `증명사진 업로드는 적재 직후 올린 학생에게 다운로드 URL을 함께 돌려준다`() {
        mvc.perform(multipart("/api/document/v11/photo").file(MockMultipartFile("file", "사진.png", null, ByteArray(2))).with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").value("https://s3/photo"))

        assertEquals(FileCategory.PHOTO, upload.lastCommand?.category)
        assert(upload.lastCommand!!.fileName.matches(Regex("photo_[0-9a-f]{32}\\.png")))
        assertEquals(Requester(10, Requester.Role.STUDENT), issue.lastCommand?.requester)
    }

    @Test
    fun `없는 파일을 조회하면 404를 돌려준다`() {
        issue.notFound = true

        mvc.perform(get("/api/document/v11/guideline/download").param("guidelineId", "guideline_3").with(student(10)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
    }

    private fun admin() = gatewayHeaders(1, "ADMIN")

    private fun student(userId: Long) = gatewayHeaders(userId, "STUDENT")

    private fun gatewayHeaders(userId: Long, role: String) = RequestPostProcessor { request ->
        request.apply {
            addHeader("X-User-Id", userId.toString())
            addHeader("X-User-Role", role)
        }
    }

    private fun pdf(name: String = "지원서.pdf") =
        MockMultipartFile("file", name, null, "content".toByteArray())

    private fun xlsx() = MockMultipartFile("file", "명단.xlsx", null, ByteArray(1))

    private fun stored(objectKey: String) = FileDocument(
        id = 7,
        originalName = "지원서",
        objectKey = objectKey,
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 7,
        checksum = "abc",
        ownerUserId = 10,
        createdAt = Instant.parse("2026-02-01T00:00:00Z"),
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
        var denied = false

        override fun issueByCommand(command: IssueDownloadUrlCommand): DownloadUrl {
            lastCommand = command
            if (denied) throw DocumentAccessDeniedException()
            if (notFound) throw FileDocumentNotFoundException(command.fileName)
            return DownloadUrl(command.fileName, "https://s3/photo", 300)
        }

        override fun issueById(category: FileCategory, id: Long, requester: Requester): DownloadUrl {
            if (notFound) throw FileDocumentNotFoundException("id=$id")
            return DownloadUrl("file_$id.pdf", "https://s3/${category.name}/$id", 300)
        }
    }

    private class StubReadFileUseCase : ReadFileUseCase {
        var application: FileDocument? = null
        var lastRequester: Requester? = null

        override fun findById(id: Long): FileDocument = throw FileDocumentNotFoundException("id=$id")

        override fun findApplication(receiptCode: String, requester: Requester): FileDocument? {
            lastRequester = requester
            return application
        }

        override fun existsById(id: Long): Boolean = false
    }
}
