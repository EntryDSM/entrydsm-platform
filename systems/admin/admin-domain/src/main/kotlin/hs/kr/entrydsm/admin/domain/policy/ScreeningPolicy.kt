package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant

/**
 * 합격자 일괄 산출 규칙입니다.
 *
 * 단계별 대상 상태의 지원자만 평가하며, 원서 미도착·수험 번호 미발급·성적 미산출 지원자는
 * 평가에서 제외한다. 합격자는 총점 내림차순으로 정원까지 채우고, 동점이면 접수 번호가
 * 빠른 지원자를 우선한다.
 */
object ScreeningPolicy {

    /**
     * @param applicants 회차에 속한 지원자 전체
     * @param stage 산출 단계
     * @param quota 해당 단계의 합격 정원
     */
    fun evaluate(
        applicants: List<Applicant>,
        stage: ScreeningStage,
        quota: Int,
    ): ScreeningOutcome {
        if (quota < 0) {
            throw AdminDomainException(ErrorCode.INVALID_REQUEST_BODY)
        }

        val candidates = applicants.filter { it.status == stage.from }
        val (evaluable, excluded) = candidates.partition(::isEvaluable)

        val ranked = evaluable.sortedWith(
            compareByDescending<Applicant> { it.score!!.totalScore }.thenBy { it.receiptNumber },
        )

        return ScreeningOutcome(
            passed = ranked.take(quota).map { it.copy(status = stage.pass) },
            failed = ranked.drop(quota).map { it.copy(status = stage.fail) },
            excluded = excluded,
        )
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
