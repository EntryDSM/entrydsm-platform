package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.domain.model.Applicant

interface ApplicantRepository {
    fun save(applicant: Applicant): Applicant
    fun findById(id: Long): Applicant?
    fun findByAccountId(accountId: Long): Applicant?

    /** [ids] 중 실제로 존재하는 지원자 식별자만 돌려줍니다. */
    fun existingIds(ids: Collection<Long>): Set<Long>

    /** 제출을 마친 원서 전체를 제출 시각 오름차순으로 돌려줍니다. */
    fun findAllSubmitted(): List<Applicant>
}
