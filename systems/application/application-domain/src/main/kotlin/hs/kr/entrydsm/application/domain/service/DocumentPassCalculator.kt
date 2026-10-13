package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.model.Applicant
import kotlin.math.ceil

class DocumentPassCalculator(private val multiplier: Double = 2.0) {
    init {
        require(
            multiplier.isFinite() && multiplier > 0 &&
                ceil((DOCUMENT_PASS_QUOTAS.values.sum() + SECOND_PASS_QUOTA) * multiplier) <= Int.MAX_VALUE,
        ) {
            "1차 합격 배수는 양수이며 전체 정원이 정수 범위 안에 있어야 합니다."
        }
    }

    /** 통계도 실제 1순위 선발과 같은 배수 및 올림을 적용한다. */
    fun firstPassQuotas(): Map<AdmissionType, Int> =
        DOCUMENT_PASS_QUOTAS.mapValues { (_, quota) -> scaledQuota(quota) }

    /** 경쟁률에만 일반전형의 후순위 정원을 포함한다. */
    fun competitionQuotas(): Map<AdmissionType, Int> =
        DOCUMENT_PASS_QUOTAS.mapValues { (type, quota) ->
            scaledQuota(quota + if (type == AdmissionType.REGULAR) SECOND_PASS_QUOTA else 0)
        }

    /** 기존 정원 상수에 동일한 배수를 적용한다. */
    fun totalFirstPassQuota(): Int = scaledQuota(DOCUMENT_PASS_QUOTAS.values.sum() + SECOND_PASS_QUOTA)

    /** 기존 합격자를 보존하고 전형별 잔여 정원과 후순위만 보충한다. */
    fun supplement(applicants: List<Applicant>, existingPassIds: Set<Long>): Map<Long, PassResultStatus> {
        val passedIds = existingPassIds.toMutableSet()
        val ranked = applicants.filter { it.admissionType != null && it.totalScore != null }
            .sortedWith(compareByDescending<Applicant> { it.totalScore }.thenBy { it.id })
        // 기존 합격자를 제외하고 잔여 정원만 선발한다.
        fun select(eligibleApplicants: List<Applicant>, quota: Int) {
            eligibleApplicants.filter { it.id !in passedIds }
                .take(minOf(quota.coerceAtLeast(0), (totalFirstPassQuota() - passedIds.size).coerceAtLeast(0)))
                .mapTo(passedIds) { it.id }
        }
        DOCUMENT_PASS_QUOTAS.forEach { (type, quota) ->
            val eligibleApplicants = ranked.filter { it.admissionType == type }
            val selected = applicants.filter { it.admissionType == type && it.id in passedIds }
            if (type == AdmissionType.REGULAR) {
                val daejeon = eligibleApplicants.filter { it.region == Region.DAEJEON }
                select(daejeon, minOf(scaledQuota(DAEJEON_PRIORITY_QUOTA) - selected.count { it.region == Region.DAEJEON },
                    scaledQuota(quota) - selected.size))
            }
            select(eligibleApplicants, scaledQuota(quota) - applicants.count { it.admissionType == type && it.id in passedIds })
        }
        val existingOverflow = DOCUMENT_PASS_QUOTAS.entries.sumOf { (type, quota) ->
            (applicants.count { it.id in existingPassIds && it.admissionType == type } - scaledQuota(quota)).coerceAtLeast(0)
        }
        select(ranked, scaledQuota(SECOND_PASS_QUOTA) - existingOverflow)
        return applicants.associate { it.id to if (it.id in passedIds) PassResultStatus.PASS else PassResultStatus.FAIL }
    }

    /** 기준 정원에 설정 배수를 적용해 올림한다. 지역은 원서의 region을 기준으로 한다. */
    fun calculate(applicants: List<Applicant>): Map<Long, PassResultStatus> {
        if (applicants.size <= scaledQuota(DOCUMENT_PASS_QUOTAS.values.sum() + SECOND_PASS_QUOTA)) {
            return applicants.associate { it.id to PassResultStatus.PASS }
        }

        val ranked = applicants.filter { it.admissionType != null && it.totalScore != null }
            .sortedWith(compareByDescending<Applicant> { it.totalScore }.thenBy { it.id })
        val passedIds = hashSetOf<Long>()
        DOCUMENT_PASS_QUOTAS.forEach { (type, quota) ->
            val eligibleApplicants = ranked.filter { it.admissionType == type }
            if (type == AdmissionType.REGULAR) {
                eligibleApplicants.filter { it.region == Region.DAEJEON }.take(scaledQuota(DAEJEON_PRIORITY_QUOTA))
                    .mapTo(passedIds) { it.id }
            }
            val selectedCount = eligibleApplicants.count { it.id in passedIds }
            eligibleApplicants.filter { it.id !in passedIds }.take(scaledQuota(quota) - selectedCount)
                .mapTo(passedIds) { it.id }
        }
        // 전형별 미달 인원을 이월하지 않고 후순위 정원만 추가 선발한다.
        ranked.filter { it.id !in passedIds }.take(scaledQuota(SECOND_PASS_QUOTA)).mapTo(passedIds) { it.id }

        return applicants.associate { applicant ->
            applicant.id to if (applicant.id in passedIds) PassResultStatus.PASS else PassResultStatus.FAIL
        }
    }

    /** 소수 배수로 계산한 정원은 사람 수에 맞게 올림한다. */
    private fun scaledQuota(quota: Int): Int = ceil(quota * multiplier).toInt()

    private companion object {
        // 기준 정원은 여기서만 관리하고 선발·통계에 같은 배수를 적용한다.
        const val DAEJEON_PRIORITY_QUOTA = 16
        const val SECOND_PASS_QUOTA = 20
        val DOCUMENT_PASS_QUOTAS = mapOf(
            AdmissionType.REGULAR to 32,
            AdmissionType.MEISTER to 10,
            AdmissionType.SOCIAL to 2,
        )
    }
}
