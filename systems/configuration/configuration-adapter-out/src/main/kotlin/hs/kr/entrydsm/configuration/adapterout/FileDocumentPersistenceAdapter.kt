package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.FileDocumentJpaRepository
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class FileDocumentPersistenceAdapter(
    private val fileDocumentJpaRepository: FileDocumentJpaRepository,
) : FileDocumentRepository {

    @Transactional
    override fun save(fileDocument: FileDocument): FileDocument =
        fileDocumentJpaRepository.save(
            FileDocumentJpaEntity.from(fileDocument)
        ).toDomain()

    override fun findById(id: Long): FileDocument? =
        fileDocumentJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByObjectKey(objectKey: String): FileDocument? =
        fileDocumentJpaRepository.findByObjectKey(objectKey)?.toDomain()

    override fun existsById(id: Long): Boolean =
        fileDocumentJpaRepository.existsById(id)

    @Transactional
    override fun deleteByObjectKey(objectKey: String) =
        fileDocumentJpaRepository.deleteByObjectKey(objectKey)
}
