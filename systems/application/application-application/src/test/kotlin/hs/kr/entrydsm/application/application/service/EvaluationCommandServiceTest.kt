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
        assertEquals(173.0, savedApplicant.totalScore ?: 0.0, 0.0)
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
