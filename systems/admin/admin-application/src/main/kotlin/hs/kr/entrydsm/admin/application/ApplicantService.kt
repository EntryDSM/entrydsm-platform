package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.command.UpdateArrivalCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExamineeNumberIssueResult
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.policy.ExamineeNumberPolicy
import hs.kr.entrydsm.admin.domain.port.`in`.IssueExamineeNumberUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ApplicantService(
    private val applicantRepository: ApplicantRepository,
    private val clock: Clock,
) : ReadApplicantUseCase,
    UpdateApplicantUseCase,
    IssueExamineeNumberUseCase {

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> =
        applicantRepository.search(filter, pageRequest)

    override fun findById(applicantId: Long): Applicant = requireApplicant(applicantId)

    @Transactional
    override fun updateArrival(command: UpdateArrivalCommand) {
        val applicant = requireApplicant(command.applicantId)
        applicantRepository.save(
            applicant.copy(
                isSubmitted = command.isSubmitted,
                submittedAt = if (command.isSubmitted) {
                    applicant.submittedAt ?: Instant.now(clock)
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
        } else if (!applicant.status.canTransitionTo(command.status)) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }

        applicantRepository.save(
            applicant.copy(status = command.status, updatedAt = Instant.now(clock)),
        )
    }

    @Transactional
    override fun issueAll(): ExamineeNumberIssueResult {
        val issuance = ExamineeNumberPolicy.issue(applicantRepository.findAll())
        val now = Instant.now(clock)

        applicantRepository.saveAll(issuance.issued.map { it.copy(updatedAt = now) })

        return ExamineeNumberIssueResult(
            issuedCount = issuance.issued.size,
            skippedCount = issuance.skippedCount,
            totalTargets = issuance.totalTargets,
        )
    }

    private fun requireApplicant(applicantId: Long): Applicant =
        applicantRepository.findById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
}
