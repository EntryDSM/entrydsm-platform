package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand

interface PassResultPort {
    fun announce(command: AnnouncePassResultsCommand): Int
}
