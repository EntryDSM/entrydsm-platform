package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

class FileDocumentServiceTest {

    private val storage = FakeStoragePort()
    private val repository = FakeFileDocumentRepository()
    private val applicants = mutableMapOf(APPLICANT_ID to applicant())
    private val pdf = RecordingPdfRenderPort()
    private val service = FileDocumentService(
        storage, repository, presignExpirySeconds = 300,
        applicantPort = ApplicantPort(applicants::get), pdfRenderPort = pdf, admissionYear = 2027,
    )

    private val admin = Requester(1, Requester.Role.ADMIN)

    @Test
    fun `원서는 지원자 ID 파일명으로 적재하고 서명 URL을 붙여 돌려준다`() {
        val uploaded = service.uploadApplication(APPLICANT_ID, application(student(STUDENT_ID)), content())

        assertEquals("dsm_Entry/Backend/application/application_12.pdf", uploaded.document.objectKey)
        assertEquals("지원서.pdf", uploaded.document.originalName)
        assertEquals(STUDENT_ID, uploaded.document.ownerUserId)
        assertEquals("https://s3/dsm_Entry/Backend/application/application_12.pdf?expires=300", uploaded.downloadUrl)
        assertEquals(300L, uploaded.expiresIn)
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `카테고리가 허용하지 않는 확장자는 거부한다`() {
        service.uploadApplication(APPLICANT_ID, application(admin, originalName = "사진.jpg"), content())
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `확장자가 없으면 거부한다`() {
        service.uploadApplication(APPLICANT_ID, application(admin, originalName = "지원서"), content())
    }

    @Test(expected = FileTooLargeException::class)
    fun `카테고리 용량 한도를 넘으면 거부한다`() {
        service.uploadApplication(APPLICANT_ID, application(admin, sizeBytes = FileCategory.APPLICATION.maxSizeBytes + 1), content())
    }

    @Test
    fun `원본 파일명이 DB에 담을 수 없을 만큼 길면 저장소에 올리기 전에 거부한다`() {
        val longName = "a".repeat(FileNaming.MAX_STORED_NAME_LENGTH) + ".pdf"

        assertThrows(InvalidFileNameException::class.java) {
            service.uploadApplication(APPLICANT_ID, application(admin, originalName = longName), content())
        }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `메타데이터 저장이 실패하면 새로 올린 객체를 지운다`() {
        repository.failOnSave = true

        runCatching { service.uploadApplication(APPLICANT_ID, application(admin), content()) }

        assertEquals(listOf("dsm_Entry/Backend/application/application_12.pdf"), storage.deleted)
    }

    @Test
    fun `덮어쓴 객체는 메타데이터 저장이 실패해도 지우지 않는다`() {
        storage.existingKeys += "dsm_Entry/Backend/application/application_12.pdf"
        repository.failOnSave = true

        runCatching { service.uploadApplication(APPLICANT_ID, application(admin), content()) }

        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun `보상 삭제가 실패해도 원래 예외를 그대로 올린다`() {
        repository.failOnSave = true
        storage.failOnDelete = true

        val error = runCatching { service.uploadApplication(APPLICANT_ID, application(admin), content()) }.exceptionOrNull()

        assertEquals("save failed", error?.message)
    }

    @Test
    fun `원서는 application이 알려 준 본인과 관리자만 적재하고, 관리자가 올려도 본인은 지원자 계정이다`() {
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.uploadApplication(APPLICANT_ID, application(student(11)), content())
        }

        val byAdmin = service.uploadApplication(APPLICANT_ID, application(admin), content())

        assertEquals(STUDENT_ID, byAdmin.document.ownerUserId)
        assertEquals(1, storage.uploaded.size)
    }

    @Test
    fun `없는 지원자는 학생에게 403, 관리자에게 404다`() {
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.uploadApplication(404, application(student(STUDENT_ID)), content())
        }
        assertThrows(ApplicantNotFoundException::class.java) {
            service.uploadApplication(404, application(admin), content())
        }
        assertThrows(ApplicantNotFoundException::class.java) { service.findApplication(404, admin) }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `원서 조회는 올리기 전이면 null, 올린 뒤엔 여러 형식 중 최근 것을 본인과 관리자에게만 준다`() {
        assertNull(service.findApplication(APPLICANT_ID, student(STUDENT_ID)))

        service.uploadApplication(APPLICANT_ID, application(student(STUDENT_ID)), content())
        service.uploadApplication(APPLICANT_ID, application(student(STUDENT_ID), originalName = "지원서.hwp"), content())

        assertEquals("application_12.hwp", service.findApplication(APPLICANT_ID, student(STUDENT_ID))?.document?.fileName)
        assertEquals("application_12.hwp", service.findApplication(APPLICANT_ID, admin)?.document?.fileName)
        assertThrows(DocumentAccessDeniedException::class.java) { service.findApplication(APPLICANT_ID, student(11)) }
    }

    @Test
    fun `수험표는 지원자 정보와 본인 사진으로 만들고 수험번호는 미발급으로 찍는다`() {
        val photo = service.upload(photo(student(STUDENT_ID)), content())
        applicants[APPLICANT_ID] = Applicant(
            STUDENT_ID, "홍<길동>", "대덕중학교", Applicant.Region.DAEJEON, Applicant.AdmissionType.MEISTER, photo.document.publicId,
        )

        val ticket = service.generateAdmissionTicket(APPLICANT_ID, student(STUDENT_ID))

        assertEquals("dsm_Entry/Backend/admission-ticket/admission_ticket_12.pdf", ticket.document.objectKey)
        assertEquals("application/pdf", ticket.document.contentType)
        assertEquals(STUDENT_ID, ticket.document.ownerUserId)
        assertTrue(ticket.downloadUrl.contains("admission_ticket_12.pdf"))
        listOf("2027학년도", "미발급", "홍&lt;길동&gt;", "대덕중학교", "대전", "마이스터전형", "data:image/png;base64,")
            .forEach { assertTrue(it, it in pdf.lastHtml) }
    }

    @Test
    fun `남의 지원자 수험표는 없어도 403이고, 관리자는 없는 지원자면 404다`() {
        assertThrows(DocumentAccessDeniedException::class.java) { service.generateAdmissionTicket(APPLICANT_ID, student(11)) }
        assertThrows(DocumentAccessDeniedException::class.java) { service.generateAdmissionTicket(404, student(11)) }
        assertThrows(ApplicantNotFoundException::class.java) { service.generateAdmissionTicket(404, admin) }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `원서에 다른 학생의 사진이나 사진이 아닌 파일 ID가 적혀 있으면 수험표에 넣지 않는다`() {
        val othersPhoto = service.upload(photo(student(11)), content())
        val attachment = service.upload(attachment(), content())

        applicants[APPLICANT_ID] = applicant(photoFileId = othersPhoto.document.publicId)
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertFalse("data:" in pdf.lastHtml)

        applicants[APPLICANT_ID] = applicant(photoFileId = attachment.document.publicId)
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertFalse("data:" in pdf.lastHtml)
    }

    @Test
    fun `증명사진은 학생이 올리고 공개 ID로 본인과 관리자가 받는다`() {
        val photo = service.upload(photo(student(STUDENT_ID)), content())
        val photoId = photo.document.publicId

        assertTrue(photo.document.objectKey.matches(Regex("dsm_Entry/Backend/photo/photo_[0-9a-f]{32}\\.png")))
        assertTrue(photoId.matches(Regex("photo_[0-9a-f]{32}")))
        assertEquals(photo.document.objectKey, service.find(FileCategory.PHOTO, photoId, student(STUDENT_ID)).document.objectKey)
        assertEquals(photo.document.objectKey, service.find(FileCategory.PHOTO, photoId, admin).document.objectKey)
        assertThrows(DocumentAccessDeniedException::class.java) { service.find(FileCategory.PHOTO, photoId, student(11)) }
        assertThrows(DocumentAccessDeniedException::class.java) { service.upload(photo(admin), content()) }
    }

    @Test
    fun `첨부는 관리자만 올리고 원본 파일명을 남긴다`() {
        val attachment = service.upload(attachment(), content())

        assertEquals("notice.pdf", attachment.document.originalName)
        assertTrue(attachment.document.objectKey.matches(Regex("dsm_Entry/Backend/attachment/[0-9a-f]{32}_notice\\.pdf")))
        assertThrows(DocumentAccessDeniedException::class.java) { service.upload(attachment(student(STUDENT_ID)), content()) }
    }

    @Test
    fun `공개 ID로 찾을 때 다른 종류의 파일이나 없는 ID는 없는 것으로 본다`() {
        val attachment = service.upload(attachment(), content())

        assertThrows(FileDocumentNotFoundException::class.java) {
            service.find(FileCategory.GUIDELINE, attachment.document.publicId, admin)
        }
        assertThrows(FileDocumentNotFoundException::class.java) { service.find(FileCategory.ATTACHMENT, "attachment_1", admin) }
    }

    @Test
    fun `요강 목록은 최근 것부터 페이지로 주고 전체 개수를 센다`() {
        val older = service.upload(guideline("2026.pdf"), content())
        val newer = service.upload(guideline("2027.pdf"), content())
        service.upload(attachment(), content())

        val first = service.findPage(FileCategory.GUIDELINE, page = 1, size = 1, requester = student(STUDENT_ID))
        val second = service.findPage(FileCategory.GUIDELINE, page = 2, size = 1, requester = admin)

        assertEquals(listOf(newer.document.publicId), first.items.map { it.document.publicId })
        assertEquals(listOf(older.document.publicId), second.items.map { it.document.publicId })
        assertEquals(2L, first.totalElements)
        assertTrue(first.items.single().downloadUrl.startsWith("https://s3/dsm_Entry/Backend/guideline/"))
    }

    @Test
    fun `첨부 삭제는 관리자만 하고 행과 객체를 모두 지운다`() {
        val attachment = service.upload(attachment(), content())
        val attachmentId = attachment.document.publicId

        assertThrows(DocumentAccessDeniedException::class.java) {
            service.delete(FileCategory.ATTACHMENT, attachmentId, student(STUDENT_ID))
        }

        service.delete(FileCategory.ATTACHMENT, attachmentId, admin)

        assertNull(repository.findByPublicId(attachmentId))
        assertEquals(listOf(attachment.document.objectKey), storage.deleted)
        assertThrows(FileDocumentNotFoundException::class.java) { service.delete(FileCategory.ATTACHMENT, attachmentId, admin) }
    }

    @Test
    fun `저장소 객체 삭제가 실패해도 행은 지우고 예외를 올리지 않는다`() {
        val attachmentId = service.upload(attachment(), content()).document.publicId
        storage.failOnDelete = true

        service.delete(FileCategory.ATTACHMENT, attachmentId, admin)

        assertNull(repository.findByPublicId(attachmentId))
    }

    private fun student(userId: Long) = Requester(userId, Requester.Role.STUDENT)

    private fun application(requester: Requester, originalName: String = "지원서.pdf", sizeBytes: Long = 1024) =
        UploadFileCommand(FileCategory.APPLICATION, originalName, sizeBytes, requester)

    private fun photo(requester: Requester) = UploadFileCommand(FileCategory.PHOTO, "사진.png", 1024, requester)

    private fun attachment(requester: Requester = admin) = UploadFileCommand(FileCategory.ATTACHMENT, "notice.pdf", 1024, requester)

    private fun guideline(originalName: String) = UploadFileCommand(FileCategory.GUIDELINE, originalName, 1024, admin)

    private fun content(): InputStream = ByteArrayInputStream(ByteArray(4))

    private companion object {
        const val APPLICANT_ID = 12L
        const val STUDENT_ID = 10L

        fun applicant(photoFileId: String? = null) = Applicant(STUDENT_ID, "홍길동", null, null, null, photoFileId)
    }

    private class FakeStoragePort : StoragePort {
        val existingKeys = mutableSetOf<String>()
        val uploaded = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var failOnDelete = false

        override fun upload(
            objectKey: String,
            contentType: String,
            sizeBytes: Long,
            content: InputStream,
        ): StoredObject {
            uploaded += objectKey
            existingKeys += objectKey
            return StoredObject(bucket = "entrydsm", objectKey = objectKey, checksum = "abc")
        }

        override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String =
            "https://s3/$objectKey?expires=$expiresInSeconds"

        override fun download(objectKey: String): ByteArray = objectKey.toByteArray()

        override fun exists(objectKey: String): Boolean = objectKey in existingKeys

        override fun delete(objectKey: String) {
            if (failOnDelete) throw IllegalStateException("delete failed")
            deleted += objectKey
        }
    }

    private class RecordingPdfRenderPort : PdfRenderPort {
        var lastHtml = ""

        override fun render(html: String): ByteArray {
            lastHtml = html
            return "%PDF-".toByteArray()
        }
    }

    /** 실제 어댑터처럼 같은 object key 는 새 행 대신 기존 행을 갱신하고 공개 ID 를 유지한다. */
    private class FakeFileDocumentRepository : FileDocumentRepository {
        private val saved = mutableListOf<FileDocument>()
        var failOnSave = false
        private var clock = 0L

        override fun save(fileDocument: FileDocument): FileDocument {
            if (failOnSave) throw IllegalStateException("save failed")
            val existing = findByObjectKey(fileDocument.objectKey)
            saved.removeIf { it.objectKey == fileDocument.objectKey }
            return fileDocument.copy(
                id = existing?.id ?: ((saved.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1),
                publicId = existing?.publicId ?: fileDocument.publicId,
                createdAt = Instant.EPOCH.plusSeconds(++clock),
            ).also { saved += it }
        }

        override fun findByObjectKey(objectKey: String): FileDocument? = saved.firstOrNull { it.objectKey == objectKey }

        override fun findByPublicId(publicId: String): FileDocument? = saved.firstOrNull { it.publicId == publicId }

        override fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument> =
            saved.filter { category.holds(it.objectKey) }
                .sortedByDescending { it.createdAt }
                .drop((page - 1) * size)
                .take(size)

        override fun count(category: FileCategory): Long = saved.count { category.holds(it.objectKey) }.toLong()

        override fun deleteByObjectKey(objectKey: String) {
            saved.removeIf { it.objectKey == objectKey }
        }
    }
}
