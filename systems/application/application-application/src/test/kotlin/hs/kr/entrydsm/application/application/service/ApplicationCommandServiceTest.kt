package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationCommandServiceTest {
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
