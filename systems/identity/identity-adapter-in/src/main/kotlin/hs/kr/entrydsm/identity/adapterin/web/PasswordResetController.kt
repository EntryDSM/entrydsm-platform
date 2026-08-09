package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.request.PasswordResetRequest
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.out.PasswordResetOwnershipVerifier
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Disabled by default until an external ownership-verification port is connected. */
@RestController
@RequestMapping(AuthEndpointPaths.PASSWORD_RESET)
@ConditionalOnProperty(
    prefix = "auth.password-reset",
    name = ["enabled"],
    havingValue = "true",
)
class PasswordResetController(
    private val authPort: AuthPort,
    private val ownershipVerifier: PasswordResetOwnershipVerifier,
) {
    @PatchMapping
    fun resetPassword(
        @Valid @RequestBody request: PasswordResetRequest,
    ): ApiResponse<Unit> {
        val command = PasswordResetCommand(
            loginId = request.loginId,
            name = request.name,
            birthdate = request.birthdate,
            newPassword = request.newPassword,
        )
        if (!ownershipVerifier.verify(command)) {
            throw IdentityDomainException(ErrorCode.INVALID_CREDENTIALS)
        }
        authPort.resetPassword(command)
        return ApiResponse(data = null)
    }
}
