package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.junit.Assert.assertEquals
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
    private val service = FileDocumentService(storage, repository, presignExpirySeconds = 300)

    private val admin = Requester(1, Requester.Role.ADMIN)

    @Test
    fun `업로드는 카테고리 키로 저장하고 저장된 메타데이터를 돌려준다`() {
        val saved = service.upload(command(), content())

        assertEquals("dsm_Entry/Backend/application/application_1001.pdf", saved.objectKey)
        assertEquals("application/pdf", saved.contentType)
        assertEquals("지원서.pdf", saved.originalName)
        assertEquals(listOf("dsm_Entry/Backend/application/application_1001.pdf"), storage.uploaded)
        assertEquals(1, repository.saved.size)
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `카테고리가 허용하지 않는 확장자는 거부한다`() {
        service.upload(command(originalName = "사진.jpg"), content())
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `확장자가 없으면 거부한다`() {
        service.upload(command(originalName = "지원서"), content())
    }

    @Test(expected = FileTooLargeException::class)
    fun `카테고리 용량 한도를 넘으면 거부한다`() {
        service.upload(command(sizeBytes = FileCategory.APPLICATION.maxSizeBytes + 1), content())
    }

    @Test(expected = InvalidFileNameException::class)
    fun `파일명에 상위 경로 참조가 들어오면 거부한다`() {
        service.upload(command(fileName = "../../etc/passwd"), content())
    }

    @Test
    fun `원본 파일명이 DB에 담을 수 없을 만큼 길면 저장소에 올리기 전에 거부한다`() {
        assertThrows(InvalidFileNameException::class.java) {
            service.upload(command(originalName = "a".repeat(FileNaming.MAX_STORED_NAME_LENGTH) + ".pdf"), content())
        }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `메타데이터 저장이 실패하면 새로 올린 객체를 지운다`() {
        repository.failOnSave = true

        runCatching { service.upload(command(), content()) }

        assertEquals(listOf("dsm_Entry/Backend/application/application_1001.pdf"), storage.deleted)
    }

    @Test
    fun `덮어쓴 객체는 메타데이터 저장이 실패해도 지우지 않는다`() {
        storage.existingKeys += "dsm_Entry/Backend/application/application_1001.pdf"
        repository.failOnSave = true

        runCatching { service.upload(command(), content()) }

        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun `보상 삭제가 실패해도 원래 예외를 그대로 올린다`() {
        repository.failOnSave = true
        storage.failOnDelete = true

        val error = runCatching { service.upload(command(), content()) }.exceptionOrNull()

        assertEquals("save failed", error?.message)
    }

    @Test
    fun `학생은 다른 학생이 본인인 수험번호의 원서를 적재할 수 없다`() {
        service.upload(command(requester = student(10)), content())

        assertThrows(DocumentAccessDeniedException::class.java) {
            service.upload(command(requester = student(11)), content())
        }
    }

    @Test
    fun `관리자가 원서를 덮어써도 본인은 그대로 남는다`() {
        service.upload(command(requester = student(10)), content())
        service.upload(command(requester = admin), content())

        assertEquals(10L, repository.findByObjectKey("dsm_Entry/Backend/application/application_1001.pdf")?.ownerUserId)
        service.issueByCommand(IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.pdf", student(10), "1001"))
    }

    @Test
    fun `학생은 수험표를 적재할 수 없다`() {
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.upload(admissionTicket(requester = student(10)), content())
        }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `관리자가 적재한 수험표는 그 수험번호로 원서를 적재한 학생만 받는다`() {
        service.upload(command(requester = student(10)), content())
        service.upload(admissionTicket(requester = admin), content())

        val own = IssueDownloadUrlCommand(FileCategory.ADMISSION_TICKET, "admission_ticket_1001.pdf", student(10), "1001")
        assertEquals("admission_ticket_1001.pdf", service.issueByCommand(own).fileName)
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.issueByCommand(own.copy(requester = student(11)))
        }
    }

    @Test
    fun `파일명으로 다운로드 URL을 발급한다`() {
        storage.existingKeys += "dsm_Entry/Backend/application/application_1001.pdf"

        val downloadUrl = service.issueByCommand(
            IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.pdf", admin, "1001"),
        )

        assertEquals("application_1001.pdf", downloadUrl.fileName)
        assertEquals("https://s3/dsm_Entry/Backend/application/application_1001.pdf?expires=300", downloadUrl.downloadUrl)
        assertEquals(300L, downloadUrl.expiresIn)
    }

    @Test(expected = FileDocumentNotFoundException::class)
    fun `없는 객체의 다운로드 URL은 발급하지 않는다`() {
        service.issueByCommand(IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.pdf", admin, "1001"))
    }

    @Test
    fun `다운로드 권한이 없으면 파일이 있어도 거부한다`() {
        service.upload(applicantList(), content())

        assertThrows(DocumentAccessDeniedException::class.java) {
            service.issueByCommand(IssueDownloadUrlCommand(FileCategory.APPLICANT_LIST, "applicants_20260726.xlsx", student(10)))
        }
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.issueById(FileCategory.APPLICANT_LIST, 1, student(10))
        }
    }

    @Test
    fun `ID로 다운로드 URL을 발급하면 원본 파일명을 돌려준다`() {
        repository.saved += FileDocument(
            id = 1,
            originalName = "첨부.pdf",
            objectKey = "dsm_Entry/Backend/attachment/abc_첨부.pdf",
            bucket = "entrydsm",
            contentType = "application/pdf",
            sizeBytes = 10,
            checksum = "abc",
        )

        val downloadUrl = service.issueById(FileCategory.ATTACHMENT, 1, student(10))

        assertEquals("첨부.pdf", downloadUrl.fileName)
        assertTrue(downloadUrl.downloadUrl.startsWith("https://s3/dsm_Entry/Backend/attachment/"))
    }

    @Test
    fun `ID로 다운로드할 때 다른 종류의 파일은 없는 것으로 본다`() {
        service.upload(command(), content())

        assertThrows(FileDocumentNotFoundException::class.java) {
            service.issueById(FileCategory.GUIDELINE, 1, admin)
        }
    }

    @Test(expected = FileDocumentNotFoundException::class)
    fun `없는 ID를 조회하면 예외를 올린다`() {
        service.findById(1)
    }

    @Test
    fun `원서 조회는 적재 전이면 null, 적재 후엔 여러 형식 중 최근 것을 본인에게만 돌려준다`() {
        assertNull(service.findApplication("1001", student(10)))

        service.upload(command(requester = student(10)), content())
        service.upload(
            command(originalName = "지원서.hwp", fileName = "application_1001.hwp", requester = student(10)),
            content(),
        )

        assertEquals("application_1001.hwp", service.findApplication("1001", student(10))?.fileName)
        assertEquals("application_1001.hwp", service.findApplication("1001", admin)?.fileName)
        assertThrows(DocumentAccessDeniedException::class.java) {
            service.findApplication("1001", student(11))
        }
    }

    private fun student(userId: Long) = Requester(userId, Requester.Role.STUDENT)

    private fun command(
        originalName: String = "지원서.pdf",
        fileName: String = "application_1001.pdf",
        sizeBytes: Long = 1024,
        requester: Requester = admin,
    ) = UploadFileCommand(FileCategory.APPLICATION, originalName, fileName, sizeBytes, requester, receiptCode = "1001")

    private fun admissionTicket(requester: Requester) = UploadFileCommand(
        FileCategory.ADMISSION_TICKET, "수험표.pdf", "admission_ticket_1001.pdf", 1024, requester, receiptCode = "1001",
    )

    private fun applicantList() = UploadFileCommand(
        FileCategory.APPLICANT_LIST, "명단.xlsx", "applicants_20260726.xlsx", 1024, admin,
    )

    private fun content(): InputStream = ByteArrayInputStream(ByteArray(4))

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

        override fun exists(objectKey: String): Boolean = objectKey in existingKeys

        override fun delete(objectKey: String) {
            if (failOnDelete) throw IllegalStateException("delete failed")
            deleted += objectKey
        }
    }

    /** 실제 어댑터처럼 같은 object key 는 새 행 대신 기존 행을 갱신한다. */
    private class FakeFileDocumentRepository : FileDocumentRepository {
        val saved = mutableListOf<FileDocument>()
        var failOnSave = false
        private var clock = 0L

        override fun save(fileDocument: FileDocument): FileDocument {
            if (failOnSave) throw IllegalStateException("save failed")
            val id = findByObjectKey(fileDocument.objectKey)?.id ?: (saved.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1
            saved.removeIf { it.objectKey == fileDocument.objectKey }
            return fileDocument.copy(id = id, createdAt = Instant.EPOCH.plusSeconds(++clock)).also { saved += it }
        }

        override fun findById(id: Long): FileDocument? = saved.firstOrNull { it.id == id }

        override fun findByObjectKey(objectKey: String): FileDocument? =
            saved.firstOrNull { it.objectKey == objectKey }

        override fun existsById(id: Long): Boolean = saved.any { it.id == id }

        override fun deleteByObjectKey(objectKey: String) {
            saved.removeIf { it.objectKey == objectKey }
        }
    }
}
