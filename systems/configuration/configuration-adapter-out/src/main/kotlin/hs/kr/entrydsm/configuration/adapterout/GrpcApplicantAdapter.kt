package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.SemesterGrades as GrpcSemesterGrades
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GrpcApplicantAdapter(
    @Value("\${application.grpc.host}") host: String,
    @Value("\${application.grpc.port}") port: Int,
    @Value("\${application.grpc.deadline-ms:3000}") private val deadlineMs: Long,
) : ApplicantPort, DisposableBean {

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = ApplicationServiceGrpc.newBlockingStub(channel)

    override fun findById(applicantId: Long): Applicant? =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .getApplicant(GetApplicantRequest.newBuilder().setApplicantId(applicantId).build())
                .toApplicant()
        } catch (e: StatusRuntimeException) {
            // 0 이하 id 는 application 이 INVALID_ARGUMENT 로 거절한다. 없는 지원자와 같다.
            if (e.status.code in NO_APPLICANT) null else throw ApplicantLookupFailedException(applicantId, cause = e)
        }

    override fun findApplicationForm(accountId: Long): ApplicationForm? =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(accountId).build())
                .toApplicationForm()
        } catch (e: StatusRuntimeException) {
            if (e.status.code in NO_APPLICANT) null else throw ApplicantLookupFailedException(accountId, "accountId", e)
        }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun ApplicantResponse.toApplicant() = Applicant(
        userId = userId,
        name = name.takeIf { hasName() },
        schoolName = schoolName.takeIf { hasSchoolName() },
        region = region.toRegion(),
        admissionType = admissionType.toAdmissionType(),
        photoFileId = photoFileId.takeIf { hasPhotoFileId() },
    )

    private fun ApplicationFormResponse.toApplicationForm() = ApplicationForm(
        applicantId = applicantId,
        userId = userId,
        name = name.takeIf { hasName() },
        phoneNumber = phoneNumber.takeIf { hasPhoneNumber() },
        birthdate = birthdate.takeIf { hasBirthdate() },
        gender = when (gender) {
            GrpcGender.GENDER_MALE -> ApplicationForm.Gender.MALE
            GrpcGender.GENDER_FEMALE -> ApplicationForm.Gender.FEMALE
            else -> null
        },
        address = address.takeIf { hasAddress() },
        photoFileId = photoFileId.takeIf { hasPhotoFileId() },
        region = region.toRegion(),
        admissionType = admissionType.toAdmissionType(),
        // 요강이 특기사항 칸을 정의하지 않아, 원서에 있는 값 중 전형에 덧붙는 구분을 찍는다.
        specialNote = when (specialAdmissionType) {
            GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NATIONAL_MERIT -> "국가유공자 자녀"
            GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_SPECIAL_ADMISSION -> "특례입학 대상자"
            else -> null
        },
        graduationType = when (graduationType) {
            GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE -> ApplicationForm.GraduationType.PROSPECTIVE
            GrpcGraduationType.GRADUATION_TYPE_GRADUATED -> ApplicationForm.GraduationType.GRADUATED
            GrpcGraduationType.GRADUATION_TYPE_GED -> ApplicationForm.GraduationType.GED
            else -> null
        },
        graduationDate = graduationDate.takeIf { hasGraduationDate() },
        guardianName = guardianName.takeIf { hasGuardianName() },
        guardianRelation = guardianRelation.takeIf { hasGuardianRelation() },
        guardianPhoneNumber = guardianPhoneNumber.takeIf { hasGuardianPhoneNumber() },
        introduction = introduction.takeIf { hasIntroduction() },
        studyPlan = studyPlan.takeIf { hasStudyPlan() },
        school = middleSchool.takeIf { hasMiddleSchool() }?.let { school ->
            ApplicationForm.MiddleSchool(
                code = school.code,
                name = school.name,
                studentNumber = school.studentNumber,
                phone = school.phone,
                teacherName = school.teacherName,
                address = school.address.takeIf { school.hasAddress() },
            )
        },
        // 서식의 열 순서 그대로다 — 3학년 2학기, 3학년 1학기, 직전학기, 직전전학기.
        semesterGrades = listOf(
            thirdGradeSecondSemester.takeIf { hasThirdGradeSecondSemester() },
            thirdGradeFirstSemester.takeIf { hasThirdGradeFirstSemester() },
            previousSemester.takeIf { hasPreviousSemester() },
            secondPreviousSemester.takeIf { hasSecondPreviousSemester() },
        ).map { it?.toSemesterGrades() },
        gedScores = gedScores.takeIf { hasGedScores() }?.let {
            ApplicationForm.SemesterGrades(
                korean = it.korean.toString(),
                society = it.society.toString(),
                history = it.history.toString(),
                math = it.math.toString(),
                science = it.science.toString(),
                technology = it.technology.toString(),
                english = it.english.toString(),
            )
        },
        academicRecord = academicRecord.takeIf { hasAcademicRecord() }?.let {
            ApplicationForm.AcademicRecord(
                absentCount = it.absentCount,
                lateCount = it.lateCount,
                earlyLeaveCount = it.earlyLeaveCount,
                classAbsenceCount = it.classAbsenceCount,
                volunteerTime = it.volunteerTime,
                dsmAlgorithmAwarded = it.dsmAlgorithmAwarded,
                programmingCertified = it.programmingCertified,
            )
        },
    )

    private fun GrpcSemesterGrades.toSemesterGrades() = ApplicationForm.SemesterGrades(
        korean = korean,
        society = society,
        history = history,
        math = math,
        science = science,
        technology = technology,
        english = english,
    )

    private fun GrpcRegion.toRegion(): Applicant.Region? = when (this) {
        GrpcRegion.REGION_DAEJEON -> Applicant.Region.DAEJEON
        GrpcRegion.REGION_NATIONAL -> Applicant.Region.NATIONAL
        else -> null
    }

    private fun GrpcAdmissionType.toAdmissionType(): Applicant.AdmissionType? = when (this) {
        GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> Applicant.AdmissionType.REGULAR
        GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> Applicant.AdmissionType.MEISTER
        GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> Applicant.AdmissionType.SOCIAL
        else -> null
    }

    private companion object {
        val NO_APPLICANT = setOf(Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT)
    }
}
