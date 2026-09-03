package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.FileDocument

interface FileDocumentRepository {
    fun save(fileDocument: FileDocument): FileDocument
    fun findById(id: Long): FileDocument?
    fun findByObjectKey(objectKey: String): FileDocument?
    fun existsById(id: Long): Boolean
    fun deleteByObjectKey(objectKey: String)
}
