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

    /** 지목한 지원자의 원서 PDF 를 새로 만들어 올린다. applicant id 를 아는 관리자가 쓴다. */
    fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile

    /** 수험표 PDF 를 원서 내용으로 새로 만들어 올린다. */
    fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile
}
