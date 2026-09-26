package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import java.time.LocalDate

data class ApplicationResultResult(
    val passStatus: PassStatus,
    val announcedAt: Instant?,
    val applicationNumber: String? = null,
    val examineeNumber: String? = null,
    val name: String? = null,
    val birthDate: LocalDate? = null,
    val region: String? = null,
    val admissionType: String? = null,
)
