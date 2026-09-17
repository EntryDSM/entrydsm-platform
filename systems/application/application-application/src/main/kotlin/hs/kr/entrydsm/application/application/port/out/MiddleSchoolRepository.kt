package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult

fun interface MiddleSchoolRepository {
    fun findMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult
}
