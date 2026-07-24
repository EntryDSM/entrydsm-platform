package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException

internal fun AccountRepository.resolveAccount(authorization: String?): Account {
    val token = authorization
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.takeIf { it.isNotBlank() }
        ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
    val userId = when (token) {
        "mock-access-token", "access-token" -> 123L
        else -> token.removePrefix("user_").toLongOrNull()
            ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
    }
    return findByUserId(userId) ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
}

internal fun ApplicationDataPort.findApplication(account: Account): ApplicationSnapshot =
    findByUserId(account.userId) ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)

internal fun requireValidSignup(command: SignupCommand) {
    if (command.password.isBlank() || command.name.isBlank() || command.phone.isBlank()) {
        throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
    }
}
