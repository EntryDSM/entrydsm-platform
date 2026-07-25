package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException

internal fun AccountQueryPort.resolveAccount(userId: Long): Account =
    findByUserId(userId) ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)

internal fun ApplicationDataPort.findApplication(account: Account): ApplicationSnapshot =
    findByUserId(account.userId) ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)

internal fun requireValidSignup(command: SignupCommand) {
    if (command.password.isBlank() || command.name.isBlank() || command.phone.isBlank()) {
        throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
    }
}
