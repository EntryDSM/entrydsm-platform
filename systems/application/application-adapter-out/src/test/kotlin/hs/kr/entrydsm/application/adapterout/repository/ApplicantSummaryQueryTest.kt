package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.SpringBootConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

/**
 * 지원자 목록이 쓰는 조회입니다.
 *
 * 열을 이름으로 맞추므로 생년월일·연락처처럼 같은 타입인 값이 서로 바뀌어도 컴파일은 됩니다.
 * 값이 제자리에 오는지 여기서 확인합니다.
 */
@RunWith(SpringRunner::class)
@DataJpaTest
@ContextConfiguration(classes = [ApplicantSummaryQueryTest.JpaTestConfig::class])
class ApplicantSummaryQueryTest {

    @Autowired
    private lateinit var applicantJpaRepository: ApplicantJpaRepository

    @Autowired
    private lateinit var institutionCodeJpaRepository: InstitutionCodeJpaRepository

    @Test
    fun `원서를 낸 지원자만 지원자 번호 순으로 준다`() {
        save(submitted(accountId = 101))
        save(draft(accountId = 102))
        save(submitted(accountId = 103, status = ApplicantStatus.COMPLETED))

        val summaries = applicantJpaRepository.findSummariesByStatusIn(APPLIED_STATUSES)

        assertEquals(listOf(101L, 103L), summaries.map { it.accountId })
        assertEquals(summaries.map { it.id }.sorted(), summaries.map { it.id })
    }

    @Test
    fun `원서 내용을 제자리에 담는다`() {
        // middle_school_infos.school_code 는 기관코드 표를 참조한다.
        institutionCodeJpaRepository.save(
            InstitutionCodeJpaEntity(
                code = "7031234",
                fullName = "대전광역시교육청 대전서부교육지원청 대덕중학교",
                name = "대덕중학교",
                postalCode = null,
                address = null,
                phoneNumber = null,
                faxNumber = null,
            ),
        )
        save(
            submitted(accountId = 101).apply {
                name = "김철수"
                phoneNumber = "01011112222"
                photoFileId = "photo_1"
                birthdate = LocalDate.of(2010, 3, 1)
                region = Region.DAEJEON
                admissionType = AdmissionType.REGULAR
                graduationType = GraduationType.PROSPECTIVE
                totalScore = 150.5
                submittedAt = SUBMITTED_AT
                gender = Gender.FEMALE
                addressBase = "충청남도 천안시"
                middleSchoolInfo = MiddleSchoolInfo(
                    schoolCode = "7031234",
                    schoolName = "대덕중학교",
                    studentNumber = "30101",
                    schoolPhone = "0421234567",
                    teacherName = "홍길동",
                )
            },
        )

        val summary = applicantJpaRepository.findSummariesByStatusIn(APPLIED_STATUSES).single()

        assertEquals(101L, summary.accountId)
        assertEquals("김철수", summary.name)
        assertEquals("대덕중학교", summary.schoolName)
        assertEquals("01011112222", summary.phoneNumber)
        assertEquals("photo_1", summary.photoFileId)
        assertEquals(LocalDate.of(2010, 3, 1), summary.birthdate)
        assertEquals(Region.DAEJEON, summary.region)
        assertEquals(AdmissionType.REGULAR, summary.admissionType)
        assertEquals(GraduationType.PROSPECTIVE, summary.graduationType)
        assertEquals(150.5, summary.totalScore!!, 0.0)
        assertEquals(ApplicantStatus.SUBMITTED, summary.status)
        assertEquals(SUBMITTED_AT, summary.submittedAt)
        assertEquals(Gender.FEMALE, summary.gender)
        assertEquals("충청남도 천안시", summary.address)
    }

    /** 제출 검증이 중학교를 요구하지 않으므로 낸 원서에도 없을 수 있다. 목록이 그 지원자를 빠뜨리면 안 된다. */
    @Test
    fun `중학교가 없는 원서도 목록에 든다`() {
        save(submitted(accountId = 101))

        val summary = applicantJpaRepository.findSummariesByStatusIn(APPLIED_STATUSES).single()

        assertEquals(101L, summary.accountId)
        assertNull(summary.schoolName)
    }

    private fun save(applicant: Applicant) =
        applicantJpaRepository.saveAndFlush(ApplicantJpaEntity.from(applicant))

    private fun submitted(accountId: Long, status: ApplicantStatus = ApplicantStatus.SUBMITTED) =
        Applicant(id = 0, accountId = accountId, status = status, submittedAt = SUBMITTED_AT)

    private fun draft(accountId: Long) =
        Applicant(id = 0, accountId = accountId, status = ApplicantStatus.DRAFT)

    @SpringBootConfiguration
    @EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class])
    @EntityScan(basePackageClasses = [ApplicantJpaEntity::class])
    class JpaTestConfig

    private companion object {
        val SUBMITTED_AT: LocalDateTime = LocalDateTime.of(2026, 9, 20, 13, 30)
        val APPLIED_STATUSES = setOf(
            ApplicantStatus.SUBMITTED,
            ApplicantStatus.REVIEWING,
            ApplicantStatus.COMPLETED,
        )
    }
}
