package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.model.Applicant

class DocumentPassCalculator {
    /** 통계도 실제 1순위 선발과 같은 2배수 정원을 적용한다. */
    fun firstPassQuotas(): Map<AdmissionType, Int> =
        DOCUMENT_PASS_QUOTAS.mapValues { (_, quota) -> quota * 2 }

    /** 선발 정원은 2배수로 고정한다. 지역은 원서의 region을 기준으로 한다. */
    fun calculate(applicants: List<Applicant>): Map<Long, PassResultStatus> {
        if (applicants.size <= 64 * 2) {
            return applicants.associate { it.id to PassResultStatus.PASS }
        }

        val ranked = applicants.filter { it.admissionType != null && it.totalScore != null }
            .sortedWith(compareByDescending<Applicant> { it.totalScore }.thenBy { it.id })
        val passedIds = hashSetOf<Long>()
        DOCUMENT_PASS_QUOTAS.forEach { (type, quota) ->
            val candidates = ranked.filter { it.admissionType == type }
            if (type == AdmissionType.REGULAR) {
                candidates.filter { it.region == Region.DAEJEON }.take(16 * 2)
                    .mapTo(passedIds) { it.id }
            }
            val selectedCount = candidates.count { it.id in passedIds }
            candidates.filter { it.id !in passedIds }.take(quota * 2 - selectedCount)
                .mapTo(passedIds) { it.id }
        }
        // 전형별 미달 인원을 이월하지 않고 후순위 정원만 추가 선발한다.
        ranked.filter { it.id !in passedIds }.take(20 * 2).mapTo(passedIds) { it.id }

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
