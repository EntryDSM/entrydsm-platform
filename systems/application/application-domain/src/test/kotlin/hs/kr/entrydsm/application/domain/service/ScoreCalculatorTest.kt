package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {
    private val calculator = ScoreCalculator()

    @Test
    fun calculatesProspectiveApplicantScores() {
        val applicant = Applicant(
            id = 1L,
            accountId = 1L,
            graduationType = GraduationType.PROSPECTIVE,
            academicRecord = AcademicRecord(
                volunteerTime = 20,
                isDsmAlgorithmAwarded = true,
                isProgrammingCertified = true,
                subjectGrades = linkedMapOf(
                    SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                ),
            ),
        )

        val result = calculator.calculate(applicant)

        assertEquals(173.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(119.0, result.getValue(AdmissionType.SOCIAL), 0.0)
        assertEquals(119.0, result.getValue(AdmissionType.MEISTER), 0.0)
    }

    @Test
    fun calculatesGedScores() {
        val applicant = Applicant(
            id = 1L,
            accountId = 1L,
            graduationType = GraduationType.GED,
            academicRecord = AcademicRecord(
                absentCount = 10,
                lateCount = 6,
                earlyLeaveCount = 6,
                classAbsenceCount = 6,
                volunteerTime = 15,
                gedScores = GedScores(
                    koreanScore = 100,
                    mathScore = 100,
                    englishScore = 100,
                    scienceScore = 100,
                    societyScore = 100,
                    technologyScore = 100,
                    historyScore = 100,
                ),
            ),
        )

        val result = calculator.calculate(applicant)

        assertEquals(140.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(80.0, result.getValue(AdmissionType.SOCIAL), 0.0)
        assertEquals(80.0, result.getValue(AdmissionType.MEISTER), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteProspectiveSubjectGrades() {
        calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                graduationType = GraduationType.PROSPECTIVE,
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    ),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteGraduatedSubjectGrades() {
        calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                graduationType = GraduationType.GRADUATED,
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                        SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                    ),
                ),
            ),
        )
    }

    @Test
    fun returnsZeroWhenAcademicRecordDoesNotExist() {
        val result = calculator.calculate(Applicant(id = 1L, accountId = 1L))

        assertEquals(0.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(0.0, result.getValue(AdmissionType.SOCIAL), 0.0)
        assertEquals(0.0, result.getValue(AdmissionType.MEISTER), 0.0)
    }

    private fun all(grade: SubjectGrade): SubjectGrades =
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
