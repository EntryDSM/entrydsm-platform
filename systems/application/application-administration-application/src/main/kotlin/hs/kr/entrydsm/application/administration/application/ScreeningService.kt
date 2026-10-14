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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
@Transactional(readOnly = true)
class ScreeningService(
    private val applicantRepository: ApplicantRepository,
    private val clock: Clock,
    private val documentPassCalculator: DocumentPassCalculator = DocumentPassCalculator(),
    private val schedules: hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository? = null,
) : EvaluateFirstScreeningUseCase,
    EvaluateFinalScreeningUseCase {

    /** 발표 전 부족분만 예비 선발한다. */
    @Transactional
    override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult {
        requireBeforeAnnouncement()
        return supplement(command)
    }

    /** 일정 점유 트랜잭션 안에서 기존 합격자를 보존하고 결과를 공개한다. */
    @Transactional
    override fun publishFirst(): ScreeningResult {
        val result = supplement(EvaluateScreeningCommand(false), publish = true)
        applicantRepository.publishFirstResults(result.processedAt)
        return result
    }

    /** 발표와 수동 변경은 같은 일정 행 잠금으로 직렬화한다. */
    private fun requireBeforeAnnouncement() {
        val schedule = schedules?.findByTitleForUpdate("1차 합격 발표")
            ?: throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        val now = java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneId.of("Asia/Seoul"))
        if (!now.isBefore(schedule.startAt) || schedule.firstScreeningProcessedAt != null) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
    }

    /** 개인 지정은 전체 정원만 제한하고 취소된 지원자도 다시 지정할 수 있다. */
    @Transactional
    override fun selectFirst(applicantId: Long) = changeFirst(applicantId, ApplicantStatus.FIRST_PASS)

    /** 취소 결과를 FAIL로 남겨 자동 보충에서 다시 선발하지 않는다. */
    @Transactional
    override fun cancelFirst(applicantId: Long) = changeFirst(applicantId, ApplicantStatus.FIRST_FAIL)

    private fun changeFirst(applicantId: Long, target: ApplicantStatus) {
        require(applicantId > 0)
        requireBeforeAnnouncement()
        val applicants = applicantRepository.findAll()
        if (applicants.any { it.status in setOf(ApplicantStatus.FINAL_PASS, ApplicantStatus.FINAL_FAIL) }) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        val applicant = applicants.find { it.id == applicantId }
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
        if (applicant.status == target) return
        if (target == ApplicantStatus.FIRST_PASS) {
            if (applicant.totalScore == null || applicant.admissionType == null || applicant.submittedAt == null ||
                applicants.count { it.status == ApplicantStatus.FIRST_PASS } >= documentPassCalculator.totalFirstPassQuota()) {
                throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
            }
        } else if (applicant.status != ApplicantStatus.FIRST_PASS) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        applicantRepository.save(applicant.copy(status = target, updatedAt = Instant.now(clock)))
    }

    /** 점수가 부족한 기존 합격자도 그대로 보존하며 FAIL은 자동 재선발하지 않는다. */
    private fun supplement(command: EvaluateScreeningCommand, publish: Boolean = false): ScreeningResult {
        val applicants = applicantRepository.findAll()
        if (applicants.any { it.status in setOf(ApplicantStatus.FINAL_PASS, ApplicantStatus.FINAL_FAIL) }) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        val existing = applicants.filter { it.status == ApplicantStatus.FIRST_PASS }
        val eligibleApplicants = applicants.filter { it.status == ApplicantStatus.PENDING && it.totalScore != null &&
            it.admissionType != null && it.submittedAt != null }
        val selectable = existing + eligibleApplicants
        val results = if (existing.size >= documentPassCalculator.totalFirstPassQuota()) emptyMap() else
            documentPassCalculator.supplement(selectable.map {
                ApplicationApplicant(id = it.id, accountId = 0,
                    admissionType = when (it.admissionType) {
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.GENERAL -> ApplicationAdmissionType.REGULAR
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.MEISTER -> ApplicationAdmissionType.MEISTER
                        hs.kr.entrydsm.admin.domain.enum.AdmissionType.SOCIAL -> ApplicationAdmissionType.SOCIAL
                        null -> null
                    },
                    region = if (it.region == hs.kr.entrydsm.admin.domain.enum.Region.DAEJEON) ApplicationRegion.DAEJEON else ApplicationRegion.NATIONAL,
                    totalScore = it.totalScore)
            }, existing.mapTo(hashSetOf()) { it.id })
        val now = Instant.now(clock)
        val evaluated = eligibleApplicants.map { it.copy(status = if (results[it.id] == PassResultStatus.PASS)
            ApplicantStatus.FIRST_PASS else ApplicantStatus.FIRST_FAIL, updatedAt = now) }
        if (!command.dryRun) applicantRepository.saveAll(if (publish) evaluated else
            evaluated.filter { it.status == ApplicantStatus.FIRST_PASS })
        return ScreeningResult(command.dryRun,
            existing.size + evaluated.count { it.status == ApplicantStatus.FIRST_PASS },
            applicants.count { it.status == ApplicantStatus.FIRST_FAIL } + evaluated.count { it.status == ApplicantStatus.FIRST_FAIL },
            applicants.size - existing.size - eligibleApplicants.size - applicants.count { it.status == ApplicantStatus.FIRST_FAIL }, now)
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
        val schedule = schedules?.findByTitleForUpdate("1차 합격 발표")
            ?: throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        val nowKst = java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneId.of("Asia/Seoul"))
        if (schedule.firstScreeningProcessedAt == null || nowKst.isBefore(schedule.startAt)) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        // 최종 발표 처리와 직렬화해 발표 직전 등록이 공개 이벤트에서 빠지지 않게 한다.
        schedules?.findByTitleForUpdate("최종 합격 발표")
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
