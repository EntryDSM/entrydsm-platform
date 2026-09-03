package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.UpdateScorePolicyCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.`in`.ReadScorePolicyUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateScorePolicyUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ScorePolicyService(
    private val scorePolicyRepository: ScorePolicyRepository,
    private val applicantRepository: ApplicantRepository,
    private val clock: Clock,
) : ReadScorePolicyUseCase,
    UpdateScorePolicyUseCase {

    override fun findCurrent(): ScorePolicy =
        scorePolicyRepository.findCurrent()
            ?: throw AdminDomainException(ErrorCode.SCORE_POLICY_NOT_FOUND)

    @Transactional
    override fun update(command: UpdateScorePolicyCommand) {
        val nextVersion = (scorePolicyRepository.findCurrent()?.policyVersion ?: 0) + 1
        val policy = scorePolicyRepository.save(
            ScorePolicy(
                policyVersion = nextVersion,
                weights = command.weights,
                roundingScale = command.roundingScale,
                effectiveFrom = Instant.now(clock),
                updatedBy = command.updatedBy,
            ),
        )

        if (command.recalculate) {
            recalculateAll(policy)
        }
    }

    /**
     * 새 정책의 가중치로 모든 지원자의 총점을 다시 계산합니다.
     *
     * ponytail: 동기로 전부 다시 계산한다. 지원자 수가 만 단위가 되면 배치로 뺀다.
     */
    private fun recalculateAll(policy: ScorePolicy) {
        val recalculated = applicantRepository.findAll()
            .filter { it.score != null }
            .map { it.recalculated(policy) }

        applicantRepository.saveAll(recalculated)
    }

    private fun Applicant.recalculated(policy: ScorePolicy): Applicant {
        val current = score!!
        val total = weighted(current.subjectScore, policy.weights.subject) +
            weighted(current.attendanceScore, policy.weights.attendance) +
            weighted(current.volunteerScore, policy.weights.volunteer)

        return copy(
            score = ApplicantScore(
                subjectScore = current.subjectScore,
                attendanceScore = current.attendanceScore,
                volunteerScore = current.volunteerScore,
                totalScore = total
                    .setScale(policy.roundingScale, RoundingMode.HALF_UP)
                    .toDouble(),
            ),
            updatedAt = Instant.now(clock),
        )
    }

    /** Double 곱셈의 이진 오차가 반올림 경계를 넘기지 않도록 BigDecimal로 계산합니다. */
    private fun weighted(score: Double, weight: Double): BigDecimal =
        BigDecimal.valueOf(score).multiply(BigDecimal.valueOf(weight))
}
