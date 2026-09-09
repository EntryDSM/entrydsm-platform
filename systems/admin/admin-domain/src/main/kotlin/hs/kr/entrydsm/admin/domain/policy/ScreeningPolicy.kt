package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant

/**
 * 합격자 산출 규칙입니다.
 *
 * 단계별 대상 상태의 지원자만 평가하며, 원서 미도착·수험 번호 미발급·성적 미산출 지원자는
 * 평가에서 제외한다. 지원자를 모집 지역 × 전형 묶음으로 나눠 묶음 안에서 총점 내림차순으로
 * 해당 묶음의 정원까지 채우고, 동점이면 접수 번호가 빠른 지원자를 우선한다.
 */
object ScreeningPolicy {

    /**
     * @param applicants 회차에 속한 지원자 전체
     * @param stage 산출 단계
     * @param quotas 해당 단계의 지역별 → 전형별 합격 정원. 없는 묶음은 정원 0으로 본다
     */
    fun evaluate(
        applicants: List<Applicant>,
        stage: ScreeningStage,
        quotas: Map<Region, Map<AdmissionType, Int>>,
    ): ScreeningOutcome {
        val candidates = applicants.filter { it.status == stage.from }
        val (evaluable, excluded) = candidates.partition(::isEvaluable)

        val passed = mutableListOf<Applicant>()
        val failed = mutableListOf<Applicant>()
        evaluable
            .groupBy { it.region to it.admissionType }
            .forEach { (bucket, group) ->
                val quota = quotas[bucket.first]?.get(bucket.second) ?: 0
                val ranked = group.sortedWith(
                    compareByDescending<Applicant> { it.score!!.totalScore }.thenBy { it.receiptNumber },
                )
                passed += ranked.take(quota).map { it.copy(status = stage.pass) }
                failed += ranked.drop(quota).map { it.copy(status = stage.fail) }
            }

        return ScreeningOutcome(passed = passed, failed = failed, excluded = excluded)
    }

    /**
     * 지원자 한 명의 최종 합격 여부를 산출합니다.
     *
     * 정원 안에 드는지는 전체 순위를 봐야 정해지므로 회차 전체를 함께 받는다.
     * 1차 합격자가 아니거나 평가 조건을 갖추지 못해 산출되지 않은 지원자는 불합격이다.
     *
     * @param applicant 산출 대상 지원자
     * @param applicants 회차에 속한 지원자 전체
     * @param quotas 지역별 → 전형별 최종 합격 정원
     */
    fun evaluateFinal(
        applicant: Applicant,
        applicants: List<Applicant>,
        quotas: Map<Region, Map<AdmissionType, Int>>,
    ): ApplicantStatus {
        val passed = evaluate(applicants, ScreeningStage.FINAL, quotas).passed
        return if (passed.any { it.receiptNumber == applicant.receiptNumber }) {
            ScreeningStage.FINAL.pass
        } else {
            ScreeningStage.FINAL.fail
        }
    }

    private fun isEvaluable(applicant: Applicant): Boolean =
        applicant.isSubmitted && applicant.examineeNumber != null && applicant.score != null
}

/**
 * 합격자 일괄 산출 결과입니다.
 *
 * @property passed 합격 상태가 반영된 지원자 목록
 * @property failed 불합격 상태가 반영된 지원자 목록
 * @property excluded 평가 조건을 갖추지 못해 상태를 바꾸지 않은 지원자 목록
 */
data class ScreeningOutcome(
    val passed: List<Applicant>,
    val failed: List<Applicant>,
    val excluded: List<Applicant>,
)
