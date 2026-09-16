package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository

interface InstitutionCodeJpaRepository : JpaRepository<InstitutionCodeJpaEntity, String> {
    fun findByNameContainingOrderByNameAscCodeAsc(name: String, pageable: Pageable): Slice<InstitutionCodeJpaEntity>
}
