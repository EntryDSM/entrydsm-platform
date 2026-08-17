package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.ApplicationProjectionJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApplicationProjectionJpaRepository : JpaRepository<ApplicationProjectionJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select projection from ApplicationProjectionJpaEntity projection where projection.userId = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): ApplicationProjectionJpaEntity?
}
