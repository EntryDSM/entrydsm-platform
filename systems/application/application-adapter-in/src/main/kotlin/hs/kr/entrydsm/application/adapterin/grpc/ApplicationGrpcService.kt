package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.AcademicRecord as GrpcAcademicRecord
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.MiddleSchool as GrpcMiddleSchool
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.SemesterGrades as GrpcSemesterGrades
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.ZoneOffset
import org.springframework.stereotype.Component

@Component
class ApplicationGrpcService(
    private val applicationPort: ApplicationPort,
) : ApplicationServiceGrpc.ApplicationServiceImplBase() {
    override fun createApplication(
        request: CreateApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByAccountId(request.userId)
            ?: applicationPort.createApplicant(CreateApplicantCommand(request.userId)).snapshot
    }

    override fun getApplication(
        request: GetApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByAccountId(request.userId)
            ?: throw ApplicantNotFoundException(request.userId)
    }

    override fun cancelApplication(
        request: CancelApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.cancel(request.userId, request.reason.takeIf { request.hasReason() })
    }

    override fun getApplicant(
        request: GetApplicantRequest,
        responseObserver: StreamObserver<ApplicantResponse>,
    ) = responseObserver.respondWith {
        request.applicantId.validate()
        (applicationPort.findApplicant(request.applicantId) ?: throw ApplicantNotFoundException(request.applicantId))
            .toResponse()
    }

    override fun getApplicationForm(
        request: GetApplicationFormRequest,
        responseObserver: StreamObserver<ApplicationFormResponse>,
    ) = responseObserver.respondWith {
        request.accountId.validate()
        (applicationPort.findApplicationForm(request.accountId) ?: throw ApplicantNotFoundException(request.accountId))
            .toResponse()
    }

    private fun Long.validate() {
        require(this > 0) { "id must be positive" }
    }

    private fun StreamObserver<ApplicationResponse>.respond(block: () -> ApplicationSnapshotResult) =
        respondWith { block().toResponse() }

    private fun <T> StreamObserver<T>.respondWith(block: () -> T) {
        try {
            onNext(block())
            onCompleted()
        } catch (exception: Exception) {
            onError(
                when (exception) {
                    is IllegalArgumentException -> Status.INVALID_ARGUMENT
                    is ApplicantNotFoundException -> Status.NOT_FOUND
                    is ApplicationCancelNotAllowedException -> Status.FAILED_PRECONDITION
                    else -> Status.INTERNAL
                }.withCause(exception).asRuntimeException(),
            )
        }
    }

    private fun ApplicationSnapshotResult.toResponse(): ApplicationResponse =
        ApplicationResponse.newBuilder()
            .setUserId(accountId)
            .setApplicantStatus(applicantStatus.toGrpc())
            .apply {
                submittedAt?.let { setSubmittedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli()) }
                announcedAt?.let { setAnnouncedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli()) }
            }
            .setUpdatedAtEpochMillis(updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .setPassStatus(
                when (passStatus) {
                    PassResultStatus.PENDING -> GrpcPassStatus.PASS_STATUS_NOT_ANNOUNCED
                    PassResultStatus.PASS -> GrpcPassStatus.PASS_STATUS_PASSED
                    PassResultStatus.FAIL -> GrpcPassStatus.PASS_STATUS_FAILED
                },
            )
            .build()

    private fun ApplicantStatus.toGrpc(): GrpcApplicantStatus = when (this) {
        ApplicantStatus.DRAFT -> GrpcApplicantStatus.APPLICANT_STATUS_DRAFT
        ApplicantStatus.SUBMITTED -> GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED
        ApplicantStatus.REVIEWING -> GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING
        ApplicantStatus.COMPLETED -> GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED
        ApplicantStatus.CANCELED -> GrpcApplicantStatus.APPLICANT_STATUS_CANCELED
    }

    private fun ApplicantResult.toResponse(): ApplicantResponse =
        ApplicantResponse.newBuilder()
            .setApplicantId(applicantId)
            .setUserId(accountId)
            .setRegion(region.toGrpc())
            .setAdmissionType(admissionType.toGrpc())
            // apply 안에서는 name 이 빌더의 getName() 으로 잡히므로 also 로 넘긴다.
            .also { builder ->
                name?.let(builder::setName)
                schoolName?.let(builder::setSchoolName)
                photoFileId?.let(builder::setPhotoFileId)
            }
            .build()

    private fun ApplicationFormResult.toResponse(): ApplicationFormResponse =
        ApplicationFormResponse.newBuilder()
            .setApplicantId(applicantId)
            .setUserId(accountId)
            .setApplicantStatus(status.toGrpc())
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
                name?.let(builder::setName)
                phoneNumber?.let(builder::setPhoneNumber)
                birthdate?.let { builder.setBirthdate(it.toString()) }
                address?.let(builder::setAddress)
                photoFileId?.let(builder::setPhotoFileId)
                graduationDate?.let { builder.setGraduationDate(it.toString()) }
                guardianName?.let(builder::setGuardianName)
                guardianRelation?.let(builder::setGuardianRelation)
                guardianPhoneNumber?.let(builder::setGuardianPhoneNumber)
                middleSchool?.let {
                    builder.setMiddleSchool(
                        GrpcMiddleSchool.newBuilder()
                            .setName(it.schoolName)
                            .setStudentNumber(it.studentNumber)
                            .setPhone(it.schoolPhone)
                            .setTeacherName(it.teacherName)
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
            }
            .build()

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
}
