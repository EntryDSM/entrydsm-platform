package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.model.Applicant

interface ApplicantRepository {
    fun save(applicant: Applicant): Applicant
    fun findById(id: Long): Applicant?
    fun findByAccountId(accountId: Long): Applicant?
    fun deleteById(id: Long)
    fun findAllByAccountIdIn(accountIds: List<Long>): List<Applicant> = accountIds.mapNotNull(::findByAccountId)

    /**
     * 목록에 쓰는 값만 읽습니다. 원서 전문([Applicant])이 아닙니다.
     *
     * 목록은 회차 전체를 부르므로 지원자마다 연관 테이블을 더 읽으면 질의 수가 지원자 수에
     * 비례해 늘어납니다. 한 명을 읽을 때는 [findById] 로 전문을 씁니다.
     */
    fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantResult>
}
