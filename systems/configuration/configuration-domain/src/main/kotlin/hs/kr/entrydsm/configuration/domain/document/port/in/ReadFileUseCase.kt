package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.Requester

interface ReadFileUseCase {
    fun findById(id: Long): FileDocument

    /** 수험번호로 적재된 원서 중 가장 최근 것. 없으면 null. */
    fun findApplication(receiptCode: String, requester: Requester): FileDocument?

    fun existsById(id: Long): Boolean
}
