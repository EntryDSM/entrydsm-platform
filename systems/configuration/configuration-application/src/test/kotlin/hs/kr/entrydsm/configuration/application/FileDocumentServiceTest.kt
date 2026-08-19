package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class FileDocumentServiceTest {

    private val storage = FakeStoragePort()
    private val repository = FakeFileDocumentRepository()
    private val service = FileDocumentService(storage, repository, presignExpirySeconds = 300)

    @Test
    fun `업로드는 카테고리 키로 저장하고 저장된 메타데이터를 돌려준다`() {
        val saved = service.upload(command(), content())

        assertEquals("application/application_1001.pdf", saved.objectKey)
        assertEquals("application/pdf", saved.contentType)
        assertEquals("지원서.pdf", saved.originalName)
        assertEquals(listOf("application/application_1001.pdf"), storage.uploaded)
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
    fun `메타데이터 저장이 실패하면 새로 올린 객체를 지운다`() {
        repository.failOnSave = true

        runCatching { service.upload(command(), content()) }

        assertEquals(listOf("application/application_1001.pdf"), storage.deleted)
    }

    @Test
    fun `덮어쓴 객체는 메타데이터 저장이 실패해도 지우지 않는다`() {
        storage.existingKeys += "application/application_1001.pdf"
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
    fun `파일명으로 다운로드 URL을 발급한다`() {
        storage.existingKeys += "application/application_1001.pdf"

        val downloadUrl = service.issueByCommand(
            IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.pdf"),
        )

        assertEquals("application_1001.pdf", downloadUrl.fileName)
        assertEquals("https://s3/application/application_1001.pdf?expires=300", downloadUrl.downloadUrl)
        assertEquals(300L, downloadUrl.expiresIn)
    }

    @Test(expected = FileDocumentNotFoundException::class)
    fun `없는 객체의 다운로드 URL은 발급하지 않는다`() {
        service.issueByCommand(IssueDownloadUrlCommand(FileCategory.APPLICATION, "application_1001.pdf"))
    }

    @Test
    fun `ID로 다운로드 URL을 발급하면 원본 파일명을 돌려준다`() {
        repository.saved += FileDocument(
            id = 1,
            originalName = "첨부.pdf",
            objectKey = "attachment/abc_첨부.pdf",
            bucket = "entrydsm",
            contentType = "application/pdf",
            sizeBytes = 10,
            checksum = "abc",
        )

        val downloadUrl = service.issueById(1)

        assertEquals("첨부.pdf", downloadUrl.fileName)
        assertTrue(downloadUrl.downloadUrl.startsWith("https://s3/attachment/"))
    }

    @Test(expected = FileDocumentNotFoundException::class)
    fun `없는 ID를 조회하면 예외를 올린다`() {
        service.findById(1)
    }

    @Test
    fun `파일명 조회는 카테고리 키로 찾는다`() {
        repository.saved += FileDocument(
            id = 1,
            originalName = "지원서.pdf",
            objectKey = "application/application_1001.pdf",
            bucket = "entrydsm",
            contentType = "application/pdf",
            sizeBytes = 10,
            checksum = "abc",
        )

        assertEquals(1L, service.findByFileName(FileCategory.APPLICATION, "application_1001.pdf")?.id)
        assertNull(service.findByFileName(FileCategory.APPLICATION, "application_9999.pdf"))
    }

    private fun command(
        category: FileCategory = FileCategory.APPLICATION,
        originalName: String = "지원서.pdf",
        fileName: String = "application_1001.pdf",
        sizeBytes: Long = 1024,
    ) = UploadFileCommand(category, originalName, fileName, sizeBytes)

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

    private class FakeFileDocumentRepository : FileDocumentRepository {
        val saved = mutableListOf<FileDocument>()
        var failOnSave = false

        override fun save(fileDocument: FileDocument): FileDocument {
            if (failOnSave) throw IllegalStateException("save failed")
            val stored = fileDocument.copy(id = saved.size + 1L)
            saved += stored
            return stored
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
