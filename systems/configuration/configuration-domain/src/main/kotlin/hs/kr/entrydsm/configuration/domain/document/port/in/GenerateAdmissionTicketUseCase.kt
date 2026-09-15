package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.Requester

fun interface GenerateAdmissionTicketUseCase {
    /** 수험표 PDF 를 새로 만들어 적재한다. 같은 수험번호로 다시 만들면 덮어쓴다. */
    fun generateAdmissionTicket(receiptCode: String, requester: Requester): FileDocument
}
