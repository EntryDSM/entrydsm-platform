package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.FileDocumentJpaRepository
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class FileDocumentPersistenceAdapterTest {

    @Test
    fun `같은 객체 키가 이미 있으면 기존 행을 갱신한다`() {
        val saved = mutableListOf<FileDocumentJpaEntity>()
        val adapter = FileDocumentPersistenceAdapter(repository(existing = entity(id = 7L), saved = saved))

        val result = adapter.save(document())

        assertEquals(7L, saved.single().id)
        assertEquals(7L, result.id)
    }

    @Test
    fun `객체 키가 없으면 새 행으로 저장한다`() {
        val saved = mutableListOf<FileDocumentJpaEntity>()
        val adapter = FileDocumentPersistenceAdapter(repository(existing = null, saved = saved))

        adapter.save(document())

        assertNull(saved.single().id)
    }

    @Test
    fun `도메인 필드가 엔티티로 그대로 옮겨진다`() {
        val saved = mutableListOf<FileDocumentJpaEntity>()
        val adapter = FileDocumentPersistenceAdapter(repository(existing = null, saved = saved))

        val result = adapter.save(document())

        assertEquals("원본.pdf", result.originalName)
        assertEquals("application/application_1234.pdf", result.objectKey)
        assertEquals("entrydsm", result.bucket)
        assertEquals("application/pdf", result.contentType)
        assertEquals(1024L, result.sizeBytes)
        assertEquals("sha256", result.checksum)
    }

    private fun document() = FileDocument(
        originalName = "원본.pdf",
        objectKey = "application/application_1234.pdf",
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 1024L,
        checksum = "sha256",
    )

    private fun entity(id: Long?) = FileDocumentJpaEntity(
        id = id,
        originalName = "원본.pdf",
        objectKey = "application/application_1234.pdf",
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 1024L,
        checksum = "sha256",
        createdAt = Instant.EPOCH,
    )

    // JpaRepository 상속 메서드가 많아 프록시로 필요한 두 개만 응답한다.
    private fun repository(
        existing: FileDocumentJpaEntity?,
        saved: MutableList<FileDocumentJpaEntity>,
    ): FileDocumentJpaRepository =
        Proxy.newProxyInstance(
            FileDocumentJpaRepository::class.java.classLoader,
            arrayOf(FileDocumentJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findByObjectKey" -> existing
                "save" -> (args[0] as FileDocumentJpaEntity).also { saved += it }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as FileDocumentJpaRepository
}
