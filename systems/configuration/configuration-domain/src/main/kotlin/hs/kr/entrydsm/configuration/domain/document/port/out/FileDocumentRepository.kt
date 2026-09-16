package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument

interface FileDocumentRepository {
    fun save(fileDocument: FileDocument): FileDocument
    fun findByObjectKey(objectKey: String): FileDocument?
    fun findByPublicId(publicId: String): FileDocument?

    /** 그 종류 파일을 최근에 올린 것부터. [page] 는 1부터 센다. */
    fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument>
    fun count(category: FileCategory): Long
    fun deleteByObjectKey(objectKey: String)
}
