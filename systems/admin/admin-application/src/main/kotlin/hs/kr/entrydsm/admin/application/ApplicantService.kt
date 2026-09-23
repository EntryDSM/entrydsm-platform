package hs.kr.entrydsm.admin.application

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
import org.springframework.transaction.annotation.Propagation

@Service
@Transactional(readOnly = true)
class ApplicantService(
    private val applicantRepository: ApplicantRepository,
    private val applicantArrivalPort: ApplicantArrivalPort,
    private val applicantDeletionPort: ApplicantDeletionPort = ApplicantDeletionPort {},
    private val distancePort: DistancePort,
    private val clock: Clock,
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
        applicantRepository.save(
            applicant.copy(
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

        applicantRepository.save(
            applicant.copy(status = command.status, updatedAt = Instant.now(clock)),
        )
    }

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
                    ?.let { applicant.id to distancePort.distanceFromSchool(it) }
            }
            .toMap()
        val issuance = ExamineeNumberPolicy.issue(applicants, distances)
        val now = Instant.now(clock)

        applicantRepository.saveAll(issuance.issued.map { it.copy(updatedAt = now) })

        return ExamineeNumberIssueResult(
            issuedCount = issuance.issued.size,
            skippedCount = issuance.skippedCount,
            totalTargets = issuance.totalTargets,
        )
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun delete(applicantId: Long) {
        try {
            applicantDeletionPort.delete(applicantId)
        } catch (exception: AdminDomainException) {
            if (exception.errorCode == ErrorCode.APPLICANT_NOT_FOUND) applicantRepository.deleteById(applicantId)
            throw exception
        }
        applicantRepository.deleteById(applicantId)
    }

    private fun requireApplicant(applicantId: Long): Applicant =
        applicantRepository.findById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
}
