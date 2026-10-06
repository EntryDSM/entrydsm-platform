package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationErrorCode.*
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.domain.enum.*
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import java.time.LocalDate
import java.time.YearMonth

/** 지원자·관리자 수정이 공유하는 필드 검증과 변경 규칙. 상태·권한 정책은 각 유스케이스에서 검증한다. */
class ApplicationFormEditor(private val accountPhoneValidator: AccountPhoneValidator) {
    fun updateType(
        applicant: Applicant,
        admissionType: AdmissionType,
        region: Region,
        graduationType: GraduationType,
        graduationDate: YearMonth?,
    ) {
        APPLICATION_GRADUATION_DATE_REQUIRED.requireValid(graduationType == GraduationType.GED || graduationDate != null)
        APPLICATION_GRADUATION_DATE_NOT_ALLOWED.requireValid(graduationType != GraduationType.GED || graduationDate == null)
        APPLICATION_GRADUATION_DATE_OUT_OF_RANGE.requireValid(graduationDate == null || graduationDate.year in 2026..2027)

        applicant.admissionType = admissionType
        applicant.region = region
        applicant.graduationType = graduationType
        applicant.graduationDate = graduationDate
        if (graduationType == GraduationType.GED) {
            applicant.middleSchoolInfo = null
            applicant.academicRecord?.subjectGrades?.clear()
    }
        applicant.totalScore = null
        applicant.totalScoreUpdatedAt = null
    }

    fun updatePersonal(
        applicant: Applicant,
        photoFileId: String,
        name: String,
        phoneNumber: String,
        gender: Gender,
        birthdate: LocalDate,
        specialAdmissionType: SpecialAdmissionType,
    ) {
        APPLICATION_PHOTO_FILE_ID_REQUIRED.requireValid(photoFileId.isNotBlank())
        APPLICATION_PHOTO_FILE_ID_TOO_LONG.requireValid(photoFileId.length <= MAX_PHOTO_FILE_ID_LENGTH)
        APPLICATION_NAME_REQUIRED.requireValid(name.isNotBlank())
        APPLICATION_PHONE_NUMBER_INVALID_FORMAT.requireValid(phoneNumber.matches(PHONE_NUMBER_REGEX))

        APPLICATION_PHONE_NUMBER_MISMATCH.requireValid(accountPhoneValidator.validate(applicant.accountId, phoneNumber))
        applicant.photoFileId = photoFileId
        applicant.name = name
        applicant.phoneNumber = phoneNumber
        applicant.gender = gender
        applicant.birthdate = birthdate
        applicant.specialAdmissionType = specialAdmissionType
    }

    fun updateFamily(
        applicant: Applicant,
        guardianName: String,
        guardianPhoneNumber: String,
        guardianGender: Gender,
        guardianRelation: String,
        zipCode: String,
        addressBase: String,
        addressDetail: String,
    ) {
        APPLICATION_GUARDIAN_NAME_REQUIRED.requireValid(guardianName.isNotBlank())
        APPLICATION_GUARDIAN_PHONE_NUMBER_INVALID_FORMAT.requireValid(guardianPhoneNumber.matches(PHONE_NUMBER_REGEX))
        APPLICATION_ADDRESS_ZIP_CODE_REQUIRED.requireValid(zipCode.isNotBlank())
        APPLICATION_ADDRESS_ADDRESS_BASE_REQUIRED.requireValid(addressBase.isNotBlank())
        APPLICATION_ADDRESS_ADDRESS_DETAIL_REQUIRED.requireValid(addressDetail.isNotBlank())

        applicant.guardianName = guardianName
        applicant.guardianPhoneNumber = guardianPhoneNumber
        applicant.guardianGender = guardianGender
        applicant.guardianRelation = guardianRelation
        applicant.zipCode = zipCode
        applicant.addressBase = addressBase
        applicant.addressDetail = addressDetail
    }

    fun updateMiddleSchool(
        applicant: Applicant,
        schoolCode: String,
        schoolName: String,
        studentNumber: String,
        schoolPhone: String,
        teacherName: String,
    ) {
        APPLICATION_MIDDLE_SCHOOL_NOT_ALLOWED.requireValid(applicant.graduationType != GraduationType.GED)
        APPLICATION_SCHOOL_CODE_REQUIRED.requireValid(schoolCode.isNotBlank())
        APPLICATION_SCHOOL_NAME_REQUIRED.requireValid(schoolName.isNotBlank())
        APPLICATION_STUDENT_NUMBER_REQUIRED.requireValid(studentNumber.isNotBlank())
        APPLICATION_STUDENT_NUMBER_INVALID_FORMAT.requireValid(studentNumber.matches(Regex("[0-9]{5}")))
        APPLICATION_STUDENT_NUMBER_OUT_OF_RANGE.requireValid(
            studentNumber.first() in '1'..'3' &&
                studentNumber.substring(1, 3).toInt() in 1..99 &&
                studentNumber.substring(3, 5).toInt() in 1..99,
        )
        APPLICATION_SCHOOL_PHONE_REQUIRED.requireValid(schoolPhone.isNotBlank())
        APPLICATION_TEACHER_NAME_REQUIRED.requireValid(teacherName.isNotBlank())

        applicant.middleSchoolInfo = MiddleSchoolInfo(
            schoolCode = schoolCode,
            schoolName = schoolName,
            studentNumber = studentNumber,
            schoolPhone = schoolPhone,
            teacherName = teacherName,
        )
    }

    fun updateIntroduction(applicant: Applicant, introduction: String) {
        APPLICATION_INTRODUCTION_REQUIRED.requireValid(introduction.isNotBlank())
        APPLICATION_INTRODUCTION_TOO_LONG.requireValid(introduction.length <= MAX_ESSAY_LENGTH)

        applicant.introduction = introduction
    }

    fun updateStudyPlan(applicant: Applicant, studyPlan: String) {
        APPLICATION_STUDY_PLAN_REQUIRED.requireValid(studyPlan.isNotBlank())
        APPLICATION_STUDY_PLAN_TOO_LONG.requireValid(studyPlan.length <= MAX_ESSAY_LENGTH)

        applicant.studyPlan = studyPlan
    }
    companion object {
        private val PHONE_NUMBER_REGEX = Regex("^010-\\d{4}-\\d{4}$")
        private const val MAX_ESSAY_LENGTH = 1600
        private const val MAX_PHOTO_FILE_ID_LENGTH = 64
    }
}
