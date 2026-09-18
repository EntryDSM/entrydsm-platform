package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface InstitutionCodeJpaRepository : JpaRepository<InstitutionCodeJpaEntity, String> {
    @Query("select i from InstitutionCodeJpaEntity i where i.name like concat(:name, '%') order by i.name, i.id")
    fun findByNameStartingWith(name: String): List<InstitutionCodeJpaEntity>
}
