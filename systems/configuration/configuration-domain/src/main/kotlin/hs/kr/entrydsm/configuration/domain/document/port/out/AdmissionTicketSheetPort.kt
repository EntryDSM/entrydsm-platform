package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicket

/** 수험표 여러 장을 받은 순서대로 xlsx 한 시트에 이어 그린다. 관리자 일괄 출력 양식은 어댑터가 갖는다. */
interface AdmissionTicketSheetPort {
    fun render(tickets: List<AdmissionTicket>): ByteArray
}
