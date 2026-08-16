package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Clock
import java.time.Instant

class ApplicationService(
    private val applicationDataPort: ApplicationDataPort,
    private val clock: Clock = Clock.systemUTC(),
) : ApplicationPort {
    override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult {
        val userId = resolveUserId(command.userId)
        return applicationDataPort.findByUserId(userId)?.toStatusResult()
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
    }

    override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult {
        val userId = resolveUserId(command.userId)
        val application = applicationDataPort.findByUserId(userId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (application.passStatus == PassStatus.NOT_ANNOUNCED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_RESULT_NOT_AVAILABLE)
        }
        return ApplicationResultResult(application.passStatus, application.announcedAt)
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
