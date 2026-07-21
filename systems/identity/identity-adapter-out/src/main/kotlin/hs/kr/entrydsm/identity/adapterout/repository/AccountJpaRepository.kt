package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.AccountJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByLoginIdHash(loginIdHash: String): AccountJpaEntity?
}
