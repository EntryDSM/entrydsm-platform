package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.model.ScreeningResult
import hs.kr.entrydsm.admin.domain.policy.ScreeningPolicy
import hs.kr.entrydsm.admin.domain.policy.ScreeningStage
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ScreeningService(
    private val applicantRepository: ApplicantRepository,
    private val clock: Clock,
    @Value("\${admin.screening.first-quota}") private val firstQuota: Int,
    @Value("\${admin.screening.final-quota}") private val finalQuota: Int,
) : EvaluateFirstScreeningUseCase,
    EvaluateFinalScreeningUseCase {

    @Transactional
    override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult =
        evaluate(ScreeningStage.FIRST, firstQuota, command.dryRun)

    @Transactional
    override fun evaluateFinal(command: EvaluateScreeningCommand): ScreeningResult =
        evaluate(ScreeningStage.FINAL, finalQuota, command.dryRun)

    private fun evaluate(stage: ScreeningStage, quota: Int, dryRun: Boolean): ScreeningResult {
        val outcome = ScreeningPolicy.evaluate(applicantRepository.findAll(), stage, quota)
        val now = Instant.now(clock)

        if (!dryRun) {
            applicantRepository.saveAll(
                (outcome.passed + outcome.failed).map { it.copy(updatedAt = now) },
            )
        }

        return ScreeningResult(
            dryRun = dryRun,
            passCount = outcome.passed.size,
            failCount = outcome.failed.size,
            excludedCount = outcome.excluded.size,
            processedAt = now,
        )
    }
}
