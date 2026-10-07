package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.AccountJpaEntity
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update AccountJpaEntity a
        set a.passwordHash = :newHash, a.tokenVersion = a.tokenVersion + 1, a.updatedAt = :updatedAt
        where a.id = :userId and a.passwordHash = :expectedHash
    """)
    fun changePasswordAndRevoke(
        @Param("userId") userId: Long,
        @Param("expectedHash") expectedHash: String,
        @Param("newHash") newHash: String,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update AccountJpaEntity a
        set a.tokenVersion = a.tokenVersion + 1, a.updatedAt = :updatedAt
        where a.id = :userId
    """)
    fun revokeTokens(@Param("userId") userId: Long, @Param("updatedAt") updatedAt: Instant): Int

    fun findByLoginIdHash(loginIdHash: String): AccountJpaEntity?
}
