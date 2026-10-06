package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult

fun interface MiddleSchoolRepository {
    fun existsByCode(code: String): Boolean = throw UnsupportedOperationException("학교 코드 검증이 필요합니다")
    fun findMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult
}
