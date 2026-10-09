package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.FinalScreeningResult
import hs.kr.entrydsm.admin.domain.model.ScreeningResult
import hs.kr.entrydsm.application.domain.service.DocumentPassCalculator
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.AdmissionType as ApplicationAdmissionType
import hs.kr.entrydsm.application.domain.enum.Region as ApplicationRegion
import hs.kr.entrydsm.application.domain.model.Applicant as ApplicationApplicant
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
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
    private val clock: Clock,
    @Value("\${admin.screening.first-pass-multiplier}") private val firstPassMultiplier: Double,
) : EvaluateFirstScreeningUseCase,
    EvaluateFinalScreeningUseCase {

    init {
        require(firstPassMultiplier.isFinite() && firstPassMultiplier >= 1.0) {
            "admin.screening.first-pass-multiplier 는 1 이상이어야 한다: $firstPassMultiplier"
        }
    }

    /**
     * 1차(서류) 합격자를 대전 우선·전형별 1순위·후순위 공통 정책으로 산출합니다.
     *
     * 다시 실행하면 이미 1차 결과를 받은 지원자까지 다시 줄 세워 정원 안에서만 합격시킨다.
     * 최종 결과를 받은 지원자가 하나라도 있으면 다시 산출하지 않는다.
     */
    @Transactional
    override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult {
        val applicants = applicantRepository.findAll()
        if (applicants.any { it.status in setOf(ApplicantStatus.FINAL_PASS, ApplicantStatus.FINAL_FAIL) }) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        val (evaluable, excluded) = applicants.partition {
            it.totalScore != null && it.admissionType != null
        }
        val results = DocumentPassCalculator().calculate(
            evaluable.map {
                ApplicationApplicant(
                    id = it.id,
                    accountId = 0,
                    admissionType = when (requireNotNull(it.admissionType)) {
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.GENERAL -> ApplicationAdmissionType.REGULAR
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.MEISTER -> ApplicationAdmissionType.MEISTER
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.SOCIAL -> ApplicationAdmissionType.SOCIAL
                    },
                    region = if (it.region == hs.kr.entrydsm.admin.domain.enum.Region.DAEJEON) {
                        ApplicationRegion.DAEJEON
                    } else ApplicationRegion.NATIONAL,
                    totalScore = it.totalScore,
                )
            },
            firstPassMultiplier,
        )
        val evaluated = evaluable.map {
            it.copy(status = if (results[it.id] == PassResultStatus.PASS) ApplicantStatus.FIRST_PASS else ApplicantStatus.FIRST_FAIL)
        }
        val now = Instant.now(clock)

        if (!command.dryRun) {
            applicantRepository.saveAll(
                evaluated.map { it.copy(updatedAt = now) },
            )
        }

        return ScreeningResult(
            dryRun = command.dryRun,
            passCount = evaluated.count { it.status == ApplicantStatus.FIRST_PASS },
            failCount = evaluated.count { it.status == ApplicantStatus.FIRST_FAIL },
            excludedCount = excluded.size,
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
}
