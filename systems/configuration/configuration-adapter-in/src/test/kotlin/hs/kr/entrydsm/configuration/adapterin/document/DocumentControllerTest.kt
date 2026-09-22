package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.DocumentExceptionHandler
import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.FilePage
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.FileUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.InputStream
import java.time.Instant

class DocumentControllerTest {

    private val applicantFiles = RecordingApplicantFileUseCase()
    private val files = RecordingFileUseCase()

    private val mvc: MockMvc =
        MockMvcBuilders.standaloneSetup(ApplicantFileController(applicantFiles), FileController(files))
            .addInterceptors(ConfigurationAuthorizationInterceptor())
            .setControllerAdvice(DocumentExceptionHandler())
            .build()

    @Test
    fun `원서는 GET으로 요청자 본인 것을 만들고 key 없이 서명 URL을 준다`() {
        mvc.perform(get("/api/document/v11/applications").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileName").value("application_0012.pdf"))
            .andExpect(jsonPath("$.data.size").value(7))
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/dsm_Entry/backend/stag/application/application_0012.pdf"))
            .andExpect(jsonPath("$.data.expiresIn").value(300))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.key").doesNotExist())

        assertEquals(Requester(10, Requester.Role.STUDENT), applicantFiles.formRequester)
    }

    @Test
    fun `원서는 경로의 applicantId로도 만든다 - 관리자가 지원자를 지목한다`() {
        mvc.perform(get("/api/document/v11/applications/12").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("application_0012.pdf"))
            .andExpect(jsonPath("$.data.id").doesNotExist())

        assertEquals(12L to Requester(1, Requester.Role.ADMIN), applicantFiles.formGenerated)
    }

    @Test
    fun `원서를 받을 권한이 없으면 403이다`() {
        applicantFiles.denied = true

        mvc.perform(get("/api/document/v11/applications/12").with(student(11)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FILE_ACCESS_DENIED"))
    }

    @Test
    fun `멀티파트가 아니거나 파일 파트가 없으면 400이다`() {
        mvc.perform(post("/api/document/v11/photos").content("{}").with(student(10)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))

        mvc.perform(multipart("/api/document/v11/photos").with(student(10)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `수험표는 GET으로 만들고 서명 URL을 준다, 올리는 요청은 405다`() {
        mvc.perform(get("/api/document/v11/admission-tickets/12").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fileName").value("admission_ticket_0012.pdf"))
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/dsm_Entry/backend/stag/admission-ticket/admission_ticket_0012.pdf"))
            .andExpect(jsonPath("$.data.id").doesNotExist())

        assertEquals(12L to Requester(10, Requester.Role.STUDENT), applicantFiles.generated)

        mvc.perform(multipart("/api/document/v11/admission-tickets/12").file(pdf()).with(admin()))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `지원자 ID가 숫자가 아니면 400이다`() {
        mvc.perform(get("/api/document/v11/admission-tickets/abc").with(admin()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `게이트웨이 헤더가 없거나 id가 숫자가 아니면 401, 모르는 역할이면 403으로 거부한다`() {
        mvc.perform(get("/api/document/v11/guidelines"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))

        mvc.perform(get("/api/document/v11/guidelines").header("X-User-Id", "user_1").header("X-User-Role", "STUDENT"))
            .andExpect(status().isUnauthorized)

        mvc.perform(get("/api/document/v11/guidelines").header("X-User-Id", "9").header("X-User-Role", "GUEST"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `파일 권한이 없다는 판정은 403 FILE_ACCESS_DENIED다`() {
        files.denied = true

        mvc.perform(get("/api/document/v11/photos/photo_3f2c").with(student(11)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("FILE_ACCESS_DENIED"))
    }

    @Test
    fun `증명사진·첨부·요강 적재는 종류와 올린 파일명을 넘기고 공개 ID와 서명 URL을 준다`() {
        mvc.perform(multipart("/api/document/v11/photos").file(MockMultipartFile("file", "사진.png", null, ByteArray(2))).with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value("photo_3f2c"))
            .andExpect(jsonPath("$.data.fileName").value("사진.png"))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/dsm_Entry/backend/stag/photo/photo_3f2c.png"))
            .andExpect(jsonPath("$.data.expiresIn").value(300))
        assertEquals(UploadFileCommand(FileCategory.PHOTO, "사진.png", 2, Requester(10, Requester.Role.STUDENT)), files.lastCommand)

        mvc.perform(multipart("/api/document/v11/attachments").file(pdf("공지.pdf")).with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value("attachment_3f2c"))
        assertEquals(FileCategory.ATTACHMENT, files.lastCommand?.category)

        mvc.perform(multipart("/api/document/v11/guidelines").file(pdf("요강.pdf")).with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value("guideline_3f2c"))
        assertEquals(FileCategory.GUIDELINE, files.lastCommand?.category)
    }

    @Test
    fun `공개 ID 조회는 경로의 ID와 종류를 넘긴다`() {
        listOf(
            "/api/document/v11/photos/photo_3f2c" to FileCategory.PHOTO,
            "/api/document/v11/attachments/attachment_3f2c" to FileCategory.ATTACHMENT,
            "/api/document/v11/guidelines/guideline_3f2c" to FileCategory.GUIDELINE,
        ).forEach { (path, category) ->
            mvc.perform(get(path).with(student(10)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.id").value("${category.prefix}_3f2c"))
            assertEquals(category to path.substringAfterLast('/'), files.lastFound)
        }
    }

    @Test
    fun `없는 파일을 조회하면 404를 돌려준다`() {
        files.notFound = true

        mvc.perform(get("/api/document/v11/attachments/attachment_1").with(student(10)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
    }

    @Test
    fun `첨부·요강 삭제는 본문 없이 204다`() {
        mvc.perform(delete("/api/document/v11/attachments/attachment_3f2c").with(admin()))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
        assertEquals(FileCategory.ATTACHMENT to "attachment_3f2c", files.lastDeleted)

        mvc.perform(delete("/api/document/v11/guidelines/guideline_3f2c").with(admin()))
            .andExpect(status().isNoContent)
        assertEquals(FileCategory.GUIDELINE to "guideline_3f2c", files.lastDeleted)
    }

    @Test
    fun `요강 목록은 공통 목록 응답으로 주고, 페이지 범위를 벗어나면 400이다`() {
        files.totalElements = 3

        mvc.perform(get("/api/document/v11/guidelines").param("page", "2").param("size", "2").with(student(10)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id").value("guideline_3f2c"))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
        assertEquals(2 to 2, files.lastPage)

        mvc.perform(get("/api/document/v11/guidelines").with(student(10)))
            .andExpect(status().isOk)
        assertEquals(1 to 10, files.lastPage)

        listOf("page" to "0", "size" to "0", "size" to "101").forEach { (name, value) ->
            mvc.perform(get("/api/document/v11/guidelines").param(name, value).with(student(10)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
        }
    }

    @Test
    fun `없앤 경로의 요청 방식은 405다`() {
        mvc.perform(post("/api/document/v11/applications").with(student(10)))
            .andExpect(status().isMethodNotAllowed)
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

    private class RecordingApplicantFileUseCase : ApplicantFileUseCase {
        var formRequester: Requester? = null
        var formGenerated: Pair<Long, Requester>? = null
        var generated: Pair<Long, Requester>? = null
        var denied = false

        override fun generateApplicationForm(requester: Requester): DownloadableFile {
            formRequester = requester
            if (denied) throw DocumentAccessDeniedException()
            return downloadable(FileCategory.APPLICATION.objectKeyOf(FileNaming.applicationFileName(12)))
        }

        override fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile {
            formGenerated = applicantId to requester
            if (denied) throw DocumentAccessDeniedException()
            return downloadable(FileCategory.APPLICATION.objectKeyOf(FileNaming.applicationFileName(applicantId)))
        }

        override fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile {
            generated = applicantId to requester
            return downloadable(FileCategory.ADMISSION_TICKET.objectKeyOf(FileNaming.admissionTicketFileName(applicantId)))
        }

        override fun renderAdmissionTicket(applicantId: Long, examineeNumber: String?): ByteArray =
            error("REST 가 부르지 않는다")
    }

    private class RecordingFileUseCase : FileUseCase {
        var lastCommand: UploadFileCommand? = null
        var lastFound: Pair<FileCategory, String>? = null
        var lastDeleted: Pair<FileCategory, String>? = null
        var lastPage: Pair<Int, Int>? = null
        var totalElements = 0L
        var denied = false
        var notFound = false

        override fun upload(command: UploadFileCommand, content: InputStream): DownloadableFile {
            lastCommand = command
            val file = downloadable(command.category.objectKeyOf("${command.category.prefix}_3f2c.png"), command.sizeBytes)
            return file.copy(document = file.document.copy(originalName = command.originalName))
        }

        override fun find(category: FileCategory, publicId: String, requester: Requester): DownloadableFile {
            lastFound = category to publicId
            if (denied) throw DocumentAccessDeniedException()
            if (notFound) throw FileDocumentNotFoundException(publicId)
            return downloadable(category.objectKeyOf("${category.prefix}_3f2c.pdf"))
        }

        override fun findPage(category: FileCategory, page: Int, size: Int, requester: Requester): FilePage {
            lastPage = page to size
            return FilePage(listOf(downloadable(category.objectKeyOf("guideline_3f2c.pdf"))), totalElements)
        }

        override fun delete(category: FileCategory, publicId: String, requester: Requester) {
            lastDeleted = category to publicId
        }
    }

    private companion object {
        fun downloadable(objectKey: String, sizeBytes: Long = 7) = DownloadableFile(
            document = FileDocument(
                id = 7,
                publicId = "${FileCategory.entries.first { it.holds(objectKey) }.prefix}_3f2c",
                originalName = "원본.pdf",
                objectKey = objectKey,
                bucket = "entrydsm",
                contentType = "application/pdf",
                sizeBytes = sizeBytes,
                checksum = "abc",
                ownerUserId = 10,
                createdAt = Instant.parse("2026-02-01T00:00:00Z"),
            ),
            downloadUrl = "https://s3/$objectKey",
            expiresIn = 300,
        )
    }
}
