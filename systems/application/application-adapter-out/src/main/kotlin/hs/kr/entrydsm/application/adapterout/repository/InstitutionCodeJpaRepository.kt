package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InstitutionCodeJpaRepository : JpaRepository<InstitutionCodeJpaEntity, String>
