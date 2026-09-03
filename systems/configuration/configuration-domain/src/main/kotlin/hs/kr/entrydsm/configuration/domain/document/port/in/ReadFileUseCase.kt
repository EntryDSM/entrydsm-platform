package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument

interface ReadFileUseCase {
    fun findById(id: Long): FileDocument
    fun findByFileName(category: FileCategory, fileName: String): FileDocument?
    fun existsById(id: Long): Boolean
}
