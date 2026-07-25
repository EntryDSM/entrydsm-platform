package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.AuthTokenResult

interface AuthPort {
    fun signup(command: SignupCommand): AccountResult

    fun login(command: LoginCommand): AuthTokenResult

    fun logout(command: LogoutCommand)

    fun refreshToken(command: RefreshTokenCommand): AuthTokenResult

    fun resetPassword(command: PasswordResetCommand)
}
