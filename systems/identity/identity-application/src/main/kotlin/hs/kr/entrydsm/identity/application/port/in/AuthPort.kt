package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult

interface AuthPort {
    fun signup(command: SignupCommand): AccountResult

    fun login(command: LoginCommand): UserSummaryResult

    fun logout(command: LogoutCommand)

    fun refreshToken(command: RefreshTokenCommand): UserSummaryResult

    fun resetPassword(command: PasswordResetCommand)
}
