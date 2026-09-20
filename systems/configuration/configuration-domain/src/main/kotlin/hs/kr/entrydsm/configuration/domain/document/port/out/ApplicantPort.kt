package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm

/** application 서비스가 가진 원서에서 지원자 정보를 읽는다. */
interface ApplicantPort {
    /** 그 applicant id 의 원서가 없으면 null. */
    fun findById(applicantId: Long): Applicant?

    /** 요강 <서식 1> 을 찍는 데 쓰는 원서 전문. 그 계정의 원서가 없으면 null. */
    fun findApplicationForm(accountId: Long): ApplicationForm?
}
