package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.model.Applicant

/**
 * 합격자 산출 규칙입니다.
 *
 * 단계별 대상 상태의 지원자만 평가하며, 원서 미도착·수험 번호 미발급·성적 미산출 지원자와
 * 전형이 비어 묶을 수 없는 지원자는 평가에서 제외한다. 지원자를 전형별로
 * 나눠 총점 내림차순으로 해당 전형의 정원까지 채우고, 동점이면 지원자 번호가
 * 빠른 지원자를 우선한다.
 */
object ScreeningPolicy {

    /**
     * @param applicants 회차에 속한 지원자 전체
     * @param stage 산출 단계
     * @param quotas 해당 단계의 전형별 합격 정원. 없는 전형은 정원 0으로 본다
     */
    fun evaluate(
        applicants: List<Applicant>,
        stage: ScreeningStage,
        quotas: Map<AdmissionType, Int>,
    ): ScreeningOutcome {
        val candidates = applicants.filter { it.status == stage.from }
        val (evaluable, excluded) = candidates.partition(::isEvaluable)

        val passed = mutableListOf<Applicant>()
        val failed = mutableListOf<Applicant>()
        evaluable
            .groupBy { it.admissionType }
            .forEach { (type, group) ->
                val quota = quotas[type] ?: 0
                val ranked = group.sortedWith(
                    compareByDescending<Applicant> { it.totalScore!! }.thenBy { it.id },
                )
                passed += ranked.take(quota).map { it.copy(status = stage.pass) }
                failed += ranked.drop(quota).map { it.copy(status = stage.fail) }
            }

        return ScreeningOutcome(passed = passed, failed = failed, excluded = excluded)
    }

    private fun isEvaluable(applicant: Applicant): Boolean =
        applicant.isArrived &&
            applicant.examineeNumber != null &&
            applicant.totalScore != null &&
            applicant.admissionType != null
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
