package hs.kr.entrydsm.configuration.adapterout.repository

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FileDocumentJpaRepository : JpaRepository<FileDocumentJpaEntity, Long> {
    fun findByObjectKey(objectKey: String): FileDocumentJpaEntity?
    fun findByPublicId(publicId: String): FileDocumentJpaEntity?
    fun findByObjectKeyStartingWith(keyPrefix: String, pageable: Pageable): List<FileDocumentJpaEntity>
    fun countByObjectKeyStartingWith(keyPrefix: String): Long
    fun deleteByObjectKey(objectKey: String)
}
