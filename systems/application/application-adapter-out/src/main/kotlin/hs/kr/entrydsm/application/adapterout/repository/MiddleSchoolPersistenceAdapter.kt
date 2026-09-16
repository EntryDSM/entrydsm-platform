package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolResult
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class MiddleSchoolPersistenceAdapter(
    private val institutionCodeJpaRepository: InstitutionCodeJpaRepository,
) : MiddleSchoolRepository {
    override fun findMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult {
        val schools = institutionCodeJpaRepository.findByNameContainingOrderByNameAscCodeAsc(
            command.name,
            PageRequest.of(command.page, command.size),
        )
        return MiddleSchoolSearchResult(
            schools = schools.content.map { MiddleSchoolResult(code = it.code, name = it.name) },
            hasNext = schools.hasNext(),
        )
    }
}
