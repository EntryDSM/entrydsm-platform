package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeJpaRepository : JpaRepository<NoticeJpaEntity, Long> {
    fun findAllByOrderByCreatedAtDescIdDesc(pageable: Pageable): Page<NoticeJpaEntity>
    fun findAllByCategoryOrderByCreatedAtDescIdDesc(
        category: NoticeCategory,
        pageable: Pageable,
    ): Page<NoticeJpaEntity>
}

