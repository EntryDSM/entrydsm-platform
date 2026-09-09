package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import java.time.Instant

/**
 * 모집 지역 × 전형별 모집 정원입니다.
 *
 * 대전/전국과 일반/마이스터/사회통합의 모든 조합이 0 이상으로 채워져야 한다.
 * 경쟁률을 낼 때 분모로 쓰며, 전형별 정원은 두 지역 정원의 합이다.
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

    /** 전형별 정원. 지역 정원을 더한 값이다. */
    val byType: Map<AdmissionType, Int>
        get() = AdmissionType.entries.associateWith { type ->
            Region.entries.sumOf { region -> quotas.getValue(region).getValue(type) }
        }
}
