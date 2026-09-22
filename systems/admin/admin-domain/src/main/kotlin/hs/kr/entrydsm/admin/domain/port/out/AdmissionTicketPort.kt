package hs.kr.entrydsm.admin.domain.port.out

/**
 * 수험표 여러 장을 한 시트에 이어 그린 xlsx 를 받습니다.
 *
 * 수험표 양식·증명사진은 document(configuration) 가 갖고, 수험 번호는 admin 이 발급하므로 넘겨줍니다.
 */
interface AdmissionTicketPort {
    /** @param tickets 찍을 순서대로 지원자 번호와 수험 번호. 수험 번호가 null 이면 미발급으로 찍힌다 */
    fun render(tickets: List<Pair<Long, String?>>): ByteArray
}
