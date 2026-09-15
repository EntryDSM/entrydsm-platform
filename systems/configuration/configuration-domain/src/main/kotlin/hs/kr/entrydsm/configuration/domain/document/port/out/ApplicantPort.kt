package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.Applicant

/** application 서비스가 가진 원서에서 지원자 정보를 읽는다. */
interface ApplicantPort {
    /** 그 계정의 원서가 없으면 null. */
    fun findByUserId(userId: Long): Applicant?
}
