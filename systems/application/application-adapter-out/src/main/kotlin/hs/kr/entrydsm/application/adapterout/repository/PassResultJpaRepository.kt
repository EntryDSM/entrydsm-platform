package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.PassResultId
import hs.kr.entrydsm.application.adapterout.entity.PassResultJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PassResultJpaRepository : JpaRepository<PassResultJpaEntity, PassResultId>
