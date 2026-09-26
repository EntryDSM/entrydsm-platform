package hs.kr.entrydsm.identity.adapterin.web.dto.response

import java.time.Instant
import java.time.LocalDate

data class ApplicationResultResponse(
    val applicationNumber: String?,
    val examineeNumber: String?,
    val name: String?,
    val birthDate: LocalDate?,
    val region: String?,
    val admissionType: String?,
    val passStatus: String,
    val passDescription: String,
    val note: String? = null,
    val announcedAt: Instant?,
)
