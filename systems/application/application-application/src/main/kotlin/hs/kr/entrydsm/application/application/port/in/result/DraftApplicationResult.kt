package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades

data class DraftApplicationResult(
    val type: Type,
    val personal: Personal,
    val family: Family,
    val middleSchool: MiddleSchool?,
    val evaluation: Evaluation,
    val introduction: String?,
    val studyPlan: String?,
) {
    data class Type(val admissionType: AdmissionType?, val region: Region?, val graduationType: GraduationType?, val graduationDate: String?)
    data class Personal(val photoFileId: String?, val name: String?, val phoneNumber: String?, val gender: Gender?, val birthdate: String?, val specialAdmissionType: SpecialAdmissionType)
    data class Family(val guardianName: String?, val guardianPhoneNumber: String?, val guardianGender: Gender?, val guardianRelation: String?, val address: Address)
    data class Address(val zipCode: String?, val addressBase: String?, val addressDetail: String?)
    data class MiddleSchool(val schoolCode: String, val schoolName: String, val studentNumber: String, val schoolPhone: String, val teacherName: String)
    data class Evaluation(val subjectGrades: List<SemesterGrades>, val gedScores: GedScores?, val academicRecord: AcademicRecord?, val certificates: Certificates?)
    data class SemesterGrades(val schoolSemester: String, val subjects: SubjectGrades)
    data class AcademicRecord(val absentCount: Int, val lateCount: Int, val earlyLeaveCount: Int, val classAbsenceCount: Int, val volunteerTime: Int)
    data class Certificates(val isDsmAlgorithmAwarded: Boolean, val isProgrammingCertified: Boolean)
}

// 저장된 입력만 복사하며 계산·보정·원본 변경은 수행하지 않는다.
fun Applicant.toDraftApplicationResult(): DraftApplicationResult {
    val record = academicRecord
    return DraftApplicationResult(
        type = DraftApplicationResult.Type(admissionType, region, graduationType, graduationDate?.toString()),
        personal = DraftApplicationResult.Personal(photoFileId, name, phoneNumber, gender, birthdate?.toString(), specialAdmissionType),
        family = DraftApplicationResult.Family(guardianName, guardianPhoneNumber, guardianGender, guardianRelation,
            DraftApplicationResult.Address(zipCode, addressBase, addressDetail)),
        middleSchool = middleSchoolInfo?.let {
            DraftApplicationResult.MiddleSchool(it.schoolCode, it.schoolName, it.studentNumber, it.schoolPhone, it.teacherName)
        },
        evaluation = DraftApplicationResult.Evaluation(
            subjectGrades = listOf(
                SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to "2-1",
                SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to "2-2",
                SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to "3-1",
                SchoolSemester.THIRD_GRADE_SECOND_SEMESTER to "3-2",
            ).mapNotNull { (semester, label) ->
                record?.subjectGrades?.get(semester)?.let { DraftApplicationResult.SemesterGrades(label, it) }
            },
            gedScores = record?.gedScores?.copy(),
            academicRecord = record?.let { DraftApplicationResult.AcademicRecord(it.absentCount, it.lateCount, it.earlyLeaveCount, it.classAbsenceCount, it.volunteerTime) },
            certificates = record?.let { DraftApplicationResult.Certificates(it.isDsmAlgorithmAwarded, it.isProgrammingCertified) },
        ),
        introduction = introduction,
        studyPlan = studyPlan,
    )
}
