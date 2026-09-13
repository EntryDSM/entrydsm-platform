package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import java.time.Instant
import kotlin.math.ceil

/**
 * 모집 지역 × 전형별 모집 정원입니다.
 *
 * 대전/전국과 일반/마이스터/사회통합의 모든 조합이 0 이상으로 채워져야 한다.
 * 최종 합격자 산출과 경쟁률의 기준이며, 전형별 정원은 두 지역 정원의 합이다.
 *
 * @property quotas 지역별 → 전형별 정원
 */
data class AdmissionQuota(
    val quotas: Map<Region, Map<AdmissionType, Int>>,
    val updatedAt: Instant,
    val updatedBy: String,
) {
    init {
        val complete = Region.entries.all { region ->
            AdmissionType.entries.all { type -> (quotas[region]?.get(type) ?: -1) >= 0 }
        }
        if (!complete) {
            throw AdminDomainException(ErrorCode.INVALID_ADMISSION_QUOTA)
        }
    }

    /**
     * 정원에 배수를 곱해 올림한 값입니다. 1차(서류) 선발 인원을 "모집 정원의 N배수"로 낼 때 쓴다.
     */
    fun scaled(multiplier: Double): Map<Region, Map<AdmissionType, Int>> =
        quotas.mapValues { (_, byType) -> byType.mapValues { (_, quota) -> ceil(quota * multiplier).toInt() } }

    /** 전형별 정원. 지역 정원을 더한 값이다. */
    val byType: Map<AdmissionType, Int>
        get() = AdmissionType.entries.associateWith { type ->
            Region.entries.sumOf { region -> quotas.getValue(region).getValue(type) }
        }
}
