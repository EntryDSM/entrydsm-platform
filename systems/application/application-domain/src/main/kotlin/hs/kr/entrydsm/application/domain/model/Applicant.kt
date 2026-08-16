package hs.kr.entrydsm.application.domain.model

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.GuardianRelation
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class Applicant(
    val id: Long,
    val accountId: Long,
    var photoFileId: Long? = null,
    var name: String? = null,
    var phoneNumber: String? = null,
    var gender: Gender? = null,
    var birthdate: LocalDate? = null,
    var specialAdmissionType: SpecialAdmissionType = SpecialAdmissionType.NONE,
    var admissionType: AdmissionType? = null,
    var region: Region? = null,
    var graduationType: GraduationType? = null,
    var graduationDate: YearMonth? = null,
    var guardianName: String? = null,
    var guardianPhoneNumber: String? = null,
    var guardianGender: Gender? = null,
    var guardianRelation: GuardianRelation? = null,
    var addressBase: String? = null,
    var addressDetail: String? = null,
    var zipCode: String? = null,
    var introduction: String? = null,
    var studyPlan: String? = null,
    var middleSchoolInfo: MiddleSchoolInfo? = null,
    var academicRecord: AcademicRecord? = null,
    var totalScore: Double? = null,
    var totalScoreUpdatedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun touch() {
        updatedAt = LocalDateTime.now()
    }
}

data class MiddleSchoolInfo(
    val schoolName: String,
    val studentNumber: String,
    val schoolPhone: String,
    val teacherName: String,
)

data class AcademicRecord(
    var absentCount: Int = 0,
    var lateCount: Int = 0,
    var earlyLeaveCount: Int = 0,
    var classAbsenceCount: Int = 0,
    var volunteerTime: Int = 0,
    var isDsmAlgorithmAwarded: Boolean = false,
    var isProgrammingCertified: Boolean = false,
    val subjectGrades: MutableMap<SchoolSemester, SubjectGrades> = linkedMapOf(),
    var gedScores: GedScores? = null,
) {
    init {
        require(absentCount >= 0) { "absentCount must be greater than or equal to 0" }
        require(lateCount >= 0) { "lateCount must be greater than or equal to 0" }
        require(earlyLeaveCount >= 0) { "earlyLeaveCount must be greater than or equal to 0" }
        require(classAbsenceCount >= 0) { "classAbsenceCount must be greater than or equal to 0" }
        require(volunteerTime >= 0) { "volunteerTime must be greater than or equal to 0" }
    }
}

data class SubjectGrades(
    val koreanGrade: SubjectGrade,
    val mathGrade: SubjectGrade,
    val englishGrade: SubjectGrade,
    val scienceGrade: SubjectGrade,
    val societyGrade: SubjectGrade,
    val technologyGrade: SubjectGrade,
    val historyGrade: SubjectGrade,
)

data class GedScores(
    val koreanScore: Int,
    val mathScore: Int,
    val englishScore: Int,
    val scienceScore: Int,
    val societyScore: Int,
    val technologyScore: Int,
    val historyScore: Int,
) {
    init {
        listOf(
            koreanScore,
            mathScore,
            englishScore,
            scienceScore,
            societyScore,
            technologyScore,
            historyScore,
        ).forEach { score ->
            require(score in 0..100) { "score must be between 0 and 100" }
        }
    }
}
