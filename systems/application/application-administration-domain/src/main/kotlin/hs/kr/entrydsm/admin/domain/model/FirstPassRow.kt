package hs.kr.entrydsm.admin.domain.model

data class FirstPassRow(
    val receiptNumber: String,
    val combinedCode: String? = null,
    val admissionType: String? = null,
    val region: String? = null,
    val specialAdmissionType: String? = null,
    val name: String? = null,
    val birthDate: String? = null,
    val address: String? = null,
    val phoneNumber: String? = null,
    val gender: String? = null,
    val graduationStatus: String? = null,
    val graduationYear: String? = null,
    val schoolName: String? = null,
    val classNumber: String? = null,
    val studentNumber: String? = null,
    val guardianName: String? = null,
    val guardianPhoneNumber: String? = null,
    val thirdGradeSecondSemester: SemesterGrades = SemesterGrades(),
    val thirdGradeFirstSemester: SemesterGrades = SemesterGrades(),
    val previousSemester: SemesterGrades = SemesterGrades(),
    val secondPreviousSemester: SemesterGrades = SemesterGrades(),
    val thirdGradeTotal: Double? = null,
    val previousSemesterTotal: Double? = null,
    val secondPreviousSemesterTotal: Double? = null,
    val subjectScore: Double? = null,
    val volunteerTime: Int? = null,
    val volunteerScore: Double? = null,
    val absentCount: Int? = null,
    val lateCount: Int? = null,
    val earlyLeaveCount: Int? = null,
    val classAbsenceCount: Int? = null,
    val attendanceScore: Double? = null,
    val awarded: Boolean? = null,
    val certified: Boolean? = null,
    val additionalScore: Double? = null,
    val totalScore: Double? = null,
    val admissionTypeCode: String? = null,
    val regionCode: String? = null,
    val specialAdmissionTypeCode: String? = null,
    val gedAverage: Double? = null,
    val gedScores: GedScores? = null,
    val subjectBaseScore: Double? = null,
) {
    // 두 XLSX에서 동일한 학기별 배점을 사용한다.
    val thirdGradeSecondSemesterScore: Double?
        get() = if (graduationStatus == "졸업") thirdGradeSecondSemester.convertedScore(20.0) else null
    val thirdGradeFirstSemesterScore: Double?
        get() = if (graduationStatus == "검정고시") null
            else thirdGradeFirstSemester.convertedScore(if (graduationStatus == "졸업예정") 40.0 else 20.0)
    val previousSemesterScore: Double?
        get() = if (graduationStatus == "검정고시") null else previousSemester.convertedScore(20.0)
    val secondPreviousSemesterScore: Double?
        get() = if (graduationStatus == "검정고시") null else secondPreviousSemester.convertedScore(20.0)
}

data class SemesterGrades(
    val korean: String? = null,
    val society: String? = null,
    val history: String? = null,
    val math: String? = null,
    val science: String? = null,
    val technology: String? = null,
    val english: String? = null,
) {
    /** X를 제외한 평균 평점을 학기 배점으로 환산한다. */
    fun convertedScore(maxScore: Double): Double? =
        listOf(korean, society, history, math, science, technology, english)
            .mapNotNull { GRADE_POINTS[it] }
            .takeIf { it.isNotEmpty() }
            ?.let { it.average() / 5.0 * maxScore }

    val total: Double?
        get() = listOf(korean, society, history, math, science, technology, english)
            .mapNotNull { GRADE_POINTS[it] }
            .takeIf { it.isNotEmpty() }
            ?.sum()

    private companion object {
        val GRADE_POINTS = mapOf("A" to 5.0, "B" to 4.0, "C" to 3.0, "D" to 2.0, "E" to 1.0)
    }
}
