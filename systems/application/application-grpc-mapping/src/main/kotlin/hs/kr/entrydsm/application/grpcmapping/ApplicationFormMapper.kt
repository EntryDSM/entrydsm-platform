package hs.kr.entrydsm.application.grpcmapping

import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.AcademicRecord as GrpcAcademicRecord
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.GedScores as GrpcGedScores
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.MiddleSchool as GrpcMiddleSchool
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.SemesterGrades as GrpcSemesterGrades
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import java.time.ZoneOffset

fun ApplicationFormResult.toGrpcForm(): ApplicationFormResponse =
    ApplicationFormResponse.newBuilder()
        .setApplicantId(applicantId)
        .setUserId(accountId)
        .setApplicantStatus(status.toGrpc())
        .setStatusVersion(statusVersion)
        .setGender(
            when (gender) {
                Gender.MALE -> GrpcGender.GENDER_MALE
                Gender.FEMALE -> GrpcGender.GENDER_FEMALE
                null -> GrpcGender.GENDER_UNSPECIFIED
            },
        )
        .setRegion(region.toGrpc())
        .setAdmissionType(admissionType.toGrpc())
        .setSpecialAdmissionType(
            when (specialAdmissionType) {
                SpecialAdmissionType.NONE -> GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NONE
                SpecialAdmissionType.NATIONAL_MERIT -> GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NATIONAL_MERIT
                SpecialAdmissionType.SPECIAL_ADMISSION -> GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_SPECIAL_ADMISSION
            },
        )
        .setGraduationType(graduationType.toGrpc())
        // apply 안에서는 name 이 빌더의 getName() 으로 잡히므로 also 로 넘긴다.
        .also { builder ->
            submittedAt?.let { builder.setSubmittedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli()) }
            addressBase?.let(builder::setAddressBase)
            name?.let(builder::setName)
            phoneNumber?.let(builder::setPhoneNumber)
            birthdate?.let { builder.setBirthdate(it.toString()) }
            address?.let(builder::setAddress)
            photoFileId?.let(builder::setPhotoFileId)
            graduationDate?.let { builder.setGraduationDate(it.toString()) }
            guardianName?.let(builder::setGuardianName)
            guardianRelation?.let(builder::setGuardianRelation)
            guardianPhoneNumber?.let(builder::setGuardianPhoneNumber)
            introduction?.let(builder::setIntroduction)
            studyPlan?.let(builder::setStudyPlan)
            middleSchool?.let {
                builder.setMiddleSchool(
                    GrpcMiddleSchool.newBuilder()
                        .setCode(it.schoolCode)
                        .setName(it.schoolName)
                        .setStudentNumber(it.studentNumber)
                        .setPhone(it.schoolPhone)
                        .setTeacherName(it.teacherName)
                        .also { schoolBuilder -> it.schoolAddress?.let(schoolBuilder::setAddress) }
                        .build(),
                )
            }
            thirdGradeSecondSemester?.let { builder.setThirdGradeSecondSemester(it.toGrpc()) }
            thirdGradeFirstSemester?.let { builder.setThirdGradeFirstSemester(it.toGrpc()) }
            previousSemester?.let { builder.setPreviousSemester(it.toGrpc()) }
            secondPreviousSemester?.let { builder.setSecondPreviousSemester(it.toGrpc()) }
            academicRecord?.let {
                builder.setAcademicRecord(
                    GrpcAcademicRecord.newBuilder()
                        .setAbsentCount(it.absentCount)
                        .setLateCount(it.lateCount)
                        .setEarlyLeaveCount(it.earlyLeaveCount)
                        .setClassAbsenceCount(it.classAbsenceCount)
                        .setVolunteerTime(it.volunteerTime)
                        .setDsmAlgorithmAwarded(it.isDsmAlgorithmAwarded)
                        .setProgrammingCertified(it.isProgrammingCertified)
                        .build(),
                )
            }
            score?.let {
                builder.setSubjectScore(it.subjectScore)
                builder.setAttendanceScore(it.attendanceScore)
                builder.setVolunteerScore(it.volunteerScore)
                builder.setAdditionalScore(it.additionalScore)
                builder.setTotalScore(it.totalScore)
            }
            classNumber?.let(builder::setClassNumber)
            studentNumber?.let(builder::setStudentNumber)
            gedAverage?.let(builder::setGedAverage)
            gedScores?.let { builder.setGedScores(it.toGrpc()) }
            examineeNumber?.let(builder::setExamineeNumber)
            admissionType.code()?.let(builder::setAdmissionTypeCode)
            region.code()?.let(builder::setRegionCode)
            builder.setSpecialAdmissionTypeCode(specialAdmissionType.code())
        }
        .build()

private fun AdmissionType?.code(): String? = when (this) {
    AdmissionType.MEISTER -> "1"
    AdmissionType.SOCIAL -> "2"
    AdmissionType.REGULAR -> "3"
    null -> null
}

private fun Region?.code(): String? = when (this) {
    Region.DAEJEON -> "1"
    Region.NATIONAL -> "2"
    null -> null
}

private fun SpecialAdmissionType.code(): String = when (this) {
    SpecialAdmissionType.NONE -> "0"
    SpecialAdmissionType.NATIONAL_MERIT -> "1"
    SpecialAdmissionType.SPECIAL_ADMISSION -> "2"
}

/** 성취도 A~E. 미이수(X)는 요강에 없는 값이라 빈 문자열로 준다. */
private fun SubjectGrades.toGrpc(): GrpcSemesterGrades =
    GrpcSemesterGrades.newBuilder()
        .setKorean(koreanGrade.label())
        .setSociety(societyGrade.label())
        .setHistory(historyGrade.label())
        .setMath(mathGrade.label())
        .setScience(scienceGrade.label())
        .setTechnology(technologyGrade.label())
        .setEnglish(englishGrade.label())
        .build()

private fun SubjectGrade.label(): String = if (this == SubjectGrade.X) "" else name

private fun GedScores.toGrpc(): GrpcGedScores =
    GrpcGedScores.newBuilder()
        .setKorean(koreanScore)
        .setSociety(societyScore)
        .setHistory(historyScore)
        .setMath(mathScore)
        .setScience(scienceScore)
        .setTechnology(technologyScore)
        .setEnglish(englishScore)
        .build()

private fun Region?.toGrpc(): GrpcRegion = when (this) {
    Region.DAEJEON -> GrpcRegion.REGION_DAEJEON
    Region.NATIONAL -> GrpcRegion.REGION_NATIONAL
    null -> GrpcRegion.REGION_UNSPECIFIED
}

private fun AdmissionType?.toGrpc(): GrpcAdmissionType = when (this) {
    AdmissionType.REGULAR -> GrpcAdmissionType.ADMISSION_TYPE_REGULAR
    AdmissionType.MEISTER -> GrpcAdmissionType.ADMISSION_TYPE_MEISTER
    AdmissionType.SOCIAL -> GrpcAdmissionType.ADMISSION_TYPE_SOCIAL
    null -> GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED
}

private fun GraduationType?.toGrpc(): GrpcGraduationType = when (this) {
    GraduationType.PROSPECTIVE -> GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE
    GraduationType.GRADUATED -> GrpcGraduationType.GRADUATION_TYPE_GRADUATED
    GraduationType.GED -> GrpcGraduationType.GRADUATION_TYPE_GED
    null -> GrpcGraduationType.GRADUATION_TYPE_UNSPECIFIED
}

private fun ApplicantStatus.toGrpc(): GrpcApplicantStatus = when (this) {
    ApplicantStatus.DRAFT -> GrpcApplicantStatus.APPLICANT_STATUS_DRAFT
    ApplicantStatus.SUBMITTED -> GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED
    ApplicantStatus.ARRIVAL -> GrpcApplicantStatus.APPLICANT_STATUS_ARRIVAL
    ApplicantStatus.REVIEWING -> GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING
    ApplicantStatus.COMPLETED -> GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED
    ApplicantStatus.CANCELED -> GrpcApplicantStatus.APPLICANT_STATUS_CANCELED
}
