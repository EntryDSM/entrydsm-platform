package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApplicantJpaRepository : JpaRepository<ApplicantJpaEntity, Long> {
    fun findByAccountId(accountId: Long): ApplicantJpaEntity?

    /** 존재 확인만 하므로 엔티티 대신 식별자만 읽습니다. */
    @Query("select a.id from ApplicantJpaEntity a where a.id in :ids")
    fun findExistingIds(@Param("ids") ids: Collection<Long>): List<Long>
}
