package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.model.Applicant
import kotlin.math.ceil

class DocumentPassCalculator {
    /** 통계도 실제 1순위 선발 정원과 같은 배수를 적용한다. */
    fun firstPassQuotas(multiplier: Double = 2.0): Map<AdmissionType, Int> {
        require(multiplier.isFinite() && multiplier >= 1.0) { "선발 배수는 유한한 1 이상의 값이어야 합니다." }
        return DOCUMENT_PASS_QUOTAS.mapValues { (_, quota) -> ceil(quota * multiplier).toInt() }
    }

    /** 배수는 호출자가 설정값을 전달한다. 지역은 원서의 region을 기준으로 한다. */
    fun calculate(applicants: List<Applicant>, multiplier: Double = 2.0): Map<Long, PassResultStatus> {
        require(multiplier.isFinite() && multiplier >= 1.0) { "선발 배수는 유한한 1 이상의 값이어야 합니다." }
        fun scaled(quota: Int): Int = ceil(quota * multiplier).toInt()

        if (applicants.size <= scaled(64)) {
            return applicants.associate { it.id to PassResultStatus.PASS }
        }

        val ranked = applicants.filter { it.admissionType != null && it.totalScore != null }
            .sortedWith(compareByDescending<Applicant> { it.totalScore }.thenBy { it.id })
        val passedIds = hashSetOf<Long>()
        DOCUMENT_PASS_QUOTAS.forEach { (type, quota) ->
            val candidates = ranked.filter { it.admissionType == type }
            if (type == AdmissionType.REGULAR) {
                candidates.filter { it.region == Region.DAEJEON }.take(scaled(16))
                    .mapTo(passedIds) { it.id }
            }
            val selectedCount = candidates.count { it.id in passedIds }
            candidates.filter { it.id !in passedIds }.take(scaled(quota) - selectedCount)
                .mapTo(passedIds) { it.id }
        }
        // 전형별 미달 인원을 이월하지 않고 후순위 정원만 추가 선발한다.
        ranked.filter { it.id !in passedIds }.take(scaled(20)).mapTo(passedIds) { it.id }

        return applicants.associate { applicant ->
            applicant.id to if (applicant.id in passedIds) PassResultStatus.PASS else PassResultStatus.FAIL
        }
    }

    private companion object {
        val DOCUMENT_PASS_QUOTAS = mapOf(
            AdmissionType.REGULAR to 32,
            AdmissionType.MEISTER to 10,
            AdmissionType.SOCIAL to 2,
        )
    }
}
