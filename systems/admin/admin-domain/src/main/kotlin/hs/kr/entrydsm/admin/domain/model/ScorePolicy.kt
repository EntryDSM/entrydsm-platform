package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import java.time.Instant

private const val WEIGHT_SUM_TOLERANCE = 1e-6
private val ROUNDING_SCALE_RANGE = 0..6

/**
 * 성적 산출에 쓰는 항목별 가중치입니다. 세 항목의 합은 항상 1이어야 합니다.
 */
data class ScoreWeights(
    val subject: Double,
    val attendance: Double,
    val volunteer: Double,
) {
    init {
        // NaN은 어떤 비교도 false라서 합계 검증만으로는 통과한다. 항목별로 먼저 막는다.
        if (listOf(subject, attendance, volunteer).any { !it.isFinite() || it < 0.0 }) {
            throw AdminDomainException(ErrorCode.INVALID_SCORE_POLICY)
        }
        val sum = subject + attendance + volunteer
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw AdminDomainException(ErrorCode.INVALID_SCORE_POLICY)
        }
    }
}

/**
 * 현재 적용 중인 성적 산출 정책입니다.
 *
 * @property policyVersion 정책을 바꿀 때마다 1씩 오르는 버전
 * @property roundingScale 총점 반올림 소수 자릿수
 */
data class ScorePolicy(
    val id: Long? = null,
    val policyVersion: Int,
    val weights: ScoreWeights,
    val roundingScale: Int,
    val effectiveFrom: Instant,
    val updatedBy: String,
) {
    init {
        if (roundingScale !in ROUNDING_SCALE_RANGE) {
            throw AdminDomainException(ErrorCode.INVALID_SCORE_POLICY)
        }
    }
}
