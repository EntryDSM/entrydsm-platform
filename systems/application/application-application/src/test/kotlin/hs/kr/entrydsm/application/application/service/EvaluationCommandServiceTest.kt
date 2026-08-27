package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EvaluationCommandServiceTest {
    @Test
    fun calculateResultSavesScoreForApplicantsAdmissionType() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                admissionType = AdmissionType.REGULAR,
                graduationType = GraduationType.PROSPECTIVE,
                academicRecord = AcademicRecord(
                    volunteerTime = 15,
                    isDsmAlgorithmAwarded = true,
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                        SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                        SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    ),
                ),
            ),
        )
        val service = EvaluationCommandService(repository, ScoreCalculator())

        service.calculateResult(userId = 10L)

        val savedApplicant = requireNotNull(repository.savedApplicant)
        assertEquals(173.0, savedApplicant.totalScore ?: 0.0, 0.0)
        assertNotNull(savedApplicant.totalScoreUpdatedAt)
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
