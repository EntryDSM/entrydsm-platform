package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import java.time.Instant

interface AccountPort {
    fun deleteAccount(command: DeleteAccountCommand)

    fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult

    fun getAuthority(command: ReadAccountCommand): UserSummaryResult

    fun agreeSensitiveInformation(command: ReadAccountCommand): SensitiveAgreeResult
}

data class SensitiveAgreeResult(
    val isSensitiveAgree: Boolean,
    val updatedAt: Instant,
)
