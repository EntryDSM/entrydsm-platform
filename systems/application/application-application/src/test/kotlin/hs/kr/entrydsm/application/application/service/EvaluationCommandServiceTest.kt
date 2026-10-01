package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import java.time.Instant
import org.junit.Test

class EvaluationCommandServiceTest {
    @Test
    fun academicRecordValidationIdentifiesEachNegativeFieldWithoutSaving() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        val service = EvaluationCommandService(repository, ScoreCalculator(), OPEN)
        val fields = listOf("ABSENT_COUNT", "EARLY_LEAVE_COUNT", "LATE_COUNT", "CLASS_ABSENCE_COUNT", "VOLUNTEER_TIME")
        for ((index, field) in fields.withIndex()) {
            val values = List(5) { if (it == index) -1 else 0 }
            val exception = assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) {
                service.saveAcademicRecord(10L, values[0], values[1], values[2], values[3], values[4])
            }
            assertEquals("APPLICATION_${field}_OUT_OF_RANGE", exception.errorCode.name)
            assertNull(repository.savedApplicant)
        }
        service.saveAcademicRecord(10L, 0, 0, 0, 0, 0)
        assertNotNull(repository.savedApplicant)
    }

    @Test
    fun calculateResultReportsMissingDataWithoutSavingScores() {
        val applicant = Applicant(id = 1L, accountId = 10L, graduationType = GraduationType.PROSPECTIVE)
        val cases = listOf(
            applicant.copy(graduationType = null) to "졸업 구분이 누락되었습니다",
            applicant to "성적 및 출결·봉사활동 기록이 누락되었습니다",
            applicant.copy(academicRecord = AcademicRecord()) to "전형 구분이 누락되었습니다",
            applicant.copy(admissionType = AdmissionType.REGULAR, academicRecord = AcademicRecord()) to "3학년 1학기 성적이 누락되었습니다",
            applicant.copy(admissionType = AdmissionType.REGULAR, graduationType = GraduationType.GED, academicRecord = AcademicRecord()) to "검정고시 성적이 누락되었습니다",
            applicant.copy(admissionType = AdmissionType.REGULAR, graduationType = GraduationType.GRADUATED, academicRecord = AcademicRecord(
                subjectGrades = linkedMapOf(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.X)),
            )) to "반영 가능한 교과 성적이 없습니다. 모든 과목이 X인지 확인해주세요",
            applicant.copy(admissionType = AdmissionType.REGULAR, academicRecord = AcademicRecord(
                subjectGrades = linkedMapOf(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.X)),
            )) to "3학년 1학기 성적 입력은 필수입니다",
        )
        cases.forEach { (incomplete, message) ->
            val repository = FakeApplicantRepository(incomplete)
            val exception = assertThrows(IllegalArgumentException::class.java) {
                EvaluationCommandService(repository, ScoreCalculator(), OPEN).calculateResult(10L)
            }
            assertEquals(message, exception.message)
            assertNull(repository.savedApplicant)
            assertEquals(0.0, incomplete.totalScore, 0.0)
            assertNull(incomplete.totalScoreUpdatedAt)
        }
    }

    @Test
    fun thirdGradeFirstSemesterRequiresAtLeastOneGradeForBothGraduationTypes() {
        listOf(GraduationType.PROSPECTIVE, GraduationType.GRADUATED).forEach { graduationType ->
            val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L, graduationType = graduationType))
            val service = EvaluationCommandService(repository, ScoreCalculator(), OPEN)
            val exception = assertThrows(IllegalArgumentException::class.java) {
                service.saveSubjectGrades(10L, SchoolSemester.THIRD_GRADE_FIRST_SEMESTER, all(SubjectGrade.X))
            }
            assertEquals("3학년 1학기 성적 입력은 필수입니다", exception.message)
            assertNull(repository.savedApplicant)
            service.saveSubjectGrades(10L, SchoolSemester.SECOND_GRADE_FIRST_SEMESTER, all(SubjectGrade.X))
            service.saveSubjectGrades(10L, SchoolSemester.THIRD_GRADE_FIRST_SEMESTER, all(SubjectGrade.X).copy(historyGrade = SubjectGrade.A))
            assertEquals(SubjectGrade.A, repository.savedApplicant?.academicRecord?.subjectGrades?.get(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER)?.historyGrade)
        }
    }

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
        val service = EvaluationCommandService(repository, ScoreCalculator(), OPEN)

        service.calculateResult(accountId = 10L)

        val savedApplicant = requireNotNull(repository.savedApplicant)
        assertEquals(173.0, savedApplicant.totalScore, 0.0)
        assertNotNull(savedApplicant.totalScoreUpdatedAt)
    }

    @Test
    fun outsideApplicationPeriodRejectsScores() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        val service = EvaluationCommandService(repository, ScoreCalculator()) { null }

        assertThrows(ApplicationPeriodClosedException::class.java) {
            service.saveCertificates(accountId = 10L, isDsmAlgorithmAwarded = true, isProgrammingCertified = true)
        }
        assertThrows(ApplicationPeriodClosedException::class.java) { service.calculateResult(accountId = 10L) }
        assertNull(repository.savedApplicant)
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

        // 목록은 쿼리가 거른다. ApplicantSummaryQueryTest 가 덮는다.
        override fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantResult> =
            emptyList()

        override fun findById(id: Long): Applicant? =
            applicant.takeIf { it.id == id }

        override fun findByAccountId(accountId: Long): Applicant? =
            applicant.takeIf { it.accountId == accountId }

        override fun deleteById(id: Long) = Unit
    }

    private companion object {
        val OPEN = ApplicationPeriodReader { Instant.MIN..Instant.MAX }

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
