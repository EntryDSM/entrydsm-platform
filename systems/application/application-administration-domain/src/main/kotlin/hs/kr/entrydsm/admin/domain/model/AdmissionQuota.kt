package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import java.time.Instant
import kotlin.math.ceil

/**
 * 전형별 모집 정원입니다.
 */
data class AdmissionQuota(
    val quotas: Map<AdmissionType, Int>,
    val updatedAt: Instant,
    val updatedBy: String,
) {
    init {
        val complete = AdmissionType.entries.all { type -> (quotas[type] ?: -1) >= 0 }
        if (!complete) {
            throw AdminDomainException(ErrorCode.INVALID_ADMISSION_QUOTA)
        }
    }

    /**
     * 정원에 배수를 곱해 올림한 값입니다. 1차(서류) 선발 인원을 "모집 정원의 N배수"로 낼 때 쓴다.
     */
    fun scaled(multiplier: Double): Map<AdmissionType, Int> =
        quotas.mapValues { (_, quota) -> ceil(quota * multiplier).toInt() }
}
