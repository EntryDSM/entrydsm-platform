package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.FaqJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FaqJpaRepository : JpaRepository<FaqJpaEntity, Long> {
    fun findAllByOrderByIdAsc(pageable: Pageable): Page<FaqJpaEntity>
}

