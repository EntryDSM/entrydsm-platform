package hs.kr.entrydsm.configuration.adapterout.repository

import hs.kr.entrydsm.configuration.adapterout.entity.FileDocumentJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FileDocumentJpaRepository : JpaRepository<FileDocumentJpaEntity, Long> {
    fun findByObjectKey(objectKey: String): FileDocumentJpaEntity?
    fun deleteByObjectKey(objectKey: String)
}
