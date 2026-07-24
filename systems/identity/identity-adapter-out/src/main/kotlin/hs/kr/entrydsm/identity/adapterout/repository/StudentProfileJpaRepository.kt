package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.StudentProfileJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface StudentProfileJpaRepository : JpaRepository<StudentProfileJpaEntity, Long> {
    fun findByAccount_Id(accountId: Long): StudentProfileJpaEntity?
}
