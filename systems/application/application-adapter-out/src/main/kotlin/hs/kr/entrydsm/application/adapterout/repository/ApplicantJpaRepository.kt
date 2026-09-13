package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApplicantJpaRepository : JpaRepository<ApplicantJpaEntity, Long> {
    fun findByAccountId(accountId: Long): ApplicantJpaEntity?

    /** 존재 확인만 하므로 엔티티 대신 식별자만 읽습니다. */
    @Query("select a.id from ApplicantJpaEntity a where a.id in :ids")
    fun findExistingIds(@Param("ids") ids: Collection<Long>): List<Long>

    /**
     * 학적까지 한 번에 읽습니다.
     *
     * admin 이 회차 전체를 받아 가는 조회라, 지원자마다 학적을 따로 읽으면 목록 크기만큼
     * 쿼리가 늘어납니다. 컬렉션은 성적 하나만 조인합니다(둘 이상은 Hibernate 가 막습니다).
     */
    @Query(
        """
        select distinct a from ApplicantJpaEntity a
        left join fetch a.middleSchoolInfo
        left join fetch a.academicRecord record
        left join fetch record.subjectGrades
        left join fetch record.gedScores
        where a.status in :statuses
        order by a.submittedAt asc, a.id asc
        """,
    )
    fun findAllByStatusInWithRecord(
        @Param("statuses") statuses: Collection<ApplicantStatus>,
    ): List<ApplicantJpaEntity>
}
