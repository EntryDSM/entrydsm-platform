package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument

interface FileDocumentRepository {
    fun save(fileDocument: FileDocument): FileDocument
    fun findByObjectKey(objectKey: String): FileDocument?
    fun findByPublicId(publicId: String): FileDocument?

    /** 공개 ID 가 생기기 전(V003)에 밖으로 나간 순번 id 로 찾는다. 원서에 남은 옛 사진 ID 에만 쓴다. */
    fun findById(id: Long): FileDocument?

    /** 그 종류 파일을 최근에 올린 것부터. [page] 는 1부터 센다. */
    fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument>
    fun count(category: FileCategory): Long
    fun deleteByObjectKey(objectKey: String)
}
