package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.port.`in`.MiddleSchoolPort
import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository

class MiddleSchoolQueryService(
    private val middleSchoolRepository: MiddleSchoolRepository,
) : MiddleSchoolPort {
    override fun getMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult =
        middleSchoolRepository.findMiddleSchools(command)
}
