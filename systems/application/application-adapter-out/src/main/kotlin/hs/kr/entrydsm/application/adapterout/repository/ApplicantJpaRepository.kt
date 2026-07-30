package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ApplicantJpaRepository : JpaRepository<ApplicantJpaEntity, Long>
