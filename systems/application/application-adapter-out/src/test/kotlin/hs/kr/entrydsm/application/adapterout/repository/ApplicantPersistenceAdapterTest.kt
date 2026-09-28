package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import jakarta.persistence.EntityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

/**
 * 원서 서식이 읽는 지원자 전문 조회입니다.
 *
 * 출신지역에 찍을 중학교 소재지는 원서에 저장하지 않고 기관코드 표에서 읽습니다.
 * 영속성 컨텍스트를 비우고 읽어야 기관코드가 지연 프록시로 오므로, 프록시를 거쳐도 주소가 차는지 봅니다.
 */
@RunWith(SpringRunner::class)
@DataJpaTest(properties = ["security.pii.encryption-key-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="])
@ContextConfiguration(classes = [ApplicantPersistenceAdapterTest.JpaTestConfig::class])
class ApplicantPersistenceAdapterTest {

    @Autowired
    private lateinit var applicantJpaRepository: ApplicantJpaRepository

    @Autowired
    private lateinit var institutionCodeJpaRepository: InstitutionCodeJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `개인정보는 암호화해서 저장하고 평문으로 조회한다`() {
        val applicant = Applicant(
            id = 0,
            accountId = 100,
            name = "홍길동",
            phoneNumber = "01012345678",
            guardianName = "홍보호",
            guardianPhoneNumber = "01087654321",
            addressBase = "대전광역시 유성구",
            addressDetail = "101동 101호",
            zipCode = "34111",
        )
        applicantJpaRepository.saveAndFlush(ApplicantJpaEntity.from(applicant))
        entityManager.clear()

        val stored = entityManager.createNativeQuery(
            "select name, phone_number, guardian_name, guardian_phone_number, address_base, address_detail, zip_code " +
                "from applicants where account_id = 100",
        ).singleResult as Array<*>
        listOf(
            applicant.name,
            applicant.phoneNumber,
            applicant.guardianName,
            applicant.guardianPhoneNumber,
            applicant.addressBase,
            applicant.addressDetail,
            applicant.zipCode,
        ).zip(stored).forEach { (plain, encrypted) -> assertNotEquals(plain, encrypted) }

        assertEquals(applicant.name, applicantJpaRepository.findByAccountId(100)?.name)
        assertEquals(applicant.addressDetail, applicantJpaRepository.findByAccountId(100)?.addressDetail)
    }

    @Test
    fun `중학교 소재지를 기관코드 표에서 채운다`() {
        institutionCodeJpaRepository.save(
            InstitutionCodeJpaEntity(
                code = SCHOOL_CODE,
                fullName = "대전광역시교육청 대전서부교육지원청 대덕중학교",
                name = "대덕중학교",
                postalCode = "34111",
                address = SCHOOL_ADDRESS,
                phoneNumber = null,
                faxNumber = null,
            ),
        )
        applicantJpaRepository.save(
            ApplicantJpaEntity.from(
                Applicant(
                    id = 0,
                    accountId = 101,
                    middleSchoolInfo = MiddleSchoolInfo(
                        schoolCode = SCHOOL_CODE,
                        schoolName = "대덕중학교",
                        studentNumber = "30101",
                        schoolPhone = "0421234567",
                        teacherName = "홍길동",
                    ),
                ),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val middleSchool = ApplicantPersistenceAdapter(applicantJpaRepository)
            .findByAccountId(101)?.middleSchoolInfo

        assertEquals(SCHOOL_CODE, middleSchool?.schoolCode)
        assertEquals(SCHOOL_ADDRESS, middleSchool?.schoolAddress)
    }

    @Test
    fun `원서와 연관 데이터를 함께 삭제한다`() {
        institutionCodeJpaRepository.save(
            InstitutionCodeJpaEntity(
                code = SCHOOL_CODE,
                fullName = "대덕중학교",
                name = "대덕중학교",
                postalCode = null,
                address = null,
                phoneNumber = null,
                faxNumber = null,
            ),
        )
        val saved = applicantJpaRepository.saveAndFlush(
            ApplicantJpaEntity.from(
                Applicant(
                    id = 0,
                    accountId = 102,
                    middleSchoolInfo = MiddleSchoolInfo(SCHOOL_CODE, "중학교", "30101", "0421234567", "담임"),
                ),
            ),
        )

        ApplicantPersistenceAdapter(applicantJpaRepository).deleteById(requireNotNull(saved.id))
        entityManager.flush()

        assertFalse(applicantJpaRepository.existsById(requireNotNull(saved.id)))
    }

    @SpringBootConfiguration
    @EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class])
    @EntityScan(basePackageClasses = [ApplicantJpaEntity::class])
    class JpaTestConfig

    private companion object {
        const val SCHOOL_CODE = "7031234"
        const val SCHOOL_ADDRESS = "대전광역시 유성구 가정북로 76"
    }
}
