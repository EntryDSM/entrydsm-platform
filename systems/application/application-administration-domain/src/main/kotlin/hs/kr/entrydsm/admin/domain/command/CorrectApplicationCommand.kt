package hs.kr.entrydsm.admin.domain.command

/** null인 필드는 유지한다. ID·계정·접수·전형 결과는 수정 요청에 포함하지 않는다. */
data class CorrectApplicationCommand(
    val applicantId: Long,
    val reason: String,
    val changes: ApplicationFormChanges,
)

data class ApplicationFormChanges(
    val admissionType: String? = null,
    val region: String? = null,
    val graduationType: String? = null,
    val graduationDate: String? = null,
    val photoFileId: String? = null,
    val name: String? = null,
    val phoneNumber: String? = null,
    val gender: String? = null,
    val birthdate: String? = null,
    val specialAdmissionType: String? = null,
    val guardianName: String? = null,
    val guardianPhoneNumber: String? = null,
    val guardianGender: String? = null,
    val guardianRelation: String? = null,
    val zipCode: String? = null,
    val addressBase: String? = null,
    val addressDetail: String? = null,
    val introduction: String? = null,
    val studyPlan: String? = null,
    val school: SchoolCorrection? = null,
    val academicRecord: AcademicRecordCorrection? = null,
)

data class SchoolCorrection(
    val schoolCode: String,
    val schoolName: String,
    val studentNumber: String,
    val schoolPhone: String,
    val teacherName: String,
)

/** 성적 변경은 출결·봉사·자격증·교과/검정고시 성적을 포함한 전체 기록으로 받는다. */
data class AcademicRecordCorrection(
    val absentCount: Int?,
    val lateCount: Int?,
    val earlyLeaveCount: Int?,
    val classAbsenceCount: Int?,
    val volunteerTime: Int?,
    val dsmAlgorithmAwarded: Boolean?,
    val programmingCertified: Boolean?,
    val subjectGrades: Map<String, SubjectGradesCorrection> = emptyMap(),
    val gedScores: GedScoresCorrection? = null,
)

data class SubjectGradesCorrection(
    val korean: String, val math: String, val english: String,
    val science: String, val society: String, val technology: String, val history: String,
)

data class GedScoresCorrection(
    val korean: Int?, val math: Int?, val english: Int?,
    val science: Int?, val society: Int?, val technology: Int?, val history: Int?,
)
