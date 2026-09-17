package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import java.math.BigDecimal
import java.math.RoundingMode

class ScoreCalculator {

    fun calculate(applicant: Applicant): Map<AdmissionType, Double> {
        val record = applicant.academicRecord ?: return emptyScores()

        val baseSubjectScore = when (applicant.graduationType) {
            GraduationType.GED -> calculateGedBaseScore(
                requireNotNull(record.gedScores) {
                    "gedScores is required for GED applicants"
                },
            )

            else -> calculateSchoolBaseScore(
                record = record,
                graduationType = applicant.graduationType,
            )
        }

        val attendanceScore = calculateAttendanceScore(
            record = record,
            graduationType = applicant.graduationType,
        )

        val volunteerScore = calculateVolunteerScore(
            volunteerTime = record.volunteerTime,
            graduationType = applicant.graduationType,
        )

        val regularAdditionalScore =
            calculateRegularAdditionalScore(record)

        val specialAdditionalScore =
            calculateSpecialAdditionalScore(record)

        val regularSubjectScore = if (applicant.graduationType == GraduationType.GED) {
            baseSubjectScore * GED_REGULAR_SUBJECT_SCORE_MULTIPLIER
        } else {
            baseSubjectScore * REGULAR_SUBJECT_SCORE_MULTIPLIER
        }

        val specialSubjectScore = if (applicant.graduationType == GraduationType.GED) {
            baseSubjectScore * GED_SPECIAL_SUBJECT_SCORE_MULTIPLIER
        } else {
            baseSubjectScore
        }

        val regularScore = calculateTotalScore(
            subjectScore = regularSubjectScore,
            attendanceScore = attendanceScore,
            volunteerScore = volunteerScore,
            additionalScore = regularAdditionalScore,
            maxScore = REGULAR_FIRST_SCREENING_MAX_SCORE,
        )

        val specialScore = calculateTotalScore(
            subjectScore = specialSubjectScore,
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

    /**
     * 학교생활기록부 기반 교과 기준점수 계산.
     *
     * 졸업예정자:
     * - 3학년 1학기: 40점
     * - 그 이전 자유학기가 아닌 최근 2개 학기: 각 20점
     *
     * 졸업자:
     * - 3학년 2학기까지 자유학기가 아닌 최근 4개 학기: 각 20점
     *
     * 기준점수 최대 80점.
     */
    private fun calculateSchoolBaseScore(
        record: AcademicRecord,
        graduationType: GraduationType?,
    ): Double {
        return when (graduationType) {
            GraduationType.GRADUATED ->
                calculateGraduatedBaseScore(record)

            else ->
                calculateProspectiveGraduateBaseScore(record)
        }
    }

    /**
     * 졸업예정자.
     *
     * 3-1은 반드시 있어야 한다.
     *
     * 이후 2-2 -> 2-1 -> 1-2 -> 1-1 순서로 확인하면서
     * 실제 반영 가능한 성적이 있는 최근 2개 학기를 선택한다.
     *
     * 따라서 예를 들어 2-1이 자유학기라면:
     *
     * 3-1 = 40점
     * 2-2 = 20점
     * 1-2 = 20점
     */
    private fun calculateProspectiveGraduateBaseScore(
        record: AcademicRecord,
    ): Double {
        val thirdGradeFirstSemester =
            requireNotNull(
                record.subjectGrades[
                    SchoolSemester.THIRD_GRADE_FIRST_SEMESTER
                ],
            ) {
                "3rd grade first semester grades are required " +
                        "for prospective graduates"
            }

        val thirdGradeAverage =
            requireNotNull(
                calculateSemesterAveragePointOrNull(
                    thirdGradeFirstSemester,
                ),
            ) {
                "3rd grade first semester must contain " +
                        "at least one reflected subject grade"
            }

        val previousSemesters =
            PROSPECTIVE_PREVIOUS_SEMESTERS
                .mapNotNull { semester ->
                    val grades =
                        record.subjectGrades[semester]
                            ?: return@mapNotNull null

                    val average =
                        calculateSemesterAveragePointOrNull(grades)
                            ?: return@mapNotNull null

                    SemesterAverage(
                        semester = semester,
                        averagePoint = average,
                    )
                }
                .take(PROSPECTIVE_PREVIOUS_SEMESTER_COUNT)

        /*
         * 정상적인 졸업예정자는 총 3개 학기를 사용하지만,
         * 해외 귀국자 등 반영 가능한 학기가 부족한 경우를 위해
         * 실제 반영된 학기의 총 배점을 기준으로 80점 환산한다.
         */
        val weightedScores = buildList {
            add(
                WeightedSemesterScore(
                    averagePoint = thirdGradeAverage,
                    maxScore = PROSPECTIVE_CURRENT_SEMESTER_MAX_SCORE,
                ),
            )

            previousSemesters.forEach {
                add(
                    WeightedSemesterScore(
                        averagePoint = it.averagePoint,
                        maxScore = OTHER_SEMESTER_MAX_SCORE,
                    ),
                )
            }
        }

        return normalizeSubjectScore(weightedScores)
    }

    /**
     * 졸업자.
     *
     * 3-2 -> 3-1 -> 2-2 -> 2-1 -> 1-2 -> 1-1
     *
     * 순으로 확인하면서 자유학기 등을 제외한
     * 최근 4개 학기를 반영한다.
     */
    private fun calculateGraduatedBaseScore(
        record: AcademicRecord,
    ): Double {
        val semesters =
            GRADUATED_CANDIDATE_SEMESTERS
                .mapNotNull { semester ->
                    val grades =
                        record.subjectGrades[semester]
                            ?: return@mapNotNull null

                    val average =
                        calculateSemesterAveragePointOrNull(grades)
                            ?: return@mapNotNull null

                    WeightedSemesterScore(
                        averagePoint = average,
                        maxScore = OTHER_SEMESTER_MAX_SCORE,
                    )
                }
                .take(GRADUATED_REFLECTED_SEMESTER_COUNT)

        if (semesters.isEmpty()) {
            return EMPTY_SCORE
        }

        return normalizeSubjectScore(semesters)
    }

    /**
     * 학기 평균 평점을 해당 학기의 최대 배점으로 환산한 뒤,
     * 실제 반영된 학기들의 총 배점을 기준으로 80점 환산.
     *
     * 예:
     *
     * 평균평점 5.0, 최대배점 40
     * -> 5 / 5 * 40 = 40
     *
     * 평균평점 4.0, 최대배점 20
     * -> 4 / 5 * 20 = 16
     */
    private fun normalizeSubjectScore(
        semesters: List<WeightedSemesterScore>,
    ): Double {
        if (semesters.isEmpty()) {
            return EMPTY_SCORE
        }

        val earnedScore = semesters.sumOf { semester ->
            semester.averagePoint /
                    MAX_GRADE_POINT *
                    semester.maxScore
        }

        val reflectedMaxScore =
            semesters.sumOf { it.maxScore }

        if (reflectedMaxScore == EMPTY_SCORE) {
            return EMPTY_SCORE
        }

        return earnedScore /
                reflectedMaxScore *
                SPECIAL_SUBJECT_MAX_SCORE
    }

    /** 검정고시 6개 과목의 구간별 환산점수 평균(1~5)을 반환한다. */
    private fun calculateGedBaseScore(
        scores: GedScores,
    ): Double {
        val subjectScores = listOf(
            scores.koreanScore,
            scores.mathScore,
            scores.englishScore,
            scores.scienceScore,
            scores.societyScore,
            scores.technologyScore,
        )

        require(
            subjectScores.all {
                it.toDouble() in MIN_GED_SCORE..PERFECT_GED_SCORE
            },
        ) {
            "GED subject scores must be between 0 and 100"
        }

        return subjectScores
            .map(::gedScoreToPoint)
            .average()
    }

    private fun gedScoreToPoint(score: Int): Double = when {
        score >= 98 -> 5.0
        score >= 94 -> 4.0
        score >= 90 -> 3.0
        score >= 86 -> 2.0
        else -> 1.0
    }

    /**
     * 한 학기의 교과 평균평점.
     *
     * X는 반영하지 않는다.
     *
     * 모든 과목이 X라면 해당 학기는 자유학기 또는
     * 미반영 학기로 간주하여 null을 반환한다.
     */
    private fun calculateSemesterAveragePointOrNull(
        subjectGrades: SubjectGrades,
    ): Double? {
        val points = listOf(
            subjectGrades.koreanGrade,
            subjectGrades.mathGrade,
            subjectGrades.englishGrade,
            subjectGrades.scienceGrade,
            subjectGrades.societyGrade,
            subjectGrades.technologyGrade,
            subjectGrades.historyGrade,
        )
            .filterNot { it == SubjectGrade.X }
            .map(::gradeToPoint)

        if (points.isEmpty()) {
            return null
        }

        return points.average()
    }

    /**
     * 환산결석
     *
     * 미인정 결석
     * +
     * (미인정 지각 + 미인정 조퇴 + 미인정 결과) / 3
     *
     * 정수 나눗셈이므로 나머지는 버린다.
     *
     * 출석점수 = 15 - 환산결석
     */
    private fun calculateAttendanceScore(
        record: AcademicRecord,
        graduationType: GraduationType?,
    ): Double {
        if (graduationType == GraduationType.GED) {
            return EMPTY_SCORE
        }

        require(record.absentCount >= 0) {
            "absentCount cannot be negative"
        }
        require(record.lateCount >= 0) {
            "lateCount cannot be negative"
        }
        require(record.earlyLeaveCount >= 0) {
            "earlyLeaveCount cannot be negative"
        }
        require(record.classAbsenceCount >= 0) {
            "classAbsenceCount cannot be negative"
        }

        val convertedFromAttendanceEvents =
            (
                    record.lateCount.toLong() +
                            record.earlyLeaveCount.toLong() +
                            record.classAbsenceCount.toLong()
                    ) / ATTENDANCE_CONVERSION_UNIT

        val convertedAbsences =
            record.absentCount.toLong() +
                    convertedFromAttendanceEvents

        return (
                ATTENDANCE_MAX_SCORE -
                        convertedAbsences.toDouble()
                )
            .coerceIn(
                minimumValue = EMPTY_SCORE,
                maximumValue = ATTENDANCE_MAX_SCORE,
            )
    }

    private fun calculateVolunteerScore(
        volunteerTime: Int,
        graduationType: GraduationType?,
    ): Double {
        if (graduationType == GraduationType.GED) {
            return EMPTY_SCORE
        }

        require(volunteerTime >= 0) {
            "volunteerTime cannot be negative"
        }

        return volunteerTime
            .coerceAtMost(VOLUNTEER_MAX_SCORE.toInt())
            .toDouble()
    }

    private fun calculateRegularAdditionalScore(
        record: AcademicRecord,
    ): Double {
        return if (record.isDsmAlgorithmAwarded) {
            DSM_ALGORITHM_AWARD_SCORE
        } else {
            EMPTY_SCORE
        }
    }

    private fun calculateSpecialAdditionalScore(
        record: AcademicRecord,
    ): Double {
        var score = EMPTY_SCORE

        if (record.isDsmAlgorithmAwarded) {
            score += DSM_ALGORITHM_AWARD_SCORE
        }

        if (record.isProgrammingCertified) {
            score += PROGRAMMING_CERTIFICATE_SCORE
        }

        return score.coerceAtMost(
            SPECIAL_ADDITIONAL_MAX_SCORE,
        )
    }

    private fun calculateTotalScore(
        subjectScore: Double,
        attendanceScore: Double,
        volunteerScore: Double,
        additionalScore: Double,
        maxScore: Double,
    ): Double {
        val score =
            subjectScore +
                    attendanceScore +
                    volunteerScore +
                    additionalScore

        return roundToThirdDecimal(
            score.coerceIn(
                minimumValue = EMPTY_SCORE,
                maximumValue = maxScore,
            ),
        )
    }

    private fun gradeToPoint(
        grade: SubjectGrade,
    ): Double {
        return when (grade) {
            SubjectGrade.A -> 5.0
            SubjectGrade.B -> 4.0
            SubjectGrade.C -> 3.0
            SubjectGrade.D -> 2.0
            SubjectGrade.E -> 1.0
            SubjectGrade.X -> EMPTY_SCORE
        }
    }

    /**
     * 일반적인 "반올림" 동작을 명확하게 하기 위해
     * Double + kotlin.math.round 대신 HALF_UP 사용.
     */
    private fun roundToThirdDecimal(
        score: Double,
    ): Double {
        return BigDecimal
            .valueOf(score)
            .setScale(
                SCORE_DECIMAL_SCALE,
                RoundingMode.HALF_UP,
            )
            .toDouble()
    }

    private fun emptyScores(): Map<AdmissionType, Double> {
        return mapOf(
            AdmissionType.REGULAR to EMPTY_SCORE,
            AdmissionType.SOCIAL to EMPTY_SCORE,
            AdmissionType.MEISTER to EMPTY_SCORE,
        )
    }

    private data class SemesterAverage(
        val semester: SchoolSemester,
        val averagePoint: Double,
    )

    private data class WeightedSemesterScore(
        val averagePoint: Double,
        val maxScore: Double,
    )

    companion object {
        private const val EMPTY_SCORE = 0.0

        private const val MAX_GRADE_POINT = 5.0

        private const val MIN_GED_SCORE = 0.0
        private const val PERFECT_GED_SCORE = 100.0

        private const val SPECIAL_SUBJECT_MAX_SCORE = 80.0

        private const val REGULAR_SUBJECT_SCORE_MULTIPLIER =
            1.75

        private const val GED_REGULAR_SUBJECT_SCORE_MULTIPLIER =
            34.0

        private const val GED_SPECIAL_SUBJECT_SCORE_MULTIPLIER =
            22.0

        private const val PROSPECTIVE_CURRENT_SEMESTER_MAX_SCORE =
            40.0

        private const val OTHER_SEMESTER_MAX_SCORE =
            20.0

        private const val PROSPECTIVE_PREVIOUS_SEMESTER_COUNT =
            2

        private const val GRADUATED_REFLECTED_SEMESTER_COUNT =
            4

        private const val ATTENDANCE_MAX_SCORE =
            15.0

        private const val ATTENDANCE_CONVERSION_UNIT =
            3L

        private const val VOLUNTEER_MAX_SCORE =
            15.0

        private const val DSM_ALGORITHM_AWARD_SCORE =
            3.0

        private const val PROGRAMMING_CERTIFICATE_SCORE =
            6.0

        private const val SPECIAL_ADDITIONAL_MAX_SCORE =
            9.0

        private const val REGULAR_FIRST_SCREENING_MAX_SCORE =
            173.0

        private const val SPECIAL_FIRST_SCREENING_MAX_SCORE =
            119.0

        private const val SCORE_DECIMAL_SCALE =
            3

        /**
         * 졸업예정자는 3-1이 고정이고,
         * 그보다 이전 학기 중 최근 비자유학기 2개 선택.
         */
        private val PROSPECTIVE_PREVIOUS_SEMESTERS = listOf(
            SchoolSemester.SECOND_GRADE_SECOND_SEMESTER,
            SchoolSemester.SECOND_GRADE_FIRST_SEMESTER,
            SchoolSemester.FIRST_GRADE_SECOND_SEMESTER,
            SchoolSemester.FIRST_GRADE_FIRST_SEMESTER,
        )

        /**
         * 졸업자는 3-2까지 포함하여
         * 최근 비자유학기 4개 선택.
         */
        private val GRADUATED_CANDIDATE_SEMESTERS = listOf(
            SchoolSemester.THIRD_GRADE_SECOND_SEMESTER,
            SchoolSemester.THIRD_GRADE_FIRST_SEMESTER,
            SchoolSemester.SECOND_GRADE_SECOND_SEMESTER,
            SchoolSemester.SECOND_GRADE_FIRST_SEMESTER,
            SchoolSemester.FIRST_GRADE_SECOND_SEMESTER,
            SchoolSemester.FIRST_GRADE_FIRST_SEMESTER,
        )
    }
}
