package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.NoticeJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeJpaRepository : JpaRepository<NoticeJpaEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<NoticeJpaEntity>
}

