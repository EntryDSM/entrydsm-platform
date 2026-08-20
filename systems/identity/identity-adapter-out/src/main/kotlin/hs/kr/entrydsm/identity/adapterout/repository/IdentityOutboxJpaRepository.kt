package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.IdentityOutboxJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdentityOutboxJpaRepository : JpaRepository<IdentityOutboxJpaEntity, String> {
    fun findTop100ByPublishedAtIsNullOrderByCreatedAtAsc(): List<IdentityOutboxJpaEntity>
}
