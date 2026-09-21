package hs.kr.entrydsm.admin.domain.port.out

/**
 * 지원자 한 명의 수험표 PDF 를 받습니다.
 *
 * 수험표 양식·증명사진은 document(configuration) 가 갖고, 수험 번호는 admin 이 발급하므로 넘겨줍니다.
 */
interface AdmissionTicketPort {
    /** @param examineeNumber 발급 전이면 null. 수험표에 미발급으로 찍힌다 */
    fun render(applicantId: Long, examineeNumber: String?): ByteArray
}
