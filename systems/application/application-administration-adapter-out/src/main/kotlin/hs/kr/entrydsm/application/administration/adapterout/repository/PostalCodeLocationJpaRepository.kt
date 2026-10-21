package hs.kr.entrydsm.application.administration.adapterout.repository

import hs.kr.entrydsm.application.administration.adapterout.entity.PostalCodeLocationJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PostalCodeLocationJpaRepository : JpaRepository<PostalCodeLocationJpaEntity, String>
