package hs.kr.entrydsm.application.administration.adapterout.document

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPdfs
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import hs.kr.entrydsm.application.administration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.administration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class LocalAdmissionTicketAdapter(private val documents: ApplicantFileUseCase) : AdmissionTicketPort {
    override fun render(tickets: List<Pair<Long, String?>>): ByteArray = try {
        documents.renderAdmissionTickets(tickets)
    } catch (error: Exception) {
        throw renderFailure(error, ErrorCode.ADMISSION_TICKET_GENERATION_FAILED, tickets.map { it.first })
    }
}

@Component
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class LocalApplicationEssayAdapter(private val documents: ApplicantFileUseCase) : ApplicationEssayPort {
    override fun render(applicantId: Long, examineeNumber: String): ApplicationEssayPdfs = try {
        documents.renderApplicationEssay(applicantId).let { ApplicationEssayPdfs(it.first, it.second) }
    } catch (error: Exception) {
        throw renderFailure(error, ErrorCode.ESSAY_GENERATION_FAILED, listOf(applicantId))
    }
}

private fun renderFailure(error: Exception, fallback: ErrorCode, targets: List<Long>): AdminDomainException {
    val causes = generateSequence<Throwable>(error) { it.cause }.toList()
    val code = when {
        causes.any { it is EvaluationValidationException } -> ErrorCode.APPLICATION_SCORE_INVALID
        causes.any { it is ApplicantNotFoundException } -> ErrorCode.APPLICANT_NOT_FOUND
        causes.any { it is IllegalArgumentException } -> ErrorCode.APPLICATION_FORM_INVALID
        else -> fallback
    }
    return AdminDomainException(code, error, targetIds = targets)
}
