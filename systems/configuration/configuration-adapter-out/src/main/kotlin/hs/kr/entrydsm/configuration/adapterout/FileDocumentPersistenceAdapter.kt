package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.FileDocumentJpaRepository
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class FileDocumentPersistenceAdapter(
    private val fileDocumentJpaRepository: FileDocumentJpaRepository,
) : FileDocumentRepository {

    @Transactional
    override fun save(fileDocument: FileDocument): FileDocument {
        // object_key 가 고유하므로 같은 키를 다시 올리면 새 행 대신 기존 행을 갱신한다. 공개 ID 는 처음 것을 유지한다.
        val existing = fileDocumentJpaRepository.findByObjectKey(fileDocument.objectKey)
        return fileDocumentJpaRepository.save(
            FileDocumentJpaEntity.from(
                existing?.let { fileDocument.copy(id = it.id, publicId = it.publicId) } ?: fileDocument
            )
        ).toDomain()
    }

    override fun findByObjectKey(objectKey: String): FileDocument? =
        fileDocumentJpaRepository.findByObjectKey(objectKey)?.toDomain()

    override fun findByPublicId(publicId: String): FileDocument? =
        fileDocumentJpaRepository.findByPublicId(publicId)?.toDomain()

    override fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument> =
        fileDocumentJpaRepository.findByObjectKeyStartingWith(
            category.keyPrefix,
            PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        ).map { it.toDomain() }

    override fun count(category: FileCategory): Long =
        fileDocumentJpaRepository.countByObjectKeyStartingWith(category.keyPrefix)

    @Transactional
    override fun deleteByObjectKey(objectKey: String) =
        fileDocumentJpaRepository.deleteByObjectKey(objectKey)
}
