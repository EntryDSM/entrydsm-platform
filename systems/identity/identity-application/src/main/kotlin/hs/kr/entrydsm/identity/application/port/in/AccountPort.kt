package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult

interface AccountPort {
    fun deleteAccount(command: DeleteAccountCommand)

    fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult
}
