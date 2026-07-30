package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.FaqJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FaqJpaRepository : JpaRepository<FaqJpaEntity, Long> {
    fun findAllByOrderByIdAsc(): List<FaqJpaEntity>
}

