package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.command.UpdateArrivalCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExamineeNumberIssueResult
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.policy.ExamineeNumberPolicy
import hs.kr.entrydsm.admin.domain.port.`in`.IssueExamineeNumberUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.DeleteApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantDeletionPort
import hs.kr.entrydsm.admin.domain.port.out.DistancePort
import java.time.Clock
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
// 원본 조회는 누락된 총점을 재산출하여 저장할 수 있다.
@Transactional
class ApplicantService(
    private val applicantRepository: ApplicantRepository,
    private val applicantArrivalPort: ApplicantArrivalPort,
    private val applicantDeletionPort: ApplicantDeletionPort = ApplicantDeletionPort {},
    private val distancePort: DistancePort,
    private val clock: Clock,
    private val screening: hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase? = null,
    private val publication: hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy? = null,
    private val schedules: hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository? = null,
) : ReadApplicantUseCase,
    UpdateApplicantUseCase,
    IssueExamineeNumberUseCase,
    DeleteApplicantUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> =
        applicantRepository.search(filter, pageRequest)

    override fun findDetail(applicantId: Long): ApplicantDetail =
        applicantRepository.findDetailById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)

    @Transactional
    override fun updateArrival(command: UpdateArrivalCommand) {
        val applicant = requireApplicant(command.applicantId)
        if (applicant.isArrived == command.isArrived) return

        applicantArrivalPort.update(command.applicantId, command.isArrived)
        // 원본 도착 변경이 원서 버전을 증가시키므로 같은 트랜잭션에서 갱신된 버전으로 저장한다.
        val updated = requireApplicant(command.applicantId)
        applicantRepository.save(
            updated.copy(
                isArrived = command.isArrived,
                arrivedAt = if (command.isArrived) {
                    applicant.arrivedAt ?: Instant.now(clock)
                } else {
                    null
                },
                updatedAt = Instant.now(clock),
            ),
        )
    }

    @Transactional
    override fun updateStatus(command: UpdateApplicantStatusCommand) {
        // 지원자 조회 전에 잠가 이전 정원 스냅샷으로 동시에 선발하지 않도록 한다.
        if (command.status in setOf(hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS,
                hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_FAIL)) {
            schedules?.findByTitleForUpdate("1차 합격 발표")
                ?: throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        val applicant = requireApplicant(command.applicantId)

        if (command.force) {
            if (command.reason.isNullOrBlank()) {
                throw AdminDomainException(ErrorCode.INVALID_REQUEST_BODY)
            }
            // ponytail: 감사 로그 테이블이 아직 없다. 남길 곳이 생기면 그쪽으로 옮긴다.
            logger.warn(
                "Forced applicant status change [applicantId={}, {} -> {}, reason={}]",
                applicant.id,
                applicant.status,
                command.status,
                command.reason,
            )
        } else if (!applicant.status.canTransitionTo(command.status)) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }

        // 기존 강제 상태 변경도 예비 선발의 정원·발표 잠금을 우회하지 못하게 한다.
        if (command.status in setOf(hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS,
                hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_FAIL)) {
            val first = screening ?: throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
            if (command.status == hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS) first.selectFirst(command.applicantId)
            else first.cancelFirst(command.applicantId)
            return
        }
        if ((command.status == hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.PENDING &&
                applicant.status != hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.PENDING) ||
            (command.status in setOf(hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FINAL_PASS,
                hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FINAL_FAIL) && publication?.isPublished() != true)) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }

        applicantRepository.save(
            applicant.copy(status = command.status, updatedAt = Instant.now(clock)),
        )
    }

    // 원서 우편번호를 거리 계산에 전달하고 기존 발급 정책을 적용한다.
    @Transactional
    override fun issueAll(): ExamineeNumberIssueResult {
        val applicants = applicantRepository.findAll()
        applicants
            .filter { it.examineeNumber != null && !ExamineeNumberPolicy.isValidExistingNumber(it) }
            .forEach { logger.warn("Invalid existing examinee number skipped [applicantId={}]", it.id) }
        val distances = applicants
            .filter { it.isArrived && it.examineeNumber == null && it.admissionType != null && it.region != null }
            .mapNotNull { applicant ->
                applicant.address?.takeIf(String::isNotBlank)
                    ?.let { applicant.id to distancePort.distanceFromSchool(it, applicant.zipCode) }
            }
            .toMap()
        val issuance = ExamineeNumberPolicy.issue(applicants, distances)
        val now = Instant.now(clock)

        applicantRepository.saveAll(
            issuance.issued.map { it.copy(updatedAt = now) } +
                applicants.filter { it.examineeNumber != null && ExamineeNumberPolicy.isValidExistingNumber(it) },
        )

        return ExamineeNumberIssueResult(
            issuedCount = issuance.issued.size,
            skippedCount = issuance.skippedCount,
            totalTargets = issuance.totalTargets,
        )
    }

    /** 원서 삭제와 전형 기록 삭제는 Application DB의 같은 트랜잭션이다. */
    @Transactional
    override fun delete(applicantId: Long) {
        applicantDeletionPort.delete(applicantId)
        applicantRepository.deleteById(applicantId)
    }

    private fun requireApplicant(applicantId: Long): Applicant =
        applicantRepository.findById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
}
