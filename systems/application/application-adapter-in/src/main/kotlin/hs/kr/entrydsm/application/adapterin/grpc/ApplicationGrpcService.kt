package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicantQueryPort
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.PassResultPort
import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantSummaryResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.PassResult
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsRequest
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsResponse
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantSummary
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.ListApplicantsResponse
import hs.kr.entrydsm.application.grpc.PassResultEntry
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.ResultType as GrpcResultType
import hs.kr.entrydsm.application.grpc.ScoreBreakdown as GrpcScoreBreakdown
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component

@Component
class ApplicationGrpcService(
    private val applicationPort: ApplicationPort,
    private val passResultPort: PassResultPort,
    private val applicantQueryPort: ApplicantQueryPort,
) : ApplicationServiceGrpc.ApplicationServiceImplBase() {
    override fun createApplication(
        request: CreateApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByUserId(request.userId)
            ?: applicationPort.createApplicant(CreateApplicantCommand(request.userId)).snapshot
    }

    override fun getApplication(
        request: GetApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByUserId(request.userId)
            ?: throw ApplicantNotFoundException(request.userId)
    }

    override fun cancelApplication(
        request: CancelApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.cancel(request.userId, request.reason.takeIf { request.hasReason() })
    }

    override fun announcePassResults(
        request: AnnouncePassResultsRequest,
        responseObserver: StreamObserver<AnnouncePassResultsResponse>,
    ) {
        try {
            val applied = passResultPort.announce(
                AnnouncePassResultsCommand(request.entriesList.map { it.toPassResult() }),
            )
            responseObserver.onNext(
                AnnouncePassResultsResponse.newBuilder().setAppliedCount(applied).build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatus().withCause(exception).asRuntimeException())
        }
    }

    override fun listApplicants(
        request: ListApplicantsRequest,
        responseObserver: StreamObserver<ListApplicantsResponse>,
    ) {
        try {
            responseObserver.onNext(
                ListApplicantsResponse.newBuilder()
                    .addAllItems(applicantQueryPort.findAllSubmitted().map { it.toSummary() })
                    .build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatus().withCause(exception).asRuntimeException())
        }
    }

    /**
     * 빌더를 `apply` 로 받으면 빌더의 동명 프로퍼티가 원본 값을 가리므로, 선택 항목은
     * 지역 변수로 꺼내 둔 뒤 채웁니다.
     */
    private fun ApplicantSummaryResult.toSummary(): ApplicantSummary {
        val builder = ApplicantSummary.newBuilder()
            .setApplicantId(applicantId)
            .setUserId(userId)
            .setName(name)
            .setPhoneNumber(phoneNumber)
            .setSchoolName(schoolName)
            .setRegion(
                when (region) {
                    Region.DAEJEON -> GrpcRegion.REGION_DAEJEON
                    Region.NATIONAL -> GrpcRegion.REGION_NATIONAL
                    null -> GrpcRegion.REGION_UNSPECIFIED
                },
            )
            .setAdmissionType(
                when (admissionType) {
                    AdmissionType.REGULAR -> GrpcAdmissionType.ADMISSION_TYPE_REGULAR
                    AdmissionType.MEISTER -> GrpcAdmissionType.ADMISSION_TYPE_MEISTER
                    AdmissionType.SOCIAL -> GrpcAdmissionType.ADMISSION_TYPE_SOCIAL
                    null -> GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED
                },
            )
            .setGraduationType(
                when (graduationType) {
                    GraduationType.PROSPECTIVE -> GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE
                    GraduationType.GRADUATED -> GrpcGraduationType.GRADUATION_TYPE_GRADUATED
                    GraduationType.GED -> GrpcGraduationType.GRADUATION_TYPE_GED
                    null -> GrpcGraduationType.GRADUATION_TYPE_UNSPECIFIED
                },
            )
            .setApplicantStatus(applicantStatus.toGrpc())
            .setUpdatedAtEpochMillis(updatedAt.toEpochMillis())

        birthdate?.let { builder.setBirthDate(DateTimeFormatter.ISO_LOCAL_DATE.format(it)) }
        submittedAt?.let { builder.setSubmittedAtEpochMillis(it.toEpochMillis()) }
        score?.let {
            builder.setScore(
                GrpcScoreBreakdown.newBuilder()
                    .setSubjectScore(it.subjectScore)
                    .setAttendanceScore(it.attendanceScore)
                    .setVolunteerScore(it.volunteerScore)
                    .setTotalScore(it.totalScore)
                    .build(),
            )
        }

        return builder.build()
    }

    private fun PassResultEntry.toPassResult(): PassResult = PassResult(
        applicantId = applicantId,
        resultType = when (resultType) {
            GrpcResultType.RESULT_TYPE_DOCUMENT -> ResultType.DOCUMENT
            GrpcResultType.RESULT_TYPE_FINAL -> ResultType.FINAL
            else -> throw IllegalArgumentException("result_type must be DOCUMENT or FINAL")
        },
        result = when (result) {
            GrpcPassStatus.PASS_STATUS_PASSED -> PassResultStatus.PASS
            GrpcPassStatus.PASS_STATUS_FAILED -> PassResultStatus.FAIL
            else -> throw IllegalArgumentException("result must be PASSED or FAILED")
        },
        processedBy = processedBy.takeIf { hasProcessedBy() },
        processedAt = Instant.ofEpochMilli(processedAtEpochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime(),
    )

    private fun Long.validate() {
        require(this > 0) { "user_id must be positive" }
    }

    private fun StreamObserver<ApplicationResponse>.respond(block: () -> ApplicationSnapshotResult) {
        try {
            onNext(block().toResponse())
            onCompleted()
        } catch (exception: Exception) {
            onError(exception.toStatus().withCause(exception).asRuntimeException())
        }
    }

    private fun Exception.toStatus(): Status = when (this) {
        is IllegalArgumentException -> Status.INVALID_ARGUMENT
        is ApplicantNotFoundException -> Status.NOT_FOUND
        is ApplicationCancelNotAllowedException -> Status.FAILED_PRECONDITION
        else -> Status.INTERNAL
    }

    private fun ApplicationSnapshotResult.toResponse(): ApplicationResponse =
        ApplicationResponse.newBuilder()
            .setUserId(userId)
            .setApplicantStatus(applicantStatus.toGrpc())
            .apply {
                submittedAt?.let { setSubmittedAtEpochMillis(it.toEpochMillis()) }
                announcedAt?.let { setAnnouncedAtEpochMillis(it.toEpochMillis()) }
            }
            .setUpdatedAtEpochMillis(updatedAt.toEpochMillis())
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

    private fun LocalDateTime.toEpochMillis(): Long =
        toInstant(ZoneOffset.UTC).toEpochMilli()
}
