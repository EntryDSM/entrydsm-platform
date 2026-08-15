package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.model.Applicant

private const val FIRST_EXAMINEE_NUMBER = 100001

/**
 * 수험 번호 일괄 발급 규칙입니다.
 *
 * 원서 원본이 도착한 지원자에게만, 접수 번호 오름차순으로 빈 번호 없이 순차 발급합니다.
 * 이미 번호를 받은 지원자는 재발급하지 않습니다.
 */
object ExamineeNumberPolicy {

    /**
     * 발급 대상 전체를 훑어 새로 번호를 받아야 하는 지원자에게 번호를 채워 반환합니다.
     *
     * @param applicants 회차에 속한 지원자 전체
     */
    fun issue(applicants: List<Applicant>): ExamineeNumberIssuance {
        val targets = applicants.filter { it.isSubmitted }
        val (alreadyIssued, pending) = targets.partition { it.examineeNumber != null }

        var nextNumber = alreadyIssued
            .mapNotNull { it.examineeNumber?.toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: FIRST_EXAMINEE_NUMBER

        val issued = pending
            .sortedBy { it.receiptNumber }
            .map { it.copy(examineeNumber = (nextNumber++).toString()) }

        return ExamineeNumberIssuance(
            issued = issued,
            skippedCount = alreadyIssued.size,
            totalTargets = targets.size,
        )
    }
}

/**
 * 수험 번호 일괄 발급 결과입니다.
 *
 * @property issued 이번 발급으로 번호가 채워진 지원자 목록
 * @property skippedCount 이미 번호가 있어 건너뛴 지원자 수
 * @property totalTargets 발급 대상(원서 도착) 지원자 총 수
 */
data class ExamineeNumberIssuance(
    val issued: List<Applicant>,
    val skippedCount: Int,
    val totalTargets: Int,
)
