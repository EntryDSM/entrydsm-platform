package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@DataJpaTest
@ContextConfiguration(classes = [InstitutionCodeJpaRepositoryTest.JpaTestConfig::class])
class InstitutionCodeJpaRepositoryTest {

    @Autowired
    private lateinit var institutionCodeJpaRepository: InstitutionCodeJpaRepository

    @Test
    fun findByNameStartingWithReturnsOnlyPrefixMatches() {
        institutionCodeJpaRepository.saveAll(
            listOf(
                institution(
                    code = "1000002",
                    name = "대전대성중학교",
                ),
                institution(
                    code = "1000001",
                    name = "대전대성중학교",
                ),
                institution(
                    code = "1000003",
                    name = "대전중학교",
                ),
                institution(
                    code = "1000004",
                    name = "서울대전중학교",
                ),
            ),
        )

        institutionCodeJpaRepository.flush()

        val result = institutionCodeJpaRepository.findByNameStartingWith("대전")

        assertEquals(3, result.size)

        // 같은 학교명일 때 code ASC
        assertEquals("1000001", result[0].code)
        assertEquals("1000002", result[1].code)

        // contains가 아니라 prefix 검색이어야 함
        assertEquals(
            false,
            result.any { it.code == "1000004" },
        )
    }

    @Test
    fun persistenceAdapterReturnsAddressAndTotalCount() {
        institutionCodeJpaRepository.save(
            institution(
                code = "2000001",
                name = "대전가람중학교",
                address = null,
            ),
        )

        institutionCodeJpaRepository.flush()

        val adapter = MiddleSchoolPersistenceAdapter(institutionCodeJpaRepository)

        val result = adapter.findMiddleSchools(
            SearchMiddleSchoolCommand(name = "대전"),
        )

        assertEquals(1, result.totalCount)
        assertEquals(1, result.schools.size)
        assertEquals("2000001", result.schools[0].code)
        assertEquals("대전가람중학교", result.schools[0].name)
        assertNull(result.schools[0].address)
    }

    private fun institution(
        code: String,
        name: String,
        address: String? = "대전광역시 중구",
    ) = InstitutionCodeJpaEntity(
        code = code,
        fullName = "대전광역시교육청 대전동부교육지원청 $name",
        name = name,
        postalCode = "12345",
        address = address,
        phoneNumber = "042-123-4567",
        faxNumber = "042-123-4568",
    )

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [InstitutionCodeJpaRepository::class])
    @EntityScan(basePackageClasses = [InstitutionCodeJpaEntity::class])
    class JpaTestConfig
}