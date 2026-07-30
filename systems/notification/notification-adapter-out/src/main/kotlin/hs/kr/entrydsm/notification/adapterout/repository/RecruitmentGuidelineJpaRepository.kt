package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.RecruitmentGuidelineJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RecruitmentGuidelineJpaRepository : JpaRepository<RecruitmentGuidelineJpaEntity, Long> {
    fun findTopByOrderByCreatedAtDesc(): RecruitmentGuidelineJpaEntity?
}

