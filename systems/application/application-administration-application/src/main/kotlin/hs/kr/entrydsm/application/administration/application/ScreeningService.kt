package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.FinalScreeningResult
import hs.kr.entrydsm.admin.domain.model.ScreeningResult
import hs.kr.entrydsm.admin.domain.policy.ScreeningPolicy
import hs.kr.entrydsm.admin.domain.policy.ScreeningStage
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
@Transactional(readOnly = true)
class ScreeningService(
    private val applicantRepository: ApplicantRepository,
    private val admissionQuotaRepository: AdmissionQuotaRepository,
    private val clock: Clock,
    @Value("\${admin.screening.first-pass-multiplier}") private val firstPassMultiplier: Double,
) : EvaluateFirstScreeningUseCase,
    EvaluateFinalScreeningUseCase {

    init {
        require(firstPassMultiplier >= 1.0) {
            "admin.screening.first-pass-multiplier 는 1 이상이어야 한다: $firstPassMultiplier"
        }
    }

    /**
     * 1차(서류) 합격자를 산출합니다. 묶음별 정원은 모집 정원 × 배수(올림)다.
     *
     * 다시 실행하면 이미 1차 결과를 받은 지원자까지 다시 줄 세워 정원 안에서만 합격시킨다.
     * 최종 결과를 받은 지원자가 하나라도 있으면 다시 산출하지 않는다.
     */
    @Transactional
    override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult {
        val outcome = ScreeningPolicy.evaluate(
            applicantRepository.findAll(),
            ScreeningStage.FIRST,
            currentQuota().scaled(firstPassMultiplier),
        )
        val now = Instant.now(clock)

        if (!command.dryRun) {
            applicantRepository.saveAll(
                (outcome.passed + outcome.failed).map { it.copy(updatedAt = now) },
            )
        }

        return ScreeningResult(
            dryRun = command.dryRun,
            passCount = outcome.passed.size,
            failCount = outcome.failed.size,
            excludedCount = outcome.excluded.size,
            processedAt = now,
        )
    }

    /**
     * 1차 합격자 한 명을 최종 합격자로 등록합니다.
     *
     * 2차(면접) 결과는 시스템에 없어 관리자가 합격자를 고른다. 서버는 순위를 다시 매기지 않고
     * `FIRST_PASS` -> `FINAL_PASS` 로 바꾸기만 한다. 이미 등록한 지원자는 다시 불러도 그대로 둔다.
     * 등록을 되돌리려면 지원자 상태 강제 변경을 쓴다.
     */
    @Transactional
    override fun evaluateFinal(applicantId: Long): FinalScreeningResult {
        val applicant = applicantRepository.findById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
        val now = Instant.now(clock)

        if (applicant.status != ApplicantStatus.FINAL_PASS) {
            if (!applicant.status.canTransitionTo(ApplicantStatus.FINAL_PASS)) {
                throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
            }
            applicantRepository.save(applicant.copy(status = ApplicantStatus.FINAL_PASS, updatedAt = now))
        }

        return FinalScreeningResult(
            applicantId = applicantId,
            status = ApplicantStatus.FINAL_PASS,
            processedAt = now,
        )
    }

    private fun currentQuota(): AdmissionQuota =
        admissionQuotaRepository.find()
            ?: throw AdminDomainException(ErrorCode.ADMISSION_QUOTA_NOT_FOUND)
}
