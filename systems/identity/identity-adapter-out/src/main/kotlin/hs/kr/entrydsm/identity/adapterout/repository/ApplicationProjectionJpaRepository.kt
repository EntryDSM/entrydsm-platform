package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.ApplicationProjectionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ApplicationProjectionJpaRepository : JpaRepository<ApplicationProjectionJpaEntity, Long>
