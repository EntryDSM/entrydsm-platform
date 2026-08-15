package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.model.ScoreWeights
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ScorePolicyTest {

    @Test
    fun `가중치 합이 1이면 정책을 만든다`() {
        val weights = ScoreWeights(subject = 0.725, attendance = 0.15, volunteer = 0.125)

        assertEquals(0.725, weights.subject, 0.0)
    }

    @Test(expected = AdminDomainException::class)
    fun `가중치 합이 1이 아니면 정책을 거부한다`() {
        ScoreWeights(subject = 0.7, attendance = 0.15, volunteer = 0.2)
    }

    @Test
    fun `가중치 오류는 정책 오류 코드를 전달한다`() {
        val exception = runCatching { ScoreWeights(0.5, 0.5, 0.5) }.exceptionOrNull()

        assertEquals(ErrorCode.INVALID_SCORE_POLICY, (exception as AdminDomainException).errorCode)
    }

    @Test(expected = AdminDomainException::class)
    fun `반올림 자릿수가 범위를 벗어나면 정책을 거부한다`() {
        ScorePolicy(
            policyVersion = 1,
            weights = ScoreWeights(0.7, 0.15, 0.15),
            roundingScale = 9,
            effectiveFrom = Instant.parse("2026-06-01T00:00:00Z"),
            updatedBy = "admin01",
        )
    }
}
