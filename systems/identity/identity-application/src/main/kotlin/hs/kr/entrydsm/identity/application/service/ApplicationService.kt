package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Clock
import java.time.Instant

class ApplicationService(
    private val applicationDataPort: ApplicationDataPort,
    private val accountQueryPort: AccountQueryPort,
    private val clock: Clock = Clock.systemUTC(),
) : ApplicationPort {
    override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult {
        val userId = resolveUserId(command.userId)
        return applicationDataPort.findByUserId(userId)?.toStatusResult()
            ?: ApplicationStatusResult(ApplicantStatus.NONE, null, now())
    }

    override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult {
        val userId = resolveUserId(command.userId)
        val account = accountQueryPort.findByUserId(userId) ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        val application = applicationDataPort.findResultByUserId(userId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        return ApplicationResultResult(
            passStatus = application.passStatus,
            announcedAt = application.announcedAt,
            applicationNumber = application.applicantId?.toString()?.padStart(4, '0'),
            examineeNumber = application.examineeNumber,
            name = account.profile.name,
            birthDate = account.profile.birthdate,
            region = application.region,
            admissionType = application.admissionType,
        )
    }

    override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult {
        val userId = resolveUserId(command.userId)
        val application = applicationDataPort.cancel(userId, command.reason, now())
        return application.toStatusResult()
    }

    private fun resolveUserId(userId: Long?): Long =
        userId ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)

    private fun now(): Instant = Instant.now(clock)
}
