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
        val record = AcademicRecord(
            volunteerTime = 20,
            isDsmAlgorithmAwarded = true,
            isProgrammingCertified = true,
            subjectGrades = linkedMapOf(
                SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
            ),
        )

        // 프로그래밍기능사 가산점 6점은 특별전형에만 붙는다.
        assertEquals(173.0, calculate(AdmissionType.REGULAR, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(119.0, calculate(AdmissionType.SOCIAL, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(119.0, calculate(AdmissionType.MEISTER, GraduationType.PROSPECTIVE, record), 0.0)
    }

    @Test
    fun calculatesGedScores() {
        val record = AcademicRecord(
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
        )

        // 출결·봉사 기록은 무시하고 교과 환산 점수에 비례해서 30점을 준다.
        assertEquals(170.0, calculate(AdmissionType.REGULAR, GraduationType.GED, record), 0.0)
        assertEquals(110.0, calculate(AdmissionType.SOCIAL, GraduationType.GED, record), 0.0)
        assertEquals(110.0, calculate(AdmissionType.MEISTER, GraduationType.GED, record), 0.0)
    }

    @Test
    fun calculatesGedScoresBySixSubjectConversionBands() {
        val record = AcademicRecord(
            gedScores = GedScores(
                koreanScore = 96,
                mathScore = 92,
                englishScore = 95,
                scienceScore = 86,
                societyScore = 100,
                technologyScore = 96,
                historyScore = 0,
            ),
        )

        // (4 + 3 + 4 + 2 + 5 + 4) / 6 / 5 × 80 = 58.667점 → 175% 또는 100% + 비례 30점.
        assertEquals(124.667, calculate(AdmissionType.REGULAR, GraduationType.GED, record), 0.0)
        assertEquals(80.667, calculate(AdmissionType.SOCIAL, GraduationType.GED, record), 0.0)
    }

    @Test
    fun fillsMissingProspectiveSemestersWithAvailableScore() {
        val record = AcademicRecord(
            subjectGrades = linkedMapOf(
                SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
            ),
        )

        assertEquals(155.0, calculate(AdmissionType.REGULAR, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(95.0, calculate(AdmissionType.SOCIAL, GraduationType.PROSPECTIVE, record), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProspectiveApplicantWithoutSubjectGrades() {
        calculate(AdmissionType.REGULAR, GraduationType.PROSPECTIVE, AcademicRecord())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsApplicantWithoutAdmissionType() {
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

    @Test
    fun fillsMissingGraduatedSemesterWithAverageOfReflectedScores() {
        val record = AcademicRecord(
            subjectGrades = linkedMapOf(
                SchoolSemester.THIRD_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
            ),
        )

        assertEquals(155.0, calculate(AdmissionType.REGULAR, GraduationType.GRADUATED, record), 0.0)
        assertEquals(95.0, calculate(AdmissionType.SOCIAL, GraduationType.GRADUATED, record), 0.0)
    }

    @Test
    fun doesNotRoundSubjectBaseBeforeRegularConversion() {
        val record = AcademicRecord(
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
        )

        // 기준점수 4×39/7은 중간 반올림 없이 일반전형에서 39점이 된다.
        assertEquals(69.0, calculate(AdmissionType.REGULAR, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(52.286, calculate(AdmissionType.SOCIAL, GraduationType.PROSPECTIVE, record), 0.0)
    }

    @Test
    fun clampsAttendanceScoreWhenAttendanceCountsAreTooLarge() {
        val record = AcademicRecord(
            absentCount = Int.MAX_VALUE,
            lateCount = Int.MAX_VALUE,
            earlyLeaveCount = Int.MAX_VALUE,
            classAbsenceCount = Int.MAX_VALUE,
            subjectGrades = linkedMapOf(
                SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.A),
                SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
            ),
        )

        assertEquals(140.0, calculate(AdmissionType.REGULAR, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(80.0, calculate(AdmissionType.SOCIAL, GraduationType.PROSPECTIVE, record), 0.0)
        assertEquals(80.0, calculate(AdmissionType.MEISTER, GraduationType.PROSPECTIVE, record), 0.0)
    }

    @Test
    fun returnsZeroWhenAcademicRecordDoesNotExist() {
        assertEquals(
            0.0,
            calculator.calculate(
                Applicant(
                    id = 1L,
                    accountId = 1L,
                    admissionType = AdmissionType.REGULAR,
                ),
            ),
            0.0,
        )
    }

    private fun calculate(
        admissionType: AdmissionType,
        graduationType: GraduationType,
        record: AcademicRecord,
    ): Double =
        calculator.calculate(
            Applicant(
                id = 1L,
                accountId = 1L,
                admissionType = admissionType,
                graduationType = graduationType,
                academicRecord = record,
            ),
        )

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
