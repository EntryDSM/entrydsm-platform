package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
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
            .setApplicantStatus(
                when (applicantStatus) {
                    ApplicantStatus.DRAFT -> GrpcApplicantStatus.APPLICANT_STATUS_DRAFT
                    ApplicantStatus.SUBMITTED -> GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED
                    ApplicantStatus.REVIEWING -> GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING
                    ApplicantStatus.COMPLETED -> GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED
                    ApplicantStatus.CANCELED -> GrpcApplicantStatus.APPLICANT_STATUS_CANCELED
                },
            )
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

    private fun ApplicantResult.toResponse(): ApplicantResponse =
        ApplicantResponse.newBuilder()
            .setApplicantId(applicantId)
            .setUserId(accountId)
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
            // apply 안에서는 name 이 빌더의 getName() 으로 잡히므로 also 로 넘긴다.
            .also { builder ->
                name?.let(builder::setName)
                schoolName?.let(builder::setSchoolName)
                photoFileId?.let(builder::setPhotoFileId)
            }
            .build()
}
