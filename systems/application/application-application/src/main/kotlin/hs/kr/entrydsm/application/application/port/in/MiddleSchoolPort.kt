package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult

fun interface MiddleSchoolPort {
    fun getMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult
}
