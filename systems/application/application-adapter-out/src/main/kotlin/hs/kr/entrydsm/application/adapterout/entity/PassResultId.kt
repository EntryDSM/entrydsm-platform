package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.ResultType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

@Embeddable
data class PassResultId(
    @Column(name = "applicant_id")
    var applicantId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", length = 16)
    var resultType: ResultType? = null,
) : Serializable
