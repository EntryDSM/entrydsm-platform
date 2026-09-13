package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
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
import hs.kr.entrydsm.admin.domain.port.out.AnnouncedPassResult
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.PassResultAnnouncementPort
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ScreeningService(
    private val applicantRepository: ApplicantRepository,
    private val admissionQuotaRepository: AdmissionQuotaRepository,
    private val passResultAnnouncementPort: PassResultAnnouncementPort,
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
            val decided = (outcome.passed + outcome.failed).map { it.copy(updatedAt = now) }
            applicantRepository.saveAll(decided)
            announce(decided.map { AnnouncedPassResult(it.id, it.status) }, now)
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
     * 지원자 한 명의 최종 합격 여부를 산출해 상태에 반영합니다.
     *
     * 정원 안에 드는지 보려면 순위가 필요해 회차 전체를 읽는다.
     * ponytail: 지원자가 수백 명 수준이라 지금은 매 호출마다 전체를 읽어도 충분하다.
     * 규모가 커지면 순위 계산을 쿼리로 내린다.
     */
    @Transactional
    override fun evaluateFinal(applicantId: Long): FinalScreeningResult {
        val applicants = applicantRepository.findAll()
        val applicant = applicants.find { it.id == applicantId }
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)

        val status = ScreeningPolicy.evaluateFinal(applicant, applicants, currentQuota().quotas)
        val now = Instant.now(clock)

        applicantRepository.save(applicant.copy(status = status, updatedAt = now))
        announce(listOf(AnnouncedPassResult(applicantId, status)), now)

        return FinalScreeningResult(
            applicantId = applicantId,
            status = status,
            processedAt = now,
        )
    }

    /**
     * 산출 결과를 원본에 반영합니다.
     *
     * admin DB 에만 적으면 수험생이 보는 합격 조회에 나타나지 않습니다. 반영에 실패하면
     * 예외가 올라가 admin 쪽 저장도 함께 되돌아가, 두 시스템의 판단이 갈리지 않습니다.
     */
    private fun announce(results: List<AnnouncedPassResult>, processedAt: Instant) {
        passResultAnnouncementPort.announce(results, processedAt)
    }

    private fun currentQuota(): AdmissionQuota =
        admissionQuotaRepository.find()
            ?: throw AdminDomainException(ErrorCode.ADMISSION_QUOTA_NOT_FOUND)
}
