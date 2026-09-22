package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester

/**
 * 지원자 한 명에게 하나씩 있는 원서·수험표. application 의 applicant id 로 찾고, 본인은 application 이
 * 알려 준 원서 주인 계정이다. 둘 다 올리지 않고 요청할 때마다 새로 만든다.
 */
interface ApplicantFileUseCase {
    /**
     * 요청자 본인 원서 PDF 를 요강 <서식 1> 양식으로 새로 만들어 올린다.
     * 학생은 applicant id 를 들고 있지 않으므로 요청자 계정으로 찾는다.
     */
    fun generateApplicationForm(requester: Requester): DownloadableFile

    /**
     * 지목한 지원자의 원서 PDF 를 새로 만들어 올린다. 관리자와 그 원서 주인 학생이 받을 수 있고,
     * 남의 applicant id 를 넣은 학생은 거부된다. applicant id 를 들고 있는 쪽이 주로 관리자다.
     */
    fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile

    /** 수험표 PDF 를 원서 내용으로 새로 만들어 올린다. 수험번호는 받을 길이 없어 미발급으로 찍는다. */
    fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile

    /**
     * admin 수험표 일괄 출력용 수험표 한 장. admin 이 발급한 [examineeNumber] 를 찍고, 올리지 않고 PDF 를 돌려준다.
     * 서비스 안쪽 gRPC 로만 부르므로 요청자 권한을 보지 않는다.
     */
    fun renderAdmissionTicket(applicantId: Long, examineeNumber: String?): ByteArray
}
