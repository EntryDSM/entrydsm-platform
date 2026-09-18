package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolResult
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository
import org.springframework.stereotype.Repository

@Repository
class MiddleSchoolPersistenceAdapter(
    private val institutionCodeJpaRepository: InstitutionCodeJpaRepository,
) : MiddleSchoolRepository {
    override fun findMiddleSchools(command: SearchMiddleSchoolCommand): MiddleSchoolSearchResult {
        val schools = institutionCodeJpaRepository.findByNameStartingWith(command.name)
        return MiddleSchoolSearchResult(
            schools = schools.map {
                MiddleSchoolResult(
                    code = it.institutionCode,
                    name = it.institutionName,
                    address = it.roadAddress
            ) },
            totalCount = schools.size,
        )
    }
}
