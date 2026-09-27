package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.UpdateScorePolicyCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.`in`.ReadScorePolicyUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateScorePolicyUseCase
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 성적 산출 정책을 보관합니다.
 *
 * 지원자 총점은 application 이 전형별 상한에 맞춰 산출하므로 이 정책으로 다시 계산하지 않습니다.
 * 정책은 기록으로만 남습니다.
 */
@Service
@Transactional(readOnly = true)
class ScorePolicyService(
    private val scorePolicyRepository: ScorePolicyRepository,
    private val clock: Clock,
) : ReadScorePolicyUseCase,
    UpdateScorePolicyUseCase {

    override fun findCurrent(): ScorePolicy =
        scorePolicyRepository.findCurrent()
            ?: throw AdminDomainException(ErrorCode.SCORE_POLICY_NOT_FOUND)

    @Transactional
    override fun update(command: UpdateScorePolicyCommand) {
        val nextVersion = (scorePolicyRepository.findCurrent()?.policyVersion ?: 0) + 1
        scorePolicyRepository.save(
            ScorePolicy(
                policyVersion = nextVersion,
                weights = command.weights,
                roundingScale = command.roundingScale,
                effectiveFrom = Instant.now(clock),
                updatedBy = command.updatedBy,
            ),
        )
    }
}
