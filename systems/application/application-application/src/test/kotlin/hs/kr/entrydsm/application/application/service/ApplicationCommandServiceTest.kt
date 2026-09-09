package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationCommandServiceTest {
    @Test
    fun submitAndCancelPersistLifecycle() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                admissionType = AdmissionType.REGULAR,
                name = "홍길동",
                guardianName = "보호자",
                introduction = "소개",
                studyPlan = "학업 계획",
            ),
        )
        val service = ApplicationCommandService(repository)

        service.submit(userId = 10L)
        assertEquals(ApplicantStatus.SUBMITTED, repository.savedApplicant?.status)
        assertNotNull(repository.savedApplicant?.submittedAt)

        val canceled = service.cancel(10L, "개인 사유")
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", repository.savedApplicant?.cancelReason)
    }

    @Test
    fun cancelRejectsDraftApplication() {
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)))

        assertThrows(ApplicationCancelNotAllowedException::class.java) {
            service.cancel(10L, null)
        }
    }

    @Test
    fun updateTypeClearsMiddleSchoolInfoAndSubjectGradesWhenChangedToGed() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                graduationType = GraduationType.PROSPECTIVE,
                middleSchoolInfo = MiddleSchoolInfo(
                    schoolName = "대덕중학교",
                    studentNumber = "30101",
                    schoolPhone = "042-000-0000",
                    teacherName = "담임",
                ),
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    ),
                ),
            ),
        )
        val service = ApplicationCommandService(repository)

        service.updateType(
            applicantId = 1L,
            userId = 10L,
            admissionType = AdmissionType.REGULAR,
            region = Region.DAEJEON,
            graduationType = GraduationType.GED,
            graduationDate = null,
        )

        val savedApplicant = requireNotNull(repository.savedApplicant)
        assertNull(savedApplicant.middleSchoolInfo)
        assertTrue(savedApplicant.academicRecord?.subjectGrades?.isEmpty() == true)
    }

    private class FakeApplicantRepository(
        private var applicant: Applicant,
    ) : ApplicantRepository {
        var savedApplicant: Applicant? = null

        override fun save(applicant: Applicant): Applicant {
            savedApplicant = applicant
            this.applicant = applicant
            return applicant
        }

        override fun findById(id: Long): Applicant? =
            applicant.takeIf { it.id == id }

        override fun findByAccountId(accountId: Long): Applicant? =
            applicant.takeIf { it.accountId == accountId }
    }

    private companion object {
        fun all(grade: SubjectGrade): SubjectGrades =
            SubjectGrades(
                koreanGrade = grade,
                mathGrade = grade,
                englishGrade = grade,
                scienceGrade = grade,
                societyGrade = grade,
                technologyGrade = grade,
                historyGrade = grade,
            )
    }
}
