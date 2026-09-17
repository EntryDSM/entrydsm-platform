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

        assertEquals(170.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(110.0, result.getValue(AdmissionType.SOCIAL), 0.0)
        assertEquals(110.0, result.getValue(AdmissionType.MEISTER), 0.0)
    }

    @Test
    fun calculatesGedScoresBySixSubjectConversionBands() {
        val result = calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                graduationType = GraduationType.GED,
                academicRecord = AcademicRecord(
                    gedScores = GedScores(
                        koreanScore = 96,
                        mathScore = 92,
                        englishScore = 95,
                        scienceScore = 86,
                        societyScore = 100,
                        technologyScore = 96,
                        historyScore = 0,
                    ),
                ),
            ),
        )

        // (4 + 3 + 4 + 2 + 5 + 4) / 6 × 34 or 22.
        assertEquals(124.667, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(80.667, result.getValue(AdmissionType.SOCIAL), 0.0)
    }

    @Test
    fun fillsMissingProspectiveSemestersWithAvailableScore() {
        val result = calculator.calculate(
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

        assertEquals(155.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(95.0, result.getValue(AdmissionType.SOCIAL), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProspectiveApplicantWithoutSubjectGrades() {
        calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                graduationType = GraduationType.PROSPECTIVE,
                academicRecord = AcademicRecord(),
            ),
        )
    }

    @Test
    fun fillsMissingGraduatedSemesterWithAverageOfReflectedScores() {
        val result = calculator.calculate(
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

        assertEquals(155.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(95.0, result.getValue(AdmissionType.SOCIAL), 0.0)
    }

    @Test
    fun doesNotRoundSubjectBaseBeforeRegularConversion() {
        val result = calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                graduationType = GraduationType.PROSPECTIVE,
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to
                            SubjectGrades(
                                koreanGrade = SubjectGrade.A,
                                mathGrade = SubjectGrade.E,
                                englishGrade = SubjectGrade.E,
                                scienceGrade = SubjectGrade.E,
                                societyGrade = SubjectGrade.E,
                                technologyGrade = SubjectGrade.E,
                                historyGrade = SubjectGrade.E,
                            ),
                        SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to
                            all(SubjectGrade.E),
                        SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to
                            SubjectGrades(
                                koreanGrade = SubjectGrade.B,
                                mathGrade = SubjectGrade.E,
                                englishGrade = SubjectGrade.E,
                                scienceGrade = SubjectGrade.E,
                                societyGrade = SubjectGrade.E,
                                technologyGrade = SubjectGrade.E,
                                historyGrade = SubjectGrade.E,
                            ),
                    ),
                    volunteerTime = 15,
                ),
            ),
        )

        // 기준점수 4×39/7은 중간 반올림 없이 일반전형에서 39점이 된다.
        assertEquals(69.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(52.286, result.getValue(AdmissionType.SOCIAL), 0.0)
    }

    @Test
    fun clampsAttendanceScoreWhenAttendanceCountsAreTooLarge() {
        val applicant = Applicant(
            id = 1L,
            accountId = 1L,
            graduationType = GraduationType.PROSPECTIVE,
            academicRecord = AcademicRecord(
                absentCount = Int.MAX_VALUE,
                lateCount = Int.MAX_VALUE,
                earlyLeaveCount = Int.MAX_VALUE,
                classAbsenceCount = Int.MAX_VALUE,
                subjectGrades = linkedMapOf(
                    SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                ),
            ),
        )

        val result = calculator.calculate(applicant)

        assertEquals(140.0, result.getValue(AdmissionType.REGULAR), 0.0)
        assertEquals(80.0, result.getValue(AdmissionType.SOCIAL), 0.0)
        assertEquals(80.0, result.getValue(AdmissionType.MEISTER), 0.0)
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
