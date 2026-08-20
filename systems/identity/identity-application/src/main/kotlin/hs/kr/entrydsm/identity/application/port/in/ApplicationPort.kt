package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult

interface ApplicationPort {
    fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult

    fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult

    fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult
}
