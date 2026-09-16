package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.FileDocumentJpaRepository
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.lang.reflect.Proxy
import java.time.Instant

class FileDocumentPersistenceAdapterTest {

    @Test
    fun `Hibernate가 사용할 기본 생성자가 존재한다`() {
        FileDocumentJpaEntity::class.java.getDeclaredConstructor()
    }

    @Test
    fun `같은 객체 키가 이미 있으면 기존 행을 갱신하고 공개 ID 는 처음 것을 유지한다`() {
        val saved = mutableListOf<FileDocumentJpaEntity>()
        val adapter = FileDocumentPersistenceAdapter(repository(existing = entity(id = 7L), saved = saved))

        val result = adapter.save(document().copy(publicId = "application_new"))

        assertEquals(7L, saved.single().id)
        assertEquals(7L, result.id)
        assertEquals("application_first", result.publicId)
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

        assertEquals("application_first", result.publicId)
        assertEquals("원본.pdf", result.originalName)
        assertEquals("application/application_1234.pdf", result.objectKey)
        assertEquals("entrydsm", result.bucket)
        assertEquals("application/pdf", result.contentType)
        assertEquals(1024L, result.sizeBytes)
        assertEquals("sha256", result.checksum)
    }

    @Test
    fun `목록은 종류 폴더 아래를 1부터 센 페이지를 0부터로 바꿔 최근 순으로 찾는다`() {
        var prefix: String? = null
        var pageable: Pageable? = null
        val adapter = FileDocumentPersistenceAdapter(
            repository(existing = null, saved = mutableListOf()) { args ->
                prefix = args[0] as String
                pageable = args[1] as Pageable
            },
        )

        adapter.findPage(FileCategory.GUIDELINE, page = 3, size = 20)

        assertEquals("dsm_Entry/Backend/guideline/", prefix)
        assertEquals(2, pageable?.pageNumber)
        assertEquals(20, pageable?.pageSize)
        assertEquals(Sort.Direction.DESC, pageable?.sort?.getOrderFor("createdAt")?.direction)
    }

    private fun document() = FileDocument(
        publicId = "application_first",
        originalName = "원본.pdf",
        objectKey = "application/application_1234.pdf",
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 1024L,
        checksum = "sha256",
    )

    private fun entity(id: Long?) = FileDocumentJpaEntity(
        id = id,
        publicId = "application_first",
        originalName = "원본.pdf",
        objectKey = "application/application_1234.pdf",
        bucket = "entrydsm",
        contentType = "application/pdf",
        sizeBytes = 1024L,
        checksum = "sha256",
        createdAt = Instant.EPOCH,
    )

    // JpaRepository 상속 메서드가 많아 프록시로 필요한 것만 응답한다.
    private fun repository(
        existing: FileDocumentJpaEntity?,
        saved: MutableList<FileDocumentJpaEntity>,
        onFindPage: (Array<Any?>) -> Unit = {},
    ): FileDocumentJpaRepository =
        Proxy.newProxyInstance(
            FileDocumentJpaRepository::class.java.classLoader,
            arrayOf(FileDocumentJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findByObjectKey" -> existing
                "save" -> (args[0] as FileDocumentJpaEntity).also { saved += it }
                "findByObjectKeyStartingWith" -> emptyList<FileDocumentJpaEntity>().also { onFindPage(args) }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as FileDocumentJpaRepository
}
