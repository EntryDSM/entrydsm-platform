package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import kotlin.math.floor
import kotlin.math.round

class ScoreCalculator {
    fun calculate(applicant: Applicant): Map<AdmissionType, Double> {
        val record = applicant.academicRecord ?: return mapOf(
            AdmissionType.REGULAR to EMPTY_SCORE,
            AdmissionType.SOCIAL to EMPTY_SCORE,
            AdmissionType.MEISTER to EMPTY_SCORE,
        )

        val baseSubjectScore = when (applicant.graduationType) {
            GraduationType.GED -> calculateGedBaseScore(
                requireNotNull(record.gedScores) {
                    "gedScores is required for GED applicants"
                },
            )
            else -> calculateSchoolBaseScore(record, applicant.graduationType)
        }

        val attendanceScore = calculateAttendanceScore(record)
        val volunteerScore = calculateVolunteerScore(record.volunteerTime)
        val regularAdditionalScore = calculateRegularAdditionalScore(record)
        val specialAdditionalScore = calculateSpecialAdditionalScore(record)

        val regularScore = calculateTotalScore(
            subjectScore = baseSubjectScore * REGULAR_SUBJECT_SCORE_MULTIPLIER,
            attendanceScore = attendanceScore,
            volunteerScore = volunteerScore,
            additionalScore = regularAdditionalScore,
            maxScore = REGULAR_FIRST_SCREENING_MAX_SCORE,
        )
        val specialScore = calculateTotalScore(
            subjectScore = baseSubjectScore,
            attendanceScore = attendanceScore,
            volunteerScore = volunteerScore,
            additionalScore = specialAdditionalScore,
            maxScore = SPECIAL_FIRST_SCREENING_MAX_SCORE,
        )

        return mapOf(
            AdmissionType.REGULAR to regularScore,
            AdmissionType.SOCIAL to specialScore,
            AdmissionType.MEISTER to specialScore,
        )
    }

    private fun calculateSchoolBaseScore(
        record: AcademicRecord,
        graduationType: GraduationType?,
    ): Double {
        if (record.subjectGrades.isEmpty()) {
            return EMPTY_SCORE
        }

        val semesterWeights = when (graduationType) {
            GraduationType.GRADUATED -> GRADUATED_SEMESTER_WEIGHTS
            else -> PROSPECTIVE_GRADUATION_SEMESTER_WEIGHTS
        }
        val weightedScores = semesterWeights.mapNotNull { (semester, weight) ->
            record.subjectGrades[semester]
                ?.let(::calculateSemesterAveragePoint)
                ?.let { averagePoint -> (averagePoint / MAX_GRADE_POINT) * weight to weight }
        }
        if (weightedScores.isEmpty()) {
            return EMPTY_SCORE
        }

        val earnedScore = weightedScores.sumOf { it.first }
        val reflectedWeight = weightedScores.sumOf { it.second }
        return roundToThirdDecimal(earnedScore / reflectedWeight * SPECIAL_SUBJECT_MAX_SCORE)
    }

    private fun calculateGedBaseScore(scores: GedScores): Double {
        val average = listOf(
            scores.koreanScore,
            scores.mathScore,
            scores.englishScore,
            scores.scienceScore,
            scores.societyScore,
            scores.technologyScore,
            scores.historyScore,
        ).average()

        return roundToThirdDecimal(average / PERFECT_GED_SCORE * SPECIAL_SUBJECT_MAX_SCORE)
    }

    private fun calculateSemesterAveragePoint(subjectGrades: SubjectGrades): Double {
        val points = listOf(
            subjectGrades.koreanGrade,
            subjectGrades.mathGrade,
            subjectGrades.englishGrade,
            subjectGrades.scienceGrade,
            subjectGrades.societyGrade,
            subjectGrades.technologyGrade,
            subjectGrades.historyGrade,
        ).filterNot { it == SubjectGrade.X }
            .map(::gradeToPoint)

        return if (points.isEmpty()) EMPTY_SCORE else points.average()
    }

    private fun calculateAttendanceScore(record: AcademicRecord): Double {
        val convertedAbsences = record.absentCount + floor(
            (
                record.lateCount +
                    record.earlyLeaveCount +
                    record.classAbsenceCount
                ) / ATTENDANCE_CONVERSION_UNIT.toDouble(),
        ).toInt()

        return (ATTENDANCE_MAX_SCORE - convertedAbsences).coerceAtLeast(EMPTY_SCORE)
    }

    private fun calculateVolunteerScore(volunteerTime: Int): Double {
        return volunteerTime.coerceIn(0, VOLUNTEER_MAX_SCORE.toInt()).toDouble()
    }

    private fun calculateRegularAdditionalScore(record: AcademicRecord): Double {
        return if (record.isDsmAlgorithmAwarded) DSM_ALGORITHM_AWARD_SCORE else EMPTY_SCORE
    }

    private fun calculateSpecialAdditionalScore(record: AcademicRecord): Double {
        var score = EMPTY_SCORE
        if (record.isDsmAlgorithmAwarded) {
            score += DSM_ALGORITHM_AWARD_SCORE
        }
        if (record.isProgrammingCertified) {
            score += PROGRAMMING_CERTIFICATE_SCORE
        }
        return score.coerceAtMost(SPECIAL_ADDITIONAL_MAX_SCORE)
    }

    private fun calculateTotalScore(
        subjectScore: Double,
        attendanceScore: Double,
        volunteerScore: Double,
        additionalScore: Double,
        maxScore: Double,
    ): Double {
        return roundToThirdDecimal(
            (subjectScore + attendanceScore + volunteerScore + additionalScore)
                .coerceIn(EMPTY_SCORE, maxScore),
        )
    }

    private fun gradeToPoint(grade: SubjectGrade): Double {
        return when (grade) {
            SubjectGrade.A -> 5.0
            SubjectGrade.B -> 4.0
            SubjectGrade.C -> 3.0
            SubjectGrade.D -> 2.0
            SubjectGrade.E -> 1.0
            SubjectGrade.X -> 0.0
        }
    }

    private fun roundToThirdDecimal(score: Double): Double {
        return round(score * ROUNDING_SCALE) / ROUNDING_SCALE
    }

    companion object {
        private const val EMPTY_SCORE = 0.0
        private const val MAX_GRADE_POINT = 5.0
        private const val PERFECT_GED_SCORE = 100.0
        private const val SPECIAL_SUBJECT_MAX_SCORE = 80.0
        private const val REGULAR_SUBJECT_SCORE_MULTIPLIER = 1.75
        private const val ATTENDANCE_MAX_SCORE = 15.0
        private const val ATTENDANCE_CONVERSION_UNIT = 3
        private const val VOLUNTEER_MAX_SCORE = 15.0
        private const val DSM_ALGORITHM_AWARD_SCORE = 3.0
        private const val PROGRAMMING_CERTIFICATE_SCORE = 6.0
        private const val SPECIAL_ADDITIONAL_MAX_SCORE = 9.0
        private const val REGULAR_FIRST_SCREENING_MAX_SCORE = 173.0
        private const val SPECIAL_FIRST_SCREENING_MAX_SCORE = 119.0
        private const val ROUNDING_SCALE = 1000.0

        private val PROSPECTIVE_GRADUATION_SEMESTER_WEIGHTS = linkedMapOf(
            SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to 40.0,
            SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to 20.0,
            SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to 20.0,
        )
        private val GRADUATED_SEMESTER_WEIGHTS = linkedMapOf(
            SchoolSemester.THIRD_GRADE_SECOND_SEMESTER to 20.0,
            SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to 20.0,
            SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to 20.0,
            SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to 20.0,
        )
    }
}
